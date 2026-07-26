# L3-04·大主题9 声明式与元语言抽象（选修广度）

> 基线 Py3.11.15/np2.4.6/gcc13.3 @2026-07-25 ｜ 核实日期：2026-07-26 ｜ 先修：L3-04 大主题1（范式总览：命令式/OO/函数式/声明式）、大主题7（函数式：一等/高阶函数、闭包）、大主题8（求值模型、环境模型、惰性求值引申）；L1-01（表达式求值、递归） ｜ 一手锚点：SICP《Structure and Interpretation of Computer Programs》第 4 章 Metalinguistic Abstraction（§4.1 元循环求值器、§4.2 惰性求值、§4.3 非确定性计算、§4.4 逻辑编程），https://sarabander.github.io/sicp/html/index.xhtml ；独立第二版 SICP JavaScript Edition（Abelson & Sussman，Henz & Wrigstad 改编，MIT Press 2022，Ch4 同结构）；求值策略/逻辑编程/amb 的领域一手（Haskell 2010 Report 非严格语义、Colmerauer/Kowalski 逻辑编程、McCarthy 1963 amb） ｜ 成熟度：GA/稳定（SICP Ch4 为经典教材内容，无版本漂移；所标语言历史事实为定论）

> 粒度判定：**1 份**（不拆）。理由：本大主题只含 3 个小主题（9.1–9.3），且 prompt 明确定位为"选修广度"——只作范式概览，机制深挖显式归 L5-06（PL 理论）与 L1-01（CS61A 解释器/SQL），此处不重复立项。三个小主题共享同一条主线（"用一门语言去描述/改写另一门语言的求值规则"，即元语言抽象），合为一份能保住"求值器→改求值策略→改范式"的教学序，拆开反而割裂对照。篇幅适中，无拆分理由。

本报告的 Python 玩具示例（toy eval、微型规则引擎、amb 直觉）均为基线环境（Python 3.11.15、Linux 6.18.5 x86_64）下的真实运行结果，命令输出随节贴出；这些是"帮助理解 SICP Scheme 机制"的直觉旁证，不替代对 SICP 一手章节的多来源比对。

---

## 9.1 元循环求值器视角 — 解释器即抽象

### 9.1.1 元循环求值器是什么

元循环求值器（metacircular evaluator）指的是：**用语言 L 自己写一个能求值 L 程序的解释器**。SICP §4.1 给出的正是一个"用 Lisp 写的、能跑 Lisp 的求值器"，它之所以叫"metacircular（元循环）"，就是因为被解释的语言和写解释器所用的语言是同一门。SICP 的理由是：求值本身是一个过程，而 Lisp 正是我们用来描述过程的工具，所以用 Lisp 来描述"Lisp 的求值过程"是恰当的。

对初学者，先破一个误解：解释器不是什么神秘的黑箱，它就是一个**普通程序**，输入是"另一段程序的表示（一棵表达式树）"，输出是"这段程序的求值结果"。既然它只是个普通程序，那当然可以用被解释的那门语言自己写出来。这一步跨越很关键——它说明"语言"和"用语言写的程序"之间没有不可逾越的墙：语言的语义可以被写成一段代码，于是语义就成了可以阅读、可以修改的对象。这正是"元语言抽象（metalinguistic abstraction）"这一整章的主题——把"语言"本身当成可以设计和改造的抽象。

### 9.1.2 eval/apply 相互递归的循环

求值器的心脏是两个相互递归的过程 `eval` 与 `apply`，它们构成 SICP 所说的 eval–apply 循环：

eval（表达式, 环境）：按表达式的**类型**分派——自求值的（数、字符串）直接返回；变量到环境里查值；特殊形式（`quote`/`if`/`lambda`/`define`/`begin`/`cond` 等）各按其规则处理；一般的**组合式**（过程调用）则先递归 eval 出运算符和各实参的值，再交给 apply。

apply（过程, 实参表）：若是**基本过程**（原语），直接施行；若是**复合过程**（用户定义的 lambda），就在"该过程被创建时所记住的环境"上**扩展一层**新框架、把形参绑定到实参，然后在这个新环境里 eval 过程体——于是又回到 eval。

这两者你调我、我调你，直到把表达式一路化简到基本值和基本过程为止。SICP 说这个循环"揭示了一门计算机语言的本质"。给初学者的心智模型：eval 负责"看这段代码是什么形状、该怎么拆"，apply 负责"把一个函数真正作用到一组已经算好的值上"。求值一个嵌套表达式（如 `(+ 1 (* 2 3))`），就是 eval 先把它拆成"运算符 + 若干子表达式"，递归 eval 每个子表达式拿到值，再 apply 把 `+` 作用上去——递归下降、逐层归约。特别注意 apply 复合过程时"在过程记住的环境上扩展新框架"这一条，正是 L3-04 大主题8 环境模型的落地，也是闭包与词法作用域之所以成立的原因。

### 9.1.3 一个能跑的玩具 eval（Python 旁证）

把上面的 eval/apply 循环用 Python 写成一个只认算术的迷你求值器，直观感受"解释器就是普通程序"。真实运行输出：

```python
def std_env():
    import operator as op
    return {'+': op.add, '-': op.sub, '*': op.mul, '/': op.truediv}

def evl(x, env):
    if isinstance(x, (int, float)):       # 自求值
        return x
    if isinstance(x, str):                # 变量：查环境
        return env[x]
    op, *args = x                         # 组合式
    proc = evl(op, env)                   # 先 eval 运算符
    vals = [evl(a, env) for a in args]    # 再 eval 各实参
    return apply_(proc, vals)             # 交给 apply

def apply_(proc, vals):
    return proc(*vals)

env = std_env()
print(evl(['+', 1, ['*', 2, 3]], env))    # (+ 1 (* 2 3))
print(evl(['-', ['/', 10, 2], 1], env))   # (- (/ 10 2) 1)
```

```text
7
4.0
```

这段代码就是 SICP eval/apply 循环的最小骨架：`evl` 按"是数 / 是变量 / 是组合式"分派（对应 SICP 的表达式分类），组合式先递归求出运算符与实参的值、再 `apply_` 施行。它当然是玩具（没有 `if`/`lambda`/环境扩展），但足以让初学者亲眼看到：`(+ 1 (* 2 3))` 得到 7，靠的不过是"递归拆开、逐层归约"这一件事。要长成真正的解释器，只需继续给 `evl` 添加特殊形式分支和环境框架——这正是 SICP §4.1 一步步做的，也是 L1-01（CS61A 的 Calculator/Scheme 解释器项目）与 L5-06 深挖的方向。

### 9.1.4 "解释器即抽象"的意义与跨课边界

把语言的语义写成一段可改的代码后，最有威力的一点是：**改几行求值规则，就等于设计了一门新语言**。9.2/9.3 就是明证——在同一个求值器骨架上改写 apply/eval 的规则，Scheme 就分别变成了逻辑编程语言、惰性语言、非确定性语言。这就是本章标题"元语言抽象"的含义：语言不是天上掉下来的固定物，而是可以被当作抽象来设计、叠加、定制的产物。这也回应了大主题1 里"范式=风格而非语言"的说法——很多范式差异，本质上是求值规则的差异。

跨课边界要讲清，避免读者以为这里会深挖：解释器/编译器的完整实现（词法分析、语法分析、尾递归优化、编译到寄存器机）归 L5-06 与编译原理课；SQL 作为声明式查询语言的机制归 L1-01/数据库课。本节只立"解释器是可读可改的普通程序、eval/apply 是其骨架"这一范式直觉，不下机制深水区。

#### 来源与时效（本小主题末集中列）
- SICP §4.1 The Metacircular Evaluator（§4.1.1 The Core of the Evaluator、§4.1.2 Representing Expressions、§4.1.3 Evaluator Data Structures、§4.1.4 Running the Evaluator as a Program）：metacircular 定义、eval/apply 相互递归、"用 Lisp 描述求值过程"的论证，https://sarabander.github.io/sicp/html/4_002e1.xhtml ，核实 2026-07-26。
- SICP JavaScript Edition（Abelson & Sussman；Henz & Wrigstad 改编，MIT Press 2022）Ch4「Metalinguistic Abstraction」§4.1 同结构的元循环求值器——独立第二版本作交叉核对，核实 2026-07-26。
- 本机旁证：上述玩具 eval 在 Python 3.11.15 下 `(+ 1 (* 2 3))=7`、`(- (/ 10 2) 1)=4.0`，核实 2026-07-26。

## 9.2 逻辑/关系式编程 — 声明式范式一支

### 9.2.1 声明式：只说"是什么"，不说"怎么做"

逻辑编程是声明式范式的代表分支：程序由**事实（facts）**和**规则（rules）**组成，你向系统提出**查询（query）**，系统去推导出满足条件的所有答案——你描述"想要什么样的结果"，而**不**编写"一步步怎么算出来"的过程。SICP §4.4 把这一对立点讲得最清楚，引用其判据：

计算机科学处理的是命令式（imperative，"how to"，怎么做）知识，而数学处理的是声明式（declarative，"what is"，是什么）知识。

逻辑编程的目标，正是让程序更靠近"数学式的、只说是什么"的一端。

初学者最好的抓手是对比：命令式写"排序"，你要写出交换、循环、下标这些步骤；声明式写"排序结果"，你只需说明"输出是输入的一个排列，且相邻元素非降序"，至于怎么搜出这个结果交给系统。SICP §4.4 的查询语言就是这种风格——同一条规则（如"祖父 = 父亲的父亲"）既能用来问"谁是某人的祖父"，也能反过来问"某人是谁的祖父"，因为你陈述的是**关系**而非**单向的计算过程**。这种"一条定义、多个方向都能用"的双向性，是关系式/逻辑编程区别于普通函数最直观的特征。

### 9.2.2 事实、规则、查询与合一（unification）

逻辑编程的三块积木：事实是无条件为真的断言（如"alice 是 bob 的 parent"）；规则是带条件的断言（如"若 x 是 y 的 parent 且 y 是 z 的 parent，则 x 是 z 的 grandparent"）；查询是带**变量**的模式，系统去找所有能让它成立的变量取值。系统跑起来靠两台发动机——模式匹配与合一：

模式匹配（pattern matching）：只有**数据**一侧含具体值、**模式**一侧含变量，测试数据是否符合模式并给变量赋值。

合一（unification）：SICP 称其为"实现规则所需的模式匹配的推广"——**两侧都可以含变量**，系统求出一组变量赋值，使两个模式变得相等。

给初学者的直觉：模式匹配像"拿一个带空格的模板去套一条具体记录"；合一更强，是"两个都带空格的模板互相对齐，一起把空格填一致"。规则之所以能链式推理（用一条规则的结论去满足另一条规则的前提），正是因为合一允许两侧都有变量、能把它们绑定到一致的值。SICP §4.4 里查询系统的运转方式是"以一条**帧流（stream of frames）**流过各个子查询"：每个帧是一组当前的变量绑定，子查询不断筛选、扩展这些帧——这也顺带把大主题8 的"流/惰性"与本章串了起来。

### 9.2.3 一个微型规则引擎（Python 直觉旁证）

把"事实 + 一条 grandparent 规则 + 按模式查询"用最朴素的方式在 Python 里搭出来，感受"声明关系、系统去搜"。真实运行输出：

```python
facts = [("parent","alice","bob"),("parent","bob","carol")]

def match(pat, fact, binds):          # 单向模式匹配 + 变量绑定
    if len(pat)!=len(fact): return None
    b=dict(binds)
    for p,f in zip(pat,fact):
        if isinstance(p,str) and p.startswith("?"):
            if p in b:
                if b[p]!=f: return None    # 已绑定则须一致
            else: b[p]=f
        elif p!=f: return None
    return b

def query(pat):
    return [m for f in facts if (m:=match(pat,f,{})) is not None]

# 规则 grandparent(?x,?z) :- parent(?x,?y), parent(?y,?z)
def grandparent():
    out=[]
    for f1 in facts:
        b=match(("parent","?x","?y"),f1,{})
        if not b: continue
        for f2 in facts:
            b2=match(("parent",b["?y"],"?z"),f2,b)   # 用 ?y 的绑定去连下一条
            if b2: out.append((b2["?x"],b2["?z"]))
    return out

print(query(("parent","?p","carol")))   # 谁是 carol 的 parent？
print(grandparent())                     # 由规则推导出的关系
```

```text
[{'?p': 'bob'}]
[('alice', 'carol')]
```

读法：`query(("parent","?p","carol"))` 只声明了"我要 carol 的 parent"，系统扫事实库、把 `?p` 绑成 `bob`；`grandparent()` 演示了规则如何链式推理——第一条子目标 `parent(?x,?y)` 绑出 `?y=bob`，把这个绑定**带进**第二条子目标 `parent(bob,?z)`，最终推出 `alice` 是 `carol` 的祖父。注意这里"把 ?y 的绑定带进下一步"就是合一/帧流思想的最朴素版本。这只是直觉玩具（未做真正的双向合一、未做回溯搜索、只处理单变量前缀 `?`），真正的查询系统与 Prolog 深挖归 L5-06；本节到"声明关系、系统去搜、规则可链式推理"为止。

### 9.2.4 与数学逻辑的差距、历史与易错点

务实的边界要点：逻辑编程**不等于**纯数学逻辑。SICP §4.4 明确指出，当代逻辑编程语言存在实质缺陷——其通用的"how to"方法可能陷入**虚假的无限循环**或其他不良行为。也就是说，你写下的是声明式的关系，但底层仍有一套具体的搜索/求解策略（如深度优先 + 回溯），这套策略的顺序、终止性会实实在在影响程序能不能跑出结果。这正是 Kowalski 那句名言"算法 = 逻辑 + 控制"（Algorithm = Logic + Control，CACM 1979）的意思：你写的"逻辑"部分是声明式的，但"控制"部分（求解顺序）依然存在，不能完全忽视。

历史脉络帮助定位这一分支（作范式广度的背景，非深挖）：逻辑编程的代表语言 Prolog 由 Alain Colmerauer 与 Philippe Roussel 于 1972 年前后在马赛提出（"Prolog" = programmation en logique），其理论基础是 Robert Kowalski 对 Horn 子句的过程式解释。初学者易错点：把"声明式"误解为"没有性能/终止性问题、写了就一定能自动跑对"。恰恰相反——声明式让你少写步骤，但求解引擎的搜索策略仍可能不终止或效率极差；换个子句书写顺序，Prolog 的行为就可能天差地别。与前后关联：本节的"关系/规则"是 SQL（另一门声明式语言）的近亲，SQL 深挖归 L1-01/数据库课；求解机制（合一算法、resolution、回溯）深挖归 L5-06，本报告只作范式广度。

#### 来源与时效（本小主题末集中列）
- SICP §4.4 Logic Programming（§4.4.1 Deductive Information Retrieval、§4.4.2 How the Query System Works）：declarative/imperative 判据、unification 定义（"pattern matching 的推广"）、帧流处理、当代逻辑编程"虚假无限循环"的缺陷说明，https://sarabander.github.io/sicp/html/4_002e4.xhtml ，核实 2026-07-26。
- SICP JavaScript Edition（MIT Press 2022）§4.4「Logic Programming」——独立第二版本交叉核对声明式 vs 命令式与查询系统结构，核实 2026-07-26。
- 逻辑编程史一手：Prolog 由 Colmerauer & Roussel 于 1972 年在马赛实现，基于 Kowalski 对 Horn 子句的过程式解释；Kowalski《Algorithm = Logic + Control》(CACM, 1979 年 7 月)——"逻辑 + 控制"分账的原始出处，交叉自 doc.ic.ac.uk/~rak（Kowalski 主页）与《Fifty Years of Prolog and Beyond》(arXiv:2201.10816)，核实 2026-07-26。
- 本机旁证：上述微型规则引擎在 Python 3.11.15 下输出 `[{'?p': 'bob'}]` 与 `[('alice', 'carol')]`，核实 2026-07-26。

## 9.3 惰性/非确定性求值广度 — 求值策略变体

### 9.3.1 应用序 vs 正则序、惰性求值与 thunk

"求值策略"回答的是一个基础问题：**调用一个过程时，实参在什么时候被求值？** SICP §4.2 给出两种：

应用序（applicative order）：调用前**先把所有实参求值完**，再进入过程体。这是 Scheme、Python、C 等绝大多数语言的默认。

正则序（normal order）：**先不求实参**，把未求值的实参表达式直接代入过程体，直到它的值真正被需要（例如被某个基本操作用到）时才求。

惰性求值（lazy evaluation）就是把实参的求值"推迟到最后一刻"的正则序策略。要实现它，求值器给每个被推迟的实参打一个包裹，叫 thunk——SICP 定义 thunk 为一个对象，里面装着"实参表达式 + 该过程调用发生时所在的环境"。当这个值真正被需要时，thunk 被**强制（force）**：取出里面的表达式、在记住的环境里求值。为避免同一个 thunk 被反复强制而重复计算，可加**记忆化（memoization）**——首次强制时把算出的值缓存，之后直接返回缓存。

给初学者的直觉与"为什么有用"：应用序像"点菜时厨房把所有菜一次全做好"，惰性像"你真吃到哪道才现做那道"。惰性最直接的好处是**能跳过不需要的计算**——SICP 举的例子是自定义一个 `unless`：只有惰性求值下，没被选中的那个分支才不会被求值，否则会白算甚至触发本不该发生的错误。thunk 这个"延迟计算的包裹"务必和大主题7 的闭包联系起来看：两者都靠"把表达式连同它的环境一起打包、以后再用"——thunk 本质就是一个"待强制的闭包"。

### 9.3.2 惰性带来的能力：流即惰性表（Python 生成器旁证）

惰性求值最漂亮的产物是**流（stream）**。SICP §4.2.3 指出：一旦有了惰性求值，流和普通表就**变成同一样东西**——`cons`/`car`/`cdr` 可以实现成非严格（non-strict）的，于是无需显式的 `delay`/`force` 就能构造**无穷数据结构**。Python 里最接近这一直觉的是生成器：它按需产生元素，可以表示"无穷序列"而不会一次算完撑爆内存。真实运行输出：

```python
def integers(start=1):        # 概念上的无穷流
    n=start
    while True:
        yield n; n+=1

import itertools
print(list(itertools.islice(integers(), 5)))   # 只取前 5 个
```

```text
[1, 2, 3, 4, 5]
```

`integers()` 写的是一个"永远产整数"的无穷过程，但 `islice(..., 5)` 只**按需**拉了 5 个出来——中间根本没有"先把无穷个整数算完"这回事。这正是惰性的价值：把"如何产生"与"实际要用多少"解耦，让无穷/庞大的结构在需要的粒度上才被真正计算。注意生成器只是"帮助理解惰性"的类比，并非 SICP 的 thunk/force 机制本身；Scheme 流、`delay`/`force`、记忆化的完整实现与惰性语言 Haskell 深挖归大主题8 与 L5-06。

### 9.3.3 非确定性求值与 amb

SICP §4.3 换一个方向改求值器：加入非确定性计算。核心是一个特殊形式 amb：

(amb ⟨e₁⟩ ⟨e₂⟩ … ⟨eₙ⟩)

它"歧义地"返回这 n 个表达式之一的值——比如 `(amb 1 2 3)` 可能是 1、2 或 3 中任意一个，取决于哪条分支最终能让整个程序**成功**。SICP 把非确定性求值刻画为"探索一组可能世界，每个世界由一组选择确定"——与流那种"一次给出所有答案的、无时间的序列"不同，非确定性下"时间会分叉，程序有多条可能的执行历史"。

给初学者的心智模型：amb 是一个"许愿点"，你在某处说"这里可以是 1 或 2 或 3"，然后继续往下写**约束条件**；求值器会替你**自动**去试各种组合，只保留能满足全部约束的那条路。配套的约束工具是 `require`：

```scheme
(define (require p)
  (if (not p) (amb)))
```

`(amb)`（空的 amb，无任何选择）表示"此路不通/失败"。所以 `require` 的意思是"若谓词不成立，就宣告当前这条路失败"，逼求值器回头换别的选择。这样你就能用"声明式"的口吻写搜索问题：先 amb 出候选、再 require 掉不合法的，剩下的就是解——不用手写任何循环和回溯代码。

### 9.3.4 自动搜索/回溯，与流的区别（Python amb 直觉旁证）

amb 求值器背后是**深度优先搜索 + 自动回溯**：遇到 amb 时先选第一个候选；若后续（常因某个 `require` 失败）走不通，就**回溯到最近的选择点**，试下一个候选；某个 amb 的候选全部试尽，就再往上回溯到更早的选择点。整套搜索由求值器代劳，程序员只写"有哪些选择 + 要满足什么约束"。用 Python 生成器把这套"枚举候选 + 约束剪枝 + 自动回溯"的直觉搭出来（求勾股数三元组），真实运行输出：

```python
def amb(*choices):
    for c in choices:
        yield c

def pyth_triples(n):
    for a in amb(*range(1, n+1)):
        for b in amb(*range(a, n+1)):        # b>=a 去重
            for c in amb(*range(b, n+1)):
                if a*a + b*b == c*c:         # 相当于 require 约束
                    yield (a, b, c)

print(list(pyth_triples(20)))
```

```text
[(3, 4, 5), (5, 12, 13), (6, 8, 10), (8, 15, 17), (9, 12, 15), (12, 16, 20)]
```

嵌套的 `for ... in amb(...)` 就是"三个选择点"，`if a*a+b*b==c*c` 就是 `require` 约束；Python 的嵌套循环回退天然实现了"深度优先 + 回溯"——一组 `(a,b,c)` 不满足约束就自动试下一组。这直观再现了 SICP amb 的效果：你只声明了"三个数各在 1..20 里选、且要满足勾股定理"，搜索过程是自动的。当然这只是直觉类比，真正的 amb 求值器用**续延（continuation）**记住选择点以便任意回溯，机制深挖归 L5-06。

与流的区别值得点明（易错点）：流用惰性把"答案的产生"与"答案的组装"解耦，概念上所有答案是一个**无时间**的序列；非确定性则是**真的在多条执行路径间搜索、动态地选值**，"时间分叉"。历史锚点（作范式广度背景）：amb（ambiguous function，歧义函数）由 John McCarthy 于 1963 年在《A Basis for a Mathematical Theory of Computation》中提出，SICP §4.3 是其在教学求值器中的经典落地。跨课边界：惰性与非确定性都属"改求值策略"，与 9.1 的元循环求值器是同一条主线（改 eval/apply 的规则就得到新语言的语义），完整实现与形式化归 L5-06。

#### 来源与时效（本小主题末集中列）
- SICP §4.2 Lazy Evaluation（§4.2.1 Normal Order and Applicative Order、§4.2.2 An Interpreter with Lazy Evaluation、§4.2.3 Streams as Lazy Lists）：应用序/正则序定义、thunk 定义（"表达式 + 环境"的延迟包裹）、force、记忆化、"流即惰性表"，https://sarabander.github.io/sicp/html/4_002e2.xhtml ，核实 2026-07-26。
- SICP §4.3 Nondeterministic Computing（§4.3.1 Amb and Search、§4.3.2 Examples、§4.3.3 Implementing the Amb Evaluator）：amb 特殊形式语义、"可能世界/时间分叉"、深度优先 + 自动回溯、`require` 定义，https://sarabander.github.io/sicp/html/4_002e3.xhtml ，核实 2026-07-26。
- SICP JavaScript Edition（MIT Press 2022）§4.2「Lazy Evaluation」与 §4.3「Nondeterministic Computing」——独立第二版本交叉核对求值策略变体，核实 2026-07-26。
- 领域一手交叉：惰性/非严格语义作为语言默认，见 Haskell 2010 Language Report（Haskell 为 non-strict/lazy 函数式语言）作第二独立来源；amb 起源为 McCarthy《A Basis for a Mathematical Theory of Computation》(1963)，交叉自 PLS Lab「Amb」词条，核实 2026-07-26。
- 本机旁证：无穷流生成器取前 5 项得 `[1,2,3,4,5]`；amb 直觉求勾股三元组（n=20）得 `[(3,4,5),(5,12,13),(6,8,10),(8,15,17),(9,12,15),(12,16,20)]`，Python 3.11.15，核实 2026-07-26。
