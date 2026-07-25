# 第 5 组「OS 与并发」· Round 3a 大主题分解 · 核实 2026-07-25

> 调查员：第 5 组。基线 Py3.11.15/np2.4.6/gcc13.3 @2026-07-25。
> 目标：把组内每门课拆成**查全的大主题清单**（教学单元级，覆盖全、少重叠、按教学序），锚定权威教材目录/官方 syllabus。
> 覆盖课程：**L4-01 操作系统**（并入 🔧「内核实践」xv6/本机 syscall，作实证性大主题）＋ **L4-05 并发与并行程序设计**。
> 承接 R2 定方向（`round2-partials/g05-os-concurrency.md`）的边界分账（§3）。本文件只做**大主题清单**，不做三条腿深挖（R4）。

---

## 0 · 权威目录锚点（一手，逐项核实 2026-07-25）

| 锚点 | 类型 | 版本 | URL | 核实 |
|------|------|------|-----|------|
| **OSTEP《Operating Systems: Three Easy Pieces》** | 免费权威教材 TOC | 现行网页版（Virtualization/Concurrency/Persistence 三部） | https://pages.cs.wisc.edu/~remzi/OSTEP/ | ✅ 2026-07-25 抓取到完整章目（第 4–57 章 + 附录） |
| **MIT 6.1810 Operating System Engineering** | 官方 syllabus + xv6-riscv | 2024 Fall schedule（22 讲 + 9 labs） | https://pdos.csail.mit.edu/6.1810/2024/schedule.html | ✅ 2026-07-25 抓取到讲次序列 |
| **Berkeley CS162 Operating Systems** | 官方 syllabus | 现行（cs162.org） | https://cs162.org/ | ✅ 2026-07-25 抓取到讲次序列（protection→sync→sched→VM→FS→IO） |
| **《The Art of Multiprocessor Programming》(Herlihy/Shavit/Luchangco/Spear)** | 权威教材 TOC | 2nd ed / Revised Reprint（Part I Principles + Part II Practice，18 章） | https://www.sciencedirect.com/book/monograph/9780124159501/ · https://www.oreilly.com/library/view/the-art-of/9780123973375/ | ✅ 2026-07-25 交叉核到 ch1–18 章题 |
| **ISO C `<stdatomic.h>` 与内存模型条款** | 语言规范 | **C23 = ISO/IEC 9899:2024（现行，2024-10-31 发布）**；atomics 在 §7.17 / §7.31.8。C11 首引入，C17 无实质改动 | 工作草案 N3220 / cppreference c/atomic | ✅ 2026-07-25 核到 C23 为现行、atomics 章节号 |
| **C++ 内存模型 `[intro.races]`/`[atomics]`** | 语言规范 | ISO C++（happens-before 定义更完整，交叉印证 C 模型） | cppreference（作二手指路，结论回落 ISO） | ✅ |
| **Intel SDM Vol.3A「Memory Ordering」/ AMD64 APM Vol.2** | 硬件规范 | 现行（x86-64 TSO 语义） | Intel/AMD 官网 | ✅（R2 已定位，稳定） |

> ⚠ 时效红旗（继承 R2）：① Linux 通用调度器默认 **EEVDF（≥v6.6，2023）取代 CFS**——基线 6.18.5 必为 EEVDF，而 OSTEP/CS162 教材主线仍讲 MLFQ/CFS，属「教材 vs 当前实现」分账，R4 实证坐实。② C 标准现行 = **C23**，`memory_order` 措辞较 C11 有调整，R4 以规范文本核实勿凭记忆。

---

## 1 · L4-01 操作系统（并入内核实践）——大主题清单

> 教学序对齐 OSTEP 三部（虚拟化→并发→持久化），并用 CS162（双模式/保护）与 6.1810（陷入/VM/多核 RCU/xv6）补全「用户态↔内核态边界」与「机制参照实现」两条主线。
> 分账原则（R2 §3）：本课讲「原语是什么、内核里怎么实现、要付多少代价」；并发的「正确性与内存模型」整体归 L4-05。

| 编号 | 大主题 | 一句话范围 | 来源锚点 | 跨课重叠提示 |
|------|--------|-----------|----------|--------------|
| OS-01 | 操作系统概念、双模式与内核结构 | OS = 资源复用器 + 保护边界执行者；用户态/内核态、特权级、系统结构（宏内核 vs 微内核） | OSTEP intro；CS162 L1–3「What is an OS/Protection」；6.1810「OS Design/OS Organization」 | — |
| OS-02 | 进程抽象与进程 API | 进程模型、地址空间视图、PCB（Linux `task_struct`）、`fork`/`execve`/`wait`、COW、僵尸/孤儿 | OSTEP 4–5「Processes/Process API」；CS162 L4 | 进程 vs 线程同源（`clone` 标志）→ 与 OS-04 |
| OS-03 | 受限直接执行：中断、陷入与系统调用 | dual-mode 切换、timer 中断、trap/异常、系统调用约定（x86-64 `syscall` + 寄存器）、上下文切换 | OSTEP 6「Direct Execution」；6.1810「Syscall Entry/Exit」「Page Faults(trap)」「GDB & Calling Conventions」 | 内存屏障只在此点到；深挖归 L4-05（CP-05） |
| OS-04 | 线程抽象与线程 API | 内核线程 vs 用户线程、1:1（`clone`/pthread）vs M:N、协程/用户态任务、并发≠并行 | OSTEP 26–27「Concurrency&Threads/Thread API」；CS162 L5 | 表 A 三档执行流；协程范式亦触 L4-05（CP-14） |
| OS-05 | CPU 调度 | FCFS/SJF-SRTF/RR/MLFQ/lottery/multi-CPU 调度；Linux **EEVDF**（时效分账）与实时类 | OSTEP 7–10；CS162 L10–12「Scheduling」 | 抢占制造交错=数据竞争根因→归 L4-05（CP-07） |
| OS-06 | 同步原语与锁实现 | 锁/条件变量/信号量/monitor；锁的实现（原子指令、关中断、futex 进内核睡眠）；RCU（读多写少） | OSTEP 28–31「Locks/Locked DS/CV/Semaphores」；CS162 L6–9；6.1810「Locking/Coordination/RCU」 | **重叠 L4-05 CP-06/08/09**：本课=机制与代价（futex/让 CPU）；L4-05=happens-before 边/可组合性 |
| OS-07 | 并发 bug 与死锁 | 死锁四条件（Coffman）、预防/避免/检测、银行家算法；非死锁并发 bug（原子性/顺序违背） | OSTEP 32「Concurrency Bugs」；CS162 L13「Deadlock」 | **重叠 L4-05**：四条件+银行家归本课（资源管理）；锁序/无锁回避/ABA 归 L4-05（CP-10） |
| OS-08 | 地址空间与内存 API | 虚拟地址空间布局、`malloc`/`free`、`mmap`、空闲空间管理（分配器/碎片） | OSTEP 13–14,17「Address Spaces/Memory API/Free-Space」；6.1810「VM for Applications」 | — |
| OS-09 | 地址翻译与分页机制 | 分段、多级页表、TLB、大页、地址翻译硬件 | OSTEP 15–20；6.1810「Page Tables」 | — |
| OS-10 | 缺页、页替换与交换 | demand paging、page fault 处理、替换策略（LRU/clock/工作集）、COW、swap 机制/策略 | OSTEP 21–23「Swapping Mechanisms/Policies/Complete VM」；6.1810「Page Faults」 | — |
| OS-11 | I/O 子系统与设备驱动 | 设备交互模型、中断 vs 轮询、DMA、设备驱动分层、中断处理下半部 | OSTEP 36「I/O Devices」；6.1810「Device Drivers」；CS162 L17「General IO」 | — |
| OS-12 | 持久存储介质与 RAID | 机械盘几何/调度、SSD/闪存转换层、RAID 级别与可靠性权衡 | OSTEP 37,38,44「HDD/RAID/SSD」 | — |
| OS-13 | 文件与目录接口 | 文件描述符表、inode、VFS 抽象、`open/read/write/lseek`、页缓存、硬/软链接 | OSTEP 39「Files & Directories」；CS162 L18 | — |
| OS-14 | 文件系统实现与崩溃一致性 | FS 磁盘布局、FFS 局部性、`fsck`、journaling、LFS、数据完整性/校验 | OSTEP 40–43,45；6.1810「File Systems/Crash Recovery/FS Performance」 | — |
| OS-15 | 虚拟化与容器隔离 | 硬件虚拟化/hypervisor/trap-and-emulate；namespaces/cgroups 作为 OS 隔离的工程落地 | 6.1810「Virtual Machines/Meltdown」；OSTEP VM 概念 | **跨课 L5-08 容器与云原生**：本课=内核隔离机制；L5-08=Docker/K8s 编排落地 |
| OS-16 | 内核实践实证腿（xv6 + 本机 Linux） | 🔧R1 裁定并入：xv6-riscv 只读源码（`proc.c`/`trap.c`/`vm.c`/`spinlock.c`）讲「机制为何如此」+ 本机 Linux（strace/`/proc`/直接 `syscall`）讲「基线真实行为」 | 6.1810 labs（utils→syscall→pgtbl→traps→COW→net→lock→FS→mmap）；xv6-riscv 源码；基线 Linux 6.18.5 | 非独立知识点，是 OS-02~OS-14 的腿②源码 + 腿③实证；xv6 运行需 QEMU/riscv 工具链（本机未取，R4 决定装或退只读源码腿） |

**建议大主题数（L4-01）：K = 16**（其中 OS-16 为实证腿，非独立知识对象，但按 R1 裁定作大主题登记）。

---

## 2 · L4-05 并发与并行程序设计——大主题清单

> 教学序对齐 AMP 两部：Part I 原理（互斥→并发对象→共享内存基础→原语能力/共识）→ Part II 实践（自旋锁→阻塞同步→并发数据结构→协调→任务并行→事务内存）；再叠加语言级内存模型（C11/C23 + C++ + Intel SDM）作为「无锁正确性」的规范地基。
> 分账原则（R2 §3）：本课讲「在最弱内存模型下这段并发代码为什么对/错、能不能更弱」；原语的内核实现与代价归 L4-01。

| 编号 | 大主题 | 一句话范围 | 来源锚点 | 跨课重叠提示 |
|------|--------|-----------|----------|--------------|
| CP-01 | 互斥问题与理论 | 互斥问题的形式化、Peterson/bakery 算法、为什么纯软件互斥难、时间/因果 | AMP ch2「Mutual Exclusion」 | 与 OS-06 分账：此处是**问题本质与正确性证明**，非内核 mutex 实现 |
| CP-02 | 并发对象与线性一致性 | 顺序一致性 vs 线性一致性（linearizability）、正确性条件、可组合性、进展性（wait-free/lock-free/阻塞定义） | AMP ch3「Concurrent Objects」 | 进展性定义是本课地基（R2 P0 级） |
| CP-03 | 共享内存基础与寄存器构造 | safe/regular/atomic 寄存器、从弱寄存器构造强寄存器、读写原子性来源 | AMP ch4「Foundations of Shared Memory」 | — |
| CP-04 | 同步原语的相对能力与共识 | consensus number、wait-free 同步层级、CAS 的通用性（universality of consensus） | AMP ch5–6 | 解释「为何 CAS 万能、read/write 不行」，接 CP-06 |
| CP-05 | 内存一致性模型与重排序 | happens-before、SC/**TSO(x86-64)** vs 弱序(ARM/POWER)、编译器重排 vs 硬件重排、屏障来源、litmus(SB/MP) | Intel SDM Vol.3A「Memory Ordering」/AMD64 APM Vol.2；C++ `[intro.races]`；AMP 附录 | **本课主场**；L4-01 仅在 OS-03/OS-06 点到内核屏障。litmus 实证受 E9 工具限制（herdtools 未装） |
| CP-06 | 原子操作与 memory_order | C11/**C23** `<stdatomic.h>`、relaxed/acquire/release/acq_rel/seq_cst/consume 六档、`atomic_thread_fence`、CAS/LL-SC、`atomic_is_lock_free` | ISO C 9899:2024 §7.17/§7.31.8；cppreference（指路） | **重叠 OS-06**：语言级语义归本课；`LOCK` 前缀/关中断等原子性**来源**归 L4-01 |
| CP-07 | 数据竞争定义与 UB | data race 精确定义（两访问/≥1 写/无 happens-before/非原子）、UB 后果、TSan 检测 | ISO C/C++ 标准数据竞争条款 | **本机最扎实实证腿（E5：TSan 已核可检出 `data race`）**；根因触 OS-05 抢占交错 |
| CP-08 | 自旋锁与竞争 | TAS/TTAS/exponential backoff/队列锁(MCS/CLH)、缓存一致性对自旋性能的影响、NUMA | AMP ch7「Spin Locks and Contention」 | **重叠 OS-06**：此处=可扩展性与内存层级；OS 侧=持锁被抢占的代价/futex |
| CP-09 | 阻塞同步与 monitor | monitor 模式、条件变量、信号量、可重入、**虚假唤醒→while 判定**、生产者消费者/读者写者 | AMP ch8「Monitors and Blocking Synchronization」 | **重叠 OS-06**：此处=同步惯用法正确性；OS 侧=陷入内核睡眠/唤醒机制 |
| CP-10 | 无锁并发数据结构 | 链表 coarse→fine→optimistic→lazy→lock-free、并发队列/栈、**ABA 问题**、消除(elimination)、内存回收(safe reclamation) | AMP ch9–11「Linked Lists/Queues&ABA/Stacks」 | **重叠 OS-07 死锁**：锁序/无锁如何**回避**死锁、ABA、活锁归本课（算法层） |
| CP-11 | 可扩展并发结构与分布式协调 | 并发 hashing、skiplist/平衡搜索、优先队列、counting networks、sorting、组合 | AMP ch12–15 | — |
| CP-12 | 任务并行与调度 | futures、work-stealing、fork-join、barriers、任务图与负载均衡 | AMP ch16–17「Futures/Barriers」 | 与 L6-06 HPC 前置衔接 |
| CP-13 | 事务内存 | STM/HTM、乐观并发、原子块编程模型、与锁的对比 | AMP ch18「Transactional Memory」 | 成熟度：HTM 硬件支持有限，R4 硬标平台可用性 |
| CP-14 | 消息传递与并行范式 | 共享内存 vs 消息传递、channel/actor（Go/Erlang）、MPI、"share by communicating"、数据并行 vs 任务并行 | AMP 外补（Go/Erlang/MPI 一手）；R2 表 C | **重叠 OS-04 协程**、L5-01 分布式；本课=范式正确性与选型 |
| CP-15 | 并行性能模型 | Amdahl/Gustafson 定律、加速比/效率、可扩展性上界作为心智模型（并行**能达到什么**的界） | AMP ch1（speedup 讨论）；R2 §表 | **跨课 L6-06 HPC**：本课点到为止作上界模型；实测调优/GPU 归 L6-06 |

**建议大主题数（L4-05）：K = 15**（P0 级：CP-02/05/06/07 —— 并发正确性地基且本机可硬实证；P1：CP-08/09/10/CP-04；P2：CP-11/12/13/14/15）。

---

## 3 · 组内跨课分账小结（承 R2 §3，供 R4 防打架）

- **同步原语（mutex/spinlock/monitor/信号量）**：OS-06 讲**机制与代价**（futex 进内核、让 CPU、优先级倒置、RCU 宽限期）；CP-06/08/09 讲**正确性与模型**（happens-before 边、可组合性、可扩展性、虚假唤醒纪律）。
- **原子性**：OS-06 讲**来源**（`LOCK` 前缀/关中断/SMP）；CP-06 讲**语言级语义**（`memory_order` 六档、lock-free 判定）。
- **调度**：OS-05 讲算法/时间片/抢占点/EEVDF；CP-07 只借「抢占制造交错」解释数据竞争根因。
- **死锁**：OS-07 讲四条件+预防/避免/检测/银行家（资源分配层）；CP-10 讲锁序/无锁回避/ABA/活锁（并发算法层）。
- **内存屏障/模型**：整体 CP-05 主场；OS-03/OS-06 仅在 syscall/中断/内核锁上下文点到 `smp_mb()` 家族。
- **虚拟化/隔离**：OS-15 内核机制 ↔ L5-08 容器云原生工程落地。
- **并行性能**：CP-15 上界模型 ↔ L6-06 HPC 实测调优/GPU。

---

## 建议大主题数：K

- **L4-01 操作系统（并入内核实践）：K = 16**
- **L4-05 并发与并行程序设计：K = 15**
- **本组合计 T = 31 个大主题（2 门课）**
