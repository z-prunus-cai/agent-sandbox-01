# Round3c · L3-05 计算理论/形式语言与自动机 · 报告 prompt（P1）

```text
[P1] L3-05·大主题01 数学预备与证明方法 — 报告prompt

【任务】为「L3-05·大主题01 数学预备与证明方法」产出一份 report-format v3（广度优先的教辅式）报告。先读 study/cs-kb/report-format.md（v3 模板）与 study/cs-kb/brief.md，再动笔。本大主题锚定 Sipser 3rd ed.(2012) Ch0；与 L1-02 离散数学强重叠（边界 B5），本课「只用不证」——把这些基础工具讲清够用即可，不做离散数学纵深。

【粒度判定（先做）】动笔前先判「1 份 or 拆 N 份 + 理由」并在报告抬头写明。本大主题仅 4 个小主题、篇幅短，默认 1 份；若判拆分，产物记为 012-theory-of-computation-a/-b。

【v3 格式硬性】
- 标题层级固定：# 大主题（报告标题） → ## 小主题（章节） → ### 内容项（每项一节）。
- ### 内容项下【不写「核心概念：」「辅助说明：」等标签】，直接正文分段：先核心说明（是什么/定义/结论，正确），再充分辅助说明（面向初学者：直觉/为什么/最小例子/易错点/前后关联，写到初学者能懂为止，可多段；辅助说明是必需项，不是点缀）。
- 任何形式定义 / 转移函数 / 文法规则 / 公式【独占一行】（块级），不内联埋进句子。
- 教辅口吻；广度优先、每项讲到初学者懂但不做专家级纵深（不写长篇机制深挖/完整推导/设计权衡长论）。

【小主题清单（=## 章节）与应覆盖内容项】（可另读 study/cs-kb/round3b-subtopics/g03-algorithms.md 中 L3-05「01 数学预备与证明方法」表核对小主题号 1.1–1.4）
- ## 1.1 集合、序列、元组、函数、关系（§0.2）：基本集合运算、序列与元组、函数（定义域/值域/一对一/映上）、关系与等价关系的性质。
- ## 1.2 图、字符串与语言、布尔逻辑（§0.2）：图的术语；字母表 Σ、字符串、Σ*、语言的定义；布尔连接词与德摩根律。
- ## 1.3 定义、定理与证明（§0.3）：数学命题的结构、定理/引理/推论、证明在本学科中的作用。
- ## 1.4 证明类型（§0.4）：构造性证明、反证法、数学归纳法（含最小反例）。

【多来源比对（强制）】每个内容项须用 ≥2 个独立来源交叉核对，优先一手（Sipser 3e Ch0 定位到 §/定义号；交叉 Hopcroft–Motwani–Ullman《Automata Theory, Languages, and Computation》相应章）。来源打架时两边都记、点明分歧、不和稀泥。Python 验证可选（如 sympy 集合运算/布尔真值表/德摩根、归纳命题小 n 数值核对），仅在便宜且能加固时做，不能替代多来源比对。不编造数值/定义，无法核实标「待核」并标核实日期。

【章节末来源与时效】每个 ## 小主题末尾集中列本章用到的来源（Sipser §号 + 交叉源 + 核实日期），不逐项脚注；冲突项两边定位；前沿/易变项标注。抬头带可 grep 基线串：基线 Py3.11.15/np2.4.6/gcc13.3 @2026-07-25。

【权威锚点清单】
- 一手：Sipser《Introduction to the Theory of Computation》3rd ed.(2012, Cengage)，Ch0；作者页 https://math.mit.edu/~sipser/book.html （3rd/2012 为当前最新版，核实 2026-07-25）。
- 交叉一手：Hopcroft–Motwani–Ullman《Introduction to Automata Theory, Languages, and Computation》。
- 官方大纲交叉：MIT 6.045J Automata, Computability & Complexity syllabus https://ocw.mit.edu/courses/6-045j-automata-computability-and-complexity-spring-2011/pages/syllabus/

【落盘】自己 Write 到 study/cs-kb/findings/012-theory-of-computation.md（若拆分则 012-...-a/-b）。报告内不得残留工具标签 </…> 或不可见控制字符；实证代码只写 scratchpad，不入库。
```

```text
[P1] L3-05·大主题02 有限自动机与正则语言 — 报告prompt

【任务】为「L3-05·大主题02 有限自动机与正则语言」产出一份 report-format v3（广度优先的教辅式）报告。先读 study/cs-kb/report-format.md（v3 模板）与 study/cs-kb/brief.md，再动笔。本大主题锚定 Sipser 3rd ed.(2012) Ch1（§1.1–1.4）；下游 L4-04 编译词法分析消费本章（边界 B6），本课讲「语言类与识别机」，不落地词法工具。

【粒度判定（先做）】动笔前先判「1 份 or 拆 N 份 + 理由」并写入报告抬头。本大主题 4 个小主题、含 DFA/NFA/正则式/泵引理多机制，默认 1 份即可；若判过长可拆 012-theory-of-computation-a/-b。

【v3 格式硬性】
- 层级固定：# → ## → ###；### 下不写标签，直接核心说明 + 充分辅助说明（面向初学者、必需且充分：直觉/为什么/最小例子/易错点/关联）。
- DFA/NFA 的形式定义（五元组）、转移函数 δ、正则表达式与运算规则【独占一行】（块级），不内联。
- 教辅口吻；广度优先、讲到初学者懂但不做专家级纵深。

【小主题清单（=## 章节）与应覆盖内容项】（可另读 round3b-subtopics/g03-algorithms.md 中 L3-05「02 有限自动机与正则语言」表核对 2.1–2.4）
- ## 2.1 有限自动机（§1.1）：DFA 的五元组形式定义、转移函数、接受/计算、正则语言定义、正则运算（并/连接/星）的闭包。
- ## 2.2 非确定性（§1.2）：NFA、ε 转移、NFA 与 DFA 等价、子集构造（NFA→DFA）。
- ## 2.3 正则表达式（§1.3）：正则表达式语法、与有限自动机等价（Kleene 定理）、GNFA→正则式的状态消去。
- ## 2.4 非正则语言（§1.4）：正则语言泵引理、用泵引理判定非正则。
- 补充点名：Myhill–Nerode 定理与 DFA 最小化（R2 已标为重点）——作为本章延伸内容项覆盖到初学者能懂。

【多来源比对（强制）】每项 ≥2 独立来源，优先一手（Sipser 3e §1.1–1.4 定位到定义/定理号，如泵引理 Theorem 1.70；交叉 Hopcroft–Motwani–Ullman 相应章）。冲突两边都记、点明分歧。Python 自动机模拟验证可选（DFA 模拟器判串、NFA 确定化后与原 NFA 对随机串接受一致、与 re 模块交叉、构造反例串演示泵引理否证），仅在便宜时做，不替代多来源比对。不编造，未核实标「待核」+ 核实日期。

【章节末来源与时效】每个 ## 末集中列来源（Sipser §/定理号 + 交叉源 + 核实日期），冲突项两边定位。抬头带基线串：基线 Py3.11.15/np2.4.6/gcc13.3 @2026-07-25。

【权威锚点清单】
- 一手：Sipser 3rd ed.(2012) Ch1 §1.1–1.4；作者页 https://math.mit.edu/~sipser/book.html （核实 2026-07-25）。
- 交叉一手：Hopcroft–Motwani–Ullman《Automata Theory, Languages, and Computation》。
- 官方大纲交叉：MIT 6.045J syllabus https://ocw.mit.edu/courses/6-045j-automata-computability-and-complexity-spring-2011/pages/syllabus/

【落盘】自己 Write 到 study/cs-kb/findings/012-theory-of-computation.md（拆分则 -a/-b）。不得残留工具标签/控制字符；实证代码只写 scratchpad。
```

```text
[P1] L3-05·大主题03 上下文无关语言 — 报告prompt

【任务】为「L3-05·大主题03 上下文无关语言」产出一份 report-format v3（广度优先的教辅式）报告。先读 study/cs-kb/report-format.md 与 study/cs-kb/brief.md，再动笔。本大主题锚定 Sipser 3rd ed.(2012) Ch2（§2.1–2.4，3e 新增 DCFL 节）；下游 L4-04 编译语法分析（LL/LR）消费（边界 B6），本课讲「语言类与识别机」；交/补不闭合为反直觉考点，务必讲清。

【粒度判定（先做）】先判「1 份 or 拆 N 份 + 理由」写入抬头。5 个小主题、含 CFG/PDA/等价/泵引理/DCFL，默认 1 份；过长可拆 -a/-b。

【v3 格式硬性】
- 层级固定：# → ## → ###；### 下不写标签，核心说明 + 充分辅助说明（初学者向、必需）。
- 文法规则（产生式）、Chomsky 范式规则、PDA 六元组形式定义、栈转移函数【独占一行】（块级），不内联。
- 教辅口吻；广度优先、讲到初学者懂但不做专家级纵深。

【小主题清单（=## 章节）与应覆盖内容项】（可另读 round3b-subtopics/g03-algorithms.md 中 L3-05「03 上下文无关语言」表核对 3.1–3.5）
- ## 3.1 上下文无关文法（§2.1）：CFG 形式定义、派生与派生树、歧义、Chomsky 范式（CNF）。
- ## 3.2 下推自动机（§2.2）：PDA 形式定义、栈计算、示例（如平衡括号/回文）。
- ## 3.3 CFG 与 PDA 等价（§2.2）：CFG→PDA 与 PDA→CFG 的双向构造等价。
- ## 3.4 非上下文无关语言（§2.3）：CFL 泵引理、用其判非 CFL、CFL 对交/补不闭合（反直觉）。
- ## 3.5 确定型上下文无关语言（§2.4，3e 新增节）：DPDA、DCFL、与 LR(k) 解析的关系。

【多来源比对（强制）】每项 ≥2 独立来源，优先一手（Sipser 3e §2.1–2.4 定位到定义/定理号，如 CFL 泵引理 Theorem 2.34；交叉 Hopcroft–Motwani–Ullman）。§2.4 为 3e 新增，需核对确属 3rd ed. 正文；冲突两边都记。Python 验证可选（CNF 转换后语言不变对拍、括号语言 PDA 栈模拟、CYK 判成员与 PDA 模拟对拍、构造反例串演示 CFL 泵引理），不替代多来源比对。不编造，未核实标「待核」+ 核实日期。

【章节末来源与时效】每个 ## 末集中列来源（Sipser §/定理号 + 交叉源 + 核实日期）；3e 新增节标「3e 新增」；冲突项两边定位。抬头带基线串：基线 Py3.11.15/np2.4.6/gcc13.3 @2026-07-25。

【权威锚点清单】
- 一手：Sipser 3rd ed.(2012) Ch2 §2.1–2.4；作者页 https://math.mit.edu/~sipser/book.html （核实 2026-07-25）。
- 交叉一手：Hopcroft–Motwani–Ullman《Automata Theory, Languages, and Computation》。
- 官方大纲交叉：MIT 6.045J syllabus https://ocw.mit.edu/courses/6-045j-automata-computability-and-complexity-spring-2011/pages/syllabus/

【落盘】自己 Write 到 study/cs-kb/findings/012-theory-of-computation.md（拆分则 -a/-b）。不得残留工具标签/控制字符；实证代码只写 scratchpad。
```

```text
[P1] L3-05·大主题04 图灵机与丘奇–图灵论题 — 报告prompt

【任务】为「L3-05·大主题04 图灵机与丘奇–图灵论题」产出一份 report-format v3（广度优先的教辅式）报告。先读 study/cs-kb/report-format.md 与 study/cs-kb/brief.md，再动笔。本大主题锚定 Sipser 3rd ed.(2012) Ch3（§3.1–3.3）；是全库「计算模型」的公共语言，下游 L5-06 PL、L6-02 验证消费（边界 B7）。

【粒度判定（先做）】先判「1 份 or 拆 N 份 + 理由」写入抬头。仅 3 个小主题、篇幅中等，默认 1 份。

【v3 格式硬性】
- 层级固定：# → ## → ###；### 下不写标签，核心说明 + 充分辅助说明（初学者向、必需）。
- TM 七元组形式定义、格局（configuration）记法、转移函数 δ【独占一行】（块级），不内联。
- 教辅口吻；广度优先、讲到初学者懂但不做专家级纵深。

【小主题清单（=## 章节）与应覆盖内容项】（可另读 round3b-subtopics/g03-algorithms.md 中 L3-05「04 图灵机与丘奇–图灵论题」表核对 4.1–4.3）
- ## 4.1 图灵机（§3.1）：TM 形式定义、格局、接受/拒绝/循环、图灵可判定与图灵可识别（可枚举）语言。
- ## 4.2 图灵机变体（§3.2）：多带 TM、非确定型 TM、枚举器，三者与单带 TM 的等价（鲁棒性）。
- ## 4.3 算法的定义（§3.3）：丘奇–图灵论题、Hilbert 第十问题、算法描述的三个层次（形式/实现/高层）。

【多来源比对（强制）】每项 ≥2 独立来源，优先一手（Sipser 3e §3.1–3.3 定位到定义/定理号；交叉 Hopcroft–Motwani–Ullman；丘奇–图灵论题与 Hilbert 第十问题可交叉 Turing 1936 原始论文与权威史料）。冲突两边都记。Python 验证可选（单带 TM 模拟器跑一元加法、多带 TM 与单带模拟结果对拍）；停机/不可计算对象本章不涉，不作实证结论。不编造，未核实标「待核」+ 核实日期。

【章节末来源与时效】每个 ## 末集中列来源（Sipser §/定理号 + 交叉源 + 核实日期）；冲突项两边定位。抬头带基线串：基线 Py3.11.15/np2.4.6/gcc13.3 @2026-07-25。

【权威锚点清单】
- 一手：Sipser 3rd ed.(2012) Ch3 §3.1–3.3；作者页 https://math.mit.edu/~sipser/book.html （核实 2026-07-25）。
- 交叉一手：Hopcroft–Motwani–Ullman；Turing 1936「On Computable Numbers」。
- 官方大纲交叉：MIT 6.045J syllabus https://ocw.mit.edu/courses/6-045j-automata-computability-and-complexity-spring-2011/pages/syllabus/

【落盘】自己 Write 到 study/cs-kb/findings/012-theory-of-computation.md（拆分则 -a/-b）。不得残留工具标签/控制字符；实证代码只写 scratchpad。
```

```text
[P1] L3-05·大主题05 可判定性 — 报告prompt

【任务】为「L3-05·大主题05 可判定性」产出一份 report-format v3（广度优先的教辅式）报告。先读 study/cs-kb/report-format.md 与 study/cs-kb/brief.md，再动笔。本大主题锚定 Sipser 3rd ed.(2012) Ch4（§4.1–4.2）。停机问题/不可判定对象【不可实证】，须硬标「不可计算，无实证」，只做符号推演。

【粒度判定（先做）】先判「1 份 or 拆 N 份 + 理由」写入抬头。3 个小主题、篇幅中等，默认 1 份。

【v3 格式硬性】
- 层级固定：# → ## → ###；### 下不写标签，核心说明 + 充分辅助说明（初学者向、必需）。
- 判定问题的语言编码记法（如 A_DFA = { ⟨B, w⟩ | … }）、对角化论证的关键式子【独占一行】（块级），不内联。
- 教辅口吻；广度优先、讲到初学者懂但不做专家级纵深。

【小主题清单（=## 章节）与应覆盖内容项】（可另读 round3b-subtopics/g03-algorithms.md 中 L3-05「05 可判定性」表核对 5.1–5.3）
- ## 5.1 关于正则语言的可判定问题（§4.1）：A_DFA、E_DFA、EQ_DFA 等的判定过程与可判定性。
- ## 5.2 关于上下文无关语言的可判定问题（§4.1）：A_CFG、E_CFG，以及「每个 CFL 都可判定」。
- ## 5.3 不可判定性与对角化（§4.2）：集合可数性与对角化、A_TM 不可判定、存在图灵不可识别语言。

【多来源比对（强制）】每项 ≥2 独立来源，优先一手（Sipser 3e §4.1–4.2 定位到定理号，如 A_TM 不可判定 Theorem 4.11、对角化 Theorem 4.22；交叉 Hopcroft–Motwani–Ullman）。冲突两边都记。Python 验证仅限可判定项（A_DFA 判定器模拟 DFA、CYK 实现 A_CFG 判定）；A_TM/不可判定/图灵不可识别项【硬标不可实证】。不编造，未核实标「待核」+ 核实日期。

【章节末来源与时效】每个 ## 末集中列来源（Sipser §/定理号 + 交叉源 + 核实日期）；不可判定项标「不可实证」；冲突项两边定位。抬头带基线串：基线 Py3.11.15/np2.4.6/gcc13.3 @2026-07-25。

【权威锚点清单】
- 一手：Sipser 3rd ed.(2012) Ch4 §4.1–4.2；作者页 https://math.mit.edu/~sipser/book.html （核实 2026-07-25）。
- 交叉一手：Hopcroft–Motwani–Ullman。
- 官方大纲交叉：MIT 6.045J syllabus https://ocw.mit.edu/courses/6-045j-automata-computability-and-complexity-spring-2011/pages/syllabus/

【落盘】自己 Write 到 study/cs-kb/findings/012-theory-of-computation.md（拆分则 -a/-b）。不得残留工具标签/控制字符；实证代码只写 scratchpad。
```

```text
[P1] L3-05·大主题06 归约 — 报告prompt

【任务】为「L3-05·大主题06 归约」产出一份 report-format v3（广度优先的教辅式）报告。先读 study/cs-kb/report-format.md 与 study/cs-kb/brief.md，再动笔。本大主题锚定 Sipser 3rd ed.(2012) Ch5（§5.1–5.3）。映射归约 ≤ₘ 是复杂度归约（大主题08）的雏形，讲清其与后续的关联；涉不可判定的归约对象硬标不可实证。

【粒度判定（先做）】先判「1 份 or 拆 N 份 + 理由」写入抬头。3 个小主题、篇幅中等，默认 1 份。

【v3 格式硬性】
- 层级固定：# → ## → ###；### 下不写标签，核心说明 + 充分辅助说明（初学者向、必需）。
- 归约关系 ≤ₘ 定义、可计算函数定义、归约构造的关键映射【独占一行】（块级），不内联。
- 教辅口吻；广度优先、讲到初学者懂但不做专家级纵深。

【小主题清单（=## 章节）与应覆盖内容项】（可另读 round3b-subtopics/g03-algorithms.md 中 L3-05「06 归约」表核对 6.1–6.3）
- ## 6.1 语言理论中的不可判定问题（§5.1）：HALT_TM、E_TM、EQ_TM 的不可判定性（经由归约证明）。
- ## 6.2 计算历史法（§5.2）：线性有界自动机（LBA）、Post 对应问题（PCP）及其不可判定性。
- ## 6.3 映射归约（§5.3）：映射归约 ≤ₘ 的定义、可计算函数、Rice 定理（习题族）。

【多来源比对（强制）】每项 ≥2 独立来源，优先一手（Sipser 3e §5.1–5.3 定位到定理号，如 HALT_TM Theorem 5.1、PCP Theorem 5.15、映射归约 Definition 5.20；交叉 Hopcroft–Motwani–Ullman）。冲突两边都记。Python 验证有限：涉不可判定项【硬标不可实证】；PCP 可做有限深度搜索演示（非判定）、归约函数可在有限实例上演示其可计算性。不替代多来源比对；不编造，未核实标「待核」+ 核实日期。

【章节末来源与时效】每个 ## 末集中列来源（Sipser §/定理号 + 交叉源 + 核实日期）；不可判定项标「不可实证」；冲突项两边定位。抬头带基线串：基线 Py3.11.15/np2.4.6/gcc13.3 @2026-07-25。

【权威锚点清单】
- 一手：Sipser 3rd ed.(2012) Ch5 §5.1–5.3；作者页 https://math.mit.edu/~sipser/book.html （核实 2026-07-25）。
- 交叉一手：Hopcroft–Motwani–Ullman。
- 官方大纲交叉：MIT 6.045J syllabus https://ocw.mit.edu/courses/6-045j-automata-computability-and-complexity-spring-2011/pages/syllabus/

【落盘】自己 Write 到 study/cs-kb/findings/012-theory-of-computation.md（拆分则 -a/-b）。不得残留工具标签/控制字符；实证代码只写 scratchpad。
```

```text
[P1] L3-05·大主题07 可计算性高级专题（可选） — 报告prompt

【任务】为「L3-05·大主题07 可计算性高级专题（可选）」产出一份 report-format v3（广度优先的教辅式）报告。先读 study/cs-kb/report-format.md 与 study/cs-kb/brief.md，再动笔。本大主题锚定 Sipser 3rd ed.(2012) Ch6（§6.1–6.4），为可选高级专题章，按课时/方向取舍——报告抬头标「可选延伸章」。Kolmogorov 复杂度与 L6-01 随机性、L5-04 密码有远端联系（边界 B7），仅点名不展开下游。

【粒度判定（先做）】先判「1 份 or 拆 N 份 + 理由」写入抬头。4 个小主题、各自独立但均浅讲，默认 1 份。

【v3 格式硬性】
- 层级固定：# → ## → ###；### 下不写标签，核心说明 + 充分辅助说明（初学者向、必需）。
- 递归定理陈述、图灵归约 ≤_T 定义、Kolmogorov 复杂度定义式【独占一行】（块级），不内联。
- 教辅口吻；广度优先、讲到初学者懂但不做专家级纵深。

【小主题清单（=## 章节）与应覆盖内容项】（可另读 round3b-subtopics/g03-algorithms.md 中 L3-05「07 可计算性高级专题」表核对 7.1–7.4）
- ## 7.1 递归定理（§6.1）：自指、可自我复制程序（quine 直觉）、递归定理的应用。
- ## 7.2 逻辑理论的可判定性（§6.2）：Th(N,+) 可判定 vs Th(N,+,×) 不可判定。
- ## 7.3 图灵归约（§6.3）：预言机图灵机、≤_T、图灵度。
- ## 7.4 信息定义（§6.4）：Kolmogorov 复杂度、不可压缩串。

【多来源比对（强制）】每项 ≥2 独立来源，优先一手（Sipser 3e §6.1–6.4 定位到定理号，如递归定理 Theorem 6.3；交叉 Hopcroft–Motwani–Ullman 或经典可计算性教材如 Sipser 之外的 Kozen/Rogers 相应处）。冲突两边都记。Python 验证可选且仅作直觉演示（Python quine 演示递归定理直觉、压缩率统计近似说明不可压缩——均非证明）；不替代多来源比对。不编造，未核实标「待核」+ 核实日期。

【章节末来源与时效】每个 ## 末集中列来源（Sipser §/定理号 + 交叉源 + 核实日期）；演示项标「直觉演示，非证明」；冲突项两边定位。抬头带基线串：基线 Py3.11.15/np2.4.6/gcc13.3 @2026-07-25。

【权威锚点清单】
- 一手：Sipser 3rd ed.(2012) Ch6 §6.1–6.4；作者页 https://math.mit.edu/~sipser/book.html （核实 2026-07-25）。
- 交叉一手：Hopcroft–Motwani–Ullman 或经典可计算性教材（Kozen/Rogers）。
- 官方大纲交叉：MIT 6.045J syllabus https://ocw.mit.edu/courses/6-045j-automata-computability-and-complexity-spring-2011/pages/syllabus/

【落盘】自己 Write 到 study/cs-kb/findings/012-theory-of-computation.md（拆分则 -a/-b）。不得残留工具标签/控制字符；实证代码只写 scratchpad。
```

```text
[P1] L3-05·大主题08 时间复杂度：P/NP/NP 完全 — 报告prompt

【任务】为「L3-05·大主题08 时间复杂度：P/NP/NP 完全」产出一份 report-format v3（广度优先的教辅式）报告。先读 study/cs-kb/report-format.md 与 study/cs-kb/brief.md，再动笔。本大主题锚定 Sipser 3rd ed.(2012) Ch7（§7.1–7.5）。与 L3-01 #13 重叠（边界 B1，组内最重要）：本课给「严格版」（复杂度类定义 + Cook–Levin 全证），L3-01 只讲「会用归约判难」——本报告须承担严格构造。P vs NP 现状：@2026-07-25 仍未解，引用时维持「未解」结论并标时效。

【粒度判定（先做）】先判「1 份 or 拆 N 份 + 理由」写入抬头。6 个小主题、含 Cook–Levin 全证与归约链、篇幅偏大，倾向评估是否拆为 012-...-a（复杂度类 §7.1–7.3+P vs NP）/ -b（NP 完全性 §7.4–7.5）；给明理由。

【v3 格式硬性】
- 层级固定：# → ## → ###；### 下不写标签，核心说明 + 充分辅助说明（初学者向、必需）。
- 类 P/NP 的定义、验证器定义、≤ₚ 多项式归约定义、Cook–Levin 构造中的关键式子【独占一行】（块级），不内联。
- 教辅口吻；广度优先、讲到初学者懂但不做专家级纵深（Cook–Levin 全证要「讲到初学者看懂构造脉络」，不写超长逐格推导）。

【小主题清单（=## 章节）与应覆盖内容项】（可另读 round3b-subtopics/g03-algorithms.md 中 L3-05「08 时间复杂度」表核对 8.1–8.6）
- ## 8.1 复杂度度量（§7.1）：大 O、模型对运行时间的影响、多项式等价。
- ## 8.2 类 P（§7.2）：多项式可判定、PATH/RELPRIME 等实例。
- ## 8.3 类 NP（§7.3）：验证器、非确定 TM 刻画、HAMPATH/CLIQUE。
- ## 8.4 P vs NP 问题（§7.3）：未解现状（@2026-07-25 仍未解），两种可能的含义。
- ## 8.5 NP 完全性与 Cook–Levin（§7.4）：NP 完全性定义、SAT 的 NP 完全性全证（电路/公式编码脉络）。
- ## 8.6 更多 NP 完全问题（§7.5）：3SAT/CLIQUE/VC/HAMPATH/SUBSET-SUM 的归约链。

【多来源比对（强制）】每项 ≥2 独立来源，优先一手（Sipser 3e §7.1–7.5 定位到定理号，如 Cook–Levin Theorem 7.37；交叉 CLRS 4e Ch34、Cook 1971/Karp 1972 原始论文、Hopcroft–Motwani–Ullman）。P vs NP 未解结论交叉时效源（如 blog.computationalcomplexity.org 2026-06 复述「AI 生成的 P≠NP 证明尚不在眼前」）。冲突两边都记。Python 验证可选（电路→CNF 编码小实例对拍、证书验证器、归约保可满足性小规模对拍、PATH/RELPRIME 多项式实现）；不替代多来源比对。不编造，未核实标「待核」+ 核实日期。

【章节末来源与时效】每个 ## 末集中列来源（Sipser §/定理号 + 交叉源 + 核实日期）；P vs NP 项硬标「@2026-07-25 仍未解，演进随时变」；冲突项两边定位。抬头带基线串：基线 Py3.11.15/np2.4.6/gcc13.3 @2026-07-25。

【权威锚点清单】
- 一手：Sipser 3rd ed.(2012) Ch7 §7.1–7.5；作者页 https://math.mit.edu/~sipser/book.html （核实 2026-07-25）。
- 交叉一手：CLRS 4th ed.(2022) Ch34；Cook 1971；Karp 1972；Hopcroft–Motwani–Ullman。
- 官方大纲交叉：MIT 6.045J syllabus https://ocw.mit.edu/courses/6-045j-automata-computability-and-complexity-spring-2011/pages/syllabus/
- 时效交叉：blog.computationalcomplexity.org（P vs NP 现状复核）。

【落盘】自己 Write 到 study/cs-kb/findings/012-theory-of-computation.md（若判拆则 012-...-a/-b）。不得残留工具标签/控制字符；实证代码只写 scratchpad。
```

```text
[P1] L3-05·大主题09 空间复杂度 — 报告prompt

【任务】为「L3-05·大主题09 空间复杂度」产出一份 report-format v3（广度优先的教辅式）报告。先读 study/cs-kb/report-format.md 与 study/cs-kb/brief.md，再动笔。本大主题锚定 Sipser 3rd ed.(2012) Ch8（§8.1–8.6）。PSPACE 与广义博弈（TQBF）联系紧密，讲清直觉。

【粒度判定（先做）】先判「1 份 or 拆 N 份 + 理由」写入抬头。6 个小主题、机制多（Savitch/PSPACE/L/NL/NL=coNL），默认 1 份，若过长评估拆分并给理由。

【v3 格式硬性】
- 层级固定：# → ## → ###；### 下不写标签，核心说明 + 充分辅助说明（初学者向、必需）。
- 空间复杂度类定义（PSPACE/L/NL）、Savitch 定理陈述、NL=coNL 关键式【独占一行】（块级），不内联。
- 教辅口吻；广度优先、讲到初学者懂但不做专家级纵深。

【小主题清单（=## 章节）与应覆盖内容项】（可另读 round3b-subtopics/g03-algorithms.md 中 L3-05「09 空间复杂度」表核对 9.1–9.6）
- ## 9.1 空间度量与 Savitch 定理（§8.1）：空间复杂度度量、Savitch 定理 NSPACE(f)⊆DSPACE(f²)。
- ## 9.2 类 PSPACE（§8.2）：多项式空间判定、PSPACE 与 P/NP 的包含关系。
- ## 9.3 PSPACE 完全性（§8.3）：TQBF、广义博弈的 PSPACE 完全。
- ## 9.4 类 L 与 NL（§8.4）：对数空间判定。
- ## 9.5 NL 完全性（§8.5）：PATH、对数空间归约。
- ## 9.6 NL = coNL（§8.6）：Immerman–Szelepcsényi 定理。

【多来源比对（强制）】每项 ≥2 独立来源，优先一手（Sipser 3e §8.1–8.6 定位到定理号，如 Savitch Theorem 8.5、NL=coNL Theorem 8.27；交叉 Hopcroft–Motwani–Ullman 或 Arora–Barak《Computational Complexity》相应章）。冲突两边都记。Python 验证可选（递归可达性 O(log²n) 空间模拟核对 Savitch、TQBF 递归求值器判小公式、PATH 判定与 BFS 对拍）；不替代多来源比对。不编造，未核实标「待核」+ 核实日期。

【章节末来源与时效】每个 ## 末集中列来源（Sipser §/定理号 + 交叉源 + 核实日期）；冲突项两边定位。抬头带基线串：基线 Py3.11.15/np2.4.6/gcc13.3 @2026-07-25。

【权威锚点清单】
- 一手：Sipser 3rd ed.(2012) Ch8 §8.1–8.6；作者页 https://math.mit.edu/~sipser/book.html （核实 2026-07-25）。
- 交叉一手：Hopcroft–Motwani–Ullman；Arora–Barak《Computational Complexity》。
- 官方大纲交叉：MIT 6.045J syllabus https://ocw.mit.edu/courses/6-045j-automata-computability-and-complexity-spring-2011/pages/syllabus/

【落盘】自己 Write 到 study/cs-kb/findings/012-theory-of-computation.md（拆分则 -a/-b）。不得残留工具标签/控制字符；实证代码只写 scratchpad。
```

```text
[P1] L3-05·大主题10 难解性：层级定理与电路复杂度 — 报告prompt

【任务】为「L3-05·大主题10 难解性：层级定理与电路复杂度」产出一份 report-format v3（广度优先的教辅式）报告。先读 study/cs-kb/report-format.md 与 study/cs-kb/brief.md，再动笔。本大主题锚定 Sipser 3rd ed.(2012) Ch9（§9.1–9.3）。层级定理是「确有更难问题」的证明；相对化讲清它为何限制了 P vs NP 的某些证明手段。

【粒度判定（先做）】先判「1 份 or 拆 N 份 + 理由」写入抬头。4 个小主题、篇幅中等，默认 1 份。

【v3 格式硬性】
- 层级固定：# → ## → ###；### 下不写标签，核心说明 + 充分辅助说明（初学者向、必需）。
- 层级定理陈述、电路/电路族形式定义、相对化预言机记法【独占一行】（块级），不内联。
- 教辅口吻；广度优先、讲到初学者懂但不做专家级纵深。

【小主题清单（=## 章节）与应覆盖内容项】（可另读 round3b-subtopics/g03-algorithms.md 中 L3-05「10 难解性」表核对 10.1–10.4）
- ## 10.1 空间层级定理（§9.1）：更多空间可判定更多语言。
- ## 10.2 时间层级定理（§9.1）：更多时间可判定更多语言、EXPSPACE 完全性。
- ## 10.3 相对化（§9.2）：预言机、相对化对 P vs NP 证明手段的局限。
- ## 10.4 电路复杂度（§9.3）：布尔电路、电路 SAT、电路族与语言。

【多来源比对（强制）】每项 ≥2 独立来源，优先一手（Sipser 3e §9.1–9.3 定位到定理号，如时间层级 Theorem 9.10、空间层级 Theorem 9.3；交叉 Arora–Barak《Computational Complexity》或 Hopcroft–Motwani–Ullman）。冲突两边都记。Python 验证可选（布尔电路 DAG 求值与真值表对拍）；层级定理/相对化为纯理论，不作实证结论。不替代多来源比对。不编造，未核实标「待核」+ 核实日期。

【章节末来源与时效】每个 ## 末集中列来源（Sipser §/定理号 + 交叉源 + 核实日期）；纯理论项标「无实证」；冲突项两边定位。抬头带基线串：基线 Py3.11.15/np2.4.6/gcc13.3 @2026-07-25。

【权威锚点清单】
- 一手：Sipser 3rd ed.(2012) Ch9 §9.1–9.3；作者页 https://math.mit.edu/~sipser/book.html （核实 2026-07-25）。
- 交叉一手：Arora–Barak《Computational Complexity》；Hopcroft–Motwani–Ullman。
- 官方大纲交叉：MIT 6.045J syllabus https://ocw.mit.edu/courses/6-045j-automata-computability-and-complexity-spring-2011/pages/syllabus/

【落盘】自己 Write 到 study/cs-kb/findings/012-theory-of-computation.md（拆分则 -a/-b）。不得残留工具标签/控制字符；实证代码只写 scratchpad。
```

```text
[P1] L3-05·大主题11 复杂度高级专题（可选） — 报告prompt

【任务】为「L3-05·大主题11 复杂度高级专题（可选）」产出一份 report-format v3（广度优先的教辅式）报告。先读 study/cs-kb/report-format.md 与 study/cs-kb/brief.md，再动笔。本大主题锚定 Sipser 3rd ed.(2012) Ch10（§10.1–10.6），为可选高级专题章——抬头标「可选延伸章」。与 L6-01（近似 #4 / 随机 #5）、L5-04（密码）重叠（边界 B7）：本课只给「理论定义」，工程/算法设计在下游，仅点名不展开。

【粒度判定（先做）】先判「1 份 or 拆 N 份 + 理由」写入抬头。6 个小主题、各自浅讲，默认 1 份。

【v3 格式硬性】
- 层级固定：# → ## → ###；### 下不写标签，核心说明 + 充分辅助说明（初学者向、必需）。
- BPP 定义、交替图灵机与多项式层级定义、IP=PSPACE 陈述、单向函数定义【独占一行】（块级），不内联。
- 教辅口吻；广度优先、讲到初学者懂但不做专家级纵深。

【小主题清单（=## 章节）与应覆盖内容项】（可另读 round3b-subtopics/g03-algorithms.md 中 L3-05「11 复杂度高级专题」表核对 11.1–11.6）
- ## 11.1 近似算法（§10.1）：MIN-VERTEX-COVER / MAX-CUT 的近似（理论定义视角）。
- ## 11.2 概率算法（§10.2）：类 BPP、素性测试、分支程序。
- ## 11.3 交替（§10.3）：交替图灵机、多项式层级。
- ## 11.4 交互式证明系统（§10.4）：IP、IP=PSPACE、图不同构协议。
- ## 11.5 并行计算（§10.5）：PRAM、类 NC、P 完全性。
- ## 11.6 密码学（§10.6）：私钥/单向函数/公钥（理论定义）。

【多来源比对（强制）】每项 ≥2 独立来源，优先一手（Sipser 3e §10.1–10.6 定位到定理号，如 IP=PSPACE Theorem 10.29；交叉 Arora–Barak《Computational Complexity》相应章）。冲突两边都记。Python 验证可选（近似比经验统计、Miller–Rabin 与确定素性对拍、图不同构交互协议模拟、RSA 小素数加解密回路）；不替代多来源比对。不编造，未核实标「待核」+ 核实日期。

【章节末来源与时效】每个 ## 末集中列来源（Sipser §/定理号 + 交叉源 + 核实日期）；下游应用项标「点名，不展开」；冲突项两边定位。抬头带基线串：基线 Py3.11.15/np2.4.6/gcc13.3 @2026-07-25。

【权威锚点清单】
- 一手：Sipser 3rd ed.(2012) Ch10 §10.1–10.6；作者页 https://math.mit.edu/~sipser/book.html （核实 2026-07-25）。
- 交叉一手：Arora–Barak《Computational Complexity》。
- 官方大纲交叉：MIT 6.045J syllabus https://ocw.mit.edu/courses/6-045j-automata-computability-and-complexity-spring-2011/pages/syllabus/

【落盘】自己 Write 到 study/cs-kb/findings/012-theory-of-computation.md（拆分则 -a/-b）。不得残留工具标签/控制字符；实证代码只写 scratchpad。
```

共 11 条 prompt
