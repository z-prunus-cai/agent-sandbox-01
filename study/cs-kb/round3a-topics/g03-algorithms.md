# Round 3a 大主题分解 · 第 3 组「算法与可计算性」

> 基线 Py3.11.15/np2.4.6/gcc13.3 @2026-07-25
> 组内课程：**L3-01 算法设计与分析（枢纽 P0）** · **L3-05 计算理论/形式语言与自动机（P1）** · **L6-01 高级算法（P2）**
> 本轮任务：把每门课拆成**查全的大主题清单**（教学单元级、覆盖全、少重叠、按教学序），锚定权威教材目录/官方 syllabus。
> 前序产物：`round2-partials/g03-algorithms.md`（已给选型对比与重点方向）。本轮聚焦「大主题清单 + 一手目录锚点 + 跨课重叠」，逐条章节/定理号的精确取证留 R4。

---

## 权威目录锚点（URL + 版本 + 核实 2026-07-25）

| 课 | 首选一手锚点 | 版本/年份 | URL（核实 2026-07-25 可达） | 交叉锚点 |
|----|--------------|-----------|------------------------------|----------|
| L3-01 | **CLRS《Introduction to Algorithms》** 官方目录 PDF | **4th ed. (2022, MIT Press)** | 书页 `https://mitpress.mit.edu/9780262046305/introduction-to-algorithms/` ｜ 目录 PDF `https://mitp-content-server.mit.edu/books/content/sectbyfn/books_pres_0/11599/4e_toc.pdf`（本轮已下载并抽取全目录） | **MIT 6.046J** Design & Analysis of Algorithms 官方 syllabus `https://ocw.mit.edu/courses/6-046j-design-and-analysis-of-algorithms-spring-2015/pages/syllabus/` |
| L3-05 | **Sipser《Introduction to the Theory of Computation》** 官方目录 | **3rd ed. (2012, Cengage)** | 作者页 `https://math.mit.edu/~sipser/book.html`（确认 3rd/2012）；目录逐章由 PDF 副本抽取核对 | **MIT 6.045J** Automata, Computability & Complexity syllabus `https://ocw.mit.edu/courses/6-045j-automata-computability-and-complexity-spring-2011/pages/syllabus/` |
| L6-01 | **MIT 6.854J Advanced Algorithms** 官方讲次表 | Fall 2008（OCW，机制稳定） | `https://ocw.mit.edu/courses/6-854j-advanced-algorithms-fall-2008/pages/lecture-notes/`（本轮已取全 26 讲清单） | 教材锚点：**Williamson–Shmoys《The Design of Approximation Algorithms》**（免费 PDF）｜**Motwani–Raghavan《Randomized Algorithms》**；另可用 **Roughgarden CS261/CS264** 大纲交叉 |

> 时效核实（@2026-07-25）：**P vs NP 仍未解**（Clay Millennium 七题仅 Poincaré 已解；`blog.computationalcomplexity.org` 2026-06 复述"AI 生成的 P≠NP 证明尚不在眼前"）——R4 引用时维持"未解"结论。CLRS 4th=2022、Sipser 3rd=2012 均为当前最新版（截至 2026-07-25 无更晚版本）。

---

## L3-01 算法设计与分析（枢纽 · P0）

> 锚点：CLRS 4th ed. 八大 Part（I Foundations / II Sorting & Order Statistics / III Data Structures / IV Advanced Design & Analysis Techniques / V Advanced Data Structures / VI Graph Algorithms / VII Selected Topics / VIII 附录）+ 6.046 十大模块。
> 拆分原则：以"设计与分析"为主轴（数据结构块与 L2-01 有重叠，按锚点保留但显式标注）；专题类（并行/矩阵/FFT/数论/字符串/ML）合并为一个"专题选讲"单元并指回下游课。

| 编号 | 大主题 | 一句话范围 | 来源锚点 | 跨课重叠提示 |
|------|--------|------------|----------|--------------|
| 01 | 算法分析与渐进记号 | 计算模型、最坏/平均分析、O/Ω/Θ/o/ω 定义与常用函数 | CLRS 4e Ch1–3；6.046「Asymptotic Analysis」 | 全库"代价"词汇源头；与 L2-01 有轻微交集（复杂度基本记号） |
| 02 | 分治与递归式求解 | 分治范式、代入/递归树/主定理/Akra–Bazzi，Strassen 矩阵乘 | CLRS 4e Ch4；6.046「Divide-and-Conquer」 | 主定理数值核对可 sympy 化；FFT（Ch30）为其延伸，见 #14 |
| 03 | 随机化算法与概率分析 | 指示器随机变量、雇佣问题、随机化设计与期望分析 | CLRS 4e Ch5；6.046「Randomized Algorithms」 | **与 L6-01 #5 重叠**：此处为入门（期望分析/随机快排引子），深入（Karger/集中不等式）在 L6-01；概率工具先修属 L2-03 |
| 04 | 排序与比较模型下界 | 堆排序、快排、比较排序 Ω(n log n) 决策树下界、线性时间排序（计数/基数/桶） | CLRS 4e Ch6–8 | 与 L2-01 数据结构（堆/优先队列）交集；下界论证是本课教学高价值点 |
| 05 | 中位数与顺序统计量 | 期望线性时间选择、最坏线性时间选择（BFPRT） | CLRS 4e Ch9 | — |
| 06 | 高级数据结构 | 散列表、二叉搜索树、红黑树、B 树、并查集（含 α(n)）、数据结构扩张 | CLRS 4e Ch10–13, 17–19；6.046「Data Structures」 | **与 L2-01 数据结构强重叠**：L2-01 讲实现与操作，L3-01 讲分析/摊还与选型；Fibonacci 堆归 L6-01 #1 |
| 07 | 动态规划 | 最优子结构+重叠子问题、钢条切割/矩阵链/LCS/最优 BST | CLRS 4e Ch14；6.046「Dynamic Programming」 | 与 L4-03 DB 查询优化、L4-04 编译为下游应用（仅点名） |
| 08 | 贪心算法与拟阵 | 贪心选择性质、交换论证、Huffman、离线缓存、拟阵理论 | CLRS 4e Ch15；6.046「Greedy Algorithms」 | 与 #07 的"选型证明"对照是本课最高教学价值点 |
| 09 | 摊还分析 | 聚合法/记账法/势能法，动态表 | CLRS 4e Ch16；6.046「Amortized Analysis」 | 势能法在 L6-01 在线算法（#6）复用 |
| 10 | 图的遍历与连通性 | 图表示、BFS/DFS、拓扑排序、强连通分量 | CLRS 4e Ch20 | 图基础先修属 L1-02；下游 L4-02/L5-01 消费 |
| 11 | 最小生成树与最短路 | Kruskal/Prim、Bellman–Ford、DAG 最短路、Dijkstra、Floyd–Warshall、Johnson | CLRS 4e Ch21–23；6.046「Graph Algorithms」 | Fibonacci 堆加速 Dijkstra/Prim 属 L6-01 #1 边界 |
| 12 | 网络流与二部图匹配 | 流网络、Ford–Fulkerson/Edmonds–Karp、最大流最小割、二部匹配、稳定婚姻、匈牙利算法 | CLRS 4e Ch24–25 | 流的进阶（最小费用循环流/Goldberg–Tarjan）归 L6-01 #2 |
| 13 | NP 完全性与多项式归约 | P/NP/NPC、多项式验证、≤ₚ 归约、Cook–Levin、经典 NPC 问题与归约链 | CLRS 4e Ch34；6.046（P vs NP） | **与 L3-05 #8 重叠（组内最重要边界 B1）**：严格构造（Cook–Levin 全证/复杂度类定义）归 L3-05；本课只讲"会用归约判难 + 已知 NPC 清单" |
| 14 | 近似算法入门 | 近似比框架、VC 2-近似、TSP、集合覆盖、子集和、随机化+LP 视角 | CLRS 4e Ch35；6.046「Approximation Algorithms」(PTAS/FPTAS) | **与 L6-01 #4 重叠（边界 B2）**：本课为入门，近似比/竞争比的完整框架与 LP-rounding/PCP 归 L6-01 |
| 15 | 专题选讲 | 并行(fork-join)、在线、矩阵运算、线性规划、多项式与 FFT、数论(RSA/素性)、字符串匹配(KMP/后缀数组)、机器学习算法 | CLRS 4e Ch26–33（Part VII Selected Topics） | 多为下游课"点名应用"：LP→L6-01；数论→L5-04 密码；ML→L5-02；并行→L4-05/L6-06；在线→L6-01 |

**建议大主题数：K = 14**（#01–14 为核心教学序；#15 专题选讲按学期与主攻方向选取 2–3 个，不逐一展开，指回下游课）。

---

## L3-05 计算理论 / 形式语言与自动机（P1）

> 锚点：Sipser 3rd ed. 三大 Part（Part One Automata and Languages / Part Two Computability Theory / Part Three Complexity Theory）+ Ch0 预备。
> 拆分原则：严格按 Sipser 章序；Ch6/Ch10 为"高级专题"章，标注为可选延伸。

| 编号 | 大主题 | 一句话范围 | 来源锚点 | 跨课重叠提示 |
|------|--------|------------|----------|--------------|
| 01 | 数学预备与证明方法 | 集合/序列/函数/关系/图/字符串与语言/布尔逻辑、构造/反证/归纳证明 | Sipser 3e Ch0 | **与 L1-02 离散数学强重叠（边界 B4）**：L3-05 只用不证这些基础工具 |
| 02 | 有限自动机与正则语言 | DFA/NFA 及等价（子集构造）、正则表达式与 Kleene 等价、正则运算闭包、泵引理判非正则 | Sipser 3e Ch1（§1.1–1.4） | 下游 L4-04 编译词法分析；Myhill–Nerode 最小化在 R2 已标为重点 |
| 03 | 上下文无关语言 | CFG、Chomsky 范式、PDA 及与 CFG 等价、CFL 泵引理、确定型 CFL 与 LR(k) 解析 | Sipser 3e Ch2（§2.1–2.4，3e 新增 DCFL 节） | 下游 L4-04 编译语法分析（LL/LR）；交/补不闭合为反直觉考点 |
| 04 | 图灵机与丘奇–图灵论题 | TM 形式定义、多带/非确定 TM/枚举器等价、"算法"的定义、Hilbert 问题 | Sipser 3e Ch3（§3.1–3.3） | 计算模型的公共语言；下游 L5-06 PL、L6-02 验证 |
| 05 | 可判定性 | 关于正则/CFL 的可判定问题、对角化方法、不可判定语言、图灵不可识别语言 | Sipser 3e Ch4（§4.1–4.2） | 停机问题不可实证（R4 硬标"不可计算，无实证"） |
| 06 | 归约 | 语言理论中的不可判定问题、计算历史法、简单不可判定问题、映射归约 ≤ₘ 与可计算函数 | Sipser 3e Ch5（§5.1–5.3） | Rice 定理在此族；映射归约是复杂度归约（#08）的雏形 |
| 07 | 可计算性高级专题（可选） | 递归定理与自指、逻辑理论的可判定性、图灵归约、Kolmogorov 复杂度/信息定义 | Sipser 3e Ch6（§6.1–6.4） | 可选延伸；Kolmogorov 与 L6-01 随机性、L5-04 有远端联系 |
| 08 | 时间复杂度：P/NP/NP 完全 | 复杂度测量、类 P、类 NP、P vs NP、NP 完全性定义、Cook–Levin 全证、更多 NPC 问题 | Sipser 3e Ch7（§7.1–7.5） | **与 L3-01 #13 重叠（边界 B1）**：本课给"严格版"（复杂度类定义 + Cook–Levin 构造），应早于/并行 L3-01 的 NP 章 |
| 09 | 空间复杂度 | Savitch 定理、类 PSPACE 与 PSPACE 完全（TQBF/博弈）、类 L 与 NL、NL 完全、NL=coNL | Sipser 3e Ch8（§8.1–8.6） | PSPACE 与广义博弈；R2 表 2 已列 TQBF |
| 10 | 难解性：层级定理与电路复杂度 | 时间/空间层级定理、相对化与对角化的局限、电路复杂度 | Sipser 3e Ch9（§9.1–9.3） | 指数空间完全性；层级定理是"确有更难问题"的证明 |
| 11 | 复杂度高级专题（可选） | 近似算法、概率算法与 BPP、交替(alternation)与多项式层级、交互式证明 IP=PSPACE、并行计算与 NC/P 完全、密码学 | Sipser 3e Ch10（§10.1–10.6） | **与 L6-01（近似/随机 #4#5）、L5-04（密码）重叠**：本课给理论定义，工程/算法设计在下游 |

**建议大主题数：K = 10**（#01–10 为核心；#01 与 L1-02 重叠仅作复习、#07 与 #11 为可选高级专题章，按课时/方向取舍）。

---

## L6-01 高级算法（研究生深化 · P2）

> 锚点：MIT 6.854J 26 讲清单（Fibonacci 堆 → 网络流/最小费用循环流 → splay/动态树 → LP 对偶/单纯形/椭球/锥规划 → 近似算法/max-cut/sparsest-cut → 度量嵌入 → 计算几何（凸包/Voronoi/欧氏 TSP PTAS）→ 流式算法）+ Williamson–Shmoys（近似）与 Motwani–Raghavan（随机）教材分册。
> R2 定位主线：**当精确多项式解不存在时的出路** = 近似 / 随机 / 在线 / LP 松弛 / 亚线性，再加高级数据结构与几何两条 6.854 主线。

| 编号 | 大主题 | 一句话范围 | 来源锚点 | 跨课重叠提示 |
|------|--------|------------|----------|--------------|
| 01 | 高级数据结构与摊还加速 | Fibonacci 堆、splay 树、动态树(link-cut) —— 用摊还换更优图算法界 | 6.854 Lec 1,5–8 | **与 L3-01 #06/#11 边界**：Fibonacci 堆把 Dijkstra/Prim 提到 O(E+V log V)；本课讲结构与摊还证明 |
| 02 | 网络流进阶 | 最大流、最小费用循环流、Goldberg–Tarjan、cancel-and-tighten | 6.854 Lec 2–5 | L3-01 #12 止于 Ford–Fulkerson/Edmonds–Karp；本课接手最小费用流与更强算法 |
| 03 | 线性规划与对偶 | 单纯形几何、强/弱对偶、互补松弛、椭球法、内点/锥规划(SDP)概览 | 6.854 Lec 9–15；CLRS 4e Ch29（先修视角） | 下游 L5-02 ML 优化、L6-06 数值；强对偶可 scipy.optimize.linprog 实证 |
| 04 | 近似算法 | 近似比框架、VC 2-近似、Set-Cover ln n、度量 TSP Christofides、PTAS/FPTAS、LP-rounding、原始-对偶、max-cut/sparsest-cut、不可近似性(PCP 一句话) | 6.854 Lec 16–19；Williamson–Shmoys 全书 | **与 L3-01 #14 重叠（边界 B2）**：L3-01 入门，本课给完整框架 + LP-rounding/原始对偶/hardness |
| 05 | 随机化算法 | Las Vegas vs Monte Carlo、Karger 最小割、指纹/Freivalds 验矩阵乘、Markov/Chebyshev/Chernoff 集中不等式作分析工具 | Motwani–Raghavan 全书；6.854（随机化贯穿） | **与 L3-01 #03 重叠**：L3-01 入门；集中不等式先修属 L2-03（边界 B7） |
| 06 | 在线算法与竞争分析 | 竞争比、ski-rental 2-竞争、分页(LRU/marking) k-竞争、势能法、随机化打破确定下界 | CLRS 4e Ch27（先修）；Borodin–El-Yaniv 一手 | **与 L3-01 #15（在线专题）重叠（边界 B2）**：竞争比的定义与框架在本课 |
| 07 | 亚线性与流式算法 | Morris 计数、Count-Min Sketch、HyperLogLog、蓄水池抽样 —— 不看全量的估计 + 误差界 | 6.854 Lec 25–26；Flajolet HLL 2007、Cormode 综述 | 下游 L6-04 DB 基数估计、L5-02 特征、L6-05 分布式聚合（边界 B8） |
| 08 | 高维几何与度量嵌入 | 凸包、固定维 LP、Voronoi 图、度量嵌入、欧氏 TSP 的近似方案(PTAS) | 6.854 Lec 20–24 | 与 L5-03 图形学几何有远端交集；欧氏 TSP PTAS 衔接 #04 |

**建议大主题数：K = 8**（#01–08；其中 #04/#05/#06 是 R2 "四条出路"的深化承载，与 L3-01 的入门单元构成主要跨课边界；#01/#02/#08 为 6.854 特有的数据结构/流/几何主线）。

---

## 组内跨课边界总表（R4 编排避重复用）

| 边界 | 归属划分 |
|------|----------|
| **B1 L3-01 #13 ↔ L3-05 #08（最重要）** | NPC 的"严格构造"（Cook–Levin 全证、P/NP/PSPACE 复杂度类定义）归 **L3-05**；NPC 的"工程用途"（识别难题→转求近似/启发式）归 **L3-01**。L3-05 应早于/并行 L3-01 的 NP 章。 |
| **B2 L3-01 #14/#15 → L6-01 #04/#06** | 近似比、竞争比的**定义与完整框架**（含 LP-rounding、原始对偶、PCP、势能法）归 **L6-01**；L3-01 只给入门实例。 |
| **B3 L3-01 #03 → L6-01 #05** | 随机化：L3-01 入门（期望分析/随机快排），L6-01 深入（Karger/集中不等式/指纹）。 |
| **B4 L3-01 #06 ↔ L2-01 数据结构** | 散列/平衡树/并查集：L2-01 讲实现与操作，L3-01 讲分析/摊还/选型，L6-01 #01 讲 Fibonacci/splay/动态树等高级结构。 |
| **B5 L3-05 #01 ↔ L1-02 离散** | 集合/关系/归纳/图基础是 L1-02 先修，L3-05 只用不证。 |
| **B6 L3-05 #02/#03 → L4-04 编译** | 正则→词法、CFG/LR→语法是编译课落地；L3-05 讲"语言类与识别机"。 |
| **B7 L3-05 #11、L6-01 → L5-04 密码 / L5-06 PL / L6-02 验证** | 密码学、可判定性边界、模型检验是下游应用；本组给通用理论语言。 |
| **B8 L6-01 #07 → L6-04 DB / L5-02 ML / L6-05 分布式** | 流式/草图算法在下游作基数估计、特征、聚合的应用点名。 |

---

## 一手源清单（R4 逐条取证时下沉到章节/定理号）

- **L3-01**：CLRS《Introduction to Algorithms》4th ed. (2022, MIT Press)，目录锚点已核对（Part I–VIII，Ch1–35 + 附录 A–D）；Cook 1971 / Karp 1972 原始论文；MIT 6.046J syllabus。
- **L3-05**：Sipser《Introduction to the Theory of Computation》3rd ed. (2012, Cengage)，目录锚点已核对（Ch0 + Part One Ch1–2 / Part Two Ch3–6 / Part Three Ch7–10）；Hopcroft–Motwani–Ullman 交叉；Turing 1936；MIT 6.045J syllabus。
- **L6-01**：MIT 6.854J Fall 2008 讲次表（26 讲，已核对）；Williamson–Shmoys《The Design of Approximation Algorithms》；Motwani–Raghavan《Randomized Algorithms》；Borodin–El-Yaniv《Online Computation》；Flajolet HyperLogLog 2007；Roughgarden CS261/CS264 可交叉。
