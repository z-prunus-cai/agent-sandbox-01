# L3-04·大主题7 函数式范式

> 基线 Py3.11.15/np2.4.6/gcc13.3 @2026-07-25 ｜ 核实日期：2026-07-26 ｜ 先修：L1-01（函数定义/参数/返回值、作用域 LEGB、闭包雏形）；L3-04 大主题1（编程范式总览，§1.3「函数式范式概览：表达式 + 无副作用」） ｜ 一手锚点：SICP（Abelson & Sussman，2nd ed）§1.3「Formulating Abstractions with Higher-Order Procedures」（§1.3.1 Procedures as Arguments、§1.3.2 Lambda、§1.3.4 Procedures as Returned Values）、§2.2.1「Representing Sequences」（含「Mapping over lists」引入 `map`）、§2.2.3「Sequences as Conventional Interfaces」（`filter`/`accumulate`），https://sarabander.github.io/sicp/html/index.xhtml ；CMU 15-150《Principles of Functional Programming》(Fall 2015, SML/NJ 110.75)，https://www.cs.cmu.edu/~iliano/courses/15F-CMU-CS150/syllabus.shtml ；Python 3.11 官方文档（`functools.reduce`、内置 `map`/`filter`、Data Model） ｜ 成熟度：GA/稳定（FP 概念为经典范式，无版本漂移；Python API 自 Py3 起 `map`/`filter` 返回惰性迭代器、`reduce` 移入 `functools`）

> 粒度判定：**1 份**（不拆）。理由：本大主题 5 个小主题（7.1–7.5）是一条连贯教学序——从「函数是值」（7.1）出发，到「函数带着环境走」（7.2 闭包），再到「不带可变状态地算」（7.3 纯/不可变），落到最常用的「用高阶函数做序列变换」（7.4 map/filter/reduce），最后收束于「把副作用挤到边缘」的工程直觉（7.5）。按 v3「广度优先、讲懂不纵深」的边界，λ 演算的形式化根、求值策略的机制深挖显式归 L5-06，本报告不下形式化结论；总篇幅适中且共享同一主线（「以表达式而非状态变更来构造程序」），拆开反而割裂对照，故合为 1 份。

本报告的 Python 示例（高阶函数、晚绑定 bug、不可变性、map/filter/reduce、纯核心重构）均为基线环境（Python 3.11.15、Linux 6.18.5 x86_64）下的真实运行结果，命令与输出随节贴出；CMU 15-150 以 SML 授课，本报告用 Python 佐证同一概念，规范（SICP/15-150 的语言无关论述）与实现（Python 具体行为）分别标注。

---

## 7.1 一等与高阶函数 — 函数作参数/返回值

### 7.1.1 一等函数：函数本身就是一种「值」

一等函数（first-class functions）指的是：在这门语言里，函数和整数、字符串一样是**头等公民**——可以赋给变量、装进列表/字典、当作参数传给别的函数、也可以当作另一个函数的返回值。凡是"值能待的地方，函数都能待"，就说函数是一等的。

初学者的抓手：在 L1-01 你调用函数写的是 `f(3)`——**带括号**表示"执行它"。这里要建立的新认知是 `f`（**不带括号**）本身也是一个可以搬来搬去的东西，它的值是"那段还没被执行的代码 + 它记住的环境"。区分 `f` 与 `f()` 是理解整章的第一道门槛：前者是"函数这个对象"，后者是"调用它得到的结果"。

真实运行，展示函数被当值对待：

```python
def square(x): return x * x
f = square              # 把函数赋给变量（不加括号）
box = [square, len]     # 把函数装进列表
print(f(5), box[0](5), box[1]([1,2,3]))
```

```text
25 25 3
```

`f`、`box[0]` 和 `square` 指向同一个函数对象，加上括号才触发执行。这一点是后面 7.2 闭包、7.4 高阶变换全部成立的基础。

### 7.1.2 高阶函数：以函数为参数

高阶函数（higher-order procedure，SICP §1.3 的用词）是指**接受函数作为参数，或返回函数作为结果**（满足其一即可）的函数。它让"计算的模式"本身可以被抽象和复用。

SICP §1.3.1 的经典动机：求"从 a 到 b 各整数之和"、"各整数立方之和"、"某收敛级数之和"，三段代码骨架完全一样，只有"对每一项做什么"和"如何取下一项"不同。把这两处"变化的部分"提取成函数参数，就得到一个通用的 `sum`：

```python
def sum_term(term, a, nxt, b):
    total = 0
    while a <= b:
        total += term(a)     # term 是传进来的函数
        a = nxt(a)
    return total

print(sum_term(lambda x: x,      1, lambda x: x+1, 5))   # 1+2+3+4+5
print(sum_term(lambda x: x*x*x,  1, lambda x: x+1, 3))   # 1+8+27
```

```text
15
36
```

同一个 `sum_term` 靠传入不同的 `term`/`nxt` 表达了两种求和——"求和"这个模式被抽象出来复用了。这就是高阶函数管理复杂度的方式：把"骨架"和"填空"分离。

### 7.1.3 以函数为返回值：函数工厂

函数不仅能当参数，还能当返回值（SICP §1.3.4「Procedures as Returned Values」）。一个函数在运行时**造出并返回另一个函数**，常被称作"函数工厂"。

```python
def adder(n):
    def add(x):
        return x + n     # add 记住了外层的 n
    return add           # 返回内层函数本身

add5 = adder(5)
print(add5(3), add5(10))
```

```text
8 15
```

`adder(5)` 返回的 `add5` 是一个"专门加 5"的新函数；`adder(100)` 会造出"专门加 100"的另一个函数。返回的函数记住了造它时的 `n`——这正是 7.2 闭包的引子。SICP §1.3.4 的 `average-damp`（接受一个函数、返回它的"平均阻尼"版本）就是同一模式：输入一个函数，输出一个被改造过的函数。

### 7.1.4 为什么重要与易错点

高阶函数是函数式范式的"发动机"：它让你用组合小函数的方式搭出复杂计算，而不是靠改一堆变量的状态。7.4 的 map/filter/reduce 全是高阶函数，7.2 的闭包是"返回函数"的必然产物，7.5 的"策略传函数"也建立在此。

最常见的初学者错误是**该传函数却传成了调用结果**：写 `sum_term(square(1), …)` 传进去的是 `1`（`square(1)` 的结果）而不是函数 `square`。记住规则——要"把这段计算交给别人以后再执行"就传 `f`（不加括号），要"现在就要结果"才写 `f()`。

#### 来源与时效（本小主题末集中列）
- SICP §1.3「Formulating Abstractions with Higher-Order Procedures」：§1.3.1 以 `sum` 抽象求和模式（procedures as arguments）、§1.3.4 `average-damp`（procedures as returned values），https://sarabander.github.io/sicp/html/1_002e3.xhtml ，核实 2026-07-26。
- CMU 15-150 syllabus：将 higher-order functions 列为核心技术之一（SML 授课），https://www.cs.cmu.edu/~iliano/courses/15F-CMU-CS150/syllabus.shtml ，核实 2026-07-26。
- 本机旁证：上述 `sum_term`、`adder` 在 Python 3.11.15 分别输出 `15/36`、`8 15`，核实 2026-07-26。

## 7.2 闭包与自由变量捕获 — 晚绑定陷阱

### 7.2.1 闭包是什么、自由变量与绑定变量

闭包（closure）是"一个函数 + 它定义时所在的那个环境（外层变量的引用）"打包在一起的整体。当一个内层函数用到了**不是自己参数、也不是自己局部变量**的名字时，这些名字叫**自由变量（free variable）**；闭包让内层函数即使被返回到外层之外、外层函数早已结束，仍能访问这些自由变量。

区分两类变量是理解闭包的关键。在 `def add(x): return x + n` 里，`x` 是 `add` 的参数（**绑定变量 / bound variable**，由 `add` 自己引入）；`n` 不是 `add` 引入的，来自外层（**自由变量**）。闭包"闭合"的就是这些自由变量——把它们从"悬空的名字"变成"有确定出处的引用"。7.1.3 的 `add5` 就是闭包：`adder` 返回后早已退出，`add5` 却还能用当时的 `n=5`。

### 7.2.2 捕获的是变量，还是当时的值？

这是闭包最反直觉、也最容易出 bug 的一点：Python（以及多数语言）的闭包**捕获的是变量本身（一个引用/绑定），而不是创建闭包那一刻该变量的值**。也就是说，闭包读到的是自由变量**被调用时**的当前值，而不是**被定义时**的快照。

一句话心智模型：闭包记住的是"去哪儿找这个变量"（一个地址），不是"当时它等于几"（一张照片）。只要那个变量后来变了，闭包下次读到的就是变后的新值。这正是下一节 bug 的根源，也称"晚绑定（late binding）"。

### 7.2.3 循环中 lambda 的晚绑定 bug

在循环里批量制造闭包时，晚绑定会咬人。真实运行：

```python
funcs = [lambda: i for i in range(3)]
print([f() for f in funcs])
```

```text
[2, 2, 2]
```

期望多半是 `[0, 1, 2]`，实际全是 `2`。原因：三个 lambda 都捕获**同一个变量 `i`**（不是三个各自的值）；循环结束时 `i` 的最终值是 `2`，等到 `f()` 真正执行才去读 `i`，读到的自然都是 `2`。这里必须分清"函数**定义**发生在循环中，函数**调用**发生在循环后"——晚绑定读的是调用时的值。

修复办法是在**定义闭包时就把当前值固定下来**，最常用的是默认参数（默认值在定义时求值并绑定）：

```python
funcs = [lambda i=i: i for i in range(3)]   # 默认参数 i=i 在定义时抓住当前值
print([f() for f in funcs])
```

```text
[0, 1, 2]
```

另一种等价修法是用 7.1.3 的"函数工厂"：`[make(i) for i in range(3)]`，靠每次调用 `make` 产生一个各自独立的参数绑定。两法本质相同——都是把"共享的循环变量"换成"每个闭包独享的一份绑定"。

### 7.2.4 关联与语言对照

关联：闭包是 7.1"函数作返回值"的直接产物，也是 7.5"用闭包携带配置的纯函数"以及大主题8"用闭包造带局部状态的计数器"的共同基础。CMU 15-150 因用不可变的 SML，闭包捕获的自由变量本身不可变，天然没有这个 bug；晚绑定坑主要出现在带可变绑定的语言（Python、老式 JavaScript 的 `var`）。

易错点补充：JavaScript 里同样的循环若用 `var` 声明计数器会复现此 bug，用 `let`（块级作用域、每轮迭代一个新绑定）则天然正确——这从反面印证"问题不在闭包本身，而在被捕获的是不是每轮独立的绑定"。λ 演算里的自由/绑定变量与 α-变换等形式化根源归 L5-06，本节只到"捕获变量而非值"的直觉为止。

#### 来源与时效（本小主题末集中列）
- SICP §1.3.2「Constructing Procedures Using Lambda」+ §3.2 环境模型（闭包 = 过程 + 定义环境的帧指针），https://sarabander.github.io/sicp/html/1_002e3.xhtml ，核实 2026-07-26。
- CMU 15-150 syllabus：higher-order functions/闭包在不可变 SML 下的处理（佐证不可变环境无晚绑定问题），核实 2026-07-26。
- Python 3.11 官方文档：默认参数值在 `def` 执行时求值一次（`lambda i=i` 修法的依据），Language Reference §7「Function definitions」/教程 §4.8，核实 2026-07-26。
- 本机旁证：Python 3.11.15 下 `[lambda: i for i in range(3)]` 全调用得 `[2,2,2]`，默认参数版得 `[0,1,2]`，核实 2026-07-26。

## 7.3 不可变性与纯函数 — 引用透明、无副作用

### 7.3.1 不可变性 immutability

不可变（immutable）对象指**创建后其内容不能再被改变**的对象。在 Python 里 `int`、`str`、`tuple`、`frozenset` 是不可变的；`list`、`dict`、`set` 是可变的。要"修改"一个不可变对象，只能造一个新对象，原对象保持不动。

真实运行，对不可变对象写入会直接报错：

```python
t = (1, 2, 3)
t[0] = 9
```

```text
TypeError: 'tuple' object does not support item assignment
```

不可变性的一个直接好处是**可哈希、可安全共享**：因为内容不会变，`frozenset`/`tuple` 能当字典的键、能放进集合，而可变的 `list` 不能。真实运行：

```python
hash([1, 2, 3])
```

```text
TypeError: unhashable type: 'list'
```

`frozenset([1,2])` 则可哈希（`hash(frozenset([1,2]))` 正常返回整数）。初学者由此可建立直觉：可哈希 ≈ "身份/内容稳定，可被当索引用"，而这以不可变为前提。

### 7.3.2 纯函数 pure function

纯函数指同时满足两个条件的函数：其一，**输出只由输入决定**——相同输入永远给相同输出；其二，**无副作用（no side effects）**——不修改外部状态（全局变量、传入的可变参数、文件/网络/打印等外部世界）。

对照最直观。下面 `pure_double` 是纯的，`impure_append` 不是：

```python
def pure_double(xs):
    return [x * 2 for x in xs]      # 造新列表，不碰 xs

def impure_append(xs):
    xs.append(0)                    # 副作用：改了调用者的列表
    return xs
```

`pure_double([1,2])` 无论调多少次都返回 `[2,4]` 且不影响任何人；`impure_append` 每调一次就把调用者手里的列表改长一点——这就是副作用，也是 7.5 要隔离的东西。

### 7.3.3 引用透明 referential transparency

引用透明（referential transparency）是纯函数带来的一个可贵性质：一个表达式**可以被它的求值结果直接替换而不改变程序含义**。因为纯函数相同输入必得相同输出、且不改变外部世界，`pure_double([1,2])` 出现在哪里都等价于直接写 `[2,4]`。

引用透明为什么值钱：它让"等式推理（equational reasoning）"成立——你可以像做代数一样，把函数调用换成它的结果来分析和优化程序；也让结果可**缓存/记忆化**、可安全地**并行**（见 7.5，各次调用互不干扰）。反过来，一旦函数有副作用或依赖可变外部状态，"换成结果"就可能改变行为，引用透明随之破坏——这正是大主题8「赋值破坏引用透明」的核心命题。

### 7.3.4 好处、易错点与关联

好处小结：纯 + 不可变让代码**易测试**（无需搭环境、无隐藏依赖）、**易推理**（无远处的意外修改）、**易并行**（无共享可变状态就无数据竞争）。这是 CMU 15-150 全程以不可变 SML 授课的根本理由。

初学者易错点一：Python 的不可变是**浅**的。`tuple` 本身不可变，但若元素是可变对象，元素内部仍可改：

```python
t = ([1, 2], 3)
t[0].append(9)      # 合法！改的是元组里那个 list 的内容
print(t)
```

```text
([1, 2, 9], 3)

```

易错点二：把可变对象作默认参数会与"纯"的直觉相悖（同一默认列表被多次调用共享、逐次累积），这一"可变默认参数陷阱"归大主题8详述，此处只提醒它与"无副作用"直接冲突。关联：不可变性是 7.4 map/filter"造新序列而非原地改"的前提，也是 7.5"纯核心"的构成材料。

#### 来源与时效（本小主题末集中列）
- CMU 15-150《Principles of Functional Programming》syllabus：以不可变 SML 授课，强调 data-centric computation、无副作用、可推理性，https://www.cs.cmu.edu/~iliano/courses/15F-CMU-CS150/syllabus.shtml ，核实 2026-07-26。
- SICP §1.1.1（引用透明与代换模型 substitution model 的适用前提）、§3.1（引入赋值后代换模型失效，反向印证），https://sarabander.github.io/sicp/html/index.xhtml ，核实 2026-07-26。
- Python 3.11 官方文档：`frozenset`/`tuple` 为不可变、可哈希类型（Data Model §3「objects, values and types」、内置类型文档），核实 2026-07-26。
- 本机旁证：Python 3.11.15 下 `tuple` 写入抛 `TypeError`、`hash([...])` 抛 `unhashable type: 'list'`、浅不可变 `([1,2],3)` 元素可 `append`，核实 2026-07-26。

## 7.4 map/filter/reduce 与序列处理 — 高阶序列变换

### 7.4.1 map：逐元素变换

`map(f, seq)` 对序列的**每个元素**施加函数 `f`，得到一个等长的新序列（元素一一对应）。它把"对每一项做同样的事"这个模式抽象成一个高阶函数，替代手写 for 循环 + 逐个 append。SICP 在 §2.2.1「Mapping over lists」正是以此引入 `map`。

```python
nums = [1, 2, 3, 4, 5]
print(list(map(lambda x: x * x, nums)))
```

```text
[1, 4, 9, 16, 25]
```

注意 Python 3 的 `map` 返回的是**惰性迭代器**（不是列表），要 `list(...)` 才物化。这与 SICP（Scheme）里 `map` 直接返回新表略有不同——语义相同，Python 多了"惰性"这层（见 7.4.4）。

### 7.4.2 filter：按谓词筛选

`filter(pred, seq)` 保留使谓词 `pred` 返回真的元素，丢弃其余，得到一个**可能更短**的新序列（顺序不变）。它抽象的是"挑出满足条件的项"。

```python
nums = [1, 2, 3, 4, 5]
print(list(filter(lambda x: x % 2 == 0, nums)))
```

```text
[2, 4]
```

map 与 filter 的分工要记牢：map **不改变元素个数、改变每个元素的值**；filter **不改变保留下来的元素、只改变个数**。SICP §2.2.3 把 `filter` 与 `map` 并列为"序列的约定接口"。

### 7.4.3 reduce/fold：折叠成单值

`reduce(f, seq[, init])`（Python 在 `functools` 里）把一个**二元函数**沿序列**从左到右**累积作用，最终把整个序列压成**一个值**。在 SICP §2.2.3 里对应 `accumulate`，函数式文献中通称 **fold（fold-left）**。

其累积过程（fold-left）是：

reduce(f, [a, b, c], init) = f(f(f(init, a), b), c)

真实运行，用可视化的方式看清折叠方向：

```python
from functools import reduce
print(reduce(lambda acc, x: f"({acc}+{x})", [1, 2, 3, 4], "0"))
print(reduce(lambda a, b: a + b, [1, 2, 3, 4, 5]))       # 无 init
print(reduce(lambda a, b: a + b, [1, 2, 3, 4, 5], 0))    # 有 init
```

```text
((((0+1)+2)+3)+4)
15
15
```

关于初始值 `init` 的规则（Python 官方语义）要点：给了 `init` 就以它为起点，把它放在序列所有元素之前；**不给** `init` 时取序列第一个元素作起点、从第二个开始折叠；序列只有一个元素则直接返回该元素；**序列为空且无 init 会报错**（`TypeError`）。因此处理可能为空的序列时，务必显式给 `init`（如求和给 `0`、求积给 `1`），否则空输入会崩。

### 7.4.4 序列作为约定接口、惰性、与推导式对照

SICP §2.2.3 的核心思想是把 map/filter/reduce 当作**约定接口（conventional interfaces）**：数据像信号一样在这些"处理级"之间流动，程序被组织成"取数 → filter 筛 → map 变 → reduce 汇"的流水线，比一团嵌套循环更清晰、更可组合。

```python
from functools import reduce
data = range(1, 11)
result = reduce(lambda a, b: a + b,
                map(lambda x: x * x,
                    filter(lambda x: x % 2 == 0, data)), 0)
print(result)      # 偶数的平方和：4+16+36+64+100
```

```text
220
```

惰性要点（规范 vs 实现分账）：Python 3 的 `map`/`filter` 返回**惰性迭代器**，只在被消费时逐个产出、可省内存并支持无穷流（`type(map(...)).__name__` 为 `'map'`，非 `list`）；而 Scheme（SICP）版直接返回新表。`reduce` 无论如何都要走完整个序列（要得出单值），不惰性。

与列表推导式对照：Python 社区惯用列表/生成器推导式表达 map+filter——`[x*x for x in data if x%2==0]` 与上面的 `map(..., filter(...))` 等价且更常见。这不改变本节概念（都是高阶序列变换），只是 Python 的语法偏好；理解 map/filter/reduce 仍是掌握 FP 序列处理与其他语言（Scheme/SML/JS）通用词汇的基础。

#### 来源与时效（本小主题末集中列）
- SICP §2.2.1「Representing Sequences」含「Mapping over lists」引入 `map`；§2.2.3「Sequences as Conventional Interfaces」引入 `filter`、`accumulate`（= reduce/fold）与"约定接口/信号流"思想，https://sarabander.github.io/sicp/html/2_002e2.xhtml ，核实 2026-07-26。〔与本报告 prompt 所列「§2.2.1」的细化：`map` 在 §2.2.1，`filter`/`accumulate` 在 §2.2.3，两处均引。〕
- Python 3.11 官方文档 `functools.reduce`：签名 `reduce(function, iterable[, initializer])`、从左到右折叠、有/无 initializer 的语义与空序列报错，等价实现见文档，https://docs.python.org/3.11/library/functools.html#functools.reduce ，核实 2026-07-26。
- Python 3.11 官方文档：内置 `map`/`filter` 返回迭代器（惰性），https://docs.python.org/3.11/library/functions.html ，核实 2026-07-26。
- 本机旁证：Python 3.11.15 下 `map` 得 `[1,4,9,16,25]`、`filter` 得 `[2,4]`、`reduce` fold-left 得 `((((0+1)+2)+3)+4)`、流水线得 `220`，核实 2026-07-26。

## 7.5 副作用隔离 — 纯核心/薄壳、work & span 直觉

### 7.5.1 副作用是什么、为何要隔离

副作用（side effect）指函数在"算出返回值"之外，还对外部世界产生的可观察改变：改全局变量、改传入的可变对象、打印、读写文件/网络/数据库、抛出异常等。副作用本身不可消灭（程序总得和外界交互），函数式范式的主张不是"消灭副作用"，而是**把它们隔离、集中到程序的边缘**，让内部的绝大多数逻辑保持纯净。

为什么要隔离：7.3 讲过纯函数易测、易推理、易并行，而副作用恰好破坏这些性质。若副作用散布在各处，任何一次调用都可能"顺手"改了别处的状态，程序行为随之难以预测。把副作用挤到边缘，就能让"核心计算"重新获得引用透明的全部好处。

### 7.5.2 纯核心 / 薄壳（functional core, imperative shell）

一种被广泛采用的组织方式是"纯核心 + 薄壳"（functional core, imperative shell）：把**决策与计算**写成纯函数构成的核心（无 I/O、无全局状态），把**读写外部世界**的副作用收拢到一层很薄的外壳里。外壳负责"取输入 → 交给纯核心算 → 把结果写出去"，核心对外界一无所知。

```python
def compute(data):                 # 纯核心：只算，不碰外界
    return [x * 2 for x in data]

def main():                        # 薄壳：只做 I/O
    data = [1, 2, 3]               # 真实场景这里是读文件/网络
    result = compute(data)         # 把脏活之外的计算交给纯核心
    print(result)                  # 副作用集中在这里

main()
```

```text
[2, 4, 6]
```

这样切分的收益：`compute` 可以脱离一切环境被单元测试（给输入断言输出即可），也可放心并行；需要改 I/O 方式（换成写数据库、走网络）时只动薄壳，核心不受影响。初学者可把它记成"决定做什么"（纯核心）与"真的去做（有后果的动作）"（薄壳）分家。

### 7.5.3 work 与 span 的并行直觉

CMU 15-150 用 **work** 与 **span** 两个量刻画纯函数式程序的并行代价。work 是"总计算量"——把所有操作**顺序**做完要多少步（≈ 单核运行时间）；span（也叫 depth）是"最长依赖链"——即使有**无穷多处理器**、把能并行的都并行了，仍必须串行完成的那条最长路径长度。

两者给出可并行性的直觉：

work / span = 理想情况下可获得的最大并行加速比

为什么纯函数与此强相关：无副作用、无共享可变状态时，互不依赖的子计算可以**放心地同时算**（不会相互踩踏），所以 span 才可能远小于 work、加速才有空间。例如"对列表每个元素独立求平方"（一次 map），各元素互不依赖，span 约为常数级、work 与元素数成正比，天然高度并行。反之，一旦引入共享可变状态和副作用，子计算之间就产生隐藏依赖，并行既不安全、span 也被拉长。这正是"隔离副作用"在性能上的回报，也是 15-150 强调纯函数的另一层动机。

### 7.5.4 易错点、边界与关联

易错点：不要把"隔离副作用"误解为"程序不能有副作用"——有，只是集中管理；也不要以为把代码写成一串 map/filter 就自动纯了，若传入的函数偷偷改了外部状态，照样不纯（纯不纯看有没有副作用，不看用没用高阶函数）。

边界与关联：并行的具体机制（线程/进程、GIL、数据竞争、锁）与共享可变状态的深挖显式归 L4-05（并发与并行），本节只立 work/span 与"纯→易并行"的直觉，不下机制结论；"赋值引入局部状态如何破坏引用透明"归大主题8。7.5 是全章的收束——7.1 的高阶函数搭骨架、7.2 的闭包携带上下文、7.3 的纯/不可变提供可安全组合的材料、7.4 的序列变换是纯核心最常见的形态，最终都服务于"把副作用挤到边缘、让核心可推理可并行"这一工程目标。

#### 来源与时效（本小主题末集中列）
- CMU 15-150《Principles of Functional Programming》syllabus：课程涵盖 asymptotic cost analysis 与 parallelism，以 work/span（cost graphs）刻画并行代价，纯函数式（不可变 SML）为前提，https://www.cs.cmu.edu/~iliano/courses/15F-CMU-CS150/syllabus.shtml ，核实 2026-07-26。〔work/span 具体定义为 15-150 讲义/后继课 15-210 的标准表述；本节取其直觉，形式化细节标「以讲义为准」。〕
- SICP §3.1「Assignment and Local State」：反向印证——引入赋值/副作用后代换模型失效、程序不再可用等式推理，https://sarabander.github.io/sicp/html/3_002e1.xhtml ，核实 2026-07-26。
- 「functional core, imperative shell」为业界通行的架构表述（G. Bernhardt 提出并流行），此处作组织副作用的直觉框架，非一手规范术语，标 `⚠仅二手`（承重的纯/引用透明论断由 SICP §3.1 与 CMU 15-150 支撑）。
- 本机旁证：Python 3.11.15 下"纯核心 + 薄壳"示例 `compute([1,2,3])` 稳定得 `[2,4,6]`（相同输入相同输出），核实 2026-07-26。
