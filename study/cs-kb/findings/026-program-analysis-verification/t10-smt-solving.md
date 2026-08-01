# L6-02·大主题V10 SMT 求解引擎

> 基线 Py3.11.15/np2.4.6/gcc13.3 @2026-07-25 ｜ 核实日期：2026-07-30 ｜ 先修：本课 V7（符号执行，路径条件交 SMT 判可达）、V9（演绎验证，VC 交求解器）、L4-04 命题/一阶逻辑基础 ｜ 一手锚点：de Moura & Bjørner, "Z3: An Efficient SMT Solver", TACAS 2008（https://github.com/Z3Prover/z3 ）；Kroening & Strichman《Decision Procedures: An Algorithmic Point of View》(2nd ed., Springer 2016)；SMT-LIB Standard v2.7（2025-02-05，https://smt-lib.org ）｜ 交叉一手：Davis-Putnam 1960 与 Davis-Logemann-Loveland 1962（DPLL 原论文）；Nieuwenhuis-Oliveras-Tinelli, "Solving SAT and SAT Modulo Theories", JACM 53(6), 2006（DPLL(T) 抽象框架）；Nelson & Oppen, "Simplification by Cooperating Decision Procedures", ACM TOPLAS 1(2), 1979（理论组合）；Z3 官方文档 https://microsoft.github.io/z3guide/ ｜ 后端实证：z3-solver 5.0.0（本机 pip 装，真跑取证）｜ 成熟度：GA/内核稳定、工具层演进快（Z3 自 2008 起工业级；SMT-LIB 输入格式活跃演进）

SMT（Satisfiability Modulo Theories，模理论可满足性）求解引擎是这门课"验证半场"的**共享后端**——符号执行（V7）问"这条路径可不可达"、演绎验证（V9）问"这个验证条件成不成立"，最后都归结为一句话：**把一个逻辑公式交给 SMT 求解器，问它"有没有一组取值让公式为真"**。一句话心智模型是：SMT = SAT（命题可满足性）+ 一批"理论"（算术、位向量、数组、未解释函数……），它在纯布尔逻辑的"真/假"之上，额外理解"x + 2y = 7""数组第 i 位存了 10""a = b 就必须 f(a) = f(b)"这类**带含义的约束**。

抓住一条主线就抓住了整个引擎：SAT 求解器（V10.1）负责把公式的布尔骨架搜个遍，理论求解器（V10.3）负责判断每一组布尔赋值在具体理论里到底成不成立，两者的协作协议就是 DPLL(T)（V10.2）；当一个公式同时用了好几种理论，Nelson-Oppen（V10.4）规定它们怎么交换信息合作；求解结束后引擎给出 sat / unsat / model / unsat-core 四类产物（V10.5），这正是上层验证工具真正消费的东西。

> 本报告所有 sat/unsat/model/unsat-core 结论在如下环境实跑取证：Python 3.11.15 + z3-solver 5.0.0 @2026-07-30，代码仅存 scratchpad、不入库，正文贴真实输出。实证是多来源比对的**补充而非替代**：机制性命题（DPLL/CDCL/DPLL(T)/Nelson-Oppen 的算法）以一手论文与 Kroening-Strichman 教材比对为准。⚠版本分歧：本机 `z3.get_version_string()` 返回 `5.0.0`（与 brief 锚定的 z3-solver 5.0.0.0 一致），而 2026-07-30 核实 PyPI 上游 z3-solver 最新为 4.16.0.0——本机版本号高于上游，属沙箱环境特有，实测结果以本机为准，版本对齐上游情况标「待核」。

---

## V10.1 SAT 与 DPLL/CDCL：命题可满足性内核

### V10.1.1 命题可满足性问题（SAT）

命题可满足性问题（Boolean Satisfiability，SAT）问的是：给定一个只由布尔变量、与（∧）、或（∨）、非（¬）构成的命题公式，**是否存在一组"真/假"赋值让整个公式为真**？公式通常先转成合取范式（CNF），即"一堆子句的合取"，每个子句是"一堆文字的析取"，文字就是变量 p 或它的否定 ¬p。SAT 是历史上第一个被证明 NP-完全的问题（Cook 1971），意味着最坏情况下没有已知多项式算法，但现代 SAT 求解器在实践中能处理上百万变量的工业实例。

举个最小 CNF 例子，三个子句：

```
(p ∨ q) ∧ (¬p ∨ r) ∧ (¬r)
```

初学者可以把每个子句读成一条"必须满足的要求"：第一条要求"p 或 q 至少一个真"，第三条 (¬r) 直接逼着 r = 假；r 假了，第二条要满足就得 ¬p 为真即 p = 假；p 假了，第一条就得 q = 真。于是唯一逼出的解是 p=假, q=真, r=假。本机 z3 对这个公式实测返回 `sat`，模型 `[r = False, q = True, p = False]`——和手推一致。SAT 之所以是 SMT 的内核，是因为任何 SMT 公式都有一层"布尔骨架"（把每个理论原子当成一个布尔变量），先得把这层骨架搜清楚。

### V10.1.2 DPLL 算法：回溯搜索 + 单元传播

DPLL 是 Davis-Putnam（1960）经 Davis-Logemann-Loveland（1962）改进而成的完备判定过程，是几乎所有现代 SAT 求解器的骨架。它是一个"带智能剪枝的深度优先回溯搜索"：反复挑一个还没赋值的变量试着赋真或假（decide），沿途用两条规则化简，走进死胡同就回溯。两条核心规则是：

```
单元传播（unit propagation / BCP）：
  若某子句只剩一个未赋值文字、其余文字都已为假，
  则该文字必须为真（否则子句假），立即赋值并传播。

纯文字规则（pure literal）：
  若某变量在所有子句中只以一种极性出现（总是 p 或总是 ¬p），
  可直接把它赋成让这些子句都满足的值。
```

初学者最该记住的是"单元传播"这条：它是 DPLL 的主力，把一个决策的**逻辑后果**一次性推到底。比如上例里一旦决定 r=假，(¬r) 子句…其实 (¬r) 本身就是单元子句，直接逼出 r=假，再逼出 p=假，再逼出 q=真，全程不需要"猜"。DPLL 的完备性来自：如果搜索遍历了所有决策组合仍找不到满足赋值，就能确定地回答 unsat；找到一组就回答 sat。相比 1960 年原始 Davis-Putnam 用变量消解（resolution）导致子句数爆炸，DPLL 改用回溯搜索，空间可控，这是它胜出的关键。

### V10.1.3 CDCL：冲突驱动子句学习

CDCL（Conflict-Driven Clause Learning，冲突驱动子句学习）是 1990 年代对 DPLL 的重大升级，是今天所有高性能 SAT 求解器（也是 Z3 内部 SAT 引擎）的实际算法。朴素 DPLL 碰到冲突（某子句所有文字都假）只会"退一步换个值"（时序回溯，chronological backtracking），常常反复在同一个坑里打转。CDCL 的洞见是：**每次冲突都分析"到底是哪几个决策共同导致了矛盾"，把这个原因固化成一条新学到的子句加进公式，然后跳回到导致冲突的那个决策层**（非时序回溯，non-chronological backtracking / backjumping）。学到的子句阻止求解器将来重犯同样的错误。

CDCL 的关键部件，按一手文献（Marques-Silva & Sakallah 的 GRASP 1996/1999；Moskewicz 等的 Chaff 2001）与 Kroening-Strichman §2 归纳如下：

```
冲突分析 + 蕴含图：追溯冲突的成因，计算学习子句（常用 1-UIP 切割）
非时序回溯（backjumping）：直接跳回相关决策层，而非只退一层
学习子句（learned clauses）：把冲突原因加入子句库，剪掉整片搜索空间
VSIDS 决策启发式：优先选"最近参与冲突多"的变量分支
两观察文字（two watched literals）：让单元传播的检测近乎 O(1)，是工程加速关键
定期重启（restart）：清空决策栈重来但保留学到的子句，跳出坏搜索区
```

初学者可以把 CDCL 想成"会记教训的 DPLL"：DPLL 是每次撞墙只退一步的莽夫，CDCL 撞墙后会分析"为什么撞墙"并写下一条"以后别再这么走"的规则，还能一口气跳回问题的真正源头。这就是为什么现代 SAT 能处理工业级规模。DPLL(T) 里 SAT 骨架用的正是 CDCL，理论求解器学到的冲突也以"理论子句"的形式喂回 CDCL 的学习机制（见 V10.2）。

#### 来源与时效

- 一手：Davis & Putnam, "A Computing Procedure for Quantification Theory", JACM 7(3), 1960；Davis, Logemann & Loveland, "A Machine Program for Theorem-Proving", CACM 5(7), 1962（DPLL 两篇奠基论文）。核实 2026-07-30。
- 一手：Marques-Silva & Sakallah, "GRASP: A Search Algorithm for Propositional Satisfiability", IEEE Trans. Computers 48(5), 1999（CDCL/子句学习）；Moskewicz et al., "Chaff: Engineering an Efficient SAT Solver", DAC 2001（VSIDS、两观察文字）。核实 2026-07-30。
- 教材交叉：Kroening & Strichman《Decision Procedures》2nd ed. 2016, §1–2（SAT/DPLL/CDCL 系统阐述）。
- 实证：z3-solver 5.0.0 @2026-07-30 对 `(p∨q)∧(¬p∨r)∧(¬r)` 实测 `sat`，模型 `[r=False, q=True, p=False]`，与手推一致。
- 无冲突项。Cook 定理（SAT 为 NP-完全）为课外一手（Cook 1971 STOC），此处仅作背景，不承重。

---

## V10.2 DPLL(T) 框架：理论求解器与布尔骨架协作

### V10.2.1 从 SAT 到 SMT：Lazy 与 Eager 两条路线

SMT 要判定的公式里，原子不再是纯布尔变量，而是**理论原子**，比如 `x + 2y = 7`、`a = b`、`A[i] = 10`。把这些理论原子各自替换成一个新布尔变量，得到的就是公式的**布尔骨架**（Boolean skeleton）。把 SMT 归约到 SAT 有两条经典路线：

```
Eager（急切）编码：把整个理论一次性编码成等价的纯命题公式，再交给 SAT 求解器一把梭。
  代表：位向量常用的 bit-blasting。优点是复用成熟 SAT；缺点是编码可能巨大。

Lazy（惰性）集成：SAT 只管布尔骨架，理论判断按需、增量地交给专门的理论求解器 T-solver。
  代表：DPLL(T)。这是 Z3、CVC5 等主流 SMT 求解器的主架构。
```

初学者可以这样理解两者的差别：eager 是"先把所有理论含义翻译成布尔谜题，再整体解一次"；lazy 是"先假装每个理论原子只是个布尔开关，SAT 猜一组开关状态，再回头问理论求解器'这组状态在算术/数组里真的成立吗'"。lazy 之所以主流，是因为它只在需要时才调用昂贵的理论推理，且能把理论发现的矛盾即时反馈给 SAT 剪枝。

### V10.2.2 DPLL(T) 的协作循环

DPLL(T) 是 lazy 路线的标准框架，由 Nieuwenhuis-Oliveras-Tinelli（JACM 2006）抽象化、de Moura & Bjørner 在 Z3（TACAS'08）中工程实现。它把一个 CDCL SAT 求解器和一个（或多个）理论求解器 T-solver 组成一个反馈循环。核心流程如下：

```
1. SAT 引擎（CDCL）在布尔骨架上做单元传播/决策，得到一个部分或完整的真值赋值 M。
2. M 对应一组"被断言为真/假的理论原子"，交给 T-solver 做一致性检查（T-consistency）。
3a. T-solver 说"一致"（sat）：若 M 已完整 → 整个公式 SAT，返回 model。
3b. T-solver 说"不一致"（unsat）：它给出一个"理论冲突原因"——一小撮互相矛盾的理论文字，
    取其否定的析取，作为一条"理论引理"（theory lemma / T-lemma）加回 SAT 的子句库。
4. SAT 引擎把这条 T-lemma 当作学到的冲突子句，回溯并继续搜索。
5. 反复，直到 SAT 找到 T-一致的完整赋值（SAT）或穷尽搜索（UNSAT）。
```

**SAT 负责"枚举布尔可能性"，理论求解器负责"否决那些理论上讲不通的可能性"，被否决的原因以一条新子句的形式回喂给 SAT，让它以后不再犯同样的错**。这正好复用了 V10.1.3 里 CDCL 的子句学习机制——理论冲突和布尔冲突走同一条学习通道。

公式 `(x > 5) ∧ (x < 3)`。SAT 只看到两个布尔开关 A=(x>5)、B=(x<3)，把它们都设真是完全合法的布尔赋值。可一旦把 {x>5, x<3} 交给算术 T-solver，它立刻报"不一致"，给出理论引理 ¬(x>5) ∨ ¬(x<3)。SAT 加入这条子句后无路可走，整体返回 unsat。本机 z3 对 `x>5 ∧ x<3` 实测正是 `unsat`。

### V10.2.3 理论传播与在线（online）集成

朴素 lazy 集成只在 SAT 猜出**完整**赋值后才叫理论求解器，效率低。DPLL(T) 的关键优化是把理论求解做成**增量、在线、带传播**的：

```
理论传播（theory propagation）：
  T-solver 不只在事后检查一致性，还能主动推断"在当前已断言的理论文字下，
  某个尚未赋值的理论原子必然为真/假"，把这个结论作为单元传播喂给 SAT。
  例：已断言 x = y，理论可传播出 (x > 0) ↔ (y > 0)，SAT 少猜一步。

早期剪枝（early pruning）：
  SAT 每扩展一部分赋值就顺手做一次轻量理论一致性检查，
  一旦部分赋值已经理论不一致就立即回溯，不等它长成完整赋值。

增量性 + 可回溯（incremental / backtrackable）：
  T-solver 支持随 SAT 的 push/pop 一起增量添加、撤销断言，避免每次从头重算。
```

初学者可以把理论传播理解成"理论求解器也参与单元传播"：它像一个懂算术的助手，在 SAT 还在犹豫时就提前说"既然你已经定了 x=y，那 x>0 和 y>0 必须同真同假，别浪费时间分别猜了"。这三项优化（理论传播、早期剪枝、增量求解）是 DPLL(T) 从"能用"到"高效"的分水岭，也是 de Moura & Bjørner 在 Z3 里强调的工程重点。

#### 来源与时效

- 一手：de Moura & Bjørner, "Z3: An Efficient SMT Solver", TACAS 2008（DPLL(T) 的工程实现、理论传播/增量集成）。核实 2026-07-30。
- 一手：Nieuwenhuis, Oliveras & Tinelli, "Solving SAT and SAT Modulo Theories: From an Abstract DPLL Procedure to DPLL(T)", JACM 53(6), 2006（DPLL(T) 抽象框架、lazy vs eager、理论传播的形式化）。核实 2026-07-30。
- 教材交叉：Kroening & Strichman《Decision Procedures》2nd ed. 2016, §3（SMT 与 lazy/eager 集成）。
- 实证：z3-solver 5.0.0 @2026-07-30 对 `x>5 ∧ x<3` 实测 `unsat`，与 DPLL(T) 循环手推一致。
- 无冲突项。术语提示：eager 编码的位向量 bit-blasting 归入 V10.3.2 展开。

---

## V10.3 常用理论：线性算术 / 位向量 / 数组 / 未解释函数

### V10.3.1 线性算术（LIA / LRA）

线性算术理论处理形如 `a₁x₁ + … + aₙxₙ ≤ b`（含 =、<、≥ 等）的线性约束。按变量取值域分两种：LRA（Linear Real Arithmetic，实数/有理数上的线性算术）和 LIA（Linear Integer Arithmetic，整数上的线性算术）。乘两个变量的非线性项（如 x·y）不属于线性算术，Z3 会转入更弱可判定性的非线性算术 NIA/NRA。

LRA 的核心判定过程是**单纯形法**（simplex，Z3 用其变体）：把约束看成多面体，判可行性。LIA 更难——整数解的存在性判定需在 LRA 之上加**分支定界**（branch-and-bound）或**割平面**（cutting planes）：先求实数松弛解，若某变量取了非整数值 x=3.5，就分裂成 x≤3 和 x≥4 两支分别再解。判定复杂度上，LRA 多项式可解，LIA 是 NP-完全（一般 Presburger 算术可判定但复杂度极高）。

本机 z3 实测：LIA 公式

```
x + 2y = 7  ∧  x − y = 1
```

返回 `sat`，模型 `[y = 2, x = 3]`（代回：3+4=7 ✓，3−2=1 ✓）。LRA 公式

```
a + b = 1  ∧  a − b = 1/3
```

返回 `sat`，模型 `[b = 1/3, a = 2/3]`——注意 LRA 的模型是**有理数**（Z3 用精确有理数而非浮点），这是初学者常忽略的点：LRA 不做浮点近似，1/3 就是精确的三分之一。

### V10.3.2 位向量（Bit-Vectors, BV）

位向量理论把变量建模成**固定宽度的二进制位串**（如 32 位、8 位），并提供机器算术语义：加减乘、按位与或非、移位、有/无符号比较、截断/拼接。它和数学整数的关键区别是**有限位宽会溢出、会按模 2ⁿ 回绕**，这正是位向量能精确刻画 C/汇编等真实机器运算的原因，也是它在符号执行、二进制分析里不可替代的地方。

位向量的标准求解手法是**bit-blasting**（位爆破，属 eager 路线）：把每个 n 位向量变量拆成 n 个布尔变量，把加法器、乘法器等按数字电路展开成等价的命题公式，整体交给 SAT。优点是精确、复用 SAT；缺点是宽度大、乘法多时公式巨大。

本机 z3 实测，8 位向量 bx 满足

```
bx + 1 == 0     （8 位无符号）
```

返回 `sat`，模型 `[bx = 255]`。这正是溢出回绕：255 + 1 在 8 位里回绕成 0。初学者若用数学整数直觉会以为无解（哪个整数加 1 等于 0？），但在位向量语义下 255（即 0xFF）加 1 溢出归零——这一条最能说明"位向量 ≠ 整数"，也说明为什么分析真实程序的整数溢出漏洞必须用 BV 而非 LIA。

### V10.3.3 数组（Arrays）

数组理论（McCarthy 1962 的经典公理化）建模"可读可写的映射"，两个操作：

```
select(A, i)        读：数组 A 在下标 i 处的值
store(A, i, v)      写：返回一个新数组，除下标 i 处变为 v 外与 A 相同
```

核心是两条**读写公理**（read-over-write）：

```
i = j  →  select(store(A, i, v), j) = v          （读刚写的那个下标，得写入值）
i ≠ j  →  select(store(A, i, v), j) = select(A, j)  （读别的下标，写操作无影响）
```

外加一条**外延性公理**（extensionality）：两数组在所有下标处都相等，则两数组相等。

本机 z3 实测，公式

```
i = j  ∧  select(store(A, i, 10), j) ≠ 10
```

返回 `unsat`——因为 i=j 时读写公理逼出 select(store(A,i,10),j)=10，与"≠10"矛盾。初学者要抓住的直觉是：**store 不是"就地修改"，而是"返回一个改过一处的新数组"**（函数式语义），这样才能对"同一个逻辑数组的不同版本"做推理，正是程序验证里刻画堆、内存的基础。

### V10.3.4 未解释函数（Uninterpreted Functions, UF / EUF）

未解释函数理论（带等词的一阶逻辑片段，EUF）里，函数符号 f 没有任何具体定义，我们对它**唯一**知道的性质是**函数一致性**（congruence，同余）：

```
a = b  →  f(a) = f(b)          （相等的输入必给相等的输出）
```

判定 EUF 的标准算法是**同余闭包**（congruence closure）：维护项之间的等价类，遇到 a=b 就合并两类，再传播——若 a、b 已同类，则把 f(a)、f(b) 也合并。UF 的用途是**抽象**：当程序里某个函数（如复杂库调用、乘法）对当前证明无关紧要时，把它抽象成未解释函数，只保留"同输入同输出"这一条，能大幅简化求解。

本机 z3 实测，公式

```
a = b  ∧  f(a) ≠ f(b)
```

返回 `unsat`——违反函数一致性。初学者常问"未解释是什么意思"：意思是求解器**不看 f 内部怎么算**，只认"输入一样输出必一样"这一条铁律。这也解释了 UF 为何是 Nelson-Oppen 组合（V10.4）里最常出现的粘合理论——它几乎和任何其他理论都能干净地组合。

#### 来源与时效

- 一手：SMT-LIB Standard v2.7（2025-02-05，理论定义 Ints/Reals/FixedSizeBitVectors/ArraysEx/Core 的官方语义）；⚠时效分歧：v2.6 最新修订为 2021-05-12，v2.7 于 2025-02-05 发布并新增多态与 map 理论——理论核心语义两版一致，本报告以 v2.7 为准。核实 2026-07-30。
- 一手：de Moura & Bjørner, TACAS 2008（Z3 各理论求解器：simplex、bit-blasting、congruence closure）。核实 2026-07-30。
- 教材交叉：Kroening & Strichman《Decision Procedures》2nd ed. 2016——§5 EUF/congruence closure、§7 位向量、§8 数组、§5–6 线性算术；McCarthy, "Towards a Mathematical Science of Computation", 1962（数组读写公理原始出处）。
- 实证（z3-solver 5.0.0 @2026-07-30，真实输出）：LIA `x+2y=7 ∧ x−y=1` → sat `[y=2,x=3]`；LRA `a+b=1 ∧ a−b=1/3` → sat `[b=1/3,a=2/3]`；BV8 `bx+1==0` → sat `[bx=255]`（溢出回绕）；数组 `i=j ∧ select(store(A,i,10),j)≠10` → unsat；UF `a=b ∧ f(a)≠f(b)` → unsat。
- 无冲突项。待核：本机 z3-solver 版本号 5.0.0 与上游 4.16.0.0 的对齐关系（见报告抬头分歧标注）。

---

## V10.4 理论组合：Nelson-Oppen 组合

### V10.4.1 为什么需要理论组合

真实的验证条件几乎总是**多个理论混用**的。比如 `f(x) = f(y) ∧ x + 1 = y + 1`，里面既有未解释函数 f（EUF 理论），又有整数加法（算术理论）。单独一个理论求解器只懂自己那一块，谁也判不了整个公式。理论组合要解决的问题是：**已有 T₁ 的判定过程和 T₂ 的判定过程，如何组合出 T₁ ∪ T₂（两个理论的并）的判定过程**，而不必从头写一个大而全的求解器。

Nelson-Oppen（1979）给出的答案是：让两个理论求解器**只通过交换"变量之间的等式/不等式"来合作**——各自在自己理论里推理，把推出的"共享变量 x 和 y 相等"这类信息告诉对方，如此往复直到达成一致或某方报矛盾。

### V10.4.2 Nelson-Oppen 组合过程

Nelson-Oppen 方法（TOPLAS 1979；Kroening-Strichman §10 系统阐述）的标准流程：

```
0. 前提：两理论签名不相交（disjoint signatures，除等号外无共享符号），
         且都是 stably infinite（稳定无限）理论。
1. 净化（purification）：引入辅助变量，把混合项拆开，
   使每个原子只属于单一理论。共享的辅助变量成为两理论间的"接口"。
2. 各理论求解器独立判定自己那部分约束的可满足性。
3. 等式传播（equality propagation）：
   若某理论推出两个共享变量必然相等（xᵢ = xⱼ），就把这条等式告知另一理论。
4. 反复第 2–3 步，直到：某理论报 unsat（整体 unsat），
   或再无新的共享等式可传播且各理论都 sat（整体 sat）。
```

初学者可以把它想成"两个只懂各自领域的专家隔着一堵墙合作"：算术专家和函数专家看不到对方的公式，只能通过墙上的小窗口互相递纸条，纸条上只能写"你我共享的那些变量里，x 和 y 其实相等"。靠不断递这种最朴素的等式信息，两个局部判定就能拼出全局判定。用 §V10.4.1 的例子：算术侧从 x+1=y+1 推出 x=y，把"x=y"递给 EUF 侧；EUF 侧由函数一致性得 f(x)=f(y)，与原公式的 f(x)=f(y) 一致，整体 sat。

本机 z3 实测组合场景（UF+LIA）：`xi = yi ∧ g(xi) ≠ g(yi)` 返回 `unsat`（算术侧确认 xi=yi，UF 侧的一致性逼出 g(xi)=g(yi)，与 ≠ 矛盾）。

### V10.4.3 凸性、稳定无限与非凸理论的代价

Nelson-Oppen 的适用有两个理论层面的前提，初学者最容易踩坑，务必点清：

```
稳定无限（stably infinite）：理论的每个可满足公式都在某个无限论域上可满足。
  LRA、LIA、EUF 都满足；这是保证组合可靠（sound）的技术条件。

签名不相交（disjoint）：两理论除等号 = 外不共享函数/谓词符号。
```

另一个关键区分是**凸（convex）vs 非凸（non-convex）**理论：

```
凸理论：若一组约束蕴含某个"等式的析取" x₁=y₁ ∨ … ∨ xₙ=yₙ，
        则它必蕴含其中某一个单独的等式。
        LRA、EUF 是凸的 → 等式传播只需传单个等式，组合过程是多项式的。

非凸理论：LIA（整数）是非凸的。
        例：1 ≤ x ≤ 2 蕴含 (x=1 ∨ x=2) 却不蕴含任一单个等式。
        → 组合时必须对"等式的析取"做案例分裂（case split），代价指数级上升。
```

**凸理论合作时只需递"x=y"这种确定等式，便宜；非凸理论（典型是整数）有时只能确定"x 等于 1 或 2 之一"，求解器只好分情况各试一遍，这就是整数约束求解常常更慢的一个根本原因。** 现代求解器（Z3、CVC5）并非逐字实现 1979 版 Nelson-Oppen，而是把理论组合融进 DPLL(T)：理论传播的等式直接走 CDCL 的学习/回溯机制，非凸带来的案例分裂交给 SAT 引擎去枚举，工程上更统一。

#### 来源与时效

- 一手：Nelson & Oppen, "Simplification by Cooperating Decision Procedures", ACM TOPLAS 1(2):245–257, 1979（组合方法、等式传播原论文）。核实 2026-07-30。
- 一手：de Moura & Bjørner, TACAS 2008（Z3 将理论组合融入 DPLL(T) 的模型-based 理论组合思路）。核实 2026-07-30。
- 教材交叉：Kroening & Strichman《Decision Procedures》2nd ed. 2016, §10（Nelson-Oppen、净化、凸性、稳定无限的系统阐述与前提条件）。
- 实证：z3-solver 5.0.0 @2026-07-30，UF+LIA 组合 `xi=yi ∧ g(xi)≠g(yi)` → unsat，与等式传播手推一致。
- 冲突/分歧提示：1979 原始 Nelson-Oppen 是"确定性等式传播 + 非凸案例分裂"的独立算法；主流实现（Z3/CVC5）改用与 DPLL(T) 融合的模型驱动组合——两者在**可判定的可靠性结论**上一致，仅**工程实现路径**不同，本报告两者都记，正文以融合式为现代基线。

---

## V10.5 sat / unsat / model / unsat-core：结果解读与用法

### V10.5.1 三种判定结果：sat / unsat / unknown

SMT 求解器对一个断言集合（assertions）的判定，SMT-LIB `check-sat` 命令定义了三种结果：

```
sat      ：存在一组取值让所有断言同时成立（公式可满足）。
unsat    ：不存在任何取值让所有断言同时成立（公式不可满足/矛盾）。
unknown  ：求解器无法在给定资源/理论可判定性下确定（既非确定 sat 也非确定 unsat）。
```

`unknown` 的出现有两类原因，初学者必须分清：一是**理论本身不可判定或半可判定**（如含量词的一阶逻辑、非线性整数算术 NIA），求解器可能给不出确定答案；二是**触发了超时/资源限制**。这也是为什么"SMT 求解器返回 unsat 才等于'证明了性质成立'"——在验证里，我们通常把"性质 P 成立"编码为"¬P 不可满足"，得到 unsat 才算证明通过；返回 sat 则给出一个反例；返回 unknown 则既没证成也没证伪，不能当作任何结论。

把 unknown 当成 sat 或 unsat 之一处理。正确做法是把它当第三种独立结果显式分支。z3 的 `Solver.check()` 返回值正是 `sat` / `unsat` / `unknown` 三者之一。

### V10.5.2 model：sat 时的满足赋值（反例/见证）

当结果为 sat，求解器能给出一个**模型**（model）——一组让所有断言为真的具体取值。模型是 SMT 在验证/测试里最有用的产物之一：

```
在符号执行（V7）中：model = 一组能走到目标路径的真实输入 → 直接变成测试用例。
在验证（V9）中：若把"¬(待证性质)"喂进去得到 sat，model 就是一个反例（counterexample），
              精确指出性质在什么输入下被违反。
```

本机 z3 实测，公式 `mx·mx = 9 ∧ mx > 0`（非线性整数）返回 `sat`，`model()` 给出 `mx = 3`，通过 `m[mx].as_long()` 取到 Python 整数 3。初学者要点：**model 只在 sat 时存在**——对 unsat 的求解调用 `model()` 会报错，对 unknown 也没有保证的模型。取值要通过求解器提供的求值接口（z3 里是 `model()` 加下标或 `model.eval()`），别自己猜。另外模型通常只是"某一个"满足解，不是全部解，也不保证唯一（V10.1.1 的例子恰好唯一是巧合）。

### V10.5.3 unsat-core：不可满足的"最小矛盾子集"

当结果为 unsat，求解器能给出**不可满足核**（unsat core）——原断言集合的一个**子集**，它本身已经不可满足。unsat-core 回答"到底是哪几条断言互相打架"，对调试大规模约束、定位规约错误、做基于核的抽象精化（如 CEGAR）极其有用。

用法上有个初学者必知的机制：要拿 unsat-core，得先给断言**打标签追踪**。z3 里用 `assert_and_track(约束, 标签布尔)` 或开 `unsat_core=True`，unsat 后 `unsat_core()` 返回参与矛盾的标签集合。本机 z3 实测：

```
断言 c1: x > 5
断言 c2: x < 3
断言 c3: x == 4
check() → unsat
unsat_core() → [c1, c2]
```

核只含 c1、c2——因为 x>5 与 x<3 已经直接矛盾，c3 与这个矛盾无关，被正确排除在核外。初学者两个易错点：其一，**默认返回的核不保证是"最小"（minimal/minimum）的**——它只保证"是一个不可满足子集"，要真正最小需额外做核最小化（z3 有相关策略/参数）；SMT-LIB 标准与 z3 文档均只承诺"an unsatisfiable subset"，不承诺基数最小。其二，**没打标签的断言不会出现在核里**，所以想让某条约束能进核，必须先 track 它。

### V10.5.4 其他产物与增量求解（proof / push-pop）

除四种主结果外，求解器还能提供两类进阶产物，此处点名不深挖：

```
proof（证明）：unsat 时输出一份可独立检查的不可满足性证明（z3 需开 proof 生成选项）。
              用于把"求解器说 unsat"降级为"可被第三方核验的证明"，提高可信度。
incremental（增量）：push / pop 维护断言栈，在已有断言基础上增删后重查，
              避免每次从头求解——符号执行逐路径累加约束时的标准用法。
```

验证工具链里，proof 用于对接可信基（把 SMT 的信任转移到一个小巧的证明检查器），increment 的 push/pop 则是上层工具（符号执行器、VC 生成器）反复调用同一求解器实例时的性能关键——每加一条路径约束就 push、回溯就 pop，复用求解器已建立的内部状态。

#### 来源与时效

- 一手：SMT-LIB Standard v2.7（2025-02-05，`check-sat` 的 sat/unsat/unknown 语义、`get-model`、`get-unsat-core`、`push`/`pop` 命令定义；unsat-core 明文只承诺"an unsatisfiable subset"，不承诺最小）。核实 2026-07-30。
- 一手：Z3 官方文档/指南 https://microsoft.github.io/z3guide/ （`check`/`model`/`unsat_core`/`assert_and_track`/proof 的 API 语义）。核实 2026-07-30。
- 教材交叉：Kroening & Strichman《Decision Procedures》2nd ed. 2016（结果语义与验证中"证明性质 = 证明其否定 unsat"的用法）。
- 实证（z3-solver 5.0.0 @2026-07-30，真实输出）：`mx·mx=9 ∧ mx>0` → sat，model `mx=3`；unsat-core 场景 {c1:x>5, c2:x<3, c3:x==4} → unsat，`unsat_core()`=`[c1, c2]`（c3 被正确排除）。
- 无冲突项。待核：z3 默认 unsat-core 是否已最小化——文档与实测均表明默认仅返回"某个"不可满足子集、非保证最小，需显式最小化策略（标「以文档为准，默认非最小」）。
