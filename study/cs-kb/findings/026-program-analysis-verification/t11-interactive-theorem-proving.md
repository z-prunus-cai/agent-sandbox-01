# L6-02·大主题V11 交互式定理证明

> 基线 Py3.11.15/np2.4.6/gcc13.3 @2026-07-25 ｜ 核实日期：2026-07-30 ｜ 先修：本课 V9（Hoare 逻辑/演绎验证）、L5-06 T3（Safety = Progress + Preservation）、L5-06 T10（高阶系统与依赖类型一瞥，本报告 V11.2 点名接入不展开）｜ 一手锚点：Software Foundations（Pierce 等，Rocq/Coq 一手可执行教材）Vol.1 Logical Foundations v7.0（2026-01-09 发布，要求 Rocq/Coq 9.0.0+）、Vol.2 Programming Language Foundations，https://softwarefoundations.cis.upenn.edu/ ；The Rocq Prover 官方参考手册 Core language 章（V9.0.0 / V9.1.0），https://rocq-prover.org/ ｜ 交叉一手：Coquand & Huet《The Calculus of Constructions》(1988)；Paulson《From LCF to Isabelle/HOL》(arXiv:1907.02836)；de Moura 等《The Lean 4 Theorem Prover》系统描述 ｜ 工具实证：Coq/Rocq 本机**未预装**，本报告全部机制以一手比对为准，代码片段为示意/未实机验证（如实标注）｜ 成熟度：GA/⚙演进快·锚版本（Coq→Rocq 更名进行中；Lean 4 / mathlib 生态活跃演进）

交互式定理证明（interactive theorem proving，ITP）是这门课"验证半场"的顶点技术。前面的数据流分析、抽象解释、模型检验、SMT 都追求"全自动但只能证一类有限性质"；交互式定理证明反过来——它允许你证明**几乎任何**数学命题或程序性质，代价是需要人来引导证明的大方向，机器只负责逐步、机械地检查每一步是否严丝合缝。它的一句话心智模型是：**你和一台"绝不放过任何逻辑漏洞的助教"一起写证明——你出思路（用 tactic 指挥），它把你的每一步翻译成一个可被机械检查的证明对象，最后由一个极小的、可信的内核逐符号验收；只要内核通过，这个定理就被认为在数学上无懈可击。**

理解 ITP 的两个总钥匙，正是本报告的两条暗线。第一条是 **Curry-Howard 对应**：命题就是类型、证明就是这个类型的程序（项）——于是"证明一个定理"这件本来很玄的事，被还原成"写一个类型正确的程序"这件可被类型检查器机械核对的事（V11.2）。第二条是 **de Bruijn 判据**：把整个庞大复杂的系统（几十万行的 tactic、自动化、用户界面）都当成"不可信的建议者"，只让一个几千行、被反复审计的**内核**（kernel）去做最终裁决——你只需要信任这个内核，就等于信任了全部证明（V11.1）。抓住这两点，Coq/Rocq、Isabelle、Lean 这些具体系统的差异就都成了同一主题下的变奏。

> 时效性提醒（贯穿全报告）：经典的 **Coq 证明助手**自 2025 年起正式更名为 **The Rocq Prover**，更名随 Rocq 9.0.0（2025-03-12）完成；本报告统一写作"Coq/Rocq"并在涉及版本处硬标。此项及 Lean 4 / mathlib 生态属 ⚙演进快·锚版本，核实日期 2026-07-30，具体版本以官方发布为准、不凭记忆。

---

## V11.1 证明助手与可信基（TCB）

### V11.1.1 什么是证明助手（proof assistant）

证明助手，又称交互式定理证明器，是一类软件：用户在其中用一种形式语言写下定义与定理，再通过与系统交互（而非全自动）逐步构造出证明，系统负责机械地检查这个证明是否正确。它介于"纸笔证明"与"全自动定理证明"之间——比纸笔严格（不允许任何"显然""易证"的跳步），又比全自动灵活（能处理全自动工具证不了的深命题，因为难的创造性步骤由人给出）。

主流证明助手可按逻辑基础粗分两大阵营。一类基于**依赖类型论**（type theory）：代表是 Coq/Rocq 与 Lean，它们把证明当成类型正确的程序（下一小节展开）。另一类基于**高阶逻辑**（HOL）配合 LCF 式架构：代表是 Isabelle/HOL 与 HOL Light。两条路线殊途同归，都做到了"极小可信核 + 机械检查"，只是内核裁决的"对象"不同——类型论阵营裁决"一个项是否具有某类型"，HOL 阵营裁决"一个定理是否由推理规则合法导出"。

初学者最容易的误解是把证明助手当成"更聪明的计算器"或"能自动证数学题的 AI"。恰恰相反：它几乎不"猜"，它只"验"。你若不给思路，它证不出费马大定理；但你给出的每一步只要有半点不严谨，它一定拒收。它的价值不在"替你想"，而在"绝不替你放水"——这正是它能把"这个证明真的对吗"从人类互相信任降格为机器可核验的原因。

### V11.1.2 可信基（TCB）与 de Bruijn 判据

可信基（Trusted Computing Base，TCB）指的是：为了相信"系统输出的定理确实成立"，你**不得不信任的那部分代码与假设**的总和。证明助手设计的核心追求，就是把 TCB 压到尽可能小、尽可能简单、尽可能被反复审计。实现这一点的原则叫 **de Bruijn 判据**（de Bruijn criterion）：

```
把系统切成两半——
  · 内核（kernel）：极小的、只做最终检查的可信代码；
  · 其余全部（tactic、自动化、elaboration 详释引擎、UI、库）：一律视为"不可信的建议者"。
只有内核的裁决算数；建议者怎么产生候选证明都行，错了内核会拒。
```

据 Rocq 官方参考手册，内核与详释引擎/tactic 的这道分界正是 de Bruijn 判据的体现：保持一个小而边界清晰的可信代码库，即使外围系统可以复杂得多。你只需信任这个更小的关键组件（内核），而非整个系统。

内核像考试的判卷标准答案核对程序，tactic 像一群帮你打草稿的助教。助教可以七嘴八舌、甚至偶尔算错，但最终交卷时那份答案必须逐字通过判卷程序。判卷程序几千行、被无数人盯过；助教代码几十万行、天天在改。把信任押在前者身上，是这套体系可靠性的全部秘密。这也解释了一个初学者常困惑的点——为什么 tactic 有 bug 不致命：tactic 只是"提议"一个证明，提议错了内核会打回，它无法伪造一个能骗过内核的假证明（除非内核自己有 bug）。

### V11.1.3 类型论阵营：证明项 + 内核即类型检查器（Coq/Rocq、Lean）

在 Coq/Rocq 与 Lean 这类基于依赖类型论的系统里，"证明"被具体化成一个**证明项**（proof term）——一个 Calculus of Inductive Constructions（CIC，归纳构造演算）里的项，它的**类型恰好就是待证定理的陈述**。于是内核的工作就是一个**类型检查器**：验证这个项确实具有它声称的类型。

```
定理陈述  ≙  一个类型 A
证明      ≙  一个项 p，使得 p : A（p 的类型是 A）
内核裁决  ≙  类型检查："p : A 成立吗？"——成立则定理得证
```

用户平时用 tactic 交互式地写证明，但 Rocq 手册说得很清楚：tactic 的作用是**增量地构造出一个证明项**，最后交由内核验证。也就是说，你敲的 `intros`、`induction`、`rewrite` 只是在幕后拼装那个 CIC 项；`Qed` 一按，内核就对拼好的项做最终类型检查。

初学者可以这样体会"证明就是项"：写证明和写程序在这里是**同一件事**，只不过程序的"类型"是一个逻辑命题。这就是 V11.2 要展开的 Curry-Howard 对应，也是类型论阵营 TCB 特别干净的原因——不需要单独发明一套"证明检查器"，复用类型检查器即可。Coq/Rocq 还能把这些经过验证的定义/函数**抽取**（extraction）成 OCaml/Haskell 等可执行代码，让"经证明正确的算法"直接变成能跑的程序（CompCert 就是这么做的，见 V11.4）。

### V11.1.4 HOL/LCF 阵营：定理是抽象类型（Isabelle/HOL）

Isabelle/HOL、HOL Light 走的是另一条历史更悠久的路线——**LCF 式架构**，源自 Robin Milner 为 Edinburgh LCF 设计的思想。这里没有显式的"证明项"，取而代之的是一个技巧：把"定理"实现成一个**受保护的抽象数据类型** `thm`，唯一能造出 `thm` 值的途径，是调用那几个对应**基本推理规则**的内核函数。

```
定理  ≙  抽象类型 thm 的一个值
造定理的唯一合法途径  ≙  内核提供的少数几个推理规则函数（如 modus ponens、假设引入…）
其余一切（tactic、自动化）  ≙  只能通过组合这些内核函数来间接产生 thm，无法伪造
```

据 Paulson《From LCF to Isabelle/HOL》，LCF 式证明器建立在一个小逻辑核之上，用抽象类型保证可靠性，从而**不必存储证明对象**（但也支持在需要时生成）。换句话说，可靠性由编程语言的类型系统（抽象类型的封装）来担保：既然只有内核规则能生产 `thm`，那么任何拿到手的 `thm` 必定是合法推导出来的。

对初学者，两阵营的对照很有启发：类型论阵营"把证明写成一个可被复查的对象"（要信任的是类型检查器）；LCF 阵营"根本不让非法证明被造出来"（要信任的是那几个内核规则函数 + 抽象类型封装没被绕过）。前者是"事后验收"，后者是"事前封堵"，但共同点都是 de Bruijn 判据——TCB 都被压缩到一个极小、可审计的核。

### V11.1.5 主流系统定位与版本时效（⚙演进快·锚版本）

四个最常被提及的系统，定位与当前状态如下（核实 2026-07-30；此项 ⚙演进快，版本以官方发布为准）。

Coq / **The Rocq Prover** 是基于 CIC 的依赖类型论证明助手，法国 INRIA 系主导，学术界形式化验证的主力之一（CompCert、Feit-Thompson 定理等均用它）。2025 年起它正式更名为 The Rocq Prover，更名随 **Rocq 9.0.0（2025-03-12）** 完成；后续有 Rocq 9.1（2025-09-15）、9.1.1 补丁（2026-02-09）、9.2.0（2026-03-30），Rocq Platform 2025.08.0（2026-01-29）。旧名"Coq"在文献与教材中仍大量存在，处于过渡期。

Isabelle/HOL 是基于高阶逻辑的 LCF 式证明助手，慕尼黑/剑桥系，以强大的自动化（Sledgehammer 调外部 ATP/SMT）与 Isar 结构化证明语言著称。其稳定版为 **Isabelle2025（2025-03）**，其后 Isabelle2025-1（2025-12）因构建选项问题被 Isabelle2025-2（2026-01）替换。

Lean（Lean 4）由微软研究院 de Moura 等发起、现由 Lean FRO 维护，是依赖类型论证明助手兼编程语言，内核实现 CIC 家族的类型论（含累积宇宙与归纳类型），近年因数学库 **mathlib（mathlib4）** 的大规模数学形式化而迅速走红。此处硬标 ⚙演进快·锚版本——本报告不锚定具体 Lean 版本号，以官方发布为准。

Software Foundations 是本报告的主一手锚点，为 Pierce 等人用 Coq/Rocq 写成的可执行教材系列（整本书就是一份能被证明助手检查的证明脚本）。其 Vol.1 Logical Foundations **v7.0（2026-01-09）** 要求 Rocq/Coq 9.0.0+，正处于 Coq/Rocq 命名过渡态（书中两种称呼并存）。

#### 来源与时效
- Software Foundations, Vol.1 Logical Foundations, Preface（一手，证明助手定位、"整本书=证明脚本"；v7.0 要求 Rocq/Coq 9.0.0+，2026-01-09；https://softwarefoundations.cis.upenn.edu/lf-current/Preface.html ，核实 2026-07-30）。
- The Rocq Prover Reference Manual, "Core language" 章（一手，proof term = CIC 项、内核=类型检查器、de Bruijn 判据、tactic 增量构造证明项；V9.0.0/V9.1.0，https://rocq-prover.org/doc/V9.1.0/refman/language/core/index.html ，核实 2026-07-30）。
- Paulson, "From LCF to Isabelle/HOL", arXiv:1907.02836（一手/权威综述，LCF 式小内核、thm 抽象类型、无需存储证明对象）。
- 版本时效（⚙演进快·锚版本，核实 2026-07-30）：Rocq 9.0.0 更名完成 2025-03-12、9.2.0 2026-03-30（rocq-prover.org/releases）；Isabelle2025 2025-03、Isabelle2025-2 2026-01（sketis.net）；Lean 4 内核为 CIC 家族（lean-lang.org/papers/lean4.pdf）。
- 术语/命名冲突：同一系统在 2025 前称"Coq"、之后称"Rocq"，文献并存；本报告统一写"Coq/Rocq"，不视为矛盾而视为过渡期命名，涉及版本处均硬标。

## V11.2 依赖类型即命题（Curry-Howard 深化）

### V11.2.1 Curry-Howard 对应：命题即类型、证明即程序

Curry-Howard 对应（Curry-Howard correspondence，又称命题即类型 propositions-as-types）是交互式定理证明的理论心脏：**逻辑与类型论之间存在一一对应**——每个命题对应一个类型，每个证明对应该类型的一个项（程序），"命题可证"对应"类型非空（有居留者）"。这条对应把 L5-06 T10 通向的依赖类型论，与逻辑证明彻底焊在一起（T10 已点名接入本主题，此处不再展开 T10 侧的高阶系统细节）。

```
命题 A → B          对应   函数类型 A → B
命题 A ∧ B          对应   积类型（pair）A × B
命题 A ∨ B          对应   和类型 A + B
命题 True           对应   单元类型 unit（有唯一居留者）
命题 False          对应   空类型（无任何构造子/居留者）
命题 ∀ x:T, P x      对应   依赖函数类型（Π 类型）Π x:T. P x
命题 ∃ x:T, P x      对应   依赖对类型（Σ 类型）Σ x:T. P x
"A 有证明"           对应   "类型 A 有一个居留项 p : A"
"证明命题 A"          对应   "构造一个类型为 A 的程序 p"
```

初学者抓最基础的一行就够入门：**蕴含 A → B 就是函数类型 A → B**。为什么？一个"从 A 推出 B"的证明，本质就是一个方法——拿到任何一份 A 的证明，就能加工出一份 B 的证明；这不正是一个"输入 A 的证明、输出 B 的证明"的函数吗？同理，A ∧ B 的证明要同时给出 A 的证明和 B 的证明，所以是一个"装了两样东西的 pair"（积类型）；A ∨ B 的证明是"要么给 A 的证明、要么给 B 的证明并标明是哪边"，所以是和类型。False 对应空类型这一条尤其精妙：False 无法被证明，恰对应"空类型没有任何居留者"。

### V11.2.2 依赖类型：类型可以依赖于值

依赖类型（dependent type）指**类型本身可以依赖（提及）具体的值**。普通类型系统里 `list`（列表）是个类型；依赖类型里可以有 `vector n`——长度恰为 n 的向量，这个类型里嵌着一个值 n。正是依赖类型让"类型"表达力强到足以编码任意数学命题：命题 `P x`（关于 x 的性质）本身就是一个依赖于 x 的类型。

```
非依赖：  A → B          （给一个 A，还一个 B；返回类型固定是 B）
依赖：    Π x:A. B(x)     （给一个 a:A，还一个 B(a)；返回类型可随输入 a 变化）
```

Π 类型（依赖函数类型）是普通函数类型的推广，对应全称量词 ∀。为什么 ∀ x:T, P x 是 Π 类型？因为它的证明必须是一个函数：对任意给定的 x:T，都能产出一份 P x 的证明——而 P x 这个"目标类型"是随 x 变的，所以必须用依赖函数。同理 Σ 类型（依赖对类型）对应 ∃：∃ x, P x 的证明是一个 pair，第一个分量是"见证者"（witness）某个具体的 x₀，第二个分量是 P x₀ 的证明。

依赖类型让你能把"这个函数返回的列表长度等于输入长度"这种规约**写进类型签名**里，于是类型检查器在检查程序类型的同时就检查了这条规约。命题只是依赖类型的一种特例（返回到"证据"世界的类型）。这也是为什么在 Coq/Rocq 里定义一个函数和陈述一个定理用的是同一套语言。

### V11.2.3 Prop 与 Type：证据与数据的分野

在 Coq/Rocq 里，类型被安排在不同的"宇宙/种类"（sort）中，最常见的两个是 `Prop` 与 `Type`。`Prop` 是**逻辑命题**所在的宇宙——它的居留者是"证明/证据"；`Type` 是**普通数据类型**所在的宇宙——它的居留者是"数据"（nat、list 等）。命题即类型的对应在这里落地为：一个逻辑命题的 Coq 表示是一个 `: Prop` 的类型，它的证明是这个类型的项。

```
P : Prop         P 是一个命题（逻辑陈述）
h : P            h 是命题 P 的一份证明（证据）
nat : Type       nat 是一个数据类型
3 : nat          3 是 nat 的一个数据
```

为什么要把 Prop 和 Type 分开？一个关键工程理由是**证明无关性与抽取**：`Prop` 里的证据在把程序抽取成可执行 OCaml/Haskell 代码时会被**擦除**（因为运行时不需要"为什么正确"的证据，只需要数据和计算）。初学者不必深究宇宙层级（universe hierarchy，为避免罗素悖论式的 `Type : Type` 而分层），只需记住：Prop 装"证明"、Type 装"数据"，二者共用同一套依赖类型语言，这正是 Curry-Howard 让逻辑与编程统一后的自然产物。

### V11.2.4 计算即证明的一部分：定义等价

依赖类型论里还有一个初学者常忽略但很重要的机制：**类型检查会做计算**。两个在计算上相等（能互相化简到同一范式）的类型被视为同一个类型，这叫定义等价（definitional equality）或转换规则（conversion）。于是有些等式命题"算一下就成立"，证明可以短到只需说"两边化简后相同"。

```
在 Coq/Rocq 中，2 + 2 与 4 定义等价（+ 按定义展开计算后同为 4）
故命题 2 + 2 = 4 的证明可以只是 reflexivity（自反性：两边算出来一样）
```

这一点解释了为什么在证明助手里"计算"和"证明"没有清晰界线——把命题的两边尽量算简单，往往证明就自动完成了。初学者可把它对照普通编程里的"常量折叠"：编译器知道 `2+2` 就是 `4`；在依赖类型论里，类型检查器同样"知道"，于是这种相等无需额外逻辑步骤。这也预告了 V11.3——很多归纳证明的基础情形（base case）就是靠 `reflexivity` 这种"算一下就相等"关掉的。

#### 来源与时效
- Software Foundations, Vol.1, Poly / IndProp / ProofObjects 各章（一手，命题即类型、Prop vs Type、证明项、reflexivity 与定义等价；核实 2026-07-30，https://softwarefoundations.cis.upenn.edu/lf-current/ProofObjects.html ）。
- The Rocq Prover Reference Manual, "Core language"（一手，Sorts：Prop/SProp/Type 与宇宙层级、Π/依赖乘积、转换/定义等价；V9.1.0，核实 2026-07-30）。
- Coquand & Huet, "The Calculus of Constructions", Information and Computation 76(2/3), 1988（一手，CoC——Coq/Rocq 逻辑基础的奠基文献，依赖类型 + 命题即类型）。
- 关联：Curry-Howard 的类型系统侧（System F/Fω、依赖类型一瞥）见 L5-06 T10（本报告点名接入，不展开高阶系统细节）。
- 口径提示：不同文献对 ∃ 的对应有"Σ 类型"（构造性/类型论习惯）与"存在类型 pack/open"（TAPL Ch.24 的数据抽象语境，L5-06 T8.4）两种用法，语境不同、非矛盾；本报告取类型论语境的 Σ 类型。

## V11.3 归纳类型与归纳证明

### V11.3.1 归纳类型定义：用构造子造出数据与命题

归纳类型（inductive type）是依赖类型论里定义数据与命题的主要手段：你列出若干**构造子**（constructor），规定"这个类型的东西只能由这些构造子造出来，别无其他"。自然数、列表、树、乃至逻辑命题（如"某数是偶数"）都用归纳定义给出。

```
Inductive nat : Type :=
  | O : nat                (* 零 *)
  | S : nat -> nat.        (* 后继：给一个 nat，造出下一个 nat *)
```

这行定义读作：`nat` 里的东西要么是 `O`，要么是"某个 nat 的后继 `S n`"，没有第三种来源。于是 `3` 其实是 `S (S (S O))`。命题也能归纳定义——例如"n 是偶数"：

```
Inductive ev : nat -> Prop :=
  | ev_0  : ev 0                              (* 0 是偶数 *)
  | ev_SS : forall n, ev n -> ev (S (S n)).   (* 若 n 偶，则 n+2 偶 *)
```

初学者要抓住"只能由构造子来"这条封闭性（no-junk / no-confusion）：正因为 nat 的东西只可能是 O 或 S 造的、且 O 与 S n 绝不相等、S 是单射，才能对它做严密的归纳推理与模式匹配。`ev` 的例子还展示了归纳定义命题的威力——`ev 4` 有证明（`ev_SS 2 (ev_SS 0 ev_0)`），而 `ev 3` 无论如何造不出证明，恰对应"3 不是偶数"。

### V11.3.2 归纳原理：每个归纳类型自带一条"数学归纳法"

每定义一个归纳类型，Coq/Rocq 会**自动生成**一条对应的**归纳原理**（induction principle）。对 nat，生成的正是熟悉的数学归纳法：

```
nat_ind :
  forall P : nat -> Prop,
    P O ->                                  (* 基础情形：P 对 0 成立 *)
    (forall n : nat, P n -> P (S n)) ->     (* 归纳步骤：P 对 n 成立则对 S n 成立 *)
    forall n : nat, P n                     (* 结论：P 对一切 n 成立 *)
```

这条原理本身就是一个项（一个函数），它的类型就是"数学归纳法"这个命题——又一次印证 Curry-Howard。为什么归纳类型能自带归纳法？因为构造子穷尽了造出该类型元素的全部方式：只要证明了"对每种构造方式，若各子部件满足 P 则整体满足 P"，就覆盖了所有可能的元素。

初学者可把归纳原理理解成"结构归纳法的自动特化"：数学归纳法是它在 nat 上的样子；换成 list，就变成"对空表成立 + 假设对表尾成立则对 cons 成立"；换成二叉树，就变成"对叶成立 + 对左右子树成立则对整树成立"。同一套结构归纳（L5-06 T1.3）被每个归纳类型自动实例化，这是 tactic `induction` 背后的引擎。

### V11.3.3 tactic 与证明状态：目标、假设、逐步归约

在证明助手里，你几乎不直接手写庞大的证明项，而是用 **tactic**（证明策略）交互式地把它拼出来。任一时刻屏幕显示一个**证明状态**：上方是已有的**假设**（hypotheses，即已知），一条横线，下方是当前要证的**目标**（goal）。每敲一个 tactic，就把当前目标变换/拆解成更简单的（可能多个）子目标，直到全部关闭。

```
tactic 速览（Software Foundations 常用）：
  intros    —— 把 ∀ / → 的前件移进假设（"设 n 为任意…""假设 H 成立…"）
  simpl     —— 按定义化简目标中的计算
  reflexivity —— 关掉"两边定义等价"的等式目标
  rewrite   —— 用一条等式改写目标（-> 正向 / <- 反向）
  induction —— 对某变量套用其归纳原理，分裂出基础情形与归纳步骤（并给出归纳假设 IH）
  destruct  —— 按构造子分情况（不给归纳假设）
  apply / exact —— 用已有定理/假设精确匹配目标
  Qed.      —— 收尾：交由内核对拼好的证明项做最终类型检查
```

初学者最该建立的心智模型是"目标—假设"这块黑板：tactic 不是"魔法一句证完"，而是把大目标持续拆小，直到每个子目标都能被 `reflexivity`、`apply H` 这类简单动作关掉。`Qed.` 不是"信我说的对"，而是"把我拼的证明项交给内核复查"——这再次呼应 V11.1 的 de Bruijn 判据：tactic 只是建议者，`Qed` 时内核才是裁判。

### V11.3.4 一次完整的归纳证明骨架

把上面拼起来，一个最小但完整的归纳证明长这样（示意，Coq/Rocq 本机未预装，未实机验证）。先看一个不需要归纳、靠化简即可的例子：

```
Theorem plus_O_n : forall n : nat, 0 + n = n.
Proof.
  intros n.       (* 设 n 为任意自然数 *)
  simpl.          (* 0 + n 按 + 的定义化简为 n *)
  reflexivity.    (* 目标变成 n = n，自反关闭 *)
Qed.
```

再看一个必须用归纳的对称例子——`n + 0 = n` 反而不能只靠 `simpl`（因为 `+` 通常按左参数递归定义，`n + 0` 卡在变量 n 上算不动），必须对 n 归纳：

```
Theorem plus_n_O : forall n : nat, n + 0 = n.
Proof.
  intros n.
  induction n as [| n' IHn'].   (* 对 n 归纳：分裂为 n=O 与 n=S n' 两个子目标 *)
  - (* 基础情形 n = O *)
    reflexivity.                (* 0 + 0 = 0，化简后自反 *)
  - (* 归纳步骤 n = S n'，归纳假设 IHn' : n' + 0 = n' *)
    simpl.                      (* S n' + 0 化简为 S (n' + 0) *)
    rewrite -> IHn'.            (* 用 IH 把 n' + 0 改写成 n' *)
    reflexivity.                (* 目标 S n' = S n'，自反关闭 *)
Qed.
```

这个例子几乎是 Software Foundations 的招牌，因为它戳中初学者最大的一个易错点：**`plus_O_n` 和 `plus_n_O` 看起来对称，难度却天差地别**。差别全在 `+` 的定义方向——它对左参数做递归，所以 `0 + n` 一步化简即得 `n`，而 `n + 0` 在 n 是变量时无从化简，必须归纳。这提醒初学者：能不能"算一下就完"取决于定义的递归结构，不取决于命题看着对不对称。这也是为什么 `induction n as [| n' IHn']` 里那个归纳假设 `IHn'` 是全部关键——归纳步骤能成，全靠"假设对 n' 已成立"这份 IH。

#### 来源与时效
- Software Foundations, Vol.1, Basics / Induction / IndProp 各章（一手，nat/ev 归纳定义、induction/simpl/rewrite/reflexivity tactic、plus_O_n 与 plus_n_O 对照、归纳假设 IH；核实 2026-07-30，https://softwarefoundations.cis.upenn.edu/lf-current/Induction.html ）。
- The Rocq Prover Reference Manual, "Inductive types and recursive functions" / "Tactics"（一手，Inductive 命令、自动生成的 _ind 归纳原理、tactic 语义；V9.1.0，核实 2026-07-30）。
- 关联：结构归纳/规则归纳的理论侧见 L5-06 T1.3；本节的归纳原理是其在具体归纳类型上的自动实例化。
- 工具缺席声明：本节代码为 Software Foundations 章节示意，Coq/Rocq 本机**未预装**，**未实机验证**；机制正确性以 SF 一手 + Rocq 手册比对为准，行为细节以官方为准、不凭记忆。

## V11.4 机器化元理论 / 可靠性

### V11.4.1 什么是机器化元理论

元理论（metatheory）指的是"关于一个形式系统本身的定理"——不是在某个类型系统里证某段程序对，而是**证这个类型系统本身有某种好性质**，最典型的就是类型安全（type safety）。机器化元理论（mechanized metatheory）就是把这类"关于语言/系统的证明"完整地写进证明助手、由内核逐步核验，而非停留在论文的纸笔证明上。它是本课"验证半场"的集大成处：把 L5-06 学的类型系统理论，用 V11.1–V11.3 的机器搬到无懈可击的高度。

初学者要分清两个层次。第一层是"用证明助手证某个具体程序正确"（如证某排序函数确实返回有序表）。第二层，也就是机器化元理论，是"用证明助手证某个类型系统/语言设计本身可靠"（如证 STLC 的类型系统绝不会让良类型程序卡住）。后者更抽象、更根本——它一次性担保了该语言下**所有**良类型程序的某种安全性。

为什么要机器化？因为语言的元理论证明又长又易错，纸笔证明中"这个情形类似、略去"的跳步屡屡藏着 bug。机器化强迫每个情形（每条求值规则、每个语法构造）都被显式处理，内核不放过任何一个，从而把"这个语言设计真的安全吗"提升为机器可核验的事实。

### V11.4.2 类型安全 = Progress + Preservation 的机械化

本课与 L5-06 T3 反复出现的类型安全定理，可靠性由两条定理合成：进展（Progress）与保持（Preservation）。机器化元理论最经典的练习，就是在 Coq/Rocq 里把简单类型 λ 演算（STLC）的这两条定理连同全部引理证到底。

```
进展 Progress：
  若  ⊢ t : T （t 是空环境下的良类型项），
  则  t 是一个值（value），或存在 t' 使 t --> t'（还能求值一步）。
  —— 良类型项永不"卡住"（stuck）。

保持 Preservation（主语归约 subject reduction）：
  若  ⊢ t : T  且  t --> t'，
  则  ⊢ t' : T。
  —— 求值一步后类型不变。
```

在 Software Foundations Vol.2（Programming Language Foundations）里，它们被写成可被内核检查的 Coq/Rocq 定理（示意，未实机验证）：

```
Theorem progress : forall t T,
  empty |-- t \in T ->
  value t \/ exists t', t --> t'.

Theorem preservation : forall t t' T,
  empty |-- t \in T ->
  t --> t' ->
  empty |-- t' \in T.
```

两条合起来给出的直觉是"良类型永不出错、且一直良类型"：进展说良类型项要么已是答案、要么还能往前走（不会半路卡死在无意义状态），保持说走一步之后仍良类型——归纳地反复用，就得到"良类型项一路求值下去，要么终止于一个值、要么无限求值，绝不会卡在既非值又无法前进的错误状态"。初学者常问的"那不终止的程序呢"——类型安全只保证"不卡住"（no stuck states），并不保证终止；终止性是更强的另一回事。这两条定理的证明结构（对类型推导或求值规则做归纳）正是 V11.3 归纳证明的大规模实战。

### V11.4.3 机器化验证的里程碑成果

机器化元理论与可靠性证明已支撑起一批"人类无法靠纸笔放心相信"的成果，列举本课应知的几个（成熟度均为已发表的重大成果，工具版本随时演进）。

CompCert 是 Xavier Leroy 等在 Coq 里从头**证明正确**的 C 编译器——它把"编译后的汇编与源程序语义等价"这条性质完整机械化，是"经证明正确的系统软件"的标杆，也用到了 V11.1.3 的抽取（把 Coq 定义抽取成可运行的 OCaml 编译器）。

seL4 是一个操作系统微内核，用 Isabelle/HOL 机械化证明了其 C 实现满足功能规约（功能正确性），是形式验证在系统软件领域最著名的成果之一。

四色定理（Gonthier，Coq）与 Feit-Thompson 奇阶定理（Gonthier 团队，Coq）则是数学定理的完全机械化证明，说明证明助手能承载人类数学的最深处；Kepler 猜想的 Flyspeck 项目则用 HOL Light/Isabelle 完成。

POPLmark 挑战（Aydemir 等，2005）是一份"号召大家用证明助手机械化编程语言元理论"的基准挑战题（以 System F<: 的元理论为标的），推动了 Coq/Isabelle 在 PL 元理论机械化上的工具与方法（如变量绑定的处理）成熟。

初学者从这份清单该带走的认知是：交互式定理证明不是象牙塔玩具——它已经产出了工业界与数学界都认可的、可信度远超普通同行评审的成果。共同点是，它们都把"我相信这对"降格成了"内核检查通过"。

### V11.4.4 回接 L5-14 R12：Rust 与机器化可靠性

本课验证半场的机器化元理论，与 L5-14「Rust 与内存安全」在 **RustBelt**（POPL'18）处汇合——RustBelt 用 Coq 里的 **Iris 分离逻辑**，为含 `unsafe` 的 Rust 标准库建立了机器化的语义可靠性证明（证明这些库的 `unsafe` 封装确实维持了 Rust 的安全保证）。这正是 V11.4.1–V11.4.2 那套"机械化类型安全/可靠性"方法在一门工业语言上的高峰应用（详情接 L5-14 R12，本报告点名不展开）。

STLC 的 progress + preservation（本节 + L5-06 T3）是"祖型"，RustBelt 是它在真实语言 + 分离逻辑 + `unsafe` 语义上的现代放大版。四门课（编译原理 L4-04、PL 理论 L5-06、本课 L6-02、Rust L5-14）在"机器化可靠性证明"这一点上收束——这也是把交互式定理证明放在整个知识库靠后位置的原因：它是许多线索的共同终点。

#### 来源与时效
- Software Foundations, Vol.2 Programming Language Foundations, Stlc / StlcProp / Types 各章（一手，progress/preservation 的 Coq/Rocq 机械化定理与证明结构；核实 2026-07-30，https://softwarefoundations.cis.upenn.edu/plf-current/StlcProp.html ）。
- Leroy, "Formal verification of a realistic compiler", CACM 52(7), 2009（一手，CompCert——Coq 机械化的可验证 C 编译器 + 抽取）。
- Klein 等, "seL4: Formal Verification of an OS Kernel", SOSP'09（一手，Isabelle/HOL 机械化 OS 微内核功能正确性）。
- Aydemir 等, "Mechanized Metatheory for the Masses: The POPLmark Challenge", TPHOLs 2005（一手，PL 元理论机械化基准，System F<: 绑定处理）。
- Gonthier, "Formal Proof—The Four-Color Theorem", Notices AMS 55(11), 2008（一手，Coq 机械化四色定理）；Feit-Thompson（Gonthier 团队 2012，Coq）。
- 关联/点名不展开：RustBelt（Jung 等, POPL'18，Coq + Iris 分离逻辑，含 unsafe 的 Rust 库机械化可靠性）详见 L5-14 R12；类型安全 = Progress + Preservation 的理论侧见 L5-06 T3。
- 工具缺席声明：本节 Coq/Rocq 定理为 Software Foundations Vol.2 章节示意，本机**未预装**、**未实机验证**；成果与定理陈述以各一手论文 + SF 比对为准。
