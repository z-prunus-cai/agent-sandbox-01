# Round 3a 大主题分解 · 第 9 组「AI 线」

> 基线 Py3.11.15/np2.4.6/gcc13.3 @2026-07-25 ｜ 调查员：第 9 组（AI 线）
> 本组课程：**L5-08 符号/经典 AI** · **L5-02 机器学习** · **L6-03 深度学习及其分支** · **L6-08 AI/LLM 系统**
> 任务：每门课锚定权威教材目录/官方 syllabus，列全「教学单元级」大主题（覆盖全、少重叠、按教学序）。
> 四层划界贯穿全组：**符号（L5-08）vs 统计（L5-02）vs 深度（L6-03）vs 系统（L6-08）**；**L6-08 是"系统"课不是"模型"课**。
> 每主题行格式：`编号 | 大主题 | 一句话范围 | 来源锚点 | 跨课重叠提示`。
> 核实时效：所有锚点均于 **2026-07-25** 复核在架（见文末锚点清单）。

---

## 锚点核实台账（2026-07-25 复核在架）

| 课程 | 权威锚点 | 版本/学期 | URL | 状态 |
|------|----------|-----------|-----|------|
| L5-08 | AIMA（Russell & Norvig, *Artificial Intelligence: A Modern Approach*）目录 | 第 4 版 2020（Global Ed. 2021） | https://aima.cs.berkeley.edu/contents.html | ✅ TOC 已抓取核对 |
| L5-08 | Berkeley **CS188** Intro to AI 讲座表 | Spring 2025 | https://inst.eecs.berkeley.edu/~cs188/sp25/ | ✅ 28 讲已抓取 |
| L5-02 | Stanford **CS229** Machine Learning syllabus | Ng 讲义（syllabus 已核对单元序） | https://cs229.stanford.edu/ | ✅ 单元序已抓取 |
| L5-02 | 辅锚：《ISL/ESL》《PRML》(Bishop)、scikit-learn 官方文档 | ISL 2013/ESL 2009/PRML 2006 | https://scikit-learn.org/stable/ | ✅ 承重辅锚 |
| L6-03 | Goodfellow, Bengio, Courville, *Deep Learning* 目录 | MIT Press **2016**（⚠ 早于 Transformer/diffusion） | https://www.deeplearningbook.org/ | ✅ TOC 已抓取 |
| L6-03 | 现代补锚：Stanford **CS224n**（NLP/Transformer/预训练）、**CS231n**（视觉）、"Attention Is All You Need" | CS224n **Winter 2026**；Vaswani 2017 | https://web.stanford.edu/class/cs224n/ ；https://arxiv.org/abs/1706.03762 | ✅ CS224n Winter 2026 讲座表已抓取 |
| L6-08 | Stanford **CS336「Language Modeling from Scratch」** schedule | **Spring 2026**（Mar–Jun 2026 在架，周一/三 15:00–16:20） | https://cs336.stanford.edu/ | ✅ 19 讲 schedule 已抓取 |
| L6-08 | 论文锚：FlashAttention（Dao 2022/v2 2023/v3 2024）、PagedAttention/vLLM（SOSP 2023）、Megatron-LM 并行 | 见文末 | https://arxiv.org/abs/2205.14135 ；https://arxiv.org/abs/2309.06180 | ✅ 承重论文锚 |

> **前沿硬标声明**：**L6-08 整门课⚙演进极快、内容年年重写**，以 **CS336 Spring 2026** 为参照系；框架/kernel/serving 栈半衰期以月计。下表 L6-08 主题凡涉具体方案（kernel、并行、serving、量化、对齐）一律标 `⚙演进快·锚版本`，实证只坐实"该锚点下如此"，**不排名、不作定论**。

---

## L5-08 · 符号 / 经典 AI（CS-Core 基石）

> 主锚 AIMA 4th ed Part I–III（+ Part IV 边界）；对齐 Berkeley CS188 Sp25 前半。定位：**世界被显式建模成状态/逻辑/约束，用搜索与推理求解**——可解释、可保证完备/最优，但要人建模、不从数据学。

| 编号 | 大主题 | 一句话范围 | 来源锚点 | 跨课重叠提示 |
|------|--------|-----------|----------|--------------|
| 08-1 | 智能体范式与问题形式化 | 理性智能体、环境类型（PEAS）、把任务抽象成状态空间/初始态/后继/目标/代价 | AIMA Ch1–3(setup)；CS188 L1 | 全组入口；与 L5-02/L6-03 的"函数拟合"范式对照，不重叠 |
| 08-2 | 无信息搜索 | BFS/DFS/UCS/IDDFS 的完备性·最优性·时空复杂度四维对比 | AIMA Ch3；CS188 L2 | 与 L3-01 算法（图遍历）机制同源，但此处聚焦 AI 问题求解语义 |
| 08-3 | 启发式（有信息）搜索 | 贪心 vs A\*；可采纳性(admissible)/一致性(consistent) 保证 A\* 最优；启发式设计（松弛、模式数据库） | AIMA Ch3；CS188 L3 | 纯符号；与统计线无重叠 |
| 08-4 | 复杂环境中的搜索 | 局部搜索（爬山/模拟退火/遗传）、非确定性/部分可观测/在线搜索 | AIMA Ch4；CS188（局部搜索） | 局部搜索优化思想与 L5-02 梯度优化对照（离散 vs 连续） |
| 08-5 | 对抗搜索与博弈 | Minimax、**α-β 剪枝**（O(b^d)→O(b^{d/2})）、期望极小极大、MCTS（通往 AlphaGo 的桥） | AIMA Ch5；CS188 L6–7 | MCTS 在 L6-03 深度 RL / AlphaGo 语境复用；此处讲纯搜索版 |
| 08-6 | 约束满足问题 CSP | 回溯 + 前向检查 + 弧相容 AC-3；变量/值排序启发式（MRV/度/LCV） | AIMA Ch6；CS188 L4–5 | 与 L5-04 SAT/求解器邻接；纯符号推理 |
| 08-7 | 命题逻辑与逻辑智能体 | 知识库、蕴含、归结、DPLL/SAT、模型检验 | AIMA Ch7 | 与 L6-02 形式验证（SAT/SMT）强邻接；此处为 AI 推理入门 |
| 08-8 | 一阶逻辑与推理 | FOL 语法语义、合一、前向/后向链、一阶归结 | AIMA Ch8–9 | 与 L5-06 PL 理论（形式系统）邻接，不重叠 |
| 08-9 | 知识表示 | 本体/类别/事件、默认推理、语义网络与描述逻辑 | AIMA Ch10 | 与现代 neuro-symbolic、知识图谱邻接 |
| 08-10 | 自动规划 | STRIPS/PDDL、状态空间 vs 计划空间、规划图（Graphplan） | AIMA Ch11 | 规划 = 搜索+逻辑的合流；与 L6-08 的 LLM+工具+规划前沿呼应 |
| 08-11 | 不确定性下的推理与序贯决策（边界主题） | 贝叶斯网表示/推理、HMM、MDP、价值/策略迭代 | AIMA Ch12–17；CS188 L8–19 | ⚠**重度重叠**：概率论属 L2-03；贝叶斯/HMM 属 L5-02；RL 属 L6-03。此处仅作"符号→概率"的桥，正文深挖归对应课 |

> **划界**：符号 AI **不学、不感知**——讲清"它保证什么（完备/最优）、代价是什么（状态爆炸/需人建模）"。08-11 是四层交界的缝合主题，硬标重叠、避免与 L5-02/L2-03/L6-03 重复立项。

---

## L5-02 · 机器学习（统计线主干 · 浅层+经典+评估纪律）

> 主锚 CS229 syllabus 单元序；辅锚 ISL/ESL/PRML + scikit-learn。定位：**放弃显式规则，从数据拟合函数**；核心张力是偏差-方差 / 泛化 vs 记忆。深度架构留给 L6-03。

| 编号 | 大主题 | 一句话范围 | 来源锚点 | 跨课重叠提示 |
|------|--------|-----------|----------|--------------|
| 02-1 | 监督学习框架与经验风险最小化 | 假设空间、损失函数、ERM，回归(MSE)/分类(交叉熵)统一视角 | CS229 U1–2；ESL Ch2 | 全统计线世界观入口；与 L5-08 显式建模对照 |
| 02-2 | 线性回归与优化基础 | 最小二乘、正规方程闭式解 vs 梯度下降/SGD、LMS 规则 | CS229 U2；ISL Ch3 | GD 与 L6-03 反向传播、L1-04 微积分梯度同源 |
| 02-3 | 分类：逻辑回归与广义线性模型 | 逻辑/softmax 回归、对数几率、GLM、牛顿法、感知机 | CS229 U3；ISL Ch4 | 感知机是 02-8/L6-03 神经网络的原子 |
| 02-4 | 生成式学习 | 高斯判别分析(GDA)、朴素贝叶斯；生成 vs 判别之别 | CS229 U4；CS188 L20 | 朴素贝叶斯与 08-11 贝叶斯网、L2-03 概率重叠，此处为分类器 |
| 02-5 | 支持向量机与核方法 | 最大间隔、软间隔、对偶、核技巧把线性推广到非线性 | CS229 U5；ESL Ch12 | 核方法与 L1-03 线代（内积空间）呼应 |
| 02-6 | 偏差-方差·正则化·模型选择 | 偏差-方差分解、L1(稀疏)/L2(收缩)、交叉验证、特征选择、学习曲线 | CS229 U6；ISL Ch5–6 | **全组最可迁移的一条**；L6-03/L6-08 默认已懂 |
| 02-7 | 树与集成方法 | 决策树 → 随机森林(bagging) → 梯度提升(boosting/GBDT)；表格数据主力 | CS229 U7；ESL Ch9–10,15 | 与 L5-08 决策论树邻接；深度学习不覆盖此块 |
| 02-8 | 神经网络基础与反向传播 | 单/多层感知机、反向传播作为深度学习入口 | CS229 U8；CS188 L22 | **边界主题**：深读归 L6-03（02-8 只到入门） |
| 02-9 | 无监督学习：聚类与密度估计 | k-means、高斯混合(GMM)、期望最大化(EM) | CS229 U9；ESL Ch14 | EM 与 L2-03 概率、02-4 生成模型呼应 |
| 02-10 | 降维与流形学习 | PCA、ICA、因子分析；维度灾难 | CS229 U10；ISL Ch12 | PCA=L1-03 SVD/特征分解的应用；与 L6-03 表示学习对照 |
| 02-11 | 学习理论 | PAC 可学习、VC 维、偏差-复杂度权衡、泛化界 | CS229（学习理论讲义）；ESL Ch7 | 纯统计理论；为 02-6 提供数学根 |
| 02-12 | 评估与度量纪律 | 准确率陷阱、精确率/召回/F1/AUC/混淆矩阵、校准、数据划分 | CS229 U6；scikit-learn metrics 文档 | 贯穿全组；L6-08 评估(08系统级)是其系统化延伸 |

> **划界**：L5-02 讲**浅层+经典+评估纪律**。强化学习 CS229 虽含，但本 KB 归 L6-03 深度 RL / L5-08 MDP，故 L5-02 不单列 RL 主题（呼应 round2 §6 组内 DAG）。

---

## L6-03 · 深度学习及其分支（表示学习 · 架构层）

> 主锚 Goodfellow 目录 Part II（现代实践）；**⚠ Goodfellow 2016 早于 Transformer/diffusion**，架构前沿以 CS224n Winter 2026 / CS231n / Attention 论文补锚。定位：**让模型自己学表示**，架构(CNN/RNN/Transformer)是归纳偏置的载体。默认你已懂过拟合/评估(L5-02)。

| 编号 | 大主题 | 一句话范围 | 来源锚点 | 跨课重叠提示 |
|------|--------|-----------|----------|--------------|
| 03-1 | 前馈网络与反向传播/自动微分 | 深度前馈网、链式法则、计算图、自动微分 | Goodfellow Ch6；CS224n(backprop 讲) | 承接 02-8；是 L6-08 从零实现 LM 的前提 |
| 03-2 | 深度学习的正则化 | L1/L2、Dropout、早停、数据增强、**BatchNorm/LayerNorm** | Goodfellow Ch7 | 02-6 正则思想在深度语境的具体化 |
| 03-3 | 深度模型优化 | SGD→Momentum→Adam、学习率调度、初始化、归一化、残差连接 | Goodfellow Ch8 | "能训深"的工程三大件；与 L6-08 训练系统衔接 |
| 03-4 | 卷积网络 | 卷积/池化、权值共享、经典架构（ResNet 等）、ViT 蚕食视觉 | Goodfellow Ch9；CS231n | 视觉主战场；ViT 与 03-6 Transformer 合流 |
| 03-5 | 序列建模：RNN/LSTM/GRU | 时序递归、状态记忆、梯度消失/爆炸、双向/编码-解码 | Goodfellow Ch10；CS224n L4 | 大多被 03-6 取代；流式/边缘仍见 |
| 03-6 | 注意力与 Transformer | QKV 自注意力、多头、位置编码、缩放点积、掩码(因果 vs 双向) | "Attention Is All You Need" 2017；CS224n L5 | **全线枢纽**：正是 L6-08 要榨干硬件去跑的那个 model |
| 03-7 | 表示学习与嵌入 | 为何深度=自动学层次特征、词/上下文嵌入、迁移、自监督 | Goodfellow Ch15；CS224n(word vectors) | 与 02-10 降维对照（学的 vs 线性的表示） |
| 03-8 | 预训练-微调范式 | 预训练→微调、PEFT/LoRA、提示(prompting) `锚2026现代` | CS224n L7–9 | ⚙较活跃但机制成熟；应用层(RAG/agents)归 L6-08/X-GENAI 边界 |
| 03-9 | 深度生成模型 | 自编码器/VAE、GAN、**扩散模型**、自回归生成 | Goodfellow Ch14,20 + CS231n(diffusion，`⚠2016后补锚`) | 扩散不在 Goodfellow，锚现代课；与 L6-08 采样解码呼应 |
| 03-10 | 深度强化学习 | 策略梯度、DQN、actor-critic；MCTS+深度(AlphaGo) | CS231n/CS234 分支；AIMA Ch22 | 复用 L5-08 MDP/搜索；此处深度函数逼近版 |
| 03-11 | 分支落地：视觉 / NLP / 多模态 | 检测/分割/ViT；NLP 任务谱系；多模态融合 | CS231n；CS224n L1–3,17 | 应用分支总览；系统化跑起来归 L6-08 |
| 03-12 | 实践方法论 | 调试、超参搜索、诊断偏差-方差、性能瓶颈定位 | Goodfellow Ch11 | 与 02-6/02-12 评估纪律衔接 |

> **划界**：L6-03 是"**模型与架构**"层。训练它的分布式工程、服务它的推理栈**属 L6-08**（系统层）。与 L5-02 界：这里默认已懂泛化/评估，专注表示与架构。

---

## L6-08 · AI/LLM 系统（系统层 · ⚙整门课演进极快 · 锚 CS336 Spring 2026）

> 主锚 **CS336 Spring 2026** 19 讲 schedule（按教学序）；论文锚 FlashAttention / PagedAttention-vLLM / Megatron。**这是"系统"课不是"模型"课**——知识对象是显存/并行/调度/kernel/成本/数据/对齐流水线，不是 loss 设计。**全表硬标演进快·锚版本；实证只坐实"该锚点下如此"，不排名、不作定论。**

| 编号 | 大主题 | 一句话范围 | 来源锚点 | 跨课重叠提示 / 前沿硬标 |
|------|--------|-----------|----------|--------------------------|
| 08S-1 | 从零实现 LM 骨架 + tokenization | BPE 分词 → Transformer 前向 → 训练循环 → 采样解码 | CS336 L1（Overview, tokenization） | 复用 03-6 Transformer；此处当"既定物"跑起来。`⚙锚 CS336 Sp26` |
| 08S-2 | 资源核算与工程 | einops、参数/FLOP/显存预算、PyTorch 系统级用法 | CS336 L2（PyTorch, resource accounting） | 系统度量学入口。`⚙锚 CS336 Sp26` |
| 08S-3 | 架构与超参的系统取舍 | 架构变体、norm/激活/宽深选择对吞吐-收敛的影响 | CS336 L3（Architectures, hyperparameters） | 与 03-2/03-3 交界，但视角=系统成本非建模。`⚙演进快` |
| 08S-4 | 注意力变体与 MoE | 稀疏/线性注意力、状态空间(Mamba 类)、混合专家(MoE) | CS336 L4 | ⚙**活跃研究未定型**，标状态不排名。`⚙演进快·锚版本` |
| 08S-5 | GPU/TPU 硬件与内存层次 | HBM vs SRAM、roofline、算术强度、compute- vs memory-bound | CS336 L5 | 与 L6-06 HPC/GPU **强重叠**：此讲 LM 语境，勿重复立项。`⚙锚硬件代际` |
| 08S-6 | Kernel 与 Triton / FlashAttention | IO 感知融合 kernel、减少 HBM 读写、Triton 写 kernel | CS336 L6；FlashAttention v1(2022)/v2(2023)/v3(2024) | ⚙**kernel 逐版本演进**，锚具体版本。与 L6-06 CUDA 重叠。`⚙演进快·锚版本` |
| 08S-7 | 并行策略 | 数据/张量/流水线/序列并行、ZeRO/FSDP 分片、通信-计算重叠 | CS336 L7–8；Megatron-LM | ⚙具体 ZeRO 阶段随框架变；与 L5-01/L6-05 分布式通信呼应。`⚙演进快·锚版本` |
| 08S-8 | 缩放定律 | compute-optimal、参数/数据/算力配比（Chinchilla 类） | CS336 L9,11（Scaling laws） | ⚙经验规律随代际修订，锚论文时点。`⚙演进快` |
| 08S-9 | 推理 / serving | KV cache、continuous batching、**PagedAttention**、投机解码、量化(INT8/FP8/GPTQ/AWQ)、TTFT/TPOT/吞吐 SLO | CS336 L10；PagedAttention/vLLM SOSP 2023 | ⚙**框架排名每季翻盘**——只写机制不排名(vLLM/TRT-LLM/SGLang/TGI)。decode=memory-bound 是钥匙。`⚙演进快·锚版本` |
| 08S-10 | 评估（系统视角） | 评测方法学、基准、正确性/性能联合评估 | CS336 L12（Evaluation） | 是 02-12 评估纪律的 LM 系统化延伸。`⚙演进快` |
| 08S-11 | 数据工程 | 数据源、过滤、去重、混配、合成数据 | CS336 L13–14 | 与 L6-04/L6-05 数据管线邻接；训练前置流水线。`⚙演进快` |
| 08S-12 | 训练后与对齐 | SFT / RLHF / RLVR、对齐、多模态接入 | CS336 L15–17（Mid/post-training, alignment） | ⚙**最不稳定**；RLHF 复用 03-10 RL；应用层(RAG/agents/prompt)划归 X-GENAI 仅登记，本课不吞并。`⚙演进快·锚版本` |

> **划界（关键）**：L6-08 把模型当既定物，研究**系统层怎么把它跑经济**。与 L6-06 高性能计算/GPU 编程强重叠——**避免重复立项**：L6-08 讲 LM 系统语境，L6-06 讲通用并行/CUDA。生成式 AI 应用层(RAG/agents/prompt) 已在 ledger 定为 `X-GENAI 仅登记·短半衰期`，本课边界明确（系统 vs 应用）。

---

## 建议大主题数：K

| 课程 | 建议 K | 说明 |
|------|--------|------|
| L5-08 符号/经典 AI | **11**（其中 08-11 为重叠边界主题，正文可压缩为"桥"） | 覆盖 AIMA Part I–III 全 + Part IV 边界 |
| L5-02 机器学习 | **12** | 覆盖 CS229 监督/生成/核/集成/无监督/降维/理论/评估全谱 |
| L6-03 深度学习 | **12** | Goodfellow 现代实践 + Transformer/扩散/RL/多模态现代补锚 |
| L6-08 AI/LLM 系统 | **12**（全部硬标演进快·锚 CS336 Sp26） | 严格对齐 CS336 Spring 2026 19 讲教学序 |
| **合计** | **47** | 四层递进：符号 11 · 统计 12 · 深度 12 · 系统 12 |

---

## 一手锚点清单（核实 2026-07-25）

- AIMA 4th ed (2020) 目录：https://aima.cs.berkeley.edu/contents.html —— Part I–VII，28 章已抓取核对。
- Berkeley CS188 Spring 2025 讲座表：https://inst.eecs.berkeley.edu/~cs188/sp25/ —— 28 讲已抓取（搜索→CSP→博弈→MDP/RL→概率/贝叶斯网→ML→Transformer）。
- Stanford CS229 syllabus：https://cs229.stanford.edu/ —— 单元序已抓取（监督→生成→SVM→评估→集成→NN→无监督→降维→RL）。
- Goodfellow *Deep Learning* (MIT Press 2016) 目录：https://www.deeplearningbook.org/ —— Part I–III，20 章已抓取（⚠早于 Transformer/diffusion）。
- Stanford CS224n Winter 2026 讲座表：https://web.stanford.edu/class/cs224n/ —— 19 讲已抓取（词向量→反传→RNN→Transformer→预训练→后训练→PEFT→agents/RAG→评估→推理→多模态）。
- Stanford CS231n：视觉架构/检测/分割/扩散补锚。
- "Attention Is All You Need" (Vaswani et al., NeurIPS 2017)：https://arxiv.org/abs/1706.03762 —— Transformer 原论文。
- Stanford CS336「Language Modeling from Scratch」Spring 2026 schedule：https://cs336.stanford.edu/ —— 19 讲已抓取（tokenization→资源核算→架构→注意力变体/MoE→GPU/TPU→kernel/Triton→并行×2→缩放律→推理→评估→数据→SFT/RLHF/RLVR→对齐/多模态）。
- FlashAttention (Dao et al. 2022 / v2 2023 / v3 2024)：https://arxiv.org/abs/2205.14135 。
- PagedAttention / vLLM (Kwon et al., SOSP 2023)：https://arxiv.org/abs/2309.06180 。
- Megatron-LM 并行论文（张量/流水线并行）。

## 辅锚（承重辅助，非一手）

- ISL/ESL（统计学习导论/要素）、Bishop *PRML* 2006：L5-02 章节序辅锚。
- scikit-learn 官方文档 https://scikit-learn.org/stable/ ：L5-02 模型/度量一手实现辅锚。

## ⚠ 留白 / 待 R4 时点重核

- L6-08 全部主题：FlashAttention/量化/serving 框架的当前 SOTA、并行策略默认参数、CS336 具体作业版本——R4 取证时按当时时点重新核实并硬标，本表不落定论。
- AIMA/Goodfellow 具体页码、CS229/CS224n/CS231n 当学期 syllabus 细节——R4 逐条取证时定位到章节。
