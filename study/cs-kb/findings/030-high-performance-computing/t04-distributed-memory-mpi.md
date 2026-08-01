# L6-06·大主题4 分布式内存并行 / MPI

> 基线 Py3.11.15/np2.4.6/gcc13.3 @2026-07-25 ｜核实日期：2026-08-01 ｜先修：大主题1 并行动机与架构分类（分布式内存 vs 共享内存、集群谱系）、大主题2 性能模型（强/弱扩展）、L5-01 分布式系统（进程、消息传递的基本直觉）｜一手锚点：MPI Standard 5.0（MPI Forum，2025-06-05 通过，官方 PDF 为唯一权威版）、Pacheco《An Introduction to Parallel Programming》2nd ed（2022）MPI 章、Berkeley CS267（Spring 2024，Demmel）分布式内存编程章、Open MPI / MPICH 官方文档｜成熟度：核心 API（SPMD/点对点/collective/通信子）GA 且长期稳定；标准版本推进 ⚙演进快·锚 MPI 5.0

当很多台机器各自只能看到自己的内存、彼此之间只有网络相连时，怎么让它们协同算一个大问题？答案就是**消息传递**：进程之间不共享任何变量，要交换数据只能显式地"寄信"。MPI（Message Passing Interface）是这套做法的事实标准——它不是一门语言、也不是一个库的名字，而是一份**接口规范**，规定了一组函数的语义，由 Open MPI、MPICH 等具体实现去落地。这一章讲清 MPI 编程者必须建立的心智模型：所有进程跑同一份程序（SPMD）、靠 rank 区分角色、用点对点和集合通信搬数据、用通信子把进程分组。

这里只讲编程模型层面的"是什么、怎么用、坑在哪"，不深入网络协议栈、具体实现的进程管理器（如 `mpirun`/`srun`）内部机制，也不碰共识/容错——那属于分布式系统课；本章的分布式是**为算得快而做的消息传递**，不是为可靠而做的复制。

本机实证说明：本机基线默认无 MPI，本次为验证 API 语义**现装（非持久，跑完即弃）**了 Open MPI 4.1.6 + mpi4py 4.1.2。一个关键事实——**装上的 Open MPI 4.1.6 运行时 `MPI_Get_version()` 返回 (3, 1)，即它实现到 MPI-3.1 标准**；而本章的标准锚点是更新的 MPI 5.0。所以"标准写什么"与"手边实现支持到哪一版"是两笔账，后文凡涉及新版本特性都会分开标。下文标「本机实证」处均为这套现装环境上 `mpirun -np N python3 …` 的真实输出。

---

## 4.1 SPMD 模型与 rank / size

### 4.1.1 SPMD：同一份程序，多个进程各跑一份

SPMD（Single Program, Multiple Data，单程序多数据）是 MPI 最基本的执行模型：启动时你用启动器（如 `mpirun -np 4 ./a.out`）把**同一个可执行文件**复制成 N 个进程同时运行，每个进程有各自独立的内存空间和一份完整的代码。它们跑的是同一段程序，但因为各自拿到不同的编号，会在 `if (rank == 0)` 这类分支上走不同的路，从而处理不同的数据、扮演不同的角色。

初学者最容易误解的一点：SPMD 不是"主程序 fork 出一堆子任务"，而是"从第一行起就有 N 个平等的进程在跑同一个 `main`"。你写的 `printf("hello")` 如果不加 rank 判断，会被打印 N 次。它和大主题3 的 OpenMP fork-join 是相反的直觉——OpenMP 是"一个进程内，遇到并行区才临时分出线程，用完合并"；MPI 是"一开始就是 N 个进程，自始至终并行，靠显式消息才有交互"。SPMD 是"单程序"，与之相对的 MPMD（多程序多数据，不同进程跑不同可执行文件）MPI 也支持，但 HPC 里绝大多数程序是 SPMD。

本机实证（4 进程跑同一脚本，各自打印自己的 rank/size）：

```
$ mpirun -np 4 python3 demo.py
MPI version (3, 1) size 4
```

### 4.1.2 rank 与 size：进程的"工号"和"总人数"

在一个进程组里，`size` 是进程总数，`rank` 是每个进程的唯一编号，取值从 0 到 size−1。它们通过两个调用获得：

```
MPI_Comm_size(MPI_COMM_WORLD, &size);   /* 总进程数 */
MPI_Comm_rank(MPI_COMM_WORLD, &rank);   /* 本进程编号 0..size-1 */
```

`MPI_COMM_WORLD` 是 MPI 初始化后自动存在的"默认通信子"，囊括本次运行启动的全部进程（通信子的细节见 4.4）。rank 是编程者切分工作的唯一抓手——你几乎总是用它来决定"这个进程负责数组的哪一段""谁当 root 收集结果"。

一个最常见的用法是按 rank 均分数据：若有 n 个元素、size 个进程，进程 rank 负责下标区间

```
[ rank * n / size , (rank+1) * n / size )
```

有个容易踩的点：rank 是**相对于某个通信子**的，同一个进程在不同通信子里 rank 可能不同（4.4 会用到这点），因此不带通信子谈"rank 几"是不完整的。另外 rank 0 常被当作"主进程"来做 I/O 或收尾，但这只是编程约定，MPI 本身不赋予 rank 0 任何特权。

### 4.1.3 初始化与收尾：MPI_Init / MPI_Finalize 的生命周期

MPI 程序必须以初始化开始、以收尾结束，中间才能调其它 MPI 函数：

```
MPI_Init(&argc, &argv);      /* 建立通信环境，之后才能用 MPI 调用 */
   ... MPI_Comm_rank / send / recv / collective ...
MPI_Finalize();              /* 释放资源；之后不得再调 MPI 函数 */
```

规范要求：`MPI_Init`（或线程版 `MPI_Init_thread`，见 4.5）之前和 `MPI_Finalize` 之后，除极少数查询函数外不能调用任何 MPI 例程；每个进程各调一次。

初学者用 mpi4py（Python 绑定）时会发现看不到 `Init`/`Finalize`——那是因为 mpi4py 在 `import mpi4py.MPI` 时自动初始化、解释器退出时自动收尾，把这对调用藏起来了，但底层语义不变。C/Fortran 里必须手写。易错点：忘了 `MPI_Finalize` 或在它之后又调 MPI 函数，属未定义行为；还有把耗时初始化写在 rank 判断里导致某些进程没进 `MPI_Init` 而挂起——初始化必须是所有进程无条件执行的。

### 4.1.4 分布式内存的心智模型：没有共享变量，只有消息

MPI 假设的是**分布式内存**机器模型：每个进程有自己私有的地址空间，一个进程改了自己的变量 `x`，别的进程完全看不到；进程间要交换任何数据，都必须通过显式的发送/接收（或集合通信）来搬运字节。这与共享内存（OpenMP，多个线程直接读写同一块内存）是根本不同的两种世界观。

这点是 MPI 一切设计的出发点，务必先钉牢。直觉上，把每个进程想成一间没有窗户的办公室，里面的人（进程）只能通过邮件（消息）和别人交流，不能隔墙看别人桌上的文件。好处是：因为没有共享状态，就没有数据竞争、不需要锁——正确性问题从"谁改了共享变量"变成"消息有没有按预期收发到"。代价是：搬数据要显式写代码、且网络通信比访存慢几个数量级，所以**减少和隐藏通信**成了分布式并行性能的头等大事（这正是大主题8 的主题）。

一个常见混淆：MPI 程序也可以跑在一台多核机器上（像本机就是把 4 个进程放在同一台 4 核机上）。这时进程间可能走共享内存做快速拷贝（实现优化），但**编程模型上你仍然当它们是分布式的**——你不能直接读别的进程的变量，只能发消息。模型（分布式、消息传递）和物理承载（可能同机）要分开看。

#### 来源与时效
- MPI Standard 5.0（MPI Forum，2025-06）：`MPI_Init`/`MPI_Finalize`/`MPI_Comm_rank`/`MPI_Comm_size` 语义、SPMD 与进程模型。官方规范页 https://www.mpi-forum.org/docs/ （核实 2026-08-01）
- Pacheco《An Introduction to Parallel Programming》2nd ed（2022）MPI 章：SPMD、rank/size、Hello World 骨架的教学式讲法。
- Berkeley CS267（Spring 2024）分布式内存编程 / MPI 讲义：分布式内存模型与消息传递动机。https://sites.google.com/lbl.gov/cs267-spr2024 ；存档 https://inst.eecs.berkeley.edu/~cs267/archives.html （核实 2026-08-01）
- 本机实证（现装·非持久）：Open MPI 4.1.6 + mpi4py 4.1.2，`mpirun -np 4` 打印 size=4、`MPI_Get_version()` 返回 (3,1)；rank 均分下标验证通过。
- 冲突/分歧：无实质分歧。仅版本层面——标准锚点为 MPI 5.0，而现装实现只到 MPI-3.1，属"标准 vs 实现"分账，非语义冲突。

## 4.2 点对点 send / recv 与死锁避免

### 4.2.1 阻塞式 send / recv 的语义

点对点通信是两个进程之间的一次消息传递：一方 `MPI_Send`、另一方 `MPI_Recv`。C 接口的核心参数是"数据在哪、多少个、什么类型、发给谁/从谁收、用什么标签（tag）、在哪个通信子"：

```
MPI_Send(buf, count, datatype, dest,   tag, comm);
MPI_Recv(buf, count, datatype, source, tag, comm, &status);
```

一次消息的匹配由三元组决定：**(通信子, 源/目的 rank, tag)** 都对上，接收方才收下这条消息。tag 是用户自定义的整数标记，用来区分同一对进程间的不同消息。

"阻塞（blocking）"这个词最容易误解，要精确理解：`MPI_Send` 阻塞的含义是"**返回时发送缓冲区 `buf` 已可安全重用**"，而**不**保证消息已经被对方收到；`MPI_Recv` 阻塞的含义是"返回时数据已完整放进接收缓冲区"。所以 Send 返回 ≠ 对方已收到。接收方可以用 `MPI_ANY_SOURCE`、`MPI_ANY_TAG` 做通配匹配，然后从 `status` 里读出真正的来源和 tag。易错点：count/datatype 说的是**元素个数**不是字节数；接收缓冲区要足够大（可以比实际来的消息大，多出的不动）；收发的 count 不匹配、tag 对不上都会导致收不到而挂起。

### 4.2.2 四种发送模式：standard / synchronous / buffered / ready

MPI 提供四种发送模式，语义各不同，理解它们能解释很多"为什么有时不死锁有时死锁"：

```
MPI_Send   标准模式：由实现决定是否先缓冲；可能立即返回，也可能等到对方开始接收
MPI_Ssend  同步模式：必须等到对方 Recv 已经开始配对，才返回（一定"握手"）
MPI_Bsend  缓冲模式：把数据拷进用户提供的缓冲区就返回（需先 MPI_Buffer_attach）
MPI_Rsend  就绪模式：假定对方 Recv 已提前发好，否则行为未定义（少用）
```

标准模式 `MPI_Send` 是日常最常用的，但它的完成语义是"**非本地（nonlocal）**"的——是否阻塞、阻塞多久，可能取决于对方有没有来接收。规范明确警告：**正确的 MPI 程序不得依赖标准 Send 会替你缓冲**，因为标准没承诺缓冲。想要确定性的握手用 `MPI_Ssend`，想要"拷了就走"的自缓冲用 `MPI_Bsend`。初学者常写出"在小消息上跑得好好的、换大消息就挂"的程序，根源就在这里（下一节展开）。

### 4.2.3 死锁的成因：都先发、都不收

最经典的 MPI 死锁：两个进程都先 `MPI_Send` 给对方、再 `MPI_Recv`：

```
两个进程都执行：
  MPI_Send(...dest = partner...);   ← 都卡在这一步
  MPI_Recv(...source = partner...); ← 永远走不到
```

如果 Send 是同步语义或消息太大无法被缓冲，两个进程就都停在 Send 上等对方来接收，而谁都不会去执行后面的 Recv——互相等待，永久挂起。

为什么"小消息不挂、大消息挂"？这是实现层的 **eager vs rendezvous 协议**在起作用（Open MPI / MPICH 文档）：小消息走 **eager**（急切）协议，实现会先把数据塞进内部缓冲、Send 立即返回，于是没死锁；大消息走 **rendezvous**（会合）协议，必须先和接收方握手确认对方准备好了才传，于是 Send 阻塞、死锁暴露。切换的阈值是实现相关的可调参数（具体默认值随实现和版本变，标「待核」，不背具体字节数）。这就是为什么这种 bug 极其阴险——它依赖了标准根本没承诺的缓冲行为，规模一变就翻车。本机实证复现：2 进程各 `Send` 一个约 16 MB 的大数组给对方再 `Recv`，`timeout 8 mpirun -np 2` 返回退出码 124（超时），死锁如预期重现。

### 4.2.4 死锁避免：换序、Sendrecv、非阻塞

有三条标准手段可以打破上面的循环等待。其一，**打破对称**——让一方先收后发、另一方先发后收（如按 rank 奇偶分工，或经典的"环形传递"里 rank 0 特殊处理）。其二，用 **`MPI_Sendrecv`**，它把一次发送和一次接收合成一个调用，由 MPI 实现内部保证不自锁：

```
MPI_Sendrecv(sendbuf, ..., dest,   sendtag,
             recvbuf, ..., source, recvtag, comm, &status);
```

其三，用**非阻塞**通信（下一节），先把收发都"挂起"再统一等待。

本机实证对照很直观：4.2.3 里"都先 Send"的版本超时死锁；把它换成 `MPI_Sendrecv` 后，2 进程各自拿到对方的值、退出码 0、正常结束。直觉上，`Sendrecv` 相当于告诉 MPI"我要同时寄信和收信，你自己安排顺序别让我卡住"，实现会用非阻塞机制在内部兜住。易错点：`Sendrecv` 的发送缓冲和接收缓冲通常要是**不同的**内存（要原地覆盖用 `MPI_Sendrecv_replace`）；还有一个隐藏出口是"环"两端接 `MPI_PROC_NULL`（一个空进程哨兵，向它收发是空操作），本机实证 `MPI.PROC_NULL == -2`（此为 Open MPI 取值，具体常量值实现相关）。

### 4.2.5 非阻塞通信：Isend / Irecv 与 Wait

非阻塞调用 `MPI_Isend` / `MPI_Irecv`（`I` = immediate）**立即返回**一个请求句柄 `MPI_Request`，通信在后台进行；你必须之后用 `MPI_Wait`（或 `MPI_Test`）来确认它真正完成，完成前不得动用相关缓冲区：

```
MPI_Irecv(rbuf, ..., source, tag, comm, &req_r);  /* 先挂起接收 */
MPI_Isend(sbuf, ..., dest,   tag, comm, &req_s);  /* 再挂起发送 */
   ... 这里可以做与通信无关的计算 ...
MPI_Wait(&req_r, &status);   /* 等接收完成 */
MPI_Wait(&req_s, &status);   /* 等发送完成 */
```

非阻塞有两大价值。其一是**避免死锁**：因为 `Isend`/`Irecv` 都不阻塞，前面"都先发"的死锁自然消失（大家都先挂起、再一起 Wait）。其二是**通信-计算重叠**：通信在后台跑时，进程可以先算别的，等真需要数据时再 Wait，从而把网络延迟藏到计算背后（这是大主题8 性能优化的关键手法）。易错点：`Wait` 之前**绝对不能读写**正在收发的缓冲区，否则读到半截数据或破坏发送；`MPI_Test` 是"看一眼完成没、没完成也立刻返回"的轮询版，别把 Test 当 Wait 用而漏了真正的完成确认。

#### 来源与时效
- MPI Standard 5.0（MPI Forum，2025-06）：点对点通信章——`MPI_Send`/`MPI_Recv` 完成语义、四种发送模式、`MPI_Sendrecv`、`MPI_Isend`/`MPI_Irecv`/`MPI_Wait`/`MPI_Test`、"正确程序不得依赖缓冲"的显式警告。（核实 2026-08-01）
- Pacheco《An Introduction to Parallel Programming》2nd ed（2022）：send/recv 匹配（comm, rank, tag）、"unsafe program"与死锁、`MPI_Sendrecv` 与非阻塞重叠的教学讲解。
- Open MPI / MPICH 官方文档：eager vs rendezvous 协议是实现层机制（阈值为可调参数，随实现/版本变）。https://www.open-mpi.org/doc/ 、https://www.mpich.org/documentation/ （核实 2026-08-01）
- 本机实证（现装·非持久）：大消息"都先 Send"→ `mpirun -np 2` 退出码 124（死锁超时）；改 `Sendrecv` → 退出码 0、两进程互换数据成功；`MPI.PROC_NULL` 打印为 -2。
- 冲突/分歧：eager/rendezvous 阈值与 `MPI_PROC_NULL` 具体数值属实现相关，各实现不同——标准只规定语义不规定数值；本报告只记语义与本机观测值，具体默认字节数标「待核」。规范语义（Send 完成 ≠ 已送达）与"实现常帮你缓冲"的表象是同一件事的两面，按"规范 vs 实现"分账处理，不和稀泥。

## 4.3 集合通信：bcast / reduce / scatter / gather / allreduce

### 4.3.1 集合通信的通用规则

集合通信（collective communication）是**通信子内所有进程一起参与**的一次通信操作。它和点对点最大的区别是"集体性"：一个 collective（如 `MPI_Bcast`）必须被该通信子里**每一个进程都调用**，缺一个就会挂起。集合操作在概念上是同步协作的，但除 `MPI_Barrier` 外并不保证进程齐步走出——它只保证"数据被正确地按模式搬运/规约完成"。

为什么要有 collective 而不都用 send/recv 手搓？两个理由。一是**表达力**：广播、规约、散播这些模式太常见，标准直接给成一个调用，代码干净、意图清晰。二是**性能**：实现可以用树形、递归折半等算法把一个 N 进程的广播/规约做到 O(log N) 步而非朴素的 O(N) 步，还能针对具体网络拓扑优化——这些你自己用 send/recv 写既繁琐又难做好。易错点：所有进程传入的 count/datatype/root 等参数必须"匹配一致"；把 collective 放进 `if(rank==0)` 这种只有部分进程走到的分支里，是新手最常见的挂起原因。

### 4.3.2 Bcast：一个进程把数据发给所有进程

`MPI_Bcast` 把 root 进程缓冲区里的数据，复制到通信子内所有其它进程的同名缓冲区：

```
MPI_Bcast(buf, count, datatype, root, comm);
```

调用后，所有进程的 `buf` 都等于 root 原来的内容。注意它是"就地"的——root 上 `buf` 已有数据，其它进程上 `buf` 是待填的空间，但接口形式一样（所有进程都传 `buf`、都传同一个 root）。

典型场景：rank 0 从文件读入配置参数或初始矩阵，然后 `Bcast` 给全体，避免每个进程都去读一遍磁盘。直觉上就是"广播一条通知，全场都收到同一份"。本机实证：rank 0 持 `[1,2,3]`、其余进程持空数组，`Bcast(root=0)` 后 rank 3 打印 `[1. 2. 3.]`，符合预期。易错点：`Bcast` 不是"发出去就好"，接收方也必须调用同一个 `Bcast`，否则挂起；这与点对点"发一次收一次"是不同的心智模型。

### 4.3.3 Scatter / Gather：分发与收集

`MPI_Scatter` 是 root 把一个大数组**切成 size 段、每个进程分一段**；`MPI_Gather` 是它的逆操作，把各进程的小块**收集拼回** root 的一个大数组：

```
MPI_Scatter(sendbuf, sendcount, sendtype,      /* 仅 root 有意义 */
            recvbuf, recvcount, recvtype, root, comm);
MPI_Gather (sendbuf, sendcount, sendtype,
            recvbuf, recvcount, recvtype, root, comm);   /* 结果仅在 root */
```

Scatter 与 Bcast 的区别要分清：Bcast 是"人人拿到**同一份完整**数据"，Scatter 是"每人拿到**不同的一小段**"。Gather 则把结果汇总回 root。这一对是"数据并行"的标准骨架：root 切分输入 → Scatter 分给大家 → 各自算自己那段 → Gather 收回结果。

本机实证（4 进程）：root 造 `[0,1,2,3]`，`Scatter` 后每进程各拿一个数，各自乘 10，`Gather` 回 root 得 `[0,10,20,30]`，链路正确。相关变体：`MPI_Allgather`（收集结果后人人都拿到完整拼接，不只 root）、`MPI_Alltoall`（每个进程给每个进程发不同一段，相当于分布式转置）。易错点：`sendcount`/`recvcount` 指的是"发给/收自**每个**进程"的元素数，不是总数；等分不尽时要改用 `MPI_Scatterv`/`MPI_Gatherv`（带位移和变长）。

### 4.3.4 Reduce：带运算的规约

`MPI_Reduce` 在把各进程的数据汇总到 root 的同时，**用一个运算符 op 把它们合并成一个结果**（求和、求最值等）：

```
MPI_Reduce(sendbuf, recvbuf, count, datatype, op, root, comm);
```

比如每个进程有一个局部和，`MPI_Reduce(..., MPI_SUM, root=0, ...)` 会把它们全加起来、把总和放到 root 的 `recvbuf`。常用内置 op 有：

```
MPI_SUM  求和   MPI_PROD 求积   MPI_MAX 最大   MPI_MIN 最小
MPI_LAND/MPI_LOR 逻辑与/或      MPI_MAXLOC/MPI_MINLOC 带位置的最值
```

它是并行"求和/求最值/求点积"这类聚合的标准工具——每个进程算自己那部分的局部结果，一次 `Reduce` 就拿到全局结果，实现内部用树形归约做到 O(log N) 步。本机实证（4 进程，各持 rank+1，即 1/2/3/4）：`Reduce(MPI_SUM, root=0)` 得 10，正确。易错点：内置 op 要求所有进程 datatype/count 一致；op 必须是可结合的，因为实现会以任意结合顺序合并（浮点求和因此在不同进程数下可能有微小舍入差异，这是正常现象不是 bug）；自定义运算要用 `MPI_Op_create`。

### 4.3.5 Allreduce / Allgather：结果人人有份

`MPI_Allreduce` 等价于"`Reduce` + 把结果 `Bcast` 回所有进程"，做完后**每个进程都拿到同一个全局规约结果**，没有单一 root：

```
MPI_Allreduce(sendbuf, recvbuf, count, datatype, op, comm);  /* 无 root 参数 */
```

它是分布式计算里出现频率极高的一个操作。想想迭代求解里每一步都要算一个全局残差/全局点积，且**所有进程都要拿这个值来判断是否收敛**——这时用 `Allreduce` 一步到位，比"先 Reduce 到 root 再 Bcast 回去"更省事，实现也能把两步融合优化。深度学习的数据并行训练里，多卡对梯度做 `Allreduce` 求和/平均，正是同一个模式（大主题10 会再遇到）。

本机实证（4 进程各持 1/2/3/4）：`Allreduce(MPI_SUM)` 后每个进程都得到 10（本机打印最后一个 rank 看到 10）。易错点：`Allreduce` 比 `Reduce` 贵（人人都要拿到结果，通信量更大），只在确实"人人都需要该值"时才用；只有 root 需要就用 `Reduce`。注意它没有 root 参数——这是和 `Reduce` 接口上的直接区别。

### 4.3.6 非阻塞集合通信（MPI-3 起）

从 MPI-3.0（2012）起，每个集合通信都有对应的**非阻塞版本**，函数名前加 `I`（immediate），返回请求句柄，语义类似非阻塞点对点：

```
MPI_Ibcast(...,  &request);
MPI_Ireduce(..., &request);
MPI_Iallreduce(..., &request);
   ... 期间可做别的计算 ...
MPI_Wait(&request, &status);
```

它的用途和非阻塞点对点一样——**把集合通信和计算重叠**，把全局通信（往往是扩展性瓶颈）的延迟藏到计算背后。这属于相对较新、面向性能的特性：本机现装的 Open MPI 4.1.6 支持到 MPI-3.1，包含这些非阻塞 collective。易错点：和非阻塞点对点同理，`Wait` 完成前不能碰缓冲区；且非阻塞 collective 仍要求所有进程都发起（缺一挂起），只是各自不阻塞而已。相关的还有 MPI-3 的**邻居集合通信**（neighborhood collectives，配合进程拓扑做只跟邻居的规则通信，见 4.4）。

#### 来源与时效
- MPI Standard 5.0（MPI Forum，2025-06）：集合通信章——`MPI_Bcast`/`MPI_Scatter(v)`/`MPI_Gather(v)`/`MPI_Reduce`/`MPI_Allreduce`/`MPI_Allgather`/`MPI_Alltoall`、内置 `MPI_Op`、非阻塞集合（`MPI_Ibcast` 等，MPI-3.0 引入）、邻居集合。（核实 2026-08-01）
- Pacheco《An Introduction to Parallel Programming》2nd ed（2022）：collective vs 点对点、树形规约的性能直觉、Scatter/Gather 数据并行骨架。
- Berkeley CS267（Spring 2024）：collective 算法（树形/递归折半广播与规约）与其扩展性。（核实 2026-08-01）
- 本机实证（现装·非持久，`mpirun -np 4`）：Bcast → 末进程收到 `[1,2,3]`；Scatter→×10→Gather 得 `[0,10,20,30]`；Reduce(SUM 1..4)=10；Allreduce(SUM)=10 人人可见。
- 冲突/分歧：无语义冲突。版本层面——非阻塞集合是 MPI-3.0 起特性，早于 MPI-3 的实现不具备；浮点规约的结果对进程数敏感（结合序不同导致舍入差异）是标准允许的行为，不同实现/进程数下数值可有末位差别，不作"结果唯一"结论。

## 4.4 通信子（communicator）与进程拓扑

### 4.4.1 通信子是什么：一组进程 + 一个通信上下文

通信子（communicator）是 MPI 里"一次通信发生在哪群进程之间"的封装。它包含两样东西：一个**进程组**（哪些进程是成员，各自的 rank 是几）和一个**通信上下文**（一个隔离的"频道"，保证不同通信子里的消息不会互相串台）。程序启动就自带 `MPI_COMM_WORLD`（全体进程）和 `MPI_COMM_SELF`（只含自己）。前面所有 send/recv/collective 的最后一个 `comm` 参数，指的就是通信子。

为什么需要它、能不能只用 `MPI_COMM_WORLD`？能，但会有两个问题。其一是**隔离**：如果你写的库内部用 tag=5 发消息，用户代码恰好也用 tag=5，就可能误匹配；把库的通信放进一个**独立通信子**，其上下文就把两边的消息彻底隔开，即使 tag 撞车也不串——这是 MPI 支持可组合并行库的关键机制。其二是**分组协作**：很多算法要让"一部分进程"单独做集合通信（如按行分组的矩阵算法），这就需要能造出只含那部分进程的子通信子。可以把通信子理解成"一个带专属对讲频道的工作小组"。

### 4.4.2 MPI_Comm_split：按颜色分组

造子通信子最常用的是 `MPI_Comm_split`：所有进程传入一个 `color` 和一个 `key`，MPI 把 **color 相同的进程归入同一个新通信子**，并按 `key` 决定它们在新通信子里的 rank 顺序：

```
MPI_Comm_split(old_comm, color, key, &new_comm);
```

例如按 `color = rank % 2` 分成"偶数组"和"奇数组"两个子通信子，此后在 `new_comm` 上做的集合通信就只在组内进行。

本机实证（4 进程，`color = rank % 2`，`key = rank`）：world rank 0/2 进入偶数组（子 rank 0/1），world rank 1/3 进入奇数组；在各自子通信子上 `allreduce(SUM)` 得偶数组和 =0+2=2、奇数组和 =1+3=4，分组隔离正确。这直接印证了 4.1.2 说的"同一进程在不同通信子里 rank 不同"——world rank 2 在偶数子组里 rank 是 1。易错点：`Comm_split` 本身是**集合操作**，old_comm 里所有进程都要调用；传 `color = MPI_UNDEFINED` 的进程会得到 `MPI_COMM_NULL`（表示"不加入任何新组"）；用完的通信子应 `MPI_Comm_free` 释放。

### 4.4.3 进程组（group）与从组造通信子

进程组（`MPI_Group`）是通信子里"成员集合"这一半的独立抽象——它只是一个有序的进程集合，不带通信上下文，因此不能直接用来通信。MPI 提供对组做集合运算的函数（并、交、差，以及按 rank 列表增删），再由组造出通信子：

```
MPI_Comm_group(comm, &group);              /* 取出通信子的进程组 */
MPI_Group_incl(group, n, ranks, &subgrp);  /* 按 rank 列表挑出子组 */
MPI_Comm_create(comm, subgrp, &new_comm);  /* 由子组造新通信子 */
```

这条路子比 `Comm_split` 更精细：当你要的分组不是"按某个 color 均匀切"，而是"精确指定就用这几个 rank"时，用 group 操作更直接。直觉上，group 是"名单"，communicator 是"名单 + 专属频道"；你先在名单层面做集合运算，满意了再"开一个频道"变成能通信的通信子。初学者阶段用 `Comm_split` 覆盖绝大多数需求即可，group 路线作为更灵活的备选了解即可。

### 4.4.4 进程拓扑：Cartesian 与 graph

进程拓扑（process topology）是给通信子附加一层"进程之间的逻辑邻接结构"，方便按几何/图结构定位邻居。最常用的是**笛卡尔拓扑**（Cartesian），把进程排成一维/二维/三维网格：

```
MPI_Cart_create(comm, ndims, dims, periods, reorder, &cart_comm);
MPI_Cart_coords(cart_comm, rank, ndims, coords);     /* rank -> 网格坐标 */
MPI_Cart_shift(cart_comm, direction, disp, &src, &dst); /* 求某方向的邻居 rank */
```

这在**模板计算 / 网格 PDE**（大主题10）里极自然：把 P 个进程摆成二维网格、每个进程负责一块子区域，`MPI_Cart_shift` 直接告诉你"我上/下/左/右的邻居是哪个 rank"，边界交换（halo exchange）就好写了。除笛卡尔外还有更一般的**图拓扑**（graph / dist_graph）描述任意邻接关系。

本机实证（4 进程建 2×2 笛卡尔、非周期）：rank 0 的坐标为 `[0,0]`，沿维度 0 正向 `Cart_shift` 得 `dst=2`（即坐标 `[1,0]` 的进程，正确）、`src=-2`（即 `MPI_PROC_NULL`，因为 rank 0 在该方向是边界、没有上游邻居）。这也顺带印证了 4.2.4 提到的"边界接 `MPI_PROC_NULL`"——拓扑帮你把边界的空邻居自动标成可安全收发的空进程。易错点：`reorder=true` 允许 MPI 为贴合物理网络而重排 rank，此后进程的 rank 可能变，不能再假定它等于 `COMM_WORLD` 里的 rank；`periods` 控制该维是否首尾相接（环）。

#### 来源与时效
- MPI Standard 5.0（MPI Forum，2025-06）：通信子与组章（`MPI_Comm_split`/`MPI_Comm_group`/`MPI_Group_incl`/`MPI_Comm_create`/`MPI_Comm_free`）、进程拓扑章（`MPI_Cart_create`/`MPI_Cart_coords`/`MPI_Cart_shift`、图拓扑、邻居集合）。（核实 2026-08-01）
- Pacheco《An Introduction to Parallel Programming》2nd ed（2022）：通信子作为"分组 + 上下文隔离"的动机、`Comm_split` 用法、笛卡尔拓扑做网格分解的教学例子。
- Open MPI / MPICH 官方文档：`MPI_Comm_split`、`MPI_Cart_*` 手册页。（核实 2026-08-01）
- 本机实证（现装·非持久，`mpirun -np 4`）：`Comm_split(color=rank%2)` 偶/奇组内 allreduce 得 2/4；`Cart_create([2,2])` 下 rank0 坐标 `[0,0]`、`Cart_shift` 得 dst=2、src=-2（PROC_NULL）。
- 冲突/分歧：无实质分歧。`MPI_PROC_NULL` 具体数值（本机 -2）实现相关，标准只保证语义。

## 4.5 MPI+X 混合并行（MPI+OpenMP / MPI+CUDA）

### 4.5.1 为什么要混合：两级并行匹配两级硬件

MPI+X（"MPI plus X"）指用 MPI 处理**节点之间**的并行、再在**每个节点内部**叠加另一种并行技术 X（X 常是 OpenMP 线程或 CUDA GPU）。它的动机来自现代超算的硬件结构本身就是两级的：一台超算由很多**节点**（各自独立内存，节点间靠网络）组成，每个节点内又有很多**核**（共享该节点内存）甚至 GPU。纯 MPI 也能跑（每核一个进程），但混合并行让通信模型贴合硬件层次——跨节点用消息传递、节点内用共享内存或 GPU，往往更高效。

直觉上：MPI 负责"楼与楼之间寄快递"，X 负责"同一栋楼里的人直接喊话/协作"。好处主要有二：节点内用 OpenMP 共享内存协作，省掉了同节点进程间那份消息拷贝和每进程一份的数据副本，**减少内存占用和节点内通信**；跨节点的 MPI 进程数变少，也让全局集合通信（如 `Allreduce`）参与者更少、更快。代价是编程复杂度上升——你要同时管好两套并行的正确性。这是当代 HPC 应用的主流形态，不是可选项。

### 4.5.2 MPI+OpenMP 与线程安全级别

MPI+OpenMP 是最常见的组合：每个节点跑一个（或少数几个）MPI 进程，进程内用 OpenMP 开多个线程占满该节点的核。关键在于——**当多线程和 MPI 混用时，必须声明你需要的线程安全级别**，用 `MPI_Init_thread` 代替 `MPI_Init`：

```
MPI_Init_thread(&argc, &argv, required, &provided);
```

标准定义了四个从弱到强的级别：

```
MPI_THREAD_SINGLE      只有一个线程（不多线程）
MPI_THREAD_FUNNELED    多线程，但只有主线程调用 MPI
MPI_THREAD_SERIALIZED  多线程都可调 MPI，但同一时刻只能一个（需自行串行化）
MPI_THREAD_MULTIPLE    多线程可任意并发调用 MPI（最灵活，实现开销也最大）
```

你请求 `required`、实现回填它实际能给的 `provided`，**必须检查 `provided >= required`**——实现不保证一定满足你请求的级别。常见做法是"MPI 通信只在 OpenMP 并行区之外、由主线程做"，对应 `FUNNELED`，简单又够用；只有当多个线程真要各自独立发 MPI 消息时才需要 `MULTIPLE`（它对实现要求最高、可能更慢）。易错点：用了 OpenMP 却仍调 `MPI_Init`（默认只保证 `SINGLE`）、又让多线程碰 MPI，属未定义行为；这是混合程序最隐蔽的一类 bug。

### 4.5.3 MPI+CUDA 与 CUDA-aware MPI

⚙演进快·锚 CUDA Toolkit 13.x（本机无 GPU·文档腿，核实 2026-08-01）

MPI+CUDA 用于 GPU 集群：每个 MPI 进程管理一块（或多块）GPU，节点间用 MPI 交换数据、节点内用 CUDA 在 GPU 上算（GPU 编程模型见大主题5/6）。朴素写法是"GPU→主机（D2H）拷回 CPU 内存 → MPI 发送 → 对端 MPI 接收 → 主机→GPU（H2D）拷上去"。而 **CUDA-aware MPI** 允许你**直接把 GPU 设备指针传给 `MPI_Send`/`MPI_Recv`**，由 MPI 实现（配合 GPUDirect 等技术）在底层直接搬运显存数据，省去显式的 D2H/H2D 中转、并可能走 GPU 间直连。

这是当前 HPC 与大规模深度学习训练的骨架技术：多机多卡训练里对梯度做的分布式 `Allreduce`，底层常就是 CUDA-aware MPI（或 NCCL 等专用集合库）在 GPU 显存间直接规约。直觉上，CUDA-aware MPI 让"显存到显存"成为一等公民，不必每次都绕道主机内存。是否可用、性能如何**高度依赖实现、硬件（NVLink/InfiniBand + GPUDirect）与驱动版本**，随版本演进快，具体能力与默认行为标「待核」，不背具体数字。本机无 GPU，本项为文档腿、未做实机验证，不给任何 kernel/带宽输出。

### 4.5.4 混合并行的权衡：不是层数越多越好

混合并行不是"叠的技术越多越快"。它引入了实打实的复杂度：正确性上要同时守住 MPI（消息匹配、死锁）和 X（OpenMP 数据竞争 / CUDA 内存管理）两套规则，还要处理它们的交界（线程安全级别、GPU 指针能否直接进 MPI）；性能上，节点内进程数与线程数的配比（如"每节点 1 进程 × 32 线程" vs "4 进程 × 8 线程"）需要按具体应用和机器实测调优，没有普适最优解。

给初学者的落点是心智模型而非调参细节：先理解"**MPI 管节点间、X 管节点内**"这条分工主线，知道混合的收益（省内存、少通信、贴合硬件）和代价（复杂度、需调配比、线程安全）各是什么。很多程序在纯 MPI（每核一进程）下已经足够好——只有当纯 MPI 遇到内存吃紧或全局通信成为扩展瓶颈时，混合并行的价值才真正显现。具体配比与是否上 CUDA-aware，属需实测和随平台/版本变的工程决策，本报告不给固定结论。

#### 来源与时效
- MPI Standard 5.0（MPI Forum，2025-06）：`MPI_Init_thread` 与四个线程安全级别（`MPI_THREAD_SINGLE`/`FUNNELED`/`SERIALIZED`/`MULTIPLE`）、`provided` 回填语义。（核实 2026-08-01）
- OpenMP API 6.0（2024-11）作为 X=OpenMP 的一手参照（节点内共享内存并行，详见大主题3）。（核实 2026-08-01）
- Berkeley CS267（Spring 2024）：混合 MPI+OpenMP、GPU 集群编程与进程/线程配比的讨论。（核实 2026-08-01）
- ⚙演进快·锚版本：CUDA Toolkit 13.x（CUDA-aware MPI / GPUDirect，本机无 GPU·文档腿·未验证，核实 2026-08-01）；交叉参照 PMPP 4th ed Ch20 异构集群（CUDA streams 与 MPI 结合）。
- 冲突/分歧：CUDA-aware MPI 的可用性/性能强依赖实现、硬件与驱动，各来源给的收益因平台而异，本报告只记机制不记具体加速数字（标「待核」）；进程/线程配比无普适最优，属实测项。标准锚 MPI 5.0，现装实现 Open MPI 4.1.6（MPI-3.1）——`MPI_Init_thread` 与四级线程安全自 MPI-2 起即有，现装实现支持。
