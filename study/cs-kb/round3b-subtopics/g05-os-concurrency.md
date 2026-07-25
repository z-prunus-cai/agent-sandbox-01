# 第 5 组「OS 与并发」· Round 3b 小主题分解 · 核实 2026-07-25

> 调查员：第 5 组。基线 Py3.11.15/np2.4.6/gcc13.3 @2026-07-25。
> 输入：`round3a-topics/g05-os-concurrency.md`（大主题清单+权威锚点）＋ `brief.md`。
> 目标：把 g05 每个大主题再拆成 3–8 个**小主题**（次级条目：覆盖全、少重叠、按教学序），每个一句话范围 + 标本机实证机会。
> 编号：大主题沿用原编号（OS-0x / CP-0x），小主题为 `X.Y`。
> 纪律：锚定权威章节（OSTEP/6.1810/CS162/AMP/ISO C23/Intel SDM），不编造；不确定标「待核」；本文件是**清单**不是讲解；核实 2026-07-25。
> 锚点版本（承 3a）：OSTEP 网页版；MIT 6.1810 2024 Fall + xv6-riscv；Berkeley CS162 现行；AMP 2nd/Revised（18 章）；ISO C **C23=9899:2024** atomics §7.17/§7.31.8；Intel SDM Vol.3A「Memory Ordering」(x86-64 TSO)。

---

# 课 L4-01 · 操作系统（并入内核实践）

> 教学序沿 OSTEP 三部：虚拟化→并发→持久化；用户态↔内核态边界补自 CS162/6.1810。
> 分账（承 3a §3）：本课讲「原语是什么、内核怎么实现、代价几何」；并发正确性/内存模型归 L4-05。

## OS-01 · 操作系统概念、双模式与内核结构
*锚点：OSTEP intro；CS162 L1–3；6.1810「OS Design/Organization」*

| 编号 | 小主题 | 一句话范围 | 本机实证机会 |
|------|--------|-----------|--------------|
| OS-01.1 | OS 作为资源虚拟化器 | CPU/内存/设备的复用与抽象，"三块拼图"（虚拟化/并发/持久化）预告 | 概念，无直接实证 |
| OS-01.2 | 双模式与特权级 | 用户态/内核态、ring 0/3、模式位、受保护指令 | 用户态执行特权指令（如 `cli`）触发 SIGSEGV/#GP 演示保护边界 |
| OS-01.3 | 内核结构谱系 | 宏内核 vs 微内核 vs 混合；Linux 宏内核 + 可加载模块 | `lsmod` / `cat /proc/modules` 看模块化宏内核 |
| OS-01.4 | 系统调用作为受保护接口 | syscall 是穿越保护边界的唯一受控入口、ABI 稳定契约 | `strace` 任一命令看用户态→内核态调用序列 |

## OS-02 · 进程抽象与进程 API
*锚点：OSTEP 4–5；CS162 L4*

| 编号 | 小主题 | 一句话范围 | 本机实证机会 |
|------|--------|-----------|--------------|
| OS-02.1 | 进程模型与地址空间视图 | 每进程独占的代码/数据/堆/栈虚拟视图 | `cat /proc/self/maps` 看段布局 |
| OS-02.2 | PCB / `task_struct` 与状态机 | 进程控制块字段、状态 R/S/D/Z/T 转换 | `ps -o pid,stat,cmd` + `/proc/[pid]/status` |
| OS-02.3 | fork/execve/wait 生命周期 | 复制-替换-回收三步、父子返回值语义 | 小程序 + `strace -f` 看 clone/execve/wait4 |
| OS-02.4 | 写时复制 (COW) | fork 后共享物理页、写触发复制 | fork 后对比 `/proc/[pid]/smaps` Shared→Private |
| OS-02.5 | 僵尸与孤儿、SIGCHLD | 未回收退出态=僵尸、父先死=孤儿被 init/reaper 收养 | 故意不 wait 制造僵尸，`ps` 看 `Z` 态 |

## OS-03 · 受限直接执行：中断、陷入与系统调用
*锚点：OSTEP 6；6.1810「Syscall Entry/Exit」「Page Faults」「Calling Conventions」*

| 编号 | 小主题 | 一句话范围 | 本机实证机会 |
|------|--------|-----------|--------------|
| OS-03.1 | 受限直接执行协议 | 内核设陷阱表→启动进程→时钟中断夺回控制的三步舞 | 概念+xv6 源码（OS-16.2） |
| OS-03.2 | 中断/异常/陷入分类 | trap(自愿) / fault(可恢复) / abort、同步 vs 异步 | 触发除零/缺页看信号（SIGFPE/SIGSEGV） |
| OS-03.3 | x86-64 系统调用约定 | `syscall` 指令、rax=号、rdi..r9 传参、返回 rax | 内联汇编直发 `write` syscall，对照 `strace` |
| OS-03.4 | 上下文切换机制 | 保存/恢复寄存器、切页表、TLB 刷新代价 | `perf stat` context-switches；`/proc/[pid]/status` *_ctxt_switches |
| OS-03.5 | 时钟中断与抢占点 | 周期 tick 作为强占内核控制权的时机 | `perf stat -e ...`（具体计数器待核） |

## OS-04 · 线程抽象与线程 API
*锚点：OSTEP 26–27；CS162 L5*

| 编号 | 小主题 | 一句话范围 | 本机实证机会 |
|------|--------|-----------|--------------|
| OS-04.1 | 线程 vs 进程 | 共享地址空间/fd 表、独立栈与寄存器上下文 | 多线程程序看 `/proc/[pid]/task/` 多 TID |
| OS-04.2 | 内核线程 vs 用户线程、1:1 vs M:N | 调度实体归属、映射模型权衡 | 概念（NPTL 为 1:1） |
| OS-04.3 | `clone()` 标志谱与 pthread | CLONE_VM/FILES/SIGHAND... 决定"共享多少"、pthread_create 落地 | `strace -f` 看 pthread 背后的 clone flags |
| OS-04.4 | 并发 ≠ 并行 | 单核交错 vs 多核真同时执行 | `taskset -c 0` 绑单核 vs 多核对比计时 |
| OS-04.5 | 协程/用户态任务 | 协作式让出、无内核介入的轻量执行流（对比线程） | 概念（范式深挖触 CP-14） |

## OS-05 · CPU 调度
*锚点：OSTEP 7–10；CS162 L10–12*

| 编号 | 小主题 | 一句话范围 | 本机实证机会 |
|------|--------|-----------|--------------|
| OS-05.1 | 调度指标与工作负载假设 | 周转/响应/公平、批处理 vs 交互假设 | 概念 |
| OS-05.2 | 基本算法 | FCFS/SJF/SRTF/RR、时间片长短权衡 | 概念+可数值模拟 |
| OS-05.3 | MLFQ | 多级反馈队列：优先级、配额、周期提升防饿死 | 概念 |
| OS-05.4 | 比例份额 | lottery/stride/CFS 红黑树 vruntime | `nice`/`renice` 观察份额变化 |
| OS-05.5 | Linux 当前实现：EEVDF 与调度类 | ≥6.6 EEVDF 取代 CFS、RT/DL/普通类（教材 vs 实现分账） | `chrt`、`/proc/[pid]/sched`；EEVDF 细节以内核文档为准 **待核** |
| OS-05.6 | 多处理器调度 | CPU 亲和性、负载均衡、缓存亲和 | `taskset`；`/proc/[pid]/status` Cpus_allowed |

## OS-06 · 同步原语与锁实现
*锚点：OSTEP 28–31；CS162 L6–9；6.1810「Locking/Coordination/RCU」*

| 编号 | 小主题 | 一句话范围 | 本机实证机会 |
|------|--------|-----------|--------------|
| OS-06.1 | 锁的接口与目标 | 正确性/公平/性能三目标 | 概念（正确性证明归 CP-01） |
| OS-06.2 | 硬件原语造锁 | TAS/CAS/关中断构建自旋锁 | xv6 `spinlock.c`（OS-16.4）；objdump 看 `lock` 前缀 |
| OS-06.3 | 条件变量与信号量 | CV 睡眠等待条件、semaphore 计数原语的内核语义 | 概念（用法正确性归 CP-09） |
| OS-06.4 | futex | 用户态无争用快路径 + 内核睡眠慢路径 | `strace` 看 pthread_mutex 争用时的 `futex` 调用 |
| OS-06.5 | RCU | 读多写少：无锁读 + 宽限期延迟回收 | 6.1810 RCU 讲义；xv6 无 RCU（Linux 源码只读） |
| OS-06.6 | 锁的代价 | 让 CPU、优先级倒置、锁护航、持锁被抢占 | 概念（可扩展性代价归 CP-08） |

## OS-07 · 并发 bug 与死锁
*锚点：OSTEP 32；CS162 L13*

| 编号 | 小主题 | 一句话范围 | 本机实证机会 |
|------|--------|-----------|--------------|
| OS-07.1 | 非死锁并发 bug | 原子性违背、顺序违背 | `-fsanitize=thread` 复现（与 CP-07 共用腿） |
| OS-07.2 | 死锁四条件 (Coffman) | 互斥/持有并等待/不可抢占/循环等待 | 概念 |
| OS-07.3 | 死锁预防 | 逐条破坏四条件 | 概念 |
| OS-07.4 | 死锁避免：银行家算法 | 安全状态/安全序列判定 | 数值模拟可跑 |
| OS-07.5 | 死锁检测与恢复 | 资源分配图/等待图找环、恢复策略 | 造 AB–BA 死锁，`gdb` 看两线程互等栈；`cat /proc/[pid]/task/*/stack` |

## OS-08 · 地址空间与内存 API
*锚点：OSTEP 13–14,17；6.1810「VM for Applications」*

| 编号 | 小主题 | 一句话范围 | 本机实证机会 |
|------|--------|-----------|--------------|
| OS-08.1 | 虚拟地址空间布局 | 文本/数据/BSS/堆/栈/mmap 区的排布 | `cat /proc/self/maps` |
| OS-08.2 | malloc/free 语义与误用 | 分配释放契约、悬垂/双重释放/泄漏 | `valgrind` / `-fsanitize=address` |
| OS-08.3 | brk/sbrk vs mmap 分配路径 | 小块走堆、大块走匿名 mmap（阈值） | `strace` 大小分配看 `brk` vs `mmap`（M_MMAP_THRESHOLD） |
| OS-08.4 | 空闲空间管理 | free list、内外碎片、首/最佳适配、伙伴/slab | 概念（OSTEP 17）；内核 slab 见 `/proc/slabinfo` |

## OS-09 · 地址翻译与分页机制
*锚点：OSTEP 15–20；6.1810「Page Tables」*

| 编号 | 小主题 | 一句话范围 | 本机实证机会 |
|------|--------|-----------|--------------|
| OS-09.1 | 动态重定位与分段 | base/bound、段式内存 | 概念 |
| OS-09.2 | 分页基础 | 页/页帧/页表项、有效位/权限位 | xv6 `vm.c` walk（OS-16.3） |
| OS-09.3 | TLB | 快表、命中/未命中、覆盖率、TLB 刷新 | `perf stat -e dTLB-load-misses`（计数器名待核） |
| OS-09.4 | 多级/倒排页表 | 页表本身的空间开销、x86-64 四级 | 概念 |
| OS-09.5 | 大页 (hugepages) | 减少 TLB 项数与页表层级 | `/proc/meminfo` HugePages_*；`/sys/kernel/mm/hugepages` |

## OS-10 · 缺页、页替换与交换
*锚点：OSTEP 21–23；6.1810「Page Faults」*

| 编号 | 小主题 | 一句话范围 | 本机实证机会 |
|------|--------|-----------|--------------|
| OS-10.1 | 按需分页与缺页处理 | demand paging、minor/major fault 流程 | `/proc/[pid]/stat` minflt/majflt；`/usr/bin/time -v` |
| OS-10.2 | 替换策略 | OPT/FIFO/LRU/clock、Belady 异常、工作集近似 | 数值模拟命中率 |
| OS-10.3 | 抖动与工作集 | thrashing、工作集模型、swappiness 调参 | `cat /proc/sys/vm/swappiness` |
| OS-10.4 | COW 与按需置零 | 零页共享、写触发复制（呼应 OS-02.4） | `/proc/[pid]/smaps` |
| OS-10.5 | swap 机制与策略 | 换出/换入、swap 分区/文件 | `free -m`、`swapon --show`、`/proc/meminfo` |

## OS-11 · I/O 子系统与设备驱动
*锚点：OSTEP 36；6.1810「Device Drivers」；CS162 L17*

| 编号 | 小主题 | 一句话范围 | 本机实证机会 |
|------|--------|-----------|--------------|
| OS-11.1 | 设备交互模型 | 状态/命令/数据寄存器、MMIO vs 端口 I/O | `/proc/iomem`、`/proc/ioports` |
| OS-11.2 | 中断 vs 轮询 | 各自代价、混合（NAPI 式）权衡 | `cat /proc/interrupts` |
| OS-11.3 | DMA | 设备直接搬内存、绕开 CPU 拷贝 | 概念 |
| OS-11.4 | 驱动分层与统一接口 | 设备无关层 + 设备相关驱动、字符/块设备 | `ls -l /dev`、`/sys/class` |
| OS-11.5 | 中断下半部 | 软中断/tasklet/workqueue 延迟处理 | 概念 **待核**（Linux 机制名） |

## OS-12 · 持久存储介质与 RAID
*锚点：OSTEP 37,38,44*

| 编号 | 小主题 | 一句话范围 | 本机实证机会 |
|------|--------|-----------|--------------|
| OS-12.1 | HDD 几何与磁盘调度 | 寻道/旋转延迟、SSTF/SCAN/C-SCAN | 概念（本机多为虚拟盘） |
| OS-12.2 | SSD/闪存与 FTL | 页/块擦写、写放大、磨损均衡、TRIM | `lsblk -d -o name,rota` 看 rotational |
| OS-12.3 | RAID 级别 | 0/1/4/5/6 的性能/容量/容错权衡 | 概念 |
| OS-12.4 | RAID 一致性与小写问题 | 校验更新的 read-modify-write、写洞 | 概念 |

## OS-13 · 文件与目录接口
*锚点：OSTEP 39；CS162 L18*

| 编号 | 小主题 | 一句话范围 | 本机实证机会 |
|------|--------|-----------|--------------|
| OS-13.1 | fd 表与 open/read/write/lseek | 描述符→打开文件表→inode 的三级间接 | `strace` + `ls -l /proc/[pid]/fd` |
| OS-13.2 | inode 与元数据 | 文件属性、大小、时间戳、块指针 | `stat <file>` |
| OS-13.3 | VFS 抽象层 | 统一 file/inode/dentry/superblock 接口 | `mount`、`/proc/filesystems` |
| OS-13.4 | 硬链接 vs 符号链接 | 共享 inode(引用计数) vs 存路径 | `ln`/`ln -s` + `ls -li` 看 inode 号与 link count |
| OS-13.5 | 页缓存与 fsync | 缓冲写、脏页回写、mmap 文件映射 | `/proc/meminfo` Cached；`sync`/`fsync`（vmtouch 未装 **待核**） |

## OS-14 · 文件系统实现与崩溃一致性
*锚点：OSTEP 40–43,45；6.1810「File Systems/Crash Recovery/FS Performance」*

| 编号 | 小主题 | 一句话范围 | 本机实证机会 |
|------|--------|-----------|--------------|
| OS-14.1 | FS 磁盘布局 | 超级块/位图/inode 表/数据块的经典分区 | `dumpe2fs`（ext4）看布局 |
| OS-14.2 | FFS 局部性 | 柱面组、把相关数据放近 | 概念 |
| OS-14.3 | 崩溃一致性与 fsck | 崩溃后不一致、离线检查修复 | 概念 |
| OS-14.4 | Journaling | WAL、data/ordered/writeback 模式 | ext4 journal（`dumpe2fs` 看 has_journal）**待核** |
| OS-14.5 | LFS 日志结构 | 全部当作追加日志、段清理 | 概念 |
| OS-14.6 | 数据完整性与校验和 | 静默损坏、checksum、写时校验 | 概念（btrfs/zfs 特性） |

## OS-15 · 虚拟化与容器隔离
*锚点：6.1810「Virtual Machines/Meltdown」；OSTEP VM 概念*

| 编号 | 小主题 | 一句话范围 | 本机实证机会 |
|------|--------|-----------|--------------|
| OS-15.1 | 硬件虚拟化 | trap-and-emulate、Type-1/2 hypervisor | 概念 |
| OS-15.2 | 内存虚拟化 | 影子页表 / EPT/NPT 二级翻译 | 概念 **待核** |
| OS-15.3 | Linux namespaces | pid/mnt/net/uts/ipc/user/cgroup 隔离维度 | `lsns`、`unshare`、`ls -l /proc/[pid]/ns` |
| OS-15.4 | cgroups (v2) | CPU/内存/IO 限额与计量 | `ls /sys/fs/cgroup`、`cat .../memory.max` |
| OS-15.5 | 隔离的侧信道代价 | Meltdown/Spectre、KPTI 缓解 | `/sys/devices/system/cpu/vulnerabilities/*` |

## OS-16 · 内核实践实证腿（xv6 + 本机 Linux）
*锚点：6.1810 labs；xv6-riscv 源码；基线 Linux 6.18.5。R1 裁定：非独立知识点，是 OS-02~14 的源码腿②+实证腿③*

| 编号 | 小主题 | 一句话范围 | 本机实证机会 |
|------|--------|-----------|--------------|
| OS-16.1 | xv6 `proc.c` | 进程表、scheduler()、swtch 上下文切换（读源码解释机制） | 只读源码 `.reference/xv6-riscv` |
| OS-16.2 | xv6 `trap.c` | trampoline/usertrap/kerneltrap 陷入返回路径 | 只读源码 |
| OS-16.3 | xv6 `vm.c` | walk/mappages/kvmmap 页表建立与翻译 | 只读源码 |
| OS-16.4 | xv6 `spinlock.c`/`sleeplock.c` | push_off 关中断、acquire/release、睡眠锁 | 只读源码 |
| OS-16.5 | 本机 Linux 实证工具带 | strace / `/proc` / 直接 syscall / perf 作为跨主题实证核心 | 本机真跑（贯穿全课） |
| OS-16.6 | xv6 运行环境状态硬标 | QEMU/riscv 工具链本机未取，R4 决定装或退只读源码腿 | **待核**（环境未验证） |

---

# 课 L4-05 · 并发与并行程序设计

> 教学序沿 AMP：Part I 原理（互斥→并发对象→共享内存基础→原语能力/共识→内存模型）→ Part II 实践（自旋锁→阻塞同步→无锁结构→可扩展结构→任务并行→事务内存），叠加 C23/C++/Intel SDM 语言级模型。
> 分账（承 3a §3）：本课讲「最弱内存模型下这段并发代码为何对/错、能不能更弱」；原语内核实现与代价归 L4-01。

## CP-01 · 互斥问题与理论
*锚点：AMP ch2「Mutual Exclusion」*

| 编号 | 小主题 | 一句话范围 | 本机实证机会 |
|------|--------|-----------|--------------|
| CP-01.1 | 互斥的形式化 | 临界区、互斥性/无死锁/无饥饿的精确定义 | 概念 |
| CP-01.2 | 两线程算法 | LockOne/LockTwo/Peterson 及其正确性 | pthread 复现 Peterson（需内存屏障，触 CP-05） |
| CP-01.3 | N 线程算法 | filter 锁、bakery 算法（先来先服务） | 概念+可模拟 |
| CP-01.4 | 纯软件互斥的下界 | 需 Ω(N) 个内存位置、为何离不开硬件原语 | 概念 |
| CP-01.5 | 有界等待与公平 | bounded waiting、时间/因果对公平性的约束 | 概念（与 OS-06.1 分账：此处是问题本质与证明） |

## CP-02 · 并发对象与线性一致性
*锚点：AMP ch3「Concurrent Objects」（R2 P0）*

| 编号 | 小主题 | 一句话范围 | 本机实证机会 |
|------|--------|-----------|--------------|
| CP-02.1 | 并发对象需要规约 | 用顺序规约定义并发对象的正确性 | 概念 |
| CP-02.2 | 顺序一致性 (SC) | 各线程内序保持、全局存在一致交错 | 概念 |
| CP-02.3 | 线性一致性 | linearizability、线性化点、比 SC 更强 | 概念 |
| CP-02.4 | 可组合性 | linearizability 的局部性（对象各自线性即整体线性） | 概念 |
| CP-02.5 | 进展性条件 | wait-free/lock-free/obstruction-free vs starvation-/deadlock-free | 概念（本课地基，R2 P0） |

## CP-03 · 共享内存基础与寄存器构造
*锚点：AMP ch4「Foundations of Shared Memory」*

| 编号 | 小主题 | 一句话范围 | 本机实证机会 |
|------|--------|-----------|--------------|
| CP-03.1 | safe/regular/atomic 寄存器层级 | 三级读写语义强度递增 | 概念 |
| CP-03.2 | 从弱到强的构造 | 单读→多读、布尔→多值寄存器构造 | 概念 |
| CP-03.3 | atomic 多读多写构造 | 用时间戳把 regular 提升到 atomic | 概念 |
| CP-03.4 | 读写原子性的来源与代价 | 原子性并非免费、构造开销 | 概念 |

## CP-04 · 同步原语的相对能力与共识
*锚点：AMP ch5–6*

| 编号 | 小主题 | 一句话范围 | 本机实证机会 |
|------|--------|-----------|--------------|
| CP-04.1 | 共识问题 | 多线程就一个值达成一致、wait-free 要求 | 概念 |
| CP-04.2 | consensus number | read/write=1、FIFO 队列=2、... 的能力刻度 | 概念 |
| CP-04.3 | wait-free 同步层级 | 原语按共识数分层、不可跨层实现 | 概念 |
| CP-04.4 | CAS 的通用性 | compareAndSet 共识数=∞ | C11 CAS 实测（承 CP-06.4） |
| CP-04.5 | universal construction | 用共识对象实现任意 wait-free 对象 | 概念 |

## CP-05 · 内存一致性模型与重排序
*锚点：Intel SDM Vol.3A「Memory Ordering」/AMD64 APM Vol.2；C++ `[intro.races]`；AMP 附录（本课主场）*

| 编号 | 小主题 | 一句话范围 | 本机实证机会 |
|------|--------|-----------|--------------|
| CP-05.1 | happens-before 与 DRF | 因果序定义、无数据竞争则似 SC | 概念 |
| CP-05.2 | 顺序一致性作参照 | SC 作为程序员心智基线 | 概念 |
| CP-05.3 | x86-64 TSO | 存储缓冲导致的 store→load 重排（唯一放松） | 手写 pthread SB litmus 复现 r1=r2=0 |
| CP-05.4 | 弱序模型对照 | ARM/POWER 允许更多重排（本机 x86 不可直测） | **待核/跨平台**（基线为 x86，无 ARM 硬件） |
| CP-05.5 | 编译器重排 vs 硬件重排 | 两级重排、屏障来源、`volatile` 不等于原子 | `objdump -d` 看 seq_cst 生成的 `mfence`/`lock` |
| CP-05.6 | litmus 测试 | SB/MP/IRIW 经典样式 | herdtools 未装 **待核**；SB/MP 可手写 pthread 复现 |

## CP-06 · 原子操作与 memory_order
*锚点：ISO C 9899:2024 §7.17/§7.31.8；cppreference（指路）*

| 编号 | 小主题 | 一句话范围 | 本机实证机会 |
|------|--------|-----------|--------------|
| CP-06.1 | `_Atomic` 与泛型接口 | `<stdatomic.h>` 类型、atomic_load/store/exchange | `-std=c11` 编译 atomics 最小例 |
| CP-06.2 | 六档 memory_order | relaxed/consume/acquire/release/acq_rel/seq_cst 强弱谱 | 概念+编译对比生成码 |
| CP-06.3 | acquire/release 配对 | release-store 与 acquire-load 建立跨线程 happens-before（MP 惯用法） | pthread + acquire/release 复现 message passing |
| CP-06.4 | RMW 操作 | CAS(strong/weak)、fetch_add/or 的原子读改写 | `atomic_fetch_add` 计数 vs 非原子丢更新对比 |
| CP-06.5 | fence | `atomic_thread_fence` 独立屏障语义 | objdump 看 fence 生成 |
| CP-06.6 | lock-free 判定 | `atomic_is_lock_free`、`ATOMIC_*_LOCK_FREE` 宏 | 程序打印各类型 lock-free 宏值 |

## CP-07 · 数据竞争定义与 UB
*锚点：ISO C/C++ 数据竞争条款（本机最扎实实证腿）*

| 编号 | 小主题 | 一句话范围 | 本机实证机会 |
|------|--------|-----------|--------------|
| CP-07.1 | data race 精确定义 | 两访问/≥1 写/无 happens-before/非原子，四要件 | 概念 |
| CP-07.2 | 竞争即 UB 的后果 | 撕裂读写、编译器基于无竞争的激进假设 | 概念+反汇编演示 |
| CP-07.3 | TSan 检测 | ThreadSanitizer 影子内存 + happens-before 追踪 | `-fsanitize=thread` 复现并读 `WARNING: data race` 报告 |
| CP-07.4 | "良性竞争"迷思 | 无真正良性的非原子竞争、用 relaxed atomic 修正 | 把竞争改 `atomic_*` relaxed 后 TSan 转干净 |
| CP-07.5 | 与抢占交错的根因 | 调度抢占制造交错=竞争暴露的触发面（借 OS-05） | 概念 |

## CP-08 · 自旋锁与竞争
*锚点：AMP ch7「Spin Locks and Contention」*

| 编号 | 小主题 | 一句话范围 | 本机实证机会 |
|------|--------|-----------|--------------|
| CP-08.1 | TAS vs TTAS | test-and-set 的缓存行失效风暴、先读再试的改进 | 概念（perf 缓存计数 **待核**） |
| CP-08.2 | 指数退避 | backoff 缓解争用 | 可计时对比 |
| CP-08.3 | 队列锁 MCS/CLH | 各自本地变量自旋、公平且可扩展 | 概念 |
| CP-08.4 | 缓存一致性与 false sharing | MESI 对自旋成本的支配、伪共享 | 同缓存行 vs 对齐(`_Alignas(64)`)计数计时对比 |
| CP-08.5 | NUMA 与锁放置 | 跨节点访问的非一致代价 | **待核**（本机拓扑 `lscpu` 看 NUMA 节点数） |

## CP-09 · 阻塞同步与 monitor
*锚点：AMP ch8「Monitors and Blocking Synchronization」*

| 编号 | 小主题 | 一句话范围 | 本机实证机会 |
|------|--------|-----------|--------------|
| CP-09.1 | monitor 模式 | 锁 + 条件变量封装成对象内同步 | 概念 |
| CP-09.2 | 条件变量语义 | wait/signal/broadcast、Mesa vs Hoare 语义 | 概念 |
| CP-09.3 | 虚假唤醒纪律 | 必须 `while` 重检谓词而非 `if` | pthread_cond_wait 用 while 复现正确惯用法 |
| CP-09.4 | 生产者-消费者 | 有界缓冲、满/空条件变量 | pthread 实现 + 计数校验 |
| CP-09.5 | 读者-写者 | 共享读/独占写、公平与写者饥饿 | pthread 复现 |
| CP-09.6 | 信号量 | 计数同步原语、二元 vs 计数 | `sem_t` 实证 |

## CP-10 · 无锁并发数据结构
*锚点：AMP ch9–11「Linked Lists/Queues&ABA/Stacks」*

| 编号 | 小主题 | 一句话范围 | 本机实证机会 |
|------|--------|-----------|--------------|
| CP-10.1 | 链表并发级别谱 | coarse→fine→optimistic→lazy→lock-free 递进 | 概念 |
| CP-10.2 | lock-free 链表 | 逻辑删除标记 + CAS 物理摘除 | C11 CAS 实现小例 |
| CP-10.3 | 并发队列与 ABA | Michael-Scott 队列、ABA 问题成因 | CAS 复现 ABA（计数器/带标签指针对比） |
| CP-10.4 | 无锁栈与消除 | Treiber 栈、elimination backoff | 概念 |
| CP-10.5 | 安全内存回收 | hazard pointers / epoch / RCU 解决"何时能 free" | 概念（RCU 呼应 OS-06.5） |
| CP-10.6 | 死锁/活锁的算法层回避 | 锁序、无锁如何绕开四条件、活锁 | 概念（与 OS-07 分账：算法层归此） |

## CP-11 · 可扩展并发结构与分布式协调
*锚点：AMP ch12–15*

| 编号 | 小主题 | 一句话范围 | 本机实证机会 |
|------|--------|-----------|--------------|
| CP-11.1 | 并发 hashing | 开/闭地址、可扩展哈希、拆分有序表 | 概念 |
| CP-11.2 | skiplist 与并发平衡搜索 | 概率平衡、无锁跳表 | 概念 |
| CP-11.3 | 并发优先队列 | 基于 skiplist/堆的并发实现 | 概念 |
| CP-11.4 | counting networks | balancer 网络分散计数争用 | 概念 |
| CP-11.5 | 并发排序与组合 | sorting/组合网络 | 概念 |

## CP-12 · 任务并行与调度
*锚点：AMP ch16–17「Futures/Barriers」*

| 编号 | 小主题 | 一句话范围 | 本机实证机会 |
|------|--------|-----------|--------------|
| CP-12.1 | futures | 异步结果占位、依赖表达 | 概念 |
| CP-12.2 | fork-join | 分治任务的并行展开与汇合 | pthread/OpenMP 小例（OpenMP 可用性 **待核**） |
| CP-12.3 | work-stealing | 每核双端队列、空闲核偷任务 | 概念 |
| CP-12.4 | barriers | sense-reversing barrier 等同步点 | pthread_barrier 实证 |
| CP-12.5 | 任务图与负载均衡 | DAG 调度、均衡策略 | 概念 |

## CP-13 · 事务内存
*锚点：AMP ch18「Transactional Memory」*

| 编号 | 小主题 | 一句话范围 | 本机实证机会 |
|------|--------|-----------|--------------|
| CP-13.1 | TM 编程模型 | 原子块、乐观并发、自动回滚重试 | 概念 |
| CP-13.2 | STM 软件事务 | 版本/所有权记录、冲突检测与提交 | 概念 |
| CP-13.3 | HTM 硬件事务 | Intel TSX 等、成熟度与平台可用性硬标 | **待核**（TSX 本机是否可用，R4 核实） |
| CP-13.4 | TM vs 锁 | 可组合性优势、性能/回滚代价对比 | 概念 |

## CP-14 · 消息传递与并行范式
*锚点：AMP 外补（Go/Erlang/MPI 一手）；R2 表 C*

| 编号 | 小主题 | 一句话范围 | 本机实证机会 |
|------|--------|-----------|--------------|
| CP-14.1 | 共享内存 vs 消息传递 | 两大范式对立、"share by communicating" | 概念 |
| CP-14.2 | CSP/channel | Go goroutine + channel 模型 | 概念（Go 未列入基线工具链 **待核**） |
| CP-14.3 | actor 模型 | Erlang 进程/邮箱、无共享状态 | 概念 |
| CP-14.4 | MPI | 进程级显式消息、集合通信 | 概念（MPI 未装 **待核**；node 22 可作范式备用） |
| CP-14.5 | 数据并行 vs 任务并行 | 划分维度差异与选型 | 概念 |

## CP-15 · 并行性能模型
*锚点：AMP ch1（speedup 讨论）；R2 §表（跨课 L6-06 上界）*

| 编号 | 小主题 | 一句话范围 | 本机实证机会 |
|------|--------|-----------|--------------|
| CP-15.1 | 加速比与效率 | speedup=T1/Tp、efficiency=speedup/p | numpy 数值+作图 |
| CP-15.2 | Amdahl 定律 | 串行比例决定加速上界 | sympy 推导 + numpy 曲线验证 |
| CP-15.3 | Gustafson 定律 | 规模随核数增长的弱扩展视角 | numpy 曲线 |
| CP-15.4 | 可扩展性上界与开销来源 | 作心智模型：同步/通信/负载不均侵蚀加速 | 概念（实测调优归 L6-06） |

---

## 末尾统计

| 课 | 大主题数 | 小主题数 |
|----|---------|---------|
| L4-01 操作系统（并入内核实践） | 16 (OS-01~16) | 81 |
| L4-05 并发与并行程序设计 | 15 (CP-01~15) | 76 |
| **合计** | **31** | **157** |

- 每大主题小主题数落在 4–6，符合 3–8 约束。
- 本机可硬实证的强腿小主题（strace/proc/pthread/C11 atomics/数据竞争复现）集中在：OS-02/03/06/08/09/10/13/15、CP-05.3、CP-06.*、CP-07.3、CP-08.4、CP-09.*。
- 标「待核」小主题（环境/平台未验证或机制名待定）：OS-03.5、OS-05.5、OS-09.3、OS-11.5、OS-13.5、OS-14.4、OS-15.2、OS-16.6、CP-05.4、CP-05.6、CP-08.1/.5、CP-12.2、CP-13.3、CP-14.2/.4。
- 分账继承 3a §3：OS-06↔CP-06/08/09（机制/代价 vs 正确性/模型）、OS-07↔CP-10（资源层 vs 算法层）、OS-05↔CP-07（抢占交错）、内存屏障 CP-05 主场、OS-15↔L5-08、CP-15↔L6-06，均在对应小主题行内已点明。
