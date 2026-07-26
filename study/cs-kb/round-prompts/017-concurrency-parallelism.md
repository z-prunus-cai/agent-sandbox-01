# Round3c · L4-05 并发与并行程序设计 · 报告 prompt（P1）

> 用途：为课程 L4-05 的 15 个大主题（CP-01~CP-15）各造 1 条 Round 4 报告 prompt。下游 subagent 每条独立产出一份 report-format v3 教辅报告，自己 Write 落盘。
> 通用先读（每条都适用，不再逐条重复）：`study/cs-kb/report-format.md`（v3 模板）、`study/cs-kb/brief.md`、`study/cs-kb/round3b-subtopics/g05-os-concurrency.md`（回读对应 CP-0X 节校全小主题）、`study/cs-kb/round3a-topics/g05-os-concurrency.md` §0 权威锚点 + §2 对应行 + §3 跨课分账。
> 通用格式硬性（v3，违反即返工，每条都适用）：
> - 标题层级：`#` 报告标题（=本大主题）→ `##` 小主题（=章节）→ `###` 每个内容项一节；`###` 之下**不写**「核心概念：」「辅助说明：」等任何标签，直接正文靠空行分段。
> - 每个 `###`：核心说明（是什么/定义/结论，正确，必要处标时效/版本）**必需** + 辅助说明（面向初学者的直觉 / 为什么这样 / 最小例子 / 易错点 / 前后关联）**必需且充分**，写到初学者能懂为止、可多段，**不是可省点缀**。
> - 内存序 / 原子操作 / 任何公式 / 代码片段一律**独占行**（块级），不内联埋进句子。
> - 口吻为教辅；广度优先——每项讲到初学者懂即止，**不做专家级纵深**（不写长篇机制深挖 / 完整推导 / 设计权衡长论）。
> - 抬头带可 grep 基线串：`基线 Py3.11.15/np2.4.6/gcc13.3 @2026-07-25`，并注核实日期。
> 通用多来源比对（强制，每条都适用，不可省，不可用实机验证替代）：
> - 每个内容项 ≥2 个独立来源交叉核对，优先一手（下方各条给本主题一手锚点）。
> - 来源打架时两边都记、点明分歧、不和稀泥。
> - 本机实证（pthread / C11 atomics `-std=c11` / 数据竞争 `-fsanitize=thread` 复现等）为**可选补充**，仅在便宜且能加固某结论/输出/默认值时做，不逐项强制，**不能替代多来源比对**。
> - 不编造任何数值 / 默认值 / 版本号 / 公式系数——来自比对通过的来源，或标「待核」。
> - 来源与时效在**每个 `##` 章节末集中列**（锚点 + 版本 + 核实日期 + 冲突项两边定位），不逐项脚注。
> 通用粒度判定（每条动笔前先做）：先判本大主题「1 份报告 or 拆 N 份 + 理由」——小主题多 / 跨机制 / 过长则拆成 `-a/-b`，理由写入报告抬头附近并交主 agent。
> 通用落盘：subagent 自己 Write 到 `study/cs-kb/findings/017-concurrency-parallelism-cp0X-<slug>.md`（同 `017-concurrency-parallelism` 前缀族；拆分用 `-a/-b` 后缀）。报告须自足（复现命令 / 环境串贴进正文），不得残留工具标签 `</…>` 或不可见控制字符。

```text
[P1] L4-05·大主题01 互斥问题与理论 — 报告prompt

任务：为「L4-05·CP-01 互斥问题与理论」产出一份 report-format v3 教辅报告。先读通用先读清单中 round3b 的 CP-01 一节。遵守顶部通用格式硬性 / 多来源比对 / 粒度判定 / 落盘规则。

覆盖范围（## 章节 = 小主题，内容项须查全，宁多列浅讲不遗漏）：
- CP-01.1 互斥的形式化：临界区、互斥性 / 无死锁 / 无饥饿的精确定义。
- CP-01.2 两线程算法：LockOne / LockTwo / Peterson 算法及其正确性论证。
- CP-01.3 N 线程算法：filter 锁、bakery（面包店）算法的先来先服务性质。
- CP-01.4 纯软件互斥的下界：为何需要 Ω(N) 个内存位置、为何离不开硬件原语。
- CP-01.5 有界等待与公平：bounded waiting、时间 / 因果对公平性的约束。
（回读 round3b CP-01 节校全；以上已自足。分账：本课讲问题本质与正确性证明，非内核 mutex 实现——那归 L4-01·OS-06。）

多来源比对一手锚点：《The Art of Multiprocessor Programming》(Herlihy/Shavit/Luchangco/Spear) 2nd/Revised ch2「Mutual Exclusion」为主一手；交叉源可用 OSTEP 并发部分、经典论文（Peterson 1981 / Lamport bakery 1974）。本机实证可选：pthread 复现 Peterson（需内存屏障，触 CP-05）。

权威锚点清单（取自 round3a §0，逐项核实 2026-07-25）：
- AMP《The Art of Multiprocessor Programming》2nd ed / Revised Reprint（18 章）https://www.sciencedirect.com/book/monograph/9780124159501/ · https://www.oreilly.com/library/view/the-art-of/9780123973375/
- OSTEP《Operating Systems: Three Easy Pieces》网页版 https://pages.cs.wisc.edu/~remzi/OSTEP/
```

```text
[P1] L4-05·大主题02 并发对象与线性一致性 — 报告prompt

任务：为「L4-05·CP-02 并发对象与线性一致性」产出一份 report-format v3 教辅报告。先读通用先读清单中 round3b 的 CP-02 一节。遵守顶部通用格式硬性 / 多来源比对 / 粒度判定 / 落盘规则。

覆盖范围（## 章节 = 小主题）：
- CP-02.1 并发对象需要规约：用顺序规约定义并发对象的正确性。
- CP-02.2 顺序一致性 (SC)：各线程内序保持、全局存在一致交错。
- CP-02.3 线性一致性：linearizability、线性化点、比 SC 更强的道理。
- CP-02.4 可组合性：linearizability 的局部性（各对象各自线性 ⇒ 整体线性）。
- CP-02.5 进展性条件：wait-free / lock-free / obstruction-free vs starvation-free / deadlock-free 的层级与区别（本课地基，R2 标 P0）。
（回读 round3b CP-02 节校全；进展性定义是全课地基，务必讲到初学者能分清。）

多来源比对一手锚点：AMP ch3「Concurrent Objects」为主一手；交叉源可用 Herlihy-Wing 1990 linearizability 原始论文、C++/C 标准中相关 happens-before 措辞作印证。本主题以概念为主，本机实证机会少，重点在多来源把定义讲准。

权威锚点清单（round3a §0，核实 2026-07-25）：
- AMP 2nd/Revised ch3 https://www.sciencedirect.com/book/monograph/9780124159501/
- （印证）ISO C++ `[intro.races]` happens-before 定义（cppreference 指路，结论回落 ISO）。
```

```text
[P1] L4-05·大主题03 共享内存基础与寄存器构造 — 报告prompt

任务：为「L4-05·CP-03 共享内存基础与寄存器构造」产出一份 report-format v3 教辅报告。先读通用先读清单中 round3b 的 CP-03 一节。遵守顶部通用格式硬性 / 多来源比对 / 粒度判定 / 落盘规则。

覆盖范围（## 章节 = 小主题）：
- CP-03.1 safe / regular / atomic 寄存器层级：三级读写语义强度递增。
- CP-03.2 从弱到强的构造：单读→多读、布尔→多值寄存器的构造思路。
- CP-03.3 atomic 多读多写构造：用时间戳把 regular 提升到 atomic。
- CP-03.4 读写原子性的来源与代价：原子性并非免费、构造开销。
（回读 round3b CP-03 节校全；本主题偏理论，辅助说明要用最小例子把"为什么弱寄存器不够、怎么加强"讲到初学者能懂。）

多来源比对一手锚点：AMP ch4「Foundations of Shared Memory」为主一手；交叉源可用 Lamport「On interprocess communication」(1986) 寄存器分类原始论文。以概念为主，多来源重在把三级寄存器定义与构造正确性讲准。

权威锚点清单（round3a §0，核实 2026-07-25）：
- AMP 2nd/Revised ch4 https://www.sciencedirect.com/book/monograph/9780124159501/
```

```text
[P1] L4-05·大主题04 同步原语的相对能力与共识 — 报告prompt

任务：为「L4-05·CP-04 同步原语的相对能力与共识」产出一份 report-format v3 教辅报告。先读通用先读清单中 round3b 的 CP-04 一节。遵守顶部通用格式硬性 / 多来源比对 / 粒度判定 / 落盘规则。

覆盖范围（## 章节 = 小主题）：
- CP-04.1 共识问题：多线程就一个值达成一致、wait-free 要求。
- CP-04.2 consensus number：read/write=1、FIFO 队列=2、… 的能力刻度。
- CP-04.3 wait-free 同步层级：原语按共识数分层、不可跨层实现。
- CP-04.4 CAS 的通用性：compareAndSet 共识数 = ∞（承 CP-06.4）。
- CP-04.5 universal construction：用共识对象实现任意 wait-free 对象。
（回读 round3b CP-04 节校全；本主题解释"为何 CAS 万能、read/write 不行"，接 CP-06。CAS 可用 C11 实测作可选补充。）

多来源比对一手锚点：AMP ch5–6 为主一手；交叉源可用 Herlihy「Wait-Free Synchronization」(1991) 原始论文。共识数数值（1 / 2 / ∞ 等）务必比对锚定，勿凭记忆。

权威锚点清单（round3a §0，核实 2026-07-25）：
- AMP 2nd/Revised ch5–6 https://www.sciencedirect.com/book/monograph/9780124159501/
- （CAS 实测印证）ISO C 9899:2024 §7.17 `atomic_compare_exchange_*`。
```

```text
[P1] L4-05·大主题05 内存一致性模型与重排序 — 报告prompt

任务：为「L4-05·CP-05 内存一致性模型与重排序」产出一份 report-format v3 教辅报告（本课主场，内容偏多，先做粒度判定，很可能拆 -a/-b）。先读通用先读清单中 round3b 的 CP-05 一节。遵守顶部通用格式硬性 / 多来源比对 / 粒度判定 / 落盘规则。

覆盖范围（## 章节 = 小主题）：
- CP-05.1 happens-before 与 DRF：因果序定义、无数据竞争则似 SC（data-race-free 定理）。
- CP-05.2 顺序一致性作参照：SC 作为程序员心智基线。
- CP-05.3 x86-64 TSO：store buffer 导致的 store→load 重排（x86 唯一放松）。
- CP-05.4 弱序模型对照：ARM / POWER 允许更多重排（本机 x86 不可直测，标「待核 / 跨平台」，无 ARM 硬件不作实测结论）。
- CP-05.5 编译器重排 vs 硬件重排：两级重排、屏障来源、`volatile` 不等于原子。
- CP-05.6 litmus 测试：SB / MP / IRIW 经典样式（herdtools 未装标「待核」；SB / MP 可手写 pthread 复现作可选补充）。
（回读 round3b CP-05 节校全。分账：内存屏障 / 模型整体归本课；L4-01·OS-03/OS-06 仅在 syscall / 内核锁点到 `smp_mb()` 家族。所有内存序记号独占行。）

多来源比对一手锚点：Intel SDM Vol.3A「Memory Ordering」(x86-64 TSO) 与 AMD64 APM Vol.2 为硬件语义主一手；C++ `[intro.races]` / AMP 附录为模型定义印证。硬件规范与教材若对 x86 放松项表述不一，两边都记并定位。x86-64 只放松 store→load 这一项务必以 SDM 原文核实、勿凭记忆。

权威锚点清单（round3a §0，核实 2026-07-25）：
- Intel SDM Vol.3A「Memory Ordering」/ AMD64 APM Vol.2（x86-64 TSO，Intel/AMD 官网）
- ISO C++ `[intro.races]` / `[atomics]`（happens-before 更完整，交叉印证）
- AMP 2nd/Revised 附录（内存模型）https://www.sciencedirect.com/book/monograph/9780124159501/
- ⚠ 时效红旗：x86 = TSO；ARM/POWER 为弱序但本机不可直测。
```

```text
[P1] L4-05·大主题06 原子操作与 memory_order — 报告prompt

任务：为「L4-05·CP-06 原子操作与 memory_order」产出一份 report-format v3 教辅报告。先读通用先读清单中 round3b 的 CP-06 一节。遵守顶部通用格式硬性 / 多来源比对 / 粒度判定 / 落盘规则。

覆盖范围（## 章节 = 小主题）：
- CP-06.1 `_Atomic` 与泛型接口：`<stdatomic.h>` 类型、atomic_load / store / exchange。
- CP-06.2 六档 memory_order：relaxed / consume / acquire / release / acq_rel / seq_cst 的强弱谱（各记号独占行）。
- CP-06.3 acquire/release 配对：release-store 与 acquire-load 建立跨线程 happens-before（MP 惯用法）。
- CP-06.4 RMW 操作：CAS（strong / weak）、fetch_add / fetch_or 的原子读改写。
- CP-06.5 fence：`atomic_thread_fence` 的独立屏障语义。
- CP-06.6 lock-free 判定：`atomic_is_lock_free`、`ATOMIC_*_LOCK_FREE` 宏。
（回读 round3b CP-06 节校全。分账：语言级语义归本课；`LOCK` 前缀 / 关中断等原子性**来源**归 L4-01·OS-06。可选实证：`-std=c11` 编 atomics 最小例、objdump 看生成码、fetch_add 计数 vs 非原子丢更新对比。）

多来源比对一手锚点：ISO C 9899:2024（C23）§7.17 / §7.31.8 `<stdatomic.h>` 为主一手（C11 首引入，C17 无实质改动，C23 措辞较 C11 有调整——以规范文本核实，勿凭记忆）；cppreference 仅作指路、结论回落 ISO；C++ `[atomics]` 交叉印证。六档语义与宏值务必比对锚定。

权威锚点清单（round3a §0，核实 2026-07-25）：
- ISO C `<stdatomic.h>`：C23 = ISO/IEC 9899:2024（现行，2024-10-31 发布），atomics §7.17 / §7.31.8（工作草案 N3220 / cppreference c/atomic）
- ISO C++ `[atomics]`（交叉印证）
- ⚠ 时效红旗：C 标准现行 = C23，`memory_order` 措辞较 C11 有调整。
```

```text
[P1] L4-05·大主题07 数据竞争定义与 UB — 报告prompt

任务：为「L4-05·CP-07 数据竞争定义与 UB」产出一份 report-format v3 教辅报告。先读通用先读清单中 round3b 的 CP-07 一节。遵守顶部通用格式硬性 / 多来源比对 / 粒度判定 / 落盘规则。

覆盖范围（## 章节 = 小主题）：
- CP-07.1 data race 精确定义：两访问 / ≥1 写 / 无 happens-before / 非原子，四要件。
- CP-07.2 竞争即 UB 的后果：撕裂读写、编译器基于"无竞争"的激进假设。
- CP-07.3 TSan 检测：ThreadSanitizer 影子内存 + happens-before 追踪。
- CP-07.4 "良性竞争"迷思：无真正良性的非原子竞争、用 relaxed atomic 修正。
- CP-07.5 与抢占交错的根因：调度抢占制造交错 = 竞争暴露的触发面（借 L4-01·OS-05）。
（回读 round3b CP-07 节校全。本主题本机实证腿最扎实——可选但推荐：`-fsanitize=thread` 复现并读 `WARNING: data race`，改 `atomic_*` relaxed 后转干净；反汇编演示撕裂/激进优化。）

多来源比对一手锚点：ISO C / C++ 数据竞争条款为主一手（C23 §5.1.2.4 多线程执行与数据竞争 / C++ `[intro.races]`）；实证输出与标准定义交叉印证。四要件表述以标准原文核实。

权威锚点清单（round3a §0，核实 2026-07-25）：
- ISO C 9899:2024 数据竞争条款；ISO C++ `[intro.races]`
- （实证工具）gcc 13.3 `-fsanitize=thread`（基线 Linux 6.18.5）
```

```text
[P1] L4-05·大主题08 自旋锁与竞争 — 报告prompt

任务：为「L4-05·CP-08 自旋锁与竞争」产出一份 report-format v3 教辅报告。先读通用先读清单中 round3b 的 CP-08 一节。遵守顶部通用格式硬性 / 多来源比对 / 粒度判定 / 落盘规则。

覆盖范围（## 章节 = 小主题）：
- CP-08.1 TAS vs TTAS：test-and-set 的缓存行失效风暴、先读再试的改进。
- CP-08.2 指数退避：backoff 缓解争用。
- CP-08.3 队列锁 MCS / CLH：各自本地变量自旋、公平且可扩展。
- CP-08.4 缓存一致性与 false sharing：MESI 对自旋成本的支配、伪共享（同缓存行 vs `_Alignas(64)` 对齐可计时对比作可选实证）。
- CP-08.5 NUMA 与锁放置：跨节点访问的非一致代价（本机拓扑标「待核」，`lscpu` 看 NUMA 节点数）。
（回读 round3b CP-08 节校全。分账：本课讲可扩展性与内存层级；L4-01·OS-06 讲持锁被抢占的代价 / futex。）

多来源比对一手锚点：AMP ch7「Spin Locks and Contention」为主一手；MCS（Mellor-Crummey & Scott 1991）/ CLH 原始论文作交叉印证；MESI 行为可回落体系结构教材（如 Hennessy-Patterson）。

权威锚点清单（round3a §0，核实 2026-07-25）：
- AMP 2nd/Revised ch7 https://www.sciencedirect.com/book/monograph/9780124159501/
```

```text
[P1] L4-05·大主题09 阻塞同步与 monitor — 报告prompt

任务：为「L4-05·CP-09 阻塞同步与 monitor」产出一份 report-format v3 教辅报告。先读通用先读清单中 round3b 的 CP-09 一节。遵守顶部通用格式硬性 / 多来源比对 / 粒度判定 / 落盘规则。

覆盖范围（## 章节 = 小主题）：
- CP-09.1 monitor 模式：锁 + 条件变量封装成对象内同步。
- CP-09.2 条件变量语义：wait / signal / broadcast、Mesa vs Hoare 语义。
- CP-09.3 虚假唤醒纪律：必须 `while` 重检谓词而非 `if`。
- CP-09.4 生产者-消费者：有界缓冲、满 / 空条件变量。
- CP-09.5 读者-写者：共享读 / 独占写、公平与写者饥饿。
- CP-09.6 信号量：计数同步原语、二元 vs 计数。
（回读 round3b CP-09 节校全。分账：本课讲同步惯用法正确性；L4-01·OS-06 讲陷入内核睡眠 / 唤醒机制。可选实证：pthread_cond_wait 用 while 复现正确惯用法、pthread 实现生产者消费者 + 计数校验、`sem_t`。）

多来源比对一手锚点：AMP ch8「Monitors and Blocking Synchronization」为主一手；Mesa 语义可回落 Lampson-Redell「Experience with Processes and Monitors in Mesa」(1980)；POSIX pthread 条件变量文档印证 `while` 纪律。Mesa vs Hoare 差异两边都讲清。

权威锚点清单（round3a §0，核实 2026-07-25）：
- AMP 2nd/Revised ch8 https://www.sciencedirect.com/book/monograph/9780124159501/
- （印证）OSTEP 并发部分 CV / 信号量章。
```

```text
[P1] L4-05·大主题10 无锁并发数据结构 — 报告prompt

任务：为「L4-05·CP-10 无锁并发数据结构」产出一份 report-format v3 教辅报告（内容偏多，先做粒度判定，可能拆 -a/-b）。先读通用先读清单中 round3b 的 CP-10 一节。遵守顶部通用格式硬性 / 多来源比对 / 粒度判定 / 落盘规则。

覆盖范围（## 章节 = 小主题）：
- CP-10.1 链表并发级别谱：coarse → fine → optimistic → lazy → lock-free 递进。
- CP-10.2 lock-free 链表：逻辑删除标记 + CAS 物理摘除。
- CP-10.3 并发队列与 ABA：Michael-Scott 队列、ABA 问题成因。
- CP-10.4 无锁栈与消除：Treiber 栈、elimination backoff。
- CP-10.5 安全内存回收：hazard pointers / epoch / RCU 解决"何时能 free"。
- CP-10.6 死锁 / 活锁的算法层回避：锁序、无锁如何绕开 Coffman 四条件、活锁。
（回读 round3b CP-10 节校全。分账：锁序 / 无锁回避 / ABA / 活锁归本课算法层；四条件 + 银行家归 L4-01·OS-07 资源管理层。可选实证：C11 CAS 实现小例、CAS 复现 ABA。）

多来源比对一手锚点：AMP ch9–11「Linked Lists / Queues & ABA / Stacks」为主一手；Michael-Scott 队列（1996）、Treiber 栈、hazard pointers（Michael 2004）原始论文作交叉印证。ABA 成因与各回收方案适用条件两边核对。

权威锚点清单（round3a §0，核实 2026-07-25）：
- AMP 2nd/Revised ch9–11 https://www.sciencedirect.com/book/monograph/9780124159501/
```

```text
[P1] L4-05·大主题11 可扩展并发结构与分布式协调 — 报告prompt

任务：为「L4-05·CP-11 可扩展并发结构与分布式协调」产出一份 report-format v3 教辅报告。先读通用先读清单中 round3b 的 CP-11 一节。遵守顶部通用格式硬性 / 多来源比对 / 粒度判定 / 落盘规则。

覆盖范围（## 章节 = 小主题）：
- CP-11.1 并发 hashing：开 / 闭地址、可扩展哈希、拆分有序表。
- CP-11.2 skiplist 与并发平衡搜索：概率平衡、无锁跳表。
- CP-11.3 并发优先队列：基于 skiplist / 堆的并发实现。
- CP-11.4 counting networks：balancer 网络分散计数争用。
- CP-11.5 并发排序与组合：sorting / 组合网络。
（回读 round3b CP-11 节校全。本主题偏概念，辅助说明用直觉图景讲清"为何这些结构能分散争用 / 提升可扩展性"，不做纵深。）

多来源比对一手锚点：AMP ch12–15 为主一手；counting networks（Aspnes-Herlihy-Shavit 1994）、skiplist（Pugh 1990）原始论文作交叉印证。

权威锚点清单（round3a §0，核实 2026-07-25）：
- AMP 2nd/Revised ch12–15 https://www.sciencedirect.com/book/monograph/9780124159501/
```

```text
[P1] L4-05·大主题12 任务并行与调度 — 报告prompt

任务：为「L4-05·CP-12 任务并行与调度」产出一份 report-format v3 教辅报告。先读通用先读清单中 round3b 的 CP-12 一节。遵守顶部通用格式硬性 / 多来源比对 / 粒度判定 / 落盘规则。

覆盖范围（## 章节 = 小主题）：
- CP-12.1 futures：异步结果占位、依赖表达。
- CP-12.2 fork-join：分治任务的并行展开与汇合（OpenMP 可用性标「待核」）。
- CP-12.3 work-stealing：每核双端队列、空闲核偷任务。
- CP-12.4 barriers：sense-reversing barrier 等同步点（pthread_barrier 可实证）。
- CP-12.5 任务图与负载均衡：DAG 调度、均衡策略。
（回读 round3b CP-12 节校全。与 L6-06 HPC 前置衔接——本课点到范式与正确性，实测调优归 L6-06。可选实证：pthread_barrier、pthread/OpenMP fork-join 小例。）

多来源比对一手锚点：AMP ch16–17「Futures / Barriers」为主一手；work-stealing 可回落 Blumofe-Leiserson（Cilk，1999）原始论文；OpenMP 语义回落 OpenMP 规范。

权威锚点清单（round3a §0，核实 2026-07-25）：
- AMP 2nd/Revised ch16–17 https://www.sciencedirect.com/book/monograph/9780124159501/
```

```text
[P1] L4-05·大主题13 事务内存 — 报告prompt

任务：为「L4-05·CP-13 事务内存」产出一份 report-format v3 教辅报告。先读通用先读清单中 round3b 的 CP-13 一节。遵守顶部通用格式硬性 / 多来源比对 / 粒度判定 / 落盘规则。

覆盖范围（## 章节 = 小主题）：
- CP-13.1 TM 编程模型：原子块、乐观并发、自动回滚重试。
- CP-13.2 STM 软件事务：版本 / 所有权记录、冲突检测与提交。
- CP-13.3 HTM 硬件事务：Intel TSX 等、成熟度与平台可用性硬标（TSX 本机是否可用标「待核」，R4 核实）。
- CP-13.4 TM vs 锁：可组合性优势、性能 / 回滚代价对比。
（回读 round3b CP-13 节校全。成熟度：HTM 硬件支持有限，务必硬标平台可用性，不作"本机可用"结论除非实证坐实。）

多来源比对一手锚点：AMP ch18「Transactional Memory」为主一手；HTM 成熟度回落 Intel SDM（TSX / RTM 指令、及其后续勘误 / 禁用状态）作交叉印证——TSX 在部分处理器已被微码禁用，务必以现行官方文档核实、两边分歧都记。

权威锚点清单（round3a §0，核实 2026-07-25）：
- AMP 2nd/Revised ch18 https://www.sciencedirect.com/book/monograph/9780124159501/
- （HTM 平台）Intel SDM（TSX/RTM）官网。
- ⚠ 成熟度红旗：HTM 硬件支持有限 / 部分平台已禁用，硬标状态。
```

```text
[P1] L4-05·大主题14 消息传递与并行范式 — 报告prompt

任务：为「L4-05·CP-14 消息传递与并行范式」产出一份 report-format v3 教辅报告。先读通用先读清单中 round3b 的 CP-14 一节。遵守顶部通用格式硬性 / 多来源比对 / 粒度判定 / 落盘规则。

覆盖范围（## 章节 = 小主题）：
- CP-14.1 共享内存 vs 消息传递：两大范式对立、"share by communicating"。
- CP-14.2 CSP / channel：Go goroutine + channel 模型（Go 未列入基线工具链，标「待核」）。
- CP-14.3 actor 模型：Erlang 进程 / 邮箱、无共享状态。
- CP-14.4 MPI：进程级显式消息、集合通信（MPI 未装标「待核」；node 22 可作范式备用）。
- CP-14.5 数据并行 vs 任务并行：划分维度差异与选型。
（回读 round3b CP-14 节校全。分账：本课讲范式正确性与选型；重叠 L4-01·OS-04 协程、L5-01 分布式。以概念为主，工具链未装项硬标。）

多来源比对一手锚点：CSP 回落 Hoare「Communicating Sequential Processes」(1978) 与 Go 官方文档；actor 回落 Erlang 官方文档 / Hewitt actor 模型；MPI 回落 MPI 标准；范式对比可用 AMP 相关讨论印证。范式差异两边都讲清，不偏袒。

权威锚点清单（round3a §0，核实 2026-07-25）：
- AMP 外补 + Go / Erlang / MPI 一手官方文档（R2 表 C）
- ⚠ 工具链：Go / MPI 未列入基线，相关实证标「待核」。
```

```text
[P1] L4-05·大主题15 并行性能模型 — 报告prompt

任务：为「L4-05·CP-15 并行性能模型」产出一份 report-format v3 教辅报告。先读通用先读清单中 round3b 的 CP-15 一节。遵守顶部通用格式硬性 / 多来源比对 / 粒度判定 / 落盘规则。

覆盖范围（## 章节 = 小主题；所有公式独占行）：
- CP-15.1 加速比与效率：
  speedup = T1 / Tp
  efficiency = speedup / p
- CP-15.2 Amdahl 定律：串行比例决定加速上界（公式独占行；sympy 推导 + numpy 曲线可选验证）。
- CP-15.3 Gustafson 定律：规模随核数增长的弱扩展视角（公式独占行）。
- CP-15.4 可扩展性上界与开销来源：作心智模型——同步 / 通信 / 负载不均侵蚀加速。
（回读 round3b CP-15 节校全。分账：本课点到上界模型；实测调优 / GPU 归 L6-06 HPC。可选实证：numpy 画加速比曲线、sympy 核对 Amdahl/Gustafson 极限。公式系数勿凭记忆，比对锚定。）

多来源比对一手锚点：AMP ch1（speedup 讨论）为主一手；Amdahl (1967) 与 Gustafson (1988) 原始论文作交叉印证。两定律的假设差异（固定规模 vs 固定时间 / 规模随核数增长）务必讲清、勿混。

权威锚点清单（round3a §0，核实 2026-07-25）：
- AMP 2nd/Revised ch1 https://www.sciencedirect.com/book/monograph/9780124159501/
- （原始）Amdahl 1967 / Gustafson 1988。
- 跨课：CP-15 上界模型 ↔ L6-06 HPC 实测调优 / GPU。
```

共 15 条 prompt
