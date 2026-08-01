# L6-08·大主题08S-6 Kernel 与 Triton / FlashAttention

> 基线 Py3.11.15/np2.4.6/gcc13.3 @2026-07-25 ｜核实日期：2026-08-01 ｜先修：L6-03·03-6（注意力与 Transformer）、L6-08·08S-5（GPU 内存层次与 roofline）｜一手锚点：CS336「Language Modeling from Scratch」Spring 2026 L6；FlashAttention v1（Dao et al., arXiv:2205.14135, 2022）/ v2（Dao, arXiv:2307.08691, 2023）/ v3（Shah et al., arXiv:2407.08608, 2024）；在线 softmax（Milakov & Gimelshein, arXiv:1805.02867, 2018）；Triton 官方文档（triton-lang.org）｜成熟度：⚙演进快·逐版演进·锚版本 CS336 Spring 2026 + FlashAttention v1/v2/v3

在现代 GPU 上，注意力慢不是因为乘加算不过来，而是因为一张巨大的中间矩阵在慢速显存里反复读写——把"省访存"当成第一目标去重写 kernel，就能在不改变数学结果的前提下大幅加速。下面五个小主题从"为什么访存是墙"讲到"怎么写这种 kernel"再到"这套方法逐版怎么演进"。

因为整个大主题的知识对象（FlashAttention 版本、Triton 版本、支持的硬件与精度）演进极快，文中凡涉及具体加速比、利用率、支持硬件的数字都锚定到具体论文/版本与核实日期；无一手来源坐实的数值一律标「待核」，不凭记忆填。

---

## 08S-6.1 IO 感知与 HBM 读写瓶颈：访存而非算力是墙

### GPU 的两层关键存储：HBM 与 SRAM

现代 GPU 上和注意力性能最相关的是两层存储：一层是容量大但相对慢的高带宽显存 HBM（High Bandwidth Memory，就是我们平时说的"显存"，几十 GB 级别），另一层是容量极小但极快、贴着计算单元的片上 SRAM（on-chip SRAM，包括共享内存/寄存器，每个流式多处理器只有几十到上百 KB 级别）。

FlashAttention v1 论文把这套结构总结为一句核心动机：注意力算法应当"IO 感知（IO-aware）"，也就是要"把 GPU 各层存储之间的读与写算进成本里"[^fa1]。这层区分是后面一切的地基：数据只有搬进 SRAM 才能被高速计算，算完若要保留就得写回 HBM，而 HBM 的带宽比 SRAM 低一两个数量级（具体带宽/容量数值随硬件代际变化，属 08S-5 的内容，此处只用"HBM 慢而大、SRAM 快而小"这一定性关系，具体型号数字见 08S-5 且标「待核」）。

一个初学者常见的误解是"GPU 那么强，瓶颈肯定是算不过来"。真相往往相反：一个算子如果每读一个字节只做很少几次浮点运算，那它绝大部分时间都花在等数据从 HBM 搬进来，计算单元反而在空转。这种情形叫访存受限（memory-bound），与之相对的是计算受限（compute-bound）。判定用的是算术强度（arithmetic intensity，每字节访存对应的浮点运算数），这一判据在 08S-5（roofline）里已系统讲过，这里只需记住结论：注意力里有若干步骤天然是访存受限的。

### IO 感知：优化目标从"少算"变成"少搬"

传统写算法时我们数的是浮点运算次数（FLOP）。IO 感知的视角是：在访存受限的场景里，真正该数、该压的是 HBM 读写量（字节数），而不是 FLOP。

这带来一个反直觉但极其重要的结论——为了少搬数据，故意多算一点是划算的。FlashAttention 在反向传播里就故意不保存中间的大矩阵、而是用已有的小结果把它重新算一遍（recomputation），因为"重算"消耗的是便宜的算力，省下的是昂贵的 HBM 往返[^fa1]。CS336 L6 把这条思路作为整节的主线来讲：kernel 优化的第一性目标是减少 HBM 访问[^cs336]。多算换少搬，是本大主题反复出现的母题。

### 标准注意力为什么撞在访存墙上

回忆 L6-03·03-6：注意力对序列长度 N 要先算出分数矩阵

S = Q Kᵀ

再对它做 softmax 得到 P，最后

O = P V

问题出在 S 和 P 都是 N×N 的大矩阵。标准实现会把 S 整个写回 HBM，再从 HBM 读回来做 softmax、写回 P，再读回 P 去乘 V。序列越长，这张 N×N 矩阵越大，它在 HBM 上的一来一回就越贵。于是标准注意力的显存占用和 HBM 读写量都随 N² 增长——这既是"上下文变长就爆显存"的根因，也是"注意力比同规模的矩阵乘（GEMM）慢得多"的根因[^fa1]。

这些 N×N 的中间矩阵在数学上只是过程量，最终我们只要 N×d 的输出 O。把它们物化到 HBM 再读回，纯属为访存墙交的"过路费"。08S-6.3 的 FlashAttention 正是要取消这笔过路费。

#### 来源与时效

- 锚点：FlashAttention v1（Dao, Fu, Ermon, Rudra, Ré, arXiv:2205.14135, 2022）摘要与 §1–3，"making attention algorithms IO-aware"、tiling 减少 HBM↔SRAM 传输、反向 recomputation。核实日期 2026-08-01。[^fa1]
- 锚点：CS336 Spring 2026 L6（Kernels），https://cs336.stanford.edu/ ——"减少 HBM 访问是 kernel 优化第一目标"这一主线。核实日期 2026-08-01。[^cs336]
- 前沿标注：⚙演进快·锚版本 CS336 Spring 2026。HBM/SRAM 的具体容量与带宽随硬件代际变化，本节只用定性关系，具体数值归 08S-5 且标「待核」。
- 交叉核对：memory-bound / compute-bound 与算术强度判据与 08S-5（roofline）一致，两处口径互校。

---

## 08S-6.2 kernel 融合：减少中间张量往返 HBM

### 什么是 kernel，什么是一次 kernel launch

在 GPU 编程里，一个 kernel 就是一段在 GPU 上并行执行的函数。每次让 GPU 跑一个 kernel，叫一次 kernel launch。像 PyTorch 这样的框架里，你写的每个基础算子（一次矩阵乘、一次逐元素加、一次激活函数）通常各自对应一次或多次 kernel launch。

要理解融合，先要理解一个未融合的算子链是怎么花冤枉钱的。假设你要算 `y = relu(x + b)`：一个 kernel 从 HBM 读 x、读 b，算出 `x+b`，把结果写回 HBM；下一个 kernel 再从 HBM 把这个中间结果读回来，做 relu，再写回 HBM。中间那个 `x+b` 的张量，写一次、读一次，全程只为了在两个 kernel 之间传话。

### 融合：把多步塞进一个 kernel，中间量留在片上

kernel 融合（kernel fusion）就是把本来分成多个 kernel 的连续算子合并成一个 kernel：数据一次性从 HBM 读进 SRAM/寄存器，在片上把 `x+b` 和 `relu` 连着算完，只把最终结果写回 HBM。那个中间张量从头到尾没碰过 HBM，于是省掉了一读一写，也省掉了一次 kernel launch 的固定开销。

这直接呼应 08S-6.1 的目标：融合的收益主要不是让计算变快，而是砍掉中间张量往返 HBM 的访存量。对访存受限的算子，砍访存就是砍时间。CS336 L6 与 PyTorch 官方关于 `torch.compile` / 算子融合的说明都把"减少 HBM 往返、减少 kernel 启动次数"列为融合的主要收益来源[^cs336][^torchcompile]。

中间张量往返 HBM 就像每做一道工序就把半成品搬回仓库、下一道工序再搬出来。融合等于把几道工序摆到同一张工作台上一次做完，半成品不进仓库。

### 逐元素算子易融合，注意力却不止是逐元素

最容易融合的是逐元素（element-wise）算子链，因为每个输出只依赖同位置的输入，天然一进一出。编译器（如 PyTorch 的 `torch.compile` 后端 TorchInductor、或 Triton）能自动把这类链融成一个 kernel[^torchcompile]。

它中间夹着两个矩阵乘（QKᵀ 和 PV）和一个沿整行做归约的 softmax。softmax 要先看到一整行的所有分数才能求出那一行的最大值与求和归一化——这是一个跨元素的归约依赖，不能像逐元素算子那样"看一个点算一个点"。所以简单的逐元素融合规则套不到注意力上。要把整条"QKᵀ → softmax → PV"融成一个不落 N×N 矩阵的 kernel，必须换一套能"边分块边增量更新 softmax"的算法——这正是 08S-6.3 要解决的问题，也是 FlashAttention 的技术核心。

#### 来源与时效

- 锚点：CS336 Spring 2026 L6（Kernels，kernel fusion 部分），https://cs336.stanford.edu/ 。核实日期 2026-08-01。[^cs336]
- 锚点：PyTorch 官方文档 `torch.compile` / TorchInductor 关于算子融合与减少 kernel 启动/HBM 往返的说明，https://pytorch.org/docs/stable/torch.compiler.html （具体默认后端行为随 PyTorch 版本演进，本节只取"融合减少中间张量往返 HBM"这一稳定结论；当前默认后端细节标「待核」）。核实日期 2026-08-01。[^torchcompile]
- 前沿标注：⚙演进快·锚版本 CS336 Spring 2026。编译器自动融合能覆盖的算子模式随框架版本变，具体覆盖范围标「待核」。
- 交叉核对：CS336 与 PyTorch 文档对"融合收益来自减少 HBM 往返"口径一致；两者都指出复杂归约（如 softmax）需专门算法而非通用逐元素融合。

---

## 08S-6.3 FlashAttention 分块 + 在线 softmax：不落全注意力矩阵

### 分块（tiling）：把注意力切成能装进 SRAM 的小块

FlashAttention 的做法是把 Q、K、V 沿序列维切成小块（block/tile），每次只把一小块 Q 和一小块 K、V 搬进 SRAM，在片上算出这一小块对应的部分注意力结果，再滑到下一块 K、V。整个过程中，那张 N×N 的完整分数矩阵从不被整体物化到 HBM——它只以一小块一小块的形态短暂活在 SRAM 里，用完即弃[^fa1]。

这就把 08S-6.2 结尾提出的难题落地了：既然不能整行一次看全，那就分块地看，边看边把 softmax 的结果增量更新出来。难点在于 softmax 需要"整行的最大值和求和"，而分块时每次只看到一部分列——怎么在只见过前几块的情况下算出正确的 softmax？答案是在线 softmax。

### 在线 softmax：一边扫一边修正的 softmax

先回忆为什么 softmax 要减最大值。数值稳定的 softmax 是

softmax(x)_i = exp(x_i − m) / Σ_j exp(x_j − m)，其中 m = max_j x_j

减去最大值 m 是为了防止 exp 溢出。麻烦在于：要知道 m，似乎必须先扫过整行；要知道分母，也必须扫过整行。分块处理时我们不想扫完整行才动手。

在线 softmax（Milakov & Gimelshein, 2018）解决的正是这个：它维护一个"到目前为止见过的最大值" m 和一个"到目前为止的指数和" ℓ，每来一个新块就更新它们，并对已经累加的旧结果做一次修正缩放，最终结果与一次性看全整行完全相同[^online]。设处理完前面若干块后当前的运行最大值为 m_old、运行和为 ℓ_old，新到一块其局部最大值为 m_blk、该块内 Σexp(x − m_blk) 为 s_blk，则更新为

m_new = max(m_old, m_blk)

ℓ_new = exp(m_old − m_new) · ℓ_old + exp(m_blk − m_new) · s_blk

其中 `exp(m_old − m_new)` 就是"发现了更大的最大值后，把之前基于旧最大值累加的和重新缩放到新基准"的修正因子。FlashAttention 把这套递推同时套在分母 ℓ 和输出累加器 O 上：每来一块 K、V，就把该块贡献的 P·V 按同样的因子缩放后累加进 O。于是扫完所有 K、V 块时，O 恰好等于对整行做标准 softmax 再乘 V 的结果[^fa1][^online]。

这一步的正确性可以在本机纯用 numpy 验证（不需要 GPU）：把一行分数切成几块，用上面的递推增量算出 softmax，再和 `np.exp(x-x.max())/sum` 的一次性结果对比，两者在浮点误差内完全相等。本库编排清单 也把此项标为 `[验✓]`（在线/分块 softmax 与朴素 softmax 数值等价）。这类等价验证是"分块不改变数学结果"的直接佐证。

### 不落 N×N 矩阵 + 反向重算 + 精确非近似

把分块和在线 softmax 合起来，FlashAttention 得到一个只在 SRAM 里流转小块、绝不把 N×N 分数矩阵整体写回 HBM 的融合 kernel。这样前向的 HBM 读写量从随 N² 增长压到大致随 N 增长（论文表述为"比标准注意力更少的 HBM 访问，且在一定 SRAM 范围内是最优的"[^fa1]）。

反向传播需要用到中间的 P 矩阵，但 FlashAttention 前向并没有把它存下来。这里再次用 08S-6.1 的"多算换少搬"：反向时用保存下来的很小的 softmax 统计量（每行的 m 和 ℓ）把需要的块重新算一遍，而不是从 HBM 读回一张巨大的 P[^fa1]。

最关键的一点、也是初学者最容易误会的：FlashAttention 是精确（exact）注意力，不是近似。它算出的结果和标准注意力在数学上等价，只是访存路径不同——它不像稀疏/线性注意力（见 08S-4）那样为了省算力而改变了注意力的定义[^fa1]。它省的是访存，不是精度。

论文给出的 v1 端到端加速（务必锚 v1、后续版本另计）：BERT-large（序列长 512）约 15% 加速，GPT-2（序列长 1K）约 3× 加速，long-range arena（序列长 1K–4K）约 2.4× 加速[^fa1]。这些是 v1 论文在其测试硬件上的数字，跨硬件/跨版本会变，引用时须连同版本一起报。

#### 来源与时效

- 锚点：FlashAttention v1（arXiv:2205.14135, 2022）摘要与 §3（tiling、online softmax、recomputation、exact attention）；端到端加速数字（BERT-large ~15%、GPT-2 ~3×、LRA ~2.4×）出自其摘要。核实日期 2026-08-01。[^fa1]
- 锚点：在线 softmax 原始出处 Milakov & Gimelshein「Online normalizer calculation for softmax」（arXiv:1805.02867, 2018），运行最大值 + 运行归一化和的单遍递推。核实日期 2026-08-01。[^online]
- 锚点：CS336 Spring 2026 L6，FlashAttention 分块 + 在线 softmax 的讲解与 v1 论文一致。核实日期 2026-08-01。[^cs336]
- 前沿标注：⚙演进快·锚版本 FlashAttention v1（2022）。此处加速数字硬锚 v1；v2/v3 的数字见 08S-6.5，不可混用。
- 本机可验证：在线/分块 softmax 与朴素 softmax 数值等价（本库编排清单 `[验✓]`），纯 numpy 即可坐实"分块不改变数学结果"，不需 GPU。
- 交叉核对：递推公式在 FA v1 §3 与在线 softmax 原论文两处独立成立，口径一致，互为佐证。

---

## 08S-6.4 Triton 编程模型：块级 Python 写 GPU kernel

### Triton 是什么

Triton 是一个开源、嵌入在 Python 里的 GPU kernel 编程语言和编译器，最初由 Philippe Tillet 开发、OpenAI 于 2021 年发布，目标是让研究者不写 CUDA C++ 也能写出高性能的自定义 GPU kernel[^triton][^tritonintro]。在 LM 系统的语境里，它的意义是：像 FlashAttention 这类需要精细控制分块和访存的 kernel，用 Triton 能以远少于 CUDA 的代码量写出来，并且能直接嵌进 PyTorch 的 Python 训练脚本里。

### 块级编程 vs CUDA 的线程级 SIMT

理解 Triton 的钥匙是它和传统 CUDA 编程模型的差别。CUDA 是 SIMT（单指令多线程）模型：你要显式地想"每一个线程各自在做什么"，还要手动管理线程如何协作、如何读写共享内存、如何合并访存。

Triton 换了一个抽象层级：块级（block-level）编程。你写的 kernel 面向的是"块"——一小片数据（其维度通常是 2 的幂），你对整块做向量化操作，而不去操心块内部单个线程怎么分工[^tritonintro]。每次 kernel launch 会派生许多并行的程序实例（program instance，类比 CUDA 的 thread block），每个实例负责处理一个块。块内的线程调度、共享内存分配、访存合并这些琐事，交给 Triton 编译器自动做[^triton][^tritonintro]。

CUDA 像用汇编级的精细度指挥每个工人；Triton 像给出"这批货整块怎么处理"的工序说明，让编译器去排布工人。抽象更高，但对访存密集型 kernel 往往已足够榨出接近手写 CUDA 的性能，而开发速度快得多。

### 核心 API 骨架

Triton kernel 用 `@triton.jit` 装饰一个 Python 函数，函数体里用 `triton.language`（惯例 `import triton.language as tl`）提供的块级操作。最常出现的几个：

- `tl.program_id(axis)`：拿到当前程序实例的编号，用来算出"我这个实例该负责哪一块数据"。
- `tl.load(ptr + offsets, mask=...)`：把一块数据从 HBM 读进片上（对应 08S-6.1 的"搬进 SRAM 才能算"）。
- `tl.store(ptr + offsets, value, mask=...)`：把一块结果写回 HBM。
- `mask`：处理块边界（当数据长度不是块大小整数倍时，屏蔽越界的元素）。

`mask` 参数值得单独点一下，因为初学者最容易在这里踩坑：块大小固定（2 的幂），但真实数据长度往往不是块大小的整数倍，末尾那一块会越界，必须用 `mask` 把越界位置屏蔽掉，否则读到脏数据或越界崩溃。这套 API 的具体签名/新增算子随 Triton 版本演进，写代码时应对照当前版本官方文档，本节只锚定"块级 + program_id + load/store + mask"这一稳定骨架，具体版本号与新算子标「待核」。

FlashAttention 的分块思想（08S-6.3）用 Triton 表达起来非常自然——`program_id` 决定这个实例处理哪一块 Q，循环里用 `tl.load` 逐块搬入 K、V，在片上跑在线 softmax 递推，最后 `tl.store` 写出 O。这也是 CS336 L6 用 Triton 作为教学载体来讲 kernel 的原因：它让"分块 + 融合 + 省访存"这套思想以可读的 Python 呈现[^cs336]。本项需要 GPU 才能真跑（本库编排清单 标 `[验—]`），本报告只讲清编程模型，不要求实机验证。

#### 来源与时效

- 锚点：Triton 官方文档「Introduction / Programming Guide」，https://triton-lang.org/ （块级编程模型、`@triton.jit`、`tl.program_id`/`tl.load`/`tl.store`/`mask`）。核实日期 2026-08-01。[^triton]
- 锚点：OpenAI「Introducing Triton」发布说明（2021 年发布、块级抽象对比 SIMT、由 Philippe Tillet 开发），https://openai.com/index/triton/ 。核实日期 2026-08-01。[^tritonintro]
- 锚点：CS336 Spring 2026 L6，用 Triton 讲 kernel 的教学定位。核实日期 2026-08-01。[^cs336]
- 前沿标注：⚙演进快·锚版本。Triton API 具体签名/新增算子随版本演进，当前版本号与新算子标「待核」；本节只锚稳定骨架。
- 本机限制：Triton kernel 需 GPU，本机不实证（本库编排清单 `[验—]`），仅讲清编程模型。
- 交叉核对：Triton 官方文档与 OpenAI 发布说明对"块级 vs SIMT""编译器自动管理线程/共享内存/访存合并"口径一致。

---

## 08S-6.5 版本演进：v1 → v2（并行/工作划分）→ v3（Hopper/FP8）逐版锚

### 为什么必须逐版锚：硬件与精度绑定极强

FlashAttention 不是一个固定算法，而是一条随 GPU 代际持续重写的 kernel 线。每一版都深度绑定当时的硬件特性（新的张量核心、异步拷贝引擎、低精度格式），所以任何"加速比 / 利用率 / 支持精度"的说法都必须连同版本和测试硬件一起报，否则就是错的。下面逐版给出一手论文坐实的要点与显式 delta，成熟度全篇标 ⚙演进快·逐版演进。

### v1（2022）：奠定 IO 感知 + 分块 + 在线 softmax

FlashAttention v1（Dao et al., arXiv:2205.14135, 2022）确立了整套范式：IO 感知、分块、在线 softmax、反向重算、精确注意力（详见 08S-6.1 与 08S-6.3）。它相对标准注意力已有显著加速，但论文自陈仍未达到优化过的矩阵乘（GEMM）的效率——其前向大约只到设备理论峰值 FLOPs/s 的 30–50%，反向更低[^fa1][^fa2]。这个"还不够快"正是 v2 的动机。

### v2（2023）：更好的并行与工作划分

FlashAttention-2（Dao, arXiv:2307.08691, 2023）标题即点明主题——「Better Parallelism and Work Partitioning」。它做了三件事[^fa2]：

- 减少非矩阵乘（non-matmul）的浮点运算。GPU 的张量核心对矩阵乘极快，但对普通浮点运算（如 softmax 里的缩放）慢得多，所以砍掉多余的非 matmul FLOP 很值。
- 增强并行：即使只有一个注意力头，也把计算沿序列长度维切到不同的线程块（thread block）上并行，从而提高 GPU 占用率（occupancy）。
- 更好的 warp 间工作划分：调整一个线程块内部各 warp 的分工，减少它们之间经由共享内存的通信。

结果（硬锚 v2 论文、A100）：相对 v1 约 2× 加速，前向达到理论峰值的约 50–73%（最高约 73%），反向最高约 63%，端到端训练可达约 225 TFLOPs/s per A100（约 72% 的模型 FLOPs 利用率）[^fa2]。这些数字只对 v2 及其测试硬件成立。

### v3（2024）：面向 Hopper 的异步与低精度

FlashAttention-3（Shah et al., arXiv:2407.08608, 2024，NeurIPS 2024）专门针对 NVIDIA Hopper 架构（如 H100）重写，利用了 v2 时代硬件没有的新能力[^fa3]：

- TMA（Tensor Memory Accelerator）：Hopper 的异步拷贝引擎，让数据搬运和计算重叠。
- warp 特化（warp specialization）的生产者/消费者流水线，以及把两个矩阵乘围绕 softmax 做"乒乓（ping-pong）"调度，让张量核心尽量不空转。
- FP8 低精度：配合非相干处理（incoherent processing，用 Hadamard 变换打散离群值）来控制量化误差。

结果（硬锚 v3 论文、H100）：相对 v2 在典型形状上约 1.5–2× 加速；FP8 变体达到约 H100 FP8 峰值算力的 75% 左右；论文还报告在有离群特征时，其块量化 + 非相干处理比"标准注意力 + 逐张量量化"精度高约 2.6×[^fa3]。这些数字硬绑 v3 + H100 + FP8，不可外推到别的硬件或版本。

### 版本 delta 一览

| 版本 | 一手来源 | 主要新增 | 绑定硬件（论文测试） | 关键数字（仅对该版本+该硬件成立） |
|---|---|---|---|---|
| v1（2022） | arXiv:2205.14135 | IO 感知 + 分块 + 在线 softmax + 反向重算，精确注意力 | 论文所测（含 A100 世代） | 前向约达理论峰值 30–50%；端到端 GPT-2(1K) ~3×、BERT-large(512) ~15%、LRA ~2.4×[^fa1][^fa2] |
| v2（2023） | arXiv:2307.08691 | 减少非 matmul FLOP、跨序列维并行、warp 间工作划分 | A100 | 相对 v1 ~2×；前向峰值约 50–73%（最高 ~73%），反向最高 ~63%；训练可达 ~225 TFLOPs/s per A100[^fa2] |
| v3（2024） | arXiv:2407.08608 | TMA 异步、warp 特化生产者/消费者、ping-pong 调度、FP8 + 非相干处理 | Hopper（H100） | 相对 v2 ~1.5–2×；FP8 约达 H100 FP8 峰值 ~75%；离群场景块量化比逐张量量化精度约高 2.6×[^fa3] |

关于"更新的版本 / 更新的硬件（如 Blackwell 世代）之后是否还有新一版"：本报告只对上述三版有一手论文坐实。是否存在锚定更新硬件的后续版本，属快速演进内容，标「待核」，须按当时时点重新定位一手来源，不在此凭记忆断言。跨版本比数字时，务必核对是否同一硬件、同一序列长度、同一精度——不同前提下的加速比不可直接相比。

#### 来源与时效

- 锚点：FlashAttention v1（Dao, Fu, Ermon, Rudra, Ré, arXiv:2205.14135, 2022）。核实日期 2026-08-01。[^fa1]
- 锚点：FlashAttention-2（Dao, arXiv:2307.08691, 2023）——并行/工作划分、非 matmul FLOP、~2× 与 50–73% 前向峰值、~225 TFLOPs/s per A100。核实日期 2026-08-01。[^fa2]
- 锚点：FlashAttention-3（Shah, Bikshandi, et al., arXiv:2407.08608, 2024, NeurIPS 2024）——Hopper/TMA/warp 特化/ping-pong/FP8 + 非相干处理、约 H100 FP8 峰值 75%、相对 v2 ~1.5–2×、离群场景精度约 2.6×。核实日期 2026-08-01。[^fa3]
- 锚点：CS336 Spring 2026 L6，逐版演进的教学梳理。核实日期 2026-08-01。[^cs336]
- 前沿标注：⚙演进快·逐版演进·锚版本 v1/v2/v3。所有加速比/利用率数字均硬绑"具体版本 + 论文测试硬件 + 精度"，跨版本/跨硬件不可外推。
- 待核：是否存在锚定更新硬件代际（如 Blackwell）的后续 FlashAttention 版本，未凭记忆断言，标「待核」，R4 时点重定位一手来源。
- 冲突记录：本节各版本数字分别取自各自一手论文，未见相互矛盾；跨版本差异均为硬件/前提不同所致，已在表内显式区分前提，不做统一定论。

---

## 一手引用清单

[^fa1]: Tri Dao, Daniel Y. Fu, Stefano Ermon, Atri Rudra, Christopher Ré.「FlashAttention: Fast and Memory-Efficient Exact Attention with IO-Awareness」. arXiv:2205.14135, 2022. https://arxiv.org/abs/2205.14135 （核实 2026-08-01）
[^fa2]: Tri Dao.「FlashAttention-2: Faster Attention with Better Parallelism and Work Partitioning」. arXiv:2307.08691, 2023. https://arxiv.org/abs/2307.08691 （核实 2026-08-01）
[^fa3]: Jay Shah, Ganesh Bikshandi, Ying Zhang, Vijay Thakkar, Pradeep Ramani, Tri Dao.「FlashAttention-3: Fast and Accurate Attention with Asynchrony and Low-precision」. arXiv:2407.08608, 2024（NeurIPS 2024）. https://arxiv.org/abs/2407.08608 （核实 2026-08-01）
[^online]: Maxim Milakov, Natalia Gimelshein.「Online normalizer calculation for softmax」. arXiv:1805.02867, 2018. https://arxiv.org/abs/1805.02867 （核实 2026-08-01）
[^triton]: Triton 官方文档（Introduction / Programming Guide）. https://triton-lang.org/ （块级编程模型、@triton.jit、tl.program_id/tl.load/tl.store/mask；具体 API 签名随版本演进，标「待核」）（核实 2026-08-01）
[^tritonintro]: OpenAI.「Introducing Triton: Open-source GPU programming for neural networks」. 2021. https://openai.com/index/triton/ （核实 2026-08-01）
[^cs336]: Stanford CS336「Language Modeling from Scratch」Spring 2026, Lecture 6（Kernels / Triton / FlashAttention）. https://cs336.stanford.edu/ （核实 2026-08-01）
[^torchcompile]: PyTorch 官方文档「torch.compile / TorchInductor」（算子融合、减少 kernel 启动与 HBM 往返；默认后端行为随 PyTorch 版本演进，标「待核」）. https://pytorch.org/docs/stable/torch.compiler.html （核实 2026-08-01）
