# L5-14·大主题R5 代数数据类型与模式匹配

> 基线 Py3.11.15/np2.4.6/gcc13.3 @2026-07-25（+ rustc/cargo 1.94.1）｜核实日期：2026-08-01｜先修：R2 所有权与 move 语义、R3 借用（值被 match 消费或借用取决于所有权规则）｜一手锚点：The Rust Programming Language book Ch.5「Using Structs」、Ch.6「Enums and Pattern Matching」、Ch.18/19「Patterns and Matching」@对齐 rustc 1.90.0 + edition 2024、The Rust Reference §「Patterns」「Match expressions」@活文档、本机 rustc 1.94.1 实测｜成熟度：GA（struct/enum/match/模式语法自 Rust 1.0 稳定；`let ... else` 自 Rust 1.65 稳定）；穷尽性检查（exhaustiveness）内部算法 ⚙演进快·锚 rustc 1.94.1·随时变（对外的"必须覆盖所有情况"规则稳定）

代数数据类型（algebraic data type，ADT）是把数据"用积（product）和和（sum）两种方式组合起来"的类型建模手段。Rust 的 `struct` 是积类型（一个值同时含有若干字段），`enum` 是和类型（一个值是若干变体之一）；两者配合 `match` 模式匹配，构成 Rust 描述数据形状、并让编译器强制你处理所有情况的核心工具。这一章面向初学者，把 struct 建模、enum 与 `Option`、`match` 与穷尽性检查、模式语法全谱四块讲到能看懂、能上手、能改错为止。积/和类型的形式理论根在编程语言理论一脉（→L5-06 T4），此处只作一句交叉引用，不展开。

本报告所有 rustc 编译结果与报错均为本机 rustc 1.94.1（`--edition 2024`）现跑，复现命令随例给出；报错码以本机输出为准，不凭记忆。

## R5.1 struct 建模

### struct 是把多个字段捆成一个类型的积类型

struct（结构体）把一组相关的值命名并打包成一个自定义类型。它是"积类型"：一个 struct 值同时持有它所有字段的值，就像数学里的笛卡尔积，可能的取值数等于各字段可能取值数的乘积。Rust 有三种 struct：具名字段 struct、元组 struct、单元 struct。

```rust
struct Point { x: i32, y: i32 }   // 具名字段 struct：字段各有名字
struct Pair(i32, i32);            // 元组 struct：字段只有位置、无名字
struct Unit;                      // 单元 struct：没有任何字段
```

对初学者最直观的是具名字段 struct：你给每个字段起名字，构造和访问都靠名字，代码可读性最好。它适合"这个东西由几个有明确含义的部分组成"的场景，比如一个点由 `x`、`y` 组成。构造时用 `Point { x: 3, y: 4 }`，访问时用 `p.x`。

### 元组 struct 与单元 struct 各自的用途

元组 struct 像给一个元组起了类型名，字段没有名字、靠下标 `.0`、`.1` 访问。它的价值在于"类型区分"：`struct Meters(f64);` 和 `struct Feet(f64);` 底层都是一个 `f64`，但它们是两个不同类型，编译器不允许你把米当英尺传，这就用极小的成本挡住了一类单位混淆 bug。

单元 struct 没有任何字段，`struct Unit;` 定义、`Unit` 构造。它本身不存数据，常用于给某个类型实现 trait 而该类型不需要携带状态的场景（trait 见 R6）。初学者阶段先知道它存在、是合法的一种 struct 即可。

### 方法与关联函数：impl 块

给 struct 添加行为要写在 `impl` 块里。带 `self`（或 `&self`、`&mut self`）第一个参数的是方法，用 `实例.方法()` 调用；不带 `self` 的是关联函数（associated function），用 `类型::函数()` 调用，最典型的就是充当构造器的 `new`。

```rust
struct Point { x: i32, y: i32 }
impl Point {
    fn new(x: i32, y: i32) -> Self { Point { x, y } }   // 关联函数（构造器）
    fn manhattan(&self) -> i32 { self.x.abs() + self.y.abs() } // 方法
}
fn main() {
    let p = Point::new(3, -4);
    println!("{}", p.manhattan());
}
```

`rustc --edition 2024 structs.rs && ./structs` 本机现跑输出 `7`（exit 0）。这里 `Self` 是"当前 impl 的类型"的简写，等价于写 `Point`。`&self` 表示方法只借用（不获取所有权）实例，是最常见的写法；若方法要修改字段则用 `&mut self`，若要消费掉实例则用 `self`。初学者易错点：Rust 没有 `class` 关键字，"数据（struct）"和"行为（impl）"是分开写的，这和很多 OOP 语言把两者写在一起不同——但用起来 `p.manhattan()` 的手感是一致的。

## R5.2 enum 与 Option

### enum 是"多选一"的和类型

enum（枚举）定义一个类型，它的值是若干"变体（variant）"之一，同一时刻只能是其中一个。这就是"和类型"：可能取值的总数是各变体可能取值数之和。和 C 里"枚举只是整数别名"不同，Rust 的 enum 变体可以各自携带不同类型、不同数量的数据。

```rust
enum Msg {
    Quit,                       // 无数据
    Move { x: i32, y: i32 },    // 携带具名字段（像内嵌 struct）
    Write(String),              // 携带一个 String
}
```

这段的关键在于三个变体形状完全不同：`Quit` 不带数据，`Move` 带两个具名字段，`Write` 带一个 `String`。用一个 `enum` 就能表达"一条消息是这几种之一，且不同种类带不同负载"。这正是 enum 比 C 枚举强大的地方——它同时编码了"是哪一种"和"这一种带什么数据"，且编译器保证你访问数据前先确认了是哪一种。

struct 是"AND"（同时有 x 和 y），enum 是"OR"（是 Quit 或 Move 或 Write 之一）。真实数据建模里两者常嵌套：一个 enum 的某个变体里放一个 struct，或一个 struct 的某个字段是 enum。

### Option：用类型消灭空指针

`Option<T>` 是标准库定义的一个 enum，只有两个变体：`Some(T)` 表示"有一个 T 类型的值"，`None` 表示"没有值"。Rust 没有 null，"可能没有值"这件事被强制编码进类型里，让编译器逼你显式处理"没有"的情况。

```rust
enum Option<T> {
    None,
    Some(T),
}
```

这是 Rust 安全性的一个招牌设计。在有 null 的语言里，任何引用都可能悄悄是 null，忘记检查就在运行期崩溃（Tony Hoare 称之为"十亿美元的错误"）。Rust 把它翻译成类型层面的区分：一个 `i32` 一定是个整数，而一个 `Option<i32>` 才可能"没有"。你不能把 `Option<i32>` 当 `i32` 直接用——想拿到里面的值，必须先经过 `match`、`if let` 或 `unwrap` 一类方法把 `Some` 和 `None` 两种情况都交代清楚。

```rust
fn plus_one(x: Option<i32>) -> Option<i32> {
    match x {
        None => None,
        Some(i) => Some(i + 1),
    }
}
fn main() {
    println!("{:?}", plus_one(Some(5)));  // Some(6)
    println!("{:?}", plus_one(None));     // None
}
```

`rustc --edition 2024 optmatch.rs && ./optmatch` 本机现跑输出 `Some(6)` 与 `None`（exit 0）。注意 `match` 里对 `Some(i)` 的匹配同时完成了两件事：确认了是 `Some` 分支，并把内部的值绑定到 `i` 上供分支体使用——这是 enum 携带数据能被安全取出的机制。`Option`（以及 `Result`，见 R7）是"用和类型建模'可能缺失/可能失败'"的工程化落地，其类型理论根（sum/variant）→L5-06 T4，此处只作一句交叉引用，不展开。

## R5.3 match 与穷尽性检查

### match 是必须覆盖所有情况的分支表达式

`match` 拿一个值，依次和一系列"模式（pattern）"比对，命中第一个匹配的分支（arm）就执行它。它是表达式，整体求值出一个结果可赋给变量。`match` 的招牌特性是穷尽性检查（exhaustiveness checking）：编译器要求所有可能的情况都被某个分支覆盖，否则拒绝编译。

```rust
enum Dir { North, South, East, West }
fn main() {
    let d = Dir::North;
    match d {
        Dir::North => println!("n"),
        Dir::South => println!("s"),
    }
}
```

这段故意漏掉了 `East` 和 `West`。`rustc --edition 2024 e0004.rs` 本机现跑输出：

```text
error[E0004]: non-exhaustive patterns: `Dir::East` and `Dir::West` not covered
 --> e0004.rs:4:11
  |
4 |     match d {
  |           ^ patterns `Dir::East` and `Dir::West` not covered
  |
note: `Dir` defined here
 --> e0004.rs:1:6
  |
1 | enum Dir { North, South, East, West }
  |      ^^^                 ----  ---- not covered
  = note: the matched value is of type `Dir`
help: ensure that all possible cases are being handled by adding a match arm with a wildcard pattern, a match arm with multiple or-patterns as shown, or multiple match arms
```

报错码是 E0004（non-exhaustive patterns），并精确指出 `East` 和 `West` 未被覆盖。这就是穷尽性检查的价值所在：将来你给 `Dir` 加一个新变体 `Up`，所有没覆盖它的 `match` 会立刻编译失败，逼你去处理新情况——编译器帮你把"忘了处理某种情况"的 bug 拦在编译期。这是 Rust 相对很多语言 `switch` 默认不强制 default 分支的一大安全优势。

### 通配符 `_` 与兜底绑定

当情况太多、你只关心其中几种时，用通配符 `_` 或一个绑定变量兜底剩余全部情况，满足穷尽性。`_` 匹配任何值且不绑定；一个小写名字（如 `other`）匹配任何值并把它绑定，供分支体使用。

```rust
let n = 9;
match n {
    1 => println!("one"),
    2 => println!("two"),
    _ => println!("something else"),   // 兜底，i32 有 2^32 种取值，必须兜底
}
```

对 `i32` 这类取值范围极大的类型，`match` 也要求穷尽，所以几乎总要一个 `_` 或绑定分支兜底，否则同样报 E0004。初学者易错点：`_` 会吞掉它之前所有分支没接住的情况，因此它必须放在最后——放在前面会让它后面的具体分支变成"永远匹配不到"的死代码（编译器会给出 unreachable pattern 警告）。分支的匹配顺序是自上而下、命中即停。

## R5.4 模式语法全谱

### 解构 struct 与 enum

模式不只用来"选分支"，还能把复合值拆开、把内部数据绑定到变量。对 enum 变体，模式的形状要和变体定义一致：`Msg::Move { x, y }` 拆出两个具名字段，`Msg::Write(s)` 拆出元组式负载。

```rust
enum Msg { Quit, Move { x: i32, y: i32 }, Write(String) }
let m = Msg::Move { x: 1, y: 2 };
match m {
    Msg::Quit => println!("quit"),
    Msg::Move { x, y } => println!("move {x},{y}"),  // 解构并绑定 x、y
    Msg::Write(s) => println!("write {s}"),
}
```

这段在 `patterns.rs` 中现跑输出 `move 1,2`（见本节末实证）。解构是模式匹配日常最常用的能力：一次匹配同时完成"确认是哪个变体"和"取出内部数据"，且编译器保证你只在确认了变体之后才能碰它的数据，从根上杜绝了"当成错误的变体去访问数据"。struct 也能同样解构，如 `let Point { x, y } = p;`。

### 范围模式与匹配守卫

范围模式用 `..=` 匹配一段闭区间，如 `1..=5` 匹配 1 到 5（含两端）。匹配守卫（match guard）是分支后加 `if 条件`，只有模式匹配且条件为真才命中，用于表达模式本身表达不了的额外判断。

```rust
let n = 5;
match n {
    1..=5 if n % 2 == 1 => println!("small odd"),
    1..=5 => println!("small even"),
    _ => println!("other"),
}
```

在 `patterns.rs` 中这段现跑输出 `small odd`（`n=5` 落在 1..=5 且为奇数）。守卫让你把"范围/形状"（交给模式）和"任意布尔条件"（交给 `if`）分工，比把所有逻辑塞进一个模式清晰得多。初学者要注意：守卫里的条件不参与穷尽性检查——编译器不会分析 `if` 里的表达式，所以一组带守卫的分支即便逻辑上覆盖了所有值，编译器也可能仍要求一个兜底分支。范围模式对整数与 `char` 可用；`..=` 是含右端的闭区间语法。

### `@` 绑定与 or-模式

`@` 绑定（读作 at-binding）让你在用模式测试一个值的同时，把这个值整体绑定到一个变量。写法 `变量 @ 模式`：只有值匹配 `模式` 才命中，且命中后变量拿到整个值。or-模式用 `|` 把多个模式并列，任一匹配即命中。

```rust
let id = 7;
match id {
    v @ 1..=9 => println!("got single digit {v}"),  // 既测范围又拿到值 v
    _ => println!("big"),
}

let c = 'c';
match c {
    'a' | 'e' | 'i' | 'o' | 'u' => println!("vowel"),  // or-模式
    _ => println!("consonant"),
}
```

在 `patterns.rs` 中这两段分别现跑输出 `got single digit 7` 与 `consonant`。`@` 解决的痛点是"我既想检查值落在某范围，又想在分支里用到这个值"——没有 `@` 时，范围模式匹配成功却不给你绑定，你就拿不到具体值。or-模式则避免为每个字面量写一个重复分支。

### `if let`、`let else` 与 `while let`

当你只关心一种模式、其余一概不理时，用 `if let` 替代冗长的 `match`。`if let 模式 = 值 { ... }` 在匹配成功时执行块，否则跳过；可配 `else`。`let else`（`let 模式 = 值 else { ... };`）则在匹配失败时执行 `else` 块，且 `else` 块必须发散（return/break/panic 等），匹配成功时把绑定引入到后续正常流程，避免右移的嵌套。

```rust
let opt = Some(3);
if let Some(x) = opt { println!("if let {x}"); }   // 只处理 Some

let Some(y) = opt else { return; };                // 失败即 return，成功则 y 可用于后续
println!("let else {y}");
```

在 `patterns.rs` 中这段现跑输出 `if let 3` 与 `let else 3`。`if let` 是"只关心一个分支"时对 `match` 的简写，代价是放弃了穷尽性检查——所以它适合"其余情况确实无需处理"的场合，别用它来偷懒回避本该处理的情况。`let else` 是 Rust 1.65 稳定的较新语法，专治"取出可选值、取不到就提前退出"这一高频模式，让主流程保持在左侧不缩进。与之对应的 `while let` 在模式持续匹配期间循环，常用于反复从容器/迭代器里取值直到 `None`。

本节所有片段合并在 `patterns.rs` 中：`rustc --edition 2024 patterns.rs && ./patterns` 现跑，除对未构造变体的 `dead_code` 警告外编译成功（exit 0），依次输出 `move 1,2` / `small odd` / `got single digit 7` / `consonant` / `if let 3` / `let else 3`。

#### 来源与时效

- 一手：The Rust Programming Language book Ch.5「Using Structs to Structure Related Data」（三种 struct、方法/关联函数）https://doc.rust-lang.org/book/ch05-00-structs.html
- 一手：The Rust Programming Language book Ch.6「Enums and Pattern Matching」（enum、`Option`、`match`、`if let`）https://doc.rust-lang.org/book/ch06-00-enums.html
- 一手：The Rust Programming Language book「Patterns and Matching」章（解构/守卫/范围/`@`/or-模式/`while let`）https://doc.rust-lang.org/book/ch19-00-patterns.html
- 一手：The Rust Reference §「Patterns」「Match expressions」（模式语法与 match 穷尽性的规范表述）https://doc.rust-lang.org/reference/patterns.html 、https://doc.rust-lang.org/reference/expressions/match-expr.html
- 一手：标准库 `std::option::Option` 文档（`Some`/`None` 定义）https://doc.rust-lang.org/std/option/enum.Option.html
- 本机实证：rustc 1.94.1（`rustc --version` → `rustc 1.94.1 (e408947bf 2026-03-25)`）；`structs.rs`→`7`、`optmatch.rs`→`Some(6)`/`None`、`patterns.rs`→6 行输出均编译运行成功（exit 0）；`e0004.rs` 现跑得 E0004（non-exhaustive patterns），输出见正文。命令与输出随例给出。
- 交叉核对一致：book Ch.5–6/19 与 Reference 对「struct=积类型三形态」「enum=和类型/变体带异构数据」「`Option` 两变体消灭 null」「`match` 强制穷尽、E0004」「解构/守卫/范围/`@`/or/`if let`/`let else` 语义」表述一致，无冲突。
- 版本与时效：模式语法、`struct`/`enum`/`match` 自 Rust 1.0 稳定；`let ... else` 自 Rust 1.65（2022-11）稳定，本机 rustc 1.94.1（edition 2024）可用并实测通过。穷尽性检查的对外规则（必须覆盖所有情况）稳定，其内部判定算法属编译器实现细节 ⚙演进快·锚 rustc 1.94.1·随时变。the book 页面对齐 rustc 1.90.0，与本机 1.94.1 在本章语法上无可见 delta（现跑核对）。核实 2026-08-01。
