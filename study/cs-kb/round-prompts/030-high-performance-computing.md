# Round3c · L6-06 高性能计算 · 报告 prompt（P2）

```text
[P2] L6-06·大主题1 并行计算动机与架构分类 — 报告prompt

【任务】为「L6-06·大主题1 并行计算动机与架构分类」（编号 H-01）产出一份符合 report-format.md v3 模板的教辅式报告。产物 Write 到 study/cs-kb/findings/030-high-performance-computing.md。抬头带可 grep 基线串：基线 Py3.11.15/np2.4.6/gcc13.3 @2026-07-25。

【v3 格式硬性】
- 标题层级固定：#（报告标题=大主题）→ ##（小主题=章节）→ ###（每个内容项一节）。### 下不写「核心概念：」「辅助说明：」等任何标签，直接正文、靠空行分段。
- 每个 ### 下：核心说明（必需，把是什么/定义/结论讲正确）+ 充分的辅助说明（必需，面向初学者讲到懂：直觉/为什么/最小例子/易错点/与前后关联，可多段，不是点缀）。
- 并行模型示意/公式/代码片段一律独占行或独占块，不内联埋进句子。
- 教辅口吻；广度优先——每项讲到初学者能懂即可，不做专家级纵深（不写长篇机制深挖/完整推导/设计权衡长论）。

【小主题清单（## 章节，逐个覆盖）】下游可读 round3b-subtopics/g10-graphics-embedded-hpc.md 中 L6-06 大主题 H-01 的小主题表（H-01.1～H-01.4）确认内容项。要覆盖：
- 1.1 为何并行：功耗墙 / 频率墙 / 内存墙（登纳德缩放终结的直觉）
- 1.2 Flynn 分类（SISD / SIMD / MIMD）与 SIMT 的位置
- 1.3 共享内存 vs 分布式内存架构（含 UMA/NUMA 直觉）
- 1.4 多核 / 众核 / 集群 / 异构系统谱系
本机可选 demo：读 /proc/cpuinfo 核对本机核数/架构（概念佐证，非强制）。

【验证纪律（强制）】
- 多来源比对：每个内容项 ≥2 个独立来源交叉核对，优先一手（PMPP 教材 / CS267 大纲）；来源打架时两边都记、点明分歧、不和稀泥。
- 不编造：具体数值/分类定义/术语来自比对通过的来源，或标「待核」，绝不凭记忆填。标核实日期。
- CUDA/框架版本处一律标「⚙演进快·锚版本」（本主题涉及 SIMT/异构时锚 CUDA Toolkit 13.x）。GPU/加速器编程视角并入本主题的架构谱系（1.2 SIMT、1.4 异构）。
- 章节末（每个小主题末）集中列来源与时效，不逐项脚注。

【权威锚点】
- 主锚：PMPP 4th ed（Kirk/Hwu/El Hajj）Ch1 Introduction。TOC：https://www.oreilly.com/library/view/programming-massively-parallel/9780323984638/xhtml/Contents.xhtml
- 副锚：Berkeley CS267（Spring 2024，Demmel）架构综述。https://sites.google.com/lbl.gov/cs267-spr2024 ；存档 https://inst.eecs.berkeley.edu/~cs267/archives.html
- ⚙演进快·锚版本：CUDA Toolkit 13.x（本机无 GPU·文档腿）、MPI 5.0（2025-06）、OpenMP 6.0（2024-11）。全部核实 2026-07-25。
- 跨课分账：↔ L5-05 体系结构——本主题取编程视角，不深入微架构细节。

【粒度判定】动笔前先判「1 份 or 拆 N 份 + 理由」：H-01 仅 4 个概念性小主题、无实证纵深，默认预期 1 份，除非你判定需拆并给理由。

【落盘】study/cs-kb/findings/030-high-performance-computing.md。报告须自足，不残留工具标签/控制字符，实证代码只写 scratchpad 不入库。
```

```text
[P2] L6-06·大主题2 性能模型与扩展性 — 报告prompt

【任务】为「L6-06·大主题2 性能模型与扩展性」（编号 H-02）产出一份符合 report-format.md v3 模板的教辅式报告。产物 Write 到 study/cs-kb/findings/030-high-performance-computing.md。抬头带基线串：基线 Py3.11.15/np2.4.6/gcc13.3 @2026-07-25。

【v3 格式硬性】
- 层级 #→##→###；### 下不写标签，直接正文分段。
- 每个 ### 下：核心说明（必需）+ 充分辅助说明（必需、面向初学者讲到懂：直觉/为什么/最小例子/易错点/关联）。
- 公式独占行/块（Amdahl、Gustafson、加速比、效率、Roofline 各公式单独成行，不内联）；代码片段独占块。
- 教辅口吻；广度优先、讲到初学者懂，不做专家纵深。

【小主题清单（## 章节）】下游可读 round3b 中 H-02 小主题表（H-02.1～H-02.4）。要覆盖：
- 2.1 Amdahl 定律（固定问题规模、串行瓶颈）
- 2.2 Gustafson 定律与强扩展 / 弱扩展的区别
- 2.3 加速比、效率与可扩展性度量
- 2.4 Roofline 模型与算术强度（arithmetic intensity）
本机可选 demo：numpy 拟合 Amdahl 加速比曲线；多线程 BLAS 测强扩展曲线；估算 matmul 算术强度（便宜且能加固公式时才做，非强制）。

【验证纪律（强制）】
- 多来源比对 ≥2 独立源，优先一手（PMPP Ch6 / CS267 性能分析）；冲突两边都记。
- 不编造公式系数/数值；标核实日期。前沿/框架版本处标「⚙演进快·锚版本」。
- 章节末集中列来源与时效。

【权威锚点】
- 主锚：PMPP 4th ed Ch6 Performance considerations。TOC 见上。
- 副锚：CS267（Spring 2024）性能分析部分。
- ⚙演进快·锚版本：涉及 GPU roofline 时锚 CUDA Toolkit 13.x。核实 2026-07-25。
- 跨课分账：↔ L3-01 复杂度——此处是实测扩展性，非渐近复杂度。

【粒度判定】动笔前判「1 份 or 拆 N 份 + 理由」：H-02 为 4 个公式驱动的小主题，默认 1 份，除非判定需拆并给理由。

【落盘】findings/030-high-performance-computing.md。自足、不残留工具标签/控制字符，实证代码只写 scratchpad。
```

```text
[P2] L6-06·大主题3 共享内存并行 / OpenMP — 报告prompt

【任务】为「L6-06·大主题3 共享内存并行 / OpenMP」（编号 H-03）产出一份符合 report-format.md v3 模板的教辅式报告。产物 Write 到 study/cs-kb/findings/030-high-performance-computing.md。抬头带基线串：基线 Py3.11.15/np2.4.6/gcc13.3 @2026-07-25。

【v3 格式硬性】
- 层级 #→##→###；### 下不写标签，直接正文分段。
- 每个 ### 下：核心说明（必需）+ 充分辅助说明（必需、初学者向）。
- OpenMP 指令/代码片段（如 #pragma omp parallel for、reduction 子句）独占块；并行模型示意（fork-join）独占行/块；不内联。
- 教辅口吻；广度优先、不做专家纵深。

【小主题清单（## 章节）】下游可读 round3b 中 H-03 小主题表（H-03.1～H-03.4）。要覆盖：
- 3.1 fork-join 模型与 parallel for
- 3.2 数据竞争、共享 / 私有变量、归约（reduction）子句
- 3.3 伪共享（false sharing）与缓存行、缓存一致性开销
- 3.4 NUMA 亲和与线程调度策略
本机可选 demo：Python multiprocessing 并行 map；复现竞争 vs 归约正确性；C + gcc 复现伪共享减速（便宜时做，非强制；本机核数有限的 NUMA 结论标近似）。

【验证纪律（强制）】
- 多来源比对 ≥2 独立源，优先一手（OpenMP 6.0 规范 / CS267 共享内存章）；冲突两边都记。
- 不编造子句语义/默认调度；标核实日期。
- OpenMP 版本处标「⚙演进快·锚版本」（锚 OpenMP API 6.0，2024-11；TR14=6.1 预览 2025-11）。GPU 视角：可点到 OpenMP target offload 归入并行框架谱系，深细节不展开。
- 章节末集中列来源与时效。

【权威锚点】
- 一手规范：OpenMP API 6.0（2024-11 定稿）官方规范 PDF 为权威版；TR14（6.1 预览）2025-11。
- 副锚：CS267（Spring 2024）共享内存编程章。
- 主锚参照：PMPP 4th（并行思维通用部分）。核实 2026-07-25。
- 跨课分账：↔ L4-05 并发——本主题讲性能（伪共享/NUMA/加速比），L4-05 讲正确性（内存模型/无锁）。

【粒度判定】动笔前判「1 份 or 拆 N 份 + 理由」：H-03 为 4 个小主题、可本机实证，默认 1 份，除非判定需拆并给理由。

【落盘】findings/030-high-performance-computing.md。自足、不残留工具标签/控制字符，实证代码只写 scratchpad。
```

```text
[P2] L6-06·大主题4 分布式内存并行 / MPI — 报告prompt

【任务】为「L6-06·大主题4 分布式内存并行 / MPI」（编号 H-04）产出一份符合 report-format.md v3 模板的教辅式报告。产物 Write 到 study/cs-kb/findings/030-high-performance-computing.md。抬头带基线串：基线 Py3.11.15/np2.4.6/gcc13.3 @2026-07-25。

【v3 格式硬性】
- 层级 #→##→###；### 下不写标签，直接正文分段。
- 每个 ### 下：核心说明（必需）+ 充分辅助说明（必需、初学者向）。
- MPI 调用/伪代码片段（send/recv、collective）独占块；通信模式示意独占行/块；不内联。
- 教辅口吻；广度优先、不做专家纵深。

【小主题清单（## 章节）】下游可读 round3b 中 H-04 小主题表（H-04.1～H-04.5）。要覆盖：
- 4.1 SPMD 模型与 rank / size
- 4.2 点对点 send/recv 与死锁避免
- 4.3 collective：bcast / reduce / scatter / gather / allreduce
- 4.4 通信子（communicator）与进程拓扑
- 4.5 MPI+X 混合并行（MPI+OpenMP / MPI+CUDA）
本机可选 demo：MPI 需现装 openmpi+mpi4py（非持久）；本地多进程可跑 send/recv 与 collective 小 demo（便宜且能加固时做，装了要标「现装·非持久」，跑不通如实留白）。

【验证纪律（强制）】
- 多来源比对 ≥2 独立源，优先一手（MPI 5.0 标准官方 PDF / CS267 分布式内存章）；冲突两边都记。
- 不编造函数签名/collective 语义；标核实日期。
- MPI 版本处标「⚙演进快·锚版本」（锚 MPI Standard 5.0，MPI Forum 2025-06-05 通过，要点=标准化 ABI；官方 PDF 为唯一权威版）。MPI+CUDA 处锚 CUDA Toolkit 13.x 并标⚙演进快。
- 章节末集中列来源与时效。

【权威锚点】
- 一手标准：MPI Standard 5.0（2025-06，官方 PDF）。
- 副锚：CS267（Spring 2024）分布式内存编程章。
- 主锚参照：PMPP 4th Ch20 异构集群（CUDA streams）作 MPI+CUDA 交叉参照。核实 2026-07-25。
- 跨课分账：↔ L5-01 分布式系统——本主题讲消息传递 HPC 计算，非共识/容错。

【粒度判定】动笔前判「1 份 or 拆 N 份 + 理由」：H-04 为 5 个小主题、demo 需现装，默认 1 份，除非判定需拆并给理由。

【落盘】findings/030-high-performance-computing.md。自足、不残留工具标签/控制字符，实证代码只写 scratchpad。
```

```text
[P2] L6-06·大主题5 GPU 架构与 CUDA 编程模型 — 报告prompt

【任务】为「L6-06·大主题5 GPU 架构与 CUDA 编程模型」（编号 H-05）产出一份符合 report-format.md v3 模板的教辅式报告。产物 Write 到 study/cs-kb/findings/030-high-performance-computing.md。抬头带基线串：基线 Py3.11.15/np2.4.6/gcc13.3 @2026-07-25。

【v3 格式硬性】
- 层级 #→##→###；### 下不写标签，直接正文分段。
- 每个 ### 下：核心说明（必需）+ 充分辅助说明（必需、初学者向）。
- CUDA kernel 代码片段、启动语法 <<<grid,block>>>、grid-block-thread 层级示意独占块/行；索引计算公式独占行；不内联。
- 教辅口吻；广度优先、不做专家纵深。

【小主题清单（## 章节）】下游可读 round3b 中 H-05 小主题表（H-05.1～H-05.4）。本大主题即「GPU/加速器编程」并入项，是核心。要覆盖：
- 5.1 SIMT 执行与 warp 概念
- 5.2 grid-block-thread 层级与全局索引计算
- 5.3 kernel 启动语法与 host / device 分离
- 5.4 H2D / D2H 数据传输与统一内存（unified memory）
本机可选 demo：本机无 GPU，5.1/5.3/5.4 硬标「未验证·无设备·文档腿」，不得凭记忆编造 kernel 输出；仅 5.2 可用 numpy 模拟索引映射（无 kernel）作类比。

【验证纪律（强制）】
- 多来源比对 ≥2 独立源，优先一手（CUDA C++ Programming Guide / PMPP Ch2-4）；冲突两边都记。
- 不编造：warp 大小/默认值/API 语法来自比对通过的来源，或标「待核」，绝不凭记忆填。标核实日期。
- CUDA 版本处一律标「⚙演进快·锚版本」（锚 CUDA C++ Programming Guide / CUDA Toolkit 13.x，13.2.2 与 13.3 Update 1 于 2026 年中）。全主题硬标「本机无 GPU·未验证·文档腿」。
- 章节末集中列来源与时效。

【权威锚点】
- 主锚：PMPP 4th ed Ch2 异构数据并行 / Ch3 多维网格与数据 / Ch4 计算架构与调度。
- 一手文档：CUDA C++ Programming Guide（CUDA Toolkit 13.x）⚙演进快。
- 副锚：CS267（Spring 2024）GPU 章。核实 2026-07-25。
- 跨课分账：↔ L5-03 图形 G-06 可编程渲染管线（同一 GPU 硬件，图形视角 vs 通用计算视角，勿互相覆盖）；↔ L5-05 微架构（此处讲编程模型，非硬件微架构）。

【粒度判定】动笔前判「1 份 or 拆 N 份 + 理由」：H-05 与 H-06 同属 GPU 且均本机无法实证，若单独成篇默认 1 份；如判定 H-05+H-06 合并或再拆，给理由。

【落盘】findings/030-high-performance-computing.md。自足、不残留工具标签/控制字符，实证代码只写 scratchpad。
```

```text
[P2] L6-06·大主题6 GPU 内存层级与性能优化 — 报告prompt

【任务】为「L6-06·大主题6 GPU 内存层级与性能优化」（编号 H-06）产出一份符合 report-format.md v3 模板的教辅式报告。产物 Write 到 study/cs-kb/findings/030-high-performance-computing.md。抬头带基线串：基线 Py3.11.15/np2.4.6/gcc13.3 @2026-07-25。

【v3 格式硬性】
- 层级 #→##→###；### 下不写标签，直接正文分段。
- 每个 ### 下：核心说明（必需）+ 充分辅助说明（必需、初学者向）。
- 内存层级示意、tiling 代码片段、访存合并图示独占块/行；占用率相关公式独占行；不内联。
- 教辅口吻；广度优先、不做专家纵深。

【小主题清单（## 章节）】下游可读 round3b 中 H-06 小主题表（H-06.1～H-06.5）。本大主题为「GPU/加速器编程」并入项的性能篇。要覆盖：
- 6.1 内存层级：global / shared / register / constant / local
- 6.2 访存合并（memory coalescing）
- 6.3 共享内存 tiling（分块）
- 6.4 占用率（occupancy）与 warp 分歧（divergence）
- 6.5 原子操作（atomics）与 privatization
本机可选 demo：本机无 GPU，6.1/6.2/6.4 硬标「未验证·无设备·文档腿」，不编造；6.3 可用 numpy 分块矩阵乘类比 tiling 思想；6.5 可用 numpy 串行类比私有化累加。

【验证纪律（强制）】
- 多来源比对 ≥2 独立源，优先一手（PMPP Ch5-6/Ch7/Ch9 / CUDA C++ Programming Guide）；冲突两边都记。
- 不编造：内存延迟/带宽/占用率数值来自来源或标「待核」，绝不凭记忆填。标核实日期。
- CUDA 版本处一律标「⚙演进快·锚版本」（锚 CUDA Toolkit 13.x）。全主题硬标「本机无 GPU·未验证·文档腿」。
- 章节末集中列来源与时效。

【权威锚点】
- 主锚：PMPP 4th ed Ch5 内存架构与数据局部性 / Ch6 性能 / Ch7 Convolution / Ch9 Parallel histogram（atomics/privatization）。
- 一手文档：CUDA C++ Programming Guide（CUDA Toolkit 13.x）⚙演进快。
- 副锚：CS267（Spring 2024）GPU 性能优化部分。核实 2026-07-25。
- 跨课分账：↔ L5-05 存储层级（此处编程优化视角）。

【粒度判定】动笔前判「1 份 or 拆 N 份 + 理由」：H-06 为 5 个小主题、多数本机无法实证，默认 1 份；如判定与 H-05 合并，给理由。

【落盘】findings/030-high-performance-computing.md。自足、不残留工具标签/控制字符，实证代码只写 scratchpad。
```

```text
[P2] L6-06·大主题7 并行算法模式 — 报告prompt

【任务】为「L6-06·大主题7 并行算法模式」（编号 H-07）产出一份符合 report-format.md v3 模板的教辅式报告。产物 Write 到 study/cs-kb/findings/030-high-performance-computing.md。抬头带基线串：基线 Py3.11.15/np2.4.6/gcc13.3 @2026-07-25。

【v3 格式硬性】
- 层级 #→##→###；### 下不写标签，直接正文分段。
- 每个 ### 下：核心说明（必需）+ 充分辅助说明（必需、初学者向）。
- 各模式的并行改造示意/伪代码/代码片段独占块；扫描/归约的步骤公式独占行；不内联。
- 教辅口吻；广度优先、不做专家纵深。

【小主题清单（## 章节，本主题小主题较多）】下游可读 round3b 中 H-07 小主题表（H-07.1～H-07.8）。要覆盖：
- 7.1 Reduction（归约、减分歧策略）
- 7.2 Scan / prefix-sum（Hillis-Steele / Blelloch）
- 7.3 Histogram（atomics / privatization）
- 7.4 Convolution / Stencil
- 7.5 Sorting（并行基数 / 归并思想）
- 7.6 Merge（并行归并 / 协同秩 co-rank）
- 7.7 Sparse matrix（CSR / SpMV）
- 7.8 Graph traversal（并行 BFS）
本机可选 demo：绝大多数可就地实证——numpy 树形归约 vs 线性、Hillis-Steele/Blelloch 扫描、私有化直方图、scipy 卷积/stencil、基数排序玩具、双序列归并、scipy.sparse SpMV、Python/numpy BFS 层级遍历（便宜且加固时做，非逐项强制）。

【验证纪律（强制）】
- 多来源比对 ≥2 独立源，优先一手（PMPP Part II-III Ch7-15 / CS267 并行算法）；冲突两边都记。
- 不编造算法步数/复杂度；标核实日期。GPU 落地处（原子/分歧）标「⚙演进快·锚版本」并锚 CUDA Toolkit 13.x。
- 章节末集中列来源与时效。

【权威锚点】
- 主锚：PMPP 4th ed Ch7 Convolution、Ch8 Stencil、Ch9 Histogram、Ch10 Reduction、Ch11 Prefix sum、Ch12 Merge、Ch13 Sorting、Ch14 Sparse matrix、Ch15 Graph traversal。
- 副锚：CS267（Spring 2024）并行算法（含线代/FFT/图）。核实 2026-07-25。
- 跨课分账：↔ L3-01 算法（串行 vs 并行改造）；↔ L1-03 稀疏线代。

【粒度判定】动笔前判「1 份 or 拆 N 份 + 理由」：H-07 有 8 个小主题、跨多种模式，属「小主题多」情形，很可能需拆成 -a/-b（如基础模式 vs 高级模式）；先给「1 份 / 拆 N 份 + 理由」判定再动笔。

【落盘】findings/030-high-performance-computing.md（若拆分用 030-...-a/-b）。自足、不残留工具标签/控制字符，实证代码只写 scratchpad。
```

```text
[P2] L6-06·大主题8 数据局部性与通信优化 — 报告prompt

【任务】为「L6-06·大主题8 数据局部性与通信优化」（编号 H-08）产出一份符合 report-format.md v3 模板的教辅式报告。产物 Write 到 study/cs-kb/findings/030-high-performance-computing.md。抬头带基线串：基线 Py3.11.15/np2.4.6/gcc13.3 @2026-07-25。

【v3 格式硬性】
- 层级 #→##→###；### 下不写标签，直接正文分段。
- 每个 ### 下：核心说明（必需）+ 充分辅助说明（必需、初学者向）。
- 分块/重叠/划分示意与代码片段独占块；局部性相关公式独占行；不内联。
- 教辅口吻；广度优先、不做专家纵深。

【小主题清单（## 章节）】下游可读 round3b 中 H-08 小主题表（H-08.1～H-08.5）。要覆盖：
- 8.1 缓存分块（cache blocking）与时间 / 空间局部性
- 8.2 通信-计算重叠（overlap）
- 8.3 通信规避（communication-avoiding）算法
- 8.4 负载均衡与数据划分策略
- 8.5 同步开销与屏障（barrier）
本机可选 demo：numpy 分块 vs 朴素 matmul 计时；mpi4py 非阻塞演示重叠（需现装·非持久）；threading barrier 计时；Python 划分方案对比（便宜且加固时做，非强制）。

【验证纪律（强制）】
- 多来源比对 ≥2 独立源，优先一手（PMPP Ch5 / CS267 划分与负载均衡章）；冲突两边都记。
- 不编造带宽/开销数值；标核实日期。GPU/MPI 相关处标「⚙演进快·锚版本」（锚 CUDA Toolkit 13.x / MPI 5.0）。
- 章节末集中列来源与时效。

【权威锚点】
- 主锚：PMPP 4th ed Ch5 内存架构与数据局部性。
- 副锚：CS267（Spring 2024）数据划分 / 负载均衡 / 通信规避部分。核实 2026-07-25。
- 跨课分账：↔ H-04（collective 优化）；↔ L4-05（同步开销、屏障的正确性视角在别处）。

【粒度判定】动笔前判「1 份 or 拆 N 份 + 理由」：H-08 为 5 个小主题、概念+可实证混合，默认 1 份，除非判定需拆并给理由。

【落盘】findings/030-high-performance-computing.md。自足、不残留工具标签/控制字符，实证代码只写 scratchpad。
```

```text
[P2] L6-06·大主题9 数值库生态 — 报告prompt

【任务】为「L6-06·大主题9 数值库生态」（编号 H-09）产出一份符合 report-format.md v3 模板的教辅式报告。产物 Write 到 study/cs-kb/findings/030-high-performance-computing.md。抬头带基线串：基线 Py3.11.15/np2.4.6/gcc13.3 @2026-07-25。

【v3 格式硬性】
- 层级 #→##→###；### 下不写标签，直接正文分段。
- 每个 ### 下：核心说明（必需）+ 充分辅助说明（必需、初学者向）。
- 库调用代码片段独占块；BLAS 层级/复杂度公式独占行；不内联。
- 教辅口吻；广度优先、不做专家纵深。

【小主题清单（## 章节）】下游可读 round3b 中 H-09 小主题表（H-09.1～H-09.4）。要覆盖：
- 9.1 BLAS 三级（L1/L2/L3）与 LAPACK 稠密线代
- 9.2 FFT 库
- 9.3 稀疏求解（PETSc / 迭代法直觉）
- 9.4 「站在优化库肩上」vs 手写 kernel 的权衡
本机可选 demo：numpy @ 调用多线程 BLAS 测速；scipy.fft 验证+计时；scipy.sparse.linalg 迭代解；numpy 库 vs 手写 Python 循环对比（便宜且加固时做，非强制）。GPU 库（cuBLAS/cuFFT/cuSPARSE）可点到归入生态，标本机无 GPU·文档腿。

【验证纪律（强制）】
- 多来源比对 ≥2 独立源，优先一手（CS267 库/工具箱 / PMPP 数值附录 A）；冲突两边都记。
- 不编造库版本/复杂度/接口；标核实日期。GPU 数值库处标「⚙演进快·锚版本」（锚 CUDA Toolkit 13.x）。
- 章节末集中列来源与时效。

【权威锚点】
- 副锚（主用）：CS267（Spring 2024）数值库/工具箱部分。
- 主锚：PMPP 4th ed Appendix A Numerical considerations。
- 一手参照：BLAS/LAPACK 官方参考；本机 numpy 底层 BLAS 实测。核实 2026-07-25。
- 跨课分账：↔ L1-03 线代（并行数值线代算法）。

【粒度判定】动笔前判「1 份 or 拆 N 份 + 理由」：H-09 为 4 个小主题、可本机实证，默认 1 份，除非判定需拆并给理由。

【落盘】findings/030-high-performance-computing.md。自足、不残留工具标签/控制字符，实证代码只写 scratchpad。
```

```text
[P2] L6-06·大主题10 应用与领域案例 — 报告prompt

【任务】为「L6-06·大主题10 应用与领域案例」（编号 H-10）产出一份符合 report-format.md v3 模板的教辅式报告。产物 Write 到 study/cs-kb/findings/030-high-performance-computing.md。抬头带基线串：基线 Py3.11.15/np2.4.6/gcc13.3 @2026-07-25。

【v3 格式硬性】
- 层级 #→##→###；### 下不写标签，直接正文分段。
- 每个 ### 下：核心说明（必需）+ 充分辅助说明（必需、初学者向）。
- 各应用的并行化示意/代码片段独占块；控制方程/更新公式独占行；不内联。
- 教辅口吻；广度优先、不做专家纵深。

【小主题清单（## 章节）】下游可读 round3b 中 H-10 小主题表（H-10.1～H-10.4）。要覆盖：
- 10.1 N-body 粒子仿真
- 10.2 网格 / PDE（stencil）仿真
- 10.3 深度学习并行（数据并行 / 模型并行直觉）
- 10.4 科学计算案例（MRI 重建 / 静电势图）
本机可选 demo：numpy 小 N-body（直接求和）；numpy 2D 热方程显式 stencil；numpy 静电势叠加小算例（便宜且加固时做，非强制）；10.3 深度学习并行为概念。GPU 落地处标本机无 GPU·文档腿。

【验证纪律（强制）】
- 多来源比对 ≥2 独立源，优先一手（PMPP Ch16 深度学习 / Ch17 迭代 MRI 重建 / Ch18 静电势图 / CS267 应用）；冲突两边都记。
- 不编造算例数值/收敛结论；标核实日期。GPU/框架版本处标「⚙演进快·锚版本」（锚 CUDA Toolkit 13.x）。
- 章节末集中列来源与时效。

【权威锚点】
- 主锚：PMPP 4th ed Ch16 Deep learning、Ch17 Iterative MRI reconstruction、Ch18 Electrostatic potential map。
- 副锚：CS267（Spring 2024）应用（N-body 粒子、网格 PDE、FFT、图、机器学习）。核实 2026-07-25。
- 跨课分账：↔ L5-02/L6-03 深度学习（此处并行化视角）；↔ L5-03 G-10 物理仿真；↔ N-11 AI/LLM 系统（邻接不重复，LLM 系统另立不并入）。

【粒度判定】动笔前判「1 份 or 拆 N 份 + 理由」：H-10 为 4 个案例型小主题，默认 1 份，除非判定需拆并给理由。

【落盘】findings/030-high-performance-computing.md。自足、不残留工具标签/控制字符，实证代码只写 scratchpad。
```

共 10 条 prompt
