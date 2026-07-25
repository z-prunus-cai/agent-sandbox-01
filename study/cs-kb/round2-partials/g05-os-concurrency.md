# 第 5 组「OS 与并发」· Round 2 定方向 · 核实 2026-07-25

> 调查员：第 5 组（OS 与并发）。基线 Py3.11.15/np2.4.6/gcc13.3 @2026-07-25；系统课实证基线 **Linux 6.18.5 x86_64 / gcc 13.3.0 / strace 6.8 / gdb 15.1 / glibc pthreads / C11 `<stdatomic.h>`**（本机已核，见 §4）。
> 覆盖课程：**L4-01 操作系统**（并入 🔧「内核实践」xv6/本机 syscall，作 L4-01 的第三条实证腿，不单独立报告）＋ **L4-05 并发与并行程序设计**。
> 本文件是**阶段2 定方向**产物：给组内选型/对比表 + 逐课重点方向 + 边界分账 + 一手源候选 + 本机实证点 + 优先级校准。**不做**逐条三条腿深挖（那是 R4）。凡涉及会过期的事实（内核默认调度器、C 标准版本、POSIX 版次）在下文硬标时效。

---

## 0 · 一句话定位与本组主轴

- **L4-01 操作系统** = 「一台机器如何被抽象成多个可复用的资源视图」：进程/线程抽象、CPU 调度、同步与死锁、虚存与分页、文件系统、中断与系统调用这一「用户态↔内核态」的边界。心智锚：**OS = 资源的复用器 + 保护边界的执行者**。
- **L4-05 并发与并行** = 「当多个执行流真的同时读写共享内存时，什么保证成立、什么不成立」：内存模型（可见性/重排序）、原子操作、锁 vs 无锁、消息传递、数据竞争。心智锚：**并发正确性 = 在最弱的内存模型下仍成立的不变量**。
- **两课的接缝**（本组核心分账，详见 §3）：L4-01 讲**内核提供的并发原语与其代价**（futex、内核锁、调度点、原子性来源）；L4-05 讲**语言级内存模型与无锁算法的可组合正确性**（happens-before、memory_order、ABA、TSO vs 弱序）。同一个 mutex：L4-01 问「它怎么进内核、怎么让出 CPU」，L4-05 问「它建立了哪些 happens-before 边、能否用更弱的原语替代」。

---

## 1 · 组内选型 / 对比表（本组最高价值产出）

### 表 A · 进程 vs 线程 vs 协程（执行流抽象的三档）
| 维度 | 进程 | 线程（内核级，1:1） | 协程 / 用户态任务（N:1 或 M:N） |
|------|------|----------------------|-----------------------------------|
| 地址空间 | 独立（各自页表） | 共享进程地址空间 | 共享（同线程内） |
| 调度者 | 内核 | 内核 | 用户态运行时（无内核介入） |
| 创建/切换代价 | 最高（`fork`+页表+TLB） | 中（`clone(2)` 共享标志位） | 最低（仅保存/恢复寄存器+栈指针，无陷入） |
| 并行能力 | 真并行（多核） | 真并行（多核） | 单线程内**不**并行（并发≠并行）；需 M:N 才并行 |
| 隔离/容错 | 强（崩溃不互溢） | 弱（共享内存，一个越界全崩） | 弱 |
| 通信 | IPC（管道/共享内存/socket） | 共享变量（需同步） | 共享变量 / channel |
| Linux 落地 | `fork`/`execve` | `pthread_create`→`clone` | 语言运行时（Go goroutine、Python `asyncio`、C `ucontext`/`swapcontext`） |
| 何时选 | 强隔离/多语言/安全边界 | CPU 密集且需共享大状态 | 海量 I/O 并发、切换开销敏感 |

> 关键辨析（R4 深挖点）：Linux 中「进程」与「线程」在内核里都是 `task_struct`，差别只在 `clone(2)` 的共享标志（`CLONE_VM`/`CLONE_FILES`/…）——这是「进程 vs 线程本质同源」的一手落点。协程「并发≠并行」是最常被误解处。

### 表 B · CPU 调度算法对比
| 算法 | 优化目标 | 抢占 | 饥饿风险 | 典型场景 | 备注 |
|------|----------|------|----------|----------|------|
| FCFS | 简单/吞吐 | 否 | 无 | 批处理 | 护航效应（convoy） |
| SJF / SRTF | 最短平均周转 | SRTF 抢占 | 有（长作业） | 理论最优基线 | 需预知运行时间（不现实） |
| Round-Robin | 响应时间/公平 | 是（时间片） | 无 | 分时 | 时间片长度是关键权衡 |
| 优先级调度 | 重要性 | 可 | 有 | 实时/混合 | 需优先级继承防倒置 |
| MLFQ | 兼顾响应+吞吐（近似 SJF 无需预知） | 是 | 需老化（aging） | 通用（OSTEP 主线） | 教学主力模型 |
| **EEVDF**（Linux 默认，见时效标注） | 公平 + 延迟敏感 | 是 | 无（虚拟截止期） | Linux 通用 | **6.6 起取代 CFS** |
| CFS（历史） | 公平（虚拟运行时间） | 是 | 无 | Linux ≤6.5 | 红黑树按 vruntime |
| 实时（`SCHED_FIFO`/`SCHED_RR`/`SCHED_DEADLINE`） | 确定性延迟 | 依策略 | FIFO 有 | 硬/软实时 | `SCHED_DEADLINE`=EDF+CBS |

> ⚠时效硬标（基线 Linux 6.18.5）：Linux 通用调度类默认是 **EEVDF（Earliest Eligible Virtual Deadline First），自 v6.6（2023）合并起取代 CFS**。教材（OSTEP/恐龙书）仍以 CFS/MLFQ 为主线——这是典型「教材 vs 当前实现」分账点，R4 必须实证坐实基线内核用的是 EEVDF（`chrt`/`/proc/sched_debug`/内核 `kernel/sched/fair.c`）。

### 表 C · 锁 vs 无锁 vs 消息传递（并发协调三范式）
| 维度 | 基于锁（mutex/rwlock/spinlock） | 无锁 / 无等待（CAS 循环、原子） | 消息传递（channel / actor / MPI） |
|------|-------------------------------|-------------------------------|-----------------------------------|
| 核心原语 | 互斥 + 阻塞让出 | `atomic_compare_exchange`（CAS/LL-SC） | 拷贝/所有权转移，无共享写 |
| 进展保证 | 可能死锁/优先级倒置 | lock-free（系统级进展）/wait-free（每线程进展） | 由通道语义决定 |
| 典型故障 | 死锁、护航、倒置 | **ABA 问题**、活锁、内存回收（safe reclamation） | 死锁（循环等待通道）、背压 |
| 内存模型依赖 | 低（锁封装了屏障） | **高**（必须显式选 memory_order） | 低（无共享） |
| 扩展性（多核竞争下） | 竞争激烈时差 | 通常更好，但 CAS 重试放大 | 好（无共享缓存行） |
| 心智负担 | 低 | 高（最易写错） | 中（重构成本） |
| 落地 | `pthread_mutex_t`、futex | C11 `<stdatomic.h>`、`__atomic` | Go channel、Erlang/actor、MPI、Unix pipe |

> 辨析：三者是**同一问题的不同点**，不是优劣序。锁把内存序细节封在实现里；无锁把它暴露给你（所以需要 §表 D 的内存模型）；消息传递用「不共享」绕过问题（"do not communicate by sharing memory; share memory by communicating"）。

### 表 D · x86-64 强内存模型（TSO）vs 弱内存模型（ARM/POWER）vs C11 抽象模型
| 维度 | x86-64（Intel/AMD） | ARMv8 / POWER | C11/C++11 抽象机 |
|------|---------------------|----------------|-------------------|
| 类别 | **TSO**（Total Store Order，强） | 弱序（relaxed by default） | 与硬件解耦的 happens-before 模型 |
| 允许的重排 | **仅 StoreLoad**（写后读可乱序） | Store/Load 几乎任意重排 | 由 `memory_order` 显式约束 |
| 普通读写含义 | 已有 acquire/release 语义（近似） | 无任何序保证 | `relaxed` 无序、`acq/rel`、`seq_cst` 全序 |
| 屏障来源 | `MFENCE`；`LOCK`前缀=全屏障 | `DMB`/`DSB`/`ISB`、`LDAR`/`STLR` | `atomic_thread_fence`、原子操作携带序 |
| 对程序员影响 | 「在 x86 上碰巧对」的错代码到 ARM 上暴雷 | 逼你写正确内存序 | 一次写对，编译器为各 ISA 生成正确屏障 |
| 一手定位 | Intel SDM Vol.3A §「Memory Ordering」；AMD64 APM Vol.2 | ARM ARM「Memory model」 | ISO C `<stdatomic.h>`；C++ `[intro.races]` |

> 本组「杀手级」实证点：写一个 relaxed 原子的 **Store-Buffer 里 litmus（r1=r2=0）**，在 x86-64 上可观察到 StoreLoad 重排——直接坐实「强模型仍允许一种重排」（见 §4，需 litmus harness）。

### 表 E · 同步原语选型（细粒度）
| 原语 | 适用 | 阻塞? | 代价 | 陷阱 |
|------|------|-------|------|------|
| spinlock | 极短临界区、持锁不睡 | 忙等 | 低延迟高 CPU | 持锁被抢占=灾难 |
| mutex（futex 支撑） | 一般临界区 | 是（陷入内核睡眠） | 竞争时高 | 死锁、递归 |
| rwlock | 读多写少 | 是 | 写者饥饿 | 升级死锁 |
| 条件变量 | 等待条件成立 | 是 | — | **虚假唤醒→必须 while 判定** |
| 信号量 | 计数资源/排序 | 是 | — | 计数错配 |
| RCU（内核） | 读极多写极少 | 读侧无锁 | 写侧宽限期 | 只在内核/liburcu |
| C11 atomic | 无锁数据结构 | 否 | — | 内存序选错 |

---

## 2 · 逐课重点方向

### L4-01 操作系统 —— 5 个重点方向
1. **进程/线程抽象与生命周期**：`task_struct`、`fork`/`execve`/`wait`/`clone` 语义、地址空间与 fork 的 COW、僵尸/孤儿进程。实证腿：strace `fork`+`execve`、`/proc/<pid>/`。（对应表 A）
2. **CPU 调度**：从 FCFS→SJF→RR→MLFQ 的教学阶梯，落到 Linux **EEVDF（时效硬标）** 与实时类。实证腿：`chrt`、`nice`/`/proc/<pid>/sched`、`/proc/sched_debug`。（对应表 B）
3. **同步与死锁**：临界区、锁/信号量/条件变量、死锁四条件（Coffman）与预防/避免/检测。实证腿：pthread 原语 + 构造并观测一个死锁（gdb 看两线程互等）。（对应表 E）——**注意与 L4-05 分账，见 §3**
4. **虚拟内存与分页**：地址翻译、多级页表、TLB、缺页、页替换（LRU/clock）、`mmap`/COW/demand paging。实证腿：`/proc/<pid>/maps`、`/proc/<pid>/smaps`、`mmap` + strace 缺页/`getrusage` 观察 minflt/majflt。
5. **中断与系统调用（用户↔内核边界）**：陷入机制、系统调用约定（x86-64 `syscall` 指令 + 寄存器传参）、上下文切换。实证腿：strace 全量、`syscall(2)` 直接调用、objdump 看 libc wrapper 的 `syscall` 指令。
6. **文件系统**（次优先）：inode/目录/文件描述符表、VFS 抽象、页缓存、`open/read/write/lseek`。实证腿：strace I/O、`/proc/<pid>/fd`、`stat`。

### 🔧 内核实践（xv6 / 本机 syscall）—— 作为 L4-01 的第三条实证腿并入
- **定位**：R1 已裁定「OS 内核实践 → 🔧并入 L4-01，作 xv6/内核实证腿，不单独立报告」。它不是独立课，而是把 L4-01 上面 6 个方向从「读教材」升级到「读/改一手内核源码 + 本机 syscall 实证」。
- **两条落地路径**（R4 取证时二选一或并用）：
  - **xv6-riscv 源码（MIT 6.1810/6.S081 教学 OS）**：读得懂的完整内核——`proc.c`（调度器/上下文切换 `swtch`）、`trap.c`（中断/系统调用分发）、`vm.c`（分页）、`spinlock.c`/`sleeplock.c`（同步）。用于讲**机制原理**（不受 Linux 复杂度淹没）。⚠ xv6 需 RISC-V 工具链 + QEMU，本机默认无（见 §4，标「工具链未取，R4 需装 `qemu-system-riscv` + `riscv64-*-gcc` 或只读源码腿）。
  - **本机 Linux syscall 实证**：真机 6.18.5 上 strace/`/proc`/直接 `syscall()`，讲**当前实现的真实行为**。本机即可跑（§4 已核）。
- **分账**：xv6 = 「机制为什么这样设计」的可读参照实现；本机 Linux = 「基线上到底怎么跑」的实证。两者互补，正好对应方法论的「腿②源码」+「腿③本机实证」。

### L4-05 并发与并行 —— 5 个重点方向
1. **内存模型与重排序**：为什么需要内存模型、happens-before、可见性、编译器重排 vs 硬件重排、TSO vs 弱序（表 D）。一手：C11/C++11 内存模型 + Intel SDM。实证腿：litmus（SB/MP）、`atomic_thread_fence`。
2. **原子操作与 memory_order**：C11 `<stdatomic.h>`、relaxed/acquire/release/acq_rel/seq_cst 六档、`atomic_is_lock_free`、CAS。实证腿：本机已核 atomic 可用且 lock-free（§4）。
3. **锁 vs 无锁**：从 mutex 到 CAS 循环，无锁栈/队列、**ABA 问题**、进展性（lock-free/wait-free 定义）、内存回收难题。实证腿：写一个无锁计数器/栈，TSan 验证。（表 C/表 E）
4. **数据竞争的定义与复现**：data race 的**精确定义**（两个访问、至少一个写、无 happens-before、非原子）、UB 后果、检测工具。实证腿：**本机已核 TSan 能检出竞争（§4）**——这是本组最扎实的一条实证腿。
5. **消息传递与并行范式**：共享内存 vs 消息传递、channel/actor、（可选）数据并行 vs 任务并行、Amdahl/Gustafson 定律作为并行**上界**的心智模型。实证腿：pipe/socketpair 做消息传递；Amdahl 用 4 核 pthread 加速比实测对照理论。

---

## 3 · 边界分账：L4-01 的并发原语 vs L4-05 的内存模型深挖

这是本组最容易内容打架的地方，明确切线：

| 主题 | 归 L4-01（OS 视角：机制与代价） | 归 L4-05（并发视角：正确性与模型） |
|------|-------------------------------|-----------------------------------|
| **mutex** | 它如何用 futex 进内核、如何让出 CPU、竞争时的睡眠/唤醒、优先级倒置 | 它建立哪些 happens-before 边、能否用 acquire/release 原子替代、是否可组合 |
| **原子性** | 从哪来（`LOCK` 前缀、关中断、单核 vs SMP） | 语言级 `atomic_*` 语义、`memory_order` 六档、lock-free 判定 |
| **调度** | 调度算法、时间片、抢占点、EEVDF | 抢占如何制造交错→数据竞争的**根因** |
| **死锁** | 四条件、预防/避免/检测/银行家算法（OS 资源分配层面） | 锁序、无锁如何**回避**死锁、活锁/ABA（算法层面） |
| **内存屏障** | 内核何处插屏障、`smp_mb()` 家族 | TSO vs 弱序、`atomic_thread_fence`、litmus 语义 |

一句话切线：**「原语是什么、内核里怎么实现、要付多少代价」→ L4-01；「在最弱内存模型下这段并发代码为什么对/错、能不能更弱」→ L4-05。** 死锁的「四条件+银行家」放 L4-01（资源管理），死锁的「锁序/无锁回避/ABA」放 L4-05（并发算法）。内存模型深挖整体是 L4-05 的主场，L4-01 只在系统调用/中断上下文点到屏障。

---

## 4 · 一手源候选 + 本机实证点（含本机可行性预核结果）

### 一手源候选（腿①文档 / 腿②源码）
| 源 | 类型 | 用于 | 时效/版本注 |
|----|------|------|-------------|
| **xv6: a simple, Unix-like teaching OS**（MIT 6.1810 book + xv6-riscv 源码） | 教材+源码 | L4-01 全部方向的可读参照实现 | RISC-V 版为现行版；x86 版已弃用。需 QEMU 工具链 |
| **OSTEP《Operating Systems: Three Easy Pieces》**（Arpaci-Dusseau，免费在线） | 权威教材 | L4-01 机制主线（虚拟化/并发/持久化三部） | 调度以 MLFQ/CFS 讲，需与 EEVDF 分账 |
| **Linux 内核源码**（`kernel/sched/fair.c` EEVDF、`kernel/futex/`、`fs/`、`mm/`） | 一手实现 | 「当前实现」承重；EEVDF/futex 的真实行为 | 以基线 6.18.5 对应 tag 只读副本 |
| **Intel SDM Vol.3A §「Memory Ordering」/ AMD64 APM Vol.2** | 硬件规范 | 表 D 的 x86-64 TSO 语义 | 权威、稳定 |
| **ISO C `<stdatomic.h>` 与内存模型条款**（C11 引入，C17 无改动，**C23=ISO/IEC 9899:2024** 现行） | 语言规范 | L4-05 原子/memory_order 语义 | ⚠时效：C11 首引入，语义源自 C++11；C23 为当前标准，`memory_order` 相关有措辞调整——R4 以规范文本核实，勿凭记忆 |
| **C++ 标准 `[intro.races]`/`[atomics]`（cppreference 作二手指路）** | 语言规范 | happens-before 精确定义 | C++ 内存模型定义更完整，可交叉印证 |
| **POSIX（IEEE Std 1003.1，2024 版为现行）pthreads / `futex(2)` man** | 标准+手册 | 线程 API 契约、futex 语义 | POSIX 定契约，Linux man 定实现 |
| **《The Art of Multiprocessor Programming》(Herlihy & Shavit)** | 权威教材 | 无锁/wait-free、ABA、进展性 | L4-05 无锁理论主源 |

### 本机实证点（腿③）——预核结果（基线 Linux 6.18.5 / gcc 13.3 / strace 6.8 / gdb 15.1 @2026-07-25）
| # | 实证点 | 工具/命令 | 预核状态 |
|---|--------|-----------|----------|
| E1 | 系统调用轨迹 | `strace -f ./prog` | ✅ 已核可用（strace 6.8，成功 trace `write`） |
| E2 | 进程/内存自省 | `/proc/self/{maps,smaps,status,fd,sched}` | ✅ 已核（`/proc` 齐全） |
| E3 | 线程创建=clone | `strace -e clone pthread 程序` | ✅ 可行（pthread 头就位） |
| E4 | C11 原子 + lock-free | `gcc -std=c11 <stdatomic.h>` | ✅ 已核：`atomic_int` lock-free=1 |
| E5 | **数据竞争检出** | `gcc -fsanitize=thread` | ✅ **已核：TSan 成功检出 `data race`** |
| E6 | 死锁复现+观测 | pthread 交叉锁 + `gdb -p` 看栈 | ✅ 工具就位（gdb 15.1）；R4 构造 |
| E7 | 分页/缺页统计 | `mmap`+`getrusage`（minflt/majflt）、`/proc/self/smaps` | ✅ 可行 |
| E8 | 调度器坐实 | `chrt`、`/proc/sched_debug` | 待 R4 核（坐实基线=EEVDF 而非 CFS） |
| E9 | x86-64 StoreLoad 重排 litmus | relaxed 原子 SB 测试 / `litmus7`(herd) | ⚠ 部分：手写 naive 版本会超时/需正确 harness；`litmus7` **未安装**。R4 需装 herdtools 或写高效 harness，**不得凭记忆声称观察到** |
| E10 | Amdahl 加速比实测 | 4 核 pthread（`nproc`=4） | ✅ 4 核可用 |
| —  | perf 微架构剖析 | `perf` | ❌ **本机无 perf**（归 L5-05；本组不承重） |
| —  | xv6 运行 | `qemu-system-riscv64`+`riscv64-gcc` | ❌ 工具链未取；R4 若走 xv6 运行腿需先装，否则退回**只读源码腿** |

> 已核实证脚本要点（跑完即清，未入库）：TSan 对两线程 `counter++` 竞争打印 `WARNING: ThreadSanitizer: data race`；`atomic_is_lock_free`=1；strace 捕获 `write(1, "atomic=1 lockfree=1\n", 20)`。E9 的 naive spin 版本 40s 超时——故 E9 硬标「需正确 harness，未观察到」，绝不编造重排结果。

---

## 5 · 优先级校准

| 对象 | R1 优先级 | 本组建议 | 理由 |
|------|-----------|----------|------|
| **L4-01 操作系统** | P0（枢纽） | **P0** 维持 | SF 跨层 KA 主承载；L4-02/03/05、L5-01 均依赖它 |
| **🔧 内核实践** | 并入 L4-01 | 作 L4-01 的**腿②+腿③**，不单列 | R1 已裁定；本机 Linux 腿即刻可跑，xv6 腿视工具链 |
| **L4-05 并发与并行** | P1 | **P1** 维持（内部子项分级见下） | PDC+SF；依赖 L4-01 |

**L4-05 内部方向分级**（R3 造 prompt 时用）：
- **P0 级子项**：数据竞争定义与复现（E5 最扎实）、内存模型 happens-before、C11 原子与 memory_order —— 这三者是「并发正确性」的地基，且本机可硬实证。
- **P1 级子项**：锁 vs 无锁（ABA/进展性）、TSO vs 弱序 litmus（受 E9 工具限制）。
- **P2 级子项**：消息传递范式、Amdahl/并行加速比（偏 L6-06 HPC 前置，点到为止）。

**内核实践如何并入 L4-01 作实证腿（执行建议）**：
1. L4-01 每个重点方向配**双腿**——「本机 Linux 实证（E1–E8，即刻可跑）」承「基线真实行为」，「xv6-riscv 只读源码」承「机制为何如此设计」。
2. xv6 的**运行**腿（QEMU）标为「工具链未取，R4 决定是否装」；若不装，退回**只读源码 + 本机 Linux 实证**双腿，仍满足三条腿（文档+源码+本机实证）。
3. 调度方向必须在 R4 实证坐实基线内核是 **EEVDF**（E8），并在报告里对「教材讲 CFS/MLFQ」做规范/实现-历史分账。

---

## 6 · 时效与分账备忘（交给 R4/R5 的红旗）
- 🚩 **Linux 默认调度器 = EEVDF（≥6.6），非 CFS**：基线 6.18.5 必然 EEVDF。教材普遍滞后于此，R4 报告须显式分账并实证。
- 🚩 **C 标准版本**：C11 首引入 `<stdatomic.h>` 与内存模型；C17 无实质改动；**C23（ISO/IEC 9899:2024）为现行**，atomics 措辞有调整——R4 以规范文本核实具体 memory_order 语义，勿凭记忆。
- 🚩 **进程 vs 线程在 Linux 同源**（都是 `task_struct`，差别在 `clone` 标志）——避免落入「线程是轻量进程」的模糊说法，直接落到 `clone(2)` 标志位。
- 🚩 **E9（内存重排 litmus）与 xv6-QEMU 运行腿本机未坐实**：R4 必须先装工具或如实标「未取」，严禁凭记忆写「观察到重排/xv6 输出」。
- 🚩 **perf 本机缺失**：微架构级实证归 L5-05，本组不依赖 perf。

---
### 一手源清单（R4 承重候选，按腿归类）
- 腿①文档：Intel SDM Vol.3A「Memory Ordering」/ AMD64 APM Vol.2；ISO C（C11/C23）`<stdatomic.h>` 与内存模型条款；C++ `[intro.races]`；POSIX IEEE 1003.1(2024) pthreads；Linux man `futex(2)`/`clone(2)`/`mmap(2)`。
- 腿②源码：xv6-riscv（`proc.c`/`trap.c`/`vm.c`/`spinlock.c`）；Linux 6.18.x（`kernel/sched/fair.c` EEVDF、`kernel/futex/`、`mm/`、`fs/`）。
- 腿③本机实证：strace 6.8 / gdb 15.1 / `/proc` / gcc 13.3 `-fsanitize=thread` / `<stdatomic.h>`（E1–E8、E10 已核可行；E9 需 herdtools；xv6 运行需 QEMU/riscv 工具链）。

### 二三手（非承重，仅指路）
- [T1] OSTEP《Operating Systems: Three Easy Pieces》——机制教学主线，调度部分需与 EEVDF 分账 · ⚠教材时效滞后。
- [T2] cppreference（memory_order / atomic）——快速定位规范条款用，结论回落 ISO 文本 · ⚠二手指路。
- [T3] Herlihy & Shavit《The Art of Multiprocessor Programming》——无锁/wait-free 理论 · 权威教材，可升腿①。
