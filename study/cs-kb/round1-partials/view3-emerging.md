# 视角③ 最新选修与前沿动态 · 核实 2026-07-25
> 基线 Py3.11.15/np2.4.6/gcc13.3 @2026-07-25 ｜ 调查员：视角③（最新选修与前沿）
> 任务：展开 L5-07 选修桶 + 检索 2024–2026 进入主流课表的新兴主题，逐条评定重要性与「是否升级为正式报告」。
> 权威锚点：**CS2023**（ACM/IEEE-CS/AAAI，正式发布 2024-06-05）定义 **17 个知识域（KA）**——这是本轮判定「主流 / 前沿」的一手参照系。[^1][^2]
> 一手 = 顶校课程页 / CS2023 官方；趋势博客/榜单/在线课平台标 `⚠二手`，仅作线索。

## 关键背景：CS2023 的 17 个知识域（KA）
Algorithmic Foundations (AL)、Architecture & Organization (AR)、**Artificial Intelligence (AI，本版大幅扩展)**、Data Management (DM)、Foundations of Programming Languages (FPL)、Graphics & Interactive Techniques (GIT)、**Human-Computer Interaction (HCI)**、Mathematical & Statistical Foundations (MSF)、Networking & Communication (NC)、Operating Systems (OS)、**Parallel & Distributed Computing (PDC)**、Security (SEC)、**Society, Ethics & the Profession (SEP，本版升为贯穿所有能力域的整合 KA)**、Software Development Fundamentals (SDF)、Software Engineering (SE)、**Specialized Platform Development (SPD)**、Systems Fundamentals (SF)。[^2]
- CS2013 → CS2023 的净变化：17/18 个 KA 更新；**Computational Science 被删除**；数学要求从「仅离散」扩到「离散 + 概率统计」；AI 扩展并新增「生成式 AI 用于 CS 教育」章节；SEP 升为整合 KA。[^1]
- ⚠**种子对象地图的结构性缺口**：CS2023 的两个核心 KA —— **HCI** 与 **SEP（社会/伦理/职业）** —— 在用户六层课表里**完全缺席**（HCI 仅隐含在 L5-07 桶内，SEP 无任何登记）。这不是"前沿"，是主流课程学的**已固化必修内容缺口**，建议正式对象地图显式补位。

---

## A. L5-07 选修桶展开（HCI / 嵌入式 / OS 内核实践 / DSP）

| 主题 | 重要性档 | CS2023 归属 | 建议层 | 是否升级为报告 | 成熟度 |
|------|----------|-------------|--------|----------------|--------|
| **HCI 人机交互** | **Recommended（在 CS 广度上接近 Must）** | 独立 KA：**HCI** | L5 | **是** —— 独立报告 | ✅完全成熟稳定 |
| **嵌入式系统** | **Recommended** | KA：**SPD**（+ AR / OS 交叉） | L5 | **是** —— 独立报告 | ✅成熟；具体平台/RTOS 演进快，硬标 |
| **OS 内核实践** | Recommended（作 L4-01 的实践延伸） | KA：**OS** | L4-01 附属 | 否 —— 并入 L4-01 作「内核实证/xv6 类实践」延伸，不单独立报告 | ✅成熟 |
| **DSP 数字信号处理** | **Niche（CS 语境边缘，EE 交叉）** | 非 CS2023 独立 KA | 登记即可 | 否 | ✅成熟但对 CS 主线外围 |

**逐条依据**
- **HCI**：是 CS2023 十七 KA 之一（不是选修，是核心广度）。[^2] 顶校常态开课：CMU 有独立 HCI 学位项目与课程目录、Gatech OMSCS CS6750、UCSC CSE 265（2024–25）、Stony Brook HCI 专业方向均在架。[^3] 覆盖可用性/可达性/交互设计/评估方法——内容**已固化**，教学取证充分。→ 用户课表把它埋在 L5-07 桶里是**低估**；应提为 L5 独立报告。
- **嵌入式**：映射 CS2023 的 **SPD**（"驻留并与特定软件平台互操作的应用开发"，明确含嵌入式/移动/机器人平台）+ AR/OS 交叉。[^2][^4] 顶校/工程学院常态电子选修（UCSC 计算机工程电选 2024–25 列有嵌入式；覆盖 RTOS/时钟/GPIO/中断/总线/ADC-DAC/DMA/无线）。[^3] 机制层稳定；**具体 SoC/RTOS/工具链演进快**，报告须硬标「以某参考平台为锚」。
- **OS 内核实践**：本质是 L4-01 操作系统的动手延伸。顶校形态：CMU 15-410/15-605「OS Design & Implementation」（用 C+x86 写类 Unix 内核）、MIT 6.1810/6.828（xv6）、以及 Red Hat KDLP「Kernel Development Learning Pipeline」（2024 已在 Technion 落地，教 Linux 字符驱动/内存管理/DMA/中断）。[^5] 结论：**不单独立报告**，作为 L4-01 的「内核实证腿」增强项登记（xv6 / 本机 syscall 已在 L4-01 基线锚点）。
- **DSP**：不是 CS2023 的独立 KA（属 EE / 信号处理交叉，CS 内多见于音频/多媒体/嵌入式子话题）。[^3] 成熟但对本知识库的 CS 主线属外围。→ 维持"仅登记"，不升级。

---

## B. 新兴主题候选（2024–2026 进入主流课表的观察）

> 档：Must 必修级 / Recommended 推荐 / Niche 小众。成熟度硬标：⚙演进快（半衰期短，报告须锚版本+标"未定型") / ✅已稳定成课。

| # | 主题 | 重要性档 | 建议层 | 升级为报告？ | 成熟度硬标 | 核心依据 |
|---|------|----------|--------|--------------|-----------|----------|
| B1 | **AI/LLM 系统与内核**（从零实现 LM、训练/推理系统、CUDA/ML 编译、分布式并行、serving） | **Recommended（系统方向趋 Must）** | L6（邻接 L6-03 深度学习 / L6-06 HPC） | **是**（硬标） | ⚙**演进极快**——已稳定成"旗舰课"但内容年年重写 | Stanford CS336「Language Modeling from Scratch」（2024/25/26 连开，手写 tokenizer+Transformer+FlashAttention2+分布式）；CMU 11-868 LLM Systems、15-779 Adv MLSys(LLM Edition)。[^6][^7] 一手课程页 |
| B2 | **生成式 AI 应用 / LLM 工程**（RAG、agents、prompt、向量库、多模态） | Recommended | L5/L6（应用向） | **是**（强硬标，短半衰期） | ⚙**最不稳定**——顶校正式学分课少，多为 bootcamp/在线 | 一手信号：CS336 的应用侧、CMU Adv NLP（2025 春/秋）覆盖构建现代 NLP 系统。[^6] 其余多为 Udemy/Udacity/Coursera `⚠二手`——只能作行业需求线索，不承重 |
| B3 | **MLOps / AI 工程**（生产化、CI/CD/CT、数据/模型生命周期、部署监控） | Recommended | L4-06 SE 邻接 / L5 | **是** | ⚙实践驱动、工具链演进快 | DeepLearning.AI MLOps 四课专项、Duke「MLOps」专项（SageMaker/MLflow/HF）——均 Coursera `⚠二手`；顶校 CS 正式学分课偏少，行业需求强。归为 SE 的现代延伸 |
| B4 | **Rust 与内存安全 / 系统编程新范式** | **Recommended（系统方向趋 Must）** | L5，或并入 L3-04 范式 / L4-05 并发 | **是** | ✅语言成熟；课程采纳仍在扩张 | Stanford **CS110L「Safety in Systems Programming」**（教 Rust 写安全系统代码，后半并发）、CS340R「Rusty Systems」(2024 春, 研)、CMU 98-008 Intro to Rust（2024–25 连开，借用检查/并行/unsafe）。[^8] 一手课程页/大纲 |
| B5 | **数据密集型应用 / 云原生 / 分布式数据处理** | **Recommended** | L5/L6（邻接 L6-04 DB 内核 / L6-05 分布式云） | **是** | ✅成熟 | Stanford CS245「Principles of Data-Intensive Systems」、KTH ID2221「Data Intensive Computing Platforms」、UiS DAT535、Bologna「Cloud Computing & Big Data」；教材 **DDIA 第2版（Kleppmann & Riccomini, 2024）** 为标志。[^9] 一手课程页 + 教材 |
| B6 | **隐私前沿：差分隐私 / 机密计算 / 隐私保护 ML** | Recommended（DP）→ 局部 Niche（机密计算） | 并入 L5-04 安全/密码 或 L5 独立小节 | 部分——**建议并入 L5-04**，不单独立大报告 | DP 理论 ✅成熟；机密计算/PPML ⚙演进 | UCSD DSC291「Intro to Differential Privacy」(2024秋)、UToronto CSC2412H、USC CSCI 699 PPML(2024秋)；TPDP 2024 @Harvard。[^10] 一手课程页。机密计算(TEE/enclave)顶校正式课仍零散，硬标未定型 |
| B7 | **量子计算入门** | **Niche→Recommended（视兴趣）** | L5/L6 | 可选——低优先，暂不升级 | ✅入门课稳定；领域整体 ⚙演进 | UCSD CSE190(2024春)、CMU 18-619、Gatech OMSCS CS7400；NVIDIA CUDA-Q 混合平台（2024 Purdue 工作坊）。[^11] 一手课程页。对 CS 主线仍属方向选修，非必经 |
| B8 | **GPU / 加速器编程**（CUDA、massively parallel） | **Recommended** | **L6-06 HPC（已部分覆盖！）** | **是**——建议扩为 L6-06 显式子报告或独立报告 | ✅成熟（CUDA 稳定） | Caltech CS179「GPU Programming」、UGA CSCI4130/6130（2024秋/25春）、Northwestern CS368/468、JHU GPU 专项。[^12] ⚠注意：种子地图 **L6-06 高性能计算已含「GPU 编程」**，此项为**已在架的深化**而非新桶，避免重复立项 |
| B9 | **负责任 AI / AI 伦理（SEP 落地）** | **Recommended（因 SEP 已是 CS2023 整合 KA）** | 跨层/L5（对应 SEP KA） | **是** | ✅作为主题成熟；治理/法规 ⚙演进 | CS2023 将 **SEP 升为贯穿所有能力域的整合 KA**（bias/fairness/privacy/accountability/transparency）。[^1][^2] 顶校/项目：GMU「AI: Ethics, Policy & Society」(2024 新开)、Northeastern「Responsible & Ethical AI」、CMU RAI 项目。[^13] 一手 CS2023 + 课程页。**对应种子地图 SEP 缺口**（见背景） |

**小结（B 段）**：候选 9 条；成熟稳定可直接立报告的（B4/B5/B8/B9）；旗舰但须硬标演进的（B1）；应并入既有对象的（B6→L5-04，B8→L6-06）；短半衰期须强硬标的（B2）；实践向延伸（B3）；低优先可选（B7）。

---

## C. L6-07（专题研讨 + 论文与答辩）处理结论
**结论：维持「元课程 · 仅登记，不产三腿报告」。**
- L6-07 是**过程/元课程**（seminar + thesis + defense），无可用「一手规范 / 源码 / 本机实证」三腿取证的**稳定知识对象**——它教的是流程与产出方式，不是可实证的机制。
- 唯一可考虑的轻量例外：把「**研究方法论**」（如何读论文、实验设计与可复现性、学术写作与同行评审）抽成一条**薄登记条目/方法笔记**，但**不构成**符合 brief §2 三腿标准的正式报告。
- 建议：保持种子地图现状（`仅登记（元课程，不产报告）`）。若用户日后想要，可追加一条非承重的「研究方法 checklist」备忘，明确标注「非三腿报告」。

---

## D. 来源清单

### 一手（顶校课程页 / CS2023 官方 / 权威教材）
[^1]: CS2023 官方发布稿，IEEE Computer Society，发布日期 2024-06-05（AI 扩展 / SEP 整合 / 数学扩展 / Computational Science 删除）。https://www.computer.org/press-room/new-cs2023-curriculum-guide ｜核实 2026-07-25
[^2]: CS2023 Knowledge Areas 官方页（ACM csed.acm.org），17 个 KA 完整列表（含 HCI / SPD / SEP / AI / SEC / PDC）。https://csed.acm.org/knowledge-areas/ ｜核实 2026-07-25
[^3]: HCI/嵌入式/DSP 顶校在架佐证：CMU HCI 项目课程目录 http://coursecatalog.web.cmu.edu/schools-colleges/schoolofcomputerscience/humancomputerinteractionprogram/ ；Gatech OMSCS CS6750 HCI https://omscs.gatech.edu/cs-6750-human-computer-interaction ；UCSC 计算机工程电选 2024–25 https://undergrad.engineering.ucsc.edu/curriculum-charts/2024-2025/computer-engineering-electives-2024-2025/ ｜核实 2026-07-25
[^4]: CS2023 SPD（Specialized Platform Development）知识域说明，含嵌入式/移动/机器人平台。https://csed.acm.org/knowledge-areas-specialized-platform-development-spd-sigcse-2022-version/ ｜核实 2026-07-25
[^5]: OS 内核实践：CMU 15-605 OS Design & Implementation https://csd.cmu.edu/course/15605/s24 ；CMU 15-612 OS Practicum https://www.csd.cs.cmu.edu/course/15612/f26 ；Red Hat KDLP（2024, Technion 落地）https://research.redhat.com/blog/2024/02/26/kernel-development-learning-pipeline-program-brings-linux-to-college-students/ ｜核实 2026-07-25
[^6]: AI/LLM 系统与 NLP：Stanford CS336「Language Modeling from Scratch」(2024/25/26) https://cs336.stanford.edu/ ；CMU Advanced NLP Spring 2025 https://cmu-l3.github.io/anlp-spring2025/ ｜核实 2026-07-25
[^7]: LLM 系统课：CMU LLM Systems https://llmsystem.github.io/ ｜核实 2026-07-25
[^8]: Rust/内存安全：Stanford CS110L「Safety in Systems Programming」（csdiy 索引）https://csdiy.wiki/en/编程入门/Rust/CS110L/ ；Stanford CS340R「Rusty Systems」(2024春) https://web.stanford.edu/class/cs340r/ ；CMU 98-008 Intro to Rust https://www.coursicle.com/cmu/courses/STU/98008/ ｜核实 2026-07-25
[^9]: 数据密集型/云原生：Stanford CS245「Principles of Data-Intensive Systems」https://online.stanford.edu/courses/cs245-principles-data-intensive-systems ；KTH ID2221 https://id2221kth.github.io/ ；UiS DAT535 https://www.uis.no/en/course/DAT535_1 ；教材 DDIA 第2版（Kleppmann & Riccomini, 2024）｜核实 2026-07-25
[^10]: 差分隐私：UCSD DSC291(2024秋) https://cseweb.ucsd.edu/~yuxiangw/classes/DSC291-2024Fall/ ；UToronto CSC2412H https://sgs.calendar.utoronto.ca/course/csc2412h ；USC CSCI699 PPML(2024秋) https://spkreddy.org/ppmlfall2024.html ；TPDP 2024 https://tpdp.journalprivacyconfidentiality.org/2024/ ｜核实 2026-07-25
[^11]: 量子计算入门：UCSD CSE190(2024春) https://danielgrier.com/courses/CSE190/Sp24/ ；Gatech OMSCS CS7400 https://omscs.gatech.edu/cs-7400-intro-quantum-computing ；NVIDIA CUDA-Q https://developer.nvidia.com/cuda-q ｜核实 2026-07-25
[^12]: GPU/加速器：Caltech CS179 https://courses.cms.caltech.edu/cs179/ ；UGA CSCI4130/6130 https://www.cs.uga.edu/courses/content/csci-41306130 ；Northwestern CS368/468 https://www.mccormick.northwestern.edu/computer-science/academics/courses/descriptions/368-468.html ｜核实 2026-07-25
[^13]: 负责任 AI/SEP：GMU「AI: Ethics, Policy & Society」(2024 新开) https://www.gmu.edu/news/2024-12/new-course-creates-ethical-leaders-ai-driven-future ；CMU Responsible AI https://exec.cs.cmu.edu/custom/responsible-ai ｜核实 2026-07-25

### ⚠二手（趋势/在线课平台，仅作行业需求线索，不承重）
- ⚠ DeepLearning.AI MLOps 专项 / Duke MLOps 专项（Coursera）——MLOps 行业需求线索。https://www.coursera.org/specializations/mlops-machine-learning-duke ｜核实 2026-07-25
- ⚠ Udemy/Udacity/NareshIT 生成式 AI・LLM app・agents bootcamp——B2 行业热度线索，非学分课。https://www.udacity.com/course-collection/generative-ai-and-large-language-models ｜核实 2026-07-25
- ⚠ Class Central「Differential Privacy / MLOps」聚合榜——课程存在性交叉线索。https://www.classcentral.com/subject/differential-privacy ｜核实 2026-07-25
```
