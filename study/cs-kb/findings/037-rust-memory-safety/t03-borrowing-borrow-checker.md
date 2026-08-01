# L5-14·大主题R3 借用、引用与借用检查器

> 基线 Py3.11.15/np2.4.6/gcc13.3 @2026-07-25（+ rustc/cargo 1.94.1） ｜ 核实日期：2026-08-01 ｜ 先修：R2（所有权三规则、move 语义、值离开作用域即被 Drop）、C 语言里的指针与悬垂指针概念 ｜ 一手锚点：The Rust Programming Language book（"the book"，页面声明对齐 Rust 1.90.0 + edition 2024）Ch.4「Understanding Ownership」§4.2 References and Borrowing / §4.3 The Slice Type；The Rust Reference（references、borrow operators、lifetime）；The Rustonomicon「Aliasing」「References」；NLL 一手 RFC 2094（Non-Lexical Lifetimes）与 rustc-dev-guide 借用检查章 ｜ 成熟度：借用/引用/NLL 为 GA 稳定；Polonius 为 ⚙演进快·锚版本·随时变（nightly，本机稳定版不可用，见 R3.4）

所有权（R2）解决了"谁负责释放这块内存"，但如果每次读一个值都要交出所有权、用完再还回来，代码会寸步难行。借用（borrowing）就是所有权系统的补丁：你可以临时把一个值"借"给别人读或写，借用期间原主人仍然是 owner，借用结束后什么都不用还——引用只是一个受编译期规则约束的指针，它不拥有所指的数据，也就不会在离开作用域时去 Drop 它。

这一章的主线是一条贯穿 Rust 全部内存安全承诺的不变式——同一时刻，一块数据要么被任意多个只读引用共享，要么被恰好一个可写引用独占，二者不可兼得（aliasing XOR mutability）。负责在编译期强制这条规则、且不产生任何运行时开销的组件，就是借用检查器（borrow checker）；本篇讲清它当前的实现（NLL）、它拒绝的典型错误、以及下一代实现 Polonius 的状态。「这条别名不变式本质上是把编译期别名分析（→L6-02 程序分析）做成了语言内建」，此处只点名，不展开。

---

## R3.1 共享借用与可变借用（`&` / `&mut`）

### R3.1.1 引用与借用：`&` 取引用、`*` 解引用

引用是一个指向某个值的指针，用 `&` 运算符创建；创建引用这个动作叫"借用"。与 R2 的 move 不同，把 `&x` 传出去不会转移 `x` 的所有权，`x` 仍是那块数据的 owner，引用只是在有效期内"临时通行证"。读取引用背后的值用解引用运算符 `*`。

```rust
fn main() {
    let s = String::from("hello");
    let r = &s;                 // r 借用 s，s 仍拥有该 String
    println!("{}", *r);         // 解引用读取；println! 的 {} 也会自动解引用
    println!("{}", s.len());    // 借用结束后 s 照常可用，所有权从未离开
}
```

初学者最容易把借用和 R2 的 move 搞混。判断标准很简单：看有没有 `&`。`let a = s;` 是 move，`s` 之后不可用；`let a = &s;` 是借用，`s` 之后照常可用。之所以要引用，是因为很多时候你只想让一个函数"看一眼"数据，而不想把数据的所有权交出去、再费劲从返回值里接回来——引用让"只是看看"这件极常见的事变得零成本。

引用本身也是一个值、占一个机器字（指针宽度），它也遵守借用规则：一个引用必须在其指向的数据仍然存活时才有效，这正是 R3.5 悬垂引用要防的东西。

### R3.1.2 共享借用 `&T`：可以有很多个，但都只读

`&T` 是共享借用（shared / immutable borrow）。同一时刻你可以对同一个值创建任意多个 `&T`，它们都能读、但谁也不能通过它写。因为大家都只读、谁都不改，多个只读者并存永远是安全的。

```rust
fn main() {
    let s = String::from("hello");
    let r1 = &s;
    let r2 = &s;
    let r3 = &s;                    // 任意多个共享借用同时存在，合法
    println!("{} {} {}", r1, r2, r3);
}
```

"共享即只读"是理解整套规则的锚点。一旦你拿到的是 `&T`，编译器就保证在这个引用有效期间，没有任何人能修改被指向的数据——这让你读到的内容不会在你眼皮底下变化。想通过 `&T` 去改数据会直接编译报错，Rust 里默认一切不可变，引用也不例外。

### R3.1.3 可变借用 `&mut T`：同一时刻只能有一个

`&mut T` 是可变借用（mutable / exclusive borrow），通过它可以修改被指向的值。关键限制是独占性：在一个 `&mut T` 有效期间，对同一个值不能再有任何其它借用——既不能有第二个 `&mut T`，也不能有 `&T`。而且被借的那个变量本身必须是 `mut` 的。

```rust
fn main() {
    let mut s = String::from("hello");
    let r = &mut s;                 // 唯一的可变借用
    r.push_str(", world");          // 通过 &mut 修改
    println!("{}", r);
}
```

初学者常见的第一个坑是忘了给变量加 `mut`：想 `&mut s` 但 `s` 声明成了 `let s`，编译器会提示 `cannot borrow as mutable`。第二个坑是"独占"意味着借用期间连原变量自己都不能直接用——因为直接用 `s` 也算一次访问，会和 `&mut` 打架。

为什么可变借用必须独占？因为只要"能写"和"另一个能读或写"同时成立，就可能出现数据被一方改动而另一方毫不知情的情形（单线程里的迭代器失效、多线程里的数据竞争都是这个根源）。把"可写"和"独占"绑死，是 Rust 用最小代价堵住这一整类错误的方式，下一节把它抽象成一条不变式。

### R3.1.4 `.` 运算符的自动引用与自动解引用

调用方法时你几乎从不手写 `&` 或 `*`——`.` 运算符会自动帮你取引用或解引用，以匹配方法接收者（`self` / `&self` / `&mut self`）的需要。这叫自动引用/自动解引用（automatic referencing and dereferencing）。

```rust
fn main() {
    let mut v = vec![1, 2, 3];
    v.push(4);        // 等价于 Vec::push(&mut v, 4)，. 自动加了 &mut
    let n = v.len();  // 等价于 Vec::len(&v)，. 自动加了 &
    println!("{n}");
}
```

这解释了一个初学者常困惑的现象：明明没写 `&mut`，为什么调用 `v.push(...)` 也会占用一次可变借用、从而和别处的借用冲突。因为 `push` 的签名是 `fn push(&mut self, ...)`，`v.push(4)` 背后就是 `&mut v`。理解这一点后，R3.3 里那些"看起来只是调了个方法却报借用错误"的情况就不再神秘了。

#### 来源与时效
- The Rust Programming Language book Ch.4 §4.2「References and Borrowing」（`&`/`&mut` 定义、共享可多个、可变须独占、变量需 `mut`）https://doc.rust-lang.org/book/ch04-02-references-and-borrowing.html ；核实 2026-08-01。页面声明对齐 Rust 1.90.0 + edition 2024。
- The Rust Reference：borrow operators（`&` / `&mut` 表达式语义）https://doc.rust-lang.org/reference/expressions/operator-expr.html#borrow-operators 与 shared/mutable references 类型说明 https://doc.rust-lang.org/reference/types/pointer.html ；核实 2026-08-01。
- 自动引用/解引用：the book Ch.5 §5.3「Method Syntax」的 "Where's the -> Operator?" 一节；核实 2026-08-01。
- 本机实测 rustc 1.94.1（2026-03-25 build）：`&mut` 需 `mut` 变量、`&T` 可多份并存，行为与两来源一致，无冲突。
- ⚙演进快·锚版本：the book 页面锚 1.90.0，本机 rustc 1.94.1；本小主题涉及的引用基础语义在此 delta 内无变化（现查一致）。

## R3.2 别名异或可变（aliasing XOR mutability）

### R3.2.1 核心不变式：共享 XOR 可变

把 R3.1 的两条规则合起来，就是 Rust 内存安全的核心不变式：对任一数据，同一时刻，要么存在任意多个共享引用 `&T`（大家都读、都不写），要么存在恰好一个可变引用 `&mut T`（唯一一个可读可写），两种状态互斥、不可同时成立。这常被简称为 "aliasing XOR mutability"（别名与可变，二选一）或"shared XOR mutable"。

这里的"别名"（aliasing）指同一块内存被多个指针同时指向。这条不变式的直白翻译是：允许多个指针指向同一数据，或允许通过指针修改数据，但不允许"多指针"和"可修改"同时发生。

```rust
fn main() {
    let mut x = 10;
    {
        let a = &x;        // 进入"共享"态：可再有 &x，但此期间不能有 &mut x
        let b = &x;
        println!("{a} {b}");
    }
    {
        let m = &mut x;    // 进入"可变"态：此期间不能有任何其它 &x 或 &mut x
        *m += 1;
        println!("{m}");
    }
}
```

### R3.2.2 为什么这一条就能防住一大类内存错误

这条不变式之所以是承重墙，是因为绝大多数内存不安全都源于"一边在改、另一边还拿着旧的视图"。数据竞争需要"两个线程访问同一数据、至少一个在写、且无同步"——XOR 规则直接铲掉了"共享的同时可写"这一前提。迭代器失效（一边遍历容器、一边往里 `push` 导致底层缓冲区搬家、遍历指针变悬垂）在 Rust 里表现为：遍历持有 `&` 或 `&mut`，`push` 又要 `&mut`，两个借用重叠，编译期就被拒。

下面是迭代器失效在 Rust 里被静态拦截的最小例（`v.push` 需要 `&mut v`，而共享借用 `r` 仍要在之后使用）：

```rust
fn main() {
    let mut v = vec![1, 2, 3];
    let r = &v;        // 共享借用
    v.push(4);         // 需要 &mut v —— 与仍在使用的 r 冲突
    println!("{:?}", r);
}
```

本机 rustc 1.94.1 现跑，报 E0502：

```text
error[E0502]: cannot borrow `v` as mutable because it is also borrowed as immutable
 --> e0502.rs:4:5
  |
3 |     let r = &v;
  |             -- immutable borrow occurs here
4 |     v.push(4);
  |     ^^^^^^^^^ mutable borrow occurs here
5 |     println!("{:?}", r);
  |                      - immutable borrow later used here
```

在 C++ 里这段等价代码能编译、运行时可能因 vector 扩容而访问已释放内存，是经典 UB；Rust 把它变成一条编译错误。这就是"用类型系统换安全、且零运行时开销"的具体样貌——检查全发生在编译期，跑起来的机器码里没有任何额外检查。

### R3.2.3 与编译期别名分析的关联

值得一提的是，别名分析本身在编译器领域是一门独立学问（判断两个指针是否可能指向同一内存，服务于优化与静态检查）。Rust 的独特之处是把"无别名"从"编译器努力去推断的性质"提升为"由类型系统（`&` vs `&mut`）强制声明的性质"——这既保证了安全，也给了优化器强保证（例如 `&mut T` 天然具备类似 C 的 `restrict` 语义）。「这一点作为编译期别名分析的语言内建化，与 L6-02 程序分析相接」，此处仅交叉引用，不展开。

#### 来源与时效
- The Rustonomicon「Aliasing」（aliasing XOR mutability 的表述、`&mut` 的 no-alias 保证、与 `restrict` 的类比）https://doc.rust-lang.org/nomicon/aliasing.html ；「References」https://doc.rust-lang.org/nomicon/references.html ；核实 2026-08-01。
- The Rust Programming Language book Ch.4 §4.2（"data race" 三条件、可变与共享不可并存的动机）https://doc.rust-lang.org/book/ch04-02-references-and-borrowing.html ；核实 2026-08-01。
- 本机实测 rustc 1.94.1：迭代器失效样例现跑报 E0502（输出见正文），与规则一致。
- 交叉一致，无冲突：the book 从"防数据竞争/悬垂"动机侧讲，Nomicon 从"别名不变式/优化保证"机制侧讲，二者互补不矛盾。

## R3.3 借用检查器与非词法生命周期（NLL）

### R3.3.1 借用检查器在做什么

借用检查器是编译器里专门验证上述借用规则的组件。它为每个引用推断出一段"存活区域"（region，直觉上就是这个引用从创建到最后一次被使用所覆盖的代码范围），然后检查是否有任何两段区域以违反 XOR 不变式的方式重叠——例如一个 `&mut` 的区域和另一个借用的区域相交。若有重叠冲突，编译失败；否则程序被证明满足别名不变式，且不产生任何运行时代码。

初学者可把它想成一个"编译期的读写锁检查器"，只不过它不在运行时加锁，而是静态证明"根本不会有冲突的访问同时发生"。它检查的是借用之间的时间与空间关系，而不是值本身对不对。

### R3.3.2 NLL：借用活到"最后一次使用"，而非作用域结束

NLL（Non-Lexical Lifetimes，非词法生命周期）是当前（本机 rustc 1.94.1）稳定版默认的借用检查算法。它的核心改进是：一个借用的存活区域延伸到它最后一次被使用的地方为止，而不是延伸到它所在的词法作用域（`{}`）结束。"non-lexical"（非词法）正是指借用寿命不再和花括号绑定。

```rust
fn main() {
    let mut x = 5;
    let a = &mut x;
    *a += 1;
    // a 到此为最后一次使用，其借用区域在这里就结束了
    let b = &x;        // NLL 下合法：a 的借用已不再存活
    println!("{}", b);
}
```

本机 rustc 1.94.1 现跑：编译通过。在 NLL 之前的旧借用检查器里，`a` 的借用会一直"活到"作用域结尾，从而和后面的 `&x` 冲突、被拒——那种"明明后面没再用它却还报错"的反直觉体验，正是 NLL 消除的。理解 NLL 的实践价值在于：当你看到借用错误时，真正相关的往往是引用的"最后一次使用点"，把冲突的两次访问在时间上错开（让一个用完再开始另一个）通常就能解决。

### R3.3.3 两个典型冲突的实测：E0499 与 E0502

两个可变借用同时存在，报 E0499：

```rust
fn main() {
    let mut x = 5;
    let a = &mut x;
    let b = &mut x;                 // 第二个 &mut，冲突
    println!("{} {}", a, b);        // a 和 b 都还要用，两个 &mut 区域重叠
}
```

本机 rustc 1.94.1 现跑：

```text
error[E0499]: cannot borrow `x` as mutable more than once at a time
 --> e0499.rs:4:13
  |
3 |     let a = &mut x;
  |             ------ first mutable borrow occurs here
4 |     let b = &mut x;
  |             ^^^^^^ second mutable borrow occurs here
5 |     println!("{} {}", a, b);
  |                       - first borrow later used here
```

共享借用与可变借用冲突，报 E0502（样例与输出见 R3.2.2）。两者的区别值得记住：E0499 是"两个 `&mut`"，E0502 是"`&` 与 `&mut` 撞车"。报错信息里的三行标注（first borrow here / second borrow here / first borrow later used here）几乎总能告诉你冲突的三要素：谁先借、谁后借、以及先借的那个又在哪里被用到（正是这"later used"把 NLL 的存活区域撑到了后面）。

### R3.3.4 两阶段借用（two-phase borrows）

有一类写法看起来违反规则却能编译，例如 `v.push(v.len())`：`push` 需要 `&mut v`，而实参 `v.len()` 又需要 `&v`，天真地看是 `&mut` 与 `&` 冲突。它能通过，靠的是两阶段借用（two-phase borrows）：可变借用先以"保留（reserved）"状态存在（此阶段允许共享读），直到真正开始写时才"激活（activated）"为独占。

```rust
fn main() {
    let mut v = vec![1, 2, 3];
    v.push(v.len());        // &mut v 的写发生在 v.len() 的读之后，故合法
    println!("{:?}", v);
}
```

本机 rustc 1.94.1 现跑：编译通过。初学者不必深究其内部机制，只需知道：正是它让大量自然的方法调用写法（尤其涉及 `self` 与实参都要借用同一对象时）无需改写就能通过。它是 NLL 配套引入的实用放宽，不是漏洞。

#### 来源与时效
- 非词法生命周期一手：RFC 2094「Non-Lexical Lifetimes」（借用区域延伸到"最后一次使用"、基于 MIR 的区域推断）https://rust-lang.github.io/rfcs/2094-nll.html ；rustc-dev-guide 借用检查 / MIR borrowck 章 https://rustc-dev-guide.rust-lang.org/borrow_check.html ；核实 2026-08-01。
- 两阶段借用：rustc-dev-guide「Two-phase borrows」https://rustc-dev-guide.rust-lang.org/borrow_check/two_phase_borrows.html ；核实 2026-08-01。
- The Rust Programming Language book Ch.4 §4.2（NLL 效果的教学化说明："a reference's scope starts ... and continues through the last time that reference is used"）https://doc.rust-lang.org/book/ch04-02-references-and-borrowing.html ；核实 2026-08-01。
- 本机实测 rustc 1.94.1：NLL 放宽样例编译通过；E0499、E0502 现跑复现（输出见正文）；`v.push(v.len())` 编译通过（两阶段借用）。
- ⚙演进快·锚版本：the book 锚 1.90.0，本机 rustc 1.94.1；NLL 为二者共同的稳定默认，delta 内无变化。

## R3.4 Polonius 状态（待核）

### R3.4.1 Polonius 是什么

Polonius 是借用检查器的下一代实现，用基于 Datalog 的关系式（逻辑规则 + 事实推导）来表述借用检查，相比当前 NLL 的实现，它能接受一些 NLL 会误拒的合法程序（典型是涉及在控制流中有条件返回引用的"NLL problem case #3"），并给出更精确的错误定位。它与 NLL 检查的是同一套借用规则、目标相同，区别在于内部推断算法更强、更精确。

对初学者而言，只需理解：Polonius 不是"新规则"，而是"更聪明地执行同一套规则"的实现——它的到来预计会让更多本就安全、但当前被 NLL 保守拒绝的代码通过编译，而不会放宽安全承诺本身。

### R3.4.2 是否已成默认借用检查器（待核）

⚙演进快·锚版本·随时变（核实 2026-08-01）。截至本机 rustc 1.94.1（2026-03-25 build），稳定版的默认借用检查器仍是 NLL，Polonius 尚未成为默认。Polonius 相关工作以 nightly 实验特性形式存在（历史上通过 `-Zpolonius` / `-Zpolonius=next` 开关，`-Z` 类不稳定选项仅 nightly 可用）。

本机实测（rustc 1.94.1 稳定版）：

```text
$ rustc --edition 2024 -Zpolonius example.rs
error: the option `Z` is only accepted on the nightly compiler
```

且本机 `rustup toolchain list` 只有 `stable`（无 nightly），故 Polonius 在本机稳定版上不可用、无法实测其行为。因此本篇一切结论以 NLL 为基线；"Polonius 是否/何时成为稳定默认、其开关与行为细节"——待核，不下结论，需对齐后续版本现查，不凭记忆外推。此项属快速演进对象，读到本篇时状态可能已变。

#### 来源与时效
- Polonius 项目一手：rust-lang/polonius 仓库与其 book（Datalog 表述、动机、"problem case #3"）https://github.com/rust-lang/polonius 、https://rust-lang.github.io/polonius/ ；rustc-dev-guide「Polonius」章 https://rustc-dev-guide.rust-lang.org/borrow_check/region_inference.html （及 Polonius 小节）；核实 2026-08-01。
- 背景动机：Niko Matsakis 关于 NLL 与 Polonius 的系列文章（作者一手，二手性质，仅补背景，不承重结论）；核实 2026-08-01。
- 本机实测 rustc 1.94.1：`-Zpolonius` 在稳定版被拒（"`Z` is only accepted on the nightly compiler"），本机无 nightly 工具链，Polonius 不可用；默认借用检查器为 NLL。
- 待核项（明确）：Polonius 成为稳定默认借用检查器的状态与时间线——本篇不下结论，标「待核」，以后续 rustc 版本现查为准。这是本大主题的显式待核冲突面（清单锚 NLL 为基线，Polonius 状态未定）。

## R3.5 悬垂引用与切片

### R3.5.1 悬垂引用与 E0106

悬垂引用（dangling reference）指一个引用指向的数据已经被释放、引用却仍存活——解引用它就是访问已失效内存。Rust 的借用检查器保证这在安全代码里不可能发生：一个引用的存活区域绝不能超出它所指数据的存活区域。最经典的形态是函数返回一个指向其局部变量的引用：局部变量在函数返回时就被 Drop，返回的引用必然悬垂。

```rust
fn dangle() -> &String {
    let s = String::from("hello");
    &s                     // 返回指向 s 的引用，但 s 马上要被释放
}
fn main() { let _r = dangle(); }
```

本机 rustc 1.94.1 现跑，报 E0106（缺生命周期标注）：

```text
error[E0106]: missing lifetime specifier
 --> dangle.rs:1:16
  |
1 | fn dangle() -> &String {
  |                ^ expected named lifetime parameter
  |
  = help: this function's return type contains a borrowed value, but there is
          no value for it to be borrowed from
help: instead, you are more likely to want to return an owned value
  |
1 - fn dangle() -> &String {
1 + fn dangle() -> String {
```

初学者常被 E0106 的字面（"缺生命周期说明符"）迷惑，以为只要加个 `'a` 就行。实则编译器给出的第二条建议才是对的：这里根本没有可供借用的、活得够久的数据，正确做法是直接返回拥有所有权的 `String`（把值移出去，见 R2.3），而不是返回引用。生命周期标注的完整机制交由 R4；本篇只需记住 E0106 在这种场景是在阻止悬垂。

### R3.5.2 借用会阻止被借数据被移动或释放（E0505）

反过来，只要一个引用还存活，被它借用的值就不能被移动（move）或释放，否则引用会悬垂。这保证了悬垂不仅在函数返回处被防，在普通作用域内也被防。

```rust
fn main() {
    let s = String::from("hi");
    let r = &s;            // 借用 s
    let s2 = s;            // 试图把 s move 走 —— 但 r 还要用
    println!("{} {}", r, s2);
}
```

本机 rustc 1.94.1 现跑，报 E0505：

```text
error[E0505]: cannot move out of `s` because it is borrowed
 --> e0505.rs:4:14
  |
3 |     let r = &s;
  |             -- borrow of `s` occurs here
4 |     let s2 = s;
  |              ^ move out of `s` occurs here
5 |     println!("{} {}", r, s2);
  |                       - borrow later used here
```

这把 R2 的 move 语义和 R3 的借用规则连了起来：值被借用期间是"锁住"的，既不能被移动、也不能被销毁。这正是引用能安全存在的前提——编译器保证被指向的数据在引用有效期内一定还在原地。

### R3.5.3 切片：借用集合的一段连续视图

切片（slice）是一种不持有所有权的引用，指向某个集合中一段连续的元素，写作 `&[T]`（数组/`Vec` 的切片）或 `&str`（字符串切片）。切片在内部是"指针 + 长度"的胖指针（fat pointer），它借用底层数据、不复制、不拥有，因此同样受借用规则约束。

```rust
fn first_word(s: &str) -> &str {
    let bytes = s.as_bytes();
    for (i, &b) in bytes.iter().enumerate() {
        if b == b' ' {
            return &s[..i];       // 返回借用输入的一段，未复制
        }
    }
    &s[..]
}

fn main() {
    let s = String::from("hello world");
    println!("{}", first_word(&s));   // 打印 "hello"
}
```

`first_word` 返回的 `&str` 是对输入 `s` 的一段借用，没有任何拷贝，且因为它是借用，如果之后 `s` 被清空或释放，编译器会阻止你再用这个切片（同 R3.5.2 的机制），从而杜绝"返回了一个指向已变动数据的下标/指针"这一 C 系语言的经典 bug。易错点：切片的字节下标必须落在合法边界上，尤其 `&str` 切片必须切在 UTF-8 字符边界，否则运行时 panic——切片保证的是内存安全（不越界访问已释放内存），越界/非字符边界这类逻辑错误由运行时边界检查兜底、以 panic 形式暴露，而非 UB。

#### 来源与时效
- The Rust Programming Language book Ch.4 §4.2「Dangling References」（返回局部引用的悬垂例）与 §4.3「The Slice Type」（`&str`/`&[T]`、`first_word` 例、切片是借用不拥有）https://doc.rust-lang.org/book/ch04-03-the-slice-type.html ；核实 2026-08-01。
- The Rust Reference：slice types（切片为胖指针 [ptr, len]、`str` 需 UTF-8 有效）https://doc.rust-lang.org/reference/types/slice.html 、str 类型 https://doc.rust-lang.org/reference/types/textual.html ；核实 2026-08-01。
- 本机实测 rustc 1.94.1：`dangle` 现跑报 E0106（输出见正文）；move-while-borrowed 现跑报 E0505（输出见正文）；`first_word` 切片示例编译并运行输出 `hello`。
- 交叉一致，无冲突：the book（教学侧："slice 不拥有所有权、防悬垂"）与 Reference（类型侧："slice 是 [ptr, len] 胖指针、`str` UTF-8 约束"）互补。字符边界 panic 属运行时行为，非编译期借用检查范畴，已在正文分账标明。
