# L5-14·大主题R9 无畏并发

> 基线 Py3.11.15/np2.4.6/gcc13.3 @2026-07-25（+ rustc/cargo 1.94.1）｜核实日期：2026-08-01｜先修：R2 所有权与 move 语义、R3 借用与借用检查器、R8 智能指针与内部可变性（Rc/RefCell）｜一手锚点：The Rust Programming Language book Ch.16「Fearless Concurrency」（16.1 线程 / 16.2 消息传递 / 16.3 共享状态 / 16.4 Send 与 Sync）@对齐 rustc 1.90.0 + edition 2024、The Rust Reference §「Special types and traits」（Send/Sync）@活文档、std 标准库文档（`std::thread`/`std::sync::mpsc`/`std::sync::Mutex`/`std::sync::Arc`）、本机 rustc 1.94.1 实测｜成熟度：GA（线程 / channel / Mutex / Arc / Send / Sync 自 Rust 1.0 稳定）；`std::sync::mpsc` 内部实现 ⚙演进快·锚 rustc 1.94.1·随时变（自 1.67 起换用基于 crossbeam 的新实现，对外 API 不变）

"无畏并发（fearless concurrency）"是 Rust 的一句口号：并发代码里最难缠的一类 bug——数据竞争（data race）——在 Rust 里不是靠程序员小心，而是被编译器在编译期直接拒绝。它的底层机制其实就是前面几章的所有权与借用规则原封不动搬到多线程场景，再加上两个标记 trait（`Send`/`Sync`）替编译器判断"哪些类型跨线程是安全的"。因此这一章几乎没有全新概念，而是把 R2/R3/R8 学到的规则在线程边界上再走一遍。

本章面向初学者，把线程与 `move` 闭包、消息传递（channel）、共享状态（`Mutex`/`Arc`）、`Send`/`Sync` 四块讲到能看懂、能上手、能改错为止。并发的一般理论与操作系统层面的线程/锁语义在并发课一脉（→L4-05 并发），此处只作一句交叉引用：Rust 的贡献是把"数据竞争的避免"从运行期约定提升为类型系统层的编译期拦截，不展开并发理论本身。

本报告所有 rustc 编译结果、报错与运行输出均为本机 rustc 1.94.1（`--edition 2024`）现跑，复现命令随例给出；报错码以本机输出为准，不凭记忆。

## R9.1 线程与 move 闭包

### thread::spawn 创建线程，返回 JoinHandle

`std::thread::spawn` 接收一个闭包（要在新线程里跑的代码），立即启动一个新的操作系统线程去执行它，并返回一个 `JoinHandle<T>`。在这个句柄上调用 `.join()` 会阻塞当前线程，直到那个子线程结束；`join` 返回 `Result`，里面装着闭包的返回值（若子线程 panic 则是 `Err`）。

```rust
use std::thread;
fn main() {
    let v = vec![1, 2, 3];
    let h = thread::spawn(move || {
        println!("from thread: {:?}", v);
    });
    h.join().unwrap();
    println!("main done");
}
```

`rustc --edition 2024 a.rs && ./a` 本机现跑输出：

```text
from thread: [1, 2, 3]
main done
```

对初学者最关键的直觉是"主线程不会等子线程"。如果你 `spawn` 之后不 `join`，主线程可能先跑完并结束整个进程，子线程还没来得及打印就被一起终止了。`join` 就是"在这里等它跑完再往下走"。`join()` 返回 `Result<T, Box<dyn Any + Send>>`——正常时 `Ok(闭包返回值)`，子线程 panic 时是 `Err`，所以示例里用 `.unwrap()` 取出结果（或在子线程 panic 时把 panic 传播上来）。

### move 闭包：把捕获的变量所有权交给线程

传给 `thread::spawn` 的闭包几乎总要写成 `move` 闭包。`move` 关键字强制闭包"拿走（move）它用到的外部变量的所有权"，而不是借用它们。原因是：子线程可能比创建它的函数活得更久，如果闭包只是借用主线程栈上的变量，等主线程那个变量被释放后，子线程手里的引用就成了悬垂引用——这正是 Rust 要在编译期挡掉的。

如果忘了写 `move`，编译器会直接报 E0373：

```rust
use std::thread;
fn main() {
    let v = vec![1, 2, 3];
    let h = thread::spawn(|| {          // 少了 move
        println!("{:?}", v);
    });
    h.join().unwrap();
}
```

本机 rustc 1.94.1 现跑报错（节选）：

```text
error[E0373]: closure may outlive the current function, but it borrows `v`, which is owned by the current function
help: to force the closure to take ownership of `v` (and any other referenced variables), use the `move` keyword
```

编译器不但报错，还直接建议你加 `move`——这是"无畏"的具体体现：一个在别的语言里可能到运行期才崩、还偶发难复现的悬垂引用，在这里变成一条编译期错误和一条修复提示。易错点：`move` 是"移动所有权"，所以被 `move` 进闭包的变量之后在原作用域不能再用（除非它是 `Copy` 类型，会按位复制一份）。如果你既想在主线程继续用、又想给线程一份，就得 `clone` 一份再 `move` 进去，或改用后面的 `Arc` 共享。

### 线程与栈、以及为什么 spawn 要求 'static

`thread::spawn` 的签名要求传入的闭包满足 `'static` 约束（闭包捕获的所有东西都必须能活到"任意长"，即不含任何借用主线程数据的短生命周期引用），并且要求闭包及其返回值是 `Send`（见 R9.4）。这两条约束加在一起，就是"子线程拿到的数据要么是它自己拥有的、要么活得足够久"，从类型层面杜绝了跨线程悬垂。

初学者不必记住 `'static` 的全部细节，只需建立一个操作直觉：给 `spawn` 的闭包写 `move`，把要用的数据的所有权交进去，绝大多数情况就对了。若确实需要"多个线程共享同一份数据"，那属于 R9.3 的 `Arc` 场景，而不是靠借用主线程变量来实现。标准库还提供了作用域线程 `std::thread::scope`（Rust 1.63 稳定），它允许子线程安全地借用父作用域的数据（因为作用域保证线程在数据释放前一定被 join），是对上面 `'static` 限制的一个受控放松；本章以最常用的 `spawn`+`move` 为主线，`scope` 作为进阶存在了解即可。

#### 来源与时效
- The Rust Programming Language book Ch.16.1「Using Threads to Run Code Simultaneously」（`thread::spawn`、`join`、`move` 闭包），对齐 rustc 1.90.0 + edition 2024，https://doc.rust-lang.org/book/ch16-01-threads.html ，核实 2026-08-01。
- std 标准库文档 `std::thread`（`spawn` 的 `F: Send + 'static` 约束、`JoinHandle::join` 返回 `Result`）、`std::thread::scope`（1.63 起），https://doc.rust-lang.org/std/thread/ ，核实 2026-08-01。
- 本机 rustc 1.94.1 现跑：正常线程输出 `from thread: [1, 2, 3]` / `main done`；缺 `move` 报错 E0373（附带 `use the move keyword` 建议）。
- 两来源一致，无冲突。`scope` 稳定版本（1.63）为查证记录，非本机实测重点。

## R9.2 消息传递（channel）

### mpsc::channel 与"所有权随消息转移"

Rust 标准库在 `std::sync::mpsc` 提供多生产者、单消费者（multiple producer, single consumer）的通道。`mpsc::channel()` 返回一对端点：发送端 `Sender<T>`（`tx`）和接收端 `Receiver<T>`（`rx`）。一个线程用 `tx.send(值)` 发送，另一个线程用 `rx.recv()` 接收。核心思想是并发课里常引的一句话——"不要通过共享内存来通信，而要通过通信来共享内存"（→L4-05 并发）。

```rust
use std::sync::mpsc;
use std::thread;
fn main() {
    let (tx, rx) = mpsc::channel();
    thread::spawn(move || {
        let s = String::from("hi");
        tx.send(s).unwrap();
    });
    let got = rx.recv().unwrap();
    println!("got: {}", got);
}
```

`rustc --edition 2024 c.rs && ./c` 本机现跑输出 `got: hi`。

这里最能体现 Rust 特色的一点是：`tx.send(s)` 会把 `s` 的所有权移动进通道，之后发送线程就不再拥有 `s`。这不是运行期约定，而是编译期强制的。如果你在 `send` 之后还想用 `s`，编译器直接拒绝：

```rust
tx.send(s).unwrap();
println!("{}", s);   // 错误：s 的所有权已随消息转移
```

本机 rustc 1.94.1 现跑报错（节选）：

```text
error[E0382]: borrow of moved value: `s`
```

这条规则一举消除了一类经典并发 bug：发送方以为消息发出去了、自己还能改那份数据，结果和接收方同时读写造成数据竞争。在 Rust 里"发出去"就等于"交出所有权"，同一份数据不会同时被两个线程持有。

### send/recv 的阻塞语义与通道关闭

`recv()` 会阻塞当前线程，直到收到一条消息（返回 `Ok(值)`），或者所有发送端都被丢弃、通道再也不可能有新消息时返回 `Err`。对应地，还有非阻塞的 `try_recv()`（没有消息时立即返回 `Err` 而不等待），适合"轮询一下有没有消息、没有就先干别的"的循环。

`Receiver` 还实现了迭代器：`for x in rx` 会不断取消息，直到通道关闭后自动结束循环，这是消费所有消息的最常用写法。初学者易错点：只要还有任何一个 `Sender` 存活，`recv()` 就会一直等下去（可能永久阻塞）；通道"关闭"是指所有发送端都被 drop 了。所以如果你的接收循环一直不退出，先检查是不是有某个 `tx` 没被释放。

### 多生产者：clone 发送端

"mpsc"里的"多生产者"通过 clone `Sender` 实现：`let tx2 = tx.clone();` 得到第二个发送端，可以 `move` 进另一个线程，两个线程都往同一个 `rx` 发消息。接收端 `Receiver` 不能 clone，这就是"单消费者"的含义。

`mpsc` 还区分两种通道：`channel()` 是异步/无界通道，`send` 几乎不阻塞（缓冲区概念上无限增长）；`sync_channel(n)` 是有界通道，缓冲满了之后 `send` 会阻塞直到有空位，可用来做背压（backpressure）。初学者先掌握 `channel()` 即可，知道 `sync_channel` 的存在与用途即够。

#### 来源与时效
- The Rust Programming Language book Ch.16.2「Using Message Passing to Transfer Data Between Threads」（`mpsc::channel`、`send` 移动所有权、`recv`、多生产者 clone），对齐 rustc 1.90.0 + edition 2024，https://doc.rust-lang.org/book/ch16-02-message-passing.html ，核实 2026-08-01。
- std 标准库文档 `std::sync::mpsc`（`channel` vs `sync_channel`、`recv`/`try_recv` 语义、`Receiver` 的 `IntoIterator`、通道关闭返回 `Err`），https://doc.rust-lang.org/std/sync/mpsc/ ，核实 2026-08-01。
- 本机 rustc 1.94.1 现跑：`got: hi`；send 后再用被移动值报错 E0382。
- 版本说明：`std::sync::mpsc` 内部实现自 Rust 1.67 换为基于 crossbeam 的版本，⚙演进快·锚 rustc 1.94.1·随时变，但 `channel`/`send`/`recv` 的对外 API 与语义稳定，两来源一致、无冲突。

## R9.3 共享状态 Mutex/Arc

### Mutex<T>：拿到锁才能访问里面的数据

有时多个线程确实需要读写同一份数据，而不是把它传来传去。`std::sync::Mutex<T>`（互斥锁）把数据包起来，规定"任一时刻只有一个线程能访问它"。用法是 `mutex.lock()`：这会阻塞直到拿到锁，返回一个 `Result<MutexGuard<T>>`；`MutexGuard` 是一个智能指针，通过它（解引用）读写内部数据，而当 `MutexGuard` 离开作用域被 drop 时，锁自动释放。

```rust
use std::sync::Mutex;
fn main() {
    let m = Mutex::new(5);
    {
        let mut num = m.lock().unwrap();
        *num = 6;
    } // 这里 num（MutexGuard）被 drop，锁自动释放
    println!("m = {:?}", m);
}
```

这套设计把 R8 学过的 RAII 用在了锁上：你不可能"忘记解锁"，因为解锁是 `MutexGuard` 析构时自动做的。同时它也把"必须先加锁才能碰数据"变成了类型层面的强制——数据被封在 `Mutex` 里，不 `lock()` 就拿不到 `&mut`，编译器替你保证了访问纪律。`lock()` 返回 `Result` 是因为持锁线程如果 panic 了，锁会进入"中毒（poisoned）"状态，后续 `lock()` 会返回 `Err` 提醒你数据可能处于不一致状态。

### Arc<T>：可以跨线程共享的引用计数指针

`Mutex` 解决了"同一时刻只有一个线程访问"，但还有一个问题：怎么让多个线程都持有指向这个 `Mutex` 的指针？R8 里的 `Rc<T>` 能共享所有权，但它只能用于单线程——`Rc` 的引用计数不是原子操作，跨线程会数据竞争，所以 `Rc` 不是 `Send`（见 R9.4）。跨线程版本是 `std::sync::Arc<T>`（atomically reference counted，原子引用计数），API 和 `Rc` 几乎一样（`Arc::clone` 增加计数、drop 减少计数、归零即释放），区别只是计数用原子操作，因此可以安全跨线程。

典型组合是 `Arc<Mutex<T>>`：`Arc` 负责"多个线程共享同一个东西"，`Mutex` 负责"共享的这个东西被安全地互斥访问"。

```rust
use std::sync::{Arc, Mutex};
use std::thread;
fn main() {
    let counter = Arc::new(Mutex::new(0));
    let mut handles = vec![];
    for _ in 0..10 {
        let counter = Arc::clone(&counter);
        let h = thread::spawn(move || {
            let mut num = counter.lock().unwrap();
            *num += 1;
        });
        handles.push(h);
    }
    for h in handles { h.join().unwrap(); }
    println!("Result: {}", *counter.lock().unwrap());
}
```

`rustc --edition 2024 d.rs && ./d` 本机现跑输出 `Result: 10`——十个线程各自加一，结果恰好是 10，没有丢更新（若是有数据竞争的语言写错了锁，这里可能得到小于 10 的随机数）。注意循环里 `let counter = Arc::clone(&counter);` 给每个线程 `move` 进去一份自己的 `Arc` 句柄，它们指向同一个 `Mutex<i32>`。

### 为什么必须是 Arc 而不是 Rc

如果把上例的 `Arc` 换成 `Rc`，编译直接失败，因为 `Rc` 不能跨线程发送。本机 rustc 1.94.1 现跑报错（节选）：

```text
error[E0277]: `Rc<std::sync::Mutex<i32>>` cannot be sent between threads safely
 = help: within `...`, the trait `Send` is not implemented for `Rc<std::sync::Mutex<i32>>`
```

这就是"无畏"的又一次体现：你不需要记住"Rc 不能跨线程"这条规则，写错了编译器会精确告诉你哪个类型不满足 `Send`。反过来也说明 Rust 的分工很清晰：`Rc`/`Arc` 管"共享所有权"，`RefCell`/`Mutex` 管"内部可变性 + 访问纪律"，单线程用 `Rc<RefCell<T>>`，多线程用 `Arc<Mutex<T>>`，是 R8 与本章一一对应的姊妹结构。

### 死锁：Rust 挡数据竞争，但不挡死锁

Rust 编译期消除的是数据竞争，不是所有并发 bug。死锁（两个线程各持一把锁又互相等对方的锁）在 Rust 里仍然可能发生，编译器不会拦。逻辑错误（比如锁的粒度不对、拿锁顺序不一致）也仍然要靠程序员自己设计。`Mutex` 帮你保证"不会忘记解锁、不会不加锁就访问"，但"拿几把锁、按什么顺序拿"是你的责任。这条边界很关键，别把"无畏并发"误读成"并发不会出任何错"。

#### 来源与时效
- The Rust Programming Language book Ch.16.3「Shared-State Concurrency」（`Mutex<T>`、`lock`、`MutexGuard` 自动解锁、`Arc<T>`、`Arc<Mutex<T>>` 计数器例子、Rc 不能替代 Arc），对齐 rustc 1.90.0 + edition 2024，https://doc.rust-lang.org/book/ch16-03-shared-state.html ，核实 2026-08-01。
- std 标准库文档 `std::sync::Mutex`（`lock` 返回 `Result`、poisoning、`MutexGuard`）、`std::sync::Arc`（原子引用计数、与 `Rc` 的区别），https://doc.rust-lang.org/std/sync/ ，核实 2026-08-01。
- 本机 rustc 1.94.1 现跑：`Arc<Mutex<i32>>` 十线程计数输出 `Result: 10`；改用 `Rc` 报错 E0277（`Rc<...>` cannot be sent between threads / 未实现 `Send`）。
- 两来源一致，无冲突。"死锁不被编译期拦截"为 the book Ch.16.3 明述立场。

## R9.4 Send/Sync

### Send：类型的所有权可以安全地转移到另一个线程

`Send` 是一个标记 trait（marker trait，本身不含任何方法）。一个类型实现了 `Send`，意味着把它的值（所有权）转移到另一个线程是安全的。几乎所有类型都是 `Send`，最著名的例外是 `Rc<T>`：`Rc` 用非原子计数，两个线程同时改计数会造成数据竞争，所以 `Rc` 故意不是 `Send`。这正是 R9.3 里 `Rc` 无法跨线程、报 E0277 的根本原因。

```rust
use std::rc::Rc;
use std::thread;
fn main() {
    let data = Rc::new(5);
    let h = thread::spawn(move || {
        println!("{}", data);   // 试图把 Rc move 进新线程
    });
    h.join().unwrap();
}
```

本机 rustc 1.94.1 现跑报错（节选）：

```text
error[E0277]: `Rc<i32>` cannot be sent between threads safely
 = help: within `{closure@...}`, the trait `Send` is not implemented for `Rc<i32>`
note: required by a bound in `spawn`
```

`thread::spawn` 要求它的闭包是 `Send`；闭包捕获了一个 `Rc`，而 `Rc` 不是 `Send`，于是整个闭包"被传染"成不是 `Send`，编译不过。你不需要手动检查，类型系统会顺着组合关系自动推导。

### Sync：类型的引用可以安全地被多个线程共享

`Sync` 是另一个标记 trait。一个类型 `T` 是 `Sync`，意味着"从多个线程同时持有 `&T`（共享引用）是安全的"。规范给出的精确关系是：

```text
T 是 Sync  当且仅当  &T 是 Send
```

也就是说，"能安全共享引用"等价于"引用能安全跨线程发送"。这条等价式是理解两者关系的关键：`Send` 是关于"把值/所有权搬过去"，`Sync` 是关于"把引用同时给多个线程看"。`Mutex<T>` 是很好的例子——只要 `T: Send`，`Mutex<T>` 就是 `Sync`，因为 `Mutex` 用锁保证任意时刻只有一个线程真正访问内部数据，共享它的引用就是安全的。而 `Rc<T>` 既不是 `Send` 也不是 `Sync`；`Cell<T>`/`RefCell<T>` 是 `Send`（若 `T: Send`）但不是 `Sync`（它们的内部可变性没有同步机制，多线程共享会竞争）。

### 自动派生：Send/Sync 是编译器自动实现的

`Send` 和 `Sync` 绝大多数时候不需要你写。它们是"自动 trait（auto trait）"：如果一个类型的所有组成部分都是 `Send`，那么这个类型自动是 `Send`（`Sync` 同理）。所以你自定义的 struct、enum，只要字段都是 `Send`/`Sync`，它就自动获得 `Send`/`Sync`，无需任何声明。反过来，只要里面塞了一个 `Rc`，整个类型就自动"不是 `Send`"。

手动实现 `Send`/`Sync` 是可以的，但必须用 `unsafe`——因为你是在向编译器承诺"我保证这个类型跨线程是安全的"，一旦承诺错了，编译器不再兜底，后果由你负责（这属于 R11 unsafe 的范畴）。初学者阶段：几乎永远不需要手写 `impl Send`/`impl Sync`，看到别人在 `unsafe impl Send` 要警觉这是在做一个需要人工论证的安全承诺。

### 编译期消除数据竞争：类型系统层的拦截点

把前面拼起来，就得到 Rust "无畏并发"的完整机制：`thread::spawn`（及各种并发 API）在签名里要求 `Send`/`Sync` 约束；这些 marker trait 由编译器沿类型结构自动推导；于是"把一个不该跨线程的东西（如 `Rc`）带进线程"这类会导致数据竞争的写法，在编译期就因为不满足约束而被拒绝。数据竞争的定义是"两个以上线程并发访问同一内存、至少一个是写、且无同步"——Rust 用"别名异或可变（R3）+ Send/Sync 边界"这两道防线，让满足数据竞争定义的程序根本编译不出来。

需要给初学者划清的边界有两条。其一，被消除的是数据竞争（data race），不是更广义的竞态条件（race condition）或死锁——逻辑上的先后依赖错误、死锁仍需自己防（见 R9.3）。其二，这些保证建立在安全 Rust 之内；`unsafe` 代码（含手写 `Send`/`Sync`、FFI）可以绕过，那部分的正确性由写 `unsafe` 的人负责论证。并发的一般理论、内存模型与操作系统线程语义在并发课一脉展开（→L4-05 并发），此处只强调 Rust 独有的这一层：把数据竞争的防范从"运行期靠人自觉"提升为"编译期由类型系统强制"。

#### 来源与时效
- The Rust Programming Language book Ch.16.4「Extensible Concurrency with the `Send` and `Sync` Traits」（`Send`/`Sync` 为 marker trait、`Rc` 非 `Send`、自动派生、手动实现需 `unsafe`），对齐 rustc 1.90.0 + edition 2024，https://doc.rust-lang.org/book/ch16-04-extensible-concurrency-sync-and-send.html ，核实 2026-08-01。
- The Rust Reference §「Special types and traits」→ Send / Sync（`Send`：可跨线程边界转移；`Sync`：`T: Sync` 当且仅当 `&T: Send`；均为 auto trait、`unsafe` 手动实现），https://doc.rust-lang.org/reference/special-types-and-traits.html ，核实 2026-08-01。
- std 标准库文档 `std::marker::Send`/`std::marker::Sync`（定义、`Rc`/`Cell`/`RefCell` 的 Send/Sync 归属、`Mutex<T>: Sync where T: Send`），https://doc.rust-lang.org/std/marker/ ，核实 2026-08-01。
- 本机 rustc 1.94.1 现跑：`Rc<i32>` move 进 `thread::spawn` 报错 E0277（未实现 `Send`、`required by a bound in spawn`）。
- 三来源对 `T: Sync ⟺ &T: Send`、auto trait、`Rc` 非 Send/Sync 一致，无冲突。"编译期消除的是数据竞争而非死锁/一般竞态"为 the book 与 Reference 一致立场。
