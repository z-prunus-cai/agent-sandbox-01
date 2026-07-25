# Round 2 · 第 10 组「图形 / 嵌入式 / HPC」定方向

> 基线 Py3.11.15/np2.4.6/gcc13.3 @2026-07-25 ｜ 调查员：第 10 组（G10）
> 本组课程：**L5-03 计算机图形学**、**L5-15 嵌入式系统**（Round1 新增，SPD KA）、**L6-06 高性能计算**（并入 GPU/加速器编程）。
> 组内共性：**贴近硬件 / 数值密集 / 并行**——都是"把数学与算法压到具体执行基质（GPU 光栅化单元 / MCU 外设 / 多核-众核）上跑"的课。
> 交付定位：本轮只**定方向**（重点方向 + 组内对比 + 一手源候选 + 本机验证点 + 优先级），不是 R4 三腿成品。
> 纪律：一手优先；具体 SoC/RTOS/CUDA 版本硬标「锚版本、演进快」；时效核实 2026-07-25；不确定留白。

---

## §0 · 本机能力边界（先交代，决定各课"证"腿可行性）

开工自检通过：`numpy 2.4.6 / sympy 1.14.0 / scipy 1.17.1` 均可用。本组关键硬约束——

| 能力 | 本机状态（@2026-07-25） | 影响 |
|------|------------------------|------|
| numpy/scipy 数值 | ✅ 可用 | 图形管线、数值库、CPU 并行 demo 的主力证腿 |
| CPU 核数 | `nproc` = **4** | 可做 multiprocessing / BLAS 多线程 加速比 demo（小规模） |
| MPI 工具链 | ❌ `mpirun/mpicc` 均无 | MPI 只能走**文档腿**；如需证腿须 `apt/conda` 装 openmpi + mpi4py（非持久，须现装现跑并标注） |
| CUDA / GPU | ❌ `nvcc/nvidia-smi` 均无、无 GPU | **CUDA/GPU 编程无法本机实证**——只能一手文档 + 概念，硬标「未验证·无设备」；不得凭记忆编造 kernel 输出 |
| 嵌入式硬件 | ❌ 无开发板 | 嵌入式以**参考平台文档 + QEMU 仿真**为锚，不假装上板 |

> 这条边界是本组诚实性红线：**GPU/CUDA 与真实 MCU 外设在本机不可实证**，报告须显式标注，不能用记忆填平（brief §2/§3 红线）。

---

## §1 · 组内选型 / 对比表（定方向骨架）

### 1.1 图形：光栅化 vs 光线追踪
| 维度 | 光栅化 Rasterization | 光线追踪 Ray Tracing |
|------|----------------------|----------------------|
| 核心问题 | 三角形 → 像素（正向、以图元为中心） | 像素 → 场景求交（反向、以视线为中心） |
| 复杂度直觉 | O(图元 × 覆盖像素)，高度并行、固定管线友好 | O(像素 × 光线 × 求交)，需加速结构（BVH） |
| 全局光照 | 天生局部；软阴影/反射/GI 靠"作弊"（shadow map、SSR、烘焙） | 天生支持反射/折射/软阴影/GI（路径追踪） |
| 硬件 | GPU 固定光栅化单元（数十年成熟） | RT Core（硬件 BVH 遍历，2018+ 消费级） |
| 定位 | **实时**主力（游戏、UI） | 离线渲染主力；实时为**混合**（栅格 + RT 补光影） |

### 1.2 图形：实时 vs 离线渲染
| | 实时 (real-time) | 离线 (offline) |
|--|------------------|----------------|
| 帧预算 | 单帧 ≤ ~16/8 ms（60/120 fps） | 每帧数秒~数小时 |
| 目标 | "足够可信"，可近似 | 物理正确，收敛到无偏 |
| 代表 | 光栅化 + 混合 RT；OpenGL/Vulkan/D3D | 路径追踪；离线渲染器 |
| 权衡 | 延迟>质量；预计算/时域复用 | 质量>延迟；蒙特卡洛收敛 |

### 1.3 HPC：MPI vs OpenMP vs CUDA（三大并行模型）
| 维度 | MPI | OpenMP | CUDA |
|------|-----|--------|------|
| 并行范式 | 分布式内存 · **消息传递** | 共享内存 · **fork-join 线程** | 众核 · **SIMT 数据并行** |
| 地址空间 | 每进程私有，显式收发 | 单进程多线程共享 | Host/Device 分离，显式 H2D/D2H |
| 扩展边界 | **跨节点**（集群、超算） | **单节点多核** | 单机 GPU（多 GPU 需 + MPI/NCCL） |
| 编程心智 | rank / send-recv / collective | `#pragma omp parallel for` 增量并行 | grid-block-thread、内存层级(global/shared/register) |
| 通信瓶颈 | 网络延迟/带宽（通信优化的主战场） | 伪共享/缓存一致性 | H2D 拷贝、显存带宽、warp 分歧 |
| 组合 | 常与 OpenMP/CUDA **混合**（MPI+X） | 可嵌 MPI 进程内 | GPU 内加速，节点间靠 MPI |

> 心智模型一句话：**OpenMP = 把循环摊给同屋的几个人；MPI = 把活寄给不同城市的人再汇总；CUDA = 雇一支纪律严明的千人军团做同一个动作。**

### 1.4 跨组：通用 CPU vs GPU vs 嵌入式 MCU 的定位
| | 通用 CPU | GPU（众核加速器） | 嵌入式 MCU |
|--|----------|-------------------|------------|
| 优化目标 | 低延迟、控制流复杂、少线程强单核 | 高吞吐、大规模数据并行、隐藏延迟 | 确定性、低功耗、实时响应、成本 |
| 典型指标 | IPC、缓存命中 | FLOPS、显存带宽、占用率 | 中断延迟、功耗(µA)、RAM/Flash(KB) |
| 编程面 | OS + 通用语言 | CUDA/HIP/SYCL + host 协同 | 裸机/RTOS、寄存器、外设(GPIO/ADC/DMA) |
| 本组落点 | HPC 的 MPI/OpenMP 宿主 | HPC 的 GPU 编程 | 嵌入式系统 |

---

## §2 · 逐课重点方向（每课 3–6 个）+ 边界

### L5-03 计算机图形学（GIT KA｜层 L5｜依赖 L1-03 线代 / L1-04 微积分）
重点方向：
1. **变换与投影管线**：model→world→view→clip→NDC→screen；齐次坐标、透视除法、视口变换。（**本组最强证腿**，见 §3）
2. **光栅化与可见性**：三角形遍历、重心坐标插值、深度缓冲 Z-buffer、透视校正插值。
3. **着色 / 光照模型**：Phong / Blinn-Phong、法线变换（用逆转置矩阵）、纹理映射与采样(mipmap)。
4. **光线追踪 / 路径追踪**：光线-图元求交、BVH 加速、蒙特卡洛积分与渲染方程直觉。
5. **可编程管线**：顶点/片元着色器阶段、GPU 固定 vs 可编程分工（接 HPC/GPU）。

边界：**不**深入具体 GPU 微架构（→ L5-05 体系结构）；**不**做 GUI/交互设计（→ HCI，另组）；渲染方程的完整测度论推导超出本科图形学范围，点到"直觉 + 数值积分"为止。

### L5-15 嵌入式系统（SPD KA + AR/OS 交叉｜层 L5｜Round1 新增）
重点方向：
1. **裸机执行模型与启动**：内存映射 I/O、寄存器读写、startup/链接脚本、`volatile` 语义。
2. **中断与实时性**：中断向量、ISR、优先级/嵌套、中断延迟；轮询 vs 中断 vs DMA 三种 I/O 范式。
3. **外设机制**：GPIO、定时器/PWM、ADC-DAC、总线（I²C/SPI/UART/CAN）、DMA（省 CPU 的数据搬运）。
4. **RTOS 概念**：任务/调度（抢占式优先级、时间片）、实时约束（硬/软实时、WCET）、同步（信号量/互斥/优先级反转+继承）。
5. **约束驱动的设计权衡**：功耗（睡眠模式）、RAM/Flash 预算、确定性 vs 吞吐。

边界（**硬标演进快**）：机制层（中断/DMA/RTOS 调度语义）**稳定**，但**具体 SoC / 工具链 / RTOS 版本演进快**——报告须**锚一个参考平台**（建议 ARM Cortex-M + 一个具名 RTOS，见 §3），并显式标「以某平台为锚，其它平台细节可能不同」。**不**替代 L4-01 OS 的通用调度/虚存理论（嵌入式常无 MMU / 无完整 OS）。

### L6-06 高性能计算（PDC KA｜层 L6｜依赖 L4-05 并发 / L1-03 线代｜并入 GPU 编程）
重点方向：
1. **并行模型三分**：共享内存(OpenMP) / 分布式(MPI) / 众核(CUDA)——见 §1.3；MPI+X 混合。
2. **性能分析框架**：Amdahl / Gustafson 定律、强/弱扩展性、加速比与效率、Roofline（算力 vs 带宽瓶颈）。
3. **通信 / 数据局部性优化**：collective 通信、通信-计算重叠、缓存分块(tiling)、NUMA 亲和。
4. **GPU / 加速器编程**：SIMT、内存层级(global/shared/register)、占用率、warp 分歧、H2D 拷贝优化。（**本机无 GPU，文档腿**）
5. **数值库生态**：BLAS/LAPACK、稀疏(PETSc)、FFT；"站在优化好的库肩上" vs 手写 kernel 的权衡。

边界：**不**重复 L4-05 的内存模型/无锁细节（那是并发正确性；这里是**性能**）；**不**深入分布式共识/容错（→ L5-01）；GPU 微架构细节与 L5-05 分账（这里讲**编程模型与性能**，L5-05 讲**硬件机制**）。

---

## §3 · 一手源候选 + 本机验证点

### 一手源候选（腿①，待 R4 定位到章节/版本）
| 课 | 一手源候选 | 版本 / 时效锚（@2026-07-25） |
|----|-----------|------------------------------|
| L5-03 | **OpenGL 4.6 Core Profile 规范**（Khronos Registry）；**Vulkan 1.4 规范**；*Real-Time Rendering* 4th ed；*PBRT*（物理渲染，在线开放） | OpenGL 4.6 = **最终版/已冻结**（2017 发布，末次 spec 更新 2022-05-05），Khronos 后续主推 Vulkan；**Vulkan 1.4**（2024-12）为当前活跃线[^gl][^vk] |
| L5-15 | **ARM Cortex-M Generic User Guide / ARMv7-M / ARMv8-M Architecture Ref Manual**（ARM 官方）；参考 MCU 的 datasheet/reference manual（如 STM32）；**RTOS 官方文档** | 建议锚 **Cortex-M** + **FreeRTOS 202604 LTS（kernel v11.3.0, 2026-04）** 或 **Zephyr 4.4（2026-04）**；均date-based/半年节奏，**硬标演进快**[^frtos][^zephyr] |
| L6-06 | **MPI Standard 5.0**（MPI Forum 官方 PDF，唯一权威版）；**OpenMP 6.0 规范**（openmp.org）；**CUDA C++ Programming Guide**（NVIDIA docs）；BLAS/LAPACK 参考 | **MPI 5.0** 2025-06-05 通过（要点：标准化 **ABI**）；**OpenMP 6.0** 2024-11 定稿（TR14=6.1 预览 2025-11）；**CUDA Toolkit 13.x**（13.2.2 / 13.3 Update 1，2026 年中）——CUDA **硬标演进快**[^mpi][^omp][^cuda] |

### 本机验证点（腿③，已跑通 proof-of-concept）

**验证点 A · L5-03 变换投影管线 + Blinn-Phong 光照（numpy，✅已跑通）**
用 numpy 实现 `look_at / perspective / model` 矩阵，对一个三角形做完整 MVP→透视除法→NDC→视口 变换，并在世界空间用**逆转置矩阵**变换法线后算 Blinn-Phong。真实输出（可复现脚本见下）：
```
clip w = [3.   2.75 3.25]                 # 透视除法的 w 分量（深度相关）
NDC:  [[ 0.      0.2887  0.9353] ...]      # 裁剪空间归一化
screen xy: [[400.  213.4] ...]            # 800x600 视口像素坐标（顶点投影到屏幕中心 x=400 ✓）
Nworld=[0.5 0. 0.866]  diffuse=0.5521 spec=0.0415 I=0.6935   # 法线经 rot_y(30°) 后 + 光照强度
```
自洽校验：物体空间法线 +z 经绕 y 轴 30° 旋转 → 世界法线 (sin30°,0,cos30°)=(0.5,0,0.866) ✓；顶点 (0,0.5,0) 位于视锥中轴，投影屏幕 x=400（视口正中）✓。**这是本组最扎实的证腿，R4 可直接扩为完整教学脚本。**

**验证点 B · L6-06 CPU 并行 / 数值库（numpy+BLAS，✅已跑通）**
- 向量化 SAXPY（n=2e7）与 `2000³` matmul 触发多线程 BLAS（本机 4 核），演示"数据并行 + 库加速"。真实输出：`2000^3 matmul: 166.5 ms (BLAS multithread)`。
- R4 可补：`multiprocessing` 手写数据并行加速比（对比 1→4 进程）、Amdahl 定律数值拟合。

**待补 / 受限验证点（诚实留白）**
- **MPI**：本机无工具链。R4 若做，须现装 `openmpi + mpi4py`（非持久，标注"现装现跑"），做 ring/bcast/reduce 小 demo；否则纯文档腿。
- **CUDA/GPU**：**本机无 GPU/nvcc，无法实证**——只能一手文档 + 概念图，硬标「未验证·无设备」。可选替代：用 numpy 模拟 SIMT 数据并行的**概念**（非真实 kernel），须明确标注"仅概念模拟，非 GPU 执行"。
- **嵌入式**：无开发板。可选 **QEMU 仿真 Cortex-M**（如 `qemu-system-arm` + 参考固件）做中断/GPIO 概念实证，须装工具链并标注；否则以参考平台 datasheet/RTOS 文档为锚。

复现环境：`基线 Py3.11.15/np2.4.6/gcc13.3 @2026-07-25`，脚本仅用 numpy 标准库。

---

## §4 · 优先级校准

沿用 round1-map §1：三课在种子里均为 **P2**（方向深化/选修）。本轮校准建议：

| 课 | 种子优先级 | 本组建议 | 理由 |
|----|-----------|----------|------|
| **L5-03 图形学** | P2 | **P2，但证腿价值上调** | 变换投影管线是 L1-03/L1-04 数学的最佳"看得见"落地，numpy 证腿最扎实、教学收益高；作为本组**首选深挖对象** |
| **L5-15 嵌入式** | P2（Round1 新增） | **P2，硬标演进快** | 机制稳、但平台/RTOS 演进快且本机无硬件——证腿最弱（靠仿真/文档）；教学独立价值高，但取证成本高，排本组第二 |
| **L6-06 HPC** | P2 | **P2，拆分证腿难度** | CPU/OpenMP/numpy 部分可实证（易）；**GPU/CUDA + MPI 本机不可实证**（难，文档腿）——R4 须显式分账"可证 vs 只讲"，避免 GPU 部分凭记忆编造 |

组内深挖顺序建议：**L5-03（证腿最实）→ L6-06（部分可证，注意 GPU 留白）→ L5-15（硬件受限，靠仿真/文档，硬标演进快）**。

关键约束提醒（供 R3/R4）：
- L5-03 依赖 L1-03 线代 + L1-04 微积分，须在其后。
- L6-06 与 **L5-05 高级体系结构**（GPU/向量）在种子里已标"分账"——R3 造 prompt 时明确：L6-06 讲**并行编程模型与性能**，L5-05 讲**硬件微架构**，勿重复立项。
- L6-06 的 GPU 子方向与 round1-map N-16「GPU/加速器编程」是**同一对象**（已并入，勿重复立项）；与 N-11「AI/LLM 系统」邻接但不同（后者是系统方向旗舰课，另立）。

---

## 脚注（一手源，待 R4 逐条下沉到章节/版本）
[^gl]: OpenGL 4.6 Core Profile 规范（末次更新 2022-05-05），Khronos Registry https://registry.khronos.org/OpenGL/specs/gl/glspec46.core.pdf ；OpenGL 4.6 = 最终冻结版，后续主推 Vulkan。核实 2026-07-25。
[^vk]: Vulkan 1.4（2024-12 发布，当前活跃线），Khronos。核实 2026-07-25（未逐条下沉，R4 补 minor 版本号）。
[^mpi]: MPI: A Message-Passing Interface Standard **Version 5.0**（MPI Forum 2025-06-05 通过，要点=标准化 ABI），官方 PDF 为唯一权威版 https://www.mpi-forum.org/docs/mpi-5.0/mpi50-report.pdf ；MPICH 5.0.0 已实现（2026-02）。核实 2026-07-25。
[^omp]: OpenMP API Specification **6.0**（2024-11 定稿）；TR14 = 6.1 Preview（2025-11）https://www.openmp.org/specifications/ 。核实 2026-07-25。
[^cuda]: CUDA C++ Programming Guide / CUDA Toolkit **13.x**（13.2.2 与 13.3 Update 1 于 2026 年中，13.4 开发预览），NVIDIA docs https://docs.nvidia.com/cuda/ 。**硬标演进快**——须锚具体 Toolkit 版本。核实 2026-07-25。
[^frtos]: FreeRTOS **202604 LTS**（kernel **v11.3.0**，2026-04；date-based YYYYMM 版本），AWS/FreeRTOS 官方 https://github.com/FreeRTOS/FreeRTOS-LTS 。**硬标演进快**。核实 2026-07-25。
[^zephyr]: Zephyr **4.4**（2026-04），下一版 4.5（2026-10，半年节奏），Zephyr Project https://docs.zephyrproject.org/latest/releases/index.html 。**硬标演进快**。核实 2026-07-25。

## 非承重（二手 · 仅线索）
- 嵌入式课纲佐证：UCSC 计算机工程电选 2024–25（RTOS/GPIO/中断/总线/ADC-DAC/DMA 覆盖）——见 view3-emerging §A（一手课程页已在 round1）。
- GPU 编程顶校课：Caltech CS179 等——见 view3-emerging §B B8（已并入 L6-06，勿重复立项）。
- ARM 参考平台/QEMU 仿真为嵌入式证腿的候选路径（R4 定平台后再取一手 ARM 手册 + datasheet）。
