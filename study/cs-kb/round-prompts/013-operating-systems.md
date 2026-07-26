# Round3c · L4-01 操作系统 · 报告 prompt（P0）

> 说明：L4-01 在 round3a 登记 16 个大主题（OS-01~OS-16），其中 **OS-16「内核实践实证腿」经 R1 裁定非独立知识对象**，本轮按课程标题「操作系统（并入内核实践）」的口径，把 OS-16 的 xv6-riscv 只读源码腿（`proc.c`/`trap.c`/`vm.c`/`spinlock.c`）与本机 Linux 实证工具带（strace/`/proc`/直接 syscall/perf）**并入相应大主题**作实证性内容，不单独成篇。故本文件产出 **K = 15** 条报告 prompt（OS-01~OS-15）。
> 全课共用一手锚点（round3a §0，核实 2026-07-25）：OSTEP 网页版（https://pages.cs.wisc.edu/~remzi/OSTEP/）；MIT 6.1810 2024 Fall（https://pdos.csail.mit.edu/6.1810/2024/schedule.html）+ xv6-riscv 源码；Berkeley CS162 现行（https://cs162.org/）。时效红旗：Linux 通用调度器 ≥v6.6 已由 **EEVDF 取代 CFS**（基线 6.18.5 必为 EEVDF），OSTEP/CS162 教材主线仍讲 MLFQ/CFS，属「教材 vs 当前实现」分账。xv6 运行需 QEMU/riscv 工具链（本机未取，OS-16.6 待核）——凡涉 xv6 一律退**只读源码腿**，不要求真跑。

```text
[P0] L4-01·大主题01 操作系统概念、双模式与内核结构 — 报告prompt
任务：为「L4-01·大主题01 操作系统概念、双模式与内核结构」产出一份 report-format v3（study/cs-kb/report-format.md）教辅式报告。先读 brief.md、report-format.md（v3 模板）、round3a-topics/g05-os-concurrency.md（OS-01 行与 §0 锚点）、round3b-subtopics/g05-os-concurrency.md 中 OS-01 一节。

粒度判定（先做）：先判「本大主题 1 份 or 拆 N 份 + 理由」，写在报告最前。默认 1 大主题=1 报告；小主题多/跨机制/过长才拆。判定依据写清。

v3 格式硬性：标题层级固定 #（报告标题=大主题）→ ##（小主题=章节）→ ###（每个内容项一节）。### 下**不写「核心概念：」「辅助说明：」等任何标签**，直接正文分段：先核心说明（是什么/定义/关键结论，正确），再充分辅助说明（面向初学者把它讲懂所需——直觉/为什么/最小例子/易错点/前后关联，写到初学者能懂为止，可多段，不是点缀）。系统调用/数据结构/时序图独占整行或独立代码块，不内联埋进句子。教辅口吻。广度优先：每项讲到初学者懂即止，不做专家级纵深（不写长篇机制深挖/推导/设计权衡长论）。

小主题清单（作 ## 章节，可让下游读 round3b OS-01 核对；本 prompt 已点名号+关键内容项，自足）：
- 1.1 OS 作为资源虚拟化器：CPU/内存/设备的复用与抽象；「三块拼图」（虚拟化/并发/持久化）预告。
- 1.2 双模式与特权级：用户态/内核态、ring 0/3、模式位、受保护指令。
- 1.3 内核结构谱系：宏内核 vs 微内核 vs 混合；Linux = 宏内核 + 可加载模块。
- 1.4 系统调用作为受保护接口：syscall 是穿越保护边界的唯一受控入口、ABI 稳定契约。

多来源比对（强制）：每个内容项 ≥2 个独立来源交叉核对，优先一手（OSTEP intro 各章 / CS162 L1–3 / 6.1810「OS Design」「OS Organization」讲义 / Linux 源码）；来源打架两边都记、点明分歧、不和稀泥。不编造版本号/默认值，未证实标「待核」，标核实日期。本机实证可选（不强制）：用户态执行 `cli` 触发 SIGSEGV/#GP 演示保护边界；`lsmod`/`cat /proc/modules` 看模块化宏内核；`strace` 任一命令看用户态→内核态调用序列。实证是补充，不能替代多来源比对。每章末集中列「来源与时效」（锚点+版本+核实日期、前沿项标演进快、冲突项两边定位），不逐项脚注。

权威锚点：OSTEP intro（网页版）；CS162 L1–3「What is an OS/Protection」（cs162.org 现行）；MIT 6.1810 2024 Fall「OS Design/OS Organization」。核实基线 2026-07-25。

落盘：写入 findings/013-operating-systems.md 家族——本大主题单独成文用 findings/013-operating-systems-concepts-dualmode-kernel.md；若拆分按 report-format v3 §一 用 …-a/-b.md，避免互相覆盖。抬头带基线串「基线 Py3.11.15/np2.4.6/gcc13.3 @2026-07-25」。报告须自足（命令/环境串贴正文），不残留工具标签/控制字符。
```

```text
[P0] L4-01·大主题02 进程抽象与进程 API — 报告prompt
任务：为「L4-01·大主题02 进程抽象与进程 API」产出一份 report-format v3 教辅式报告。先读 brief.md、report-format.md（v3）、round3a-topics/g05-os-concurrency.md（OS-02 行与 §0 锚点）、round3b-subtopics/g05-os-concurrency.md 中 OS-02 一节。

粒度判定（先做）：先判「1 份 or 拆 N 份 + 理由」，写在最前。

v3 格式硬性：#→##→###；### 下不写标签，直接核心说明（是什么/定义/关键结论）+ 充分辅助说明（初学者向：直觉/为什么/最小例子/易错点/关联，写到懂为止，可多段）。系统调用（`fork`/`execve`/`wait`）、数据结构（PCB/`task_struct` 字段、状态机）、时序独占行/独立块。教辅口吻；广度优先、讲到初学者懂但不做专家纵深。

小主题清单（## 章节；下游可读 round3b OS-02 核对）：
- 2.1 进程模型与地址空间视图：每进程独占的代码/数据/堆/栈虚拟视图。
- 2.2 PCB / `task_struct` 与状态机：进程控制块字段、状态 R/S/D/Z/T 转换。
- 2.3 fork/execve/wait 生命周期：复制-替换-回收三步、父子返回值语义。
- 2.4 写时复制（COW）：fork 后共享物理页、写触发复制。
- 2.5 僵尸与孤儿、SIGCHLD：未回收退出态=僵尸、父先死=孤儿被 init/reaper 收养。

内核实践并入（OS-16，作实证性内容）：并入 OS-16.1 xv6 `proc.c` 的 `struct proc` 进程表（只读源码解释「进程在内核里长什么样」）；OS-16.5 本机 `strace -f` 看 fork 背后的 `clone`/`execve`/`wait4`。xv6 QEMU/riscv 未装（OS-16.6 待核），退只读源码腿，不要求真跑。

多来源比对（强制）：每项 ≥2 独立源，优先一手（OSTEP 4–5「Processes/Process API」/ CS162 L4 / Linux `task_struct` 源码 / xv6 `proc.c`）；冲突两边都记。不编造字段/默认值，标「待核」+核实日期。本机实证可选：`cat /proc/self/maps` 看段布局；`ps -o pid,stat,cmd` + `/proc/[pid]/status`；小程序 `strace -f` 看 clone/execve/wait4；fork 后对比 `/proc/[pid]/smaps` Shared→Private；故意不 wait 造僵尸看 `Z` 态。实证不替代比对。章末集中列来源与时效。

权威锚点：OSTEP 4–5「Processes/Process API」；CS162 L4；xv6-riscv `proc.c`（只读）；Linux `task_struct`。基线 2026-07-25。

落盘：findings/013-operating-systems.md 家族——单独成文用 findings/013-operating-systems-process-api.md；拆分用 …-a/-b.md。抬头带基线串。自足、不残留工具标签/控制字符。
```

```text
[P0] L4-01·大主题03 受限直接执行：中断、陷入与系统调用 — 报告prompt
任务：为「L4-01·大主题03 受限直接执行：中断、陷入与系统调用」产出一份 report-format v3 教辅式报告。先读 brief.md、report-format.md（v3）、round3a OS-03 行与 §0、round3b OS-03 一节。

粒度判定（先做）：先判「1 份 or 拆 N 份 + 理由」。

v3 格式硬性：#→##→###；### 下不写标签，核心说明 + 充分辅助说明（初学者向、必需）。系统调用约定（`syscall` 指令、rax=号、rdi..r9 传参、返回 rax）、寄存器保存/恢复、trap 时序独占行/独立块。教辅口吻；广度优先、不做专家纵深。

小主题清单（## 章节；下游可读 round3b OS-03 核对）：
- 3.1 受限直接执行协议：内核设陷阱表→启动进程→时钟中断夺回控制的三步舞。
- 3.2 中断/异常/陷入分类：trap(自愿)/fault(可恢复)/abort、同步 vs 异步。
- 3.3 x86-64 系统调用约定：`syscall` 指令、rax=号、rdi..r9 传参、返回 rax。
- 3.4 上下文切换机制：保存/恢复寄存器、切页表、TLB 刷新代价。
- 3.5 时钟中断与抢占点：周期 tick 作为强占内核控制权的时机。

内核实践并入（OS-16）：并入 OS-16.2 xv6 `trap.c` 的 trampoline/usertrap/kerneltrap 陷入返回路径；OS-16.1 xv6 `swtch` 上下文切换（只读源码解释「切换到底切了什么」）；OS-16.5 本机内联汇编直发 `write` syscall 对照 `strace`。xv6 QEMU 未装（OS-16.6 待核）→ 只读源码腿。

多来源比对（强制）：每项 ≥2 独立源，优先一手（OSTEP 6「Direct Execution」/ 6.1810「Syscall Entry/Exit」「Page Faults(trap)」「GDB & Calling Conventions」/ x86-64 System V ABI / xv6 `trap.c`）；冲突两边都记。系统调用号/寄存器约定不凭记忆，比对通过或标「待核」+日期。本机实证可选：触发除零/缺页看信号（SIGFPE/SIGSEGV）；内联汇编直发 syscall 对照 strace；`perf stat` context-switches；`/proc/[pid]/status` *_ctxt_switches。实证不替代比对。章末集中列来源与时效。

权威锚点：OSTEP 6「Direct Execution」；6.1810「Syscall Entry/Exit」「Page Faults」「Calling Conventions」；x86-64 System V AMD64 ABI；xv6-riscv `trap.c`（只读）。基线 2026-07-25。

落盘：findings/013-operating-systems.md 家族——单独成文用 findings/013-operating-systems-trap-syscall.md；拆分用 …-a/-b.md。抬头带基线串。自足、不残留工具标签/控制字符。
```

```text
[P0] L4-01·大主题04 线程抽象与线程 API — 报告prompt
任务：为「L4-01·大主题04 线程抽象与线程 API」产出一份 report-format v3 教辅式报告。先读 brief.md、report-format.md（v3）、round3a OS-04 行与 §0、round3b OS-04 一节。

粒度判定（先做）：先判「1 份 or 拆 N 份 + 理由」。

v3 格式硬性：#→##→###；### 下不写标签，核心说明 + 充分辅助说明（初学者向、必需）。系统调用/标志（`clone()` 的 CLONE_VM/FILES/SIGHAND、pthread_create）独占行/独立块。教辅口吻；广度优先、不做专家纵深。

小主题清单（## 章节；下游可读 round3b OS-04 核对）：
- 4.1 线程 vs 进程：共享地址空间/fd 表、独立栈与寄存器上下文。
- 4.2 内核线程 vs 用户线程、1:1 vs M:N：调度实体归属、映射模型权衡（Linux NPTL 为 1:1）。
- 4.3 `clone()` 标志谱与 pthread：CLONE_VM/FILES/SIGHAND… 决定「共享多少」、pthread_create 落地。
- 4.4 并发 ≠ 并行：单核交错 vs 多核真同时执行。
- 4.5 协程/用户态任务：协作式让出、无内核介入的轻量执行流（对比线程；范式深挖归 L4-05 CP-14，本课点到）。

分账提醒：并发正确性/内存模型归 L4-05；本课讲线程「是什么、内核怎么实现、代价几何」。

多来源比对（强制）：每项 ≥2 独立源，优先一手（OSTEP 26–27「Concurrency & Threads / Thread API」/ CS162 L5 / Linux `clone(2)` man 与源码 / NPTL 文档）；冲突两边都记。CLONE_* 标志语义不凭记忆，比对或标「待核」+日期。本机实证可选：多线程程序看 `/proc/[pid]/task/` 多 TID；`strace -f` 看 pthread 背后的 clone flags；`taskset -c 0` 绑单核 vs 多核对比计时。实证不替代比对。章末集中列来源与时效。

权威锚点：OSTEP 26–27「Concurrency & Threads / Thread API」；CS162 L5；Linux `clone(2)`/NPTL。基线 2026-07-25。

落盘：findings/013-operating-systems.md 家族——单独成文用 findings/013-operating-systems-threads-api.md；拆分用 …-a/-b.md。抬头带基线串。自足、不残留工具标签/控制字符。
```

```text
[P0] L4-01·大主题05 CPU 调度 — 报告prompt
任务：为「L4-01·大主题05 CPU 调度」产出一份 report-format v3 教辅式报告。先读 brief.md、report-format.md（v3）、round3a OS-05 行与 §0（含时效红旗 EEVDF）、round3b OS-05 一节。

粒度判定（先做）：先判「1 份 or 拆 N 份 + 理由」。

v3 格式硬性：#→##→###；### 下不写标签，核心说明 + 充分辅助说明（初学者向、必需）。任何公式（如周转时间、加权 vruntime）独占整行（块级），不内联。教辅口吻；广度优先、不做专家纵深。

小主题清单（## 章节；下游可读 round3b OS-05 核对）：
- 5.1 调度指标与工作负载假设：周转/响应/公平、批处理 vs 交互假设。
- 5.2 基本算法：FCFS/SJF/SRTF/RR、时间片长短权衡。
- 5.3 MLFQ：多级反馈队列——优先级、配额、周期提升防饿死。
- 5.4 比例份额：lottery/stride/CFS 红黑树 vruntime。
- 5.5 Linux 当前实现：EEVDF 与调度类：≥v6.6 EEVDF 取代 CFS、RT/DL/普通类（教材 vs 实现分账，EEVDF 细节以内核文档为准，标「待核」）。
- 5.6 多处理器调度：CPU 亲和性、负载均衡、缓存亲和。

内核实践并入（OS-16）：并入 OS-16.1 xv6 `proc.c` 的 `scheduler()`/`swtch`（只读源码解释最小调度循环）；OS-16.5 本机 `nice`/`renice`/`chrt`、`/proc/[pid]/sched`。

时效硬标：基线 6.18.5 通用调度器为 EEVDF（≥6.6，2023 取代 CFS）；OSTEP/CS162 讲 MLFQ/CFS 属教材主线——**教材 vs 当前实现两账都记**，EEVDF 机制细节以 Linux 内核文档核实，未证实标「待核」。

多来源比对（强制）：每项 ≥2 独立源，优先一手（OSTEP 7–10 / CS162 L10–12 / Linux 内核调度文档与源码 / xv6 `proc.c`）；冲突两边都记，尤其教材算法 vs EEVDF 现状。不编造调度参数默认值，标「待核」+日期。本机实证可选：`nice`/`renice` 观察份额；`chrt`、`/proc/[pid]/sched`；`taskset` + `/proc/[pid]/status` Cpus_allowed。实证不替代比对。章末集中列来源与时效（EEVDF 标演进快·锚版本）。

权威锚点：OSTEP 7–10「Scheduling」；CS162 L10–12；Linux 内核调度文档（EEVDF）；xv6-riscv `proc.c`（只读）。基线 2026-07-25。

落盘：findings/013-operating-systems.md 家族——单独成文用 findings/013-operating-systems-cpu-scheduling.md；拆分用 …-a/-b.md。抬头带基线串。自足、不残留工具标签/控制字符。
```

```text
[P0] L4-01·大主题06 同步原语与锁实现 — 报告prompt
任务：为「L4-01·大主题06 同步原语与锁实现」产出一份 report-format v3 教辅式报告。先读 brief.md、report-format.md（v3）、round3a OS-06 行与 §0（含 §3 跨课分账）、round3b OS-06 一节。

粒度判定（先做）：先判「1 份 or 拆 N 份 + 理由」。本主题跨机制（自旋/futex/CV/RCU），是否拆分要给理由。

v3 格式硬性：#→##→###；### 下不写标签，核心说明 + 充分辅助说明（初学者向、必需）。原子指令（`lock` 前缀）、系统调用（`futex`）、数据结构（自旋锁/睡眠锁字段）独占行/独立块。教辅口吻；广度优先、不做专家纵深。

小主题清单（## 章节；下游可读 round3b OS-06 核对）：
- 6.1 锁的接口与目标：正确性/公平/性能三目标。
- 6.2 硬件原语造锁：TAS/CAS/关中断构建自旋锁。
- 6.3 条件变量与信号量：CV 睡眠等待条件、semaphore 计数原语的内核语义。
- 6.4 futex：用户态无争用快路径 + 内核睡眠慢路径。
- 6.5 RCU：读多写少——无锁读 + 宽限期延迟回收。
- 6.6 锁的代价：让 CPU、优先级倒置、锁护航、持锁被抢占。

内核实践并入（OS-16）：并入 OS-16.4 xv6 `spinlock.c`/`sleeplock.c` 的 push_off 关中断、acquire/release、睡眠锁（只读源码解释「锁为什么这么写」）；`objdump` 看 `lock` 前缀；OS-16.5 本机 `strace` 看 pthread_mutex 争用时的 `futex`；RCU 读 6.1810 讲义 + Linux 源码只读（xv6 无 RCU）。

分账提醒（承 round3a §3）：本课=**机制与代价**（futex 进内核、让 CPU、优先级倒置、RCU 宽限期、原子性来源如 `LOCK` 前缀/关中断）；**正确性/happens-before 边/可组合性/虚假唤醒纪律归 L4-05（CP-06/08/09）**，本课不深挖，只在交界点一句指路。

多来源比对（强制）：每项 ≥2 独立源，优先一手（OSTEP 28–31「Locks/Locked DS/CV/Semaphores」/ CS162 L6–9 / 6.1810「Locking/Coordination/RCU」/ xv6 `spinlock.c`/`sleeplock.c` / Linux `futex(2)` 与 RCU 文档）；冲突两边都记。不编造 futex op/默认值，标「待核」+日期。本机实证可选：xv6 源码 + `objdump` 看 `lock` 前缀；`strace` 看争用 `futex`。实证不替代比对。章末集中列来源与时效。

权威锚点：OSTEP 28–31；CS162 L6–9；6.1810「Locking/Coordination/RCU」；xv6-riscv `spinlock.c`/`sleeplock.c`（只读）；Linux `futex(2)`/RCU 文档。基线 2026-07-25。

落盘：findings/013-operating-systems.md 家族——单独成文用 findings/013-operating-systems-sync-locks.md；拆分用 …-a/-b.md。抬头带基线串。自足、不残留工具标签/控制字符。
```

```text
[P0] L4-01·大主题07 并发 bug 与死锁 — 报告prompt
任务：为「L4-01·大主题07 并发 bug 与死锁」产出一份 report-format v3 教辅式报告。先读 brief.md、report-format.md（v3）、round3a OS-07 行与 §0（含 §3 分账）、round3b OS-07 一节。

粒度判定（先做）：先判「1 份 or 拆 N 份 + 理由」。

v3 格式硬性：#→##→###；### 下不写标签，核心说明 + 充分辅助说明（初学者向、必需）。四条件列表、银行家算法的安全序列判定步骤、资源分配图独占行/独立块；任何判定条件公式独占行。教辅口吻；广度优先、不做专家纵深。

小主题清单（## 章节；下游可读 round3b OS-07 核对）：
- 7.1 非死锁并发 bug：原子性违背、顺序违背。
- 7.2 死锁四条件（Coffman）：互斥/持有并等待/不可抢占/循环等待。
- 7.3 死锁预防：逐条破坏四条件。
- 7.4 死锁避免：银行家算法——安全状态/安全序列判定。
- 7.5 死锁检测与恢复：资源分配图/等待图找环、恢复策略。

分账提醒（承 round3a §3）：本课=**资源管理层**（四条件 + 预防/避免/检测/银行家）；**锁序/无锁回避/ABA/活锁归 L4-05（CP-10）算法层**，本课不深挖，交界处一句指路。

多来源比对（强制）：每项 ≥2 独立源，优先一手（OSTEP 32「Concurrency Bugs」/ CS162 L13「Deadlock」/ 原始 Coffman 条件文献 / 银行家算法原始描述）；冲突两边都记。银行家判定步骤不凭记忆，比对通过。本机实证可选：`-fsanitize=thread` 复现原子性/顺序违背；造 AB–BA 死锁用 `gdb` 看两线程互等栈、`cat /proc/[pid]/task/*/stack`；银行家算法数值模拟可跑。实证不替代比对。章末集中列来源与时效。

权威锚点：OSTEP 32「Concurrency Bugs」；CS162 L13「Deadlock」；Coffman 四条件原始来源。基线 2026-07-25。

落盘：findings/013-operating-systems.md 家族——单独成文用 findings/013-operating-systems-deadlock.md；拆分用 …-a/-b.md。抬头带基线串。自足、不残留工具标签/控制字符。
```

```text
[P0] L4-01·大主题08 地址空间与内存 API — 报告prompt
任务：为「L4-01·大主题08 地址空间与内存 API」产出一份 report-format v3 教辅式报告。先读 brief.md、report-format.md（v3）、round3a OS-08 行与 §0、round3b OS-08 一节。

粒度判定（先做）：先判「1 份 or 拆 N 份 + 理由」。

v3 格式硬性：#→##→###；### 下不写标签，核心说明 + 充分辅助说明（初学者向、必需）。系统调用（`malloc`/`free`/`mmap`/`brk`/`sbrk`）、地址空间布局图、free list 数据结构独占行/独立块。教辅口吻；广度优先、不做专家纵深。

小主题清单（## 章节；下游可读 round3b OS-08 核对）：
- 8.1 虚拟地址空间布局：文本/数据/BSS/堆/栈/mmap 区的排布。
- 8.2 malloc/free 语义与误用：分配释放契约、悬垂/双重释放/泄漏。
- 8.3 brk/sbrk vs mmap 分配路径：小块走堆、大块走匿名 mmap（阈值 M_MMAP_THRESHOLD）。
- 8.4 空闲空间管理：free list、内外碎片、首/最佳适配、伙伴/slab。

多来源比对（强制）：每项 ≥2 独立源，优先一手（OSTEP 13–14,17「Address Spaces/Memory API/Free-Space Management」/ 6.1810「VM for Applications」/ glibc malloc 文档 / Linux `mmap(2)`/`brk(2)`）；冲突两边都记。阈值 M_MMAP_THRESHOLD 默认值不凭记忆，比对或标「待核」+日期。本机实证可选：`cat /proc/self/maps` 看段布局；`valgrind`/`-fsanitize=address` 抓误用；`strace` 不同大小分配看 `brk` vs `mmap`；`/proc/slabinfo` 看内核 slab。实证不替代比对。章末集中列来源与时效。

权威锚点：OSTEP 13–14,17「Address Spaces/Memory API/Free-Space Management」；6.1810「VM for Applications」；glibc malloc / Linux `mmap(2)`。基线 2026-07-25。

落盘：findings/013-operating-systems.md 家族——单独成文用 findings/013-operating-systems-memory-api.md；拆分用 …-a/-b.md。抬头带基线串。自足、不残留工具标签/控制字符。
```

```text
[P0] L4-01·大主题09 地址翻译与分页机制 — 报告prompt
任务：为「L4-01·大主题09 地址翻译与分页机制」产出一份 report-format v3 教辅式报告。先读 brief.md、report-format.md（v3）、round3a OS-09 行与 §0、round3b OS-09 一节。

粒度判定（先做）：先判「1 份 or 拆 N 份 + 理由」。

v3 格式硬性：#→##→###；### 下不写标签，核心说明 + 充分辅助说明（初学者向、必需）。页表项（PTE）位域、多级页表结构、地址翻译公式/时序独占行/独立块。教辅口吻；广度优先、不做专家纵深。

小主题清单（## 章节；下游可读 round3b OS-09 核对）：
- 9.1 动态重定位与分段：base/bound、段式内存。
- 9.2 分页基础：页/页帧/页表项、有效位/权限位。
- 9.3 TLB：快表、命中/未命中、覆盖率、TLB 刷新。
- 9.4 多级/倒排页表：页表本身的空间开销、x86-64 四级。
- 9.5 大页（hugepages）：减少 TLB 项数与页表层级。

内核实践并入（OS-16）：并入 OS-16.3 xv6 `vm.c` 的 `walk`/`mappages`/`kvmmap` 页表建立与翻译（只读源码解释「多级页表怎么走」）。xv6 QEMU 未装（OS-16.6 待核）→ 只读源码腿。

多来源比对（强制）：每项 ≥2 独立源，优先一手（OSTEP 15–20 / 6.1810「Page Tables」/ Intel SDM 或 riscv 特权手册的分页章 / xv6 `vm.c`）；冲突两边都记。x86-64 四级页表位宽/大页尺寸不凭记忆，比对或标「待核」+日期。本机实证可选：xv6 `vm.c` walk 源码；`perf stat -e dTLB-load-misses`（计数器名待核）；`/proc/meminfo` HugePages_*、`/sys/kernel/mm/hugepages`。实证不替代比对。章末集中列来源与时效。

权威锚点：OSTEP 15–20「Address Translation…/Paging/TLB/Advanced Page Tables」；6.1810「Page Tables」；xv6-riscv `vm.c`（只读）。基线 2026-07-25。

落盘：findings/013-operating-systems.md 家族——单独成文用 findings/013-operating-systems-paging.md；拆分用 …-a/-b.md。抬头带基线串。自足、不残留工具标签/控制字符。
```

```text
[P0] L4-01·大主题10 缺页、页替换与交换 — 报告prompt
任务：为「L4-01·大主题10 缺页、页替换与交换」产出一份 report-format v3 教辅式报告。先读 brief.md、report-format.md（v3）、round3a OS-10 行与 §0、round3b OS-10 一节。

粒度判定（先做）：先判「1 份 or 拆 N 份 + 理由」。

v3 格式硬性：#→##→###；### 下不写标签，核心说明 + 充分辅助说明（初学者向、必需）。替换策略命中率公式、缺页处理时序独占行/独立块。教辅口吻；广度优先、不做专家纵深。

小主题清单（## 章节；下游可读 round3b OS-10 核对）：
- 10.1 按需分页与缺页处理：demand paging、minor/major fault 流程。
- 10.2 替换策略：OPT/FIFO/LRU/clock、Belady 异常、工作集近似。
- 10.3 抖动与工作集：thrashing、工作集模型、swappiness 调参。
- 10.4 COW 与按需置零：零页共享、写触发复制（呼应 OS-02.4）。
- 10.5 swap 机制与策略：换出/换入、swap 分区/文件。

内核实践并入（OS-16）：可并入 OS-16.5 本机 Linux 实证工具带；缺页处理路径可对照 6.1810「Page Faults」讲义与 xv6 只读源码（如涉及）。

多来源比对（强制）：每项 ≥2 独立源，优先一手（OSTEP 21–23「Swapping: Mechanisms/Policies/Complete VM」/ 6.1810「Page Faults」/ Linux VM 文档）；冲突两边都记（尤其教材 LRU/clock vs Linux 实际多级 LRU/MGLRU 现状——标演进）。swappiness 默认值不凭记忆，比对或标「待核」+日期。本机实证可选：`/proc/[pid]/stat` minflt/majflt、`/usr/bin/time -v`；替换命中率数值模拟；`cat /proc/sys/vm/swappiness`；`free -m`、`swapon --show`、`/proc/meminfo`。实证不替代比对。章末集中列来源与时效。

权威锚点：OSTEP 21–23「Swapping Mechanisms/Policies/Complete VM」；6.1810「Page Faults」；Linux VM/swap 文档。基线 2026-07-25。

落盘：findings/013-operating-systems.md 家族——单独成文用 findings/013-operating-systems-paging-swap.md；拆分用 …-a/-b.md。抬头带基线串。自足、不残留工具标签/控制字符。
```

```text
[P0] L4-01·大主题11 I/O 子系统与设备驱动 — 报告prompt
任务：为「L4-01·大主题11 I/O 子系统与设备驱动」产出一份 report-format v3 教辅式报告。先读 brief.md、report-format.md（v3）、round3a OS-11 行与 §0、round3b OS-11 一节。

粒度判定（先做）：先判「1 份 or 拆 N 份 + 理由」。

v3 格式硬性：#→##→###；### 下不写标签，核心说明 + 充分辅助说明（初学者向、必需）。设备寄存器交互协议、中断/轮询/DMA 时序独占行/独立块。教辅口吻；广度优先、不做专家纵深。

小主题清单（## 章节；下游可读 round3b OS-11 核对）：
- 11.1 设备交互模型：状态/命令/数据寄存器、MMIO vs 端口 I/O。
- 11.2 中断 vs 轮询：各自代价、混合（NAPI 式）权衡。
- 11.3 DMA：设备直接搬内存、绕开 CPU 拷贝。
- 11.4 驱动分层与统一接口：设备无关层 + 设备相关驱动、字符/块设备。
- 11.5 中断下半部：软中断/tasklet/workqueue 延迟处理（Linux 机制名标「待核」，以内核文档核实）。

内核实践并入（OS-16）：并入 6.1810「Device Drivers」讲义作机制参照；OS-16.5 本机 `/proc/interrupts`、`/proc/iomem`、`ls -l /dev`、`/sys/class` 作实证。

多来源比对（强制）：每项 ≥2 独立源，优先一手（OSTEP 36「I/O Devices」/ 6.1810「Device Drivers」/ CS162 L17「General I/O」/ Linux 内核 softirq/tasklet/workqueue 文档）；冲突两边都记。下半部机制名/现状不凭记忆，标「待核」+日期。本机实证可选：`/proc/iomem`、`/proc/ioports`、`cat /proc/interrupts`、`ls -l /dev`、`/sys/class`。实证不替代比对。章末集中列来源与时效。

权威锚点：OSTEP 36「I/O Devices」；6.1810「Device Drivers」；CS162 L17「General I/O」；Linux softirq/workqueue 文档。基线 2026-07-25。

落盘：findings/013-operating-systems.md 家族——单独成文用 findings/013-operating-systems-io-devices.md；拆分用 …-a/-b.md。抬头带基线串。自足、不残留工具标签/控制字符。
```

```text
[P0] L4-01·大主题12 持久存储介质与 RAID — 报告prompt
任务：为「L4-01·大主题12 持久存储介质与 RAID」产出一份 report-format v3 教辅式报告。先读 brief.md、report-format.md（v3）、round3a OS-12 行与 §0、round3b OS-12 一节。

粒度判定（先做）：先判「1 份 or 拆 N 份 + 理由」。

v3 格式硬性：#→##→###；### 下不写标签，核心说明 + 充分辅助说明（初学者向、必需）。磁盘寻道/旋转延迟公式、RAID 级别容量/容错公式独占行；RAID 布局图独占块。教辅口吻；广度优先、不做专家纵深。

小主题清单（## 章节；下游可读 round3b OS-12 核对）：
- 12.1 HDD 几何与磁盘调度：寻道/旋转延迟、SSTF/SCAN/C-SCAN。
- 12.2 SSD/闪存与 FTL：页/块擦写、写放大、磨损均衡、TRIM。
- 12.3 RAID 级别：0/1/4/5/6 的性能/容量/容错权衡。
- 12.4 RAID 一致性与小写问题：校验更新的 read-modify-write、写洞。

多来源比对（强制）：每项 ≥2 独立源，优先一手（OSTEP 37「HDD」、38「RAID」、44「Flash/SSD」/ 厂商 SSD 白皮书或 Linux md-RAID 文档）；冲突两边都记。RAID 容错公式/写放大数值不凭记忆，比对或标「待核」+日期。本机实证可选：`lsblk -d -o name,rota` 看 rotational（本机多为虚拟盘，实证价值有限，如实标）。实证不替代比对。章末集中列来源与时效。

权威锚点：OSTEP 37「Hard Disk Drives」、38「Redundant Arrays (RAID)」、44「Flash-based SSDs」。基线 2026-07-25。

落盘：findings/013-operating-systems.md 家族——单独成文用 findings/013-operating-systems-storage-raid.md；拆分用 …-a/-b.md。抬头带基线串。自足、不残留工具标签/控制字符。
```

```text
[P0] L4-01·大主题13 文件与目录接口 — 报告prompt
任务：为「L4-01·大主题13 文件与目录接口」产出一份 report-format v3 教辅式报告。先读 brief.md、report-format.md（v3）、round3a OS-13 行与 §0、round3b OS-13 一节。

粒度判定（先做）：先判「1 份 or 拆 N 份 + 理由」。

v3 格式硬性：#→##→###；### 下不写标签，核心说明 + 充分辅助说明（初学者向、必需）。系统调用（`open`/`read`/`write`/`lseek`/`fsync`）、fd 表→打开文件表→inode 三级间接结构图独占行/独立块。教辅口吻；广度优先、不做专家纵深。

小主题清单（## 章节；下游可读 round3b OS-13 核对）：
- 13.1 fd 表与 open/read/write/lseek：描述符→打开文件表→inode 的三级间接。
- 13.2 inode 与元数据：文件属性、大小、时间戳、块指针。
- 13.3 VFS 抽象层：统一 file/inode/dentry/superblock 接口。
- 13.4 硬链接 vs 符号链接：共享 inode(引用计数) vs 存路径。
- 13.5 页缓存与 fsync：缓冲写、脏页回写、mmap 文件映射。

多来源比对（强制）：每项 ≥2 独立源，优先一手（OSTEP 39「Files & Directories」/ CS162 L18 / Linux VFS 文档与 `open(2)`/`fsync(2)` man / 内核源码）；冲突两边都记。不编造字段/接口语义，标「待核」+日期。本机实证可选：`strace` + `ls -l /proc/[pid]/fd`；`stat <file>`；`mount`、`/proc/filesystems`；`ln`/`ln -s` + `ls -li` 看 inode 号与 link count；`/proc/meminfo` Cached、`sync`/`fsync`（vmtouch 未装，待核）。实证不替代比对。章末集中列来源与时效。

权威锚点：OSTEP 39「Files & Directories」；CS162 L18；Linux VFS 文档 / `open(2)`/`fsync(2)`。基线 2026-07-25。

落盘：findings/013-operating-systems.md 家族——单独成文用 findings/013-operating-systems-files-dirs.md；拆分用 …-a/-b.md。抬头带基线串。自足、不残留工具标签/控制字符。
```

```text
[P0] L4-01·大主题14 文件系统实现与崩溃一致性 — 报告prompt
任务：为「L4-01·大主题14 文件系统实现与崩溃一致性」产出一份 report-format v3 教辅式报告。先读 brief.md、report-format.md（v3）、round3a OS-14 行与 §0、round3b OS-14 一节。

粒度判定（先做）：先判「1 份 or 拆 N 份 + 理由」。本主题内容多（布局/FFS/一致性/journaling/LFS/校验），是否拆分给理由。

v3 格式硬性：#→##→###；### 下不写标签，核心说明 + 充分辅助说明（初学者向、必需）。FS 磁盘布局图、journaling WAL 三模式、LFS 段结构独占行/独立块。教辅口吻；广度优先、不做专家纵深。

小主题清单（## 章节；下游可读 round3b OS-14 核对）：
- 14.1 FS 磁盘布局：超级块/位图/inode 表/数据块的经典分区。
- 14.2 FFS 局部性：柱面组、把相关数据放近。
- 14.3 崩溃一致性与 fsck：崩溃后不一致、离线检查修复。
- 14.4 Journaling：WAL、data/ordered/writeback 模式。
- 14.5 LFS 日志结构：全部当作追加日志、段清理。
- 14.6 数据完整性与校验和：静默损坏、checksum、写时校验（btrfs/zfs 特性）。

内核实践并入（OS-16）：并入 6.1810「File Systems/Crash Recovery/FS Performance」讲义作机制参照；OS-16.5 本机 `dumpe2fs`（ext4）看布局/`has_journal`（journal 模式细节标「待核」，以内核文档核实）。

多来源比对（强制）：每项 ≥2 独立源，优先一手（OSTEP 40–43,45「FS Implementation/FFS/FSCK & Journaling/LFS/Data Integrity」/ 6.1810 FS 讲义 / ext4 或 btrfs 官方文档）；冲突两边都记。journaling 模式默认值不凭记忆，比对或标「待核」+日期。本机实证可选：`dumpe2fs`（ext4）看布局与 has_journal。实证不替代比对。章末集中列来源与时效。

权威锚点：OSTEP 40–43,45；6.1810「File Systems/Crash Recovery/FS Performance」；ext4/btrfs 官方文档。基线 2026-07-25。

落盘：findings/013-operating-systems.md 家族——单独成文用 findings/013-operating-systems-filesystems.md；拆分用 …-a/-b.md。抬头带基线串。自足、不残留工具标签/控制字符。
```

```text
[P0] L4-01·大主题15 虚拟化与容器隔离 — 报告prompt
任务：为「L4-01·大主题15 虚拟化与容器隔离」产出一份 report-format v3 教辅式报告。先读 brief.md、report-format.md（v3）、round3a OS-15 行与 §0（含 §3 跨课分账）、round3b OS-15 一节。

粒度判定（先做）：先判「1 份 or 拆 N 份 + 理由」。

v3 格式硬性：#→##→###；### 下不写标签，核心说明 + 充分辅助说明（初学者向、必需）。trap-and-emulate 时序、二级地址翻译（EPT/NPT）、namespaces/cgroups 接口独占行/独立块。教辅口吻；广度优先、不做专家纵深。

小主题清单（## 章节；下游可读 round3b OS-15 核对）：
- 15.1 硬件虚拟化：trap-and-emulate、Type-1/2 hypervisor。
- 15.2 内存虚拟化：影子页表 / EPT/NPT 二级翻译（机制名/细节标「待核」，以厂商手册核实）。
- 15.3 Linux namespaces：pid/mnt/net/uts/ipc/user/cgroup 隔离维度。
- 15.4 cgroups (v2)：CPU/内存/IO 限额与计量。
- 15.5 隔离的侧信道代价：Meltdown/Spectre、KPTI 缓解。

分账提醒（承 round3a §3）：本课=**内核隔离机制**（namespaces/cgroups/hypervisor）；**Docker/K8s 编排落地归 L5-08 容器与云原生**，本课不深挖，交界处一句指路。

多来源比对（强制）：每项 ≥2 独立源，优先一手（6.1810「Virtual Machines/Meltdown」/ OSTEP VM 概念 / Intel SDM VMX 或 AMD APM SVM / Linux namespaces(7)/cgroup-v2 文档 / Meltdown-Spectre 原始论文）；冲突两边都记。EPT/NPT、KPTI 现状不凭记忆，标「待核」+日期。本机实证可选：`lsns`、`unshare`、`ls -l /proc/[pid]/ns`；`ls /sys/fs/cgroup`、`cat .../memory.max`；`/sys/devices/system/cpu/vulnerabilities/*`。实证不替代比对。章末集中列来源与时效。

权威锚点：6.1810「Virtual Machines/Meltdown」；OSTEP VM 概念；Intel SDM VMX / AMD APM SVM；Linux `namespaces(7)`/cgroup-v2 文档；Meltdown/Spectre 原始论文。基线 2026-07-25。

落盘：findings/013-operating-systems.md 家族——单独成文用 findings/013-operating-systems-virtualization.md；拆分用 …-a/-b.md。抬头带基线串。自足、不残留工具标签/控制字符。
```

共 15 条 prompt
