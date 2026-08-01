# L6-04·大主题04-8 调度与多核并行执行

> 基线 Py3.11.15/np2.4.6/gcc13.3 @2026-07-25 ｜核实日期：2026-08-01 ｜先修：L6-04·04-4 向量化查询执行（vector/batch 批列模型）、L6-04·04-6 连接与聚合的内核实现（并行 hash join / 两阶段聚合 / 溢出）｜一手锚点：CMU 15-721 Fall 2025 L08 Query Scheduling & Coordination（讲序跨学期稳定，本份细节实取 Spring 2024 同名讲义 08-scheduling，https://www.cs.cmu.edu/~15721-f25/）＋ Leis, Boncz, Kemper, Neumann《Morsel-Driven Parallelism: A NUMA-Aware Query Evaluation Framework for the Many-Core Age》（SIGMOD 2014）＋ Wagner, Kohn, Neumann《Self-Tuning Query Scheduling for Analytical Workloads》（SIGMOD 2021，Umbra 调度）＋ DuckDB 官方文档与执行/内存管理博客 ｜成熟度：morsel-driven 并行框架 GA（2014 定型、被 HyPer/Umbra/DuckDB 等广泛采用、口径稳定）；各引擎具体的默认并行度 / morsel 大小 / 内存预算阈值 ⚙演进快·锚版本·随时变

本主题讲分析型数据库把一条查询"铺满"多核 CPU 时，执行层怎么切分工作、怎么把工作块分派给线程、怎么在意内存的 NUMA 拓扑、以及流水线之间怎么用背压和内存预算彼此协调。它承接前面两讲：向量化解决了"单个线程内每批数据算得快"，连接/聚合解决了"单个算子实现得快"，而本讲解决"几十上百个核心怎么协同把整条计划跑完、且没有核心闲着"。核心范式是 2014 年提出的 morsel-driven 并行——它已成为现代主存分析引擎的事实标准，可放心作定论；只有各引擎具体的并行度默认值、morsel 尺寸、内存预算阈值仍在演进，凡涉及具体默认值处都锚定来源版本或标「待核」。

这里要和第 5 组并发课划清界限：并发课讲的是通用的锁、原子操作、线程原语等"并行的地基"；本讲讲的是数据库执行层如何在这些地基上专门为查询执行设计调度器——两者可交叉引用，但本讲聚焦 DB 执行层的调度决策，不重复讲通用并发原语本身。

---

## 04-8.1 morsel-driven 并行模型：数据切片与弹性并行度

### 04-8.1.1 出发点：Volcano/exchange 的 plan-driven 并行为什么不再 scale

传统并行数据库沿用 Volcano 模型的并行方式：优化器在编译期就静态决定用几个线程，为每个线程复制一份相同的算子流水线，再用特殊的"交换算子"（exchange operator）在线程之间路由元组流。这叫 plan-driven（计划驱动）并行——并行度被"烤进"了查询计划里，运行时不再改变。

这种做法在几十上百核的机器上会撞上两堵墙。第一堵是负载均衡：优化器编译期就把工作切成固定份，可现代乱序 CPU 的实际执行速度、以及中间结果的真实大小都很难在编译期准确预测，一旦切得不均，快的线程干完只能干等最慢的那个（straggler，掉队者），核心利用率上不去。第二堵是上下文切换：exchange 算子在线程间搬运元组要额外的物化、排队、同步，线程数一多，这些开销和上下文切换就成了瓶颈。morsel-driven 并行就是为了绕开这两堵墙而提出的：不在编译期定死并行度，改成运行时细粒度地把小数据块动态发给空闲线程。

plan-driven 像开工前就把活按人头分死，谁手快也不能帮手慢的；morsel-driven 像把活拆成一小堆一小堆放在公共料筐里，谁干完手上这筐就自己去筐里再抓一筐，天然不会有人闲着。

### 04-8.1.2 morsel 是什么：定长数据切片与"流水线跑到尽头"

morsel（直译"一小口/一小块"）是把一大块输入数据横向切开后得到的、大小恒定的小数据块。morsel-driven 执行的基本单位就是"一个流水线 + 一个 morsel"：一个工作线程拿到这样一个任务后，让这一个 morsel 的数据一路穿过整条算子流水线，直到遇到下一个"流水线断点"（pipeline breaker）为止，把结果物化下来，再回去领下一个 morsel。

```
一张大表（输入）横向切成许多定长 morsel：
[ morsel_0 ][ morsel_1 ][ morsel_2 ] ... [ morsel_n ]
   每个 morsel 约 100,000 元组（HyPer 论文取值）

一个 morsel 沿整条流水线跑到断点：
morsel_i --> [Scan] -> [Filter] -> [Probe HT] -> 写入下一个 pipeline breaker 的存储区
（多个线程各拿一个 morsel，同一条流水线上并行跑）
```

Leis 等人在 HyPer 里给出的经验取值是每个 morsel 约 100,000 个元组，实验表明这个量级在"弹性调整的即时性、负载均衡、维护开销"三者之间取得了好的折中。要理解 morsel 为什么这样设计，关键在"定长"和"小"两个词：定长让所有工作块彼此可替换、方便任意线程接手（工作窃取的前提）；小让抢占（preemption）可以在 morsel 边界上自然发生——一个线程只要干完当前 morsel 就能被重新指派去干别的活，不需要任何昂贵的中断机制，这就是弹性调度的物理基础。

### 04-8.1.3 弹性并行度（elasticity）：并行度不写进计划、运行时可变

弹性是 morsel-driven 区别于 Volcano 的最核心特征：一条查询用几个线程跑不是编译期定死的，而是运行时由调度器随时调整的，甚至可以在同一段流水线执行到一半时改变。

因为工作是"一次一个 morsel"地分派出去的，调度器随时可以决定把某个核心从这条查询撤走、去干别的查询。这带来两个直接好处。其一是完美的负载均衡与抗倾斜：即便中间结果大小分布未知、即便某些 CPU 核心莫名跑得慢，所有参与同一流水线的线程也能"冲线照（photo finish）"般几乎同时完工——最坏也只差一个 morsel 的处理时间。其二是查询间的资源再分配：一条跑了很久的长查询 Q_l 可以在任意阶段被优雅降并行度，把核心让给刚进来的、更该快速响应的交互式查询 Q_+；等 Q_+ 跑完，核心又摆回给长查询。这种"随时可改并行度"的能力，是 plan-driven 模型做不到的。

论文实现里所有查询同优先级、线程在活跃查询间均分，基于优先级的调度组件当时还在开发中、超出该论文范围；更成熟的优先级调度见 04-8.2.6 的 Umbra 方案。

### 04-8.1.4 morsel 大小的权衡：不是越小越好，也不必贴合缓存

morsel 大小是一个可调参数，它的选择原则和向量化里的 vector 大小完全不同：morsel 不需要装进 CPU 缓存，因为 morsel 只是"切分工作以便窃取和抢占"的调度单位，一个 morsel 放不进缓存并不会有性能惩罚（这一点论文明确对比了 Vectorwise / IBM BLU 那种"用向量在算子间传数据"的模型——那种向量才需要考虑缓存）。

论文用 `select min(a) from R` 这条极简查询在 64 线程上做了压力测试（这条查询几乎不做计算，专门压榨工作窃取数据结构）。结论是：

```
morsel 太小  ->  频繁访问全局工作窃取结构 -> 调度开销吃掉收益
morsel 太大  ->  抢占/窃取粒度变粗 -> 弹性变差、负载均衡变差
经验取值    ->  取"开销可忽略的最小值"，该实验里约 > 10,000 元组即可
             最优值随硬件而变，可实验确定；HyPer 主线用约 100,000
```

初学者常把 morsel 和 vector 混为一谈（见下一节），进而误以为 morsel 越小越贴缓存越好。实际恰相反：morsel 只要"大到足够摊薄调度开销"就行，再大就损失弹性。这个"取开销可忽略的最小值"是理解 morsel 尺寸的关键直觉。

### 04-8.1.5 morsel 与 vector/batch 不是一回事

morsel 是调度层的数据切片单位（约十万行量级），vector/batch 是向量化执行层算子内部一次处理的一小批数据（千行量级）。二者是嵌套关系而非同一物：一个线程领到一个 morsel 后，在这个 morsel 内部仍按 vector 为单位喂给向量化算子逐批计算。

```
morsel（调度单位，~100,000 行） 内部再切成若干 vector：
[ morsel_i ] = [vec][vec][vec] ... [vec]
                每个 vector 千行量级（引擎相关：Vectorwise 约 1024；DuckDB 默认 2048）
调度器只认 morsel 边界；向量化算子只认 vector 边界
```

之所以要两级切分：morsel 服务于"跨线程分派与窃取"，要够大以摊薄调度开销；vector 服务于"单线程内摊薄解释开销、贴合缓存与 SIMD"，要够小以装进缓存。DuckDB 默认 vector 大小为 2048（引擎特定值，随版本可变），它同样采用 morsel-driven 并行，其 morsel 尺寸也可调、调大会减少访问工作窃取结构的次数。把这两级记清楚，后面所有调度讨论才不会串味。

#### 来源与时效
- Leis et al.《Morsel-Driven Parallelism》（SIGMOD 2014）§1–§3、§3.3 及 Figure 6：morsel ≈ 100,000 元组的折中、morsel 大小实验（> 10,000 开销可忽略、不需贴缓存、最优随硬件）、弹性与 photo-finish 负载均衡、priority 调度当时未实现。核实 2026-08-01。
- CMU 15-721 L08 Query Scheduling（Spring 2024 讲义 08-scheduling）：static scheduling 的 straggler 问题、morsel-driven「one worker per core / one morsel per task / pull-based / round-robin placement」定义。核实 2026-08-01。
- DuckDB 官方文档/博客：morsel-driven 并行、默认 vector 2048、morsel 可调。⚙演进快·锚 DuckDB 文档@2026-08-01（默认值随版本变，具体版本号待核）。
- 冲突/分歧：morsel 具体默认尺寸各引擎不同且随版本变，除 HyPer 论文的 ~100,000 与实验下限 ~10,000 外，其余引擎具体值标「待核」，不跨引擎套用。

## 04-8.2 任务调度与工作窃取（work-stealing）

### 04-8.2.1 调度的基本词汇：operator instance / task / task set / pipeline

要谈调度先得有词汇。一个查询计划是算子构成的有向无环图（DAG）。一个算子实例（operator instance）是某个算子在一段唯一数据上的一次具体调用；一个任务（task）是一串顺序执行的算子实例（也就是"一个 morsel 穿过一条流水线"）；一个任务集（task set）是某条逻辑流水线对应的全部可执行任务的集合。

```
查询计划（算子 DAG）
   拆成若干 pipeline（流水线）
      每条 pipeline 对应一个 task set（任务集）
         task set 里每个 task = 一个 morsel 穿过这条 pipeline
            task 内部 = 一串 operator instance
```

这套词汇的用处在于：调度器调度的最小单位是 task（morsel×pipeline），而 pipeline 之间的先后依赖决定了哪些 task set 现在可跑、哪些要等（见 04-8.4.1）。初学者记住"task = 一个 morsel 跑一条流水线"这一句，本节其余内容就都能挂上去。

### 04-8.2.2 worker 分配：一核一 worker 并钉住，还是每核一个 worker 池

调度器要先决定"谁来干活"，即 worker（执行任务的线程）怎么摆到 CPU 上。两种主流做法。其一是一核一 worker：为每个硬件线程预创建恰好一个 worker，并用操作系统调用把它永久钉（pin）在那个核心上（Linux 上是 `sched_setaffinity`）。其二是每核（或每 socket）一个 worker 池：一个核心上放多个 worker，好处是当某个 worker 阻塞（比如等 I/O）时，同核心的另一个 worker 能顶上、核心不至于闲置。

HyPer/morsel-driven 走的是第一种：为每个硬件线程预建一个 worker 并永久绑定，查询的并行度不靠创建/销毁线程来控制，而靠"给这些常驻线程分派不同任务"来控制。钉住线程有个额外好处——防止操作系统把线程迁到别的核心，从而意外破坏 NUMA 局部性（见 04-8.3）。选池化方案的系统则更能容忍算子内的阻塞点。这里没有绝对优劣：一核一 worker 简单且 NUMA 局部性稳，池化更抗阻塞，取舍取决于负载里阻塞点多不多。

### 04-8.2.3 push 还是 pull：谁去找活干

任务怎么落到 worker 手里有两种模型。push（推）：一个中央 dispatcher（调度器）主动把任务派给 worker 并监视其进度，worker 报告干完了它才发下一个。pull（拉）：worker 自己从一个队列里拉下一个任务、干完、再回来拉，没有中央派发者。

这里有一处容易让人困惑、值得两边都记的措辞分歧。morsel 论文通篇用"dispatcher（调度器）"这个词，听起来像 push 的中央派发者；但它实现上明确不是一个独立线程——那样 dispatcher 既要占一个核、又可能成为争用热点。论文的真实实现是：dispatcher 就是一组无锁（lock-free）数据结构（待跑 pipeline job 队列 + 各自的 morsel 队列），这些结构的代码由"正好来请求新任务的那个 worker 线程自己"执行。CMU 15-721 讲义据此把 HyPer/morsel-driven 直接归类为 pull-based，并强调"没有独立 dispatcher 线程，worker 协作式调度，共用一个全局任务队列"。所以：论文叫它 dispatcher（概念上像 push），实现与教学口径都是 worker 自取的 pull——不是矛盾，而是"逻辑上的派发者"由"物理上的 worker 自服务"来落地。理解这一点能省掉初学者读论文时的大量困惑。

### 04-8.2.4 work-stealing：本地干完就去偷别人的活

工作窃取（work-stealing）是 morsel-driven 保证不掉队的机制。调度器为每个核心维护一份"该核心待处理的 morsel 列表"，正常情况下 Core 0 请求任务时拿回的是分配在 Core 0 同一 socket 上的 morsel（保 NUMA 局部性）。但一旦某个核心把自己 socket 上的 morsel 全干完了，它不会闲等，而是去"偷"别的核心/socket 上还没被处理的 morsel 来干。

```
正常态：Core0 从"本 socket morsel 列表"取 morsel（本地、低延迟）
        ┌ Core0 队列: [m][m][m] ┐   ┌ Core3 队列: [m][m][m][m][m] ┐
本地干完：Core0 队列空 -> Core0 转去偷 Core3（或更近 socket）的 morsel
        └ Core0: [] (空) ┘        └ Core3: [m][m] <- 被 Core0 偷走一个 ┘
```

因为 morsel 是定长且彼此可替换的小块，偷一块过来接着干毫无障碍——这正是"定长小块"设计的回报。为了不让全局队列本身成为瓶颈，这套 pipeline job 队列和 morsel 队列都实现为无锁数据结构（HyPer 用无锁哈希表维护全局工作队列），即使多个 worker 同时来请求新任务，争用也很低。窃取会牺牲一点局部性（偷来的 morsel 往往在远端内存），所以只在本地确实干完时才发生，且优先偷"更近的 socket"（见 04-8.3.4）。

### 04-8.2.5 静态调度的 straggler 问题：morsel 动态调度救的就是它

静态调度在生成计划时就定死用几个线程、跑起来不再变，最省事的做法是任务数 = 核心数。它的致命伤是掉队者（straggler）——只要有一个任务因为数据倾斜或核心变慢而拖后腿，所有依赖它的下游流水线都得等它跑完，整条查询的延迟被这一个慢任务拖垮。

morsel-driven 的动态调度 + 工作窃取正是对症下药：把工作切成远多于核心数的小 morsel，谁快谁多领，掉队自然被别人偷走的活摊平。这也解释了为什么 morsel 必须"小到远多于核心数"——若一个 morsel 等于总数据的 1/核心数，就退化回静态调度、又会掉队。理解"静态调度的病"才能理解"morsel 动态调度的药"。

### 04-8.2.6 Umbra 的 Morsel Scheduling 2.0：按时间调度与优先级衰减 ⚙演进快

HyPer 原版调度有两个已知短板：它假定每个 task 代价相同（可实际上"简单选择"和"字符串匹配"每元组代价差很多），且没有优先级概念（短查询会堵在长查询后面）。Umbra 在 SIGMOD 2021《Self-Tuning Query Scheduling for Analytical Workloads》里提出的方案常被称为"Morsel Scheduling 2.0"，专治这两点。

它的核心改动是：不再按"固定数据块大小"切 morsel，而是按时间来定——任务集里的 morsel 大小从小开始指数增长，直到单个任务大约耗时 1ms 为止（因此每个任务的实际耗时被拉齐，抹平了"每元组代价不同"的问题）；同时引入自动优先级衰减，让短查询先跑完、长查询不被饿死，本质是 stride scheduling（步长调度）的现代实现。实现上每个 worker 维护线程本地的调度元数据（活跃槽 active slots、变更掩码 change mask、返回掩码 return mask），用 CAS 原子操作广播状态变化，避免全局锁。

这一节整体标 ⚙演进快·锚 SIGMOD 2021 与 Umbra 当时实现：1ms 目标、指数增长策略、优先级衰减是论文口径；各引擎是否采用、具体参数随版本变，标「待核」。要点在方向而非数字：现代调度器正从"按数据切"走向"按时间切 + 带优先级"。

#### 来源与时效
- CMU 15-721 L08（Spring 2024 08-scheduling）：调度术语（operator instance/task/task set）、调度四目标、worker 分配（一核一 worker + `sched_setaffinity` vs 每核 worker 池）、push vs pull、static scheduling 掉队、HyPer「无独立 dispatcher 线程 / 单一全局任务队列 / 协作式 / 无锁哈希表 / 必须 work-stealing」、HyPer 短板（同代价假设、无优先级）、Umbra Morsel Scheduling 2.0（按时间、指数增长到 1ms、优先级衰减/stride、active/change/return mask + CaS）。核实 2026-08-01。
- Leis et al.（SIGMOD 2014）§3、§3.2：dispatcher 实为无锁数据结构、由请求任务的 worker 自身执行；per-core morsel 列表、按需从存储区"切出"morsel；work-stealing 触发条件与无锁全局队列。核实 2026-08-01。
- Wagner, Kohn, Neumann《Self-Tuning Query Scheduling for Analytical Workloads》（SIGMOD 2021）：Umbra 调度一手来源（细节以论文为准）。⚙演进快·锚 SIGMOD 2021。
- 冲突/分歧：morsel-driven 的任务分派模型——论文措辞为"dispatcher"（概念近 push），CMU 讲义与实现口径归为 pull-based（worker 自取、无独立调度线程）；两边都记，实质一致（逻辑派发者由 worker 自服务落地）。

## 04-8.3 NUMA 感知调度与数据局部性

### 04-8.3.1 NUMA 是什么，DB 执行为什么必须在意

NUMA（Non-Uniform Memory Access，非一致内存访问）是多路服务器的内存拓扑：内存控制器被分散进各个 CPU 芯片（socket），于是一个线程访问"挂在自己这颗 socket 上的内存"（本地）很快，访问"挂在别的 socket 上的内存"（远程）要经过芯片间互连、慢得多。论文把这形容为"计算机本身变成了一个网络"——数据在哪颗芯片、访问它的线程在哪颗芯片，决定了访问代价。

对主存分析数据库这尤其致命：查询已不再受磁盘 I/O 限制、几乎全在内存里跑，如果线程频繁访问远程内存，跨 socket 的内存流量不仅让自己慢，还会挤占互连带宽拖慢别的线程。所以多核并行必须把 RAM 和缓存层次纳入考量，尤其要保证线程"大多数时候在本地内存上干活"。初学者可以把 socket 想成不同楼层的仓库：在本层取货快，跨层取货要坐电梯，且电梯就那么几部、大家抢。

### 04-8.3.2 NUMA-local 处理：morsel 在本 socket、结果写本地、线程钉住

morsel 框架把 NUMA 局部性做进了执行的每一环。一个线程处理的输入 morsel 分配在它所在的 socket 上，它把结果也写进本 socket 的存储区，供后续流水线继续本地处理。加上 04-8.2.2 说的"线程永久钉在核心上"，操作系统不会把线程迁走、局部性不会被意外破坏。

```
Socket 0                         Socket 1
┌ Core Core Core Core ┐         ┌ Core Core Core Core ┐
│ 本地 RAM: morsel、HT、结果存储区 │  ...  │ 本地 RAM: morsel、HT、结果存储区 │
└─────────────────────┘         └─────────────────────┘
        └────────── 芯片间互连（远程访问走这里，慢）──────────┘
线程读本 socket 的 morsel、写本 socket 的结果 -> 绝大多数访问是本地
```

效果是：绝大多数执行发生在 NUMA 本地内存上，只有为了负载均衡去偷少量远端 morsel 时才发生远程访问。理解这条链路——输入本地、输出本地、线程不迁移——就理解了 morsel 框架为何被称作"NUMA-aware"。

### 04-8.3.3 数据放置：round-robin/interleave 分区与 per-socket morsel 列表

要让"morsel 在本 socket"成立，得先决定数据一开始怎么摊到各 socket 上，这就是数据放置（data placement）。分区（partitioning）把数据按某种策略切开（轮转 round-robin、属性范围、哈希、复制等），放置（placement）再决定这些分区落到哪些节点/worker（轮转、跨节点交织 interleave 等）。morsel-driven 常用的是把基表按 round-robin 摊到各 socket，使每个 socket 都持有全表的一份均匀切片。

实现上论文并不是真为每个核心维护一条很长的 morsel 链表，而是为每个 core/socket 维护存储区的边界，当某核心来请求任务时，才从"该 socket 上这条流水线的输入存储区"里现切一个 morsel 出来（"cut out"）。这样既省内存、又保证切出来的 morsel 天然属于本 socket。放置策略是"本地优先"能成立的前提：数据摊得均匀，各 socket 才有本地活可干、才少去偷远端。

### 04-8.3.4 NUMA-aware work-stealing：优先偷近 socket，远程窃取很罕见

工作窃取和 NUMA 局部性看似冲突（偷来的 morsel 在远端），morsel 框架用"分层优先"调和二者：正常态严格本地取 morsel；只有本 socket 的 morsel 全干完，才去偷别的 socket。而且在某些 NUMA 系统上 socket 之间并非两两直连，偷的时候优先从"距离更近的 socket"偷，把远程访问代价压到最低。

正常负载下，跨远端 socket 的窃取极少发生——它只是为了在收尾阶段抹平负载而存在的兜底手段，绝大部分时间大家都在本地干。这就是 morsel 框架"既要负载均衡、又要 NUMA 局部性"两个看似矛盾目标的调和方式：默认本地、窃取兜底、且窃取也讲究就近。初学者容易担心"窃取会不会把局部性全毁了"，答案是不会——窃取是罕见的、就近的、只在本地枯竭时才触发。

### 04-8.3.5 共享算子状态（哈希表）的 NUMA 局部性

比输入/输出更微妙的是算子的共享状态，典型是 hash join 的哈希表。这类状态是所有核心都可能访问的共享数据，但它同样具有很强的 NUMA 局部性——它被建在某些 socket 的本地内存上，本地核心访问快、远端核心访问慢。

morsel 调度因此不仅调度输入 morsel，还倾向于把"会访问某块共享状态的任务"排给"那块状态所在 socket 的核心"，最大化 NUMA 本地执行。论文由此论证了一个设计取舍：纯 Volcano 模型为了避免共享状态，会在 exchange 算子里做即时数据分区（on-the-fly partitioning），但分区本身有开销、且不总是划算；morsel 框架允许算子共享状态、靠"局部性感知的调度"来达到分区本想达到的局部性效果，省掉了分区那趟开销。这是 morsel 框架相对 Volcano 的一个关键立论：与其用分区强行制造局部性，不如让调度器顺着数据的自然局部性走。

#### 来源与时效
- Leis et al.（SIGMOD 2014）§1–§3：NUMA 定义与"计算机变成网络"、输入/输出/线程三重本地化、per-core/socket 存储区边界与按需切出 morsel、NUMA-aware 窃取（优先近 socket、远程窃取罕见）、共享哈希表的 NUMA 局部性、与 Volcano on-the-fly partitioning 的取舍。核实 2026-08-01。
- CMU 15-721 L08（Spring 2024 08-scheduling）：数据放置与分区（round-robin / attribute range / hashing / replication；placement round-robin/interleave）、"workers 必须操作本地数据""调度器须感知数据位置与内存布局"、HyPer「worker 优先选本地 morsel，无本地则从全局队列拉」。核实 2026-08-01。
- 冲突/分歧：本小主题两来源口径一致（论文给机制、讲义给分类框架），无实质分歧。

## 04-8.4 算子间流水线、背压与内存预算

### 04-8.4.1 pipeline 与 pipeline breaker：流水线怎么切、依赖谁先跑

一条查询计划被拆成若干 pipeline（流水线）：一段可以让数据"一路流过、不落地"的连续算子。切分点是 pipeline breaker（流水线断点）——即必须把全部输入收齐、物化下来才能出结果的算子，典型是 hash join 的 build 端（要收齐小表建完哈希表）和聚合/排序。断点两侧属于不同 pipeline，且有先后依赖。

```
SELECT ... FROM R JOIN S JOIN T ...    （T、S 为 build 端，R 为 probe 端）
Pipeline 1: Scan T -> Filter -> Build HT(T)      ┐ 两条 build 必须先跑完
Pipeline 2: Scan S -> Filter -> Build HT(S)      ┘ （它们互不依赖，可并行/先后）
Pipeline 3: Scan R -> Filter -> Probe HT(S) -> Probe HT(T) -> 输出
            （只有 HT(S)、HT(T) 都建好后才能开跑）
```

调度器靠一个观察数据依赖的状态机（论文里的 QEP 对象）来管这件事：只有前置 pipeline 全跑完，后续 pipeline 才被放进"待跑任务队列"。所以 04-8.2 说的"待跑 pipeline job 队列"里，永远只装依赖已满足、现在就能跑的任务集。理解 pipeline breaker 决定依赖顺序，就理解了调度器"什么时候能放哪条流水线出来跑"。

### 04-8.4.2 push-based 执行模型：数据从 source 被推向 sink

现代 morsel 引擎（HyPer、Umbra、DuckDB）多采用 push-based（推式）执行模型，而非经典火山模型的 pull（拉式，见 04-4.5）。一条 pipeline 有一个 source（数据源，如表扫描）、若干中间算子、和一个 sink（汇，如哈希表 build 或聚合的物化端）。执行时从 source 取一批数据，主动"推"着它依次穿过中间算子、最后交给 sink，而不是由最上层算子反复向下 `next()` 拉。

push 模型和 morsel 天然契合：一个 morsel 从 source 被推入流水线、跑到 sink 落地，正好构成一个 task。它相比 pull 的好处是控制流更简单、更利于把整条流水线编译成一段紧凑代码（配合 04-5 的查询编译），也更容易表达"多条流水线汇到同一个 sink"。初学者对照记忆：pull 是消费者驱动（上层要一个、下层给一个），push 是生产者驱动（下层来一批、往上层推一批），morsel-driven 走的是后者。

### 04-8.4.3 背压（backpressure）与 blocked 状态：sink 顶不住时暂停 source ⚙演进快

背压是流水线里下游跟不上上游时的减速信号：当 sink 或某个算子暂时无法接收更多数据（比如在等异步磁盘 I/O、或某个缓冲已满），它需要能让 source 停下来、别再推、以免内存无限堆积。push 模型里这通常表现为算子返回一个"阻塞（blocked）"状态。

以 DuckDB 的 pipeline executor 为例：算子在处理一个数据块时可以返回 BLOCKED，表示"我现在推不动了（例如在等 I/O）"，执行器据此暂停这条流水线在该 morsel 上的推进，把线程释放去干别的任务，等阻塞条件解除（I/O 完成）再恢复——这既实现了背压、又不让线程空转在阻塞点上。这一整节标 ⚙演进快·锚 DuckDB 当前实现：blocked/backpressure 的具体机制名、状态枚举随版本演进，此处只讲清"下游顶不住 -> 上游暂停 -> 线程转干别的"这条不变的主线，具体接口与版本号「待核」。理解背压的意义在于：没有它，快的 source 会把慢的 sink 淹掉、内存爆掉。

### 04-8.4.4 内存预算与溢出（spill）：buffer manager 管住内存、放不下就落盘 ⚙演进快

多核并行下多条流水线、多个算子同时吃内存，必须有一个统一的内存预算把总量管住，否则 OOM。做法通常是一个 buffer manager（缓冲管理器）：用一池定长内存块跟踪每一笔分配、强制一个可配置的内存上限；当内存压力过大时，把可落盘的中间数据（哈希表分区、排序 run 等）spill（溢出）到临时磁盘文件，之后再分批读回处理。

这与 04-6 讲的连接/聚合溢出是同一回事从调度视角看：当 hash join 的哈希表超过内存上限，引擎把两侧都分区、把分区溢出到磁盘、一次只处理一对（GRACE/hybrid 思路）。DuckDB 的实现里，buffer manager 以定长内存块（其博客材料称约 256KB 一块，具体值随版本，⚙待核）为单位管理分页的中间结构，配合流式执行让大多数算子只需持有单个 vector（默认 2048 行）的内存，从而在单机上处理远超 RAM 的数据集。这里初学者要建立的心智是：内存预算是全局的、跨流水线共享的一个"总闸"，spill 是这个总闸压力过大时的泄压阀——它让"数据大于内存"从崩溃变成"变慢但能跑完"。具体的块大小、内存上限默认值、溢出触发阈值都是引擎与版本相关，凡具体数值标「待核」，不跨引擎套用。

### 04-8.4.5 为什么通常避免同一查询的多条流水线并行（bushy 并行）

既然两条 build pipeline（HT(S) 与 HT(T)）互不依赖，能不能让它们同时并行跑？这叫 bushy（多枝）/ intra-query 的流水线间并行。morsel 框架的选择是通常不这么做。

原因有三：其一，一条查询里彼此独立的流水线数量通常远少于核心数，靠它并行填不满多核；其二，各条流水线的工作量一般不相等，同时开跑反而更难均衡；其三也是关键——同时跑多条流水线会降低缓存局部性（多套数据结构同时在缓存里打架）。所以论文的做法是：先跑完 pipeline T，跑完再把 pipeline S 的任务加进队列，一条一条来；每条流水线内部则用 morsel 铺满所有核心（intra-pipeline 并行）。这解释了 morsel 框架把并行的"主战场"放在单条流水线内部的 morsel 级、而非流水线之间的原因：核心已经被单条流水线的众多 morsel 喂饱了，没必要再靠 bushy 并行、还赔上缓存局部性。

#### 来源与时效
- Leis et al.（SIGMOD 2014）§2、§3.1：pipeline / pipeline breaker 切分、QEP 对象按数据依赖顺序放行 pipeline job、build 先于 probe；通常避免 bushy（同查询多 pipeline 并行）——独立 pipeline 少于核心数、工作量不均、降缓存局部性，故先跑 T 再跑 S。核实 2026-08-01。
- CMU 15-721 L08（Spring 2024 08-scheduling）：push vs pull 任务分派与 push-based 处理模型；调度四目标（含 low overhead）。核实 2026-08-01。
- DuckDB 官方文档/博客（执行管线、内存管理）：push-based pipeline（source/中间算子/sink）、pipeline executor 的 blocked/背压、buffer manager 定长块（约 256KB，随版本）、内存上限与 spill-to-disk、流式执行单 vector 2048 行、hash join 超限时分区落盘分批处理。⚙演进快·锚 DuckDB 文档@2026-08-01；256KB 块大小/2048 vector 等为引擎特定值，具体版本号「待核」。
- Wagner, Kohn, Neumann（SIGMOD 2021）：并行调度下的自适应任务粒度与优先级（背景参照）。核实 2026-08-01。
- 冲突/分歧：背压/blocked 的具体机制为引擎实现细节（DuckDB 有明确 BLOCKED 状态；HyPer/Umbra 表述不同），非规范统一物，正文只锚不变主线、具体接口标「待核」，两边不强行对齐。
