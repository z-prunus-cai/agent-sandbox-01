# L5-08·大主题8 一阶逻辑与推理

> 基线 Py3.11.15/np2.4.6/gcc13.3 @2026-07-25 ｜核实日期：2026-08-01 ｜先修：L5-08·大主题7 命题逻辑与逻辑智能体（KB/蕴含/CNF/归结/DPLL）｜一手锚点：AIMA（Russell & Norvig）第 4 版 2020，Ch8（一阶逻辑）与 Ch9（一阶推理）｜成熟度：GA（经典理论，数十年稳定）

命题逻辑能表达"事实为真/为假"，但一旦世界里有大量对象、它们之间还有关系，命题逻辑就得为每一个具体组合单独造一个命题符号，语句数量爆炸且无法说"所有""存在"。一阶逻辑把世界拆成对象、关系、函数，再配上量词，用一句话概括无穷多个体。本报告的主线是一条被反复使用的思想——提升（lifting）：命题层面的推理规则（假言推理、归结）通过引入合一（unification），被原样"抬升"到含变量的一阶层面，从而在不把变量枚举成所有具体值的前提下直接推理。合一里一个最容易被忽视却关乎健全性的细节是 occurs-check，本报告会专门点明。

与程序语言理论中"形式系统/证明论"的邻接：一阶逻辑的证明演算、可靠性与完备性同属形式系统研究对象，此处只作提及、不展开。

## 8.1 FOL 语法与语义（对象/关系/函数/量词、模型论）

### 8.1.1 为什么要从命题逻辑升级到一阶逻辑

命题逻辑把整个陈述句当作一个不可再分的原子符号（如 P 表示"下雨了"），只能组合真假、无法看进句子内部的结构。当世界里有很多相似对象、并且它们共享同一条规律时，命题逻辑必须为每个对象各写一条规则，既冗长又无法表达"对所有对象都成立"。

一阶逻辑（First-Order Logic, FOL；也叫一阶谓词逻辑）承诺的世界由三类东西组成：对象（objects，世界里的个体，如 John、数字 2）、关系（relations，对象之间成立与否的性质，如 Brother、大于）、函数（functions，从对象映到对象，如 Mother-of、加一）。这三样加上量词，让一句话能一次性覆盖一整类对象。

想说"所有国王都贪婪的人是坏人"。命题逻辑做不到用一句涵盖每一个国王；一阶逻辑写成一条带全称量词的蕴含式即可，推理时再针对具体的 John 实例化。这正是 AIMA 用来引出一阶推理的经典例子。

### 8.1.2 语法要素：项、原子语句、复合语句

一阶逻辑的符号分三类。常量符号指代具体对象（John、A）；谓词符号指代关系（King、Brother）；函数符号指代函数（Mother、LeftLegOf）。变量（x、y）是占位的对象名。

由这些符号按规则搭出两级结构。项（term）指代一个对象，它要么是常量、要么是变量、要么是函数符号作用在若干项上，如

Mother(John)

原子语句（atomic sentence）是一个谓词作用在若干项上，表达一个可真可假的事实，如

Brother(Richard, John)

复合语句用逻辑连接词（¬、∧、∨、⇒、⇔）把语句组合起来，再用量词绑定变量，如

King(x) ∧ Greedy(x) ⇒ Evil(x)

一个常见的初学者混淆是把项和原子语句搞混：项（如 Mother(John)）指的是"一个人"，本身没有真假；原子语句（如 King(John)）说"某事成立"，才有真假。函数返回对象、谓词返回真假，这是划清两者的关键。

### 8.1.3 语义与模型论：论域、解释、真值

一阶逻辑语句的意义，由一个模型（model）来定。模型给出两样东西：一个论域（domain，世界里所有对象的非空集合），以及一个解释（interpretation，把每个常量映到论域里的某个对象、每个谓词映到对象之上的一个关系、每个函数映到一个实际的函数）。给定模型后，任何一个不含自由变量的语句在其中要么真要么假。

这一套"用集合论结构给语法符号赋予意义"的框架就是模型论（model theory）。它把"符号"和"符号指的东西"彻底分开：同一句 King(John) 的真假，取决于在当前模型里 John 指谁、King 这个关系包含哪些对象。

符号名字本身不带含义。写 Mother 这个函数符号，不代表它自动就是"生母"关系——它的含义完全由模型的解释决定。逻辑系统只保证"从公理按规则能推出什么"，至于这些符号对应现实里的什么，是建模者通过选择公理和解释来负责的。

### 8.1.4 量词：全称 ∀、存在 ∃ 及其对偶与嵌套

全称量词 ∀x P 断言"对论域里每个对象，P 都成立"；存在量词 ∃x P 断言"论域里至少有一个对象使 P 成立"。二者互为对偶：

¬∀x P  ≡  ∃x ¬P
¬∃x P  ≡  ∀x ¬P

这条对偶关系在后面转 CNF"把否定内移"时会直接用到。

一个高频易错点是量词和连接词的搭配。全称量词几乎总和蕴含 ⇒ 搭配，存在量词几乎总和合取 ∧ 搭配。想说"所有国王都是人"应写

∀x  King(x) ⇒ Person(x)

如果错写成 ∀x King(x) ∧ Person(x)，意思变成"世界上万物都既是国王又是人"，几乎必假。反过来，"有一个国王很贪婪"应写

∃x  King(x) ∧ Greedy(x)

若错写成 ∃x King(x) ⇒ Greedy(x)，由于蕴含在前件为假时整体为真，只要世界上存在任何一个不是国王的对象，这句就自动成真，完全说不出想说的意思。

嵌套量词还要注意顺序。∀x ∃y Loves(x, y)（人人都爱着某个人，各爱各的）与 ∃y ∀x Loves(x, y)（存在某一个被所有人爱的人）意思截然不同，∀∃ 与 ∃∀ 不可随意交换。

### 8.1.5 等词与相等

一阶逻辑通常内建一个特殊谓词——等词 =，表示两个项指代论域里同一个对象。x = y 为真当且仅当 x 和 y 指的是同一个个体。用它可以表达"恰好两个"这类计数陈述，例如"Richard 至少有两个兄弟"写成

∃x, y  Brother(x, Richard) ∧ Brother(y, Richard) ∧ ¬(x = y)

若不加 ¬(x = y)，x 和 y 可以指同一个人，就说不出"两个不同的兄弟"。

要注意逻辑里的相等和某些实现里的相等不是一回事。经典一阶逻辑允许推断 morningstar = eveningstar 这类同一性；而 Prolog 的 = 只做"两个项是否可合一"的语法检查，morningstar = eveningstar 会失败——这属于规范语义与具体实现的分账，8.4 会再点。

### 8.1.6 蕴含、有效性、可满足性与半可判定性

和命题逻辑一样，一阶逻辑里 KB ⊨ α（KB 蕴含 α）意思是"凡使 KB 全真的模型都使 α 真"。有效（valid）指在一切模型下都真，可满足（satisfiable）指至少有一个模型使之真。这三个概念的区分是命题逻辑那一主题的核心易错点，在一阶逻辑里同样成立，只是"模型"从真值指派升级成了论域加解释。

一阶逻辑与命题逻辑的关键分水岭在于可判定性。命题逻辑的蕴含是可判定的（真值表枚举一定停机）；一阶逻辑的蕴含只是半可判定的（semidecidable）。AIMA 第 9 章小结明确写道：广义假言推理对定子句完备，"尽管蕴含问题是半可判定的"。

半可判定的含义是：存在一个过程，若 KB ⊨ α 成立，它保证在有限步内找到证明；但若不成立，它可能永远跑不停、给不出"不蕴含"的确定回答。这背后是两条经典结论合力的结果——Gödel 1929/1930 完备性定理保证"每个语义有效的一阶语句都有证明"（于是有效语句集是递归可枚举的），而 Church 与 Turing（1936）证明一阶有效性不可判定（该集合不是递归的）。可枚举但不可判定，合起来正是"半可判定"。

初学者容易把 Gödel 完备性定理和他的不完备性定理混为一谈：前者说的是"一阶逻辑这套证明系统本身完备"，后者说的是"足够强的算术理论无法既一致又完备"，二者对象不同、结论方向也不同，别混。

#### 来源与时效
- AIMA 第 4 版 2020，Ch8「First-Order Logic」（语法/语义/量词/等词）与 Ch9 小结「entailment problem is semidecidable」；TOC 与章节 PDF：https://aima.cs.berkeley.edu/contents.html ，Ch9 PDF https://aima.cs.berkeley.edu/4th-ed/pdfs/newchap09.pdf （2026-08-01 复核在架，正文引文取自该 PDF 提取文本）。
- 半可判定 / Gödel 完备性 vs Church-Turing 不可判定：Bezhanishvili & Moss「Undecidability of First-Order Logic」讲义 https://www.cs.nmsu.edu/historical-projects/Projects/FoLundecidability.pdf ；Stanford Encyclopedia of Philosophy「Gödel's Incompleteness Theorems」https://plato.stanford.edu/entries/goedel-incompleteness/ （2026-08-01 复核）。二者与 AIMA 小结一致：有效语句递归可枚举、非递归。
- 冲突项：未见实质冲突。"完备性定理"与"不完备性定理"是不同定理，本报告已显式区分，非来源分歧。

## 8.2 知识工程与 FOL 表示（领域公理化流程）

### 8.2.1 知识工程的含义与七步流程

知识工程（knowledge engineering）指把某个领域的知识调查清楚、选定词汇、再写成一阶逻辑公理的整个过程，产出一个能被推理引擎使用的知识库。它是"用逻辑给现实世界建模"这件事的工程化落地。

AIMA 把这个过程归纳为一条大致流程：确定任务（要回答哪类查询、有哪类事实）；搜集相关知识（知识获取）；决定词汇表，即选定用哪些谓词、函数、常量（这一步叫本体论承诺，选得好坏直接决定后面写公理顺不顺）；把领域的一般知识编码成公理；把具体问题实例编码成事实；把查询提给推理过程并读回答案；最后调试知识库（answer 不对，往往是漏了公理或公理写错）。

对初学者，最有价值的直觉是：写逻辑不像写普通程序那样"一步步下指令"，而是声明式地陈述"世界里什么为真"，推理引擎负责从这些陈述里导出结论。你的活儿是把知识说对、说全。

### 8.2.2 用一个受限世界做公理化示范

AIMA 用 wumpus 世界（一个格子地牢，含怪物、陷阱、金子，智能体靠感知微风/臭味推断危险）来演示：先选谓词，如 Smelly(s)、Adjacent(s1, s2)、Pit(s)；再写一般规律，例如"某格有臭味当且仅当相邻格有 wumpus"这类双向蕴含公理；最后把每一步实际感知作为事实 TELL 进 KB，用 ASK 查询"下一步走哪安全"。数字电路诊断、亲属关系等也是章节里的标准公理化练习领域。

这个环节要让初学者体会的是：一条自然语言规律（"有臭味 ⟺ 邻格有怪物"）如何被逐字翻译成一条带量词的双向蕴含，以及"一般公理"（对所有格子成立的规律）与"具体事实"（这一格现在闻到臭味）在 KB 里如何分工。

### 8.2.3 表示阶段最常见的陷阱

公理化最容易出错的地方，是"你以为你写的意思"和"逻辑上它真正的意思"对不上。前面 8.1.4 说的 ∀ 误配 ∧、∃ 误配 ⇒ 就是典型：一个连接词选错，整条公理的真值行为就跑偏，而语法上它完全合法、推理引擎不会报错，错误只会体现在最终答案莫名其妙。

另一类陷阱是漏写约束。想表达"两个不同的兄弟"却漏掉 ¬(x = y)，或想表达关系的对称性/传递性却忘了补上相应公理，都会让 KB 推不出本应成立的结论，或推出本不该成立的结论。调试知识库时，"缺公理"和"公理写歪"是两大主因。

写完每条公理，回头用自然语言把它"读回来"一遍，检查读出来的意思是否正是你想说的。这个自检习惯比任何工具都管用。

#### 来源与时效
- AIMA 第 4 版 2020，Ch8「Knowledge Engineering in First-Order Logic」（七步流程、词汇选择、wumpus/电路公理化、调试）；TOC https://aima.cs.berkeley.edu/contents.html （2026-08-01 复核）。
- 量词/连接词误配陷阱交叉印证：Stanford CS221 讲义「first-order modus ponens / first-order logic / definite clauses」https://stanford-cs221.github.io/autumn2022-extra/modules/logic/first-order-modus-ponens.pdf （2026-08-01 复核）与 AIMA Ch8 表述一致。
- 冲突项：未见分歧。本节偏流程与建模实践，两来源互补。

## 8.3 合一 unification（最一般合一子 MGU，含 occurs-check）

### 8.3.1 提升（lifting）与广义假言推理 GMP

把命题逻辑的推理规则搬到含变量的一阶逻辑上，核心手段就是提升。命题层的假言推理是"由 p 和 p⇒q 得 q"。一阶层想直接用它，得先找到一个变量替换，让规则前件和 KB 里已有的事实对上号。这条被提升后的规则叫广义假言推理（Generalized Modus Ponens, GMP）。

其形式是：对原子语句 pᵢ、pᵢ′、q，若存在替换 θ 使得对所有 i 都有 SUBST(θ, pᵢ′) = SUBST(θ, pᵢ)，则

p₁′, p₂′, …, pₙ′,  (p₁ ∧ p₂ ∧ … ∧ pₙ ⇒ q)
─────────────────────────────────────────
              SUBST(θ, q)

拿 AIMA 的例子：规则是 ∀x King(x) ∧ Greedy(x) ⇒ Evil(x)，事实是 King(John) 和"人人贪婪" ∀y Greedy(y)。取替换 θ = {x/John, y/John}，就能让规则前件 King(x)、Greedy(x) 分别对上 King(John) 和 Greedy(y)，于是推出 SUBST(θ, Evil(x)) = Evil(John)。

GMP 是可靠（sound）的推理规则。它相对命题假言推理"提升"的地方在于：它不需要把 ∀x 的规则先枚举成所有具体对象的实例（那叫命题化，代价巨大），而是只做恰好能让这一步推理走通的替换。AIMA 原话是提升规则"make only those substitutions which are required to allow particular inferences to proceed"。这就是提升相对命题化的核心优势。

### 8.3.2 合一与最一般合一子 MGU

要执行 GMP，得有算法自动找出那个让不同表达式"长得一样"的替换。这个过程叫合一（unification）。UNIFY 算法接收两个语句，返回一个合一子（unifier）θ，使

SUBST(θ, p) = SUBST(θ, q)

AIMA 给的几组例子很能说明行为：

UNIFY(Knows(John, x), Knows(John, Jane)) = {x/Jane}
UNIFY(Knows(John, x), Knows(y, Bill)) = {x/Bill, y/John}
UNIFY(Knows(John, x), Knows(y, Mother(y))) = {y/John, x/Mother(John)}

一对表达式可能有多个合一子。例如 UNIFY(Knows(John, x), Knows(y, z)) 既可返回 {y/John, x/z}，也可返回更"具体"的 {y/John, x/John, z/John}。前者对变量的约束更少、更宽松，后者可由前者再追加替换得到。对每一对可合一的表达式，都存在一个最一般合一子（most general unifier, MGU），它对变量施加的限制最少，且在变量改名意义下唯一。合一算法要返回的正是这个 MGU。

直觉上，MGU 是"刚好够让两边相等、绝不多约束一分"的那个替换。多约束会丢解——把 x 也钉死成 John，就排除了 x 取别的值的可能，推理会漏掉本应找到的答案。

### 8.3.3 标准化分离 standardizing apart

一个隐蔽的坑是两个待合一语句碰巧用了同一个变量名。看这例：

UNIFY(Knows(John, x), Knows(x, Elizabeth)) = fail

它失败是因为同一个 x 不能既等于 John 又等于 Elizabeth。可是第二句里的 x（"人人都认识 Elizabeth"）和第一句里的 x 本是毫不相干的两个变量，只是撞了名。解决办法叫标准化分离（standardizing apart）：合一前把其中一句的变量统一改名成全新的名字。把第二句的 x 改成 z₁₇ 后：

UNIFY(Knows(John, x), Knows(z₁₇, Elizabeth)) = {x/Elizabeth, z₁₇/John}

就正常了。这个改名步骤在前向链、后向链、归结里都是标配前置动作，初学者若忘了它，会莫名其妙地丢解。

### 8.3.4 occurs-check 与省略它导致的不健全

合一算法里有一个既昂贵又关键的步骤：当要把一个变量和一个复合项匹配时，必须检查这个变量本身是否出现在那个项内部；若出现，匹配必须失败，因为构造不出自洽的合一子。这就是 occurs-check（occur check）。

设想合一 x 与 f(x)。若允许 {x/f(x)}，代回去 x 变成 f(x)，而 f(x) 里的 x 又变成 f(f(x))……无限展开，不存在有限项能同时等于 x 和 f(x)。所以正确行为是失败。

省略 occurs-check 的代价是可能做出不健全（unsound）的推理。AIMA 明确指出："Some systems, including all logic programming systems, simply omit the occur check and sometimes make unsound inferences as a result."Prolog 就为了效率省掉了它——"The occur check is omitted from Prolog's unification algorithm. This means that some unsound inferences can be made"，只是在做数学定理证明之外很少出问题。Wikipedia 的 occurs-check 条目同样点明：省略 occurs-check 会导致不健全推理。这是一个规范（应做 occurs-check 以保健全）与实现（Prolog 为速度略去）分账的经典案例。

下面这段本机手写合一（含 occurs-check）跑出的输出复现了上述三种行为：

```
ex1: {'y': ('const','John'), 'x': ('func','Mother',[('var','y')])}   # {y/John, x/Mother(John)}
ex2: None                                                            # 同名 x 冲突 → 失败
ex3 occurs: None                                                     # UNIFY(x, f(x)) 被 occurs-check 拦下
```

（基线 Py3.11.15；ex1 与 AIMA p.278 的 {y/John, x/Mother(John)} 一致；ex2 未做标准化分离故失败；ex3 正是 occurs-check 生效的证据。）

### 8.3.5 UNIFY 算法与复杂度

MGU 的计算过程本身很朴素：递归地把两个表达式"并排"同步向下探，一路累积替换，一旦对应位置对不上就失败。变量对复合项时插入 occurs-check。

带 occurs-check 的朴素算法复杂度是被合一表达式规模的平方级（AIMA："makes the complexity of the entire algorithm quadratic in the size of the expressions being unified"）。存在更复杂的线性时间合一算法；Prolog 类系统则靠省略 occurs-check 换速度。这里同样是"标准要求 vs 实现取舍"的分账。

初学者读伪代码时抓住主干即可：相等就直接成功；一边是变量就走变量分支（先查已有绑定、再做 occurs-check、再登记绑定）；两边都是复合项就先合一函数符号、再逐个合一参数，任何一步失败则整体失败。

#### 来源与时效
- AIMA 第 4 版 2020，Ch9「Unification and Lifting」（GMP 定义与可靠性证明、UNIFY/MGU、standardizing apart、occur check、平方复杂度、Fig 9.1 UNIFY 伪代码）与 Ch9「Efficient implementation of logic programs」（Prolog 省略 occur check 致不健全）；引文取自 https://aima.cs.berkeley.edu/4th-ed/pdfs/newchap09.pdf 提取文本（2026-08-01 复核在架）。
- occurs-check 不健全性交叉印证：Wikipedia「Occurs check」https://en.wikipedia.org/wiki/Occurs_check （2026-08-01 复核，与 AIMA 一致：省略 occurs-check 在定理证明中可致不健全）。
- 本机实证：手写合一（含 occurs-check）复现 AIMA p.278 例与 occurs-check 失败，基线 Py3.11.15 @2026-07-25；仅作加固，不替代上述多来源比对。
- 冲突项：未见分歧。"应做 occurs-check（健全）"vs"Prolog 省略（快但可不健全）"是规范/实现分账，非来源冲突。

## 8.4 一阶前向/后向链（提升、Datalog 风格推理）

### 8.4.1 一阶定子句与 Datalog

前向链和后向链只适用于一类受限但常用的知识库——一阶定子句（definite clause）知识库。一阶定子句是若干原子语句的析取里恰好含一个正文字，通常写成"前提合取 ⇒ 单个结论"的蕴含形式，且默认变量全称量化。

Datalog 是其中一个更受限的子类：不含函数符号的一阶定子句集合。AIMA 原文："Datalog knowledge bases—that is, sets of first-order definite clauses with no function symbols."没有函数符号意味着不能靠 f(f(f(...))) 造出无穷多新项，这一限制让推理变得容易得多，也保证了后面说的多项式可解与终止性。

一般一阶定子句 ⊇ Datalog（去掉函数符号）。前者推理可能不终止，后者一定终止。

### 8.4.2 前向链 FOL-FC-ASK：数据驱动、不动点、完备性

一阶前向链（FOL-FC-ASK）是数据驱动的：从 KB 里已知的事实出发，触发所有前提被满足的规则，把结论加进已知事实，如此反复，直到查询被回答、或再也加不出新事实。每一步都是一次广义假言推理。注意"新事实"要排除只是变量改名的重复。

以 AIMA 的犯罪推理为例（West 卖导弹给敌国 Nono，问 West 是否有罪）：第一轮触发几条规则，导出 Sells(West, M1, Nono)、Weapon(M1)、Hostile(Nono)；第二轮触发主规则、导出 Criminal(West)；此后再无新结论。这种"加不出新东西"的状态叫不动点（fixed point）。

FOL-FC-ASK 是可靠的（每步都是 GMP）。它对定子句知识库是完备的——凡是被定子句 KB 蕴含的答案它都能给出。对 Datalog 知识库，完备性证明尤其简单：设谓词最大元数为 k、谓词数为 p、常量数为 n，则不同的基本事实至多 p·nᵏ 个，因此至多这么多轮就必达不动点。由此 AIMA 小结："Forward chaining is complete for Datalog programs and runs in polynomial time."

对一般的（含函数符号的）定子句知识库，前向链仍完备，但由于函数能造出无穷多新项，蕴含问题只是半可判定的——不动点可能永远到不了。这与 8.1.6 说的半可判定性一致。

### 8.4.3 后向链 FOL-BC-ASK：目标驱动与 Prolog

一阶后向链（FOL-BC-ASK）是目标驱动的：从查询（目标）出发，找结论能与目标合一的规则，把该规则的前提当作新的子目标，递归地往回证，直到子目标都落到 KB 里的事实上。它天然是深度优先的递归搜索。

Prolog 就是后向链的工程化身。它把子句写成 head :- body 形式，如

criminal(X) :- american(X), weapon(Y), sells(X,Y,Z), hostile(Z).

执行方式是深度优先后向链，按子句在程序里书写的先后顺序尝试。Prolog 有若干超出标准逻辑的特性需分账：内建算术（X is 4+3）靠执行代码而非推理；negation as failure（not P 只要证不出 P 就算成立，不是经典否定）；= 只做可合一检查（见 8.1.5）；以及前面强调过的省略 occurs-check。这些都是实现选择，不属规范一阶逻辑语义。

前向 vs 后向的取舍直觉：事实少、规则会引爆大量无关结论时，后向链（只顺着目标查）更省；需要一次性把所有可推结论都算出来（如演绎数据库）时，前向链更合适。

### 8.4.4 效率问题与优化方向

朴素前向链每轮都重扫全部规则、反复重算，效率很差。AIMA 指出几个优化方向。其一，把"一条规则的合取前提去 KB 里找满足的替换"看成一个约束满足问题（CSP）——"a Datalog clause can be viewed as defining a CSP, so matching will be tractable"，于是合取子目标的排序（先做约束最紧的）能大幅剪枝，这与 CSP 那一主题里的启发式呼应。其二，增量式前向链：每次只针对新 TELL 进来的事实去触发可能被它激活的规则（Rete 类算法思想），避免重复推理。其三，后向链里消除冗余计算可用 tabling/memoization。

初学者不必深究这些算法细节，抓住一点即可：一阶推理的实际瓶颈往往不在"规则对不对"，而在"如何高效地把规则前提和海量事实做模式匹配"，这本质是个组合搜索问题。

#### 来源与时效
- AIMA 第 4 版 2020，Ch9「Forward Chaining」（FOL-FC-ASK Fig 9.3、犯罪例、不动点、Datalog p·nᵏ 界与多项式完备性）、「Backward Chaining」与「Logic programming（Prolog）」（深度优先后向链、negation as failure、= 语义、省略 occur check）；引文取自 https://aima.cs.berkeley.edu/4th-ed/pdfs/newchap09.pdf （2026-08-01 复核）。
- GMP/前向链/Datalog 完备性与多项式性交叉印证：AIMA 2nd ed Ch9 讲稿 http://aima.eecs.berkeley.edu/2nd-ed/slides-ppt/m9-inference.ppt ；Stanford CS221 first-order modus ponens 讲义 https://stanford-cs221.github.io/autumn2020-extra/modules/logic/first-order-modus-ponens-6pp.pdf （均 2026-08-01 复核，结论一致：GMP 对定子句可靠且完备，Datalog 前向链多项式时间且终止）。
- 冲突项：未见分歧。Prolog 特性（negation as failure、省 occurs-check）属实现层，与规范语义分账标注。

## 8.5 一阶归结（斯科伦化、合一驱动的归结反证）

### 8.5.1 转换为合取范式 CNF

归结要求先把任意一阶语句转成合取范式（CNF：子句的合取，每个子句是文字的析取）。AIMA："Every sentence of first-order logic can be converted into an inferentially equivalent CNF sentence."且转换保持可满足性——CNF 不可满足当且仅当原句不可满足，这正是反证法（proof by contradiction）的基础。

流程与命题逻辑类似，主要多了处理量词的步骤。以"凡爱所有动物者必被某人所爱"为例，依次：消去蕴含（把 ⇒ 换成 ¬∨）；把否定内移到量词/连接词内层（用 8.1.4 的 ¬∀≡∃¬、¬∃≡∀¬）；标准化变量，把重名变量改开；斯科伦化，消去存在量词（见 8.5.2）；去掉全称量词（此时剩下的变量默认全称）；把 ∨ 对 ∧ 分配，整理成子句合取。

初学者只需记住转 CNF 是机械可自动化的、且结果通常很不可读——AIMA 说"humans seldom need look at CNF sentences—the translation process is easily automated"，重点是理解每一步在干什么，而非手算大式子。

### 8.5.2 斯科伦常量 vs 斯科伦函数

斯科伦化（Skolemization）是消去存在量词的过程。简单情形下它等同于存在实例化：把 ∃x P(x) 换成 P(A)，A 是一个全新常量，称斯科伦常量（Skolem constant）。

但当存在量词嵌在一个或多个全称量词的作用域内时，不能直接换常量。AIMA 用反例点明：若把

∀x [Animal(y) ∧ ¬Loves(x, y)] … ∃y Loves(y, x)

里的存在变量粗暴换成常量 B，会得到"存在某一个固定的 B 被所有人所爱"，意思全错——原句允许每个 x 被不同的人所爱。正确做法是让斯科伦实体依赖于外层的全称变量，换成一个斯科伦函数（Skolem function）作用在这些全称变量上：

∀x [Animal(F(x)) ∧ ¬Loves(x, F(x))] ∨ Loves(G(x), x)

这里 F、G 是斯科伦函数。总规则（AIMA 原文）："the arguments of the Skolem function are all the universally quantified variables in whose scope the existential quantifier appears."斯科伦化保持可满足性：斯科伦化后的句子可满足当且仅当原句可满足。

直觉上，斯科伦函数就是给"那个存在的对象"起个依赖于上下文的名字：F(x) 读作"x 可能不爱的那只动物"，随 x 不同而不同。这正是"是否落在全称量词作用域内"决定用常量还是函数的原因——存在的东西到底是"全局唯一一个"还是"每个 x 各有一个"。

### 8.5.3 一阶归结规则：合一驱动的二元归结与因式分解

一阶归结规则是命题归结的提升版。两个（已标准化分离、不共享变量的）子句，若各含一个互补文字就能归结。命题层"互补"指一个是另一个的否定；一阶层"互补"指一个能与另一个的否定合一。规则形式：

ℓ₁ ∨ … ∨ ℓₖ,   m₁ ∨ … ∨ mₙ
────────────────────────────────────────────────────
SUBST(θ, ℓ₁∨…∨ℓᵢ₋₁∨ℓᵢ₊₁∨…∨ℓₖ ∨ m₁∨…∨mⱼ₋₁∨mⱼ₊₁∨…∨mₙ)

其中 UNIFY(ℓᵢ, ¬mⱼ) = θ。即：找到一对能合一的互补文字，删掉它们，把两子句剩余部分并起来，再对整体施加合一子 θ。

AIMA 的例子：由 [Animal(F(x)) ∨ Loves(G(x), x)] 和 [¬Loves(u, v) ∨ ¬Kills(u, v)]，让 Loves(G(x), x) 与 ¬Loves(u, v) 互补，合一子 θ = {u/G(x), v/x}，得归结式 [Animal(F(x)) ∨ ¬Kills(G(x), x)]。

只用二元归结（每次恰好消一对文字）本身不完备，还需配合因式分解（factoring，把一个子句内可合一的多个文字并成一个，并把合一子作用于整个子句）。AIMA："The combination of binary resolution and factoring is complete."

### 8.5.4 反证法与归结的反驳完备性

归结证明 KB ⊨ α 的方式是反证：把 α 取反并入 KB，转成 CNF，反复归结，若能推出空子句（empty clause，无任何文字的子句，代表矛盾/假），就证明了 KB ∧ ¬α 不可满足，从而 KB ⊨ α。AIMA："Resolution proves that KB ⊨ α by proving KB ∧ ¬α unsatisfiable, i.e., by deriving the empty clause."

归结的核心性质是反驳完备（refutation-complete）：若一组子句不可满足，归结一定能从中推出空子句。AIMA："resolution is refutation-complete, which means that if a set of sentences is unsatisfiable, then resolution will always be able to derive a contradiction."

注意"反驳完备"不等于"能推出所有被蕴含的语句"——归结不保证生成一切逻辑后承，它保证的是：只要存在矛盾（即待证结论确实成立），就一定找得到那个矛盾。对初学者，这个区分很关键：归结是个"证伪反命题"的机器，不是"枚举所有真理"的机器。

反驳完备性的证明思路（Robinson）分层：先靠 Herbrand 定理把问题归约到基本（无变量）子句的命题归结，再用一条提升引理（lifting lemma）说明"任何基本层的归结证明，都能在一阶层用合一原样重演一遍"。AIMA 特别提到，正是为了证这条提升引理，Robinson 当年发明了合一。这再次回到本报告主线——合一是把命题推理提升到一阶的那把钥匙。

### 8.5.5 完备但不可判定：半可判定性再确认

把 8.1.6 的结论落到归结上：一阶归结是反驳完备的（有矛盾必能找到），但一阶蕴含整体只是半可判定的。这意味着当 KB ⊨ α 时归结保证停机给出证明；当 KB ⊭ α 时，归结过程可能永远产生新子句、永不停机，你无法据此断定"不蕴含"。

这不是归结算法的缺陷，而是一阶逻辑本身的固有边界（Church-Turing 不可判定）。任何一阶逻辑的完整证明过程都逃不出这个天花板。理解这一点，能帮初学者对"为什么自动定理证明器有时会挂着不返回"建立正确预期——那可能不是 bug，而是撞上了半可判定性。

#### 来源与时效
- AIMA 第 4 版 2020，Ch9「Resolution」（转 CNF 步骤与 Skolemization、Skolem constant vs Skolem function 规则、binary resolution + factoring 完备、反证/空子句、refutation-completeness、lifting lemma 与 Robinson 发明合一）；引文取自 https://aima.cs.berkeley.edu/4th-ed/pdfs/newchap09.pdf （2026-08-01 复核）。
- 斯科伦化与归结反驳完备性交叉印证：MTU CS4811 Ch9「Inference in FOL」讲义 https://pages.mtu.edu/~nilufer/classes/cs4811/2016-spring/lecture-slides/cs4811-ch09-inference-in-fol.pdf （基于 AIMA，2026-08-01 复核）；「Skolemization φ 不可满足 ⟺ Skolemized φ′ 不可满足」及 Robinson 归结完备性另见 EPFL「First-Order Logic — Syntax, Semantics, Resolution」讲义 https://lara.epfl.ch/w/_media/sar10/fol.pdf （2026-08-01 复核）。三源一致。
- 不可判定/半可判定边界：同 8.1.6 来源（NMSU 讲义、SEP）。
- 冲突项：未见实质分歧。个别教材用"most general unifier 唯一性 up to renaming"表述，与 AIMA 一致，非冲突。

---

参考锚点（一手承重 + 交叉印证）：AIMA 第 4 版 2020 Ch8–9（https://aima.cs.berkeley.edu/contents.html ；Ch9 PDF https://aima.cs.berkeley.edu/4th-ed/pdfs/newchap09.pdf ）为本报告一手承重；MTU CS4811 Ch9 讲义、EPFL FOL 讲义、Stanford CS221 逻辑模块、Wikipedia Occurs-check、NMSU「一阶逻辑不可判定」讲义、SEP「Gödel 不完备性」为独立交叉印证来源。CS188 Sp25（https://inst.eecs.berkeley.edu/~cs188/sp25/ ）本主题覆盖有限，一阶逻辑与推理以 AIMA 承重。所有链接 2026-08-01 复核在架。
