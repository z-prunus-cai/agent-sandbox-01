# L6-06·大主题9 数值库生态

> 基线 Py3.11.15/np2.4.6/gcc13.3 @2026-07-25 ｜核实日期：2026-08-01 ｜先修：大主题1 并行动机与架构分类、大主题2 性能模型（算术强度/Roofline）、大主题8 数据局部性（cache blocking）、L1-03 线性代数（矩阵乘、分解、特征值的基本定义）｜一手锚点：BLAS/LAPACK 官方参考（Netlib，reference LAPACK 3.12.1 @2025-01-08）、OpenBLAS 0.3.31（本机 numpy 底层，实测）、FFTW 3.3.11（2026-04-18）、PETSc 3.25.4（2026-03）、Berkeley CS267（Spring 2024，Demmel）数值库/工具箱部分、PMPP 4th ed（Kirk/Hwu/El Hajj）Appendix A Numerical considerations｜成熟度：BLAS/LAPACK/FFTW/迭代法核心 API 长期稳定 GA；GPU 数值库（cuBLAS/cuFFT/cuSPARSE）⚙演进快·锚 CUDA Toolkit 13.x（本机无 GPU·文档腿）

高性能计算里有一条几乎人人都会撞到的经验：**真正跑得快的代码，大部分算力都花在别人早已写好、调过无数遍的库里**，而不是你自己手写的循环。矩阵乘、解线性方程组、做傅里叶变换、解稀疏系统——这些底层算子被封装成一层层标准化的数值库，从最底的 BLAS，到建在其上的 LAPACK，到 FFT 专用库，到稀疏求解框架 PETSc。这一章讲清这套生态里每一层"是什么、解决什么、为什么快"，以及初学者最需要建立的一个判断力：**什么时候该站在库的肩膀上，什么时候才值得自己动手写 kernel**。

这里只讲编程与生态层面的心智模型，不深入具体分解算法的数学推导（那属线性代数课）、也不深挖某个库的调优内幕。本机 numpy/scipy 底层链接的是 OpenBLAS 0.3.31（`numpy.show_config()` 实测，线程层 pthreads、架构识别为 SkylakeX、编译期 DYNAMIC_ARCH），下文标「本机实证」处均为在此环境上 `python3` 的真实输出（4 核容器，`os.cpu_count()==4`）。GPU 数值库本机无设备、仅文档腿。

---

## 9.1 BLAS 三级（L1/L2/L3）与 LAPACK 稠密线代

### 9.1.1 BLAS 是接口规范，不是某个具体库

BLAS（Basic Linear Algebra Subprograms，基础线性代数子程序）是一份**接口规范**：它规定了一组做向量和矩阵基本运算的标准函数（名字、参数、语义），但不规定怎么实现。Netlib 上的 "reference BLAS" 是一份朴素的 Fortran 参考实现，正确但慢；真正上生产的是各家的高性能实现——OpenBLAS、Intel oneMKL、Arm Performance Libraries、Apple Accelerate 等，它们对同一套接口做了极致的体系结构优化（向量化、cache blocking、多线程）。

这条"规范与实现分账"的直觉非常重要：当你调 numpy 的 `A @ B`，numpy 自己并不算矩阵乘，它把活转交给底层链接的那个 BLAS 实现。所以同一段 Python 代码，链接 OpenBLAS 和链接 MKL 可能差好几倍速度，但**结果的数值语义相同**（都遵守同一份 BLAS 规范）。这也是为什么"换个 BLAS 后端"是 HPC 里常见的免费提速手段——你没改一行算法，只是换了实现。本机 numpy 链接的是 scipy-openblas64（OpenBLAS 0.3.31），可用 `numpy.show_config()` 查到。

### 9.1.2 三个级别 L1 / L2 / L3：按运算的数据维度分层

BLAS 按操作涉及的数据结构分成三级，级别越高，一次调用做的算术越多：

Level 1（向量-向量）：如 `axpy`（y ← αx + y）、`dot`（内积）、`nrm2`（范数）。数据量 O(n)，算术量 O(n)。

Level 2（矩阵-向量）：如 `gemv`（矩阵乘向量 y ← αAx + βy）、`ger`（秩一更新）。数据量 O(n²)，算术量 O(n²)。

Level 3（矩阵-矩阵）：如 `gemm`（通用矩阵乘 C ← αAB + βC）、`trsm`（三角求解）、`syrk`。数据量 O(n²)，算术量 O(n³)。

这个分级不是形式上的编号，而是直接对应大主题2 学过的**算术强度**（每字节访存能换来多少次浮点运算）。Level 1/2 每读一个数据元素只做常数次运算，属于典型的**内存带宽受限**（memory-bound）操作；Level 3 每 O(n²) 的数据能喂出 O(n³) 的运算，算术强度随 n 增长，才有机会变成**计算受限**（compute-bound），把处理器的浮点单元喂饱。初学者常见的误解是"矩阵越大越慢所以要避免大矩阵乘"——恰恰相反，大的 Level 3 操作是 BLAS 里效率最高的部分，因为只有它的算术强度足够高、能压住访存开销。

### 9.1.3 为什么 Level 3（尤其 GEMM）是整个生态的性能核心

Level 3 的 GEMM（general matrix multiply）之所以被称作 HPC 的"心脏"，是因为它是极少数能逼近硬件峰值浮点性能的操作，而上层大量算法都被有意改写成"以 GEMM 为主"的形式，好蹭它的高效率。

关键在算术强度。一个 n×n 矩阵乘做

2·n³ 次浮点运算

却只需搬运

约 3·n² 个矩阵元素

所以每个元素平均被复用 O(n) 次。库的实现通过 cache blocking（大主题8 的分块思想）把子块留在 cache/寄存器里反复使用，使得实际访存远低于朴素三重循环。本机实证 GEMM 的绝对性能与多线程强扩展：

```
$ python3  # numpy 2.4.6, OpenBLAS 0.3.31, 4-core
n=2000 双精度 A@B，用 threadpoolctl 限制 BLAS 线程数：
threads=1:  223.7 ms    71.5 GFLOP/s
threads=2:  112.0 ms   142.9 GFLOP/s
threads=4:   58.6 ms   272.8 GFLOP/s
```

从 1 线程到 4 线程近乎线性提速（71.5 → 272.8 GFLOP/s，效率约 95%），正是因为 GEMM 算术强度高、通信/同步开销占比小，属于最容易并行加速的负载。一个容易踩的坑：如果你在一个已经开了多线程 BLAS 的程序里，又自己用 Python 多进程去并行调用 numpy，两层并行会争抢同一批物理核（oversubscription），反而变慢——这时应把 BLAS 线程数压到 1（设 `OMP_NUM_THREADS`/`OPENBLAS_NUM_THREADS`）再让外层并行。

### 9.1.4 LAPACK：建在 BLAS 之上的稠密线代分解

LAPACK（Linear Algebra PACKage）是一层更高的库，提供**稠密矩阵**的分解与求解：LU 分解（解一般线性方程组）、Cholesky 分解（对称正定系统）、QR 分解（最小二乘）、SVD（奇异值分解）、特征值/特征向量。它自己不重造轮子，而是把计算尽量拆成对底层 BLAS（尤其 Level 3 GEMM）的调用——这就是所谓的 **blocked algorithm（分块算法）**：把一个大分解切成"对角小块做标量级分解 + 大块用 GEMM 更新"的形式，让绝大部分算力落到高效的 Level 3 上。

对初学者，记住这条依赖链就够了：

BLAS（算子）← LAPACK（分解/求解）← numpy.linalg / scipy.linalg（Python 封装）

当你调 `numpy.linalg.solve(A, b)` 解稠密方程组，实际是 LAPACK 的 `gesv`（做 LU 分解再回代），而 LAPACK 内部又落到 BLAS。所以 LAPACK 的速度天花板由底层 BLAS 决定，换 BLAS 后端同样能加速 LAPACK 例程。reference LAPACK 由 Netlib 维护，本次核实最新参考版为 3.12.1（2025-01-08 发布），但生产环境用的仍是各家 BLAS 厂商随附的优化版 LAPACK。一个常见混淆：LAPACK 处理**稠密**矩阵；如果你的矩阵是大而稀疏的（绝大多数元素为 0），用稠密 LAPACK 会浪费海量存储和算力，那属于 9.3 稀疏求解的范畴。

### 9.1.5 命名约定：一眼读懂 xGEMM 这类名字

BLAS/LAPACK 例程名遵循一套固定的字母编码，看懂它能极大降低查文档的成本。名字的首字母是**数据类型**：`s` 单精度实数、`d` 双精度实数、`c` 单精度复数、`z` 双精度复数。后面几个字母是**矩阵类型 + 操作**。

例如 `dgemm` = **d**ouble + **ge**neral matrix + **m**atrix **m**ultiply，即双精度一般矩阵乘；`sgemv` = 单精度一般矩阵乘向量；`dpotrf` = 双精度对称正定（**po**）矩阵的 Cholesky 分解（**trf** = triangular factorization）。

这套命名是 Fortran 时代 6 字符名长限制留下的遗产，初看像乱码，但拆开读就有规律。它的实用价值在于：当你在性能剖析（profiler）里看到程序 80% 的时间花在 `dgemm` 上，立刻就知道热点是双精度矩阵乘，优化方向应指向 Level 3 而非别处。

### 9.1.6 多线程 BLAS 与 numpy 的后端选择

现代 BLAS 实现（OpenBLAS、MKL）默认是**多线程**的：一次 GEMM 会自动把工作切给多个线程。numpy 本身不管线程，线程行为完全由底层 BLAS 决定，可通过环境变量控制（`OPENBLAS_NUM_THREADS`、`MKL_NUM_THREADS`，或统一的 `OMP_NUM_THREADS`），也可在 Python 里用 `threadpoolctl` 临时限制（9.1.3 的实证就用了它）。

**你装的 numpy 快不快，很大程度取决于它绑了哪个 BLAS**。pip 装的 numpy 官方 wheel 现在自带 OpenBLAS；conda 的 numpy 常可选 MKL；有些精简环境可能只有 reference BLAS，那会慢一个数量级。排查"为什么我的 numpy 矩阵乘特别慢"时，第一步就是 `numpy.show_config()` 看后端。本机确认后端为 OpenBLAS 0.3.31、pthreads 线程层。一个容易忽略的点：多线程 BLAS 在**小矩阵**上未必更快——线程启动/同步的固定开销可能盖过收益，小矩阵反而单线程更好，这也是为什么很多库对小规模会走单线程或专门的小核。

#### 来源与时效
- 一手规范/参考：BLAS 官方（Netlib，https://www.netlib.org/blas/ ）分级 L1/L2/L3 定义与命名约定；reference LAPACK（Netlib，https://www.netlib.org/lapack/ ）最新参考版 3.12.1 @2025-01-08（版本经联网核实 2026-08-01）。
- 一手实现：OpenBLAS 0.3.31.188.0（本机 numpy 2.4.6 底层，`numpy.show_config()` 实测，线程层 pthreads / DYNAMIC_ARCH / 识别 SkylakeX）。
- 副锚：Berkeley CS267（Spring 2024，Demmel）稠密线代 / 数值库部分（https://sites.google.com/lbl.gov/cs267-spr2024 ；存档 https://inst.eecs.berkeley.edu/~cs267/archives.html ）——BLAS 分级与算术强度、GEMM 为性能核心。
- 主锚参照：PMPP 4th ed Appendix A Numerical considerations（数值库与精度考量）。
- 本机实证：GEMM 双精度 n=2000 强扩展 1/2/4 线程 71.5/142.9/272.8 GFLOP/s（核实 2026-08-01）。
- ⚙演进快：Intel oneMKL 具体版本随 oneAPI 发行推进（本机未安装，版本此处不落具体号，标「待核」不编造）。

## 9.2 FFT 库

### 9.2.1 FFT 是什么，为什么它把 O(N²) 降到 O(N log N)

快速傅里叶变换（FFT，Fast Fourier Transform）是计算**离散傅里叶变换**（DFT）的高效算法。DFT 把一个长度 N 的信号从时域变到频域，其直接定义是每个输出都对所有输入做加权求和：

X[k] = Σ_{n=0}^{N-1} x[n] · e^(−2πi·kn/N)，  k = 0,…,N−1

直接按定义算，N 个输出各要 N 次乘加，总共

O(N²) 次运算

FFT 利用 e 指数的对称性与周期性，把问题递归地分成两半（经典的 Cooley-Tukey 分治），把复杂度降到

O(N log N)

这就是为什么 FFT 被称作 20 世纪最重要的算法之一——当 N = 100 万时，N² 是万亿级，而 N·log N 只有两千万级，差了约五万倍。本机验证 FFT 输出与按定义手算的 DFT 一致：

```
$ python3  # scipy 1.17.1
x = 长度8随机向量
scipy.fft.fft(x) 与 手写 DFT 矩阵 e^(-2πi kn/8) @ x
最大绝对误差: 3.03e-15   （仅浮点舍入级别，两者等价）
scipy.fft.fft, N=65536: 1.79 ms
```

误差在 1e-15 量级（双精度机器 epsilon 附近），证明 FFT 算的就是 DFT、只是快得多。

### 9.2.2 FFTW：plan / execute 两段式模型与 wisdom

FFTW（"Fastest Fourier Transform in the West"）是最著名的开源 FFT 库，它的设计有个初学者必须理解的特点：**先规划、再执行**。你先对某个尺寸、某种变换调 `fftw_plan_dft(...)` 生成一个 **plan**，FFTW 会在这一步实际测量、搜索出对当前硬件最优的分解方式；之后对同尺寸数据反复调 `fftw_execute(plan)`，享受已优化好的执行路径。

这套两段式的直觉是"一次规划、多次复用"：规划本身有开销（甚至会试跑多种算法），只有当同一尺寸变换要做很多次时才划算。FFTW 还能把规划结果导出成 **wisdom**（一段可存盘、可复用的调优知识），下次跳过搜索直接用。本次核实 FFTW 最新版为 3.3.11（2026-04-18，新增 `fftw_copy_plan()`、SVE/LoongArch SIMD 支持）。一个容易踩的坑：如果你每次变换都重新 `plan` 且用了 `FFTW_MEASURE`（充分搜索）级别，规划开销可能远超变换本身——只做一两次变换时应改用 `FFTW_ESTIMATE`（快速估计、不试跑）。

### 9.2.3 numpy.fft 与 scipy.fft 的接口和后端

日常 Python 里做 FFT，用 `numpy.fft` 或 `scipy.fft` 即可，接口几乎一致：`fft`/`ifft`（复数正逆变换）、`rfft`/`irfft`（实数输入专用，省一半计算和存储）、`fft2`/`fftn`（多维）。两者底层现在都用 **pocketfft**（一个 C++ 实现，非 FFTW，许可证更宽松），不需要你手动 plan——库内部替你管理。scipy.fft 通常被推荐优先于 numpy.fft，因为它接口更完整（如支持多线程 `workers` 参数、DCT/DST 等）且默认走 pocketfft 的更快路径。

对初学者，记住选择顺序：先用 `scipy.fft`，接口够用且不必操心 plan；只有当你在 C/C++ 里追求极致性能、且要对同尺寸做海量变换时，才值得上 FFTW 手动规划。一个常见误区是以为 numpy/scipy 底层是 FFTW——早期 numpy 曾有 FFTPACK，现在是 pokectfft，两者与 FFTW 是不同实现，性能量级相近但 API 与许可证不同。

### 9.2.4 归一化约定与实数变换：两个最常见的坑

FFT 里最坑初学者的不是性能，而是**归一化约定**。正变换和逆变换里那个 1/N 因子放在哪一边，各库/各文档不统一：numpy/scipy 默认把 1/N 全放在**逆变换**（`ifft`）上，正变换不除 N；也提供 `norm='ortho'`（正逆各乘 1/√N，保持能量对称）等选项。如果你比对两个来源的频谱幅值发现差了 N 倍或 √N 倍，八成就是归一化约定不同，不是算错了。

第二个坑是实数信号该用 `rfft` 而非 `fft`。实数序列的 DFT 具有共轭对称性（后一半频率是前一半的复共轭），所以 `rfft` 只返回前 N/2+1 个独立的频率分量，省一半存储与计算。初学者常对实信号用完整 `fft`、再纳闷"为什么频谱左右对称还多算了一倍"——那对称的另一半是冗余，本就该用 `rfft` 避开。

### 9.2.5 GPU FFT：cuFFT（本机无 GPU·文档腿）

在 GPU 上，NVIDIA 提供 cuFFT 作为 CUDA 生态里的 FFT 库，接口思路与 FFTW 类似（也是 plan + execute），是深度学习、信号处理、科学计算在 GPU 上做频域运算的标准组件。它随 CUDA Toolkit 一起发行。

本主题涉及 GPU 库处一律标 ⚙演进快·锚 CUDA Toolkit 13.x，且本机无 GPU 设备、无法实测，仅文档腿。初学者只需建立一个映射直觉：CPU 上的 FFTW/pocketfft ↔ GPU 上的 cuFFT，两者算的都是同一个 DFT，差别在执行硬件与规划细节；具体 API 语法与性能数值本报告不凭记忆编造，需以对应版本官方文档为准（标「待核」）。

#### 来源与时效
- 一手文档：FFTW 官方（https://www.fftw.org/ ），plan/execute 模型、wisdom、`FFTW_MEASURE`/`FFTW_ESTIMATE`；最新版 3.3.11 @2026-04-18（版本经联网核实 2026-08-01，含 SVE/LoongArch SIMD、`fftw_copy_plan()`）。
- 一手实现：scipy.fft / numpy.fft 官方文档（后端 pocketfft；归一化 `norm` 约定、`rfft` 实数变换、`workers` 多线程）；本机 scipy 1.17.1 / numpy 2.4.6。
- 副锚：Berkeley CS267（Spring 2024）FFT/谱方法部分（复杂度 O(N log N)、Cooley-Tukey 分治直觉）。
- 本机实证：scipy.fft 与手写 DFT 矩阵最大绝对误差 3.03e-15、N=65536 用时 1.79 ms（核实 2026-08-01）。
- ⚙演进快·锚版本·随时变：GPU cuFFT 随 CUDA Toolkit 13.x（本机无 GPU·文档腿，具体版本/API 标「待核」，核实 2026-08-01）。

## 9.3 稀疏求解（PETSc / 迭代法直觉）

### 9.3.1 稀疏矩阵与存储格式（CSR / CSC / COO）

很多实际问题（离散化的偏微分方程、图、网络）产生的矩阵是**稀疏**的：绝大多数元素为 0，非零元只占极小比例。用稠密数组存一个 n×n 稀疏矩阵要 O(n²) 内存，纯属浪费——稀疏存储格式只记录非零元及其位置，把存储降到 O(nnz)（nnz = 非零元个数）。

最常用的格式是 CSR（Compressed Sparse Row，压缩稀疏行）：用三个数组存——非零值、对应列号、每行起始位置的指针。CSC 是列压缩版（三数组按列组织）。COO（Coordinate，坐标格式）最直白，直接存 (行, 列, 值) 三元组，便于构造但不便于运算。

对初学者，直觉是"只记非零元、外加它们在哪"。CSR 之所以流行，是因为它让"矩阵乘向量"（SpMV，大主题7 讲过的核心稀疏算子）能高效地逐行遍历非零元。一个容易踩的坑：反复往一个 CSR/CSC 矩阵里插入新元素很慢（要移动数组），构造阶段应先用 COO 或 LIL 格式攒好、最后 `.tocsr()` 一次转换——scipy.sparse 里就是这么建议的。

### 9.3.2 直接法 vs 迭代法：解稀疏线性系统的两条路

解线性方程组 Ax = b，稠密情形通常用直接法（LU/Cholesky 分解，9.1.4）；但对大规模稀疏系统，两条路各有代价：

直接法（稀疏 LU/Cholesky）：分解精确、一次求解，但分解过程会产生 **fill-in**（原本为 0 的位置在分解中变成非零），可能让稀疏矩阵迅速变稠，吃光内存。适合中等规模或结构良好的矩阵。

迭代法（CG、GMRES 等）：不分解矩阵，从一个初始猜测出发反复逼近真解，每步只需做矩阵乘向量（SpMV），内存友好、天然适合超大规模和并行。代价是收敛速度依赖矩阵性质，可能需要很多步、甚至不收敛。

**矩阵大到稠密分解装不下、或本就来自网格/图**时，走迭代法；矩阵不太大、要高精度一次性解、又能容忍 fill-in 时，走直接法。现实中大型科学计算几乎清一色迭代法 + 预条件。

### 9.3.3 迭代法直觉：Krylov 子空间与 CG / GMRES

主流迭代解法属于 **Krylov 子空间方法**。直觉是：既然每步只能便宜地做"矩阵乘向量"，那就用 A、Ab、A²b、… 这些向量张成的空间（Krylov 子空间）里找 Ax=b 的最佳近似解，每迭代一步把这个搜索空间扩大一维。

CG（Conjugate Gradient，共轭梯度法）用于**对称正定**矩阵，收敛快、存储省，是离散化椭圆型 PDE（如泊松方程）的首选；GMRES（Generalized Minimal RESidual）用于**一般非对称**矩阵，更通用但每步存储和计算随迭代数增长（常需 restart 控制）。本机实证 CG 解一个 1D 泊松三对角对称正定系统：

```
$ python3  # scipy 1.17.1
A = 2000x2000 三对角 [-1, 2, -1]（对称正定），b = 全 1
x, info = scipy.sparse.linalg.cg(A, b, rtol=1e-8)
info = 0   （0 表示成功收敛）
||A x - b|| = 0.0   （残差降到机器精度）
```

CG 成功把残差压到 0（本例结构规整、收敛极快）。初学者要建立的核心直觉：迭代法把"解方程"变成"反复做 SpMV 逼近"，所以它的性能和可并行性直接继承自 SpMV——这正是大主题7 的稀疏矩阵模式在数值库里的落点。

### 9.3.4 预条件（preconditioning）：让迭代法真正能用的关键

迭代法能不能在可接受步数内收敛，取决于矩阵的**条件数**（大致衡量矩阵"病态"程度）。条件数越大，CG/GMRES 收敛越慢甚至停滞。**预条件**（preconditioning）就是找一个容易求逆的近似矩阵 M（预条件子），把原系统 Ax=b 变换成一个条件数更好的等价系统再迭代求解，直觉上等价于求解

M⁻¹ A x = M⁻¹ b

其中 M ≈ A 但 M⁻¹ 好算。常见预条件子有 Jacobi（对角）、不完全 LU/Cholesky（ILU/IC，只做部分 fill-in 的近似分解）、多重网格（multigrid）等。

对初学者，记住一句话：**没有好预条件子的迭代法在真实大问题上基本不能用**——教科书上"CG 一步到位"的例子（如本机那个规整的三对角）是理想情形，真实病态矩阵往往靠预条件把上千步压到几十步。预条件的选择是稀疏求解里最需要经验、也是各求解框架（PETSc/hypre）真正的价值所在。

### 9.3.5 PETSc 与稀疏求解框架生态

PETSc（Portable, Extensible Toolkit for Scientific Computation，读作"pet-see"）是解大规模稀疏线性/非线性系统的标准框架，尤其面向**分布式内存**（跑在 MPI 之上）的超大问题。它把上面这些概念封装成可组合的模块：KSP（Krylov 求解器，选 CG/GMRES/…）、PC（预条件子，选 Jacobi/ILU/multigrid/…）、以及分布式稀疏矩阵/向量数据结构；你通过组合"哪个 Krylov 法 + 哪个预条件子"来搭一个求解器，甚至能在命令行运行时切换而不改代码。

小规模稀疏系统用 scipy.sparse.linalg（本地、单机、够用）；跨多节点的大规模科学计算用 PETSc（MPI 分布式、模块可组合），同类框架还有 Trilinos、hypre（后者常作为 PETSc 的多重网格预条件后端）。本次核实 PETSc 最新版为 3.25.4（2025 系列，2026-03 前后）。一个常见误解是把 PETSc 当成"又一个更快的 numpy"——不是，它的价值不在单机小矩阵，而在把迭代法 + 预条件 + 分布式并行整合成能解上亿未知量系统的框架；单机小问题用它反而是杀鸡用牛刀。

#### 来源与时效
- 一手实现/文档：scipy.sparse 与 scipy.sparse.linalg 官方文档（CSR/CSC/COO 格式、`cg`/`gmres` 接口、构造建议 COO→CSR）；本机 scipy 1.17.1。
- 一手文档：PETSc 官方（https://petsc.org/ ），KSP/PC 组合、分布式稀疏求解；最新版 3.25.4（版本经联网核实 2026-08-01，2026 年 3 月 3.25 系列）。
- 副锚：Berkeley CS267（Spring 2024）稀疏线代 / 迭代法 / 预条件与多重网格部分；直接法 fill-in、Krylov 子空间、CG（SPD）vs GMRES（一般）、预条件对收敛的作用。
- 主锚参照：PMPP 4th ed Ch14 Sparse matrix computation（CSR/SpMV 的并行落点）、Appendix A。
- 本机实证：scipy.sparse.linalg.cg 解 2000×2000 三对角 SPD 系统，info=0 收敛、残差 0.0（核实 2026-08-01）。
- 相关框架（生态定位，非承重深挖）：Trilinos、hypre（多重网格预条件，常作 PETSc 后端）——仅点名归入生态。

## 9.4 「站在优化库肩上」vs 手写 kernel 的权衡

### 9.4.1 为什么默认应该用库：性能、正确性、可移植三重收益

HPC 里的第一原则是"能用库就别自己写"。原因有三层，且都超出初学者的直觉预期：

性能——库背后是数十年、专家级的体系结构调优（向量化、cache blocking、多线程、针对具体 CPU 微架构的手工汇编 kernel），一个新手手写的循环几乎不可能接近。本机实证这个差距的量级：

```
$ python3  # numpy 2.4.6 / OpenBLAS
n=200 双精度矩阵乘：
  BLAS  A@B :  约 0.9 ms（稳定态）
  手写三重 Python for 循环 : 2248 ms
  ≈ 200x 以上 差距
```

正确性——数值库对精度、溢出、退化输入（NaN、奇异矩阵）等边界做了大量处理，手写代码极易在这些角落出错却难以察觉。

可移植性——同一份调库代码，换到新硬件只需换/升级底层库（如换 BLAS 后端、升级 CUDA），无需重写算法。

所以初学者应把"调用 numpy/scipy/BLAS/LAPACK/FFTW"当默认动作，把"手写 kernel"当需要特别理由才启动的例外。

### 9.4.2 库也有慢的时候：何时手写 kernel 才划算

库不是万能的，有几类情形手写（或用更底层工具）确实能赢：

算子融合（kernel fusion）——如果你的计算是"一连串小操作接力"（比如 `A@B` 之后立刻逐元素加、再取 relu），每步调库都要把中间结果写回内存再读出，内存往返成为瓶颈；手写一个融合 kernel 把多步合并、中间量留在寄存器/cache 里，能省掉往返。这正是深度学习编译器（如各种 fused kernel、Triton）存在的理由。

特殊结构——你的矩阵有库不知道的特殊结构（带状、块对角、特定稀疏模式），通用库会浪费算力在无意义的零上，针对结构手写能大幅省算。

规模太小——库的固定调用开销（线程启动、参数检查）在极小规模上可能盖过收益，一个内联的小循环反而快（呼应 9.1.6 小矩阵单线程更好）。

先用库、用 profiler 找到真正的热点，只在"热点确实卡在库覆盖不到的模式上、且收益可观"时才手写——过早手写优化通常是白费力气还引入 bug。

### 9.4.3 权衡的本质：算术强度与内存往返，不是"手写就快"

把上面串起来，这个权衡的底层逻辑仍是大主题2 的算术强度和大主题8 的数据局部性。库赢在**单个大算子**（如一次大 GEMM）——它的算术强度高、库已把访存压到极限，你手写不可能更好。手写赢在**跨算子的数据流**——当多个算子之间有大量可复用的中间数据、而分别调库会强制它们经由内存往返时，融合能提升整体的算术强度。

所以正确的心智模型不是"库 vs 手写谁快"，而是"**在算子内部信库，在算子之间管好数据流**"。初学者最常犯的错是反过来：花大力气手写一个矩阵乘 kernel（注定打不过 BLAS），却对"把十个 numpy 操作串成一长串、每步都在内存里进进出出"这种真正的浪费视而不见。真正的高手优化，往往是重排算法让更多工作落进一次大 Level 3 调用，或减少库调用之间的数据搬运，而不是去和库比手写单个算子。

### 9.4.4 GPU 数值库生态：同一套权衡的加速器版本（本机无 GPU·文档腿）

在 GPU 上，这套"站在库肩上"的生态有一一对应的加速器版本，都随 CUDA Toolkit 发行：cuBLAS（对应 BLAS）、cuSOLVER（对应 LAPACK 的稠密分解）、cuFFT（对应 FFTW）、cuSPARSE（稀疏矩阵运算）、以及分布式的 cuSOLVERMp 等。深度学习框架底层的矩阵乘、卷积也大量落到 cuBLAS/cuDNN 这类库上。

权衡逻辑与 CPU 完全同构：GPU 上单个大算子信 cuBLAS/cuFFT（它们逼近硬件峰值，手写 CUDA kernel 极难超越），而跨算子融合、特殊结构才是自己写 kernel 的战场（大主题5/6 的 CUDA 编程正是为这类情形准备的）。本主题 GPU 库处一律标 ⚙演进快·锚 CUDA Toolkit 13.x，本机无 GPU 设备、无法实测，仅文档腿；具体版本号、API 语法与性能数值不凭记忆编造，以对应版本官方文档为准（标「待核」）。

#### 来源与时效
- 主锚：PMPP 4th ed Appendix A Numerical considerations、Ch14 Sparse matrix（"用优化库 vs 手写 kernel"的权衡、算子融合直觉）。
- 副锚：Berkeley CS267（Spring 2024）"站在库肩上" / autotuning / 何时手写的讨论。
- 一手实现：numpy 2.4.6 + OpenBLAS 0.3.31（本机 BLAS vs 手写循环对比的实现基座）。
- 本机实证：n=200 双精度矩阵乘，BLAS 约 0.9 ms（稳定态）vs 手写三重 Python 循环 2248 ms，差距 ≈200x 以上（核实 2026-08-01；手写为纯 Python 循环，无向量化，量级示意）。
- ⚙演进快·锚版本·随时变：GPU 数值库 cuBLAS/cuSOLVER/cuFFT/cuSPARSE 随 CUDA Toolkit 13.x（本机无 GPU·文档腿，具体版本/API 标「待核」，核实 2026-08-01）。
- 跨课分账：↔ L1-03 线代（并行数值线代算法本身）；↔ 大主题5/6 CUDA（手写 GPU kernel 的编程模型）。
