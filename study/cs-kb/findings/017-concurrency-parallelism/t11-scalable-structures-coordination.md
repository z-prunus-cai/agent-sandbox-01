# L4-05·大主题11 可扩展并发结构与分布式协调

> 基线 Py3.11.15/np2.4.6/gcc13.3 @2026-07-25 ｜ 核实日期：2026-07-30 ｜ 先修：L4-05 大主题02（并发对象与线性一致性：顺序规约、线性化点、可组合性）、大主题04（同步原语相对能力：CAS 共识数=∞）、大主题06（原子操作与 memory_order：CAS/`fetch_add`）、大主题10（无锁数据结构：逻辑删除标记 + CAS 物理摘除、lazy 链表、hazard pointer/RCU 内存回收）、L4-01 大主题06（锁与条带化）、数据结构课（哈希表、平衡树、二叉堆）｜ 一手锚点：《The Art of Multiprocessor Programming》(Herlihy/Shavit/Luchangco/Spear) 2nd ed / Revised Reprint（Elsevier/Morgan Kaufmann，https://www.sciencedirect.com/book/monograph/9780124159501/ ，核实 2026-07-25）——ch12「Counting, Sorting, and Distributed Coordination」(pp.265–303)、ch13「Concurrent Hashing and Natural Parallelism」(pp.305–334)、ch14「Skiplists and Balanced Search」(pp.335–357)、ch15「Priority Queues」(pp.359–376) ｜ 交叉源：Pugh「Skip Lists: A Probabilistic Alternative to Balanced Trees」CACM 33(6):668–676, 1990；Aspnes-Herlihy-Shavit「Counting Networks」JACM 41(5):1020–1048, 1994；Shalev-Shavit「Split-Ordered Lists: Lock-Free Extensible Hash Tables」JACM 53(3):379–405, 2006；Shavit-Zemach「Diffracting Trees」ACM TOCS 14(4), 1996 ｜ 成熟度：GA/理论稳定（这些结构与网络均为 1990–2006 确立的经典结果；skiplist 的无锁变体已进入 Java `java.util.concurrent.ConcurrentSkipListMap` 现役标准库）

> 本报告一条主线心智模型：**可扩展性的敌人是"热点"——一个所有线程都必须碰的单一位置（一把全局锁、一个计数器变量、树根、堆顶）。核越多，撞这个点的人越多，结构就越退化成串行。本大主题的每一种结构都在回答同一个问题：能不能把这个热点"摊开"？哈希把键散到很多桶（各桶几乎独立）；skiplist 用随机层数让插入删除落在不同位置且永不需要旋转再平衡（再平衡本身是热点）；并发优先队列想办法让多个线程各取一个"近似最小"元素而不都盯着同一个堆顶；counting network 用一串 balancer 把"给我下一个号"的请求像分流阀一样扇散到 w 条输出线上，任意时刻只有极少数线程碰同一个 balancer。摊开得越均匀，越接近"加核即提速"。**

> 下游边界（本课不外扩，交界处一句指路）：**逻辑删除标记 + CAS 物理摘除的链表底盘、以及"何时能安全 free"的内存回收（hazard pointer / epoch / RCU）**已在 CP-10（大主题10 无锁数据结构）讲透，本报告的无锁哈希/skiplist 直接复用其结论，只指路不重推；**锁本身的可扩展实现（MCS/CLH 队列锁、条带锁的缓存代价）**归 CP-08；**线性一致性/顺序规约/可组合性的形式定义**归 CP-02，本报告用到"线性化点""quiescent consistency"时给直觉并指回 CP-02；**这些结构在真实机器上的吞吐调优/NUMA 放置**归 L6-06 HPC，本课只讲"为何能扩展"的算法图景，不做实测调优。

> 本报告以多来源比对为主承重腿：AMP ch12–15 为主一手，四个原始论文（Pugh 1990 skiplist、AHS 1994 counting networks、Shalev-Shavit 2006 split-ordered、Shavit-Zemach 1996 diffracting trees）对各自结构的定义、复杂度与关键性质逐条交叉核对。本主题 5 个小主题在 本库编排清单清单中**全部标注"概念"、无本机强实证腿**，故本报告实机验证**未取**（如实标注）；正确性由"一手教材 + 原始论文"两源交叉支撑，不以实机替代。

---

## CP-11.1 并发 hashing：开/闭地址、可扩展哈希、拆分有序表

### CP-11.1.1 哈希表的"天然并行性"

哈希表把每个键 `k` 通过哈希函数映射到一个桶（bucket）下标，理想情况下不同键落在不同桶、各桶的操作互不相干。这就是 AMP 说的"天然并行性"（natural parallelism）：只要键散得均匀，作用在不同桶上的插入/删除/查找**本来就没有数据依赖**，可以真正并行。哈希表的可扩展并发设计，核心就是"如何在不破坏这份天然并行的前提下，处理好两件麻烦事——桶内冲突、和整表扩容（resize）"。

哈希是本大主题里"分散争用"做得最自然的结构。相比之下一棵树有唯一的根、一个堆有唯一的顶，天生就有热点；哈希表只要桶足够多、散列足够均匀，热点就被切成了很多小块。所以并发哈希的难点不在"日常操作要不要抢同一个锁"（散开就行），而在"当负载升高需要把桶数翻倍时，怎么在有别的线程正在读写的情况下安全地重新分布所有元素"——扩容是唯一一次所有桶都被牵动的全局事件，它是并发哈希真正的战场。

### CP-11.1.2 闭地址（链地址）+ 条带锁

**闭地址哈希**（closed-address hashing，即链地址法/chaining）中每个桶挂一条链表（或小集合），冲突到同一桶的键都串在这条链上。最粗的并发做法是一把大锁保护整表（coarse-grained），显然不可扩展。改进是**条带锁**（lock striping）：不给每个桶一把锁（锁太多、内存浪费、且扩容时锁数量也得变），而是准备固定数量 `L` 把锁，桶 `b` 由锁 `b mod L` 保护。这样并发度约为 `L`，而锁数组本身不随桶数增长。

条带锁的巧妙之处在于把"锁的数量"和"桶的数量"解耦：即使桶从 16 扩到 16 万，仍然只有 `L` 把锁；扩容时只需按顺序拿齐所有 `L` 把锁，就冻结了全表、可以安全重排。初学者常见的困惑是"多个桶共享一把锁不会误伤吗"——会有一点（两个本可并行的桶因共享锁而串行），但概率随 `L` 增大而降低，用少量伪串行换来"锁开销恒定、扩容可控"，是典型的工程折中。AMP 在此基础上给出 **RefinableHashSet**：扩容时能安全地把锁数组也一起换掉（用一个标记位让正在加锁的线程发现"锁集正在被替换"从而退避重试），解决了"锁数组自己也要 resize"的鸡生蛋问题。

### CP-11.1.3 可扩展哈希与"何时/如何扩容"

哈希表性能取决于**负载因子**（load factor，元素数 / 桶数）：太高则每个桶的链变长、查找退化成线性扫描。所以表必须在负载超阈值时**扩容**（通常桶数翻倍并重新分布元素）。串行世界里这只是一次 `O(n)` 的 rehash；并发世界里它是最难的一步，因为扩容期间别的线程还在访问。

条带锁方案的扩容是"停世界"式的：拿齐所有锁 → 分配新桶数组 → 把每个旧元素 rehash 到新桶 → 释放。代价是扩容瞬间全表阻塞。要理解为什么这仍可接受：扩容是**摊还**事件——每翻一倍容量，要走很多次普通操作才会再次触发，把一次昂贵扩容的成本摊到大量廉价操作上，平均每次操作的扩容开销是常数。真正想避免"停世界"的，就得走下面的无锁路线，让扩容变成**增量的、不阻塞任何操作**的。

### CP-11.1.4 开地址与并发 cuckoo hashing

**开地址哈希**（open-addressing）不挂链表：每个元素直接存在桶数组的某个槽里，冲突时按某种探测序列（线性探测、双重哈希等）另找空槽。它对缓存友好（数据连续、无指针追逐），但并发下"元素可能在探测链上移动"使加锁边界难划。AMP ch13 介绍的现代方案是**并发 cuckoo hashing**：用两张表、两个哈希函数，每个键要么在 `table1[h1(k)]`、要么在 `table2[h2(k)]` 两个位置之一，查找因此只需看**两个固定槽**（`O(1)` 最坏查找）。插入时若两槽都满，就把占位者"踢"到它的另一张表的备用槽，可能引发一串连锁踢出（cuckoo，杜鹃占巢）。

并发版本叫 **PhasedCuckooHashSet / StripedCuckooHashSet**：把表切成小块加条带锁，插入分阶段进行，踢出链过长或成环时触发扩容。要记住的核心权衡是——cuckoo 用"查找只看两个位置"的确定性上界换取"插入可能级联踢出且偶尔失败重来"的复杂性。它是"读多、要最坏查找保证"场景的选择；而链地址 + 条带锁是更通用、实现更简单的默认选择。两者都在 AMP ch13，分别代表开/闭地址两条路。

### CP-11.1.5 无锁可扩展哈希：拆分有序表（split-ordered lists）

最优雅的可扩展并发哈希是 Shalev-Shavit 2006 的**拆分有序表**（split-ordered lists）。它的反直觉之处在于：**所有元素其实躺在同一条无锁链表上**，桶只是指向这条链中某个位置的"快捷入口"。扩容时**不移动任何元素**——只是在链上多插入几个新的桶指针，把原本一个桶负责的一段链"劈"成两段。这就把"停世界 rehash"彻底消灭了。

它靠的数学结构叫**递归拆分序**（recursive split-ordering）：给每个键的哈希值做**位反转**（bit-reversal）后作为链上的排序键。位反转的妙处是——当桶数从 `2^i` 翻到 `2^(i+1)`，需要把旧桶 `b` 一分为二成 `b` 和 `b + 2^i`，而在位反转序下，这两个新桶所负责的元素恰好是旧桶那段链的**前半和后半**，于是"劈桶"只需在链的中间某点插入一个新的哨兵结点（用一次 CAS），无需搬动数据。

原论文的结论是：插入/删除/查找期望 `O(1)`，且整个结构**只用 load / store / CAS**，是"当时体系结构上第一个真正无锁的可扩展哈希表"。底层那条链就是 CP-10 讲过的无锁有序链表（逻辑删除标记 + CAS 摘除），本节只需理解"桶=链上的快捷指针、扩容=在链里插哨兵而非搬元素"这一层。初学者可以这样记：普通哈希"扩容=重排所有元素"，split-ordered"扩容=在一条不动的链上多放几个书签"——把最贵的操作变成了最便宜的。

#### 来源与时效
- AMP 2nd/Revised ch13「Concurrent Hashing and Natural Parallelism」pp.305–334（核实 2026-07-25）：天然并行性、闭地址集合（粗锁→条带锁→RefinableHashSet）、开地址 cuckoo（PhasedCuckoo/StripedCuckoo）、无锁拆分有序表（LockFreeHashSet）。章节页码与内容经 O'Reilly 目录页交叉印证（https://www.oreilly.com/library/view/the-art-of/9780123973375/xhtml/CHP013.html ，核实 2026-07-30）。
- 拆分有序表交叉源：Shalev & Shavit「Split-Ordered Lists: Lock-Free Extensible Hash Tables」JACM 53(3), 2006（DOI 10.1145/1147954.1147958，核实 2026-07-30）——"递归拆分序、位反转键、扩容不移动元素、期望 O(1)、仅用 load/store/CAS"逐条与 AMP 一致。
- cuckoo hashing 原始思想回落 Pagh & Rodler「Cuckoo Hashing」（J. Algorithms 2004）——"两个哈希函数、两候选位置、最坏 O(1) 查找、插入可能级联踢出"。AMP 讲的是其并发/分阶段变体。
- 交叉一致，无冲突。
- 实证：本节全部为概念/算法图景，本库编排清单标"概念"、无本机强实证腿，本报告**未取实机验证**（如实标注）。

## CP-11.2 skiplist 与并发平衡搜索：概率平衡、无锁跳表

### CP-11.2.1 顺序 skiplist：用抛硬币代替再平衡

**skiplist**（跳表，Pugh 1990）是一种有序的链式结构，能在期望 `O(log n)` 时间内查找/插入/删除，却**不需要任何再平衡操作**。它是若干层有序链表的叠放：最底层（level 0）串起全部元素；每往上一层，元素数约减半，形成"快速通道"。查找从最高层最左端开始，能往右走就往右、走过头就下一层，像坐特快列车逐段换乘到目标附近。每个新插入的元素**随机决定自己有多高**：以概率 `p`（通常 `p = 1/2`）再升一层，直到某次抛硬币失败为止。

一个元素达到层数 `≥ i` 的概率是

p^i

期望层数是 `1/(1-p)`（`p=1/2` 时为 2），期望的最高层是 `log_{1/p} n`，故查找期望走 `O(log n)` 步。要抓住的直觉是：平衡树（红黑树、AVL）靠**旋转**强制维持平衡，而旋转在并发下是噩梦——一次旋转要同时改动父、子、孙多个结点的指针，加锁范围大、且这些结点是别的线程正在路过的热点。skiplist 用"随机层数"从**概率**上得到平衡，代价是偶尔运气差（某次全表恰好都很矮），换来的是**永远不需要旋转**——这正是它"天然适合并发"的根源。Pugh 原文标题直接点题："平衡树的概率替代品"。

### CP-11.2.2 为什么 skiplist 对并发友好

平衡树的再平衡是一个**结构性全局操作**：插入一个叶子可能触发从叶到根的一连串旋转，牵动树根这个终极热点。skiplist 没有这种级联——插入一个元素只影响它自己那几层的前驱结点的 `next` 指针（数量期望是常数 `1/(1-p)`），改动**局部且数量有界**，天然契合"细粒度加锁"或"逐指针 CAS"。

这解释了工业界的选择：Java 标准库的并发有序映射 `ConcurrentSkipListMap`/`ConcurrentSkipListSet` 用的正是无锁 skiplist，而不是并发红黑树——后者的并发实现复杂到几乎没有实用的无锁版本。初学者可以记住这条对比：**要有序 + 高并发 → skiplist；要有序 + 单线程极致性能 → 平衡树**。skiplist 用一点点空间（多层指针）和"期望而非最坏"的复杂度保证，买到了并发实现上的巨大简化。

### CP-11.2.3 基于锁的并发 skiplist（lazy / optimistic）

AMP ch14 的锁式并发 skiplist 沿用 CP-10 的 **lazy（惰性）+ optimistic（乐观）** 套路：查找/遍历**不加锁**（乐观地一路走下去），只有真正要修改（插入/删除）时，才锁住相关的前驱结点，然后**验证**（validation）——重新确认这些前驱当前仍然指向预期的后继、且未被逻辑删除。验证通过才提交修改，否则解锁重试。

每个结点带一个 `fullyLinked`（是否已在所有层链好）和 `marked`（是否已逻辑删除）标志。插入分两步：先自底向上把新结点在各层链入（此时 `fullyLinked=false`），全部链好后再置 `fullyLinked=true` 使其对查找"生效"，这保证了插入的**线性化点**（linearization point，见 CP-02）是那次把 `fullyLinked` 置真的写。删除则先标 `marked`、再逐层摘除。要点是——多层结构让"加锁范围"仍然局部（只锁一个元素在各层的前驱），而 lazy 验证让读路径完全无锁，这两点合起来使锁式 skiplist 已经相当可扩展，是理解无锁版本前的踏脚石。

### CP-11.2.4 无锁 skiplist：逐层 CAS + 标记引用

无锁 skiplist（AMP ch14「Lock-Free Concurrent Skiplists」，思想承 Fraser 2004 与 Java 的 Lea 实现）把每一层链表都做成 CP-10 那种无锁有序链表：用**带标记的引用**（marked reference，把"已删除"标志和 `next` 指针打包成一个可原子 CAS 的字）实现"逻辑删除 + CAS 物理摘除"。插入时自底向上、每层用一次 CAS 把新结点接进去；删除时自顶向下把各层的 `next` 标记为 deleted，再 CAS 摘除。

关键设计是把**底层（level 0）链表当作"真相"**：一个元素"存在"当且仅当它在底层链上且未被标记删除；上层只是加速查找的索引，即使上层链暂时不一致（某结点在上层还没接好或还没摘干净），也不影响正确性——查找路过一个"半接入/半删除"的上层结点时，会在底层拿到最终判定。这种"底层定正确性、上层管性能"的分层，正是无锁 skiplist 能容忍中间态、无需全局锁定的原因。初学者不必背 CAS 的每一步，抓住三点即可：(1) 每层就是一条 CP-10 式无锁链表；(2) 底层是唯一权威、上层可暂时不一致；(3) 没有旋转所以没有跨结点的原子性难题，这是它比无锁平衡树可行得多的根本原因。

#### 来源与时效
- AMP 2nd/Revised ch14「Skiplists and Balanced Search」pp.335–357（核实 2026-07-25）：顺序 skiplist、锁式（lazy/optimistic + validation、`fullyLinked`/`marked`）、无锁（逐层 CAS + 标记引用、底层定正确性）。章节页码经 GlobalSpec/O'Reilly 目录交叉印证（核实 2026-07-30）。
- 概率平衡交叉源：Pugh「Skip Lists: A Probabilistic Alternative to Balanced Trees」CACM 33(6):668–676, 1990（DOI 10.1145/78973.78977，普林斯顿技术报告修订版 https://15721.courses.cs.cmu.edu/spring2016/papers/pugh-skiplists1990.pdf ，核实 2026-07-30）——"层数以概率 p 递增（p=1/2 标准，p=1/4 减常数增方差）、期望 O(log n)、无需再平衡"逐条与 AMP 一致。
- 工业实现印证：Java `java.util.concurrent.ConcurrentSkipListMap` 采用无锁 skiplist（Lea 实现），佐证"无锁 skiplist 是现役而非纸面"。
- 交叉一致，无冲突。`p` 的取值 AMP 与 Pugh 一致（默认 1/2）。
- 实证：概念性，本报告**未取实机验证**（如实标注）。

## CP-11.3 并发优先队列：基于 skiplist / 堆的并发实现

### CP-11.3.1 优先队列的并发难点：removeMin 是天生热点

优先队列（priority queue）支持两个操作：`add(x, priority)` 插入带优先级的元素、`removeMin()` 取出并删除当前优先级最高（数值最小）的元素。它的顺序规约就是普通优先队列。并发的根本难点在于 **`removeMin` 天生是热点**：所有线程都想拿"那一个最小元素"，都盯着同一个位置（堆顶 / 有序结构的头），争用无法像哈希那样靠"散到不同桶"化解——因为按定义只有一个最小值。

因此并发优先队列的设计哲学分两路：**有界优先队列**（priority 取值范围固定且不大，如 0..m-1）可以用"每个优先级一个计数器/位桶"把结构摊平，用 counting-network 式思路分散争用；**无界优先队列**（priority 任意）只能落回堆或 skiplist，并想办法让多个 removeMin **各拿一个"近似最小"**、而不是死盯真正的全局最小，以此把热点拆开。理解这条分岔是本节的钥匙——是否有界，决定了能不能把"取最小"这个热点摊开。

### CP-11.3.2 基于树的有界优先队列

当优先级来自一个小的有界范围 `0..m-1` 时，AMP 用**基于树的有界优先队列**（tree-based bounded priority queue）：把 `m` 个优先级槽放在一棵二叉树的叶子上，每个内部结点维护一个计数器，记录"以我为根的子树里当前有多少个元素"。`add` 时从叶往根一路把计数 `+1`（用原子 `getAndIncrement`）；`removeMin` 从根往叶走，每到一个内部结点看左子树计数——大于 0 就往左（最小的一定在左边），否则往右，同时把沿途计数 `-1`，最终落到一个非空叶子取走元素。

它可扩展的原因是：树的不同分支上的操作**碰不同的计数器**，争用被这棵树扇散开了，只有靠近根的少数计数器是共享热点。这本质上是把 CP-11.4 的"计数分散"思想用到优先队列上。初学者要抓住的限制是——它只对**有界且不大的优先级范围**有效（树的叶子数 = 优先级数 `m`），优先级若是 64 位任意整数就没法这么建树，那就得走下面的堆/skiplist 无界方案。

### CP-11.3.3 基于堆的无界优先队列：细粒度锁堆

对无界优先级，经典结构是二叉堆（数组表示的完全二叉树，父 ≤ 子）。并发版本（AMP ch15「Unbounded Heap-Based Priority Queue」，承 Hunt-Michael-Parthasarathy-Scott 1996 的细粒度锁堆）给**每个结点一把锁**，让 `add`（自底向上 sift-up）和 `removeMin`（把堆顶换成末元素后自顶向下 sift-down）能在不同分支上并行推进。

难点是 `add` 的 sift-up 与 `removeMin` 的 sift-down **方向相反**（一个往上、一个往下），若两者在同一条路径相遇、又各自逐结点加锁，极易死锁或活锁。Hunt 等人的方案用"结点带一个 tag 标记归属线程 + 规定加锁顺序"来避免逆向相遇的僵局。初学者不必掌握其加锁协议细节，只需理解两点：(1) 每结点一锁让不同子树的操作并行，把堆顶这个单一热点部分缓解为"越靠近顶越争、越靠近叶越松"；(2) sift-up/sift-down 反向是并发堆的核心麻烦，这也是为什么很多系统宁愿用下面的 skiplist 优先队列——它没有这种反向路径冲突。

### CP-11.3.4 基于 skiplist 的无界优先队列（SkipQueue）

更实用的无界并发优先队列直接**复用无锁 skiplist**（CP-11.2.4）：把 skiplist 按 priority 作为键排序，`add` 就是 skiplist 插入，`removeMin` 就是"删除并返回最左（最小键）元素"。它的并发优势直接继承 skiplist：插入散在不同键位置、无再平衡热点。

`removeMin` 仍是热点（大家都想删最左那个），但可以用一个技巧缓解——多个线程竞争最左元素时，让它们**沿着底层链依次尝试"逻辑删除"**：第一个 CAS 成功标记删除某元素的线程就"认领"了它，失败者顺着链往右去认领下一个，于是多个并发 removeMin **各自拿到一个靠前但不必是全局第一的元素**。这就把"死盯一个最小值"松弛成"大家各拿一个近似最小值"，把热点摊成了一小段链上的分散竞争。代价是它不再严格线性一致于"总是返回精确全局最小"（属于 CP-02 意义上为可扩展性放松规约的常见手法），但对绝大多数"取一个高优先任务"的场景足够。初学者记住这条主线即可：**skiplist 优先队列 = 有序 skiplist + "removeMin 时大家顺链错开认领"**，是无界并发优先队列里实现最简、扩展性最好的一路。

#### 来源与时效
- AMP 2nd/Revised ch15「Priority Queues」pp.359–376（核实 2026-07-25）：优先队列规约、数组式有界、树式有界（计数树）、堆式无界（细粒度锁堆）、skiplist 式无界。章节页码经 ScienceDirect/O'Reilly 目录交叉印证（核实 2026-07-30）。
- 细粒度锁堆交叉源：Hunt, Michael, Parthasarathy, Scott「An Efficient Algorithm for Concurrent Priority Queue Heaps」（IPPS 1996）——"每结点一锁、sift-up/sift-down 反向相遇的死锁规避"。
- skiplist 优先队列的"顺链错开认领 removeMin"承 Lotan-Shavit 的并发优先队列思想，与 AMP SkipQueue 一致。
- 交叉一致，无冲突。
- 实证：概念性，本报告**未取实机验证**（如实标注）。

## CP-11.4 counting networks：balancer 网络分散计数争用

### CP-11.4.1 计数争用问题与 balancer

很多并发场景需要一个共享的**取号器**：每个线程调用一次就拿到一个**互不重复、连续**的整数（内存分配偏移、任务序号、环形缓冲下标）。最朴素的实现是一个共享计数器 + 原子 `getAndIncrement`，但这就是 CP-08 讲过的终极热点——所有核抢同一个缓存行，核越多越慢。**counting network**（计数网络，Aspnes-Herlihy-Shavit 1994）的目标就是把这个单点计数**分散**成一张网，让任意时刻只有极少数线程碰同一个元件。

网络的基本元件是 **balancer**（平衡器）：一个 `(p, q)`-balancer 有 `p` 条输入线、`q` 条输出线；最基本的是 `(2,2)`-balancer，可以想象成一个玩具开关——每来一个 token（线程），它像拨浪鼓一样把 token **交替**地送往上、下两条输出线（第 1 个走上、第 2 个走下、第 3 个走上……）。单个 balancer 只需一个 bit 的状态、一次原子翻转即可实现，本身**没有热点**，因为整张网里有很多 balancer，token 被迅速扇散到不同 balancer 上。要抓住的直觉是：balancer 是"分流阀"，把一股请求流均匀劈成两股；把很多分流阀按特定拓扑连成网，就能把一股洪流均匀摊到 `w` 条输出线上。

### CP-11.4.2 step property 与 quiescent consistency

一个宽度为 `w` 的 counting network，若在**静默态**（quiescent state，所有已进入的 token 都已从输出线离开、网络中没有在途 token）时，各输出线收到的 token 数满足**阶梯性质**（step property），就说它"会计数"：

若输出线 i 收到 y_i 个 token，则 0 ≤ y_i − y_j ≤ 1（当 i < j）

静默时各输出线的计数**至多相差 1**，且是从上到下"阶梯"下降的。有了阶梯性质，就能把输出线 `i` 上第 `k` 个出来的 token 赋号 `i + k·w`——保证全网出的号**无重复、无空缺、连续**。这正是取号器要的。

这里有一个初学者极易踩的坑：counting network 保证的是 **quiescent consistency（静默一致性）而非 linearizability（线性一致性）**。也就是说，两个 token `A`、`B`，即使 `A` 在真实时间上先完成，也**不保证** `A` 拿到的号比 `B` 小——网络只保证"号连续不重复"，不保证"号的先后等于时间的先后"。只有当网络进入静默态时，阶梯性质才成立。这是一种为可扩展性而**主动放松规约**的经典例子（对照 CP-02 的线性一致性）：放弃"号序=时序"这个强保证，换来"计数争用被摊平到全网"的巨大扩展性。很多场景（取内存偏移、分配任务号）根本不在乎号的先后，只要不重不漏，于是这个放松是白赚的。

### CP-11.4.3 Bitonic / Periodic 计数网络与深度

AHS 1994 给出两族具体的 counting network：**Bitonic[w]** 和 **Periodic[w]**，它们分别与同名的**排序网络**（Batcher 的 bitonic sorting network、periodic network）**同构**——把排序网络里的"比较器"换成 balancer，就得到会计数的网络。宽度 `w`（`w` 为 2 的幂）的 Bitonic 网络深度（token 穿过的 balancer 层数）为

depth = (log₂ w)(log₂ w + 1) / 2 = O(log² w)

深度就是一个 token 从进到出要依次触碰的 balancer 数。`O(log² w)` 意味着：宽度翻倍（能分散的输出线翻倍），每个 token 只多走大约 `log w` 个额外元件——用对数级的延迟换取线性级的争用分散，这就是它可扩展的本钱。要点在于"同构"这个桥：排序网络研究了几十年、拓扑现成，counting network 直接借用其结构，把"排序"复用成"计数"。深度 `O(log²w)` 也提示了它的适用面——争用极高、值得为分散付出对数延迟时才划算；争用不高时一个原子计数器反而更快。

### CP-11.4.4 用 balancer 网络造计数器，及 diffracting / combining 树

有了会计数的网络，共享 `getAndIncrement` 计数器就实现为：token 进网 → 从某输出线 `i` 出来、这是该线的第 `k` 个 token → 返回号 `i + k·w`。这就把一次原子操作的全局争用，换成了穿过 `O(log²w)` 个几乎无争用的 balancer。

同章还有两个"分散计数/协调"的近亲结构。**diffracting trees（衍射树，Shavit-Zemach 1996）**：用二叉树形排布的 balancer，并在每个 balancer 前放一个"棱镜（prism）"——让成对到达的两个 token 直接一上一下"对冲"通过（因为一个 balancer 连续来两个 token 净效果是各走一边、状态不变），从而无需真的去翻转 balancer 的状态位，进一步削减热点，在高并发下比 counting network 更快。**combining trees（组合树，AMP §Software Combining）**：反向思路——让并发请求在一棵树上**边上升边合并**，两个 `+1` 请求在某结点合成一个 `+2` 上报根，根只被访问一次就服务了两个请求，返回时再把结果拆分给两个请求方。counting network 是"把争用扇散开"，combining tree 是"把请求合并起来减少对根的访问次数"，两者是分散计数热点的两种互补思路。初学者抓住这组对比即可，无需推每种树的协议细节。

#### 来源与时效
- AMP 2nd/Revised ch12「Counting, Sorting, and Distributed Coordination」pp.265–303（核实 2026-07-25）：balancer、counting network、step property、quiescent consistency、Bitonic/Periodic 网络、diffracting trees、software combining（combining trees）。章节页码与小节（Shared Counting / Software Combining / Counting Networks / Diffracting Trees）经 O'Reilly/ScienceDirect 目录与 Moir-Shavit「Concurrent Data Structures」综述交叉印证（核实 2026-07-30）。
- 计数网络交叉源：Aspnes, Herlihy, Shavit「Counting Networks」JACM 41(5):1020–1048, 1994（DOI 10.1145/185675.185815，核实 2026-07-30）——"(p,q)-balancer、step property、Bitonic/Periodic 与同名排序网络同构、深度 O(log²w)"逐条与 AMP 一致。
- diffracting trees 交叉源：Shavit & Zemach「Diffracting Trees」ACM TOCS 14(4), 1996——"balancer 二叉树 + prism 让成对 token 对冲、比 counting network 更抗争用"。
- 冲突/分歧记录：counting network 只满足 **quiescent consistency，非 linearizability**——AHS 1994 与 AMP ch12 一致明确此点，本报告已在 11.4.2 显式标注，避免"它像原子计数器一样保证时序"的误解。
- 深度公式 `(log₂w)(log₂w+1)/2` 与 Batcher bitonic 排序网络深度同（交叉自 CLRS ch27「Sorting Networks」与多个网络课件，核实 2026-07-30）；见 11.5。
- 实证：概念性，本报告**未取实机验证**（如实标注）。

## CP-11.5 并发排序与组合：sorting / 组合网络

### CP-11.5.1 排序网络与 0-1 原理

**排序网络**（sorting network）是一种"数据无关"的并行排序：它由固定数量的**比较器**（comparator，把两条线上的值比较后小的放上、大的放下）按固定拓扑连成，**比较的先后与拓扑完全不依赖输入数据**。因为拓扑固定，不同比较器可以在同一时刻并行工作，非常适合硬件电路和高并发实现；它的排序时间由**深度**（数据穿过的比较器层数）决定，而非比较次数。

验证一个比较器网络是否真能排序，靠的是**0-1 原理**（zero-one principle）：

若一个比较器网络能正确排序所有 2ⁿ 个由 0/1 组成的输入串，则它能正确排序任意输入

这条原理是排序网络理论的基石，因为它把"验证对所有实数输入都排好序"这件无穷的事，缩减成"只需检查有限的 2ⁿ 个 0/1 串"。初学者理解它的直觉：比较器只做"比较-交换"，是**单调**操作，若它在最苛刻的二值分布上都能把所有 0 挤到前、1 挤到后，那对任意值（可看成用不同阈值切成的一族 0/1 串）也就都对了。排序网络与 CP-11.4 counting network 的连接点正在这里——把比较器换成 balancer，排序网络就变成计数网络。

### CP-11.5.2 Batcher 的 bitonic 排序网络

最著名的排序网络是 Batcher（1960 年代）的**bitonic sorting network**（双调排序网络）。它基于"双调序列（先增后减或先减后增）可以被 `bitonic merge` 高效并行地排成有序"这一性质，递归地先把两半分别排成一升一降拼成双调序列、再 merge。宽度 `n`（2 的幂）的 bitonic 网络深度为

depth = (log₂ n)(log₂ n + 1) / 2 = O(log² n)

比较器总数为

count = n · (log₂ n)(log₂ n + 1) / 4

`O(log²n)` 的深度意味着 `n` 个数能在对数平方的并行步内排好——这正是并行排序相对串行 `O(n log n)` 的价值：用大量并行硬件/线程把时间压到多项式对数级。要抓住的是"深度 = 并行时间"这个换算：同一层的比较器互不冲突可同时做，所以决定墙钟时间的是层数而非比较器总数。这个深度公式与 11.4.3 的 Bitonic 计数网络**完全相同**，因为二者同构——记住一个就记住了两个。

### CP-11.5.3 组合与采样排序：把并行排序落到共享内存

纯排序网络假设"每条线一个值"，而真实场景是 `p` 个线程要排 `n` 个（`n ≫ p`）内存中的元素。AMP ch12 给出两种"把网络思想落地到共享内存"的组合手法。**sample sort（采样排序）**：先抽样估计数据分布、定出 `p-1` 个"分界点"（splitter），把元素按分界点扇到 `p` 个桶（桶间近似有序、桶内乱序），各线程独立排自己的桶，最后拼接——本质是"用采样把大数组切成 p 段近似有序的块，块间并行、块内串行"，把网络里的"比较器分流"放大成"按 splitter 分桶"。

**combining（组合）** 则呼应 CP-11.4.4 的组合树：让并发到达同一位置的请求在上升途中**合并**，减少对共享热点（计数器根、桶头）的实际访问次数，返回时再拆分结果。把这两者与前面的 counting/sorting 网络放在一起看，AMP ch12 讲的其实是同一族思想的谱系——**分散（网络/树把争用扇开）+ 组合（把请求合并减少访问）+ 采样（按分布切块让块间独立）**，三种手法都指向同一目标：把"所有线程都得碰的那个点"要么摊开、要么合并、要么切开，从而随核数扩展。这也正是整个大主题11 的收束：可扩展并发结构的全部艺术，是消灭热点的艺术。

#### 来源与时效
- AMP 2nd/Revised ch12「Counting, Sorting, and Distributed Coordination」§Parallel Sorting / Sorting Networks / Sample Sorting / Software Combining（pp.265–303，核实 2026-07-25）：排序网络、0-1 原理、bitonic 网络、sample sort、combining。
- 排序网络与 bitonic 交叉源：CLRS《Introduction to Algorithms》ch27「Sorting Networks」（0-1 原理、bitonic sorter、depth O(log²n)；MIT 章节 PDF https://mitp-content-server.mit.edu/books/content/sectbyfn?collid=books_pres_0&fn=Chapter+27.pdf&id=8030 ，核实 2026-07-30）；Batcher 1968 原始工作。深度公式 `(log₂n)(log₂n+1)/2`、比较器数 `n(log₂n)(log₂n+1)/4` 两源一致（核实 2026-07-30）。
- 0-1 原理表述交叉自 CLRS ch27 与多份网络课件，一致。
- 与 11.4 的连接（比较器↔balancer、bitonic 排序网络↔bitonic 计数网络同构）由 AHS 1994 与 AMP ch12 共同印证。
- 交叉一致，无冲突。
- 实证：概念性，本报告**未取实机验证**（如实标注）。
