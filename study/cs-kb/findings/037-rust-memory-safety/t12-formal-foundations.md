# L5-14·大主题R12 形式基础与保证

> 基线 Py3.11.15/np2.4.6/gcc13.3 @2026-07-25（+ rustc/cargo 1.94.1）｜核实日期：2026-08-01｜先修：R2 所有权与 move、R3 借用与借用检查器、R4 生命周期、R11 unsafe 与安全边界；理论侧建议先了解简单类型 λ 演算的类型安全（→L5-06 T3）与分离逻辑/机器化证明的基本概念（→L6-02）｜一手锚点：RustBelt「Securing the Foundations of the Rust Programming Language」Jung, Jourdan, Krebbers, Dreyer，Proc. ACM Program. Lang. 2, POPL, Article 66（2018 年 1 月，34 页）https://plv.mpi-sws.org/rustbelt/popl18/ ；Iris / lambda-rust Coq 开发库 https://gitlab.mpi-sws.org/iris/lambda-rust ；The Rustonomicon（unsafe 库合理性背景）https://doc.rust-lang.org/nomicon/ ｜成熟度：学术成果按发表版本记（RustBelt 针对 2017–2018 年的 Rust 语义子集）；与本机 rustc 1.94.1 语言现状的 delta 属 ⚙演进快·锚 POPL'18·随时变，现查不外推｜本大主题为形式基础，无本机实证（rustc 复现不适用）

这一章讲的是 Rust 安全承诺背后的"数学证明"那一层。前面几章（R2–R11）讲的是编译器怎么用所有权、借用、生命周期挡住内存错误；但有两个问题一直悬着：第一，编译器自己遵循的这套规则真的能保证"安全代码永远不会出未定义行为"吗，还是只是经验上没出事？第二，标准库里到处是 unsafe（`Vec`、`Rc`、`Mutex`、`RefCell` 内部都绕过了借用检查器），这些 unsafe 会不会悄悄把安全承诺打穿？RustBelt 这项工作就是用一台"证明机器"（Coq）把第一个问题证成了定理，并给出一个框架回答第二个问题。

本报告面向初学者，只讲到"RustBelt 证明了什么、用了哪些工具、为什么这件事重要"的层面，不深入分离逻辑的推理规则或证明细节。凡涉及具体论文结论一律按发表版本（POPL'18）记录，不外推到本机 rustc 1.94.1 的当前语言；两者的语言差异（新增特性、语义调整）属于随版本演进的部分，需现查、不凭记忆。

本大主题是"四门课的汇合点"：Rust 工程课（L5-14）在这里回接编程语言理论课的类型安全（L5-06）与形式验证课的机器化证明（L6-02）。R12.4 会把这些交叉引用逐条点题。

## R12.1 RustBelt 目标

### RustBelt 要回答的问题：unsafe 打穿了类型系统的信任链

Rust 的安全承诺建立在一个类型系统上——所有权 + 借用规则保证"要么多个只读别名、要么一个可写别名，二者不可兼得"（aliasing XOR mutability），从而在编译期排除数据竞争、use-after-free、悬垂引用等一大类内存错误。问题在于：很多底层数据结构和同步原语（比如 `Vec` 的扩容、`Rc` 的共享、`Mutex` 的内部可变）本质上需要"改动被别名的状态"，而这恰恰是安全类型系统禁止的。Rust 的解法是让这些库在内部用 `unsafe` 绕过检查器，再用一层安全 API 把 unsafe 封起来。

于是信任链变成了这样：安全代码信任标准库的安全 API，安全 API 内部却是没被类型系统检查过的 unsafe 代码。RustBelt 要回答的核心问题就是——在这种"安全代码 + 内部含 unsafe 的库"混合体里，Rust 的安全承诺到底还成不成立。

论文摘要说"none of Rust's safety claims have been formally proven"（Rust 的安全主张此前从未被形式化证明过）；MPI-SWS 官方项目页把这个张力概括为 Rust 同时拥有"a strong, ownership-based type system"和"libraries that internally use unsafe features"，需要判定安全主张在这种张力下是否真的成立。

对初学者来说，可以这样理解这件事的分量：日常写 Rust 时你"相信"编译器挡住了内存错误，这份信任在 RustBelt 之前是工程经验层面的（大量代码跑下来没崩），RustBelt 把它抬升到了"有机器检查过的定理背书"的层面。这不是把 Rust 变得更安全，而是把"Rust 为什么安全"从直觉变成了证明。

### 目标一：为一个现实 Rust 子集建立机器化的安全性证明

RustBelt 的第一个具体目标，是给"一个代表 Rust 现实子集的语言"做出安全性证明，而且是机器检查（machine-checked）的。官方页面的原话是它给出了"the first formal (and machine-checked) safety proof for a language representing a realistic subset of Rust"（第一个针对现实 Rust 子集的形式化且机器检查的安全性证明）。

"机器化 / 机器检查"是这里的关键词。它意味着整个证明不是写在纸上靠人审阅，而是在 Coq 这个交互式定理证明器里一步步构造、由 Coq 内核逐条验证的。好处是：人写证明会犯错、会跳步、会默默假设不成立的前提，而定理证明器不允许任何一步没有依据——只要 Coq 接受了这个证明，就几乎不可能存在推理漏洞（可信基仅剩 Coq 内核本身，这一点回接 L6-02 关于可信基 TCB 的讨论）。

"现实子集（realistic subset）"这个限定词同样重要，初学者容易把 RustBelt 误读成"证明了整个 Rust 语言"。它证明的是一个刻意选取的、贴近真实 Rust 核心语义的子集（见 R12.3 的 λRust），不是逐字覆盖 rustc 实现的每个特性。哪些特性在子集内、哪些在外，属于论文发表版本的具体范围；与今天 rustc 1.94.1 语言现状的差异需现查，不能想当然认为"论文证过 = 现在的 Rust 全都证过"。

### 目标二：可扩展的证明框架——给每个 unsafe 库一个"验证条件"

RustBelt 最有工程味道的贡献，是它的证明是可扩展的（extensible）。它不是把标准库当成一个封闭的整体一次性证完，而是给出一个框架：对每一个用了 unsafe 特性的新库，可以明确"它需要满足什么验证条件（verification condition），才能被判定为对语言的一个安全扩展（safe extension）"。官方页面原话即"for each new Rust library that uses unsafe features, we can say what verification condition it must satisfy in order for it to be deemed a safe extension to the language"。

这个"可扩展"设计直接对应 Rust 生态的现实结构。Rust 的安全不是靠"禁止一切 unsafe"，而是靠"把 unsafe 关进少数经过精心设计的库里、再用安全 API 包住"（这正是 R11.2 讲的安全抽象封装）。RustBelt 把这种工程直觉形式化了：只要每个 unsafe 库各自满足它的验证条件，它们就能像积木一样安全地拼进语言，整体仍然安全。论文据此为 Rust 生态中若干重要库（如 `Rc`/`Arc`、`Cell`/`RefCell`、`Mutex`/`RwLock`、`thread::spawn` 等一类核心抽象）做了验证——具体验证了哪些库以发表版本记录，此处不逐一坐实数量，如需精确清单以论文正文为准。

这解释了为什么"标准库里有 unsafe"不等于"Rust 不安全"。unsafe 不是漏洞，而是被约束在"必须满足某个验证条件"的位置上；RustBelt 给出的正是那个条件长什么样、怎么检验它满足了。

### 论文结论的边界与版本纪律

RustBelt 是 2018 年 POPL 的成果，针对的是当时（2017–2018）的 Rust 语言与内存语义。作为学术定理，它的结论按发表版本固定——证过的就是当时那个子集上的那些性质，不随 Rust 后续版本自动更新。

这里要给初学者一条明确的纪律：不要把 RustBelt 的结论外推成"今天的 Rust（rustc 1.94.1、edition 2024）整体已被形式证明为安全"。语言这些年新增和调整了不少特性（异步、更成熟的借用检查、各种 API），这些超出 RustBelt 发表时子集的部分，其形式化状态需要看后续研究是否覆盖，属于 ⚙演进快·锚 POPL'18·随时变 的范畴，现查不外推。RustBelt 之后确实有一系列延续工作（见 R12.3 末），把模型扩展到了并发弱内存模型、函数正确性验证等方向，但那是另一批论文的结论，不能笼统归到 RustBelt 名下。

RustBelt 证明的是"这套设计原理是对的"，不是"当前 rustc 的每一行实现都被验证过"。前者是可迁移的语言级洞见，后者是另一个（更大的）工程问题。

#### 来源与时效
- 一手锚点：RustBelt「Securing the Foundations of the Rust Programming Language」Jung/Jourdan/Krebbers/Dreyer，Proc. ACM Program. Lang. 2, POPL, Article 66（2018-01，34 页），官方项目页 https://plv.mpi-sws.org/rustbelt/popl18/ ；ACM DL https://dl.acm.org/doi/10.1145/3158154 。核实 2026-08-01。
- 交叉核对（≥2 独立来源）："第一个机器检查的现实 Rust 子集安全性证明""可扩展、每个 unsafe 库给验证条件""此前无形式化证明"三点由官方 MPI-SWS 项目页与论文摘要独立印证；the morning paper（acolyer.org，二手，非承重）对同样三点的转述一致，仅作补一手留白与通俗校验之用。
- 版本/演进：论文结论按 POPL'18 发表版本记；与本机 rustc 1.94.1 语言现状的 delta 属 ⚙演进快·锚 POPL'18·随时变，现查不外推。RustBelt 具体验证的库清单以论文正文为准，本报告未逐一坐实数量。
- 本机实证：无（形式基础，rustc 复现不适用）。

## R12.2 语义类型与 Iris 分离逻辑

### 语法可定型 vs 语义可定型：把"通过检查器"和"确实安全"分开

要理解 RustBelt 怎么处理 unsafe，先要区分两种"良型（well-typed）"。语法可定型（syntactically well-typed）指的是：代码按类型系统的语法规则逐条推导得到类型、通过了编译器检查——普通安全 Rust 代码就是这样。语义可定型（semantically well-typed）指的是：不管代码长什么样、有没有通过语法检查，它的实际运行行为满足类型该有的"契约"——比如一个声称返回 `&mut T` 的东西，运行时确实给出一个当下独占、可安全写入的引用。

unsafe 代码通常不是语法可定型的（它故意绕过了检查器），但它可以是语义可定型的——只要它内部虽然乱动指针，对外表现出的行为仍然守住了类型的契约。RustBelt 的策略就是不再要求"所有代码都语法可定型"，而是要求"unsafe 那部分是语义可定型的"。

the morning paper 的转述是"if the only code that is not syntactically well-typed appears in semantically well-typed libraries, then the program is safe to execute"（如果程序里唯一不满足语法良型的代码都出现在语义良型的库中，那么程序运行是安全的）；同一来源引述论文的基本定理为"given a program that is syntactically well-typed except for certain components that are only semantically (but not syntactically) well-typed, the entire program is semantically well-typed"（一个程序若除某些仅语义良型、不语法良型的组件外都是语法良型的，则整个程序语义良型）。

语法检查像考试的选择题机器判分——按格式对就给分；语义良型像老师亲自核对你的解题过程确实成立。unsafe 库好比"格式不合规范但解法实际正确"的答卷，RustBelt 干的事情是逐个核对这些答卷的解法真的成立，再证明"合规答卷 + 已核对过的不合规答卷"拼起来整份卷子的结论仍然可靠。

### 基本定理（fundamental theorem）：语法良型蕴含语义良型

把上面两种"良型"连起来的桥梁，是所谓的基本定理（fundamental theorem，逻辑关系方法里的标准命题名）。它大致断言：

语法可定型 ⟹ 语义可定型

也就是说，凡是老老实实通过类型检查器的安全代码，都自动满足语义契约（这一半是"送分"的，普通安全代码天然安全）。真正要费力气证的是反方向的补丁：每个 unsafe 库单独证明它虽然不语法良型、但语义良型。两者一拼，整份程序语义良型，而语义良型直接推出"运行安全、无未定义行为"。

这里对初学者最容易混淆的点是方向：基本定理管的是"语法良型的普通代码不用你操心"，而 unsafe 库的语义良型是要人工（在 Coq 里）逐个证的额外义务。R12.1 说的"每个 unsafe 库的验证条件"，落到这里就是"证明该库语义良型"这件具体差事。

值得点明的是，RustBelt 走的是语义可靠性（semantic soundness，基于逻辑关系）的路子，而不是经典类型安全那套纯语法的进展性 + 保持性（见 R12.4 的辨析）。选语义路线正是为了能容纳 unsafe——纯语法方法没法给"绕过语法规则却仍安全"的代码一个位置。

### 分离逻辑与所有权推理：frame rule 让"各管一块内存"成为可能

要在证明里刻画"这段代码只动它自己那块内存、不碰别人的"，用的工具是分离逻辑（separation logic）。分离逻辑是霍尔逻辑（Hoare logic，→L6-02 演绎验证）的一个扩展，专门为"堆内存 + 指针别名"设计。它的招牌是分离合取 `P ∗ Q`，表示"P 描述的那块内存和 Q 描述的那块内存互不重叠"，以及框架规则（frame rule）：

{P} C {Q}  可推出  {P ∗ R} C {Q ∗ R}

如果代码 C 在只碰 P 那块内存时把它从 P 变成 Q，那么在旁边多摆一块无关内存 R 的情况下，C 照样把 P 变 Q，而 R 原封不动。这正好把"局部推理（只看你动的那块）"变成合法的证明步骤。

分离逻辑之所以是 Rust 形式化的天作之合，是因为 Rust 的所有权本来就是"谁拥有哪块内存"的纪律——分离逻辑里的"拥有一块内存的断言"几乎是所有权在逻辑层的镜像。acolyer 的转述即指出 Iris"provides built-in support for reasoning modularly about ownership"（对所有权的模块化推理有内建支持）。对初学者：你在 Rust 里写代码时脑子里那套"这个值现在归谁、借给谁了"的账，分离逻辑就是把这本账写成可以做数学推导的形式。

### Iris：一套高阶并发分离逻辑，且有 Coq 支持

RustBelt 不是从零发明逻辑，而是构建在 Iris 之上。Iris 是一套高阶并发分离逻辑（higher-order concurrent separation logic），并且在 Coq 里有完整实现。两个独立来源一致：官方页面说 RustBelt is "realized using Iris"；acolyer 把 Iris 描述为"a higher-order concurrent separation logic with support in Coq"，且它能"derive custom program logics for different domains"（为不同领域派生定制的程序逻辑）。

"并发（concurrent）"指它能推理多线程共享状态下的正确性——这对 Rust 至关重要，因为 `Mutex`/`Arc`/`Send`/`Sync`（R9）全是并发抽象；"高阶（higher-order）"指逻辑里可以对断言本身再做量化、能表达很复杂的不变式；"有 Coq 支持"指它不只是纸面逻辑，而是能在定理证明器里真正跑证明。Iris 的一个特点是它像"逻辑的地基 + 搭积木工具"，RustBelt 就是用它搭出了一套专门刻画 Rust 借用与生命周期的定制逻辑（见 R12.3 的生命周期逻辑）。

### 逻辑关系：把"类型"翻译成"关于内存的谓词"

把上面几件工具串起来的方法论叫逻辑关系（logical relations）。粗略说，它给每个类型 `τ` 指派一个"语义解释"——一个用 Iris 断言写成的谓词，刻画"一个值/一块内存要满足什么条件，才算真正属于类型 `τ`"。比如 `&mut T` 的解释大致是"当下独占地拥有一块存着合法 `T` 的内存、且可安全写入"。

有了这层解释，"语义可定型"就有了精确含义：一段 unsafe 代码语义良型，就是证明它的实际行为满足它所声称类型的那个 Iris 谓词。RustBelt 论文自己把用到的关键概念列为 separation logic、type systems、logical relations、concurrency（分离逻辑、类型系统、逻辑关系、并发），逻辑关系正是把"类型系统"和"分离逻辑"焊在一起的那道焊缝。

对初学者不必深究谓词长什么样，抓住这条链就够了：类型 → （逻辑关系）→ 一条关于内存的 Iris 断言 → 在 Coq 里证明 unsafe 库满足它 → 得到该库语义良型 → 拼进基本定理 → 整程序安全。

#### 来源与时效
- 一手锚点：RustBelt POPL'18 论文（Article 66）及官方项目页 https://plv.mpi-sws.org/rustbelt/popl18/ ；Iris 项目（Coq 中的高阶并发分离逻辑）https://iris-project.org/ 与 lambda-rust 开发库 https://gitlab.mpi-sws.org/iris/lambda-rust 。核实 2026-08-01。
- 交叉核对（≥2 独立来源）：语法良型 vs 语义良型、基本定理表述、"unsafe 出现在语义良型库中则程序安全"由 the morning paper 对论文的直接引述给出，与官方页面对 Iris/语义证明的描述一致互证；分离逻辑 frame rule 与霍尔逻辑的关系另与 L6-02（演绎验证）一手材料交叉一致。二手（acolyer）仅用于补一手留白与通俗校验，不单独承重。
- 冲突项：未见来源分歧；各来源对 Iris/逻辑关系/语义可靠性的定位一致。
- 版本/演进：Iris 与 lambda-rust 为持续维护的开源开发库，本报告只用其 POPL'18 时点的概念定位；库当前版本细节属 ⚙演进快，现查不凭记忆。
- 本机实证：无（形式基础）。

## R12.3 λRust 演算与借用模型

### λRust：一个贴近 MIR 的 Rust 核心演算

RustBelt 不直接对源码级 Rust 做证明——源码语法太庞杂。它先把 Rust 蒸馏成一个小型形式演算 λRust（lambda-Rust）。据 acolyer 对论文的转述，λRust 把 Rust 形式化在"a level very close to Rust's Mid-level Intermediate Representation (MIR)"（非常接近 Rust 的中级中间表示 MIR 的层次），程序以续延传递风格（continuation-passing style）书写，内存模型支持指针算术。

为什么要造这么一个中间语言，而不是直接啃源码？给初学者一个类比：证明一条几何定理时，你不会对着一张潦草的手绘图硬推，而是先把它抽象成"点、线、角"这些干净的对象。λRust 就是 Rust 的"干净对象版"——保留所有权、借用、生命周期、unsafe 这些本质机制，剥掉语法糖和不影响安全论证的枝节。选在 MIR 层附近，是因为 MIR 正是 rustc 内部做借用检查的那一层，语义足够贴近真实编译器行为，又比源码规整得多。

λRust 是论文里的形式对象，不是你能安装运行的东西；它的目的是"能被 Iris 逻辑谈论"，不是"给人写程序"。理解到"RustBelt 证的是 λRust 这个模型、而 λRust 是 Rust 的忠实小模型"这一层即可，不必深入其操作语义规则。

### 生命周期逻辑与借用命题：把"借用"写进逻辑

λRust 里最有原创性的一块，是 RustBelt 为处理借用和生命周期专门推导出的一套生命周期逻辑（lifetime logic）。acolyer 引述论文说它的核心特征是一种借用命题（borrow propositions）的概念，"mirrors the 'borrowing mechanism' for tracking aliasing in Rust"（镜像了 Rust 里用于追踪别名的借用机制），从而能对"临时的资源所有权"做模块化推理。

Rust 里的借用是"临时把某块内存的使用权借出去，到生命周期结束时收回"（R3/R4）。分离逻辑原本擅长表达"我永久拥有这块内存"，但不擅长表达"我暂时把它借给别人、待会儿要拿回来"。生命周期逻辑补的正是这个缺口——它引入逻辑层的"借用命题"来刻画"某资源在生命周期 `'a` 内被借出、`'a` 结束后归还"，让 `&T` 和 `&mut T` 这类借用引用在证明里有精确对应物。

这一节可以和前面的工程章节直接对照：你在 R4 学的 `'a` 标注、"借用不能活得比被借的值久"，在 RustBelt 里就是被生命周期逻辑形式化的那套东西。RustBelt 的贡献之一，就是证明了这套借用/生命周期纪律在逻辑上确实成立——不是随便约定的规矩，而是有可靠性保证的。

### 借用模型与别名纪律：与 Stacked Borrows 的分工

λRust 的借用模型回答"借用引用在逻辑上意味着什么"；但还有一个相邻问题：在实际内存操作层面，什么样的指针访问算"违反了别名纪律"、从而是未定义行为（UB）？这个问题由同一研究组的另一项工作 Stacked Borrows（Jung, Dang, Kang, Dreyer, POPL 2020）回答，它给 Rust 的内存访问定义了一套操作语义级的别名模型。

据其官方页面与转述，Stacked Borrows 的思路是：为内存里每个对象维护一个"允许访问它的指针的栈"（a stack of pointers），用压栈/弹栈来建模嵌套借用；违反这个纪律的程序被判为 UB，编译器因而可以放心地围绕它做重排等优化。这套模型被实现进了 Rust 解释器 Miri（R11.4 提到的 UB 检测器就靠它），并用标准库测试集验证过它允许了足够多的真实 unsafe 代码。

RustBelt/λRust 证明的是"这套类型规则整体安全"；Stacked Borrows 定义的是"具体到一次指针访问，怎样算越界从而是 UB"，是 unsafe 代码作者要遵守的操作纪律，也是 Miri 判 UB 的依据。两者都出自 MPI-SWS 的 Rust 形式化路线，互为补充：一个管"规则对不对"，一个管"越线的边界画在哪"。需注意 Stacked Borrows 之后又有改进提案 Tree Borrows，其成为默认别名模型的状态属演进中，此处不下结论、如实标待核。

### 后续演进：RustBelt 之上的一系列扩展

RustBelt 是一条研究线的起点而非终点，初学者了解"它有后续、且后续在往哪走"有助于建立正确的坐标。已知的延续工作至少包括：RustBelt Meets Relaxed Memory（把安全性证明扩展到弱内存/宽松内存模型下的并发，Dang 等）、RustHornBelt（为 Rust 程序的函数正确性验证建立语义基础，Matsushita 等），以及更晚近把语义基础推向验证工具的 VerusBelt（为 Verus 的证明导向类型扩展建立语义可靠性）等。

这些名字里都带"Belt"，是因为它们共享 RustBelt 的方法论内核（Iris + 语义类型 + λRust 式模型），各自往一个方向延伸。对初学者不必逐一深究，记住一条即可：RustBelt 奠的地基被反复复用来证更多东西（弱内存并发、函数正确性、其他验证语言），说明它的语义框架是可迁移、可扩展的，而不是一次性的孤立证明。这些后续成果各按自己的发表版本记，属 ⚙演进快·锚各自发表版本·随时变，不归并到 RustBelt POPL'18 的结论里。

#### 来源与时效
- 一手锚点：RustBelt POPL'18 论文与官方页 https://plv.mpi-sws.org/rustbelt/popl18/ ；lambda-rust Coq 库 https://gitlab.mpi-sws.org/iris/lambda-rust ；Stacked Borrows「An Aliasing Model for Rust」Jung/Dang/Kang/Dreyer，POPL 2020，官方页 https://plv.mpi-sws.org/rustbelt/stacked-borrows/ ；The Rustonomicon https://doc.rust-lang.org/nomicon/ 。核实 2026-08-01。
- 交叉核对（≥2 独立来源）：λRust 贴近 MIR、CPS 风格、支持指针算术，以及生命周期逻辑/借用命题"镜像 Rust 借用机制"，由 the morning paper 对论文的直接引述给出，与官方项目页对工作内容的描述一致；Stacked Borrows 的"每对象一个指针栈、违反即 UB、实现进 Miri、用标准库测试集验证"由其官方 POPL'20 页面与检索摘要独立印证。后续工作（RustBelt Meets Relaxed Memory / RustHornBelt / VerusBelt）由各自论文页/PDF 佐证，仅作演进坐标、非本主题承重结论。
- 冲突项：未见来源在 λRust/借用模型定位上的分歧。
- 待核：Tree Borrows 是否已取代 Stacked Borrows 成为默认别名模型——演进中，如实标待核，不下结论（核实 2026-08-01）。
- 版本/演进：λRust 针对 POPL'18 时的 Rust 子集；Stacked Borrows 为 POPL'20 成果；后续 Belt 系列各按发表版本记，均属 ⚙演进快·锚各自版本·随时变。
- 本机实证：无（形式基础；Miri 相关 UB 检测的本机可用性属 R11.4 范畴，本主题不实证）。

## R12.4 四门汇合点

### 回接类型安全：语义可靠性与 Progress + Preservation 的异同

RustBelt 直接上承编程语言理论课的类型安全主题（→L5-06 T3）。经典的简单类型 λ 演算把"类型安全"拆成两条纯语法定理：

Safety = Progress + Preservation

进展性（Progress）说"良型的程序要么是最终值、要么还能再走一步（不会卡住）"；保持性（Preservation，又称 subject reduction）说"良型的程序走一步之后仍然良型"。两条合起来推出"良型程序永不卡在非法状态"，这就是 STLC 那一套的类型安全，也是 RustBelt 的祖型。

RustBelt 与它的关系是"同一目标、不同方法"。目标都是"良型程序不出坏事"；但方法上，经典 Progress+Preservation 是语法可靠性（syntactic soundness）——全靠对语法推导的归纳，要求每段代码都语法良型。RustBelt 走的是语义可靠性（semantic soundness）——用逻辑关系把类型翻译成 Iris 谓词。差别的根源正是 unsafe：纯语法方法无法安置"绕过语法规则却仍安全"的 unsafe 代码，而语义方法能给它一个"满足语义契约"的位置。给初学者一句话：RustBelt 是"为了容纳 unsafe，把课本上的类型安全从语法版升级成语义版"。

### 回接形式验证半场：机器化证明、分离逻辑、可信基

RustBelt 同时上承形式验证课（→L6-02）。它用到的几乎每件工具都是那半场课的主角：分离逻辑是霍尔逻辑面向堆与指针的扩展（→L6-02 演绎验证）；Iris 是建在 Coq 上的程序逻辑；整个证明在 Coq 这个交互式定理证明器里机器化（→L6-02 交互式定理证明），可信基（TCB）收缩到 Coq 内核。

对初学者点明这条交叉的价值：形式验证课里学的 Hoare 三元组、循环不变式、定理证明器、可信基这些看似抽象的概念，在 RustBelt 里被合起来干了一件很具体的大事——证明一门工业语言的核心安全性。R12 因此是"验证理论"落到"真实语言"的一个样板。此处只作一句交叉引用点题，不展开分离逻辑推理规则或 Coq 证明战术本身。

### RustBelt 证的是模型，不是 rustc 实现——一个必须守住的分账

初学者最容易踩的误区，是把"RustBelt 证明了 Rust 安全"理解成"rustc 编译器被证明没有 bug"。要严格分账：RustBelt 证的是 λRust 这个语言模型上的安全性定理，即"这套类型/借用/生命周期规则作为一套设计是可靠的"。它没有、也不声称证明了 rustc 这个具体实现的每一行代码正确。

这与"规范与实现分账"这条基本纪律一致：标准/模型的语义 是一回事，某个具体实现的行为 是另一回事。rustc 里若有实现 bug（历史上借用检查器确实修过 soundness 漏洞），并不推翻 RustBelt——被推翻的是"实现忠实于模型"这个另外的假设，而不是模型本身的定理。反过来，RustBelt 的价值在于：它确立了"值得被正确实现的那个规则集"确实是安全的，给编译器实现指明了一个有证明背书的目标。

### 对初学者：为什么"能被证明"这件事值得在意

收束整章。日常写 Rust 时你其实一直在"信任"编译器和标准库——信任借用检查器没放过内存错误，信任 `Rc`/`Mutex` 内部的 unsafe 没埋雷。RustBelt 做的事，是把这份信任里最根本的一层从"经验上没出事"抬升到"有机器检查过的定理背书"。

这带来两个初学者能实感的结论。其一，标准库里的 unsafe 不该让你恐慌——它们被约束在"必须满足验证条件"的位置上，且核心抽象已被这套框架验证过，unsafe 是被驯服的工具而非漏洞。其二，Rust "安全"这个词有了不同于其他语言营销话术的分量：它背后站着一条从简单类型 λ 演算的类型安全（L5-06）、经分离逻辑与机器化证明（L6-02）、到 λRust 与 Iris（本章）的完整学术链条。R12 正是这四门课——编译/语言理论、类型理论、形式验证、Rust 工程——真正握手的地方。

#### 来源与时效
- 一手锚点：RustBelt POPL'18 论文与官方页 https://plv.mpi-sws.org/rustbelt/popl18/ ；类型安全 Progress+Preservation 见 TAPL（Pierce, MIT Press 2002）第 8–9 章及本库 L5-06 T3 报告；分离逻辑/机器化证明/可信基见本库 L6-02（演绎验证、交互式定理证明）相关报告。核实 2026-08-01。
- 交叉核对（≥2 独立来源）："语义可靠性 vs 语法可靠性"的区分由 the morning paper 对论文语义证明方法的转述与官方页对 Iris/逻辑关系路线的描述共同支持；Progress+Preservation 的经典表述由 TAPL 与 L5-06 T3 一手交叉一致；"证模型非证实现"的分账由"规范与实现分账"这条通用严谨性纪律与 RustBelt 自述的证明对象（λRust 模型）共同支撑。
- 冲突项：未见分歧。需强调的是各交叉引用（L5-06 T3、L6-02）在本章仅作点题，不展开其内容。
- 版本/演进：RustBelt 结论按 POPL'18 发表版本记；与本机 rustc 1.94.1 当前语言的 delta 属 ⚙演进快·锚 POPL'18·随时变，现查不外推。
- 本机实证：无（形式基础，rustc 复现不适用）。
