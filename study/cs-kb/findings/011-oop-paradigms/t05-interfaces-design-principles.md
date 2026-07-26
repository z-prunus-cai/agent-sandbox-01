# L3-04·大主题5 接口、抽象与设计原则

> 基线 Py3.11.15/np2.4.6/gcc13.3 @2026-07-25 ｜ 核实日期：2026-07-26 ｜ 先修：L3-04 大主题2（数据抽象与封装）、大主题3（OO 三支柱：封装/继承/多态）、大主题4（动态派发与 MRO） ｜ 一手锚点：Barbara Liskov《Data Abstraction and Hierarchy》(OOPSLA '87 keynote, 刊于 SIGPLAN Notices 23(5), May 1988)；Liskov & Wing《A Behavioral Notion of Subtyping》(ACM TOPLAS 16(6), Nov 1994)；Bertrand Meyer《Object-Oriented Software Construction》(1988) — 开闭原则原始出处；GoF《Design Patterns》(Gamma/Helm/Johnson/Vlissides, 1994) — "favor object composition over class inheritance"；Robert C. Martin《Design Principles and Design Patterns》(2000) 与《Clean Architecture》(2017) — SOLID；PEP 3119（abc）、PEP 544（Protocol）与 Python 3.11 官方 `abc`/`typing` 文档 ｜ 成熟度：GA/稳定（经典设计原则，无版本漂移；Python 机制锚 3.11，Protocol 自 3.8 起 GA）
>
> 粒度判定：**1 份**（不拆）。理由：本大主题 4 个小主题（5.1–5.4）共享同一条主线——"如何用接口/抽象约束类型之间的关系，并据此评价一个设计好不好"。5.1 给出表达接口的语言机制（ABC/Protocol），5.2 给出接口正确性的判据（LSP），5.3 给出复用手段的取舍（组合 vs 继承），5.4 把前三者收敛成一张原则总表（SOLID）。四者环环相扣、篇幅适中、跨机制少，不足以触发拆分。跨课边界：SOLID 在架构/工程层面的方法学应用（分层、依赖注入容器、模块边界设计）显式归 L4-06，本报告只做**语言机制层的原则概览**；Rust trait 约束下的等价讨论归 L5-14；类型系统的形式化归 L5-06。

本报告的 Python 示例基于 Python 3.11.15、Linux 6.18.5 x86_64（对应上面的基线串）。5.1 中 `abc` 未实现即报错、`Protocol` 运行时检查两处贴的是本机真实输出；其余示例为说明性代码，未逐一实机跑，如未标"真实输出"即为讲解用最小片段。

---

## 5.1 抽象基类与协议 — 用语言机制强制"接口"

### 5.1.1 抽象基类 ABC 是什么

抽象基类（Abstract Base Class, ABC）是一种**不能被直接实例化、只规定"子类必须提供哪些方法"的类**。在 Python 中，它由标准库 `abc` 模块提供：给类指定元类 `ABCMeta`（最方便的写法是继承 `abc.ABC`），再用装饰器 `@abstractmethod` 把某些方法标成"抽象的"。只要一个类还留着没实现的抽象方法，试图 `Class()` 就会在实例化那一刻抛 `TypeError`。这一机制由 PEP 3119 引入，是 Python 表达"接口契约"的官方一手手段。

它的价值是把"你忘了实现某个方法"这个错误，从"运行到调用那一行才崩"提前到"一创建对象就崩"。初学者可以把 ABC 想成一张**待签合同**：合同上列了几个必须履行的条款（抽象方法），你不逐条填完，就不许把它当成正式对象拿去用。下面是本机 Python 3.11.15 的真实行为：

```python
from abc import ABC, abstractmethod

class Repository(ABC):
    @abstractmethod
    def get(self, id): ...

Repository()   # 直接实例化抽象基类
```

在本机 Python 3.11.15 上的真实输出是

```
TypeError: Can't instantiate abstract class Repository with abstract method get
```

只有当子类把 `get` 真正实现出来，子类才能被实例化。注意报错发生在"实例化"而不是"定义类"时——定义一个还没实现全的子类是允许的，Python 只在你试图造对象时才检查。

### 5.1.2 协议 Protocol 与结构化子类型

协议（Protocol）是 Python 3.8 引入、由 PEP 544 定义的另一种表达接口的方式：让类继承 `typing.Protocol`，在里面列出期望的方法签名。与 ABC 最大的不同是——**一个类不需要显式继承某个 Protocol，只要它"长得对"（具备协议要求的那些方法），就被认为满足该协议**。这叫**结构化子类型（structural subtyping）**，PEP 544 自己把它称作"静态鸭子类型（static duck typing）"。

对照 5.1.1 的 ABC：ABC 是**名义子类型（nominal）**——你必须写明 `class MyRepo(Repository)` 才算数，靠"血缘"认亲；Protocol 是结构化的——靠"形状"认亲，谁有 `__len__` 谁就是 `Sized`，无关它的祖先是谁。这恰好把大主题4 讲的"鸭子类型"用类型注解正式表达了出来：过去鸭子类型只能靠运行时试着调用，现在类型检查器（如 mypy）能在写代码时就静态判断"这个对象符不符合协议"。

```python
from typing import Protocol

class SupportsClose(Protocol):
    def close(self) -> None: ...

class File:
    def close(self) -> None:  # 没有继承 SupportsClose
        print("closed")

def shutdown(x: SupportsClose) -> None:
    x.close()

shutdown(File())   # 类型检查器认可：File 结构上满足 SupportsClose
```

默认情况下 Protocol 只在**静态检查**时起作用，运行时用 `isinstance` 去判断会报错。若想让 `isinstance`/`issubclass` 在运行时也能对协议做结构检查，需要给协议加 `@runtime_checkable` 装饰器。本机验证（Python 3.11.15）：

```python
from typing import Protocol, runtime_checkable

@runtime_checkable
class Sized(Protocol):
    def __len__(self) -> int: ...

print(isinstance([1, 2], Sized))   # True：list 有 __len__
print(isinstance(5, Sized))        # False：int 没有 __len__
```

在本机 Python 3.11.15 上的真实输出是

```
True
False
```

一个初学者易错点：`@runtime_checkable` 的运行时检查**只看方法名是否存在，不检查签名/参数类型**——它能告诉你"有没有 `__len__` 这个方法"，但不能保证这个 `__len__` 的参数和返回类型对得上，那部分只有静态类型检查器管。

### 5.1.3 ABC 与 Protocol 何时用哪个

选择的经验准则是：**你拥有并控制那些子类、想强制它们显式声明"我实现了这个接口"时，用 ABC；你想给一批你不拥有、或不想让它们改继承关系的类事后套一个接口时，用 Protocol。** ABC 提供"一处集中声明契约 + 实例化即校验"的强约束，还能塞进共享的默认实现（抽象基类里可以有普通具体方法）；Protocol 提供"零侵入、按形状匹配"的松约束，特别适合给第三方类型或内置类型描述接口。

两者并非对立，标准库里它们其实交织在一起：`collections.abc` 里的 `Iterable`、`Sized` 等既是 ABC，又通过 `__subclasshook__` 支持"只要实现了 `__iter__` 就自动算 `Iterable` 的子类"这种结构化判断——这说明 ABC 也能有限度地做结构匹配。理解要点是：Protocol 把这种"结构匹配"提升成了可被静态类型检查器理解的一等机制，而普通 ABC 的结构匹配只发生在运行时。

一个常见误区是以为"用了 ABC/Protocol 就等于有了 Java/C# 那样的编译期接口强制"。在 Python 里，ABC 的强制发生在**运行时实例化那一刻**，Protocol 的强制主要发生在**独立的静态类型检查器（mypy 等）**里——解释器本身不会因为你违反 Protocol 而拒绝运行。接口在 Python 中始终是"约定 + 工具辅助"，不是语言层的硬编译门禁。

#### 来源与时效（本小主题末集中列）
- 一手：Python 3.11 官方文档《`abc` — Abstract Base Classes》（docs.python.org/3/library/abc.html，核实 2026-07-26）；PEP 3119《Introducing Abstract Base Classes》（引入 `ABCMeta`/`abstractmethod`/`__subclasshook__`）。
- 一手：PEP 544《Protocols: Structural subtyping (static duck typing)》（peps.python.org/pep-0544，核实 2026-07-26）——定义 `typing.Protocol`、结构化子类型、`@runtime_checkable` 及"运行时只查方法存在不查签名"的限制。
- 本机实证：Python 3.11.15 @2026-07-26，`Repository()` 报 `TypeError: Can't instantiate abstract class Repository with abstract method get`；`isinstance([1,2], Sized)=True`、`isinstance(5, Sized)=False`（均为真实输出）。
- 交叉旁证（非承重）：Justin A. Ellis《Abstract Base Classes and Protocols》(2022)，用于 ABC vs Protocol 选择准则的二手佐证，结论以 PEP 544/官方文档为准。

## 5.2 里氏替换原则 LSP — 子类型必须能替换父类型

### 5.2.1 LSP 的原始表述与出处

里氏替换原则（Liskov Substitution Principle, LSP）来自 Barbara Liskov 1987 年在 OOPSLA 的主题演讲《Data Abstraction and Hierarchy》（刊于 SIGPLAN Notices 23(5), 1988）。它的直觉是：**凡是能用父类型对象的地方，都应该能换成任意一个子类型对象，而程序的行为不出问题。** Liskov 在该演讲中给出的原始（较非形式）表述是：

若对每个 S 类型的对象 o₁ 都存在一个 T 类型对象 o₂，使得对所有以 T 定义的程序 P，把 o₂ 换成 o₁ 后 P 的行为不变，则 S 是 T 的子类型。

七年后，Liskov 与 Jeannette Wing 在《A Behavioral Notion of Subtyping》(TOPLAS, 1994) 里把它精炼成如今被反复引用的"子类型要求（subtype requirement）"：

设 φ 是关于 T 类型对象可证明的性质，则 φ 对 S 类型对象也应成立，其中 S 是 T 的子类型。

一句大白话概括这两版：**子类型不能打破父类型对使用者做出的承诺。** 这里要给初学者点明一个常见误解——LSP 说的"子类型"是**行为上的子类型**，不等于语言里的"子类（继承）"。你可以用 `extends`/继承语法造出一个类，但如果它的行为违反了父类的承诺，它在 LSP 意义上根本不是合格的子类型。继承是语法关系，子类型是行为契约。

### 5.2.2 一个违反 LSP 的经典反例

最经典的反例是"正方形是不是长方形"。数学上正方形是特殊的长方形，于是新手很自然写出 `Square(Rectangle)`。但长方形对使用者的隐含承诺是"宽和高可以独立设置"，而正方形为了维持"四边相等"必须在设宽时偷偷改高——这就毁约了：

```python
class Rectangle:
    def __init__(self, w, h):
        self._w, self._h = w, h
    def set_width(self, w):  self._w = w
    def set_height(self, h): self._h = h
    def area(self):          return self._w * self._h

class Square(Rectangle):          # 语法上是子类
    def set_width(self, w):  self._w = self._h = w   # 被迫连带改高
    def set_height(self, h): self._w = self._h = h

def stretch_and_check(r: Rectangle):
    r.set_width(5)
    r.set_height(4)
    assert r.area() == 20         # 对 Rectangle 恒成立的承诺

stretch_and_check(Rectangle(1, 1))  # 通过：area == 20
stretch_and_check(Square(1, 1))     # 失败：area 变成 16
```

`stretch_and_check` 是"以 T（Rectangle）定义的程序 P"，它依赖"分别设宽高后面积 = 宽×高"这条对长方形永真的性质。把对象换成 `Square` 后这条性质被打破——所以 `Square` 不是 `Rectangle` 的合法行为子类型，尽管它在语法上继承自 `Rectangle`。这个例子的教学价值在于：**is-a 关系（正方形"是"长方形）在自然语言里成立，不代表在"可替换"意义上成立。** LSP 逼你从使用者的角度问："我能不假思索地拿子类替换父类吗？"

### 5.2.3 LSP 的行为规则：前置/后置条件与不变式

LSP 不只是一句口号，Liskov-Wing 1994 给了可操作的行为规则，教辅层面记住三条即可。子类覆盖方法时，第一条是前置条件（precondition，方法对输入的要求）不能加强——子类要求的输入不能比父类更苛刻；第二条是后置条件（postcondition，方法对输出的保证）不能削弱——子类保证的输出不能比父类更弱；第三条是父类的对象不变式（invariant）必须继续保持，且历史约束（history constraint）要求子类不能引入父类没有的"允许状态被外部意外改变"的行为。

这三条的直觉解释是，前置条件是"我对调用者的要求"，后置是"我对调用者的保证"。如果子类要求更多（加强前置），原本合法的调用现在被拒，替换就穿帮了；如果子类保证更少（削弱后置），依赖父类保证的下游代码就会拿到不满足预期的结果。历史约束是 Liskov-Wing 相对更早的 Meyer/America 版本新增的关键——它考虑了**别名与可变性**：一个"可变的点"不能算"不可变的点"的子类型，因为不可变点承诺"创建后坐标永不变"，而可变点破坏了这条历史性承诺。初学者只要记："子类可以要得更松、给得更多，绝不能要得更严、给得更少，也不能偷偷破坏父类承诺的稳定性。"

违反 LSP 的现实信号很好识别：如果你在函数里写 `if isinstance(obj, SubType): ...特殊处理...`，往往就是某个子类型无法被无差别替换、你不得不为它开后门——这通常是 LSP 被破坏的味道。

#### 来源与时效（本小主题末集中列）
- 一手：Barbara Liskov《Data Abstraction and Hierarchy》(OOPSLA '87 keynote, SIGPLAN Notices 23(5), May 1988)——LSP 原始（非形式）表述（原文 PDF：cs.tufts.edu 存档，核实 2026-07-26）。
- 一手：B. Liskov & J. Wing《A Behavioral Notion of Subtyping》(ACM TOPLAS 16(6), Nov 1994；原文 PDF：cs.cmu.edu/~wing，核实 2026-07-26)——"subtype requirement"精炼表述、前置/后置/不变式/历史约束规则。
- 交叉核对：Wikipedia《Liskov substitution principle》（核实 2026-07-26）与上述两篇一手原文一致；正方形/长方形反例为社区通行经典例（Martin 亦引用），此处以性质断言 `area==宽×高` 显式坐实，不依赖二手措辞。
- 冲突标注：LSP 的"行为子类型"定义（Liskov-Wing，含历史约束）与更早的 Meyer/America 版本存在分歧——后者下"可变点是不可变点的子类型"成立，Liskov-Wing 的历史约束**明确禁止**这一点；本报告采用 Liskov-Wing 版本并标明分歧点。

## 5.3 组合优于继承 — 首选委托而非继承复用

### 5.3.1 原则出处与含义

"组合优于继承（favor composition over inheritance）"作为一条显式设计准则，最有影响力的出处是 GoF《Design Patterns》(1994)，原文写作"Favor object composition over class inheritance"。它说的是：当你想复用已有功能时，**优先把已有对象作为成员"持有"并转发调用（组合/委托），而不是通过继承把功能"继承"进来。**

对照两种复用方式：**继承**是 "is-a"（子类"是一种"父类），在**编译期/定义期**就把父类实现焊进子类，关系是静态的、白盒的（子类能看到父类内部）；**组合**是 "has-a"（对象"拥有"另一个对象），在**运行期**通过持有引用来复用，关系是动态的、黑盒的（只通过对方公开接口交互）。初学者的抓手：继承像"生下来就带着爸妈的全部家当"，改不掉也甩不脱；组合像"雇了个助手干活"，随时能换人、能换成另一个符合要求的助手。GoF 之所以给出这条偏好，正是因为组合的黑盒复用带来更低的耦合与更高的运行期灵活性。

### 5.3.2 脆弱基类问题——为什么继承会出事

继承被慎用的核心原因是**脆弱基类问题（fragile base class problem）**：**基类作者在不知情的情况下修改了内部实现（改动某个内部方法、或改动方法之间的调用顺序），就可能悄悄弄坏那些依赖了这些实现细节的子类。** 因为继承是白盒的——子类常常依赖父类"内部怎么实现"而不仅是"对外承诺什么"，父类一次看似无害的内部重构就能让远处的子类行为错乱。

一个教科书级的例子是"计数集合"：想统计一共加进去过多少元素，于是继承内置集合并覆盖 `add` 与 `add_all` 各自 +1。问题在于父类 `add_all` 的**内部实现**可能是循环调用 `add`——于是每个元素被 `add_all` 计一次、又被它内部调用的 `add` 计一次，重复计数：

```python
class InstrumentedSet(set):
    def __init__(self):
        super().__init__()
        self.added = 0
    def add(self, x):
        self.added += 1
        super().add(x)
    def update(self, items):       # 对应 add_all
        self.added += len(list(items))
        super().update(items)      # 若 update 内部又逐个调 add，就会重复计数
```

子类的正确性**取决于父类 `update` 内部到底调不调 `add`**——而这是父类的实现细节，父类作者有权在任何版本改动它，改动后子类就崩，且崩得毫无征兆。这正是脆弱基类的要害：**子类与父类之间存在超出公开契约的隐式耦合。** 用组合改写就没有这个问题——你持有一个真正的 `set` 成员，只通过它的公开方法交互，父类内部怎么实现与你无关：

```python
class CountingSet:
    def __init__(self):
        self._data = set()      # 组合：持有而非继承
        self.added = 0
    def add(self, x):
        self.added += 1
        self._data.add(x)
    def update(self, items):
        items = list(items)
        self.added += len(items)
        self._data.update(items)   # 只用公开接口，无隐式重复计数
```

### 5.3.3 委托与"优于"不等于"禁止继承"

组合复用的具体手法叫**委托（delegation）**：对象把收到的请求"转交"给它持有的成员去完成。上面 `CountingSet.add` 把真正的存储工作委托给 `self._data`，自己只在外面加一层计数逻辑。委托的代价是要写一些"转发样板代码"（每个要暴露的方法都得手写一遍转发），好处是耦合松、可在运行期替换被委托对象、且不受脆弱基类之害。

要澄清一个初学者最容易误读的点：这条原则叫"组合**优于**继承"，是**默认偏好**，不是"禁止继承"。继承在"确实是稳定的 is-a 关系、且你需要多态替换 + 共享默认实现"时仍然合适——这也正是 5.1 抽象基类的典型用法（ABC 提供抽象契约 + 可选默认实现，子类继承并实现）。判断口诀：**问自己"我到底想要子类型多态，还是只想复用一段实现？"** 若只是想复用实现，几乎总该用组合；只有当你确实需要"子类型能替换父类型"（回到 5.2 的 LSP）时，继承才是对的工具。

这条原则也直接通向 5.4 的 SOLID：优先组合 + 面向接口（ABC/Protocol）委托，正是实现依赖倒置（DIP）与开闭原则（OCP）的常见落地手段——把易变的实现藏在接口后、以组合注入，而非以继承焊死。

#### 来源与时效（本小主题末集中列）
- 一手：GoF《Design Patterns: Elements of Reusable Object-Oriented Software》(Gamma/Helm/Johnson/Vlissides, Addison-Wesley, 1994)，Ch.1 "Favor object composition over class inheritance"——原则原始出处。
- 交叉核对：脆弱基类问题的界定（基类内部改动/方法调用顺序改动破坏子类）在多处一手/权威二手一致（Joshua Bloch《Effective Java》Item "Favor composition over inheritance" 给出 InstrumentedHashSet/HashSet 的 addAll 重复计数原型；本报告用 Python `set` 等价复现）。核实 2026-07-26。
- 二手佐证（非承重）：Pratik Pandey《Fragile Base Class Problem: Composition over Inheritance》、O'Reilly 相关章节，用于表述交叉印证，结论以 GoF/Effective Java 为准。

## 5.4 SOLID 原则概览 — 五条面向对象设计准则

### 5.4.1 SOLID 的来历与定位

SOLID 是五条面向对象设计原则首字母缩写的合称。这些原则由 Robert C. Martin（"Uncle Bob"）在 2000 年论文《Design Principles and Design Patterns》中系统提出并推广，"SOLID"这个便于记忆的缩写词则由 Michael Feathers 在约 2004 年提炼。要点先摆明：**其中两条并非 Martin 原创——"O"（开闭）源自 Bertrand Meyer 1988，"L"（里氏替换）源自 Barbara Liskov 1987（见本报告 5.2）**；Martin 的贡献是把它们与自己整理的另外三条并成一套可传授的清单。

按本大主题的边界，这里只做**语言机制层的原则概览**——即每条原则"是什么、直觉、最小落地手段"；它们在架构方法学上的深挖（分层架构、依赖注入、模块与包边界、组件内聚）显式归 L4-06，本报告不展开。初学者可以先把 SOLID 理解为"五个互相配合的经验法则，目标都是让代码在需求变化时**改动小、波及少、易替换、易测试**"，而不是硬性规则或银弹。

### 5.4.2 S — 单一职责原则 SRP

单一职责原则（Single Responsibility Principle）最早的表述是"一个类应当只有一个变化的理由（a class should have only one reason to change）"。由于"理由"一词易生歧义，Martin 后来在《Clean Architecture》(2017) 里改述为更精确的版本：

一个模块应当只对一个、且仅一个"行为者（actor）"负责。

这里的"actor"指"会要求这个模块发生改动的那一群人/角色"（如财务、运维、市场）。直觉是：如果一段代码同时服务于两拨会因不同原因提出变更的人，那么其中一拨的需求变更就可能连带弄坏另一拨依赖的功能。最小落地：把"计算工资"（财务关心）、"格式化报表"（美工/市场关心）、"存数据库"（DBA 关心）拆到不同类里，而不是塞进一个 `Employee` 万能类。易错点——SRP 不是"一个类只能有一个方法"，而是"一个类只应有一个变更来源"。

### 5.4.3 O — 开闭原则 OCP

开闭原则（Open-Closed Principle）由 Bertrand Meyer 1988 提出，一句话是：

软件实体（类/模块/函数）应当对扩展开放、对修改关闭。

这句话的意思是，加新功能时，理想情况是**新增代码**（写一个新的实现类/子类型），而**不必回去改动已经写好、已经测过、已经上线的老代码**。直觉抓手：好的设计像插座——要加新电器插上就行（扩展开放），不用把墙拆开重接电线（修改关闭）。最小落地：把易变的部分抽象成接口（ABC/Protocol，见 5.1），调用方只依赖接口；新增行为时实现一个新类注入进去即可。一个常见误解是"永远不能改任何代码"——实际含义是"针对**可预见的变化维度**做好抽象，使这类变化通过新增而非改旧来吸收"，不是要求对一切变化都封闭。

### 5.4.4 L — 里氏替换原则 LSP

里氏替换原则（Liskov Substitution Principle）即本报告 5.2 详述的原则：子类型对象必须能无差别替换父类型对象而不破坏程序正确性，其源头是 Liskov 1987 演讲、经 Liskov-Wing 1994 精炼。在 SOLID 语境下，它是"继承/子类型化必须守的行为契约"，为 OCP 提供正确性保障——只有子类型真能替换父类型，"面向接口扩展"才不会在替换时翻车。此处不重复展开，规则与反例见 5.2.2、5.2.3。

### 5.4.5 I — 接口隔离原则 ISP

接口隔离原则（Interface Segregation Principle）由 Robert C. Martin 提出（源自其为 Xerox 咨询的经历，收入《Agile Software Development》2002），表述为：

不应强迫客户端依赖它并不使用的接口。

它针对的是"胖接口（fat interface）"——一个塞了太多不相关方法的大接口，会让只想用其中一小部分的客户端被迫依赖（甚至被迫实现）自己用不到的方法。直觉：与其给所有人一把有 30 个键、多数人只按 2 个的巨型遥控器，不如按人群拆成几个小遥控器。最小落地：把大接口按"客户端群体"拆成多个小而聚焦的接口（在 Python 里就是拆成多个小 Protocol/ABC）。它与 5.4.2 SRP 精神相通——SRP 讲类的职责单一，ISP 讲接口的职责单一。

### 5.4.6 D — 依赖倒置原则 DIP

依赖倒置原则（Dependency Inversion Principle）由 Robert C. Martin 提出，表述为：

高层模块不应依赖低层模块，二者都应依赖抽象；抽象不应依赖细节，细节应依赖抽象。

"倒置"指的是依赖方向的翻转：传统写法里高层业务逻辑直接 `import`/`new` 一个具体的低层实现（如某个具体数据库类），于是高层被钉死在低层细节上；DIP 要求在两者之间插入一个抽象接口，让**高层和低层都指向这个接口**，从而高层不再认得任何具体实现。直觉：老板（高层）只对"司机"这个角色（抽象）下指令，不关心今天来的是张三还是李四（具体实现），换人不影响老板的工作。最小落地：高层依赖 ABC/Protocol，具体实现类由外部"注入"进来（构造函数传参即可），这正是 5.3 "组合 + 面向接口"的直接产物。要区分一个易混点：DIP 是"依赖抽象"的**原则**，依赖注入（DI）只是实现 DIP 的一种**技术手段**，二者不是同一层次的概念（DI 容器等工程化内容归 L4-06）。

### 5.4.7 把五条串起来看

五条并非孤立：SRP/ISP 从"职责/接口要单一聚焦"两个角度约束**粒度**；OCP 给出**目标**（改动靠新增而非改旧）；LSP 保证**子类型替换的正确性**，是 OCP 能成立的前提；DIP 提供**落地方向**（依赖抽象、把实现推到边缘）。它们共同的底层手段就是 5.1 的接口抽象（ABC/Protocol）与 5.3 的组合/委托——先用接口把"稳定的契约"与"易变的实现"分开，再用组合把实现注入进来。初学者不必背诵条文，抓住这条主线即可：**用接口切开稳定与易变，用组合装配，用 LSP 守住替换的正确性，目标是让变化只在局部发生。**

需要再次强调时效/边界：SOLID 是经验原则而非定理，学界与业界对个别原则（尤其 SRP 措辞、OCP 是否被过度推崇）持续有讨论；本报告只做机制层概览，其在真实架构中的权衡、反模式与批评归 L4-06 展开。

#### 来源与时效（本小主题末集中列）
- 一手：Robert C. Martin《Design Principles and Design Patterns》(2000) 与《Clean Architecture》(Prentice Hall, 2017)——SOLID 系统提出、SRP 的"actor"改述、ISP/DIP 表述（ISP/DIP 原表述亦见 Martin《Agile Software Development: Principles, Patterns, and Practices》2002）。核实 2026-07-26。
- 一手：Bertrand Meyer《Object-Oriented Software Construction》(1988)——开闭原则原始定义（"open for extension, closed for modification"）。
- 一手：Barbara Liskov《Data Abstraction and Hierarchy》(1988)——LSP 源头（详见 5.2 来源）。
- 交叉核对：Wikipedia《SOLID》《Open–closed principle》《Single-responsibility principle》（均核实 2026-07-26）与上述一手一致——确认"SOLID 缩写由 Michael Feathers 约 2004 提炼"、"O 源自 Meyer 1988"、SRP 的"reason to change"→"one actor"演化。
- 归属澄清（避免编造）：五原则中 O 归 Meyer(1988)、L 归 Liskov(1987)，非 Martin 原创；Martin 的贡献是整合成套并提出 S/I/D 的现代表述，缩写词归 Feathers。此归属为多源一致结论。
