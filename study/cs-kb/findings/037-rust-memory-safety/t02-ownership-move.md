# L5-14·大主题R2 所有权与 move 语义

> 基线 Py3.11.15/np2.4.6/gcc13.3 @2026-07-25（+ rustc/cargo 1.94.1） ｜ 核实日期：2026-08-01 ｜ 先修：R1（cargo/rustc 用法、edition 2024、`cargo new`/`cargo run`）、对栈与堆的基本概念、对 C/C++ 手动 `malloc`/`free` 或 `new`/`delete` 有印象更好 ｜ 一手锚点：The Rust Programming Language book（"the book"，Klabnik/Nichols/Krycho，活文档，页面声明对齐 Rust 1.90.0 + edition 2024）Ch.4「Understanding Ownership」§4.1；The Rust Reference「Destructors」章 https://doc.rust-lang.org/reference/destructors.html ；本机 rustc/cargo 1.94.1 实测 ｜ 成熟度：⚙演进快·锚版本（语言核心语义 GA/稳定；报错文案与提示随编译器版本演进，本篇报错以本机 rustc 1.94.1 现跑为准）

所有权（ownership）是 Rust 用来在**没有垃圾回收器、也不用手写 `free`** 的前提下，仍然保证内存安全的那套核心机制。它的做法是：把"这块内存归谁管、什么时候该还"的规则从"程序员靠自觉"改成"编译器靠类型检查强制执行"。本大主题只讲这套规则的四个面——所有权三规则与栈堆布局、move 与 Copy 的区别、值在函数间怎么流转、以及作用域结束时的自动析构（Drop/RAII）。

所有权背后的理论根是「仿射类型 / 线性类型」（每个资源至多被用一次），那属编程语言理论范畴，此处只做一句交叉引用（→ L5-06 编程语言理论关于线性/仿射类型系统的讨论），不展开。本篇立足工程直觉：读完能看懂编译器为什么报错、以及怎么改对。

---

## R2.1 栈/堆与所有权三规则

### R2.1.1 栈与堆：为什么 Rust 要区分它们

栈（stack）和堆（heap）是程序运行时内存的两块区域。栈按「后进先出（LIFO）」存取：进入一个函数就在栈顶压入它的局部变量，函数返回就整块弹出；栈上的每个值都必须是**编译期已知的固定大小**。堆则用于存放大小在运行期才确定、或需要跨作用域存活的数据：向分配器（allocator）申请一块空间，它找一处足够大的空位标记为已用，返回一个**指向该位置的指针**。

理解所有权前必须先接受一个事实：栈上存取比堆快。书里的原话是"Pushing to the stack is faster than allocating on the heap"——压栈永远往栈顶放、分配器无需搜索；而堆分配要找空位、访问堆数据还要先解引用指针多绕一跳。所以"谁来负责归还堆内存、什么时候归还"就成了一个真问题：还早了会用到已释放内存（use-after-free），还晚了或还两次会出双重释放（double free），忘了还会内存泄漏。C/C++ 把这个责任交给程序员，Rust 则用所有权规则把它交给编译器。

一个典型的"堆上"类型是 `String`。它在栈上其实是一个三元组——指向堆缓冲区的指针、长度 `len`、容量 `capacity`；真正的字符字节躺在堆上。后面讲 move 时，这个"栈上三元组 + 堆上字节"的二层结构是关键。

### R2.1.2 所有权三条规则

the book §4.1 把所有权浓缩成三条规则，原文照抄：

```text
- Each value in Rust has an owner.          （每个值都有一个所有者）
- There can only be one owner at a time.    （任一时刻只能有一个所有者）
- When the owner goes out of scope, the value will be dropped.
                                             （所有者离开作用域时，值被丢弃/释放）
```

这三条要连起来读才有意义。第一条与第二条合起来说的是"一份资源、唯一负责人"——任何时刻一个值只有一个变量对它负责，杜绝了"两个变量都以为自己该释放它"的混乱根源。第三条则规定了释放的时机：不是靠 GC 事后扫描、也不是靠你手写 `free`，而是**所有者变量一离开它的作用域，编译器就在那个点自动插入释放代码**。

所有权就像一件东西的"唯一保管责任"。你可以把责任转交（move，见 R2.2），转交后原来的人就不再负责、也不许再碰；没人转交的话，保管人一走出房间（作用域），东西就当场销毁。正因为"唯一负责 + 出作用域即销毁"是编译器静态就能算清的，Rust 才敢不带 GC 还宣称内存安全。

所有权是**编译期**概念，不是运行期开销。规则由借用检查器在编译时验证，编译通过后运行的机器码里并没有"引用计数"或"标记清扫"这类东西，`drop` 也只是编译器在固定位置插入的一次普通函数调用。

### R2.1.3 作用域结束即释放：drop 的时机

变量从声明处开始有效，到它所在的那对花括号 `}` 结束时失效；就在失效的那一刻，如果它仍是某个堆资源的所有者，编译器插入的清理逻辑会释放该资源。C++ 程序员会认出这就是 RAII，本篇 R2.4 会正式讲。

下面这个最小例子（本机 rustc 1.94.1，`rustc --edition 2024 drop.rs && ./drop`）用一个会在析构时打印的类型直观展示"出作用域即释放"：

```rust
struct Noisy(&'static str);
impl Drop for Noisy {
    fn drop(&mut self) {
        println!("dropping {}", self.0);
    }
}
fn main() {
    let _a = Noisy("a");
    let _b = Noisy("b");
    {
        let _c = Noisy("c");
        println!("inner scope end soon");
    } // _c 在此处离开作用域，立即被释放
    println!("outer scope end soon");
} // _b、_a 在此处离开作用域被释放
```


```text
inner scope end soon
dropping c
outer scope end soon
dropping b
dropping a
```

可以看到 `_c` 在内层 `}` 处就被释放（早于外层的两个），而 `_b`/`_a` 拖到 `main` 结束。释放时机完全由作用域嵌套决定，无需任何手动调用。至于 `_b`、`_a` 为什么是"先 b 后 a"这个逆序，见 R2.4.2。

#### 来源与时效（本小主题）

- 一手：The Rust Programming Language book §4.1「What Is Ownership?」——三条所有权规则原文、栈/堆的 LIFO 与固定大小描述、"Pushing to the stack is faster than allocating on the heap" https://doc.rust-lang.org/book/ch04-01-what-is-ownership.html
- 一手：The Rust Reference「Destructors」——"When an initialized variable or temporary goes out of scope, its destructor is run or it is dropped." https://doc.rust-lang.org/reference/destructors.html
- 本机实测：rustc 1.94.1（`rustc 1.94.1 (e408947bf 2026-03-25)`），上例 `--edition 2024` 编译运行，输出如正文。
- 版本时效：所有权三规则属语言核心语义，自 Rust 1.0 稳定，无 delta；drop 时机语义稳定。⚙演进快仅指报错文案层面，本项无报错。

## R2.2 move 与 Copy

### R2.2.1 move：移动语义与源变量失效

对于像 `String` 这样"栈上三元组 + 堆上数据"的类型，把一个变量赋给另一个变量（或传参、返回）时，Rust 执行的是 **move（移动）**：只按位复制栈上那个三元组（指针、长度、容量），**不复制堆上的字节**；同时把原变量标记为"已失效"，之后再用它就是编译错误。

`let s2 = s1;` 之后"Rust considers `s1` as no longer valid"，因此"Rust doesn't need to free anything when `s1` goes out of scope"。为什么要让 `s1` 失效？因为若不失效，`s1` 和 `s2` 会各持一份指向**同一块堆内存**的指针，两者出作用域时都会去释放它——这正是 double free。move 语义把两个所有者砍成一个，从根上消除了这个 bug（呼应三规则里的"任一时刻只能有一个所有者"）。

下面的最小例子在本机触发经典的 use-after-move 报错 E0382（`rustc --edition 2024 move.rs`）：

```rust
fn main() {
    let s1 = String::from("hello");
    let s2 = s1;          // s1 被移动进 s2，s1 就此失效
    println!("{s1}");     // 错误：使用已被移动的值
}
```

本机 rustc 1.94.1 现跑报错（节选）：

```text
error[E0382]: borrow of moved value: `s1`
 --> move.rs:4:16
  |
2 |     let s1 = String::from("hello");
  |         -- move occurs because `s1` has type `String`, which does not implement the `Copy` trait
3 |     let s2 = s1;
  |              -- value moved here
4 |     println!("{s1}");
  |                ^^ value borrowed here after move
  |
help: consider cloning the value if the performance cost is acceptable
```

编译器不仅指出错在哪，还把"发生 move 的原因"（`String` 未实现 `Copy`）和修复建议（clone）一并给出。初学者最容易困惑的是"我只是读一下 `s1` 怎么就错了"——记住：move 后原变量在类型系统里已经**不存在有效值**，读、写、传参一律禁止，直到重新给它赋一个新值。

### R2.2.2 Copy trait：为什么 `let y = x;` 有时不失效

不是所有类型赋值都会失效。整数、布尔、浮点、字符这类**完全存放在栈上、大小固定**的类型实现了 `Copy` trait；对它们做 `let y = x;` 是按位复制一份，`x` 与 `y` 都仍然有效、互不影响。这是因为复制它们只是拷贝几个栈上字节，既廉价又不涉及任何堆资源的"归还责任"，让原值继续有效没有任何危害。

书里给出的 `Copy` 类型清单（原文照抄要点）：

```text
- All the integer types, such as u32.
- The Boolean type, bool.
- All the floating-point types, such as f64.
- The character type, char.
- Tuples, if they only contain types that also implement Copy.
    例如 (i32, i32) 是 Copy，但 (i32, String) 不是。
```

本机验证 Copy 类型赋值后原值仍可用（`rustc --edition 2024 copy.rs && ./copy`）：

```rust
fn main() {
    let x = 5;
    let y = x;      // i32 是 Copy，这里是复制不是移动
    println!("x={x} y={y}");   // 两个都能用
}
```

本机输出：`x=5 y=5`。

**一个类型不能同时是 `Copy` 又实现 `Drop`**。书里原话是"Rust won't let us annotate a type with `Copy` if the type, or any of its parts, has implemented the `Drop` trait"。直觉上这很合理——`Copy` 意味着"随便复制、复制出来的副本无需特殊清理"，而 `Drop` 意味着"这个值销毁时要跑一段特殊清理代码"；若二者兼得，一次复制就凭空多出一个"待清理"的副本，清理逻辑该跑几次就说不清了。所以语言直接禁止这种组合。

这里最容易混的是 `Copy` 与 `Clone` 的区别：`Copy` 是"是否隐式按位复制"的开关，`Clone` 是"能否显式深拷贝"的能力，两者不同（`Copy` 要求先是 `Clone`，但反之不然）。`String` 是 `Clone` 但**不是** `Copy`，所以它走 move。

### R2.2.3 clone：显式的深拷贝

如果确实想要两份互相独立、各自拥有堆数据的值，用 `.clone()` 显式深拷贝。书里的定位很明确："If we do want to deeply copy the heap data of the `String` … we can use a common method called `clone`。"

```rust
fn main() {
    let s1 = String::from("hello");
    let s2 = s1.clone();   // 深拷贝：堆上字节也复制一份
    println!("{s1} {s2}"); // s1 仍有效，两者独立
}
```

Rust 的一个刻意设计是：**深拷贝必须写出来**，绝不隐式发生。书里说"Rust will never automatically create deep copies of your data"，因此任何自动的复制都可假定为廉价的。反过来，一看到 `.clone()` 就等于收到一个视觉信号——这里"some arbitrary code is being executed and that code may be expensive"，可能有一次堆分配加内存拷贝。这让性能开销在代码里显式可见，而不是藏在赋值号背后。初学者常把 `.clone()` 当成消灾符到处撒来绕过 move 报错，能编过但可能悄悄引入不必要的堆拷贝；正解往往是改用借用（→ R3 借用与引用）。

### R2.2.4 与仿射类型的关系（交叉引用，不展开）

move 语义"一个值至多被消费一次、消费后源失效"正是**仿射类型（affine types）**在工程语言里的落地：线性类型要求资源恰好用一次，仿射类型放宽为至多一次（允许提前丢弃）。其类型论根据属编程语言理论范畴，此处仅点名交叉引用（→ L5-06 线性/仿射类型系统），本篇不展开推导。

#### 来源与时效（本小主题）

- 一手：The Rust Programming Language book §4.1——move 定义与 double-free 论证（"Rust considers `s1` as no longer valid"）、`Copy` 类型清单、`Copy` 与 `Drop` 互斥（"won't let us annotate a type with `Copy` if … has implemented the `Drop` trait"）、clone 与"never automatically create deep copies" https://doc.rust-lang.org/book/ch04-01-what-is-ownership.html
- 一手：The Rust Reference——赋值会先运行左操作数（若已初始化）的析构器；`Copy`/`Clone` trait 语义。 https://doc.rust-lang.org/reference/destructors.html
- 本机实测：rustc 1.94.1 现跑 E0382（`error[E0382]: borrow of moved value: \`s1\``，含 "does not implement the `Copy` trait" 与 clone 建议）；Copy 类型赋值后原值可用（输出 `x=5 y=5`）。报错码/文案以本机现跑为准。
- 冲突项：无来源分歧。E0382 具体文案随编译器版本演进（⚙演进快·锚版本），本项已用本机 1.94.1 现跑锚定；the book 页面对齐 1.90.0，与本机 1.94.1 在本主题上语义无 delta。

## R2.3 参数/返回的所有权流转

### R2.3.1 传参 = 移动或拷贝

把值传给函数，与把它赋给变量遵循**完全相同**的规则。书里原话："Passing a variable to a function will move or copy, just as assignment does." 也就是说：传一个 `String` 进去就是 move，实参在调用点之后失效；传一个 `i32` 进去则是 copy，实参仍然可用。

下例把 `String` 传进函数后再用它，触发 E0382（`rustc --edition 2024 movefn.rs`）：

```rust
fn takes(s: String) {
    println!("got {s}");
}
fn main() {
    let s = String::from("hi");
    takes(s);            // s 被移动进 takes
    println!("{s}");     // 错误：s 已失效
}
```

本机 rustc 1.94.1 现跑报错（节选）：

```text
error[E0382]: borrow of moved value: `s`
 --> movefn.rs:7:16
  |
6 |     takes(s);
  |           - value moved here
7 |     println!("{s}");
  |                ^ value borrowed here after move
  |
note: consider changing this parameter type in function `takes` to borrow instead
      if owning the value isn't necessary
  |
1 | fn takes(s: String) {
  |    -----    ^^^^^^ this parameter takes ownership of the value
```

编译器点出 `takes` 的参数类型"takes ownership of the value"，并建议若不必拥有就改成借用。这正是"函数签名即契约"：看到参数是 `String`（而非 `&String`），就知道调用后自己会失去这个值的所有权。

### R2.3.2 返回 = 移出

函数也可以通过返回值把所有权**交出来**。书里把这条模式概括为：赋值移动值；一个持有堆数据的变量出作用域时会被 `drop` 清理，**除非其所有权已被移交给别的变量**。返回就是这种"移交出去"的一种——本该在函数结束时被销毁的局部值，因为被 `return` 出去，责任转移到了调用方接收它的那个变量上，于是不释放。

```rust
fn gives() -> String {
    let s = String::from("made in fn");
    s          // 把 s 的所有权移出函数，交给调用方
}
fn takes_and_gives_back(s: String) -> String {
    s          // 收进来再原样交回去
}
fn main() {
    let a = gives();                    // a 拿到所有权
    let b = takes_and_gives_back(a);    // a 移入、又移出到 b
    println!("{b}");
}
```

这里 `a` 先被 `gives` 移出赋值获得，再被移入 `takes_and_gives_back`（`a` 随即失效）、然后返回值又移给 `b`。全过程没有一次堆拷贝，搬的只是栈上那个三元组。

### R2.3.3 靠这一路搬所有权太累——借用是出路（关联，不展开）

如果每次让函数"读一下"数据都得把所有权交进去、再原样返回来（像 R2.3.2 的 `takes_and_gives_back` 那样），代码会非常啰嗦。Rust 的解法是**借用（borrowing）**：用 `&T` / `&mut T` 把值"借"给函数用完即还，所有权始终留在原处。这是下一个大主题的内容（→ R3 借用、引用与借用检查器），本篇只点出所有权流转的痛点由它来解。

#### 来源与时效（本小主题）

- 一手：The Rust Programming Language book §4.1「Ownership and Functions」/「Return Values and Scope」——"Passing a variable to a function will move or copy, just as assignment does."；返回值移交所有权、"unless ownership of the data has been moved to another variable" https://doc.rust-lang.org/book/ch04-01-what-is-ownership.html
- 一手：The Rust Reference「Destructors」——所有权移交后原变量不再运行析构器（避免 double free）的规范依据。 https://doc.rust-lang.org/reference/destructors.html
- 本机实测：rustc 1.94.1 现跑传参后使用触发 E0382，报错含 "this parameter takes ownership of the value" 与改借用的建议；`gives`/`takes_and_gives_back` 示例编译运行输出 `made in fn`。
- 版本时效：传参/返回的移动语义稳定无 delta；报错文案（⚙演进快·锚版本）以本机 1.94.1 现跑为准。

## R2.4 Drop 与 RAII

### R2.4.1 Drop trait 与析构

当一个值离开作用域，Rust 会运行它的**析构器（destructor）**来清理资源。The Reference 把类型 `T` 的析构器定义为两步组合：若 `T` 实现了 `Drop`，先调用 `<T as core::ops::Drop>::drop`；然后**递归运行它所有字段的析构器**（这第二步俗称 drop glue，由编译器自动生成，你无需也无法手写）。

`Drop` trait 只有一个方法 `fn drop(&mut self)`，你在里面写"这个值销毁时要做的特殊清理"——释放堆内存、关闭文件、解锁等。标准库类型如 `String`、`Vec`、`Box`、`File`、`MutexGuard` 都实现了它，所以你几乎从不需要手动清理。

```rust
struct Noisy(&'static str);
impl Drop for Noisy {
    fn drop(&mut self) {          // 值销毁时自动被调用
        println!("dropping {}", self.0);
    }
}
```

你不能手动调用 `x.drop()`——编译器会拒绝，因为那会导致离开作用域时再自动 drop 一次（double drop）。要提前释放请用 R2.4.3 的 `std::mem::drop(x)`。

### R2.4.2 析构顺序：局部变量逆序、结构体字段声明序

析构顺序有两条容易记混的规则，The Reference 的原文分得很清楚：

一是**同一作用域内的局部变量，按声明的逆序析构**——"all variables associated to that scope are dropped in reverse order of declaration"。所以 R2.1.3 例子里先声明的 `_a` 最后释放、后声明的 `_b` 先释放（本机输出 `dropping b` 在 `dropping a` 之前）。逆序的直觉：后声明的变量可能依赖先声明的变量（比如后者借用了前者），先建的后拆才安全，和栈的 LIFO 一致。

二是**结构体/元组的字段，按声明顺序析构**——"The fields of a struct are dropped in declaration order"。注意这和局部变量恰好相反。

```rust
// 局部变量：逆序析构 → 先 dropping b，再 dropping a
let a = Noisy("a");
let b = Noisy("b");

// 结构体字段：声明序析构 → 先 dropping x，再 dropping y
struct Pair { x: Noisy, y: Noisy }
```

这个"局部逆序、字段正序"的不对称是初学者高频踩坑点，需要单独记住。此外，pattern 里绑定的变量按 pattern 内声明的逆序析构（The Reference 单列此条）。

### R2.4.3 提前释放：std::mem::drop 与 mem::forget

若想在作用域结束前就释放一个值（例如提前解锁一个 `MutexGuard`），用标准库函数 `std::mem::drop(x)`：它接收 `x` 的所有权，函数体一结束 `x` 就在那里被析构。

```rust
fn main() {
    let s = String::from("bye");
    drop(s);            // 此刻就释放 s（drop 已在 prelude 中）
    // 这之后再用 s 会报 E0382：s 已被移动进 drop
}
```

反方向的 `std::mem::forget(x)` 和 `ManuallyDrop` 则用于**抑制**析构——The Reference 指出 `core::mem::forget` "can be used to prevent the destructor of a variable from being run"，`ManuallyDrop` 提供一个包装器阻止自动析构。这些属进阶/unsafe 场景（如手写智能指针时接管释放责任，→ R8 智能指针、R11 unsafe），初学者知道其存在即可。注意 `forget` 本身是安全的（不引发 UB，只是可能泄漏资源）。

### R2.4.4 对比 C++ RAII

Rust 的这套"出作用域即自动析构"就是 C++ 的 **RAII（Resource Acquisition Is Initialization）**：C++ 里对象离开作用域自动调析构函数、`std::unique_ptr` 表达独占所有权、`std::move` 转移所有权，思想与 Rust 高度一致。差异在于**谁来兜底**：

C++ 的 `move` 后源对象进入一个"有效但未指定"的状态，你**仍然可以访问它**（往往会读到空壳或旧值），用错了是逻辑 bug、编译器不拦；use-after-free、double free 在 C++ 里也只能靠程序员纪律和工具（sanitizer）事后发现。Rust 则把这些变成**编译期错误**：move 后源变量在类型系统里直接失效，再碰就是 E0382；所有权唯一性由借用检查器静态保证，从语言层面消除了整类内存安全 bug。

C++ RAII 是"约定 + 库设施（`unique_ptr`/`move`）"，靠程序员正确使用；Rust 把同样的所有权模型**内建进类型系统并强制检查**，把"别用已移动的值""别释放两次"从"最佳实践"升级成"编不过"。代价是写法更受约束、需要顺从借用检查器；收益是安全性由编译器兜底。

#### 来源与时效（本小主题）

- 一手：The Rust Reference「Destructors」——析构器组成（`Drop::drop` + 递归字段析构 glue）、"variables … dropped in reverse order of declaration"、"The fields of a struct are dropped in declaration order"、pattern 变量逆序、`mem::forget`/`ManuallyDrop` 抑制析构。 https://doc.rust-lang.org/reference/destructors.html
- 一手：The Rust Programming Language book §4.1（drop 时机、所有权与作用域）与 Ch.15「Running Code on Cleanup with the Drop Trait」（`Drop` trait、不能手动调 `.drop()`、用 `std::mem::drop` 提前释放）。 https://doc.rust-lang.org/book/
- 本机实测：rustc 1.94.1 现跑嵌套作用域析构，输出确认"内层先于外层、同层后声明先析构"（`dropping c` → `dropping b` → `dropping a`）。
- 冲突项：无来源分歧。C++ 对照为概念性交叉参考（RAII/`unique_ptr`/`std::move` 语义，非 Rust 一手），仅作直觉桥接，不承重具体数值。
- 版本时效：Drop/析构顺序语义稳定，自 Rust 1.0 无 delta；the book 页面对齐 1.90.0、本机 1.94.1，本主题无语义差异（⚙演进快仅涉报错文案层面）。
