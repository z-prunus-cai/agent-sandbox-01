# L4-05·大主题05 内存一致性模型与重排序

> 基线 Py3.11.15/np2.4.6/gcc13.3 @2026-07-25 ｜ 核实日期：2026-07-30 ｜ 先修：本课大主题01（互斥的正确性性质）、大主题02（顺序一致性 SC、线性一致性、并发对象规约）、L4-01·OS-04（线程与共享地址空间）、L4-01·OS-06（硬件原语造锁、`LOCK` 前缀的原子性来源）｜ 一手锚点：Intel® 64 and IA-32 Architectures Software Developer's Manual (SDM) Vol.3A 第 8 章「Multiple-Processor Management」§8.2「Memory Ordering」（§8.2.2 排序原则、§8.2.3 例子；章/节号随版本微调，以现行 SDM 目录为准）与 AMD64 Architecture Programmer's Manual Vol.2 §7「Memory System」为硬件语义主一手；ISO/IEC 14882 C++ `[intro.races]`（happens-before / DRF）与 ISO/IEC 9899:2024 (C23) §5.1.2.4「Multi-threaded executions and data races」为语言级模型主一手；Sewell/Sarkar/Owens/Zappa Nardelli/Myreen「x86-TSO: A Rigorous and Usable Programmer's Model for x86 Multiprocessors」CACM 53(7):89–97, 2010 为 TSO 形式化交叉源；AMP 第 3 章 + 附录 B（内存一致性模型）为教材印证｜ 成熟度：GA/稳定（x86=TSO 自 2007–2009 官方澄清后未变；C11/C++11 内存模型自 2011 定型，C23 措辞微调；ARMv8 于 2017–2018 修订为 multi-copy-atomic，属演进项，见 CP-05.4 硬标）

> 粒度判定：**1 份，不拆**。本大主题在 prompt 中被提示"内容偏多、很可能拆 -a/-b"；实做时 6 个小主题（CP-05.1–05.6）共享一条**单一主线**——先立"程序员该拿什么当心智基线（SC，05.2）、编译器又替我们保证了什么（DRF-SC，05.1）"，再落到"真实硬件到底放松了哪些序（x86-TSO 只放 store→load，05.3；ARM/POWER 放得更多，05.4）"，接着说清"重排其实有编译器与硬件两级、`volatile` 为何不是原子（05.5）"，最后用几组经典 litmus（SB/MP/IRIW，05.6）把前面的抽象规则钉到可判读的具体样式上。六节层层递进、互为支撑，拆开反而割裂主线，故按 report-format v3 §一默认 1 大主题 = 1 报告，合为一份；篇幅可控。

> 本报告一条主线心智模型：**你写下的内存访问顺序，不是任何人真正执行的顺序**。编译器为优化会重排、CPU 为流水线与缓冲会重排，于是"线程 A 明明先写 x 再写 y，线程 B 却看见 y 变了而 x 没变"这种反直觉现象会真实发生。内存一致性模型就是一纸**契约**：它规定"在没有数据竞争的前提下（你正确地用了锁/原子），系统承诺让程序看起来像顺序一致（SC）"——这就是 DRF-SC 定理，是本主题的定海神针。反过来，一旦你越过原子/锁去裸读写共享变量，契约作废，重排就会咬人。x86 是最"温柔"的主流硬件（只放松 store→load 一项），ARM/POWER 放松得多；但**语言层**（C/C++）为了可移植，默认按最弱模型给你保证，所以正确的并发代码要靠 happens-before 而不是靠"我这台机器恰好是 x86"。

> 分账（本课不外扩，只在交界处一句指路）：内存模型整体、happens-before/DRF、硬件重排与屏障的**语义**归本课（CP-05 主场）；六档 `memory_order` 的**语言 API 细节**（relaxed/acquire/release/… 怎么写、fence 怎么调）归 CP-06；数据竞争的**四要件精确定义与 UB 后果、TSan 检测**归 CP-07；`smp_mb()`/`LOCK` 前缀等屏障在**内核/syscall 路径**的落地与代价归 L4-01·OS-06；顺序一致性/线性一致性作为**并发对象正确性条件**的定义框架归大主题02。本报告只把这些点指路，不深挖。

> 本报告以多来源比对为主承重腿：硬件项以 Intel SDM / AMD64 APM 官方文本为准、x86-TSO 论文交叉印证；语言项以 ISO C++/C 标准文本为准、AMP 附录与权威表格印证；跨平台弱序项（ARM/POWER）本机为 x86-64、**无 ARM/POWER 硬件不可直测**，相关结论一律标「跨平台·未实测」并回落一手规范/论文。可选本机实证（手写 pthread 复现 SB litmus、`objdump` 看 `seq_cst` 生成的 `mfence`/`lock`）**本报告未取**，如实标注——因为其结论（"x86 观测到 r1=r2=0""seq_cst store 生成带锁指令"）已由 SDM 原文 + x86-TSO 论文 + C/C++ 到指令的公开映射表多源锚定，实测仅为加固、非承重，缺之不影响以上论断。所有内存序记号、litmus 结果、屏障指令均独占行。

---

## CP-05.1 happens-before 与数据竞争自由（DRF）

### CP-05.1.1 happens-before：并发世界里唯一可靠的"先后"

在单线程里，"先写后读就能读到刚写的值"是天经地义的，因为程序里语句有明确先后。可跨线程时，"线程 A 的写"和"线程 B 的读"之间默认**没有任何先后关系**——它们是并发（concurrent）的，谁先谁后不确定，甚至"B 到底看不看得见 A 的写"都不确定。内存模型于是引入一个偏序关系 **happens-before**（记 `hb`）来精确刻画"哪些先后是系统保证的"。只有当

A 的写 happens-before B 的读

成立时，B 才**保证**看得见 A 的这次写（以及 A 在该写之前的所有写）。

happens-before 由两块拼起来。第一块是**单线程内的程序序**（C++ 里叫 sequenced-before）：同一线程里写在前面的语句 hb 写在后面的。第二块是**跨线程的同步关系**（inter-thread happens-before）：某些成对的同步操作会在两个线程之间"架一座桥"——最典型的是"线程 A 的 release 写"与"线程 B 读到该值的 acquire 读"配对，一旦 B 的 acquire 读到了 A 的 release 写的值，就建立 `A 的 release 写 hb B 的 acquire 读`（这套 API 细节归 CP-06.3）。锁的 `unlock` hb 之后对同一锁的 `lock`、线程创建 hb 被创建线程的第一条语句、`thread::join` 之类也都是同步边。把程序序和同步边传递闭包起来，就得到整张 happens-before 图。

初学者最该扭转的直觉是：**"时间上先发生"不等于 happens-before**。墙上时钟里 A 的写确实先于 B 的读，但只要它们之间没有一条同步边，`hb` 就不成立，B 就**可能读不到**A 的写（编译器/CPU 有权装作没看见）。所以并发正确性不能靠"我觉得它先跑"，只能靠显式建立 happens-before。这个概念源自 Lamport 1978 年对分布式系统"事件先后"的定义，被 C/C++/Java 内存模型搬进来做因果序的骨架。

### CP-05.1.2 数据竞争的定义（本课只给内存模型视角，四要件精讲归 CP-07）

**数据竞争**（data race）指：两个来自不同线程的内存访问，访问了同一内存位置，其中至少一个是写，且它们之间**没有** happens-before 关系（既不 `A hb B` 也不 `B hb A`），并且这些访问不是同步操作（非原子的普通读写）。

把它和 happens-before 摆一起看就顺了：happens-before 是"系统保证的先后"，数据竞争恰恰是"两个冲突访问之间**缺了**这种保证的先后"。换句话说，只要你为每一对冲突访问都建立了 happens-before（用锁、或用配对的 acquire/release 原子），程序就是**数据竞争自由**（data-race-free, DRF）的。反过来，任何一对冲突的普通访问之间没搭上同步边，就是一个 race。这里只给内存模型口径的定义，四要件的逐条拆解、竞争即 UB 的后果、TSan 怎么抓，归 CP-07。

### CP-05.1.3 DRF-SC 定理：无竞争则似顺序一致

内存模型给程序员的核心承诺，就是这条 **DRF-SC 定理**（也叫 SC-for-DRF）：

若程序在所有 SC 执行下都无数据竞争，则该程序的所有执行都表现为顺序一致（SC）。

它的分量在于把"弱内存模型"这件吓人的事，对**绝大多数程序员**关起门来：只要你老老实实用锁或（顺序一致的）原子把每对冲突访问都同步好、做到 DRF，你**根本不用去想** store buffer、乱序执行、TSO 这些底层细节——系统保证你看到的行为等价于"所有线程的操作按某个全局顺序一次一个地交错执行"（即 SC，见 CP-05.2）。弱内存模型的复杂度只会在你**故意**放松（用 relaxed 原子、或写了 race 的代码）时才暴露出来。

要点与易错点有三。其一，前提是"无竞争"，一旦有 race，DRF-SC 不再保证任何东西，程序进入 UB（C/C++）或极弱的语义（Java 为安全性给了兜底但很反直觉）。其二，"无竞争"要用**同步操作**（锁 / 至少 acquire-release 级别、通常讨论时用 seq_cst 原子）来达成；用 relaxed 原子虽然消除了"竞争即 UB"，却**不**恢复 SC 假象，所以 relaxed 代码仍可能观测到重排。其三，这条定理的现代形式由 Adve & Hill（1990，"Weak Ordering — A New Definition"）奠基、Boehm & Adve（2008）为 C++ 内存模型落地，是 C11/C++11 之后语言标准的设计基石。

#### 来源与时效
- ISO C++ `[intro.races]`（现行标准，核实 2026-07-30）：happens-before、sequenced-before、inter-thread happens-before、数据竞争定义、以及"无数据竞争程序按 SC 语义执行"的保证条款（cppreference c/cpp「memory model」指路，结论回落 ISO 文本）。
- ISO/IEC 9899:2024 (C23) §5.1.2.4「Multi-threaded executions and data races」（核实 2026-07-30）：C 侧等价定义，与 C++ 措辞对齐。
- Lamport, "Time, Clocks, and the Ordering of Events in a Distributed System", CACM 21(7):558–565, 1978：happens-before 概念源头。
- Adve & Hill, "Weak Ordering — A New Definition", ISCA 1990；Boehm & Adve, "Foundations of the C++ Concurrency Memory Model", PLDI 2008：DRF-SC 定理及其 C++ 落地。
- AMP 附录 B（内存一致性模型）与第 3 章：以"SC 是程序员心智基线、DRF 让你安全地忽略弱序"的教材口径印证，与标准一致，无冲突。

## CP-05.2 顺序一致性（SC）作为程序员心智基线

### CP-05.2.1 SC 的精确定义（回顾大主题02，此处作参照系）

**顺序一致性**（sequential consistency, SC）由 Lamport 1979 年定义：一个多线程执行是 SC 的，当且仅当存在一个把所有线程的所有内存操作排成的**单一全局顺序**，满足两条——

其一，每个线程自己的操作在这个全局顺序里保持它的程序序；

其二，每个读操作读到的，是这个全局顺序里排在它前面、对同一位置的**最后一次**写的值。

直白说，SC = "所有操作像被塞进一台单核机器、一次执行一个，且各线程内部不打乱顺序"。这正是大多数人脑子里默认的并发模型：好像有一个全局的、大家都同意的操作时间线。

### CP-05.2.2 为什么把 SC 当"基线"而不是"现实"

要害在于：**真实硬件和编译器几乎都不提供 SC**（提供的话性能代价太大——每次访存都要等全局可见）。SC 之所以还叫"基线"，是因为它是**推理的起点**和 DRF-SC 定理的**目标**：你按 SC 去想代码对不对，只要保证了 DRF，系统就替你把"看起来像 SC"这件事兑现（CP-05.1.3）。于是 SC 扮演的是"程序员心智契约的甲方期望"，弱内存模型是"乙方在你守约（DRF）时承诺交付 SC 假象、你违约（race）时不保证"。

一个最小例子把 SC 与非 SC 的分界讲清。考虑两个共享变量 `x=y=0`，线程 1 执行 `x=1; r1=y;`，线程 2 执行 `y=1; r2=x;`。在 SC 下，把四个操作排成任何合法全局序，都**不可能**出现 `r1==0 && r2==0`：因为 `r1==0` 要求"读 y"排在"写 y"之前，`r2==0` 要求"读 x"排在"写 x"之前，再叠加两线程各自的程序序（写在读前），四条约束会成环、无法排成全序。所以

SC 禁止 r1==r2==0

而这恰恰是 x86 都会违反的那一个样式（SB litmus，见 CP-05.3 / CP-05.6）——真实硬件不是 SC，就从这里露馅。

初学者易混两点。其一，SC **不**要求任何特定的交错，只要求"存在某个一致的全局交错"；所以 SC 下结果仍可以不确定（比如两线程都写同一变量，谁最后写是不定的），SC 约束的是"合法性"不是"确定性"。其二，SC 比线性一致性（大主题02）**弱**：SC 只要求全局序尊重各线程**内部**程序序，不要求它尊重**跨线程的真实时间先后**；线性一致性额外要求"若操作 A 在真实时间上整个先于 B，则全局序里 A 也在 B 前"，且可组合。谈内存模型时用 SC 就够，谈并发对象正确性时才升到线性一致性。

#### 来源与时效
- Lamport, "How to Make a Multiprocessor Computer That Correctly Executes Multiprocess Programs", IEEE Trans. Computers C-28(9):690–691, 1979（核实 2026-07-30）：SC 的原始定义（两条件）。
- AMP 第 3 章「Concurrent Objects」§3.4 与附录 B：SC 定义、SC 与线性一致性的强弱关系（linearizability 更强、可组合，SC 不可组合）——与大主题02 报告一致。
- ISO C++ `[intro.races]` 关于 seq_cst 原子的"单一全序 S"条款：语言层用 `memory_order_seq_cst` 恢复 SC 假象的机制，与 Lamport SC 对齐。
- 交叉一致，无冲突。术语记一笔：SC 谈的是"内存操作全序"，与大主题02 谈的"方法调用线性化"是不同粒度但同源的思想。

## CP-05.3 x86-64 TSO：store buffer 与唯一的 store→load 放松

### CP-05.3.1 四种重排与 x86 到底放松了哪一种

把"两条访存指令能否被观测到调换顺序"按类型分成四种：Load→Load、Load→Store、Store→Store、Store→Load（读作"先 X 后 Y 的程序序，能否被看成 Y 在 X 前"）。x86-64 的内存模型是 **TSO**（Total Store Order），它对这四种的态度是：

Load→Load：不放松（保持程序序）

Load→Store：不放松

Store→Store：不放松（同一处理器的多次写按程序序对外可见）

Store→Load：**放松**（唯一被允许的重排：后面的 load 可以越过前面到**不同**地址的 store 先执行）

也就是说，x86 只允许一种重排：一个 load 可以被提前到"程序里排在它前面、但写的是不同地址的 store"之前完成。写同一地址时不放松（后续 load 一定看到自己刚写的值，即 store-forwarding 保证的自读一致）。这是 Intel SDM Vol.3A §8.2 与 AMD64 APM Vol.2 §7 官方澄清的模型，x86-TSO 论文（Sewell et al. 2010）给了它精确的数学形式。**务必以规范核实、不要凭记忆扩大**：x86 放松的就这一项，别的三种都不放松。

### CP-05.3.2 store buffer：这项放松的物理来源

为什么偏偏是 store→load？根子在 **store buffer**（存储缓冲区）。CPU 执行一条写指令时，不会傻等这次写穿透到缓存/内存对所有核可见（那要几十上百周期），而是先把写"暂存"进本核私有的 store buffer 就让指令退休、继续往下跑。于是：

后面的 load（读别的地址）可以在前面的 store 还压在 store buffer 里、尚未对其他核可见时，就先去缓存把值读回来——

从别的核的视角看，就成了"这个核的读跑到了它的写前面"，即 store→load 重排。而 Load→Load、Store→Store 之所以不乱，是因为 store buffer 是 **FIFO**（写按序排出），读也按序发射；写同一地址能被本核 load 直接从 store buffer 取回（store forwarding），所以自己总能读到自己最新的写，只有"别人何时看见我的写"被推迟了。

一个最小心智画面：每个核面前放一个"待寄出的信件筐"（store buffer）。你写变量 = 把信投进自己的筐（别人还没收到）；你读变量 = 先翻自己筐里有没有（有就读自己的），没有才去公共信箱（内存）拿。于是"我刚写了 x（信在筐里），又去读 y（公共信箱里 y 还没被对方更新）"——在旁观者看来，就像我"先读了 y 再写 x"。这正是 SB litmus 里 `r1==r2==0` 能发生的物理原因。

### CP-05.3.3 用什么把 store→load 也摁住

当你**确实**需要禁止这项放松（例如手写 Peterson 锁、或实现 seq_cst 语义）时，x86 提供全屏障 `mfence`；此外任何带 `LOCK` 前缀的读改写指令（如 `lock xchg`、`lock cmpxchg`、`lock add`）以及 `xchg`（对内存操作数隐含 lock）都会起到"排空 store buffer、串行化"的全屏障效果。这也是为什么

x86-64 上 `atomic_store(seq_cst)` 常被编成 `mov` + `mfence`，或直接用 `xchg`（隐含 lock）

而

x86-64 上 `atomic_load(seq_cst)` 通常就是一条普通 `mov`（无需屏障）

——因为 x86 从不放松 Load→Load / Load→Store，读侧的 SC 语义已由硬件白送，只有写侧的 store→load 需要额外摁住。这个 C/C++ 内存序到 x86 指令的映射由公开权威表格（Batty/Sewell 等维护的"C/C++11 mappings to processors"）给出，本机 `objdump -d` 复核为**可选实证、本报告未取**，但结论已由 SDM + 该映射表双源锚定。屏障在内核路径的具体形态（`smp_mb()` 家族）归 L4-01·OS-06，本课不展开。

#### 来源与时效
- Intel SDM Vol.3A 第 8 章 §8.2「Memory Ordering」（§8.2.2 排序原则列表、§8.2.3 例子；节号随版本微调，以现行 SDM 目录为准，核实 2026-07-30）：Load 不与 Load 重排、Store 不与 Store 重排、Store 不与更早的 Load 重排、Load 可与更早的**不同地址** Store 重排（唯一放松），并对同址不放松；`LOCK`/`mfence` 的串行化语义。
- AMD64 APM Vol.2 §7「Memory System」（核实 2026-07-30）：AMD 侧等价的 TSO 描述，与 Intel 对 store→load 放松的表述一致（历史上早期 Intel/AMD 文档曾各有含糊，x86-TSO 论文指出并统一之——分歧属"早期文档歧义"，现行两家一致，记一笔）。
- Sewell et al., "x86-TSO", CACM 53(7):89–97, 2010（核实 2026-07-30）：TSO 的抽象机（每核 FIFO store buffer + 全局锁）形式化，是"唯一放松 store→load、来源是 store buffer"的权威交叉源。
- 冲突项：无实质冲突；仅"早期 Intel/AMD 规范措辞含糊、部分过弱或不自洽"由 x86-TSO 论文指出，现行官方文档已澄清为 TSO。

## CP-05.4 弱序模型对照：ARM / POWER（跨平台·本机未实测）

### CP-05.4.1 弱序模型放松了什么

ARM（AArch64）与 POWER（PowerPC）采用**弱序**（weakly-ordered / relaxed）内存模型：相比 x86-TSO 只放 store→load 一项，它们默认**四种重排都可能发生**（Load→Load、Load→Store、Store→Store、Store→Load 都不保证按程序序对外可见），除非你插入显式屏障或用带序的原子指令。

直觉是：这些架构为省电与吞吐，给编译器/硬件更大的重排自由，把"要不要保证顺序"的决定权更多地交回给程序员。代价是裸并发代码的行为更反直觉——在 x86 上"碰巧能跑对"的许多无屏障写法，搬到 ARM/POWER 上会真的坏掉。这也是"不能靠机器恰好是 x86 来保证正确性、要靠 happens-before"（CP-05.1.1）的现实理由。

### CP-05.4.2 屏障与依赖：弱序机器上的顺序工具

弱序机器提供分级屏障而非只有一种全屏障。ARMv8 有 `DMB`/`DSB`（数据内存/同步屏障，可带 `ISH`/`LD`/`ST` 域限定作用范围）与带 acquire/release 语义的 load/store 指令（`LDAR`/`STLR`）；POWER 有 `sync`（重量级全屏障）、`lwsync`（轻量级，够 acquire/release）、`isync` 等。C/C++ 的 `memory_order_acquire`/`release` 在这些平台上就编译成对应的带序指令或轻量屏障（API 细节归 CP-06）。

弱序模型还有一个 x86 上不太强调的机制——**地址/数据/控制依赖**天然提供一定顺序（`memory_order_consume` 的初衷即源于此），但依赖定序脆弱、易被编译器优化掉，实践中已不推荐依赖它，这里只点到为止。

### CP-05.4.3 multi-copy atomicity：x86 与 POWER 的一条深层分界（含 ARMv8 演进硬标）

除了"放松哪几种重排"，还有一条更深的分界：**写的可见是否"一次对所有核统一"**，即 multi-copy atomicity（多副本原子性）。

x86-TSO 是 **multi-copy atomic**：一次写一旦离开 store buffer 变得可见，就是**同时对所有其他核可见**，不存在"A 核先看见、B 核后看见"。

POWER 是 **non-multi-copy-atomic**：一次写可以在不同时刻被不同核看见，"写还没传播到所有人"是合法状态。

ARMv8 是**演进项，须硬标**：早期 ARMv8 模型也是 non-multi-copy-atomic，但 ARM 在 **2017–2018 年将架构修订为 other-multi-copy-atomic**（Pulte/Flur/Deacon/Sewell et al., POPL 2018 "Simplifying ARM Concurrency"）——即从"其他核之间"看写是多副本原子的，向 x86 那侧靠拢了一步。所以谈 ARM 时必须交代**版本**：现行 ARMv8/ARMv9 是 (other-)multi-copy-atomic，老资料里"ARM non-MCA"的说法已过时。

这条分界的可判读表现就是 IRIW litmus（CP-05.6.3）：multi-copy-atomic 模型**禁止** IRIW 的反常结果，non-multi-copy-atomic 模型**允许**。于是 x86 禁止、现行 ARMv8 禁止、POWER 允许。

本机基线为 x86-64（Linux 6.18.5），**无 ARM/POWER 硬件，以上跨平台行为一律未实测**，结论全部回落各架构官方规范与上述权威论文；任何"ARM 上会/不会出现某结果"的判断在本报告中不作实测坐实，标「跨平台·未实测」。

#### 来源与时效
- ARM Architecture Reference Manual for A-profile (ARMv8-A/ARMv9-A)「The AArch64 Application Level Memory Model」章（核实 2026-07-30，⚙演进项）：弱序、`DMB`/`DSB`/`ISB`、`LDAR`/`STLR` acquire/release 指令、(other-)multi-copy-atomic 属性。
- Power ISA (OpenPOWER) Book II「Storage Model」/「Memory Coherence」章：weakly consistent、`sync`/`lwsync`/`isync`、non-multi-copy-atomic。
- Pulte, Flur, Deacon, French, Sarkar, Sewell, "Simplifying ARM Concurrency: Multicopy-Atomic Axiomatic and Operational Models for ARMv8", POPL 2018（核实 2026-07-30）：ARMv8 由 non-MCA 修订为 MCA 的权威记录，IRIW 判据。
- Alglave et al., "Herding Cats"（TOPLAS 2014）与 x86-TSO 论文：x86 multi-copy-atomic、POWER non-MCA 的对照。
- ⚠ 时效红旗：ARM multi-copy-atomicity 属 2017–2018 架构修订，引用时须锚版本；本机 x86 不可直测 ARM/POWER，相关结论「跨平台·未实测」。
- 冲突项：不同年代 ARM 资料对"是否 multi-copy-atomic"结论相反，根因是 2017–2018 修订前后模型不同——两边都记、以现行 ARMv8/v9 为 MCA。

## CP-05.5 编译器重排 vs 硬件重排；`volatile` 不等于原子

### CP-05.5.1 重排有两级，且相互独立

程序员写下的源码顺序，要经过两道"可能打乱它"的关口，缺一不可地都要防：

第一级，**编译器重排**：优化器（`-O2` 等）会为了寄存器分配、公共子表达式消除、指令调度等，在**不改变单线程可观测行为**的前提下自由调换、合并、甚至删除内存访问。它只对"单线程语义"负责，对"别的线程怎么看"一无所知。

第二级，**硬件重排**：CPU 的 store buffer、乱序执行、缓存一致性协议会在运行时进一步打乱写/读对其他核可见的顺序（就是 CP-05.3/05.4 讲的那些）。

两级独立叠加。关键结论：**即使跑在 x86（硬件几乎不重排）上，编译器仍可能把你的代码重排坏掉**。所以"x86 是强序、我不用管顺序"是错的——你至少要防住编译器那一级。正确姿势是用 C/C++ 原子（`atomic_*` + 合适的 `memory_order`）或锁，它们**同时**约束编译器和硬件两级：编译器看到原子操作就不敢跨它乱排，并在需要时吐出硬件屏障指令。

### CP-05.5.2 `volatile` 为什么不是原子、也不建立 happens-before

C/C++ 的 `volatile` 常被初学者误当成"线程安全"关键字，这是危险的误解。`volatile` 只保证两件很窄的事：其一，每次访问该变量都真的去内存读/写、编译器不得把它优化进寄存器或消除（防的是编译器的**某些**优化）；其二，对同一个 volatile 对象的多次 volatile 访问之间不被编译器互相重排。它**不**提供的：

不保证访问是原子的（大对象、甚至非对齐访问都可能被撕裂成多次）

不发出任何硬件内存屏障（挡不住 store buffer 等硬件重排）

不建立跨线程 happens-before（所以两个线程用 volatile 通信仍是数据竞争、仍是 UB）

一句话总结：`volatile` 是给"内存映射 I/O 寄存器 / `sigatomic` / setjmp 局部"这类**编译器别乱动它**的场景用的，**不是**并发同步工具。要在线程间安全共享，必须用 `_Atomic`/`std::atomic`（CP-06）或锁。这里有一个著名的平台差异陷阱：微软的 MSVC 曾给 `volatile` 附加 acquire/release 语义（`/volatile:ms`），于是有人误以为 `volatile` 能跨平台做同步——那是**编译器扩展、非标准语义**，标准 C/C++ 的 `volatile` 绝无此保证，不可移植。

一个最小易错例子：两线程用 `volatile int flag` 做"生产者置位、消费者轮询"的消息传递，且数据 `data` 是普通变量。即便加了 `volatile`，`data` 的写与读之间没有 happens-before，消费者可能看见 `flag==1` 却读到 `data` 的旧值（编译器/硬件把 `data` 写重排到 `flag` 写之后）。正确写法是把 `flag` 换成 release-store / acquire-load 的原子，靠配对建立 happens-before（MP 惯用法，CP-06.3）。

#### 来源与时效
- ISO C++ `[intro.races]` 与 `[dcl.type.cv]`（volatile 语义）、ISO/IEC 9899:2024 §6.7.4「Type qualifiers」与 §5.1.2.4（核实 2026-07-30）：volatile 只约束抽象机的 volatile 访问、不提供原子性/屏障/同步；跨线程共享须用原子对象或有序同步。
- cppreference「cv (const and volatile) type qualifiers」与「memory order」（指路，核实 2026-07-30；结论回落 ISO）：明确 volatile 非线程同步工具、MSVC `/volatile:ms` 为编译器扩展。
- Boehm, "Threads Cannot Be Implemented as a Library", PLDI 2005：编译器优化如何破坏"库层"并发假设，佐证"必须语言级原子约束编译器重排"。
- AMP 附录 B 与第 7 章相关讨论：两级重排、屏障来源的教材印证。
- 交叉一致，无冲突；仅记 MSVC `/volatile:ms` 扩展与标准语义的分歧一笔。

## CP-05.6 litmus 测试：SB / MP / IRIW 经典样式

### CP-05.6.1 litmus 测试是什么、怎么读

**litmus 测试**是一段极小的多线程程序（通常每线程两三条访存），配一个"问：某组最终寄存器值是否可能出现？"的判据。它是内存模型的"试纸"：不同模型对同一个 litmus 的答案（forbidden / allowed）不同，于是一组标准 litmus 就能把各模型的差异钉死到可判读的具体现象上。读法固定：给出共享变量初值（一般全 0）、每线程的指令序、和一个待判的结果（如 `r1==0 && r2==0`），然后问"在模型 M 下这个结果 allowed 吗"。

自动化工具（herdtools 的 `herd7` 做模型推演、`litmus7` 在真机上大量跑取样）能批量判定与实测。**本机 herdtools 未安装、标「待核」**；下述 SB/MP 的本机 pthread 手写复现属**可选实证、本报告未取**，结果全部回落 SDM/规范与文献；未实测如实标注。

### CP-05.6.2 SB（Store Buffering）与 MP（Message Passing）

**SB**（Store Buffering）就是 CP-05.2.2 那个例子。初值 `x=y=0`：

线程1： x = 1;  r1 = y;

线程2： y = 1;  r2 = x;

问 `r1==0 && r2==0` 是否可能。

SC 下：禁止（CP-05.2.2 已证会成环）。

x86-TSO 下：**允许**——这正是唯一的 store→load 放松 + store buffer 的直接体现（两个 load 各自越过本核尚在 buffer 里的 store）。SB 是"x86 不是 SC"的标志性证据，也是本课本机唯一能手写 pthread 复现的样式（复现时要用普通变量或 relaxed 原子、并大量循环采样才偶发命中；加 `mfence` 或 seq_cst 后 `r1==r2==0` 消失）。

**MP**（Message Passing）是"发消息 + 读消息"的惯用法。初值 `data=0, flag=0`：

线程1（生产者）： data = 42;  flag = 1;

线程2（消费者）： r1 = flag;  r2 = data;

问"是否可能 `r1==1`（看见 flag 置位）却 `r2==0`（没看见 data）"。

x86-TSO 下：**禁止**——因为 MP 只涉及 Store→Store（生产者两次写）和 Load→Load（消费者两次读），这两种 x86 都不放松，所以看见 flag 就一定看见 data（硬件层面）。**但**编译器那一级仍可能重排（CP-05.5.1），且这段代码本身是数据竞争（UB），所以**语言层必须**把 `flag` 写成 release-store、`flag` 读成 acquire-load 才正确、可移植。

ARM/POWER 下：**允许**（弱序放松 Store→Store 与 Load→Load），必须靠屏障/acquire-release 才能禁止——这是"x86 白送、弱序机器要自己付费"的典型对照（跨平台·未实测）。

### CP-05.6.3 IRIW（Independent Reads of Independent Writes）

**IRIW** 考察 multi-copy atomicity（CP-05.4.3）。初值 `x=y=0`，两个写者、两个读者：

线程1： x = 1;

线程2： y = 1;

线程3： r1 = x;  r2 = y;

线程4： r3 = y;  r4 = x;

问"是否可能 `r1==1, r2==0` 同时 `r3==1, r4==0`"——即线程3 认定"x 的写发生在 y 的写之前"，而线程4 认定"y 的写发生在 x 的写之前"，两个旁观者对两个独立写的先后**给出互相矛盾的结论**。

multi-copy-atomic 模型（x86-TSO、现行 ARMv8）下：**禁止**——写一旦可见就对所有核统一，不可能出现两个核看到相反顺序。

non-multi-copy-atomic 模型（POWER，及 2017 修订前的老 ARMv8）下：**允许**——写可以在不同时刻传播到不同核，两个读者各自看到不同的传播顺序，即便读者内部两条读不重排也会出现矛盾。

IRIW 的意义就在于：它把"重排"之外的另一维度——"写传播是否原子"——单独暴露出来。SB/MP 谈的是"同一个核内访存的顺序"，IRIW 谈的是"不同核之间对他人写的观测是否一致"，二者正交。这也是为什么加"每线程内部的 load-load 屏障"能修 MP 却**修不了** non-MCA 上的 IRIW（问题不在读者内部顺序，而在写的非原子传播），要靠更重的、保证多副本原子性的屏障（如 POWER 的 `sync`）。跨平台结论本机不可测，标「跨平台·未实测」。

#### 来源与时效
- Intel SDM Vol.3A §8.2.3（核实 2026-07-30）：SDM 用"Example 8-x"逐条给出 x86 允许/禁止的重排样式，SB（store→load）allowed、MP 样式（write/read ordering）forbidden、write 对所有观察者顺序一致（IRIW forbidden）均可对应到其例子。
- x86-TSO 论文（Sewell et al. 2010）与 "Herding Cats"（Alglave et al., TOPLAS 2014，核实 2026-07-30）：SB/MP/IRIW 的标准命名、各模型 allowed/forbidden 对照表（x86 / POWER / ARM）。
- Pulte et al., POPL 2018：ARMv8 修订后 IRIW forbidden 的判据（CP-05.4.3 同源）。
- herdtools（herd7/litmus7，http://diy.inria.fr/ ）：litmus 记法与自动判定工具——**本机未安装，标「待核」**；SB/MP 本机 pthread 手写复现为**可选实证、本报告未取**。
- 冲突项：无模型层冲突；不同资料对 IRIW 在 ARM 上的答案不一，根因同 CP-05.4（修订前后模型不同），以现行 ARMv8=MCA=IRIW forbidden 为准。
