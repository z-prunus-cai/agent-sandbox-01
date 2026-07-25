# Round 3a · 第 2 组「编程与数据结构」· 大主题分解

> 基线 Py3.11.15/np2.4.6/gcc13.3 @2026-07-25
> 组内课程：L1-01 程序设计入门 · L2-01 数据结构（★枢纽）· L3-04 面向对象与程序设计范式
> 方法：锚定权威教材 TOC / 顶校 syllabus（不靠记忆），列教学单元级大主题清单（查全、少重叠、按教学序）。
> 核实日期：2026-07-25。

---

## L1-01 · 程序设计入门

### 权威锚点
- **Berkeley CS61A《Structure and Interpretation of Computer Programs》** — 官方 syllabus + lecture schedule。URL: https://cs61a.org/ （核实 2026-07-25 时线上为 Summer 2026 学期；Python 为主，含 Scheme/SQL）。锚定其讲次序：Functions/Exceptions → Control → Higher-Order Functions → Environments → Recursion → Tree Recursion → Sequences/Containers → Mutability & Data Abstraction → OOP → Efficiency → Interpreters。
- **K&R《The C Programming Language》(2nd ed, 1988，基于 C89)** — C 面向内存路径的经典权威教材（章节：类型/运算符/表达式、控制流、函数与程序结构、指针与数组、结构、输入输出）。
- 补充定位：ISO/IEC 9899:2011 (C11) 标准文本（UB/序列点裁决）、Python 3.11 官方 Language Reference（数据模型 §3 / 执行模型 §4）。
- 说明：本 KB 采「Python 先建计算思维 + C 补机器模型」双语路径（见 round2 表 A），故大主题按两语言接缝布置；CMU 15-122（C0，强调契约/不变式）作正确性单元的旁证。

### 大主题清单

| 编号 | 大主题 | 一句话范围 | 来源锚点 | 跨课重叠提示 |
|------|--------|-----------|----------|-------------|
| 1 | 值、变量与两套心智模型 | 字面量/表达式/类型；C「有类型的内存位置」vs Python「名字绑定对象」；赋值语义与别名陷阱 | CS61A(Environments)；K&R Ch2；C11 | — |
| 2 | 控制流与结构化编程 | 分支/循环/短路求值；布尔逻辑；结构化定理；C 的 UB 陷阱（有符号溢出/未初始化读/序列点） | CS61A(Control)；K&R Ch3；C11 | — |
| 3 | 函数、作用域与调用约定 | 定义/参数/返回；值传递 vs 引用/指针传递；作用域与环境模型；栈帧建立与销毁 | CS61A(Functions,Environments)；K&R Ch4 | ↔L3-03（栈帧机器落地）；HOF/闭包留给 L3-04 |
| 4 | 递归与栈 | 基例/递归例；递归=编译器管理的栈；树递归；尾递归；两种失败面（C→SIGSEGV / Python→RecursionError） | CS61A(Recursion,Tree Recursion)；K&R | ↔L2-01（调用栈/树） |
| 5 | 复合数据与基础数据结构入门 | 数组/字符串；Python list/tuple/set/dict；C 结构体与数组；可变数据与数据抽象入门 | CS61A(Sequences,Mutability,Data Abstraction)；K&R Ch6 | ↔L2-01（结构的实现深挖归 DS）；接缝课 |
| 6 | 指针与内存（C 专属地基） | 地址算术/sizeof/指针与数组/多级指针/动态分配 malloc-free/悬垂与野指针 | K&R Ch5；C11 | ↔L2-01（链表实现）↔L4-01（虚存） |
| 7 | 简单 I/O 与类型表示 | printf/scanf 格式串与类型对齐；标准输入输出；整型/浮点表示的入门直觉 | K&R Ch7；C11 | ↔L3-02（表示与内存层次深挖归组成原理） |
| 8 | 程序正确性与调试基础 | 测试/断言、前后置条件与循环不变式（契约式）、基础调试；效率/增长阶入门直觉 | CS61A(Efficiency)；CMU 15-122（契约） | ↔L2-01/L3-01（渐近分析正式化） |

**建议大主题数：K = 8**（其中 #6 为 C 专属地基；#5/#8 为与 L2-01 的接缝，深挖内容显式下沉）

---

## L2-01 · 数据结构　【★枢纽课】

### 权威锚点
- **Berkeley CS61B《Data Structures》** — 官方 lecture schedule。URL: https://sp24.datastructur.es/ （Spring 2024，核实 2026-07-25；Java）。讲次序：Lists(SLList/DLList/Arrays) → Inheritance/Iterators → Asymptotics I/II → Disjoint Sets → ADTs/Sets/Maps/BSTs → B-Trees/Red-Black/LLRBs → Hashing I/II → Heaps & PQs → Graphs → Tries → Sorting → Complexity/P=NP → Compression。
- **CLRS《Introduction to Algorithms》(4th ed, 2022)** — 数据结构 + 代价分析的权威教材（Part II 排序、Part III 数据结构：哈希/BST/红黑树/B树、扩充数据结构；Part VI 图基础）。
- 补充定位：Pat Morin《Open Data Structures》（开放获取、定位精确）；CPython 源码 Objects/listobject.c（动态数组扩容）与 dictobject.c（紧凑 dict + 开放定址）作实现级取证。

### 大主题清单

| 编号 | 大主题 | 一句话范围 | 来源锚点 | 跨课重叠提示 |
|------|--------|-----------|----------|-------------|
| 1 | 抽象数据类型与渐近分析 | ADT 概念（规范 vs 实现分离）；Big-O/Θ/Ω；最坏/平均/均摊分析框架 | CS61B(Asymptotics I/II,ADTs)；CLRS Ch2-4 | ↔L3-01（复杂度分析的整体算法层深挖归算法课） |
| 2 | 线性表：数组与链表 | 静态/动态数组与均摊扩容；单/双链表；连续 vs 分散的内存布局与代价画像 | CS61B(Lists I-IV)；CLRS Ch10；listobject.c | ↔L1-01（指针/结构体实现工具） |
| 3 | 受限接口结构：栈/队列/双端队列 | LIFO/FIFO/deque 语义；数组 vs 链表实现；环形缓冲 | CS61B(Lists)；CLRS Ch10 | ↔L1-01（调用栈）↔L4-01 |
| 4 | 哈希表 | 哈希函数；冲突解决（链地址 vs 开放定址）；负载因子与扩容；最坏 O(n) 退化 | CS61B(Hashing I/II)；CLRS Ch11；dictobject.c | — |
| 5 | 树与二叉搜索树 | 树术语与遍历（前/中/后序、层序）；BST 插入/查找/删除；退化成链表的陷阱 | CS61B(BSTs)；CLRS Ch12 | ↔L1-01（递归/树） |
| 6 | 平衡搜索树 | AVL/红黑树/LLRB（近似平衡）；B 树/B+ 树（多路）；不变式与旋转/分裂 | CS61B(B-Trees,Red-Black,LLRBs)；CLRS Ch13,18 | ↔L4-03/L6-04（磁盘 B+ 树/索引工程化） |
| 7 | 堆与优先队列 | 完全二叉树 + 数组表示；上浮/下沉；O(1) 取极值、O(log n) 插删 | CS61B(Heaps & PQs)；CLRS Ch6 | ↔L3-01（堆排序/PQ 算法应用归算法课，堆结构是接缝） |
| 8 | 不相交集合（并查集） | union-find；路径压缩 + 按秩/大小合并；近常数均摊 | CS61B(Disjoint Sets)；CLRS Ch19 | ↔L3-01（Kruskal MST 应用） |
| 9 | 图的表示与基础遍历 | 邻接表 vs 邻接矩阵（稀疏/稠密权衡）；BFS/DFS 及其结构 | CS61B(Graphs)；CLRS Ch20 | ↔L3-01（最短路/MST/拓扑排序等图算法深挖归算法课） |
| 10 | 字符串/前缀结构（Trie） | 前缀树；字符串键的查找与前缀操作；基数思想 | CS61B(Tries) | ↔L3-01（字符串算法） |
| 11 | 排序（作为数据结构应用） | 选择/插入/归并/快排/堆排/基数排序；比较排序下界 Ω(n log n) | CS61B(Sorting)；CLRS Ch2,6-8 | ⚠强重叠 L3-01：算法设计层深挖归算法课，此处仅作结构操作应用 |

**建议大主题数：K = 11**（#11 排序与 L3-01 强重叠，保留以求查全但显式标注深挖归属；CS61B 末尾 Complexity/P=NP、Compression 属计算理论/信息论方向，不列入 DS 大主题，归 L3-05/信息论）

---

## L3-04 · 面向对象与程序设计范式

### 权威锚点
- **SICP《Structure and Interpretation of Computer Programs》(Abelson & Sussman)** — 抽象/范式经典。TOC（核实 2026-07-25，https://sarabander.github.io/sicp/html/index.xhtml ）：Ch1 Building Abstractions with Procedures（含 Higher-Order Procedures）→ Ch2 Building Abstractions with Data（数据抽象/闭包性质/多重表示/通用型操作）→ Ch3 Modularity, Objects, and State（赋值与局部状态/环境模型/可变数据/流）→ Ch4 Metalinguistic Abstraction（元循环求值器/惰性/非确定性/逻辑编程）。
- **CMU 15-150《Principles of Functional Programming》** — FP 范式一手 syllabus。URL: https://www.cs.cmu.edu/~iliano/courses/15F-CMU-CS150/syllabus.shtml （Fall 2015，核实 2026-07-25；Standard ML）。主题：inductive definitions、functions、beyond induction；数据类型/模式匹配/多态/高阶函数/异常/流/记忆化/模块化；work & span。
- 补充定位（承重一手论文/规范）：Cardelli & Wegner《On Understanding Types, Data Abstraction, and Polymorphism》(1985，多态四分类)；Barbara Liskov《Data Abstraction and Hierarchy》(1987, LSP)；GoF《Design Patterns》(1994)；Python 3.11 Data Model（`__`协议/abc/descriptor）。

### 大主题清单

| 编号 | 大主题 | 一句话范围 | 来源锚点 | 跨课重叠提示 |
|------|--------|-----------|----------|-------------|
| 1 | 编程范式总览与抽象层次 | 命令式/OO/FP/声明式分类；抽象作为复杂度管理；范式=风格而非语言、多范式融合 | SICP Ch1；round2 表 C | ↔L1-01（默认命令式基础在此升华） |
| 2 | 数据抽象与封装 | 信息隐藏；ADT；接口与实现分离；抽象屏障 | SICP §2.1-2.2；Liskov(1987) | ↔L2-01（ADT 的结构实现） |
| 3 | OO 三支柱：封装/继承/多态 | 对象=状态+行为；is-a 继承；子类型多态；消息传递模型 | SICP §3.1,§2.4-2.5；Python Data Model | — |
| 4 | 动态派发机制 | 虚表（C++ vtable）vs 鸭子类型（Python）；方法解析顺序 MRO 与菱形继承；静/动派发对比 | Python Data Model(`__mro__`)；round2 表 C | ↔L3-03（虚表机器落地）↔L5-14（Rust trait 派发） |
| 5 | 接口、抽象与设计原则 | 抽象基类/协议；Liskov 替换原则；组合优于继承（脆弱基类）；SOLID | Liskov(1987)；round2 | ↔L4-06（架构/软工层应用） |
| 6 | 泛型与参数多态 | Cardelli-Wegner 多态四分类（参数/包含/特设/强制）；擦除(Java) vs 单态化(C++) vs 结构化鸭子 | Cardelli-Wegner(1985) | ↔L5-06（类型系统的形式化）↔L5-14（Rust 泛型/trait 约束） |
| 7 | 函数式范式 | 一等/高阶函数、闭包（含晚绑定陷阱）、不可变性、map/filter/reduce、纯函数与副作用隔离 | SICP §1.3；CMU 15-150 | ↔L1-01（基础函数）↔L5-06（λ 演算根） |
| 8 | 状态、可变性与求值模型 | 赋值与局部状态的代价；环境模型；可变 vs 不可变；（引申）流/惰性求值 | SICP Ch3；CMU 15-150 | ↔L4-05（可变共享状态与并发） |
| 9 | 声明式与元语言抽象（选修广度） | 解释器视角、逻辑/关系式声明式编程作为范式一支 | SICP Ch4 | ⚠重叠 L1-01(CS61A 解释器/SQL) 与 L5-06；此处仅作范式广度，深挖归 L5-06 |
| 10 | 设计模式作为范式产物（可选） | GoF 模式（策略/观察者/工厂）为 OO 惯用法结晶；FP 视角下部分模式消解为高阶函数 | GoF(1994) | ↔L4-06（工程实践） |

**建议大主题数：K = 8–10**（核心 8 个 #1–#8；#9 声明式、#10 设计模式为选修广度/可选单元，视与 L5-06/L4-06 的边界切分决定是否展开）

---

## 组内跨课去重提示（防重复立项）

- **L1-01 ↔ L2-01（接缝：指针/递归/函数）**：实现工具（指针、递归、结构体、动态分配）归 L1-01；用这些工具造的结构（链表/树/哈希）及其代价画像归 L2-01。L1-01 #5/#6 是下沉边界。
- **L2-01 ↔ L3-01（接缝：堆/排序/图算法）**：单结构的 insert/find/操作代价归 L2-01；用结构解决问题的整体算法与复杂度归 L3-01。L2-01 #7 堆是共享接缝，#8 并查集/#9 图/#11 排序均标「算法设计层深挖归 L3-01」。
- **L2-01 ↔ L4-03/L6-04**：内存版 B 树/BST 直觉归 L2-01；磁盘 B+ 树索引归 L4-03，LSM/跳表归 L6-04。
- **L1-01 命令式基础 ↔ L3-04 范式升华**：命令式/过程式默认写法在 L1-01；OO/FP/声明式的对比与「范式」概念化归 L3-04（L3-04 #1）。高阶函数/闭包正式化归 L3-04 #7（L1-01 只给基础函数）。
- **L3-04 ↔ L5-06 编程语言理论**：多态的「用法与工程权衡」归 L3-04（#4/#6）；类型系统/λ 演算/形式语义归 L5-06。L3-04 #9 声明式/元语言仅作广度，深挖归 L5-06。
- **L3-04 ↔ L5-14 Rust**：通用多态/派发机制归 L3-04；Rust 的所有权/无 GC 内存安全 + trait 特设多态作现代对照归 L5-14，勿与 L3-04 #4/#6 重复立项。
- **L3-04 ↔ L4-06 软件工程**：语言机制（LSP/组合优先/设计模式的语言层）归 L3-04（#5/#10）；SOLID/模式的架构方法学应用归 L4-06。
- **L3-04 #4 虚表 ↔ L3-03 汇编**：派发的语义/用法归 L3-04；虚表在机器层的落地归 L3-03。
