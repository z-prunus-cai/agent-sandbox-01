# L3-04·大主题4 动态派发机制

> 基线 Py3.11.15/np2.4.6/gcc13.3 @2026-07-25 ｜ 核实日期：2026-07-26 ｜ 先修：L3-04 大主题3（OO 三支柱：封装/继承/多态、类/实例/方法查找、子类型多态与方法覆盖）；L1-01（函数、指针基础） ｜ 一手锚点：Python 3.11 官方文档（The Python Language Reference §3 Data Model §3.3.2 属性访问、`type.mro()`/`__mro__`、内置 `super`；HOWTO：Descriptor Guide、"The Python 2.3 Method Resolution Order"）；ISO/IEC 14882 C++ 标准 [class.virtual]（虚函数/动态派发语义）；Itanium C++ ABI（vtable 布局，gcc/clang on Linux 实现基准）；Barrett et al.《A Monotonic Superclass Linearization for Dylan》(1996, C3 线性化原始描述） ｜ 成熟度：GA/稳定（派发语义为经典机制，无版本漂移；C++ vtable 属实现约定，Python MRO 自 2.3 起为 C3）

本报告的 C++ vtable 布局与虚调用反汇编、Python MRO/菱形/鸭子类型的输出，均为基线环境（gcc/g++ 13.3.0、binutils/objdump 2.42、Python 3.11.15、Linux 6.18.5 x86_64）下的真实运行结果，命令与输出随节贴出。

---

## 4.1 静态派发 vs 动态派发 — 编译期绑定 vs 运行期绑定对比

### 4.1.1 派发是什么、两种绑定时机

"派发（dispatch）"指的是：当程序执行到一个方法/函数调用点时，**决定到底执行哪一份具体代码**这件事。按"这个决定在什么时候拍板"，分成两类：

静态派发（static dispatch / early binding）：在**编译期**就根据调用处能看到的**静态类型**把调用点绑定到确定的一份实现。

动态派发（dynamic dispatch / late binding）：把决定推迟到**运行期**，根据被调用对象的**实际（动态）类型**再选哪份实现。

静态派发是"编译时就写死了跳去哪里"，动态派发是"运行到那一刻，先看手里这个对象真正是什么，再决定跳去哪里"。前者快、无运行时开销但不能随对象真实类型变化；后者是多态（大主题3 的子类型多态）能在运行时"按对象选行为"的底层支撑。

### 4.1.2 为什么需要两种、各自的代价

动态派发是"子类型多态"落地的机制：父类引用/指针指向子类对象时，调用同一个方法名却执行子类覆盖后的版本——这必须等到运行期知道对象真实类型才能做到。代价是每次调用要多一步"查表/查属性"的间接，且编译器通常无法内联优化。

静态派发没有这一步间接，调用地址在编译期确定，可内联、可做常量传播，因此更快；但它只认调用处的静态类型，无法表达"同一调用点因对象不同而行为不同"。

把调用点想成一封信。静态派发是"寄件时收件地址已印死在信封上"；动态派发是"信送到分拣中心，才根据包裹里实际装的东西决定最终转投哪里"。多态的灵活来自这一步"运行时再看"，速度的代价也来自它。

### 4.1.3 各语言的默认取向（规范 vs 实现分账）

不同语言在"默认静态还是默认动态"上取向不同，这是初学者最容易混的点：

C++：成员函数**默认静态派发**；只有显式标 `virtual` 的函数才走动态派发（ISO C++ [class.virtual]）。此外重载决议（overload resolution）、模板实例化都是编译期的静态机制。

Java：实例方法**默认动态派发**（虚），除 `static`/`private`/`final` 及构造器外都按对象运行时类型分派。

Python：**几乎一切方法调用都是动态派发**——`obj.method()` 每次都在运行时经属性查找沿 MRO 找方法（见 4.3、4.4），语言层面没有"编译期绑定的方法"这一档。

所以"动态派发"在 C++ 里是要主动开启的特性，在 Python 里是无法关闭的默认。谈"静态 vs 动态"务必带上语言语境，不能一概而论。

C 语言没有内建对象派发；用函数指针表可手工模拟动态派发（这也正是 C++ vtable 的手工版直觉，见 4.2）。

#### 来源与时效（本小主题末集中列）
- ISO/IEC 14882 C++《Programming languages — C++》[class.virtual]：虚函数按对象动态类型确定被调用的最终覆盖者（final overrider）；非虚成员按静态类型静态绑定，核实 2026-07-26。
- Python 3.11 官方文档 Data Model §3.3.2（`object.__getattribute__` 决定方法/属性的运行时查找）——佐证 Python 方法调用皆运行期解析，https://docs.python.org/3.11/reference/datamodel.html ，核实 2026-07-26。
- 术语「early/late binding」与「static/dynamic dispatch」在文献中常互换使用；本报告以"绑定决定发生在编译期还是运行期"为统一判据，与各语言规范一致。

## 4.2 虚表（C++ vtable）机制 — 虚函数指针表概念

### 4.2.1 vtable 是什么、解决什么

虚表（virtual table, vtable）是 C++ 编译器为**含虚函数的类**生成的一张**函数指针表**：表里每个槽位对应一个虚函数，存的是"该类版本的那份实现的地址"。每个这样的对象里藏一个隐藏指针 **vptr**，指向它所属类的 vtable。运行 `p->f()`（`f` 是虚函数）时，编译器生成的代码是"先经对象里的 vptr 找到 vtable，再取出 `f` 对应槽位里的地址，间接跳过去"——这正是 4.1 动态派发在 C++ 里的落地手段。

**C++ 标准只规定虚函数的动态派发语义（[class.virtual]：调用最终覆盖者），并不强制用 vtable**。vtable/vptr 是编译器的实现策略；其**具体布局**由平台 ABI 规定——Linux 上 gcc/clang 遵循 **Itanium C++ ABI**。因此下文的布局是"gcc 13.3 + Itanium ABI"的实现观察，不是语言标准的要求（这一条也回应了 本库编排清单「4.2 vtable 一手规范章节待核」：语义在 [class.virtual]，布局在 Itanium ABI，非 ISO 标准正文）。

### 4.2.2 本机观察 vtable 布局（旁证）

用 g++ 的 `-fdump-lang-class` 直接打印编译器生成的 vtable。源码与真实输出：

```cpp
struct Base {
    virtual void speak();
    virtual int  area();
    int x;
};
struct Derived : Base {
    void speak() override;   // 覆盖 slot 0
    int  extra();            // 非虚：不进 vtable
    int  y;
};
void Base::speak() {}
int  Base::area() { return 0; }
void Derived::speak() {}
int  Derived::extra() { return 1; }
Base b; Derived d;
```

```text
$ g++ -std=c++17 -fdump-lang-class -c vt.cpp
Vtable for Base
Base::_ZTV4Base: 4 entries
0     (int (*)(...))0
8     (int (*)(...))(& _ZTI4Base)
16    (int (*)(...))Base::speak
24    (int (*)(...))Base::area
Class Base
   size=16 align=8
    vptr=((& Base::_ZTV4Base) + 16)

Vtable for Derived
Derived::_ZTV7Derived: 4 entries
0     (int (*)(...))0
8     (int (*)(...))(& _ZTI7Derived)
16    (int (*)(...))Derived::speak
24    (int (*)(...))Base::area
```

Base 的 vtable 前两槽是 offset-to-top（0）和 RTTI 指针（`_ZTI4Base` 即 typeinfo），再往后才是函数槽 `speak`、`area`；对象里的 vptr 指向 vtable 的**函数区起点**（`+16`）。Derived 的 vtable 里，`speak` 槽换成了 `Derived::speak`（因为它覆盖了），`area` 槽仍是 `Base::area`（未覆盖，继承下来）。非虚的 `extra()` **不在表里**——只有虚函数才占槽位。这直观说明"覆盖"在实现层就是"把子类 vtable 对应槽位改写成子类的实现地址"。

### 4.2.3 虚调用在机器层是一次间接跳转

把一个虚调用单独编译并反汇编，看它到底做了什么：

```cpp
int call_it(Base* p) { return p->area(); }   // 虚调用
```

```text
$ g++ -std=c++17 -O1 -c call.cpp && objdump -dC call.o
<call_it(Base*)>:
   endbr64
   sub    $0x8,%rsp
   mov    (%rdi),%rax     ; rax = *p = vptr（对象首部就是 vptr）
   call   *0x8(%rax)      ; 间接调用 vtable 第 2 槽（area 是第 2 个虚函数，偏移 0x8）
   add    $0x8,%rsp
   ret
```

`mov (%rdi),%rax` 从对象取出 vptr，`call *0x8(%rax)` 是一次**通过 vtable 槽位的间接调用**——注意偏移是 `0x8` 而非 `0`：`speak` 是第 1 个虚函数占 vtable 起点（偏移 0），`area` 是第 2 个占偏移 8，所以调 `area` 走的是 `*0x8(%rax)`。这与直接 `call 某固定地址`（静态派发）形成鲜明对照，这一步间接就是动态派发的运行时开销来源。（`endbr64` 是 CET 间接分支落点，`sub/add $0x8,%rsp` 是 ABI 的 16 字节栈对齐。）

vtable 就是"每个多态类一张、类内所有虚函数一行"的电话簿，vptr 是对象随身携带的"我该查哪本电话簿"的便条。机器层更细的部分（多继承下的 thunk、`offset-to-top`、RTTI 用途）归 L3-03，本节到"一次间接跳转"为止。

### 4.2.4 易错点与关联

"每个对象都存着整张函数表"——错，对象里只存一个 vptr（一个指针），表本身每类一份、被同类所有对象共享。误解二："非虚函数也走 vtable"——非虚成员是静态派发，编译期绑定，不进表（上面 `extra()` 就是证据）。误解三："构造函数里调虚函数会派发到子类"——不会，对象构造期间 vptr 指向当前正在构造的这一层，是 C++ 的经典陷阱。

与大主题3 的关联：3.4 讲的"子类型多态、方法覆盖"在 C++ 里正是靠 vtable 实现；Python 没有 vtable，走的是运行时属性查找（4.3、4.4），机制完全不同但达成的"按对象真实类型选行为"效果一致。

#### 来源与时效（本小主题末集中列）
- ISO/IEC 14882 C++ 标准 [class.virtual]：规定虚函数动态派发语义（调用 final overrider），**不规定 vtable**——vtable 属实现，核实 2026-07-26。
- Itanium C++ ABI（https://itanium-cxx-abi.github.io/cxx-abi/abi.html ，§2.5 Virtual Table Layout）：Linux/gcc/clang 的 vtable 布局基准——offset-to-top、RTTI、虚函数槽顺序的一手来源。
- 本机旁证：gcc/g++ 13.3.0 `-fdump-lang-class` 的 vtable 转储 + objdump 2.42 对虚调用的反汇编（`mov (%rdi),%rax; call *0x8(%rax)`，area 为第 2 虚函数、槽偏移 0x8），Linux 6.18.5 x86_64，核实 2026-07-26。
- 说明：布局为 gcc13.3+Itanium ABI 的实现观察，非语言标准要求；机器层深挖（thunk/多继承 this 调整/RTTI）归 L3-03，本报告不下机器层结论。

## 4.3 鸭子类型与运行时属性查找 — 无需继承的结构化多态

### 4.3.1 鸭子类型是什么

鸭子类型（duck typing）是 Python 的多态风格："**如果它走起来像鸭子、叫起来像鸭子，就把它当鸭子**"——一个对象能不能用，不看它属于哪个类、继承自谁，只看它**是否具备被调用时所需的方法/属性**。这是一种**结构化多态**：靠"有没有这个操作"而非"是不是这个类型"来决定可用性，因此**不需要共同基类或显式接口声明**。

对照 4.1/4.2：C++ 的动态派发要求对象在一条继承链上（虚函数属于某个基类接口）；Python 的鸭子类型连继承都不要求，只要对象在运行时"恰好有那个方法名"就能调用成功。

### 4.3.2 最小例子

两个毫无继承关系的类，只要都有 `quack` 方法，就能被同一段代码通用处理。真实运行输出：

```python
class Duck:
    def quack(self): return "quack"
class Person:
    def quack(self): return "I'm quacking"

def make_it_quack(x):
    return x.quack()          # 不检查 x 的类型，只调用 .quack()

print(make_it_quack(Duck()))     # quack
print(make_it_quack(Person()))   # I'm quacking
```

`make_it_quack` 从不问"你是不是 Duck"，只发出 `.quack()` 调用。`Duck` 与 `Person` 没有任何继承关系，却都能用——这就是"结构对了就行"。

### 4.3.3 底层的属性查找（attribute lookup）

鸭子类型能成立，是因为 Python 里 `obj.attr` 是一次**运行时属性查找**，由 `object.__getattribute__` 默认实现，大致顺序（简化）：

1. 沿 `type(obj)` 的 MRO（见 4.4）查找 `attr`；若找到的是**数据描述符**（同时定义 `__get__` 和 `__set__`/`__delete__`，如 `property`），优先用它。
2. 否则查 `obj.__dict__`（实例自身的属性字典）。
3. 否则用第 1 步在类的 MRO 中找到的**非数据描述符**（普通函数即属此类，方法查找走这里）或普通类属性。
4. 都没有则调用 `__getattr__`（若定义了）兜底，否则抛 `AttributeError`。

方法之所以能被子类覆盖、能被鸭子类型通用调用，正因为它在第 1/3 步沿"对象实际类型的 MRO"动态查出来——查到谁就调谁。初学者可记住简化版："先看实例自己的字典，再沿类的继承顺序找；`property` 这类数据描述符是例外，优先级更高。"

### 4.3.4 易错点与关联

鸭子类型的代价是**错误推迟到运行期**：类型不匹配不会在"编译/定义"时报错，而是等真正调用缺失的方法时才抛 `AttributeError`。务实的加固手段是显式接口——`abc.ABC` 抽象基类或 `typing.Protocol`（结构化子类型，把鸭子类型"写进类型标注"），这些归大主题5，本节只立"无需继承、看结构"的直觉。与 4.4 的关联：属性查找沿 MRO 进行，而 MRO 正是下一节的主题。

#### 来源与时效（本小主题末集中列）
- Python 3.11 官方 Glossary「duck-typing」定义 + Data Model §3.3.2（`object.__getattribute__` 属性查找），https://docs.python.org/3.11/reference/datamodel.html ，核实 2026-07-26。
- Python 3.11 官方 HOWTO「Descriptor Guide」：数据描述符 vs 非数据描述符的查找优先级、方法即非数据描述符，https://docs.python.org/3.11/howto/descriptor.html ，核实 2026-07-26。
- 本机旁证：上述 duck typing 例在 Python 3.11.15 输出 `quack` / `I'm quacking`，核实 2026-07-26。

## 4.4 方法解析顺序 MRO 与 C3 线性化 — `__mro__` 顺序规则

### 4.4.1 MRO 是什么

方法解析顺序（Method Resolution Order, MRO）是一条**把类的所有祖先排成一维线性序列**的顺序表：查找属性/方法时（4.3 第 1 步），Python 就沿这条线**从前往后**找，找到的第一个就用。每个类的 MRO 存在 `Cls.__mro__`（元组）里，也可用 `Cls.mro()` 取得。单继承时它就是"自己→父→祖→…→object"这条直链；多继承时靠 **C3 线性化**算出唯一顺序。

MRO 的意义：它把"多继承下该先问哪个父类"这个含糊问题，变成一条**确定、无歧义**的查找序，从而让方法覆盖与 `super()` 有明确语义（4.5）。

### 4.4.2 C3 线性化算法

Python 2.3 起 MRO 采用 **C3 线性化**（源自 Dylan 语言，Barrett et al. 1996）。设 `L[C]` 为类 C 的线性化，`B1..Bn` 为 C 的直接基类（从左到右），核心递推是：

L[object] = [object]

L[C] = C + merge(L[B1], …, L[Bn], [B1, …, Bn])

merge 的规则：反复取"候选头（第一个列表的头）"，若它**不出现在其余任何列表的尾部**（即不是别人还没轮到的祖先），就选它、并从所有列表中删去；否则换下一个列表的头试。取不出合法头时说明无法线性化，Python 在**建类时**直接抛 `TypeError`。

C3 保证两条性质，这也是它取代旧算法的原因：**局部优先序**（子类排在父类前、直接基类保持从左到右的相对次序）与**单调性**（子类的 MRO 与各父类 MRO 相容，不会出现父类里 A 在 B 前、子类里却颠倒）。

### 4.4.3 本机例子

菱形结构 D(B, C)，B、C 均继承 A，打印其 MRO（真实输出）：

```python
class A: ...
class B(A): ...
class C(A): ...
class D(B, C): ...

for k in D.__mro__:
    print(k.__name__)
```

```text
D
B
C
A
object
```

顺序是 `D → B → C → A → object`：D 在最前，直接基类 B 先于 C（保左到右），共同祖先 A 被推到 B、C **之后**（C3 的关键——祖先不能排在任一后代之前），最后是万类之祖 object。手算 merge：`L[D]=D+merge([B,A,object],[C,A,object],[B,C])`，先取 B（不在他人尾部）→ 再取 C → 此时 A 成为合法头 → 再 object，得 `[D,B,C,A,object]`。

### 4.4.4 无法线性化时报错

当继承关系自相矛盾（要求 A 既在 B 前又在 B 后），C3 找不到合法顺序，**建类那一刻**就报错（真实输出）：

```python
class X: pass
class Y(X): pass
class Bad(X, Y): pass   # 要求 X 在 Y 前，但 Y 的 MRO 要求 X 在 Y 后
```

```text
TypeError: Cannot create a consistent method resolution order (MRO) for bases X, Y
```

MRO 错误是**类定义时**（不是调用时）就暴露的，这是 C3 相比早期深度优先算法的一大优点——矛盾早发现。易错点：直接基类的书写顺序会影响 MRO（`D(B,C)` 与 `D(C,B)` 结果不同），因此多继承时基类顺序不是随便写的。

#### 来源与时效（本小主题末集中列）
- Python 3.11 官方文档「The Python 2.3 Method Resolution Order」（C3 算法、merge 规则、单调性）+ `type.mro()`/`__mro__` 文档，https://docs.python.org/3.11/reference/datamodel.html ，核实 2026-07-26。
- Barrett, Cassey, Haahr, Moon, Playford, Withington, Yourtchenko《A Monotonic Superclass Linearization for Dylan》(OOPSLA 1996)：C3 线性化原始描述与单调性证明——算法一手来源。
- 本机旁证：Python 3.11.15 下 `D.__mro__ == (D,B,C,A,object)`，矛盾继承抛 `TypeError: Cannot create a consistent method resolution order`，核实 2026-07-26。

## 4.5 菱形继承问题 — 多继承二义性与 C3/super() 解决

### 4.5.1 菱形问题是什么

菱形继承（diamond problem）指这种形状：D 同时继承 B 和 C，而 B、C 又同继承 A——继承图画出来像个菱形。它带来两个经典麻烦：

一是**方法二义性**：D 调用一个 B、C 都覆盖过的方法时，该用哪个？二是**共同祖先 A 该被"经过"几次**：如果 B、C 都要调用 A 的初始化，A 会不会被执行两遍？

Python 的答案是：用 4.4 的 **MRO 把菱形拉直成一条线**（`D→B→C→A→object`），二义性消失（顺序确定）；再配合 `super()` 沿这条线**协作式**地走，保证每个类**恰好被访问一次**。

### 4.5.2 super() 不是"调用父类"，而是"沿 MRO 走下一个"

初学者最大的误解是把 `super()` 理解为"调用我的父类"。准确说法是：`super()` 返回一个代理，它沿**当前对象的 MRO**，从**当前类的下一个**类开始查找方法。所以在菱形里，`B` 里的 `super().m()` 在 `D()` 的调用语境下，下一个不是 A 而是 **C**（因为 D 的 MRO 是 D→B→C→A）。这套"每个类都调 `super()` 把接力棒传给 MRO 里的下一位"的写法叫**协作式多继承（cooperative multiple inheritance）**。

### 4.5.3 本机例子：菱形被走成一条链，A 只经过一次

每个类都 `super().who()`，观察调用链（真实输出）：

```python
class A:
    def who(self): return "A"
class B(A):
    def who(self): return "B->" + super().who()
class C(A):
    def who(self): return "C->" + super().who()
class D(B, C):
    def who(self): return "D->" + super().who()

print(D().who())
```

```text
D->B->C->A
```

链是 `D→B→C→A`：D 的 `super()` 走到 B，B 的 `super()`**不是**回到它自己的父类 A，而是走到 MRO 里的下一个 C，C 的 `super()` 才走到 A，A 收尾。**A 只被执行一次**——菱形顶端没有被重复访问。这正是协作式 super + C3 MRO 联手解决菱形的效果：若用"直接写 `A.who(self)`"的老写法，B、C 会各自调一次 A，A 就被执行两遍。

### 4.5.4 易错点与语言对照

用 `super()` 协作有个前提：链条上每个类的方法签名要兼容（尤其 `__init__` 的参数传递），否则接力会断——这是多继承 `__init__` 的常见坑，务实做法是配合 `**kwargs` 透传，或干脆优先用组合（大主题5.3）。

C++ 靠**虚继承（virtual inheritance）** 让菱形顶端只保留一份子对象来解决重复基类，但需程序员显式声明，且不提供 Python 式的 MRO 协作链；Java 干脆**不允许类多继承**（只多继承接口，接口默认方法冲突需显式覆盖解决）从源头回避菱形。Python 选择"允许多继承 + 用 C3/super 给出确定语义"这条路。

4.1 的"运行期按对象选行为"、4.3 的"属性查找"、4.4 的"MRO 顺序"，到这里合成一句话——Python 的动态派发 = 沿对象真实类型的 C3 MRO 做运行时属性查找，`super()` 让多继承下的这条查找链变得协作而无重复。

#### 来源与时效（本小主题末集中列）
- Python 3.11 官方文档：内置 `super`（沿 MRO 代理到下一个类）+「The Python 2.3 Method Resolution Order」（C3 解决菱形），https://docs.python.org/3.11/library/functions.html#super ，核实 2026-07-26。
- ISO/IEC 14882 C++ 标准 [class.mi]/[class.derived]（虚继承解决重复基类）——作多继承取舍的语言对照旁证；Java 语言规范（类单继承、接口默认方法冲突显式解决）作第二对照。
- 本机旁证：Python 3.11.15 下 `D().who()` 输出 `D->B->C->A`（A 仅一次），核实 2026-07-26。
