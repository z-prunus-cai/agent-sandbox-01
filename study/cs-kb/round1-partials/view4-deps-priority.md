# 视角④ 依赖与优先级校验 · 核实 2026-07-25

> 参照系：CS2023（ACM/IEEE/AAAI，2024-01 正式版）KA 依赖建议 + 顶校 degree 先修链
> （MIT EECS、UC Berkeley EECS、CMU SCS、Stanford CS）。基线 Py3.11.15/np2.4.6/gcc13.3 @2026-07-25。
> 纪律：只在有权威依据时提修正；无依据的一律「保持种子现状」。官方课程页/CS2023 官方 = 一手；
> csdiy.wiki / medium / 个人笔记 = ⚠二手（仅交叉验证，不单独承重）。

---

## A. 依赖关系修正（逐条：ID | 种子现状 | 建议 | 依据URL）

### A1. L4-02 计算机网络 —— 建议：**移除对 L4-01(OS) 的硬依赖**
- **种子现状**：L4-02 依赖 L4-01（OS）。
- **建议**：改依赖 **L2-01（数据结构）**，弱依赖 **L3-02（组成/系统）**；OS 与网络**并行**，非先修。
- **依据**：
  - UC Berkeley CS168《Intro to the Internet》官方目录：先修仅 **CS61B（数据结构）**，CS61C（机器结构）为 recommended，**不含** CS162(OS)。https://www2.eecs.berkeley.edu/Courses/CSCourseCode.php168/ ｜ https://sp26.cs168.io/ （Spring 2026 仍在开）
  - MIT 6.033 把网络与系统**合并**在同一门（先修 6.004 计算机结构 + 6.02），并不要求先修一门独立 OS。https://web.mit.edu/6.033/2014/wwwdocs/general.shtml
  - 结论：网络的真实硬先修是数据结构 + 基本系统概念（socket/分层），OS 不是前置。种子把 L4-02 挂在 L4-01 之后属**多余依赖**，会人为拉长关键路径。

### A2. L4-03 数据库系统 —— 建议：**把 L4-01(OS) 依赖降级/替换为 L3-02(组成/系统)**
- **种子现状**：L4-03 依赖 L3-01（算法）、L4-01（OS）。
- **建议**：保留 **L3-01（算法，含数据结构 B+树/查询优化）**；把 **L4-01(OS) 改为 L3-02（组成/系统）**（OS 的事务/并发概念是加分项而非硬先修）。
- **依据**：
  - CMU 15-445《Intro to Database Systems》官方 syllabus：先修 **15-213（Computer Systems）**，要求 B 以上；**不含** OS。https://15445.courses.cs.cmu.edu/spring2026/syllabus.html
  - UC Berkeley CS186《Databases》：先修 **CS61A/61B/61C**（编程/数据结构/机器结构），**不含** CS162(OS) 或 CS170(算法)。https://www2.eecs.berkeley.edu/Courses/CSCourseCode.php186/
  - 结论：DB 建立在「数据结构 + 系统编程」之上；对完整 OS 的依赖属**过强**。数据结构经 L3-01 已传递满足；把硬 OS 依赖降为软依赖更符合顶校实践。

### A3. L5-02 机器学习 —— 建议：**补 L1-04(微积分) 缺失先修；L3-01(算法) 降为软依赖**
- **种子现状**：L5-02 依赖 L2-03（概率）、L1-03（线代）、L3-01（算法）。
- **建议**：**新增 L1-04（微积分，尤其多元/向量微积分与梯度）**；把 L3-01（算法）改为 **L2-01（数据结构）+ 编程经验**（算法设计非硬先修）。
- **依据**：
  - Stanford CS229《Machine Learning》官方：先修为概率（CS109/STATS116）+ **多元微积分与线代（MATH51）** + Python/NumPy 编程。https://cs229.stanford.edu/
  - UC Berkeley CS189《Intro to ML》官方 syllabus：要求 **Math53（向量微积分，理解梯度与多元链式法则）** + 线代/概率直觉 + 扎实编程与数据结构。https://eecs189.org/fa25/syllabus/
  - 结论：微积分（梯度/优化）是 ML 的**明确缺失先修**（头号修正）；ML 的 CS 前置是「数据结构 + 编程」而非「算法设计」，L3-01 依赖过强。

### A4. L5-04 信息安全/密码学 —— 建议：**L4-02(网络)、L4-01(OS) 降为软/主题依赖**，硬先修保留 L1-02 + L3-02
- **种子现状**：L5-04 依赖 L4-02（网络）、L4-01（OS）、L1-02（离散）。
- **建议**：硬先修 = **L1-02（离散/数论）+ L3-02（组成/系统，内存安全攻击面）**；L4-02 网络、L4-01 OS 视安全子方向作**软依赖**（网络安全→网络；系统安全→OS）。
- **依据**：
  - UC Berkeley CS161《Computer Security》：先修 **CS61B（数据结构）+ CS70（离散/概率）+ CS61C（机器结构）**，**不含** CS168(网络) 或 CS162(OS)。https://fa25.cs161.org/policies/
  - 结论：安全课的通用硬先修是「数据结构 + 离散/数论 + 系统/机器级」；网络与 OS 的依赖取决于子方向，非普适硬先修。种子挂 L4-02+L4-01 属**偏强**，会把安全课不必要地推到第四层之后。

### A5. L4-04 编译原理 —— **保持种子现状（依赖合理），仅记一条备注**
- **种子现状**：L4-04 依赖 L2-01（数据结构）、L3-05（自动机）、L3-03（汇编）。
- **判定**：数据结构 ✓、自动机 ✓ 与权威一致（Stanford CS143 先修 = 103 数学基础/自动机 + 107 计算机组成与系统）。https://web.stanford.edu/class/cs143/ ｜ https://online.stanford.edu/courses/cs143-compilers
- **备注**：顶校用「计算机组成 L3-02」作系统前置，而非一门独立汇编课；种子挂 L3-03（汇编）在代码生成/目标码语境下**成立**，且 L3-03→L3-02 已传递带入组成。**无需改**，标「保持种子现状」。

### 依赖判定为合理、无权威依据调整（保持种子现状）
- L2-01←L1-01；L2-02←L1-02；L2-03←L1-04；L3-01←L2-01,L1-02（数据结构+离散，标准）；L3-02←L2-02；L3-03←L3-02；L3-05←L1-02；L4-01←L3-02,L2-01（对齐 Berkeley CS162 先修 61B+61C；MIT 6.S081 先修 6.004）；L4-05←L4-01；L5-01←L4-01,L4-02；L5-03←L1-03,L1-04；L5-05←L3-02；L5-06←L4-04,L3-05；L6-01~L6-06 依赖链均与其上游一致。以上无权威反证，**保持种子现状**。

---

## B. 优先级修正（逐条：ID | 种子P? | 建议P? | 理由/依据）

> 判定尺 = 种子自定义「P0 = 枢纽/必经（后续大量课依赖）」 + CS2023 CS-Core / 顶校「核心 vs 选修」分层。

### B1. L4-04 编译原理 | 种子 **P0** | 建议 **P1**
- **理由**：(a) 在本图中下游 fan-out 仅 2（L5-06 PL理论、L6-02 程序分析），远低于真正枢纽 L2-01/L3-01/L3-02/L4-01（各带出大量下游），不达「大量课依赖」的 P0 门槛；(b) MIT/Berkeley/Stanford 均把整门编译**列为选修（depth elective）**，CS2023 中核心是 FPL 的概念层而非完整编译构造；(c) 用户「压缩工程路径」也未含编译。
- **依据**：CS2023 KA 列表（FPL 为 KA，完整编译属深度）https://csed.acm.org/knowledge-areas/ ；Stanford CS143 为选修 https://online.stanford.edu/courses/cs143-compilers 。
- **注**：若确定要走 PL/编译/验证方向，可在该方向视图内保留 P0（枢纽定义随子图成立）；但**全局默认建议 P1**。

### B2. L1-04 微积分 | 种子 **P1** | 建议 **保持 P1（但上调其枢纽权重标注）**
- **理由**：经 A3 修正后，L1-04 的下游变为 L2-03（概率）、L5-02（ML）、L5-03（图形），fan-out 升至 3，枢纽权重明显高于其 P1 同侪。数学地基本身仍属 P1 合适，但应在图中**显式标注为「AI/ML 关键路径上的必经数学地基」**，Round 4 排期时不可后置。
- **依据**：CS229/CS189 均把多元微积分列为 ML 硬先修（见 A3）。

### B3. L2-03 概率与统计 | 种子 **P1** | 建议 **保持 P1（可选上调 P0，视 AI 权重）**
- **理由**：CS2023 强化了 MSF/统计与 AI 的地位；概率是 ML、随机算法、系统性能分析的地基。但在**本图**中直接下游仅 L5-02，fan-out 低，按种子自定义 fan-out 尺 P1 成立。**仅当**项目把 AI/ML 设为主攻方向时上调 P0。标「保持种子现状，除非 AI 主攻」。

### B4. 判定合理、无调整（保持种子现状）
- P0 组 L1-01/L1-02/L1-03/L2-01/L3-01/L3-02/L4-01/L4-02/L4-03：均为高 fan-out 枢纽且属 CS2023 CS-Core（SDF/MSF/AL/AR/OS/NC/DM/SF），P0 恰当。**保持**。
- L4-06 软件工程 P1：SE 是 CS2023 核心 KA，但无强先修、非高 fan-out 枢纽，P1 恰当。**保持**。
- L5-04 安全 P1：SEC 为 CS2023 提级后的核心 KA，作方向课 P1 合理。**保持**。
- L3-03/L3-04/L3-05/L4-05/L5-01/L5-03/L5-05/L5-06/L6-*：分层与「主干 vs 深化/选修」一致，无权威反证，**保持种子现状**。

---

## C. 校验后拓扑排序建议（供 Round 4 分批）

> 采用 A 节修正后的 DAG（L4-02 去 OS 依赖、L4-03 OS→组成、L5-02 补微积分/算法降软、L5-04 网络&OS 降软）。
> 同一批内课程互不依赖，可并发调查；跨批必须前批先完成。

- **批 0（无前置，最先）**：L1-01 程序设计、L1-02 离散、L1-03 线代、L1-04 微积分、L4-06 软件工程*（*经验前置，可任意时点）
- **批 1**：L2-01 数据结构、L2-02 数字逻辑、L2-03 概率、L3-04 OO与范式、L3-05 自动机
- **批 2**：L3-01 算法、L3-02 计算机组成、L5-02 机器学习†（†先修 L1-03/L1-04/L2-03/L2-01 已在批0-1 齐备，可提前到本批）
- **批 3**：L3-03 汇编、L4-01 OS、L4-02 网络（已解耦 OS）、L5-03 图形、L5-05 高级体系结构、L6-01 高级算法
- **批 4**：L4-03 数据库、L4-04 编译、L4-05 并发并行、L5-01 分布式、L5-04 安全
- **批 5（最深）**：L5-06 PL理论、L6-02 程序分析验证、L6-03 深度学习、L6-04 DB内核、L6-05 分布式云专题、L6-06 高性能计算

**必须前置的关键约束（Round 4 不可违反）**：
1. 六大枢纽 **L1-01 / L1-02 / L1-03 / L2-01 / L3-01 / L3-02** 必须最先落地——它们直接/传递支撑几乎全部后续课。
2. **L1-04 微积分** 现处 ML/图形关键路径，必须在 L5-02/L5-03 前完成（种子原路径易漏）。
3. 修正后 **网络(L4-02) 与 OS(L4-01) 解耦**、可并发；DB(L4-03) 只需算法+组成先行，无需等 OS——三者可在批 3-4 内更灵活并行，缩短关键路径。
4. 深化层 L6-* 严格晚于其唯一上游（L6-03←L5-02、L6-04←L4-03、L6-05←L5-01、L6-06←L4-05/L1-03、L6-02←L4-04）。

---

## D. 来源清单

一手（官方课程页 / 官方课纲）：
- CS2023 官方 KA 列表（ACM/IEEE/AAAI，2024-01 正式版）：https://csed.acm.org/knowledge-areas/ ｜ 报告 PDF：https://ieeecs-media.computer.org/media/education/reports/CS2023.pdf
- MIT 6.033 System Engineering（先修 6.004+6.02）：https://web.mit.edu/6.033/2014/wwwdocs/general.shtml ｜ MIT EECS 课程转轨（6.004/6.1900 关系）：https://www.eecs.mit.edu/academics/undergraduate-programs/curriculum/2022-curriculum-transition/
- UC Berkeley CS168 Intro to the Internet（先修 61B，61C recommended）：https://www2.eecs.berkeley.edu/Courses/CSCourseCode.php168/ ｜ https://sp26.cs168.io/
- UC Berkeley CS162 Operating Systems（先修 61A/B/C/70）：https://cs162.org/policies/
- UC Berkeley CS186 Databases（先修 61A/B/C）：https://www2.eecs.berkeley.edu/Courses/CSCourseCode.php186/
- UC Berkeley CS161 Computer Security（先修 61B/70/61C）：https://fa25.cs161.org/policies/
- UC Berkeley CS189 Intro to ML（先修 Math53 向量微积分 + 线代/概率 + 数据结构）：https://eecs189.org/fa25/syllabus/
- CMU 15-445 Intro to Database Systems（先修 15-213 Computer Systems，Spring 2026 仍开）：https://15445.courses.cs.cmu.edu/spring2026/syllabus.html
- CMU SCS 本科课程/核心序列（15-122→15-210/15-213/15-251）：https://csd.cmu.edu/academics/all-courses
- Stanford CS229 Machine Learning（先修 概率 + 多元微积分/线代 MATH51 + Python）：https://cs229.stanford.edu/
- Stanford CS143 Compilers（先修 103 自动机/离散 + 107 组成与系统；列为选修）：https://web.stanford.edu/class/cs143/ ｜ https://online.stanford.edu/courses/cs143-compilers

⚠二手（仅用于交叉验证，未单独承重）：
- csdiy.wiki（CS168/15-445 汇总页）、berkeley-codebase (medium)、ben's notes (cs162)：用于交叉确认上述先修，结论均以对应官方页为准。

时效：全部来源于 2026-07-25 访问核实；CS2023 为 2024-01 正式版；CMU 15-445 Spring2026、Berkeley CS168/162/161/189 及 Stanford CS229/CS143 页面均为现行学期，未见先修结构变更。
