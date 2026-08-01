# L6-04·大主题04-9 MVCC 实现内幕

> 基线 Py3.11.15/np2.4.6/gcc13.3 @2026-07-25 ｜ 核实日期：2026-08-01 ｜ 先修：本课大主题 03-9 事务与 ACID（原子性/隔离性、隔离级别与并发异常）、03-10 并发控制入门（2PL 与 MVCC 入门：快照读与可见性直觉，版本链/GC 实现即留本节展开） ｜ 一手锚点：PostgreSQL 16 官方文档（Ch.13 Concurrency Control：13.1 mvcc-intro、13.2 transaction-iso、24.1 routine-vacuuming、20.10 runtime-config-autovacuum、73.7 storage-hot）+ 本机 PostgreSQL 16.13 实证（`xmin/xmax/ctid`、`heap_page_items`、`VACUUM VERBOSE`、`pg_visibility`）；MySQL 8.0 官方文档（InnoDB Multi-Versioning、Consistent Nonlocking Reads）；交叉框架：Wu, Arulraj, Lin, Xian, Pavlo《An Empirical Evaluation of In-Memory Multi-Version Concurrency Control》VLDB 2017（版本存储/可见性/GC 的设计维度分类）、CMU 15-721/15-445 MVCC 讲 ｜ 成熟度：核心机制 GA/稳定；具体默认参数 ⚙演进快（随版本迭代，正文逐点标注、未亲自实测的默认值标「待核」）

> 一条主线心智模型：MVCC（多版本并发控制）用一句话概括就是——**写操作不覆盖旧数据，而是造一个新版本；每个读操作按自己开始时的"快照"去挑对它可见的那个版本**。于是"读永不阻塞写、写永不阻塞读"。但这句漂亮口号背后有三笔账要还，正好对应本报告四节：旧版本存哪儿、怎么存（04-9.1 版本存储）；读怎么在一堆版本里认出"属于我的那个"（04-9.2 可见性判定与快照）；旧版本堆积了谁来清、怎么清（04-9.3 版本清理与膨胀）；索引指向的是哪个版本、更新了要不要动索引（04-9.4 索引与版本交互）。全程用 PostgreSQL 与 InnoDB 两套真实实现并排讲——它们在每一笔账上都做了不同选择，对照着看最能看清 MVCC 的设计空间。

> 分账（本报告的边界）：本主题只讲**单机 MVCC 的机制实现**。跨节点/跨副本如何达成一致的快照、分布式事务下的时间戳与提交协议，属本track L6-04·04-10（分布式事务）与 L6-09（数据密集型应用一致性），本报告不展开。隔离级别与并发异常的**概念定义**属先修 03-9，本报告只讲"隔离级别如何落到快照取用时机"这一实现侧。

> 本机实证：本报告按「本机实证鼓励做」执行。基线环境已装 PostgreSQL 16.13 服务端，故 PostgreSQL 侧结论**全部附本机真实输出**（临时集群，跑完即清，不入库）；InnoDB 侧基线未装 MySQL 服务端、`.reference/` 下亦无源码副本，故 InnoDB 侧以 MySQL 8.0 官方文档为主承重、与 Wu 2017 分类交叉，未做实机 dump。凡未亲自实测的版本默认值显式标「待核」，不凭记忆填具体数字。

---

## 04-9.1 版本存储方案：append-only 堆（Postgres）vs delta/undo（InnoDB）

### 04-9.1.1 为什么 MVCC 必须保存多个版本

MVCC 的核心承诺是：一个正在运行的读事务，不应该看到别的事务在它开始之后才做的修改，也不应该因为别人在改数据就被迫等待。要同时做到这两点，唯一的办法就是——当一行被更新时，**不把旧内容扔掉**，而是让旧内容和新内容同时存在一段时间，好让那些"应该看到旧值"的读事务还能找到旧值。这些同时存在的同一逻辑行的不同内容，就叫一行的多个**版本**。

对初学者，关键是先建立"逻辑行 vs 物理版本"的区分。用户眼中 `id=2` 永远是一行；但在存储引擎内部，它可能对应磁盘上两三份物理记录（当前值、上一个值、再上一个值……）。哪一份对"你"可见，取决于你的事务开始得早还是晚。于是所有 MVCC 引擎都要回答同一个工程问题：这些旧版本**存在哪里、以什么形式存**。这就是"版本存储方案"，也是 PostgreSQL 与 InnoDB 分道扬镳的第一个岔路口。

### 04-9.1.2 版本存储的三类设计（append-only / time-travel / delta）

学界对版本存储方案有一个被广泛引用的三分法（Wu 等 2017 的 MVCC 实证综述给出）：**append-only 存储**（新版本和旧版本都作为完整元组存在同一个主表空间里，靠一条版本链把它们串起来）、**time-travel 存储**（主表只放最新版本，旧的完整版本被搬到一个单独的历史表）、**delta 存储**（主表放最新版本，旧版本不完整保存，只在一个单独的 undo/delta 空间里保存"改了哪些字段、改前是什么"，需要时靠这些增量把旧版本反推出来）。

理解这三类的差别，抓住两个维度就够了：**旧版本是"整份"还是"增量"**，以及**旧版本和最新版本放不放在一起**。append-only 是"整份 + 放一起"，delta 是"增量 + 分开放"。这个选择牵一发动全身——它决定了更新有多快、读旧版本有多贵、垃圾回收在哪儿做、以及表会以什么形式"膨胀"。PostgreSQL 走的是 append-only 路线，InnoDB 走的是 delta 路线，正好是两个极端，下面两节分别看。

### 04-9.1.3 PostgreSQL：堆内 append-only，新版本另起一条元组

PostgreSQL 把每一行的每个版本都作为一条独立的**堆元组（heap tuple）**存在同一张表的数据页里，不区分主表和历史表。每条元组头部带两个关键的事务 ID 字段：`xmin`（**创建**这条元组的事务 ID）和 `xmax`（**删除/使其失效**的事务 ID，若尚未被删则为 0）。更新一行时，PostgreSQL 不去改旧元组的数据，而是**新写一条元组**作为新版本，并把**旧元组的 `xmax` 填上当前事务 ID**，表示"旧版本到我这里为止就失效了"。

本机实证最能说清这件事。先建表插三行，观察系统列：

```
$ psql -X    # PostgreSQL 16.13, 基线 @2026-07-25
CREATE TABLE t (id int primary key, v text) WITH (autovacuum_enabled=off);
INSERT INTO t VALUES (1,'a'),(2,'b'),(3,'c');
SELECT xmin, xmax, ctid, id, v FROM t ORDER BY id;

 xmin | xmax | ctid  | id | v
------+------+-------+----+---
  733 |    0 | (0,1) |  1 | a
  733 |    0 | (0,2) |  2 | b
  733 |    0 | (0,3) |  3 | c
```

三行都由事务 733 创建（`xmin=733`），都还活着（`xmax=0`）。`ctid` 是物理地址 `(页号, 页内槽位号)`。现在更新 `id=2`，再看物理页里的所有元组：

```
UPDATE t SET v='b2' WHERE id=2;
SELECT lp, t_ctid, t_xmin, t_xmax FROM heap_page_items(get_raw_page('t',0));

 lp | t_ctid | t_xmin | t_xmax
----+--------+--------+--------
  1 | (0,1)  |    733 |      0
  2 | (0,4)  |    733 |    734    <- id=2 的旧版本：xmax 被填成 734（更新它的事务），t_ctid 指向 (0,4)
  3 | (0,3)  |    733 |      0
  4 | (0,4)  |    734 |      0    <- id=2 的新版本：由事务 734 创建，活着
```

这段输出就是 append-only 版本链的全貌：`id=2` 现在物理上有**两条**元组，槽位 2（旧）和槽位 4（新）。旧元组的 `t_xmax=734` 说明"它被 734 干掉了"，它的 `t_ctid=(0,4)` 是一根指向新版本的"前进指针"，把同一逻辑行的版本串成链。新元组 `t_xmin=734` 说明它是 734 的产物。初学者最容易忽略的一点：`DELETE` 在 PostgreSQL 里根本不删数据，只是给目标元组填上 `xmax`；`UPDATE` 则等于"给旧元组填 `xmax` + 插一条新元组"。数据的物理消失要等到 04-9.3 的 VACUUM。

### 04-9.1.4 InnoDB：聚簇索引原地更新 + undo 段保存回滚增量

InnoDB 的选择相反。它把最新版本**原地（in-place）更新**写在聚簇索引（主键索引，即数据本身）里，旧版本不完整保留，而是把"改前的样子怎么恢复"记成一条 **undo log 记录**，存进一个专门的 **rollback segment（回滚段，位于 undo 表空间）**。每条聚簇索引记录带三个隐藏系统列：`DB_TRX_ID`（6 字节，最近插入或更新该行的事务 ID）、`DB_ROLL_PTR`（7 字节，**回滚指针**，指向该行对应的 undo log 记录）、以及在没有用户主键时才出现的 `DB_ROW_ID`（6 字节，自增行号）。

版本链在 InnoDB 里是这样串的：当前记录的 `DB_ROLL_PTR` 指向一条 undo 记录，那条 undo 记录里存着"上一个版本相对当前版本的差异"，据此可反推出上一版本；上一版本又带自己的回滚指针指向更早的 undo……顺着回滚指针一路回溯，就能重建任意早的历史版本。这里初学者要抓住和 PostgreSQL 的本质差别：InnoDB 主表里**永远只有一份最新数据**（读最新值最快、不用扫版本链），历史是"欠着的"，要用时才靠 undo 现场重建。

InnoDB 还把 undo 分成两类，寿命不同：**insert undo log**（记录 INSERT 的回滚信息）只在事务回滚时有用，事务一提交就能丢；**update undo log**（记录 UPDATE/DELETE 的回滚信息）还要供别的读事务重建旧版本用，因此**必须活到"再没有任何快照可能需要它重建旧版本"为止**才能丢——这正是 04-9.3 purge 的清理对象，也是"长事务撑爆 undo 表空间"的根源。

### 04-9.1.5 两方案对照：代价挪到了不同地方

把两者摆在一起看，会发现 MVCC 没有免费午餐，两种存储只是把成本挪到了不同环节。可用下面这张对照抓住要点（每一格都是"同一件事，两种做法"）：

```
维度              PostgreSQL（append-only 堆）        InnoDB（delta/undo）
------------      ------------------------------      -----------------------------
旧版本存在哪       和新版本一起，就在主表数据页里       主表只存最新版；旧版本在 undo 段
旧版本形态         完整元组                            增量（怎么回滚回去）
更新代价           另写整条新元组 + 改旧元组 xmax        原地改 + 写一条 undo
读"最新版本"        可能要沿版本链跳过失效元组           直接读主表，最快
读"历史版本"        沿 ctid/版本链找到可见的那条          沿回滚指针 + undo 现场重建
垃圾回收            VACUUM 清死元组（04-9.3）            purge 线程丢弃 update undo（04-9.3）
"膨胀"的形态        表本身变大（死元组占页）             undo 表空间变大 + 二级索引 delete-mark 堆积
```

初学者常见的误解是"哪个更好"。答案是各有代价：PostgreSQL 更新便宜、回滚便宜（旧版本现成），但主表会因死元组膨胀、且读要跳过失效版本；InnoDB 读最新值最快、主表紧凑，但每次读旧版本要靠 undo 重建、且长事务会让 undo 无法回收。这不是优劣，是权衡取向的不同。后面每一节都会不断回到这张对照表。

#### 来源与时效
- PostgreSQL 16 官方文档 · 13.1 Introduction (mvcc-intro) 与 73.5 Database Page Layout / 73.6 Heap tuple 结构（`xmin`/`xmax`/`t_ctid` 语义），核实 2026-08-01：https://www.postgresql.org/docs/16/mvcc-intro.html
- 本机实证：PostgreSQL 16.13（Ubuntu 24.04），`heap_page_items` + `get_raw_page`（pageinspect 扩展）观察 UPDATE 前后堆元组，`xmax`/`t_ctid` 版本链输出如正文，核实 2026-08-01。
- MySQL 8.0 官方文档 · InnoDB Multi-Versioning：`DB_TRX_ID`(6B)/`DB_ROLL_PTR`(7B)/`DB_ROW_ID`(6B) 三隐藏列、insert vs update undo 生命周期，核实 2026-08-01：https://dev.mysql.com/doc/refman/8.0/en/innodb-multi-versioning.html
- Wu, Arulraj, Lin, Xian, Pavlo《An Empirical Evaluation of In-Memory Multi-Version Concurrency Control》VLDB 2017 — 版本存储三分类（append-only / time-travel / delta）框架来源，核实 2026-08-01。
- 术语差异说明：PostgreSQL 文档称 append-only 堆内多版本，未用"delta"一词；InnoDB 文档称 undo/rollback segment，未用"append-only"。Wu 2017 的三分法是统一学术口径，本报告用它作对照骨架，两边各自术语并记，不混用。

## 04-9.2 可见性判定与快照：xid、commit timestamp 与可见性规则

### 04-9.2.1 事务 ID（xid）与提交状态

MVCC 判定可见性的第一块基石是**事务 ID**：每个会真正写数据的事务被分配一个单调递增的整数 ID（PostgreSQL 叫 `xid`，32 位；InnoDB 叫 transaction id，写进每行的 `DB_TRX_ID`）。有了它，"这个版本是谁造的、谁删的"就变成了整数比较。但光有 ID 不够，还要知道那个 ID 的事务**最终是提交了还是回滚了**——一个版本只有在其创建事务已提交时才可能对别人可见。PostgreSQL 把每个事务的提交/回滚状态存在 `pg_xact`（旧称 clog）里；InnoDB 则用活跃事务列表间接表达。

一个初学者常忽略、但实现上很重要的细节是：**只读事务通常不消耗事务 ID**。事务 ID 是稀缺资源（32 位会用尽，见 04-9.3.4），所以引擎"能省则省"——只有当事务第一次要写数据时才给它真正分配一个 xid。本机实证可以直接看到这个"惰性分配"：

```
-- 事务里先只读，再写
SELECT pg_current_xact_id_if_assigned();   -- 只读时：返回空（还没分配 xid）
BEGIN;
  SELECT pg_current_xact_id();             -- 强制分配：735
  DELETE FROM t WHERE id=3;
  -- 看被删元组：t_xmax 被填成 735（删除者），元组仍在物理页上
  --  lp | t_xmin | t_xmax
  --   3 |    733 |    735
ROLLBACK;
```

`DELETE` 也只是把目标元组的 `t_xmax` 填成删除者的 xid（735），物理数据仍在；若事务回滚，735 在 `pg_xact` 里被标为 aborted，那么"735 删除了这条"这一动作就自动失效、旧值继续可见——回滚在 MVCC 里几乎是"免费"的，因为什么都没真正改掉。

### 04-9.2.2 快照是什么：一次"当前世界谁已提交"的定格

**快照（snapshot）**是可见性判定的第二块基石。它是对"在某个瞬间，哪些事务已经提交"的一次定格。有了快照，一条读语句就能对任意版本回答"这个版本的创建/删除事务，在我这个瞬间算已提交吗"。PostgreSQL 的快照可粗略理解为三元组 `xmin:xmax:xip_list`——`xmin` 是当时最老的仍活跃事务（比它更早的都已结束），`xmax` 是"下一个将分配的 xid"（≥ 它的都属未来、不可见），`xip_list` 是这两者之间那些"拍快照瞬间仍在运行、尚未提交"的事务 ID 列表。本机实证里，一个只读快照长这样：

```
SELECT pg_current_snapshot();   -->  735:735:
```

`735:735:` 表示 xmin=735、xmax=735、活跃列表为空——即"735 之前的都结束了，735 及以后都还没提交"。InnoDB 的对应概念叫 **read view**，本质相同：它记下"创建 read view 瞬间的活跃事务集合"以及活跃事务 ID 的上下界，用来判断某个 `DB_TRX_ID` 相对本次读算不算"已提交且在我之前"。

对初学者，最好的直觉是：快照就是你戴上的一副"时光眼镜"，它把世界冻结在你拍照那一刻——之后别人再提交多少改动，透过这副眼镜都看不到。可见性判定 = 拿每个物理版本的"生卒事务 ID"去和这副眼镜的定格状态比对。

### 04-9.2.3 PostgreSQL 的可见性规则

给定一条读语句持有的快照，PostgreSQL 判定一条堆元组是否可见，核心是同时满足两条：**创建它的事务（`xmin`）对本快照算"已提交且在我之前"**，且**删除它的事务（`xmax`）对本快照算"尚未提交 / 不存在 / 是未来"**。用一句话：

```
可见  ⇔  xmin 已提交且在快照中可见  且  (xmax 为空 或 xmax 未提交/在快照中不可见)
```

也就是"这个版本已经被某个我能看到的事务造出来了，且还没被任何我能看到的事务删掉"。举例：回到 04-9.1.3 里 `id=2` 的两条元组——旧元组 `xmin=733, xmax=734`，新元组 `xmin=734, xmax=0`。一个在 733 之后、734 提交之前就拍了快照的老读事务，会认为"734 尚未提交"：于是旧元组（造它的 733 可见、删它的 734 不可见）对它可见，新元组（造它的 734 不可见）对它不可见——它看到的是 `v='b'`。而一个在 734 提交之后才拍快照的新事务，则反过来看到 `v='b2'`。同一份物理数据，两副眼镜看到不同的行，这就是 MVCC 快照隔离的全部魔法。

一个实现上的加速细节值得初学者知道：判定 `xmin`/`xmax` 是否已提交要查 `pg_xact`，频繁查很贵，所以 PostgreSQL 在元组头里放了 **hint bits（提示位）**——第一次判定后把"已知提交/已知回滚"的结论缓存在元组上，之后就不必再查 `pg_xact`。这也是为什么"第一次读某些刚写入的数据反而稍慢"（要设置 hint bits 并回写页）。

### 04-9.2.4 InnoDB 的可见性规则：read view + undo 重建

InnoDB 因为主表只存最新版本，可见性判定和"重建旧版本"是连在一起做的。扫描聚簇索引读到一条记录时，拿它的 `DB_TRX_ID` 和当前 read view 比：若该事务在 read view 眼里算"已提交且在我之前"，这条最新记录就是可见版本，直接用；否则说明"这条最新版本是我不该看到的人改的"，于是顺着 `DB_ROLL_PTR` 找到 undo 记录，回滚出上一个版本，再拿上一版本的 `DB_TRX_ID` 重复判定——一路回溯，直到找到第一个对本 read view 可见的版本为止。

和 PostgreSQL 对照来看差别很清楚：PostgreSQL 是"版本都在，挑一个可见的"，InnoDB 是"只有最新版，不可见就现场倒推"。倒推的代价随"你落后当前多远"增长——一个开了很久的老快照要重建很旧的版本，可能得顺着很长的 undo 链回溯，这是 InnoDB 侧长事务变慢的直接原因之一。

### 04-9.2.5 隔离级别如何落到"何时拍快照"

MVCC 的隔离级别差异，在实现上主要归结为**多久换一副新眼镜（重新拍快照）**。两大引擎在这点上高度一致：

在 **Read Committed（读已提交）** 下，事务里**每条语句各自拍一次新快照**——所以同一事务里两次相同查询之间，若别人提交了改动，第二次能看到（这就是"不可重复读"被允许的实现根源）。在 **Repeatable Read（可重复读）** 下，**整个事务共用一副在事务第一次读时拍下的快照**——所以事务内反复查看到的永远一致。PostgreSQL 里 Serializable 与 RR 用同一副事务级快照，只是额外叠加 SSI（可串行化快照隔离，用读写依赖检测）来消除快照隔离下仍可能出现的串行化异常。PostgreSQL 还有个规范-实现分账：SQL 标准的 Read Uncommitted 在 PostgreSQL 里被直接当作 Read Committed 处理（MVCC 架构下没有"读未提交"的合理落法），而 PostgreSQL 的 RR 禁止幻读、强于标准要求。

InnoDB 的默认隔离级别是 Repeatable Read（同一事务内所有一致性读复用首次读建立的 read view）；Read Committed 则每条一致性读各建新 read view——与 PostgreSQL 的语句级/事务级快照取用时机完全同构。这个"隔离级别 = 快照粒度"的对应关系，是把抽象隔离级别落到实现的关键一跃，初学者务必建立。

以上说的都是 MVCC 的**一致性（快照）读**。带 `SELECT ... FOR UPDATE`/`FOR SHARE` 的**锁定读**、以及 UPDATE/DELETE 的写，走的是当前读 + 行锁路径，不完全由快照决定，那属先修 03-9/03-10 的锁并发控制范畴，本节不展开。

#### 来源与时效
- PostgreSQL 16 官方文档 · 13.1 mvcc-intro（"each SQL statement sees a snapshot… reading never blocks writing and writing never blocks reading" 原文）与 13.2 transaction-iso（RC 每语句拍快照、RR/SER 事务起始拍快照、Read Uncommitted 按 Read Committed 处理），核实 2026-08-01：https://www.postgresql.org/docs/16/transaction-iso.html
- 本机实证：PostgreSQL 16.13，`pg_current_xact_id_if_assigned()`（只读不分配 xid）、`pg_current_xact_id()`（强制分配 735）、`pg_current_snapshot()` 返回 `735:735:`、DELETE 后 `t_xmax=735`，输出如正文，核实 2026-08-01。
- MySQL 8.0 官方文档 · Consistent Nonlocking Reads（RR 复用首次读快照、RC 每读建新快照）与 InnoDB Multi-Versioning（沿 `DB_ROLL_PTR` + undo 重建可见版本），核实 2026-08-01：https://dev.mysql.com/doc/refman/8.0/en/innodb-consistent-read.html
- hint bits 属 PostgreSQL 实现细节，主由源码 `HeapTupleSatisfies*`/`tqual.c` 承载；本报告仅作机制方向陈述，具体位布局未逐一实测标「待核」。
- 冲突/分歧：PostgreSQL RR 禁止幻读（强于 SQL 标准允许幻读的 RR），InnoDB RR 通过一致性读+间隙锁在多数场景避免幻读但语义细节不同——两引擎"RR"名同实略异，选型时不可想当然，两边定位如上文各自文档。

## 04-9.3 版本清理：vacuum / purge、GC 与表膨胀

### 04-9.3.1 为什么 MVCC 一定要有垃圾回收

MVCC 用"不覆盖、留旧版本"换来了读写不互相阻塞，但代价是旧版本会不断累积。一条被更新 100 次的行，可能留下上百个历史版本。一旦某个旧版本**再也没有任何仍活着的快照可能需要它**，它就是纯粹的垃圾（dead tuple / 可丢弃的 undo），必须回收，否则空间无限增长、扫描越来越慢。这个后台回收机制在 PostgreSQL 叫 **VACUUM**，在 InnoDB 叫 **purge**。它们是 MVCC 这笔账的"还款环节"，也是运维里最常出问题的地方。

判断"能否回收"的准绳是同一个：一个旧版本只有当**所有仍在运行的事务的快照都不再需要它**时才能删。因此**最老的活跃事务**成了那道"回收水位线"——凡是这条水位线之后才失效的版本都还得留着。这直接推出一个重要后果：一个开着不提交的长事务，会把水位线死死钉在过去，使得这期间产生的所有垃圾**谁都清不掉**。这是 PostgreSQL 与 InnoDB 共有的头号 MVCC 运维陷阱。

### 04-9.3.2 PostgreSQL VACUUM：清死元组，回收进页内空闲空间

PostgreSQL 的 VACUUM 扫描表，找出那些"`xmax` 已提交且早于回收水位线"的死元组，把它们占的空间标记为**页内可复用**（供该表后续 INSERT/UPDATE 填新元组），并清理指向它们的索引项。关键性质：普通 VACUUM **不把空间还给操作系统**（除非表尾恰好整页空了），它只是"就地腾格子"。要把物理文件真正缩小，得用 **VACUUM FULL**——它重写整张表到一个无空洞的新文件，但代价是全程持有表级 `ACCESS EXCLUSIVE` 锁（阻塞一切读写），不能与业务并发。

本机实证完整走一遍。承接 04-9.1.3 更新后（`id=2` 有一条死元组），先看统计和可见性图，再 VACUUM：

```
SELECT n_tup_upd, n_tup_hot_upd, n_dead_tup FROM pg_stat_user_tables WHERE relname='t';
 n_tup_upd | n_tup_hot_upd | n_dead_tup
-----------+---------------+------------
         1 |             1 |          1      <- 1 条死元组待清

VACUUM (VERBOSE) t;
INFO:  finished vacuuming "postgres.public.t": index scans: 0
       tuples: 1 removed, 3 remain, 0 are dead but not yet removable
       removable cutoff: 735, ...

-- VACUUM 后：pg_visibility 里该页变为 all-visible（可见性图置位）
SELECT count(*) FROM pg_visibility_map('t') WHERE all_visible;  -->  1   （VACUUM 前为 0）
```

`1 removed, 3 remain` 就是回收了那条死元组、留下 3 条活元组。`0 are dead but not yet removable` 是关键健康指标：若这里数字很大，说明有死元组因回收水位线（老快照/长事务）被卡住清不掉。VACUUM 顺带维护**可见性图（visibility map）**：把"整页全是所有事务都可见的活元组"的页打上 all-visible 位——实证里该页在 VACUUM 后从 0 变 1。这块位图是 04-9.4.3 index-only scan 能成立的前提。

### 04-9.3.3 autovacuum：什么时候自动触发

手动 VACUUM 不现实，PostgreSQL 靠 **autovacuum** 后台守护进程自动触发。一张表积累的死元组超过阈值就排队清理，阈值公式为：

```
触发阈值 = autovacuum_vacuum_threshold + autovacuum_vacuum_scale_factor × 表估算行数
```

PostgreSQL 16 的默认值（本次于官方文档核实）：`autovacuum_vacuum_threshold = 50`（基数 50 行）、`autovacuum_vacuum_scale_factor = 0.2`（外加表 20%）、`autovacuum_naptime = 1min`（守护进程每分钟醒一次）、`autovacuum_max_workers = 3`（最多 3 个并发清理 worker）。对应的统计信息（供优化器用）由 ANALYZE 维护，阈值默认 `autovacuum_analyze_threshold = 50` + `autovacuum_analyze_scale_factor = 0.1`（表 10%）。⚙演进快·锚 PG16：这些默认值随大版本会调整（例如大表按比例触发过晚的问题促使后续版本讨论调低 scale factor），跨版本部署时应以目标版本文档为准。

初学者要理解 scale_factor 的含义与陷阱：0.2 意味着"表越大、要攒越多死元组才触发一次清理"。对亿级大表，20% 是巨量死元组，等到触发时膨胀已经很重——所以大表常被单独调低 scale_factor 或调低 threshold。这是 PostgreSQL MVCC 运维最常见的调参点之一。

### 04-9.3.4 冻结（freeze）与事务 ID 回卷（wraparound）

PostgreSQL 的事务 ID 是 **32 位**，约 42 亿个，且用**模 2³²的环形比较**：对任一 xid，都有约 20 亿个 xid 算"更老"、约 20 亿个算"更新"。问题来了：一个非常老的元组，其 `xmin`（比如 100）在几十亿个事务之后，会被环形比较误判成"来自未来、不可见"——数据凭空消失，这就是灾难性的**事务 ID 回卷（wraparound）**。

防御手段是 **freeze（冻结）**：VACUUM 把足够老的、且对所有人都可见的元组的 `xmin` 打上一个特殊的"冻结"标记，从此该元组被视为"无限久远、永远可见"，不再参与环形比较，也就不会被回卷误伤。为此 PostgreSQL 强制"每张表至少每 20 亿事务被 VACUUM 冻结一次"，并在 `relfrozenxid`（表内最老未冻结 xid）年龄超过 `autovacuum_freeze_max_age`（PG16 默认 2 亿事务，本次官方文档核实）时触发**强制的 aggressive/anti-wraparound VACUUM**——它即使 autovacuum 被关也会来，且很难被打断。本机 VACUUM VERBOSE 输出里的 `new relfrozenxid` 就是这条冻结水位线在推进的证据。

如果冻结长期跟不上（往往还是因为长事务或 autovacuum 被压制），XID 年龄逼近 20 亿时数据库会强制进入只读保护、拒绝新写，是生产事故的经典成因。相关的其他冻结参数（如 `vacuum_freeze_min_age`、`vacuum_freeze_table_age` 的具体默认值）本次未逐一实测，标「待核」，部署以目标版本文档为准。

### 04-9.3.5 InnoDB purge：丢弃 update undo、清 delete-marked 记录

InnoDB 侧的 GC 叫 **purge**，由专门的 purge 线程做。它清两类东西：一是**不再被任何快照需要的 update undo log**（回收 undo 空间）；二是被标记删除（delete-marked）的记录的**物理删除**。因为 InnoDB 的 DELETE 也不立即物理删行——它只在行上置一个删除标记，真正的物理清除推迟到 purge：当"再没有任何 read view 可能需要看到这条被删记录的旧版本"时，purge 才把该聚簇索引记录及其对应的二级索引记录真正删掉。官方文档明说这个删除"很快，通常和当初那条 DELETE 语句同数量级耗时"。

和 PostgreSQL 的对照很有教益：VACUUM 清的是"主表里的死元组"，purge 清的是"undo 段里的旧增量 + delete-marked 记录"——因为两者旧版本存的地方不同（04-9.1），垃圾的形态和清理对象也就不同。但共有的死穴一样：**长事务/未提交的旧 read view 会把 purge 卡住**，update undo 无法丢弃，undo 表空间持续膨胀。InnoDB 提供 `innodb_max_purge_lag` 等旋钮在 purge 落后时对新写限速，但根治办法还是"及时提交事务、别开长事务"。

### 04-9.3.6 表/索引膨胀（bloat）：MVCC 的空间税

**膨胀（bloat）**指表或索引因死版本堆积而占用远超实际活数据所需的空间。它是 MVCC "不覆盖"哲学收取的空间税，在两引擎表现形态不同：PostgreSQL 表现为**堆表本身变大**（死元组占着页，普通 VACUUM 只回收进页内空闲、不还给 OS，除非 VACUUM FULL 重写）；InnoDB 表现为 **undo 表空间增长 + 二级索引里 delete-marked 记录堆积**。

对初学者，把膨胀的因果链记牢就够用了：写得多/更新频繁 → 产生大量旧版本 → 若清理（VACUUM/purge）**跟得上**，膨胀受控；若清理**跟不上**（清理速度不足，或更常见地被长事务/老快照钉住回收水位线），旧版本清不掉 → 表/索引/undo 膨胀 → 扫描变慢、缓存命中率下降 → 更慢。所以 MVCC 数据库的头号健康守则永远是同一句：**别开长事务、及时提交**（包括只读事务也要提交，否则 InnoDB 的 update undo 无法回收、PostgreSQL 的回收水位线无法前进）。

#### 来源与时效
- PostgreSQL 16 官方文档 · 24.1 Routine Vacuuming（VACUUM 回收进页内空闲、VACUUM FULL 重写并需 ACCESS EXCLUSIVE 锁、32 位 XID 环形比较与"至少每 20 亿事务冻结一次"、aggressive vacuum），核实 2026-08-01：https://www.postgresql.org/docs/16/routine-vacuuming.html
- PostgreSQL 16 官方文档 · 20.10 Automatic Vacuuming — 默认值 `autovacuum_vacuum_threshold=50`、`autovacuum_vacuum_scale_factor=0.2`、`autovacuum_analyze_threshold=50`、`autovacuum_analyze_scale_factor=0.1`、`autovacuum_freeze_max_age=200000000`、`autovacuum_naptime=1min`、`autovacuum_max_workers=3`，均本次核实 2026-08-01：https://www.postgresql.org/docs/16/runtime-config-autovacuum.html
- 本机实证：PostgreSQL 16.13，`VACUUM (VERBOSE)` 输出（`1 removed, 3 remain`、`new relfrozenxid` 推进）、`pg_stat_user_tables`（`n_dead_tup`）、`pg_visibility_map` all-visible 由 0→1，输出如正文，核实 2026-08-01。
- MySQL 8.0 官方文档 · InnoDB Multi-Versioning（purge 丢弃 update undo、delete-marked 记录物理清除、purge lag 与 `innodb_max_purge_lag`、长事务撑大 undo 表空间），核实 2026-08-01。
- 待核项：`vacuum_freeze_min_age`、`vacuum_freeze_table_age` 具体默认值本次未逐一实测；InnoDB `innodb_purge_threads` 等默认值未实测——均标「待核」，以目标版本文档为准。⚙演进快·锚 PG16 / MySQL8.0：默认参数随版本调整。

## 04-9.4 索引与版本交互：HOT 更新与二级索引可见性回查

### 04-9.4.1 索引指向什么：两种截然不同的设计

要理解"更新一行时索引要不要跟着改",先要弄清索引项**指向什么**——这是 PostgreSQL 与 InnoDB 又一处根本分歧。PostgreSQL 的**所有**索引（含主键索引）都是**次级（secondary）风格**：索引项存的是索引键 + 该元组的**物理地址 ctid**，直接指向堆里那条元组。InnoDB 则是**聚簇索引组织表**：主键索引的叶子就是数据行本身；而**二级索引**叶子存的是"索引键 + 主键值"（不是物理地址），要拿完整行还得用主键值回聚簇索引再查一次（回表）。

这个差别决定了更新时的索引维护代价，也决定了 MVCC 下的可见性回查怎么做。PostgreSQL 因为索引直指物理元组，而每个新版本是一条新物理元组（新 ctid），所以"每造一个新版本，原则上每个索引都要新增一条指向新 ctid 的项"——这很贵，于是有了 04-9.4.2 的 HOT 优化来豁免。InnoDB 二级索引指主键值（更新非索引列时主键不变、二级索引项无需变），但带来了 04-9.4.4 的"二级索引可见性回查"问题。下面分别讲。

### 04-9.4.2 PostgreSQL HOT 更新：不动索引的原地新版本

**HOT（Heap-Only Tuple，仅堆元组）**是 PostgreSQL 针对"频繁更新非索引列"的关键优化。当一次 UPDATE 同时满足两个条件——**没有修改任何被索引引用的列**，且**旧元组所在页上有足够空间容纳新版本**——PostgreSQL 就走 HOT 路径：新版本仍写在**同一页**里，且**不给任何索引新增项**。既然索引键没变，索引项可以继续指向旧元组的槽位；旧槽位被改造成一个**重定向行指针（redirect line pointer，LP_REDIRECT）**，把索引访问转发到同页的新版本。

本机实证正好落实了这一点。04-9.1.3 那次 `UPDATE t SET v='b2'`——`v` 未被索引（只有 `id` 上有主键索引），且同页有空间，因此它是一次 HOT 更新：

```
-- 统计确认是 HOT 更新
 n_tup_upd | n_tup_hot_upd
-----------+---------------
         1 |             1        <- 1 次更新，1 次走 HOT

-- VACUUM 后同一页的行指针状态（lp_flags: 1=NORMAL, 2=REDIRECT, 0=UNUSED, 3=DEAD）
SELECT lp, lp_flags, t_ctid, t_xmin, t_xmax FROM heap_page_items(get_raw_page('t',0));
 lp | lp_flags | t_ctid | t_xmin | t_xmax
----+----------+--------+--------+--------
  1 |        1 | (0,1)  |    733 |      0
  2 |        2 |        |        |          <- 旧槽位变成 REDIRECT，指针转发到新版本
  3 |        1 | (0,3)  |    733 |      0
  4 |        1 | (0,4)  |    734 |      0    <- HOT 链上的当前版本
```

槽位 2 的 `lp_flags=2`（REDIRECT）就是那个重定向指针——主键索引里"id=2"那一项仍指向槽位 2，访问时被转发到槽位 4 的新版本，索引项自始至终没动过。HOT 还带来一个额外好处：这类"仅堆内"的旧版本可以在**普通 SELECT 顺带的页内 pruning** 中被清理，不必等完整 VACUUM（因为没有任何索引项引用它们的槽位）。实践上常用降低表的 `fillfactor`（预留页内空闲空间）来提高 HOT 命中率。

### 04-9.4.3 非 HOT 更新与 index-only scan 的可见性配合

一旦更新**改了被索引的列**、或**当前页放不下新版本**，就退化为**非 HOT 更新**：新版本可能落到别的页，且每个索引都要新增一条指向新版本的项。这时索引会随更新持续增长，也更依赖 VACUUM 来清理指向死元组的过期索引项——这是"更新索引列很贵"的实现根源，也是设计 schema 时"少给频繁更新的列建索引"这条经验的由来。

一个由此衍生、初学者常困惑的点是 **index-only scan（仅索引扫描）与可见性的关系**。因为 PostgreSQL 索引项里**不含事务可见性信息**（`xmin/xmax` 只在堆元组里），理论上哪怕查询要的列全在索引里，也得回堆看一眼"这个版本对我可见吗"，回堆就抵消了 index-only 的意义。解法正是 04-9.3.2 提到的**可见性图（visibility map）**：若索引项指向的页在 vismap 里被标为 all-visible（整页对所有事务可见），就可以跳过回堆、直接用索引里的值——这才让 index-only scan 真正省 I/O。所以 index-only scan 的效果强烈依赖 VACUUM 是否及时把页标成 all-visible，这把 04-9.3 的清理和 04-9.4 的索引访问紧紧绑在了一起。

### 04-9.4.4 InnoDB 二级索引的可见性回查

InnoDB 的二级索引记录**不像聚簇索引那样带隐藏系统列、也不原地更新版本**。当某个二级索引列被更新时，做法是：把**旧二级索引记录打上删除标记（delete-mark）**、**插入一条新记录**，被删标记的记录留待 purge 清理。这就带来 MVCC 下的一个特殊判定问题：扫二级索引读到一条记录时，它**本身没有事务信息**，怎么知道这条对当前 read view 可见不可见？

InnoDB 的规则（本次于官方文档核实）：如果这条二级索引记录**没有被 delete-mark、且它所在的二级索引页没有被更晚的事务改过**，就可以认为它稳定可见，直接用（这也是覆盖索引/covering index 能生效、无需回表的前提）。反之——**记录被 delete-mark，或该二级索引页被某个更新的事务改过**——就不能只信二级索引，必须**回聚簇索引查这条记录，检查聚簇记录的 `DB_TRX_ID`，必要时顺 undo 重建出对本次读可见的正确版本**。官方文档原话：此时"覆盖索引技术不被使用，InnoDB 转而去聚簇索引里查记录"。

对初学者，抓两个后果就够：其一，这是"覆盖索引在读写混合负载下有时并不省事"的底层原因——一旦目标二级索引页近期被改动，本以为纯走索引的查询会退化成大量回表 + undo 重建；其二，purge 落后（04-9.3.5）会让 delete-marked 的二级索引记录长期滞留，进一步抬高"必须回查聚簇索引"的比例，读性能因此变差。这再次把索引行为和 GC 的健康度绑在一起。

### 04-9.4.5 两引擎索引-版本交互的总对照

把本节收束成一张对照，帮初学者建立完整心智模型（同一件事、两种实现）：

```
问题                     PostgreSQL                          InnoDB
--------------------     ------------------------------      ------------------------------
索引项指向什么            物理地址 ctid（直指堆元组）           二级索引指主键值（需回表取行）
更新非索引列              HOT：同页新版本 + 索引不变            主键不变、二级索引不变；聚簇索引原地更新+undo
更新索引列                非 HOT：每索引新增项、可能跨页         旧二级索引记录 delete-mark + 插新记录
读时的可见性判定          堆元组自带 xmin/xmax，就地判定         二级索引无事务信息，可疑则回聚簇索引 + undo 判定
覆盖/仅索引扫描的门槛      需该页在 visibility map 标 all-visible  需记录未 delete-mark 且页未被更新事务改过
和 GC 的耦合             vismap 由 VACUUM 维护，影响 index-only  delete-mark 由 purge 清，滞后则回表增多
```

一句话总结全报告的两条设计哲学：**PostgreSQL 把多版本摊在堆里、索引直指版本，于是靠 HOT 省索引写、靠 VACUUM + vismap 收尾；InnoDB 把最新版留在聚簇索引、历史欠在 undo 里，于是二级索引靠 delete-mark + 回聚簇索引判定、靠 purge 收尾。** 没有哪个更优，只是把 MVCC 那笔"读写不互斥"的账，还在了不同的地方。

#### 来源与时效
- PostgreSQL 16 官方文档 · 73.7 Heap-Only Tuples (HOT) — 两条件原文（"does not modify any columns referenced by the table's indexes… not including summarizing indexes" 与 "sufficient free space on the page"）、redirect line pointer、无需新增索引项、SELECT 顺带页内清理、`fillfactor` 提升 HOT 命中，核实 2026-08-01：https://www.postgresql.org/docs/16/storage-hot.html
- 本机实证：PostgreSQL 16.13，`pg_stat_user_tables.n_tup_hot_upd=1` 确认 HOT、`heap_page_items` 显示旧槽位 `lp_flags=2`(LP_REDIRECT)、HOT 链当前版本在同页，输出如正文，核实 2026-08-01。
- PostgreSQL 16 官方文档 · index-only scan 与 visibility map 的配合（索引不含可见性信息，需页级 all-visible 方可免回堆），交叉 24.1 routine-vacuuming 的 vismap 维护，核实 2026-08-01。
- MySQL 8.0 官方文档 · InnoDB Multi-Versioning「Multi-Versioning and Secondary Indexes」小节 — 二级索引不带隐藏系统列/不原地更新版本、更新走 delete-mark+插新、被 delete-mark 或页被更新事务改动时回聚簇索引查 `DB_TRX_ID`+undo、覆盖索引此时失效，全部本次核实 2026-08-01：https://dev.mysql.com/doc/refman/8.0/en/innodb-multi-versioning.html
- 待核/分歧：InnoDB 判定"二级索引页是否被更新事务改过"的页级 max trx id 具体字段实现属源码细节，本次未取源码副本核实，标「待核」，仅按官方文档的行为描述陈述。
