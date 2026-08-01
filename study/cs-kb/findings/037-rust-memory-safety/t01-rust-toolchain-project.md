# L5-14·大主题R1 Rust 定位、工具链与工程骨架

> 基线 Py3.11.15/np2.4.6/gcc13.3 @2026-07-25（+ rustc/cargo 1.94.1） ｜ 核实日期：2026-08-01 ｜ 先修：任一门系统级语言（C/C++/Java 皆可）+ 命令行基础；无需先学所有权（那是 R2 起的内容）｜ 一手锚点：The Rust Programming Language book（"the book"，Klabnik/Nichols/Krycho）Ch.1–2、Ch.7、Ch.11、Ch.14 https://doc.rust-lang.org/book/ ；The Rust Reference https://doc.rust-lang.org/reference/ ；The Cargo Book https://doc.rust-lang.org/cargo/ ；本机 rustc/cargo 1.94.1 实测 ｜ 成熟度：GA，工具链稳定；其中 edition/resolver 默认值 `⚙演进快·锚版本`，须对齐锚版本现查

这一关先把 Rust 的"外围"打通：怎么装、怎么建工程、怎么编译运行、怎么管依赖、怎么测试、怎么出文档。它不碰所有权/借用那套让 Rust 与众不同的核心机制（那从大主题 R2 开始），而是让初学者先能把一个真实的、多文件、带测试和文档的 Rust 工程从零跑起来。可以把本章理解成"先学会用锅和灶，再学炒菜"。

一个贯穿始终的事实要先摆明：Rust 的语言编译器叫 `rustc`，但日常几乎没人直接调 `rustc`——真正的入口是构建工具 `cargo`。`cargo` 负责建工程、拉依赖、编译（背后调 `rustc`）、跑测试、生成文档、发布包。所以本章的主线其实是"围绕 cargo 的工作流"，`rustc` 更多是它幕后的执行者。

本报告所有命令与输出均在本机 **rustc/cargo 1.94.1（2026-03 构建）** 上真跑取证；the book 页面声明对齐 **Rust 1.90.0（2025-09-18）+ edition 2024**，二者存在版本差，凡涉及默认值（如新建工程的默认 edition、workspace 的 resolver 版本）处均标注并以本机实测为准。

---

## R1.1 安装与 cargo/rustc

### R1.1.1 rustup、rustc 与 cargo 三者的分工

Rust 的官方安装方式是通过 **rustup**——一个"工具链管理器"，它不是编译器本身，而是负责下载、切换、更新多套 Rust 工具链（stable / beta / nightly，以及不同目标平台）的管家。装好 rustup 后，你就得到了两个核心命令行工具：**rustc**（Rust 编译器，把 `.rs` 源码编成可执行文件或库）和 **cargo**（构建系统与包管理器，是你日常真正打交道的入口）。本机三者版本实测如下。

```text
$ rustup --version
rustup 1.29.0 (28d1352db 2026-03-05)
$ rustc --version
rustc 1.94.1 (e408947bf 2026-03-25)
$ cargo --version
cargo 1.94.1 (29ea6fb6a 2026-03-24)
```

初学者最容易混淆的就是"到底该用哪个命令"。心智模型是一层套一层：rustup 管"我装了哪几个版本的 Rust"，cargo 管"我这个工程怎么建怎么跑"，rustc 是 cargo 最终调用的那个真正干编译活的程序。绝大多数时候你只敲 `cargo ...`，几乎不需要手动敲 `rustc foo.rs`；直接调 `rustc` 通常只出现在教学演示单文件、或写极小实验时。

为什么要有 rustup 这层？因为 Rust 六周一个稳定版、发布很勤，而且很多前沿特性只在 nightly 上开放（本课后面 Polonius、部分 async 工具会碰到"需要 nightly"的情况）。rustup 让你能在同一台机器上并存 stable 和 nightly，用 `rustup toolchain install nightly`、`rustup default stable`、`rustup update` 这类命令自由切换和升级，而不用重装整个环境。这也是为什么官方文档几乎都假设你是用 rustup 装的 Rust。

rustup 的版本号（这里 1.29.0）和 rustc/cargo 的版本号（1.94.1）是两套独立编号，别把它们看成同一个东西——`rustup --version` 的输出里官方还专门提示了这一点。

### R1.1.2 cargo new 与工程骨架

`cargo new <名字>` 一键生成一个标准 Rust 工程骨架。它默认建的是"二进制工程"（能编成可执行程序）；加 `--lib` 则建"库工程"。本机实跑 `cargo new demo` 的产物如下。

```text
$ cargo new demo
    Creating binary (application) `demo` package
$ find demo -type f -not -path '*/.git/*'
demo/.gitignore
demo/Cargo.toml
demo/src/main.rs
```

生成的骨架只有三个关键文件：**Cargo.toml**（工程清单，声明包名、版本、edition、依赖）、**src/main.rs**（源码入口，二进制工程默认带一个打印 `Hello, world!` 的 `main` 函数）、**.gitignore**（默认忽略 `target/` 构建产物目录）。此外 `cargo new` 默认还会顺手 `git init` 初始化一个 git 仓库。本机生成的 Cargo.toml 内容为：

```toml
[package]
name = "demo"
version = "0.1.0"
edition = "2024"

[dependencies]
```

要理解这套骨架，关键是抓住"约定优于配置"这条 cargo 的设计哲学。cargo 靠固定的目录约定来找东西，你不用告诉它源码在哪、产物放哪：源码放 `src/`，构建产物一律进 `target/`（所以 `.gitignore` 默认忽略它），工程根目录必须有 `Cargo.toml`。只要遵守这套约定，`cargo build`/`run`/`test` 就都能自动工作。

初学者常见的困惑是"main.rs 里为什么不用像 C 那样写一堆头文件包含"。因为 cargo/rustc 用的是模块系统而不是文本包含（详见 R1.3），骨架给你的就是一个能立刻编译运行的最小完整工程。你现在就可以 `cd demo && cargo run`，无需任何额外配置。

`cargo new` 会尝试 `git init`，如果你是在一个已有的 git 仓库里面再 `cargo new`，它会检测到并跳过初始化，不会嵌套建库。

### R1.1.3 构建、运行与检查：build / run / check 与 dev/release

日常三条命令覆盖绝大多数场景：`cargo build` 只编译不运行、`cargo run` 编译并运行（若源码没变则直接跑上次的产物）、`cargo check` 只做类型检查和借用检查、**不生成可执行文件**。本机在上面的 demo 工程里实跑 `cargo run`：

```text
$ cargo run
   Compiling demo v0.1.0 (.../demo)
    Finished `dev` profile [unoptimized + debuginfo] target(s) in 1.04s
     Running `target/debug/demo`
Hello, world!
```

注意输出里的 `dev profile [unoptimized + debuginfo]`：默认构建走的是 **dev（调试）配置**，不开优化、带调试信息、编译快，产物放在 `target/debug/`。当你要测真实性能或发布时，加 `--release`，走 **release 配置**，开优化、去调试信息、编译慢但运行快，产物进 `target/release/`。profile 的细节留到 R1.5.1。

`cargo check` 是初学者最该早点养成习惯的命令。它跑完编译器的全部前端分析（语法、类型、借用检查），但跳过最耗时的代码生成和链接，所以比 `cargo build` 快很多。写 Rust 时常见的循环是"改代码 → `cargo check` 看编译器报错 → 改 → 再 check"，只有真要运行时才 `cargo build`/`run`。因为 Rust 的借用检查器很严格，你会频繁地跟编译器报错打交道，`cargo check` 能把这个反馈回路显著加速。

`cargo run` 不接受源文件名参数——它跑的是当前工程，不是"跑某个 .rs 文件"。想给你的程序传命令行参数，用 `cargo run -- 参数1 参数2`，`--` 后面的东西才会传给你的程序，而不是传给 cargo。

### R1.1.4 依赖管理：Cargo.toml、crates.io 与 Cargo.lock

在 Rust 里加一个第三方库（Rust 术语叫 crate），只需在 `Cargo.toml` 的 `[dependencies]` 段写一行"名字 = 版本要求"，下次 `cargo build`/`run` 时 cargo 会自动从官方中央仓库 **crates.io** 下载、编译并链接它。例如加随机数库：

```toml
[dependencies]
rand = "0.8.5"
```

这里的版本号 `"0.8.5"` 不是"精确要 0.8.5"，而是一条**语义化版本（SemVer）兼容要求**：它表示"要与 0.8.5 兼容的版本"，cargo 会挑选 `>=0.8.5` 且 `<0.9.0` 范围内的最新版。SemVer 的约定是"主版本号相同即向后兼容"，所以升到 0.8.6、0.8.9 都算兼容、可自动采用，但不会自动跳到 0.9.0（那被视为可能有破坏性变更）。理解这一点能避免"我明明写了 0.8.5 怎么装了 0.8.9"的困惑。

真正锁定"这次到底用了哪些精确版本"的是 **Cargo.lock** 文件，它由 cargo 自动生成和维护，记录整棵依赖树里每个包被解析到的确切版本。这样别人 clone 你的工程、或你换台机器，只要有 Cargo.lock，`cargo build` 就会装出完全一样的版本组合，保证可复现构建。一条常被问的实践准则来自 Cargo 官方文档：**二进制（应用）工程应把 Cargo.lock 提交进版本控制；库工程通常不提交**（因为库的使用者会用他们自己的 lock）。

初学者两个易错点。其一，改了 `Cargo.toml` 不必手动装依赖，下次任何 `cargo build`/`check`/`run` 都会自动同步；也可以用 `cargo add rand` 让 cargo 帮你把这行写进 Cargo.toml（它会自动填上当前最新的兼容版本号）。其二，`target/` 里的东西全是可再生的构建缓存，删掉不影响源码，出现奇怪的构建缓存问题时 `cargo clean` 清空重来是安全的。

### R1.1.5 edition：什么是版本纪元，以及 edition 2024

**edition（版本纪元）** 是 Rust 特有的机制：它是一组语言层面的"方言选择"，让 Rust 能引入某些不向后兼容的语法/默认行为变化，同时不分裂生态。每个 crate 在 `Cargo.toml` 里用 `edition = "..."` 声明自己按哪一版纪元的规则编译；目前存在的 edition 有 2015、2018、2021、2024。本机 `cargo new` 默认生成的是 `edition = "2024"`（见 R1.1.2 的 Cargo.toml 实测）。

⚙演进快·锚版本（核实 2026-08-01）：edition 每几年出一版，"新建工程默认用哪一版"随工具链版本推进。the book 页面声明对齐 Rust 1.90.0 + edition 2024；本机 rustc/cargo 1.94.1 实测新建工程默认也是 edition 2024。具体某版本默认哪个 edition，请以你手上的 `cargo new` 实际产物为准，不要凭记忆断言。

理解 edition 的关键，是它解决了一个真实的两难：语言想进化（比如改某个关键字的含义、改某个默认行为），但又绝不能让老代码一升级编译器就编不过。Rust 的解法是——编译器同时懂所有 edition，每个 crate 自报用哪一版规则。于是你的旧 crate 声明 `edition = "2015"` 就永远按 2015 规则编，新 crate 声明 `edition = "2024"` 享受新语法，两者还能互相依赖、链接到同一个程序里。这就是"语言能进化又不撕裂生态"的工程手段。

初学者要澄清两个误解。其一，edition 不是"Rust 的版本号"——Rust 版本号是 1.90、1.94 这种六周一发的东西，edition 是 2015/2018/2021/2024 这种几年一次的语法纪元，二者正交。新版编译器照样能编老 edition 的代码。其二，edition 之间的差异对初学者其实很小，日常写代码几乎感觉不到；它主要影响的是一些边角语法和默认 lint，真正重要的是"知道有这么个字段、别手贱去改动一个能编过的工程的 edition"。想升级 edition 时，cargo 提供 `cargo fix --edition` 来半自动迁移。

#### 来源与时效
- The Rust Programming Language book Ch.1《Getting Started》（安装/rustup、cargo new、cargo build/run/check、hello-cargo）https://doc.rust-lang.org/book/ ；版本声明："This version of the text assumes you're using Rust 1.90.0 (released 2025-09-18) or later with `edition = \"2024\"`"（title-page，核实 2026-08-01）。
- The Cargo Book（Cargo.toml/Cargo.lock 提交策略、[dependencies]、SemVer、profile）https://doc.rust-lang.org/cargo/ 。
- 本机 rustc/cargo 1.94.1 实测：`rustup/rustc/cargo --version`、`cargo new demo` 产物、`cargo run` 输出、默认 `edition = "2024"`（核实 2026-08-01）。
- `⚙演进快·锚版本`：新建工程默认 edition 随工具链推进；the book 锚 1.90.0+edition 2024，本机 1.94.1 实测默认 2024，具体默认以本机 `cargo new` 产物为准，不凭记忆。
- 版本 delta：the book 页面锚 Rust 1.90.0，本机 rustc/cargo 1.94.1，命令行为一致，无冲突项。

## R1.2 猜数游戏综合（the book Ch.2）

### R1.2.1 引入外部依赖并 use 进作用域

the book 第 2 章用一个"猜数字"小游戏，把初学者第一次要用到的核心语言 feature 一次性串起来。第一步就是引入一个第三方 crate `rand` 来生成随机数：在 `Cargo.toml` 的 `[dependencies]` 写上 `rand`，然后在源码里用 `use` 把要用的项引进当前作用域。

```rust
use rand::Rng;
use std::io;
use std::cmp::Ordering;
```

这里同时演示了两类来源：`std::io`、`std::cmp::Ordering` 来自**标准库**（不用写进 Cargo.toml，Rust 自带），`rand::Rng` 来自刚加进依赖的**外部 crate**。`use` 的作用是"起个短名字"——写了 `use std::io;` 之后，后面就能用 `io::stdin()` 而不必每次写全 `std::io::stdin()`。

Rust 的标准库并不会把所有东西都自动塞进你的作用域。只有一小撮最常用的类型/trait（比如 `String`、`Vec`、`Option`、`Result`）在一个叫 **prelude** 的默认导入清单里，不用 `use` 就能直接用；其余的都要显式 `use` 引进来。`rand::Rng` 是一个 trait（能力接口），必须 `use` 进来，你才能对随机数生成器调用它提供的 `gen_range` 之类的方法——这点对刚学 Rust 的人很反直觉，是"方法来自 trait、trait 得先在作用域内"的第一次照面（trait 机制在 R6 展开）。

### R1.2.2 变量绑定、可变性与 String

Rust 里变量默认**不可变**（immutable），用 `let` 绑定；要让它可变必须显式写 `let mut`。猜数游戏里存玩家输入用一个可增长的字符串：

```rust
let mut guess = String::new();
```

`String::new()` 造一个空的、可增长的字符串；因为后面要往里读入内容、会修改它，所以绑定时必须写 `mut`。

"默认不可变"是 Rust 与大多数语言最直接的差别之一，初学者几乎一定会在这里第一次撞墙：写了 `let x = 5;` 然后 `x = 6;` 会编译报错。这不是刁难，而是 Rust 的一贯取向——把"这个值会不会被改"变成一件编译器能看见、能帮你把关的事。默认不可变，意味着凡是加了 `mut` 的地方都是你明确宣告"这里我要改"，读代码的人和编译器都一目了然。

`String` 和字符串字面量 `"..."`（类型是 `&str`）不是一回事。`"hello"` 是编译期就固定、不可增长的字符串切片；`String` 是运行期在堆上分配、可增长可修改的字符串。猜数游戏要不断读入未知长度的输入，所以用 `String`。二者的区别背后是所有权与借用（R2/R3），这里先记住"要可变可增长就用 `String::new()`"。

### R1.2.3 读取标准输入与 Result/expect

读一行控制台输入的标准写法，同时也是初学者第一次遇到 Rust 的错误处理风格：

```rust
io::stdin()
    .read_line(&mut guess)
    .expect("Failed to read line");
```

`read_line(&mut guess)` 把用户输入追加到 `guess` 里，`&mut guess` 是"可变借用"——把 guess 借给这个方法让它往里写，而不是把所有权交出去（借用是 R3 的主题，这里先照抄）。关键在于 `read_line` 返回的是一个 **`Result`** 类型，表示"这个操作可能成功、也可能失败"，Rust 强制你处理这种可能失败。

`.expect("...")` 是处理 `Result` 最粗暴的方式：如果操作成功，取出里面的值继续；如果失败，就让程序 panic（崩溃）并打印你给的这句提示。它不是"优雅"的错误处理，但对入门刚好——它让你先意识到"这里可能失败、你必须表态"。如果你不写 `.expect()`（或别的处理），编译器会给一个警告，提醒你有个 `Result` 没被处理。这就是 Rust "错误不能被默默忽略"的设计在第一课的体现，完整的错误处理（`Result`、`?` 运算符、何时 panic）是大主题 R7 的内容。

`&mut guess` 里的 `&mut` 不能省，也不能写成 `guess`——`read_line` 需要一个可变引用才能往你的字符串里写数据；漏掉会编译不过。这是所有权/借用规则在最简单场景下的第一次亮相。

### R1.2.4 loop、continue、break 循环

要让玩家反复猜直到猜中，游戏用无限循环 `loop` 把猜测逻辑包起来，猜中时用 `break` 跳出：

```rust
loop {
    // ... 读输入、比较 ...
    match guess.cmp(&secret_number) {
        Ordering::Less => println!("Too small!"),
        Ordering::Greater => println!("Too big!"),
        Ordering::Equal => {
            println!("You win!");
            break;
        }
    }
}
```

Rust 有三种循环：`loop`（无条件无限循环，靠 `break` 退出）、`while`（条件循环）、`for`（遍历迭代器）。猜数游戏用 `loop` 是因为"猜几次不确定，猜中才停"这种逻辑最自然地对应"无限循环 + 满足条件时 break"。

Rust 的 `loop` 可以有返回值——`break 表达式;` 能把一个值带出循环，写成 `let x = loop { ... break v; };`。这在别的语言里少见（多数语言循环不返回值）。猜数游戏没用到这个特性，但它体现了 Rust "尽量让一切都是表达式（有值）"的风格。此外 Rust 支持给循环打标签（`'outer: loop { ... break 'outer; }`）来从多层嵌套里精确跳出某一层，这解决了很多语言里"break 只能跳最内层"的痛点。

### R1.2.5 match 与穷尽性、Ordering

比较大小用 `match` 对 `guess.cmp(&secret_number)` 的结果做分支。`cmp` 返回一个枚举 `Ordering`，它只有三个取值：`Less`、`Greater`、`Equal`。`match` 逐一对这三种情况给出处理（见上一节代码）。

`match` 是 Rust 里极其核心的控制结构，可以粗略理解成"超级加强版的 switch"，但有一个 switch 没有的杀手锏：**穷尽性检查（exhaustiveness）**。编译器会检查你有没有覆盖 `Ordering` 的全部三种可能，只要漏了一种（比如忘了写 `Equal`），编译直接报错、不让过。这就把"忘处理某个情况"这类经典 bug 挡在了编译期。

初学者从 C/Java 的 switch 过来，最需要转变的观念就是这个"必须穷尽"。在那些语言里漏一个 case 顶多是运行时逻辑错，编译器不管；在 Rust 里编译器强制你要么列全、要么用通配符 `_ => ...` 兜底。配合枚举（R5 的主题），`match` 让"处理一个值的所有可能形态"变成一件编译器帮你查全的事。`Ordering` 就是标准库给"比较结果"建模的枚举，是初学者第一次见到"用枚举表达一组互斥可能"的例子。match 与模式匹配的完整谱系是大主题 R5。

### R1.2.6 shadowing 与字符串转数字

玩家输入进来是字符串，要和数字比较必须先转成数字。这里同时用到两个 feature——把字符串 parse 成整数，以及用同名变量"遮蔽"（shadowing）旧变量：

```rust
let guess: u32 = guess.trim().parse().expect("Please type a number!");
```

这行里第二个 `guess` 遮蔽了前面那个字符串类型的 `guess`：**shadowing** 允许你用 `let` 重新绑定一个同名变量，新的把旧的"盖住"。于是变量名还叫 `guess`，但类型从 `String` 变成了 `u32`。`trim()` 去掉输入末尾的换行，`parse()` 做字符串到数字的转换。

shadowing 对初学者是个新概念，容易和"可变（mut）"搞混。区别在于：`mut` 是改同一个变量的值、类型不变；shadowing 是用 `let` 造一个全新变量、可以换类型，只是恰好复用了名字。猜数游戏正是利用 shadowing 把"输入的字符串"和"解析出的数字"用同一个名字 `guess`，省掉起两个名字的麻烦，这是 Rust 里非常地道的写法。

注意 `parse()` 也返回 `Result`（输入可能不是合法数字），所以又跟了 `.expect(...)`。类型标注 `: u32` 很关键——正是它告诉 `parse()` 要解析成什么类型的数字，否则编译器不知道该往哪个数字类型转。这也顺带展示了 Rust 局部类型推导的边界：大多数时候类型能自动推出来，但像 `parse()` 这种"目标类型决定行为"的场合，需要你显式标注（局部类型推导是 R6 的内容）。

#### 来源与时效
- The Rust Programming Language book Ch.2《Programming a Guessing Game》（依赖 rand、use、let mut、String::new、io::stdin().read_line、Result/expect、loop/break、match/Ordering、shadowing、parse）https://doc.rust-lang.org/book/ 。
- The Rust Reference（loop 表达式与 break 带值、match 穷尽性、变量遮蔽语义作交叉印证）https://doc.rust-lang.org/reference/ 。
- 本机 rustc/cargo 1.94.1：`cargo add rand`/`cargo run` 可复现该游戏（本报告未逐行贴运行截屏，代码片段取自 the book Ch.2 并与本机编译行为一致）。
- 版本 delta：the book 锚 1.90.0，本机 1.94.1；Ch.2 所用语言 feature 均为长期稳定语法，无 edition/版本差异，无冲突项。

## R1.3 包/crate/模块系统（the book Ch.7）

### R1.3.1 package 与 crate：二进制 crate 与库 crate

Rust 的代码组织有一套四层术语，第 7 章一次讲清：**package（包）→ crate（箱）→ module（模块）→ path（路径）**。最外层是 crate，它是编译的基本单位、"一棵模块树"，编出来要么是一个可执行程序（binary crate），要么是一个供别人链接的库（library crate）。**package** 则是 cargo 层面的概念——一个带 `Cargo.toml` 的工程，它可以包含"多个二进制 crate，外加最多一个库 crate"。

crate 的"根文件"由 cargo 按约定确定，the book Ch.7-01 给的规则逐字为：`src/main.rs` 是与包同名的**二进制 crate 的根**；若包目录里有 `src/lib.rs`，则包含一个与包同名的**库 crate**，`src/lib.rs` 是它的根；一个包若同时有这两个文件，就有两个 crate（一个二进制、一个库，同名）；额外的二进制 crate 放进 `src/bin/` 目录，每个文件是一个独立的二进制 crate。

初学者最容易把 package 和 crate 混为一谈。抓住这个对应：你 `cargo new` 出来的那个工程目录是一个 **package**；它里面那份 `src/main.rs` 编出来的可执行文件是一个 **crate**。多数小工程是"一个 package = 一个 crate"，所以感觉不到区别；但当你想在同一个工程里既提供一个库、又提供几个命令行工具时，"一个 package 装多个 crate"的能力就用上了（库放 `src/lib.rs`，各命令行工具放 `src/bin/*.rs`）。

一条硬约束别记错：一个 package 最多只能有**一个库 crate**，但可以有任意多个二进制 crate。这是 the book 明写的规则，不是可协商的风格。

### R1.3.2 模块树与 mod

一个 crate 内部再用 **模块（module）** 分层组织代码。模块用 `mod` 关键字声明，可以嵌套，从而形成一棵以 crate 根为树根的"模块树"。模块的作用是把相关的类型、函数、常量归组，并控制它们的可见性（谁能看见谁）。

```rust
mod front_of_house {
    mod hosting {
        fn add_to_waitlist() {}
    }
}
```

这段就在 crate 根里声明了一棵小模块树：`front_of_house` 下有 `hosting`，`hosting` 里有函数 `add_to_waitlist`。

初学者要破除一个来自 C/C++ 的直觉：Rust 的模块**不等于文件**。上面整棵树可以全写在一个 `.rs` 文件里；`mod` 是一个逻辑分组声明，不是"包含某个文件"。当然，为了不让文件太长，你可以把模块拆到单独文件——写 `mod hosting;`（带分号、无花括号）时，编译器会去找 `hosting.rs` 或 `hosting/mod.rs` 把内容接进来。所以模块的"逻辑树结构"和"物理文件布局"是两件可以分开的事：树是靠 `mod` 声明搭起来的，文件只是承载它的容器。

这跟 C 的 `#include`（纯文本粘贴）有本质不同，也跟 Python "一个文件一个模块"的强绑定不同。Rust 里是你用 `mod` 显式声明结构，编译器据此去找文件。理解这点，就不会再问"为什么我建了个 .rs 文件它却没被编译"——因为你没在某处 `mod` 它，它就不在模块树里，编译器根本不看它。

### R1.3.3 路径：绝对、相对与 self/super

要引用模块树里某个项（函数、结构体等），得用 **路径（path）** 指明它在树里的位置，就像文件系统里的路径。路径分两种：**绝对路径**从 crate 根开始，以 `crate::` 打头；**相对路径**从当前模块开始，可用 `self`（当前模块）、`super`（父模块）导航。

```rust
mod front_of_house {
    pub mod hosting {
        pub fn add_to_waitlist() {}
    }
}

pub fn eat_at_restaurant() {
    crate::front_of_house::hosting::add_to_waitlist();   // 绝对路径
    front_of_house::hosting::add_to_waitlist();          // 相对路径
}
```

路径的心智模型就是"模块树里的地址"。`crate::` 相当于文件系统里的 `/`（从根开始），`super::` 相当于 `..`（上一级），`self::` 相当于 `./`（当前）。你写 `crate::front_of_house::hosting::add_to_waitlist` 就是"从根出发，进 front_of_house，进 hosting，找 add_to_waitlist"。

初学者常纠结"该用绝对还是相对"。the book 给的经验法则是：更倾向用绝对路径（`crate::` 开头），因为它在你搬动代码位置时更稳定——把一段调用代码挪到别的模块，绝对路径通常不用改，而相对路径可能就断了。`super::` 的典型用途是子模块想调父模块里的东西，比如厨房模块修好订单后要通知前台，用 `super::` 往上找会很自然。

### R1.3.4 可见性：默认私有与 pub

Rust 里模块内的一切**默认是私有的**：子模块能看见祖先模块的东西，但祖先默认看不进子模块内部。要让一个项对外可见，得显式加 `pub`。注意 `pub mod` 和 `pub fn` 要分别加——把模块标 `pub` 只是允许外面"能进这个模块"，模块里的具体函数还得各自标 `pub` 才能被外面调用。

这套"默认私有、显式公开"的规则，目的是让**封装**成为默认状态。你新写的东西默认只有自己模块内部能用，只有当你明确决定"这个要作为对外接口"时才加 `pub`。好处是你能大胆重构任何没标 `pub` 的内部实现而不怕影响外部——因为外部本来就够不着它。这跟很多语言"默认 public、要私有才特意标"的取向相反，Rust 选了更保守、更利于长期维护的那一侧。

初学者最常踩的坑，正是"我把模块标了 pub，怎么里面的函数还是调不到"——因为可见性是逐层、逐项的：外部要调 `front_of_house::hosting::add_to_waitlist`，需要 `hosting` 是 `pub mod`、`add_to_waitlist` 是 `pub fn`，缺一层就被挡。此外结构体和它的字段可见性也是分开的：`pub struct` 让类型可见，但字段默认仍私有，要逐个字段加 `pub` 才对外可见；而 `pub enum` 则连同它的所有变体一起公开。

### R1.3.5 use：引入路径、重导出与 as

每次都写全长路径很啰嗦，`use` 关键字把一个路径引进当前作用域、起个短名字，之后就能用短名调用。

```rust
use crate::front_of_house::hosting;

pub fn eat_at_restaurant() {
    hosting::add_to_waitlist();
}
```

`use crate::front_of_house::hosting;` 之后，本作用域里 `hosting` 就等价于那条长路径，调用直接写 `hosting::add_to_waitlist()`。

Rust 社区约定，`use` 函数时通常只引到它的**父模块**（写 `use ...::hosting;` 然后 `hosting::add_to_waitlist()`），而不是把函数本身引进来。这样调用点保留 `hosting::` 前缀，一眼能看出这函数不是本地定义的。反过来，引入结构体、枚举等类型时，惯例是直接引到该类型本身（`use std::collections::HashMap;` 然后直接写 `HashMap`）。名字冲突时用 `as` 起别名（`use std::io::Result as IoResult;`）。

两个进阶但常用的点。其一，`pub use` 是**重导出（re-export）**：它把一个引进来的名字再次公开，让外部可以从你这里访问它。这是设计库对外 API 的利器——你可以把内部深层模块里的类型，通过在 `lib.rs` 里 `pub use` 提到顶层，让用户用更短、更稳定的路径访问，从而把"对外 API 的样子"和"内部模块怎么分层"解耦开（the book Ch.14 专门讲这个技巧）。其二，引入同一模块下多个项可用嵌套写法 `use std::io::{self, Write};` 或通配 `use std::collections::*;`（通配一般只在测试或 prelude 场景用，日常少用以免污染命名空间）。

#### 来源与时效
- The Rust Programming Language book Ch.7《Managing Growing Projects with Packages, Crates, and Modules》，含 Ch.7-01（package/crate 关系与 crate 根约定）、Ch.7-02（模块树/mod）、Ch.7-03（路径/pub/super）、Ch.7-04（use/as/pub use）https://doc.rust-lang.org/book/ 。crate 根规则逐字引 Ch.7-01："Cargo follows a convention that _src/main.rs_ is the crate root of a binary crate…"、"…if the package directory contains _src/lib.rs_, the package contains a library crate…"（核实 2026-08-01）。
- The Rust Reference（items/modules、paths、visibility & privacy）https://doc.rust-lang.org/reference/ ，与 the book 表述一致，作可见性/路径语义交叉印证。
- 版本 delta：the book 锚 1.90.0，本机 1.94.1；模块系统语义长期稳定，无 edition 相关行为差异，无冲突项。

## R1.4 自动化测试（the book Ch.11）

### R1.4.1 #[test] 属性与断言宏

Rust 把测试直接内建进语言和工具链：任何函数只要标上 **`#[test]`** 属性，就成为一个测试函数，`cargo test` 会自动找到并运行它。测试体里用**断言宏**检查预期：`assert!(条件)`（条件为假则失败）、`assert_eq!(a, b)`（不相等则失败并打印两边的值）、`assert_ne!(a, b)`（相等则失败）。

```rust
#[test]
fn adds() {
    assert_eq!(2 + 2, 4);
}
```

一个测试函数怎么算"通过"？规则很简单：函数正常返回即通过，函数内发生 **panic**（崩溃）即失败。断言宏的本质就是"条件不满足时 panic"，所以它们是失败测试的常见触发点。

Rust 不需要引入任何第三方测试框架（不像很多语言要装 JUnit/pytest 之类），测试是"一等公民"，写测试和写普通函数一样，只多一个 `#[test]` 标注。`assert_eq!` 比裸 `assert!(a == b)` 更好用，因为它失败时会把 `a` 和 `b` 的实际值都打出来，方便定位；这要求被比较的值能被打印和比较（实现了相应 trait，初学阶段内置类型都满足，无需操心）。此外可以给断言加自定义失败信息：`assert!(cond, "x was {}", x)`。

### R1.4.2 单元测试：#[cfg(test)] mod tests

**单元测试**测的是小单元（单个函数/模块）的内部逻辑，Rust 的惯例是把它们和被测代码放在**同一个文件**里，包在一个标了 `#[cfg(test)]` 的 `tests` 模块中：

```rust
pub fn add(a: i64, b: i64) -> i64 {
    a + b
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn adds() {
        assert_eq!(add(2, 2), 4);
    }
}
```

`#[cfg(test)]` 是关键。the book 逐字说明它让 Rust "只在你运行 `cargo test` 时编译并运行这段测试代码，运行 `cargo build` 时不编译"。也就是说测试代码不会进你的发布产物，不占最终二进制体积、也不拖慢正常构建。

初学者两个要点。其一，`use super::*;`——测试模块是被测模块的子模块，写这行把父模块里的所有项（包括上面的 `add`）引进测试作用域，才能直接调 `add(...)`。其二，正因为测试模块就嵌在被测代码内部、是它的子模块，**单元测试能访问私有函数和私有字段**（回顾 R1.3.4：子模块能看见祖先的私有项）。这是单元测试相对集成测试的一大优势——你能直接测那些不对外公开的内部实现。

### R1.4.3 集成测试：tests/ 目录

**集成测试**从外部、以库使用者的视角测你的公开 API。约定是：在工程根目录（和 `src/` 平级）建一个 **`tests/` 目录**，里面每个 `.rs` 文件都是一个独立的集成测试 crate。

```rust
// tests/integration.rs
use demo::add;

#[test]
fn adds_from_outside() {
    assert_eq!(add(10, 20), 30);
}
```

the book 逐字说明 cargo 对这个目录的特殊处理："Cargo treats the `tests` directory specially and compiles files in this directory only when we run `cargo test`."（cargo 特殊对待 tests 目录，仅在 `cargo test` 时才编译其中文件。）并且集成测试里的函数**不需要**再标 `#[cfg(test)]`——因为整个目录已经只在测试时编译。

集成测试和单元测试的分工，是初学者必须分清的：单元测试在 `src/` 里、是被测模块的子模块、能碰私有实现，测"内部逻辑对不对"；集成测试在 `tests/` 里、是完全独立的外部 crate、**只能调你的公开 API**（`pub` 的那些），测"别人用你的库时接口对不对、各部分连起来对不对"。正因为它是外部 crate，它得像真实用户那样 `use demo::add;` 通过 crate 名引入——所以集成测试只对**库 crate**（有 `src/lib.rs`）有意义，纯二进制 crate 没有可供外部 `use` 的公开 API。

`tests/` 目录下每个文件是独立 crate，想在多个集成测试文件间共享辅助代码，不能直接建 `tests/common.rs`（那会被当成又一个测试 crate 单独编译、还会显示成一个空测试集），要建 `tests/common/mod.rs`——用子模块的老式文件命名规避这个特殊处理。这是 the book 专门提醒的坑。

### R1.4.4 cargo test：运行、过滤与控制

`cargo test` 一条命令编译并运行工程里所有测试——单元测试、集成测试、以及文档测试（下一节）。本机在一个带库和集成测试的工程里实跑，输出如下（节选）：

```text
$ cargo test
   Compiling demo v0.1.0 (.../demo)
    Finished `test` profile [unoptimized + debuginfo] target(s) in 0.47s
     Running unittests src/lib.rs (target/debug/deps/demo-7f33b6e559525f28)

running 1 test
test tests::adds ... ok

test result: ok. 1 passed; 0 failed; 0 ignored; 0 measured; 0 filtered out; finished in 0.00s

     Running unittests src/main.rs (...)
running 0 tests
test result: ok. 0 passed; 0 failed; 0 ignored; 0 measured; 0 filtered out; ...

   Doc-tests demo
running 1 test
test src/lib.rs - add (line 5) ... ok
test result: ok. 1 passed; 0 failed; 0 ignored; 0 measured; 0 filtered out; ...
```

从这份真实输出能读出 cargo test 的组织方式：它分几段跑——`src/lib.rs` 的单元测试、`src/main.rs` 的单元测试、以及 `Doc-tests`（文档测试）；集成测试若存在会另起一段 `Running tests/xxx.rs`。每段末尾给统计：passed / failed / ignored / measured / filtered out。

`cargo test 名字` 按名字**子串过滤**只跑匹配的测试（如 `cargo test add` 跑所有名字含 add 的）；`cargo test -- --show-output` 显示测试里 `println!` 的输出（默认成功的测试会吞掉打印）；`cargo test -- --test-threads=1` 让测试串行跑（默认并行，共享状态的测试可能需要串行）。注意 `--` 的分界作用：`--` 之前的参数给 cargo，之后的给测试运行器（test harness）。此外可用 `#[ignore]` 标注默认跳过某个耗时测试（`cargo test -- --ignored` 才跑它们），用 `#[should_panic]` 断言某测试就应该 panic。

`cargo test` 用的是独立的 `test` profile（输出里的 `test profile [unoptimized + debuginfo]`），默认不开优化、带调试信息，跟 dev 类似，测试跑得快、报错信息全。

### R1.4.5 文档测试（doctest）

Rust 有一个很有特色的机制：**文档注释里的代码示例会被当成测试来运行**。你在 `///` 文档注释里用 ``` 代码块写的示例，`cargo test` 会真的编译并执行它们。

```rust
/// Adds two numbers.
///
/// # Examples
///
/// ```
/// assert_eq!(demo::add(2, 3), 5);
/// ```
pub fn add(a: i64, b: i64) -> i64 {
    a + b
}
```

上一节的实跑输出里那段 `Doc-tests demo / test src/lib.rs - add (line 5) ... ok` 就是这个文档示例被当测试跑通了。the book Ch.14 逐字点明这个"额外福利"："Running `cargo test` will run the code examples in your documentation as tests!"

这个设计解决了一个普遍痛点：文档里的示例代码常年不更新、跟真实 API 脱节、复制过去根本编不过。Rust 直接把示例纳入测试——一旦你改了 API 让示例失效，`cargo test` 就会失败，逼你同步更新文档。于是文档示例天然保持"永远可编译、永远和代码一致"。对初学者这也是个好习惯的起点：给公开函数写个 `# Examples` 小例子，既是文档又是测试，一举两得。

要注意文档测试是以"外部用户"的身份运行的，所以示例里要用 crate 名访问（这里 `demo::add`），跟集成测试一样只能用公开 API。想写一段展示但不实际运行的示例，可给代码块标注 `ignore`/`no_run`/`compile_fail` 等。

#### 来源与时效
- The Rust Programming Language book Ch.11《Writing Automated Tests》：Ch.11-01（#[test]、assert 宏、should_panic、自定义信息）、Ch.11-02（cargo test 运行/过滤/--test-threads/--show-output/#[ignore]）、Ch.11-03（单元测试 #[cfg(test)] mod tests、集成测试 tests/ 目录、common/mod.rs）https://doc.rust-lang.org/book/ 。逐字引 Ch.11-03："Cargo treats the `tests` directory specially and compiles files in this directory only when we run `cargo test`."（核实 2026-08-01）
- The Rust Programming Language book Ch.14-02（文档测试）逐字引："Running `cargo test` will run the code examples in your documentation as tests!"（核实 2026-08-01）
- 本机 rustc/cargo 1.94.1 实测：建 `src/lib.rs`（含单元测试 + doctest）+ `tests/integration.rs`，`cargo test` 真跑，输出如上（单元/集成/doc 三段、passed/failed/ignored/measured/filtered 统计、`test profile`）——核实 2026-08-01。
- 版本 delta：the book 锚 1.90.0，本机 1.94.1；测试框架与命令行为一致，无冲突项。

## R1.5 cargo 进阶与文档（the book Ch.14）

### R1.5.1 profile：dev 与 release 的构建配置

**profile（构建配置）** 是 cargo 预设的一组编译选项集合，决定优化级别、调试信息等。cargo 内置四个：**dev**（`cargo build`/`run` 默认，不优化、带调试信息、编译快）、**release**（`cargo build --release` 用，开优化、编译慢、跑得快）、以及测试用的 **test** 和文档测试用的 **bench** 相关配置。你可在 `Cargo.toml` 里覆盖它们的参数：

```toml
[profile.dev]
opt-level = 0      # dev 默认不优化

[profile.release]
opt-level = 3      # release 默认最高优化
```

本机实跑 `cargo build --release` 的输出印证了配置切换：

```text
$ cargo build --release
   Compiling demo v0.1.0 (.../demo)
    Finished `release` profile [optimized] target(s) in 0.15s
```

注意 `release` profile `[optimized]`（对比 dev 的 `[unoptimized + debuginfo]`），产物进 `target/release/` 而非 `target/debug/`。

平时开发一律用默认（dev），编译快、报错信息全、能上调试器；要测真实性能或交付发布时才 `--release`。二者性能可能差一个数量级——Rust 的很多零成本抽象（迭代器、泛型等）正是靠优化才"零成本"，用 dev 构建去评判 Rust 性能是常见误区。`opt-level` 的取值 `0/1/2/3`（越大优化越激进）和 `"s"/"z"`（优化体积）是最常调的旋钮，dev 默认 0、release 默认 3。

### R1.5.2 rustdoc：文档注释与 cargo doc

Rust 自带文档生成器 **rustdoc**，通过 `cargo doc` 调用。你用 **`///`** 写"文档注释"（记录它下面紧跟的那个项）、用 **`//!`** 写"内部文档注释"（记录包含它的那个项，常放在 `lib.rs` 顶部写整个 crate 的介绍），内容支持 Markdown。`cargo doc` 把这些注释渲染成一套 HTML 网站，放进 `target/doc/`；`cargo doc --open` 生成后直接在浏览器打开。本机实跑：

```text
$ cargo doc --no-deps
 Documenting demo v0.1.0 (.../demo)
    Finished `dev` profile [unoptimized + debuginfo] target(s) in 1.18s
   Generated .../demo/target/doc/demo/index.html
$ ls target/doc/demo/
all.html  fn.add.html  index.html  sidebar-items.js
```

生成的 `fn.add.html` 就是上一节那个 `add` 函数的文档页。

对初学者，rustdoc 的价值在于"文档即代码的一部分"：你写库时顺手用 `///` 记录每个公开项，一条 `cargo doc` 就得到和标准库文档（doc.rust-lang.org 上那些）长得一模一样、可点击跳转、可搜索的专业文档站。`///` 与 `//!` 的区别是初学者的高频疑问——记法："三斜杠管下面（跟在后面的项），叹号斜杠管外面（包住它的项）"。惯例上文档注释里常带 `# Examples`、`# Panics`、`# Errors`、`# Safety` 这些约定小节标题，rustdoc 会把它们渲染成醒目分节。而且别忘了 `# Examples` 里的代码块会被 `cargo test` 当 doctest 跑（见 R1.4.5）——文档、示例、测试在 Rust 里是同一件事的三个面。

### R1.5.3 workspace：多包协作

**workspace（工作空间）** 让多个相关的 package 共享同一套构建。做法是在顶层放一个只含 `[workspace]` 段的 `Cargo.toml`，用 `members` 列出成员包：

```toml
[workspace]
resolver = "3"
members = ["adder", "add_one"]
```

the book Ch.14-03 明确 workspace 的关键特征：所有成员共享**一个** `Cargo.lock`（在顶层，不是每包一个）和**一个**顶层 `target/` 输出目录。成员之间互相依赖用 **path 依赖**，如 `adder/Cargo.toml` 里写 `add_one = { path = "../add_one" }`。

理解 workspace 要抓住"共享"二字。当你的项目大到要拆成好几个协同开发的 crate（比如一个核心库 + 几个基于它的工具），workspace 让它们：用同一份 Cargo.lock，保证所有成员依赖的第三方库版本完全一致、不会各用各的版本打架；共用一个 target 目录，公共依赖只编译一次，避免每个成员各自重编、显著省时间省磁盘。the book 逐字点了这个好处——共享 target 目录让各 crate "avoid unnecessary rebuilding"（避免不必要的重复构建）。

⚙演进快·锚版本（核实 2026-08-01）：the book 当前示例的 workspace `Cargo.toml` 里写了 `resolver = "3"`——resolver（依赖解析器）版本随 edition 演进（edition 2024 对应 resolver 3）。resolver 具体默认值与你的 edition/工具链绑定、会随版本推进，请以官方文档和你本机产物为准，不凭记忆断言某个数字。初学者层面知道"workspace 顶层可能要显式声明 resolver、其值跟着 edition 走"即可。

workspace 顶层那个 `Cargo.toml` 通常**没有** `[package]` 段（它不是一个包，只是个容器），只有 `[workspace]`。在 workspace 里跑 `cargo build`/`test` 默认作用于全体成员，用 `-p 包名`（如 `cargo test -p add_one`）可只对某个成员操作。

### R1.5.4 发布到 crates.io：cargo publish 与 yank

要把自己的库分享给全世界，发布到官方中央仓库 **crates.io**。流程是：先 `cargo login <token>`（token 从 crates.io 账户设置里拿，一次即可）；在 `Cargo.toml` 补齐必需元数据（`name`、`version`、`description`、`license` 等，缺了 `cargo publish` 会拒绝）；然后 `cargo publish` 上传。发布后**版本是永久的、不可删除**；要发新版就改 `Cargo.toml` 里的 `version` 再 `cargo publish`。

版本永久不可删这条，初学者一定要重视——crates.io 这么设计是为了保证"别人依赖了你的某版本后，那个版本永远还在、构建永远可复现"。这也意味着你不能靠"删掉发错的版本"来补救。补救手段是 **yank（撤回）**：`cargo yank --vers X.Y.Z` 把某版本标记为"不许再有新工程依赖它"，但**已经在用它的工程不受影响**（它没被删、还能下载）。yank 用于"这版有严重 bug/安全问题，别让新人再踩"，而不是删除。`cargo yank --vers X.Y.Z --undo` 可撤销 yank。

配套一个 R1.3.5 提过的设计技巧：发布库前常用 `pub use` 把深层模块里的重要类型重导出到 crate 顶层，让用户用 `你的库::类型` 这种短路径访问，把对外 API 和内部模块结构解耦——这样你日后重排内部模块不会破坏用户代码。the book Ch.14-02 把它作为"打磨对外 API"的要点专门讲。

### R1.5.5 cargo install 与扩展 cargo

`cargo install` 用来从 crates.io 安装**二进制** crate（命令行工具），把它编译好放进 `~/.cargo/bin`（该目录一般已在 PATH 里），之后就能像系统命令一样直接用。它装的是可执行工具，不是给你工程当依赖的库——这点初学者容易混：想给工程加库依赖用 `cargo add`/改 Cargo.toml，想装一个命令行工具（如 `ripgrep`、`cargo-edit`）才用 `cargo install`。

cargo 还能被**子命令扩展**：如果 PATH 里有个名为 `cargo-something` 的可执行文件，你就能用 `cargo something` 来调它，就像它是 cargo 内置命令一样。很多社区工具靠这个机制融入 cargo 工作流——比如装了 `cargo-edit` 后就有了 `cargo add`/`cargo rm`（`cargo add` 现已内置），装了别的扩展能得到 `cargo audit`（查依赖安全漏洞）、`cargo watch`（改动即重跑）等。`cargo --list` 能列出当前可用的全部子命令（含扩展）。

cargo 不是一个封闭的固定命令集，而是一个可被生态扩展的平台。你日常会先掌握内置的 `new/build/run/check/test/doc/add/publish/install`，随着深入再按需装扩展。它体现了 Rust 工具链"核心精简、生态补足"的一贯风格。

#### 来源与时效
- The Rust Programming Language book Ch.14《More about Cargo and Crates.io》：Ch.14-01（profile：[profile.dev]/[profile.release]、opt-level 默认 0/3）、Ch.14-02（/// 与 //! 文档注释、常用小节、cargo doc→target/doc、doctest、pub use 重导出、crates.io 元数据/cargo login/cargo publish/版本永久/cargo yank）、Ch.14-03（workspace：[workspace]/members、共享 Cargo.lock 与 target、path 依赖）、Ch.14-04（cargo install）、Ch.14-05（自定义 cargo-xxx 子命令）https://doc.rust-lang.org/book/ 。逐字引 Ch.14-03 共享 target "avoid unnecessary rebuilding"；workspace 示例含 `resolver = "3"`（核实 2026-08-01）。
- The Cargo Book（profile 参数细节、cargo publish/yank、cargo install、resolver 版本与 edition 对应）https://doc.rust-lang.org/cargo/ 。
- 本机 rustc/cargo 1.94.1 实测：`cargo build --release` 输出 `release profile [optimized]`；`cargo doc --no-deps` 生成 `target/doc/demo/`（含 index.html、fn.add.html 等）——核实 2026-08-01。
- `⚙演进快·锚版本`：workspace `resolver` 默认值随 edition/工具链演进（edition 2024↔resolver 3），具体默认以官方文档与本机产物为准，不凭记忆；the book 锚 1.90.0+edition 2024，本机 1.94.1，命令行为一致，无冲突项。
