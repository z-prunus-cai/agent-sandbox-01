# L5-14·大主题R10 异步编程

> 基线 Py3.11.15/np2.4.6/gcc13.3 @2026-07-25（+ rustc/cargo 1.94.1）｜核实日期：2026-08-01｜先修：R2 所有权与 move、R6 泛型与 trait、R9 无畏并发（线程/channel/Send·Sync）｜一手锚点：The Rust Programming Language book Ch.17（页面对齐 Rust 1.90.0 + edition 2024）· `std::future::Future`（stable 1.36.0）· The Rust Reference｜成熟度：语言层 async/await 与 `Future` 为 GA；运行时/生态 ⚙演进快·锚版本·随时变

本大主题在全课中演进最快：async/await 语法与 `Future` trait 已进标准库并稳定，但**运行时（executor）不在标准库内**，靠第三方 crate 提供，版本与默认项随生态变化。凡触及运行时、生态 crate、稳定版本号的结论，均以本机 rustc/cargo 1.94.1 现查为准，不凭记忆；不确定处如实标「待核」。

---

## 17.1 async/await 与 Future

### async 与 await 两个关键字

`async` 把一个函数或代码块标记成「可中断、可恢复」的异步计算，`await` 则在一个异步计算内部等待另一个异步计算就绪。它们是 Rust 表达异步的语法基座：`async fn` / `async {}` 产出一个「未来才有结果」的值（future），`.await` 在需要该结果的地方把当前任务挂起、待结果就绪再继续。

对初学者，最反直觉的一点是 Rust 的 `await` 是**后缀**（postfix）：写在表达式后面，如 `some_future.await`，而不是像别的语言那样前缀 `await some_future`。这样设计是为了让链式调用自然：`client.get(url).await.text().await` 从左到右读，每个 `.await` 就是一个「可能在此暂停」的点。另一个关键直觉是——`.await` **只能**出现在 `async` 上下文（`async fn` 或 `async {}`）里，因为暂停/恢复需要有运行时来接管，普通同步函数里没有这个机制。

### async fn 编译为状态机

编译器把一个 `async fn` 改写成一个「返回 future 的普通函数」，函数体被塞进一个自动生成的、隐藏的**状态机**里；每个 `.await` 点对应状态机的一个暂停/恢复位置。the book 用下面这对等价改写来说明「`async fn` 只是语法糖」。

```rust
async fn page_title(url: &str) -> Option<String> {
    let response_text = trpl::get(url).await.text().await;
    Html::parse(&response_text)
        .select_first("title")
        .map(|title| title.inner_html())
}
```

它大致等价于一个返回 `impl Future` 的函数：

```rust
fn page_title(url: &str) -> impl Future<Output = Option<String>> {
    async move {
        let text = trpl::get(url).await.text().await;
        Html::parse(&text)
            .select_first("title")
            .map(|title| title.inner_html())
    }
}
```

每碰到一个 `.await`，编译器就把「到这里为止的局部变量」存进状态机的一个变体（variant），下次被恢复时从这个变体接着跑。the book 用一个示意 enum 表达这种「每个 await 点一个状态」的结构（示意，非真实生成代码）：

```rust
enum PageTitleFuture<'a> {
    Initial { url: &'a str },
    GetAwaitPoint { url: &'a str },
    TextAwaitPoint { response: trpl::Response },
}
```

本机 rustc 1.94.1 实测印证了「`async fn` 返回一个 future、返回的是值而非立即执行」：`let _fut = hello();` 只是构造状态机、不跑函数体（见下一条）。

### Future trait 与 Poll

`Future` 是异步计算的核心 trait，定义在标准库 `std::future` 中，自 Rust 1.36.0 稳定。它只有一个关联类型 `Output`（计算完成时产出的值）和一个方法 `poll`。

```rust
pub trait Future {
    type Output;

    fn poll(self: Pin<&mut Self>, cx: &mut Context<'_>) -> Poll<Self::Output>;
}
```

`poll` 的返回类型是 `Poll<T>` 枚举，只有两个变体：

```rust
pub enum Poll<T> {
    Ready(T),
    Pending,
}
```

`poll` 的语义是「**尝试**把 future 推进到最终值，但绝不阻塞」。若已就绪返回 `Poll::Ready(值)`；若还没就绪返回 `Poll::Pending`，并且在返回 `Pending` 前把当前 `Context` 里的 `Waker` 存起来，等外部事件（socket 可读、定时器到点等）发生时用它「叫醒」任务、再被 `poll` 一次。这就是异步的心跳：运行时反复 `poll`，直到拿到 `Ready`。

初学者不需要手写 `poll`——写 `.await` 时编译器就替你生成对 `poll` 的调用。理解 `Poll::Pending` / `Poll::Ready` 这对状态，是理解「为什么异步不阻塞线程」的钥匙：一个任务 `Pending` 时，线程不是干等，而是掉头去 `poll` 别的任务。本机 rustc 1.94.1 实测 `Future` 在 `std::future`、`Poll` 在 `std::task`，`fn takes_future<F: Future<Output = i32>>(_f: F) {}` 可接收 `async { 5 }`。

### 惰性求值：future 不被 poll 就什么都不做

Rust 的 future 是**惰性**的：构造一个 future（调用 `async fn` 或写 `async {}`）**不会**执行里面的任何代码，必须有人对它 `.await` 或交给运行时去 `poll`，函数体才开始跑。

这和很多语言的「调用即启动」的 async（如 JS 的 promise 一创建就开跑）截然不同，是初学者最常见的坑：以为写了 `some_async_fn()` 就已经在后台跑了，实际上它一步都没动。本机 rustc 1.94.1 实测确认这一点——

```rust
async fn hello() -> i32 { 42 }
fn main() {
    let _fut = hello();     // 只构造 future，函数体一行都没跑
    println!("future created, body not yet run");
    let _ = _fut;           // 未被 poll 就丢弃，body 永不执行
}
```

上面程序输出 `future created, body not yet run`，`hello` 的函数体从未运行。记住这条：**future 是配方，不是正在下锅的菜**；不 `.await`（或不交给运行时）就永远是纸面配方。

### Pin 与 Unpin：为什么 poll 收的是 Pin<&mut Self>

`poll` 的接收者是 `Pin<&mut Self>` 而不是普通 `&mut Self`，原因是 async 块生成的状态机可能是**自引用**的——状态机内部某个字段可能持有指向同一状态机另一字段的引用（例如跨 `.await` 借用一个局部变量）。自引用的值一旦在内存里被移动，内部那些指针就会指向旧地址而失效，造成未定义行为。

`Pin` 就是「把值钉在原地、禁止移动」的包装：把 `Pin` 套在指向该值的指针上（如 `Pin<Box<T>>`、`Pin<&mut T>`），被指向的 `T` 就不能再被移走（`Box` 指针本身仍可移动，钉住的是背后的数据）。与之配套的 `Unpin` 是个标记 trait（marker trait，类比 `Send`/`Sync`），表示「这个类型即使被 `Pin` 包着也能安全移动」；绝大多数类型（如 `String`、`i32`）自动实现 `Unpin`，只有像 async 状态机这种自引用类型才需要真正被钉住。

一是「`.await` 之所以要 `Pin`，是因为编译出的 future 可能自引用、移动会出错」；二是「日常写 async 代码几乎碰不到 `Pin`——它是运行时和 `poll` 打交道时的底层细节」。只有当你手写 `Future`、或把 future 装箱跨越 trait 对象时，才会显式遇到 `Pin`/`Box::pin`。

#### 来源与时效
- The Rust Programming Language book Ch.17「Futures and the Async Syntax」与「A Closer Look at the Traits for Async」https://doc.rust-lang.org/book/ch17-01-futures-and-syntax.html · https://doc.rust-lang.org/book/ch17-05-traits-for-async.html （核实 2026-08-01；页面对齐 Rust 1.90.0 + edition 2024）
- `std::future::Future` 官方 API 文档 https://doc.rust-lang.org/std/future/trait.Future.html （trait 定义、`poll`/`Poll`/`Context`/`Waker`；标注 stable since 1.36.0；核实 2026-08-01）
- 本机 rustc 1.94.1 实测：`async fn` 惰性、`Future` 在 `std::future`、`Poll` 在 `std::task`、`async fn main` 报错 E0752（见 17.2）。
- 多来源一致：`Future` trait 形态在 the book Ch.17.1、Ch.17.5 与 std 文档三处措辞略异但语义一致，无冲突。
- ⚙演进快·锚版本：async 状态机的**具体生成代码**是编译器内部实现细节，随 rustc 版本变化，正文只用 the book 的示意 enum，不当作稳定契约。

## 17.2 执行器/运行时

### 标准库只定义接口，不含运行时

Rust 标准库提供的是异步的**接口与语法**——`Future` trait、`async`/`await` 关键字、`Poll`/`Context`/`Waker` 等类型——但**不含**驱动 future 前进的**执行器（executor）/运行时（runtime）**。也就是说，标准库告诉你「future 长什么样、怎么被 poll」，却不提供「反复 poll 直到完成」的那个引擎；这个引擎要靠第三方 crate。

光有标准库，写得出 `async fn`，却跑不起来——没有运行时就没人去 `poll` 你的 future。这是 Rust 有意的设计取舍：不同场景（服务器、嵌入式、GUI）对调度器需求差异极大，标准库不锁死一种运行时，把选择权留给生态。

### 运行时/执行器在做什么

运行时的核心工作是一个「poll 循环 + 唤醒机制」：它持有若干任务（task，每个任务本质是一个顶层 future），不断挑一个来 `poll`；若返回 `Pending`，就把该任务对应的 `Waker` 登记到相应事件源上，掉头去 poll 别的任务；当外部事件触发 `Waker`，任务被重新排进就绪队列、再被 `poll`；返回 `Ready` 则任务完成。

直觉上，运行时是异步世界的「调度员」：它让**单个（或少数几个）线程**在大量「大部分时间在等 IO」的任务之间高效切换，而不为每个任务开一条 OS 线程。`Waker` 是任务和事件源之间的回叫凭证——「我先睡，等你好了拿这个凭证叫我」。初学者不必自己实现运行时，但理解「运行时=反复 poll+按 Waker 唤醒」能解释后面所有并发原语的行为。

### the book Ch.17 用哪个运行时（原「待核」项，已核实）

原编排留白问「the book Ch.17 是否引入具体 async 运行时」——核实 2026-08-01：**是**。the book Ch.17 的示例统一通过一个教学包装 crate `trpl`（"The Rust Programming Language" 的缩写）来提供运行时能力；`trpl` 本身**再导出（re-export）并包装了 `futures` 与 `tokio` 两个生态 crate**，把它们的 API 改名/收窄，让读者聚焦异步基础而非某个运行时的全部细节。the book 明说可去读 `trpl` 源码看每个再导出到底来自哪个底层 crate。

the book 教学上确实「引入了具体运行时」，只是套了层 `trpl` 外衣，底座是 `tokio`（运行时）+ `futures`（组合子与 `Stream` 等）。对读者的意义是：书里 `trpl::block_on`、`trpl::spawn_task`、`trpl::join`、`trpl::channel`、`trpl::sleep`、`trpl::select`、`trpl::StreamExt` 这些名字，实际上是 `tokio`/`futures` 对应能力的教学化封装。

### block_on 与「main 不能是 async」

要让 future 真正跑起来，the book 用 `trpl::block_on`：它接收一个 future，**阻塞当前线程直到该 future 完成**，并在背后用 `tokio` 搭起运行时。典型入口写法是让同步的 `main` 调用 `block_on` 来启动异步世界：

```rust
fn main() {
    trpl::block_on(async {
        // 这里才是异步世界，可以自由 .await
        // ...
    })
}
```

之所以要这么包一层，是因为 `main` 本身**不允许**是 `async`——异步代码需要运行时来管理，而运行时得有人先启动。本机 rustc 1.94.1 实测印证：把 `main` 写成 `async fn main() {}` 直接编译报错 `error[E0752]: main function is not allowed to be async`。生态里常见的 `#[tokio::main]` 之类属性宏，本质就是替你把 `async fn main` 改写成「同步 main + 在里面 block_on」这套样板。

### spawn_task：任务比线程轻

`trpl::spawn_task` 是异步版的「起一个并发单元」，对应线程世界的 `thread::spawn`，但产出的是**任务（task）**而非 OS 线程。任务由运行时（库层代码）调度，不由操作系统调度，因此远比线程轻量。

```rust
use std::time::Duration;

fn main() {
    trpl::block_on(async {
        let handle = trpl::spawn_task(async {
            for i in 1..10 {
                println!("hi number {i} from the first task!");
                trpl::sleep(Duration::from_millis(500)).await;
            }
        });

        for i in 1..5 {
            println!("hi number {i} from the second task!");
            trpl::sleep(Duration::from_millis(500)).await;
        }

        handle.await.unwrap(); // 等子任务结束：用 .await，而不是线程的 join()
    });
}
```

等子任务结束用 `handle.await` 而非 `join()`；睡眠用 `trpl::sleep(...).await` 而非 `thread::sleep`（后者会阻塞整条线程、把同线程的其他任务也一起卡住）。这条「异步里别用同步阻塞调用」是初学者高频坑（详见 17.4）。

### 生态运行时全景（⚙演进快·锚版本·随时变）

标准库不含运行时，实践中要从生态选一个。最主流的通用运行时是 **tokio**（也是 the book `trpl` 的底座）；`futures` crate 提供大量组合子与 `Stream` 等抽象；此外还有 `async-std`、`smol` 等其它运行时。这一层演进快、默认项与主流选择随时间变，具体版本/特性以项目当下 `Cargo.toml` 锁定与官方文档为准。

初学者选型的安全默认是 tokio（生态最大、文档最全、多数异步库默认适配）。务必注意的坑：**运行时通常不能混用**——为某个运行时写的库（如依赖 tokio 的定时器/IO）一般要在该运行时里跑，跨运行时会出问题。具体某运行时的当前稳定版本号、默认线程模型、特性开关等此处「待核」，不凭记忆填，用时现查官方文档。

#### 来源与时效
- The Rust Programming Language book Ch.17「Futures and the Async Syntax」「Applying Concurrency with Async」https://doc.rust-lang.org/book/ch17-01-futures-and-syntax.html · https://doc.rust-lang.org/book/ch17-02-concurrency-with-async.html （核实 2026-08-01；`trpl` 包装 `futures`+`tokio`、`block_on`、`spawn_task`、`main` 不能 async 均出自此）
- 本机 rustc 1.94.1 实测：`async fn main` → `error[E0752]: main function is not allowed to be async`；`--edition 2021` 与 2024 均复现。
- 原编排「待核」项（R10.2：the book Ch.17 是否引入具体运行时）**已核实并解除**：核实 2026-08-01 确认 Ch.17 经 `trpl` 引入 `tokio`（运行时）+`futures`（组合子），非「不引入」。
- ⚙演进快·锚版本·随时变（核实 2026-08-01）：tokio/async-std/smol 等运行时的**当前版本号、默认配置、生态默认选择**随时变化，正文不写死具体版本，用时现查。tokio 官方文档 https://tokio.rs/ 作为运行时侧第二来源（与 the book 关于「标准库无运行时、需第三方」的陈述一致，无冲突）。
- 待核：某运行时确切当前稳定版本与默认线程模型——不凭记忆，用时现查。

## 17.3 流（Stream）与并发原语

### Stream：异步版的迭代器

`Stream`（流）是「随时间陆续到达的一串值」，可以理解为**异步版的 `Iterator`**：`Iterator` 用 `next()` 同步地取下一个元素，`Stream` 则异步地产出下一个元素，取的时候要 `.await`。它天然适合建模「队列里陆续冒出的消息」「从文件/网络分块读入的数据」这类「元素随时间到来」的场景。

和只能收消息的 channel 接收端不同，`Stream` 是更通用的抽象：任何「异步产出零个到多个值的序列」都可用它表达。初学者可以把心智模型定成一句话：**Stream = 会 `.await` 的 Iterator**。

### Stream trait 与 StreamExt / next

`Stream` trait 不在标准库里（截至核实日期），常见定义由 `futures` crate 提供；它把 `Iterator` 与 `Future` 揉在一起——方法叫 `poll_next`，返回 `Poll<Option<Item>>`（`Poll` 来自 future 那套，`Option` 来自 iterator 那套：`Some(值)` 是一个元素、`None` 表示流结束）。

```rust
trait Stream {
    type Item;

    fn poll_next(
        self: Pin<&mut Self>,
        cx: &mut Context<'_>,
    ) -> Poll<Option<Self::Item>>;
}
```

日常不直接调 `poll_next`，而是用扩展 trait `StreamExt` 提供的高层方法，最常用的是 `next()`；`StreamExt` 为所有 `Stream` 自动实现，所以你只要实现 `Stream` 就白得一整套便利方法。用 `next()` 前要 `use` 进 `StreamExt`：

```rust
use trpl::StreamExt;

fn main() {
    trpl::block_on(async {
        let values = [1, 2, 3, 4, 5, 6, 7, 8, 9, 10];
        let iter = values.iter().map(|n| n * 2);
        let mut stream = trpl::stream_from_iter(iter);

        while let Some(value) = stream.next().await {
            println!("The value was: {value}");
        }
    });
}
```

一个初学者易忽略的历史细节：`StreamExt::next` 在较新 Rust 里可直接写成 `async fn next(&mut self) -> Option<Self::Item>`（依赖「async fn in traits」特性；本机 rustc 1.94.1 实测 async fn 写进 trait 可正常编译，仅未用到时报 dead_code 警告）；但为兼容旧版本，实际 crate 里常写成返回一个实现了 `Future` 的 `Next<'_, Self>` 结构体的普通函数——两种写法效果等价。「async fn in traits」的确切稳定版本（常被引作 1.75）此处标「待核」，仅确认本机 1.94.1 可用。

### 异步消息传递（channel）

异步 channel 是线程版 `std::sync::mpsc` 的对应物，用于任务间传值、所有权随消息转移。the book 用 `trpl::channel`，其接收端的 `recv()` 返回一个 future、要 `.await`；发送端 `send()` 因为是无界 channel 而不需要 `.await`。

```rust
use std::time::Duration;

fn main() {
    trpl::block_on(async {
        let (tx, mut rx) = trpl::channel();

        let tx_fut = async move {
            for val in [String::from("hi"), String::from("from"),
                        String::from("the"), String::from("future")] {
                tx.send(val).unwrap();
                trpl::sleep(Duration::from_millis(500)).await;
            }
        };

        let rx_fut = async {
            while let Some(value) = rx.recv().await {
                println!("received '{value}'");
            }
        };

        trpl::join(tx_fut, rx_fut).await; // 让收发两个 future 并发推进
    });
}
```

接收端 `rx` 要声明成 `mut`；用 `while let Some(v) = rx.recv().await` 循环收；把 `tx` 用 `async move` 移进发送块。一个关键坑——**一个 async 块内部是线性执行的**，把发送和接收写进同一个 async 块并不会并发；必须拆成两个 future 再用 `join` 一起等，才有并发（见下条）。

### join：公平地并发等待多个 future

`trpl::join`（及可变参数的 `trpl::join!` 宏、以及对一组 future 的 `join_all`）让多个 future **并发**推进、全部完成后一起返回结果。它是「我全都要、都等它们跑完」的语义。

```rust
trpl::join(fut1, fut2).await;       // 两个
trpl::join!(fut1, fut2, fut3);      // 编译期已知个数，用宏
// 个数运行期才定、装在集合里，用 join_all（对 Vec<Future> 等）
```

the book 强调 `trpl::join` 是**公平**的：它均等地轮询每个 future、交替推进，给出确定、一致的顺序——这点和线程调度的「顺序不确定」形成对比，也是异步「协作式调度」可预测性的体现。初学者记忆点：`join` = 「并发地全等到」，配合上一条把「收/发拆成两个 future」，才是真正的并发。

### select / race 与超时

`trpl::select` 让两个 future **竞速**，返回**先就绪**的那个的结果，用 `Either::Left/Right` 区分是哪个赢了。它是「谁先好用谁」的语义，与 `join`（全等到）相对。

```rust
use trpl::Either;

match trpl::select(future_a, future_b).await {
    Either::Left(output)  => { /* a 先完成 */ }
    Either::Right(output) => { /* b 先完成 */ }
}
```

`select` 最经典的用途是**做超时**：把「真正要做的事」和「一个 sleep 定时器」放进 `select`，谁先好就走谁——业务先完成得结果，定时器先到就是超时。

```rust
use std::time::Duration;
use std::future::Future;
use trpl::Either;

async fn timeout<F: Future>(
    future_to_try: F,
    max_time: Duration,
) -> Result<F::Output, Duration> {
    match trpl::select(future_to_try, trpl::sleep(max_time)).await {
        Either::Left(output) => Ok(output),
        Either::Right(_)     => Err(max_time),
    }
}
```

the book 的 `trpl::select` **按参数顺序 poll、不保证公平**，第一个参数优先——和 `join` 的公平性不同。若两个都可能同时就绪，靠前的会被优先选中。

### yield_now：协作式让出

Rust 只在 `.await` 点暂停 async 块；如果一段 async 代码在两个 `.await` 之间干了很长的活、中途没有任何 `.await`，它就会**独占**运行时、饿死同线程的其他任务。`trpl::yield_now().await` 用来在不睡眠的情况下**主动把控制权交回运行时**，让别的任务有机会推进。

```rust
async fn work() {
    heavy_step();
    trpl::yield_now().await; // 不睡，只是让出一次调度
    heavy_step();
}
```

这揭示了异步是**协作式多任务**：任务自己决定何时让出（在 `.await` 处），运行时不能像 OS 抢占线程那样强行打断一个正在跑的任务。因此写异步代码要有意识地在长任务里插入让出点，或把 CPU 密集的活挪到线程/`spawn_blocking`（见 17.4），否则「异步」反而卡成串行。

#### 来源与时效
- The Rust Programming Language book Ch.17「Applying Concurrency with Async」「Working with Any Number of Futures」「Streams」「A Closer Look at the Traits for Async」https://doc.rust-lang.org/book/ch17-02-concurrency-with-async.html · https://doc.rust-lang.org/book/ch17-03-more-futures.html · https://doc.rust-lang.org/book/ch17-04-streams.html · https://doc.rust-lang.org/book/ch17-05-traits-for-async.html （核实 2026-08-01）
- `Stream`/`StreamExt` 定义与 `futures` crate 出处：the book Ch.17.5 明说 `Stream` 不在 std、常见定义由 `futures` 提供；`futures` 官方文档为第二来源 https://docs.rs/futures/（核实 2026-08-01，两者语义一致）。
- 本机 rustc 1.94.1 实测：`async fn` 写进 trait 可编译（印证 `StreamExt::next` 的新式写法在本机可用）。
- 冲突/差异点（如实记）：`trpl::join` **公平**（均等轮询），`trpl::select` **按参数顺序、不公平**——二者调度性质不同，the book 明确区分，非矛盾而是不同原语的不同保证。
- ⚙演进快·锚版本·随时变：`join`/`join_all`/`select`/`stream_from_iter`/`yield_now` 等具体 API 名与签名来自 the book 的 `trpl` 教学封装，底层 `tokio`/`futures` 的对应 API 名与形态随版本变；用真实 crate 时以其当前文档为准。
- 待核：「async fn in traits」确切稳定版本号（常引作 Rust 1.75）——仅确认本机 1.94.1 可用，确切版本未逐一核实，标待核。

## 17.4 与线程模型的取舍

### 并发 vs 并行先分清

讨论取舍前要先分清两个常被混用的词。并发（concurrency）是「一个人来回切换做多件事」——任务交错推进，未必同时；并行（parallelism）是「多个人同时各做一件事」——真正的同时执行，需要多核。异步主要买的是**并发**（在少数线程上高效交错大量等待中的任务），线程既能并发也能并行（多核上真同时跑）。

初学者把这对概念分清，才能理解后面的选型准则：async 擅长「大量任务大部分时间在等」，线程擅长「把可切分的计算铺到多核上真并行算」。

### 任务 vs 线程：谁调度、多重

线程由**操作系统**调度，每条线程在很多系统上都要占一笔不小的内存，且依赖 OS/硬件支持（有些嵌入式裸机根本没有线程）。任务（task）则由**库层代码即运行时**调度，是「库层的线程」，因此远比 OS 线程轻量，可以同时存在成千上万个。

编程模型上，线程更「简单粗暴」：它是「fire and forget」，没有 future 那样的原生「暂停点」概念，起了就一路跑到底，只被 OS 抢占式打断。任务更细粒度：一个任务内部可以在多个 future 之间切换，所以并发既发生在任务**之间**、也发生在任务**内部**。

### CPU 密集 vs IO 密集：选型准则

the book 给的经验法则很干脆。工作若**高度可并行（CPU 密集）**——比如一大批数据每块能独立处理——**用线程**更合适，因为要的是把计算铺到多核真并行。工作若**高度并发（IO 密集）**——比如同时处理来自很多来源、以不同速率/间隔到达的消息——**用 async**更合适，因为绝大多数时间在等 IO，async 能在等待中高效切换而不为每个连接开一条线程。

**CPU 密集偏线程（要并行算），IO 密集偏 async（要并发等）**。这也解释了 17.3 的坑——在 async 里干重 CPU 活或调用同步阻塞（如 `thread::sleep`、阻塞式文件读）会卡死运行时，那种活应交给线程或运行时的 `spawn_blocking` 之类机制。

### async 与线程结合、工作窃取

async 和线程不是二选一，常常**配合**使用效果最好。任务在很多运行时里可以在多条线程之间迁移；不少运行时用**工作窃取（work stealing）**：空闲线程从繁忙线程的队列里「偷」任务来跑，从而把大量任务透明地摊到多核上，兼得 async 的轻量并发与线程的多核并行。

the book 给的典型混合模式是：用一条**线程**做 CPU 密集的活（如视频编码），通过 channel 把进度/结果发给一段 **async** 代码去 `.await` 处理（如更新 UI 通知）——线程负责「重算」，async 负责「等和协调」，各取所长。

### async 的传染性与生态成本（易错点）

选 async 不是零成本，有几个初学者要预期的坑。其一「传染性」：`.await` 只能在 async 上下文里用，一处改 async 往往顺着调用链一路要求上游也 async（俗称 async 会「传染」整条调用栈）。其二依赖运行时且运行时基本不能混用：为 tokio 写的库通常得在 tokio 里跑，混用不同运行时易出问题。其三别在 async 里做同步阻塞调用，会把整条线程连同其上的其他任务一起卡住。

因此实践中的稳妥心态是：不是「异步一定更快更好」，而是**按工作性质选**——IO 密集、海量并发连接选 async；CPU 密集、要真并行选线程；两者都要就混用。这与全课「时效>正确>广度」的取向一致：这一层生态与最佳实践演进快，具体运行时的能力/默认用时以官方文档现查为准。

#### 来源与时效
- The Rust Programming Language book Ch.17「Futures, Tasks, and Threads」https://doc.rust-lang.org/book/ch17-06-futures-tasks-threads.html （核实 2026-08-01；任务 vs 线程、CPU/IO 选型准则、工作窃取、线程+async 混合模式均出自此）
- 交叉：R9 无畏并发（the book Ch.16，线程/channel/`Send`·`Sync`）为本章对照系；async 的类型系统层安全保证（跨 `.await` 的 `Send` 要求等）根在 R9 的 `Send`/`Sync`，此处不展开，交叉引用 R9 与 L4-05 并发。
- 第二来源：tokio 官方文档对「async 适合 IO 密集、CPU 密集用 `spawn_blocking`/线程」的建议与 the book 一致 https://tokio.rs/ （核实 2026-08-01，无冲突）。
- 本大主题无独立数值型结论需实证；本机 rustc 1.94.1 仅作版本锚。
- ⚙演进快·锚版本·随时变：运行时是否支持任务迁移/工作窃取、`spawn_blocking` 等能力随运行时与版本不同，正文按 the book 的一般性表述陈述，具体能力用时现查对应运行时文档。
