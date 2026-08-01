# L3-04·大主题10 设计模式作为范式产物（可选）

> 基线 Py3.11.15/np2.4.6/gcc13.3 @2026-07-25 ｜ 核实日期：2026-07-26 ｜ 先修：L3-04 大主题3（封装/继承/多态三支柱）、大主题5（接口/抽象基类/组合优于继承）、大主题7（一等与高阶函数、闭包） ｜ 一手锚点：GoF《Design Patterns: Elements of Reusable Object-Oriented Software》(Gamma/Helm/Johnson/Vlissides, 1994, Addison-Wesley) —— 创建型/结构型/行为型三章及第 1 章「What Is a Design Pattern」与两大设计原则；Python 3.11 官方文档（`functools.wraps`、Compound statements §8「Function definitions」中的 decorator 语法、`abc` 模块）；PEP 318「Decorators for Functions and Methods」；Peter Norvig《Design Patterns in Dynamic Programming》(1996) ｜ 成熟度：GA/稳定（GoF 分类为 1994 年经典结晶，30 年无实质漂移；Python decorator 语法自 2.4a2 引入 `@`，Py3 起为稳定语言特性）

设计模式（design pattern）不是可以 import 的库函数，而是「对某类反复出现的设计问题，一个经过提炼的、可复用的解决方案骨架」。GoF 1994 年的书把 23 个面向对象模式编目为三类——创建型（怎么造对象）、结构型（怎么把对象/类组装成更大结构）、行为型（对象之间怎么分配职责、怎么通信），每个模式用统一的条目描述：意图（Intent）、动机（Motivation）、适用性（Applicability）、结构（Structure，UML 图）、参与者与协作、后果、实现要点。理解本报告的关键心态是：**模式是范式的「产物」而非「零件」**——它们是 OO 程序员在缺少某些语言特性（如一等函数、多方法分派）时，用类与对象反复拼出来的固定套路；换一种范式（如函数式），同一个问题可能一行高阶函数就解决，模式随之「消解」（10.4 展开）。

GoF 全书贯穿两条设计原则，几乎所有模式都是它们的具体化，先记住它们后面每个模式都好懂：「Program to an interface, not an implementation」（面向接口而非实现编程——依赖抽象类型，不依赖具体类），以及「Favor object composition over class inheritance」（优先用对象组合而非类继承来复用——组合更灵活、耦合更松）。本报告所有 Python 示例均在基线环境（Python 3.11.15、Linux 6.18.5 x86_64）下真实运行，命令与输出随节贴出；GoF 原书以 C++/Smalltalk 举例，本报告用 Python 佐证同一意图时，会把「模式的语言无关意图」与「Python 的具体表达」分开标注。

---

## 10.1 创建型模式 — 工厂/单例

### 10.1.1 创建型模式解决什么问题

创建型模式（creational patterns）关注「对象是怎么被创建出来的」，其共同目的是把「使用一个对象」与「决定造出哪个具体类的对象」这两件事解耦。GoF 列了 5 个：Abstract Factory、Builder、Factory Method、Prototype、Singleton。它们的共同手法是把 `new`（在 Python 里是直接调用类名）这个「写死具体类」的动作，藏到一层抽象后面，让客户端代码只依赖抽象接口。

直接写 `obj = Circle()` 时，这行代码就和「Circle 这个具体类」焊死了——以后想换成 `Square` 就得改这行。创建型模式的统一直觉是「给造对象这件事加一道可替换的开关」。这正是 §10 开头那条原则「面向接口而非实现编程」在**对象创建**环节的落地。

### 10.1.2 工厂方法与抽象工厂

工厂方法（Factory Method）的意图（GoF 原文）是：「定义一个用于创建对象的接口，但让子类决定实例化哪一个类。」即父类里留一个「创建产品」的方法作为占位，具体造哪个类交给子类覆盖。抽象工厂（Abstract Factory）的意图是：「提供一个接口，用于创建一系列相关或相互依赖的对象，而无需指定它们的具体类」——它是「工厂的工厂」，一次产出一整族配套的产品（例如「深色主题」的按钮+滚动条+菜单一整套）。

工厂方法解决「造一个产品，具体子类型待定」；抽象工厂解决「造一整族互相搭配的产品，整族一起切换」。两者都遵循同一原则——客户端拿到的是抽象产品接口，不知道也不关心背后是哪个具体类。易错点是把它们和下面 10.1.3 的「Python 工厂函数」混为一谈：GoF 的工厂方法是**靠继承+覆盖**实现的类结构，而 Python 里常见的「工厂函数」是更轻的过程式写法。

### 10.1.3 Python 里的「工厂函数」惯用法

在 Python 中，因为类本身就是一等对象（可以放进字典、当参数传），GoF 那套「靠子类覆盖工厂方法」的重结构常被压扁成一个普通函数：函数内部根据参数选一个类实例化并返回。这就是课程锚点所指的「工厂函数」。

真实运行，一个按字符串选具体类的工厂函数：

```python
class Circle:
    def area(self): return "circle"
class Square:
    def area(self): return "square"
def make_shape(kind):
    return {"circle": Circle, "square": Square}[kind]()
s = make_shape("circle")
print(type(s).__name__, s.area())
```

```text
Circle circle
```

客户端只调用 `make_shape("circle")`，完全不出现具体类名，想新增形状只改工厂内部的映射表。这里要点明「规范 vs 实现」的分账：GoF 的 Factory Method 是一个**基于继承的类协作结构**，而这个 `make_shape` 是 Python 借「类是一等对象」把它退化成的过程式惯用法——两者意图相同（解耦创建），实现形态不同。这个「模式在动态语言里被压扁」的现象，正是 10.4 的预演。

### 10.1.4 单例

单例（Singleton）的意图（GoF 原文）是：「确保一个类只有一个实例，并提供一个访问它的全局访问点。」典型用途是全局配置、日志器、连接池这类「整个程序应当只有一份」的对象。

真实运行，用 `__new__` 拦截实例化、始终返回同一个对象：

```python
class Config:
    _inst = None
    def __new__(cls):
        if cls._inst is None:
            cls._inst = super().__new__(cls)
        return cls._inst
a = Config(); b = Config()
print(a is b)
```

```text
True
```

`a is b` 为 `True` 说明两次 `Config()` 拿到的是同一个对象（`is` 比较身份，见 L1-01 的 `is` vs `==`）。初学者要知道的两点：其一，在 Python 里更地道的「单例」往往直接用**模块级对象**——模块本身天然只加载一次，一个模块级变量就是全局唯一实例，不必写 `__new__`；其二，单例在现代设计里争议很大，常被视为「乔装的全局变量」，会带来隐藏依赖和测试困难，所以「能用模块/依赖注入就别硬写 Singleton」。这类工程权衡的深挖归 L4-06，此处只给语言层用法。

#### 来源与时效（本小主题末集中列）
- GoF《Design Patterns》(1994)：Creational Patterns 章，Factory Method / Abstract Factory / Singleton 的 Intent 原文。二手交叉核对：InformIT「Design Patterns: Abstract Factory」条目、CMU 15-214「All the GoF Patterns」讲义（https://www.cs.cmu.edu/~charlie/courses/15-214/2016-spring/slides/24%20-%20All%20the%20GoF%20Patterns.pdf ），核实 2026-07-26。
- 创建型 5 模式清单交叉核对：DigitalOcean「Gang of 4 Design Patterns」（https://www.digitalocean.com/community/tutorials/gangs-of-four-gof-design-patterns ）与 GeeksforGeeks「GoF Design Patterns」一致，核实 2026-07-26。
- 本机旁证：`make_shape` 输出 `Circle circle`、`Config()` 单例 `a is b == True`，Python 3.11.15，核实 2026-07-26。

## 10.2 结构型模式 — 适配器/装饰器

### 10.2.1 结构型模式解决什么问题

结构型模式（structural patterns）关注「如何把类和对象组合成更大的结构」，核心手法是**组合/委托**而非继承——正呼应 §10 开头「优先用对象组合」的原则。GoF 列了 7 个：Adapter、Bridge、Composite、Decorator、Facade、Flyweight、Proxy。（注：网络二手资料偶把 Flyweight 误列进行为型，GoF 原书归**结构型**，以原书为准。）本节按课程锚点讲最常用的两个：适配器与装饰器。

结构型模式回答的问题都是「已经有一堆现成对象了，怎么把它们拼装/包裹起来，让它们协作或让接口对得上」，而不涉及「怎么造对象」（那是创建型）或「谁调用谁」（那是行为型）。

### 10.2.2 适配器

适配器（Adapter）的意图（GoF 原文）是：「将一个类的接口转换成客户期望的另一个接口」，让原本因接口不兼容而无法协作的类能一起工作。生活类比就是电源转换插头：设备要的是一种插孔，墙上的是另一种，中间塞一个转换头。

真实运行，把「欧标插座（`voltage()`）」适配成「美制设备期望的接口（`get_volts()`）」：

```python
class EuropeanSocket:
    def voltage(self): return 230
class USDevice:
    def __init__(self, socket): self.socket = socket
    def run(self): return f"run at {self.socket.get_volts()}V"
class SocketAdapter:
    def __init__(self, eu): self.eu = eu
    def get_volts(self): return self.eu.voltage()
dev = USDevice(SocketAdapter(EuropeanSocket()))
print(dev.run())
```

```text
run at 230V
```

`USDevice` 只认识 `get_volts()`，`EuropeanSocket` 只提供 `voltage()`；`SocketAdapter` 持有一个欧标插座（组合），把 `get_volts()` 的调用转发成 `voltage()`。要点是适配器**只做接口翻译、不加新功能**——这正是它和下面装饰器的分水岭：适配器改变接口、保持功能；装饰器保持接口、增加功能。

### 10.2.3 装饰器模式（对象包装）

装饰器模式（Decorator）的意图（GoF 原文）是：「动态地给一个对象附加额外的职责」，是「通过子类扩展功能」的一种更灵活的替代。做法是让「装饰者」和「被装饰者」实现同一个接口，装饰者内部持有一个被装饰对象（组合），在转发调用的前后插入自己的行为，因此可以层层叠套。

真实运行，给咖啡对象一层层加料，每层保持同样的 `cost()/desc()` 接口：

```python
class Coffee:
    def cost(self): return 2.0
    def desc(self): return "coffee"
class MilkDecorator:
    def __init__(self, wrapped): self.wrapped = wrapped
    def cost(self): return self.wrapped.cost() + 0.5
    def desc(self): return self.wrapped.desc() + "+milk"
c = MilkDecorator(MilkDecorator(Coffee()))
print(c.desc(), c.cost())
```

```text
coffee+milk+milk 3.0
```

两层 `MilkDecorator` 包住一个 `Coffee`，最外层调用被逐层转发到内层再逐层加价，得到 `3.0`。因为装饰者和被装饰者接口一致，客户端拿到 `c` 时无从（也无需）分辨它是「裸咖啡」还是「加了两次奶的咖啡」——这就是「保持接口、叠加职责」。相较于「为每种加料组合各写一个子类」（组合爆炸），装饰器用运行时组合避免了子类泛滥。

### 10.2.4 Python 的 @decorator 与 GoF 装饰器的异同

Python 的 `@decorator` 语法（PEP 318 引入，`@` 记号自 2.4a2 起）和 GoF 的 Decorator 模式**同名但不是同一回事**，初学者极易混淆，必须点破。Python 装饰器是一种**语法糖**：`@d` 写在函数定义前，等价于「定义完这个函数后，把它传给 `d`，再把返回值绑回原名字」。

也就是说，下面两段完全等价：

```text
@d
def f(): ...
```

```text
def f(): ...
f = d(f)
```

真实运行，一个「记录调用」的函数装饰器，并用 `functools.wraps` 保住原函数名：

```python
import functools
def logged(fn):
    @functools.wraps(fn)
    def wrapper(*a, **k):
        print(f"calling {fn.__name__}")
        return fn(*a, **k)
    return wrapper
@logged
def add(x, y): return x + y
print(add(2, 3))
print(add.__name__)
```

```text
calling add
5
add
```

联系与区别：两者的**共同精神**都是「包裹一个东西、在不改它本体的前提下加行为」，所以 Python 借用了「decorator」这个名字。但**层次不同**——GoF 装饰器包裹的是**对象**、目的是运行时叠加职责且保持接口；Python 装饰器包裹的是**函数/类本身**、是一次性的定义期变换（返回一个新的可调用对象）。因为 Python 有一等函数，「包裹并返回新函数」这件事一行 `return wrapper` 就成，不需要 GoF 那套「实现同一接口的装饰者类」——这又是一处「语言特性让模式退化」的实例（10.4）。`functools.wraps` 的作用是把原函数的 `__name__`、`__doc__` 等元数据复制到 `wrapper` 上，否则 `add.__name__` 会变成 `wrapper`，调试时看不出真身。

#### 来源与时效（本小主题末集中列）
- GoF《Design Patterns》(1994)：Structural Patterns 章，Adapter / Decorator 的 Intent 原文；结构型 7 模式（含 Flyweight）归类以原书为准。二手交叉核对：DigitalOcean「Gang of 4 Design Patterns」（https://www.digitalocean.com/community/tutorials/gangs-of-four-gof-design-patterns ）、多伦多大学 ECE444 设计模式讲义，核实 2026-07-26。冲突标注：部分二手博客将 Flyweight 误置于行为型，与 GoF 原书结构型分类冲突，采信原书。
- Python 装饰器语法与语义：PEP 318「Decorators for Functions and Methods」（https://peps.python.org/pep-0318/ ，`@` 自 2.4a2 引入）+ Python 3.11 官方文档 `functools.wraps`，核实 2026-07-26。
- 本机旁证：Adapter 输出 `run at 230V`、Decorator 叠套输出 `coffee+milk+milk 3.0`、`@logged` 输出 `calling add / 5 / add`，Python 3.11.15，核实 2026-07-26。

## 10.3 行为型模式 — 策略/观察者

### 10.3.1 行为型模式解决什么问题

行为型模式（behavioral patterns）关注「对象之间如何分配职责、如何通信与协作」——不是「怎么造」（创建型）也不是「怎么拼」（结构型），而是「运行时谁负责做什么、谁通知谁」。GoF 列了 11 个：Chain of Responsibility、Command、Interpreter、Iterator、Mediator、Memento、Observer、State、Strategy、Template Method、Visitor。本节按锚点讲策略与观察者。

行为型模式常见的动机是「把会变化的行为/算法从固定的骨架里抽出来，做成可替换、可插拔、可监听的部件」，从而让骨架稳定、变化被隔离。

### 10.3.2 策略

策略（Strategy）的意图（GoF 原文）是：「定义一系列算法，把每一个都封装起来，并使它们可以互相替换。」策略让算法的变化独立于使用算法的客户端——同一个「上下文（Context）」可以在运行时换用不同策略，行为随之改变。

真实运行，一个排序上下文换用升序/降序两种策略：

```python
class Context:
    def __init__(self, strategy): self.strategy = strategy
    def do(self, data): return self.strategy.sort(data)
class Ascending:
    def sort(self, d): return sorted(d)
class Descending:
    def sort(self, d): return sorted(d, reverse=True)
print(Context(Ascending()).do([3,1,2]))
print(Context(Descending()).do([3,1,2]))
```

```text
[1, 2, 3]
[3, 2, 1]
```

`Context` 不关心具体排序法，只调用 `self.strategy.sort(...)`；换一个策略对象就换了行为。这又是「面向接口而非实现编程」的直接体现——`Context` 依赖的是「有 `sort` 方法」这个抽象接口。策略与 10.2 装饰器的区别：策略**替换**一整个算法，装饰器**叠加**职责。记住这个例子，10.4 会把它「消解」成一行传函数。

### 10.3.3 观察者

观察者（Observer）的意图（GoF 原文）是：「定义对象间的一对多依赖关系，当一个对象（主题 Subject）状态改变时，所有依赖它的对象（观察者 Observer）都会自动得到通知并更新。」这是「发布—订阅」的经典骨架，用于解耦「状态源」和「对状态变化作出反应的多方」。

真实运行，一个主题通知两个订阅者：

```python
class Subject:
    def __init__(self): self._obs = []
    def attach(self, o): self._obs.append(o)
    def notify(self, msg):
        for o in self._obs: o.update(msg)
class Logger:
    def __init__(self, name): self.name = name
    def update(self, msg): print(f"{self.name} got: {msg}")
s = Subject(); s.attach(Logger("A")); s.attach(Logger("B"))
s.notify("state changed")
```

```text
A got: state changed
B got: state changed
```

`Subject` 只维护一个观察者列表并逐个回调 `update`，它不知道也不需要知道每个观察者具体做什么——新增/移除观察者都不改 `Subject`。这就是「一对多、自动通知、松耦合」。初学者会在 GUI 事件、数据绑定、消息总线里反复见到它。要点：主题只依赖「观察者有 `update` 方法」这个抽象契约，符合面向接口原则。

#### 来源与时效（本小主题末集中列）
- GoF《Design Patterns》(1994)：Behavioral Patterns 章，Strategy / Observer 的 Intent 原文；行为型 11 模式清单。二手交叉核对：Rose-Hulman CSSE374「Adapter, Factory, Singleton, and Strategy」讲义、多伦多大学 ECE444「Observer, Adapter, Proxy, Decorator」讲义、DigitalOcean GoF 综述，核实 2026-07-26。
- 本机旁证：Strategy 输出 `[1, 2, 3]` / `[3, 2, 1]`，Observer 输出 `A got: state changed` / `B got: state changed`，Python 3.11.15，核实 2026-07-26。

## 10.4 FP 视角下的模式消解

### 10.4.1 「模式是缺失语言特性的补偿」这一视角

有一个影响深远的观点：**很多 GoF 模式之所以存在，是因为 C++/Java 那类语言当年缺少某些抽象手段（尤其一等函数）；当语言本身把这些能力内建后，对应模式就退化成语言的一个平凡用法，甚至「消失」。** 这不是贬低模式，而是把模式看清为「特定范式在特定语言表达力下的产物」——正是本大主题标题「设计模式作为范式产物」的含义。

GoF 的模式几乎都在用「类和对象」模拟「一段可以被传来传去、可以被替换的行为」。而在函数式/动态语言里，「行为」本身就是一等值（函数就是值，见大主题7），可以直接传递，于是那圈「为了把行为包成对象」而搭的类结构就没必要了。

### 10.4.2 策略 = 传一个函数

把 10.3.2 的策略模式拿来对照最直观：GoF 版需要一个 `Context` 类、一个策略接口、每种算法一个策略类；但如果函数是一等的，「策略」就是「一个函数」，「换策略」就是「传另一个函数」。

真实运行，与 10.3.2 完全等价、但没有任何策略类：

```python
def run_ctx(strategy, data): return strategy(data)
print(run_ctx(sorted, [3,1,2]))
print(run_ctx(lambda d: sorted(d, reverse=True), [3,1,2]))
```

```text
[1, 2, 3]
[3, 2, 1]
```

对照 10.3.2：那里为「升序/降序」各写了一个类，这里直接把 `sorted` 或一个 lambda 当参数传进去，输出一模一样。这就是「策略模式消解为高阶函数」：策略接口坍缩成「可调用（callable）」，策略类坍缩成「函数值」。同理，10.2.4 已经看到「装饰器」在有一等函数时坍缩成「返回新函数的函数」。这条线索能帮初学者把大主题7（高阶函数）和本主题打通：**模式不是越多越好，语言表达力上去了，该消解就消解。**

### 10.4.3 哪些模式消解、哪些不消解

「模式会消解」不等于「模式全都没用了」，要避免走极端。Peter Norvig 在《Design Patterns in Dynamic Programming》(1996) 里做了一个常被引用的量化考察：GoF 的 23 个模式中，有 **16 个** 在 Lisp/Dylan 这类动态语言里「至少在某些用法上有本质上更简单的实现」；其中真正**因为一等函数而简化**的是 4 个——Command、Strategy、Template Method、Visitor。其余简化则来自别的语言特性（如一等类型、宏、多方法分派等），并非都归功于函数式。

这给初学者两个清醒的界限。其一，「消解」是有条件、可量化的，不是「所有模式一律退化成 lambda」——把每个模式无脑改写成传函数是另一种教条。其二，剩下约三分之一的模式（以及那 16 个在另一些用法下）仍有其结构性价值，尤其当问题确实需要「有身份、有状态的对象」而非「一段纯行为」时。因此本主题的正确收尾是：**模式是范式与语言表达力共同作用的产物，看清它「为何存在」比背诵它的类图更重要**；具体到工程里该不该用某模式、如何组织，属于方法学范畴，深挖归 L4-06。

#### 来源与时效（本小主题末集中列）
- Peter Norvig《Design Patterns in Dynamic Programming》(1996)，https://norvig.com/design-patterns/ ：23 模式中 16 个在动态语言有更简单实现，其中 Command/Strategy/Template-Method/Visitor 因一等函数而简化。核实 2026-07-26。
- 一等函数 = 可传递的行为：与 L3-04 大主题7（一等与高阶函数）交叉印证；策略消解为传函数的对照以本机运行为据。二手交叉核对：O'Reilly《Fluent Python》「first-class functions to implement design patterns」章节讨论一致，核实 2026-07-26。
- 冲突/边界标注：Norvig 的量化针对 Lisp/Dylan，「因一等函数简化」严格计为 4 个；坊间「函数式让 GoF 模式全部消失」的说法夸大，与 Norvig 原始计数冲突，采信 Norvig。
- 本机旁证：`run_ctx(sorted,...)` 与 `run_ctx(lambda...)` 输出 `[1, 2, 3]` / `[3, 2, 1]`，与 10.3.2 类版本等价，Python 3.11.15，核实 2026-07-26。
