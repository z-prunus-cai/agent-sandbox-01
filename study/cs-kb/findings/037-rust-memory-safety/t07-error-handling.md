# L5-14·大主题R7 错误处理

> 基线 Py3.11.15/np2.4.6/gcc13.3 @2026-07-25（+ rustc/cargo 1.94.1）｜核实日期：2026-08-01｜先修：R2 所有权与 move、R5 代数数据类型与模式匹配（enum/`Option`/`match`）、R6 泛型与 trait（`From`/trait bound）｜一手锚点：The Rust Programming Language book Ch.9「Error Handling」@对齐 rustc 1.90.0 + edition 2024、The Rust Reference §「Panic」「The question mark operator」@活文档、`std::result` / `std::option` 标准库文档、本机 rustc 1.94.1 实测｜成熟度：GA（`panic!`/`Result`/`?` 自 Rust 1.0 起稳定，`?` 于 1.13 引入、`main` 返回 `Result` 于 1.26 稳定）；第三方错误库 thiserror/anyhow ⚙演进快·外部 crate·版本随时变

Rust 没有异常（exception）机制。它把错误分成两类，用两套完全不同的工具处理：不可恢复的错误（程序进入了不该发生、无法合理继续的状态）用 `panic!` 让线程崩溃；可恢复的错误（文件不存在、字符串解析失败这类调用方能预期并想处理的情况）用返回值 `Result<T, E>` 显式表达，逼调用方在类型层面正视它。这一章把 `panic!` 的两种收场方式、`Result` 的建模与消费、`?` 运算符的传播机制、以及"到底该 panic 还是返回 Result"的判断准则讲到初学者能看懂、能改错为止。

本报告所有 rustc 编译结果与运行输出均为本机 rustc 1.94.1 现跑（`--edition 2024`），复现命令随例给出；退出码、报错码、backtrace 文本以本机输出为准，不凭记忆。

## R7.1 panic! 与不可恢复错误

### panic! 宏与不可恢复错误

`panic!` 是一个宏，调用它会让当前线程立刻进入"崩溃"流程：打印一条错误消息（含 panic 发生的源码文件与行列），然后开始收尾并终止线程。它表达的语义是"程序遇到了一个 bug 层面的、无法合理往下走的状况"，而不是一个可以被调用方兜住的普通失败。

panic 不是给业务逻辑用的错误返回，而是"这里出问题说明代码或前置条件本身错了"。除了显式写 `panic!("...")`，很多标准库操作会在越界或非法状态时替你触发 panic——比如数组/`Vec` 索引越界、对 `None` 调 `unwrap`、debug 构建下的整数溢出。下面直接调 `panic!`。

```rust
fn main() {
    panic!("boom");
}
```

本机 `rustc --edition 2024 abort_demo.rs && ./abort_demo` 的输出（已清除环境变量 `RUST_BACKTRACE`）为：

```text
thread 'main' (16336) panicked at abort_demo.rs:2:5:
boom
note: run with `RUST_BACKTRACE=1` environment variable to display a backtrace
```

进程退出码是 101（不是 0，也不是常见的 1），这是 Rust 默认 panic 收场约定的退出码。消息里的 `abort_demo.rs:2:5` 精确指到 `panic!` 那一行那一列，是排查 panic 的第一手线索。

### 索引越界等标准库触发的 panic

很多不写 `panic!` 也会 panic 的场景来自标准库内部的安全检查。最典型的是切片/`Vec` 索引越界：Rust 不会像 C 那样读到越界内存造成未定义行为，而是在运行期检测到越界后 panic，把一次潜在的内存安全漏洞降级成一次可控的线程崩溃。

```rust
fn main() {
    let v = vec![1, 2, 3];
    println!("{}", v[99]);
}
```

本机运行输出：

```text
thread 'main' (15960) panicked at panic_demo.rs:3:21:
index out of bounds: the len is 3 but the index is 99
note: run with `RUST_BACKTRACE=1` environment variable to display a backtrace
```

这正是 Rust "内存安全"承诺的一部分：越界访问在安全代码里绝不会静默读写非法内存，代价是一次带明确消息的 panic。同类还有除以零、对 `None` 调 `.unwrap()`、debug 模式下整数运算溢出等——它们都走同一条 panic 通道。

### 栈展开（unwind）与直接中止（abort）

panic 发生后有两种收场方式。默认是栈展开（unwinding）：Rust 沿调用栈逐层往回走，对每一层里已创建的局部变量运行析构函数（`Drop`），把资源正常释放掉，然后结束线程。另一种是直接中止（abort）：不做任何清理，立刻把整个进程停掉，收尾工作交给操作系统。

两者的取舍是"干净"与"小而快"之间的权衡。unwind 会保证 RAII 析构被执行（呼应 R2 的 Drop），但为此编译器要为每个函数生成展开用的额外代码，二进制更大、也更复杂；abort 省掉这套机制，二进制更小、行为更简单，代价是 panic 时不跑析构。库作者若希望 panic 一定不泄漏资源，一般依赖默认的 unwind；追求极小体积的嵌入式或对 panic 零容忍的场景常切到 abort。

同一段 `panic!("boom")`，默认（unwind）退出码 101；`rustc -C panic=abort` 编译后运行则被信号 `SIGABRT` 终止，shell 报 `Aborted`，退出码 134（即 128 + 6，6 是 SIGABRT）。这直观说明 abort 是把控制权直接丢给操作系统的"硬"终止。

### panic= 设置与 RUST_BACKTRACE

选择 unwind 还是 abort 的开关叫 `panic`。在 Cargo 工程里通过 `Cargo.toml` 的 profile 段设置，对某个构建剖面全局生效：

```toml
[profile.release]
panic = "abort"
```

直接用 rustc 时对应 `-C panic=abort`（本机实测见上）。取值为 `"unwind"`（默认）或 `"abort"`。The book Ch.9 明确默认是 unwind，切 abort 主要为减小产物体积。dev 与 release 两个剖面各自独立设置。

看不到调用栈时，设环境变量 `RUST_BACKTRACE=1` 再运行，就会在 panic 消息后打印栈回溯（backtrace），逐帧列出 panic 一路是怎么被调到的；`RUST_BACKTRACE=full` 打印更详尽版本。本机把上面的越界例子加 `RUST_BACKTRACE=1` 运行，能看到 `panic_bounds_check` → `SliceIndex::index` → `Vec as Index::index` → `main` 的完整链条，直接定位到是哪一步索引越了界。要注意 backtrace 需要调试信息且默认关闭（靠环境变量按需打开），它是调试手段，不影响程序逻辑。

#### 来源与时效

- The Rust Programming Language book Ch.9「Error Handling」§「Unrecoverable Errors with panic!」https://doc.rust-lang.org/book/ch09-01-unrecoverable-errors-with-panic.html （对齐 rustc 1.90.0 + edition 2024；⚙演进快·锚版本·随时变，核实 2026-08-01）
- The Rust Reference §「Behavior considered undefined」相邻的 panic/unwinding 说明、Cargo Book §「Profiles · panic」https://doc.rust-lang.org/cargo/reference/profiles.html#panic （核实 2026-08-01）
- 本机 rustc 1.94.1 实测：默认 unwind panic 退出码 101、`note: run with RUST_BACKTRACE=1`；`-C panic=abort` 触发 SIGABRT、退出码 134；`RUST_BACKTRACE=1` 打印含 `panic_bounds_check` 的回溯。
- 无来源冲突。退出码 101 与"默认 unwind、abort 需显式开"两点由本机实测坐实，未凭记忆。

## R7.2 Result 与可恢复错误

### Result<T, E> 枚举

对于调用方可以预期、也应该处理的失败，Rust 用标准库枚举 `Result<T, E>` 表示。它只有两个变体：成功时是 `Ok(T)` 装着结果值，失败时是 `Err(E)` 装着错误值。它的定义就是一个普通的和类型（呼应 R5 的 enum）：

```rust
enum Result<T, E> {
    Ok(T),
    Err(E),
}
```

`Result` 是 Rust 处理可恢复错误的中枢。凡是可能失败又希望调用方接手的函数，签名就返回 `Result<成功类型, 错误类型>`，把"可能失败"这件事写进类型里——调用方拿到的不是裸值，而是一个"要么成功要么失败"的盒子，必须先拆开才能用。这与"抛异常然后可能忘了 catch"形成对比：错误在这里是普通的返回值，编译器能一路追踪它。

### 用 match 处理 Result

最基础、最显式的消费方式是 `match`，两个变体都得给出处理分支，编译器的穷尽性检查（R5.3）保证你不会漏掉 `Err`。

```rust
use std::fs::File;

fn main() {
    let f = File::open("hello.txt");
    match f {
        Ok(file) => println!("opened: {file:?}"),
        Err(e) => println!("failed to open: {e}"),
    }
}
```

`match` 的价值在于逼你正面回答"失败了怎么办"。初学者常见的进阶是对 `Err` 再按错误种类细分——例如文件不存在就尝试创建、其它错误才放弃——用嵌套 `match` 或对 `e.kind()` 匹配即可。缺点是样板代码多，所以标准库提供了下面一批便捷方法作为 `match` 的浓缩写法。

### unwrap / expect 与其它便捷方法

`Result` 上有一组方法，把"成功取值、失败按某策略处理"的常见 `match` 压成一行。`unwrap()` 成功时返回里面的值，失败时直接 panic；`expect("msg")` 与 `unwrap` 相同，但 panic 消息换成你给的 `msg`，便于定位是哪一处出错。

```rust
use std::fs::File;

fn main() {
    // 成功则拿到 File，失败则 panic 并打印这条自定义消息
    let _f = File::open("hello.txt")
        .expect("hello.txt should be present in project root");
}
```

不想 panic、想给个兜底值或兜底逻辑时，用 `unwrap_or(default)`（失败时返回 `default`）、`unwrap_or_else(|e| ...)`（失败时用闭包按错误算一个值）、`unwrap_or_default()`（失败时返回该类型的默认值）。还有 `map` / `map_err` 变换成功值或错误值、`and_then` 串接下一个可能失败的步骤。这些都是不改变"要么值要么错误"语义、只改变处理策略的组合子。教学上优先用 `expect` 而非 `unwrap`，因为 `expect` 的消息能说明"你当时凭什么认为这里不会失败"。

### Result 不该被默默忽略

`Result` 被标了 `#[must_use]`：如果你调用一个返回 `Result` 的函数却对返回值不做任何处理（既不 `match`、也不 `?`、也不 `let _ =`），编译器会给出 `unused_result` 类的警告，提醒你可能漏处理了一个错误。

这是 Rust 把"错误不能被静默吞掉"做进工具链的一环。它是警告（`warning`）而非硬错误，默认不会阻止编译，但强烈建议不要压掉——绝大多数漏处理 `Result` 都是 bug 的温床。想明确表达"我确实故意不管这个结果"，用 `let _ = some_call();` 显式丢弃，警告即消失。

### Option 与 Result 的互转

`Option<T>`（`Some`/`None`）表达"有没有值"，`Result<T, E>` 表达"成功还是带原因的失败"，两者常需互转。`Option` 的 `ok_or(err)` / `ok_or_else(|| err)` 把 `None` 变成 `Err(err)`、`Some(v)` 变成 `Ok(v)`；`Result` 的 `.ok()` 把 `Ok(v)` 变成 `Some(v)`、把 `Err(_)` 丢弃成 `None`。

初学者容易把这两个类型混用。记住区别：缺值本身不算"错误"（查字典没这个词是正常结果）就用 `Option`；失败需要携带原因供调用方区分处理就用 `Result`。当一个"没有值"的情况需要升级成"带原因的错误"以便用 `?` 往上抛时，`ok_or` 就是桥梁。

#### 来源与时效

- The Rust Programming Language book Ch.9 §「Recoverable Errors with Result」https://doc.rust-lang.org/book/ch09-02-recoverable-errors-with-result.html （对齐 rustc 1.90.0 + edition 2024；⚙演进快·锚版本·随时变，核实 2026-08-01）
- 标准库文档 `std::result::Result`（含 `#[must_use]` 标注与 `unwrap`/`expect`/`unwrap_or*`/`map`/`map_err`/`and_then`/`ok` 方法）、`std::option::Option`（`ok_or`/`ok_or_else`）https://doc.rust-lang.org/std/result/enum.Result.html （核实 2026-08-01）
- 本机 rustc 1.94.1 实测：`match`/`expect` 示例编译运行正常。
- 无来源冲突。`#[must_use]` 触发的是警告而非错误，两来源一致。

## R7.3 ? 运算符与传播

### ? 运算符：把错误提前返回给调用方

写业务代码时常见的模式是"这一步失败了就别往下走，直接把错误交给上层"。手写就是 `match`：`Ok` 取值继续、`Err` 立刻 `return`。`?` 运算符是这段模式的语法糖——把它加在一个返回 `Result` 的表达式后面，成功时它取出 `Ok` 里的值让表达式继续，失败时它就地把 `Err` 作为整个函数的返回值提前抛出。

```rust
use std::num::ParseIntError;

fn double(s: &str) -> Result<i32, ParseIntError> {
    let n: i32 = s.parse()?;   // 解析失败则在此处 return Err(...)
    Ok(n * 2)
}

fn main() {
    println!("{:?}", double("21"));   // Ok(42)
    println!("{:?}", double("xx"));   // Err(ParseIntError { kind: InvalidDigit })
}
```

本机 `rustc --edition 2024 qmark.rs && ./qmark` 输出正是上面注释里的 `Ok(42)` 与 `Err(ParseIntError { kind: InvalidDigit })`。`?` 让"传播错误"这件本该冗长的事变成一个字符，是 Rust 错误处理读起来清爽的关键。它可以连着链式调用，多步都可能失败时一行内串起来。

### From 转换：? 自动统一错误类型

`?` 不只是"抛出去"，它在抛之前会做一次类型转换：如果被传播的错误类型 `E1` 和当前函数声明的错误类型 `E2` 不同，`?` 会自动调用 `E2::from(e1)`（要求存在 `impl From<E1> for E2`，呼应 R6 的 trait）把它转成函数需要的错误类型。这让一个函数内部可以调用多个返回不同错误类型的操作，只要都能 `From` 成本函数统一的错误类型，就都能用 `?` 一把传播。

```rust
#[derive(Debug)]
enum MyError {
    Parse(std::num::ParseIntError),
    TooBig,
}

impl From<std::num::ParseIntError> for MyError {
    fn from(e: std::num::ParseIntError) -> Self {
        MyError::Parse(e)          // 定义 ParseIntError -> MyError 的转换
    }
}

fn parse_small(s: &str) -> Result<i32, MyError> {
    let n: i32 = s.parse()?;       // ParseIntError 被 ? 自动 From 成 MyError
    if n > 100 {
        return Err(MyError::TooBig);
    }
    Ok(n)
}
```

本机运行 `parse_small("50")`→`Ok(50)`、`parse_small("nope")`→`Err(Parse(ParseIntError { kind: InvalidDigit }))`、`parse_small("999")`→`Err(TooBig)`，三种路径都符合预期。这条 `From` 自动转换正是自定义错误类型（下一章）能优雅工作的机制底座：你为各底层错误实现到自家错误枚举的 `From`，函数体里就能通篇用 `?`。

### ? 也用于 Option

`?` 不限于 `Result`。用在返回 `Option` 的表达式上时，`Some(v)` 取出 `v` 继续，`None` 则让整个函数提前返回 `None`。这要求所在函数本身也返回 `Option`（或其它实现了对应 `FromResidual` 的类型）。

```rust
fn first_char_upper(s: &str) -> Option<char> {
    let c = s.chars().next()?;   // 空串则在此 return None
    Some(c.to_ascii_uppercase())
}
```

初学者的易错点是把 `?` 在 `Result` 与 `Option` 之间混用：一个返回 `Result` 的函数里不能直接对 `Option` 用 `?`（反之亦然），需要先用 `ok_or` / `ok` 之类做转换（见 R7.2）。`?` 传播的"失败通道"类型必须和函数返回类型的失败通道一致（经 `From` 可转即可）。

### ? 的适用位置：函数返回类型与 main

`?` 只能出现在返回类型能"接住"它的函数里——返回 `Result`、`Option`，或其它实现了 `FromResidual` 的类型。放到一个返回 `()` 的普通函数里会编译失败。本机对返回 `()` 的 `main` 里写 `?` 编译，得到：

```text
error[E0277]: the `?` operator can only be used in a function that returns `Result` or `Option` (or another type that implements `FromResidual`)
 --> qmark_bad.rs:2:30
  |
1 | fn main() {
  | --------- this function should return `Result` or `Option` to accept `?`
2 |     let n: i32 = "42".parse()?;
  |                              ^ cannot use the `?` operator in a function that returns `()`
```

解决办法是让 `main` 也返回 `Result`。`main` 允许返回 `Result<(), E>`（E 满足一定约束，常用 `Box<dyn Error>` 兜住任意错误）；这样 `main` 里就能用 `?`，且当 `main` 返回 `Err` 时程序以非零码退出并打印错误。本机实测：

```rust
use std::error::Error;

fn main() -> Result<(), Box<dyn Error>> {
    let n: i32 = "42".parse()?;
    println!("{n}");
    let _bad: i32 = "oops".parse()?;   // 返回 Err，程序非零退出
    Ok(())
}
```

运行输出 `42` 后打印 `Error: ParseIntError { kind: InvalidDigit }`，退出码为 1。可见 `main` 返回 `Err` 时，运行时用 `Debug` 格式打印错误并给非零退出码——这是把 `?` 一路用到顶层、让整个程序错误传播贯通的惯用法。

#### 来源与时效

- The Rust Programming Language book Ch.9 §「Recoverable Errors with Result · A Shortcut for Propagating Errors: the ? Operator」「Where The ? Operator Can Be Used」https://doc.rust-lang.org/book/ch09-02-recoverable-errors-with-result.html （对齐 rustc 1.90.0 + edition 2024；⚙演进快·锚版本·随时变，核实 2026-08-01）
- The Rust Reference §「The question mark operator」（`?` 的脱糖语义、经 `From::from` 转换错误、`FromResidual`）https://doc.rust-lang.org/reference/expressions/operator-expr.html#the-question-mark-operator 、标准库 `std::process::Termination` / `main` 返回 `Result` 说明（核实 2026-08-01）
- 本机 rustc 1.94.1 实测：`?` + `From` 到自定义枚举编译运行正常；`?` 用于返回 `()` 的函数报 E0277；`main` 返回 `Result` 时 `Err` 以 `Debug` 打印、退出码 1。
- 无来源冲突。E0277 文本与退出码由本机实测坐实，未凭记忆。

## R7.4 何时 panic 的准则

### 契约违反用 panic，可预期失败用 Result

选 `panic!` 还是 `Result` 的核心判据是：这个失败是"调用方可以预见并有意义地处理的"，还是"代表某处代码违反了本不该被违反的约定（契约）、继续运行只会更糟"。前者返回 `Result` 把决定权交给调用方；后者 panic，因为再往下走没有安全或有意义的路径。

解析用户输入的数字可能失败，这是完全可预期的，用 `Result`；而一个内部函数收到了它文档里明确要求"绝不为负"的参数却拿到了负数，这说明调用方违约、程序已处于不该出现的状态，panic 是合理的（它把一个隐蔽的逻辑错误变成一次立刻、显眼的崩溃，而非让坏数据继续扩散）。The book 的原则表述是：当代码可能进入"坏状态"（bad state，即某些假设/契约被打破）且这种状态不是偶发可预期的，就该 panic。

### 原型、示例与测试里可以放心 unwrap/expect

在写示例、原型、教程代码和测试时，`unwrap` 和 `expect` 是完全合适的。这些场景下你要的是"跑不通就立刻响亮地失败"，而不是把精力花在完善错误处理上；健壮的错误处理反而会淹没你想演示的主逻辑。

测试里尤其如此：测试函数中某步返回 `Err` 时直接 `unwrap`/`expect`，会让该测试 panic 从而标记为失败——这正是你要的行为。等原型演进成要交付的产品代码，再把这些 `unwrap` 替换成真正的错误处理。区分"探索期代码"与"生产代码"是初学者容易忽略的实践尺度。

### 当你掌握编译器不知道的信息时用 expect

有时你从逻辑上确知某个 `Result` 一定是 `Ok`，但编译器无法推断出这一点。这时用 `expect` 并在消息里写清"我为什么确定这里不会失败"是恰当的——它既取出值，又留下一条给未来读者（和你自己）的说明；一旦假设被打破，panic 消息会直接告诉你哪条假设错了。

```rust
use std::net::IpAddr;

fn main() {
    // "127.0.0.1" 是硬编码的合法字面量，解析不可能失败
    let home: IpAddr = "127.0.0.1"
        .parse()
        .expect("hardcoded IP address should always be valid");
    println!("{home}");
}
```

这里 `expect` 用在一个由硬编码常量决定、逻辑上不可能失败的场合；如果值来自用户输入或文件，就不该 `expect`，而应返回 `Result` 让调用方处理。用 `expect` 而非 `unwrap`，是为了让万一失败时的消息有解释力。

### 自定义错误类型与 std::error::Error trait

当一个库要对外报告多种失败原因时，惯用做法是定义自己的错误类型——通常是一个枚举，每个变体代表一类失败（见 R7.3 的 `MyError`）。要让它像标准错误一样好用，一般实现三样：`Debug`（多用 `#[derive(Debug)]`）、`std::fmt::Display`（给人读的错误消息）、以及标准库的 `std::error::Error` trait（错误类型的通用接口，可选提供 `source()` 指向底层错误以形成错误链）。

实现了 `Error + Display` 的类型能被塞进 `Box<dyn Error>` 做类型擦除，于是不同来源的错误可以统一往上抛（配合 R7.3 的 `?` 与 `From`）。初学者路线通常是：先用 `Box<dyn Error>` 图省事，等需要让调用方按错误种类分别处理时，再定义具体的错误枚举把类型信息保留下来。

### thiserror 与 anyhow 生态

手写 `Display` 和一堆 `From` 实现很啰嗦，Rust 社区因此形成了两个几乎标配的第三方 crate：`thiserror` 用派生宏（`#[derive(Error)]`）自动为你的错误枚举生成 `Display`/`Error`/`From` 样板，适合写库时定义具体的、供调用方区分处理的错误类型；`anyhow` 提供一个"万能"错误类型 `anyhow::Error`（近似便捷版 `Box<dyn Error>`）和 `Result` 别名，适合应用/二进制程序里"我不需要区分错误种类，只要能一路 `?` 上去并带上上下文"的场景。粗略的选择法则是：库用 `thiserror`，应用用 `anyhow`。

这两个都是标准库之外的 crate，需在 `Cargo.toml` 里加依赖，不随 rustc 内置。本机为离线环境、未拉取这两个 crate，故其具体 API 与版本号此处不做断言（待核，核实 2026-08-01）；它们非语言核心、随生态演进（⚙演进快·外部 crate·版本随时变），本报告只给到"是什么、什么时候用哪个"的概念层，不外推具体接口。The book Ch.9 本身只讲标准库路线（`panic!`/`Result`/`?`/`Box<dyn Error>`），thiserror/anyhow 属社区补充。

#### 来源与时效

- The Rust Programming Language book Ch.9 §「To panic! or Not to panic!」https://doc.rust-lang.org/book/ch09-03-to-panic-or-not-to-panic.html （契约/坏状态判据、原型与测试中用 unwrap/expect、掌握额外信息时用 expect；对齐 rustc 1.90.0 + edition 2024；⚙演进快·锚版本·随时变，核实 2026-08-01）
- 标准库文档 `std::error::Error` trait（`source()`、与 `Display`/`Debug` 的关系、`Box<dyn Error>`）https://doc.rust-lang.org/std/error/trait.Error.html （核实 2026-08-01）
- thiserror / anyhow：为社区第三方 crate，非本机可实测（离线环境未拉取），其具体 API/版本标「待核」，仅记录"库用 thiserror、应用用 anyhow"的通行分工，核实 2026-08-01。
- 本机 rustc 1.94.1 实测：自定义错误枚举 + `Display` + `From` 示例编译运行正常。
- 冲突项：无标准库层冲突。需点明的是——"库用 thiserror、应用用 anyhow"是社区惯例而非语言规范，属二手实践共识，未与官方一手规范对账，标 ⚠社区惯例。
