# L4-03·大主题03-10 并发控制

> 基线 Py3.11.15/np2.4.6/gcc13.3 @2026-07-25 ｜ 核实日期：2026-07-29 ｜ 先修：L4-03·03-9 事务与 ACID（可串行化、冲突可串行化、优先图、隔离级别谱、脏读/不可重复读/幻读/写偏斜）、03-5 物理存储（页/块）、03-6 索引与 B+树；L4 操作系统方向的死锁四条件（互斥/占有并等待/不可抢占/循环等待）｜ 一手锚点：《Database System Concepts》7th ed.（Silberschatz/Korth/Sudarshan, 2019，简称 DBSC 7e）ch18「Concurrency Control」；《Database Management Systems》3rd ed.（Ramakrishnan/Gehrke, 2003，简称 R&G）ch17；CMU 15-445 Spring 2026 L18「Two-Phase Locking」、L19「Timestamp Ordering / OCC」、L20「Multi-Version Concurrency Control」、L21「Concurrency Control Recap」（schedule 已核 2026-07-29，讲次编号随学期变，标 ⚙）；PostgreSQL 16 官方文档 ch13「Concurrency Control」、MySQL 8.0 InnoDB Locking 文档、SQLite「File Locking And Concurrency」文档 ｜ 成熟度：GA/稳定（2PL/时间戳/OCC 为 1970–80 年代经典结论；MVCC/SSI 为现行主流实现，具体引擎行为标注版本）

> 粒度判定：**1 份，不拆**。本大主题 5 个小主题（03-10.1–03-10.5）围绕同一问题——「多个事务并发跑时，如何在保证可串行化（03-9 定的正确性标准）的同时尽量放行并发」——展开的四大类方法（锁 / 时间戳 / 乐观 / 多版本）加上锁方法附带的死锁处理与多粒度扩展，环环相扣。按 report-format v3 §一「默认 1 大主题 = 1 报告」，展开后篇幅可控，不产 `-a/-b`。

> 边界申明：本报告只到**单机、入门级**。MVCC 的**实现内幕**——版本链的物理存储（append-only 堆 vs undo/delta）、可见性判定的 xid/commit-timestamp 细则、版本清理（VACUUM/purge、GC、表膨胀）、HOT 更新与二级索引回查——属内核级主题，留 **L6-04 §04-9「MVCC 实现内幕」**，本报告在 03-10.5 只讲「快照读 + 可见性」的直觉并显式点到为止。分布式并发/2PC 留 L6-04 §04-10 与 L6-09。

> 实机实证状态：**已取**。本环境自带 PostgreSQL 16.13 服务端二进制（`/usr/lib/postgresql/16/bin`），无常驻服务，故临时 `initdb` 起了一个本地 cluster（以 `postgres` 系统用户运行，`unix_socket` 于 `/tmp/pgs`、端口 5433、`trust` 认证），跑完即弃。下文的 `pg_locks` 观测、**服务端自动死锁检测与 abort**、REPEATABLE READ 快照、SERIALIZABLE 写偏斜 SSI abort 均为**真实机器输出**，环境串同抬头。SQLite 侧无 `sqlite3` CLI，故 SQLite 的「库级锁、基本不产生行级死锁」为**文档级说明**，未贴机器输出，如实标注。所有协议语义（wait-die/wound-wait、时间戳规则、OCC 三阶段）来自 DBSC 7e / R&G 一手核对，非凭记忆。

本报告有一条贯穿始终的心智模型，**03-9 已经定好了「正确」的标准——一个并发调度只要等价于某个串行调度（冲突可串行化）就算对；并发控制（concurrency control）就是运行时强制这条标准的那套机制。** 强制的办法历史上分成四大流派：**加锁**（悲观，先抢资源、冲突就等，2PL 保证可串行化，代价是死锁与阻塞，03-10.1–03-10.3）；**时间戳排序**（给每个事务发一个先后号，用号数决定谁该先谁该后，冲突就回滚，03-10.4）；**乐观并发控制 OCC**（先埋头干、提交前才验证有没有冲突，赌冲突少，03-10.4）；**多版本 MVCC**（写不覆盖旧值而是造新版本，让读永远读到一个一致快照、读写互不阻塞，是今天几乎所有主流数据库的默认底座，03-10.5）。四条线的共同评判尺子都是 03-9 的可串行化，差别只在「悲观还是乐观、单版本还是多版本」这两个维度上如何取舍。

---

## 03-10.1 基于锁的并发控制

### 03-10.1.1 共享锁、排他锁与锁相容矩阵

基于锁的协议要求事务在访问一个数据项前先申请该项上的锁，锁分两种模式：**共享锁（shared lock，记 S）**允许读，**排他锁（exclusive lock，记 X）**允许读也允许写。多个事务可以同时持有同一数据项的 S 锁（读读不冲突），但 X 锁与任何锁都互斥。事务向并发控制管理器（lock manager）请求锁，只有当请求的模式与该项上**已被其他事务持有的所有锁都相容**时才立即授予，否则请求方阻塞等待。相容关系用**锁相容矩阵（lock compatibility matrix）**表示：

```
请求\已持有     S      X
    S           ✓      ✗
    X           ✗      ✗
```

给初学者的直觉：S 锁像图书馆里「一本书可以很多人同时看」，X 锁像「有人要在书上写字，别人既不能看也不能同时写」。为什么读读相容、其余都不相容？因为并发控制的正确性尺子是冲突可串行化（03-9），而两个操作「冲突」当且仅当它们访问同一数据项且至少一个是写——读读不冲突所以可以并行，只要沾上写就有冲突、必须排队。一个易错点：光有 S/X 锁**并不足以**保证可串行化，还必须约束「什么时候能加锁、什么时候能放锁」，那就是下面的两阶段锁。此外真实系统里加锁前通常还有一步：读整表要先在**表级**打个意向标记（03-10.3 的意向锁），本节先只看单个数据项。

### 03-10.1.2 两阶段锁协议（2PL）

**两阶段锁协议（Two-Phase Locking, 2PL）**规定每个事务的加锁/放锁分成不可逆的两个阶段：**增长阶段（growing phase）**只能申请锁、不能释放任何锁；**收缩阶段（shrinking phase）**只能释放锁、不能再申请任何锁。一旦事务第一次释放锁，它就进入收缩阶段，此后再也不能加锁。事务持锁最多的那一刻叫**加锁点（lock point）**。2PL 的核心定理是：

若所有事务都遵守 2PL，则产生的任何调度都是**冲突可串行化**的（等价的串行次序由各事务加锁点的先后决定）。

给初学者的直觉与为什么。冲突可串行化要求「冲突操作的相对顺序在所有涉及的数据项上一致」，2PL 之所以能保证这点，是因为「先扩张后收缩」使得如果事务 T1 在某项上的锁先于 T2、那么 T1 的加锁点一定先于 T2 的加锁点，于是全局能排出一个一致的串行序。一个关键澄清：**2PL 保证可串行化，但不保证无死锁**——两个都遵守 2PL 的事务照样能互相等对方的锁而死锁（03-10.2）。另一个易错点：基本 2PL 允许在收缩阶段提交之前就释放锁，这会带来**级联回滚（cascading rollback）**风险——T1 放了 X 锁、T2 读到 T1 未提交的值，若 T1 随后 abort，T2 也得跟着回滚。为堵这个漏洞才有了下面的严格版本。

### 03-10.1.3 严格 2PL 与强严格 2PL（SS2PL）

为消除级联回滚，工业界普遍采用 2PL 的加强版。**严格两阶段锁（strict 2PL）**在基本 2PL 之上要求：事务持有的所有**排他锁（X 锁）**必须保持到事务**提交或回滚之后**才释放。**强严格两阶段锁（strong strict 2PL / rigorous 2PL，记 SS2PL）**更进一步：所有锁（S 和 X 都算）都保持到事务结束才释放。

给初学者的落点。严格 2PL 的效果是：任何事务写过的数据在它提交前对别人都上着 X 锁、别人读不到脏值，于是调度不但可串行化，还是**无级联的（cascadeless）**、可恢复的（recoverable）——03-9 讲的可恢复性/无级联性在这里被锁机制自动满足。SS2PL 连读锁也留到最后，实现更简单（提交时一把全放），且它产生的串行序恰好等于**提交顺序**，这正是绝大多数商业锁式引擎的默认协议。三者关系记成一条包含链：

SS2PL ⊂ 严格 2PL ⊂ 2PL（越往右约束越松、并发度越高，但越往左越不容易出级联回滚等问题）

一个常见混淆：SQL 标准的「SERIALIZABLE 隔离级别」历史上常由 SS2PL 实现，但今天很多引擎（PostgreSQL、Oracle）用 MVCC/SSI 而非纯锁来达到同等或近似效果（03-10.5），所以「SERIALIZABLE 级别」与「用 2PL 实现」不是一回事——规范讲效果，实现各有各的路子。

### 03-10.1.4 锁的实现：锁表、锁升级与阻塞的真实样貌

锁本身不写进数据页，而是由 lock manager 维护在内存里的一张**锁表（lock table）**中：以数据项为键，挂一条持有者与等待者的队列。事务请求锁时查表，相容就登记为持有、不相容就挂进等待队列并阻塞。**锁升级（lock upgrade）**指事务先持 S 锁、后来要写而把它升级成 X 锁；**锁降级**反之。粒度上，行级锁并发高但锁数量多、开销大，于是有**锁升级（lock escalation）**：当一个事务在某表上的行锁太多时，引擎可能把它们合并成一把表级锁以省内存（如 SQL Server/DB2；PostgreSQL 不做这种自动升级，改用 MVCC 避开）。

实机观测（PostgreSQL 16.13，环境串同抬头）。让一个事务 `BEGIN; UPDATE acct SET bal=bal-1 WHERE id=1;` 后 sleep，另一会话查 `pg_locks` 看到它持有的锁：

```
   locktype    |    rel    |       mode       | granted
---------------+-----------+------------------+---------
 relation      | acct_pkey | RowExclusiveLock | t
 relation      | acct      | RowExclusiveLock | t
 transactionid |           | ExclusiveLock    | t
```

给初学者的解读（这里有个著名的命名陷阱）。PostgreSQL 里出现在 `pg_locks` 的 `RowExclusiveLock` **是一个表级（relation）锁模式**，名字里的「Row」意思是「我打算改这张表里的某些行」，它不是行锁本身。真正的**行锁**在 PostgreSQL 里不进锁表、而是记在被改元组的头部（借 `xmax` 字段，属 MVCC，03-10.5 / L6-04）。每个活动事务还会在自己的事务号（`transactionid`）上持一把 `ExclusiveLock`——这是「等某事务结束」的机制：别人要等它就去申请它的 xid 的 ShareLock，从而阻塞到它提交/回滚。记住这个实现细节能解释下一节死锁报错里为什么写「waits for ShareLock on transaction 731」。

#### 来源与时效（本小主题末集中列）
- DBSC 7e ch18 §18.1「Lock-Based Protocols」——S/X 锁、相容矩阵、2PL 及其可串行化定理、strict/rigorous 2PL、锁升级、锁表实现（一手，核实 2026-07-29）。
- R&G 3e ch17 §17.1–17.4「2PL, Strict 2PL, Lock Management」——同一套 2PL 与严格 2PL 定义，交叉核对（一手）。
- CMU 15-445 Spring 2026 L18「Two-Phase Locking」——2PL/strict 2PL、加锁点、级联回滚动机（一手课程材料，schedule 核实 2026-07-29，讲次编号 ⚙随学期变）。
- PostgreSQL 16 文档「Explicit Locking / pg_locks」——表级锁模式含 RowExclusiveLock 的确切语义、行锁记于元组而非锁表（一手实现，核实 2026-07-29）。
- 实机：本地 PostgreSQL 16.13 cluster `pg_locks` 输出（真实机器输出，环境串同抬头）。
- 冲突/差异：命名上 DBSC/R&G 的「行级 X 锁」在 PostgreSQL 里名义锁模式与实现位置都不同（RowExclusiveLock 实为表级意向性锁、真行锁在元组头）——规范语义与具体实现分账，两边都记。

## 03-10.2 死锁

### 03-10.2.1 死锁的定义与循环等待

**死锁（deadlock）**指一组事务里每个都在等待该组中另一个持有的锁，形成环路，谁也走不了。它满足经典的四个必要条件（互斥、占有并等待、不可抢占、循环等待），其中**循环等待**是并发控制里最直接的抓手：把「事务 Ti 在等 Tj 持有的锁」画成有向边 Ti → Tj，得到**等待图（wait-for graph）**，则

系统存在死锁 ⟺ 等待图中存在环。

给初学者的直觉与最小例子。经典死锁：T1 先锁住行 A、T2 先锁住行 B，然后 T1 要锁 B（被 T2 挡住）、T2 要锁 A（被 T1 挡住），等待图是 T1 → T2 → T1，成环。为什么 2PL 会死锁？因为 2PL 只管「先扩张后收缩」、不管加锁**顺序**，两个事务用相反顺序抓锁就撞上了。处理死锁有三条路线：**预防**（设计协议让环根本不可能出现，03-10.2.2）、**检测+恢复**（放任发生，定期查环、发现就杀一个，03-10.2.3）、**超时**（等太久就假定死锁、直接放弃，03-10.2.4）。

### 03-10.2.2 死锁预防：wait-die 与 wound-wait

两种基于**时间戳**的预防方案给每个事务一个启动时刻的时间戳 TS（越小越「老」，老事务优先级高），当事务 Ti 请求的数据项正被 Tj 持有时，按下表决定 Ti 是等待还是有人回滚：

**wait-die（非抢占）**——只有老的能等，年轻的一律自杀：

```
Ti 请求 Tj 持有的项：
  若 TS(Ti) < TS(Tj)   （Ti 更老）  →  Ti 等待
  否则                 （Ti 更年轻）→  Ti 回滚（die）
```

**wound-wait（抢占）**——老的可以抢，年轻的只能等：

```
Ti 请求 Tj 持有的项：
  若 TS(Ti) < TS(Tj)   （Ti 更老）  →  Tj 回滚（被 Ti wound）
  否则                 （Ti 更年轻）→  Ti 等待
```

给初学者的直觉与为什么无死锁。两个方案都让「等待边只朝一个时间方向走」：wait-die 里只有老事务等年轻事务（边总是 老→年轻），wound-wait 里只有年轻事务等老事务（边总是 年轻→老）。方向单一就不可能成环，故无死锁。为什么用**回滚重启时保留原时间戳**这条规则？为了防饿死——一个事务被回滚后带着原来的（老）时间戳重来，越等越老、优先级越高，迟早轮到它。一个高频易错点：两个名字容易记反，抓住「die = 自己死、wound = 去伤别人」并配「wait-die 非抢占、wound-wait 抢占」。代价方面，两者都可能**回滚本可正常完成的事务**（保守），所以实际系统多不用纯预防，而用下面的检测。

### 03-10.2.3 死锁检测：等待图与环检测

**检测法**不阻止死锁发生，而是让系统维护等待图、周期性地（或每次有事务等待超过阈值时）跑一次**环检测**，发现环就选一个**牺牲者（victim）**回滚以打破环。选牺牲者要权衡：已做工作最少的、持锁最少的、回滚代价最小的，并要防同一个事务反复被选中而饿死。

实机观测（PostgreSQL 16.13，真实机器输出）。两会话构造相反顺序的更新触发死锁：会话 A `UPDATE id=1` 后 sleep 再 `UPDATE id=2`；会话 B `UPDATE id=2` 后 sleep 再 `UPDATE id=1`。服务端在 `deadlock_timeout`（默认 **1s**）后检测到环并自动 abort 一方：

```
ERROR:  deadlock detected
DETAIL:  Process 31767 waits for ShareLock on transaction 732; blocked by process 31768.
         Process 31768 waits for ShareLock on transaction 731; blocked by process 31767.
CONTEXT: while updating tuple (0,2) in relation "acct"
ROLLBACK          -- 会话 A 被选为牺牲者、自动回滚
COMMIT            -- 会话 B 正常提交
```

给初学者的解读。报错里的「waits for ShareLock on transaction 731/732」正是 03-10.1.4 讲的机制——每个事务在自己 xid 上持 ExclusiveLock，等它的一方去申请该 xid 的 ShareLock 从而阻塞，两条 wait 语句互为反向即等待图成环。PostgreSQL 不是每次冲突都立刻查环（那太贵），而是先让请求方**等 `deadlock_timeout`**，超时才启动环检测——因为大多数等待很快就自然解开，真死锁是少数。注意牺牲者由服务端自动选定、被回滚方收到 error（应用需捕获并重试），另一方照常提交，数据不受损。

### 03-10.2.4 超时法

**超时法（timeout）**是最简单的死锁处理：事务等锁超过某个时限就假定卷入死锁、直接回滚自己，不去真的构建/检查等待图。优点是实现极简、无需维护全局等待图（分布式场景尤其省事）；缺点是时限难调——太短会误杀根本没死锁、只是等得久的事务，太长则死锁存活太久拖垮吞吐，且无法精确挑「代价最小的牺牲者」。

给初学者的落点。可以把三种方法排成一条「多聪明 vs 多省事」的光谱：预防（wait-die/wound-wait）最保守、宁可错杀；检测（等待图）最精确、但要维护图和跑环检测；超时最省事、但最粗糙。MySQL InnoDB 默认用等待图检测（可用 `innodb_deadlock_detect` 关掉转而纯靠 `innodb_lock_wait_timeout` 超时）；PostgreSQL 用「超时触发的检测」混合法（`deadlock_timeout` 后查图）。这说明现实系统常把「超时」当成「何时去查环」的触发器，而非独立的检测手段。

### 03-10.2.5 规范 vs 实现：PostgreSQL 自动 abort vs SQLite 库级锁

不同引擎对死锁的暴露面差别很大，取决于它锁的**粒度**。PostgreSQL / MySQL InnoDB 支持**行级锁**，因此会出现行级死锁，需要上面那套检测+自动 abort（实机已证 PostgreSQL 抛 `deadlock detected` 并回滚牺牲者）。SQLite 走另一条极端：它是嵌入式库、并发模型是**整库一把锁**（rollback-journal 模式下写事务持库级 EXCLUSIVE 锁，WAL 模式下多读一写），根本没有行级锁，所以**基本不产生传统意义的行级死锁**；SQLite 更常见的是「database is locked」的 `SQLITE_BUSY`——一个写者拿不到库锁而超时，本质是并发被串行化、而非环状死锁（不过应用层若两连接各开事务再互相升级写锁，仍可能撞上 `SQLITE_BUSY` 型互等）。

给初学者的落点。这是「规范 vs 实现分账」的典型：死锁是不是问题，取决于引擎选了多细的锁。粒度越细并发越高、但越容易出行级死锁（要检测机制兜底）；粒度粗到「整库一把锁」就几乎没死锁、但并发度低到几乎串行。SQLite 侧的说明为文档级（本环境无 `sqlite3` CLI 未贴机器输出），PostgreSQL 侧为实机实证。

#### 来源与时效（本小主题末集中列）
- DBSC 7e ch18 §18.2「Deadlock Handling」——等待图定义、wait-die/wound-wait 语义与保留时间戳防饿死、检测与牺牲者选择、超时（一手，核实 2026-07-29；wait-die/wound-wait 语义已逐条核对）。
- R&G 3e ch17 §17.2「Deadlocks — Detection and Prevention」——同一套定义，交叉核对（一手）。
- CMU 15-445 Spring 2026 L18「Two-Phase Locking」死锁部分——detection vs prevention、victim selection（一手课程材料，schedule 核实 2026-07-29，讲次 ⚙随学期变）。
- PostgreSQL 16 文档「Concurrency Control / Deadlocks」＋ `deadlock_timeout` 参数（默认 1s，实机确认）——超时触发检测、自动 abort 牺牲者（一手实现）。
- MySQL 8.0「InnoDB Deadlock Detection」、SQLite「File Locking And Concurrency」——InnoDB 等待图检测 + `innodb_lock_wait_timeout`；SQLite 库级锁与 `SQLITE_BUSY`（一手实现文档，SQLite 侧未贴机器输出）。
- 实机：本地 PostgreSQL 16.13 cluster 触发的真实 `deadlock detected` 报错与牺牲者回滚（真实机器输出）。
- 冲突/差异：死锁处理策略随引擎而异——PostgreSQL/InnoDB 有行级死锁需检测自动 abort，SQLite 库级锁基本不产生行级死锁而表现为 `SQLITE_BUSY`；两边都记、点明成因是锁粒度不同。

## 03-10.3 多粒度锁与幻读防护

### 03-10.3.1 多粒度锁与锁的层次

只在单个「数据项」上加锁太死板：读整张表若逐行加 S 锁会有海量锁开销，直接加一把表级锁又太粗。**多粒度锁（multiple granularity locking）**把数据库对象组织成一棵层次树——数据库 → 表 → 页 → 元组（行），允许事务按需在**任意粒度**上加锁：改一行就锁那一行，扫全表就锁整张表。为让不同粒度的锁互相协调（比如别人锁了一行，我要锁整张表时必须知道「这表里有行被锁着」），引入了意向锁。

给初学者的直觉。想象这棵树，如果 T1 锁了表 R 里的某一行（细粒度），此时 T2 想给整张表 R 加 X 锁（粗粒度），系统怎么快速发现冲突？总不能去遍历表里每一行看有没有锁。办法是：T1 在锁那一行之前，先在它的所有祖先节点（表 R、数据库）上打一个「意向」标记，说「我下面有东西上锁了」；T2 想锁整表时先看表节点上的意向标记就知道不能锁。这就是下一节的意向锁。

### 03-10.3.2 意向锁 IS / IX / SIX 与扩展相容矩阵

**意向锁（intention lock）**加在粗粒度节点上，声明「我打算在其下的更细粒度上加某种锁」。三种：**IS（intention-shared）**表示意图在下层加 S 锁；**IX（intention-exclusive）**表示意图在下层加 X 锁；**SIX（shared and intention-exclusive）**表示对本节点加 S 锁、同时意图在下层加 X 锁（例：整表读一遍但只改其中少数行）。规则是：给某节点加 S/X 之前，必须先在其所有**祖先**上加 IS/IX。五种模式的相容矩阵为：

```
请求\已持有    IS     IX     S      SIX    X
    IS         ✓      ✓      ✓      ✓      ✗
    IX         ✓      ✓      ✗      ✗      ✗
    S          ✓      ✗      ✓      ✗      ✗
    SIX        ✓      ✗      ✗      ✗      ✗
    X          ✗      ✗      ✗      ✗      ✗
```

给初学者的读法。看两条关键相容关系就懂设计意图：IS 与 IX 相容（✓）——「我下面要读某些行」和「你下面要写另一些行」互不干扰，因为真正冲突要到具体那一行才判定，粗粒度只是意向。但 IX 与 S 不相容（✗）——你要读整张表（表级 S），而我打算改表里某些行（表级 IX），这俩必须排队，否则你可能读到我改一半的行。SIX 是「读全表 + 改少数」的组合，故只与 IS 相容（别人只能来读某些行）。加锁要**自顶向下**（先祖先意向、再子孙实锁），放锁要**自底向上**，这个方向不能反。

### 03-10.3.3 幻读与谓词锁

**幻读（phantom）**是并发控制里锁「行」锁不住的漏洞：事务 T1 按条件查了一批行（如 `WHERE age > 30`），T2 随后**插入**一行满足该条件的新行并提交，T1 再查同一条件时多出这行「幻影」。问题在于 T1 只对**已存在的行**加了锁，而 T2 插入的是一行**当时还不存在**的行，行锁无从锁起。要真正封住幻读，必须锁住「满足谓词的整个逻辑范围」，即**谓词锁（predicate lock）**——锁的不是具体行，而是一个条件，任何试图插入/更新到落入该条件的行都被挡住。

给初学者的落点。区分幻读与不可重复读（03-9）：不可重复读是**同一行**的值被别人改了，幻读是**行集合的成员**变了（多出/少掉行）。为什么幻读单独成一类？因为它恰好是「行级 2PL 也挡不住」的异常——2PL 保证已锁数据项的可串行化，但插入新行不触碰任何已锁项。纯粹的谓词锁理论上完美但实现昂贵（要对每个插入检查是否落入任意已注册谓词），故实际系统用近似手段（下节的间隙锁 / 谓词锁 / SSI）。SQL 标准的 SERIALIZABLE 级别要求消除幻读，REPEATABLE READ 在标准里则允许幻读（各引擎实际行为不同，见下节）。

### 03-10.3.4 间隙锁：InnoDB gap lock vs PostgreSQL 谓词锁/SSI

工业界用两条不同路线近似谓词锁。**MySQL InnoDB** 走**间隙锁（gap lock）与临键锁（next-key lock）**：在 REPEATABLE READ 下，索引扫描不仅锁命中的行，还锁住索引记录之间的「间隙」，使别的事务无法把新行插入这个间隙，从而在 InnoDB 的 RR 级别下就挡住幻读（临键锁 = 行锁 + 其前面的间隙锁）。**PostgreSQL** 不用间隙锁，而在 SERIALIZABLE 级别下用**谓词锁 + 可串行化快照隔离（SSI）**：它给事务读过的数据打「SIReadLock」谓词标记（记在 `pg_locks` 里 `mode=SIReadLock`），提交时检测读写依赖是否成环，成环就 abort 一方（见 03-10.5.4 的实机写偏斜例）。

给初学者的落点（这是又一处规范 vs 实现分账）。同样是「防幻读」，两家做法与生效级别都不同：InnoDB 在 REPEATABLE READ 就靠间隙锁挡住大部分幻读（悲观、加锁、可能锁到没插入意图的间隙而降并发）；PostgreSQL 的 REPEATABLE READ（= 快照隔离）**不**完全防幻读式的写偏斜，要到 SERIALIZABLE 才用 SSI 乐观地检测并 abort（不加间隙锁、并发高但可能提交时才失败要重试）。所以「同一个隔离级别名在两个引擎下的抗幻读能力不一样」，用哪个引擎、开哪个级别，得看它具体的实现文档，不能只认 SQL 标准的级别名。

#### 来源与时效（本小主题末集中列）
- DBSC 7e ch18 §18.3「Multiple Granularity」、§18.4「Insert Operations, Deletes, and Predicate Reads」（幻读与 index-locking/predicate locking）——层次锁、IS/IX/SIX、相容矩阵、幻读与谓词锁（一手，核实 2026-07-29）。
- R&G 3e ch17 §17.5「Specialized Locking Techniques — Multiple-Granularity & Dynamic Databases / Phantom」——同一套多粒度与幻读处理，交叉核对（一手）。
- CMU 15-445 Spring 2026 L18「Two-Phase Locking」多粒度/幻读部分——intention locks、phantom problem、index locking（一手课程材料，schedule 核实 2026-07-29，讲次 ⚙随学期变）。
- MySQL 8.0「InnoDB Locking — Gap Locks / Next-Key Locks」——RR 下间隙锁防幻读（一手实现）。
- PostgreSQL 16 文档「Transaction Isolation — Serializable / SSI」、`pg_locks` `SIReadLock`——谓词锁 + SSI 而非间隙锁（一手实现，核实 2026-07-29）。
- 冲突/差异：防幻读的实现两条路——InnoDB 用间隙锁（悲观、RR 生效）vs PostgreSQL 用谓词锁/SSI（乐观、SERIALIZABLE 生效）；同一隔离级别名下抗幻读能力不同，两边都记、点明。

## 03-10.4 时间戳排序与乐观并发控制

### 03-10.4.1 时间戳排序协议（基本 T/O）

**时间戳排序协议（timestamp-ordering, T/O）**不用锁，而在事务启动时发一个唯一递增的时间戳 TS(Ti)，并规定：所有冲突操作**必须按时间戳顺序**执行，等价的串行序就固定为时间戳序。为此每个数据项 Q 记两个时间戳：**W-timestamp(Q)**（成功写过 Q 的最大事务时间戳）和 **R-timestamp(Q)**（成功读过 Q 的最大事务时间戳）。事务 Ti 发操作时按规则检查，违反时间戳序就回滚：

```
Ti 读 read(Q)：
  若 TS(Ti) < W-timestamp(Q)   →  Ti 想读一个「未来」才该写的值：读被拒，Ti 回滚
  否则                         →  允许读，R-timestamp(Q) = max(R-timestamp(Q), TS(Ti))

Ti 写 write(Q)：
  若 TS(Ti) < R-timestamp(Q)   →  已有更「新」的事务读过 Q，Ti 这个旧写来晚了：回滚
  若 TS(Ti) < W-timestamp(Q)   →  已有更新的写覆盖：回滚（基本协议；Thomas 规则改为忽略，见下）
  否则                         →  允许写，W-timestamp(Q) = TS(Ti)
```

给初学者的直觉。核心是「谁先谁后由号数说了算，不由谁先跑到说了算」。若一个操作发现自己来晚了（时间戳比该项上已记录的读/写者还小，说明按序它本该更早发生但现在才到），就没救了、只能回滚重启（拿新的更大时间戳重来）。与锁的根本区别：T/O **不阻塞、不死锁**（没有等待就没有循环等待），代价换成了**回滚**——冲突多时回滚频繁、甚至可能反复饿死同一事务。此外基本 T/O 产生的调度**不一定可恢复**（可能读到未提交值），实用时要加提交依赖或缓写。

### 03-10.4.2 Thomas 写规则

**Thomas 写规则（Thomas' Write Rule）**是对基本 T/O 写规则的一个优化：当 `TS(Ti) < W-timestamp(Q)`（即已有更新的事务写过 Q）时，基本协议要回滚 Ti，而 Thomas 规则说——**直接忽略这个过时的写**（不写、也不回滚 Ti），因为这个旧值反正会被那个更新的写覆盖，写了也是白写。

给初学者的直觉与为什么安全。想象黑板上先写「x=5」（新事务写的），现在来了个旧事务想写「x=3」——既然按时间戳序 x=3 本该在 x=5 之前发生、而 x=5 已经盖过它，那 x=3 这一笔无论如何都不会被任何后续读看到，跳过它对最终结果无影响。它让 T/O 产生的调度类扩大到**视图可串行化**（超出冲突可串行化的一部分），是「过时写可安全丢弃」这一思想的经典体现（后来也用在 MVCC 与复制中）。易错点：只对**盲写（不先读就写）**的过时写能忽略，若该事务后面还要用自己的写结果就不能简单跳过。

### 03-10.4.3 乐观并发控制（OCC）的三阶段

**乐观并发控制（Optimistic Concurrency Control, OCC）**赌「冲突很少」，于是让事务先埋头干、到提交时才检查有没有踩别人。每个事务分三个阶段：

```
① 读阶段（Read Phase）：事务读所需数据，所有写只写到自己的私有工作区（本地副本），不碰共享数据库。
② 验证阶段（Validation Phase）：提交前做一次检查——我这段时间读/写的数据，有没有和其他并发事务冲突到破坏可串行化？
③ 写阶段（Write Phase）：验证通过，才把私有工作区的写批量刷进数据库；验证失败，则丢弃私有副本、回滚重来。
```

给初学者的直觉。OCC 像「先斩后奏」：不预先加锁（乐观地假设没人跟我抢），干完活到收银台（验证阶段）才结账，发现冲突就作废重来。与锁式（悲观：先抢锁再干）正好相反。适用场景是**冲突稀少**（如大量只读、或热点分散）——此时几乎不回滚，省掉了全程持锁的开销和死锁风险。不适用**高冲突**——验证频繁失败、大量事务干完活白干重来，代价比一开始就加锁还高。

### 03-10.4.4 OCC 验证阶段做什么检查

验证的目标是确认「按事务的验证/提交顺序，能排出一个等价的串行序」。给每个事务在进入验证阶段时发一个**验证时间戳 TS**，对任意一对事务 Ti、Tj（设 TS(Ti) < TS(Tj)，即 Ti 先验证），要求满足下面三条之一才让 Tj 通过：

```
(a) Ti 在 Tj 开始读阶段之前就已完成全部三阶段；或
(b) Ti 的写阶段在 Tj 开始其写阶段之前完成，且 Ti 写的数据集(write-set) 与 Tj 读的数据集(read-set) 不相交；或
(c) Ti 在 Tj 完成读阶段之前完成读阶段，且 Ti 的 write-set 与 Tj 的 read-set、write-set 都不相交。
```

给初学者的落点。不用背死这三条，抓住核心：验证就是查「先提交者写过的东西，有没有被后提交者读到旧版本」——即 **write-set ∩ read-set 是否相交**（读写冲突）。相交就说明 Tj 读的是过时数据、不可串行化，Tj 回滚。这叫**后向验证**（拿自己和已提交者比）。实现上要记录每个事务的 read-set / write-set，冲突时**回滚的是正在验证的那个（后来者）**，已提交者不受影响。OCC 是今天 MVCC + SSI（03-10.5.4）和许多内存数据库的思想源头。

### 03-10.4.5 悲观 vs 乐观：怎么选

把三大非 MVCC 方案排在「悲观 ↔ 乐观」轴上：**2PL 最悲观**（先加锁，冲突就阻塞等待，风险是死锁）；**T/O 居中**（不加锁但实时按时间戳检查，冲突即刻回滚）；**OCC 最乐观**（全程不检查，提交时一次性验证，冲突才回滚）。选择取决于冲突率：低冲突（多读、热点分散、短事务）用 OCC/MVCC 最划算，省掉持锁开销；高冲突（热点行、长事务、写密集）用 2PL 更稳，避免反复回滚白干。

给初学者的落点。一句话记忆：**悲观协议把成本花在「等待/持锁」上，乐观协议把成本花在「回滚重做」上**。现代主流数据库并不纯用某一种，而是以 **MVCC 为底座**（读走快照、天然不阻塞写）、写路径上再叠加 2PL（行锁）或 SSI（乐观验证）——即下一节。

#### 来源与时效（本小主题末集中列）
- DBSC 7e ch18 §18.5「Timestamp-Based Protocols」（含 Thomas' Write Rule）、§18.9「Validation-Based / Optimistic Protocols」——T/O 读写规则、R/W-timestamp、Thomas 规则、OCC 三阶段与验证条件（一手，核实 2026-07-29）。
- R&G 3e ch17 §17.6「Concurrency Control Without Locking — Optimistic & Timestamp CC」——OCC 三阶段与 T/O，交叉核对（一手）。
- CMU 15-445 Spring 2026 L19「Timestamp Ordering Concurrency Control / OCC」——basic T/O、OCC read/validation/write 三阶段（一手课程材料，schedule 核实 2026-07-29，讲次 ⚙随学期变）。
- Kung & Robinson 1981「On Optimistic Methods for Concurrency Control」（ACM TODS）——OCC 三阶段与后向/前向验证的原始出处（经典一手论文，交叉核对语义）。
- 冲突/差异：验证阶段有「后向验证（backward，与已提交者比）」与「前向验证（forward，与活动者比）」两种流派，DBSC 讲后向、Kung-Robinson 原文含两者——两边都记，本报告以后向为主线、点明存在前向变体。

## 03-10.5 单机 MVCC 入门

### 03-10.5.1 MVCC 基本思想：读不阻塞写、写不阻塞读

**多版本并发控制（Multi-Version Concurrency Control, MVCC）**的核心思想是：更新数据时**不覆盖旧值**，而是给该数据项造一个**新版本**，旧版本仍保留。这样一个「读」总能找到某个满足其一致性要求的旧版本来读，而不必等「写」放锁——于是**读不阻塞写、写不阻塞读**，只有写写之间才需要互斥。这是今天 PostgreSQL、MySQL InnoDB、Oracle、SQL Server（可选）等几乎所有主流 OLTP 引擎的默认并发底座。

给初学者的直觉。回想 2PL 的痛点：一个长事务在读一大批数据（持 S 锁），会把想改这些数据的写事务全堵住。MVCC 的破法是「读的和写的看不同版本的数据」——写者造新版本，读者仍读它启动时那一刻的旧版本，两不相干。代价是数据库里同时存着一个数据项的多个版本，需要**垃圾回收**旧版本（属实现内幕，见 03-10.5.5 边界）。注意 MVCC 主要优化**读写并发**，写写冲突仍要靠锁或验证解决（不是银弹）。

### 03-10.5.2 快照读与可见性直觉

MVCC 下每个事务（或每条语句）拿一个**快照（snapshot）**——数据库在某一时刻的一致状态视图。事务读数据时，看到的是「所有在我快照建立之前**已提交**的事务的写」，而在我快照之后开始或尚未提交的写对我**不可见**。判断一个版本是否可见，直觉上就是比对「造出这个版本的事务，在我拿快照那一刻是否已提交、且不晚于我」。

实机观测（PostgreSQL 16.13，真实机器输出）。让一个 REPEATABLE READ 事务读两次同一行、中间被另一会话改并提交：

```
BEGIN ISOLATION LEVEL REPEATABLE READ;
reader-first-read | 100      -- 第一次读，值 100
(此时另一会话 UPDATE ...=999 并 COMMIT)
reader-second-read| 100      -- 第二次读仍是 100：读的是事务启动时的快照，看不见并发提交
COMMIT;
-- 事务结束后新起一个读：999（已能看到那次提交）
```

给初学者的解读。REPEATABLE READ 事务两次读到相同的 100，正是「快照冻结在事务开始那一刻」的体现——并发提交的 999 落在快照之后，不可见。这也说明 PostgreSQL 的 REPEATABLE READ 实为**快照隔离（Snapshot Isolation, SI）**，快照在事务开始时取一次；而 READ COMMITTED 下每条语句取新快照（故新事务读到 999）。MVCC 让这个读**完全不加锁、不阻塞**任何并发写。可见性判定的底层字段（`xmin`/`xmax`/`ctid`，实机可查）与规则属实现内幕，边界见 03-10.5.5。

### 03-10.5.3 快照隔离与写偏斜异常

**快照隔离（SI）**给每个事务一个开始时刻的一致快照来读，写则用「首个提交者胜（first-committer-wins）」解决写写冲突（两个事务改同一行，先提交的赢、后者回滚）。SI 很强——能挡住脏读、不可重复读、以及大部分幻读——但它**不等于可串行化**，会漏一个著名异常：**写偏斜（write skew）**。写偏斜发生在两个事务各自读了一个重叠的数据集、各自根据读到的（旧快照）值去改**不同的**行，单独看都合法、合起来却破坏了一个跨行约束。

给初学者的最小例子。约束「任何时刻至少一名医生在岗」，Alice、Bob 都在岗。两个事务并发：事务 A 读到「在岗人数=2 ≥ 1，那我可以下班」于是把 Alice 设为不在岗；事务 B 同时读到「在岗=2」把 Bob 设为不在岗。两者各自的快照里在岗都还是 2、检查都通过，提交后却变成 0 人在岗——约束被破坏。关键：它们改的是**不同行**（Alice vs Bob），没有写写冲突，first-committer-wins 挡不住；但它们的读集与对方的写集有交叉，非可串行化。

### 03-10.5.4 SSI：可串行化快照隔离

**可串行化快照隔离（Serializable Snapshot Isolation, SSI）**在 SI 之上加一层运行时检测：追踪事务间的**读写依赖（rw-antidependency）**，当检测到可能破坏可串行化的「危险结构」（两条 rw 依赖首尾相接成环）时，abort 其中一个事务。它保留了 SI「读不阻塞、快照读」的高并发优点，只在真有可串行化风险时才回滚——是乐观思想（03-10.4）在 MVCC 上的落地，PostgreSQL 的 SERIALIZABLE 级别即用 SSI 实现。

实机观测（PostgreSQL 16.13，真实机器输出）。把上面的写偏斜例放到 SERIALIZABLE 下跑，两事务各查 `count(*) WHERE oncall`（都读到 2）再各设自己下班：

```
-- 两事务都读到 count=2
txn B:  UPDATE ... ; COMMIT     -- 先提交者成功
txn A:  ERROR:  could not serialize access due to read/write dependencies among transactions
```

给初学者的解读。SI 下这两个事务都会提交、酿成写偏斜；升到 SERIALIZABLE 后，PostgreSQL 的 SSI 检测到它们的读写依赖成环、乐观地 abort 掉后验证的一方（这里 A），报 `could not serialize access`。应用收到这个错误应**捕获并重试**——这正是乐观并发的典型代价（把成本花在偶发回滚上，换来平时读全程不加锁）。对照 03-10.3.4：InnoDB 靠间隙锁悲观防幻读，PostgreSQL 靠 SSI 乐观防写偏斜，两条路殊途同归。

### 03-10.5.5 边界：版本链存储与 GC 留 L6-04

本报告到「快照 + 可见性直觉」为止。MVCC 真正复杂的部分是**实现内幕**，本课不展开、显式下沉到 **L6-04 §04-9「MVCC 实现内幕」**：版本如何物理存储（PostgreSQL 的 append-only 堆——旧版本留在原表、`xmin`/`xmax` 记事务号；vs InnoDB/Oracle 的 undo-log/回滚段——旧版本另存、就地更新新值）；可见性判定的精确规则（活动事务快照、commit 时间戳、`xmin`/`xmax` 与快照的比较）；旧版本的**垃圾回收**（PostgreSQL `VACUUM`、InnoDB purge）与由此产生的**表膨胀**；索引与版本的交互（HOT 更新、二级索引可见性回查）。

给初学者的落点。为什么划这条线？因为「读快照、写造新版本」这个**直觉**足够解释 MVCC 为何读写不互相阻塞、以及 SI/SSI 的行为（本课目标）；而「版本存哪、怎么比 xid、何时回收」是引擎内核工程，跟具体实现强绑定、且需要先修存储与恢复（03-11）才讲得透，放内核课更合适。实机里 `SELECT xmin, xmax, ctid FROM ...` 能看到这些版本元数据（本环境已验证可查），但其含义与清理机制在 L6-04 才展开。

#### 来源与时效（本小主题末集中列）
- DBSC 7e ch18 §18.7「Multiversion Schemes」、§18.8（含 Snapshot Isolation 与 write skew）——MVCC 基本思想、多版本时间戳/快照、SI 与写偏斜（一手，核实 2026-07-29）。
- R&G 3e ch17「Multiversion Concurrency Control」——多版本方案，交叉核对（一手）。
- CMU 15-445 Spring 2026 L20「Multi-Version Concurrency Control」——MVCC 设计维度、快照、SI vs serializable（一手课程材料，schedule 核实 2026-07-29，讲次 ⚙随学期变）。
- PostgreSQL 16 文档 ch13「Concurrency Control — MVCC / Transaction Isolation」——RR=快照隔离、SERIALIZABLE=SSI、`could not serialize access` 语义、系统列 `xmin`/`xmax`/`ctid`（一手实现，核实 2026-07-29）。
- Berenson et al. 1995「A Critique of ANSI SQL Isolation Levels」——快照隔离与写偏斜的经典界定（交叉核对 SI/write-skew 定义）；Cahill/Röhm/Fekete 2008「Serializable Isolation for Snapshot Databases」——SSI 原始出处（PostgreSQL SSI 的理论来源）。
- 实机：本地 PostgreSQL 16.13 cluster 的 REPEATABLE READ 快照读（两读均 100）与 SERIALIZABLE 写偏斜 SSI abort（`could not serialize access`）真实机器输出（环境串同抬头）。
- 冲突/差异：隔离级别名与 MVCC 实现的对应随引擎而异——PostgreSQL 的 REPEATABLE READ 实为 SI、SERIALIZABLE 为 SSI，与 SQL 标准「RR 允许幻读、SERIALIZABLE 禁止」的字面定义并不逐字对应；两边都记、点明规范 vs 实现分账。
