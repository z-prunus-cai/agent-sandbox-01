# Round3c · L6-02 程序分析与形式验证 · 报告 prompt（P2）

```text
[P2] L6-02·大主题V1 不可判定性与近似框架 — 报告prompt

【任务】为「L6-02·大主题V1 不可判定性与近似框架」产出一份 report-format v3（广度优先的"教辅"）报告。先读 study/cs-kb/report-format.md（v3 模板）、study/cs-kb/brief.md，再动笔。落盘目标：study/cs-kb/findings/026-program-analysis-verification.md（本大主题写入该文件；若判定拆分，用 026-program-analysis-verification-a/-b… 同 slug）。

【粒度判定（先做）】动笔前先判「本大主题产 1 份报告，还是拆成 N 份（+理由）」：小主题数、是否跨机制、预计篇幅是判据。把判定结论写在报告最前（一行）。本大主题小主题 4 个（V1.1–V1.4），是全课世界观钉子，默认 1 份。

【v3 格式硬性要求】
- 标题层级固定：`#`（大主题=报告标题）→ `##`（小主题=章节）→ `###`（每个内容项一节）。
- `###` 下不写「核心概念：」「辅助说明：」等任何标签，直接正文、靠空行分段。
- 每个 `###` 内容项必含：核心说明（是什么/定义/关键结论，正确、必要处标时效）+ 充分辅助说明（面向初学者把它讲懂所需：直觉/为什么/最小例子/易错点/前后关联，写到初学者能懂为止，可多段；这是必需项不是点缀）。
- 判定性/近似的形式陈述、格（lattice）/序关系、推理规则一律独占行或独占块，绝不内联埋进句子。
- 教辅口吻；广度优先：小主题下内容项查全、宁多列浅讲；每项讲到初学者懂即止，不做专家级纵深（Rice 定理只讲直觉+结论，不铺完整证明）。

【小主题清单（=`##` 章节，按此序）与应覆盖内容项】
细目见 study/cs-kb/round3b-subtopics/g08-languages-compilers.md 中 L6-02 大主题 V1（V1.1–V1.4），下游须读该处对齐：
- V1.1 Rice 定理与不可判定性：非平凡语义性质在图灵完备语言上不可判定（全课世界观钉子，承 L3-05 计算理论）。
- V1.2 静态分析总览与 WHILE 语言：PPA 运行示例语言、五类分析导览。
- V1.3 over-/under-approximation：可靠 sound vs 完备 complete、假阳/假阴。
- V1.4 分析的正确性论证：相对语义证明分析可靠。

【验证纪律（强制）】
- 多来源比对强制：每个内容项 ≥2 个独立来源交叉核对，优先一手 PPA（Principles of Program Analysis, Nielson/Nielson/Hankin, Springer 1999/corrected 2005）到章/节；Rice 定理陈述另用一手计算理论来源核对；来源打架时两边都记、点明分歧、不和稀泥。
- 本机/工具实证可选：z3 等仅在便宜且能加固某定义/输出时做，不强制，且不得替代多来源比对。
- 不编造：定义/术语（sound/complete 与 over/under-approx 的对应）来自比对通过的来源，或标「待核」，绝不凭记忆填；sound/complete 与假阳/假阴的对应关系易被各家书写反，务必核对到出处。
- 标时效：每份给核实日期；每个 `##` 章节末集中列来源与时效（锚点=教材§/规范/文档+版本+核实日期；冲突项两边定位），不逐项脚注。

【权威锚点清单（取自 round3a）】
- 一手：PPA《Principles of Program Analysis》(Nielson/Nielson/Hankin)，Springer 1999（corrected 2005），本大主题锚 Ch.1。https://link.springer.com/book/10.1007/978-3-662-03811-6 （核实 2026-07-25）
- 第二锚点：计算理论一手（Rice 定理，承 L3-05）用于交叉核对不可判定性陈述。
```

```text
[P2] L6-02·大主题V2 数据流分析 — 报告prompt

【任务】为「L6-02·大主题V2 数据流分析」产出一份 report-format v3（广度优先的"教辅"）报告。先读 study/cs-kb/report-format.md、study/cs-kb/brief.md 再动笔。落盘目标：study/cs-kb/findings/026-program-analysis-verification.md（同 slug；拆分用 -a/-b…）。

【粒度判定（先做）】先判「1 份 or 拆 N 份 + 理由」（小主题数/是否跨机制/篇幅），结论写报告最前一行。本大主题小主题 5 个（V2.1–V2.5，与 L4-04 C9 同一数学、目标不同），默认 1 份。

【v3 格式硬性要求】
- 层级 `#`→`##`→`###`；`###` 下不写标签，直接正文空行分段。
- 每个 `###` 必含核心说明 + 充分辅助说明（初学者直觉/为什么/最小例子/易错点/关联，可多段，必需）。
- 格与偏序定义、传递函数、汇合运算（⊔/⊓）、MFP/MOP 方程、不动点式一律独占行/块，不内联。
- 教辅口吻；广度优先查全浅讲；讲到初学者懂即止，不做专家纵深。

【小主题清单（=`##` 章节，按此序）与应覆盖内容项】
细目见 study/cs-kb/round3b-subtopics/g08-languages-compilers.md 中 L6-02 大主题 V2（V2.1–V2.5）：
- V2.1 四经典分析与方向：到达定值/活跃变量/可用表达式/极忙表达式、前向-后向、may-must。
- V2.2 格论基础：偏序/格/完全格、升链条件（ACC）。
- V2.3 单调框架与传递函数：monotone / distributive framework。
- V2.4 MFP vs MOP：最大不动点解 vs meet-over-paths、二者相等的条件（分配性）。
- V2.5 过程间数据流：call/return 匹配、上下文（交 L4-04 C10.3，点名不展开）。

【验证纪律（强制）】
- 多来源比对强制：每项 ≥2 独立源，优先一手 PPA（Ch.2）到章/节/定义号；MFP≤MOP 的方向与相等条件、may/must 与 ⊔/⊓ 的对应易混，须核对原书并两边都记。
- 本机/工具实证可选：Python 手写活跃变量分析 / 格性质验证仅作加固补充，不强制，不替代比对。
- 不编造；标时效；每个 `##` 章节末集中列来源与时效（含冲突项定位）。

【权威锚点清单（取自 round3a）】
- 一手：PPA（Nielson/Nielson/Hankin, Springer 1999/2005），本大主题锚 Ch.2。https://link.springer.com/book/10.1007/978-3-662-03811-6 （核实 2026-07-25）
- 第二锚点：龙书 2nd ed 2006 Ch.9 数据流框架（同数学、交叉核对经典分析定义）。
```

```text
[P2] L6-02·大主题V3 约束式分析 — 报告prompt

【任务】为「L6-02·大主题V3 约束式分析」产出一份 report-format v3（广度优先的"教辅"）报告。先读 study/cs-kb/report-format.md、study/cs-kb/brief.md 再动笔。落盘目标：study/cs-kb/findings/026-program-analysis-verification.md（同 slug；拆分用 -a/-b…）。

【粒度判定（先做）】先判「1 份 or 拆 N 份 + 理由」（小主题数/是否跨机制/篇幅），结论写报告最前一行。本大主题小主题 4 个（V3.1–V3.4），默认 1 份。

【v3 格式硬性要求】
- 层级 `#`→`##`→`###`；`###` 下不写标签，直接正文空行分段。
- 每个 `###` 必含核心说明 + 充分辅助说明（初学者直觉/为什么/最小例子/易错点/关联，可多段，必需）。
- 约束生成规则、集合约束式、图闭包求解步骤一律独占行/块，不内联。
- 教辅口吻；广度优先查全浅讲；讲到初学者懂即止，不做专家纵深。

【小主题清单（=`##` 章节，按此序）与应覆盖内容项】
细目见 study/cs-kb/round3b-subtopics/g08-languages-compilers.md 中 L6-02 大主题 V3（V3.1–V3.4）：
- V3.1 控制流分析(CFA)：高阶/函数式语言"谁调用谁"。
- V3.2 抽象 0-CFA：约束生成规则。
- V3.3 集合约束与求解：条件约束、图闭包求解。
- V3.4 上下文敏感度：k-CFA、uniform/poly variance。

【验证纪律（强制）】
- 多来源比对强制：每项 ≥2 独立源，优先一手 PPA（Ch.3）到章/节；0-CFA/k-CFA 的约束规则各家记法有别，须核对原书、冲突两边都记。
- 本机/工具实证可选：Python 实现 0-CFA 约束仅作加固补充，不强制，不替代比对。
- 不编造；标时效；每个 `##` 章节末集中列来源与时效（含冲突项定位）。

【权威锚点清单（取自 round3a）】
- 一手：PPA（Nielson/Nielson/Hankin, Springer 1999/2005），本大主题锚 Ch.3。https://link.springer.com/book/10.1007/978-3-662-03811-6 （核实 2026-07-25）
- 第二锚点：CFA 综述或 Shivers 论文一手（k-CFA 上下文敏感度定义交叉核对）。
```

```text
[P2] L6-02·大主题V4 抽象解释 — 报告prompt

【任务】为「L6-02·大主题V4 抽象解释」产出一份 report-format v3（广度优先的"教辅"）报告。先读 study/cs-kb/report-format.md、study/cs-kb/brief.md 再动笔。落盘目标：study/cs-kb/findings/026-program-analysis-verification.md（同 slug；拆分用 -a/-b…）。

【粒度判定（先做）】先判「1 份 or 拆 N 份 + 理由」（小主题数/是否跨机制/篇幅），结论写报告最前一行。本大主题小主题 4 个（V4.1–V4.4，抽象解释框架涵盖数据流），默认 1 份。

【v3 格式硬性要求】
- 层级 `#`→`##`→`###`；`###` 下不写标签，直接正文空行分段。
- 每个 `###` 必含核心说明 + 充分辅助说明（初学者直觉/为什么/最小例子/易错点/关联，可多段，必需）。
- Galois 连接式（α/γ、α∘γ⊑id 等）、抽象域定义、widening ∇ / narrowing △ 迭代式一律独占行/块，不内联。
- 教辅口吻；广度优先查全浅讲；讲到初学者懂即止，不做专家纵深（Galois 连接只讲直觉+一个区间域例子）。

【小主题清单（=`##` 章节，按此序）与应覆盖内容项】
细目见 study/cs-kb/round3b-subtopics/g08-languages-compilers.md 中 L6-02 大主题 V4（V4.1–V4.4）：
- V4.1 Galois 连接：抽象/具体化 α/γ、抽象域正确性。
- V4.2 抽象域：符号/区间/同余/多面体、精度-代价权衡。
- V4.3 不动点迭代 + widening/narrowing：加宽保证终止、收窄恢复精度。
- V4.4 与数据流的统一视角：抽象解释框架涵盖数据流分析（回接 V2）。

【验证纪律（强制）】
- 多来源比对强制：每项 ≥2 独立源，优先一手 PPA（Ch.4）与 Cousot & Cousot POPL'77 到章/节；widening/narrowing 的顺序与终止论证易错，须核对原书、冲突两边都记。
- 本机/工具实证可选：Python 区间分析 + widening 仅作加固补充，不强制，不替代比对。
- 不编造；标时效；每个 `##` 章节末集中列来源与时效（含冲突项定位）。

【权威锚点清单（取自 round3a）】
- 一手：PPA（Nielson/Nielson/Hankin, Springer 1999/2005），本大主题锚 Ch.4。https://link.springer.com/book/10.1007/978-3-662-03811-6 （核实 2026-07-25）
- 第二锚点：Cousot & Cousot POPL'77《Abstract Interpretation》原文（Galois 连接的一手出处，交叉核对）。
```

```text
[P2] L6-02·大主题V5 类型与效果系统 — 报告prompt

【任务】为「L6-02·大主题V5 类型与效果系统」产出一份 report-format v3（广度优先的"教辅"）报告。先读 study/cs-kb/report-format.md、study/cs-kb/brief.md 再动笔。落盘目标：study/cs-kb/findings/026-program-analysis-verification.md（同 slug；拆分用 -a/-b…）。

【粒度判定（先做）】先判「1 份 or 拆 N 份 + 理由」（小主题数/是否跨机制/篇幅），结论写报告最前一行。本大主题小主题 4 个（V5.1–V5.4，交 L5-06 类型理论、此处作分析工具用），默认 1 份。

【v3 格式硬性要求】
- 层级 `#`→`##`→`###`；`###` 下不写标签，直接正文空行分段。
- 每个 `###` 必含核心说明 + 充分辅助说明（初学者直觉/为什么/最小例子/易错点/关联，可多段，必需）。
- 注解类型规则、效果推断规则（Γ ⊢ e : τ & φ 形式）、子效果 ⊑ 关系一律独占行/块，不内联。
- 教辅口吻；广度优先查全浅讲；讲到初学者懂即止，不做专家纵深。

【小主题清单（=`##` 章节，按此序）与应覆盖内容项】
细目见 study/cs-kb/round3b-subtopics/g08-languages-compilers.md 中 L6-02 大主题 V5（V5.1–V5.4）：
- V5.1 注解类型系统：把分析信息编码进类型（交 L5-06 T4/T7，此处作分析工具，点名不展开）。
- V5.2 效果(effect)推断：副作用/异常/通信效果的推断。
- V5.3 子效果与代数性质：subeffecting、effect 上的序。
- V5.4 与推断算法的关系：复用合一/约束求解（T7 技术）。

【验证纪律（强制）】
- 多来源比对强制：每项 ≥2 独立源，优先一手 PPA（Ch.5）到章/节；"分析即类型系统"的编码与 L5-06 的类型系统记法有交叠，须核对原书、点明分歧两边都记。
- 本机/工具实证可选（本大主题实证机会少，以文本比对为主）；不得以实证替代多来源比对。
- 不编造；标时效；每个 `##` 章节末集中列来源与时效（含冲突项定位）。

【权威锚点清单（取自 round3a）】
- 一手：PPA（Nielson/Nielson/Hankin, Springer 1999/2005），本大主题锚 Ch.5。https://link.springer.com/book/10.1007/978-3-662-03811-6 （核实 2026-07-25）
- 第二锚点：TAPL（Pierce, MIT Press 2002）相关章（合一/约束式类型，交叉核对推断技术）。
```

```text
[P2] L6-02·大主题V6 分析求解算法 — 报告prompt

【任务】为「L6-02·大主题V6 分析求解算法」产出一份 report-format v3（广度优先的"教辅"）报告。先读 study/cs-kb/report-format.md、study/cs-kb/brief.md 再动笔。落盘目标：study/cs-kb/findings/026-program-analysis-verification.md（同 slug；拆分用 -a/-b…）。

【粒度判定（先做）】先判「1 份 or 拆 N 份 + 理由」（小主题数/是否跨机制/篇幅），结论写报告最前一行。本大主题小主题 3 个（V6.1–V6.3，为本课最少者），默认 1 份、不建议再拆。

【v3 格式硬性要求】
- 层级 `#`→`##`→`###`；`###` 下不写标签，直接正文空行分段。
- 每个 `###` 必含核心说明 + 充分辅助说明（初学者直觉/为什么/最小例子/易错点/关联，可多段，必需）。
- worklist 算法伪码、方程/不等式系统、chaotic iteration 步骤、复杂度式一律独占行/块，不内联。
- 教辅口吻；广度优先查全浅讲；讲到初学者懂即止，不做专家纵深。

【小主题清单（=`##` 章节，按此序）与应覆盖内容项】
细目见 study/cs-kb/round3b-subtopics/g08-languages-compilers.md 中 L6-02 大主题 V6（V6.1–V6.3）：
- V6.1 worklist 算法：通用不动点迭代引擎。
- V6.2 方程/约束系统求解：chaotic iteration、迭代序影响。
- V6.3 算法共性与复杂度：各分析归约到同一求解骨架（回收 V2–V5）。

【验证纪律（强制）】
- 多来源比对强制：每项 ≥2 独立源，优先一手 PPA（Ch.6）到章/节；worklist 伪码与复杂度界各家写法有别，须核对原书、冲突两边都记。
- 本机/工具实证可选（可加分）：Python 通用 worklist 求解器仅在便宜且能加固算法步骤/输出时做；不得替代多来源比对。
- 不编造；标时效；每个 `##` 章节末集中列来源与时效（含冲突项定位）。

【权威锚点清单（取自 round3a）】
- 一手：PPA（Nielson/Nielson/Hankin, Springer 1999/2005），本大主题锚 Ch.6。https://link.springer.com/book/10.1007/978-3-662-03811-6 （核实 2026-07-25）
- 第二锚点：龙书 2nd ed 2006 Ch.9 迭代数据流求解（worklist 迭代序交叉核对）。
```

```text
[P2] L6-02·大主题V7 符号执行 — 报告prompt

【任务】为「L6-02·大主题V7 符号执行」产出一份 report-format v3（广度优先的"教辅"）报告。先读 study/cs-kb/report-format.md、study/cs-kb/brief.md 再动笔。落盘目标：study/cs-kb/findings/026-program-analysis-verification.md（同 slug；拆分用 -a/-b…）。本大主题属"验证半场"，PPA 不覆盖，须用专门一手锚点（King 1976 / KLEE）+ SMT 后端（接 V10）。

【粒度判定（先做）】先判「1 份 or 拆 N 份 + 理由」（小主题数/是否跨机制/篇幅），结论写报告最前一行。本大主题小主题 4 个（V7.1–V7.4，用 V10 SMT 作后端），默认 1 份。

【v3 格式硬性要求】
- 层级 `#`→`##`→`###`；`###` 下不写标签，直接正文空行分段。
- 每个 `###` 必含核心说明 + 充分辅助说明（初学者直觉/为什么/最小例子/易错点/关联，可多段，必需）。
- 符号状态、路径条件公式（path condition）、可行性判定的 SMT 查询一律独占行/块，不内联。
- 教辅口吻；广度优先查全浅讲；讲到初学者懂即止，不做专家纵深。

【小主题清单（=`##` 章节，按此序）与应覆盖内容项】
细目见 study/cs-kb/round3b-subtopics/g08-languages-compilers.md 中 L6-02 大主题 V7（V7.1–V7.4）：
- V7.1 符号状态与路径条件：符号值、路径公式积累。
- V7.2 路径爆炸与剪枝：路径数指数、合并/剪枝策略。
- V7.3 混合执行(concolic)：具体+符号交替。
- V7.4 SMT 后端可达性：路径条件交 SMT 判可达（接 V10，点名不展开）。

【验证纪律（强制）】
- 多来源比对强制：每项 ≥2 独立源，优先一手 King 1976《Symbolic Execution and Program Testing》(CACM) + KLEE 论文/文档；因验证半场无 PPA 兜底，第二独立锚点为硬要求；来源打架两边都记、点明分歧。
- 工具实证可选（可加分）：z3-solver 5.0.0.0 判路径条件可行性仅作加固补充，不强制，不替代多来源比对；z3 需 pip 装（`python3 -m pip install z3-solver`）。
- 不编造；标时效；每个 `##` 章节末集中列来源与时效（含冲突项定位）。

【权威锚点清单（取自 round3a）】
- 一手：King 1976《Symbolic Execution and Program Testing》(CACM)；KLEE (Cadar et al., OSDI'08) 论文与文档。https://github.com/klee/klee （核实 2026-07-25）
- 第二锚点/后端：z3-solver 5.0.0.0（本机可跑，作可达性判定后端，接 V10）。
```

```text
[P2] L6-02·大主题V8 模型检验 — 报告prompt

【任务】为「L6-02·大主题V8 模型检验」产出一份 report-format v3（广度优先的"教辅"）报告。先读 study/cs-kb/report-format.md、study/cs-kb/brief.md 再动笔。落盘目标：study/cs-kb/findings/026-program-analysis-verification.md（同 slug；拆分用 -a/-b…）。本大主题属"验证半场"，PPA 不覆盖，须用专门一手锚点（Clarke/Grumberg/Peled《Model Checking》）。

【粒度判定（先做）】先判「1 份 or 拆 N 份 + 理由」（小主题数/是否跨机制/篇幅），结论写报告最前一行。本大主题小主题 5 个（V8.1–V8.5），默认 1 份。

【v3 格式硬性要求】
- 层级 `#`→`##`→`###`；`###` 下不写标签，直接正文空行分段。
- 每个 `###` 必含核心说明 + 充分辅助说明（初学者直觉/为什么/最小例子/易错点/关联，可多段，必需）。
- LTL/CTL 时序公式（G/F/X/U、AG/EF 等）、Kripke 结构定义、BMC 有界展开式一律独占行/块，不内联。
- 教辅口吻；广度优先查全浅讲；讲到初学者懂即止，不做专家纵深（CEGAR 只作"一瞥"）。

【小主题清单（=`##` 章节，按此序）与应覆盖内容项】
细目见 study/cs-kb/round3b-subtopics/g08-languages-compilers.md 中 L6-02 大主题 V8（V8.1–V8.5）：
- V8.1 时序逻辑 LTL/CTL：时序算子、性质规约。
- V8.2 Kripke 结构与状态空间：迁移系统建模。
- V8.3 显式状态检验与状态爆炸：自动机理论法、on-the-fly。
- V8.4 符号模型检验：BDD、BMC（SAT 有界展开）。
- V8.5 反例与抽象精化：反例生成、CEGAR 一瞥。

【验证纪律（强制）】
- 多来源比对强制：每项 ≥2 独立源，优先一手 Clarke/Grumberg/Peled《Model Checking》到章/节；因验证半场无 PPA 兜底，第二独立锚点为硬要求；LTL vs CTL 表达力差异、算子记法易混，须核对原书、冲突两边都记。
- 工具实证可选：SPIN/TLA+ 本机可用性待核（round3b 注③：需装或降级为概念+手算小状态机）；先概念 + 手算，工具实证仅作加固补充，不强制、不替代比对，未装则显式标「未验证」。
- 不编造；标时效；每个 `##` 章节末集中列来源与时效（含冲突项定位）。

【权威锚点清单（取自 round3a）】
- 一手：Clarke/Grumberg/Peled《Model Checking》(MIT Press)。（核实 2026-07-25）
- 第二锚点：LTL/CTL 语义可另用一手时序逻辑教材/综述交叉核对；工具层 SPIN 或 TLA+ 官方文档（本机可用性待核）。
```

```text
[P2] L6-02·大主题V9 演绎验证：Hoare 逻辑与最弱前条件 — 报告prompt

【任务】为「L6-02·大主题V9 演绎验证：Hoare 逻辑与最弱前条件」产出一份 report-format v3（广度优先的"教辅"）报告。先读 study/cs-kb/report-format.md、study/cs-kb/brief.md 再动笔。落盘目标：study/cs-kb/findings/026-program-analysis-verification.md（同 slug；拆分用 -a/-b…）。本大主题属"验证半场"，PPA 不覆盖，须用专门一手锚点（Software Foundations），承 L5-06 T1 公理语义。

【粒度判定（先做）】先判「1 份 or 拆 N 份 + 理由」（小主题数/是否跨机制/篇幅），结论写报告最前一行。本大主题小主题 5 个（V9.1–V9.5，接 L5-06 公理语义），默认 1 份。

【v3 格式硬性要求】
- 层级 `#`→`##`→`###`；`###` 下不写标签，直接正文空行分段。
- 每个 `###` 必含核心说明 + 充分辅助说明（初学者直觉/为什么/最小例子/易错点/关联，可多段，必需）。
- Hoare 三元组 {P}C{Q}、各条推理规则（赋值/序列/条件/循环/consequence）、wp 演算式、验证条件（VC）一律独占行/块，不内联。
- 教辅口吻；广度优先查全浅讲；讲到初学者懂即止，不做专家纵深。

【小主题清单（=`##` 章节，按此序）与应覆盖内容项】
细目见 study/cs-kb/round3b-subtopics/g08-languages-compilers.md 中 L6-02 大主题 V9（V9.1–V9.5）：
- V9.1 Hoare 三元组：{P}C{Q}、部分正确性 vs 全部正确性（承 L5-06 T1 公理语义，点名不展开）。
- V9.2 Hoare 规则与推导：赋值/序列/条件/循环/consequence 规则。
- V9.3 循环不变式与变式：终止性、ranking function。
- V9.4 最弱前条件(WP)演算：wp 的计算规则。
- V9.5 验证条件生成(VCGen)：从注解程序生成 VC 交求解器。

【验证纪律（强制）】
- 多来源比对强制：每项 ≥2 独立源，优先一手 Software Foundations（Hoare/Coq 卷）到章/节；因验证半场无 PPA 兜底，第二独立锚点为硬要求；赋值规则的向后代换 {Q[E/x]} x:=E {Q} 方向、部分/全部正确性区分易错，须核对原书、冲突两边都记。
- 工具实证可选（可加分）：z3-solver 5.0.0.0 验证生成的 VC 仅作加固补充，不强制，不替代多来源比对；z3 需 pip 装。
- 不编造；标时效；每个 `##` 章节末集中列来源与时效（含冲突项定位）。

【权威锚点清单（取自 round3a）】
- 一手：Software Foundations（Pierce et al.，Coq 一手，Hoare 逻辑卷）。https://softwarefoundations.cis.upenn.edu/ （核实 2026-07-25）
- 第二锚点：公理语义原始一手（Hoare 1969《An Axiomatic Basis for Computer Programming》或 Dijkstra wp 一手）交叉核对；承 L5-06 T1。
```

```text
[P2] L6-02·大主题V10 SMT 求解引擎 — 报告prompt

【任务】为「L6-02·大主题V10 SMT 求解引擎」产出一份 report-format v3（广度优先的"教辅"）报告。先读 study/cs-kb/report-format.md、study/cs-kb/brief.md 再动笔。落盘目标：study/cs-kb/findings/026-program-analysis-verification.md（同 slug；拆分用 -a/-b…）。本大主题属"验证半场"，PPA 不覆盖，须用专门一手锚点（de Moura & Bjørner Z3 TACAS'08 + z3 官方文档）；是贯穿 V7/V9 的共享后端，本机可跑。

【粒度判定（先做）】先判「1 份 or 拆 N 份 + 理由」（小主题数/是否跨机制/篇幅），结论写报告最前一行。本大主题小主题 5 个（V10.1–V10.5），默认 1 份。

【v3 格式硬性要求】
- 层级 `#`→`##`→`###`；`###` 下不写标签，直接正文空行分段。
- 每个 `###` 必含核心说明 + 充分辅助说明（初学者直觉/为什么/最小例子/易错点/关联，可多段，必需）。
- SMT 公式、DPLL(T) 判定流程、Nelson-Oppen 组合式、sat/unsat/model/unsat-core 结果一律独占行/块，不内联。
- 教辅口吻；广度优先查全浅讲；讲到初学者懂即止，不做专家纵深。

【小主题清单（=`##` 章节，按此序）与应覆盖内容项】
细目见 study/cs-kb/round3b-subtopics/g08-languages-compilers.md 中 L6-02 大主题 V10（V10.1–V10.5）：
- V10.1 SAT 与 DPLL/CDCL：命题可满足性内核。
- V10.2 DPLL(T) 框架：理论求解器与布尔骨架协作。
- V10.3 常用理论：线性算术/位向量/数组/未解释函数。
- V10.4 理论组合：Nelson-Oppen 组合。
- V10.5 sat/unsat/model/unsat-core：结果解读与用法。

【验证纪律（强制）】
- 多来源比对强制：每项 ≥2 独立源，优先一手 de Moura & Bjørner《Z3: An Efficient SMT Solver》(TACAS'08) + Z3 官方文档/SMT-LIB 标准；因验证半场无 PPA 兜底，第二独立锚点为硬要求；来源打架两边都记、点明分歧。
- 工具实证可选（本大主题本机可跑，鼓励做）：z3-solver 5.0.0.0 各理论 solver 真跑、取 model 与 unsat-core，贴真实输出 + 可复现命令 + 环境串；z3 需 pip 装（`python3 -m pip install z3-solver`，注意包不随容器持久化）；实证是加固，不替代多来源比对。
- 不编造：具体 API/输出/版本行为来自真跑或比对通过来源，或标「待核」；标时效；每个 `##` 章节末集中列来源与时效（含冲突项定位）。

【权威锚点清单（取自 round3a）】
- 一手：de Moura & Bjørner《Z3: An Efficient SMT Solver》(TACAS'08)；Z3 官方文档/仓库。https://github.com/Z3Prover/z3 （核实 2026-07-25）
- 第二锚点：SMT-LIB 标准文档（理论定义与结果语义交叉核对）；本机 z3-solver 5.0.0.0 实测。
```

```text
[P2] L6-02·大主题V11 交互式定理证明 — 报告prompt

【任务】为「L6-02·大主题V11 交互式定理证明」产出一份 report-format v3（广度优先的"教辅"）报告。先读 study/cs-kb/report-format.md、study/cs-kb/brief.md 再动笔。落盘目标：study/cs-kb/findings/026-program-analysis-verification.md（同 slug；拆分用 -a/-b…）。本大主题属"验证半场"，PPA 不覆盖，须用专门一手锚点（Software Foundations / Coq 一手）；接 L5-06 T10 依赖类型、回接 L5-14 R12。

【粒度判定（先做）】先判「1 份 or 拆 N 份 + 理由」（小主题数/是否跨机制/篇幅），结论写报告最前一行。本大主题小主题 4 个（V11.1–V11.4，为本课偏少者），默认 1 份。

【v3 格式硬性要求】
- 层级 `#`→`##`→`###`；`###` 下不写标签，直接正文空行分段。
- 每个 `###` 必含核心说明 + 充分辅助说明（初学者直觉/为什么/最小例子/易错点/关联，可多段，必需）。
- Curry-Howard 对应（命题即类型/证明即程序）、归纳类型定义、tactic 证明骨架一律独占行/块，不内联。
- 教辅口吻；广度优先查全浅讲；讲到初学者懂即止，不做专家纵深。

【小主题清单（=`##` 章节，按此序）与应覆盖内容项】
细目见 study/cs-kb/round3b-subtopics/g08-languages-compilers.md 中 L6-02 大主题 V11（V11.1–V11.4）：
- V11.1 证明助手与可信基(TCB)：Coq/Isabelle 定位、内核可信。
- V11.2 依赖类型即命题：Curry-Howard 深化（接 L5-06 T10，点名不展开）。
- V11.3 归纳类型与归纳证明：归纳定义、tactic 证明。
- V11.4 机器化元理论/可靠性：机械化类型安全证明（接 L5-14 R12，点名不展开）。

【验证纪律（强制）】
- 多来源比对强制：每项 ≥2 独立源，优先一手 Software Foundations（Coq 一手）+ Coq 官方文档到章/节；因验证半场无 PPA 兜底，第二独立锚点为硬要求；来源打架两边都记、点明分歧。
- 工具实证可选：Coq 本机未预装（round3b 注③）——降级为概念 + 只读 SF 源码（本机可用性待核）；不得以"未装"为由略去多来源比对，工具缺席须显式标「未验证」。
- 不编造；标时效；每个 `##` 章节末集中列来源与时效（含冲突项定位）。

【权威锚点清单（取自 round3a）】
- 一手：Software Foundations（Pierce et al.，Coq 一手）。https://softwarefoundations.cis.upenn.edu/ （核实 2026-07-25）
- 第二锚点：Coq 官方参考手册（或 Isabelle 文档）交叉核对证明助手机制与 TCB 说明；工具本机可用性待核。
```

共 11 条 prompt
