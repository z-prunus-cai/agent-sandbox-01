# L4-01·大主题02 进程抽象与进程 API

> 基线 Py3.11.15/np2.4.6/gcc13.3 @2026-07-25 ｜ 核实日期：2026-07-26 ｜ 先修：本课大主题01（OS 作为资源虚拟化器、双模式与特权级、系统调用作为受保护接口）｜ 一手锚点：OSTEP 网页版 Ch4「The Abstraction: The Process」、Ch5「Interlude: Process API」（https://pages.cs.wisc.edu/~remzi/OSTEP/ ，核实 2026-07-25）；Berkeley CS162 现行 L4「Processes」（https://cs162.org/）；Linux man-pages 项目 `fork(2)`/`execve(2)`/`wait(2)`/`clone(2)`/`proc(5)`（man7.org，核实 2026-07-25）；xv6-riscv 源码只读副本（`.reference/xv6-riscv`，commit 59db7e2 @2026-07-25，`kernel/proc.c`/`proc.h`/`vm.c`）｜ 成熟度：GA/稳定（进程模型与 POSIX API 数十年稳定；Linux 内部字段名有演进，随内核版本标注）

> 粒度判定：**1 份，不拆**。本大主题 5 个小主题（2.1–2.5）沿同一条主线——"一个运行中的程序在内核里长什么样、怎么被造出来、怎么被回收"——层层推进：先立进程 = 独占地址空间 + 机器状态的抽象（2.1），再看内核用什么数据结构（PCB/`task_struct`/xv6 `struct proc`）与状态机记录它（2.2），然后用 fork/execve/wait 三个系统调用走完"造—换—收"的生命周期（2.3），再补上 fork 高效的关键机制写时复制（2.4），最后收尾于回收时序里的两个特殊态僵尸与孤儿（2.5）。五节机制彼此咬合、篇幅适中，按 report-format v3 §一默认 1 大主题 = 1 报告，不拆 `-a/-b`。

> 本报告一条主线心智模型：**进程 = 一个"正在被执行"的程序 = 一份独占的虚拟地址空间 + 一份机器状态（寄存器/PC/栈）+ 内核为它记账的一个控制块**。程序是躺在磁盘上的死字节，进程是把它装进这套抽象后活起来的运行实例。内核靠 PCB 记住每个进程的一切，靠 fork/exec/wait 三件套管理它从出生到回收的一生。

> 下游边界（本课不外扩）：进程之间"共享地址空间、只分开栈"的轻量执行流是**线程**，归大主题04；穿越用户/内核边界的 `syscall` 指令与 trap 机制归大主题03；进程谁先跑、跑多久由**调度器**决定，归大主题05。本报告只讲"进程这个抽象是什么、API 怎么用、内核怎么实现造/收"。

> 本报告 Linux 结论均在本机实跑取证：Linux 6.18.5 x86_64 @2026-07-26，gcc 13.3.0；C 程序、`strace -f`、`/proc/[pid]/{maps,smaps,status}` 真实输出贴入正文。测试代码仅存 scratchpad、不入库。xv6 QEMU/riscv 工具链本机未装（对应 OS-16.6，**待核**），故 xv6 一律退**只读源码腿**，不要求真跑。实证是多来源比对的补充，不替代比对。

---

## 2.1 进程模型与地址空间视图

### 2.1.1 进程是什么：运行中的程序

进程（process）是操作系统对**一个正在运行的程序**的抽象。程序本身只是磁盘上一堆静态的指令和初始数据（可执行文件），当内核把它加载起来、赋予它执行所需的一切资源并开始执行时，这个"活着的执行实例"就是进程。同一个程序可以同时有多个进程（例如开两个 `bash`），它们各自独立。

初学者最该分清的是"程序 vs 进程"：程序是名词、是蓝图，是躺着不动的字节；进程是动词、是施工现场，有正在执行到哪一行（程序计数器）、栈上压了哪些函数、堆里分配了多少内存这些**随时变化的运行时状态**。OSTEP Ch4 用一句话概括进程抽象要回答的核心问题：操作系统只有有限个物理 CPU，却要让每个进程都以为自己独占一个 CPU——这靠的是把 CPU 在众多进程间快速来回切换（时分复用，time sharing），配合"每个进程一套独占状态"的抽象，制造出"人人有一台机器"的假象。

### 2.1.2 每进程独占的虚拟地址空间视图

每个进程都拥有一份**独占的虚拟地址空间**（address space）：它看到的是从 0（概念上）到某个上限的一整片连续内存，里面按用途分成若干段——只读的**代码段（text）**、已初始化的**数据段（data）**、未初始化的**BSS**、向高地址生长的**堆（heap）**、向低地址生长的**栈（stack）**，以及中间由 `mmap` 映射的库和匿名区。这套视图是**虚拟的**：进程用的地址是虚拟地址，由硬件+页表翻译到物理内存，所以两个进程的"同一个虚拟地址"落在不同的物理页上，互不干扰（虚拟地址→物理地址的翻译机制归大主题09）。

为什么要给每个进程一份独占视图？为了**隔离**与**简化**。隔离：一个进程写坏自己的内存不会波及别人，也读不到别人的内存。简化：编译器和程序员可以假装自己独占整台机器、地址从固定位置开始，不用关心此刻物理内存里还挤着谁。

本机实证——直接查看一个进程的段布局（`cat /proc/self/maps`，此处 `self` 是 `cat` 自己）：

```
555ec872e000-555ec8730000 r--p  /usr/bin/cat        # 只读（rodata/头）
555ec8730000-555ec8735000 r-xp  /usr/bin/cat        # 代码段 text：可读可执行、不可写
555ec8738000-555ec8739000 rw-p  /usr/bin/cat        # 数据段：可读可写
555ec9483000-555ec94a4000 rw-p  [heap]              # 堆
7f9c3e200000-...          r--p  libc.so.6           # mmap 映射进来的共享库
7ffd...        rw-p  [stack]                         # 栈（本次输出略）
7f9c...        r-xp  [vdso]                           # 内核映射的加速页
```

代码段是 `r-xp`（可执行、不可写，防止程序改自己的指令），数据/堆/栈是 `rw-p`（可读写、不可执行，`p` 表示私有），只读常量是 `r--p`。这些权限由页表项里的位实现，越权访问（如往代码段写）会触发保护错并被内核用信号打断。

### 2.1.3 进程还包含机器状态

除了地址空间，进程抽象还包含一份**机器状态（machine state）**：程序运行到哪一条指令的**程序计数器（PC）**、当前**栈指针**、一组**通用寄存器**的值，以及一批"进程能访问什么"的信息——最典型的是**打开文件描述符表**（进程默认持有 0/1/2，即 stdin/stdout/stderr）。

抓住这一点，"上下文切换"（大主题03/05 深讲）就好懂了：内核要把 CPU 从进程 A 切给进程 B，本质就是把 A 的这份机器状态（寄存器等）保存起来、把 B 之前保存的机器状态恢复到 CPU 上——地址空间的切换则是换一套页表。进程 = 地址空间 + 机器状态 + 内核记账，这三块正是下一节 PCB 要存的东西。

#### 来源与时效
- OSTEP Ch4「The Abstraction: The Process」（网页版，核实 2026-07-25）：进程定义、机器状态（PC/寄存器/打开文件）、time sharing 制造"独占 CPU"假象。
- CS162 现行 L4「Processes」（cs162.org，核实 2026-07-25）：进程 = 地址空间 + 执行上下文 + OS 资源（fd 等）的组织方式。
- 本机实证 `cat /proc/self/maps`（Linux 6.18.5 @2026-07-26）：text `r-xp` / data `rw-p` / `[heap]` / `[stack]` / libc mmap 段权限位，交叉印证地址空间分段模型。
- 交叉一致，无冲突。

## 2.2 PCB / `task_struct` 与状态机

### 2.2.1 进程控制块（PCB）

内核为每个进程维护一个**进程控制块**（Process Control Block, PCB）：一个数据结构，集中记录内核管理这个进程所需的全部信息——进程标识（PID）、当前状态、机器状态（保存的寄存器上下文）、地址空间指针（页表）、打开文件表、父进程指针、调度相关数据等。PCB 是"进程在内核里的化身"：只要 PCB 还在，进程就还被内核记着。

初学者可以把 PCB 想成学校给每个学生建的一份档案：学生（进程）本人在教室里活动，但学校（内核）靠档案（PCB）知道他是谁、现在该上哪门课（状态）、上次考到哪（保存的寄存器）、家长是谁（父进程）。切换学生上台时，就是把上一个学生的近况写回档案、把下一个学生的档案翻出来照着办。OSTEP 把这个结构叫 process list / PCB，Linux 里它的具体实现叫 `struct task_struct`。

### 2.2.2 Linux `task_struct`（PCB 的实现）

在 Linux 内核里，PCB 就是 `struct task_struct`（定义在内核源码 `include/linux/sched.h`）。它是一个非常大的结构体，关键字段按用途分几类：进程状态字段、标识字段（`pid`、线程组 `tgid`）、亲缘关系指针（`real_parent`/`parent`、`children`/`sibling` 链表）、内存管理指针（`mm` 指向 `mm_struct` 即地址空间）、打开文件表（`files`）、以及架构相关的寄存器上下文（`thread`）等。

有两个"随版本演进"的点必须分账，不能凭记忆当常量：其一，Linux 里"进程"和"线程"都由 `task_struct` 表示，同一进程的多个线程共享一个 `tgid`（用户看到的 PID）、各有独立的内核内部 `pid`（用户看到的 TID）——这是大主题04 线程的伏笔。其二，表示运行状态的字段在较新内核里名为 `__state`（历史上曾名 `state`，约在 v5.14 前后改名），**确切字段名以基线 6.18.5 的内核源码为准，此处标「待核」**，不影响下面状态机的语义讨论。

### 2.2.3 进程状态机：R / S / D / Z / T

进程在其生命周期里在若干**状态**间转换。Linux 的 `proc(5)` / `ps(1)` 用单字母表示，最核心的五个是：

```
R  Running / Runnable   正在 CPU 上跑，或就绪等待被调度
S  Sleeping (可中断)     等待某事件（如 I/O、按键），可被信号唤醒
D  Uninterruptible sleep 不可中断睡眠，通常卡在磁盘/内核 I/O，信号也打不断
Z  Zombie               已退出但父进程尚未回收（见 2.5）
T  Stopped              被信号（SIGSTOP/SIGTSTP）暂停；t = 被调试器 trace 暂停
```

最经典的转换是三态循环：**就绪（Ready/Runnable）↔ 运行（Running）↔ 阻塞（Blocked/Sleeping）**。运行中的进程发起一次磁盘读，就从 Running 转入 Sleeping（把 CPU 让出）；数据到位后被唤醒回到 Runnable，等调度器再次选中它转回 Running；时间片用完则从 Running 直接被抢回 Runnable。OSTEP Ch4 用的正是 Running/Ready/Blocked 三态图，Linux 的 S/D 是把"阻塞"细分成能不能被信号打断两种。

R 与 S 的区别是初学者的高频困惑：`R` 不代表"此刻正占着 CPU"，而是"可运行"——包括就绪排队中；真正占 CPU 的只是其中被调度选上的那些。`D` 态的存在感来自它"kill 不掉"：进程卡在不可中断的内核路径（典型是等硬盘），连 `kill -9` 都要等它自己醒来才生效，这是系统偶尔"僵住"的常见原因。

本机实证——`R` 与 `S` 直接可见：

```
$ grep State /proc/self/status
State:  R (running)                # 查询自身时自然是 R

$ sleep 20 & ps -o pid,stat,cmd -p $!
  PID STAT CMD
12224 S    sleep 20                 # sleep 在等计时器，处于可中断睡眠 S
```

（`Z` 态在 2.5 节实证。`ps` 的 `STAT` 列在单字母后可能再跟 `+`（前台进程组）、`s`（会话首领）、`l`（多线程）等修饰符。）

### 2.2.4 xv6 `struct proc`：PCB 在内核里到底长什么样（源码腿）

xv6 用一个固定大小的进程表和一个精简的 PCB 把上述抽象落到最小可读的代码。进程表大小固定：

```
kernel/param.h:  #define NPROC 64    // 系统最多 64 个进程
kernel/proc.c:   struct proc proc[NPROC];   // 全部 PCB 就是这一个静态数组
```

状态枚举与 PCB 结构（`kernel/proc.h`，只读副本 @commit 59db7e2）：

```c
enum procstate { UNUSED, USED, SLEEPING, RUNNABLE, RUNNING, ZOMBIE };

struct proc {
  struct spinlock lock;
  enum procstate state;        // 进程状态（对应上面的 R/S/Z 思路）
  void *chan;                  // 若非零：正睡在哪个"等待通道"上
  int killed;                  // 是否被要求杀死
  int xstate;                  // 退出码，留给父进程 wait 取走
  int pid;                     // 进程号
  struct proc *parent;         // 父进程指针
  uint64 kstack;               // 内核栈虚拟地址
  uint64 sz;                   // 进程内存大小（字节）
  pagetable_t pagetable;       // 用户页表（= 这个进程的地址空间）
  struct trapframe *trapframe; // 保存的用户态寄存器（陷入时用）
  struct context context;      // swtch() 切换时保存/恢复的内核寄存器
  struct file *ofile[NOFILE];  // 打开文件表
  struct inode *cwd;           // 当前工作目录
  char name[16];               // 进程名（调试用）
};
```

把它和 2.1/2.2 的抽象对齐就一目了然：`pagetable` 是**地址空间**，`trapframe`/`context` 是**机器状态**，`ofile` 是**打开文件表**，`state`/`pid`/`parent`/`xstate` 是**内核记账**。xv6 的 `enum procstate` 里 `RUNNABLE`/`RUNNING`/`SLEEPING`/`ZOMBIE` 与 Linux 的 R/R/S/Z 一一对应（xv6 把"就绪"和"运行"分成 RUNNABLE 与 RUNNING 两个显式枚举值，比 Linux 单字母 R 更直白）。这份 50 行不到的结构体，就是"进程在内核里长什么样"的最小完整答案。

#### 来源与时效
- OSTEP Ch4「The Abstraction: The Process」（网页版，核实 2026-07-25）：process list/PCB 概念、Running/Ready/Blocked 三态图。
- Linux man-pages `proc(5)`（man7.org，核实 2026-07-25）：`/proc/[pid]/status` 的 `State` 字段与 R/S/D/Z/T/t 单字母含义；`task_struct` 为 PCB 实现。
- xv6-riscv `kernel/proc.h`（`enum procstate`、`struct proc`）、`kernel/param.h`（`NPROC 64`）、`kernel/proc.c`（`struct proc proc[NPROC]`），只读副本 commit 59db7e2 @2026-07-25。
- 本机实证 `/proc/self/status`（`State: R`）、`ps -o stat`（`sleep` → `S`）（Linux 6.18.5 @2026-07-26）。
- 待核：Linux 6.18.5 中 `task_struct` 状态字段确切名（`state` vs `__state`，约 v5.14 改名）——以基线内核源码为准，本报告不作为承重结论。
- 交叉一致（xv6 枚举 ↔ Linux 单字母 ↔ OSTEP 三态），无冲突。

## 2.3 fork/execve/wait 生命周期

### 2.3.1 `fork()`：复制出一个几乎一样的子进程

`fork()` 创建一个新进程，它是调用进程（父）的**近乎完全复制**：子进程拿到父进程地址空间的一份**独立拷贝**（代码/数据/堆/栈内容相同）、复制一份打开文件描述符表、复制寄存器状态。关键在返回值语义——**一次调用，两次返回**：

```
pid_t rc = fork();
// 父进程里：rc = 子进程的 PID（> 0）
// 子进程里：rc = 0
// 出错时（父进程里）：rc = -1，未创建子进程
```

"一次调用返回两次"意味着 `fork()` 之后父子两个进程**都**从 `fork()` 的下一行继续执行，靠返回值不同来分辨自己是谁。子进程看到 0（"我是新来的"），父进程看到子进程 PID（"这是我生的娃的编号"）。谁先跑不确定，取决于调度器。


```
$ ./flh
parent pid=10385
parent: fork()=10386 waited pid=10386 exited=1 code=0
```

父进程里 `fork()` 返回 10386（子进程 PID），子进程里 `fork()` 返回 0——与上面的语义完全吻合。

### 2.3.2 `execve()`：把自己整个替换成另一个程序

`execve(path, argv, envp)` 用磁盘上的另一个可执行程序**整体替换**当前进程的内存映像：丢弃原来的代码段、数据、堆、栈，装入新程序的段并跳到它的入口开始执行。关键性质是——**成功时它永不返回**（因为返回地址所在的旧代码已经被覆盖了）；只有失败时才返回 -1。而且它**不创建新进程**：PID 不变、打开的文件描述符默认保留，只是"换了个灵魂"。

初学者要抓的反直觉点是"exec 不返回"：`execve` 之后写的代码只有在 `execve` **失败**时才会执行，所以标准写法是 `execve(...); perror("exec"); exit(127);`——那行 `perror` 就是 exec 的错误处理。`fork` 是"多一个进程"，`exec` 是"同一个进程换程序"，二者正交。

### 2.3.3 `wait()` / `waitpid()`：回收子进程

`wait(&status)` 让父进程**阻塞等待任一子进程结束**，拿回它的退出状态并**回收**它（释放其残留的内核资源）。`waitpid()` 是更精细的版本，可指定等哪个子进程、可加 `WNOHANG` 非阻塞轮询。用 `WIFEXITED(status)` / `WEXITSTATUS(status)` 等宏从 `status` 里解出"是正常退出吗、退出码是几"。

为什么退出还需要父进程来"回收"？因为子进程 `exit` 后不能立刻彻底消失——它的退出码等信息要留着，等父进程来取。父进程调用 `wait` 就是来取这份"遗嘱"并给内核发信号"可以彻底清了"。父进程若不 `wait`，子进程就变成**僵尸**卡住不走（见 2.5）。上例中父进程 `wait` 拿回 `pid=10386, exited=1, code=0`，正是回收动作。

### 2.3.4 为什么 fork 和 exec 要分成两步

把"造进程"和"换程序"拆成 `fork` + `exec` 两个调用，是 Unix 的一个经典设计（OSTEP Ch5 专门讨论）。好处是：在 `fork` 之后、`exec` 之前这个"窗口期"里，子进程还运行着父进程的代码，可以**从容地改造自己的执行环境**——重定向标准输入输出、关掉不该继承的文件描述符、改变工作目录——然后再 `exec` 成目标程序。shell 的重定向 `ls > out.txt` 正是这么实现的：`fork` 出子进程，在子进程里把 fd 1 重定向到文件，再 `exec` 成 `ls`；`ls` 本身完全不知道自己的输出被改道了。

这就是"复制—（改造）—替换—回收"生命周期：`fork` 复制、（窗口期改造环境）、`exec` 替换、父进程 `wait` 回收。合起来是运行一个外部命令的标准套路。

### 2.3.5 底层系统调用：clone / execve / wait4（strace 实证）

上面三个是 C 库/POSIX 层的接口，落到 Linux 内核实际是不同的系统调用。用 `strace -f`（`-f` 跟踪子进程）观察本机 `./flh` 背后的真实 syscall：

```
execve("/.../flh", [...], 127 vars) = 0            # 启动 flh 自身
clone(child_stack=NULL,
      flags=CLONE_CHILD_CLEARTID|CLONE_CHILD_SETTID|SIGCHLD) = 10750   # fork() 实际走 clone
[pid 10749] wait4(-1, <unfinished ...>             # 父进程 wait() 实际是 wait4(-1,...)
[pid 10750] execve("/bin/echo", ["/bin/echo","hello-from-exec"], ...) = 0  # 子进程 exec
[pid 10750] exit_group(0)                          # 子进程退出
[pid 10749] <... wait4 resumed> [{WIFEXITED && WEXITSTATUS==0}], 0, NULL) = 10750  # 父收到
```

三条对应关系清清楚楚：glibc 的 `fork()` 落到 `clone()` 且带 `SIGCHLD` 标志（意思是"子进程终止时给父进程发 SIGCHLD"，也正是它区别于创建线程的 `clone` 之处——线程会带 `CLONE_VM` 等共享标志，见大主题04）；`execve` 就是 `execve`；`wait()` 落到 `wait4(-1, ...)`（`-1` = 等任一子进程）。`exec` 后子进程用 `exit_group` 退出，父进程的 `wait4` 随即 resumed 并取到退出状态。

### 2.3.6 xv6 的 fork / exit / wait（源码腿）

xv6 把同一套生命周期实现得极其精简，可逐段对照（`kernel/proc.c`，只读副本 @59db7e2；本版函数名为 `kfork`/`kexit`/`kwait`）：

```c
int kfork(void) {
  struct proc *np = allocproc();                 // 分配一个空 PCB
  uvmcopy(p->pagetable, np->pagetable, p->sz);   // 复制父的整个用户地址空间（见 2.4）
  *(np->trapframe) = *(p->trapframe);            // 复制寄存器现场
  np->trapframe->a0 = 0;                         // 让子进程里 fork 返回 0
  for (i=0;i<NOFILE;i++) if (p->ofile[i]) np->ofile[i]=filedup(p->ofile[i]); // 复制 fd 表
  ...
  np->state = RUNNABLE;                          // 子进程就绪，等调度
  return pid;                                    // 父进程里返回子 PID
}
```

一行 `np->trapframe->a0 = 0` 就是"子进程 `fork` 返回 0"的实现真相：`a0` 是 riscv 上放系统调用返回值的寄存器，父进程的返回值是 `pid`，内核硬把子进程的那份改成 0——"一次调用两次返回"不是魔法，是内核对两个 PCB 的寄存器分别赋了不同值。`kexit`/`kwait` 见 2.5 节。

#### 来源与时效
- OSTEP Ch5「Interlude: Process API」（网页版，核实 2026-07-25）：fork 一次调用两次返回、exec 替换映像不返回、wait 回收、fork+exec 分离用于 shell 重定向。
- Linux man-pages `fork(2)`/`execve(2)`/`wait(2)`/`clone(2)`（man7.org，核实 2026-07-25）：返回值语义；glibc `fork` 基于 `clone(...SIGCHLD)`；`wait` 基于 `wait4`。
- xv6-riscv `kernel/proc.c`（`kfork`：`uvmcopy`/`trapframe->a0=0`/`filedup`/`state=RUNNABLE`），只读副本 commit 59db7e2 @2026-07-25。
- 本机实证：`./flh`（父 rc=子PID、子 rc=0、wait 取回退出码）；`strace -f`（`clone(...SIGCHLD)` / `execve` / `wait4(-1,...)` / `exit_group`）（Linux 6.18.5 @2026-07-26）。
- 交叉一致（POSIX 语义 ↔ strace 实际 syscall ↔ xv6 源码），无冲突。

## 2.4 写时复制（COW）

### 2.4.1 什么是写时复制

写时复制（Copy-On-Write, COW）是 `fork` 高效的关键机制：`fork` 时内核**并不**真的把父进程的物理内存整份复制一遍，而是让父子两进程的页表**共享同一批物理页**，并把这些页统统标成**只读**。谁都不写，就一直共享（零拷贝成本）；一旦某一方**试图写**某个共享页，硬件触发一次缺页/保护错，内核在异常处理里才为写者**复制出那一页的私有副本**、恢复其可写权限，让写操作落到副本上。复制被推迟到"真正要写"的那一刻，且只复制被写到的那些页。

"先假装复制、真要动手改时才偷偷补上复制"。这样 `fork` 本身几乎瞬时（只是复制页表映射和翻转权限位），大量"fork 完立刻 exec"的场景更是几乎不产生任何页复制——因为 exec 马上就把整个地址空间换掉了，之前那份根本没必要复制。

### 2.4.2 为什么需要 COW

历史上 `fork` 语义要求子进程拿到父进程内存的独立副本，但绝大多数 `fork` 之后紧跟 `exec`，"辛辛苦苦复制的父进程内存"转眼被丢弃，纯属浪费。COW 让 `fork` 的开销从"正比于地址空间大小"降到"正比于页表大小"，是现代 Unix/Linux `fork` 快的根本原因。这也和大主题10 的"按需置零/零页共享"是同一类思想——能不复制就不复制、能推迟就推迟。

### 2.4.3 本机实证：smaps 看共享页在写后变私有

用一个分配 64 MiB、全部写脏、然后 `fork` 的程序：子进程先不写，睡一会儿再全量写。对照子进程 `/proc/[pid]/smaps` 里那块 64 MiB 匿名区在"写之前 / 写之后"的统计：

```
=== 子进程写之前（与父共享，COW 只读） ===
Rss:            65540 kB
Shared_Dirty:   65540 kB      # 整块 64MiB 都算"共享脏页"——父子共用同一物理页
Private_Dirty:      0 kB

=== 子进程 memset 全量写之后 ===
Rss:            65540 kB
Shared_Dirty:       0 kB      # 共享清零
Private_Dirty:  65540 kB      # 全部转为该进程私有——写触发了 COW 复制
```

`Shared_Dirty` 从 64 MiB 塌到 0、`Private_Dirty` 从 0 涨到 64 MiB，正是"写触发复制、共享页转私有页"的直接证据。写之前父子共享（成本几乎为零），写之后才各自持有一份——COW 名副其实。

### 2.4.4 分账：xv6 base fork 是急切复制，不做 COW（源码腿）

值得对照的是，xv6 的**基础版** `fork` 走的是**急切复制（eager copy）**，并没有 COW——这正是 6.1810 课程留给学生的一个实验（COW fork lab）。看 `uvmcopy`（`kernel/vm.c`，只读副本 @59db7e2）：

```c
uvmcopy(pagetable_t old, pagetable_t new, uint64 sz) {
  for (i = 0; i < sz; i += PGSIZE) {
    ...
    pa = PTE2PA(*pte);
    mem = kalloc();                    // 为每一页真的分配一张新物理页
    memmove(mem, (char*)pa, PGSIZE);   // 立刻把父页内容整页拷过去
    mappages(new, i, PGSIZE, (uint64)mem, flags);
  }
}
```

它对父进程每一页都 `kalloc` + `memmove` 立即复制，`fork` 一次就把整个地址空间搬一遍。所以"COW 是 fork 的实现选择、不是 fork 语义的一部分"这一点，从"Linux 用 COW（smaps 实证）vs xv6 base 用急切复制（源码可见）"的对比里看得最清楚：两者对用户暴露的 `fork` 语义完全相同（子进程拿到独立副本），只是内核实现的性能策略不同。

#### 来源与时效
- OSTEP Ch5「Interlude: Process API」与 Ch23（VM 完整机制里 COW/`vfork` 的动机，网页版，核实 2026-07-25）：fork 后多数紧跟 exec、COW 避免无谓复制。
- Linux man-pages `fork(2)`（man7.org，核实 2026-07-25）：明确说明 Linux fork 采用 copy-on-write 页。
- xv6-riscv `kernel/vm.c`（`uvmcopy` 急切 `kalloc`+`memmove`），只读副本 commit 59db7e2 @2026-07-25；对照 6.1810「COW fork」lab（xv6 base 无 COW）。
- 本机实证 `/proc/[pid]/smaps`：64 MiB 匿名区在子进程写前 `Shared_Dirty=65540kB`、写后 `Private_Dirty=65540kB`（Linux 6.18.5 @2026-07-26）。
- 分账（无冲突，属实现差异）：Linux 实现用 COW；xv6 base 实现用急切复制——`fork` 语义两者一致。

## 2.5 僵尸与孤儿、SIGCHLD

### 2.5.1 僵尸进程（zombie）

僵尸进程是**已经退出、但还没被父进程回收**的进程。子进程 `exit` 后，内核并不能立刻把它彻底删除——必须保留一小份信息（PID、退出码、资源使用统计）等父进程 `wait` 来取。这段"死了但还没下葬"的状态就是僵尸态（Linux `ps` 里显示 `Z`，命令名后标 `<defunct>`）。父进程一旦 `wait`，内核就把这份残留清掉，僵尸消失。

僵尸的危害不是占内存（它几乎不占，只剩个 PCB 表项），而是**占用 PID**：PID 是有限资源，大量不回收的僵尸会耗尽 PID 号导致无法再创建新进程。所以"每个 `fork` 出来的子进程，父进程都该负责 `wait`"是一条基本纪律。注意：对僵尸 `kill -9` 无效——它已经死了，杀无可杀；清除僵尸的唯一办法是让它父进程去 `wait`（或父进程退出，见下）。

本机实证——父进程故意不 `wait`、睡 3 秒，其间子进程立即 `_exit(0)`：

```
$ ps -o pid,ppid,stat,cmd -e | grep -iE 'defunct|zomb'
11478     1 S    /.../zomb                 # 父进程 zomb（本身被 init 收养，PPID=1）
11480 11478 Z    [zomb] <defunct>          # 子进程：STAT = Z，已成僵尸
```

PID 11480 状态 `Z`、命令 `[zomb] <defunct>`，父进程 11478 还活着却没 `wait`，于是它就僵在那儿——直接坐实了僵尸态。

### 2.5.2 孤儿进程与被 init/subreaper 收养

孤儿进程是**父进程先于自己退出**的进程。父进程一死，孩子就没人来 `wait` 回收了——为避免它们将来变成永远无人认领的僵尸，内核会把孤儿**重新挂到一个"收养者"名下**：传统上是 PID 1 的 init 进程（现代系统多为 systemd），或最近的一个被标记为 **subreaper** 的祖先进程（Linux 通过 `prctl(PR_SET_CHILD_SUBREAPER)` 设置）。收养者会周期性地 `wait` 掉这些孤儿，替它们收尸。

上一段实证里其实同时看到了孤儿现象：父进程 `zomb`（PID 11478）的 PPID 是 **1**——因为它是在一个用完即退的子 shell 里启动的，那个 shell 退出后 `zomb` 自己就成了孤儿，被 PID 1 收养。所以那张表同时展示了两件事：11480 是僵尸（父没 wait），11478 是孤儿（原父已退、被 init 收养）。init 的核心职责之一正是当"万物之父"，不停 `wait` 回收这些飘来的孤儿，保证系统里不会堆积僵尸。

xv6 里这套"收养"逻辑同样清晰可见（`kernel/proc.c` 的 `reparent`，只读副本 @59db7e2）：

```c
void reparent(struct proc *p) {          // p 正在退出，把它的孩子过继出去
  for (pp = proc; pp < &proc[NPROC]; pp++)
    if (pp->parent == p) {
      pp->parent = initproc;             // 孤儿一律改认 init 为父
      wakeup(initproc);                  // 唤醒 init 来 wait 它们
    }
}
```

### 2.5.3 SIGCHLD 与回收时序

`SIGCHLD` 是子进程状态改变（终止、被停止、被继续）时内核发给**父进程**的信号。它让父进程不必一直阻塞在 `wait` 上死等——可以先干别的，等 `SIGCHLD` 到来时再在信号处理函数里 `waitpid(..., WNOHANG)` 把已退出的孩子收掉。这就是长期运行的服务/shell 处理子进程回收的标准异步姿势。

把 2.3 的 strace 再看一眼就完整了：`fork` 落到 `clone` 时带的正是 `SIGCHLD` 标志——它约定"这个子进程终止时，请给父进程发 SIGCHLD"。所以"子进程退出 → 内核发 SIGCHLD 给父 → 父被唤醒去 `wait4` 回收"是一条闭环时序。一个常见易错点：若父进程把 `SIGCHLD` 显式设为 `SIG_IGN`（忽略），或设置 `SA_NOCLDWAIT` 标志，内核就不再保留僵尸、子进程退出即自动清理——这是"故意不 wait 又不想留僵尸"的正规做法，但也意味着此时 `wait` 取不到退出码。

内核实践对照——xv6 的 `kexit`（`kernel/proc.c` @59db7e2）把 2.5 三件事串在一起：

```c
void kexit(int status) {
  ...
  reparent(p);              // ① 把自己的孩子过继给 init（孤儿收养）
  wakeup(p->parent);        // ② 唤醒可能在 wait 中睡着的父进程（≈ 发 SIGCHLD 的作用）
  p->xstate = status;       // ③ 留下退出码给父进程取
  p->state = ZOMBIE;        // ④ 自己转入僵尸态，等父来 wait
  sched();                  // 交出 CPU，永不返回
}
```

配对的 `kwait` 会扫描进程表找 `state==ZOMBIE` 的子进程，取走 `xstate` 后调用 `freeproc(pp)` 彻底回收——这最后一步的 `freeproc`，就是"僵尸被父进程 `wait` 后消失"的实现。整条链——子 `kexit` 设 ZOMBIE + 唤醒父 → 父 `kwait` 见 ZOMBIE → `freeproc` 清除——正是 Linux 上 `Z` 态、`SIGCHLD`、`wait4` 回收在最小内核里的对应物。

#### 来源与时效
- OSTEP Ch5「Interlude: Process API」（网页版，核实 2026-07-25）：`wait` 回收、僵尸需被回收、`SIGCHLD` 与 shell 场景。
- Linux man-pages `wait(2)`（僵尸/`Z`/`<defunct>`、`SIGCHLD`、`SIG_IGN`/`SA_NOCLDWAIT` 自动回收语义）、`proc(5)`（`Z` 状态）、`credentials(7)`/`prctl(2)`（孤儿被 init 或 subreaper 收养），man7.org，核实 2026-07-25。
- xv6-riscv `kernel/proc.c`（`reparent` 过继 init、`kexit` 设 ZOMBIE+wakeup 父+存 xstate、`kwait` 扫 ZOMBIE 后 `freeproc`），只读副本 commit 59db7e2 @2026-07-25。
- 本机实证：父不 `wait` → 子 `STAT=Z` / `<defunct>`（僵尸）；父自身 `PPID=1`（孤儿被 init 收养）（Linux 6.18.5 @2026-07-26）。
- 交叉一致（POSIX/man 语义 ↔ 本机 Z/PPID=1 ↔ xv6 reparent/kexit/kwait），无冲突。
