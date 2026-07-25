# 第13组 人本与社会 · Round 3a 大主题分解 · 核实 2026-07-25

> 基线 Py3.11.15/np2.4.6/gcc13.3 @2026-07-25（本组两门课**无理论/系统课式可复现本机实证腿**：HCI 实证 = Nielsen 10 启发式清单的人工应用（无需库、可复现的专家判断）；**SEP 本机实证腿弱**，以援引权威框架条款替代源码/实证两腿——两处均在下文「实证腿说明」显式标注，不用记忆填平）。
> 本组：**L5-09 人机交互 HCI**（CS2023 独立 KA）/ **L5-10 社会伦理职业·负责任 AI SEP**（CS2023 升级为贯穿所有 KA 的跨层整合 KA）。二者均为 Round1 新增 · CS2023 核心 KA 缺口（round1-map §3-A ⭐必补）。
> 本文件为 Round 3a 产物：把每门课拆成**查全的大主题清单（教学单元级，覆盖全、少重叠）**，锚定一手权威目录/教材/官方框架文本。三腿深挖留待 Round 4。

---

## 锚定权威目录（URL + 版本 + 核实 2026-07-25）

### L5-09 HCI 锚点
| 锚点 | 版本/状态 | URL | 核实 |
|------|-----------|-----|------|
| CS2023 HCI KA（结构锚） | Version Gamma 2023-08；最终报告 2024-01（HCI KA 主席 Susan L. Epstein, Hunter College CUNY） | https://csed.acm.org/human-computer-interaction/ ；总目录 https://csed.acm.org/knowledge-areas/ | 2026-07-25 ✅（KA 页仅列委员/版本，具体 KU 明细在 Gamma 全文 PDF，未逐条抽取——见留白说明） |
| Rogers · Sharp · Preece《Interaction Design: Beyond Human-Computer Interaction》 | 权威教材（第5版 2019；第6版流通中） | 教材（无单一官方 URL；ISBN 承载） | 2026-07-25（教材锚，承大主题骨架） |
| Norman《The Design of Everyday Things》 | Revised & Expanded Edition, 2013 | 教材 | 2026-07-25（设计原则锚：示能/意符/映射/反馈/约束） |
| Nielsen 10 Usability Heuristics | 1994 因子分析定型；2020 措辞更新（10 条本身自 1994 未变） | https://www.nngroup.com/articles/ten-usability-heuristics/ | 2026-07-25 ✅ WebSearch 核实 |
| ISO 9241-11:2018（可用性定义） | 2018 第2版（有效性/效率/满意度 + 使用情境） | https://www.iso.org/obp/ui/#iso:std:iso:9241:-11:ed-2:v1:en | 2026-07-25 ✅ WebSearch 核实 |
| WCAG 2.2（无障碍） | **W3C Recommendation 2023-10-05**；2024-12-12 编辑更新（成功准则未变）；2025 采纳为 ISO/IEC 40500 | https://www.w3.org/TR/WCAG22/ | 2026-07-25 ✅ WebSearch 核实（POUR 四原则；共 86 条准则；移除 4.1.1 Parsing） |

### L5-10 SEP 锚点
| 锚点 | 版本/状态 | URL | 核实 |
|------|-----------|-----|------|
| CS2023 SEP KA（结构锚 · 跨层整合） | Version Gamma 2023-08；SEP Beta PDF 2023-03；最终报告 2024-01 | https://csed.acm.org/society-ethics-and-professionalism/ ；SEP Beta PDF https://csed.acm.org/wp-content/uploads/2023/03/SEP-Version-Beta.pdf | 2026-07-25 ✅（SEP core/KA hours 仅在 SEP KA 内计、其他 KA 的 SEP KU 不重复计时；⚠精确 core-hour 数值未从 PDF 逐条抽取，留白待 Round4 补） |
| ACM Code of Ethics and Professional Conduct | **2018 版**（4 节：一般伦理原则 / 职业责任 / 职业领导责任 / 遵守准则） | https://www.acm.org/code-of-ethics ；PDF https://www.acm.org/binaries/content/assets/about/acm-code-of-ethics-and-professional-conduct.pdf | 2026-07-25 ✅ WebSearch 核实 |
| NIST AI RMF 1.0 | 1.0（2023-01）：GOVERN/MAP/MEASURE/MANAGE 四功能 → 19 类 / 72 子类 | https://www.nist.gov/itl/ai-risk-management-framework | 2026-07-25 ✅ WebSearch 核实 |
| NIST AI 600-1 GenAI Profile | **发布 2024-07-26**；识别 12 类 GenAI 特有/放大风险（confabulation、prompt injection、数据隐私等）；2025 有配套 crosswalk | https://airc.nist.gov/ （AI 600-1） | 2026-07-25 ✅ WebSearch 核实 |
| EU AI Act = Regulation (EU) 2024/1689 | 风险分级（禁止/高风险/有限/最小）。**⚠时效硬标见下** | EUR-Lex Reg (EU) 2024/1689 | 2026-07-25 ✅（round2 已 WebSearch 核实时间线） |
| ISO/IEC 42001:2023（AI 管理体系） | 2023 首版 | ISO 42001 | 2026-07-25 |
| GDPR = Regulation (EU) 2016/679 | 现行 | EUR-Lex 2016/679 | 2026-07-25 |

> **⚠ EU AI Act 时效硬标（核实 2026-07-25，Round4 必复核）**：禁止性实践 Art.5 自 **2025-02-02** 适用；GPAI 义务自 **2025-08-05** 适用；但**高风险（Annex III）义务经 Digital Omnibus 简化包（Council 2026-06-29 通过）由原定 2026-08-02 推迟至 2027-12-02**（Annex I 产品类 2027-08-02→2028-08-02）。**切勿沿用「2026-08 高风险生效」旧记忆**：截至 2026-07-25，高风险全面义务尚未生效且已被官方推迟。

---

## L5-09 人机交互 HCI · 大主题清单

> 编号 | 大主题 | 一句话范围 | 来源锚点 | 跨课挂钩提示

| # | 大主题 | 一句话范围 | 来源锚点 | 跨课挂钩 |
|---|--------|-----------|----------|----------|
| HCI-1 | **人的认知基础与人因** | 感知/记忆/注意/运动模型、心智模型、认知负荷——设计为何要迁就人的局限 | Rogers-Sharp-Preece Ch. 认知；Norman（心智模型/概念模型） | ↔ 无直接 CS 前置；心智模型接 HCI-3 设计原则、HCI-5 认知走查 |
| HCI-2 | **可用性的可测量定义** | 把「好用」落成有效性/效率/满意度三维 + 使用情境，全课度量之锚 | ISO 9241-11:2018 | ↔ 度量的统计推断（样本量/显著性）借道 **L2-03 概率统计** |
| HCI-3 | **交互设计原则** | Norman 五原则（可视性/映射/反馈/示能 affordance/约束）+ 一致性/防错等设计律 | Norman《DOET》2013；Rogers-Sharp-Preece 设计原则章 | ↔ 与 HCI-4 启发式同源；界面图形**渲染管线归 L5-03 图形学**，HCI 只做交互层 |
| HCI-4 | **评估方法体系（analytic vs empirical）** | 启发式评估 / 认知走查 / 可用性测试 / A/B 测试 / 问卷量表（SUS）——**承重点是「何时用哪种 + 各自漏什么」** | Nielsen 10 Heuristics；ISO 9241-11；SUS 量表 | ↔ **A/B 测试 ↔ L2-03**（假设检验/功效/样本量/CLT）；本组唯一可本机演示实证点在此（启发式清单） |
| HCI-5 | **以用户为中心的设计流程 + 原型迭代（UCD）** | 需求获取→低/高保真原型→评估→迭代的闭环；参与式/迭代式设计 | Rogers-Sharp-Preece（UCD/原型章） | ↔ 需求/迭代与 **L4-06 软件工程**（用户故事、敏捷）对接，但 HCI 承交互质量、软工承过程 |
| HCI-6 | **无障碍与包容性设计** | WCAG 2.2 POUR 四原则（感知/可操作/可理解/健壮）+ 辅助技术；技术标准侧 | WCAG 2.2（W3C Rec 2023-10-05） | ↔ **与 L5-10 SEP 分账**：此处做**技术标准**，SEP 做**社会正当性/公平包容诉求**——勿双重深挖 |
| HCI-7 | **交互范式与新型模态** | GUI/直接操纵、触控/移动、语音/多模态、协作式 CSCW、以及 AR/VR/AI 交互等新兴前沿（硬标演进） | Rogers-Sharp-Preece（交互类型/新技术章）；CS2023 HCI KA | ↔ AR/VR 渲染归 L5-03；AI 交互（mixed-initiative/信任/可解释 UI）接 L5-10 透明维 + L5-02/L6-03 |

**实证腿说明（HCI）**：本课**无 sympy/numpy 式数值验证腿、无源码 gdb 腿**。唯一可本机做的实证 = **腿①一手文档 + Nielsen 10 启发式清单的人工应用**：取真实界面（登录页/表单）逐条打违反项 + 0–4 严重度，产「违反第 N 条 + 严重度 + 建议」问题表——可复现、可教、不需库的结构化专家判断（analytic 实证）。若升级到可用性测试则产行为数据（empirical），但需招募用户、非本机可跑。Round4 报告须如实标注「HCI 实证 = 专家判断/框架清单，非代码实证」。

---

## L5-10 社会伦理职业 / 负责任 AI SEP · 大主题清单

> SEP = **系统对社会的宏观影响 + 规范 + 责任**（不做界面级交互，那是 HCI）。作为**跨层整合 KA**，正确形态是横切嵌入 AI/安全/数据/软工各课；独立报告承「框架 + 规范」骨架，技术手段指回对应课避免重复立项。

| # | 大主题 | 一句话范围 | 来源锚点 | 跨课挂钩（SEP 如何跨层挂 AI/安全/数据课） |
|---|--------|-----------|----------|------------------------------------------|
| SEP-1 | **规范性伦理框架（分析工具）** | 后果论/义务论/德性论/契约论(Rawls) 在技术决策中的应用——教学价值是**识别技术选择背后隐含的伦理框架** | CS2023 SEP KA（Analytical Tools）；经典伦理学文本 | 底层分析工具，横切所有下述维度；识别 NIST/EU 风险框架骨架=后果论、ACM Code=义务论+德性论混合 |
| SEP-2 | **专业操守与责任（ACM Code 2018）** | 逐条：公共利益优先、避免伤害、诚实、公平不歧视、尊重隐私、职业胜任、领导责任——「职业」腿一手承重 | ACM Code of Ethics 2018（4 节结构） | ↔ **L4-06 软件工程**（责任归属、发布决策、事件响应的工程落点）；↔ L6-07 元课程（答辩/专业素养） |
| SEP-3 | **负责任 AI 多维张力** | 公平/问责/透明/隐私/可持续五维的诉求 + **硬冲突**（公平不可能性定理、隐私 vs 透明） | round2 表B；CS2023 SEP + NIST RMF trustworthy 特征 | ↔ **L5-02/L6-03**：偏见/公平度量、数据集伦理、模型卡（Model Cards）/数据表（Datasheets）——SEP 承「为何要做」，AI 课承「怎么做」；公平不可能性定理需 L5-04/L2-03 交叉 |
| SEP-4 | **AI 治理框架** | NIST AI RMF 四功能（GOVERN/MAP/MEASURE/MANAGE）+ GenAI Profile；EU AI Act 风险分级 + 关键条款（Art.5/13/14/26/50）；ISO/IEC 42001 | NIST AI RMF 1.0 + AI 600-1；EU AI Act 2024/1689；ISO 42001 | ↔ **L5-02/L6-03**（高风险 AI 系统的合规义务落在模型开发）；↔ L4-06（部署者义务/人类监督流程）；**必带 EU AI Act 高风险推迟至 2027-12-02 时效硬标** |
| SEP-5 | **隐私、数据保护与知识产权** | GDPR 原则、数据最小化、知情同意、被遗忘权；软件/数据的 IP、许可证、开源——**规范面** | GDPR (EU) 2016/679；ACM Code 1.6/1.5；CS2023 SEP（Privacy/IP KU） | ↔ **L5-04 安全**：隐私**技术**手段（差分隐私 N-15/机密计算）已并入 L5-04——SEP 承「为何要保护 + 法律要求」，L5-04 承「怎么保护」；↔ **L4-03 数据库/数据课**（数据治理、留存、最小化落地） |
| SEP-6 | **计算的社会情境与影响** | 数字鸿沟/公平获取、自动化对劳动/经济的影响、错误信息与平台责任、监控与公民自由、计算机犯罪与法律政策 | CS2023 SEP KA（Social Context / Security Policies & Laws KU） | ↔ L5-04（安全政策/计算机犯罪）；↔ 广义横切——SEP 唯一「宏观社会尺度」大主题，区别于其余偏「系统/框架」维 |
| SEP-7 | **可持续性与算力伦理** | 训练/推理能耗核算、碳报告、模型效率的环境成本（CS2023 SEP 明列；治理条款尚软，多为自愿披露） | CS2023 SEP KA（Sustainability KU） | ↔ **L5-02/L6-03/L6-06**（模型效率/HPC 能耗）——SEP 承「为何要算这笔账」，性能/HPC 课承「怎么省」 |

**实证腿说明（SEP · 本机实证腿弱）**：本课**既无数值可 sympy 验证、也无源码可 gdb 跑**——**腿②源码 + 腿③本机实证两腿缺失，仅腿①一手文档承重**。其「实证」只能是**援引权威框架的具体条款**做可核对断言，例如：
> 「EU AI Act Art.14 要求高风险系统具备**有效人类监督**」；「ACM Code 1.6 要求**尊重隐私**、1.2 要求**避免伤害**」；「NIST AI RMF 将风险管理分解为 GOVERN/MAP/MEASURE/MANAGE 四功能、19 类 72 子类」。

唯一可做的轻量「演示」= 把 round2 表B（维度 × 技术手段 × 框架条款）填成一张可核对对照表，或对某 AI 系统做一次 NIST RMF 四功能的**桌面走查**（概念性、非可复现实证）。Round4 报告须**显式声明「本课缺源码/本机实证两腿，以框架条款援引替代」**，不得伪造代码实证。所有「技术手段」列（差分隐私/SHAP/模型卡实现等）的深挖属 L5-02/L5-04/L6-03，SEP 只承「为何要做 + 框架要求什么」。

---

## 建议大主题数
- **L5-09 HCI：K = 7**（HCI-1 人因认知 / HCI-2 可用性定义 / HCI-3 设计原则 / HCI-4 评估方法体系 / HCI-5 UCD 流程与原型 / HCI-6 无障碍 / HCI-7 交互范式与新模态）。核心承重在 HCI-2/3/4；HCI-1/7 为广度腿，HCI-6 与 SEP 分账。
- **L5-10 SEP：K = 7**（SEP-1 伦理框架 / SEP-2 ACM Code 职业操守 / SEP-3 负责任 AI 多维 / SEP-4 AI 治理框架 / SEP-5 隐私·数据·IP / SEP-6 社会情境与影响 / SEP-7 可持续算力伦理）。承重在 SEP-2/3/4；其余为跨层挂钩腿。

> **本组合计建议大主题数 T = 14**（HCI 7 + SEP 7）。
> 跨组接口三条：①HCI-4 的 A/B 测试 ↔ L2-03 假设检验/样本量；②SEP-5 隐私技术手段 ↔ L5-04（N-15 差分隐私已并入）；③SEP-3 公平/偏见与模型卡 ↔ L5-02/L6-03。时效红线：**EU AI Act 高风险义务已推迟至 2027-12-02，勿用旧记忆**。
