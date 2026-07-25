# Round 3a · 大主题分解 · 第 8 组「语言 / 编译 / 验证」

> 基线 Py3.11.15/np2.4.6/gcc13.3 @2026-07-25（本组补充：clang/LLVM 18.1.3 · rustc/cargo 1.94.1 · z3-solver 5.0.0.0 需 pip 装）。
> 目标：把组内每门课拆成**查全的大主题清单**（教学单元级，覆盖全、少重叠、按教学序），每课锚定权威教材目录 / 官方 syllabus。
> 组内防重分工：**编译 L4-04 = 构造 / PL 理论 L5-06 = 理论 / 程序分析 L6-02 = 验证 / Rust L5-14 = 工程落地**。同一概念（类型、生命周期、数据流、别名）在四门里从不同高度切，跨课重叠列显式点名。
> 贯穿钉子：**Rice 定理**——非平凡语义性质在图灵完备语言上不可判定；L6-02 各法皆为某方向近似，Rust 借用检查/类型系统是"可判定但保守"的语法级近似。
> 本组课程：**L4-04 编译原理**、**L5-06 编程语言理论**、**L6-02 程序分析与形式验证**、**L5-14 Rust 与内存安全**。

---

## 权威锚点总表（URL + 版本 + 核实 2026-07-25）

| 课 | 一手锚点 | 版本 / 定位 | URL | 核实 |
|----|----------|-------------|-----|------|
| L4-04 编译 | 龙书 *Compilers: Principles, Techniques, and Tools*（Aho/Lam/Sethi/Ullman） | **2nd ed, 2006**；正文 12 章 + 附录 A/B | https://suif.stanford.edu/dragonbook/ · https://www.informit.com/store/compilers-principles-techniques-and-tools-9780321486813 | ✅ 2026-07-25 |
| L4-04 编译 | Stanford **CS143** Compilers 官方课纲 | 词法→解析→语义/类型→运行期→代码生成→（局部/全局）优化→寄存器分配→GC→JIT；Cool 语言 5 次编程作业 | https://web.stanford.edu/class/cs143/ | ✅ 2026-07-25 |
| L5-06 PL 理论 | **TAPL** *Types and Programming Languages*（Pierce） | **MIT Press 2002**；6 部 32 章（已核对完整目录 PDF） | https://www.cis.upenn.edu/~bcpierce/tapl/ | ✅ 2026-07-25 |
| L6-02 分析/验证 | **PPA** *Principles of Program Analysis*（Nielson/Nielson/Hankin） | Springer **1999（corrected 2005）**；6 章（分析半场骨架） | https://link.springer.com/book/10.1007/978-3-662-03811-6 | ✅ 2026-07-25 |
| L6-02 分析/验证 | 验证半场补锚：Clarke/Grumberg/Peled *Model Checking*；*Software Foundations*（Hoare/Coq）；de Moura & Bjørner *Z3*(TACAS'08) | 模型检验 / 演绎验证 / SMT——PPA 不覆盖 | https://softwarefoundations.cis.upenn.edu/ · https://github.com/Z3Prover/z3 | ✅ 2026-07-25 |
| L5-14 Rust | **The Rust Programming Language**（"the book"，Klabnik/Nichols/Krycho） | 活文档：页面声明"assumes **Rust 1.90.0**（2025-09-18）+ **edition 2024**"；正文 21 章 + 附录（本机 rustc **1.94.1** 更新，见留白） | https://doc.rust-lang.org/book/ | ✅ 2026-07-25 |
| L5-14 Rust | **The Rust Reference**（unsafe/语义规范）、**The Rustonomicon**（unsafe 一手）、**RustBelt**(Jung 等 POPL'18，形式基础) | 活文档，对齐本机 rustc 1.94.1 | https://doc.rust-lang.org/reference/ · https://doc.rust-lang.org/nomicon/ | ✅ 2026-07-25 |

> ⚠ 时效/版本留白：① Rust "the book" 页面对齐 **1.90.0**，本机 rustc 为 **1.94.1**——R4 取证以本机版本为准，二者 delta 需现查不凭记忆。② **Polonius 是否已成默认借用检查器待核实**（基线仍以 NLL 为主，R4 查证）。③ PPA 只覆盖"程序分析"半场（静态分析），"形式验证"半场（模型检验/演绎验证/定理证明/SMT）用第二组锚点补齐——L6-02 是"分析+验证"合课，两半场都要查全。

---

## L4-04 编译原理 —— 构造视角（锚：龙书 2nd ed 2006 · Stanford CS143）

> 编号按龙书章序 + CS143 讲序（前端→中端→后端→优化）。防重：讲"怎样正确地翻译"；"类型系统为何可靠"下沉 L5-06、"程序是否满足性质"归 L6-02。

| 编号 | 大主题 | 一句话范围 | 来源锚点 | 跨课重叠提示 |
|------|--------|-----------|----------|--------------|
| C1 | 编译器整体结构与流水线 | 前端/中端/后端分工、多趟结构、分析-综合模型、语法制导翻译器缩影 | 龙书 Ch.1–2；CS143 intro | — |
| C2 | 词法分析 | 正则 → NFA → DFA、最长匹配、词法生成器 | 龙书 Ch.3；CS143 Lexical | 承 **L3-05** 自动机（这里只用不证） |
| C3 | 语法分析 | 上下文无关文法、自顶向下 LL / 递归下降 vs 自底向上 LR/LALR、冲突与错误恢复 | 龙书 Ch.4；CS143 Parsing | 承 L3-05 CFG；组内选型表 1-A |
| C4 | 语法制导翻译与 AST | 属性文法、S/L-属性、语义动作、抽象语法树构建 | 龙书 Ch.5；CS143 SDT | — |
| C5 | 语义分析与类型检查 | 符号表、作用域、类型规则的"怎么查"、类型转换 | 龙书 Ch.6.3–6.5；CS143 Semantic Analysis & Type Checking | **为何可靠→L5-06**；HM 推导算法根在 L5-06 |
| C6 | 中间代码生成 | 三地址码、SSA、AST→IR；以 LLVM IR 为一手实物（`clang -S -emit-llvm`） | 龙书 Ch.6；CS143 IR | IR 上的分析与 **L6-02** 数据流同源 |
| C7 | 运行时环境 | 栈帧布局、调用约定、参数传递、堆与自动内存管理/GC | 龙书 Ch.7；CS143 Run-time/GC | 承 **L3-03** 汇编 ABI、**L3-02** 组成（不重复） |
| C8 | 目标代码生成 | 指令选择、寄存器分配（图着色）、指令调度 | 龙书 Ch.8；CS143 Code Gen / Register Allocation | 交 L3-03 机器级 |
| C9 | 机器无关优化与数据流分析 | 基本块/流图、常量折叠/传播、死代码、公共子表达式、循环优化（LICM/展开）；数据流框架 | 龙书 Ch.9；CS143 Local/Global Optimization | **与 L6-02 数据流分析同一数学**（此处目标=生成快代码，L6-02 目标=对性质给保证） |
| C10 | 高级优化专题 | 指令级并行/软件流水、并行与局部性（循环变换）、过程间分析 | 龙书 Ch.10–12 | 交 **L5-05** 体系结构；过程间分析交 L6-02 |

**建议大主题数：K = 10**

---

## L5-06 编程语言理论 —— 理论视角（锚：TAPL, MIT Press 2002；6 部 32 章）

> 编号严格贴 TAPL 六部结构。防重：研究语言**本身**的性质（元层）；具体翻译实现归 L4-04、具体验证工具归 L6-02、语言工程落地归 L5-14。

| 编号 | 大主题 | 一句话范围 | 来源锚点 | 跨课重叠提示 |
|------|--------|-----------|----------|--------------|
| T1 | 预备与语义风格 | 类型系统作用、归纳法（结构/规则归纳）、操作/指称/公理三种语义风格 | TAPL Ch.1–2 + §3.4 | 公理语义是通向 **L6-02** Hoare 逻辑的桥 |
| T2 | 无类型 λ 演算 | β 归约、Church 编码、Y 组合子、Church-Rosser、无名（de Bruijn）表示 | TAPL Part I（Ch.3–7） | 承 **L3-05** 可计算性；喂 L4-04 求值语义 |
| T3 | 简单类型 λ 演算与类型安全 | STLC、类型关系、**Safety = Progress + Preservation**、Curry-Howard | TAPL Part II（Ch.8–10） | type-safety 证明骨架是 L5-14 RustBelt 的祖型 |
| T4 | 简单类型系统的扩展 | 积/和/记录/变体、引用与存储类型、异常、一般递归、列表 | TAPL Ch.11–14 | 代数数据类型工程化→**L5-14** struct/enum |
| T5 | 子类型 | subsumption、子类型关系、算法子类型、面向对象编码、Featherweight Java | TAPL Part III（Ch.15–19） | 交 **L3-04** OOP 范式 |
| T6 | 递归类型 | 等递归 vs 同构递归、μ-类型、归纳与余归纳 | TAPL Part IV（Ch.20–21） | — |
| T7 | 类型重建与 let-多态 | 约束式类型、合一、principal type、Algorithm W（Hindley-Milner） | TAPL Ch.22 | **公共理论**：喂 L4-04 语义分析、L5-14 局部类型推导 |
| T8 | 全称/存在多态 | System F、参数化多态、参数性、存在类型与数据抽象 | TAPL Ch.23–25 | 泛型理论根→**L5-14** 泛型/trait |
| T9 | 有界量化 | F<:、kernel vs full F<:、Full F<: 不可判定性、界与子类型交互 | TAPL Ch.26–28 | 受限多态→L5-14 trait bound |
| T10 | 高阶系统 | 类型算子与 kinding、Fω 高阶多态、高阶子类型、依赖类型一瞥 | TAPL Part VI（Ch.29–32） | 依赖类型接 **L6-02** 证明助手 |

**建议大主题数：K = 10**

---

## L6-02 程序分析与形式验证 —— 验证视角（锚：PPA, Springer 1999/2005；补 Model Checking / Software Foundations / Z3）

> 编号 = "世界观 → 分析半场（PPA Ch.2–6）→ 验证半场（补锚）"。防重：与 L4-04 静态检查区别在**目标**——编译器分析求"够用且快"，L6-02 求"对某性质给出保证或反例"。全部挂 Rice 定理。

| 编号 | 大主题 | 一句话范围 | 来源锚点 | 跨课重叠提示 |
|------|--------|-----------|----------|--------------|
| V1 | 不可判定性与近似框架 | Rice 定理、over-/under-approx、可靠 vs 完备、为何一切皆近似（全课世界观） | PPA Ch.1 | 承 **L3-05** 计算理论 |
| V2 | 数据流分析 | 格与单调框架、MFP vs MOP、到达定值/活跃变量/可用表达式、不动点 | PPA Ch.2 | **与 L4-04 优化同一数学**（目标不同） |
| V3 | 约束式分析 | 控制流分析（CFA）、集合约束、约束求解 | PPA Ch.3 | — |
| V4 | 抽象解释 | Galois 连接、抽象域（区间/符号/多面体）、不动点迭代 + widening/narrowing | PPA Ch.4；Cousot&Cousot POPL'77 | 手写区间分析（本机实证） |
| V5 | 类型与效果系统 | 注解类型系统、effect 推断、把分析编码进类型 | PPA Ch.5 | **交 L5-06** 类型理论（此处作分析工具用） |
| V6 | 分析求解算法 | worklist 算法、方程/不等式系统求解、各法算法共性 | PPA Ch.6 | — |
| V7 | 符号执行 | 路径公式、路径爆炸、可达性、约束交 SMT | King 1976；KLEE | 用 V10 SMT 作后端 |
| V8 | 模型检验 | 时序逻辑 LTL/CTL、状态爆炸、显式/符号（BDD/BMC）、反例 | Clarke/Grumberg/Peled *Model Checking* | 工具 SPIN/TLA+ 待装，先概念+手算 |
| V9 | 演绎验证：Hoare 逻辑与最弱前条件 | Hoare 三元组 `{P}C{Q}`、WP 演算、循环不变式、验证条件生成 | Software Foundations；承 T1 公理语义 | **接 L5-06** 公理语义 |
| V10 | SMT 求解引擎 | DPLL(T)、理论组合（线性算术/位向量/数组）、sat/unsat/model | de Moura & Bjørner Z3 TACAS'08；z3-solver 5.0.0.0 | 贯穿 V7/V9 的共享后端（本机可跑） |
| V11 | 交互式定理证明 | 证明助手（Coq/Isabelle）、依赖类型即命题、归纳证明、机器化可靠性 | *Software Foundations*（Coq 一手） | 接 L5-06 依赖类型；coq 未预装→或降概念+只读 |

**建议大主题数：K = 11**

---

## L5-14 Rust 与内存安全 —— 工程落地视角（锚：The Rust Programming Language book · Reference · Rustonomicon · RustBelt）

> 编号按 the book 讲序（工具→所有权/借用/生命周期→类型→并发→unsafe→形式）。防重：Rust 只讲"语言如何**内建**这层保证"；类型理论本身→L5-06，通用验证→L6-02。

| 编号 | 大主题 | 一句话范围 | 来源锚点 | 跨课重叠提示 |
|------|--------|-----------|----------|--------------|
| R1 | Rust 定位、工具链与工程骨架 | cargo/rustc、edition 2024、模块/包/crate、自动化测试、文档 | the book Ch.1–2,7,11,14 | 版本锚：书 1.90.0 / 本机 rustc 1.94.1 |
| R2 | 所有权与 move 语义 | 栈/堆、Copy vs move、Drop、RAII；仿射类型的工程化 | the book Ch.4 | **理论根→L5-06** 线性/仿射类型；对比 C++ RAII |
| R3 | 借用、引用与借用检查器 | `&`/`&mut`、**别名异或可变**、借用检查（NLL / Polonius 区域推断）、E0502 类报错 | the book Ch.4；Reference | **=一种编译期别名/生命周期分析→L6-02**（可判定保守近似） |
| R4 | 生命周期与区域推断 | lifetime 标注、省略规则、`'static`、区域推断 | the book Ch.10 | 区域类型理论→L5-06 |
| R5 | 代数数据类型与模式匹配 | struct/enum、`match`、穷尽性检查、`Option`/`Result` 建模 | the book Ch.5–6,18 | 积/和类型工程化→**L5-06** T4 |
| R6 | 泛型、trait 与类型推导 | 泛型、trait/trait 对象、trait bound、局部类型推导 | the book Ch.10 | 受限多态/类型类根→**L5-06** T7–T9 |
| R7 | 错误处理 | `Result`/`Option`、`?` 运算符、panic vs 可恢复错误 | the book Ch.9 | — |
| R8 | 智能指针与内部可变性 | `Box`/`Rc`/`Arc`/`RefCell`/`Cell`、内部可变性、`Deref`/`Drop`、循环引用 | the book Ch.15 | 把编译期规则换成运行期检查的逃生阀 |
| R9 | 无畏并发 | `Send`/`Sync`、线程/消息(channel)/共享状态(Mutex)、**编译期消除数据竞争** | the book Ch.16 | **交 L4-05** 并发（此处=类型系统层拦截） |
| R10 | 异步编程 | `async`/`await`、`Future`、执行器、流 | the book Ch.17 | 演进较快，R4 硬标版本 |
| R11 | unsafe 与安全边界 | 裸指针、`unsafe` 语义、不变式义务转移、FFI；安全抽象封装 | the book Ch.20；**Rustonomicon** | 把证明义务转回程序员的显式逃生舱 |
| R12 | 形式基础与保证 | RustBelt 机器化可靠性证明、`unsafe` 库的合理性 | RustBelt POPL'18 | **四门汇合点**：回接 L5-06 类型安全 + L6-02 验证 |

**建议大主题数：K = 12**

---

## 组内查全性与防重自检

- **四课分工无系统性漏项**：编译（构造，龙书 12 章全覆盖）、PL 理论（理论，TAPL 6 部全覆盖）、分析+验证（PPA 5 类分析 + 模型检验/演绎/定理证明/SMT 补齐两半场）、Rust（落地，the book 主线 + Reference/Nomicon/RustBelt）。
- **最强跨课缝合点**：数据流分析（C9↔V2，同数学异目标）、类型推导 HM（C5←T7→R6）、类型安全证明（T3→R12 via RustBelt）、公理语义（T1→V9）、别名/生命周期分析（R3/R4 = L6-02 静态分析的工程内建）。这些点在 R4 报告中**显式交叉引用、单侧展开**，防重复。
- **实证可行性分层**（承 round2 附录，已各跑通一次）：L4-04 clang/LLVM 全阶段 ✅；L5-14 rustc 报错即教材 ✅；L6-02 仅 SMT(z3) ✅，模型检验/定理证明工具需装或降级为概念+只读；L5-06 纯 Python 求值器/合一算法 ✅。

> **组内四课总计 T = 10 + 10 + 11 + 12 = 43 个大主题。**
