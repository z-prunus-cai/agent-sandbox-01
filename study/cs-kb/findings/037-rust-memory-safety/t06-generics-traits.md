# L5-14·大主题R6 泛型、trait 与类型推导

> 基线 Py3.11.15/np2.4.6/gcc13.3 @2026-07-25（+ rustc/cargo 1.94.1）｜核实日期：2026-08-01｜先修：R2 所有权与 move 语义、R4 生命周期与区域推断、R5 代数数据类型与模式匹配｜一手锚点：The Rust Programming Language book Ch.10「Generic Types, Traits, and Lifetimes」@对齐 rustc 1.90.0 + edition 2024、The Rust Reference §「Traits」「Type inference」「Trait objects」@活文档、本机 rustc 1.94.1 实测｜成熟度：GA（泛型/trait/单态化/局部推导自 Rust 1.0 稳定）；术语「object safety→dyn compatibility」为近期改名 ⚙演进快·锚 rustc 1.94.1·随时变

泛型、trait 与类型推导是 Rust 复用代码而不牺牲类型安全与运行期性能的三根支柱。泛型让你把「对任意类型都适用」的逻辑写一次；trait 定义「一组类型共享哪些行为」，是 Rust 的接口机制；类型推导让你在大多数地方不必手写类型标注。这三者互相咬合：泛型靠 trait bound 表达「这个类型参数必须能干什么」，编译器再靠单态化把泛型代码具体化、靠局部推导把省略的类型补齐。

本章面向初学者把泛型与单态化、trait 定义与实现（含孤儿规则）、trait bound 与 `where`、trait 对象与动态分发、局部类型推导五块讲到能看懂、能改错为止，不做类型理论纵深（约束式多态/受限多态/类型类的理论根在编程语言理论一脉→L5-06 T7–T9，此处只作一句交叉引用）。

本报告所有 rustc 报错、编译结果与运行输出均为本机 rustc 1.94.1（`--edition 2024`）现跑，复现命令随例给出；报错码以本机输出为准，不凭记忆。

## R6.1 泛型与单态化

### 泛型：把「对任意类型都成立」的逻辑写一次

泛型（generics）是用一个占位类型参数（惯例写 `T`）代替具体类型，让同一段代码适用于多种类型。函数、结构体、枚举、方法都能带泛型参数，参数写在名字后的尖括号里，如 `fn largest<T>(list: &[T]) -> &T`、`struct Point<T> { x: T, y: T }`、`enum Option<T> { Some(T), None }`。

它解决的是「同样的逻辑，只因类型不同就得复制粘贴一遍」的重复。没有泛型，你得为 `i32` 写一个 `largest_i32`、为 `char` 写一个 `largest_char`，函数体一模一样。泛型把类型抽出来当参数，一份代码覆盖所有类型。

下面是一个泛型结构体和泛型函数的最小例子。`Point<T>` 对任意 `T` 都可用；`id<T>` 原样返回传入值，不关心类型：

```rust
struct Point<T> {
    x: T,
    y: T,
}
fn id<T>(x: T) -> T {
    x
}
fn main() {
    let pi = Point { x: 1, y: 2 };       // T = i32
    let pf = Point { x: 1.0, y: 2.0 };   // T = f64
    println!("{} {}", id(pi.x), id(pf.y));
}
```

`rustc --edition 2024 mono.rs && ./mono` 本机现跑输出 `1 2`（`id(pi.x)` 是 i32 的 `1`，`id(pf.y)` 是 f64 的 `2.0`——f64 的 `2.0` 用 `{}` 打印为 `2`）。注意 `Point { x: 1, y: 2 }` 里两个字段都是 `T`，所以必须同类型；想让 `x`、`y` 类型不同得写成两个参数 `Point<T, U>`。这是初学者常踩的坑：`Point { x: 1, y: 2.0 }` 会因 `T` 无法同时是 `i32` 和 `f64` 而报类型不匹配。

### 单态化：编译期为每个用到的具体类型生成一份专门代码

单态化（monomorphization）是 Rust 处理泛型的核心机制：编译时，编译器扫描所有泛型代码被实际调用时填入的具体类型，为每一种具体类型生成一份「把 `T` 换成该类型」的专门代码。也就是说，运行期根本不存在「泛型」——存在的只是一堆被具体化过的普通函数。

这解释了为什么 Rust 的泛型「零运行期开销」：泛型调用不需要在运行期查表、装箱或做任何间接跳转，因为编译器已经把每个具体类型的版本直接编译成机器码，等价于你手写了多个专门函数。这与后面 R6.4 的动态分发形成对比——那才有运行期间接开销。

可以直接看编译产物证实这一点。下面的 `id` 只写了一份，但被 `i32` 和 `f64` 各调用一次：

```rust
fn id<T>(x: T) -> T { x }
fn main() {
    let a = id(5i32);
    let b = id(2.5f64);
    println!("{a} {b}");
}
```

用 `rustc --edition 2024 --emit=llvm-ir -o mono.ll mono.rs` 生成 LLVM IR 后，`grep 'define .*2id' mono.ll` 本机现跑得到两条独立定义：

```text
define internal i32 @_ZN4mono2id17hcd1552a4c09f6221E(i32 %x) unnamed_addr #0 {
define internal double @_ZN4mono2id17hf9dff96f02d7eed9E(double %x) unnamed_addr #0 {
```

一份泛型 `id` 变成了两个函数：一个收发 `i32`、一个收发 `double`（即 `f64`），函数名末尾的哈希把两个单态化实例区分开。这就是单态化的实物证据。

### 零成本抽象的代价：代码膨胀与编译时间

单态化换来运行期零开销，代价是编译产物可能变大、编译变慢——每多一种具体类型就多一份代码副本，这叫代码膨胀（code bloat）。一个被上百种类型实例化的泛型函数，会在二进制里留下上百份拷贝。

绝大多数泛型只被少数几种类型用到，膨胀可忽略，且换来的性能是实打实的。真正需要在意的场景（比如想缩小二进制体积、或某泛型被极多类型实例化）可以改用下节的 trait 对象走动态分发，用一份代码 + 运行期查表替代多份单态化副本，这是一个「代码体积 vs 运行期速度」的权衡。记住这条对照关系：泛型/`impl Trait` = 单态化 = 静态分发 = 快但可能膨胀；`dyn Trait` = 动态分发 = 一份代码但有间接开销。

#### 来源与时效

- 一手：The Rust Programming Language book Ch.10 §「Generic Data Types」「Performance of Code Using Generics」https://doc.rust-lang.org/book/ch10-01-syntax.html
- 一手：The Rust Reference §「Generic parameters」「Items — Functions（generic functions / monomorphization 语义）」https://doc.rust-lang.org/reference/items/generics.html
- 本机实证：rustc 1.94.1（`rustc --version` → `rustc 1.94.1 (e408947bf 2026-03-25)`）；`mono.rs` 经 `--emit=llvm-ir` 现跑得 `id` 的 i32 与 f64 两份单态化定义（输出见正文），运行输出 `1 2`。
- 交叉核对一致：book「Performance of Code Using Generics」与 Reference 均述「泛型经单态化在编译期具体化、无运行期开销」，无冲突。核实 2026-08-01。

## R6.2 trait 定义与实现

### trait：定义一组类型共享的行为

trait 是 Rust 的接口机制，用来声明「实现了它的类型必须提供哪些方法」。定义用 `trait 名 { ... }`，里面列方法签名（可只给签名、也可给默认实现）。任何类型只要 `impl 该trait for 该类型` 并补齐方法，就「拥有」了这组行为。

它类比其他语言的 interface（Java）或 typeclass（Haskell），核心作用是让不同类型以统一方式被使用——只要都实现了某 trait，就能被同一段泛型代码或同一个 trait 对象处理。这也是 R6.3 trait bound 的基础：约束「T 必须实现某 trait」就是在要求「T 必须具备某组行为」。

下面定义一个 `Summary` trait 并为 `Tweet` 实现它：

```rust
trait Summary {
    fn summarize_author(&self) -> String;
}
struct Tweet {
    username: String,
}
impl Summary for Tweet {
    fn summarize_author(&self) -> String {
        format!("@{}", self.username)
    }
}
```

`impl Summary for Tweet` 读作「为 Tweet 实现 Summary」。实现里必须给出 trait 声明的所有无默认实现的方法，签名要对得上，否则报错。方法里的 `&self` 表示这是个借用自身的实例方法（所有权规则见 R2）。

### 默认方法：trait 可自带方法体

trait 里的方法不仅能只写签名，还能直接给出默认实现（default method）。实现该 trait 的类型可以什么都不写、直接白用这个默认版本，也可以覆盖它。默认方法能调用同 trait 里的其他方法（哪怕那些方法没有默认实现），这让你只需实现少数「核心方法」，其余行为由默认方法基于核心方法自动组合出来。

下面 `summarize` 是默认方法，它调用了没有默认实现的 `summarize_author`。`Tweet` 只实现 `summarize_author` 就白得了 `summarize`；`Article` 则选择覆盖 `summarize`：

```rust
trait Summary {
    fn summarize_author(&self) -> String;
    fn summarize(&self) -> String {
        format!("(Read more from {}...)", self.summarize_author())
    }
}
struct Tweet { username: String }
impl Summary for Tweet {
    fn summarize_author(&self) -> String { format!("@{}", self.username) }
}
struct Article { title: String }
impl Summary for Article {
    fn summarize_author(&self) -> String { self.title.clone() }
    fn summarize(&self) -> String { format!("Article: {}", self.title) }
}
fn main() {
    let t = Tweet { username: String::from("rustlang") };
    let a = Article { title: String::from("Hello") };
    println!("{}", t.summarize());   // 用默认实现
    println!("{}", a.summarize());   // 用覆盖后的实现
}
```

`rustc --edition 2024 traits.rs && ./traits` 本机现跑输出：

```text
(Read more from @rustlang...)
Article: Hello
```

`Tweet` 没写 `summarize` 却能调用，正是默认方法的作用；`Article` 写了同名方法就覆盖掉默认版本。这是「实现少量核心方法、自动获得一组派生行为」的常见模式，标准库大量 trait（如 `Iterator` 只需实现 `next`、其余几十个方法都有默认实现）都靠它。

### 孤儿规则：实现的一致性（coherence）保障

孤儿规则（orphan rule）是对「谁能为谁实现 trait」的限制：只有当 trait 或类型至少有一个是在你当前 crate 里定义的，你才能写这个 `impl`。换句话说，你不能为「外部类型」实现「外部 trait」——比如在你的 crate 里给标准库的 `Vec<i32>` 实现标准库的 `Display`，两个都是别人的，禁止。

它的目的是保证一致性（coherence）：整个程序里，任何「类型 + trait」的组合最多只有一份实现，不会出现两个 crate 各自给同一对类型和 trait 写了不同实现、链接到一起时编译器不知道该用哪个的混乱。有了孤儿规则，「某类型对某 trait 的实现」全局唯一且可预测。

违反它会报 E0117。下面试图为外部类型 `Vec<i32>` 实现外部 trait `Display`：

```rust
impl std::fmt::Display for Vec<i32> {
    fn fmt(&self, f: &mut std::fmt::Formatter) -> std::fmt::Result {
        write!(f, "custom")
    }
}
fn main() {}
```

`rustc --edition 2024 orphan.rs` 本机现跑输出：

```text
error[E0117]: only traits defined in the current crate can be implemented for types defined outside of the crate
 --> orphan.rs:1:1
  |
1 | impl std::fmt::Display for Vec<i32> {
  | ^^^^^^^^^^^^^^^^^^^^^^^^^^^--------
  |                            |
  |                            `Vec` is not defined in the current crate
  |
  = note: impl doesn't have any local type before any uncovered type parameters
  = note: define and implement a trait or new type instead
```

报错直接点出 `Vec` 不是本 crate 定义的。绕过办法正是报错末尾的提示：要么定义自己的 trait 再给 `Vec<i32>` 实现（本地 trait，允许），要么用「newtype」把 `Vec<i32>` 包一层自己的结构体 `struct MyVec(Vec<i32>)` 再实现（本地类型，允许）。初学者遇到「想给标准库类型加方法却被拒」时，newtype 包装是标准解法。

#### 来源与时效

- 一手：The Rust Programming Language book Ch.10 §「Traits: Defining Shared Behavior」（含默认方法与「Implementing a Trait on a Type」「孤儿规则/coherence」说明）https://doc.rust-lang.org/book/ch10-02-traits.html
- 一手：The Rust Reference §「Traits」「Implementations — Orphan rules」https://doc.rust-lang.org/reference/items/implementations.html
- 本机实证：rustc 1.94.1；`traits.rs`（默认方法 + 覆盖）编译成功并输出如正文；`orphan.rs` 现跑得 E0117（输出见正文）。
- 交叉核对一致：book 与 Reference 对「trait = 共享行为接口」「默认方法可调同 trait 其他方法」「孤儿规则要求 trait 或类型至少一个本地」表述一致，无冲突。核实 2026-08-01。

## R6.3 trait bound 与 where

### trait bound：给泛型参数加「必须能干什么」的约束

trait bound 是写在泛型参数上的约束，形如 `T: Display`，意思是「类型 `T` 必须实现 `Display` trait」。裸泛型 `T` 对类型一无所知，什么方法都不能调；一旦加了 bound，泛型函数体内就能对 `T` 使用该 trait 提供的所有方法，编译器也会在调用点检查实参类型是否满足约束。

它是约束式（受限）多态的落地：泛型说「我对任意类型都工作」，但很多逻辑其实要求类型具备某些能力（能比较、能打印、能克隆）。trait bound 就是把「需要这些能力」写进签名，既让函数体能用这些能力，又让编译器拦住不满足的类型。其类型理论根在受限多态/类型类一脉（→L5-06 T7–T9），此处只作一句交叉引用，不展开。

下面 `print_it` 要求 `T: Display` 才能 `println!` 它；传入一个没实现 `Display` 的类型会报 E0277：

```rust
fn print_it<T: std::fmt::Display>(x: T) {
    println!("{x}");
}
struct NoDisplay;
fn main() {
    print_it(NoDisplay);
}
```

`rustc --edition 2024 e0277.rs` 本机现跑输出：

```text
error[E0277]: `NoDisplay` doesn't implement `std::fmt::Display`
 --> e0277.rs:6:14
  |
6 |     print_it(NoDisplay);
  |     -------- ^^^^^^^^^ unsatisfied trait bound
  |     |
  |     required by a bound introduced by this call
  |
help: the trait `std::fmt::Display` is not implemented for `NoDisplay`
...
note: required by a bound in `print_it`
 --> e0277.rs:1:16
  |
1 | fn print_it<T: std::fmt::Display>(x: T) {
  |                ^^^^^^^^^^^^^^^^^ required by this bound in `print_it`
```

E0277 是最常见的 Rust 报错之一，含义永远是「某类型没实现某 trait，而某处要求它实现」。报错会同时指出「哪里要求的」（bound 定义处）和「哪个类型没满足」，按提示为该类型补上 `impl` 即可。

### where 子句：约束多了就换个写法

`where` 子句是 trait bound 的另一种写法，把约束从尖括号里挪到签名末尾，适合约束多、或约束复杂时保持签名可读。下面两种写法完全等价：

```rust
fn foo<T: Clone + std::fmt::Debug, U: Clone>(t: T, u: U) -> i32 { 0 }

fn bar<T, U>(t: T, u: U) -> i32
where
    T: Clone + std::fmt::Debug,
    U: Clone,
{
    0
}
```

`+` 表示「同时满足多个 trait」，如 `T: Clone + Debug` 要求 `T` 既能克隆又能 Debug 打印。约束一两个时写尖括号内联更紧凑；一旦每个参数都有好几个约束，`where` 能让 `fn foo(...) -> ...` 这行主签名保持清爽，把约束单独列出。二者语义无差别，纯风格选择。

### impl Trait：匿名的 trait bound 语法糖

`impl Trait` 是一种简写：写在参数位置（`fn f(x: impl Display)`）等价于一个匿名泛型参数加 bound（`fn f<T: Display>(x: T)`）；写在返回位置（`-> impl Iterator<Item=i32>`）表示「返回某个实现了该 trait 的具体类型，但不写出它的名字」。

参数位置的 `impl Trait` 就是 trait bound 的糖，适合只用一次、懒得起名的泛型参数。返回位置的 `impl Trait` 更有用——它让你返回像闭包、迭代器适配器链这类「类型名极长甚至无法书写」的具体类型，同时仍走单态化/静态分发（区别于 `Box<dyn Trait>` 的动态分发，见 R6.4）。初学者要注意：返回位置的 `impl Trait` 仍是单一具体类型，函数不能在不同分支返回两个不同的具体类型（那需要 `Box<dyn Trait>`）。

#### 来源与时效

- 一手：The Rust Programming Language book Ch.10 §「Traits as Parameters」「Trait Bound Syntax」「Clearer Trait Bounds with where Clauses」「Returning Types That Implement Traits」https://doc.rust-lang.org/book/ch10-02-traits.html
- 一手：The Rust Reference §「Trait and lifetime bounds」「Impl trait」https://doc.rust-lang.org/reference/trait-bounds.html
- 本机实证：rustc 1.94.1；`e0277.rs` 现跑得 E0277（输出见正文）；`where`/`impl Trait` 等价写法编译通过。
- 交叉核对一致：book 与 Reference 对「`T: Trait` 约束语义」「`where` 与内联 bound 等价」「参数位 `impl Trait` 是匿名泛型糖、返回位 `impl Trait` 为单一具体类型」表述一致，无冲突。核实 2026-08-01。

## R6.4 trait 对象与动态分发

### trait 对象与 dyn：把「实现了同一 trait 的不同类型」放进同一个容器

trait 对象（trait object）是形如 `&dyn Trait` 或 `Box<dyn Trait>` 的类型，代表「某个实现了 `Trait` 的值，但具体是哪个类型到运行期才知道」。`dyn` 关键字标明这是动态分发。它让你把不同具体类型、只要都实现了同一 trait 的值，装进同一个集合或用同一个引用处理——这是泛型做不到的：`Vec<T>` 里所有元素必须是同一个 `T`，而 `Vec<Box<dyn Trait>>` 里可以混装多种类型。

它解决的是「运行期才确定、且可能异构」的场景。典型例子是 GUI 里一个「组件列表」要同时放按钮、文本框、图片，它们类型各异但都实现 `Draw` trait，就用 `Vec<Box<dyn Draw>>` 装起来统一 `draw()`。

下面把两种不同类型（`Tweet`、`Article`，都实现 `Summary`）装进同一个 `Vec<Box<dyn Summary>>`：

```rust
fn main() {
    let t = Tweet { username: String::from("rustlang") };
    let a = Article { title: String::from("Hello") };
    let items: Vec<Box<dyn Summary>> = vec![Box::new(t), Box::new(a)];
    for it in &items {
        println!("{}", it.summarize());
    }
}
```

（`Summary`/`Tweet`/`Article` 定义同 R6.2。）`rustc --edition 2024 traits.rs && ./traits` 现跑，两种类型混在一个 `Vec` 里被统一调用 `summarize`，分别输出 `(Read more from @rustlang...)` 与 `Article: Hello`。注意元素必须包一层指针（这里 `Box`），因为 `dyn Summary` 是大小不定的类型（不同实现体积不同），只能通过指针访问。

### 静态分发 vs 动态分发

静态分发（static dispatch）指编译期就确定调用哪个具体方法——泛型和 `impl Trait` 走的就是这条路，经单态化后每个调用直接指向具体类型的方法，无运行期查找。动态分发（dynamic dispatch）指运行期才确定调用哪个方法——trait 对象走这条路，每次方法调用都要通过一张运行期的函数指针表（vtable）查出该调哪个实现，再间接跳过去。

这是 Rust 里一组核心权衡。静态分发快（无间接、可内联），但每种类型生成一份代码（可能膨胀）；动态分发只需一份代码、支持运行期异构，代价是每次调用一层间接跳转、且通常无法内联优化。选择原则：能用泛型就用泛型（多数情况），只有当你确实需要「运行期异构集合」或「想避免代码膨胀」时才用 trait 对象。

同一个方法两种分发都能跑通，下面对照：

```rust
fn notify_static(item: &impl Summary) {   // 静态分发：单态化
    println!("static: {}", item.summarize());
}
fn notify_dyn(item: &dyn Summary) {        // 动态分发：vtable 查表
    println!("dyn: {}", item.summarize());
}
```

本机 `traits.rs` 中二者对同一个 `Tweet` 调用，输出内容相同（`(Read more from @rustlang...)`），差别只在底层：`notify_static` 被单态化成针对 `Tweet` 的专门函数，`notify_dyn` 则在运行期通过 vtable 找到 `Tweet::summarize`。

### vtable 与胖指针：动态分发的底层实现

trait 对象的引用是一个胖指针（fat pointer）：它比普通引用多一个字，除了指向数据本身的指针，还带一个指向 vtable（虚函数表）的指针。vtable 是编译期为「某类型实现某 trait」生成的一张表，里面存着该类型对该 trait 各方法的具体函数地址（以及类型的大小、对齐、析构函数）。运行期调 `it.summarize()` 时，就是顺着数据指针旁边的 vtable 指针查表、取出 `summarize` 的地址、间接调用。

可以直接量出胖指针比普通引用大一倍。下面测 `&S`（普通引用）与 `&dyn T`（trait 对象引用）的大小：

```rust
use std::mem::size_of;
trait T { fn f(&self); }
struct S;
impl T for S { fn f(&self) {} }
fn main() {
    println!("&S     = {}", size_of::<&S>());
    println!("&dyn T = {}", size_of::<&dyn T>());
}
```

`rustc --edition 2024 fatptr.rs && ./fatptr` 本机现跑输出：

```text
&S     = 8
&dyn T = 16
```

普通引用 8 字节（一个指针），trait 对象引用 16 字节（数据指针 + vtable 指针），实测印证了「胖指针」之说。这多出来的一个指针，就是动态分发能在运行期找到正确方法的代价与本钱。

### 对象安全（dyn 兼容性）：不是所有 trait 都能做成 trait 对象

要能用作 `dyn Trait`，trait 必须满足一组规则，这组规则历史上叫「对象安全（object safety）」，在 rustc 1.94.1 里官方术语已改名为「dyn 兼容性（dyn compatibility）」（⚙演进快·锚 rustc 1.94.1：错误信息与 `rustc --explain E0038` 现在都用「dyn compatible」，book Ch.10 等旧材料可能仍写「object safe」，二者指同一概念，delta 现查不凭记忆）。核心限制包括：方法不能返回 `Self`、方法不能带泛型参数——因为这类方法无法放进一张统一的 vtable（返回 `Self` 时不同实现返回不同大小的类型，泛型方法则会有无穷多个单态化版本，都无法编一张定长的表）。

违反会报 E0038。下面 `NotObjSafe` 有个返回 `Self` 的方法，试图用作 `&dyn NotObjSafe`：

```rust
trait NotObjSafe {
    fn make() -> Self;
}
fn use_it(_x: &dyn NotObjSafe) {}
fn main() {}
```

`rustc --edition 2024 objsafe.rs` 本机现跑输出（节选）：

```text
error[E0038]: the trait `NotObjSafe` is not dyn compatible
 --> objsafe.rs:4:16
  |
4 | fn use_it(_x: &dyn NotObjSafe) {}
  |                ^^^^^^^^^^^^^^ `NotObjSafe` is not dyn compatible
  |
note: for a trait to be dyn compatible it needs to allow building a vtable
...
 --> objsafe.rs:2:8
  |
2 |     fn make() -> Self;
  |        ^^^^ ...because associated function `make` has no `self` parameter
```

报错点破本质：`make` 没有 `self` 参数、且返回 `Self`，无法建 vtable，所以整个 trait 不能做 trait 对象。解决办法是报错提示给的两条：要么把方法改成带 `&self` 的实例方法，要么给它加 `where Self: Sized` 把这个方法「排除在 trait 对象之外」（这样 trait 剩下的部分仍可 dyn 化）。初学者遇到 E0038 时，多半是 trait 里有返回 `Self`（如 `Clone`）或泛型方法，按这两条改即可。

#### 来源与时效

- 一手：The Rust Programming Language book Ch.10 §「Using Trait Objects That Allow for Values of Different Types」（trait 对象 / `dyn` / 静态 vs 动态分发）https://doc.rust-lang.org/book/ch10-02-traits.html
- 一手：The Rust Reference §「Trait objects」「Traits — Dyn compatibility」https://doc.rust-lang.org/reference/items/traits.html
- 本机实证：rustc 1.94.1；`traits.rs`（`Vec<Box<dyn Summary>>` 异构容器 + 静态/动态分发对照）编译运行成功；`fatptr.rs` 测得 `&S`=8、`&dyn T`=16（胖指针）；`objsafe.rs` 现跑得 E0038；`rustc --explain E0038` 现跑正文用「dyn-compatible」术语。
- ⚙演进快·锚 rustc 1.94.1：术语「object safety」已改名「dyn compatibility」——本机 E0038 报错与 `--explain` 均用新词，book Ch.10 页面（对齐 1.90.0）与许多社区材料仍用旧词「object safe」，指同一规则集；改名不改语义，delta 现查。核实 2026-08-01。
- 交叉核对一致：book 与 Reference 对「trait 对象靠 vtable 动态分发」「元素需经指针访问」「不能返回 Self / 无泛型方法」规则一致；仅术语新旧有别（已单列于上）。

## R6.5 局部类型推导

### 局部类型推导：表达式级、非全程的类型推断

Rust 有类型推导，但它是局部的（local）——编译器在函数体内根据字面量、函数返回类型、后续用法等就近线索推断变量与表达式的类型，让你在大多数 `let` 处不必写类型标注。但它不是全程（whole-program）推导：函数签名（参数与返回类型）必须显式写全类型，推导只在函数体内部起作用。

它的好处是省去大量样板标注的同时保持完全的静态类型安全——省的只是「写出来」，类型本身编译期一样确定、一样检查。签名处强制标注则保证了：读一个函数签名就能知道它的完整类型接口，不必去读函数体，也让每个函数能独立类型检查（改一处函数体不会让别处签名的推断结果漂移）。

下面这些都无需标注，编译器就近推断：

```rust
fn main() {
    let n = 5;                                    // 由字面量默认推为 i32
    let v = vec![1, 2, 3];                        // 推为 Vec<i32>
    let doubled: Vec<i32> = v.iter().map(|x| x * 2).collect();
    let parsed: u32 = "42".parse().unwrap();      // 由标注反推 parse 的目标类型
    println!("{n} {doubled:?} {parsed}");
}
```

`rustc --edition 2024 infer_ok.rs && ./infer_ok` 本机现跑输出 `5 [2, 4, 6] 42`。注意 `parse` 那行：`parse` 能解析成多种数值类型，编译器靠等号左边的 `: u32` 标注反推出应解析成 `u32`——这是「推导双向流动」的例子，标注可以从使用点回流去确定表达式类型。

### 与 Hindley–Milner 全程推导的差别

一些函数式语言（如 ML、Haskell）用 Hindley–Milner（HM）类型推导，能在完全不写任何类型标注的情况下推出整个程序（含函数签名）的类型。Rust 有意不这么做：它只在局部（函数体内）推导，函数签名必须显式。HM 的算法根（合一、主类型、Algorithm W、let-多态）在编程语言理论一脉（→L5-06 T7），此处只作一句交叉引用，不展开。

这个设计是权衡的结果。全程 HM 推导虽省标注，但一处改动可能让远处函数的推断类型悄悄改变、报错信息也难以定位到根因；Rust 要求签名显式，换来的是清晰的模块边界、可读的接口、以及能精确指到出错点的报错。对初学者的实践含义是：函数体内放心少写标注、让编译器推；但写函数时参数和返回类型必须自己标全。

### 何时推导会失败、必须你来标注

当编译器就近找不到足够线索唯一确定类型时，推导失败，报 E0282 或 E0283（type annotations needed），要求你补标注。最经典的是 `collect()`：它能收集成 `Vec`、`HashSet`、`String` 等多种类型，若你不告诉它收集成什么，编译器无法决定。

```rust
fn main() {
    let v = vec![1, 2, 3];
    let doubled = v.iter().map(|x| x * 2).collect();
    println!("{doubled:?}");
}
```

`rustc --edition 2024 infer.rs` 本机现跑输出：

```text
error[E0283]: type annotations needed
 --> infer.rs:3:9
  |
3 |     let doubled = v.iter().map(|x| x * 2).collect();
  |         ^^^^^^^                           ------- type must be known at this point
  |
  = note: cannot satisfy `_: FromIterator<i32>`
help: consider giving `doubled` an explicit type
  |
3 |     let doubled: Vec<_> = v.iter().map(|x| x * 2).collect();
  |                ++++++++
```

报错说「无法确定收集成什么类型」，提示补 `: Vec<_>`（元素类型仍可让编译器推，只需定「是个 Vec」）。这里也可以用另一种常见写法 turbofish：`.collect::<Vec<_>>()`，把类型直接写在方法调用上。二者都是给推导补上它缺的那一条线索。初学者记住：绝大多数标注可省，但遇到 `collect`、`parse`、`"...".into()` 这类「一个来源能产出多种目标类型」的调用，往往要标目标类型。

#### 来源与时效

- 一手：The Rust Programming Language book Ch.3 §「Data Types（标注与推断）」、Ch.10（泛型语境下的推断）https://doc.rust-lang.org/book/ch03-02-data-types.html
- 一手：The Rust Reference §「Type inference」（局部推断、签名需显式）https://doc.rust-lang.org/reference/type-inference.html
- 本机实证：rustc 1.94.1；`infer_ok.rs`（含由标注反推 `parse` 目标类型）编译运行得 `5 [2, 4, 6] 42`；`infer.rs`（`collect` 无标注）现跑得 E0283 + 提示补 `Vec<_>`（输出见正文）。
- 交叉核对一致：book 与 Reference 对「Rust 采用局部/表达式级推断而非全程 HM」「函数签名必须显式标类型」「`collect`/`parse` 等需目标类型标注」表述一致，无冲突。核实 2026-08-01。
- 术语注记：本机对无标注 `collect` 报 E0283（存在多个候选，annotations needed）；纯粹无任何线索的空推断变量则报 E0282，二者同属「type annotations needed」族，均以本机现跑为准。
