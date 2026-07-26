# L3-04·大主题3 OO 三支柱：封装/继承/多态

> 基线 Py3.11.15/np2.4.6/gcc13.3 @2026-07-25 ｜ 核实日期：2026-07-26 ｜ 先修：L1-01 程序设计入门（值/变量、函数、可变性入门）、本课大主题 1（范式总览）与大主题 2（数据抽象与封装的数据侧） ｜ 一手锚点：SICP《Structure and Interpretation of Computer Programs》(Abelson & Sussman, 2nd ed, MIT Press) — §3.1（Assignment and Local State：局部状态、对象、消息传递）、§2.4（Multiple Representations for Abstract Data）、§2.5（Systems with Generic Operations：通用型操作、数据导向编程）；Python 3.11 官方文档 — Data Model（`__dict__`、`__getattribute__`、描述符协议、实例方法绑定，docs.python.org/3.11/reference/datamodel.html）、Language Reference §6.2.1（私有名字改写 name mangling）、`functools` 库（`singledispatch`，docs.python.org/3.11/library/functools.html） ｜ 成熟度：GA/稳定（OO 语义为经典理论，无版本漂移；Python 示例锚 3.11.15）
>
> 粒度判定：**1 份**（不拆）。理由：本大主题 5 个小主题（3.1–3.5）是一条连贯主线——「对象把状态和行为捆在一起（封装）→ 类/实例如何按查找规则找到属性和方法（消息传递模型）→ 用继承表达 is-a 并覆盖行为 → 覆盖带来运行时按对象选行为（子类型多态）→ 把多态推广到跨多种数据表示的通用操作」，跨机制少、彼此高度承接，合成一份最利于初学者顺读。跨课边界：派发（vtable / MRO / 鸭子类型）的机制细节归本课大主题 4，接口与设计原则（ABC/LSP/组合优于继承）归大主题 5，泛型与参数多态归大主题 6；本报告只讲三支柱的语义与 Python 用法，不深挖派发的机器层落地（那归 L3-03）。

本报告所有 Python 示例基于 Python 3.11.15、Linux 6.18.5 x86_64，对应上面的基线串；关键片段的真实运行输出以代码块紧随其后给出，可复现。

---

## 3.1 对象=状态+行为、局部状态与封装

### 3.1.1 对象是状态与行为的捆绑

面向对象的最小单位是**对象**：它把一组**数据（状态）** 和**作用在这组数据上的过程（行为/方法）** 打包在一起，对外只以「发消息（调用方法）」的方式交互。SICP §3.1 用这一世界观作为引入赋值与可变状态的动机——现实里我们把世界看成一堆各自带着「会随时间变化的状态」的独立对象，OO 就是把这种直觉搬进程序。判断一个对象「有状态」的标准是：它当前的行为受自身历史影响。

初学者最好的抓手是 SICP 的银行账户例子：一个账户能不能取出 60 元，取决于此前存过取过多少——也就是它「当前余额」这个局部状态。你对它发 `withdraw(60)` 这条消息，它内部改写自己的余额并回话。状态藏在对象里、只能通过它认可的消息去动，这就是「对象=状态+行为」。

```python
class Account:
    def __init__(self, balance):
        self._balance = balance          # 局部状态
    def withdraw(self, amt):             # 行为：一条消息
        if amt <= self._balance:
            self._balance -= amt
            return self._balance
        return "insufficient"

a = Account(100); b = Account(100)
print(a.withdraw(60), b._balance)
```

```text
40 100
```

### 3.1.2 局部状态：每个对象各有一份

**局部状态**指状态被封在单个对象内部，不同对象各持一份、互不干扰。上面的 `a.withdraw(60)` 只改了 `a` 的余额，`b` 仍是 100——这正是局部状态的意义：对象之间隔离。SICP §3.1.1 强调，正是「每个对象维护自己的局部状态变量」，才让我们能用许多相互独立的对象拼出一个大系统。

对照 L1-01 学过的普通函数：纯函数没有跨调用记忆，同样输入永远同样输出。而带局部状态的对象「有记忆」，同样的 `withdraw(60)` 第一次和第二次结果不同。这份「记忆」就是可变状态，它是 OO 建模现实的能力来源，代价是让「程序此刻的行为」依赖执行历史（引入赋值的代价，深挖归本课大主题 8）。

### 3.1.3 封装：把表示藏起来，只暴露接口

**封装（encapsulation）** 是把对象的内部表示隐藏起来，只通过一组公开方法（接口）与外界打交道。好处是：调用方不依赖你内部怎么存数据，你日后换实现（比如余额从整数换成 Decimal）也不会波及别人的代码。这是大主题 2「抽象屏障 / 信息隐藏」思想在对象层面的落地。

Python 不像 Java/C++ 有强制的 `private` 关键字，它靠**约定 + 一条轻量语言机制**来做封装。约定是：单前导下划线 `_balance` 表示「这是内部的，请别从外面碰」，但语言不拦你。真正有语言支持的是**双前导下划线的名字改写（name mangling）**：类体里写的 `self.__secret` 会被编译器改写成 `self._类名__secret`，从而让外部按 `obj.__secret` 直接访问失败（并非加密，只是改了名字、主要用于避免子类命名冲突）。

```python
class Safe:
    def __init__(self):
        self.__secret = 42          # 被改写为 _Safe__secret

s = Safe()
print(list(s.__dict__.keys()))     # 实际存的键名
print(s._Safe__secret)             # 用改写后的名字仍能拿到
```

```text
['_Safe__secret']
42
```

一个初学者易错点：把双下划线当成「真正的私有/加密」。它拦不住有意访问（`s._Safe__secret` 照样读到 42），只是把名字改掉以免误撞和子类覆盖。要做「读写受控」的封装，更常用的是 `property`（见 3.2.5）。

#### 来源与时效（本小主题末集中列）
- SICP 2nd ed §3.1、§3.1.1（Local State Variables、对象与银行账户、局部状态变量），https://sarabander.github.io/sicp/html/index.xhtml ，核实 2026-07-26。
- Python 3.11 Language Reference §6.2.1（Identifiers / 私有名字改写：`__spam` 在类体内被文本替换为 `_classname__spam`），docs.python.org/3.11/reference/expressions.html 与 tutorial §9.6，核实 2026-07-26；本机 Python 3.11.15 运行输出佐证（改写后键名 `_Safe__secret`）。
- 规范与实现分账：「封装=隐藏表示只留接口」是语言无关的 OO 语义（SICP / Liskov 传统）；Python 无强制访问控制、以命名约定 + name mangling 近似实现——这是 Python 具体实现行为，与 Java/C++ 的强制 `private` 分开记。

## 3.2 类、实例与消息传递模型

### 3.2.1 类是模板，实例是按模板造出的对象

**类（class）** 描述「这一类对象长什么样、能干什么」（有哪些属性、哪些方法），**实例（instance）** 是按类造出来的一个个具体对象。调用类 `Account(100)` 就创建一个实例，其中 `__init__` 负责给这个新对象装上初始的局部状态。同一个类可以造出任意多个实例，各带自己的状态。

心智模型是「饼干模具与饼干」：类是模具，规定形状；每块饼干（实例）是独立的实体，可以撒不同的糖（不同的实例属性值），但都符合模具定义的形状（都有类定义的方法）。

### 3.2.2 实例 `__dict__`：状态实际存在哪里

在 Python 里，一个普通实例的属性存在它自己的命名空间 `__dict__`（一个字典）里；类的属性和方法则存在类对象的 `__dict__` 里。官方 Data Model 的原话是：实例的命名空间「是属性引用最先被搜索的地方」。

```python
class C:
    cls_attr = "on class"           # 存进 C.__dict__
    def __init__(self):
        self.inst_attr = "on instance"   # 存进实例 __dict__
    def m(self): return "method"    # 也存进 C.__dict__

c = C()
print(c.__dict__)
print('cls_attr' in C.__dict__, 'cls_attr' in c.__dict__)
```

```text
{'inst_attr': 'on instance'}
True False
```

看输出就懂了：实例 `__dict__` 里只有实例属性 `inst_attr`；类属性 `cls_attr` 和方法 `m` 都不在实例字典里，而在类字典里。理解「状态放实例、行为放类」是读懂后面查找规则的前提。

### 3.2.3 属性查找链：先实例、再类、再基类

访问 `obj.x` 时，Python 默认按一条**查找链**去找。官方 Data Model 给的原话是：`a.x` 的查找链「从 `a.__dict__['x']` 开始，然后是 `type(a).__dict__['x']`，再沿 `type(a)` 的各基类继续（不含元类）」。也就是先看实例自己的字典，找不到再看类、再顺着继承链上溯。

```
obj.x  →  obj.__dict__['x']
       →  type(obj).__dict__['x']
       →  基类们.__dict__['x']（沿 MRO）
```

这条链解释了一个常见现象——**遮蔽（shadowing）**：如果给实例赋了同名属性，它就盖住类属性，因为实例字典先被查到。

```python
c = C()
c.cls_attr = "shadow"               # 写进实例 __dict__，盖住类属性
print(c.cls_attr, C.cls_attr)
```

```text
shadow on class
```

注意 `C.cls_attr` 仍是 `"on class"`：给实例赋值并不改类属性，只是在实例字典里新增了一个更"靠前"的同名项。初学者易错点正在这里——以为改 `c.cls_attr` 会影响所有实例（那需要改 `C.cls_attr`）。

### 3.2.4 方法调用就是「取出方法再发消息」

「消息传递」在 Python 里被拆成两步：先按 3.2.3 的查找链从类里取出方法，再把实例作为第一个参数喂进去调用。官方文档说得很直白：`x.f(1)` 等价于 `C.f(x, 1)`——取到的函数被「绑定」到实例上，调用时自动把实例插到参数列表最前面成为 `self`。绑定的技术手段是描述符协议：`a.x` 在类里是函数时，实际执行 `type(a).__dict__['x'].__get__(a, type(a))`，产出一个绑定方法。

```python
class Dog:
    def speak(self): return "Woof"

d = Dog()
print(d.speak())            # 发消息
print(Dog.speak(d))         # 等价的显式调用
```

```text
Woof
Woof
```

这就是为什么每个方法第一个形参都要写 `self`：它不是 Python 的关键字，只是「调用时被自动塞进来的那个实例」的惯用名。理解「`d.speak()` = `Dog.speak(d)`」能解开初学者关于 `self` 的大部分困惑。

### 3.2.5 property：让「取属性」背后跑一段方法

`property` 是把「像访问属性一样的写法」接到「一段方法逻辑」上的机制，用来做受控读写（校验、只读、惰性计算），是封装的常用工具。它在底层是一个**数据描述符**，所以能拦截对该名字的读和写。

```python
class Temp:
    def __init__(self, c): self._c = c
    @property
    def c(self): return self._c
    @c.setter
    def c(self, v):
        if v < -273.15: raise ValueError("below absolute zero")
        self._c = v

t = Temp(20)
print(t.c)                  # 看着像取属性，其实调了 getter
try:
    t.c = -300              # 触发 setter 校验
except ValueError as e:
    print("rejected:", e)
```

```text
20
rejected: below absolute zero
```

这里要点出属性查找的一条重要细则（与 3.2.3 配套）：**数据描述符优先级高于实例字典**。官方原话是「定义了 `__get__` 且定义了 `__set__` 和/或 `__delete__` 的数据描述符，总是覆盖实例字典里的同名重定义；而非数据描述符可被实例覆盖」。因为 `property` 是数据描述符，所以你没法用实例属性把它盖掉——这正是它能可靠拦截读写的原因；而普通方法是非数据描述符，所以实例可以用同名属性把方法「顶掉」。

#### 来源与时效（本小主题末集中列）
- Python 3.11 Data Model，docs.python.org/3.11/reference/datamodel.html ，核实 2026-07-26。引用点：实例命名空间「是属性引用最先被搜索的地方」；`a.x` 查找链 `a.__dict__['x']` → `type(a).__dict__['x']` → 基类；`x.f(1)` 等价 `C.f(x, 1)`、绑定方法经 `__get__` 产生；数据描述符（定义 `__set__`/`__delete__`）覆盖实例字典、非数据描述符可被实例覆盖，`property` 是数据描述符、方法是非数据描述符。
- SICP 2nd ed §3.1（消息传递 / 对象作为带局部状态的过程）作「消息传递模型」的语言无关旁证，https://sarabander.github.io/sicp/html/index.xhtml ，核实 2026-07-26。
- 本机 Python 3.11.15 运行输出佐证 `__dict__` 内容、遮蔽、`d.speak()==Dog.speak(d)`、property 校验。

## 3.3 继承与 is-a 关系

### 3.3.1 继承让子类复用并特化父类

**继承（inheritance）** 让一个**子类（subclass）** 建立在**父类/基类（superclass/base class）** 之上：子类自动获得父类的属性与方法，并可以**新增**自己的成员或**覆盖（override）** 父类已有的方法。它表达的是 **is-a（是一种）** 关系——「狗是一种动物」，所以 `Dog` 继承 `Animal`。

初学者抓手：继承是「默认全盘照搬父类，再按需改写」。你不用把父类的东西重抄一遍，只写「不一样的部分」。这既省代码，又表达了「子类属于父类这个大类」的建模意图。

```python
class Animal:
    def speak(self): return "..."
    def describe(self): return "I say " + self.speak()

class Dog(Animal):               # Dog is-a Animal
    def speak(self): return "Woof"   # 覆盖

print(isinstance(Dog(), Animal), issubclass(Dog, Animal))
print(Dog().describe())
```

```text
True True
```
```text
I say Woof
```

`isinstance`/`issubclass` 返回 `True`，正是 is-a 关系在语言层的体现：一个 `Dog` 实例「也是」一个 `Animal`。

### 3.3.2 覆盖：子类换掉父类的某个行为

**覆盖**指子类定义一个与父类同名的方法，从而在子类对象上「换掉」父类的实现。上面 `Dog.speak` 覆盖了 `Animal.speak`。覆盖之所以生效，靠的还是 3.2.3 的查找链：查 `Dog` 实例的方法时先命中 `Dog.__dict__['speak']`，就不再上溯到 `Animal` 的版本。

一个很能体现覆盖威力的现象是上面 `describe` 的输出：`describe` 定义在父类 `Animal` 里、内部调用 `self.speak()`，但因为 `self` 是个 `Dog`，`self.speak()` 解析到的是被覆盖的 `Dog.speak`，于是父类写的 `describe` 自动「用上了」子类的新行为，打印 `I say Woof`。这就是 3.4 要正式讲的多态在继承下的效果——父类代码调 `self.某方法()`，实际跑哪个由运行时对象决定。

### 3.3.3 用 super() 扩展而非丢弃父类行为

有时你不想完全替换父类方法，而是「在父类做的基础上再加点」。这时用 `super()` 调到父类版本，再补自己的逻辑。构造器里最常见：子类 `__init__` 先 `super().__init__(...)` 让父类初始化好它那部分状态，再初始化子类自己的部分。

```python
class Animal:
    def __init__(self, name): self.name = name

class Dog(Animal):
    def __init__(self, name, tricks):
        super().__init__(name)       # 复用父类初始化
        self.tricks = tricks

d = Dog("Rex", ["sit"])
print(d.name, d.tricks)
```

```text
Rex ['sit']
```

易错点：子类写了 `__init__` 却忘了 `super().__init__(...)`，父类那部分状态（这里的 `self.name`）就不会被建立，之后访问会 `AttributeError`。`super()` 的完整多继承语义（沿 MRO 走 C3 线性化）归本课大主题 4，这里只需记住它「往上一层去调父类的同名方法」。

### 3.3.4 继承的陷阱：is-a 用错就脆

继承虽方便，但用来表达非 is-a 关系时会埋雷。经典反例是「正方形继承长方形」：数学上正方形「是一种」长方形，但如果长方形有独立可设的宽和高，正方形继承它并强行让宽高相等，就会破坏「能当长方形用」的承诺（这正是里氏替换原则 LSP 的违反，深挖归本课大主题 5）。另一个常见问题是「脆弱基类」——父类改动会牵连所有子类。

因此工程上有一条经验：**优先考虑组合（把别的对象当成员持有）而非继承，只有确属 is-a 才用继承**（组合优于继承的展开归大主题 5）。初学者阶段先记住判据：能说通「A 是一种 B、且任何用到 B 的地方都能拿 A 顶上」再用继承，否则多半该用组合。

#### 来源与时效（本小主题末集中列）
- Python 3.11 Data Model / Language Reference（继承下的属性与方法查找沿基类链、`super` 语义），docs.python.org/3.11/reference/datamodel.html ；`isinstance`/`issubclass` 内置函数语义，docs.python.org/3.11/library/functions.html ，核实 2026-07-26。
- SICP 2nd ed §2.5.2（把「一种类型是另一种的特例」建成类型层级 / 强制转换塔）作 is-a 层级的语言无关旁证，https://sarabander.github.io/sicp/html/index.xhtml ，核实 2026-07-26。
- 「正方形/长方形违反 LSP」为经典 is-a 误用案例，源头是 Barbara Liskov 的子类型替换思想（Liskov & Wing 1994 形式化）；本报告只作陷阱提示，LSP 的正式表述与反例代码归本课大主题 5，此处不重复立项。
- 本机 Python 3.11.15 运行输出佐证 `isinstance`/`issubclass`、覆盖与 `super()`。

## 3.4 子类型多态与方法覆盖

### 3.4.1 多态：同一消息，按对象选行为

**子类型多态（subtype polymorphism）** 指：同一段代码对不同类型的对象发同一条消息，实际执行的行为由**运行时对象的类型**决定，而不是由变量的声明类型决定。换句话说，写 `x.speak()`，跑 `Dog` 的还是 `Cat` 的，取决于此刻 `x` 到底指向谁。SICP §2.4-2.5 把这种能力叫做「让通用操作能作用在多种表示上」。

```python
class Animal:
    def speak(self): return "..."
class Dog(Animal):
    def speak(self): return "Woof"
class Cat(Animal):
    def speak(self): return "Meow"

for x in (Dog(), Cat(), Animal()):
    print(type(x).__name__, x.speak())
```

```text
Dog Woof
Cat Meow
Animal ...
```

同一行 `x.speak()`，三次跑出三种行为——这就是多态。它让「循环里对一堆异构对象一视同仁地发消息」成为可能，是 OO 消除大量 `if type == ...` 分支的核心手段。

### 3.4.2 多态靠「动态绑定」实现

多态之所以成立，是因为方法调用采用**动态绑定 / 运行时派发（dynamic dispatch）**：到底调哪个 `speak`，是在运行时按对象实际类型、沿查找链（3.2.3）现场决定的，而不是编译期就钉死。Python 里一切方法调用天然是动态绑定的。

心智模型：变量像个「便利贴」，可以贴到不同对象上；发消息时，系统看便利贴此刻贴在谁身上，就去问谁。对照静态语言（如 C++ 非虚函数），有的调用在编译期就按声明类型定死了（静态派发）——静态派发 vs 动态派发的正式对比、以及 C++ vtable、Python MRO 的机制，都归本课大主题 4，这里只需理解语义层面「行为随运行时对象走」。

### 3.4.3 覆盖是多态的语言基础

3.3.2 的覆盖和这里的多态是一体两面：因为子类能覆盖父类方法，同一条消息在不同子类对象上才会跑出不同实现，多态才有内容。回看 3.3.2 的 `describe`——父类方法内部 `self.speak()` 会派发到子类覆盖的版本，这种「父类模板 + 子类填空」的写法（模板方法模式的雏形）正是多态最实用的形态。

一个初学者要建立的关键区分：**覆盖（override）不是重载（overload）**。覆盖是子类换掉父类同名方法、按运行时对象选（子类型多态）；重载是同名函数按参数类型/个数选不同实现（特设多态，Python 无内建的按签名重载，靠 `singledispatch` 或默认参数模拟）。重载归本课大主题 6，别混。

### 3.4.4 鸭子类型：不靠继承也能多态

Python 的多态其实比「必须继承同一父类」更宽松：只要一个对象**恰好有**被调用的那个方法/属性，它就能用，不管它是不是某个基类的子类。这叫**鸭子类型（duck typing）**——「走起来像鸭子、叫起来像鸭子，就当它是鸭子」。

```python
class Duck:
    def speak(self): return "Quack"
class Person:
    def speak(self): return "Hello"

for x in (Duck(), Person()):     # 二者无共同基类
    print(x.speak())
```

```text
Quack
Hello
```

`Duck` 和 `Person` 没有继承关系，但都能进同一个循环被发 `speak()`。这说明 Python 的子类型多态本质是「结构化」的——看有没有那个方法，而非看类型标签。鸭子类型与运行时属性查找的机制细节归本课大主题 4；这里点明它是为了让你别误以为「多态必须先建继承树」。

#### 来源与时效（本小主题末集中列）
- SICP 2nd ed §2.4（Multiple Representations）、§2.5（Generic Operations：同一操作作用于多种表示 / 运行时按类型选实现），https://sarabander.github.io/sicp/html/index.xhtml ，核实 2026-07-26。
- Python 3.11 Data Model（方法查找与动态绑定；`x.f()` 运行时沿类型链解析），docs.python.org/3.11/reference/datamodel.html ；「鸭子类型」术语见 Python 官方 glossary（docs.python.org/3.11/glossary.html#term-duck-typing），核实 2026-07-26。
- 分账：派发的机制层落地（静态 vs 动态、C++ vtable、MRO/C3、菱形继承）归本课大主题 4 与 L3-03，本报告只讲多态的语义与用法；覆盖 vs 重载中「重载/特设多态」归大主题 6。
- 本机 Python 3.11.15 运行输出佐证异构对象循环发同名消息与鸭子类型。

## 3.5 多重表示与通用型操作

### 3.5.1 多重表示：同一抽象，多种底层实现

**多重表示（multiple representations）** 指同一个抽象数据类型可以有几种不同的内部表示并存。SICP §2.4 的经典例子是复数：既能用**直角坐标**（实部、虚部）存，也能用**极坐标**（模、角）存，两种表示各有擅长的运算。问题随之而来：当系统里同时存在多种表示的对象，一个像 `magnitude`（求模）这样的操作，怎么对每种表示都正确工作？

初学者抓手：把「表示」想成同一份信息的不同记法。一个点既能记成「向东 3、向北 4」（直角），也能记成「距原点 5、方向 53°」（极坐标）；求「离原点多远」在极坐标里直接读、在直角坐标里要算 √(x²+y²)。多重表示就是允许两种记法共存，还要让「求距离」这个操作对两者都能用。

### 3.5.2 通用型操作：一个操作名，覆盖所有表示

**通用型操作（generic operation）** 是一个对多种数据表示都成立的操作：调用方只说「求这个复数的模」，不关心它是直角还是极坐标存的；系统负责把这次调用**分派（dispatch）** 到与该对象表示相匹配的具体实现。SICP §2.5 的目标就是造出这样一层通用操作，让上层代码写一次、对所有表示通用。

这与 3.4 的子类型多态是同一件事的两种视角：子类型多态强调「沿继承层级按对象选行为」，通用型操作强调「按数据的类型标签选实现」，SICP §2.5 把它推广到不必有继承关系、只需登记「类型→实现」映射的一般情形。

### 3.5.3 数据导向编程：用「类型×操作」表格分派

SICP §2.5 给出实现通用操作的一种系统办法——**数据导向编程（data-directed programming）**：维护一张以「操作名 × 类型」为索引的表，表格里放对应的具体过程；来了一次调用，就按（操作名, 参数类型）查表、取出该实现来跑。它的好处是**可加性**：新增一种表示，只需往表里登记它的那几个实现，完全不用改动已有代码里的任何分支。

对照初学者常写的「一大坨 `if isinstance(...)` 判断类型再手动分派」——那种写法每加一种新类型，就得回去改每一个这样的 `if` 链，既啰嗦又易漏。数据导向编程把分派逻辑集中成一张表，加类型 = 加表项，这是 OO/多态相对手写类型判断的核心工程优势。

### 3.5.4 Python 里的落地：functools.singledispatch

Python 标准库的 `functools.singledispatch` 正是数据导向编程/单分派的现成实现：它把一个普通函数变成**泛型函数**，**按第一个参数的类型**分派到不同实现；用 `register` 装饰器为每种类型登记实现。它在 Python 3.4 引入（后续版本增强，如 3.7 起支持用类型注解登记）。

```python
from functools import singledispatch

@singledispatch
def area(shape):
    raise NotImplementedError("unknown shape")

class Circle: pass
class Square: pass

@area.register
def _(s: Circle): return "pi*r^2"
@area.register
def _(s: Square): return "s*s"

print(area(Circle()), "|", area(Square()))
```

```text
pi*r^2 | s*s
```

这段代码正是 SICP 那张「类型→实现」表格的 Python 版：`area` 是通用操作名，`register` 往表里登记每种类型的实现，调用时按第一个实参的类型分派。要加一种新形状（比如 `Triangle`），只需再 `@area.register` 一条，无需触碰 `Circle`/`Square` 的代码——可加性一目了然。

一个易错点：`singledispatch` 是**单分派**——只看第一个参数的类型。如果你的操作要同时按两个参数的类型选实现（**多重分派 / multiple dispatch**，如 SICP §2.5.1 里 `add` 复数与有理数的跨类型运算），`singledispatch` 就不够用了，需要额外机制（如第三方 `multipledispatch` 库或手写按类型对查表）。方法名 `singledispatch` 里的 "single" 就是这个意思。

#### 来源与时效（本小主题末集中列）
- SICP 2nd ed §2.4（Multiple Representations for Abstract Data：复数直角/极坐标）、§2.5（Systems with Generic Operations：通用型操作、data-directed programming、类型标签与分派表、跨类型运算），https://sarabander.github.io/sicp/html/index.xhtml ，核实 2026-07-26。
- Python 3.11 `functools` 官方文档：`singledispatch`「dispatch happens on the type of the first argument」、`register` 登记实现、`register` 返回未装饰函数以便测试；3.4 引入（3.7、3.11 增强），docs.python.org/3.11/library/functools.html ，核实 2026-07-26；本机 Python 3.11.15 运行输出佐证按类型分派。
- 分账：SICP 的数据导向编程是「表 + 手动查表」的一般框架，`singledispatch` 是 Python 对其单分派特例的库化实现；「多重分派」为单分派的一般化，Python 无内建支持——如实标注，不编造标准库能力。参数多态/泛型容器与「特设多态=重载」的展开归本课大主题 6，此处不重复立项。
