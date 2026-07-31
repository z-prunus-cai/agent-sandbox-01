# L5-06·大主题T3 简单类型 λ 演算与类型安全

> 基线 Py3.11.15/np2.4.6/gcc13.3 @2026-07-25 ｜ 核实日期：2026-07-30 ｜ 先修：T1（三种语义风格、结构归纳/规则归纳、小步操作语义与"卡住项"）、T2（无类型 λ 演算：抽象/应用、β 归约、值调用求值策略、代换 [x↦s]t）｜ 一手锚点：TAPL《Types and Programming Languages》(Pierce, MIT Press 2002) Part II，Ch.8（Typed Arithmetic Expressions）/ Ch.9（Simply Typed Lambda-Calculus）/ Ch.10（An ML Implementation of Simple Types）；交叉核对 Harper《Practical Foundations for Programming Languages》2nd ed（Cambridge 2016）Ch.6 Type Safety；证明方法回溯 Wright & Felleisen《A Syntactic Approach to Type Soundness》(Information and Computation 115(1), 1994) ｜ 成熟度：经典理论 GA/稳定（非演进快对象）

> 粒度判定：**1 份，不拆**。本大主题 5 个小主题（T3.1–T3.5）是一条单一主线——先在最小的算术语言上把"类型关系 + 类型安全"讲清（T3.1），再把这套机器搬到函数上得到 STLC（T3.2），随后证明 STLC 确实安全（T3.3），接着从逻辑视角重看这套类型系统（Curry-Howard，T3.4），最后落到"类型检查器怎么写"（T3.5）。同一套形式机制贯穿始终、无跨机制断裂，篇幅适中，按 report-format v3「默认 1 大主题 = 1 报告」不产 `-a/-b`。

**类型系统是一个只看程序文本、不运行程序，就能提前证明"这段代码运行时不会做出无意义操作"的轻量形式方法**。T2 里的无类型 λ 演算能写出 `true true` 这种"把布尔值当函数调用"的荒谬项——它不是值、也无法继续求值，只能"卡住"(stuck)。类型系统的全部意义，就是用一组语法制导的规则，在运行前把这类会卡住的项一律拒之门外；而"良类型的程序绝不会卡住"这一承诺，正是由 **Progress + Preservation** 两条定理精确刻画并证明的。本篇两定理只讲陈述与证明骨架/直觉，不铺完整归纳证明（那属专家纵深）。

本报告是 **L5-14 Rust 与内存安全 R12 RustBelt 类型安全证明的祖型**——RustBelt 证明的"含 unsafe 的 Rust 标准库是类型安全的"，用的正是 Progress+Preservation 这套语法方法的现代重型版本；也是 **L4-04 编译原理 C5 语义分析/类型检查** 的理论根（C5 讲"类型检查器在编译器里怎么实现"，本篇 T3.5 讲"为什么这么检查是对的"）。这些下游只在此点名，不展开。

---

## T3.1 类型化算术表达式

### T3.1.1 类型与类型关系 t : T

在讨论函数之前，TAPL Ch.8 先用一个只有布尔和自然数的最小语言把"类型"这件事讲透。语言的项 t 包括 `true`、`false`、`if t then t else t`、`0`、`succ t`、`pred t`、`iszero t`；类型 T 只有两个：`Bool` 和 `Nat`（§8.1）。**类型关系** `t : T` 读作"项 t 有类型 T"，它由一组**类型规则**（typing rules）归纳定义（§8.2）：

```
true  : Bool                                    (T-True)

false : Bool                                    (T-False)

     t1 : Bool    t2 : T    t3 : T
   ---------------------------------            (T-If)
      if t1 then t2 else t3 : T

0 : Nat                                          (T-Zero)

    t1 : Nat
   -----------                                   (T-Succ)
   succ t1 : Nat

    t1 : Nat
   -----------                                   (T-Pred)
   pred t1 : Nat

     t1 : Nat
   -------------                                 (T-IsZero)
   iszero t1 : Bool
```

每条规则横线上方是**前提**、下方是**结论**，读法是"若前提都成立，则结论成立"。一个项有类型，当且仅当能用这些规则搭出一棵**类型推导树**（typing derivation）。

类型规则就是"合法搭积木的说明书"。比如 `if` 规则要求三件事同时满足——条件 `t1` 必须是 `Bool`，两个分支 `t2`、`t3` 必须是**同一个**类型 T，整个 `if` 表达式才拿到那个共同类型 T。这解释了为什么 `if true then 0 else false` 没有类型：两分支一个 `Nat` 一个 `Bool`，凑不出共同的 T，说明书里没有任何一条能搭出它。注意这套判定完全是"看结构"的，不需要真去算 `t1` 到底是不是 `true`——这正是 T1 里"静态"的含义。

### T3.1.2 良类型项、"卡住"项与类型安全直觉

一个项若能被赋予某个类型，就称为**良类型的**（well-typed / typable）。类型安全（type safety，又称 type soundness）的直觉口号是 Milner 那句 **"well-typed programs cannot go wrong"**（良类型的程序不会出错）。这里"出错"有精确定义：在小步操作语义下，一个**不是值、又没有任何求值规则可用**的项叫**卡住项**（stuck term），"卡住"就是类型系统要杜绝的那种运行时错误。

`if 0 then true else false`。它不是值（if 还没算完），可求值规则里 `if` 的下一步要求条件先归约到 `true` 或 `false`，但条件 `0` 已经是一个值、且是 `Nat` 不是布尔值，没有规则能让这个 `if` 继续——它卡住了。这正是"把一个数当条件用"的荒谬，在无类型语言里只能等运行到这一步才暴露。而在类型层面，`0 : Nat` 不满足 T-If 要求的 `t1 : Bool`，所以 `if 0 then …` 从一开始就**没有类型**、被静态拒绝。

要建立的核心联系是：**"卡住"与"无类型"应当精确对应**。类型系统的价值不在于它拒绝了某些项，而在于它拒绝的恰好是（会导致）卡住的那些项，同时不误杀能正常算完的项。T3.3 的两条定理就是把这个"恰好对应"变成可证明的数学陈述。要提醒的易错点：卡住 ≠ 不停机。一个能一直算下去、永不终止的项并没有卡住（它总有下一步）；卡住特指"停在了一个非值、又无法继续的死局"。

### T3.1.3 算术语言上的 Progress 与 Preservation（预演）

TAPL §8.3 用标题 **"Safety = Progress + Preservation"** 第一次给出类型安全的两半，并在这个小算术语言上完整证明，作为 STLC 的预演。两条定理（原书 Theorem 8.3.2 与 8.3.3）陈述为：

```
Progress (Thm 8.3.2):
若 t 是良类型项（对某个 T 有 t : T），
则 t 要么是一个值，要么存在 t' 使 t → t'。

Preservation (Thm 8.3.3):
若 t : T 且 t → t'，则 t' : T。
```

把这两条连起来看就明白为什么它们合起来等于"不卡住"。Progress 说良类型项**当下**不会卡（要么已经是答案，要么还能走一步）；Preservation 说走完这一步得到的 `t'` **仍然良类型**（类型还不变）。于是对 `t'` 又能用 Progress……如此归纳下去，一个良类型项要么一路算到值、要么无限算下去，**永远不会中途卡在某个死局**。缺任何一条都不行：只有 Preservation 不能排除"良类型但当下就卡住"，只有 Progress 不能排除"走一步后变成一个会卡住的坏项"。

这个算术语言太小、没有变量和绑定，证明很短；把它整体搬到带函数、带变量作用域的 λ 演算上，才是 Ch.9 真正的技术内容——两条定理的形状一字不变，只是证明要多出处理"变量代换"的引理。

#### 来源与时效（本小主题）
- 锚点（一手承重）：TAPL Ch.8 §8.1（Types）、§8.2（The Typing Relation，T-True/T-False/T-If/T-Zero/T-Succ/T-Pred/T-IsZero 七条规则）、§8.3（Safety = Progress + Preservation，Theorem 8.3.2 Progress、Theorem 8.3.3 Preservation）；MIT Press 2002，正文与目录结构核实 2026-07-30（https://www.cis.upenn.edu/~bcpierce/tapl/ ）。
- 交叉印证（一手）：Harper PFPL 2nd ed（Cambridge 2016）Ch.6 "Type Safety" 以其核心表达式语言 E 给出同构的 Preservation 与 Progress 两条陈述，两书定理形状一致；"stuck term / 不会 go wrong"的表述两书一致。
- "well-typed programs cannot go wrong" 追溯 Milner 1978（TAPL §8.3 引用），此处作口号引用、非承重。
- 冲突/待核：无实质分歧。TAPL 称类型/求值为"typing relation / evaluation relation"，Harper 称"statics / dynamics"，仅术语差异，正文已点明对应。

---

## T3.2 简单类型 λ 演算（STLC）

### T3.2.1 函数类型 T₁→T₂ 与为何要给参数标类型

STLC（TAPL 记为 λ→）在无类型 λ 演算之上加入唯一一族新类型：**函数类型**（§9.1）。写法为

```
T ::= T → T          （类型的语法：一切类型都是"函数类型"套出来的）
```

`T₁ → T₂` 读作"从 T₁ 到 T₂ 的函数类型"，表示"接收一个 T₁ 类型的参数、返回一个 T₂ 类型结果"的函数。箭头是**右结合**的：`T₁ → T₂ → T₃` 意为 `T₁ → (T₂ → T₃)`。为了让类型检查能进行，STLC 在 λ 抽象里给绑定变量**显式标注类型**，即写 `λx:T₁. t`（这是 Church 风格；§9.6 讨论 Church-style 与 Curry-style 之别）。

为什么非要在 `λx:T₁` 上写那个 `T₁`？因为类型检查器面对 `λx. x` 时无从知道 `x` 该是什么类型——它可能是 `Bool→Bool` 上的恒等，也可能是 `Nat→Nat` 上的恒等。标注 `λx:Bool. x` 就把这个信息补齐，检查器才能自底向上算出整个函数的类型是 `Bool→Bool`。注意纯 STLC（只有 `→` 这一种类型构造子、没有任何基本类型）其实"空得没法写出有类型的封闭项"，所以教学上总会补至少一个基本类型（如 `Bool`）当地基，本篇沿用带 `Bool` 的版本。

### T3.2.2 类型环境 Γ（typing context）

有了变量，类型关系就不能再写成孤零零的 `t : T`，因为一个变量 `x` 的类型取决于它被绑定成什么。于是引入**类型环境**（typing context，又称 typing environment，记 Γ），它是一串"变量 : 类型"的假设（§9.2）。类型关系升级为**三元判断**：

```
Γ ⊢ t : T          读作："在假设 Γ 之下，项 t 有类型 T"
```

Γ 用 `Γ, x:T` 表示"在 Γ 基础上追加假设 x:T"，空环境记 ∅。约定同一环境里变量名互不相同（必要时靠 α-换名，见 T2）。

Γ 就是一张"当前作用域里每个自由变量各是什么类型"的登记表。当类型检查器钻进 `λx:Bool. …` 的函数体时，它就往登记表里加一条 `x:Bool`，出了这个函数体再把它移除——这正是词法作用域在类型层面的体现。判断 `Γ ⊢ t : T` 里的 Γ 记录的恰好是 `t` 的自由变量。当 `t` 是封闭项（无自由变量）时 Γ 为空，写成 `∅ ⊢ t : T` 或简写 `⊢ t : T`，退回到 Ch.8 那种无环境的形式。

### T3.2.3 STLC 的三条类型规则：T-Var、T-Abs、T-App

STLC 的类型关系由三条规则归纳定义（§9.2），分别对应 λ 演算的三种项——变量、抽象、应用：

```
      x : T ∈ Γ
     -----------                                   (T-Var)
      Γ ⊢ x : T

      Γ, x:T1 ⊢ t2 : T2
     -----------------------                        (T-Abs)
      Γ ⊢ λx:T1. t2 : T1 → T2

      Γ ⊢ t1 : T11 → T12     Γ ⊢ t2 : T11
     -------------------------------------          (T-App)
                Γ ⊢ t1 t2 : T12
```

给初学者逐条拆解。T-Var：变量的类型直接从环境里查表得到——`x` 的类型就是 Γ 登记的那个。T-Abs（最关键）：要给函数 `λx:T1. t2` 定型，就先假设参数 `x` 有标注类型 `T1`（把 `x:T1` 加进环境），在这个扩充环境下算出函数体 `t2` 的类型 `T2`；那么整个函数的类型就是 `T1 → T2`。T-App：要给应用 `t1 t2` 定型，`t1` 必须是个函数类型 `T11 → T12`，而实参 `t2` 的类型必须**恰好**是这个函数要的参数类型 `T11`（对得上）；对上了，整个应用的结果类型就是 `T12`。

一个最小的完整例子——恒等函数应用到一个布尔值：

```
(λx:Bool. x) true : Bool
```

它的类型推导是：由 T-Var 得 `x:Bool ⊢ x : Bool`，再由 T-Abs 得 `∅ ⊢ λx:Bool. x : Bool→Bool`；同时 `∅ ⊢ true : Bool`；两者用 T-App 拼起来（`Bool→Bool` 的参数类型 `Bool` 恰好匹配 `true` 的类型 `Bool`），得结果类型 `Bool`。最常见的初学者易错点是 T-App 里"参数类型必须相等"这一条——`t1 t2` 不是"随便什么都能应用"，实参类型和形参类型对不上（如把 `Bool→Bool` 的函数喂一个 `Nat`）就没有类型、被拒。

### T3.2.4 类型判定的两条基本性质：Inversion 与 Uniqueness

规则是"自底向上"造类型的，但证明和实现时常要反过来问："已知 `Γ ⊢ t : T`，能倒推出关于子项的什么信息？"这由**反演引理**（Inversion of the typing relation，Lemma 9.3.1）回答：因为每种项形状只匹配唯一一条类型规则，所以从结论能唯一地反推前提。例如

```
若 Γ ⊢ λx:T1. t2 : R，则 R 必形如 T1 → R2，且 Γ, x:T1 ⊢ t2 : R2。
若 Γ ⊢ t1 t2 : R，则存在 T11 使 Γ ⊢ t1 : T11 → R 且 Γ ⊢ t2 : T11。
```

由此还能证明**类型唯一性**（Uniqueness of Types，Theorem 9.3.3）：在给定 Γ 下，一个项至多有一个类型，且推导唯一。

反演引理是"类型规则可以倒着用"的许可证——它之所以成立，全靠 STLC 的规则是**语法制导**的（每种项形状对应且只对应一条规则，规则之间不重叠）。这既是后面两大定理归纳证明的常用步骤，也是 T3.5 里"类型检查器可以顺着语法结构一遍算出类型"的根本原因：类型唯一，检查器就不必回溯或猜测，直接递归即可。

#### 来源与时效（本小主题）
- 锚点（一手承重）：TAPL Ch.9 §9.1（Function Types，`T→T` 右结合）、§9.2（The Typing Relation：类型环境 Γ、判断 `Γ ⊢ t : T`、规则 T-Var / T-Abs / T-App）、§9.3（Lemma 9.3.1 Inversion、Theorem 9.3.3 Uniqueness of Types）、§9.6（Church-style vs Curry-style，绑定变量标注类型之由来）；MIT Press 2002，核实 2026-07-30。三条规则形状另与 TAPL 派生课件（Sagiv/TAU 基于 TAPL Ch.9 的讲义）逐条比对一致。
- 交叉印证（一手）：Harper PFPL 2nd ed Ch.8（函数）以其 statics 给出同构的三条规则（变量查环境、λ-引入 `→`、应用消去 `→`），环境记法与 TAPL 一致；Harper 明确区分 statics（类型规则）与 dynamics（求值），对应 TAPL 的 typing/evaluation relation。
- 规范一致性：Church-style（本篇，参数带类型标注）vs Curry-style（不标、靠类型重建）之别属风格差异，TAPL §9.6 两边都记；类型重建/推导本身是 T7 的主题，此处不展开。
- 冲突/待核：无实质分歧。

---

## T3.3 类型安全 = Progress + Preservation

### T3.3.1 类型安全的精确定义

**类型安全（type safety / type soundness）** 在 TAPL/Harper 的语法方法（syntactic approach）里被精确定义为：**良类型的程序永不卡住**（well-typed terms never get stuck）。它不是一句口号，而是被拆成两条可分别证明的定理——Progress 与 Preservation（TAPL §9.3 的 Theorem 9.3.5 与 9.3.9）——二者合起来经归纳得到安全性。

为什么需要"拆成两条"？因为"永不卡住"是一个关于**整条求值序列**的全局命题，直接证很难；而 Progress 和 Preservation 都是只关乎"当前一步"的**局部**命题，好证得多，再靠归纳把局部结论累积成全局保证。这种"化全局为一步 + 归纳"的思路，是本节要建立的核心方法论直觉。

### T3.3.2 Progress 定理与 Canonical Forms 引理

**Progress**（TAPL Theorem 9.3.5）陈述为：

```
Progress (Thm 9.3.5):
若 t 是封闭的良类型项（∅ ⊢ t : T），
则 t 要么是一个值，要么存在 t' 使 t → t'。
```

它保证良类型项当下不卡。证明的关键支撑是 **Canonical Forms 引理**（典范形式，Lemma 9.3.4）：

```
Canonical Forms (Lemma 9.3.4):
若 v 是值且 ∅ ⊢ v : Bool，则 v 是 true 或 false。
若 v 是值且 ∅ ⊢ v : T1 → T2，则 v 形如 λx:T1. t。
```

典范形式引理回答"某个类型的值长什么样"。它之所以要紧，是因为 Progress 证到应用 `t1 t2` 这一步时需要知道——"若 `t1` 已经是个函数类型的值，那它必然是一个 λ 抽象"，于是就能用 β 归约走下一步、不会卡。**Progress 必须限定在封闭项**（Γ 为空）：像 `x true` 这种带自由变量 `x` 的项既不是值、也无法求值（不知道 `x` 是什么），会卡住；但它在非空环境下是"良类型"的，所以定理只对无自由变量的完整程序成立——这是初学者最容易漏掉的前提。

### T3.3.3 Preservation 定理与代换引理

**Preservation**（TAPL Theorem 9.3.9，又称 subject reduction，主语归约）陈述为：

```
Preservation (Thm 9.3.9):
若 Γ ⊢ t : T 且 t → t'，则 Γ ⊢ t' : T。
```

它保证求值一步后类型不变。函数版 Preservation 的技术核心是**代换引理**（Preservation of types under substitution，Lemma 9.3.8）：

```
Substitution Lemma (Lemma 9.3.8):
若 Γ, x:S ⊢ t : T 且 Γ ⊢ s : S，则 Γ ⊢ [x↦s]t : T。
```

为什么函数版比算术版难，难就难在这个代换引理。β 归约 `(λx:S. t) s → [x↦s]t` 的动作是"把实参 `s` 代进函数体里替换 `x`"；要证类型不变，就得证明"用一个类型为 S 的 `s` 去替换类型假设为 S 的 `x`，替换结果 `[x↦s]t` 的类型和原来一样"。这个引理还需要两条更基础的结构引理垫底：**Permutation**（环境里假设换序不影响定型，Lemma 9.3.6）和 **Weakening**（往环境里加无关假设不影响定型，Lemma 9.3.7）。要提醒的易错点：Preservation 只说类型"保持"，不保证求值一步后类型变得"更精确"——在纯 STLC 里类型严格不变；到了带子类型的系统（T5），归约可能让类型变小，那时定理形式会松动。

### T3.3.4 证明结构骨架：结构/规则归纳与引理依赖

两条定理都用**对类型推导（或项结构）的归纳**来证，不逐项展开（专家纵深从略），但初学者应记住这张"骨架图"：证明按最后一条用到的类型规则分情形（T-Var / T-Abs / T-App 各一情形），每种情形要么直接闭合、要么调用一条引理并对子推导用归纳假设。引理的依赖链条是：

```
Inversion (9.3.1)
   ├─ 支撑 → Canonical Forms (9.3.4) → Progress (9.3.5)
   └─ 支撑 → Permutation (9.3.6) → Weakening (9.3.7)
                                       → Substitution (9.3.8) → Preservation (9.3.9)
```

这套"陈述两条一步定理 + 一串支撑引理 + 结构归纳"的组织方式，本身就是一个可反复套用的**证明模板**。TAPL 后续每加一个语言特性（引用、异常、子类型、多态……），都是重跑这套模板、补上新特性对应的情形与引理。这也正是为什么本篇是 **L5-14 R12 RustBelt 类型安全证明的祖型**——RustBelt（POPL'18）证明 Rust（含 unsafe 标准库）的安全性，用的就是这套 Progress+Preservation 语法方法的重型化、机械化版本（其安全性建立在 Iris 分离逻辑上的语义可定型之上）；此处点名不展开，细节归 L5-14。

### T3.3.5 语法方法的来历与"安全 ≠ 正确/停机"的边界

这套"Progress + Preservation"证类型安全的路子，学名**语法类型可靠性方法**（syntactic type soundness），系统化地由 **Wright & Felleisen（1994）** 提出，取代了更早期基于指称语义的可靠性论证；TAPL 与 Harper 都采用它，是当代类型系统元理论的默认工具。

给初学者必须澄清的三条边界。其一，类型安全**不等于逻辑正确**：类型对了，程序照样能算出错误答案（`λx:Nat. 0` 是良类型的，但它把任何输入都映成 0，未必是你想要的逻辑）；正确性要靠规约与验证（L6-02）。其二，**STLC 的类型安全不涉及停机**，但纯 STLC 恰好还额外享有**强正规化**（strong normalization，TAPL Ch.12：良类型项一定终止）——注意这是 STLC 特有的奢侈性质，一旦加入一般递归（`fix`，T4.3）就丢失，那时良类型项可以不停机，但**仍然类型安全**（不停机不算卡住）。其三，"不卡住"只覆盖类型层面的错误，不覆盖被显式建模成正常结果的运行时状况（如除零、异常）——那些要么被排除在语言外、要么像 T4.5 那样专门类型化。

#### 来源与时效（本小主题）
- 锚点（一手承重）：TAPL Ch.9 §9.3——Lemma 9.3.1 Inversion、Lemma 9.3.4 Canonical Forms、Theorem 9.3.5 Progress、Lemma 9.3.6 Permutation、Lemma 9.3.7 Weakening、Lemma 9.3.8 Substitution（Preservation of types under substitution）、Theorem 9.3.9 Preservation；§8.3 的 8.3.2/8.3.3 为其算术版原型；TAPL Ch.12 强正规化。MIT Press 2002，核实 2026-07-30。定理陈述另与基于 TAPL Ch.9 的讲义（TAU/Sagiv）逐条核对：Progress 需"封闭项"限制、Canonical Forms 两条、Preservation/Permutation/Weakening/Substitution 四引理均一致。
- 交叉印证（一手）：Harper PFPL 2nd ed Ch.6 "Type Safety" 以 Preservation + Progress 两节给出同一结构的证明，并同用 Canonical Forms 引理；Harper 亦称 Preservation 为 subject reduction。
- 方法溯源（一手）：Wright & Felleisen, "A Syntactic Approach to Type Soundness", Information and Computation 115(1):38–94, 1994——Progress+Preservation 语法方法的系统化出处。机械化印证：Software Foundations（PLF 卷）StlcProp 章节以 Coq 形式化了 STLC 的 progress 与 preservation，与本节陈述一致（⚠教材/半一手，作佐证不承重）。
- 下游点名：RustBelt（Jung et al., POPL'18）为本方法的现代重型化，细节归 L5-14 R12，不展开。
- 冲突/待核：无实质分歧；两书均采语法方法，仅术语（typing/evaluation vs statics/dynamics、Preservation vs subject reduction）不同，正文已标。

---

## T3.4 Curry-Howard 同构

### T3.4.1 命题即类型（propositions as types）

**Curry-Howard 同构**（TAPL §9.4，又称 propositions-as-types、公式即类型）指出：**类型系统与构造性逻辑是同一个东西的两种读法**。同一套形式对象，逻辑学家读成"命题与证明"，程序员读成"类型与程序"。TAPL 给出的对应表核心几行为：

```
逻辑（构造性/直觉主义）          类型系统（STLC + 积类型）
------------------------        --------------------------
命题 P                     ↔    类型 P
蕴含 P ⊃ Q                 ↔    函数类型 P → Q
合取 P ∧ Q                 ↔    积（pair）类型 P × Q
命题 P 的一个证明            ↔    类型 P 的一个项（程序）
命题 P 可证                 ↔    类型 P 可被"住"(inhabited，存在该类型的项)
证明的化简（normalization） ↔    项的求值（evaluation）
```

拿蕴含这一行体会最清楚。"从 P 能推出 Q"的一个构造性证明，本质上是一个**把 P 的证明加工成 Q 的证明的过程**——这不正是一个"输入 P、输出 Q"的函数吗？所以逻辑里的 `P ⊃ Q` 和类型里的 `P → Q` 是同一个东西。同理，"同时证明了 P 和 Q"就是"手里既有一个 P 的证明、又有一个 Q 的证明"，打包成一个序对，正是 `P × Q`。

### T3.4.2 证明即程序、归约即证明化简

同构的另一半是动态的一面：**证明即程序（proofs as programs），证明的化简即程序的求值**。逻辑里把一个绕圈子的证明"约简"成更直接的证明（cut elimination / proof normalization）这个操作，在类型侧恰好对应 β 归约——把 `(λx:P. t) s` 算成 `[x↦s]t`。

这意味着"一个类型 P 有没有对应的程序（该类型被住）"等价于"命题 P 是不是可证"。一个没有任何封闭项能拥有的类型，对应一个不可证的命题。比如在纯 STLC 对应的逻辑里，类型 `A → A` 总能被 `λx:A. x`（恒等函数）住，对应"P ⊃ P 恒真"这个逻辑重言式——恒等函数就是"P 蕴含 P"的证明。反过来，这也给了类型系统一个深刻的解读：类型检查器接受你的程序，等于逻辑地验证了"你的程序是它类型所对应命题的一个合法证明"。

这个对应精确地是与**构造性/直觉主义逻辑**（而非经典逻辑）之间的同构——经典逻辑里的排中律 `P ∨ ¬P`、双重否定消去在纯 STLC/直觉主义里并不自动成立，需要额外的控制算子（如 call/cc）才对应得上，属专家纵深，此处不展开。

### T3.4.3 同构的意义与向依赖类型的延伸

Curry-Howard 不是一个孤立的巧合，而是贯穿整个类型理论的组织原则：类型系统每强大一点，对应的逻辑就强大一点。STLC + 积/和类型对应命题逻辑（∧/∨/⊃）；加上全称多态（System F，T8）对应二阶逻辑；再往上，**依赖类型**（把类型索引到值上）对应**一阶谓词逻辑**（∀/∃ 量词），这正是 Coq、Agda、Lean 这类**证明助手**的理论基础。

这条线索把"写程序"和"证数学"统一了起来——在依赖类型的证明助手里，"证明一个定理"字面上就是"写出一个具有对应类型的程序"，而类型检查器就是证明检查器。本报告只作"一瞥"，把命题即类型这颗种子埋下；它的深化（依赖类型即命题、可信基 TCB、机械化证明）交由 **L6-02 程序分析与形式验证 V11（交互式定理证明）** 展开，此处点名不展开。这也与 T3.3.4 呼应：类型安全证明之所以能被 Coq 机械化（Software Foundations），底层靠的正是 Curry-Howard。

#### 来源与时效（本小主题）
- 锚点（一手承重）：TAPL Ch.9 §9.4 "The Curry-Howard Correspondence"——命题/类型、蕴含/函数、合取/积、可证/被住、证明化简/求值的对应表；MIT Press 2002，核实 2026-07-30。
- 溯源（一手）：Howard, "The formulae-as-types notion of construction"（1969 手稿，1980 出版于 To H. B. Curry 文集）为同构的原始出处，TAPL §9.4 引用；此为承重来源的历史锚。
- 交叉印证（一手/权威综述）：Wadler, "Propositions as Types", Communications of the ACM 58(12):75–84, 2015——对同构（含 STLC↔命题逻辑、归约↔证明化简、向多态与依赖类型延伸）的权威系统综述，与 TAPL 表述一致。Harper PFPL 亦贯穿命题即类型视角。
- 边界标注：与直觉主义逻辑（非经典逻辑）同构，排中律需额外控制算子，正文已点明、不展开。
- 下游点名：依赖类型/证明助手细节归 L6-02 V11，不展开。
- 冲突/待核：无实质分歧。

---

## T3.5 STLC 的实现：类型检查器

### T3.5.1 从"类型关系"到"类型算法"：为什么能一遍算出来

TAPL Ch.10 把 Ch.9 的类型规则落成一个真正能跑的**类型检查器**。核心观察是：STLC 的类型规则是**语法制导**的（每种项形状唯一对应一条规则，见 T3.2.4），再加上类型唯一性（Theorem 9.3.3），使得"给定 Γ 和 t、求 T"这件事可以由一个**沿语法结构递归、无需回溯或猜测**的函数完成。换句话说，声明式的类型关系 `Γ ⊢ t : T` 直接读作了一个确定性算法。

类型规则本来是"关系"（可能一对多、可能要搜索），但 STLC 的规则恰好"一对一且无重叠"，于是关系退化成了函数——检查器看项的最外层是变量/抽象/应用，就知道该套哪条规则、该递归检查哪些子项，一路算下去。这就是"类型检查在 STLC 里是可判定的、且高效"的根本原因（对比 T8 System F 的类型重建不可判定，正是因为规则不再这么"乖"）。

### T3.5.2 typeof：把三条规则翻译成一个递归函数

Ch.10 §10.3 的类型检查器就是一个 `typeof(ctx, t)` 函数，**逐条把类型规则翻译成代码**：变量查环境（T-Var）、抽象往环境加绑定再递归查函数体（T-Abs）、应用递归查两个子项并核对参数类型相等（T-App）。规则里的"前提成立"变成代码里的"递归调用成功且类型相等检查通过"，"无法定型"变成抛出类型错误。

本机实证（可选·已做，加固 T3.2 规则到算法的对应）。用 ~40 行 Python 把三条规则加 `if`/`Bool` 实现为 `typeof`，在**不运行被检查项**的前提下给出类型或拒绝：

```
$ python3 stlc_check.py          # 基线 Py3.11.15 @2026-07-30
id_bool : (Bool->Bool)                       # λx:Bool. x         —— T-Abs
app_ok  : Bool                               # (λx:Bool. x) true  —— T-App 匹配成功
true true -> REJECTED: applying a non-function        # T-App：t1 不是函数类型 → 拒
if-mismatch -> REJECTED: branches differ              # T-If：两分支类型不等 → 拒
```

对应的检查器主体（三条规则一一对应，节选）：

```python
def tc(ctx, t):
    if t[0] == 'var':                       # T-Var：查环境
        return ctx[t[1]]
    if t[0] == 'abs':                       # T-Abs：加 x:T1 入环境，递归查体
        _, x, T1, body = t
        return ('->', T1, tc({**ctx, x: T1}, body))
    if t[0] == 'app':                       # T-App：t1 须为函数，且实参类型匹配
        _, t1, t2 = t
        Tf, Ta = tc(ctx, t1), tc(ctx, t2)
        if Tf[0] != '->':  raise TypeError("applying a non-function")
        if Tf[1] != Ta:    raise TypeError("arg type mismatch")
        return Tf[2]
```

把这段代码和 T3.2.3 的三条规则并排看，几乎是逐行翻译——`tc` 的三个分支就是 T-Var/T-Abs/T-App，`app` 分支里的两个 `if` 就是 T-App 前提里"`t1` 是函数类型""实参类型 = 形参类型"两个条件。第 4 行"往环境加 `x:T1` 再递归"正是 T-Abs 里 `Γ, x:T1 ⊢ t2 : T2` 的直接实现，函数返回后该绑定自动随栈帧消失，天然实现了词法作用域。上面 `true true` 和分支类型不匹配两例被拒，实机复现了"良类型才通过、会卡住的项被静态挡下"。

### T3.5.3 检查 vs 综合、上下文表示与工程要点

实现上有几个初学者该知道的要点。其一，**类型综合（synthesis）与类型检查（checking）**：上面的 `typeof` 是"综合"——自底向上算出类型；另一种风格是"检查"——给定期望类型、自顶向下核对，二者结合成**双向类型检查**（bidirectional typing），能减少标注负担，是现代实现的主流（属工程演进，细节不展开）。其二，**上下文的表示**（§10.1）：Γ 在教学实现里常用关联列表/字典（如上例的 `dict`），工程实现里为效率会用更结构化的符号表——这正好接 **L4-04 编译原理 C5.1 符号表与作用域**。其三，STLC 类型检查是**可判定且线性/近线性**的（一遍遍历），不需要 T7 那种合一与约束求解——那是类型重建（无标注推导）才需要的重武器。

本篇讲的是"检查器为什么这么写是对的"（元理论），"检查器在真实编译器里怎么和词法/语法分析拼装、符号表怎么组织、错误怎么报"属 L4-04 C5 的工程视角，两者互补、不重复。要避免的误解是把 STLC 的"轻松可判定"外推到所有类型系统——一旦上多态（T8）、子类型（T5）、依赖类型，类型检查/重建的难度会急剧上升甚至不可判定，本篇的简单递归检查器只是最幸运的起点。

#### 来源与时效（本小主题）
- 锚点（一手承重）：TAPL Ch.10 "An ML Implementation of Simple Types"——§10.1 Contexts（环境表示）、§10.2 Terms and Types、§10.3 Typechecking（`typeof` 递归函数，逐条对应 T-Var/T-Abs/T-App）；语法制导 + 类型唯一（Theorem 9.3.3）保证算法确定性。MIT Press 2002，核实 2026-07-30。
- 本机实证（可选·已做）：Python 3.11.15 实现 STLC `typeof`（三条规则 + if/Bool），对 `λx:Bool.x`、`(λx:Bool.x) true` 给出类型，对 `true true`、分支类型不等给出拒绝，命令与真实输出见正文；基线 Py3.11.15/np2.4.6/gcc13.3 @2026-07-25，实证机 @2026-07-30。此为加固补充，不替代多来源比对。
- 交叉印证（一手）：Harper PFPL 2nd ed 关于 statics 可判定性的讨论、以及 STLC 类型检查沿语法结构确定进行，与 TAPL 一致；双向类型检查（bidirectional typing）为现代实现主流风格（属工程演进，作背景不承重）。
- 下游点名：符号表/作用域工程实现归 L4-04 C5.1；类型重建（无标注、合一）归本课 T7；均不展开。
- 冲突/待核：双向类型检查等具体实现风格随语言/工具演进，本篇不锚具体版本、不编造默认；核心算法（语法制导递归）为稳定经典结论。
