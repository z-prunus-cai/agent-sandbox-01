# L6-08·08S-6 Kernel 与 Triton / FlashAttention
> 基线/核实：2026-07-26 ｜ 先修：L6-03·03-6（注意力/Transformer）、L6-08·08S-5（GPU 内存层次/roofline）、08S-2（FLOP/显存核算） ｜ 一手锚点：CS336 Spring 2026 L6（Kernels, Triton）；FlashAttention v1(2022, arXiv:2205.14135)/v2(2023, arXiv:2307.08691)/v3(2024, arXiv:2407.08608) 论文；Triton 官方文档（triton-lang.org/main） ｜ 成熟度：⚙演进快·锚版本（CS336 Sp26 / FA v1-v3）
>
> 基线 Py3.11.15/np2.4.6/gcc13.3 @2026-07-25

**粒度判定：1 份。** 理由：5 个小主题机制同源，全部围绕「IO 感知 kernel」主线（访存瓶颈 → 融合 → 分块/在线 softmax → Triton 表达 → 版本演进）；按 v2 广度优先·讲解浅的取向，6.5 逐版对齐用一张对照表即可收束，不至过长，无需拆 -a/-b。

**全篇成熟度硬标：`⚙演进快·锚版本 CS336 Sp26 / FA v1-v3`。** 本域半衰期以月/季计，一切结论仅坐实「该锚点、该版本下如此」；框架/方法（Triton vs CUDA、FA 各版）一律不排名、不下优劣定论，只讲机制与版本 delta。凡具体加速比/显存系数/API 名/FP8 精度参数，无一手来源即标「待核」，不凭记忆填。本机无 GPU，涉 kernel 实测处标「实机未验证」。

---

## 08S-6.1 IO 感知与 HBM 读写瓶颈

### 08S-6.1.1 访存主导 vs 算力主导
核心概念：朴素注意力在现代 GPU 上是访存受限（memory-bound）而非算力受限——瓶颈在 HBM 与片上 SRAM 之间反复搬运 N×N 中间量的带宽，而非 matmul 的 FLOP。FA v1 摘要明确把「未考虑 GPU 各级内存间读写」列为此前注意力算法的关键缺失。

直觉：softmax/mask/dropout 这类逐元素算子算术强度低（每读一个字节只做很少运算），落在 roofline 的带宽区（承接 08S-5.3）。

### 08S-6.1.2 GPU 内存层次回顾（承接 08S-5）
核心概念：GPU 内存分级——HBM 容量大、带宽/延迟劣；片上 SRAM（shared memory / L1 附近）容量小但带宽高、延迟低。FA 的整个立论是「把工作留在 SRAM、尽量少碰 HBM」。具体每级容量/带宽数值随 GPU 代际变化（A100/H100 不同），要引用需锚具体型号，此处不落数字（待核·锚型号）。

### 08S-6.1.3 朴素注意力的 IO 复杂度
核心概念：朴素实现显式在 HBM 上物化 S=QKᵀ（N×N）、再读回做 softmax、再读回乘 V，中间张量多次往返 HBM；HBM 访问随序列长度呈 O(N²) 量级增长，成为主成本。

### 08S-6.1.4 IO 感知（IO-aware）算法设计取向
核心概念：以「减少 HBM 访问字节数」为一等优化目标，而非仅数 FLOP。FA v1 标题即 "with IO-Awareness"，把读写次数纳入算法设计与分析（据 SRAM 大小选块大小以最小化 HBM 访问）。

### 08S-6.1.5 算术强度（arithmetic intensity）的桥接
核心概念：算术强度 = FLOP / 访存字节数（桥 08S-5.3 roofline）。注意力的逐元素部分算术强度低 ⇒ 落在带宽受限区 ⇒ 优化方向是提高有效算术强度（融合+分块，少往返 HBM），而非堆更多算力。

#### 来源与时效（核实 2026-07-26；⚙演进快·锚版本 CS336 Sp26 / FA v1-v3）
- 一手：FlashAttention v1（Dao et al. 2022，arXiv:2205.14135）摘要——IO-aware、tiling 减少 HBM↔SRAM 读写、fewer HBM accesses。
- 课程：CS336 Spring 2026 L6「Kernels, Triton」（4/15，Percy）；L2 覆盖 FLOP/memory/arithmetic intensity。两源交叉印证「访存受限 + IO 感知」主线。
- 具体各级内存容量/带宽数值：随 GPU 型号变，未锚型号 ⇒ 待核，不落数。

## 08S-6.2 kernel 融合（fusion）

### 08S-6.2.1 kernel 与 launch 开销
核心概念：kernel 是一次 GPU 上的并行程序发射；逐算子各起一个 kernel，每个都要从 HBM 读入、算完写回 HBM，且每次 launch 有固定开销。算子链越长，往返 HBM 与 launch 次数越多。

### 08S-6.2.2 融合的收益机制
核心概念：把多个算子并入一个 kernel，中间结果留在寄存器/SRAM 不落 HBM ⇒ 省带宽（少往返）+ 省 launch 次数。这是 FA 减少 HBM 访问的手段之一。

### 08S-6.2.3 注意力中的融合点
核心概念：把 QKᵀ → scale → mask → softmax → ×V 融为单一 kernel，S/P 中间矩阵不物化到 HBM。Triton 官方 fused-attention 教程即演示这条融合链（实现的正是 FA v2 算法）。

### 08S-6.2.4 融合的代价/边界
核心概念：SRAM 容量有限 ⇒ 块不能太大；可融算子受限（跨 kernel 依赖、需全局归约的算子难融）；手写/编译复杂度上升。融合是权衡不是免费午餐（不下「总是更好」定论）。

### 08S-6.2.5 与重计算（recomputation）的关系
核心概念：反向传播时不存下整张 S/P 矩阵，而是用前向保存的少量归一统计量重算——省显存换算力（recompute-in-backward）。FA v1 摘要将 recomputation 与 tiling 并列为核心手段。

#### 来源与时效（核实 2026-07-26；⚙演进快·锚版本 CS336 Sp26 / FA v1-v3）
- 一手：FA v1 摘要（tiling + recomputation 减少 HBM 读写）；FA v2 摘要（把非 matmul FLOP 降低、warp 间少走 shared memory 通信）佐证融合边界。
- 官方文档：Triton `getting-started/tutorials/06-fused-attention`——明标「Flash Attention v2 algorithm from Tri Dao」，演示单 kernel 融合。
- 课程：CS336 Sp26 L6 + Assignment 2（自写 Triton FlashAttention2）。
- launch 开销/SRAM 具体字节阈值：无一手数字 ⇒ 待核。

## 08S-6.3 FlashAttention 分块（tiling）+ 在线 softmax

### 08S-6.3.1 分块（tiling）
核心概念：把 Q/K/V 沿序列维切块载入 SRAM，逐块计算局部注意力并增量累积输出，绝不物化完整 N×N 的 S。块大小据 SRAM 容量选定（具体默认块大小随实现/硬件变，待核）。

### 08S-6.3.2 在线 / 流式 softmax（online / streaming softmax）
核心概念：维护每个 query 行的 running max（m）与 running sum（l），每来一个 K/V 块就用旧统计量对已累积输出做缩放再并入新块贡献，边流边归一，最终除以 l。此技法早于 FA（Milakov & Gimelshein 2018 提出 online softmax，⚠仅二手指回），FA 将其嵌入分块注意力。

### 08S-6.3.3 数值稳定性（safe softmax）
核心概念：softmax 前减去 running max（max-subtraction）避免 exp 上溢；分块 + 在线归一在浮点下与全量 softmax 数值等价（差异仅为浮点舍入量级）。

`[验✓]` 本机 numpy 实证（无 GPU，仅验数值等价，非 kernel 性能）：分块在线 softmax 输出 vs 朴素全量 softmax 输出，最大逐元素绝对差 ≈ 5.0e-16（machine epsilon 量级），`np.allclose(...atol=1e-12) → True`。
```python
import numpy as np
rng = np.random.default_rng(0); N,d = 512,64
Q,K,V = (rng.standard_normal((N,d)) for _ in range(3)); scale = 1/np.sqrt(d)
S = (Q@K.T)*scale; S -= S.max(1,keepdims=True)
P = np.exp(S); P /= P.sum(1,keepdims=True); O_naive = P@V
Bk=64; O=np.zeros((N,d)); m=np.full((N,1),-np.inf); l=np.zeros((N,1))
for j in range(0,N,Bk):
    Kj,Vj = K[j:j+Bk],V[j:j+Bk]; Sij=(Q@Kj.T)*scale
    m_new=np.maximum(m,Sij.max(1,keepdims=True)); P_ij=np.exp(Sij-m_new)
    alpha=np.exp(m-m_new); l=alpha*l+P_ij.sum(1,keepdims=True); O=alpha*O+P_ij@Vj; m=m_new
O/=l
print(np.max(np.abs(O_naive-O)), np.allclose(O_naive,O,atol=1e-12))
# → 4.996003610813204e-16 True   (基线 Py3.11.15/np2.4.6 @2026-07-25)
```

### 08S-6.3.4 不物化 S ⇒ 显存降阶
核心概念：因不落 N×N 的 S/P，注意力显存从 O(N²) 降到关于序列长度线性 O(N)（机制级陈述）。具体常数系数不承诺——需具体系数标待核。

### 08S-6.3.5 反向传播：重计算 + 保存归一统计量
核心概念：反向不存 S/P，靠保存的 softmax 归一统计量（logsumexp / L）+ 重算 S 来重建梯度所需量，显存换算力。

### 08S-6.3.6 「精确注意力（exact）」定位
核心概念：FA 是精确注意力（非近似），只改 IO/计算调度、不改数学结果——区别于稀疏/线性注意力（08S-4）的近似路线。FA v1 标题即 "Exact Attention"。

#### 来源与时效（核实 2026-07-26；⚙演进快·锚版本 CS336 Sp26 / FA v1-v3）
- 一手：FA v1 摘要（tiling、exact、O(N²)→线性显存、recomputation）；本机 numpy `[验✓]` 数值等价（补充，不替代多来源比对；实机 kernel 性能未验证）。
- 课程：CS336 Sp26 L6 + Assignment 2 明确以「Triton 实现 FlashAttention2」为练习。
- ⚠仅二手：online softmax 溯源 Milakov & Gimelshein 2018（指回一手论文，未在本轮直取原文）。
- 默认块大小、显存常数系数：待核，不编造。

## 08S-6.4 Triton 编程模型

### 08S-6.4.1 Triton 是什么
核心概念：Triton 是「用于并行编程的语言与编译器」，提供 Python 环境高效编写在 GPU 上跑满吞吐的自定义 DNN kernel（官方文档原述）。由 Philippe Tillet 创立（文档版权署 Philippe Tillet），在 OpenAI 开发并开源。文档站锚 triton-lang.org/main（本轮未见页面显式版本号 ⇒ 版本号待核）。

### 08S-6.4.2 编程抽象层级：块级 vs 线程级
核心概念：Triton 是「Blocked Program, Scalar Threads」——程序员写块（program instance）级逻辑；CUDA 是「Scalar Program, Blocked Threads」（SIMT 线程级手写）。二者是不同抽象取舍，不排名。

辅助：官方文档明列编译器自动负责 coalescing、thread swizzling、prefetching、自动向量化、tensor-core 指令选择、shared memory 分配/同步、异步拷贝调度——即块内线程调度由编译器管。

### 08S-6.4.3 核心原语（概念级）
核心概念：教程/文档出现的原语（以官方为准）：`tl.program_id`（定位当前 program/块）、`tl.load` / `tl.store`（带 mask 的 HBM 读写）、`tl.dot`（块级 matmul）、掩码（如 `tl.where` 做 causal mask）。新式内存描述符 `tl.make_tensor_descriptor` 在当前 fused-attention 教程中出现；`tl.make_block_ptr` 未在该页出现（API 随版本变动 ⇒ 具体签名待核）。

### 08S-6.4.4 与 CUDA 的分工/取舍
核心概念：Triton 换取开发效率与编译器自动优化，CUDA 提供极致底层控制；何时下探 CUDA vs 停在 Triton 取决于需求（与 L6-06 CUDA 主题重叠，此处仅在 LM kernel 语境陈述）。不下优劣定论。

### 08S-6.4.5 本机限制
核心概念：Triton kernel 需 GPU 运行；本机无 GPU ⇒ 不做 Triton 实证，仅概念陈述，标「实机未验证」。

#### 来源与时效（核实 2026-07-26；⚙演进快·锚版本 CS336 Sp26 / FA v1-v3）
- 官方文档（一手）：triton-lang.org/main —— index（语言+编译器定义）、programming-guide ch1（Blocked Program/Scalar Threads vs CUDA；编译器自动优化清单；版权 Philippe Tillet）、tutorials/06-fused-attention（FA v2 实现 + 原语）。
- 课程：CS336 Sp26 L6「Kernels, Triton」+ Assignment 2。两源（文档+课程）交叉印证块级模型与 Triton 用于 FlashAttention。
- 待核：文档站精确版本号、`tl.*` 各 API 精确签名、Triton 治理归属现状（Tillet/OpenAI 之外的基金会托管）。

## 08S-6.5 版本演进：FA v1 → v2 → v3（逐版锚，显式 delta）

### 08S-6.5.1 FA v1（Dao, Fu, Ermon, Rudra, Ré 2022 · arXiv:2205.14135）
核心概念：确立基本盘——IO-aware + tiling + 在线 softmax + recomputation，实现精确注意力且显存线性、减少 HBM 访问。摘要报告端到端加速：BERT-large(512) 约 15%、GPT-2(1K) 约 3×、LRA(1K–4K) 约 2.4×（这些是论文自报的模型级数字，非本机实测）。

### 08S-6.5.2 FA v2（Dao 2023 · arXiv:2307.08691）
核心概念：针对 v1「仅达理论峰值 25–40%」的低利用率，做并行化与工作划分改进。摘要三点 delta：(1) 调整算法减少非 matmul FLOP；(2) 序列长度维并行——单个 head 也跨 thread block 并行以提高 occupancy；(3) 改进 warp 间工作划分，减少经 shared memory 的通信。自报约 2× 于 v1，达 A100 理论 FLOPs/s 的 50–73%（论文数字，锚 A100）。

### 08S-6.5.3 FA v3（Shah, Bikshandi, Zhang, Thakkar, Ramani, Dao 2024 · arXiv:2407.08608）
核心概念：面向 Hopper（H100）架构（针对 v2 在 H100 仅约 35% 利用率）。摘要三条机制：(1) 利用 Tensor Core 与 TMA 的异步能力；(2) warp-specialization 重叠计算与数据搬运（含 block-wise 交错）；(3) FP8 低精度——block quantization + incoherent processing。自报 FP16 约 1.5–2.0×、最高约 740 TFLOPs/s（约 75% 利用率）；FP8 近 1.2 PFLOPs/s、数值误差较 baseline FP8 低约 2.6×（论文数字，锚 H100；FP8 精度实现细节待核）。

### 08S-6.5.4 演进主线：锚硬件代际
核心概念：v3 与具体 GPU 架构（Hopper 的 async/TMA/FP8）强绑定 ⇒ 它是「锚硬件代际」的对象；新架构出现时结论需重锚。v1/v2 主要在 Ampere(A100) 语境立数。三代不排优劣，只是针对不同瓶颈/硬件的 delta。

### 08S-6.5.5 版本差异对照表
> 加速比/利用率均为各论文自报数字（非本机实测，实机未验证）；空缺或不确定项标「待核」。⚙演进快·锚版本。

| 版本 | 年/arXiv | 目标瓶颈 | 关键改动（delta） | 绑定硬件锚 | 自报数字（论文） |
|---|---|---|---|---|---|
| v1 | 2022 / 2205.14135 | HBM 访存 / O(N²) 显存 | IO-aware + tiling + 在线 softmax + recomputation；精确、显存线性 | GPU 通用（A100 语境） | BERT-large 15% / GPT-2 3× / LRA 2.4×（端到端） |
| v2 | 2023 / 2307.08691 | v1 利用率仅 25–40% | 降非 matmul FLOP；序列维跨 block 并行；改 warp 间工作划分减 shared-mem 通信 | A100（Ampere） | ~2× vs v1；A100 峰值 50–73% |
| v3 | 2024 / 2407.08608 | v2 在 H100 仅 ~35% | 异步 Tensor Core/TMA；warp-specialization 重叠计算/搬运；FP8（block quant + incoherent processing） | H100（Hopper） | FP16 1.5–2.0×、~740 TFLOPs/s（~75%）；FP8 ~1.2 PFLOPs/s、误差低 ~2.6×（FP8 细节待核） |

#### 来源与时效（核实 2026-07-26；⚙演进快·锚版本 CS336 Sp26 / FA v1-v3）
- 一手（逐版摘要，交叉核对）：FA v1 arXiv:2205.14135；FA v2 arXiv:2307.08691（arXiv id 已核符）；FA v3 arXiv:2407.08608（已核符，Hopper/FP8）。
- 课程：CS336 Sp26 L6 引 FlashAttention；Assignment 2 指定实现 FA2。文档（Triton 教程）亦标其实现为 FA v2 → 交叉印证 v2 是当前工程主流基线。
- ⚠仅二手（非承重）：Dao-AILab/flash-attention 仓库 README 可佐证各版支持硬件，本轮未直取，指回论文。
- 待核：FP8 量化精度参数、各版默认块大小、除摘要外更细的 kernel 实现数字——无一手即留白，不编造。

---

## 一手引用清单（论文 + 官方文档 + 课程）
1. Dao, Fu, Ermon, Rudra, Ré. *FlashAttention: Fast and Memory-Efficient Exact Attention with IO-Awareness.* 2022. arXiv:2205.14135. https://arxiv.org/abs/2205.14135
2. Dao. *FlashAttention-2: Faster Attention with Better Parallelism and Work Partitioning.* 2023. arXiv:2307.08691. https://arxiv.org/abs/2307.08691
3. Shah, Bikshandi, Zhang, Thakkar, Ramani, Dao. *FlashAttention-3: Fast and Accurate Attention with Asynchrony and Low-precision.* 2024. arXiv:2407.08608. https://arxiv.org/abs/2407.08608
4. Triton 官方文档（triton-lang.org/main）：index；programming-guide ch1 introduction；getting-started/tutorials/06-fused-attention。版权 Philippe Tillet（2020–）。文档站版本号待核。
5. Stanford CS336 *Language Modeling from Scratch*, Spring 2026. Lecture 6「Kernels, Triton」(4/15)；Assignment 2（Triton 实现 FlashAttention2）。https://cs336.stanford.edu/

## 二手（非承重）清单
- Milakov & Gimelshein 2018, *Online normalizer calculation for softmax*（online softmax 溯源；本轮未直取原文，指回一手）。
- Dao-AILab/flash-attention 仓库 README（各版支持硬件的二手佐证，指回论文）。

<!-- 本报告为 _test 测试产出，不入 findings/。核实 2026-07-26；成熟度 ⚙演进快·锚版本 CS336 Sp26 / FA v1-v3。多来源比对：论文一手 + CS336 课程 + Triton 官方文档三角，每项 ≥2 独立源；无一手数字处标待核；实机 kernel 未验证（本机无 GPU），仅在线 softmax 数值等价本机 numpy [验✓]。 -->
