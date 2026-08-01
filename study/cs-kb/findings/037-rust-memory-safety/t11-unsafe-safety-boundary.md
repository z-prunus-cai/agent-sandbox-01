# L5-14·大主题R11 unsafe 与安全边界

> 基线 Py3.11.15/np2.4.6/gcc13.3 @2026-07-25（+ rustc/cargo 1.94.1）｜核实日期：2026-08-01｜先修：R2 所有权与 move、R3 借用与借用检查器、R8 智能指针（裸指针心智模型）、L3-03 ABI（承 R11.3）｜一手锚点：The Rustonomicon（https://doc.rust-lang.org/nomicon/）> The Rust Reference（unsafety / ABI，https://doc.rust-lang.org/reference/）> The Rust Programming Language book Ch.20（https://doc.rust-lang.org/book/）；the book 页面对齐 Rust 1.90.0 + edition 2024，本机 rustc/cargo 1.94.1（e408947bf 2026-03-25）实测｜成熟度：unsafe/FFI 语言机制 GA；⚙演进快·锚版本——edition 2024 对 static mut / extern / unsafe fn 有硬变更，UB 精确语义（尤其别名模型 Stacked/Tree Borrows）与 Miri 仍在演进，随时变

unsafe 是 Rust「安全」承诺的一道明确边界：安全 Rust 在编译期由借用检查器与类型系统保证不出现内存不安全，而 unsafe 把「保证内存安全」的责任从编译器临时移交给程序员。本大主题讲清 unsafe 到底解锁了什么、程序员因此背上了哪些不变式义务、如何把这些义务重新封装回安全 API、以及跨越语言边界（FFI）和未定义行为（UB）的规矩。

unsafe 不是「关掉借用检查器」，也不是「关掉类型系统」。它只解锁下面 R11.1 列举的五件事，其余一切规则照旧生效。这是初学者最常见的误解，务必先立住。

---

## R11.1 unsafe 的五种超能力

### R11.1.1 unsafe 关键字解锁什么、不解锁什么

`unsafe` 关键字（以 `unsafe { ... }` 块或 `unsafe fn` 形式出现）在其作用范围内解锁**恰好五种**普通安全 Rust 不允许的操作，the book Ch.20 称之为「unsafe 的超能力（superpowers）」：解引用裸指针、调用 unsafe 函数或方法、访问或修改可变静态变量、实现 unsafe trait、访问 union 的字段。除此之外的规则（借用检查、生命周期、类型检查、所有权与 move）在 unsafe 块内**完全照常生效**。

初学者要牢牢记住一句反直觉的话：unsafe 不会让借用检查器闭嘴。在 unsafe 块里写 `let a = &mut v; let b = &mut v;` 一样报错 E0499。unsafe 只是多给你五把钥匙，编译器对其余部分的看守一分未减。它的真正含义是一句契约：「这段代码里，凡是编译器无法自动验证的内存安全前提，由我（程序员）来保证」。因此 unsafe 不是「不安全代码」，而是「编译器已无法替你证明安全、需要你亲自证明」的代码。

`unsafe` 本身不做任何运行时检查，也不改变生成的机器码语义——它纯粹是编译期的「许可开关」。写不写 unsafe，`*p` 解引用裸指针的运行时行为完全相同；区别只在于没有 unsafe 时编译器拒绝编译。

### R11.1.2 超能力一：解引用裸指针

第一种超能力是解引用裸指针 `*const T`（不可变）与 `*mut T`（可变）。创建裸指针（例如 `&x as *const i32` 或 `&raw const x`）在安全代码里就允许，但**解引用**它必须在 unsafe 块内。

本机 rustc 1.94.1、edition 2024 实测：在安全代码里解引用裸指针报错 E0133。

```rust
fn main() {
    let x = 5;
    let p = &x as *const i32;
    let _y = *p; // error[E0133]: dereference of raw pointer is unsafe and requires unsafe block
}
```

编译器的诊断附带一句精准的理由：「raw pointers may be null, dangling or unaligned; they can violate aliasing rules and cause data races: all of these are undefined behavior」。这句话本身就是初学者理解裸指针危险性的最好教材：裸指针可能为空、悬垂、未对齐，可以违反别名规则、可以引发数据竞争——这些都是 UB。裸指针不带生命周期、不受借用检查、不保证非空、不保证对齐、可以有多个 `*mut` 同时指向同一处。把它包进 unsafe 解引用，就等于你向编译器签字保证「此刻这个地址确实指向一个有效、已初始化、对齐正确、且没有被别的可变引用同时借用的 T」。

### R11.1.3 超能力二：调用 unsafe 函数或方法

第二种超能力是调用被标记为 `unsafe fn` 的函数或方法。一个函数被标 `unsafe`，意思是「调用我之前，有一些我无法在类型层面表达的前提条件（先决条件/契约），你必须自己满足」。标准库里 `String::from_utf8_unchecked`、`<[T]>::get_unchecked`、`std::slice::from_raw_parts` 都是典型。

在安全上下文调用 unsafe fn 同样报 E0133。

```rust
unsafe fn dangerous() {}
fn main() {
    dangerous(); // error[E0133]: call to unsafe function `dangerous` is unsafe and requires unsafe block
}
```

初学者要区分两个方向的义务。`unsafe fn` 是把义务推给**调用方**：签名上的 `unsafe` 是对使用者的警告「满足契约再调我」。这与「函数体内部能用超能力」是两回事——见下一项的 edition 2024 变化。

这里有一个 ⚙演进快·锚版本 的 edition 2024 变更（本机 1.94.1 实测）：edition 2024 默认开启 `unsafe_op_in_unsafe_fn` lint（警告级）。也就是说 `unsafe fn` 的**函数体不再自动获得超能力**，体内若要解引用裸指针等操作，仍需显式再写 `unsafe { }`，否则告警。

```rust
unsafe fn deref(p: *const i32) -> i32 {
    *p // edition 2024: warning[E0133] —— unsafe fn 的体默认是「安全的」，需再包 unsafe 块
}
```

同一段代码 `--edition 2024` 给出 warning 并提示「an unsafe function restricts its caller, but its body is safe by default」，而 `--edition 2021` 编译无告警。这条变更的用意是把「对调用者的契约声明」和「体内实际执行危险操作」两件事解耦，让代码里每一处真正危险的动作都有独立可见的 unsafe 块。

### R11.1.4 超能力三：访问或修改可变静态变量 static mut

第三种超能力是读写 `static mut` 可变全局变量。普通 `static` 是不可变的全局；`static mut` 允许被修改，但因为它是全局可被多线程访问的可变状态，任何并发读写都可能造成数据竞争，所以访问它需要 unsafe。

这是本大主题里 edition 2024 变化最大、最容易踩坑的一处，硬标 ⚙演进快·锚版本。本机 rustc 1.94.1、edition 2024 实测：**创建对 `static mut` 的引用（哪怕是共享引用）现在是硬错误**（lint `static_mut_refs` 在 edition 2024 下 deny-by-default）。

```rust
static mut COUNTER: i32 = 0;
fn main() {
    unsafe {
        COUNTER += 1;
        println!("{}", COUNTER); // error: creating a shared reference to mutable static
    }
}
```

`println!("{}", COUNTER)` 会隐式借一个 `&COUNTER`，于是触发硬错误。诊断给出理由：「shared references to mutable statics are dangerous; it's undefined behavior if the static is mutated or if a mutable reference is created for it while the shared reference lives」，并指向 edition-2024 迁移页 static-mut-references.html。

edition 2024 下推荐的访问方式是绕开引用、走裸指针原始借用 `&raw const` / `&raw mut`，本机实测可编译运行：

```rust
static mut COUNTER: i32 = 0;
fn main() {
    unsafe {
        let p = &raw mut COUNTER;
        *p += 1;
        println!("{}", *p); // 输出 1
    }
}
```

`static mut` 现在被官方强烈劝退，实际工程里几乎总应改用线程安全的内部可变性（如 `AtomicI32`、`Mutex<T>` 配合 `static`），只有极少数场景才动 `static mut`。这一项也生动说明「基线随版本演进」：同样一段教材老代码，在 edition 2021 只是告警、在 edition 2024 直接编译失败。

### R11.1.5 超能力四：实现 unsafe trait

第四种超能力是为类型实现被标记为 `unsafe` 的 trait，写作 `unsafe impl SomeUnsafeTrait for T {}`。一个 trait 被标 `unsafe`，意味着它带有编译器无法自动验证的不变式，实现者必须手动保证这些不变式成立。最经典的两个是 `Send` 和 `Sync`（承 R9 无畏并发，此处只点题不展开）：它们是编译期消除数据竞争的标记 trait，绝大多数类型由编译器自动派生，但当你手写裸指针类型想跨线程共享时，就需要 `unsafe impl Send`/`unsafe impl Sync` 亲自担保线程安全。

```rust
struct MyBox(*mut u8);
// 我向编译器担保：跨线程移动 MyBox 是安全的
unsafe impl Send for MyBox {}
```

普通 trait 实现是「我提供这些方法」；unsafe trait 实现是「我提供这些方法，并且**以内存安全为赌注**保证该 trait 要求的隐含契约成立」。写下 `unsafe impl Send` 就是签字保证「这个含裸指针的类型确实可以安全地在线程间转移所有权」——如果担保错了，会造成数据竞争 UB，而编译器不会再拦你。

### R11.1.6 超能力五：访问 union 字段

第五种超能力是读取 `union`（联合体）的字段。union 与 struct 语法相似，但所有字段**共享同一块内存**，同一时刻只有一个字段是「有效」的。读取哪个字段由程序员决定，编译器无法知道当前实际存放的是哪一种解释，因此读 union 字段必须 unsafe。union 主要用于与 C 的联合体做 FFI 互操作，以及少数需要「同一内存两种类型视角」的底层场景。

```rust
union IntOrFloat {
    i: u32,
    f: f32,
}
fn main() {
    let u = IntOrFloat { i: 1065353216 };
    let x = unsafe { u.f }; // 按 f32 解释这段位，读 union 字段需 unsafe
    println!("{}", x); // 1（1065353216 的位模式恰是 f32 的 1.0）
}
```

如果写入的是 `i` 却按 `f` 去读，你得到的是同一段二进制位在另一种类型下的重新解释（type punning）。这在 C 里司空见惯，但读一个「当前并未有效存放该类型」的 union 字段可能触碰有效性不变式（见 R11.4）。Rust 之所以逼你写 unsafe，正是要你为「我知道现在里面装的是什么」负责。

#### 来源与时效

- The Rust Programming Language book Ch.20「Unsafe Rust」（活文档，声明对齐 Rust 1.90.0 + edition 2024）：五种超能力的权威枚举与「unsafe 不关闭借用检查」的表述。https://doc.rust-lang.org/book/
- The Rust Reference §「Unsafety」/§「unsafe keyword」：解引用裸指针、调用 unsafe fn、访问 mutable static、实现 unsafe trait、读 union 字段五类操作的规范定义。https://doc.rust-lang.org/reference/
- 本机 rustc 1.94.1（edition 2024）实测：E0133（解引用裸指针、调 unsafe fn）；`static_mut_refs` deny-by-default 硬错误 + `&raw mut` 可行；`unsafe_op_in_unsafe_fn` edition 2024 警告级、edition 2021 无告警。核实 2026-08-01。
- ⚙演进快·锚版本：static mut / unsafe_op_in_unsafe_fn 的默认行为随 edition 变化，delta 现查不凭记忆；本报告以本机 1.94.1 + edition 2024 实测为准。
- 冲突/分歧：the book 与 Reference 对「五种超能力」的枚举一致，无冲突；需注意教材若使用 edition 2021 老示例（如 `static mut` 直接读写、`extern` 无 `unsafe`），在 edition 2024 会告警或报错，属版本 delta 而非来源分歧。

---

## R11.2 裸指针与安全抽象封装

### R11.2.1 裸指针 *const T 与 *mut T 及其与引用的区别

裸指针有两种：`*const T`（指向不可变数据）与 `*mut T`（指向可变数据）。它们是 Rust 里最接近 C 指针的东西。与安全引用 `&T`/`&mut T` 相比，裸指针放弃了几乎所有编译期保证：允许为空、允许悬垂、可以忽略别名规则（多个 `*mut` 指向同一处）、不携带生命周期、不受借用检查约束、不保证指向已初始化且对齐的数据。

一个便于初学者建立心智模型的对照：引用是「编译器替你证明过安全的指针」，裸指针是「编译器不再替你证明、由你负责的地址」。正因如此，**创建**裸指针是安全的（只是记下一个地址而已，还没读写），**解引用**才需要 unsafe（这一刻才真正碰内存）。

```rust
fn main() {
    let mut n = 10;
    let r1 = &n as *const i32;      // 由引用转裸指针
    let r2 = &mut n as *mut i32;    // 同一变量的可变裸指针——安全代码里就能同时存在
    let addr = 0x012345usize;
    let _r3 = addr as *const i32;   // 甚至能凭空造一个地址（解引用它才是危险动作）
    unsafe {
        println!("{} {}", *r1, *r2);
    }
}
```

注意上面 `r1` 和 `r2` 同时是同一变量的共享与可变裸指针，这在安全引用里是被别名异或可变（R3.2）禁止的；裸指针世界里编译器不管，代价是解引用时的正确性全归你。

### R11.2.2 不变式义务与「安全」契约

每一处 unsafe 操作都对应一组必须由程序员维持的**不变式（invariant）**，也叫安全义务或安全契约。例如解引用 `*mut T` 的义务包括：指针非空、对齐到 `T` 的对齐要求、指向已正确初始化的 `T`、在解引用期间没有其他活跃的可变别名、且未越过所指对象的生命周期。The Rustonomicon 把「满足这些义务就不会 UB」称为 unsafe 代码的正确性标准。

unsafe 不是「怎么写都行」，而是「多了一份手写的证明义务」。安全 Rust 里这些证明由借用检查器和类型系统自动完成；进了 unsafe，证明还得做，只是改由你在脑子里（并写进注释里）完成。义务没被满足的 unsafe 代码，即使今天在你的机器上「碰巧正确运行」，仍然是错误的（是 UB，见 R11.4），编译器优化或换平台随时可能让它崩掉。

社区约定用 `// SAFETY:` 注释在每个 unsafe 块上方写清「为什么此处义务被满足」，这既是文档也是复核时的检查点：

```rust
// SAFETY: ptr 来自 Vec::as_mut_ptr，非空且对齐；len 未越界；
// 该 unsafe 块内不存在指向同一元素的其他别名。
let first = unsafe { *ptr };
```

### R11.2.3 把 unsafe 封装进安全 API（安全抽象）

Rust 生态的核心工程手法：用少量、局部、被仔细审查过的 unsafe 代码，构建对外呈现为**完全安全**的 API。只要这个 API 在任何合法输入下都不会违反内存安全，就说它是一个「安全抽象（safe abstraction）」。标准库大量如此——`Vec`、`String`、`Rc`、`slice::split_at_mut` 内部都是 unsafe，但对使用者是安全的。

一个教科书级例子是 `split_at_mut`：把一个 `&mut [T]` 切成两个不重叠的 `&mut` 子切片。借用检查器无法证明两个子切片不重叠，只能靠裸指针加人工保证。本机 rustc 1.94.1、edition 2024 实测可编译运行：

```rust
fn my_split(s: &mut [i32], mid: usize) -> (&mut [i32], &mut [i32]) {
    let len = s.len();
    let ptr = s.as_mut_ptr();
    assert!(mid <= len); // 义务前置检查：mid 不越界
    unsafe {
        (
            std::slice::from_raw_parts_mut(ptr, mid),
            std::slice::from_raw_parts_mut(ptr.add(mid), len - mid),
        )
    }
}
fn main() {
    let mut v = [1, 2, 3, 4, 5, 6];
    let (a, b) = my_split(&mut v, 3);
    println!("{:?} {:?}", a, b); // 输出 [1, 2, 3] [4, 5, 6]
}
```

这段代码的教学点全在细节里：`assert!(mid <= len)` 把「不越界」这条义务变成运行时检查，保证了 API 对任意 `mid` 都安全；两个 `from_raw_parts_mut` 各自划走 `[0, mid)` 与 `[mid, len)`，人工保证了不重叠，于是尽管内部动用裸指针，外部签名 `fn my_split(&mut [i32], usize) -> (&mut [i32], &mut [i32])` 是彻底安全的——调用者无论怎么用都不会 UB。

### R11.2.4 unsafe 块最小化与边界纪律

工程惯例：unsafe 块应尽可能小、尽可能少，只把真正需要超能力的那一两行包进去，其余逻辑（尤其是义务的前置检查，如上面的 `assert!`）放在 unsafe 块之外的安全代码里。这样审查者只需盯住极小的一段代码就能判断安全性。

初学者常犯的反面做法是「图省事把一大段逻辑整个塞进 unsafe 块」。这会让本可由编译器检查的普通代码也失去审查焦点，也让「到底哪一行是危险动作」变得模糊。edition 2024 把 `unsafe fn` 体默认变回「安全」（R11.1.3）正是同一纪律的延伸：让每一处危险动作都必须显式、局部地标出来。

配套的封装边界原则来自 Nomicon：一个模块只要**对外**不暴露任何能触发 UB 的安全接口，它内部用多少 unsafe 都不破坏「安全 Rust 不会内存不安全」这一整体保证。反过来，如果你的安全函数在某些输入下会 UB（例如上面漏写 `assert!`，传入 `mid > len`），那这个抽象就是「不健全的（unsound）」——即使它自己没写错，它把违约的可能性泄露给了无辜的安全调用者，这是比局部 unsafe bug 更严重的设计错误。

#### 来源与时效

- The Rustonomicon §「Working with Unsafe」/§「How Safe and Unsafe Interact」/§「Meet Safe and Unsafe」：安全抽象、健全性（soundness/unsound）、unsafe 与安全代码的契约边界。unsafe 一手权威。https://doc.rust-lang.org/nomicon/
- The Rust Programming Language book Ch.20「Unsafe Rust」：裸指针 `*const`/`*mut`、`split_at_mut` 安全抽象示例、「把 unsafe 封在安全 API 内」的教学表述。https://doc.rust-lang.org/book/
- The Rust Reference §「Pointer types」（raw pointers）：裸指针类型语义、创建 vs 解引用的规范。https://doc.rust-lang.org/reference/
- 本机 rustc 1.94.1（edition 2024）实测：`split_at_mut` 式安全抽象编译运行输出 `[1, 2, 3] [4, 5, 6]`；同变量的 `*const`/`*mut` 可在安全代码共存。核实 2026-08-01。
- 冲突/分歧：三源对「安全抽象」概念一致，无实质冲突；`// SAFETY:` 注释为社区/标准库约定（非语言强制），如实标为惯例而非规范要求。

---

## R11.3 FFI 与 extern

### R11.3.1 extern 块与调用 C 函数（extern "C"）

FFI（Foreign Function Interface，外部函数接口）让 Rust 调用其他语言（最常见是 C）编译出的函数。声明外部函数用 `extern "C" { ... }` 块，其中 `"C"` 指定 ABI（应用二进制接口，承 L3-03 ABI，此处只作一句交叉引用不展开）——它规定参数如何放进寄存器/栈、返回值怎么传、名字如何修饰。调用外部函数是 unsafe 的，因为 Rust 编译器无法检查另一侧的实现是否遵守它所声明的签名与义务。

这是本大主题第二处 edition 2024 硬变更，标 ⚙演进快·锚版本。本机 rustc 1.94.1、edition 2024 实测：**extern 块本身现在必须写成 `unsafe extern`**，否则报错。

```rust
extern "C" {
    fn abs(input: i32) -> i32;
}
// edition 2024: error: extern blocks must be unsafe
```

edition 2024 下正确写法是给块加 `unsafe`，调用点仍需 unsafe 块，本机实测编译运行：

```rust
unsafe extern "C" {
    fn abs(input: i32) -> i32;
}
fn main() {
    let r = unsafe { abs(-3) };
    println!("{}", r); // 输出 3（链接到 libc 的 abs）
}
```

`unsafe extern` 里的 `unsafe` 表达的是「声明这些外部符号的类型签名这件事本身就带风险」——因为你写的 Rust 签名和 C 侧真实签名必须严丝合缝，写错一个类型（比如把 C 的 `long` 当成 `i32`）不会被编译器发现，运行时就是 UB。而调用点的 `unsafe` 表达的是「实际调它有风险」。edition 2024 之所以要求块也标 unsafe，就是要让「我声明的签名可能是错的」这份风险显式化。

### R11.3.2 从 C 侧调用 Rust 函数（导出 extern "C"）

反方向：把 Rust 函数暴露给 C 调用。做法是给函数加 `extern "C"` 指定 ABI，并用 `#[unsafe(no_mangle)]`（edition 2024 的写法）或 `#[no_mangle]` 阻止编译器修饰（mangle）符号名，使 C 能按原名链接。

```rust
#[unsafe(no_mangle)]
pub extern "C" fn add_one(x: i32) -> i32 {
    x + 1
}
```

`extern "C" fn` 定义（导出方向）本身不需要在调用它的 Rust 代码里加 unsafe——它只是让这个函数用 C ABI 对外可见；风险落在 C 那一侧。`no_mangle` 是必需的，因为 Rust 默认会把符号名改写成含哈希的形式以支持泛型和避免冲突，C 链接器找不到。此处 `no_mangle` 在近版 Rust 被归入 `unsafe(...)` 属性语法（它可能造成符号冲突，属需担保项），具体属性是否强制 `unsafe(...)` 包裹随版本演进，delta 现查不凭记忆（待核到具体 edition 默认，本机未逐一验证导出侧属性语法）。

### R11.3.3 FFI 的数据表示与跨边界义务

跨 FFI 边界传数据要保证两侧对内存布局的理解一致。Rust 默认的 struct 布局是未指定的（编译器可自由重排字段），因此任何要传给 C 的结构体必须加 `#[repr(C)]` 强制使用 C 的布局规则。跨边界通常只能传 FFI 安全的类型：原生整数/浮点、裸指针、`#[repr(C)]` 结构体等；Rust 的 `String`、`Vec`、胖指针（如 `&str`、`&[T]`）不能直接传，需拆成 `指针 + 长度` 或转成 C 兼容表示。

```rust
#[repr(C)]
struct Point {
    x: i32,
    y: i32,
}
```

跨边界的所有权与生命周期义务全部落到程序员头上：谁分配的内存由谁释放（在 Rust 里 `malloc` 的必须回到 C 侧 `free`，反之亦然，混用分配器是 UB）；传过去的裸指针在 C 使用期间 Rust 侧不能提前释放对象；C 传回的指针可能为空、可能悬垂，Rust 侧必须自己检查。初学者的心智模型：一旦数据跨过 FFI 边界，Rust 的所有权系统就「看不见」它了，安全网到此为止，边界另一侧的规矩得你手动补齐。这正是为什么 FFI 是 unsafe 的重灾区，也是安全抽象（R11.2.3）最该用武的地方——成熟的 crate（如各种 `-sys` 绑定）通常在 unsafe FFI 之上再包一层安全 Rust API。

#### 来源与时效

- The Rust Reference §「External blocks」/§「ABI」/§「type layout」（`repr(C)`）：extern 块、ABI 字符串、FFI 布局的规范定义。https://doc.rust-lang.org/reference/
- The Rustonomicon §「FFI」：调用 C、从 C 调 Rust、`repr(C)`、跨边界所有权与内存管理义务。unsafe/FFI 一手权威。https://doc.rust-lang.org/nomicon/
- The Rust Programming Language book Ch.20「Using extern Functions to Call External Code」：`unsafe extern "C"`、`no_mangle` 导出示例。https://doc.rust-lang.org/book/
- 本机 rustc 1.94.1（edition 2024）实测：`extern "C" { ... }` 报「extern blocks must be unsafe」；`unsafe extern "C"` + `unsafe { abs(-3) }` 链接 libc 输出 `3`。核实 2026-08-01。
- ⚙演进快·锚版本：edition 2024 要求 `unsafe extern`；`no_mangle`/属性是否强制 `unsafe(...)` 包裹随版本变化，导出侧属性语法**待核**（本机未逐一验证），如实标不下死结论。
- 冲突/分歧：三源对 extern/ABI/repr(C) 语义一致；教材 edition 2021 老示例的裸 `extern` 块在 edition 2024 报错，属版本 delta。

---

## R11.4 UB 与 Nomicon 不变式

### R11.4.1 什么是未定义行为（UB）

未定义行为（Undefined Behavior, UB）指程序执行了语言规范认为「绝不允许发生」的操作，一旦发生，整个程序的行为不再有任何保证——编译器在做优化时**假定 UB 永不发生**，因此含 UB 的程序可能得到任意结果：崩溃、悄悄算错、看似正常、换个优化等级或平台就变样。UB 不是「未指定的具体值」，而是「编译器有权假设它不存在，从而做出让你的程序彻底走样的推理」。

初学者最该纠正的直觉是「我在我的机器上跑对了，就说明没问题」。UB 的可怕之处正在于它经常「今天碰巧对」。安全 Rust 的整个价值主张是：只要不写 unsafe，你**不可能**触发内存相关 UB；而进入 unsafe，避免 UB 的责任完全回到你身上。这就是「安全边界」四个字的分量所在。

### R11.4.2 Nomicon 的 UB 义务清单

The Rustonomicon 明确列出了会导致 UB 的操作，是 unsafe 程序员必须逐条遵守的义务清单。主要包括：解引用空指针、悬垂指针或未对齐指针；违反别名规则（在有活跃 `&mut` 的同时存在其他对同一内存的引用，即别名异或可变 R3.2 的底层版本）；读取未初始化的内存；制造无效值（例如让 `bool` 存 `0/1` 以外的位、让引用为空、让 `char` 超出合法码点范围、让枚举取无对应判别式的值）；数据竞争（无同步地并发读写同一内存，其一为写）；通过 FFI 调用违反契约；破坏标准库类型的内部不变式等。

初学者不必背全，但要抓住这份清单的共同主题：**每一条都是「编译器无法看见、但必须为真」的前提**。安全 Rust 里这些前提由借用检查、初始化检查、类型系统自动保证（例如借用检查器保证别名异或可变、类型系统保证 `bool` 只含合法位）；unsafe 里这些自动保证被你的裸指针操作绕过了，于是清单上的每一条都变成你亲手要守的军规。

一个特别反直觉、务必点明的例子：**制造一个无效值本身就是 UB，哪怕你从不使用它**。例如 `let b: bool = unsafe { std::mem::transmute(2u8) };` 仅仅创建这个值就是 UB，因为 `bool` 的有效位模式只有 `0` 和 `1`。这说明 UB 的边界比「解引用坏指针」宽得多。

### R11.4.3 安全不变式与有效性不变式的区分

Nomicon 区分两层不变式，初学者容易混但很重要。有效性不变式（validity invariant）是**编译器和语言恒假定成立**的最底层要求——违反它立刻是 UB，例如引用永不为空、`bool` 只含 `0/1`、被引用数据已初始化。安全不变式（safety invariant）是**安全代码可以依赖、但允许在 unsafe 内部临时打破再恢复**的更高层约定，例如 `Vec` 的 `len <= capacity`、`String` 内容是合法 UTF-8。

有效性不变式一刻都不能破（破了就是 UB，无论你之后怎么补救）；安全不变式可以在一段 unsafe 代码内暂时不成立，只要在把控制权交还给可能观察它的安全代码之前恢复即可。举例，`Vec::push` 内部可能先写元素、再更新 `len`，中间那一瞬 `len` 与实际元素数不匹配（安全不变式暂时被破），但这段窗口对外不可见，退出方法时已恢复，因此健全。这层区分是理解「为什么标准库能安全地在内部打破自己的规则」的钥匙。

### R11.4.4 Miri 检测 UB（本机不可用）

Miri 是 Rust 官方的一个解释器/UB 检测器，它在 MIR 层解释运行程序，能在运行时捕获许多编译器和普通运行都发现不了的 UB（越界、使用未初始化内存、违反别名模型、未对齐访问等），是 unsafe 代码作者的主力验证工具。

本机可用性核实结论（2026-08-01，本机 rustc/cargo 1.94.1 stable）：**Miri 在本机不可用**。`cargo miri` 实测报错「the 'miri' component which provides the command 'cargo-miri' is not available for the 'stable-x86_64-unknown-linux-gnu' toolchain」。Miri 只作为 nightly 工具链的 rustup 组件提供，本机为 stable 工具链，未安装 nightly，故无法运行 Miri 做本机 UB 检测。按纪律，本项**降为概念 + 只读一手（Nomicon）**，不基于本机 Miri 输出下任何结论。

补充一条 ⚙演进快·锚版本 的「待核」：Miri 所依据的 Rust 别名模型仍在演进——历史上是 Stacked Borrows，社区/官方一直在推进 Tree Borrows 作为更宽松的后继模型，究竟本机版本对齐哪一套别名规则、以及哪些 unsafe 写法会被判 UB，随版本变化，**待核**，此处只标不下结论。初学者层面只需记住：即使 Miri 今天没报错，也不等于你的 unsafe 代码在未来所有别名模型下都合法——UB 的精确边界本身仍是活的。

#### 来源与时效

- The Rustonomicon §「What Unsafe Rust Can Do」/§「Working with Uninitialized Memory」/§「Aliasing」/「Undefined Behavior」清单，及安全不变式 vs 有效性不变式（safety invariant / validity invariant）的区分。UB 语义一手权威。https://doc.rust-lang.org/nomicon/
- The Rust Reference §「Behavior considered undefined」：语言规范层面的 UB 枚举（悬垂/未对齐/空指针、数据竞争、无效值、违反别名等）。https://doc.rust-lang.org/reference/
- Miri 项目（rust-lang/miri，官方）：MIR 解释器 / UB 检测器定位与用途。https://github.com/rust-lang/miri
- 本机 rustc/cargo 1.94.1 stable 实测：`cargo miri` 报 miri 组件在 stable 工具链不可用；本机无 nightly，故 Miri 本机不可用，本项降为概念 + 只读 Nomicon。核实 2026-08-01。
- ⚙演进快·锚版本 / 待核：Rust 别名模型（Stacked Borrows → Tree Borrows）仍在演进，本机对齐的具体别名规则**待核**，不下结论；UB 精确边界随版本变化，现查不凭记忆。
- 冲突/分歧：Reference 的「Behavior considered undefined」与 Nomicon 的 UB 清单表述互补、无实质冲突；两者均声明「此清单不保证详尽（non-exhaustive）」，故不得据此反推「未列出的即安全」，此点两源一致。
