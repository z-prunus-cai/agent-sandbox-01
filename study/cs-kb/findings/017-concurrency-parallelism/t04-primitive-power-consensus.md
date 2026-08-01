# L4-05·大主题04 同步原语的相对能力与共识

> 基线 Py3.11.15/np2.4.6/gcc13.3 @2026-07-25 ｜ 核实日期：2026-07-29 ｜ 先修：L4-05 大主题02（并发对象与线性一致性、wait-free/lock-free 进展性条件）、大主题03（共享内存基础与寄存器层级）、L4-01 大主题06（硬件原语造锁的机制层）｜ 一手锚点：《The Art of Multiprocessor Programming》(Herlihy/Shavit/Luchangco/Spear) 2nd ed / Revised Reprint 第 5 章「The Relative Power of Primitive Synchronization Operations」与第 6 章「Universality of Consensus」（Elsevier/Morgan Kaufmann，https://www.sciencedirect.com/book/monograph/9780124159501/ ，核实 2026-07-25）为主一手；交叉源：Maurice Herlihy「Wait-Free Synchronization」ACM TOPLAS 13(1):124–149, 1991（原始论文 PDF https://cs.brown.edu/~mph/Herlihy91/p124-herlihy.pdf ，核实 2026-07-29）；ISO/IEC 9899:2024（C23）§7.17 `atomic_compare_exchange_*`（CAS 实测印证）｜ 成熟度：GA/理论稳定（共识层级与不可能性结果是 1991 年确立的经典结论，数十年未变）

> 本报告一条主线心智模型：**"某个同步原语到底有多强"这个模糊问题，被 Herlihy 变成了一个能算出确切数字的问题——把原语拿去解一个叫"共识"的标准难题，最多能让几个线程 wait-free 地达成一致，那个"最多几个"就是它的能力刻度（consensus number）。read/write 只能刻到 1（连两个线程都协调不了），CAS 能刻到 ∞（万能）。** 这门课回答的是"为何 CAS 万能、read/write 不行"这个能力排序问题，不是讲某把锁怎么实现。

> 下游边界（本课不外扩，交界处一句指路）：**`compareAndSet`/`atomic_compare_exchange` 的语言级接口、strong/weak 变体、memory_order 参数**归 L4-05·CP-06（本报告只在 CAS 能力处指路到 C23 §7.17，不展开六档内存序）；**这些原语在真实弱内存模型上为何/何时需要屏障**归 CP-05；**硬件如何用 `LOCK` 前缀/缓存一致性把 CAS 做成原子**归 L4-01·OS-06。本报告只做能力/共识的理论层，不做实现纵深。

> 本报告以多来源比对为主承重腿：AMP ch5–6 为主一手，Herlihy 1991 原始论文逐条交叉核对，所有 consensus number 数值（1 / 2 / 2m−2 / ∞）都以两源比对锚定、绝不凭记忆填。实机验证（C11/C23 `atomic_compare_exchange` 最小例）为**可选补充**，本报告**未取**——因为本主题的核心命题（consensus number = 1 / 2 / ∞、不可能性定理）是关于"存在/不存在某种 wait-free 协议"的**理论结论**，跑一段 CAS 计数程序既不能证明也不能证伪它们（充其量演示"CAS 指令在本机存在且原子"，那属 CP-06 的活儿）；为避免用一次运行制造"已验证理论"的错觉，此处如实标未取，正确性完全由 AMP 与 Herlihy 1991 的多来源比对支撑。

---

## CP-04.1 共识问题：多线程达成一致与 wait-free 要求

### CP-04.1.1 共识问题的精确定义

**共识**（consensus）是一个被精心设计出来当"测量尺"用的标准问题：n 个线程各自**提议**（propose）一个值，然后每个线程要**决定**（decide）一个值，使得所有线程的决定满足两条性质——**一致性**（agreement）：所有线程决定的是**同一个**值；**有效性**（validity）：这个被决定的值必须是**某个线程实际提议过**的值（不能凭空捏造一个谁都没提的值）。

一个能让 n 个线程满足这两条的对象或协议，就叫一个 **n 线程 consensus 协议**（consensus object）。要抓住的直觉是：共识不是"投票选多数"，也不是"求平均"——它只要求大家**殊途同归到同一个提议值**上，至于归到谁的提议不重要，重要的是"人人一致 + 不无中生有"。初学者容易把 validity 想漏：如果没有 validity，一个永远返回常数 42 的对象也满足 agreement（大家都是 42），却毫无意义；validity 逼着这个协议真的去"协调"线程们的输入。为什么用这么一个抽象问题当尺子？因为"达成一致"是并发协作的最小内核——几乎任何有意义的多线程协作（谁拿到锁、队列头是谁、下一个状态是什么）都隐含着"就某件事达成一致"，所以一个原语能不能解共识、能给几个线程解，就精准刻画了它的协调能力。

### CP-04.1.2 wait-free 是刚性要求，不是可选项

共识问题在这门课里**默认要求 wait-free 解**：每个线程都必须在**有限步内**独立完成自己的决定，不依赖别的线程是否在跑、是否被挂起。换句话说，协议里不允许有"等别人"的忙等或阻塞——哪怕其他 n−1 个线程全部停摆，剩下那一个线程也必须能自己走完并决定出值。

这条要求是整个理论的地基，绝不能省。原因是：如果允许阻塞/加锁，那"用 read/write 变量 + 一把锁"就能平凡地解任意多线程共识（进临界区、写下第一个人的提议、大家读它），于是所有原语看起来"能力一样"，测量尺就废了。正是 **wait-free** 这把尺子的苛刻——不许等、不许假设别人在进展——才把不同原语的能力差异逼显出来：read/write 在这把尺子下立刻暴露出"连两个线程都协调不了"。初学者要把 wait-free 理解成"最坏情况下单打独斗也得赢"：它排除了一切"靠别人配合才成立"的取巧，剩下的才是原语**自身**的协调能力。wait-free/lock-free/obstruction-free 的完整层级在 CP-02.5 已铺垫，本课直接取用其中最强的 wait-free 当共识的验收标准。

### CP-04.1.3 FLP 视角：共识为何是"硬"问题

共识之所以配当测量尺，还因为它在很弱的模型里是**出了名的难**。分布式领域著名的 FLP 不可能性（Fischer-Lynch-Paterson 1985）指出：在**异步**、消息可任意延迟、且允许一个进程崩溃的模型里，**不存在**确定性的共识协议。共享内存里的对应结论就是本报告下一节的核心——只用 read/write 寄存器无法 wait-free 地解两线程共识。

初学者不必掌握 FLP 的证明，只需领会一个观念：共识"难"不是因为算法没想巧，而是**模型本身能力不够**时它就真的无解——这正是它当尺子的价值所在。一个原语如果能把这个"本质难"的问题解开、还能给越多线程解开，就说明它给系统注入了越强的协调能力。AMP 把共享内存版本的这类不可能性作为定理逐条证明；FLP 是消息传递版本的同源思想，本报告只借它点明"共识是公认的硬核问题"，分布式模型下的完整 FLP 归 L5 分布式系统课。

#### 来源与时效
- AMP 2nd/Revised ch5 §5.1「Consensus Numbers」（核实 2026-07-25）：consensus 对象定义（一致性 agreement + 有效性 validity）、wait-free 求解要求、以 consensus 当同步能力测量尺的动机。
- Herlihy「Wait-Free Synchronization」TOPLAS 1991 §1–3（核实 2026-07-29）：wait-free 共识作为刻画对象同步能力的核心问题、"有限步内不依赖他人完成"的 wait-free 定义原始出处。
- Fischer, Lynch & Paterson「Impossibility of Distributed Consensus with One Faulty Process」JACM 32(2):374–382, 1985（交叉印证"共识在弱模型下本质困难"，核实 2026-07-29）——本报告仅借其结论，分布式模型完整证明归 L5。
- 交叉一致，无冲突。agreement/validity 两条件表述以 AMP §5.1 原文为准。

## CP-04.2 consensus number：原语的能力刻度

### CP-04.2.1 consensus number 的定义

一个共享对象类型的 **consensus number**（共识数）定义为：用这种对象（可任意多个实例）外加任意多个 read/write 寄存器，能够 **wait-free 求解共识的最大线程数**。如果对任意大的 n 都能解，consensus number 就是 **∞**。

consensus number(T) = 能被 T 类型对象 wait-free 求解共识的最多线程数（能解任意多则为 ∞）

这个数字就是原语的"能力刻度"。要点在于"最大"这个词——consensus number 是 5，意思是"能解 5 个线程的共识，但解不了 6 个"；它是一个**阈值**，越过就失效。初学者要留意定义里"外加 read/write 寄存器"这个搭配：因为寄存器本身能力最弱（下节即见其 CN=1），把它们免费附赠给任何原语都不会拔高刻度，这样测出来的数字就干净地归功于被测原语本身。另一个易错点：consensus number 衡量的是**类型**（如"FIFO 队列这种对象"），不是某一次具体使用，所以它是该类型固有的、与实现无关的理论属性。

### CP-04.2.2 read/write 寄存器 = 1（最著名的不可能性结果之一）

普通的 **atomic read/write 寄存器 consensus number = 1**：用它们**连两个线程的共识都解不了**（能解 1 个线程是平凡的——一个线程自己跟自己"达成一致"）。等价的强命题是：

无法用 atomic 寄存器 wait-free 实现任何 consensus number > 1 的对象

这被 AMP 称为"计算机科学中最惊人的不可能性结果之一"。它的深远后果是：**队列、栈、计数器这些日常数据结构（下节将见它们 CN ≥ 2），根本不可能只用普通读写变量做出 wait-free 实现**——不是没人想到聪明写法，而是理论上禁止。证明的核心直觉（AMP 的"临界状态/valence"论证）是：考虑一个两线程共识协议即将分出胜负的那个"临界"时刻，两个线程各自的下一步只能是读或写某个寄存器，穷举这几种情况都能构造出一个执行，让某个线程无法分辨自己身处哪种局面，从而破坏 agreement 或永不终止。初学者记住结论即可：**只会读和写，两个线程就无法在最坏情况下可靠地"约定同一个值"**——这解释了为什么 CP-01 的纯软件互斥算法虽然精巧，却撑不起 wait-free 的并发对象，必须引入更强的硬件原语。

### CP-04.2.3 consensus number = 2 的一大族原语

一批**极常见**的原语和对象 consensus number 恰好 = 2：**FIFO 队列**（enqueue/dequeue）、**栈**、**test-and-set**、**swap**（原子交换）、**fetch-and-add**（原子加）。它们能给两个线程解共识，但**给不了三个**。

两线程能解的直觉（以 FIFO 队列为例，AMP 的经典构造）：预先往一个队列里塞两个标记 `WIN`、`LOSE`，每个线程先把自己的提议写进一个独立寄存器，再各自 dequeue 一次——**拿到 `WIN` 的线程赢**，两人都决定"赢家提议的那个值"（赢家读自己的、输家读赢家的寄存器）。队列的原子出队天然做到了"恰好一个人先拿到头元素"这个二选一裁决，所以能解两线程共识。但它卡在 2 上不能到 3：三个线程时无法只靠一次出队把"三选一"的裁决干净地做出来（AMP 给出严格的临界状态论证）。要点在于**这些原语的能力比 read/write 强（1→2），但离万能还差得远**——它们能协调一对线程，却无法协调任意规模。初学者可由此建立分层的第一印象：能力不是"有/无"的二元，而是有确切档位的连续刻度。

### CP-04.2.4 multiple assignment 与 Common2：中间档与卡点

**m 寄存器 multiple assignment 对象**（一条指令原子地写 m 个位置、并原子读回）consensus number = **2m − 2**：能解 2m−2 个线程，解不了 2m−1 个。例如原子地一次写 2 个位置（m=2）给出 CN = 2，写 3 个位置（m=3）给出 CN = 4。公式独占行：

consensus number(m-register multiple assignment) = 2m − 2

另一族结论是 **Common2**：AMP 证明凡是满足"**可交换**（commute）或**互相覆盖**（overwrite）"条件的 RMW 原语（test-and-set、swap、fetch-and-add 都属此类）consensus number **恰好 = 2**，无法更高。这解释了为什么上一节那一大族原语齐刷刷卡在 2：它们共享同一个结构性弱点。要点在于：consensus number 不是每种原语随机取值，而是由原语的**代数结构**（能否交换、能否覆盖、一次能原子触碰几个位置）决定的——multiple assignment 靠"一次多写几个位置"把刻度线性抬高，Common2 类原语则因结构受限被死死锁在 2。初学者不必记牢 2m−2 的证明，只需领会"能力刻度是可以精确计算、且由原语结构决定的"这一核心思想，这正是下一节层级的地基。

#### 来源与时效
- AMP 2nd/Revised ch5 §5.1–5.7（核实 2026-07-25）：consensus number 定义（§5.1）；atomic 寄存器 CN=1 与"无法 wait-free 实现 CN>1 对象"定理（§5.2–5.3）；FIFO 队列 CN=2 及双标记构造（§5.4）；m 寄存器 multiple assignment CN=2m−2（§5.5）；RMW 与 Common2 类 CN=2（§5.6–5.7）。
- Herlihy「Wait-Free Synchronization」TOPLAS 1991（核实 2026-07-29）：consensus number 概念、read/write=1、queue/test-and-set/fetch-and-add 等 =2 的原始结果与不可能性证明。
- 数值锚定：read/write=1、FIFO 队列/栈/TAS/swap/fetch-and-add=2、m 寄存器 multiple assignment=2m−2、Common2 类=2、CAS=∞（见 CP-04.4）——均由 AMP §5 与 Herlihy 1991 两源比对确认，未凭记忆填。
- 交叉一致，无冲突。术语记一笔：consensus number 亦称 consensus power / synchronization number，同义。

## CP-04.3 wait-free 同步层级：一座不可跨越的能力阶梯

### CP-04.3.1 层级的结构：按 consensus number 分层

把每种同步原语按其 consensus number 归档，就得到 Herlihy 的 **wait-free 同步层级**（consensus hierarchy）：

consensus number 1 —— atomic read/write 寄存器
consensus number 2 —— FIFO 队列、栈、test-and-set、swap、fetch-and-add、（m=2 的）multiple assignment……
consensus number 2m−2 —— m 寄存器 multiple assignment
consensus number ∞ —— compare-and-swap (CAS)、load-linked/store-conditional (LL/SC)、memory-to-memory move-and-swap……

这座阶梯把"原语有多强"从一句定性判断，变成了一个有精确台阶号的排序。要点在于：处在同一层的原语，wait-free 协调能力**等价**（都恰好能解那么多线程的共识）；层与层之间是**严格递增**的能力差。初学者可以把它类比成一把带刻度的尺子——每种硬件指令、每种并发对象都能在尺子上找到唯一的一格，格号越大越"万能"。这正是这门大主题的知识内核：同步原语的能力不是玄学，而是可测量、可排序的。

### CP-04.3.2 不可跨层实现定理：低层造不出高层的 wait-free 实现

层级最关键的定理是**不可跨层向上实现**：如果对象 A 的 consensus number 是 x、对象 B 的是 y，且 x < y，那么在 y 个（或更多）线程的系统里，**无法用 A（加寄存器）wait-free 地实现 B**。

理由是一个漂亮的反证（AMP 的核心论证）：假如能用 A 造出 B 的 wait-free 实现，那我们就能先用这个"山寨 B"、再用 B 本来能解的 y 线程共识协议，拼出一个只靠 A 的 y 线程 wait-free 共识协议——可这与"A 的 consensus number 只有 x < y"直接矛盾。所以这种实现不可能存在。这条定理的现实冲击力极大：它意味着**你在层级 2 的机器上（只有队列/TAS/fetch-add），无论多聪明都写不出一个能扛任意多线程的 wait-free 队列/栈/计数器**——因为那需要 ∞ 层的能力。初学者要抓住的因果是："consensus number 低"不只是"解共识差"，而是"**能实现的 wait-free 对象的复杂度被封顶**"——共识只是那个用来卡住天花板的探针。这也正面回答了本大主题的核心追问：read/write（层级 1）为何不行——它连往上爬一层都做不到；而下一节的 CAS（层级 ∞）为何万能——它站在阶梯顶端，向下无所不能造。

#### 来源与时效
- AMP 2nd/Revised ch5 §5.1–5.3 与 ch6 §6.1（核实 2026-07-25）：consensus hierarchy 的分层、同层能力等价、不可跨层向上 wait-free 实现定理及其反证论证。
- Herlihy「Wait-Free Synchronization」TOPLAS 1991 §4–5（核实 2026-07-29）：同步层级原始提出、"低共识数原语无法 wait-free 实现高共识数对象"的定理与证明。
- 交叉一致，无冲突。层级号即 consensus number，两源命名一致（consensus hierarchy / wait-free hierarchy）。

## CP-04.4 CAS 的通用性：compareAndSet 共识数 = ∞

### CP-04.4.1 compareAndSet 的语义与 CN = ∞

**compare-and-swap**（CAS，AMP 里写作 `compareAndSet`）是一条原子的读改写指令：给定一个内存位置、一个**期望旧值** expected 和一个**新值** new，它原子地检查该位置当前是否等于 expected，**相等则写入 new 并返回成功、不等则不动并返回失败**。它的 consensus number = **∞**：

consensus number(compareAndSet) = ∞

也就是说，CAS 能给**任意多**线程 wait-free 地解共识，站在层级的最顶端，因此被称为**通用**（universal）原语。现代主流 CPU 都提供 CAS（x86 的 `CMPXCHG`、ARM 的 LL/SC 家族），这正是为什么它是并发编程的基石。要点在于对比：上一档的 fetch-add / 队列卡在 2，就差 CAS 这一步"**先验旧值再决定写不写**"的条件性——正是这个"条件写"给了它无限能力。

### CP-04.4.2 为什么 CAS 能解任意多线程共识

CAS 解 n 线程共识的协议短到可以一眼看懂：设一个共享位置初始化为特殊值 `⊥`（表示"还没人赢"），每个线程先把自己的提议写进一个以线程号索引的数组 `announce[i]`，然后对那个共享位置做一次

CAS(位置, ⊥, i)   // 期望还是 ⊥，就把它改成我的线程号 i

**恰好第一个成功的线程把 `⊥` 改成了自己的编号**，此后所有 CAS 都会失败（因为值已不再是 `⊥`）；于是每个线程读出该位置最终存的那个编号 `w`，一致地决定 `announce[w]` ——即赢家的提议。

这个协议对**任意** n 都成立、且每个线程至多几步就返回（wait-free），所以 CN = ∞。核心直觉是 CAS 提供了一次**全局唯一的、原子的"抢占裁决"**：无论多少线程同时冲上来，"把 ⊥ 改成自己"这件事**有且只有一个人能成功**，这个天然的"唯一胜出者"正是共识所需要的。对比 CP-04.2 里队列只能做"二选一"裁决、卡在 2，CAS 的"n 选一"裁决对规模无上限——差别就在 CAS 能**先读到旧状态、据此决定是否写**，而 fetch-add / swap 只能盲目地改。初学者把 CAS 记成"**看一眼，对得上才改**"的原子操作即可，它的"看一眼再决定"就是超越 Common2 那一档的关键能力。

### CP-04.4.3 语言级落地：C23 的 atomic_compare_exchange

在 C 语言里，CAS 由 `<stdatomic.h>` 的 `atomic_compare_exchange_strong` / `atomic_compare_exchange_weak` 提供（ISO/IEC 9899:2024 §7.17，C11 首次引入、C23 现行）。它们原子地"比较 `*obj` 与 `*expected`、相等则写 `desired`"，正是上面理论 CAS 的工程接口。

初学者需要知道 strong 与 weak 的区别属于 CP-06 的地盘：weak 版允许**伪失败**（spurious failure，即使值相等也可能偶尔返回失败，通常用在循环里以换取某些平台上更优的代码生成），strong 版不会伪失败。本报告只把它当作"CAS 在现行标准里确有其对应指令"的印证锚点——CN=∞ 是理论结论，`atomic_compare_exchange` 是它在真实语言中的化身；接口细节、六档 memory_order 参数、ABA 陷阱等一律归 CP-06 与 CP-10，本课不展开。可选实机验证（编一个 `-std=c11` 的 CAS 最小例）本报告未取，理由见抬头：理论 CN 值不靠单次运行来立论。

#### 来源与时效
- AMP 2nd/Revised ch5 §5.8「The compareAndSet() Operation」（核实 2026-07-25）：`compareAndSet` 语义、consensus number=∞、n 线程共识协议（CAS 抢占裁决 + announce 数组）。
- Herlihy「Wait-Free Synchronization」TOPLAS 1991（核实 2026-07-29）：compare&swap（以及 LL/SC、memory-to-memory move/swap）为 universal、consensus number 无界的原始结果。
- ISO/IEC 9899:2024（C23）§7.17 `atomic_compare_exchange_strong/weak`（现行标准，2024-10-31 发布；工作草案 N3220 / cppreference c/atomic 指路、结论回落 ISO，核实 2026-07-29）：CAS 的语言级接口印证。
- 分账指路：strong/weak 差异、memory_order、ABA 归 CP-06/CP-10。
- 交叉一致，无冲突。CAS=∞ 由 AMP §5.8 与 Herlihy 1991 双源锚定。

## CP-04.5 universal construction：用共识对象造任意 wait-free 对象

### CP-04.5.1 universal 的定义：从"能解共识"到"能造万物"

一类对象 C 被称为 **universal**（通用），如果用若干个 C 类对象加 read/write 寄存器，**能 wait-free 实现任何（有顺序规约的）并发对象**。第 6 章的核心定理是：**consensus number = ∞ 的对象（如 CAS）就是 universal 的**——即"能给任意多线程解共识"与"能造任意 wait-free 对象"是**等价**的。

这一步把"CAS 万能"从 CP-04.4 的一句断言，升级为一个**建设性**结论：不只是"CAS 共识数高"，而是给出了一台通用机器——只要有共识对象，任何你想要的线性一致并发数据结构（队列、栈、树、映射……）都能被 wait-free 地造出来。要点在于这是一个"存在性 + 构造性"的双重保证：**存在**（理论上办得到）且**给了做法**（下两节的构造）。初学者要体会这条等价的分量：它意味着 consensus 不只是一把测量尺，共识对象本身就是**并发世界的通用积木**——搞定共识，就搞定了一切 wait-free 对象。

### CP-04.5.2 lock-free universal construction：共识串起一条操作日志

通用构造的骨架是：把任意对象的状态表示成"**从初始状态出发、已执行的操作序列（日志）**"，所有线程要做的就是**就"下一个该执行哪个操作"反复达成共识**，把各自的操作一个接一个地串进这条公共日志里；每个线程在本地按这条一致的日志重放，就得到一致的对象状态。

lock-free 版本用一个共识对象（由 CAS 实现）来决定"日志的下一格是谁的操作"：线程们竞争往日志尾部追加自己的操作，**共识裁决出唯一的赢家**占住那一格，输家带着自己还没排上的操作重试下一格。关键直觉是把"并发地操作一个复杂对象"归约成"反复地就一个简单问题（下一步是谁）达成一致"——而达成一致正是共识对象的看家本领。这保证了 **lock-free**（总有线程在推进日志、系统整体不停滞），但单个倒霉线程可能反复竞争失败、迟迟排不上号，所以还不是 wait-free。初学者可把它想成"一群人往同一个记事本上排队记账，每记一行都要先投票定'这行归谁'"——投票用的就是共识。

### CP-04.5.3 wait-free universal construction：用 helping 消灭饥饿

要从 lock-free 升到 **wait-free**（每个线程都在有限步内完成，无人饿死），构造里再加一招 **helping**（帮助/协助）机制：每个线程把自己"想执行的操作"**公示**在一个共享的 `announce` 数组里；任何一个正在推进日志的线程，在追加操作时不只顾自己，还会**扫一眼公示栏、优先把最"落后"（等待最久）的那个线程的操作替它排进日志**。

这样一来，一个线程即便自己抢不到日志格，只要**别人在推进，就迟早会被别人"帮"着把它的操作排进去**，于是人人都在有限步内完成——达成 wait-free。核心直觉是把"只顾自己"的竞争改成"人人搭把手"的协作，用共识对象的裁决 + 公示栏的可见性，保证没有线程被无限期落下。这就完成了第 6 章的终极目标：给出一个**具体的、wait-free 的**通用构造，证明"有了 ∞ 共识对象（CAS），任意 wait-free 并发对象都造得出来"。初学者要抓住 lock-free→wait-free 的这一跃的实质就是 helping——从"各自竞争、允许饿死"到"互相帮助、保证人人进展"。

### CP-04.5.4 意义：闭合"CAS 为何万能"的论证环

把五节连起来看：共识是测量尺（04.1），量出每种原语的刻度 consensus number（04.2），刻度排成不可跨越的层级（04.3），CAS 站在 ∞ 顶端（04.4），而 universal construction 反过来证明"站在 ∞ 顶端"就等于"能建造一切 wait-free 对象"（04.5）。

这条环给出了本大主题最重要的实践结论：**现代多核机器普遍提供 CAS，正是因为它是 universal 的——有了它，操作系统与并发库理论上就能构造出任意想要的 wait-free/lock-free 数据结构**，而这是只有 read/write 的机器（层级 1）永远做不到的。初学者到此应能清楚回答开篇的追问：CAS 万能，是因为它共识数无穷、而共识数无穷等价于能通用构造一切；read/write 不行，是因为它共识数是 1、连往上爬一层都被不可能性定理挡死。至于这些理论上的通用构造在工程上为何常被更专门、更快的手写 lock-free 结构取代（通用构造开销大），属实践权衡，归 CP-10 无锁数据结构，本课点到为止。

#### 来源与时效
- AMP 2nd/Revised ch6 §6.1–6.4「Universality of Consensus」（核实 2026-07-25）：universal 对象/类定义（§6.2）、CN=∞ ⇔ universal 的等价、lock-free 通用构造（§6.3，共识串日志）、wait-free 通用构造与 helping/announce 机制（§6.4）。
- Herlihy「Wait-Free Synchronization」TOPLAS 1991 §6（核实 2026-07-29）：universal object 概念、用共识对象 wait-free 实现任意对象的原始通用构造与 helping 思想。
- 分账指路：通用构造 vs 专门手写 lock-free 结构的性能权衡、ABA、内存回收归 CP-10；CAS 接口细节归 CP-06。
- 交叉一致，无冲突。"CN=∞ 等价于 universal"由 AMP ch6 与 Herlihy 1991 §6 双源确认。
