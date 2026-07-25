# 视角① 权威课纲基准 · 核实 2026-07-25

> 调查员：视角①（权威课纲基准）。基线 Py3.11.15/np2.4.6/gcc13.3 @2026-07-25（本视角为课纲对照，无本机实证腿）。
> 权威锚点：**ACM/IEEE-CS/AAAI Computer Science Curricula 2023（CS2023）** 的 17 个 Knowledge Areas（KA）。
> KA 清单一手来源：ACM 官方 `https://csed.acm.org/knowledge-areas/`（核实 2026-07-25）。
> 交叉锚点：MIT 6-3、CMU SCS、UC Berkeley EECS、Stanford CS 的公开核心课序列。

## CS2023 · 17 个 Knowledge Areas（官方，作为对照基准）
AL Algorithmic Foundations ｜ AR Architecture and Organization ｜ AI Artificial Intelligence ｜ DM Data Management ｜ FPL Foundations of Programming Languages ｜ GIT Graphics and Interactive Techniques ｜ HCI Human-Computer Interaction ｜ MSF Mathematical and Statistical Foundations ｜ NC Networking and Communication ｜ OS Operating Systems ｜ PDC Parallel and Distributed Computing ｜ SEC Security ｜ SEP Society, Ethics, and the Profession ｜ SDF Software Development Fundamentals ｜ SE Software Engineering ｜ SPD Specialized Platform Development ｜ SF Systems Fundamentals
（来源 [S1]。注：相较 CS2013，AI 由「Intelligent Systems」更名并扩容、AAAI 加入为共同发起方；CS2013 的「Computational Science」KA 被删除，故种子无对应缺口 [S1][S2]。）

---

## A. 种子 30 门 ↔ CS2023 KA 映射（课名 | KA | 是否 in-scope | 来源）

| ID | 种子课名 | 对应 CS2023 KA | in-scope? | 依据 |
|----|----------|----------------|-----------|------|
| L1-01 | 程序设计入门 | **SDF** Software Development Fundamentals | ✓ | [S1] |
| L1-02 | 离散数学 | **MSF**（离散/逻辑/证明）；计算模型部分并入 **AL** | ✓ | [S1]；对照 Stanford CS103、CMU 15-251 [S6][S4] |
| L1-03 | 线性代数 | **MSF** | ✓ | [S1] |
| L1-04 | 微积分 | **MSF** | ✓ | [S1] |
| L2-01 | 数据结构 | **SDF** + **AL**（数据结构与代价分析） | ✓ | [S1]；Berkeley CS61B [S5] |
| L2-02 | 数字逻辑电路 | **AR** Architecture and Organization（数字逻辑/布尔/时序）；并入 **SF** | ✓ | [S1] |
| L2-03 | 概率论与数理统计 | **MSF**（含统计基础） | ✓ | [S1]；Berkeley CS70 [S5] |
| L3-01 | 算法设计与分析 | **AL** Algorithmic Foundations | ✓ | [S1]；MIT 6.046 [S3]、CMU 15-210 [S4] |
| L3-02 | 计算机组成原理 | **AR** + **SF** Systems Fundamentals | ✓ | [S1]；MIT 6.004、Berkeley CS61C、Stanford CS107 [S3][S5][S6] |
| L3-03 | 汇编与机器级表示 | **AR** | ✓ | [S1]；Stanford CS107（machine arch/assembly）[S6] |
| L3-04 | 面向对象与程序设计范式 | **FPL** Foundations of Programming Languages + **SDF/SE** | ✓ | [S1]；CMU 15-150 函数式 [S4] |
| L3-05 | 计算理论/形式语言与自动机 | **AL**（计算模型/可判定性/复杂度类） | ✓ | [S1]；MIT 6.045、Stanford CS103、CMU 15-251 [S3][S6][S4] |
| L4-01 | 操作系统 | **OS** Operating Systems + **SF** | ✓ | [S1]；Berkeley CS162、Stanford CS111 [S5][S6] |
| L4-02 | 计算机网络 | **NC** Networking and Communication | ✓ | [S1] |
| L4-03 | 数据库系统 | **DM** Data Management | ✓ | [S1]；Berkeley CS186 [S5] |
| L4-04 | 编译原理 | **FPL**（翻译/代码生成）+ **AL** | ✓ | [S1] |
| L4-05 | 并发与并行程序设计 | **PDC** Parallel and Distributed Computing + **SF** | ✓ | [S1] |
| L4-06 | 软件工程 | **SE** Software Engineering | ✓ | [S1]；MIT 6.031 [S3] |
| L5-01 | 分布式系统 | **PDC** | ✓ | [S1] |
| L5-02 | 机器学习 | **AI**（Machine Learning KU）+ **MSF** | ✓ | [S1][S2]；MIT 6.036、Berkeley CS188 [S3][S5] |
| L5-03 | 计算机图形学 | **GIT** Graphics and Interactive Techniques | ✓ | [S1] |
| L5-04 | 信息安全/密码学 | **SEC** Security（+ **MSF** 数论） | ✓ | [S1] |
| L5-05 | 高级计算机体系结构 | **AR** | ✓ | [S1] |
| L5-06 | 编程语言理论 | **FPL** | ✓ | [S1] |
| L5-07 | 方向选修（HCI/嵌入式/DSP…） | 桶：**HCI / SPD** 等（仅登记） | ✓（桶） | 见 §B-2/B-4 |
| L6-01 | 高级算法 | **AL** | ✓ | [S1] |
| L6-02 | 程序分析与形式验证 | **FPL**（形式方法/验证）+ **SE** | ✓ | [S1] |
| L6-03 | 深度学习及其分支 | **AI** | ✓ | [S1][S2] |
| L6-04 | 数据库内核与存储系统 | **DM** | ✓ | [S1] |
| L6-05 | 分布式与云系统专题 | **PDC** | ✓ | [S1] |
| L6-06 | 高性能计算 | **PDC**（+ 数值库；CS2023 已删 Computational Science KA） | ✓ | [S1] |
| L6-07 | 专题研讨+论文答辩 | 元课程（仅登记）；专业素养可对接 **SEP** | ✓（桶） | 见 §B-3 |

**结论**：种子 30 门具名课 **全部 in-scope**——每门都能落到至少一个 CS2023 KA，无「越界/非 CS」课程。种子对 17 个 KA 中的 **13 个**有具名课直接覆盖（AL/AR/DM/FPL/GIT/MSF/NC/OS/PDC/SEC/SDF/SE，以及 SF 以跨层方式隐式覆盖）。

---

## B. 种子缺漏的核心主题（逐条）

### B-1. 符号/经典 AI（搜索·知识表示与推理·规划·约束满足）—— 归属 L5（与 L5-02 并列）
- **现状**：种子的 AI 内容只有 L5-02 机器学习 + L6-03 深度学习，**缺经典/符号 AI**：无信息/启发式图搜索、知识表示与推理（KRR）、规划、约束满足、对抗搜索/博弈。
- **权威依据**：CS2023 把 AI 单列为 KA 并由 **AAAI 共同发起**；其 CS-Core 明确要求**所有 CS 学生**掌握 uninformed/informed graph search，KRR 为 KA-Core，AI 共 12 个 knowledge units [S1][S2]。顶校核心 AI 课（Berkeley **CS188 Intro to AI**、MIT **6.034 AI**）正是以搜索/KRR/规划为主干，ML 只是其中一块 [S5][S3]。
- **重要性**：搜索与 KRR 是 CS-Core（人人必修）层级，且是理解现代 Agent/规划/求解器的基座；种子把 AI≈ML 会漏掉这一必修基石。

### B-2. 人机交互（HCI）—— 归属 L5（应从 L5-07 选修桶提升为具名课）
- **现状**：HCI 仅出现在 L5-07「方向选修」桶里，**无具名课**。
- **权威依据**：CS2023 将 **HCI** 列为 17 个 KA 之一（独立 KA，非选修点缀）[S1]；MIT 6-3 明确把「human-computer interaction and graphics」列为核心方向领域之一 [S3]。
- **重要性**：作为一个完整 KA，HCI（可用性、交互设计、评估方法、无障碍）在权威课纲中属于本科应覆盖范围，不应只当兴趣扩展点。建议至少登记为 L5 具名候选课。

### B-3. 社会·伦理·职业（SEP / Society, Ethics, and the Profession）—— 归属：跨层横切线程（可挂 L4/L6-07）
- **现状**：种子**完全无** SEP 内容（L6-07 仅为「研讨+答辩」元课程，不产报告）。
- **权威依据**：CS2023 相较 CS2013 **显著增加了 SEP 的 core hours**，并建议将其**横切整合进各 KA**（SIGCSE TS2024 专门有 operationalizing SEP 的工作坊）[S1][S7]。Stanford CS-BS 强制要求一门 **Technology in Society (TiS)** 课 [S6]。（注：SEP 精确 core-hour 数值本次未从一手 PDF 取到，留待补 [S2]。）
- **重要性**：SEP 是每个受认证 CS 课纲的必修 KA，涵盖专业伦理、隐私、知识产权、可持续性、AI 伦理与社会影响。种子若定位为「完整 CS 知识库」，SEP 缺失是**结构性空白**（尤其在 AI 章节需要伦理配套）。

### B-4. 专用平台开发（SPD / Specialized Platform Development）—— 归属 L5（部分缺，可留在选修桶）
- **现状**：Web/移动/嵌入式/游戏/机器人等平台开发仅隐含在 L5-07 选修桶。
- **权威依据**：CS2023 将 **SPD** 列为独立 KA [S1]。
- **重要性**：**低-中**。这属于应用/平台向，学术核心序列（MIT/CMU/Berkeley/Stanford 的必修核心）通常不强制单列，故**可接受**留在 L5-07 桶内——此条记为「已知边界，非硬缺」。

### B-5. 系统基础（SF / Systems Fundamentals）—— 归属：跨层（L3-02 + L4-01 之间的横切）
- **现状**：种子无独立 SF 课，其内容（状态与状态机、并行性、性能评估、资源分配、跨层通信、计算范式）分散在 L3-02 组成 / L4-01 OS / L4-05 并发。
- **权威依据**：CS2023 SF 是**刻意设计为跨层的基础 KA**（并非要求单开课）[S1]。
- **重要性**：**低**。以横切方式覆盖符合权威意图，记为「隐式覆盖，无需新增课」，仅提醒 Round 2 编排时显式点出 SF 的跨层主题。

**缺漏计数**：硬缺/建议提升 **3 条**（B-1 符号AI、B-2 HCI、B-3 SEP）；软边界 **2 条**（B-4 SPD、B-5 SF，均判定可接受）。

---

## C. 命名对照 / 备注（中文课名 ↔ 权威英文 KA）

- 程序设计入门 ↔ Software Development Fundamentals (**SDF**)
- 数据结构 ↔ SDF + Algorithmic Foundations (**AL**)
- 离散/线代/微积分/概率统计 ↔ Mathematical and Statistical Foundations (**MSF**)
- 数字逻辑电路 / 计算机组成 / 汇编 / 高级体系结构 ↔ Architecture and Organization (**AR**)
- 计算机组成、操作系统 的跨层基础 ↔ Systems Fundamentals (**SF**，跨层)
- 算法设计与分析 / 计算理论·自动机 / 高级算法 ↔ Algorithmic Foundations (**AL**)（CS2023 把「可计算性/复杂度类」并入 AL，非独立「计算理论」KA）
- 操作系统 ↔ Operating Systems (**OS**)
- 计算机网络 ↔ Networking and Communication (**NC**)
- 数据库系统 / 数据库内核 ↔ Data Management (**DM**)
- 编译原理 / 编程语言理论 / 程序分析与形式验证 / OO与范式 ↔ Foundations of Programming Languages (**FPL**)
- 并发并行 / 分布式系统 / 云系统专题 / 高性能计算 ↔ Parallel and Distributed Computing (**PDC**)
- 机器学习 / 深度学习 ↔ Artificial Intelligence (**AI**)（**注**：CS2023 的 AI 远大于 ML/DL，见 B-1）
- 计算机图形学 ↔ Graphics and Interactive Techniques (**GIT**)
- 信息安全/密码学 ↔ Security (**SEC**)
- 软件工程 ↔ Software Engineering (**SE**)
- （缺）人机交互 ↔ Human-Computer Interaction (**HCI**)
- （缺）社会伦理职业 ↔ Society, Ethics, and the Profession (**SEP**)
- （选修桶）专用平台开发 ↔ Specialized Platform Development (**SPD**)

备注：
- 种子 L3-05「计算理论/形式语言与自动机」在 CS2023 中不是独立 KA，而是 **AL** 的组成（models of computation / computability / complexity）。命名保留中文习惯即可，映射标注 AL。
- CS2023 已**删除** CS2013 的 Computational Science KA；种子无对应缺口，L6-06 高性能计算以 PDC + 数值库覆盖其残余诉求。

---

## D. 来源清单（一手 vs ⚠二手；含版本/年份 + 核实日期 2026-07-25）

### 一手 / 权威（承重）
- [S1] ACM 官方「Knowledge Areas — CS2023」：`https://csed.acm.org/knowledge-areas/` —— 17 个 KA 名称与代码的权威清单。版本：CS2023（正式版）。核实 2026-07-25。
- [S2] CS2023 正式报告 PDF（ACM/IEEE-CS/AAAI Joint Task Force）：`https://csed.acm.org/wp-content/uploads/2023/09/Version-Gamma.pdf`（Version Gamma, Aug 2023）；及 IEEE-CS 镜像 `https://ieeecs-media.computer.org/media/education/reports/CS2023.pdf`（Jan 2024）。用于 AI 扩容、Computational Science 删除、SEP 增时等结论。核实 2026-07-25。〔SEP 精确 core-hour 数值本次未定位到具体页码——留白待补，未凭记忆填。〕
- [S3] MIT EECS 官方 Course 6-3 课程与要求：`https://www.eecs.mit.edu/academics/undergraduate-programs/curriculum/6-3-computer-science-and-engineering` 及 `https://eecsis.mit.edu/degree_requirements.pcgi?program=6-3`（2022 requirements）。核实 2026-07-25。
- [S4] CMU SCS 本科 CS 课程目录：`http://coursecatalog.web.cmu.edu/schools-colleges/schoolofcomputerscience/undergraduatecomputerscience/` 及 CSD `https://csd.cmu.edu/academics/all-courses`（15-112/15-150/15-210/15-213/15-251）。核实 2026-07-25。
- [S5] UC Berkeley EECS 课程页：`https://eecs.berkeley.edu/academics/courses/`（CS61A/61B/61C、CS70、CS162、CS186、CS188 及先修链）。核实 2026-07-25。
- [S6] Stanford CS 官方 BS 学位要求：`https://www.cs.stanford.edu/bachelors/degree-requirements`（CS103/107/109/110-111/161；含 Technology in Society 必修）。核实 2026-07-25。
- [S7] SIGCSE TS2024 官方会议页「Operationalizing the CS2023 SEP Recommendations」：`https://sigcse2024.sigcse.org/details/sigcse-ts-2024-affiliated-events/4/...`。用于 SEP 横切整合的佐证。核实 2026-07-25。
- [S2a] （半一手，供 AI KA 细节佐证）Eaton et al., "Artificial Intelligence in the CS2023 Undergraduate Computer Science Curriculum: Rationale and Challenges"，AAAI/EAAI 2024：`https://www.seas.upenn.edu/~eeaton/papers/Eaton2024AICurriculum.pdf`。作者为 CS2023 AI 组成员，同行评审。用于 AI 的 12 个 KU / search 属 CS-Core 等表述。核实 2026-07-25。

### ⚠二手（仅线索，不承重）
- ⚠ Grokipedia「Computer Science Curricula 2023」、ResearchGate/Semantic Scholar 条目 —— 仅作 KA 概览线索。
- ⚠ libertify.com「CMU CS Undergraduate Guide 2026」、Medium「Codebase's Guide to Berkeley CS」、Scribd/CourseHero 上传件 —— 仅作课程序列线索，结论以各校官方页 [S3]-[S6] 为准。
