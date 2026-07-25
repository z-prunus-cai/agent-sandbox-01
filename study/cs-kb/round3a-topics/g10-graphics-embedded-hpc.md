# Round 3a · 第 10 组「图形 / 嵌入式 / HPC」· 大主题分解

> 基线 Py3.11.15/np2.4.6/gcc13.3 @2026-07-25 ｜ 调查员：第 10 组（G10）
> 目标：把组内每门课拆成**查全的大主题清单**（教学单元级、覆盖全、少重叠、按教学序），锚定权威教材目录 / 官方 syllabus。
> 上游：`round1-map.md`（L5-03 / L5-15 / L6-06 三课定位）、`round2-partials/g10-graphics-embedded-hpc.md`（定方向 + 本机能力边界）。
> 纪律：优先一手目录；不编造；具体平台/RTOS/GPU 框架**硬标「锚版本、演进快」**；时效核实 2026-07-25。
> 每主题行格式：`编号 | 大主题 | 一句话范围 | 来源锚点 | 跨课重叠提示`。

---

## L5-03 计算机图形学

### 锚定权威目录
- **主锚**：CMU **15-462/662 Computer Graphics**，**Spring 2024**（Nancy Pollard），官方 Lectures & Readings 页 —— 教学序 lecture 清单最细。
  URL：http://15462.courses.cs.cmu.edu/spring2024/lectures ；课程主页 http://15462.courses.cs.cmu.edu/spring2024/ 。**核实 2026-07-25**（Spring 2024 为该课当前公开完整学期版；主站 `www.cs.cmu.edu/~15462/` 302 跳转到最新学期存档）。
- **副锚（教材目录）**：*Fundamentals of Computer Graphics*（Marschner & Shirley，主流本科教材）作章节骨架；*Real-Time Rendering, 4th ed.*（实时渲染管线/着色）；*PBRT*（物理渲染、在线开放）作光线/蒙卡渲染章节锚。
- **规范锚（可编程管线）**：OpenGL 4.6 Core Profile（末次 spec 更新 2022-05-05，Khronos，**已冻结/最终版**）、Vulkan 1.4（2024-12，当前活跃线）。核实 2026-07-25。
- CMU 15-462 官方课程描述给出的覆盖面（作查全对照）：sampling/aliasing/interpolation、rasterization、geometric transformations、parameterization、visibility、compositing、filtering/convolution、curves & surfaces、geometric data structures、subdivision、meshing、spatial hierarchies、ray tracing、radiometry、reflectance、light fields、geometric optics、Monte Carlo rendering、importance sampling、camera models、high-performance ray tracing、differential equations / time integration / numerical differentiation、physically-based animation、optimization、numerical linear algebra、inverse kinematics、Fourier methods、data fitting。

### 大主题清单（10）

| 编号 | 大主题 | 一句话范围 | 来源锚点 | 跨课重叠提示 |
|------|--------|-----------|----------|--------------|
| G-01 | 图形管线概览与数学预备 | 何为渲染、实时 vs 离线、图形管线各阶段总览；向量/线性映射/内积/正交基复习 | 15-462 Intro + Linear Algebra/Vector Calculus Review lecture | ← L1-03 线代、L1-04 微积分（图形是二者最直观落地） |
| G-02 | 几何变换与投影管线 | 齐次坐标、model→world→view→clip→NDC→screen、透视除法、视口变换、法线用逆转置矩阵变换 | 15-462 Perspective Projection；*Fund. of CG* 变换/视图章 | ↔ L6-06（顶点变换是数据并行；GPU 顶点阶段）；← L1-03 |
| G-03 | 光栅化与可见性 | 三角形遍历、重心坐标插值、深度缓冲 Z-buffer、透视校正插值、覆盖测试 | 15-462 Rasterization / Visibility | ↔ L6-06（光栅化高度并行、固定管线友好）；← L5-05 微架构（勿深入） |
| G-04 | 采样、走样与信号处理 | 采样定理、混叠、滤波/卷积、抗锯齿（MSAA/超采样）、Alpha 合成 | 15-462 Sampling & Aliasing / Filtering, Convolution / Compositing | ↔ L1-04 微积分（卷积/傅里叶）；↔ Fourier methods |
| G-05 | 纹理映射与着色/光照模型 | 纹理坐标与采样、mipmap、Phong/Blinn-Phong、法线/环境贴图、材质基础 | 15-462 Texture Mapping；*Real-Time Rendering* 着色章 | ↔ G-09（反射的经验 vs 物理模型）；↔ L6-06（片元着色器并行） |
| G-06 | 可编程管线与实时渲染 API | 顶点/片元着色器阶段、GPU 固定 vs 可编程分工、OpenGL/Vulkan 状态机与提交模型 | OpenGL 4.6 / Vulkan 1.4 规范；*Real-Time Rendering* | ↔ **L6-06 GPU 编程（同一硬件的图形视角 vs 通用计算视角）** |
| G-07 | 几何表示：曲线曲面/网格/细分/空间数据结构 | 参数曲线曲面、多边形网格与数据结构、细分曲面、网格化、空间层次（BVH/kd-tree/八叉树） | 15-462 Curves & Surfaces / Meshing / Subdivision / Spatial Hierarchies | ↔ G-08（空间层次也是光追加速结构）；↔ L3-01 算法（树/几何 DS） |
| G-08 | 光线追踪与加速结构 | 光线-图元求交、BVH 构建与遍历、高性能光追、几何光学与光场直觉 | 15-462 Ray Tracing / High-Performance Ray Tracing；*PBRT* | ↔ G-07（空间层次）；↔ L6-06（RT 并行/RT Core，硬件演进快） |
| G-09 | 辐射度量、反射与蒙特卡洛渲染 | 辐射度量量纲、BRDF/反射模型、渲染方程、蒙特卡洛积分、重要性采样、路径追踪、相机模型 | 15-462 Radiometry / Reflectance / Monte Carlo Rendering / Importance Sampling / Camera Models；*PBRT* | ↔ **L2-03 概率**（蒙卡估计/方差/重要性采样）；↔ L1-04 积分 |
| G-10 | 物理动画与数值方法 | 微分方程与时间积分、数值微分、基于物理的动画、优化、数值线代、逆运动学、数据拟合 | 15-462 Physically-Based Animation / Optimization / Numerical Linear Algebra / Inverse Kinematics | ↔ L1-04 微积分（ODE/积分器）；↔ L1-03 数值线代；↔ L6-06（大规模仿真并行） |

**建议大主题数：K = 10**

---

## L5-15 嵌入式系统

### 锚定权威目录
- **主锚（顶校实操课 + 平台）**：UT Austin **Valvano** 嵌入式序列 —— EE319K《Introduction to Embedded Systems》与 EE445L《Embedded Systems Design Lab》，教材 *Embedded Systems: Introduction to / Real-Time Interfacing to ARM Cortex-M Microcontrollers*，参考板 **TI EK-TM4C123GXL LaunchPad（Cortex-M4）**；自底向上覆盖 GPIO/开关-LED → 中断 → ADC/DAC → 通信 → 显示。
  URL：http://users.ece.utexas.edu/~valvano/Volume1/IntroToEmbSys/ ；http://users.ece.utexas.edu/~valvano/arm/outline1.htm ；EE445L Spring 2024 syllabus http://users.ece.utexas.edu/~valvano/EE445L/EE445L_SPRING_2024.htm 。**核实 2026-07-25**。
- **副锚（学术教材 · 建模/分析/CPS）**：**Lee & Seshia,《Introduction to Embedded Systems — A Cyber-Physical Systems Approach》第 2 版**（MIT Press 2017；免费 PDF v2.3）。三部结构：**Part I Modeling Dynamic Behaviors**（连续动力学/离散动力学/混合系统/状态机/并发模型）、**Part II Design of Embedded Systems**（Ch7 传感器与执行器 / Ch8 嵌入式处理器 / Ch9 存储架构 / Ch10 输入输出 / Ch11 多任务 / Ch12 调度）、**Part III Analysis & Verification**（Ch13 不变式与时序逻辑 / Ch14 等价与精化 / Ch15 可达性分析与模型检验 / Ch16 定量分析 / Ch17 安全与隐私）、附录 A 集合与函数、B 复杂性与可计算性。
  URL：https://ptolemy.berkeley.edu/books/leeseshia/ ；PDF：https://ptolemy.berkeley.edu/books/leeseshia/releases/LeeSeshia_DigitalV2_3.pdf 。**核实 2026-07-25**。
- **平台/RTOS 锚（🔴硬标：锚版本、演进快）**：架构手册 **ARM Cortex-M Generic User Guide / ARMv7-M / ARMv8-M Architecture Reference Manual**（ARM 官方）；RTOS 建议锚 **FreeRTOS 202604 LTS（kernel v11.3.0，2026-04）** 或 **Zephyr 4.4（2026-04，半年节奏）**。具体 SoC/工具链/RTOS 版本演进快，报告须锚一个参考平台并显式标「以某平台为锚，其它平台细节可能不同」。核实 2026-07-25。

### 大主题清单（12）

| 编号 | 大主题 | 一句话范围 | 来源锚点 | 跨课重叠提示 |
|------|--------|-----------|----------|--------------|
| E-01 | 嵌入式/CPS 概念与设计约束 | 何为嵌入式/反应式/实时系统、CPS 计算-物理耦合、功耗/成本/RAM-Flash/确定性等设计度量 | Lee-Seshia Ch1；Valvano 导论 | — （本组独有的"约束驱动"视角） |
| E-02 | 处理器与内存架构 | 嵌入式处理器、Cortex-M 寄存器/流水线、内存映射与存储架构（Flash/SRAM）、无 MMU/无缓存特性 | Lee-Seshia Ch8-9；ARM Cortex-M 手册 | 🔴锚 Cortex-M；← L3-02 组成、L3-03 汇编（ARM ISA 子集）；≠ L4-01（常无 MMU） |
| E-03 | 裸机执行模型、启动与工具链 | 复位/startup、链接脚本与段布局、内存映射 I/O、`volatile` 语义、交叉编译/烧录/调试 | Valvano（汇编+C 开发流程）；ARM 手册 | ← L3-03 汇编、L4-01 OS 启动；🔴工具链演进快 |
| E-04 | GPIO 与数字 I/O | 端口配置、方向/上下拉、开关-LED 驱动、位带/位段操作 | Valvano EE319K（switches/LEDs 起步） | ↔ E-05（GPIO 常触发中断） |
| E-05 | 中断与实时响应 | 中断向量表、NVIC、ISR、优先级/嵌套/尾链、中断延迟；轮询 vs 中断 vs DMA 三种 I/O 范式 | Lee-Seshia Ch10；ARM Cortex-M NVIC | ↔ E-09 DMA；↔ L4-01 中断处理；🔴NVIC 细节锚 Cortex-M |
| E-06 | 定时器、PWM 与时钟系统 | 系统时钟树、SysTick、通用定时器、输入捕获/输出比较、PWM 生成 | Valvano（定时器/PWM 章）；SoC reference manual | ↔ E-10 RTOS 时基（tick 来自定时器） |
| E-07 | 模拟接口：ADC/DAC | 采样量化、ADC 转换与触发、DAC 输出、采样率/分辨率、模拟前端概念 | Valvano（ADC/DAC 桥接概念） | ↔ L2-03 采样统计（弱）；EE 交叉（点到为止） |
| E-08 | 串行通信总线 | UART、SPI、I²C、CAN、USB 的帧格式、时序、主从/仲裁与驱动 | Valvano（通信章）；各总线规范 | ↔ L4-02 网络（协议分层类比，尺度不同） |
| E-09 | DMA 与高效数据搬运 | DMA 控制器、通道/触发、内存↔外设搬运、省 CPU 的数据流、双缓冲/乒乓 | Lee-Seshia Ch10 I/O；SoC reference manual | ↔ E-05（I/O 范式对比）；🔴DMA 引擎细节锚平台 |
| E-10 | RTOS、多任务与实时调度 | 任务/线程模型、抢占式优先级调度、RMS/EDF、上下文切换、信号量/互斥、优先级反转+继承、WCET | Lee-Seshia Ch11-12；FreeRTOS/Zephyr 文档 | 🔴锚 FreeRTOS 202604 / Zephyr 4.4，演进快；≠ L4-01 通用调度（此处强实时） |
| E-11 | 建模、分析与形式验证（CPS） | 连续/离散/混合动力学、有限状态机与并发模型、不变式与时序逻辑、可达性/模型检验、定量分析 | Lee-Seshia Part I & III（Ch2-6, 13-16） | ↔ L3-05 自动机、L6-02 形式验证/模型检验（同工具，嵌入式语境） |
| E-12 | 低功耗与可靠性设计权衡 | 睡眠/低功耗模式、功耗预算(µA)、RAM/Flash 预算、确定性 vs 吞吐、看门狗/安全隐私 | Lee-Seshia Ch16-17；SoC 电源手册 | ↔ E-01（约束）；↔ L5-04 安全（嵌入式安全子集） |

**建议大主题数：K = 12**

---

## L6-06 高性能计算（并入 GPU 编程）

### 锚定权威目录
- **主锚（GPU/CUDA 教材目录）**：**Kirk, Hwu, El Hajj,《Programming Massively Parallel Processors — A Hands-on Approach》第 4 版**（Elsevier/Morgan Kaufmann）。四部完整 TOC（已逐条核实）：
  - **Part I Fundamental Concepts**：Ch1 Introduction、Ch2 Heterogeneous data parallel computing、Ch3 Multidimensional grids and data、Ch4 Compute architecture and scheduling、Ch5 Memory architecture and data locality、Ch6 Performance considerations。
  - **Part II Parallel Patterns**：Ch7 Convolution、Ch8 Stencil、Ch9 Parallel histogram（atomics/privatization）、Ch10 Reduction（minimizing divergence）、Ch11 Prefix sum (scan)、Ch12 Merge。
  - **Part III Advanced Patterns & Applications**：Ch13 Sorting、Ch14 Sparse matrix、Ch15 Graph traversal、Ch16 Deep learning、Ch17 Iterative MRI reconstruction、Ch18 Electrostatic potential map、Ch19 Parallel programming & computational thinking。
  - **Part IV Advanced Practices**：Ch20 Heterogeneous computing cluster (CUDA streams)、Ch21 CUDA dynamic parallelism、Ch22 Advanced practices & future evolution、Ch23 Conclusion & outlook；Appendix A Numerical considerations。
  URL：https://www.oreilly.com/library/view/programming-massively-parallel/9780323984638/xhtml/Contents.xhtml 。**核实 2026-07-25**。（PMPP 偏 GPU/CUDA，缺分布式内存/MPI 与 OpenMP，故加 CS267 补齐。）
- **副锚（HPC 课大纲 · 含 MPI/OpenMP/CUDA）**：UC Berkeley **CS267 Applications of Parallel Computers**，**Spring 2024**（Demmel 等）。覆盖并行架构空间、共享内存与分布式内存编程、GPU、云平台、并行语言/编译器/库；并行算法覆盖线代、粒子(N-body)、网格(PDE)、排序、FFT、图、机器学习；数据划分、同步与负载均衡。
  URL：https://sites.google.com/lbl.gov/cs267-spr2024 ；存档索引 https://inst.eecs.berkeley.edu/~cs267/archives.html 。**核实 2026-07-25**。
- **规范/框架锚（🔴硬标：锚版本、演进快）**：**MPI Standard 5.0**（MPI Forum 2025-06-05 通过，要点=标准化 ABI；官方 PDF 为唯一权威版）；**OpenMP API 6.0**（2024-11 定稿；TR14=6.1 预览 2025-11）；**CUDA C++ Programming Guide / CUDA Toolkit 13.x**（13.2.2 与 13.3 Update 1 于 2026 年中）。CUDA **演进快**，报告须锚具体 Toolkit 版本。核实 2026-07-25。
- **本机能力边界（沿用 round2 §0）**：numpy/scipy + 多线程 BLAS 可实证（4 核）；**MPI 需现装 openmpi+mpi4py（非持久）**；**CUDA/GPU 本机无设备，无法实证——硬标「未验证·无设备」，不得凭记忆编造 kernel 输出**。

### 大主题清单（10）

| 编号 | 大主题 | 一句话范围 | 来源锚点 | 跨课重叠提示 |
|------|--------|-----------|----------|--------------|
| H-01 | 并行计算动机与架构分类 | 为何并行、Flynn 分类、共享 vs 分布式内存、多核/众核/集群/异构、内存墙 | PMPP Ch1；CS267 架构综述 | ↔ L5-05 体系结构（此处编程视角，非微架构细节） |
| H-02 | 性能模型与扩展性 | Amdahl/Gustafson 定律、强/弱扩展、加速比与效率、Roofline、算术强度 | PMPP Ch6；CS267 性能分析 | ↔ L3-01 复杂度（渐近 vs 实测扩展）；本机可数值拟合 Amdahl |
| H-03 | 共享内存并行 / OpenMP | fork-join、线程与 `parallel for`、数据竞争、伪共享、缓存一致性、NUMA 亲和 | 🔴OpenMP 6.0 规范；CS267 共享内存章 | ↔ **L4-05 并发（此处性能，L4-05 正确性/无锁）**；本机 multiprocessing/BLAS 可证 |
| H-04 | 分布式内存并行 / MPI | SPMD、rank、点对点 send/recv、collective（bcast/reduce/scatter/gather）、通信子、MPI+X 混合 | 🔴MPI 5.0 标准；CS267 分布式内存章 | ↔ L5-01 分布式系统（此处消息传递 HPC，非共识/容错）；🔴本机需现装 mpi4py |
| H-05 | GPU 架构与 CUDA 编程模型 | SIMT、grid-block-thread、kernel 启动、host/device 分离与 H2D/D2H、异构数据并行 | PMPP Ch2-4；🔴CUDA Toolkit 13.x Guide | ↔ **G-06 图形可编程管线（同硬件）**；↔ L5-05 微架构；🔴本机无 GPU·文档腿 |
| H-06 | GPU 内存层级与性能优化 | global/shared/register/constant、访存合并 coalescing、tiling、占用率 occupancy、warp 分歧、原子操作 | PMPP Ch5-6, Ch7, Ch9 | ↔ L5-05（存储层级）；🔴演进快·本机无 GPU·未验证 |
| H-07 | 并行算法模式 | reduction、scan/prefix-sum、histogram、convolution/stencil、sorting、merge、sparse matrix、graph traversal | PMPP Part II-III（Ch7-15）；CS267 并行算法 | ↔ L3-01 算法（串行 vs 并行改造）；↔ L1-03 稀疏线代 |
| H-08 | 数据局部性与通信优化 | 缓存分块、通信-计算重叠、通信规避算法、负载均衡、数据划分与同步 | PMPP Ch5；CS267 划分/负载均衡 | ↔ H-04（collective 优化）；↔ L4-05（同步开销） |
| H-09 | 数值库生态 | BLAS/LAPACK 稠密线代、FFT、稀疏(PETSc)、"站在优化库肩上" vs 手写 kernel 权衡 | CS267 库/工具箱；PMPP 数值附录 | ↔ **L1-03 线代（并行数值线代算法）**；本机 BLAS 多线程可证 |
| H-10 | 应用与领域案例 | N-body 粒子、网格/PDE 仿真、深度学习、MRI/静电势等科学计算并行化 | PMPP Ch16-18；CS267 应用 | ↔ L5-02/L6-03 深度学习；↔ G-10 物理仿真；↔ N-11 LLM 系统（邻接不重复） |

**建议大主题数：K = 10**

---

## 组内汇总

| 课程 | 主锚 | 建议大主题数 K |
|------|------|----------------|
| L5-03 计算机图形学 | CMU 15-462/662 (Spring 2024) + Fundamentals of CG / Real-Time Rendering / PBRT | 10 |
| L5-15 嵌入式系统 | UT Austin Valvano (EE319K/EE445L, Cortex-M) + Lee & Seshia 2nd ed (CPS) | 12 |
| L6-06 高性能计算（含 GPU） | PMPP 4th ed + Berkeley CS267 (Spring 2024) | 10 |
| **合计** | | **32** |

关键跨课重叠约束（供 R4 编排，避免重复立项）：
- **G-06 ↔ H-05/H-06**：同一 GPU 硬件的两个视角——图形学讲**可编程渲染管线**，HPC 讲**通用计算/性能**；勿互相覆盖。
- **H-05/H-06 ↔ L5-05 高级体系结构**：HPC 讲**编程模型与性能**，L5-05 讲**硬件微架构**（round1/round2 已定"分账"）。
- **H-03 ↔ L4-05 并发**：HPC 讲**性能**（伪共享/NUMA/加速比），L4-05 讲**正确性**（内存模型/无锁）。
- **H-04 ↔ L5-01 分布式系统**：HPC 讲**消息传递并行计算**，L5-01 讲**共识/容错**。
- **E-11 ↔ L3-05 自动机 / L6-02 形式验证**：模型检验/时序逻辑同工具，嵌入式为其 CPS 应用语境。
- **G-09 ↔ L2-03 概率**、**G-10/H-09 ↔ L1-03 线代 / L1-04 微积分**：数学基础在本组的可视/高性能落地。
- **H-10 ↔ N-11 AI/LLM 系统**：邻接但 N-11 是系统方向旗舰课，另立不并入。

🔴 演进快·须锚版本的对象（R4 报告显式硬标）：
- 嵌入式平台/RTOS：Cortex-M（ARMv7-M/v8-M）、TM4C123 参考板、FreeRTOS 202604 LTS / Zephyr 4.4。
- GPU/并行框架：CUDA Toolkit 13.x（本机无 GPU·无法实证）、MPI 5.0、OpenMP 6.0；OpenGL 4.6（已冻结）/ Vulkan 1.4。
- 全部核实 2026-07-25。
