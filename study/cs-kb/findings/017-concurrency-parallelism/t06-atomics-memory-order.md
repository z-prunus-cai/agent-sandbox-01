# L4-05·大主题06 原子操作与 memory_order

> 基线 Py3.11.15/np2.4.6/gcc13.3 @2026-07-25 ｜ 核实日期：2026-07-30 ｜ 先修：本课大主题05（内存一致性模型与重排序：happens-before、DRF、x86-64 TSO、编译器/硬件两级重排）、大主题02（并发对象与线性一致性）｜ 一手锚点：ISO/IEC 9899:2024（C23）§7.17「Atomics `<stdatomic.h>`」及 §5.1.2.5 多线程执行与数据竞争（工作草案 N3220，2024，本报告逐条引用其原文）；ISO C++ `[atomics]` / `[atomics.order]` 作交叉印证；cppreference 仅作指路、结论回落 ISO ｜ 成熟度：GA/稳定（`<stdatomic.h>` 自 C11 引入、C17 无实质改动、C23 措辞有调整；`memory_order_consume` 自 C++17/P0371 起被"暂时劝退"，见 6.2.3）

> 粒度判定：**1 份，不拆**。本大主题 6 个小主题（6.1–6.6）围绕同一套机制——C 语言级原子操作与内存序——层层展开：先讲原子类型与泛型接口是什么（6.1），再给六档内存序的强弱谱（6.2），随后是最常用的 acquire/release 配对惯用法（6.3）、读改写与 CAS（6.4）、独立栅栏（6.5）、以及 lock-free 判定（6.6）。单一机制族、篇幅适中，按 report-format v3 §一默认 1 大主题 = 1 报告，不拆 `-a/-b`。

> 本报告一条主线心智模型：普通变量在多线程并发读写下既可能被编译器/硬件重排、又可能"撕裂"（非不可分），一旦有数据竞争就是未定义行为（UB，归大主题07 深讲）。**原子对象**做两件事——保证单次访问不可分（不撕裂），以及通过 **memory_order** 参数控制"这次访问顺带建立多强的跨线程可见性/顺序保证"。六档内存序就是一把从"只保证不撕裂、什么顺序都不管"（relaxed）到"全局单一总序"（seq_cst）的强弱旋钮；acquire/release 是其中最实用的一档，用一对 release-store 与 acquire-load 精确地在两个线程间架起 happens-before 这座"因果桥"。抓住"原子性"与"顺序性"是两件正交的事，本大主题就通了。

> 分账（本课不外扩，只在交界处一句指路）：本报告讲**语言级语义**——`<stdatomic.h>` 类型、六档内存序、RMW/CAS、fence、lock-free 宏。原子性的**硬件来源**（x86 `LOCK` 前缀、`cmpxchg`、关中断如何造出不可分）归 L4-01·OS-06；**为什么需要内存序**背后的重排模型（TSO、编译器 vs 硬件两级重排、happens-before/DRF 定理本身）归本课大主题05；**数据竞争的精确定义与 UB 后果**归大主题07；CAS 的**共识能力**（consensus number=∞、universal construction）归大主题04。本报告只把这些作为"为什么"一句带过，落点始终在 C 标准的接口与语义。

> 本主题本机实证腿扎实且已真跑（gcc 13.3.0 `-std=c11`）：lock-free 宏值打印、原子/非原子计数丢更新对比、`objdump` 看各内存序生成码，均贴真实输出与可复现命令于正文。未跑的项（如 ARM 弱序上 acquire/release 的可见差异）如实标「未取」。

---

## 6.1 `_Atomic` 与泛型接口

### 6.1.1 `_Atomic` 类型限定符与"原子对象"

C11 引入了关键字 `_Atomic`，把一个普通类型变成**原子类型**。对原子对象的每一次库定义的原子操作都是**不可分的（indivisible）**：任何其他线程要么看到操作前的完整值、要么看到操作后的完整值，绝不会看到"改了一半"的撕裂中间态。写法有两种，二者等价：

```
_Atomic int a;          /* 限定符写法 */
_Atomic(int) b;         /* 带括号写法，用于复杂类型如 _Atomic(void*) */
```

普通 `int a; a++;` 在机器层面往往是"读—加—写回"三步，两个线程同时做就可能互相覆盖（丢更新），而且编译器/硬件还能自由重排它相对其他访问的位置。把它声明成 `_Atomic int` 后，语言保证针对它的原子操作不可分，并且每次操作可以附带一个内存序参数来约束它与周围访问的相对顺序（6.2）。初学者可以先记一句话：`_Atomic` 管"这一下不会被撕开"，内存序管"这一下顺带锁死多少周边顺序"——两件事分开的，这正是本大主题反复强调的正交性。

C23 明确了原子对象的初始化保证（N3220 §7.17.2）：具有静态或线程存储期、且非 `atomic_flag` 类型的原子对象，其显式或默认初始化"保证产生有效状态"；但自动存储期的原子对象若未初始化则表示不确定，且在原子对象被置为有效状态**之前**并发访问它（即便通过原子操作）本身就构成数据竞争。

### 6.1.2 `<stdatomic.h>` 的类型别名与 C23 对初始化的措辞调整

`<stdatomic.h>` 为常用原子整数类型提供了一批可读别名，等价于对应的 `_Atomic` 限定类型（N3220 §7.17.6）：

```
atomic_int      等价于  _Atomic int
atomic_uint     等价于  _Atomic unsigned int
atomic_bool     等价于  _Atomic bool
atomic_size_t   等价于  _Atomic size_t
atomic_llong    等价于  _Atomic long long
... （表中共约 40 个别名，含 C23 新增的 atomic_char8_t 等）
```

头文件还提供宏 `__STDC_VERSION_STDATOMIC_H__`，在 C23 中其值为 `202311L`；若实现定义了 `__STDC_NO_ATOMICS__`，则它可以不提供该头文件（原子是**可选特性**，这点自 C11 起如此）。

这里有一处**C11→C23 的具体措辞变化**，值得单标：C11 里初始化原子对象要用宏 `ATOMIC_VAR_INIT(value)`；该宏在 C17 被标为弃用，到 **C23 已被移除**——N3220 §7.17.1 的宏清单里只剩下各 `ATOMIC_*_LOCK_FREE` 宏和 `ATOMIC_FLAG_INIT`，不再有 `ATOMIC_VAR_INIT`，§7.17.2 的示例也直接写 `_Atomic int guide = 42;`。初学者遇到老代码里的 `ATOMIC_VAR_INIT` 不必惊慌，直接用普通初始化式即可；但要注意 `atomic_init()` 函数仍在（用于给对象赋初值且顺带初始化实现可能需要的附加状态），只是它**不避免数据竞争**（§7.17.2.1）。

### 6.1.3 泛型接口：atomic_load / store / exchange 与 `_explicit` 变体

对原子对象的读写不直接用 `=`（那样只能得到 seq_cst 语义，见下），而是用一组**泛型函数**（generic functions，可以是宏也可以是真函数，标准不指定，N3220 §7.17.1p7）。三个最基础的：

```
void atomic_store(volatile A *object, C desired);
C    atomic_load(const volatile A *object);
C    atomic_exchange(volatile A *object, C desired);   /* 写入新值并返回旧值，是一次 RMW */
```

每个都有一个 `_explicit` 后缀的变体，多带一个 `memory_order order` 参数：

```
void atomic_store_explicit(volatile A *object, C desired, memory_order order);
C    atomic_load_explicit(const volatile A *object, memory_order order);
```

标准规定（N3220 §7.17.1p6）：不带 `_explicit` 的函数，其语义等同于以 `memory_order_seq_cst` 作为内存序参数的 `_explicit` 版本。也就是说 `atomic_store(&x, 1)` 就是"最强也最保守"的 seq_cst 写；想要更弱更快的语义，就得显式写 `_explicit` 版本并挑一档更弱的序。此外标准还约束了哪些序对某操作**非法**：`atomic_store` 的 order 不得为 acquire/consume/acq_rel；`atomic_load` 的 order 不得为 release/acq_rel（§7.17.7.1–.2）——直觉上"纯写"不该带 acquire、"纯读"不该带 release，配错方向没有意义。

`gcc 13.3.0` 以 `-std=c11` 编译一个最小原子程序通过，确认工具链支持 C11 原子。

```
$ cat macros.c   # 片段
_Atomic int a = 0;
printf("int is_lock_free=%d\n", atomic_is_lock_free(&a));

$ gcc -std=c11 -O2 macros.c -o macros && ./macros
BOOL=2 CHAR=2 SHORT=2 INT=2 LONG=2 LLONG=2 PTR=2
int is_lock_free=1, llong is_lock_free=1
```

（宏值含义见 6.6；此处仅证明 `-std=c11` 下 `<stdatomic.h>` 可用。）

#### 来源与时效
- ISO/IEC 9899:2024 (C23) 工作草案 N3220 §7.17.1「Introduction」/ §7.17.2「Initialization」/ §7.17.6「Atomic integer types」/ §7.17.7.1–.3（一手，2024，本报告直接读取 PDF 原文核实 2026-07-30）：`_Atomic`、原子对象不可分与初始化保证、`__STDC_VERSION_STDATOMIC_H__ == 202311L`、`__STDC_NO_ATOMICS__` 可选特性、类型别名表、atomic_store/load/exchange 及 `_explicit` 变体、"不带 `_explicit` 即 seq_cst"、store/load 的非法序约束。
- `ATOMIC_VAR_INIT` 移除：N3220 §7.17.1 宏清单**不含** `ATOMIC_VAR_INIT`（对照 C11/9899:2011 §7.17.1 曾含之），§7.17.2 示例改用普通初始化式——两版对照坐实 C23 已移除（核实 2026-07-30）。
- 本机实证：gcc 13.3.0 `-std=c11`，Linux 6.18.5 x86-64（核实 2026-07-30）。
- 交叉一致，无冲突。

## 6.2 六档 memory_order

### 6.2.1 memory_order 枚举：六个常量与强弱谱

`memory_order` 是一个枚举类型，其枚举常量恰好六个，标准原文（N3220 §7.17.3）列为：

```
memory_order_relaxed
memory_order_consume
memory_order_acquire
memory_order_release
memory_order_acq_rel
memory_order_seq_cst
```

它们**不是一条简单的从弱到强的直线**，而更像一张按"提供多强的顺序/可见性保证"排的谱：relaxed 最弱（只保证本次访问不可分，不建立任何跨线程顺序）；consume/acquire 用于读侧、release 用于写侧、acq_rel 用于既读又写的 RMW；seq_cst 最强（在前述基础上再加一条全局单一总序）。

初学者最容易踩的坑是把内存序当成"给这个变量设的属性"。它不是——**内存序是每一次操作各自携带的参数**，同一个原子对象完全可以这次 relaxed 读、下次 acquire 读。选哪一档，取决于"这次访问需要顺带保证周围哪些普通内存的可见性"。默认（不写 `_explicit`）给的是 seq_cst，最安全但在弱序机器上最慢；性能优化就是在能证明正确的前提下把它往弱里调。

### 6.2.2 relaxed：只保证不可分，不管顺序

`memory_order_relaxed` 的语义（N3220 §7.17.3p2）是"no operation orders memory"——本次原子访问只保证对该对象的原子性/不可分（§7.17.3 NOTE 2：relaxed 仅在内存**排序**上放松，单次原子访问相对同对象的其他原子访问仍不可分），但不与任何其他内存访问建立先后关系。

典型用途是"只要计数对、不要求看见别的东西"的场景，比如一个纯统计计数器：多个线程各自 `fetch_add` 累加，最后只关心总数正确，不借它同步别的数据。它是最快的一档（在 x86 上一次 relaxed store 就是一条普通 `mov`，见 6.2.6 反汇编）。易错点：relaxed 决不能用来做"标志位一置起、另一线程就能安全读旁边那块数据"这种同步——那需要 release/acquire 配对（6.3），因为 relaxed 不搬运任何"旁边数据已就绪"的可见性。标准还专门用 NOTE 3 说明 relaxed 在环形依赖下允许出现看似"凭空出现"（out-of-thin-air）的诡异取值边界，进一步印证它不给因果保证。

### 6.2.3 consume：语义正确但被"暂时劝退"的一档（时效红旗）

`memory_order_consume` 用于读侧，本意是一种比 acquire 更弱、更省的顺序：它只保证与被读值存在**数据依赖**（dependency）的后续访问不被重排到该读之前，而不像 acquire 那样约束该读之后的**所有**访问。这对 RCU 这类"读一个指针、再顺着它解引用"的模式在 ARM/POWER 等弱序机器上本可省下屏障。

consume 虽在标准里，但自 C++17 起被官方"暂时劝退"。提案 **P0371（"Temporarily discourage memory_order_consume"）** 指出其现行定义"无法被正确使用"，因此**所有主流编译器实际上都把 consume 直接当 acquire 实现**（即悄悄升级为更强的一档，语义仍安全、只是没省到）。后续 C++20 对定义做了微调、2025 年又有 P3475（"Defang and deprecate memory_order::consume"）推动进一步弱化/弃用。C 侧 N3220 仍保留该枚举常量与 `kill_dependency` 宏（§7.17.3.1，用于手动切断依赖链）。初学者的实用结论：**现在别用 consume，需要读侧顺序就用 acquire**；遇到它按 acquire 理解即可。这是本报告唯一的时效红旗项。

### 6.2.4 acquire 与 release：读侧/写侧配对的半栅栏

`memory_order_acquire` 用于**读**（load），`memory_order_release` 用于**写**（store）。标准语义（N3220 §7.17.3p3–4）：release/acq_rel/seq_cst 的 store 对该位置执行一次 release 操作；acquire/acq_rel/seq_cst 的 load 对该位置执行一次 acquire 操作。它们单独看是"半个栅栏"：release-store 挡住"它之前的写"被重排到它之后，acquire-load 挡住"它之后的读写"被重排到它之前。

真正的威力在于**配对**：当一个 acquire-load 读到了某个 release-store 写入的值，两者就建立起 synchronizes-with 关系，进而在两个线程间形成 happens-before（6.3 详述）。这是 lock-free 编程里最常用、性价比最高的一档——比 seq_cst 便宜（无需全局总序），又足以传递"数据已就绪"的因果。初学者记法：release 是"我把前面备好的货都摆上架，再翻牌子"，acquire 是"我看到牌子翻了，才敢去取货"——只要翻牌/看牌这一对原子操作对上了，前面摆的货就保证看得见。

### 6.2.5 acq_rel：给"既读又写"的 RMW 用

`memory_order_acq_rel` 同时具备 acquire 和 release 语义，专门给**读改写（RMW）**操作用（如 `atomic_exchange`、`atomic_compare_exchange`、`atomic_fetch_add`）——因为 RMW 既读旧值（需要 acquire 侧保证）又写新值（需要 release 侧保证）。用它，可以让一次 CAS 同时"看到之前别人 release 的东西"并"把自己前面的写 release 出去"，无需上到 seq_cst。

对纯 load 用 acq_rel、或对纯 store 用 acq_rel 都是配错方向（标准对 store/load 的合法序有约束，见 6.1.3）。初学者只需记：acq_rel 是"给 RMW 的 acquire+release 合体档"。

### 6.2.6 seq_cst：最强的一档与单一全序 S

`memory_order_seq_cst` 是默认档，也是最强档。除了带上 acquire/release 的全部效果外，它额外要求（N3220 §7.17.3p6）：所有 seq_cst 操作之间存在**单一的全序 S**，且该全序与 happens-before 及各位置的修改序一致。直观说，所有线程对"所有 seq_cst 操作谁先谁后"看法完全一致，如同存在一位全局裁判把它们排成一条队。

这正是初学者心智里"多线程就该像把各线程语句交错成一条序列"（顺序一致性 SC）的那个模型——只要全程只用 seq_cst 原子且无数据竞争，程序行为就等于 SC，最好推理。代价是：在弱序硬件上要插最重的屏障。在 x86-64（TSO）上，代价体现在**写**上——本机反汇编可见 seq_cst store 编成 `xchg`（隐含 lock 前缀、带全屏障效果），而 relaxed store 只是普通 `mov`；acquire load 则和普通 `mov` 一样（因为 x86 的 load 本就有 acquire 性质，属大主题05 的 TSO 结论）：

```
$ gcc -std=c11 -O2 -c gen.c -o gen.o && objdump -d gen.o --no-show-raw-insn
<store_seqcst>:   xchg   %edi,0x0(%rip)     # seq_cst 写 = xchg（隐含 lock，全屏障）
<store_relaxed>:  mov    %edi,0x0(%rip)     # relaxed 写 = 普通 mov
<load_acquire>:   mov    0x0(%rip),%eax     # acquire 读 = 普通 mov（x86 load 本就 acquire）
```

**同一段源码在不同内存序下生成的机器码不同**，内存序不是"注释"，是实打实影响编译产物与硬件屏障的。x86 只在 store→load 一处放松，故 acquire/release 几乎不花额外指令、只花编译期不重排；seq_cst 的额外成本集中在写侧——这是 x86 特有，ARM/POWER 上代价分布不同（本机无 ARM 硬件，未取跨平台实测）。

#### 来源与时效
- N3220 §7.17.3「Order and consistency」p1–14（一手，2024，读 PDF 原文核实 2026-07-30）：六个枚举常量的确切名字与顺序、relaxed「no operation orders memory」及 NOTE 2/NOTE 3、release/acquire/acq_rel/seq_cst 各自在 store/load 上的语义、seq_cst 单一全序 S 与其一致性条件、consume 执行 consume 操作。§7.17.3.1 `kill_dependency`（consume 依赖链切断）。
- consume 弃用现状（时效红旗）：WG21 P0371R0/R1「Temporarily de(precate/discourage) memory_order_consume」（open-std.org，2016）——现行定义无法正确使用、编译器普遍以 acquire 实现；WG21 P3475R2「Defang and deprecate memory_order::consume」（open-std.org，2025）推动进一步弃用。二手佐证：微软 Old New Thing 2023-04-27 博文，述"C++17 temporarily discouraged consume … all compilers implement it as acquire"。三者一致（核实 2026-07-30，经 WebSearch 命中 open-std 提案标题与摘要）。
- 本机反汇编：gcc 13.3.0 `-std=c11 -O2`，`objdump -d`（binutils 2.42），Linux 6.18.5 x86-64（核实 2026-07-30）。x86-64 只放松 store→load 属大主题05 结论（Intel SDM Vol.3A），此处仅作生成码印证。
- 冲突项：无。consume 一项标准文本（仍在）与实现现状（普遍当 acquire）不同层，已分账标注、非冲突。

## 6.3 acquire/release 配对建立跨线程 happens-before

### 6.3.1 synchronizes-with 如何升级成 happens-before

这是 lock-free 同步的核心机制。规则是：若线程 T2 的一个 **acquire-load** 读到了线程 T1 某个 **release-store** 写入的值，则该 release-store **synchronizes-with** 该 acquire-load。而 happens-before 由"同一线程内的 sequenced-before"与"跨线程的 synchronizes-with"传递闭包而成。于是链条接起来：

```
（T1 内）备好数据的写  --sequenced-before-->  release-store 标志位
                                                    |
                                              synchronizes-with   （T2 的 acquire-load 读到该标志）
                                                    |
（T2 内）acquire-load 标志位  --sequenced-before-->  读取数据
```

结果是"T1 备好数据的写" happens-before "T2 读取数据"，因此 T2 **保证**看得见 T1 备的数据，且不构成数据竞争。这就是把两个线程的局部程序序，通过一对原子操作"焊接"成一条全局因果链。

**光有 release 或光有 acquire 都没用，必须配成对、且 acquire 那侧真的读到了 release 那侧写的值**，桥才算搭上。如果 T2 的 acquire-load 读到的是标志位的旧值（还没被 T1 翻牌），那就没有 synchronizes-with，也就不保证看见数据——这正是要用 `while` 循环重读标志的原因之一。

### 6.3.2 MP（message passing）惯用法最小例

最经典的用法叫 message passing：一个线程把数据写好、再 release 一个"就绪"标志；另一个线程 acquire 那个标志、见到就绪后再读数据。

```
/* 生产者 T1 */
data = 42;                                              /* 普通写，备货 */
atomic_store_explicit(&ready, 1, memory_order_release); /* release：翻牌 */

/* 消费者 T2 */
while (atomic_load_explicit(&ready, memory_order_acquire) == 0)   /* acquire：等牌翻 */
    ;
assert(data == 42);   /* 保证成立：data 的写 happens-before 这里的读 */
```

`data` 本身是**普通（非原子）变量**也没关系——只要它的写在 release 之前、它的读在配对的 acquire 之后，happens-before 就覆盖了它，读到 42 有保证、且不是数据竞争。这解释了为何 release/acquire 能"顺带"把一整块普通数据安全地交接过去，而不必把每个字段都做成原子。若把这里的 release/acquire 换成 relaxed，则 `data == 42` 不再有保证（relaxed 不搬运旁边数据的可见性），可能读到旧值——这是初学者最典型的错误。

x86 上 acquire-load 与 release-store 都编成普通 `mov`（见 6.2.6），运行时几乎零额外硬件成本，屏障主要作用在**编译期禁止重排**；因此在 x86 上即便误用 relaxed 也常"碰巧"能跑对，但换到 ARM/POWER 就会暴露 bug——正确性必须按标准语义而非某平台的巧合来判。（本机为 x86，ARM 上的可见差异未取实测，如实标「未取/跨平台」。）

### 6.3.3 release sequence 与常见易错点

release sequence。若一个 release-store 之后，同一原子对象上跟着一串该线程的写或**任意线程的 RMW**，acquire 侧读到这串里任一个值，同样能与最初那个 release 建立 synchronizes-with（N3220 §7.17.4 对 fence 版本有对应表述）。直觉上，RMW 不打断这条 release 传递链，这让"多个消费者靠 CAS 接力"的模式仍能保持因果。

三个高频易错点值得初学者记牢：其一，配对必须落在**同一个原子对象**上——T1 release 对象 X、T2 acquire 对象 Y，不建立任何关系。其二，方向不能反——写用 release、读用 acquire，写用 acquire 或读用 release 是无意义/非法的。其三，happens-before **不具"传递给无关第三方"的全局性**：release/acquire 只保证这一对参与线程间的因果，不像 seq_cst 那样给全体一个统一总序；需要多个线程对多个变量的先后达成全局共识时（如 IRIW 型样式），acquire/release 可能不够，得上 seq_cst。

#### 来源与时效
- N3220 §5.1.2.5（多线程执行：synchronizes-with、happens-before、release/acquire 操作的定义）与 §7.17.3p3–4、§7.17.4（fence 的 synchronizes-with 及 release sequence 表述）（一手，2024，核实 2026-07-30）。
- ISO C++ `[atomics.order]` / `[intro.races]`（交叉印证 release-acquire synchronizes-with 与 happens-before 的等价措辞；cppreference 指路、结论回落 ISO）。
- MP 惯用法与"data 为普通变量仍安全"：C23 §5.1.2.5 happens-before 覆盖非原子访问即无竞争，交叉 AMP 附录内存模型讨论一致（核实 2026-07-30）。
- 本机：x86 上 acquire/release 编为 `mov`（6.2.6 反汇编，binutils 2.42）；ARM/POWER 可见差异**未取**（无对应硬件），如实标注。
- 冲突项：无。

## 6.4 RMW 操作（read-modify-write）

### 6.4.1 RMW 的定义与 fetch_key 家族

**读改写（read-modify-write, RMW）**操作把"读旧值—据此算新值—写回"这三步合成一次不可分的原子操作，中途不会被别的线程插进来。标准里 `atomic_exchange`、`atomic_compare_exchange_*`、以及 `atomic_fetch_*` 家族都是 RMW（N3220 §7.17.7.3–.5）。算术/位运算 RMW 用统一的 `key` 模板给出（§7.17.7.5）：

```
key op computation
add  +  addition
sub  -  subtraction
or   |  bitwise inclusive or
xor  ^  bitwise exclusive or
and  &  bitwise and

C atomic_fetch_add(volatile A *object, M operand);              /* 返回旧值 */
C atomic_fetch_add_explicit(volatile A *object, M operand, memory_order order);
```

`atomic_fetch_add` 原子地把 `object` 加上 `operand` 并**返回加之前的旧值**。标准还明确：有符号整数的 RMW 算术在溢出时执行"静默回绕（silent wraparound）"，**没有未定义行为**（这与普通有符号溢出是 UB 不同，是原子 RMW 的一个便利保证）。

本机实证——原子 `fetch_add` 与非原子 `++` 在 4 线程各累加 100 万次下的对比，直观展示"丢更新"：

```
$ gcc -std=c11 -O2 -pthread count.c -o count && ./count
expected=4000000  plain(non-atomic)=2340265  atomic_fetch_add=4000000
```

非原子 `plain++` 因"读—加—写回"被并发穿插，丢了近一半更新（2340265 vs 期望 4000000，且每次运行数值不同）；`atomic_fetch_add` 每次都精确等于 4000000。这就是 RMW 不可分性的价值。

### 6.4.2 CAS：compare_exchange_strong / weak

**比较并交换（compare-and-swap, CAS）**是 lock-free 编程的主力 RMW。C 里是 `atomic_compare_exchange_strong/weak`（N3220 §7.17.7.4）：

```
bool atomic_compare_exchange_strong(volatile A *object, C *expected, C desired);
```

语义（§7.17.7.4p3–4）：原子地比较 `*object` 与 `*expected`；**相等**则把 `*object` 写成 `desired` 并返回 true（成功）；**不等**则把 `*object` 的当前值写回 `*expected`（更新调用者的期望）并返回 false（失败）。标准给的等价伪码是：

```
if (memcmp(object, expected, sizeof(*object)) == 0)
    memcpy(object, &desired, sizeof(*object));
else
    memcpy(expected, object, sizeof(*object));
```

关键在于整个"比较+写"是一次不可分操作——这让线程能表达"只有当值还是我以为的那个，才把它换掉；否则告诉我现在是多少，我重试"。它是构造无锁计数器、栈、队列的基本积木。本机反汇编可见 CAS 编成 x86 的 `lock cmpxchg`：

```
<cas_strong>:  lock cmpxchg %edx,0x0(%rip)   # 硬件级比较并交换，lock 前缀保证不可分
```

### 6.4.3 weak 的伪失败与 CAS 循环惯用法

`weak` 与 `strong` 的差别是：**weak 允许"伪失败（spurious failure）"**——即使 `*object` 与 `*expected` 实际相等，它也可能返回 false 并把原值写回 expected（N3220 §7.17.7.4p5）。标准解释（NOTE 2）这是为了让 CAS 能在 LL/SC（load-locked/store-conditional，如 ARM/POWER）这类机器上高效实现。

因此 weak 几乎总是**放在循环里**用，标准给的惯用法：

```
exp = atomic_load(&cur);
do {
    des = function(exp);
} while (!atomic_compare_exchange_weak(&cur, &exp, des));
```

失败（无论真失败还是伪失败）时 `exp` 已被自动更新为最新值，循环体据此重算 `des` 再试。选型规则（§7.17.7.4p7）：当 CAS 本来就要放在循环里时，用 weak 在某些平台性能更好；当"用 weak 反而要额外加循环、strong 不用循环"时，用 strong。初学者记：**循环里用 weak，单发一锤子买卖用 strong**。

### 6.4.4 success/failure 两个内存序参数

CAS 的 `_explicit` 版本带**两个**内存序参数——成功和失败各一个：

```
bool atomic_compare_exchange_strong_explicit(
        volatile A *object, C *expected, C desired,
        memory_order success, memory_order failure);
```

因为 CAS 成功时是一次完整的 RMW（既读又写，可能要 acq_rel），失败时只是一次读（只能要 load 合法的序）。标准约束（§7.17.7.4p2）：`failure` 不得为 release 或 acq_rel，且 `failure` 不得强于 `success`。常见配法是成功用 `acq_rel`（或 `acquire`）、失败用 `acquire`（或 `relaxed`）。初学者一开始可以全用默认 seq_cst 保正确，跑通后再按需放松，并牢记这条"failure 不得强于 success、且不能带 release"的约束。

#### 来源与时效
- N3220 §7.17.7.3「atomic_exchange」/ §7.17.7.4「atomic_compare_exchange」p2–8 / §7.17.7.5「atomic_fetch and modify」（一手，2024，读 PDF 原文核实 2026-07-30）：RMW 定义、fetch_key 的 key/op/computation 表、有符号溢出静默回绕无 UB、CAS strong/weak 语义与等价伪码、weak 伪失败与循环惯用法、strong/weak 选型、success/failure 双序及"failure 不强于 success、不得 release/acq_rel"约束。
- 本机实证：`atomic_fetch_add` vs 非原子计数丢更新对比（4 线程×100 万，真实输出 plain≈2.1–2.3M vs atomic=4000000），`objdump` 见 `lock cmpxchg`/`lock addl`（gcc 13.3.0 `-std=c11 -O2 -pthread`，binutils 2.42，Linux 6.18.5，核实 2026-07-30）。
- CAS 共识能力（consensus number=∞）归大主题04，此处不展开；`LOCK` 前缀的原子性硬件来源归 L4-01·OS-06。
- 交叉一致（C 标准文本与本机生成码/输出相符），无冲突。

## 6.5 fence（`atomic_thread_fence`）

### 6.5.1 独立栅栏的语义

`atomic_thread_fence(memory_order order)` 是一个**不绑定到任何具体原子对象**的独立内存栅栏（N3220 §7.17.4.1）。它按 order 取值提供不同屏障：

```
memory_order_relaxed  → 无效果
memory_order_acquire / memory_order_consume  → acquire 栅栏
memory_order_release  → release 栅栏
memory_order_acq_rel  → 既是 acquire 又是 release 栅栏
memory_order_seq_cst  → 顺序一致的 acquire+release 栅栏
```

与"在某次 load/store 上直接标内存序"不同，fence 是把屏障单独放一处，约束**它前后一片**普通/原子访问的相对顺序。标准（§7.17.4p2–4）给出 fence 版的 synchronizes-with 规则：一个 release 栅栏可以与一个 acquire 栅栏（或 acquire 操作）通过中间某个原子对象的读写建立同步。

初学者可以这样理解两种写法的关系：`atomic_store_explicit(&x, v, release)` 相当于"先摆一道 release 栅栏、再做一个 relaxed store"的合并简写；而独立 fence 让你把这道栅栏和数据操作解耦，适合"先一次性备好多个变量、再统一插一道 release 栅栏、然后 relaxed 翻标志"的写法。多数日常场景用操作自带的内存序即可，fence 更多出现在需要精细控制或与 relaxed 原子搭配的高级代码里。

### 6.5.2 fence 在 x86 上的生成码；与 atomic_signal_fence 之别

本机反汇编印证了 x86 TSO 下不同 fence 的成本差异：

```
<fence_seqcst>:   lock orq $0x0,(%rsp)   # seq_cst 栅栏 = 一条带 lock 的空操作（等效全屏障 mfence）
<fence_acquire>:  ret                    # acquire 栅栏在 x86 上不生成任何指令（TSO 下 load 已有序）
                  （release 栅栏同样通常不生成硬件指令，仅编译期禁止重排）
```

也就是说，在 x86 上 acquire/release 栅栏只是"编译器别重排"的约定、不花硬件指令；只有 seq_cst 栅栏才真的插一条全屏障（这里 gcc 用 `lock orq $0x0,(%rsp)` 而非 `mfence`，是常见的等效实现）。换到弱序架构，acquire/release 栅栏就会生成真实屏障指令了（本机无对应硬件，未取实测）。

注意区分一个"近亲"：`atomic_signal_fence(order)`（§7.17.4.2）语义等同 `atomic_thread_fence`，但只在**同一线程内的线程与其信号处理器之间**建立顺序——它抑制编译器对 load/store 的重排，但**不发射** `atomic_thread_fence` 会插的硬件屏障指令。它用于信号处理场景，不用于跨线程同步，初学者别把两者混用。

#### 来源与时效
- N3220 §7.17.4「Fences」p1–4 / §7.17.4.1「atomic_thread_fence」（order 到栅栏类型的对应表）/ §7.17.4.2「atomic_signal_fence」（一手，2024，读 PDF 原文核实 2026-07-30）。
- 本机反汇编：seq_cst fence = `lock orq $0x0,(%rsp)`、acquire fence 无指令（gcc 13.3.0 `-std=c11 -O2`，`objdump -d`，binutils 2.42，Linux 6.18.5 x86-64，核实 2026-07-30）。gcc 用 `lock or` 等效 `mfence` 属实现选择；x86 只放松 store→load 的模型结论归大主题05（Intel SDM Vol.3A）。
- 弱序架构上 acquire/release fence 生成真实屏障：**未取**（无 ARM/POWER 硬件），如实标注。
- 交叉一致，无冲突。

## 6.6 lock-free 判定

### 6.6.1 `ATOMIC_*_LOCK_FREE` 宏（0/1/2）

`<stdatomic.h>` 提供一组编译期宏，指示对应原子类型是否 lock-free（N3220 §7.17.1p4、§7.17.5）：

```
ATOMIC_BOOL_LOCK_FREE   ATOMIC_CHAR_LOCK_FREE   ATOMIC_CHAR8_T_LOCK_FREE
ATOMIC_CHAR16_T_LOCK_FREE  ATOMIC_CHAR32_T_LOCK_FREE  ATOMIC_WCHAR_T_LOCK_FREE
ATOMIC_SHORT_LOCK_FREE  ATOMIC_INT_LOCK_FREE  ATOMIC_LONG_LOCK_FREE
ATOMIC_LLONG_LOCK_FREE  ATOMIC_POINTER_LOCK_FREE
```

取值只有三种（§7.17.5p1）：

```
0 = 该类型永不 lock-free（总是用锁模拟）
1 = 有时 lock-free（运行期才定，如取决于对齐）
2 = 总是 lock-free
```

它们是**常量表达式**，可用于 `#if` 预处理条件编译，让代码在编译期就按平台的 lock-free 能力走不同分支。本机实测全部为 2：

```
$ ./macros
BOOL=2 CHAR=2 SHORT=2 INT=2 LONG=2 LLONG=2 PTR=2
```

即在此 x86-64 + gcc 13.3.0 平台上，这些内建整数/指针原子类型都是"总是 lock-free"——因为硬件有对应位宽的原子指令（`lock` 前缀等）。

### 6.6.2 `atomic_is_lock_free` 运行期查询；两个易混淆点

编译期宏只覆盖内建类型；对任意原子对象（尤其是自定义大结构的 `_Atomic`），要用**运行期函数**查询：

```
bool atomic_is_lock_free(const volatile A *obj);
```

它返回该对象的原子操作是否 lock-free（N3220 §7.17.5.1）；同一类型在同一次程序执行中结果一致。本机 `atomic_is_lock_free(&a)` 对 `_Atomic int` 与 `_Atomic long long` 都返回 1（真）。一般规律：大小超过硬件原子指令位宽的类型（如某些 16 字节以上的大结构）往往**不是** lock-free，实现会退化成"用一把隐藏的锁保护它"，此时该"原子"对象的操作其实在加锁——性能与死锁风险都要留意。

两个初学者极易混淆的点必须点破。其一，**lock-free ≠ wait-free**：lock-free 只保证"整个系统总有某个线程能前进"（无锁、不会全体卡死），但不保证**每个**线程都在有限步内完成（后者才是 wait-free）——进展性条件的严格层级归大主题02/04，这里只需知道 lock-free 是较弱的那个保证。其二，标准还建议（§7.17.5p2）lock-free 操作应当是 **address-free** 的：即通过两个不同地址访问同一内存位置也能原子通信，从而支持"同一块内存映射进进程多次"或"进程间共享内存"。此外 `atomic_flag`（经典 test-and-set 标志）是标准**唯一强制必须 lock-free** 的类型（§7.17.8p2），是"最小硬件原子积木"，其余类型都可以用它模拟（只是性质不理想）。

#### 来源与时效
- N3220 §7.17.1p4（lock-free 宏清单，含 C23 新增 `ATOMIC_CHAR8_T_LOCK_FREE`）/ §7.17.5「Lock-free property」p1–2（0/1/2 取值含义、address-free 建议）/ §7.17.5.1「atomic_is_lock_free」/ §7.17.8p2（`atomic_flag` 必须 lock-free）（一手，2024，读 PDF 原文核实 2026-07-30）。
- 本机实证：所有 `ATOMIC_*_LOCK_FREE == 2`、`atomic_is_lock_free` 对 int/llong 返回 1（gcc 13.3.0 `-std=c11 -O2`，Linux 6.18.5 x86-64，核实 2026-07-30）。
- lock-free ≠ wait-free 的进展性层级归大主题02/04，此处仅澄清不深挖。
- 交叉一致（标准取值定义与本机实测相符），无冲突。
