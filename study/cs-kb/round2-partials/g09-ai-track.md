# 第 9 组「AI 线」· Round 2 深挖定方向

> 基线 Py3.11.15/np2.4.6/gcc13.3 @2026-07-25 ｜ 调查员：第 9 组（AI 线）
> 本组课程：**L5-08 符号/经典 AI**（R1新增·CS-Core）｜**L5-02 机器学习**｜**L6-03 深度学习及其分支**｜**L6-08 AI/LLM 系统**（R1新增·硬标演进快）
> 定位：这是一条 **符号 → 统计 → 深度 → 系统** 的四段递进主轴。L5-08 是 CS2023 AI KA 里"人人必修"的经典基石；L5-02/L6-03 是统计/表示学习主干；L6-08 是**"系统"课不是"模型"课**——教怎么把 LM 从零跑起来、训得动、服务得起。
> 产出将合并进 `round2-deep-dive.md`。核实时效 2026-07-25。

---

## §0 · 一句话组心智模型

> AI 这条线不是一门课的四种口味，而是**四个抽象层**：
> - **L5-08 符号 AI**：世界被显式建模成状态/逻辑/约束，用**搜索与推理**求解——可解释、可保证最优/完备，但要人写模型，不从数据学。
> - **L5-02 机器学习**：放弃显式规则，从数据里**拟合函数**；核心张力是偏差-方差 / 泛化 vs 记忆。
> - **L6-03 深度学习**：让模型**自己学表示**（representation learning），架构（CNN/RNN/Transformer）是归纳偏置的载体。
> - **L6-08 AI/LLM 系统**：模型确定后，**工程问题主导**——显存、并行、吞吐、延迟、成本。这里的知识对象是 kernel/调度/并行策略，不是 loss 函数。
>
> 递进关系不是取代而是**分层复用**：搜索仍活在 AlphaGo 的 MCTS、规划器、约束求解里；ML 的评估/正则纪律贯穿深度学习；深度学习的 Transformer 正是 L6-08 要榨干硬件去跑的那个 model。

---

## §1 · 组内选型 / 对比表

### 表 1 · 符号 AI vs 统计学习：两大范式分账（本组主轴的第一刀）

| 维度 | 符号/经典 AI（L5-08） | 统计/机器学习（L5-02、L6-03） |
|------|----------------------|-------------------------------|
| 知识来源 | 人显式编码（逻辑/规则/状态转移/约束） | 从数据中拟合 |
| 核心计算 | **搜索 + 推理**（图搜索、逻辑演绎、约束传播） | **优化**（最小化损失，梯度下降为主） |
| 可解释性 | 高（解路径/证明可读） | 低→中（深度模型近黑箱） |
| 最优性保证 | 可有（A\* 可采纳启发式下最优；线性规划最优） | 一般无（局部最优/经验风险最小化） |
| 数据需求 | 低（不靠数据，靠建模） | 高（尤其深度学习） |
| 失效模式 | 状态空间爆炸、建模不全 | 过拟合、分布漂移、幻觉 |
| 典型对象 | BFS/DFS/UCS/A\*、Minimax+αβ、CSP、STRIPS 规划、命题/一阶逻辑 | 线性/逻辑回归、树与集成、SVM、MLP/CNN/RNN/Transformer |
| 仍活在何处 | MCTS（AlphaGo）、SAT/SMT 求解、调度/路由、规划器 | 感知、语言、生成、几乎所有现代 AI 产品 |

> 教学要点：不要把符号 AI 当"过时的旧 AI"讲。CS2023 把 search 列为 **CS-Core（人人必修）**；现代混合系统（neuro-symbolic、LLM+工具+搜索）正是两条线的合流。边界一句话：**符号 = 会推理不会感知，统计 = 会感知不会保证**。

### 表 2 · 经典 ML 各模型适用对比（L5-02 内部选型）

| 模型 | 归纳偏置 / 假设 | 擅长 | 短板 | 关键超参 |
|------|----------------|------|------|----------|
| 线性 / 逻辑回归 | 线性可分 / 对数几率线性 | 可解释、基线、系数即特征重要性 | 非线性关系差 | 正则强度 λ（L1/L2） |
| 决策树 | 轴对齐分段常数 | 可解释、无需缩放、混合特征 | 单树高方差易过拟合 | 深度、叶子最小样本 |
| 随机森林 / GBDT | 树集成（bagging / boosting） | **表格数据王者**、鲁棒、少调参 | 弱外推、模型大 | 树数、学习率、深度 |
| SVM（核） | 最大间隔 + 核技巧 | 中小样本高维、清晰间隔 | 大数据慢、核/参数敏感 | C、核、γ |
| kNN | 局部相似 | 零训练、非参 | 维度灾难、推理慢 | k、距离度量 |
| 神经网络 MLP | 分层非线性组合 | 大数据、非结构化 | 需大数据/算力、调参难、弱解释 | 层宽深、lr、正则 |

> 选型心法：**表格/中小数据先 GBDT（xgboost/lightgbm）**，非结构化（图像/文本/音频）才上深度学习。SVM 在小样本高维仍有位置。这条"先上树集成"的经验是 L5-02 最实用的一条方向。

### 表 3 · CNN vs RNN vs Transformer（L6-03 架构选型，深度学习的核心对比）

| 维度 | CNN | RNN / LSTM | Transformer |
|------|-----|------------|-------------|
| 归纳偏置 | 局部性 + 平移等变（权值共享） | 时序递归 + 状态记忆 | 无强空间先验，靠**自注意力**全局建模 + 位置编码 |
| 并行性 | 高（层内并行） | **低**（时间步串行）→ 训练慢 | **高**（序列内全并行）→ 可大规模预训练 |
| 长程依赖 | 受感受野限制 | 理论可，实际梯度消失/爆炸 | O(1) 路径长度，强 |
| 复杂度 | O(n·k)（k=核） | O(n)（串行） | O(n²·d)（序列长 n 的平方，长上下文痛点） |
| 参数/数据 | 参数高效，中等数据 | 中等 | 参数/数据饥渴，规模效应显著 |
| 现在主战场 | 视觉（仍强，ViT 在蚕食）、信号 | 大多被 Transformer 取代（流式/边缘仍见） | NLP 全面、视觉（ViT）、多模态、语音——**事实标准** |

> 边界：Transformer 的 O(n²) 是长上下文的根本瓶颈，催生 FlashAttention（省显存的 IO-aware kernel）、稀疏/线性注意力、状态空间模型（Mamba 类）等——这些正是 L6-03 前沿与 L6-08 系统的交界。

### 表 4 · 训练系统 vs 推理系统：关注点分账（L6-08 内部第一刀）

| 维度 | 训练系统 | 推理 / serving 系统 |
|------|----------|---------------------|
| 目标函数 | 吞吐（tokens/s·设备）、能收敛 | 延迟（TTFT/TPOT）+ 吞吐 + 成本，SLO 约束 |
| 显存主项 | 参数 + 梯度 + 优化器状态 + 激活 | 参数 + **KV cache**（随序列/并发增长） |
| 并行策略 | 数据 / 张量 / 流水线 / 序列 / ZeRO 分片 | 张量并行为主 + 副本扩展 |
| 关键技术 | 混合精度、梯度累积/检查点、重计算、通信-计算重叠 | **KV cache**、continuous batching、PagedAttention、投机解码、量化 |
| 计算特征 | compute-bound（大 batch GEMM） | prefill 阶段 compute-bound，**decode 阶段 memory-bound**（逐 token） |
| 代表工具 | Megatron-LM、DeepSpeed、FSDP、JAX/XLA | vLLM、TensorRT-LLM、SGLang、TGI |

> **这张表是 L6-08 的定位声明**：它不是"再讲一遍 Transformer"，而是把模型当既定物，研究**系统层怎么把它跑经济**。decode 是 memory-bound 这一条，是理解所有推理优化（量化、投机解码、批处理）的钥匙。

---

## §2 · 逐课重点方向 + 边界

### L5-08 符号 / 经典 AI（R1新增·CS-Core·P1，AI 主攻可升 P0）
重点方向（4–5）：
1. **无信息搜索**：BFS/DFS/UCS/IDDFS 的完备性、最优性、时空复杂度四维对比表——这是搜索一切的骨架。
2. **启发式（有信息）搜索**：贪心 vs A\*；**可采纳性（admissible）与一致性（consistent）**如何保证 A\* 最优；启发式设计（松弛问题、模式数据库）。
3. **对抗搜索**：Minimax + **α-β 剪枝**（最优排序下复杂度从 O(b^d) 降到 O(b^{d/2})）；期望极小极大；MCTS 作为通往现代（AlphaGo）的桥。
4. **约束满足 CSP**：回溯 + 前向检查 + 弧相容 AC-3；变量/值排序启发式（MRV、度、LCV）。
5. **知识表示与推理 KRR + 规划**：命题/一阶逻辑、归结；STRIPS/PDDL 规划、状态空间 vs 计划空间。
边界：符号 AI **不学、不感知**；讲清"它保证什么（完备/最优）、代价是什么（爆炸/需人建模）"，并显式点出它与 §1 表 1 统计线的分工与合流（neuro-symbolic、LLM 调用搜索/工具）。

### L5-02 机器学习（P1，AI 主攻可升 P0）
重点方向（5–6）：
1. **监督学习框架与损失**：从经验风险最小化出发，回归（MSE）/分类（交叉熵）统一视角。
2. **线性模型 + 正则**：线性/逻辑回归、L1（稀疏）/L2（收缩）、闭式解 vs 梯度下降。
3. **偏差-方差 / 过拟合 / 泛化**：训练-验证-测试划分、交叉验证、学习曲线——**这是 ML 的世界观，比任何单模型都重要**。
4. **树与集成**：决策树 → 随机森林（bagging）→ 梯度提升（boosting），表格数据主力。
5. **SVM 与核技巧**：最大间隔、软间隔、核方法把线性推广到非线性。
6. **神经网络基础 + 评估**：单/多层感知机、反向传播作为深度学习的入口；分类/回归评估指标（准确率的陷阱、精确率/召回/F1/AUC/混淆矩阵）。
边界：L5-02 讲**浅层 + 经典 + 评估纪律**；深度架构留给 L6-03。评估与正则化的纪律是全组最可迁移的部分。

### L6-03 深度学习及其分支（P2）
重点方向（4–6）：
1. **反向传播 + 自动微分 + 优化技巧**：链式法则、计算图；SGD→Momentum→Adam；学习率调度、**BatchNorm/LayerNorm、残差连接、Dropout**（能训深的三大件）。
2. **表示学习**：为什么深度=自动学层次化特征；嵌入（embedding）。
3. **三大架构分账**：CNN（视觉）/ RNN-LSTM（时序）/ **Transformer + 自注意力**（当代主线）——见 §1 表 3。
4. **Transformer 深读**：QKV 注意力、多头、位置编码、缩放点积、掩码（因果 vs 双向）。
5. **分支落地**：→ NLP（预训练-微调范式）、→ 视觉（ViT/检测/分割）、→ 强化学习（策略梯度/DQN/actor-critic）。
边界：L6-03 是"**模型与架构**"层；训练它的分布式工程、服务它的推理栈属 L6-08。与 L5-02 的界：这里默认你已懂过拟合/评估，专注表示与架构。

### L6-08 AI/LLM 系统（R1新增·P2·**硬标演进极快**）
重点方向（4–6）：
1. **从零实现 LM**：tokenizer（BPE）→ Transformer 前向 → 训练循环 → 采样解码（CS336 主线）。
2. **训练系统与并行**：数据/张量/流水线/序列并行、ZeRO/FSDP 分片、混合精度、梯度检查点/重计算、通信-计算重叠。
3. **推理 / serving**：**KV cache**、continuous batching、**PagedAttention**（vLLM）、投机解码、量化（INT8/FP8/GPTQ/AWQ）；TTFT/TPOT/吞吐 SLO。
4. **kernel 与 IO 感知**：**FlashAttention** 系列（把注意力做成省显存、减少 HBM 读写的融合 kernel）；GPU 内存层次（HBM vs SRAM）。
5. **系统度量学**：roofline、compute-bound vs memory-bound（prefill vs decode）、算术强度。
边界（**关键**）：这是**系统课不是模型课**——知识对象是显存/并行/调度/kernel/成本，不是 loss 设计。**演进极快**：必须锚具体版本（课程学期、框架版本、论文），实证只能坐实"某锚点下如此"，**不作定论**；工具排名/最佳实践半衰期以月计。

---

## §3 · 一手源候选 + 本机 Python 验证点

### 一手源候选（腿①/腿②）
| 课程 | 一手教材 / 论文（腿①） | 一手课程 / 源码（腿②候选） |
|------|------------------------|------------------------------|
| L5-08 | **AIMA**（Russell & Norvig《Artificial Intelligence: A Modern Approach》第 4 版，2020；Global Ed. 2021）——搜索/CSP/逻辑/规划的规范教材 | **Berkeley CS188**（Pacman 项目，搜索/CSP/对抗/MDP）；**MIT 6.034**；AIMA 配套 `aima-python` 参考实现 |
| L5-02 | **Stanford CS229** 讲义（Ng）；**《ISL》/《ESL》**（统计学习）；Bishop《PRML》 | **scikit-learn** 官方文档 + 源码（模型对比一手实现）；CS229 problem sets |
| L6-03 | **《Deep Learning》**（Goodfellow, Bengio, Courville 2016）；**"Attention Is All You Need"**（Vaswani et al., NeurIPS 2017）——Transformer 原论文 | **Stanford CS231n**（视觉）/ **CS224n**（NLP）；PyTorch 官方 tutorials；nanoGPT（Karpathy）教学实现 |
| L6-08 | **Stanford CS336「Language Modeling from Scratch」**（Spring 2026 已开，Mar 30–Jun 10 2026，讲义+作业在 GitHub `stanford-cs336`）；**FlashAttention 论文**（Dao et al. 2022 / v2 2023 / v3 2024）；**PagedAttention/vLLM**（SOSP 2023）；**Megatron-LM** 并行论文 | CMU 11-868 LLM Systems；vLLM 源码；nanoGPT / llm.c（从零训练教学实现） |

### 本机 Python 验证点（腿③，R4 落地清单）
已在本机跑通的两条锚点（真实输出，基线 np2.4.6 @2026-07-25）：
- **numpy 手写梯度下降 vs 闭式最小二乘**：200×3 线性回归，GD 2000 步 lr=0.1，`max|GD−OLS| = 1.998e-15` —— 梯度下降收敛到闭式解，验证优化正确性。用于 L5-02 / L6-03 反向传播入口。
- **小 attention 数值验证**：手写 `softmax(QK^T/√d)V`，T=3,d=4，**注意力行和=1 成立、因果上三角掩码后为 0 成立**，输出形状 (3,4) —— 验证缩放点积注意力与因果掩码语义。用于 L6-03 Transformer / L6-08 从零实现 LM。

R4 候选验证点（按课）：
- **L5-08**：纯 Python 实现 BFS/DFS/UCS/A\* 在网格/8-puzzle 上模拟，对比扩展节点数验证"A\* 在可采纳启发式下最优且更省"；实现 Minimax+αβ 对比剪枝前后访问节点数（验证 O(b^d)→O(b^{d/2})）；AC-3 解数独/地图着色 CSP。
- **L5-02**：**需 `pip install scikit-learn`（不随容器持久化）**，跑同一数据集上 逻辑回归/RandomForest/GBDT/SVM 的准确率+训练时间对比，坐实"表格先上树集成"；numpy 手写梯度下降/正则（L1 稀疏 vs L2 收缩）。
- **L6-03**：numpy 手写两层 MLP 反向传播（对照 `scipy.optimize.check_grad` 或有限差分校验梯度）；多头注意力数值展开。
- **L6-08**：小规模 KV-cache 前向的显存/耗时对比（有 cache vs 每步重算，验证 decode 复用）；BPE tokenizer 最小实现；roofline 概念用 GEMM 尺寸扫描做算术强度示意。**所有 L6-08 实证须硬标"锚 xx 版本、非定论"。**

---

## §4 · 优先级校准 + 若 AI 设主攻方向的数学上调建议

| 课程 | 现优先级 | 校准建议 |
|------|----------|----------|
| L5-08 符号 AI | ⭐P1 | 维持 P1（CS-Core 基石）；**若走符号/规划/求解方向**可局部 P0 |
| L5-02 机器学习 | P1 | **AI 主攻则升 P0**——它是深度学习/LLM 的世界观地基（评估、正则、泛化） |
| L6-03 深度学习 | P2 | AI 主攻则升 **P1**；是 L6-08 的模型前提 |
| L6-08 AI/LLM 系统 | P2 | 维持 P2 + **硬标演进快**；但对"系统方向+AI"读者是旗舰，兴趣驱动可升 P1 |

**若 AI/ML 设为主攻方向，对数学地基的上调建议（呼应 round1-map §2 B2/B3）**：
- **L2-03 概率论与数理统计 → P0**（round1-map B3 已预留此升档）：ML 的损失（最大似然/交叉熵）、贝叶斯、评估（假设检验/置信）、生成模型全建立在概率上。**这是 AI 主攻最该先补的一条。**
- **L1-03 线性代数 → 强化 P0 权重**：矩阵乘法、特征分解/SVD、向量空间是神经网络前向/反向、注意力、降维（PCA）的语言。
- **L1-04 微积分 → 保持 P1 但锚定为 AI 关键路径**（round1-map B2 已标枢纽↑）：梯度、链式法则、多元微积分是反向传播的全部数学。建议在 AI 主攻视图里视同 P0，不可后置。
- 递进落点：**概率 + 线代 + 微积分** 三者齐备才解锁 L5-02→L6-03→L6-08 的无痛推进；缺概率最伤（评估与损失无根）。

---

## §5 · 前沿硬标（L6-08 专项，纪律：演进极快·锚版本·不作定论）

- **L6-08 整体硬标**：⚙ **演进极快，内容年年重写**。基线锚点：**Stanford CS336 Spring 2026**（Mar 30–Jun 10 2026，一手课程页/GitHub 确认在架）为参照系；框架/kernel/serving 栈半衰期以月计。
- **须锚版本、不作定论的对象**：
  - **推理框架排名**（vLLM / TensorRT-LLM / SGLang / TGI）——性能与特性每季度翻盘，报告只写机制（KV cache、continuous batching、PagedAttention），**不排名、不给"最佳"结论**。
  - **注意力 kernel**：FlashAttention v1(2022)/v2(2023)/v3(2024) 演进；线性/稀疏注意力、状态空间模型（Mamba 类）——标"活跃研究，未定型"。
  - **量化 / 投机解码 / 并行策略**：具体方案（GPTQ/AWQ/FP8、投机解码变体、ZeRO 阶段）随硬件（GPU 代际）变化，锚"某参考硬件+框架版本"。
  - **生成式 AI 应用层（RAG/agents/prompt）**：已在 ledger 定为 `X-GENAI 仅登记·短半衰期·未定型`——L6-08 报告**不吞并**它，边界明确（系统 vs 应用）。
- 实证纪律：L6-08 的本机实证**只坐实"基线锚点下如此"**（如 KV cache 复用省算的机制演示），绝不外推为"当前最优做法"。

---

## §6 · 组间接口（供 round2-deep-dive.md 编排）
- **上游数学**：L1-03 线代 / L1-04 微积分 / L2-03 概率（AI 主攻须上调，见 §4）。
- **邻接组**：L6-06 高性能计算 / GPU 编程（与 L6-08 kernel/并行强重叠——**避免重复立项**，L6-08 讲 LM 系统语境、L6-06 讲通用并行/CUDA）；L6-04/L6-05 数据/分布式系统（训练数据管线、分布式通信）；L5-10 负责任 AI（SEP，AI 的伦理横切）。
- **组内 DAG**：L5-08 与 L5-02 可并行（两范式互不依赖）；L5-02 → L6-03 → L6-08 严格递进（模型地基 → 架构 → 系统）。

---

## §7 · 源清单

### 一手（教材 / 论文 / 官方课程页）
[^1]: Russell & Norvig, *Artificial Intelligence: A Modern Approach*, 4th ed., Pearson 2020（Global Ed. 2021）——搜索/CSP/逻辑/规划规范教材。｜核实 2026-07-25
[^2]: Berkeley **CS188** Intro to AI（Pacman 项目：搜索/CSP/对抗/MDP/RL）https://inst.eecs.berkeley.edu/~cs188/ ｜核实 2026-07-25
[^3]: Stanford **CS229** Machine Learning（Ng 讲义）https://cs229.stanford.edu/ ；scikit-learn 官方文档 https://scikit-learn.org/stable/ ｜核实 2026-07-25
[^4]: Goodfellow, Bengio, Courville, *Deep Learning*, MIT Press 2016 https://www.deeplearningbook.org/ ｜核实 2026-07-25
[^5]: Vaswani et al., "Attention Is All You Need", NeurIPS 2017（Transformer 原论文）https://arxiv.org/abs/1706.03762 ｜核实 2026-07-25
[^6]: Stanford **CS336** "Language Modeling from Scratch", Spring 2026（Mar 30–Jun 10 2026 在架；讲义/作业 GitHub `stanford-cs336`）https://cs336.stanford.edu/ ｜核实 2026-07-25（WebSearch 确认 Spring 2026 offering + YouTube 讲座）
[^7]: Dao et al., "FlashAttention: Fast and Memory-Efficient Exact Attention with IO-Awareness"（2022；v2 2023；v3 2024）https://arxiv.org/abs/2205.14135 ｜核实 2026-07-25
[^8]: Kwon et al., "Efficient Memory Management for LLM Serving with PagedAttention"（vLLM, SOSP 2023）https://arxiv.org/abs/2309.06180 ｜核实 2026-07-25
[^9]: CMU **11-868** LLM Systems https://llmsystem.github.io/ ｜核实 2026-07-25

### 本机实证（腿③，本报告已跑）
- numpy 手写梯度下降 vs `np.linalg.lstsq`：`max|GD−OLS|=1.998e-15`（200×3 线性回归，2000 步 lr=0.1）。
- numpy 缩放点积注意力 + 因果掩码：行和=1、上三角掩码后=0，输出形状 (3,4)。
- 环境自检：np 2.4.6 / sympy 1.14.0 / scipy 1.17.1 在架；**sklearn 未安装**（`pip install scikit-learn`，注意不随容器持久化）。

### ⚠ 待 R4 核实 / 留白
- L6-08 具体框架版本号、FlashAttention/量化方案的当前 SOTA——R4 取证时按当时时点重新核实并硬标，本报告不落定论。
- AIMA 具体版次页码、CS229/CS231n 当学期 syllabus 细节——R4 逐条取证时定位到章节。
