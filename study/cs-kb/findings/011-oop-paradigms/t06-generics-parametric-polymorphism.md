# L3-04·大主题6 泛型与参数多态

> 基线 Py3.11.15/np2.4.6/gcc13.3 @2026-07-25 ｜ 核实日期：2026-07-26 ｜ 先修：L3-04 大主题3（OO 三支柱：封装/继承/子类型多态与方法覆盖）、大主题4（动态派发、鸭子类型、MRO）、大主题5（抽象基类/协议、LSP） ｜ 一手锚点：Cardelli & Wegner《On Understanding Types, Data Abstraction, and Polymorphism》(ACM Computing Surveys, 1985, §1.3 与 Fig.1 多态分类)；Python 3.11 官方文档 `typing`（`Generic`/`TypeVar`）与 Data Model（`__add__`/`__eq__`/`__radd__` 等数值与比较协议）；PEP 484（类型提示）、PEP 695（类型参数语法，3.12 引入）；Java Language Specification（类型擦除、reifiable/non-reifiable 类型）；ISO/IEC 14882 C++（模板实例化 [temp]） ｜ 成熟度：GA/稳定（四分类为 1985 经典；Python 3.11 泛型 API 稳定，PEP 695 新语法属 3.12 delta，基线不可用）

> 粒度判定：**1 份**（不拆）。理由：本大主题只有 4 个小主题（6.1–6.4），且它们是一条紧密的教学序——先立"多态到底分几种"的总纲（6.1），再展开其中的参数多态（6.2）与特设多态（6.3），最后横向对比这些多态在三种语言里"编译期怎么落地"（6.4）。四节共享同一根主线（"同一段代码/同一个名字如何服务多种类型"），拆开反而割裂对照，篇幅也适中，故合为 1 份。跨课边界：类型系统的形式化（全称/存在量化、λ 演算编码）归 L5-06，本报告只在 6.1 点到 Cardelli-Wegner 的直觉、不做形式推导；Rust 泛型与 trait 约束（单态化的另一代表 + 一致性/coherence）归 L5-14，本报告不重复立项。

本报告的 Python 代码输出（`Stack[int]` 的运行期类型与参数不保留、运算符重载、PEP 695 语法在 3.11 报 `SyntaxError`）均为基线环境（Python 3.11.15、Linux 6.18.5 x86_64）下的真实运行结果，命令与输出随节贴出；Java 擦除、C++ 单态化以规范与官方教程为准，未在本机跑 Java/C++ 编译，相关处如实标注。

---

## 6.1 多态四分类 — 参数/包含/特设/强制（Cardelli-Wegner 1985）

### 6.1.1 多态是什么、Cardelli-Wegner 的分类树

"多态（polymorphism）"字面是"多种形态"，在编程语言里指**一段代码、一个名字或一个操作能作用于多种类型**。Cardelli 与 Wegner 在 1985 年的经典综述里把多态先劈成两大类、再各分两小类，共四种，画成一棵树：

```
polymorphism
├── universal  普适多态
│   ├── parametric  参数多态
│   └── inclusion   包含（子类型）多态
└── ad-hoc  特设多态
    ├── overloading 重载
    └── coercion    强制（类型转换）
```

这棵树是本大主题的骨架：6.2 展开参数多态，6.3 展开特设的两支（重载 + 强制），包含多态在大主题3/4 已讲（子类型 + 动态派发），这里只作定位。

先记住上层的两分判据（下节 6.1.6 详述）——**普适多态是"同一份代码服务无穷多种类型"，特设多态是"用一个名字凑合地服务有限几种类型、背后其实是不同代码"**。四个小类就是这两句话的具体化。这套 1985 年的划分几乎是后续所有类型系统教材谈多态的默认语言，务必先建立整棵树的形状，再抠单支。

### 6.1.2 参数多态（parametric polymorphism）

参数多态指一个函数或数据类型带一个（或多个）**类型参数**，从而对一整个范围的类型**用同一份代码统一工作**，这些类型通常共享某种结构。最经典的例子是"求列表长度"：不管列表里装的是整数、字符串还是别的，`length` 的逻辑完全一样，与元素类型无关。

一个类型参数化函数在数学上可看作对类型做了全称量化，写成

```
length : ∀T. List[T] → int
```

意思是"对任意类型 T，接受一个 List[T]、返回一个 int"。

初学者常把它跟"重载"混。区别在于：参数多态是**真·一份代码通吃**（`length` 内部一行都不用因元素类型改动）；重载是**看着像一个名字、其实是好几份代码**（见 6.1.4）。Python 的 `typing.Generic`/`TypeVar`、Java 的 `<T>`、C++ 的模板、Rust 的 `<T>` 都是参数多态的语法载体（6.2、6.4 详展）。

### 6.1.3 包含多态（inclusion / 子类型多态）

包含多态指一个对象可以**同时属于多个类型**（它所属的类型之间有"包含/子类型"关系），于是"接受父类型的地方可以传子类型的对象"，调用同一个方法名会按对象的实际类型执行对应版本。这正是大主题3 的"子类型多态 + 方法覆盖"和大主题4 的"动态派发"讲的东西。

名字里的"包含"来自集合直觉：若 `Cat` 是 `Animal` 的子类型，则"所有 Cat"这个集合被"所有 Animal"包含，`Cat` 值的集合 ⊆ `Animal` 值的集合，因此凡要 `Animal` 的地方都能塞一个 `Cat`。

初学者只需在这里把它和参数多态对齐着记：两者都属**普适多态**（都能对无穷多种类型用同一段调用代码），区别是"以什么方式统一"——参数多态用**类型参数**统一，包含多态用**子类型层级**统一。本报告不再深挖子类型（已在大主题3/4/5 覆盖，LSP 在大主题5），这里只补它在四分类里的坐标。

### 6.1.4 重载（overloading，特设多态之一）

重载指**同一个名字被用来表示多个不同的函数/操作**，由上下文（参数的个数与类型）决定某处的这个名字到底指哪一个。它属于"特设（ad-hoc）"多态：表面上一个名字通吃，底层其实是**为每种类型各写一份不同的代码**，编译器/解释器按参数类型挑一份。

最日常的例子是 `+`：`1 + 2`（整数加法）、`1.0 + 2.0`（浮点加法）、`"a" + "b"`（字符串拼接）用的是同一个 `+` 记号，但背后是三套完全不同的机器/字节码逻辑。运算符重载（6.3）是重载的一个特例。

重载与参数多态的分界正是"代码是一份还是多份"。`length`（参数多态）对所有元素类型跑同一段逻辑；`+`（重载）对 int/float/str 跑各自不同的逻辑。很多语言（C++/Java）允许你按参数类型定义多个同名函数——那是重载；Python 语言层不支持这种"按签名静态选函数"的重载（见 6.3.4），要靠别的机制模拟。

### 6.1.5 强制（coercion，特设多态之二）

强制指编译器/运行时**自动插入一次类型转换**，把实参转成函数期望的类型，从而让本来会类型不匹配的调用能通过。它被归入特设多态，因为"看起来一个函数能接受多种类型"其实是靠悄悄转换实现的，不是函数本身通吃。

典型例子是 `1 + 2.0`：许多语言里整数 `1` 被自动提升（coerce）为浮点 `1.0`，再走浮点加法。这里"能同时吃 int 和 float"的假象来自那一步隐式转换，而非加法函数真的对两种类型都原生工作。

初学者要注意重载与强制常常纠缠在一起、难以一眼分清：`1 + 2.0` 到底是"有一个 int+float 的重载版本"还是"先把 int 强制成 float 再用 float+float"？不同语言的选择不同，这也是 Cardelli-Wegner 特意把两者并列为"特设"的原因。Python 的做法偏向强制中的一种——它没有跨类型的隐式转换（强类型，不会把 `"1"+1` 自动转好），但保留了**数值塔**内的隐式提升（int→float→complex），见 6.3.5。

### 6.1.6 普适 vs 特设的判据，与术语冲突标注

上层两分的正式判据有两条，互相印证。其一看**类型集合**：普适多态作用于**无穷多个**具有共同结构的类型；特设多态只作用于**有限个**、可能彼此无关的类型。其二看**代码**：普适多态对所有这些类型**执行同一段代码**；特设多态对不同类型**执行不同代码**（只是共用一个名字/记号）。

**"一份代码、无穷类型 = 普适；多份代码、有限类型 = 特设"**。参数多态（`length` 通吃任意 `List[T]`）和包含多态（任意 `Animal` 子类型走同一调用点）都满足前者；重载（`+` 各类型各一套）和强制（靠插转换凑合）都属后者。

术语冲突需显式标注：日常与许多语言社区里，"多态"常被窄化为专指**包含多态**（"父类引用指子类对象"那种运行时多态），把参数多态另称"泛型"、把重载另称"重载"而不叫多态。这与 Cardelli-Wegner 的宽定义（四者皆多态）不一致——本报告统一采用 **1985 论文的宽定义**（四分类都算多态），遇到窄用法时以论文为准。此外"特设（ad-hoc）"一词在中文教材里也译作"特定/专用/临时"，指的是同一对象。

#### 来源与时效（本小主题末集中列）
- Cardelli & Wegner《On Understanding Types, Data Abstraction, and Polymorphism》，ACM Computing Surveys 17(4), 1985，§1.3 "Kinds of polymorphism" 及分类树（universal = parametric + inclusion；ad-hoc = overloading + coercion），判据"universal 执行同一段代码作用于无穷类型 / ad-hoc 对有限个类型执行不同代码"，核实 2026-07-26（论文 PDF `lucacardelli.name/papers/onunderstanding.a4.pdf` 本次取时返回 503，四分类结构由 ACM 元数据与二手综述交叉印证，定义以论文原文为准）。
- Wikipedia "Polymorphism (computer science)" 与多篇 PL 综述转述同一四分类树并直接引用 Cardelli-Wegner——作二手交叉印证（不承重），核实 2026-07-26。
- 术语冲突（"多态"窄指子类型多态 vs 论文宽定义四类）：以论文宽定义为准，窄用法在各语言社区常见。

## 6.2 参数多态与泛型容器 — 类型参数化（Python `typing.Generic`/`TypeVar`）

### 6.2.1 类型参数化解决什么问题

参数多态的语法载体叫**泛型（generics）**：把类型本身当参数传进去，写一次代码就能安全地服务任意元素类型。最典型的落地物是**泛型容器**——一个 `Stack`、`List`、`Dict` 的实现逻辑与它装什么元素无关，我们希望"栈的代码写一份，但 `Stack[int]` 和 `Stack[str]` 各自被类型检查器当成装不同东西的栈"。

没有泛型时，你要么给每种元素类型复制一份 `IntStack`/`StrStack`（重复、易错），要么用"万能类型"（Python 的 `object`、Java 早期的 `Object`）装一切、但取出来要强转且丢了类型检查。泛型是这两个坏选项之间的正解：**一份代码 + 保留元素类型信息给静态检查器**。

Python 的泛型主要服务于**静态类型检查（mypy/Pyright 等工具）与可读性**，运行期基本不参与（6.4.4 会看到运行时类型参数被"擦掉"）；而 C++/Rust 的泛型在编译期就实打实生成了各类型的专用代码（6.4.3）。同是"泛型"，落地重量差别很大。

### 6.2.2 `TypeVar` 与 `Generic`（Python 3.11 的标准写法）

在 Python 3.11（基线），定义一个泛型类要两步：先用 `TypeVar` 造一个类型变量，再让类继承 `Generic[T]`。

```python
from typing import Generic, TypeVar

T = TypeVar("T")

class Stack(Generic[T]):
    def __init__(self) -> None:
        self._items: list[T] = []
    def push(self, x: T) -> None:
        self._items.append(x)
    def pop(self) -> T:
        return self._items.pop()
```

`TypeVar("T")` 声明"T 是一个待填的类型占位符"，`Generic[T]` 告诉类型系统"本类以 T 为类型参数"。使用时写 `Stack[int]`，类型检查器就知道 `push` 只接受 `int`、`pop` 返回 `int`。

初学者的两个抓手。其一，`TypeVar` 的名字字符串必须与变量名一致（`T = TypeVar("T")`），这是约定也是工具的要求。其二，标注 `s: Stack[int] = Stack()` 里，`Stack[int]` 是给**类型检查器**看的；真去跑，Python 运行时并不阻止你 `push` 一个字符串（见 6.4.4 的实测）——泛型在 CPython 运行期是"软约束"。

### 6.2.3 PEP 695 新语法（Python 3.12 引入，基线不可用，硬标）

从 **Python 3.12** 起，PEP 695 引入了内建的类型参数语法，不再需要手动 `TypeVar` 和 `Generic` 基类：

```python
class Stack[T]:            # 3.12+：方括号直接声明类型参数
    ...
def first[T](xs: list[T]) -> T:   # 3.12+：泛型函数
    ...
type Maybe[T] = T | None          # 3.12+：泛型类型别名
```

新语法把类型参数**词法作用域化**（限定在声明它的类/函数内，不再是模块级全局变量），并让变异性（协变/逆变）由检查器**自动推断**而非手写。

必须硬标的时效事实：**这套语法在基线 Python 3.11.15 上不可用**。本机实测直接报语法错误：

```
$ python3 p695.py     # 内容为 class Box[T]: pass
    class Box[T]:
             ^
SyntaxError: invalid syntax
```

所以在 3.11 及以前必须用 6.2.2 的 `TypeVar`/`Generic` 老写法；只有目标运行时确定为 3.12+ 才能用方括号新语法。初学者若照 3.12 教程在 3.11 上写 `class Box[T]` 会立刻撞上上面的 `SyntaxError`，这是版本 delta 而非代码写错。

### 6.2.4 受约束与有界的类型变量（constraints / bound）

`TypeVar` 不只能是"任意类型"，还能加限制，让泛型更精确。两种常见形式：

```python
from typing import TypeVar
Num = TypeVar("Num", int, float)        # 受约束：只能是 int 或 float 之一
T = TypeVar("T", bound="Comparable")    # 有界：必须是 Comparable 的子类型
```

受约束（constraints，列多个具体类型）表示"T 只能在这几个给定类型里取其一"；有界（`bound=`）表示"T 可以是该上界类型的任意子类型"。有界最常用于"要求元素支持某操作"，例如要求可比较、可相加。

受约束是**枚举白名单**（就这几个），有界是**设上限**（这个及其所有子类）。若写 `TypeVar("T", int, float)` 再传 `bool`，检查器会按约束处理（`bool` 是 `int` 子类，此处行为以具体检查器为准，属工具语义、非运行时报错）。这些限制同样只影响静态检查，运行时不强制。

### 6.2.5 泛型函数与多类型参数

泛型不限于容器类，函数也能参数多态。同一个 `TypeVar` 在一次签名里出现多次，表示"这几处必须是同一个类型"：

```python
from typing import TypeVar
T = TypeVar("T")

def first(xs: list[T]) -> T:      # 传 list[int] 就返回 int，传 list[str] 就返回 str
    return xs[0]
```

这里 `first` 就是 6.1.2 里 `∀T. List[T] → int`（此处返回 T 而非 int）那类全称量化函数的 Python 落地：一份代码，元素类型由调用点决定，输入输出类型联动。

多个类型参数用多个 `TypeVar`（如 `K`、`V` 造 `Dict[K, V]`）。初学者的关键直觉：**同名 `TypeVar` = 同一个类型**——`def pair(a: T, b: T)` 强制 a、b 同类型，而 `def pair(a: T, b: S)` 允许不同。这一点决定了泛型签名到底表达了什么约束。

#### 来源与时效（本小主题末集中列）
- Python 3.11 官方文档 `typing` 模块：`TypeVar`、`Generic`（`class C(Generic[T])` 为 3.11 声明泛型的标准方式），`https://docs.python.org/3.11/library/typing.html` ，核实 2026-07-26。
- PEP 484（Type Hints，引入 `TypeVar`/`Generic` 语义）与 PEP 695（Type Parameter Syntax，3.12 引入方括号语法、类型参数词法作用域化、变异性自动推断），`https://peps.python.org/pep-0695/` ，核实 2026-07-26。
- ⚙版本 delta：PEP 695 方括号语法 **3.12+ 才可用**；基线 3.11.15 本机实测 `class Box[T]` 报 `SyntaxError`（真实输出见 6.2.3）；3.11 用 `TypeVar`/`Generic` 老写法。
- 受约束 vs 有界 `TypeVar` 的具体检查行为属各类型检查器（mypy/Pyright）实现语义，非 CPython 运行时语义——分账标注，核实 2026-07-26。

## 6.3 特设多态 — 重载与运算符重载（Python `__add__`/`__eq__`）

### 6.3.1 特设多态与参数多态的分界（回扣 6.1）

特设多态（6.1.4–6.1.5 的重载 + 强制）的本质是"一个名字/记号，背后多份代码，按操作数类型挑一份"。运算符重载是它最常见的形态：语言允许你为自定义类型**定义 `+`、`==`、`<` 等运算符的行为**，让 `a + b` 在你的类型上有意义。

参数多态是"逻辑与类型无关、一份代码通吃"，特设多态是"逻辑因类型而异、每类型一份"。`Stack[T]` 的 `push` 不关心 T 是什么（参数多态）；而 `+` 作用在向量、矩阵、字符串上是三套不同语义（特设多态）。

### 6.3.2 Python 的运算符重载协议（`__add__`/`__eq__` 等双下方法）

Python 通过 **Data Model 里的双下划线特殊方法**实现运算符重载：写 `a + b`，解释器实际调用 `type(a).__add__(a, b)`；写 `a == b` 调用 `type(a).__eq__(a, b)`。你在类里定义这些方法，就等于重载了对应运算符。

```python
class V:
    def __init__(self, x, y): self.x, self.y = x, y
    def __add__(self, o):  return V(self.x + o.x, self.y + o.y)
    def __eq__(self, o):   return (self.x, self.y) == (o.x, o.y)
    def __repr__(self):    return f"V({self.x},{self.y})"
```

基线本机真实输出（`V(1,2)+V(3,4)` 与相等判断，及内置类型走同一协议）：

```
V+V: V(4,6) | eq: True
1+2 via int.__add__: 3 | 'a'+'b': ab
```

`+` 从来不是"语言内建写死的加法"，而是**语法糖**，最终派发到操作数类型的 `__add__`。内置的 `int`、`str` 也走同一套协议（上面 `int.__add__(1,2)` 与 `str.__add__('a','b')` 直接调用可证）。常见协议还有 `__sub__`/`__mul__`/`__lt__`/`__len__`/`__getitem__` 等，一整套构成"让自定义对象用起来像内建类型"的接口。

### 6.3.3 反射运算符、`NotImplemented` 与协议的完整性

当 `a + b` 中 `a` 的类型不知道怎么和 `b` 相加时，Python 有一套后备机制：`a.__add__(b)` 可以返回特殊值 `NotImplemented`，解释器随后尝试 `b` 的**反射方法** `b.__radd__(a)`。只有两边都返回 `NotImplemented`，才抛 `TypeError`。

```python
class Money:
    def __init__(self, n): self.n = n
    def __add__(self, o):
        if isinstance(o, Money): return Money(self.n + o.n)
        return NotImplemented          # 交给对方的 __radd__ 处理
    def __radd__(self, o):             # 支持 0 + Money(...)，方便 sum()
        return self.__add__(Money(o)) if isinstance(o, int) else NotImplemented
```

初学者最容易踩两个坑。其一，不应在不认识的类型上直接 `raise TypeError`，而应**返回 `NotImplemented`**（注意不是 `NotImplementedError` 异常，是一个单例值），否则会破坏与其它类型的协作和 `sum()` 之类的惯用法。其二，定义了 `__eq__` 通常也要考虑 `__hash__`——在 Python 3 里自定义 `__eq__` 会把 `__hash__` 置为 `None`（对象变不可哈希），若需放进 set/dict 键要显式定义 `__hash__`。

### 6.3.4 Python 没有静态重载：`@overload` 存根与 `singledispatch`

和 C++/Java 不同，**Python 语言层不支持"按参数类型定义多个同名函数"的静态重载**——后定义的同名函数会直接覆盖前一个。要达到"按类型走不同逻辑"的效果，有两条路。

其一，运行时手写分派或用 `functools.singledispatch` 按第一个参数的类型选实现：

```python
from functools import singledispatch

@singledispatch
def area(shape): raise NotImplementedError
@area.register
def _(shape: int): return shape * shape      # 按运行时类型分派
```

其二，`typing.overload` 只提供**给类型检查器看的多个签名存根**，运行时仍是同一个函数体，不产生真正的多分支：

```python
from typing import overload
@overload
def f(x: int) -> int: ...
@overload
def f(x: str) -> str: ...
def f(x): return x        # 唯一真正的实现
```

C++/Java 的重载是**编译期按静态类型选函数**（真·特设多态的经典形态）；Python 靠 `singledispatch`（运行期按动态类型选）或 `@overload`（纯静态标注、运行期无效）来近似，两者机制完全不同。这也解释了为什么 Python 里"重载"往往退化成"鸭子类型 + 运行时判断"。

### 6.3.5 强制（coercion）在 Python：数值塔与显式转换

回扣 6.1.5 的第二支特设多态。Python 是强类型语言，**几乎不做隐式跨类型转换**：`"1" + 1` 直接 `TypeError`，不会把字符串悄悄转数字。唯一显著的隐式强制发生在**数值塔**内——`int → float → complex` 方向的自动提升。

```
>>> 1 + 2.0        # int 1 被提升为 float，再做 float 加法
3.0
>>> True + 1       # bool 是 int 子类，按整数参与
2
```

许多语言（C、JavaScript）有大量隐式强制（C 的整型提升、JS 的 `"1"+1=="11"`），容易出隐蔽 bug；Python 刻意收窄到数值塔，把大多数跨类型转换交给**显式**函数（`int()`、`str()`、`float()`）。这是"强类型"的一种体现，也是 Cardelli-Wegner 意义上"强制这一支特设多态在 Python 里被大幅限制"的具体表现。

#### 来源与时效（本小主题末集中列）
- Python 3.11 官方文档 Data Model：数值类型模拟（`object.__add__`/`__radd__`/`__mul__` 等，含返回 `NotImplemented` 的后备派发规则）、`object.__eq__`/`__hash__`（定义 `__eq__` 使 `__hash__` 置 None）、比较协议，`https://docs.python.org/3.11/reference/datamodel.html` ，核实 2026-07-26；本机 3.11.15 真实输出见 6.3.2。
- Python 3.11 官方文档 `functools.singledispatch`（按第一实参运行时类型分派）与 `typing.overload`（仅供类型检查器的多签名存根，运行时无重载语义），核实 2026-07-26。
- 数值塔与隐式强制：Python 语言参考"数值类型"与内置数值行为（int/float/complex 提升、bool 为 int 子类），核实 2026-07-26。

## 6.4 实现策略对比 — 擦除(Java) vs 单态化(C++) vs 结构化鸭子(Python)

### 6.4.1 三条落地路线总览

"泛型/参数多态"在源码层看起来相似（都写 `<T>` 或 `[T]`），但编译器怎么把它变成可执行代码，三大主流语言走了三条不同的路：Java 用**类型擦除**、C++ 用**模板实例化（单态化）**、Python 用**运行时结构化鸭子类型**。这一节横向对比它们的机制与权衡。

**Java 把类型信息在编译后抹掉、只留一份代码；C++ 为每个用到的具体类型各生成一份专用代码；Python 根本不在编译期检查类型、运行时"能干这个活就行"**。三者分别在"代码量、类型安全时机、运行期开销"上做了不同取舍。

### 6.4.2 Java：类型擦除（type erasure）

Java 泛型是**编译期特性**：编译器用泛型信息做类型检查，检查完就**擦除**类型参数——把 `T` 替换成它的边界（无界则替换成 `Object`），字节码里**不保留**类型实参。因此 `List<String>` 和 `List<Integer>` 在运行时是**同一个类** `List`，JVM 分不清它们。

这样做的代价与限制：运行时拿不到类型实参（不能 `new T[]`、`instanceof List<String>`），未受检的强转会产生 unchecked 警告与"堆污染（heap pollution）"风险，基本类型要装箱成对象（`List<int>` 不合法，得 `List<Integer>`）带来开销。JLS 用 **reifiable（可具体化）vs non-reifiable** 区分"类型在运行时是否完整可得"——`List<String>` 属 non-reifiable。

擦除让泛型**向后兼容**旧的非泛型字节码，且**代码只有一份**（不膨胀）、适合分离编译。抓手记忆：**"Java 泛型是给编译器看的护栏，运行时就拆掉了"**——这也是为什么 Java 里 `list.getClass()` 对 `List<String>` 和 `List<Integer>` 返回相同结果。

### 6.4.3 C++：模板实例化 / 单态化（monomorphization）

C++ 模板走相反的路：编译器为**每一种实际用到的类型实参各生成一份专门的代码**。`vector<int>` 和 `vector<double>` 是**两个真正不同的类**，各自有为该类型特化、内联优化过的机器码。这个"给每个具体类型造一份单态版本"的过程叫**单态化（monomorphization）**（Rust 泛型同属此路线，归 L5-14）。

权衡是一枚硬币的两面。好处：运行时**零抽象开销**、类型完全保留（`vector<int>` 运行时就是 int 的容器、可优化、无装箱）、100% 运行期类型安全。代价：**代码膨胀**（用得越多、类型越杂，目标码越大，极端时可指数级膨胀）、编译更慢、模板报错冗长；且单态化通常需要"看得见整份模板定义"，不利于分离编译。

初学者对照 Java 记：**Java 擦成一份、C++ 复制多份**。所以 C++ 的 `sizeof` 与运行期行为对每个模板实例都真实反映其类型，而 Java 运行期已看不到类型参数。这条"擦除 vs 单态化"的分界是本小主题最该记牢的一刀。

### 6.4.4 Python：运行时结构化鸭子类型 + 泛型运行期擦除

Python 的路线最"轻"：它**运行时根本不按声明的类型参数做检查**，只看对象**实际能不能完成所需操作**——这就是鸭子类型（"走起来像鸭子、叫起来像鸭子，就当它是鸭子"，见大主题4）。`typing` 的泛型标注（`Stack[int]`）几乎只服务静态检查器，运行时被"擦掉"：`Stack[int]()` 的实际类型就是 `Stack`，类型实参不参与执行。

基线本机真实输出印证"运行期不保留、不强制"：

```
Stack[int] is Stack[str]? False           # 但这只是不同的“别名对象”
origin: <class '__main__.Stack'> args: (<class 'int'>,)   # 参数信息仅在类型对象上可内省
runtime type of Stack[int]() : Stack       # 实例的运行期类型只是 Stack，参数被擦
isinstance(si, Stack): True
pushed str into Stack[int] at runtime -> top: not an int   # 往 Stack[int] 塞字符串，运行期毫无阻拦
```

初学者要抓两点。其一，Python 的"泛型"与 Java 的擦除**貌合神不同**：Java 编译期真的做了强类型检查（擦除只发生在检查之后），Python 连编译期检查都不做（除非你另跑 mypy/Pyright），运行期更是完全放行——上面把字符串 push 进 `Stack[int]` 毫无报错即为证。其二，`Stack[int]` 这种下标写法在运行时会生成一个可内省的**别名对象**（`get_origin`/`get_args` 能取回 `Stack` 与 `(int,)`），但实例本身不带类型参数。所以 Python 的参数多态本质是"**约定 + 工具检查 + 鸭子类型**"，不是运行期机制。

### 6.4.5 三策略对照与选型直觉

把三条路线并排看，差异集中在四个维度：

```
维度            Java 擦除            C++ 单态化            Python 鸭子/擦除
代码份数        一份(擦成 Object)    每类型一份(可能膨胀)   一份(运行期不特化)
类型检查时机    编译期(之后擦除)      编译期(每实例)         默认无(靠 mypy 等工具外挂)
运行期类型信息  丢失(non-reifiable)  完整保留              仅类型对象可内省,实例已擦
运行期开销      装箱等间接开销        零抽象开销(但码膨胀)   动态查找开销
```

要**极致性能与编译期确定性**、能接受代码膨胀与全量编译，选单态化（C++/Rust）；要**兼容性与编译产物精简**、能接受装箱与运行期类型信息缺失，选擦除（Java）；要**开发速度与灵活性**、把类型安全交给外部工具与测试，选鸭子类型（Python）。没有免费的午餐，三者是"性能/安全时机/灵活性"三角上的不同折中。

回到 6.1 的四分类——本大主题讲的"参数多态"（6.2）在这三种语言里语法都像 `<T>`，但 6.4 说明**同一个概念的编译期落地可以天差地别**。理解"擦除 / 单态化 / 鸭子类型"这组对照，比记住任何单一语言的语法都更迁移得动。

#### 来源与时效（本小主题末集中列）
- Java 类型擦除：Oracle Dev.java "Type Erasure" 与 Java 官方教程（擦除把类型参数替换为边界或 `Object`；`List<String>`/`List<Integer>` 运行时同为 `List`、JVM 不可区分；reifiable vs non-reifiable、堆污染、unchecked 警告），交叉 Java Language Specification 相关章节，`https://dev.java/learn/generics/type-erasure/` ，核实 2026-07-26。
- C++ 模板单态化：ISO/IEC 14882 C++ 模板实例化 [temp]（每个实例化产生独立特化）；"monomorphization"术语与"代码膨胀 vs 零抽象开销""单态化需整程序、擦除利于分离编译"的权衡由多来源（cppreference、PL 综述）交叉印证，核实 2026-07-26（未在本机跑 C++ 编译取证，属规范/文献结论）。
- Python 运行期擦除与鸭子类型：Python 3.11 `typing` 文档 + Data Model；本机 3.11.15 真实输出（`Stack[int]()` 运行期类型为 `Stack`、`get_origin/get_args` 可内省、往 `Stack[int]` push 字符串运行期不报错）见 6.4.4，核实 2026-07-26。
- 冲突/分账：Java 擦除与 Python 擦除**貌似同名实则不同**——Java 在擦除前有强制的编译期类型检查，Python 默认无编译期检查、运行期亦放行；本报告显式分账，不混为一谈。
