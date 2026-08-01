# L3-04·大主题2 数据抽象与封装

> 基线 Py3.11.15/np2.4.6/gcc13.3 @2026-07-25 ｜ 核实日期：2026-07-26 ｜ 先修：L3-04 大主题1（编程范式总览与抽象层次）、L1-01（值/变量、函数、可变性入门、构造器/选择器雏形）、L2-01 §1.1（ADT 的规范 vs 实现分离） ｜ 一手锚点：SICP《Structure and Interpretation of Computer Programs》(Abelson & Sussman, 2nd ed, MIT Press) §2.1（Introduction to Data Abstraction）、§2.1.3（What Is Meant by Data?）、§2.2（Hierarchical Data and the Closure Property）；Barbara Liskov《Data Abstraction and Hierarchy》(OOPSLA '87 Addendum, Oct 1987) §2；抽象函数/表示不变式的一手源为 C.A.R. Hoare《Proof of Correctness of Data Representations》(Acta Informatica 1, 1972) 与 Liskov & Guttag《Abstraction and Specification in Program Development》(MIT Press/McGraw-Hill, 1986)，教学锚 MIT 6.005/6.031 Reading（AF/RI）｜ 成熟度：GA/稳定（经典数据抽象理论，无版本漂移；Python 示例锚 3.11.15）
>

本报告中的 Python 示例基于 Python 3.11.15、Linux 6.18.5 x86_64，对应上面的基线串；示例的真实运行输出随节贴出。

小主题 2.4 的「抽象函数」「表示不变式」两个术语，**并不出现在 Liskov 1987 论文正文中**（该文讲 rep/封装/规范/局部性，并给出子类型替换性质）。这两个概念的一手源是 Hoare 1972 与 Liskov & Guttag 1986，本报告据此分账标注，不把它们硬挂到 1987 那篇上。

---

## 2.1 构造器/选择器与抽象屏障 — 分层隔离表示

### 2.1.1 数据抽象是什么

数据抽象（data abstraction）是一种把「一个复合数据对象**怎么用**」与「它**由更基本的数据怎么拼出来**」隔离开的编程方法。SICP §2.1 开篇的定义是：

Data abstraction is a methodology that enables us to isolate how a compound data object is used from the details of how it is constructed from more primitive data objects.

让程序只通过一组**接口过程**去操作「抽象数据」，除接口承诺的行为外不对数据做任何额外假设；具体的表示方式独立于使用它的程序，二者仅靠接口相连。这样一来，换掉底层表示时，上层用它的代码一行都不用改。

初学者的抓手是「插座与电器」：你插电器只依赖插座这个接口（几孔、电压），完全不用知道墙里电线怎么走、发电厂在哪。数据抽象就是给数据装上这样一个「插座」——用的人只认接口，实现的人可以随便改墙里的线。

### 2.1.2 构造器与选择器：有理数抽象

数据抽象的接口由两类过程组成：**构造器（constructor）** 负责「造出」抽象对象，**选择器（selector）** 负责「取出」对象的各部分。SICP 的经典例子是有理数：它先用「愿望式思维」假设已经有三个过程可用——

`(make-rat ⟨n⟩ ⟨d⟩)`：以 n 为分子、d 为分母造一个有理数（构造器）

`(numer ⟨x⟩)`：取有理数 x 的分子（选择器）

`(denom ⟨x⟩)`：取有理数 x 的分母（选择器）

有了它们，加减乘除等运算就能只用这三个名字写出来，完全不碰「有理数到底怎么存」。SICP 底层用 `cons`/`car`/`cdr`（序对）实现它们：

```scheme
(define (make-rat n d) (cons n d))
(define (numer x) (car x))
(define (denom x) (cdr x))
```

翻成本报告基线下的 Python，并把「约分」这一步放进构造器（这是 2.1.4 的换表示示范）：

```python
from math import gcd

def make_rat(n, d):          # 构造器：造有理数，并约到最简
    g = gcd(n, d)
    return (n // g, d // g)

def numer(x): return x[0]    # 选择器：取分子
def denom(x): return x[1]    # 选择器：取分母

def add_rat(a, b):           # 运算只用接口，不碰底层 tuple
    return make_rat(numer(a) * denom(b) + numer(b) * denom(a),
                    denom(a) * denom(b))
```

```text
1/2 + 1/3 = 5 / 6
make_rat(6,8) = (3, 4)
```

关键点是 `add_rat` 通篇只出现 `make_rat`/`numer`/`denom`，它对「有理数是个 tuple」这件事一无所知，也不该知道。

### 2.1.3 抽象屏障：分层隔离表示

把构造器/选择器这层接口画成一条**水平线**，线上是「用有理数的程序」，线下是「有理数怎么实现」，这条线就是**抽象屏障（abstraction barrier）**。SICP §2.1.2 说这些水平线「隔离了系统的不同『层次』」。有理数例子里有三道屏障，自顶向下：

顶层——`add-rat`、`sub-rat` 等运算，以及用有理数的应用代码；

中层——`make-rat`、`numer`、`denom` 构造器/选择器；

底层——序对实现 `cons`、`car`、`cdr`。

每一层只依赖它正下方那层暴露的接口，不越层去碰更底层的细节。它的价值是**把改动局限在最少的模块里**：任一层的实现变了，只要它对外的接口承诺不变，别的层就不受影响。

初学者可以把它想成公司的组织层级：销售（顶层）只跟产品经理（中层接口）打交道，不直接指挥工厂流水线（底层）；流水线怎么改，只要产品规格不变，销售根本不用重新培训。

### 2.1.4 换表示只动一层

抽象屏障的兑现方式，是「换一种底层表示，只改屏障下面那层」。上面的 Python 版本把**约分**放在了构造器 `make_rat` 里（一造出来就是最简分数）；另一种设计是让构造器原样存、把约分推迟到选择器里：

```python
from math import gcd

def make_rat(n, d): return (n, d)          # 原样存，不约分
def numer(x): g = gcd(x[0], x[1]); return x[0] // g
def denom(x): g = gcd(x[0], x[1]); return x[1] // g
```

这两种实现对**外部使用者完全不可分**——`add_rat` 和所有用有理数的代码一个字都不用改。这正是抽象屏障承诺的东西：表示的选择被关在屏障之内。SICP 把这种「先假设接口存在、照着往下写、实现留到以后填」的策略叫**愿望式思维（wishful thinking）**，它是自顶向下设计里非常实用的一招。

### 2.1.5 易错点与关联

最常见的坑是**偷偷穿透屏障**：明明有 `numer`/`denom` 不用，图省事直接写 `x[0]`、`x[1]` 去摸底层 tuple。这么写当时能跑，但一旦底层表示改了（比如从 tuple 换成对象、换成带约分的另一套），这些「抄近路」的代码就会集体崩掉——抽象屏障给你的保护，被你自己绕过去作废了。

另一个易错点是把「用了函数/类」等同于「做了数据抽象」。判据不是语法，而是**使用方是否只依赖接口、对表示零假设**。数据抽象的思想会在 2.3（升格为信息隐藏与 ADT）、2.4（用不变式钉死接口契约）以及本课大主题 3 的 OO 封装里反复出现，这里先立「构造器/选择器 + 屏障」这个原型。

#### 来源与时效

- SICP 2nd ed §2.1（Introduction to Data Abstraction：数据抽象定义、`make-rat`/`numer`/`denom`、愿望式思维）、§2.1.2（Abstraction Barriers：水平屏障与分层、换表示只动一层），https://sarabander.github.io/sicp/html/2_002e1.xhtml ，核实 2026-07-26。
- Barbara Liskov《Data Abstraction and Hierarchy》(OOPSLA '87 Addendum, Oct 1987) §2：数据抽象「把一个抽象是什么与它如何实现分开，从而同一抽象的多种实现可以自由替换」（"separate what an abstraction is from how it is implemented so that implementations of the same abstraction can be substituted freely"）——作 SICP 屏障思想的独立一手旁证。
- Python 官方教程「类」章以「对象是数据与操作的封装、只经方法交互」表述，与构造器/选择器接口一致（细节机制归大主题 3）。

## 2.2 数据的过程式表示 — 闭包性质、以函数表数据

### 2.2.1 「什么是数据」：由选择器/构造器加条件定义

要理解数据抽象的本质，得先回答一个更基础的问题：**数据到底是什么**。SICP §2.1.3 给的答案很反直觉——数据不必是某种「实体存储」，而是由一组过程加上它们必须满足的条件来定义的：

We can think of data as defined by some collection of selectors and constructors, together with specified conditions that these procedures must fulfill in order to be a valid representation.

对有理数，这个条件是：若 `x` 是 `(make-rat n d)`，则

numer(x) / denom(x) = n / d

对序对（pair），条件是：对任意 x、y，若 z 是 `(cons x y)`，则 `(car z)` 是 x、`(cdr z)` 是 y。换句话说，只要有一套东西满足「取出来正是当初放进去的」这条公理，它**就是**序对，不管内部拿什么拼的。

### 2.2.2 以函数表数据：闭包造 pair

既然「是不是序对」只看是否满足那条公理，SICP 就抖出一个惊人的构造：完全不用任何内建数据结构，**纯靠过程**就能实现序对——

```scheme
(define (cons x y)
  (define (dispatch m)
    (cond ((= m 0) x)
          ((= m 1) y)))
  dispatch)
(define (car z) (z 0))
(define (cdr z) (z 1))
```

`cons` 不返回任何「数据」，而是返回一个**记住了 x、y 的过程** `dispatch`；`car`/`cdr` 通过给这个过程发 0 或 1 来把 x、y 取回来。翻成本报告基线下的 Python：

```python
def cons(x, y):
    def dispatch(m):
        if m == 0: return x
        elif m == 1: return y
        else: raise ValueError(m)
    return dispatch

def car(z): return z(0)
def cdr(z): return z(1)
```

```text
car(cons(3,4)) = 3 | cdr(cons(3,4)) = 4
```

它满足序对公理，所以它**就是**序对。这里 `dispatch` 之所以能记住 x、y，靠的是「过程带着定义它时的自由变量」这一能力——也就是编程意义上的**闭包（lexical closure）**。SICP 把这条结论总结成：「过程作为对象」这一能力，自动就带来了表示复合数据的能力。

### 2.2.3 两个「闭包」必须分清（术语冲突显式标注）

「闭包」这个词在本小主题里指两个**完全不同**的东西，初学者极易混，SICP 专门用脚注警告过，这里显式分账：

其一是 SICP §2.2 标题里的**闭包性质（closure property）**，来自抽象代数——

An operation for combining data objects satisfies the closure property if the results of combining things with that operation can themselves be combined using the same operation.

说的是「用某操作组合出来的东西，还能再用同一操作继续组合」。`cons` 满足闭包性质：序对里可以装序对，于是能层层嵌套搭出表、树等层次结构。这是「组合后仍在同一集合内」的代数含义，与函数无关。

其二是 2.2.2 里让 `dispatch` 记住 x、y 的**词法闭包**——一个带自由变量的过程的实现技术。SICP 脚注原话（转述）：closure 一词在这里取自抽象代数「集合对某运算封闭」之意；而 Lisp 社区「不幸地」也用 closure 指一个毫不相干的概念——表示带自由变量的过程的实现技术；本书不在第二种意义上使用这个词。

**「闭包性质」是数据能不能无限嵌套（代数封闭）；「词法闭包」是函数能不能记住外层变量（2.2.2 造 pair 用的正是它）**。本小主题标题「闭包性质、以函数表数据」两头都沾，务必别把它俩当成一回事。

### 2.2.4 意义与易错点

**数据和过程的界线是模糊的**——过程可以当数据用，数据也可以纯用过程表示。它坐实了 2.1「数据抽象只认接口/公理、不认底层表示」的说法：连「有没有一块存储」都不是必需的，满足公理即可。这也预告了本课大主题 3 里 SICP 不靠 `class`、仅用闭包 + 消息分派实现「对象」的做法（`dispatch` 就是雏形）。

易错点有二。其一是把「闭包性质」和「词法闭包」混为一谈（见 2.2.3）。其二是误以为「过程表示数据」只是智力游戏——它其实是理解**为什么面向对象里对象能封状态**的钥匙：对象的私有状态，本质上就是被闭包关起来的自由变量。实用中当然仍用内建结构（性能好、可读），过程表示的价值在于讲清「数据抽象到底抽象掉了什么」。

#### 来源与时效

- SICP 2nd ed §2.1.3（What Is Meant by Data?：数据由选择器/构造器+条件定义、序对公理、`cons`/`car`/`cdr` 的过程表示），https://sarabander.github.io/sicp/html/2_002e1.xhtml ；§2.2 与其脚注（closure property 定义、抽象代数 vs Lisp 词法闭包的术语辨析），https://sarabander.github.io/sicp/html/2_002e2.xhtml ，核实 2026-07-26。
- 词法闭包为编程通用概念，Python 官方文档（嵌套函数捕获外层作用域自由变量、`nonlocal`）与 SICP 脚注所指第二种含义一致，作术语分账的独立旁证，核实 2026-07-26。
- 冲突/易混点：SICP 明确「闭包性质（代数）≠ 词法闭包（实现技术）」，本报告 2.2.3 按此两分，未和稀泥。

## 2.3 信息隐藏与 ADT 规范 — 隐藏内部表示

### 2.3.1 抽象数据类型是什么

抽象数据类型（abstract data type, ADT）把数据抽象从「一个例子」升格为一条通用的软件构造原则。Liskov 1987 §2 给的定义是：**一个 ADT 就是一组对象，加上唯一能操作这些对象的一组操作**（"a data abstraction is a set of objects that can be manipulated directly only by a set of operations"）。她举的例子很朴素：整数就是一个 ADT——对象是 1、2、3……，操作是加、判等等，用整数的程序**看不到**它是不是补码表示；集合、栈、符号表同理，只是后者要程序员自己定义。

ADT 的思想和 2.1 的数据抽象是一脉相承的，区别在视角：SICP 从「怎么给数据搭屏障」讲机制，Liskov 从「怎么组织程序才好维护、好修改」讲工程动机。她说数据抽象让我们「从数据结构如何实现，抽象到它对外提供、别的程序可以依赖的行为」。

### 2.3.2 封装与信息隐藏

ADT 要能兑现「换实现不影响用户」，靠的是**封装（encapsulation）**。Liskov 1987 §2 讲得很具体：一个数据对象在内存里怎么存，这份信息叫**表示（representation，简称 rep）**；要想改 rep 而不动所有用它的程序，就得——

This is achieved by encapsulating the rep with a set of operations that manipulate it and by restricting using programs so that they cannot manipulate the rep directly, but instead must call the operations.

即：把 rep 和「操作 rep 的那组操作」封在一起，并**禁止使用方直接碰 rep，只能调操作**。她进一步点明，封装保证模块能被独立实现与重实现，这与 Parnas 提倡的**信息隐藏（information hiding）** 原则一脉相承（"it is related to the principle of 'information hiding' advocated by Parnas"）。

信息隐藏的直觉是「别让别人依赖你打算以后改的东西」：会变的（数据结构、算法、存储布局）藏进模块内部，只把稳定的接口露出去。藏得越干净，将来改起来越自由。

### 2.3.3 规范 vs 实现，与「局部性」

Liskov 把 ADT 拆成两半：**规范（specification）** 描述「这个抽象做什么」，**实现（implementation）** 是某门语言里的一段代码。她强调「规范描述抽象做什么，但略去任何关于怎么实现的信息」，正因略去了细节，同一规范才允许多种实现——只要实现提供了规范定义的行为，它就是正确的。

这样分账带来一个关键收益，她称之为**局部性（locality）**：程序可以**一次只看一个模块**地去实现、理解、修改。理由是——用某模块的人只依赖它的**规范**、不依赖它的代码；被用的模块也只须照自己的规范推理。于是改一个模块的实现（为了提速、修 bug、加功能），只要规范不变，别的模块统统不受牵连。

初学者的抓手是「合同」：规范是甲乙双方签的合同，写明「交付什么」；实现是乙方私下怎么干活。只要交付物符合合同，乙方换人、换工艺，甲方无需过问也不受影响。

### 2.3.4 Python 里的封装手段

不同语言强制封装的力度不同。Liskov 对比过 CLU（编译期类型检查、强制封装）与 Smalltalk（运行期检查、强制封装），也提到有些语言**根本不强制封装**，只能靠人工约定。Python 属于「靠约定 + 轻量语言支持」这一档：

```python
class RationalCounter:
    def __init__(self):
        self._n = 0            # 单下划线：约定为内部，外部“请勿直接碰”

    def bump(self):            # 对外操作：只能经它改状态
        self._n += 1
        return self._n

    @property                  # 只读暴露，隐藏内部字段名与表示
    def count(self):
        return self._n
```

Python 的封装主要靠三样：单下划线前缀 `_x`（**约定**内部使用，非强制）、双下划线 `__x` 触发名字改写（`_ClassName__x`，增加误撞难度，仍非真正私有）、以及 `@property` 把「取值」包装成方法从而隐藏底层字段。要点是：Python 的封装是**社会契约式**的（"we are all consenting adults"），语言不像 CLU 那样硬拦——这正对应 Liskov 说的「无语言支持时封装只能靠人工手段、易错」。

### 2.3.5 易错点与关联

最典型的坑是把封装当成「加 getter/setter 的仪式」。封装的实质是**隐藏会变的表示**、只暴露稳定接口；给每个字段机械地配一对 `get_/set_` 而把内部结构原样透出去，等于没隐藏。第二个坑是在 Python 里误以为下划线是「真私有」——它拦不住有意的外部访问，靠的是纪律；真要防误用得配合 `property`、文档和不变式检查（见 2.4）。

本节的 ADT/信息隐藏是 2.4「用表示不变式与抽象函数把接口契约钉死」的前提，也是本课大主题 5（抽象基类/协议、组合优于继承）与 L2-01（ADT 的具体结构实现）的共同地基；结构实现本身归 L2-01，此处只讲抽象与封装的思想。

#### 来源与时效

- Barbara Liskov《Data Abstraction and Hierarchy》(OOPSLA '87 Addendum, Oct 1987) §2、§2.1（Locality）、§2.2（Linguistic Support：CLU cluster vs Smalltalk class、是否强制封装）：ADT 定义、rep、封装、信息隐藏（引 Parnas）、规范 vs 实现、局部性，PDF：https://www.cs.tufts.edu/~nr/cs257/archive/barbara-liskov/data-abstraction-and-hierarchy.pdf ，核实 2026-07-26。
- David Parnas《On the Criteria to Be Used in Decomposing Systems into Modules》(CACM 15:12, 1972)：信息隐藏原则的一手源（Liskov 文中引为 [15]/[16]）——作信息隐藏归属的独立一手锚。
- Python 3.11 官方教程 §9（Classes：私有变量约定 `_spam`、名字改写 `__spam`）与内置 `property`，佐证 Python 封装为约定式、非强制，https://docs.python.org/3.11/tutorial/classes.html ，核实 2026-07-26。

## 2.4 接口/实现分离与表示不变式 — 抽象函数与表示不变式

### 2.4.1 接口/实现分离是这一切的落点

前三个小主题反复讲「接口 vs 实现」，本节给它一套**能钉死契约、可机器检查**的工具：表示不变式（representation invariant, RI）与抽象函数（abstraction function, AF）。它们回答两个问题——「什么样的内部状态才算一个合法的对象？」（RI）和「一份合法的内部状态到底代表哪个抽象值？」（AF）。有了这两样，「实现是否忠于接口」就从口头承诺变成可以写下来、可以断言的东西。

一处取证澄清（承接篇首）：RI 与 AF 这两个术语**不出现在 Liskov 1987 论文里**。1987 那篇给的是 rep/封装/规范/局部性（2.3 已引），以及子类型替换性质（归大主题 5）。RI/AF 的一手源是 Hoare 1972《Proof of Correctness of Data Representations》（提出用一个从表示到抽象值的映射加不变式来证明数据表示正确，正是 Liskov 论文的参考文献 [5]）与 Liskov & Guttag 1986《Abstraction and Specification in Program Development》（参考文献 [11]）；现代教学的标准表述见 MIT 6.005/6.031。本节据此分账。

### 2.4.2 表示不变式 RI

表示不变式是一个**从表示值到布尔的函数**，它刻画「哪些内部状态是合法/良构的对象」：

RI : R → boolean

对某个表示值 r，`RI(r)` 为真，当且仅当 r 是一个良构的、能被抽象函数解释的表示。以「已约分的有理数」为例，rep 是一对整数 `(n, d)`，其表示不变式可以写成：

RI(n, d) = (d > 0) ∧ (gcd(|n|, d) = 1)

即分母为正、且分子分母互质（最简）。所有操作——构造器、以及任何会改状态的操作——都**必须维持 RI**：进来时 RI 成立，出去时 RI 仍成立。工程上常写一个 `checkRep()` 在关键处断言它：

```python
from math import gcd

class Rational:
    def __init__(self, n, d):
        g = gcd(n, d)
        if d < 0: g = -g            # 把符号规整到分子
        self._n, self._d = n // g, d // g
        self._check_rep()

    def _check_rep(self):           # 断言表示不变式
        assert self._d > 0
        assert gcd(abs(self._n), self._d) == 1
```

RI 的直觉是「良品判据」：不是随便一对整数都算合法有理数——`(2, 4)` 没约分、`(1, 0)` 分母为零，都不良构。RI 就是贴在流水线出口的质检标准，每个操作交货前都得过检。

### 2.4.3 抽象函数 AF

抽象函数是一个**从（满足 RI 的）表示值到抽象值的映射**：

AF : R → A

它回答「这份内部状态代表哪个抽象值」。对有理数，`AF(n, d) = 数学上的有理数 n/d`。AF 有个重要性质：它通常是**多对一（many-to-one）**、满射而未必单射——多个表示可以代表同一个抽象值。比如不加约分时 `(1, 2)`、`(2, 4)`、`(3, 6)` 都映到同一个抽象有理数 ½。MIT 6.005/6.031 的表述是：AF 把表示值映到抽象值，是满射（每个抽象值都有表示代表它），但一般不是单射（同一抽象值可有多个表示代表）。

正因为 AF 多对一，我们才需要 RI 来**收窄 AF 的定义域**：RI 挑出「合法的」那些表示，AF 只负责解释这些合法表示。两者的分工可以记成——RI 圈定哪些 r 有意义，AF 说明每个有意义的 r 意味着什么。

### 2.4.4 两者协作、checkRep 与表示泄漏

RI 和 AF 合起来，就是「实现忠于接口」的可检查版契约：RI 保证内部状态始终良构，AF 固定了「良构状态 ↔ 抽象值」的解释；只要每个操作都维持 RI、且其行为在 AF 之下与规范一致，实现就正确。这也让 2.3 的「换实现不影响用户」有了可操作的判据：换 rep 就是换 R、换 RI、换 AF，只要对外行为（经 AF 观察）不变即可。

一个必须警惕的破坏源是**表示泄漏（rep exposure）**：内部表示的引用泄露给了外部，使外部能绕过操作直接改 rep，从而在实现无法察觉的情况下打破 RI。经典触发是「构造器/选择器直接把内部可变对象的引用交出去」：

```python
class IntSet:
    def __init__(self, items):
        self._items = list(items)     # 若写成 self._items = items 就泄漏了入参

    def as_list(self):
        return list(self._items)      # 返回副本；若 return self._items 则泄漏内部引用
```

只要外部拿到 `self._items` 本身，就能 `append` 一个破坏不变式的值，而 `IntSet` 的操作根本没机会检查。防表示泄漏的常规手段是**进出都拷贝**、或返回不可变视图。初学者的抓手：封装不只是「别人别乱调我的方法」，更是「别把我家钥匙（内部引用）递出门」。

### 2.4.5 归属与冲突辨析

对同一组概念，来源在「术语归属」上有分歧，两边都记：本课 prompt 与部分二手教程把「抽象函数/表示不变式」挂在 Liskov 1987 名下；但核对 1987 论文全文，其中**并无** "abstraction function" 与 "representation invariant" 字样，只有 rep/封装/规范/局部性与子类型替换性质。这两个术语的一手出处是 Hoare 1972（表示正确性证明中的抽象映射 + 不变式）与 Liskov & Guttag 1986（标准化为 AF/RI 的规范方法），现代教学锚为 MIT 6.005/6.031。本报告按此分账，不把 AF/RI 硬记到 1987 那篇。至于 Liskov 1987 里那条真正属于它的贡献——子类型替换性质（LSP 之源），归本课大主题 5，不在此展开。

#### 来源与时效

- C.A.R. Hoare《Proof of Correctness of Data Representations》(Acta Informatica 1, 1972, 271–281)：以「从具体表示到抽象值的映射 + 表示上的不变式」证明数据表示正确——AF/RI 思想的一手源（Liskov 1987 参考文献 [5]），核实 2026-07-26。
- Liskov & Guttag《Abstraction and Specification in Program Development》(MIT Press/McGraw-Hill, 1986)（Liskov 1987 参考文献 [11]）：把抽象函数与表示不变式标准化为 ADT 规范方法；MIT 6.005/6.031 Reading 给出教学定义 `AF : R → A`（满射、多对一）、`RI : R → boolean`、`checkRep()`、rep exposure，https://web.mit.edu/6.005/www/fa14/classes/09-af-ri-equality/ ，核实 2026-07-26。
- Barbara Liskov《Data Abstraction and Hierarchy》(1987)：提供 rep/封装/规范/局部性（见 2.3）与子类型替换性质；经核对全文，**不含** AF/RI 术语。
- 冲突项定位：「AF/RI 的归属」——prompt/部分二手挂 Liskov 1987 ｜ 一手实为 Hoare 1972 + Liskov & Guttag 1986；本报告以后者为准，1987 论文只承担 ADT/封装/信息隐藏。
