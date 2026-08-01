# L5-14·大主题R8 智能指针与内部可变性

> 基线 Py3.11.15/np2.4.6/gcc13.3 @2026-07-25（+ rustc/cargo 1.94.1）｜核实日期：2026-08-01｜先修：R2 所有权与 move 语义、R3 借用与借用检查器、R4 生命周期、R5 struct/enum｜一手锚点：The Rust Programming Language book Ch.15 + std 标准库文档（Box/Rc/RefCell/Cell/Weak）+ The Rust Reference｜成熟度：GA（⚙演进快·锚 the book 对齐 Rust 1.90.0 + edition 2024，本机 rustc/cargo 1.94.1，delta 现查不凭记忆）

智能指针（smart pointer）是"行为像指针、但还额外背负元数据和能力"的数据结构。普通引用 `&T` 只借数据、不拥有；智能指针通常拥有它所指的数据，并在离开作用域时负责清理。本章覆盖标准库里最常用的一批：`Box<T>`（堆分配）、`Rc<T>`（共享所有权）、`RefCell<T>`/`Cell<T>`（内部可变性），以及支撑它们的 `Deref`/`Drop` trait 和破环用的 `Weak<T>`。贯穿全章的主线是：所有权和借用规则默认在编译期强制，而这些类型给出了"在保持内存安全前提下"松动某条规则的受控手段。

---

## R8.1 Box\<T\>：堆分配与递归类型定长化

### R8.1.1 Box\<T\> 是什么

`Box<T>` 是最简单的智能指针：它把一个值 `T` 放到堆（heap）上，栈上只留一个指向堆数据的指针。除了"数据在堆上"，它没有别的性能开销，也没有别的额外能力。

栈上的值必须在编译期知道确切大小，且随作用域结束按后进先出释放；堆上的值大小可以在运行期才定、生命周期由所有者掌控。`Box<T>` 就是把"我要一块堆内存、并且由我独占拥有它"这件事包成一个类型：`Box<T>` 是 `T` 的唯一所有者，`Box` 离开作用域时，堆上的 `T` 连同它自己都会被释放。它跟裸的 C `malloc` 不同——你不需要手动 `free`，释放是编译器按所有权自动插入的。

```rust
fn main() {
    let b = Box::new(5); // 整数 5 被放到堆上，b 在栈上指向它
    println!("b = {}", b); // 打印 5；b 离开作用域时堆内存自动回收
}
```

`Box<i32>` 把一个本可以直接放栈上的 `i32` 挪到堆上，单看这个例子没意义。`Box` 的价值体现在下面几个"非它不可"的场景，而不是给普通小值套壳。

### R8.1.2 递归类型的定长化

Rust 需要在编译期知道每个类型占多少字节，但递归类型（自己包含自己）会让编译器无法算出大小——它会陷入"`List` 里有一个 `List`，那个 `List` 里又有一个 `List`……"的无穷展开。`Box<T>` 打破这个循环：无论指向的数据多大，一个指针的大小是固定的（本机 64 位上就是 8 字节），于是递归类型就有了确定大小。

经典例子是函数式语言里的 cons list（链表）。直接写 `Cons(i32, List)` 编译器会报"recursive type has infinite size"（错误码 E0072），把递归那一支包进 `Box` 后就能通过：

```rust
enum List {
    Cons(i32, Box<List>),
    Nil,
}

use List::{Cons, Nil};

fn main() {
    let list = Cons(1, Box::new(Cons(2, Box::new(Cons(3, Box::new(Nil))))));
    // 每个 Cons 现在的大小 = i32 + 一个指针，固定可算
    let _ = list;
}
```

`enum List` 的大小取它最大变体的大小。`Cons(i32, List)` 里嵌了整个 `List`，导致"大小 = i32 + 大小"这个方程无解；换成 `Cons(i32, Box<List>)` 后变成"大小 = i32 + 一个指针大小"，方程有解。真正的链表节点数据仍在堆上，只是每一层之间用固定大小的指针相连。

### R8.1.3 Box 的三大典型用途

标准库文档把 `Box<T>` 的用途归纳为三类：其一，某类型的大小在编译期未知、却要用在需要确定大小的上下文里（例如上面的递归类型）；其二，有一大块数据要转移所有权、但希望转移时不发生拷贝；其三，你只关心某个值实现了某个 trait、而不在乎它的具体类型（即 trait 对象）。

move 一个 `Box<T>` 只拷贝那个指针（几个字节），堆上的大块数据原地不动、所有权随指针转手。若不套 `Box`，把一个巨大的结构体按值传递可能触发整块数据在栈上的拷贝。

第三类是把 `Box<dyn Trait>` 当作"拥有所有权版本的 trait 对象"。`dyn Trait` 是不定长类型（不同实现者大小不同），不能直接按值放栈上，`Box` 给它固定大小的落脚点：

```rust
trait Draw {
    fn draw(&self);
}

struct Button;
impl Draw for Button {
    fn draw(&self) { println!("draw button"); }
}

fn main() {
    let items: Vec<Box<dyn Draw>> = vec![Box::new(Button)];
    for it in &items {
        it.draw();
    }
}
```

`Box<T>` 之所以能像引用一样用（`*b`、方法调用自动解引用），是因为它实现了 `Deref`；它离开作用域能自动清堆内存，是因为它实现了 `Drop`。这两个 trait 见下一小主题。

#### 来源与时效
- The Rust Programming Language book Ch.15.1 "Using Box\<T\> to Point to Data on the Heap"，声明对齐 Rust 1.90.0 + edition 2024，核实 2026-08-01 https://doc.rust-lang.org/book/ch15-01-box.html
- std 标准库 `std::boxed::Box` 文档（堆分配、`Deref`/`Drop` 实现），核实 2026-08-01 https://doc.rust-lang.org/std/boxed/struct.Box.html
- 交叉一致：book 与 std 文档均述"栈上留指针、数据在堆、无额外开销、离开作用域自动释放"，无冲突。
- 递归类型报错码 E0072（"recursive type has infinite size"）按本机 rustc 1.94.1 语义描述；⚙演进快·锚版本，具体诊断文案 delta 现查不凭记忆。

---

## R8.2 Deref 与 Drop：解引用强制与自定义析构

### R8.2.1 Deref trait 与 \* 运算符

实现 `Deref` trait 可以自定义解引用运算符 `*` 的行为，让一个自定义类型"用起来像引用"。`Deref` 只要求实现一个方法 `deref`，它返回一个指向内部值的引用。

关键点是 `deref` 必须返回引用而不是值本身：如果直接返回值，就会把值从 `self` 里移动出去，破坏所有权系统。当你写 `*y`（`y` 是实现了 `Deref` 的智能指针）时，编译器实际在背后执行 `*(y.deref())`——先调 `deref` 拿到 `&内部值`，再对这个普通引用做一次真正的解引用。

```rust
use std::ops::Deref;

struct MyBox<T>(T);

impl<T> MyBox<T> {
    fn new(x: T) -> MyBox<T> { MyBox(x) }
}

impl<T> Deref for MyBox<T> {
    type Target = T;
    fn deref(&self) -> &T {
        &self.0
    }
}

fn main() {
    let y = MyBox::new(5);
    assert_eq!(5, *y); // *y 被展开成 *(y.deref())
}
```

初学者常问为什么不会无限递归：`*(y.deref())` 里外层那个 `*` 作用在 `deref` 返回的普通引用 `&T` 上，是编译器内建的原生解引用，不再触发 `Deref`，所以每个 `*` 只多插一次 `deref` 调用。

### R8.2.2 解引用强制（deref coercion）

解引用强制是编译器在函数/方法调用传参时自动做的便利转换：把实现了 `Deref` 的类型的引用，自动转成其目标类型的引用，必要时连续转多步，直到类型匹配。它让你不必手写一长串 `*` 和 `&` 去对齐类型。

典型场景是 `&String` 自动变成 `&str`、`&Box<String>` 自动一路变成 `&str`。因为 `String` 实现了 `Deref<Target = str>`：

```rust
fn hello(name: &str) {
    println!("Hello, {name}!");
}

fn main() {
    let m = MyBox::new(String::from("Rust"));
    hello(&m); // &MyBox<String> -> &String -> &str，全自动
}
```

若没有解引用强制，上面这行得手写成 `hello(&(*m)[..])`——先 `*m` 解成 `String`，再 `[..]` 切成 `str`，再 `&` 借出去，可读性差很多。这个转换在编译期完成，没有运行期开销。

### R8.2.3 解引用强制的三条规则与可变性不对称

解引用强制在三种情况下发生：当 `T: Deref<Target=U>` 时把 `&T` 转成 `&U`；当 `T: DerefMut<Target=U>` 时把 `&mut T` 转成 `&mut U`；当 `T: Deref<Target=U>` 时把 `&mut T` 转成 `&U`。前两条只是"不可变对不可变、可变对可变"，第三条允许把可变引用降级成不可变引用。

反过来——把不可变引用 `&T` 强制成可变引用 `&mut U`——是绝对不允许的。原因回到借用规则：把不可变借用变成可变借用，需要"当前这个不可变借用是唯一的"这个前提，而借用规则并不保证这一点；可变降不可变则永远安全，因为多一个只读别名不会破坏任何不变式。想让 `*` 在可变引用上也能自定义，就实现 `DerefMut`（它覆盖可变引用上的 `*`），语义与 `Deref` 平行。

### R8.2.4 Drop trait 与自定义析构

`Drop` trait 让你自定义"值离开作用域时"要跑的清理代码，实现它需要写一个方法 `fn drop(&mut self)`。这正是 RAII（资源获取即初始化）在 Rust 里的落地：释放文件句柄、锁、网络连接等收尾动作绑定到值的生命周期上，不用手动记着去关。

值的析构顺序是：同一作用域内的变量按声明的相反顺序（后声明的先析构）drop；结构体的字段按声明顺序 drop。下面的例子能直接观察到这个顺序：

```rust
struct CustomSmartPointer {
    data: String,
}

impl Drop for CustomSmartPointer {
    fn drop(&mut self) {
        println!("Dropping CustomSmartPointer with data `{}`!", self.data);
    }
}

fn main() {
    let _c = CustomSmartPointer { data: String::from("my stuff") };
    let _d = CustomSmartPointer { data: String::from("other stuff") };
    println!("CustomSmartPointers created.");
    // 作用域结束：先 drop d，再 drop c（声明的相反顺序）
}
```

Rust 不允许你手动调用值的 `drop` 方法（`c.drop()` 会报错 E0040），因为那样值在作用域结束时还会被自动 drop 一次，造成 double free。要提前释放，用标准库函数 `std::mem::drop(value)`（它把值按值吃掉、当场析构）。也就是说，"这个 trait 方法叫 `drop`、但你不能直接叫它，得走 `std::mem::drop` 这个自由函数"。

#### 来源与时效
- The Rust Programming Language book Ch.15.2 "Treating Smart Pointers Like Regular References with Deref" 与 Ch.15.3 "Running Code on Cleanup with the Drop Trait"，核实 2026-08-01 https://doc.rust-lang.org/book/ch15-02-deref.html / https://doc.rust-lang.org/book/ch15-03-drop.html
- std 文档 `std::ops::Deref` / `std::ops::DerefMut` / `std::ops::Drop` / `std::mem::drop`，核实 2026-08-01 https://doc.rust-lang.org/std/ops/trait.Deref.html
- 交叉一致：book 与 std 文档对 `deref` 返回引用、三条 coercion 规则、可变不可逆、`Drop` 顺序、禁止显式调 `drop` 一致，无冲突。
- 报错码 E0040（显式调 drop）、E0072 相关文案按本机 rustc 1.94.1；⚙演进快·锚版本，delta 现查。

---

## R8.3 Rc\<T\>：单线程共享所有权

### R8.3.1 Rc\<T\> 是什么

`Rc<T>` 是引用计数（Reference Counted）智能指针，让同一份数据可以有多个所有者。它内部记录"当前有多少个 `Rc` 指向这份数据"，计数降到 0 时才释放数据。名字里的 Rc 就是 reference counting 的缩写。

所有权默认是"一值一主"，但有些数据结构天然需要多主：比如图里一个节点被多条边引用，你说不清谁"应该"独占它、也无法预知谁最后用完。`Rc<T>` 用运行期的计数把"最后一个用完的人负责清理"这件事自动化：每多一个所有者计数加一，每少一个减一，到 0 自动 drop。它只适用于单线程场景（原因见 R8.3.4）。

```rust
use std::rc::Rc;

enum List {
    Cons(i32, Rc<List>),
    Nil,
}
use List::{Cons, Nil};

fn main() {
    let a = Rc::new(Cons(5, Rc::new(Cons(10, Rc::new(Nil)))));
    let _b = Cons(3, Rc::clone(&a)); // b 和 c 都共享 a
    let _c = Cons(4, Rc::clone(&a));
    println!("count = {}", Rc::strong_count(&a)); // 3
}
```

### R8.3.2 Rc::clone 与引用计数观察

按约定用 `Rc::clone(&a)` 而不是 `a.clone()` 来增加一个所有者。二者行为在 `Rc` 上等价，但写成 `Rc::clone` 是一种视觉信号：它只把引用计数加一（很便宜），不做深拷贝。相比之下，大多数类型的 `.clone()` 会深拷贝全部数据（很贵），统一用 `Rc::clone` 便于在代码审查时一眼区分"这是廉价的计数增加"还是"这是昂贵的深拷贝"。

用 `Rc::strong_count(&a)` 可以读出当前强引用数。下面能观察到计数随作用域进出而增减：

```rust
use std::rc::Rc;

fn main() {
    let a = Rc::new(5);
    println!("count after creating a = {}", Rc::strong_count(&a)); // 1
    let _b = Rc::clone(&a);
    println!("count after b = {}", Rc::strong_count(&a)); // 2
    {
        let _c = Rc::clone(&a);
        println!("count after c = {}", Rc::strong_count(&a)); // 3
    }
    println!("count after c goes out of scope = {}", Rc::strong_count(&a)); // 2
}
```

`Rc` 的 `Drop` 实现会在每个 `Rc` 离开作用域时自动把计数减一，所以你不用手动维护计数。

### R8.3.3 Rc 只给不可变共享

`Rc<T>` 只把不可变引用交给你——你可以有很多只读的共享所有者，但不能透过 `Rc` 直接改数据。若允许多个可变引用同时指向同一份数据，就违反了借用规则（同一时刻要么多个只读、要么一个可写），可能导致数据竞争和不一致。

因此"多个所有者 + 可修改"这个需求，`Rc` 自己办不到，需要和内部可变性类型组合：`Rc<RefCell<T>>` 或 `Rc<Cell<T>>`（见 R8.4）。标准库另提供 `Rc::get_mut`（仅当计数为 1、没有其他 `Rc`/`Weak` 时才给可变引用）和 `Rc::make_mut`（clone-on-write：有其他所有者时先克隆出独占副本再改），作为不需要 `RefCell` 时的轻量出口。

### R8.3.4 Rc vs Arc：为什么 Rc 不能跨线程

`Rc<T>` 显式地既不是 `Send` 也不是 `Sync`（标记为 `!Send + !Sync`），因此编译器禁止把它送到别的线程。要跨线程做引用计数，用 `Arc<T>`（Atomic Reference Counted，原子引用计数）。两者 API 几乎一样，区别只在计数的加减是否用原子操作。

`Rc` 的计数是普通的非原子加减，多线程同时增减会产生竞态、把计数算错，进而 double free 或提前释放。`Arc` 用原子指令保证计数在并发下也正确，但原子操作比普通加减慢。所以设计取舍是"你自己按场景选"：单线程用 `Rc`（更快），需要跨线程才用 `Arc`（更慢但安全）。这正是 Rust 的一贯风格——不为你不需要的线程安全付代价，且把"能不能跨线程"这件事交给类型系统在编译期拦截，而非留到运行期出错。`Rc` 与 `Arc` 都只给共享不可变访问，跨线程要改数据得配 `Mutex`/`RwLock`（见 R9）。

#### 来源与时效
- The Rust Programming Language book Ch.15.4 "Rc\<T\>, the Reference Counted Smart Pointer"，核实 2026-08-01 https://doc.rust-lang.org/book/ch15-04-rc.html
- std 文档 `std::rc::Rc`（明确 `!Send`/`!Sync`、`Rc::clone`/`strong_count`/`weak_count`/`downgrade`/`get_mut`/`make_mut`）与 `std::sync::Arc`（原子计数），核实 2026-08-01 https://doc.rust-lang.org/std/rc/struct.Rc.html / https://doc.rust-lang.org/std/sync/struct.Arc.html
- 交叉一致：book 与 std 文档均述"单线程、只给不可变、Arc 用于多线程、Rc 因非原子计数更快"，无冲突；`Rc::make_mut` 的 clone-on-write 语义以 std 文档为准（book Ch.15 未展开）。
- ⚙演进快·锚版本：book 对齐 Rust 1.90.0，本机 rustc 1.94.1，delta 现查不凭记忆。

---

## R8.4 RefCell/Cell 内部可变性：把编译期规则换成运行期逃生阀

### R8.4.1 内部可变性是什么

内部可变性（interior mutability）是一种设计模式：即便你手上只有某数据的不可变引用，也能修改它内部的数据。这在常规借用规则下是被禁止的（有 `&T` 时不能同时有 `&mut T`）。实现它的类型内部用了 `unsafe` 代码去绕过编译器的常规检查，但把这段 `unsafe` 封装在一个安全的 API 背后，并自己在运行期保证借用规则仍被遵守。

这条模式回答了一个现实矛盾：有些程序在逻辑上是内存安全的，但编译期的借用检查器过于保守、无法证明其安全而拒绝它。内部可变性给你一个"逃生阀"——把借用检查从编译期挪到运行期，换取表达力，代价是违规时不再是编译错误、而是运行期 panic。

### R8.4.2 RefCell\<T\> 与运行期借用检查

`RefCell<T>` 把借用规则的强制从编译期挪到运行期。用 `borrow()` 拿到一个不可变智能指针 `Ref<T>`，用 `borrow_mut()` 拿到一个可变智能指针 `RefMut<T>`（两者都实现 `Deref`，用起来像普通引用）。`RefCell` 在运行期记账：任一时刻只允许"多个 `Ref` 同时存在"或"恰好一个 `RefMut`"，一旦违反就 panic。

编译期检查（普通引用、`Box<T>`）和运行期检查（`RefCell<T>`）遵守同一套借用规则，区别只在何时报错。下面的双重可变借用在本机 rustc 1.94.1 上编译通过、但运行时 panic：

```rust
use std::cell::RefCell;

fn main() {
    let c = RefCell::new(5);
    let _a = c.borrow_mut();
    let _b = c.borrow_mut(); // 第二个可变借用 -> 运行期 panic
    println!("unreachable");
}
```

本机 rustc 1.94.1 下 `rustc --edition 2024 refcell_panic.rs && ./refcell_panic` 的真实输出（关键行）：

```text
thread 'main' (19741) panicked at refcell_panic.rs:5:16:
RefCell already borrowed
```

进程以退出码 101 结束。注意 panic 文案是 `RefCell already borrowed`（本机 1.94.1 实测，与当前 book 文本一致）；较老版本的 Rust 曾打印 `already borrowed: BorrowMutError`——这是随版本演进的文案 delta，判断行为不要死记文案。若想避免 panic、改成可处理的结果，用 `try_borrow()`/`try_borrow_mut()`，它们返回 `Result`（`Ok(Ref/RefMut)` 或 `Err(BorrowError/BorrowMutError)`）而不是直接崩。

### R8.4.3 Cell\<T\> 与 RefCell\<T\> 的分工

`Cell<T>` 也是内部可变性类型，但走的是"搬进搬出值"的路子，从不把内部值的引用交给你，因此没有运行期借用记账、也永不 panic。它的主要方法有 `get()`（要求 `T: Copy`，返回内部值的副本）、`set()`（替换内部值、丢弃旧值）、`replace()`（替换并返回旧值）、`take()`（要求 `T: Default`，取走并留下默认值）、`into_inner()`（消费 `Cell` 取出值）。

`RefCell<T>` 则真的把 `Ref`/`RefMut` 引用交给你、并做动态借用追踪。二者的选择准则：小而可 `Copy` 的类型（如计数器整数）用 `Cell`，拿到的是副本、开销最小且无 panic 风险；较大或不可 `Copy`、需要就地借用来读写的类型用 `RefCell`。两者都只适用于单线程——它们都不实现 `Sync`，跨线程要用 `Mutex`/`RwLock`/原子类型。

```rust
use std::cell::Cell;

fn main() {
    let c = Cell::new(5);
    c.set(10);          // 直接替换，无需借用、不会 panic
    let v = c.get();    // 拿到副本 10（i32: Copy）
    let old = c.replace(20); // old = 10，内部变 20
    println!("{v} {old}");
}
```

### R8.4.4 Rc\<RefCell\<T\>\> 组合模式

`Rc<T>` 给"多个所有者但只读"，`RefCell<T>` 给"单所有者但可在不可变引用下改"。把它们叠成 `Rc<RefCell<T>>`，就同时得到"多个所有者 + 可修改"——这是单线程共享可变状态最常用的组合拳。

```rust
use std::rc::Rc;
use std::cell::RefCell;

fn main() {
    let value = Rc::new(RefCell::new(5));
    let a = Rc::clone(&value); // value 与 a 共享同一个 RefCell
    *value.borrow_mut() += 10; // 透过"不可变的" Rc 改到了内部数据
    println!("a = {}", a.borrow()); // 15
}
```

要读懂层次：`Rc` 那层负责"谁拥有、能不能共享"，`RefCell` 那层负责"能不能改、借用是否合法"。因为改动经 `RefCell` 的运行期检查，共享带来的别名不会引入数据竞争（单线程下）。它的孪生形式 `Rc<Cell<T>>` 适合内部是小 `Copy` 值的情形。多线程版本对应 `Arc<Mutex<T>>`（见 R9）。

#### 来源与时效
- The Rust Programming Language book Ch.15.5 "RefCell\<T\> and the Interior Mutability Pattern"，核实 2026-08-01 https://doc.rust-lang.org/book/ch15-05-interior-mutability.html
- std 文档 `std::cell` 模块（Cell vs RefCell 的分工、`get`/`set`/`replace`/`take`、`borrow`/`borrow_mut`/`try_borrow*`、均 `!Sync`），核实 2026-08-01 https://doc.rust-lang.org/std/cell/index.html
- 本机实证：rustc 1.94.1（`rustc 1.94.1 (e408947bf 2026-03-25)`）编译运行 `refcell_panic.rs`（`--edition 2024`），真实 panic 文案 `RefCell already borrowed`、退出码 101；命令与输出如正文所贴。
- 冲突/演进项：panic 文案随版本变化——本机 1.94.1 与当前 book 文本均为 `RefCell already borrowed`，历史版本为 `already borrowed: BorrowMutError`；⚙演进快·锚版本，以行为（运行期 panic）为准、文案 delta 现查。

---

## R8.5 循环引用与 Weak：Rc 环泄漏与破环

### R8.5.1 Rc 循环引用导致内存泄漏

Rust 的安全保证极大降低了内存泄漏的概率，但并没有把它完全杜绝——用 `Rc<T>` 配合 `RefCell<T>` 造出互相指向的环，就会泄漏。环里每个节点的强引用计数永远降不到 0，于是它们的 `Drop` 永不运行、内存永不释放。

原理：`a` 持有一个指向 `b` 的 `Rc`、`b` 又持有一个指向 `a` 的 `Rc`，各自的强计数都是 2。当 `a`、`b` 这两个变量离开作用域，各自减 1 变成 1，但因为彼此还互相持有，计数停在 1、到不了 0，析构永远不触发。下面用 `impl Drop` 打印来观察"析构没被调用"：

```rust
use std::rc::Rc;
use std::cell::RefCell;

struct Node {
    val: i32,
    next: RefCell<Option<Rc<Node>>>,
}
impl Drop for Node {
    fn drop(&mut self) {
        println!("dropping Node {}", self.val);
    }
}

fn main() {
    let a = Rc::new(Node { val: 1, next: RefCell::new(None) });
    let b = Rc::new(Node { val: 2, next: RefCell::new(None) });
    *a.next.borrow_mut() = Some(Rc::clone(&b));
    *b.next.borrow_mut() = Some(Rc::clone(&a)); // 成环
    println!("a strong_count = {}", Rc::strong_count(&a));
    println!("b strong_count = {}", Rc::strong_count(&b));
}
```

本机 rustc 1.94.1 下 `rustc --edition 2024 rc_cycle.rs && ./rc_cycle` 的真实输出：

```text
a strong_count = 2
b strong_count = 2
```

程序正常退出（退出码 0），但两条 `dropping Node ...` 一条都没打印出来——证明析构确实没跑、这块内存到进程结束都没被回收。需要说明：Rust 认为内存泄漏是安全的（不违反内存安全，只是浪费内存），所以这类问题编译器不拦，得靠设计避免。

### R8.5.2 Weak\<T\> 与 downgrade / weak_count

`Weak<T>` 是"弱引用"：它指向 `Rc` 管理的数据，但不表达所有权，也不影响该数据何时被清理。用 `Rc::downgrade(&rc)` 从一个 `Rc` 造出 `Weak`——它增加的是 `weak_count` 而非 `strong_count`。数据是否释放只看强计数是否归零，`weak_count` 不为 0 不妨碍数据被清理。

强引用（`Rc`）声明"我是所有者之一，数据得等我用完才能走"；弱引用（`Weak`）声明"我只是想在数据还活着时看看它，但我不阻止它死"。破环的关键就是：环上至少有一条边改用 `Weak`，这样环里不再有一圈都靠强引用互撑，强计数能正常归零、析构能正常触发。

### R8.5.3 upgrade 与 parent/child 破环模式

因为 `Weak` 指向的数据可能已经被 drop，所以你不能直接解引用 `Weak`，必须先调 `upgrade()`——它返回 `Option<Rc<T>>`：数据还在就得到 `Some(Rc)`（临时升级成强引用、用完即弃），已经被清理就得到 `None`。这套机制在类型层面逼你处理"目标可能已消失"的情况，杜绝悬垂访问。

经典应用是树里的 parent/child 双向链接：子节点用 `Rc` 强持有（父拥有子），父指针用 `Weak` 弱持有（子不拥有父），从而不成环。

```rust
use std::rc::{Rc, Weak};
use std::cell::RefCell;

struct Node {
    value: i32,
    parent: RefCell<Weak<Node>>,       // 指向父：弱引用，不成环
    children: RefCell<Vec<Rc<Node>>>,  // 指向子：强引用，父拥有子
}

fn main() {
    let leaf = Rc::new(Node {
        value: 3,
        parent: RefCell::new(Weak::new()),
        children: RefCell::new(vec![]),
    });
    let branch = Rc::new(Node {
        value: 5,
        parent: RefCell::new(Weak::new()),
        children: RefCell::new(vec![Rc::clone(&leaf)]),
    });
    *leaf.parent.borrow_mut() = Rc::downgrade(&branch);

    // 访问父节点：先 upgrade，拿到 Option<Rc<Node>>
    if let Some(p) = leaf.parent.borrow().upgrade() {
        println!("leaf parent value = {}", p.value); // 5
    }
}
```

这里 `branch` 强持有 `leaf`，`leaf` 只弱持有 `branch`；当 `branch` 离开作用域，它的强计数能归零、连同子节点一起正常析构，不会像 R8.5.1 那样泄漏。这也是 `Rc`/`Weak`/`RefCell` 三者协作的完整落地：`Rc` 管共享所有权、`Weak` 管非拥有的反向链接、`RefCell` 管在共享下的可变改写。

#### 来源与时效
- The Rust Programming Language book Ch.15.6 "Reference Cycles Can Leak Memory"，核实 2026-08-01 https://doc.rust-lang.org/book/ch15-06-reference-cycles.html
- std 文档 `std::rc::Weak`（`Rc::downgrade`、`weak_count`、`upgrade` 返回 `Option<Rc<T>>`、弱引用不阻止 drop），核实 2026-08-01 https://doc.rust-lang.org/std/rc/struct.Weak.html
- 本机实证：rustc 1.94.1 编译运行 `rc_cycle.rs`（`--edition 2024`），真实输出 `a strong_count = 2` / `b strong_count = 2`、退出码 0、无任何 `dropping Node` 打印——证实 Rc 环使析构不触发（内存泄漏）。命令与输出如正文所贴。
- 交叉一致：book 与 std 文档对"强计数归零才 drop、Weak 不计入所有权、upgrade 返回 Option"表述一致，无冲突。
- ⚙演进快·锚版本：book 对齐 Rust 1.90.0，本机 rustc 1.94.1，delta 现查不凭记忆。
