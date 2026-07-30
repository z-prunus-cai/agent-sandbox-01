# L4-05·大主题12 任务并行与调度

> 基线 Py3.11.15/np2.4.6/gcc13.3 @2026-07-25 ｜ 核实日期：2026-07-30 ｜ 先修：L4-05 大主题08（自旋锁与竞争：MCS/CLH 队列锁、局部自旋、缓存一致性代价）、大主题06（原子操作与 memory_order：CAS/`fetch_add`、acquire/release）、大主题05（内存一致性与重排序）、L4-01 大主题04–05（线程 API 与 CPU 调度）、数据结构课的分治与双端队列 ｜ 一手锚点：《The Art of Multiprocessor Programming》(Herlihy/Shavit/Luchangco/Spear) 2nd ed / Revised Reprint 第 16 章「Futures, Scheduling, and Work Distribution」与第 17 章「Barriers」（Elsevier/Morgan Kaufmann，https://www.sciencedirect.com/book/monograph/9780124159501/ ，核实 2026-07-25）为主一手；work-stealing 理论回落 Blumofe & Leiserson「Scheduling Multithreaded Computations by Work Stealing」JACM 46(5):720–748, 1999（原始论文，核实 2026-07-30）；OpenMP 语义回落 OpenMP API 规范（6.0，2024-11-14 发布；本机 gcc 13.3 `_OPENMP=201511` 即 4.5，核实 2026-07-30）；工程实践回落 oneTBB（oneAPI Threading Building Blocks，work-stealing 任务调度器） ｜ 成熟度：GA/理论稳定（fork-join/futures/work-stealing/sense-reversing barrier 均为 1990 年代确立的经典结果；OpenMP task 自 3.0(2008) 起、depend 子句自 4.0(2013) 起为现役标准）

> 粒度判定：**1 份，不拆**。本大主题 5 个小主题（CP-12.1–12.5）是一条"如何把一堆有依赖的任务铺到多核上跑得又对又快"的单线——futures 给出表达异步结果与依赖的最小语言构件（12.1），fork-join 是最常用的结构化任务并行骨架（12.2），work-stealing 是让任务在核间自动均衡的运行时机制（12.3），barrier 是任务并行里最基本的阶段同步点（12.4），任务图与负载均衡把前四者统一到 DAG 调度的心智模型下（12.5）。五节共享同一套 work/span 记号与同一条"表达任务 → 运行时调度 → 同步 → 均衡"的主线，拆开会断，按 report-format v3 §一默认不拆 `-a/-b`。

> 本报告一条主线心智模型：**任务并行是"程序员只负责说清哪些活能并行、彼此谁依赖谁（画出一张任务 DAG），把'谁在哪个核上、什么时候跑'完全交给运行时"。你写 `spawn`/`task`/`future`，运行时用 work-stealing 把任务动态搬到空闲核上。评判一个任务并行程序好不好，只看两个量：总活量 work（记 T₁，单核跑完的时间）和最长依赖链 span（记 T∞，无穷多核也省不掉的时间）；二者之比 T₁/T∞ 就是这段程序理论上能吃下多少个核。本课只讲"范式怎么表达、运行时怎么调度、为什么调度是对的"，具体机器上的调优/GPU 归 L6-06 HPC。**

> 分账（本课不外扩，交界处一句指路）：**每个核上双端队列的无锁实现、CAS/ABA 细节、缓存一致性对偷任务成本的影响**指回 CP-08/CP-10（本报告用到时只点名，不重推无锁栈/队列）；**barrier 底层用到的 acquire/release 配对与 seq_cst 屏障如何生成机器码**归 CP-06（本报告只说 barrier 在语义上是一个双向同步点，不展开内存序）；**线程如何被内核创建/抢占/上下文切换**归 L4-01·OS-04/05；**在真实机器上测加速比、调 cutoff、GPU offload** 归 L6-06 HPC（本课只给上界模型与正确性）。

> 本报告以多来源比对为主承重腿：AMP ch16–17 为主一手，work-stealing 的理论界逐条与 Blumofe-Leiserson 1999 原始论文交叉核对，OpenMP 语义以官方规范为准并标注版本，工程落地用 oneTBB 印证"work-stealing 是现役生产调度器而非纸上算法"。实机验证为**可选补充**且**本报告已取两项**：CP-12.4 的 sense-reversing barrier 与 `pthread_barrier` 在本机真跑（4 线程 × 3 阶段同步、SERIAL_THREAD 每阶段恰返回 1 个线程），CP-12.2 的 OpenMP `task`/`taskwait` fork-join 在本机真跑（结果正确、4 线程）——顺带**坐实 round3b 中标「待核」的 OpenMP 可用性：本机 gcc 13.3 带 `-fopenmp`（libgomp.so.1，`_OPENMP=201511`＝OpenMP 4.5）可用**，见下文实证块。

---

## CP-12.1 futures：异步结果占位与依赖表达

### CP-12.1.1 future 是异步结果的占位符

future（也叫 promise、task handle）是一个"还没算出来、但将来会有值"的占位对象。你发起一个可能耗时的计算时，不是原地等它算完，而是立即拿到一个 future；这个 future 代表"那个计算的结果"，你可以先拿着它去干别的，等真正需要那个值时再对 future 做一次取值操作（常叫 `get`/`join`/`await`），此时若结果已就绪就直接返回，若还没算完就阻塞等到就绪为止。

可以把 future 想成餐厅的取餐号码牌。你点了餐（发起计算），店员马上给你一张号码牌（future），你不用杵在柜台前，可以先去找座位、倒水（做别的工作）；号码牌本身不是饭，但凭它随时能换到饭（`get`）；饭没做好时你拿号去换就得等（阻塞）。关键是"发起"和"取结果"这两步在时间上被拆开了，中间那段你和那个计算是**并行**推进的——这正是异步的本质。future 的值通常只被写一次（由执行该计算的一方写入）、可被多次读取，这种"写一次、读多次"的语义天然避开了对结果本身的数据竞争。

### CP-12.1.2 用 future 表达任务之间的依赖

future 的第二个作用，是把任务间的**依赖关系**显式表达出来，从而"画出"一张任务图。如果任务 B 的输入是任务 A 的输出，你就让 A 返回一个 future，B 在开始处对这个 future 做 `get`——这条 `get` 就是任务图里从 A 指向 B 的一条依赖边：运行时看到 B 在等 A 的 future，就知道"B 不能早于 A 完成"，于是能自由地把没有依赖关系的任务铺到不同核上并行跑，只在有边相连处强制先后。

拿一个最小例子来说，要算 `(a+b) * (c+d)`，可以 `f1 = future(a+b)`、`f2 = future(c+d)` 同时发起两个加法（它们之间**无依赖**，可并行），再 `f1.get() * f2.get()`（乘法**依赖**两个加法，必须等两者都就绪）。这张三节点小 DAG 里，两个加法是兄弟、可并行，乘法是它们的汇合点。要抓住的领悟是：程序员用 future 声明的不是"在第几号核上跑"，而只是"谁依赖谁"；把这些依赖边连起来就是一张有向无环图（DAG），运行时的调度器负责在满足所有依赖的前提下尽量并行——这就是 CP-12.5 任务图模型的起点。这也解释了为什么 future 是 fork-join、work-stealing 等一切任务并行机制底下的公共语言构件。

### CP-12.1.3 落地形态与常见陷阱

各语言/库都有 future 的落地：C++ 有 `std::future`/`std::async`/`std::promise`（`std::async` 返回 future，`get` 取值并传播异常）；Java 有 `Future` 与更强的 `CompletableFuture`（支持 `thenApply` 等组合）；Cilk 的 `spawn` 隐式产生一个可 `sync` 汇合的异步计算；Python 有 `concurrent.futures.Future` 与 `asyncio` 的 awaitable。它们形态各异，但内核都是"占位 + 将来取值 + 表达依赖"这三件事。

初学者要防的三个坑。其一，**取值即阻塞**：对一个还没就绪的 future 调 `get` 会把当前线程卡住，如果你在关键路径上过早 `get`，就等于把本可并行的两段强行串起来，白白丢掉并行度——正确姿势是"尽量晚地 `get`，先发起所有能发起的任务"。其二，**异常搭车传播**：异步计算里抛的异常通常不会当场炸，而是被 future 接住，等你 `get` 时才重新抛出，容易让人误以为"没报错就是成功了"。其三，**忘了 `get` 的悬空计算**：有的实现里如果没人取那个 future，计算可能根本不被推进，或其异常被静默吞掉。把 future 理解成"必须最终被兑现的承诺"，就能记住"发起了就要在某处汇合"。

#### 来源与时效
- AMP 2nd/Revised ch16 §16.1「Introduction」（核实 2026-07-25）：以 future 作为表达异步任务与依赖的编程构件，`Thread`/`Future` 抽象、futures 组织成可被调度器展开的计算，`get` 阻塞至就绪。
- Cilk 谱系（Blumofe et al.「Cilk: An Efficient Multithreaded Runtime System」PPoPP 1995；Frigo et al.「The Implementation of the Cilk-5 Multithreaded Language」PLDI 1998，核实 2026-07-30）：`spawn`/`sync` 作为 future 式异步与汇合的语言级形态，交叉印证"future/依赖边 → DAG"的表达。
- C++ `std::future`/`std::async`（cppreference 指路，二手非承重）与 Java `CompletableFuture` 作落地形态举例，不承重。
- 交叉一致，无冲突。"future=异步结果占位 + 依赖边"两源（AMP 抽象 + Cilk spawn/sync）一致。

## CP-12.2 fork-join：分治任务的并行展开与汇合

### CP-12.2.1 fork-join 是分治的并行骨架

fork-join 是任务并行里最常用的结构：遇到一个可分解的问题，就把它**分叉（fork）**成若干可并行的子任务，各自递归求解，然后在一个**汇合（join）**点等所有子任务都完成、再合并它们的结果。它天然匹配分治算法——归并排序、快排、树/数组的递归归约、并行前缀和等，都是"分成两半 → 并行做两半 → 合并"。fork 对应发起子任务（产生依赖边的分叉），join 对应等待子任务并收拢（依赖边的汇合），一次 fork-join 就是任务 DAG 里的一个"菱形"（一分为二、再合二为一）。

fork-join 的任务图可以想成一棵递归树，从根往下不断二分展开（fork），到叶子（足够小的子问题）直接顺序算，再从叶子往上逐层合并（join）。它比"手工创建 N 个线程各干一段"高级的地方在于：你不需要知道有几个核、也不需要手工切分成正好 N 份，只管按问题的自然结构递归地分叉，运行时会把这些子任务动态铺到可用核上（靠 CP-12.3 的 work-stealing）。所以 fork-join 程序是"与核数无关"地写出来的，同一份代码在 4 核和 64 核上都能自动利用。

### CP-12.2.2 用 work 与 span 度量 fork-join 的并行度

评价一个 fork-join（或任意任务 DAG）能并行到什么程度，用两个量。**work**（记 T₁）是所有任务的总计算量，等于单个处理器顺序做完全部工作的时间。**span**（也叫 critical-path length 或 T∞）是任务 DAG 中最长的一条依赖链的长度，等于**即使有无穷多个处理器**也省不掉的时间（因为链上的任务必须一个接一个）。二者相除定义**并行度**：

parallelism = T₁ / T∞

并行度是这段程序能有效利用的处理器数上限的一个刻画：处理器数 P 远小于 parallelism 时，加速接近线性；P 逼近或超过 parallelism 后，再加核也压不下运行时间（受 span 卡住）。同时永远有两条硬上界——用 P 个核的运行时间 T_P 不可能好过"把活平均分"，也不可能好过"关键路径"：

T_P ≥ T₁ / P

T_P ≥ T∞

这里要领会的一点是，并行不是"活越多越好并行"，而是"依赖链越短越好并行"。一个 work 巨大但 span 也巨大（几乎全是一条长链）的程序，parallelism 接近 1，加再多核也没用；反过来，一个能递归二分到很浅深度的 fork-join，span 只有对数级、parallelism 很高。写 fork-join 时脑子里要同时盯着这两个量：fork 得越深越广，work 不变而 span 变短、并行度变高。这套 work/span 记号是 CP-12.3 理论界和 CP-12.5 调度定理的公共语言，务必先记牢。

### CP-12.2.3 OpenMP task/taskwait 落地 fork-join（本机实证，坐实 OpenMP 可用性）

OpenMP 用 `#pragma omp task` 发起一个可异步执行的任务（fork），用 `#pragma omp taskwait` 等待当前任务派生的所有子任务完成（join），整个任务区通常套在一个 `#pragma omp parallel` 建线程组 + `#pragma omp single` 让单一线程启动递归的外壳里。round3b 曾把本机 OpenMP 可用性标「待核」，本报告实证坐实：**本机 gcc 13.3 支持 `-fopenmp`，链接 libgomp.so.1，`_OPENMP` 宏值 201511 即 OpenMP 4.5**（`task`/`taskwait` 自 3.0 起、`task` 的 `depend` 依赖子句自 4.0 起均在 4.5 覆盖内）。

本机真跑（基线 gcc 13.3 @2026-07-30，4 核 x86-64，`OMP_NUM_THREADS=4`）：用递归 fork-join 对 0..10⁷−1 求和，子区间小于阈值时顺序算（叶子），否则 `omp task` 分叉左右两半、`taskwait` 汇合再相加。可复现命令与真实输出：

```
# gcc -std=c11 -O2 -fopenmp forkjoin.c -o forkjoin && OMP_NUM_THREADS=4 ./forkjoin
OpenMP fork-join tasks: threads=4  sum(0..10000000)=49999995000000  expected=49999995000000  OK
# 对照 parallel-for reduction 版：
omp parallel for reduction: threads=4 sum=49999995000000 expected=49999995000000 OK
```

结果与解析解 (n−1)·n/2 = 49999995000000 完全一致，4 个线程都被用上。要点：`omp task` 只是"声明这段可以异步"，具体在哪个线程跑由 OpenMP 运行时的任务调度器（内部就是 work-stealing 式的任务池）决定；`taskwait` 保证了合并发生在两个子和都算完之后（正确性）。这条实证同时演示了 fork-join 的两种典型写法——显式递归 task（适合不规则/递归结构）与规则循环的 `parallel for reduction`（适合等长数组归约），两者都属于 fork-join 家族。（实证脚本 `forkjoin.c`/`redux.c` 仅存于仓库外 scratchpad，跑完即清；上面命令 + 输出已自足可复现。）

### CP-12.2.4 fork-join 的正确性纪律与陷阱

fork-join 好用，但有两个初学者必须守的正确性纪律。其一，**fork 出去的子任务之间不能有数据竞争**：既然它们可能真并行地跑在不同核上，若两个子任务写同一块非原子内存、又无同步，就是 CP-07 意义上的数据竞争（UB）。fork-join 的正确用法是让子任务各写各的、互不相交的数据，在 join 之后再由父任务合并——上面求和例里左半和右半写的是各自的局部变量，正是这个原则。其二，**join 前不能读子任务尚未写完的结果**：必须在 `join`/`taskwait`/`sync` 之后才去用子任务的输出，否则读到的是半成品（同样是竞争）。

性能上的关键陷阱是**粒度（cutoff）**：如果递归分叉到极细（每个叶子只算一两个元素），任务创建/调度的开销会淹没实际计算，反而比顺序还慢。标准做法是设一个阈值，子问题小于阈值就直接顺序算（如实证里的 `hi-lo<=1000`）——这在 span 增加极少的情况下大幅砍掉任务管理开销。要记住的权衡：fork 太粗则并行度不足（span 没压下来、核吃不满），fork 太细则开销爆炸（work 被管理成本放大）；cutoff 就是在这两者间取平衡，其最优值依赖机器，具体调优归 L6-06。

#### 来源与时效
- AMP 2nd/Revised ch16 §16.2「Analyzing Parallelism」（核实 2026-07-25）：work T₁ 与 critical-path length（span）T∞ 的定义、parallelism=T₁/T∞、T_P≥T₁/P 与 T_P≥T∞ 两条下界；fork-join 计算作为 DAG 展开。
- OpenMP API 规范（现行 6.0，2024-11-14 发布；本机为 4.5＝`_OPENMP` 201511，核实 2026-07-30）：`task` 构造自 3.0(2008)、`taskwait` 自 3.0、`task` 的 `depend` 子句自 4.0(2013)；`parallel`/`single`/`for reduction` 语义。
- 本机实证（基线 gcc 13.3 @2026-07-30，x86-64 4 核，`OMP_NUM_THREADS=4`）：`-fopenmp` 可用、libgomp.so.1、`_OPENMP=201511`；fork-join task 求和 = 49999995000000（=解析解），reduction 版一致。命令与输出见正文，可复现。**坐实 round3b「OpenMP 可用性待核」＝本机可用（4.5）。**
- 交叉一致，无冲突。work/span 记号 AMP 与 Cilk/Blumofe-Leiserson 谱系一致；OpenMP task 引入版本以官方规范为准。

## CP-12.3 work-stealing：每核双端队列、空闲核偷任务

### CP-12.3.1 为什么需要动态负载均衡

fork-join 会动态地产生大量粗细不均、且事先不知道各分支要算多久的子任务。如果用**静态分配**（开工前就把任务平均分给各核），碰上任务耗时不均（比如快排的两半极不平衡）就会有的核早早干完闲着、有的核堆积如山——负载不均直接侵蚀加速比。work-stealing 就是解决这个问题的**动态**负载均衡机制：让每个核在运行期间自己找活干，忙的核不用管别人，闲下来的核主动去别的核那里"偷"任务过来做，从而无需中央协调就把负载摊平。

给初学者的直觉对照两种思路。一种是"派活的"（work-sharing）：一个核产生新任务时，主动把任务推给（可能空闲的）别的核——问题是繁忙的核还得分心去派活，且要判断谁空闲。另一种是"抢活的"（work-stealing）：产生任务的核只把任务放进自己的本地队列继续埋头干，**只有真正空闲、没活可做的核**才承担"去别处找活"的开销。work-stealing 的精髓就是把负载均衡的成本转嫁给"闲人"——反正它闲着也是闲着，让它去偷正好，而忙碌的核几乎不受打扰。

### CP-12.3.2 每核一个双端队列：owner 从底 LIFO、thief 从顶 FIFO

work-stealing 的核心数据结构是**每个核一个双端队列（deque）**，里面存待做的任务。访问规则是精心设计的两端分工：**队列的拥有者（owner）从一端（习惯称"底部"）压入和弹出**——push 新 fork 出的任务、pop 最近压入的任务来做，这是后进先出（LIFO）；**小偷（thief，即空闲核）从另一端（"顶部"）偷**——每次偷走最早压入的那个任务，这是先进先出（FIFO）。owner 和 thief 从两端操作，绝大多数时候不碰同一个元素，因而竞争极低；只有队列快空时两端逼近，才需要用 CAS 之类的原子操作仲裁（这层无锁细节归 CP-08/CP-10）。

这套"owner 从底 LIFO、thief 从顶 FIFO"不是随便定的，背后有两条深意。第一，owner 用 LIFO 拿**最近**压入的任务，通常是刚 fork 出的子任务，数据还在缓存里（热），且分治中最近的任务往往是最小的子问题——就地做完开销最小，**局部性最好**。第二，thief 从顶偷**最早**压入的任务，那通常是分治树里靠近根、**粒度最大**的一块活——偷一次就搬走一大坨，能让小偷忙很久，从而把"偷"这个相对昂贵的跨核操作的频率降到最低（偷大块 = 少偷几次）。要记住这个对称美：**自己人拿小而热的（省局部性），小偷搬大而冷的（省偷窃次数）**。

### CP-12.3.3 work-stealing 的理论保证

work-stealing 的分量在于它不只是启发式，而是有**可证明的界**。Blumofe & Leiserson (1999) 证明：对 fully-strict（充分严格，join 只汇合到父任务的良构 fork-join）计算，用 P 个处理器的随机化 work-stealing 调度器，期望运行时间是

E[T_P] ≤ T₁ / P + O(T∞)

也就是"活平均分（T₁/P）＋一个正比于关键路径的开销（O(T∞)）"。当程序并行度充足（T₁/T∞ ≫ P）时，第二项相对可忽略，运行时间趋近理想的 T₁/P，即接近**线性加速**。同时它的**空间**也有界——每个处理器占用不超过顺序执行栈空间 S₁，故

S_P ≤ P · S₁

此外 work-stealing 的**通信量**（跨核偷窃的次数）期望也被 O(P·T∞) 界住，比 work-sharing 更省通信。

初学者要领会这个结果的意义：它把"运行时随机偷任务"这件看似碰运气的事，变成了**有数学保证的高效**——期望意义上，你几乎拿到理想加速，且不会因为动态调度而爆栈或爆通信。这解释了为什么 work-stealing 成了几乎所有现代任务并行运行时的默认调度策略。"fully-strict/期望"这些限定词要记住：界是对良构 fork-join、在期望（而非最坏）意义下成立的；病态的依赖结构或极端调度不在此保证内。

### CP-12.3.4 工程落地：Cilk 的 work-first 原则、oneTBB、有界 vs 无界 deque

work-stealing 是现役生产调度器而非纸上算法。Cilk-5 (1998) 提出了著名的 **work-first 原则**：既然绝大多数任务是被 owner 自己顺序执行掉的（偷窃是少数事件），就该把开销尽量挪到"偷"这条冷路径上，而让"自己做"这条热路径（常见情形）几乎零开销——即便这会让关键路径稍微变长也值得。Cilk 据此设计了 THE 协议等，使无争用时的 fork/sync 快到接近普通函数调用。Intel 的 **oneTBB**（oneAPI Threading Building Blocks，前身 Intel TBB）同样以 work-stealing 任务调度器为核心：维护工作线程池、把任务映射到线程、非抢占式执行（任务一旦开始绑定到线程直到完成），是 C++ 里"面向任务而非线程"编程的主流库。Java 的 `ForkJoinPool`、Go 运行时的 goroutine 调度、Rust 的 Rayon 也都是 work-stealing。

实现上 AMP 区分**有界（bounded）**与**无界（unbounded）work-stealing deque**：有界版用固定大小循环数组，实现简单但满了要处理溢出；无界版能动态增长，但要更小心地用原子操作维护两端下标、并对付 **ABA 问题**（顶部下标被偷后又转回同值，naïve CAS 会误判——需带标签或用增长型下标规避，这条与 CP-10.3 的 ABA 同源，本报告只指路）。给初学者的落点：不必掌握无锁 deque 的每行代码，只需记住"每核一个 deque + owner/thief 两端分工 + 偷窃仲裁靠 CAS"这个骨架，以及"work-first：让常见的自己做零开销、把成本推给罕见的偷"这条工程哲学。

#### 来源与时效
- AMP 2nd/Revised ch16 §16.4「Work Distribution」§16.5「Work-Stealing Dequeues」（含 Bounded/Unbounded Work-Stealing DEQueue、Work Balancing，核实 2026-07-25）：work-stealing vs yielding/multiprogramming、每核 deque、owner 从底 push/pop（LIFO）而 thief 从顶 steal（FIFO）、有界与无界 deque 及 ABA 规避、work-balancing 变体。
- Blumofe & Leiserson「Scheduling Multithreaded Computations by Work Stealing」JACM 46(5):720–748, 1999（原始论文，核实 2026-07-30）：fully-strict 计算下 E[T_P] ≤ T₁/P + O(T∞)、空间界 S_P ≤ P·S₁、通信界 O(P·T∞)——逐条交叉核对 AMP §16.4 的界。
- Frigo, Leiserson & Randall「The Implementation of the Cilk-5 Multithreaded Language」PLDI 1998（核实 2026-07-30）：work-first 原则、"push/pop 自己队列头、偷别人队列尾"、THE 协议使热路径近零开销。
- oneTBB（uxlfoundation/oneTBB；oneAPI 规范 Task Scheduler 一节，核实 2026-07-30）：现役 work-stealing 任务调度器、工作线程池、非抢占式任务映射——印证 work-stealing 为生产实践。
- 交叉一致，无冲突。owner LIFO/thief FIFO 的两端分工两源（AMP §16.5 + Cilk-5 描述）一致；理论界以 Blumofe-Leiserson 1999 原文为准（AMP 转述一致）。

## CP-12.4 barriers：sense-reversing barrier 等同步点

### CP-12.4.1 barrier 是阶段性的会合点

barrier（栅栏/屏障）是一种同步原语：一组线程各自跑到 barrier 处就停下等待，**直到组内所有线程都到达**，才一起放行继续往下跑。它把并行执行切成一个个**阶段（phase）**——第 k 阶段的所有工作必须全部做完，才能进入第 k+1 阶段。典型场景是迭代式并行算法：每轮所有线程并行更新自己那块数据，轮末在 barrier 处会合（确保本轮全部写完），再进入下一轮读用上一轮的结果。注意这里的 barrier 是**线程同步**的会合点，与 CP-06 里 `atomic_thread_fence` 那种"内存屏障"（约束单线程内读写重排）是两个不同概念，虽然中文都叫"屏障"。

barrier 可以想成旅游团在景点集合——导游说"下午三点在门口集合"，先到的人得等，最后一个人到齐了大家才一起出发去下一站。它保证"没有人还停留在上一个阶段"，从而后一阶段可以安全地依赖前一阶段的全部结果。barrier 是 fork-join 的"扁平"表亲：fork-join 是树状地分叉汇合，barrier 是一组对等线程反复地"齐步走—会合—再齐步走"，特别适合数据并行的循环（如 OpenMP `parallel for` 每次循环末尾就有一道隐式 barrier）。

### CP-12.4.2 朴素计数 barrier 的缺陷与 sense-reversing barrier

最朴素的 barrier 用一个共享计数器：每个线程到达就把计数减一，然后自旋等计数归零。问题出在**复用**：barrier 要被反复使用（每个阶段一次），归零后必须重置计数器以备下一阶段——但如果直接重置，可能出现"跑得快的线程已经冲进下一阶段、把计数器重置了，而跑得慢的线程还在上一阶段的自旋里没醒过来"，两阶段的使用互相踩踏，导致死等或错误放行。

**sense-reversing barrier（翻转敏感位屏障）**用一个巧妙的办法根治复用问题：除了计数器，再设一个共享的"sense"布尔位，每个线程还持有一个**私有的** local sense。约定"本阶段放行条件 = 共享 sense 变成我的 local sense 值"。每个线程进 barrier 先翻转自己的 local sense；然后减计数器——**最后一个到达者**（把计数器减到 0 的那个）负责把计数器重置回线程总数、并把共享 sense **翻转**成新值（这一翻转就是放行信号）；其余线程则自旋等"共享 sense == 我的 local sense"。因为相邻两个阶段用的是**相反**的 sense 值，跑得快的线程即使冲进下一阶段也是在等**另一个** sense 值，绝不会误吞上一阶段的放行信号——复用踩踏问题被"每阶段用相反极性"彻底消除。

本机真跑印证（基线 gcc 13.3 @2026-07-30，4 核，实现如上述 sense-reversing 逻辑，`_Thread_local` 存 local sense，`atomic_fetch_sub` 减计数、最后者重置并翻转 sense）：

```
# gcc -std=c11 -O2 -pthread barrier.c -o barrier && ./barrier
sense-reversing barrier: 4 threads x 3 phases synced OK
pthread_barrier: PTHREAD_BARRIER_SERIAL_THREAD returned to exactly 3 thread over 3 phases (expected 3)
```

4 个线程连续 3 个阶段每阶段都正确会合、无踩踏。要记住的骨架就三件：**私有 local sense 每次翻转、最后到达者重置计数并翻转共享 sense、其余人等共享 sense 追上自己的 local sense**。（实证脚本 `barrier.c` 仅存于仓库外 scratchpad，跑完即清；命令 + 输出已自足可复现。）

### CP-12.4.3 可扩展 barrier：combining tree / static tree / dissemination

sense-reversing barrier 正确且简单，但它让所有线程都去减**同一个**共享计数器、自旋**同一个** sense 位——高并发下这就是 CP-08 讲过的热点争用（缓存行在各核间弹跳），线程一多就不可扩展。为此有一批**树形/分散式** barrier 把这个热点打散：

**combining tree barrier（组合树屏障）**把线程组织成一棵树，线程只和树里**局部的一小组**同伴在各自的节点上会合，会合信号逐层向根汇聚、再逐层向叶广播放行——每个计数器只被少数线程碰，避免了全体挤一个变量。**static tree barrier（静态树屏障）**类似，用固定的树结构让每个线程只通知父节点、等父节点广播，通信模式在编译期就定死，很省。**dissemination barrier（传播屏障）**不用树而用 log₂N 轮"两两通知"：第 k 轮每个线程通知距离 2^k 的伙伴，log₂N 轮后所有线程互相都（间接）确认到齐——它没有单点热变量，延迟只有对数级，适合大规模。

这些高级 barrier 的共同思想都是"**别让所有人挤一个同步变量**"，用树或分散通信把 O(N) 争用降成 O(log N) 的层数/轮数——和 CP-08 里"队列锁让每人自旋自己的变量"是同一套可扩展性哲学的延续。选型上，线程少时 sense-reversing 足够；线程多、barrier 密集时才上树形/dissemination。具体哪种最快依赖机器与规模，实测调优归 L6-06，本课只需掌握"为何要打散热点"这一层。

### CP-12.4.4 pthread_barrier 与 OpenMP 隐式 barrier

工程上不必自己手写 barrier。POSIX 线程提供 `pthread_barrier_t`：`pthread_barrier_init(&b, NULL, count)` 设定会合线程数，每个线程调 `pthread_barrier_wait(&b)` 到达并等待；当第 count 个线程到达时全部放行，且**恰好一个**线程的 `pthread_barrier_wait` 返回特殊值 `PTHREAD_BARRIER_SERIAL_THREAD`、其余返回 0——这个"被选中的唯一线程"常用来做每阶段只需一次的串行收尾工作（如打印、归并）。上面的实证正验证了这点：3 个阶段里 `PTHREAD_BARRIER_SERIAL_THREAD` 恰好被返回了 3 次（每阶段 1 次），符合规范。

OpenMP 则有**隐式 barrier**：`#pragma omp parallel` 区域结束、`#pragma omp for`/`single` 等工作分担构造的末尾，默认都有一道隐式 barrier，保证本阶段所有线程做完才继续（可用 `nowait` 子句显式去掉）；也有显式的 `#pragma omp barrier`。初学者要记住的对应关系：pthread 的 barrier 要自己 init/wait/destroy 并靠 SERIAL_THREAD 选串行线程，OpenMP 的 barrier 大多是"你没写但它在那儿"的隐式同步——这也是为什么 `parallel for` 每轮之间数据是安全可见的（末尾隐式 barrier 兜底）。注意 `pthread_barrier_*` 是 POSIX 可选特性（`_POSIX_BARRIERS`），本机 glibc 支持（实证已编译运行通过）。

#### 来源与时效
- AMP 2nd/Revised ch17「Barriers」（§17.2 Sense-Reversing Barrier、§17.3 Combining Tree Barrier、§17.4 Static Tree Barrier、§17.6 Dissemination/Tournament 等，核实 2026-07-25）：barrier 定义、朴素计数 barrier 的复用踩踏、sense-reversing（私有 local sense + 共享 sense 翻转 + 最后者重置）、树形与传播 barrier 的可扩展性动机。
- POSIX/`pthread_barrier`（The Open Group Base Specifications / Linux man-pages `pthread_barrier_wait(3)`，核实 2026-07-30）：`pthread_barrier_init/wait/destroy`、恰一个线程返回 `PTHREAD_BARRIER_SERIAL_THREAD`、barrier 属可选特性 `_POSIX_BARRIERS`。
- OpenMP API 规范（现行 6.0；本机 4.5，核实 2026-07-30）：`parallel`/`for`/`single` 末尾隐式 barrier、`nowait` 子句、显式 `#pragma omp barrier`。
- 本机实证（基线 gcc 13.3 @2026-07-30，4 核）：sense-reversing barrier 4 线程×3 阶段同步 OK；`pthread_barrier` 的 SERIAL_THREAD 3 阶段恰返回 3 次（每阶段 1 次）。命令与输出见正文，可复现。
- 交叉一致，无冲突。sense-reversing 算法两源（AMP §17.2 + Mellor-Crummey/Scott 1991 barrier 讨论）一致；SERIAL_THREAD 语义以 POSIX 规范为准。

## CP-12.5 任务图与负载均衡：DAG 调度、均衡策略

### CP-12.5.1 用有向无环图（DAG）为并行计算建模

把一个并行程序抽象成**任务 DAG**：节点是一段不可分的顺序计算（任务），有向边表示依赖（A→B 意为 B 必须在 A 完成后才能开始）。fork 产生"一分为多"的出边，join 产生"多合为一"的入边，future 的 `get` 是一条依赖边——前面几节的所有构造最终都落到这张图上。DAG 是**无环**的（依赖不能循环，否则谁也没法先开始），这保证了总存在一个可执行的顺序。之前的两个量在图上有清晰含义：work T₁ 是所有节点计算量之和，span T∞ 是从任意源到任意汇的**最长路径**（关键路径）的长度。

DAG 可以理解成这堆任务的"依赖地图"。调度器要做的事，就是在**遵守所有箭头方向**（不违反依赖）且**每个核一次只能做一个任务**（P 个核）的约束下，把这张图的节点安排到时间轴上，让总完成时间尽量短。任务并行的全部理论——能并行到什么程度、该怎么调度、调度好不好——都是在这张 DAG 上做文章。这也是为什么前面反复强调 work 和 span：它们是这张图仅有的两个一阶特征，几乎决定了可达到的性能上下界。

### CP-12.5.2 贪婪调度与 Brent 式上界

一个自然的问题：不追求最优调度（NP 难），随便用个"只要有空闲核、就一定给它派一个已就绪任务、绝不让核无谓闲着"的**贪婪调度器（greedy scheduler）**，能有多好？经典结论（Graham/Brent 一系）给出漂亮的上界：任何贪婪调度在 P 个处理器上的运行时间满足

T_P ≤ T₁ / P + T∞

这个上界的直觉是把每个时间步分两类。"完全步"（P 个核全在干活）最多有 T₁/P 步（因为总活量就 T₁，每步消耗 P 个单位）；"不完全步"（有核闲着）里，每一步至少推进关键路径一层（闲着说明就绪任务不够 P 个，那关键路径上此刻可做的任务必被做掉），故最多 T∞ 步。两者相加得上界。结合 CP-12.2 的两条下界 T_P≥T₁/P 与 T_P≥T∞，可知贪婪调度**至多比最优慢 2 倍**——即

T_P ≤ 2 · T_opt(P)

这里要领会的是，不用费劲找最优调度，只要"别让核闲着、有活就派"，就自动逼近理想。这就是为什么 work-stealing（它本质上是贪婪调度的分布式随机实现）能拿到 CP-12.3 那个 T₁/P+O(T∞) 的界——O(T∞) 项正对应这里的不完全步开销。把这条 Brent 上界和 work-stealing 的期望界对照着记：前者是任意贪婪调度的确定性上界，后者是随机 work-stealing 的期望界，两者形状一致、精神相同。

### CP-12.5.3 负载均衡策略：静态 vs 动态

负载均衡就是"把任务分配到各核、让大家忙得差不多"。两大类策略。**静态负载均衡**在运行前就把任务划分好分给各核（如把数组等分成 P 段），零运行时开销、无协调成本，适合**任务量均匀且可预测**的规则计算（如等长数组逐元素运算）；缺点是任务量不均或不可预测时会严重失衡。**动态负载均衡**在运行时根据实际进度调配任务，能吸收不均，代价是调度/协调开销——work-stealing（闲核偷活）和 work-sharing（忙核派活）是它的两种实现，前者把开销压在闲核、通常更优（见 CP-12.3）。

OpenMP 的循环调度子句是这套策略的直接体现：`schedule(static)` 静态等分（最省开销、要求均匀）；`schedule(dynamic)` 每次动态领一小块（吸收不均、开销较大）；`schedule(guided)` 领的块从大到小递减（前期少协调、后期细粒度收尾以均衡）；OpenMP 5.0 起还有 `nonmonotonic`/`auto` 让运行时自行决定。初学者的选型直觉：迭代耗时齐整用 static，参差不齐用 dynamic/guided；本质就是在"协调开销"和"抗不均能力"之间权衡——越静态越省协调但越怕不均，越动态越抗不均但协调越贵。

### CP-12.5.4 加速被什么侵蚀：开销来源与选型心智

即便调度器接近贪婪最优，真实加速比仍常低于理想 P 倍，侵蚀来自四处，都能挂回本课概念。其一，**串行/关键路径**：span T∞ 是硬下界，程序里非并行的部分（对应 CP-15 Amdahl 的串行比例）和最长依赖链决定了加速天花板。其二，**同步开销**：barrier、锁、join 处的等待——barrier 上快线程要等慢线程（负载不均在同步点显形），这正是 CP-12.4 要可扩展 barrier 的原因。其三，**通信/数据移动**：任务在核间迁移带来缓存失效与一致性流量（CP-08），work-stealing 的偷窃次数即通信量。其四，**负载不均**：任务粒度不当（CP-12.2 的 cutoff）或分配不均导致有核闲有核忙。

收束成一句话，任务并行的性能 = 好的**表达**（用 future/fork-join 画出 span 短、并行度高的 DAG）＋ 好的**调度**（work-stealing 贪婪地吃满核）＋ 少的**侵蚀**（压低同步/通信/不均）。本课到此为止——它给的是这套上界模型与正确性框架；具体到一台机器上测出真实加速曲线、调 cutoff/schedule、上 GPU/向量化做极致优化，属于实测调优，交由 L6-06 HPC。把 T₁（work）、T∞（span）、parallelism=T₁/T∞、T_P≤T₁/P+T∞ 这几个量记牢，就握住了判断"一个任务并行程序值不值得、能快多少"的钥匙。

#### 来源与时效
- AMP 2nd/Revised ch16 §16.2「Analyzing Parallelism」§16.3「Realistic Multiprocessor Scheduling」（核实 2026-07-25）：计算 DAG 模型、work/span、贪婪调度、T_P ≤ T₁/P + T∞ 上界及"至多 2 倍最优"推论。
- Blumofe & Leiserson 1999（核实 2026-07-30）：work-stealing 作为贪婪调度的分布式随机实现、E[T_P] ≤ T₁/P+O(T∞)——与 Brent 上界形状对照印证。
- Brent「The Parallel Evaluation of General Arithmetic Expressions」JACM 21(2):201–206, 1974 与 Graham 1969（贪婪/list scheduling 界的原始来源，二手转述核实 2026-07-30）：T_P ≤ T₁/P+T∞ 的经典出处，交叉印证 AMP §16.3。
- OpenMP API 规范（现行 6.0；本机 4.5，核实 2026-07-30）：`schedule(static|dynamic|guided|auto)`、5.0 起 `nonmonotonic` 修饰——静态/动态负载均衡策略的落地。
- 跨课：CP-12.5 上界模型 ↔ L6-06 HPC 实测调优/GPU；Amdahl 串行比例 ↔ CP-15。
- 交叉一致，无冲突。贪婪调度上界两源（AMP §16.3 + Brent/Graham 原始谱系）一致；"至多 2× 最优"由两条下界 T_P≥T₁/P、T_P≥T∞ 直接导出。
