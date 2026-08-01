# L6-06·大主题5 GPU 架构与 CUDA 编程模型

> 基线 Py3.11.15/np2.4.6/gcc13.3 @2026-07-25 ｜核实日期：2026-08-01 ｜先修：L6-06·大主题1 并行计算动机与架构分类（SIMD/SIMT 的位置、异构系统谱系）、L4-05 并发（线程与共享状态的直觉）、C 语言基础（指针、内存分配）｜一手锚点：PMPP 4th ed（Kirk/Hwu/El Hajj，2023）Ch2 异构数据并行 / Ch3 多维网格与数据 / Ch4 计算架构与调度、CUDA C++ Programming Guide（Release 13.3，2026-06-25）、Berkeley CS267（Spring 2024，Demmel）GPU 章｜成熟度：编程模型核心（SIMT/warp/grid-block-thread/kernel 启动）为长期稳定 GA；CUDA 工具链与算力代际 ⚙演进快·锚 CUDA Toolkit 13.3（13.4 为 developer preview，2026-07-15）

本报告讲**GPU 作为吞吐机器是怎么组织执行的**，以及**程序员用 CUDA 这套编程模型如何驱动它**：一条 kernel 怎样被成千上万个线程同时执行（SIMT/warp），这些线程如何用 grid-block-thread 三级层级组织并各自算出自己该处理哪块数据（全局索引），host（CPU）代码如何启动 device（GPU）代码，以及数据如何在 CPU 与 GPU 之间搬运（H2D/D2H / 统一内存）。这里只建立编程者需要的心智模型，不深入 GPU 硬件微架构（SM 内部流水线、寄存器堆物理结构），那属于体系结构课；性能优化（内存合并、tiling、占用率）另立一篇，本篇是"怎么写对"，不是"怎么写快"。

**全主题硬标「本机无 GPU·未验证·文档腿」**：本机 `lscpu` 为 Intel Xeon CPU、无 CUDA 设备，所有 kernel 代码不能在本机真跑取输出，故 5.1/5.3/5.4 的执行行为与 API 效果均来自官方文档与教材的多来源比对，不凭记忆编造 kernel 输出。仅 5.2 的全局索引映射可用 numpy 做纯 CPU 类比（无 kernel），本报告在该处给出了真实运行的类比脚本与输出。

---

## 5.1 SIMT 执行与 warp 概念

### 5.1.1 SIMT：单指令、多线程

SIMT（Single-Instruction, Multiple-Threads，单指令多线程）是 GPU 执行 CUDA kernel 的方式：你写一份 kernel 代码（一个线程的视角），GPU 会用大量线程同时执行**同一份代码**，但每个线程带着自己的一套寄存器、自己的索引，因此处理不同的数据、并且可以走不同的分支。CUDA C++ Programming Guide 明确指出，在 SIMT 模型下"每个线程维护自己的状态和控制流，从功能角度看每个线程都能执行一条独立的代码路径"。

对初学者，最好的心智模型是"一份菜谱、一千个厨师同时照做，但每人分到不同的一堆食材"。你不需要写循环去遍历数组元素，而是写"第 i 个线程处理第 i 个元素"，然后让 GPU 铺开成千上万个线程各就各位。这与 CPU 上"一个线程跑一个 for 循环"是相反的思路：CPU 把并行度藏在循环里由一个执行流串行推进，GPU 把并行度摊在线程上由海量执行流同时推进。这也是为什么 GPU 被称为"吞吐导向"（throughput-oriented）——它不追求让单个线程快，而追求让极多线程整体吞吐大。

### 5.1.2 warp：32 个线程的硬件调度单位

warp（线程束）是 GPU 硬件真正的调度单位：block 内的线程被硬件切成每 32 个一组，一组就是一个 warp，同一个 warp 里的 32 个线程在硬件上被**捆在一起、同一时刻执行同一条指令**。CUDA C++ Programming Guide（Release 13.3）明确写道"在一个线程块内，线程被组织成每 32 个线程一组、称为 warp"，且 warp 大小为 32 这个数值在当前所有算力代际上保持不变（可通过设备属性 `warpSize` 查询，值为 32）。

为什么初学者一定要知道 warp？因为很多"为什么慢"的答案都藏在 warp 里，而它在你的源码里是**看不见**的——你写的是线程，硬件跑的是 warp。两个直接推论：其一，block 的线程数最好取 32 的倍数，否则最后一个 warp 会有线程"空转"（官方指南建议线程块总线程数取 32 的倍数）；比如 block 设 50 个线程，硬件仍要开 2 个 warp（64 个线程槽），有 14 个槽是浪费的。其二，warp 内 32 个线程走不同分支会带来"分歧"代价（见 5.1.4）。warp 这个概念是理解 GPU 性能的第一把钥匙。

### 5.1.3 SIMT 与 SIMD 不是一回事

SIMD（Single-Instruction, Multiple-Data）是 CPU 向量指令的模型：一条指令显式操作一个固定宽度的向量寄存器（如 AVX2 的 256 位、可装 8 个 float），程序员或编译器要显式地把数据打包进向量。SIMT 则是"把 SIMD 包装成多线程的样子"：硬件底层仍是一条指令驱动一批（warp 内 32 个）执行通道，但程序员写的是**标量线程代码**，每个线程看起来像独立的标量程序，打包成 warp 这件事由硬件自动完成。

初学者最容易把两者混为一谈，记住关键差异：SIMD 里"多数据"是**显式的一个宽向量**，分支要靠掩码手工处理，线程数=1；SIMT 里"多线程"是**一批标量线程**，每个线程有独立索引、可以各自越界检查和分支，硬件在幕后按 warp 打包。SIMT 的好处是编程模型简单（写标量、想成一个线程一个元素），代价是当 warp 内线程分歧时性能会退化——本质上它是"用多线程的外衣、行 SIMD 的实"。上游大主题里 Flynn 分类把 SIMT 定位为"SIMD 与 MIMD 之间"的实用折中，正是这个道理。

### 5.1.4 warp 分歧（divergence）与掩码执行

warp 分歧（warp divergence）指同一个 warp 内的 32 个线程在遇到条件分支时走向了不同的路径（比如一半线程 `if` 为真、一半为假）。因为 warp 在硬件上共享一条指令流，硬件只能**分批串行**地执行各条分支：先执行走 `if` 分支的线程（其余线程被掩码"屏蔽"、空等），再执行走 `else` 分支的线程。结果是分支的两条路径时间相加，而不是并行，warp 的有效吞吐下降。官方指南强调"当同一 warp 内线程走分歧路径的情形被最小化时，能获得可观的性能提升"。

直觉上，warp 像一个 32 人的方队只能听同一个口令：如果命令是"向左的向左走、向右的向右走"，方队只能先让向左的走完、再让向右的走，全程 32 人绑在一起。所以 `if (threadIdx.x % 2 == 0)` 这种让相邻线程分道的写法很伤，而 `if (blockIdx.x == 0)` 这种让整块（含整 warp）走同一边的写法几乎无代价——因为分歧只在 **warp 内部**才有成本，不同 warp 之间走不同分支是完全免费的。这是初学者写 kernel 最常踩的隐形坑：代码逻辑对，但数据布局让相邻线程频繁分歧，性能莫名其妙差。

### 5.1.5 独立线程调度：Volta 之后 warp 内线程有了各自的程序计数器 ⚙演进快·锚版本·随时变

在早期算力代际上，warp 内 32 个线程共享**一个**程序计数器，严格 lockstep（步调完全一致）推进。从 Volta 架构（算力 7.0，2017 年）起，NVIDIA 引入了"独立线程调度"（Independent Thread Scheduling）：warp 内每个线程有各自的程序计数器和调用栈，允许更灵活地交错执行分歧路径。这带来一个重要后果——**不能再假设 warp 内线程天然同步**，需要用 `__syncwarp()` 或带掩码的 warp 级原语（如 `__shfl_sync`、`__ballot_sync`，带 `_sync` 后缀）来显式同步一个 warp 内的线程。

网上不少老教程写的 warp 内隐式同步代码（依赖旧的 lockstep 行为）在新架构上可能出错或行为不确定，属于"过去能跑、现在要显式加同步"的坑。这条与硬件代际强相关、属于快速演进区域，核实于 2026-08-01（锚 CUDA C++ Programming Guide Release 13.3）；具体各代际的调度细节请以对应算力版本的官方文档为准，本报告不展开纵深。

#### 来源与时效
- CUDA C++ Programming Guide（Release 13.3，2026-06-25）§2.3 Writing SIMT Kernels：warp=32、SIMT 每线程独立控制流、warp 分歧最小化的性能建议。https://docs.nvidia.com/cuda/cuda-programming-guide/02-basics/writing-cuda-kernels.html （核实 2026-08-01）
- CUDA C++ Programming Guide（Release 13.3）§1.2 Programming Model：block 内线程组织成 32 线程的 warp、block 线程数宜取 32 的倍数。https://docs.nvidia.com/cuda/cuda-programming-guide/01-introduction/programming-model.html （核实 2026-08-01）
- PMPP 4th ed（2023）Ch4 计算架构与调度：warp、SIMT 硬件、控制分歧（control divergence）与掩码执行。TOC：https://www.oreilly.com/library/view/programming-massively-parallel/9780323984638/xhtml/Contents.xhtml （核实 2026-08-01）
- Berkeley CS267（Spring 2024）GPU 章：SIMT vs SIMD 的定位、warp 概念。https://sites.google.com/lbl.gov/cs267-spr2024 ；存档 https://inst.eecs.berkeley.edu/~cs267/archives.html （核实 2026-08-01）
- ⚙演进快项：独立线程调度自 Volta（CC 7.0）引入、需 `_sync` 后缀 warp 原语——锚 CUDA C++ Programming Guide Release 13.3；各代际细节随算力版本变，本报告不取单一硬编码，标随时变。
- 本机实证：未取（本机无 GPU·文档腿）。

## 5.2 grid-block-thread 层级与全局索引计算

### 5.2.1 三级层级：thread → block → grid

CUDA 把并行线程组织成固定的三级层级：最小单位是 **thread（线程）**；若干线程组成一个 **block（线程块）**；若干 block 组成一个 **grid（网格）**。启动一个 kernel 就是启动一个 grid。官方指南描述为"线程块被组织成一个 grid，同一 grid 内所有 block 尺寸和维度相同"。block 和 grid 都可以是 1、2 或 3 维的（用 `dim3` 类型描述维度）。

用 numpy 数组做类比，一个 grid 就像把一个大数组切成规则的小片：每个 block 负责一片，block 内每个 thread 负责片里的一个元素。为什么要多这一层 block、不直接一个 grid 铺满线程？因为 block 是**协作与调度的单位**：同一个 block 的线程被保证跑在同一个 SM（流多处理器）上、能共享一块高速的片上内存（shared memory）、能用 `__syncthreads()` 相互同步；而不同 block 之间**不能**假设任何执行顺序或直接同步。这一层结构是 CUDA 把"可扩展性"写进编程模型的方式——见 5.2.6。

### 5.2.2 内建变量：threadIdx / blockIdx / blockDim / gridDim

kernel 里每个线程通过四个内建变量知道"我是谁、一共有多少人"：`threadIdx` 是本线程在其 block 内的坐标，`blockIdx` 是本 block 在 grid 内的坐标，`blockDim` 是每个 block 的尺寸（线程数），`gridDim` 是 grid 的尺寸（block 数）。每个都带 `.x` / `.y` / `.z` 三个分量以支持多维。官方指南列出这四个变量各带 `[x|y|z]` 分量。

这四个变量在每个线程里的值都不同（`threadIdx`、`blockIdx`）或全局相同（`blockDim`、`gridDim`），它们是每个线程"定位自己"的唯一信息来源。kernel 里没有隐式的循环变量 `i`——那个 `i` 必须由你用这些内建变量**算出来**。把它们想成"车厢号（blockIdx）+ 座位号（threadIdx）+ 每节车厢座位数（blockDim）+ 车厢总数（gridDim）"，你就能算出每个乘客的全局座位号。

### 5.2.3 一维全局索引公式

在一维情形下，把"第几个 block × 每 block 多少线程 + block 内第几个线程"相加，就得到线程在整个 grid 里的全局线性索引 i：

i = blockIdx.x * blockDim.x + threadIdx.x

官方指南给出的正是这个式子（写作 `int workIndex = threadIdx.x + blockIdx.x*blockDim.x;`），它是几乎每个一维 kernel 的第一行。有了 i，线程就用 `C[i] = A[i] + B[i]` 这样的语句直接处理"属于自己的那个元素"。

为什么是乘加而不是别的？因为线程在 grid 里是按 block 顺序、block 内按 thread 顺序线性排开的：block 0 占 0..blockDim-1，block 1 占 blockDim..2*blockDim-1，以此类推。用纯 CPU numpy 模拟这个映射（无 kernel，仅验证公式，本机真跑）：

```python
import numpy as np
blockDim, gridDim, N = 4, 3, 10
for blockIdx in range(gridDim):
    for threadIdx in range(blockDim):
        i = blockIdx * blockDim + threadIdx
        print(blockIdx, threadIdx, i, 'active' if i < N else 'guard-skip')
```

真实输出（Py3.11.15/np2.4.6）确认全局索引 i 恰好覆盖 0..11 且连续无重复：block 0 → i=0,1,2,3；block 1 → i=4,5,6,7；block 2 → i=8,9,10,11。其中 i=10,11 因超过 N=10 被标记跳过（正是下一节的边界守卫）。这印证了公式把二维 (blockIdx, threadIdx) 坐标正确线性化为一维全局索引。

### 5.2.4 边界守卫：if (i < N)

因为线程总数通常按整块向上取整、会**多于**实际数据量，几乎每个 kernel 都要在算出 i 之后加一句边界检查，防止越界访问：

```cuda
int i = blockIdx.x * blockDim.x + threadIdx.x;
if (i < N) {
    C[i] = A[i] + B[i];
}
```

原因是 grid 的线程数必须是 blockDim 的整数倍（你按 block 为单位启动），而数据量 N 未必整除 blockDim。例如 N=1000、blockDim=256，就需要 `ceil(1000/256)=4` 个 block、共 1024 个线程，最后 24 个线程的 i 落在 1000..1023、没有对应数据。若不加 `if (i < N)`，这 24 个线程会读写数组界外的内存，导致数据损坏或崩溃。上一节 numpy 输出里被标 `guard-skip` 的 i=10,11 正是这种"多出来的线程"。这是初学者第一个 kernel 最常漏的一行，务必形成肌肉记忆。

### 5.2.5 多维索引：处理矩阵与图像

当数据本身是二维（矩阵、图像）时，用二维 block 和 grid 更自然，行列索引各自独立计算：

row = blockIdx.y * blockDim.y + threadIdx.y

col = blockIdx.x * blockDim.x + threadIdx.x

然后把二维坐标按行主序展平成一维线性偏移去访问实际内存（GPU 全局内存是一维线性的）：

offset = row * width + col

初学者的易错点有两个。其一，`.x` 通常映射到**列/连续维**、`.y` 映射到行，方向别记反——这不仅关乎正确性，还关乎性能（相邻线程 `.x` 相邻时访存才连续，属下一篇性能话题）。其二，二维只是"编程方便"的组织方式，底层内存仍是一维，所以最终一定要用 `row * width + col` 这类公式回到线性地址；width 要用真实的行宽（可能含 padding），不能想当然用列数。三维（体数据）同理再加一层 `.z`。

### 5.2.6 block 独立执行、不保证顺序，每 block 上限 1024 线程

CUDA 编程模型强制要求：不同 block **必须能以任意顺序执行**（并行、串行、任意交错都合法），block 之间不能假设执行先后、也没有全局同步原语（除非 kernel 结束）。官方指南写道"不同 block 被调度到可用的 SM 上、可以任意顺序执行……编程模型要求 block 能以任意顺序执行"。同时，单个 block 的线程数有硬上限：自算力 2.x 起为 **1024 个线程**，且各维乘积 `blockDim.x * blockDim.y * blockDim.z ≤ 1024`（各维上限为 1024×1024×64）。

这两条一起决定了 CUDA 的可扩展性哲学：因为 block 相互独立、可任意排布，同一份代码在有 8 个 SM 的小 GPU 和有 132 个 SM 的大 GPU 上都能跑，硬件只需按自己的 SM 数量去调度这些独立的 block——这叫"透明可扩展"（transparent scalability）。初学者的易错点：想让两个不同 block 的线程互相等待或交换数据是**做不到**的（block 间无同步），需要这种协作时要么放进同一个 block，要么拆成两次 kernel 启动。另外，block 尺寸不是越大越好也不是随意取——受 1024 上限约束，且宜取 32 的倍数（对齐 warp）。

#### 来源与时效
- CUDA C++ Programming Guide（Release 13.3，2026-06-25）§1.2 Programming Model：grid/block/thread 三级层级、block 任意顺序独立执行、1/2/3 维、warp 组织。https://docs.nvidia.com/cuda/cuda-programming-guide/01-introduction/programming-model.html （核实 2026-08-01）
- CUDA C++ Programming Guide（Release 13.3）§2.3：内建变量 gridDim/blockDim/blockIdx/threadIdx 各带 [x|y|z]、全局索引 `threadIdx.x + blockIdx.x*blockDim.x`。https://docs.nvidia.com/cuda/cuda-programming-guide/02-basics/writing-cuda-kernels.html （核实 2026-08-01）
- PMPP 4th ed（2023）Ch2 异构数据并行（一维向量加、边界守卫）、Ch3 多维网格与数据（二维索引 row/col、线性化）。TOC 见 5.1。
- 每 block 1024 线程上限、各维 1024/1024/64 且乘积 ≤1024（自 CC 2.x）：CUDA 官方算力表 / 设备属性 `maxThreadsPerBlock`；交叉核对 Wikipedia「Thread block (CUDA programming)」。https://en.wikipedia.org/wiki/Thread_block_(CUDA_programming) （核实 2026-08-01）
- 本机实证：5.2 全局索引映射用 numpy 2.4.6 纯 CPU 类比（无 kernel）真跑，输出确认 i 连续覆盖且边界守卫逻辑正确；GPU 上的实际执行未取（本机无 GPU）。
- 冲突/分歧：未见来源分歧；1024 上限对极老算力（CC 1.x=512）不同，但基线锚 CC 2.x 及以上，故取 1024。

## 5.3 kernel 启动语法与 host / device 分离

### 5.3.1 函数限定符：__global__ / __device__ / __host__

CUDA C++ 用函数限定符区分代码在哪儿跑、能被谁调用。`__global__` 声明一个 **kernel**——它在 device（GPU）上执行、由 host（CPU）用三尖括号语法调用，且必须返回 `void`。`__device__` 声明的函数在 device 上执行、只能被 device 代码（kernel 或其他 device 函数）调用。`__host__` 声明的函数在 host 上执行、被 host 调用（不写限定符时默认就是 `__host__`）；`__host__ __device__` 可同时标注，让编译器生成两份、host 和 device 都能调。

一个 kernel 的样子（官方指南示例形态）：

```cuda
__global__ void vecAdd(float* A, float* B, float* C, int N) {
    int i = blockIdx.x * blockDim.x + threadIdx.x;
    if (i < N) C[i] = A[i] + B[i];
}
```

`__global__` 函数为什么必须返回 `void`？因为它被成千上万个线程执行，没有"一个返回值"的概念——官方指南指出"kernel 计算出的数值结果返回给 host 的唯一途径是把结果写进全局内存"。所以 kernel 的"输出"总是通过指针参数写回一块 device 内存，再由 host 拷回来。别想着 `return sum;`，要 `C[i] = ...;`。

### 5.3.2 三尖括号启动配置 <<<grid, block>>>

host 代码用一个 CUDA 特有的语法启动 kernel：在函数名和参数表之间插入 `<<< >>>` 三尖括号，里面写**执行配置**（execution configuration）——第一个参数是 grid 尺寸（多少个 block），第二个是 block 尺寸（每 block 多少线程）：

vecAdd<<<gridDim, blockDim>>>(A, B, C, N);

两个参数都可以是整数（一维）或 `dim3`（多维）。三尖括号还有可选的第三、第四参数：第三个是**每 block 动态分配的 shared memory 字节数**，第四个是 **stream（流）**，用于异步/并发：

vecAdd<<<gridDim, blockDim, sharedBytes, stream>>>(A, B, C, N);

官方指南给出 `functionName<<<grid, block, sharedMemoryBytes>>>()` 的形态。初学者常见做法是先算 blockDim（如 256），再由数据量反推 gridDim：

int blockDim = 256;

int gridDim = (N + blockDim - 1) / blockDim;

这里 `(N + blockDim - 1) / blockDim` 是"向上取整除法"的惯用写法，保证线程数覆盖全部 N 个元素（多出来的由 5.2.4 的边界守卫兜住）。易错点：三尖括号是 CUDA 对 C++ 的扩展语法，必须用 NVIDIA 的 nvcc 编译器（或兼容编译器）编译，普通 gcc/g++ 不认识。

### 5.3.3 kernel 启动是异步的，返回 void

host 调用 `kernel<<<...>>>(...)` 后，控制权几乎**立即**返回给 host——kernel 只是被排进 GPU 的执行队列，host 不会原地等它跑完。这叫异步启动。因此 kernel 启动语句本身不返回计算结果（返回 `void`），也不会立刻反映运行期错误。要让 host 等 GPU 完成，需显式调用 `cudaDeviceSynchronize()`（或对结果做 `cudaMemcpy`，拷贝会隐式等待）。

初学者两个高频坑。其一，紧接着启动就去读结果、却没同步，会读到还没算完（甚至还没开始）的旧数据；正确做法是启动后 `cudaDeviceSynchronize()` 再用结果，或直接靠 `cudaMemcpy` 的隐式同步。其二，kernel 启动的错误（如配置非法、越界）不会从 `<<<>>>` 语句"抛出"，要用 `cudaGetLastError()` 查启动错误、用 `cudaDeviceSynchronize()` 的返回值查执行期错误——很多"kernel 好像没跑"的问题其实是启动就失败了但没检查错误码。

### 5.3.4 host / device 分离与编译模型

CUDA 程序里 host 代码（CPU 上跑，管理内存、启动 kernel、做串行部分）和 device 代码（GPU 上跑的 kernel 和 `__device__` 函数）写在同一个 `.cu` 源文件里，但由 nvcc 编译器分开处理：device 部分编译成 GPU 的机器码/中间表示（PTX），host 部分交给宿主 C++ 编译器（如 gcc/g++/MSVC）。这套"一份源码、两个目标"的模型，让程序员能把 CPU 与 GPU 的协作写在一起。

对初学者，重要的是把"谁在 host、谁在 device"这条线时刻画清楚：host 指针指向 CPU 内存，device 指针指向 GPU 内存，二者不能混用（见 5.4）；kernel 里不能调用普通的 host 库函数（如任意 `printf` 之外的多数 libc）；数据要显式在两边搬。CUDA 工具链本身（nvcc、支持的宿主编译器版本、PTX/算力目标）属于快速演进区，具体版本以当前 CUDA Toolkit 文档为准（当前锚 13.3，2026-08-01 核实），本报告不锁死工具链细节。

#### 来源与时效
- CUDA C++ Programming Guide（Release 13.3，2026-06-25）§2.3 Writing SIMT Kernels：`__global__` kernel、返回 void、结果写全局内存、`<<<grid, block>>>` 与第三参数 sharedMemoryBytes、`vecAdd` 示例。https://docs.nvidia.com/cuda/cuda-programming-guide/02-basics/writing-cuda-kernels.html （核实 2026-08-01）
- CUDA C++ Programming Guide（Release 13.3）：`__host__`/`__device__`/`__global__` 限定符语义、执行配置第四参数 stream、异步启动与 `cudaDeviceSynchronize`；C/C++ Language Extensions 附录。https://docs.nvidia.com/cuda/cuda-programming-guide/05-appendices/cpp-language-extensions.html （核实 2026-08-01）
- PMPP 4th ed（2023）Ch2：host/device 分离、nvcc 编译模型、kernel 启动语法、向上取整算 gridDim。TOC 见 5.1。
- Berkeley CS267（Spring 2024）GPU 章：CUDA 程序结构（host 分配/拷贝/启动/回拷）。链接见 5.1。
- ⚙演进快项：CUDA Toolkit / nvcc 版本、支持的宿主编译器、算力目标——锚 CUDA Toolkit 13.3（13.4 developer preview，2026-07-15），随版本变。
- 本机实证：未取（本机无 GPU·文档腿）；nvcc 未安装，kernel 未编译未运行。

## 5.4 H2D / D2H 数据传输与统一内存

### 5.4.1 分离的地址空间与 cudaMalloc

传统 CUDA 模型里，CPU（host）内存和 GPU（device）内存是**两个独立的物理地址空间**：host 的 `malloc`/普通指针指向系统内存，GPU 访问不到；要给 GPU 用的数据必须先在 device 上单独分配。分配用 `cudaMalloc(&d_ptr, bytes)`，它返回一个 **device 指针**——这个指针只能在 device 上解引用，host 代码不能直接 `*d_ptr` 读它（会崩溃）。用完用 `cudaFree(d_ptr)` 释放。

`float* d_A` 拿到的地址是"GPU 地板上的门牌号"，在 CPU 的房子里按这个门牌号找东西是找不到的。所以典型 CUDA 程序有一套固定的五步舞：host 分配并填数据 → `cudaMalloc` 在 device 上开空间 → 把数据从 host 拷到 device → 启动 kernel 算 → 把结果从 device 拷回 host。命名约定上常用 `h_` 前缀表示 host 指针、`d_` 前缀表示 device 指针，就是为了时刻提醒"这个指针属于哪个世界"，混用是新手最常见的崩溃来源。

### 5.4.2 cudaMemcpy 与方向常量 H2D / D2H

在两个地址空间之间搬数据用 `cudaMemcpy(dst, src, bytes, kind)`，其中 `kind` 指明方向。把 host 数据送上 GPU 用 `cudaMemcpyHostToDevice`（简称 H2D）；把 GPU 结果取回 CPU 用 `cudaMemcpyDeviceToHost`（简称 D2H）；另有 `cudaMemcpyDeviceToDevice`（GPU 内两块）等。典型骨架：

```cuda
cudaMemcpy(d_A, h_A, n*sizeof(float), cudaMemcpyHostToDevice);  // H2D
cudaMemcpy(d_B, h_B, n*sizeof(float), cudaMemcpyHostToDevice);  // H2D
vecAdd<<<gridDim, blockDim>>>(d_A, d_B, d_C, n);
cudaMemcpy(h_C, d_C, n*sizeof(float), cudaMemcpyDeviceToHost);  // D2H
```

易错点集中在参数顺序和方向：`cudaMemcpy` 是 **(目的地, 来源, ...)** 的顺序（和 C 的 `memcpy` 一致，dst 在前），方向常量要和实际的 dst/src 匹配——把方向写反（如实际是 H2D 却填了 D2H）是极常见 bug，轻则数据不对、重则崩溃。此外 `cudaMemcpy`（默认版本）是**阻塞**的：host 会等到拷贝完成才继续，因此它天然充当了 kernel 之后的同步点（5.3.3 提到的隐式同步）。

### 5.4.3 PCIe 传输往往是瓶颈；pinned memory 的直觉

host 与 device 之间的数据要走 PCIe 总线（或 NVLink 等更快互连），其带宽远低于 GPU 访问自身显存的带宽，延迟也高。因此"H2D/D2H 拷贝"经常是整个程序的隐藏瓶颈：kernel 算得再快，如果每次都要把大数组搬来搬去，时间都耗在搬运上了。一个反复出现的优化原则是"数据尽量留在 GPU 上、减少往返"，以及尽量让传输与计算重叠（用 stream 异步拷贝，属性能篇）。

初学者常见的性能反直觉就在这里：一个在 CPU 上几毫秒的小任务搬到 GPU 反而更慢，往往不是 kernel 慢，而是 H2D+D2H 的固定开销盖过了收益——GPU 适合"数据量大、计算密集、少往返"的场景。相关概念还有 **pinned（page-locked）内存**：用 `cudaMallocHost` 分配的、不会被操作系统换页的 host 内存，能让 PCIe 传输更快并支持异步拷贝；普通 `malloc` 的可换页内存传输要慢一些。这些量化的带宽/延迟数值高度依赖具体硬件与互连，本报告不给硬编码数字（待核，且本机无 GPU 无法实测）。

### 5.4.4 统一内存 cudaMallocManaged 与按需迁移

统一内存（Unified Memory）是 CUDA 提供的简化模型：用 `cudaMallocManaged(&ptr, bytes)` 分配一块**托管内存**（managed memory），得到的**同一个指针 host 和 device 都能直接解引用**，无需手写 `cudaMemcpy`。运行时（driver）会在 CPU 或 GPU 访问某页数据时**按需自动迁移**该页到访问方。官方指南将其描述为"可从 CPU 或 GPU 访问的内存分配"，全局作用域可用 `__managed__` 修饰。统一内存自 CUDA 6.0（2014）引入。

统一内存把"数据在哪、要不要拷"这件事交给运行时自动打理，你只管用一个指针，大幅降低了写第一个 CUDA 程序的心智负担和"指针属于哪个世界"的混乱。用它改写向量加，可以省掉显式的 `cudaMalloc`/`cudaMemcpy`：分配 → 在 host 填数据 → 启动 kernel → `cudaDeviceSynchronize()` → 在 host 直接读结果。要点是访问前仍要正确同步（GPU 在算时 CPU 别去碰同一块数据，否则数据竞争）；早期用法建议 kernel 后 `cudaDeviceSynchronize` 再由 CPU 读。

### 5.4.5 统一内存 vs 显式拷贝的权衡

统一内存和显式 `cudaMemcpy` 不是谁淘汰谁，而是"易用 vs 可控"的权衡。统一内存代码更短、更好写，特别适合原型开发、指针关系复杂（含链式结构）、或超出显存需要自动换页的场景；但自动的按需页迁移可能在访问模式不友好时引入你看不见的迁移开销，性能不总是最优。显式拷贝写起来啰嗦、易错（方向/顺序），但你**完全掌控**数据何时、以多大粒度、在哪个 stream 上搬运，便于做传输-计算重叠等极致优化，追求峰值性能的库和内核往往仍用显式管理。

先用统一内存把逻辑写对、跑通，再在性能分析发现迁移成为瓶颈时改成显式管理（并可用 `cudaMemPrefetchAsync`、`cudaMemAdvise` 等给运行时迁移提示，作为两者之间的中间地带）。统一内存的具体行为（是否硬件页错误驱动、迁移粒度、各平台支持级别）与 GPU 算力代际和 CUDA 版本强相关，属快速演进区，锚 CUDA C++ Programming Guide Release 13.3（核实 2026-08-01），细节以对应版本文档为准；本机无 GPU，上述行为与开销均未实测。

#### 来源与时效
- CUDA C++ Programming Guide（Release 13.3，2026-06-25）：`cudaMalloc`/`cudaMemcpy` 与 `cudaMemcpyHostToDevice`/`cudaMemcpyDeviceToHost` 方向常量、host/device 分离地址空间；统一内存 `cudaMallocManaged`、`__managed__`、按需迁移与支持级别检测。https://docs.nvidia.com/cuda/cuda-programming-guide/01-introduction/programming-model.html 及 §Memory Management（核实 2026-08-01）
- PMPP 4th ed（2023）Ch2 异构数据并行：五步内存管理骨架（分配/拷贝/启动/回拷/释放）、H2D/D2H、device 指针不能在 host 解引用。TOC 见 5.1。
- CUDA C++ Best Practices Guide（Release 13.3，2026-06-25）：PCIe 传输为瓶颈、pinned（page-locked）内存加速传输、zero-copy 概念。https://docs.nvidia.com/cuda/cuda-c-best-practices-guide/ （核实 2026-08-01）
- 交叉核对：cudaMallocManaged 自 CUDA 6.0 起用于 CPU/GPU 共享的统一内存、需同步避免竞争——多来源一致。
- Berkeley CS267（Spring 2024）GPU 章：主机-设备数据传输与统一内存权衡。链接见 5.1。
- ⚙演进快项：统一内存按需迁移行为、各平台支持级别、prefetch/advise 语义——锚 CUDA C++ Programming Guide Release 13.3，随算力代际/版本变。
- 待核/未取：PCIe/NVLink 具体带宽与延迟数值（依赖硬件，未给硬编码）；统一内存与显式拷贝的实测性能对比（本机无 GPU·未验证·文档腿）。
