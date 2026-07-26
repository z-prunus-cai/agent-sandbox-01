[TEST][P1] L6-08·大主题08S-6 Kernel/FlashAttention — 造 prompt（⚙演进快）

```text
你是 CS 知识库 Round 4 报告调查员。产出一份 v2 格式报告，覆盖大主题 L6-08·08S-6「Kernel 与 Triton / FlashAttention」。开工前先读：study/cs-kb/report-format.md（v2 格式，硬约束）+ study/cs-kb/brief.md（纪律，尤其 §3 严谨性红线 + 前沿硬标）。本报告是「系统」课主题，不是「模型」课主题——知识对象是 kernel / 访存 / IO 感知 / 版本演进，不是 loss 设计。

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
一、任务与产出格式（v2，严格遵守）
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
- 层级固定：`#` 大主题（=报告标题） → `##` 小主题（=章节） → `###` 相关内容（=每个内容项一节）。
- 每个 `###` 下：先「核心概念：」一段 1–3 句说明（正确、必要处标时效/版本），空行后按需 0–几段辅助说明（直觉 / 最小例子 / 何时用 / 易错点 / 该掌握到什么程度），可省。
- 每个小主题末用 `####` 集中列「来源与时效」，不逐项脚注。
- 抬头（`#` 标题下紧接一行）必须带：
  > 基线/核实：2026-07-25 ｜ 先修：L6-03·03-6（注意力/Transformer）、L6-08·08S-5（GPU 内存层次/roofline）、08S-2（FLOP/显存核算） ｜ 一手锚点：CS336 Spring 2026 L6；FlashAttention v1(2022)/v2(2023)/v3(2024) 论文；Triton 官方文档 ｜ 成熟度：⚙演进快·锚版本（CS336 Sp26 / FA v1-v3）
- 抬头另起一行带可 grep 基线串：`基线 Py3.11.15/np2.4.6/gcc13.3 @2026-07-25`。

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
二、小主题清单与应覆盖的内容项（广度完备，逐项查全，浅而准）
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
本大主题拆为 5 个小主题（沿用 round3b 编号 08S-6.1 ~ 08S-6.5）。每个小主题下的内容项要列全，宁可多列浅讲，不遗漏。

## 08S-6.1 IO 感知与 HBM 读写瓶颈
一句话范围：注意力的墙是访存不是算力。应覆盖：
- 访存主导 vs 算力主导：为何朴素注意力受 HBM 读写带宽限制而非 FLOP。
- GPU 内存层次回顾（承接 08S-5）：HBM（大、慢）vs SRAM/on-chip（小、快）的容量/带宽/延迟落差。
- 朴素注意力的 IO 复杂度：N×N 注意力矩阵在 HBM 上的物化与反复往返（materialize S=QKᵀ、softmax、乘 V）。
- 「IO 感知（IO-aware）」算法设计取向：以减少 HBM 访问字节数为一等优化目标（对照仅数 FLOP 的传统视角）。
- 算术强度（arithmetic intensity）与本主题的联系（桥 08S-5.3）。

## 08S-6.2 kernel 融合（fusion）
一句话范围：把多算子并成一个 kernel，减少中间张量往返 HBM。应覆盖：
- kernel 是什么、launch 开销、逐算子 kernel 各自读写 HBM 的代价。
- 融合的收益机制：中间结果留在寄存器/SRAM 不落 HBM，省带宽 + 省 kernel launch。
- 注意力中的融合点：QKᵀ→scale→mask→softmax→×V 融为一体（正是 FlashAttention 的做法之一）。
- 融合的代价/边界：SRAM 容量约束、可融算子的限制、编译复杂度。
- 与「重计算（recomputation）」的关系：反向传播时重算而非存全矩阵（省显存换算力）。

## 08S-6.3 FlashAttention 分块（tiling）+ 在线 softmax（online / streaming softmax）
一句话范围：分块计算注意力、绝不物化完整 N×N 矩阵，用在线 softmax 保持数值等价。应覆盖：
- 分块（tiling）：把 Q/K/V 按块载入 SRAM，逐块累积输出。
- 在线 softmax（streaming softmax）：running max + running sum 的增量更新，边流边归一。
- 数值稳定性：max-subtraction（safe softmax），分块与全量在浮点下的等价性。
- 不物化 S 矩阵 ⇒ 显存从 O(N²) 降到 O(N)（此为机制级陈述，非承诺具体常数——如需具体系数标「待核」）。
- 反向传播：不存 S/P，靠重计算 + 保存的 softmax 归一统计量（L / logsumexp）重建。
- 「精确注意力（exact，非近似）」定位：区别于稀疏/线性注意力（08S-4）的近似路线——FA 是精确的、只改 IO。

## 08S-6.4 Triton 编程模型
一句话范围：用块级（block-level）Python 写 GPU kernel，编译器管线程内调度。应覆盖：
- Triton 是什么：Python DSL + 编译器，OpenAI 出品；面向 GPU kernel。
- 编程抽象层级：程序员写「块（program / block）」级逻辑，编译器负责块内线程/向量化/内存合并（对照 CUDA 的线程级手写）。
- 核心原语（概念级）：program_id、block pointer / load / store、mask；tl.* 命名空间（具体 API 名以官方文档为准，不确定标「待核」）。
- 与 CUDA 的分工/取舍：开发效率 vs 极致控制；何时用 Triton、何时下探 CUDA（与 L6-06 CUDA 主题重叠，此处只在 LM kernel 语境讲）。
- 本机限制：需 GPU，本机（无 GPU）不实证；只做概念陈述，标「实机未验证」。

## 08S-6.5 版本演进：FA v1 → v2 → v3（逐版锚，显式 delta）
一句话范围：三代各改了什么，版本差异必须显式对齐。应覆盖（每代分别列「针对的瓶颈 + 做的改动」，不排名、只讲 delta）：
- FA v1（Dao et al. 2022）：确立 IO-aware + tiling + 在线 softmax + 重计算的基本盘。
- FA v2（Dao 2023）：并行化与工作划分改进——减少非 matmul FLOP、改进 warp/线程块间的工作分配与并行维度（seq-len 维并行等）。具体改了哪几点，逐条按论文对齐；不确定标「待核」。
- FA v3（2024）：面向 Hopper 架构——利用 async/warp-specialization、TMA、FP8 低精度等新硬件特性。具体机制按论文对齐；FP8 相关精度细节不确定标「待核」。
- 「针对硬件代际」这一演进主线：v3 与具体 GPU 架构强绑定 ⇒ 明确它是「锚硬件代际」的对象。
- 版本差异对照表（v1 / v2 / v3 各自：目标瓶颈 · 关键改动 · 绑定硬件），来源不足的格子标「待核」，不编造。

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
三、前沿纪律（硬性，全篇贯彻）
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
1. 全篇标 `⚙演进快·锚版本 CS336 Sp26 / FA v1-v3`；抬头明写、每个小主题「来源与时效」再复述该状态。此域半衰期以月/季计，一切结论仅坐实「该锚点该版本下如此」。
2. 框架/方法不排名、不作定论：Triton vs CUDA、FA vs 其他 kernel 库、v1/v2/v3 谁「更好」——一律不排名、不下优劣定论；只讲机制与版本演进 delta（各版改了什么、针对什么瓶颈）。
3. 多来源比对强制（≥2 独立源，优先一手）：每个内容项用 ≥2 个独立来源交叉核对，来源取自「论文 + 官方文档 + 课程」三类锚点。版本差异必须显式：FA v1 vs v2 vs v3 各自改了什么，逐版对齐、不糊成一团。来源打架时两边都记、点明分歧、不和稀泥。
4. 不编造 kernel 细节 / 基准数字：具体 kernel 实现细节、加速比 / 显存节省的基准数字、API 名、FP8 精度参数、默认块大小等——有一手来源才写，无则标「待核」。绝不凭记忆填 speedup 数字或「X 倍」。
5. 实机验证不要求：本主题需 GPU，本机无 GPU ⇒ 不做实机 kernel 实证，涉实测处标「实机未验证」。唯一可选的本机 numpy 实证：在线（分块）softmax 与朴素 softmax 的数值等价验证（08S-6.3，`[验✓]`）——做则贴最小脚本 + 真实输出 + 基线串；此为补充，不替代多来源比对。
6. 标时效核实日期：每个小主题「来源与时效」给核实日期（2026-07-25）；论文版本年份、CS336 学期、Triton 文档版本逐一标注；改名/新版单独标。

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
四、取向（排序，冲突时按此裁决）
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
时效 > 正确 > 广度 >（舍）讲解深度。每项浅而准：核心解释 1–3 句 + 辅助 ≤1 段即止，不写长篇机制叙事 / 设计权衡长论。宁可多列内容项浅讲，不遗漏。深度取证是验证纪律（多来源比对），不是叙事篇幅。

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
五、粒度判定（下游自行决定并在报告顶部注明）
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
默认 1 大主题 = 1 报告。本主题 5 个小主题、机制同源（都围绕「IO 感知 kernel」这一主线），倾向 1 份。但若 08S-6.5 版本演进（v1/v2/v3 逐版对齐）取证展开后过长，可考虑拆为「-a 机制（6.1–6.4）/ -b 版本演进（6.5）」两份。请你在报告开头用一行给出判定：「1 份 or 拆 N 份 + 理由」，并据此落盘（1 份 → findings/NNN-<slug>.md；拆分 → NNN-<slug>-a/-b.md）。

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
六、权威锚点清单（多来源比对用，≥2 独立源）
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
课程锚（教学序 + 讲义）：
- Stanford CS336「Language Modeling from Scratch」Spring 2026 · L6（Kernels / Triton）：https://cs336.stanford.edu/ （2026-07-25 复核在架；19 讲 schedule）。
论文锚（一手，逐版）：
- FlashAttention v1（Dao, Fu, Ermon, Rudra, Ré 2022）"FlashAttention: Fast and Memory-Efficient Exact Attention with IO-Awareness"：https://arxiv.org/abs/2205.14135 。
- FlashAttention v2（Dao 2023）"FlashAttention-2: Faster Attention with Better Parallelism and Work Partitioning"：https://arxiv.org/abs/2307.08691 （arXiv id 待你核；如不符以 CS336 引用为准，标「待核」）。
- FlashAttention v3（2024）"FlashAttention-3: Fast and Accurate Attention with Asynchrony and Low-precision"：arXiv id 待核（2024 发布，Hopper/FP8；以论文原文 + CS336 引用交叉定位）。
官方文档锚：
- Triton 官方文档（OpenAI Triton）：https://triton-lang.org/ （版本以核实当日为准，标版本号；tutorials 含 fused attention 示例）。
背景/邻接（非承重，仅补留白 + 指回一手）：
- 08S-5 GPU 内存层次 / roofline（先修）；L6-06 CUDA 编程（重叠，注意勿重复立项，本报告只在 LM kernel 语境讲）。
- FlashAttention 官方仓库 README（Dao-AILab/flash-attention）：可作版本演进 + 支持硬件的二手佐证，指回论文；只靠它支撑的项标 `⚠仅二手`。

要求：每个内容项至少命中上面 ≥2 个独立源（理想：论文一手 + CS336 课程 + Triton 文档三角）。凡涉具体数字/API/参数而无一手来源 ⇒ 标「待核」，不编造。落盘后在报告末尾附一手引用清单（论文 + 官方文档 + 课程）与二手（非承重）清单。
```

已写入 T3 prompt
