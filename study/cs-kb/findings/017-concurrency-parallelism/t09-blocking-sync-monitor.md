# L4-05·大主题09 阻塞同步与 monitor

> 基线 Py3.11.15/np2.4.6/gcc13.3 @2026-07-25 ｜ 核实日期：2026-07-30 ｜ 先修：本课大主题01（互斥的正确性性质：互斥/无死锁/无饥饿）、大主题08（自旋锁与忙等的代价，理解"为何要睡眠而非空转"）、L4-01·OS-04（线程与共享地址空间）、L4-01·OS-06（条件变量/信号量的内核实现与 `futex` 慢路径）｜ 一手锚点：Herlihy & Shavit《The Art of Multiprocessor Programming》(AMP) Revised 1st / 2nd ed. 第 8 章「Monitors and Blocking Synchronization」为主一手；C.A.R. Hoare「Monitors: An Operating System Structuring Concept」CACM 17(10):549–557, 1974（signal-and-wait 语义原始来源）；Lampson & Redell「Experience with Processes and Monitors in Mesa」CACM 23(2):105–117, 1980（Mesa notify=hint、`WHILE` 纪律原始来源）；POSIX.1-2017 (IEEE Std 1003.1-2017) `pthread_cond_wait`/`pthread_cond_signal`/`pthread_cond_broadcast`/`sem_wait`/`sem_post` 规范文本；Dijkstra 信号量（THE 系统，P/V，1965/1968）｜ 成熟度：GA/稳定（monitor 概念 1974 定型；POSIX 条件变量与信号量语义自 1990s 稳定至今，措辞在 SUSv4/POSIX.1-2017 无实质变化）

> 粒度判定：**1 份，不拆**。六个小主题（CP-09.1–09.6）沿单一主线层层递进——先立"monitor = 锁 + 条件变量封装成对象内同步"这个封装范式（09.1），再讲清它依赖的核心机制"条件变量的 wait/signal/broadcast 语义、以及 Mesa 与 Hoare 两套语义的分歧"（09.2），分歧的直接后果就是"必须 `while` 重检谓词"这条纪律（09.3），随后用两个经典范例把范式钉实——生产者-消费者（09.4）与读者-写者（09.5），最后补上与条件变量并列的另一族阻塞原语信号量（09.6）。前三节是"原理"、后三节是"惯用法落地"，共享同一套锁 + 条件变量心智模型，拆开会割裂"语义→纪律→范例"的教学链，故合为一份；篇幅可控。

> 本报告一条主线心智模型：**并发对象需要"边等边让锁"的能力**。自旋锁只解决"同一时刻只有一个线程进临界区"，但很多同步是"我要等某个条件成立才能干活"（缓冲区非空、没有写者在写）。若持锁忙等，别人永远进不来改变条件，就是死等；若放锁忙等，又浪费 CPU。monitor 给出的答案是：把共享数据、保护它的**锁**、以及若干**条件变量**打包进一个对象，线程在条件不满足时调用 `wait`——它**原子地放开锁并睡眠**，等别的线程改了状态后 `signal` 把它唤醒、唤醒后**重新抢回锁**再继续。于是"等待"不占 CPU、也不堵住临界区。整份报告就是把这套"锁 + 条件变量"机制的语义、易错纪律和两三个经典范例讲透。

> 分账（本课不外扩，只在交界处一句指路）：条件变量/信号量这些原语的**内核实现**（如何陷入睡眠、`futex` 快慢路径、唤醒如何调度）归 L4-01·OS-06，本课**只讲同步惯用法在最弱语义下为何正确/错误**；死锁四条件与银行家算法等**资源管理层**回避归 L4-01·OS-07（本课只在读者-写者的饥饿处点到公平性）；happens-before / 内存序如何保证 `wait`/`signal` 前后的可见性归本课大主题05–06，本报告默认这些原语已建立正确的同步边，不再深挖内存模型。

---

## CP-09.1 monitor 模式：锁 + 条件变量封装成对象内同步

### CP-09.1.1 什么是 monitor

monitor（监视器/管程）是一种**把共享数据和保护它的同步机制封装在一起**的并发对象范式：对象内部持有一把互斥锁，对象的每个公开方法进入时自动加锁、退出时自动解锁，于是"同一时刻至多一个线程在对象内执行"由结构本身保证，调用者不必手动配对 lock/unlock。为了表达"等某个条件成立"，monitor 内部还带一个或多个**条件变量**（condition variable），线程可在条件不满足时于某条件变量上 `wait`（睡眠让锁），别的线程改变状态后 `signal`/`notify` 把它唤醒。

这个概念由 Brinch Hansen 与 Hoare 在 1973–1974 年提出，Hoare 1974 的论文把它定义为"操作系统的结构化手段"。心智模型是一间"一次只放一个人进去的房间"：门口有锁（互斥），房间里若干张"等待长椅"（条件变量），进去发现暂时不能干活的人到某张长椅上躺下（`wait`，同时把门钥匙交回去让下一个人能进），干完活的人喊一声唤醒某条长椅上的人（`signal`）。

对初学者最关键的一点是：**`wait` 一定会先放开锁再睡**。若不放锁就睡，那么能改变条件、从而能唤醒你的那个线程根本进不了这间房，你就永远睡死——这正是 monitor 把"放锁"和"睡眠"做成一个原子动作的原因。

### CP-09.1.2 monitor 与"裸锁 + 共享变量"的区别

裸用一把 `mutex` 保护共享变量时，"加锁/解锁"和"何时该等待"都由程序员在每个调用点手写，极易漏配对或在错误的地方检查条件。monitor 的价值是把这套约定**收进对象**：互斥是"进方法即加锁"的默认，等待是"对象内的条件变量 + 谓词"的标准写法，从而把并发正确性从"每个调用点都不能错"降为"对象实现一次写对即可"。

现代语言里 monitor 常以两种形态出现。一种是**语言内建**：Java 的每个对象都隐含一把锁，`synchronized` 方法/块 = 进入 monitor，`Object.wait()/notify()/notifyAll()` 就是该对象唯一那个隐式条件变量上的操作。另一种是**库拼装**：C/POSIX 里没有 monitor 关键字，就用一个 `pthread_mutex_t` 加一个或多个 `pthread_cond_t`、再自觉地"每个公开函数开头 lock、结尾 unlock"来手工搭出一个 monitor。AMP 第 8 章正是以后一种"锁 + 条件"的组合来讲 monitor，因为它更能暴露语义细节。

一个常见误解要澄清：monitor 里可以有**多个条件变量**，各自代表一种"等待的理由"（例如缓冲区"非满"和"非空"是两个不同条件）。它们**共用对象那一把锁**，但各自维护独立的等待队列。用一个条件变量硬扛多种理由会导致唤醒了不该醒的线程、效率低甚至出错。

#### 来源与时效
- AMP Revised/2nd ed. 第 8 章「Monitors and Blocking Synchronization」§8.2「Monitor Locks and Conditions」（核实 2026-07-30）：以 `Lock` + `Condition`（Java `ReentrantLock`/`Condition` 风格）讲 monitor 封装；一手主源。
- C.A.R. Hoare, "Monitors: An Operating System Structuring Concept", CACM 17(10):549–557, 1974（核实 2026-07-30）：monitor 概念与形式定义原始来源，发展自 Brinch Hansen 的管程思想。
- POSIX.1-2017 `pthread_mutex_t` + `pthread_cond_t` 组合（核实 2026-07-30）：C 侧"库拼装 monitor"的落地方式，印证"多条件变量共用一锁"。

## CP-09.2 条件变量语义：wait / signal / broadcast、Mesa vs Hoare

### CP-09.2.1 三个基本操作

条件变量提供三个操作，它们**都必须在持有关联锁的前提下调用**。

`wait`：原子地释放锁并让当前线程在该条件变量上阻塞睡眠；被唤醒返回前，会**重新获得**那把锁。POSIX 的原话是这两个动作"atomically release mutex and cause the calling thread to block"。

`signal`（POSIX `pthread_cond_signal` / Java `notify`）：唤醒在该条件变量上等待的**至少一个**线程（若有）。

`broadcast`（POSIX `pthread_cond_broadcast` / Java `notifyAll`）：唤醒在该条件变量上等待的**所有**线程。

初学者要抓住 `wait` 的"三合一"：放锁 + 睡眠 + 醒后重新拿锁。放锁是为了让别人能进来改状态并通知你；醒后重新拿锁是为了让你醒来后仍处在临界区内、可以安全地再读共享状态。`signal` 与 `broadcast` 的差别只是"叫醒一个"还是"叫醒全部"——什么时候该用哪个，取决于被唤醒的线程是否可能"抢不到活干又得接着等"（见 09.4/09.5）。还要记住：在条件变量上"没有线程等待"时发出的 `signal` 会**丢失**（不被记住），这与信号量的计数式记忆截然不同（对比 09.6）。

### CP-09.2.2 Hoare 语义（signal-and-wait，signal 立即转交控制）

Hoare 1974 定义的原始语义是 **signal-and-wait**（也叫 signal-and-urgent-wait 或"立即恢复"）：当线程 S 在条件 C 上 `signal` 且有线程 W 在 C 上等待时，**控制权立即从 S 转交给 W**——W 马上（在锁仍被持有、状态未被任何第三方改动的情况下）恢复运行，而 signaller S 自己被挂到一个"紧急队列"上暂停，等 W 让出锁后再继续。

这套语义的**关键红利**是：W 被唤醒的那一刻，S 刚刚建立的条件**仍然为真**、且中间没有别的线程插进来破坏它。因此在纯 Hoare 语义下，等待处**理论上用 `if` 判断一次就够**：

```
if (!condition) c.wait();   // 纯 Hoare 语义下 if 即可
```

代价是实现复杂、上下文切换多（一次 signal 触发两次控制转移），且需要额外的紧急队列来暂存 signaller。因此几乎没有真实系统采用纯 Hoare 语义——它主要是理论上便于证明正确性的模型。初学者记住一句：**Hoare 语义 = "叫醒你 = 保证你现在能干活"，但现实几乎不这么实现。**

### CP-09.2.3 Mesa 语义（signal-and-continue，notify 只是提示）

Lampson & Redell 1980 在 Mesa 系统里改用 **signal-and-continue**：`signal`（Mesa 里叫 `notify`）只是把等待线程**标记为可运行**并放回锁的竞争队列，signaller **不让出控制、继续执行**直到自己退出临界区放锁；被唤醒的线程要**重新排队抢那把锁**，抢到后才恢复。论文明确把 `notify` 描述成一个**hint（提示）**：被唤醒的线程"必须在每次醒来时自行验证它关心的条件是否为真，因为唤醒之后、它真正拿到锁之前，可能已经有别的代码运行过并再次破坏了条件"。

这带来一个残酷但必须接受的事实：**在 Mesa 语义下，`wait` 返回不代表条件成立**。从 signaller 放锁到被唤醒线程抢到锁之间，存在一个窗口，其他线程可能先抢到锁、把刚成立的条件又消耗掉（例如另一个消费者先取走了唯一那件产品）。所以等待必须写成 `while` 循环重检（见 09.3）。Mesa 语义换来的是实现简单、无需紧急队列、signaller 无谓的上下文切换更少，也天然容忍"叫多了"。POSIX 条件变量、Java `wait/notify`、C++ `std::condition_variable`、几乎所有主流实现都采用 Mesa 语义。

两套语义的分歧是本主题最重要的冲突点，务必分账记清：

Hoare：signal 立即转交控制、signaller 挂起、醒来时条件保证为真、理论用 `if` 即可、实现复杂、几乎无人采用。

Mesa：signal 只是提示、signaller 继续跑、醒来要重新抢锁、条件可能已被破坏、必须用 `while`、实现简单、事实标准。

还有一档介于两者之间的 **Brinch Hansen / signal-and-exit** 语义（signal 必须是方法的最后一个动作，发完立即退出 monitor），因约束太强、实用性差，仅作历史提及，不展开。

#### 来源与时效
- AMP 第 8 章 §8.2「Monitor Locks and Conditions」/§8.3（核实 2026-07-30）：以"signal 是 hint、必须 `while` 重检"的 Mesa 口径讲条件变量；主一手。
- C.A.R. Hoare, CACM 17(10), 1974（核实 2026-07-30）：signal-and-wait / 立即恢复语义原始来源，signaller 挂到紧急队列、被唤醒者优先于新进入者。
- Lampson & Redell, "Experience with Processes and Monitors in Mesa", CACM 23(2):105–117, 1980（核实 2026-07-30，Microsoft Research PDF 存档）：原文将 `notify` 定为 hint，给出标准模式 `WHILE NOT (OK to proceed) DO WAIT c ENDLOOP`；解释"唤醒后到拿锁前可能有任意其他代码运行"。
- POSIX.1-2017 `pthread_cond_signal`/`pthread_cond_broadcast`/`pthread_cond_wait`（核实 2026-07-30）：`wait` "atomically release mutex and block"；signal 唤醒"at least one"、broadcast 唤醒全部；实现即 Mesa 语义。
- 冲突项：Hoare（signal-and-wait，`if` 足够）vs Mesa（signal-and-continue，必须 `while`）。两边定位：Hoare 1974 §"signal" 描述 / Lampson-Redell 1980 "notify as hint" 一节。真实系统一律 Mesa。

## CP-09.3 虚假唤醒纪律：必须 `while` 重检谓词而非 `if`

### CP-09.3.1 为什么必须用 while

在 Mesa 语义（所有真实系统）下，等待条件的标准且**唯一正确**写法是把 `wait` 包在检查谓词的 `while` 循环里，而不是 `if`：

```
while (!predicate)
    pthread_cond_wait(&cond, &mutex);
```

有两个独立的理由都要求 `while`。其一是 **Mesa 语义的抢锁窗口**（09.2.3）：从 signaller 放锁到你抢到锁之间，别的线程可能先抢到锁并把刚成立的条件又破坏掉，于是你 `wait` 返回时谓词可能又不成立了——`if` 只检查一次就往下走会读到错误状态；`while` 会重检发现不满足、再次 `wait`。其二是**虚假唤醒**（spurious wakeup）：POSIX 明确允许 `pthread_cond_wait`/`pthread_cond_timedwait` 在**没有任何线程 signal**的情况下也返回，规范原话是"Spurious wakeups ... may occur"，并因此建议"a condition wait be enclosed in the equivalent of a 'while loop' that checks the predicate"。

初学者可以这样记：**`wait` 返回只意味着"你可能该看看条件了"，绝不意味着"条件已经成立"。** 把 `wait` 当成"可能被无缘无故叫醒的闹钟"，醒来第一件事永远是自己核实谓词，不满足就接着睡。用 `if` 的代码在测试时可能"看起来能跑"，因为破坏窗口和虚假唤醒都是偶发的，一旦线上高并发触发，就是难复现的数据损坏或崩溃——这是并发编程最经典的坑之一。

### CP-09.3.2 虚假唤醒的来源与"谓词才是真相"

虚假唤醒并非实现偷懒，而是被规范**有意允许**的：在某些系统上，用底层原语（如信号中断、`futex` 被打断）实现条件变量时，让 `wait` 偶尔无理由返回，比强行保证"返回必因 signal"要简单且高效得多，标准于是把这个自由度留给实现。所以可移植的正确代码**不能假设"没被 signal 就不会醒"**。

由此得出条件变量使用的第一性原则：**真正决定能否往下走的是那个共享谓词，条件变量只是"避免忙等的睡眠/唤醒工具"**。谓词（如"缓冲区计数 > 0"）保存在受锁保护的共享变量里，`wait` 只负责在谓词不成立时高效地睡、在状态可能变化时被叫醒去重检。把这层关系理顺，就自然写出 `while(!pred) wait();` 而绝不会写成 `if`。

可选本机实证（pthread 用 `while` 复现正确惯用法、故意用 `if` 触发错误）本报告**未取**，如实标注——上述 `while` 纪律与虚假唤醒的允许性已由 POSIX 规范原文 + Mesa 论文 + AMP 三源锚定，实测仅为加固、非承重。

#### 来源与时效
- POSIX.1-2017 `pthread_cond_wait` / `pthread_cond_timedwait`「DESCRIPTION」（核实 2026-07-30）：明确"Spurious wakeups ... may occur"、"the predicate should be re-evaluated upon such return"、推荐"enclosed in the equivalent of a 'while loop'"。
- Lampson & Redell 1980（核实 2026-07-30）：Mesa 标准模式 `WHILE NOT (OK to proceed) DO WAIT c ENDLOOP`，原始来源。
- AMP 第 8 章（核实 2026-07-30）：教材统一以 `while` 重检谓词呈现所有条件变量惯用法；印证。

## CP-09.4 生产者-消费者：有界缓冲、满 / 空条件变量

### CP-09.4.1 问题与结构

生产者-消费者是 monitor 最经典的应用：若干**生产者**线程往一个**容量有限**的共享缓冲区放数据，若干**消费者**线程从中取数据；要求缓冲**满时生产者等待、空时消费者等待**，且放/取不撕裂、计数正确。用 monitor 建模就是一把锁保护缓冲与计数，加**两个条件变量**分别表示两种等待理由：一个 `notFull`（缓冲非满，生产者等它）、一个 `notEmpty`（缓冲非空，消费者等它）。

标准骨架（Mesa 语义、`while` 重检）如下，`put` 与 `take` 各自进方法即持锁：

```
put(item):  lock;  while (count == CAP) wait(notFull);
            buf[tail]=item; tail=(tail+1)%CAP; count++;
            signal(notEmpty);  unlock;
take():     lock;  while (count == 0)  wait(notEmpty);
            item=buf[head]; head=(head+1)%CAP; count--;
            signal(notFull);   unlock;   return item;
```

初学者读这段的要点：生产者放完一件后 `signal(notEmpty)` 是去叫醒可能在等"非空"的消费者；消费者取走一件后 `signal(notFull)` 去叫醒可能在等"非满"的生产者。等待用 `while` 是因为 Mesa 语义下醒来时状态可能又变了（比如两个消费者被一次 `broadcast` 同时唤醒，但只有一件产品，一个取走后另一个必须重新睡）。

### CP-09.4.2 为什么要两个条件变量、以及 signal vs broadcast

用**两个**条件变量而非一个，是为了"精确唤醒"：生产者制造的是"非空"这个好消息，只该叫醒消费者；消费者制造的是"非满"，只该叫醒生产者。若只用一个条件变量把两类等待者混在一起，`signal` 可能叫醒一个"同类"（例如生产者叫醒了另一个生产者），后者重检发现还是满、又睡回去，白白浪费一次唤醒与抢锁——这类"叫错人"的低效在混用单条件时很常见。

用 `signal` 还是 `broadcast`：当每次状态变化只让**恰好一个**等待者能前进（放一件只多一个空位可填、多一件可取），且所有等待者**对称等价**时，`signal`（叫醒一个）就够。但只要存在"叫醒后仍可能抢不到活"的非对称情形，或你拿不准该叫醒谁，用 `broadcast`（叫醒全部、让它们各自 `while` 重检、抢不到的再睡）总是**安全**的，代价是可能有多余的唤醒-重检-再睡（"惊群"）。初学者的稳妥默认是：**不确定就 `broadcast`**，因为它靠 `while` 纪律兜底、绝不会漏叫该醒的线程；把 `signal` 当成"确知只需叫一个且叫谁都行"时的优化。

可选本机实证（pthread 实现有界缓冲 + 生产/消费计数校验）本报告**未取**，如实标注——结构正确性由 AMP 第 8 章范例 + POSIX 语义双源锚定。

#### 来源与时效
- AMP 第 8 章「Monitors and Blocking Synchronization」有界缓冲/生产者-消费者范例（核实 2026-07-30）：两条件变量 `notFull`/`notEmpty` + `while` 重检的标准结构；主一手。
- POSIX.1-2017 `pthread_cond_signal` vs `pthread_cond_broadcast`（核实 2026-07-30）：signal 唤醒至少一个、broadcast 唤醒全部；印证"不确定就 broadcast"的安全性。

## CP-09.5 读者-写者：共享读 / 独占写、公平与写者饥饿

### CP-09.5.1 问题与允许的并发度

读者-写者（readers-writers）问题针对"读多写少"的共享数据：允许**多个读者同时读**（读不改状态，彼此不冲突），但**写者必须独占**（写时既不能有别的写者、也不能有任何读者）。它比互斥更精细——纯互斥会把并发读也串行化、白白牺牲吞吐，而读写锁放开了"共享读"这条并发度。

用 monitor 建模：一把锁保护若干计数/标志（如当前活跃读者数 `readers`、是否有写者 `writing`），加条件变量让"想读但此刻有写者"和"想写但此刻有读者或写者"的线程各自等待。读者进入时若无写者活动即把 `readers++` 后直接读、退出时 `readers--` 并在归零时唤醒等待的写者；写者进入时必须等到 `readers==0 且无写者`。

初学者要抓的核心是那句允许矩阵：**读-读相容，读-写、写-写互斥**。这正是"读写锁"（`pthread_rwlock_t`、Java `ReentrantReadWriteLock`）作为独立原语存在的理由——它把这套逻辑封装好，比自己用 monitor 手搓更省事，但内在语义就是上面这套。

### CP-09.5.2 公平性与写者饥饿

读者-写者的经典陷阱是**写者饥饿**（writer starvation）：如果策略是"只要还有读者在读，新来的读者就可以直接加入"（读者优先），那么在读者持续不断到来的高负载下，活跃读者数永远不归零，等在门外的写者**永远轮不到**独占权、被无限期饿死。

破解要靠**公平性策略**，常见几种：读者优先（最大化读吞吐，但会饿写者）；写者优先（有写者等待时就不再放新读者进来，避免饿写者，但可能饿读者）；以及更常用的**无饥饿/公平**策略——当有写者在等待时，让后到的读者也排队等在写者之后，从而保证每个写者在有限时间内获得机会。AMP 第 8 章正是以"先给一个会饿写者的简单版、再改造成无饥饿版"的顺序讲这个问题，用来教"如何在 monitor 里把公平性写对"。

这里与 L4-01·OS-07 分账：本课关心的是"用条件变量把公平/无饥饿的等待逻辑写对"这一算法层；而饥饿作为一种更广义的资源分配病态、以及死锁的四条件/避免归 OS-07，本报告不外扩。初学者只需记住：**读写锁不是"越偏袒读者越好"，选错公平策略会把某一方饿死，实际库通常提供可配置的公平模式。**

可选本机实证（pthread 复现读者-写者）本报告**未取**，如实标注——允许矩阵与写者饥饿由 AMP 范例 + POSIX `pthread_rwlock` 语义双源锚定。

#### 来源与时效
- AMP 第 8 章读者-写者范例（`SimpleReadWriteLock` → `FifoReadWriteLock`，核实 2026-07-30）：共享读/独占写允许矩阵、简单版会饿写者、公平版消除饥饿；主一手。
- POSIX.1-2017 `pthread_rwlock_rdlock`/`pthread_rwlock_wrlock`（核实 2026-07-30）：读写锁作为封装原语的语义（多读者共享、写者独占）；印证。分账：更广义饥饿与死锁四条件归 L4-01·OS-07。

## CP-09.6 信号量：计数同步原语、二元 vs 计数

### CP-09.6.1 信号量的定义与 P/V 操作

信号量（semaphore）是 Dijkstra 提出的最早的阻塞同步原语（用于 THE 操作系统，约 1965/1968）：它本质是一个**受保护的非负整数计数**，配两个原子操作。

`P`（POSIX `sem_wait`，Dijkstra 荷兰语 *Probeer* "尝试"）：若计数 > 0 则**减一**并返回；若计数为 0 则**阻塞**，直到计数变正。POSIX 原话是"If the semaphore value is currently zero, then the calling thread shall not return ... until it either locks the semaphore or ... interrupted"。

`V`（POSIX `sem_post`，Dijkstra *Verhoog* "增加"）：把计数**加一**；若此前有线程阻塞在该信号量上，则唤醒其中一个使其从 `P` 返回。

信号量的心智模型是"一叠许可证"：计数 = 当前可用许可数。`P` 是"领一张许可（没有就排队等）"，`V` 是"还一张许可（有人在等就直接把这张塞给他）"。它比条件变量更"低层"：不需要外部谓词和锁的配合，计数本身就是状态。

### CP-09.6.2 二元信号量 vs 计数信号量、与条件变量的关键差异

按计数取值范围分两类。**二元信号量**（binary semaphore）计数只在 0/1 之间，可当互斥锁用（`P` = lock、`V` = unlock，初值 1）——但语义上它和 mutex 有别：mutex 通常有"所有权"（谁加锁谁解锁），二元信号量没有所有权、任何线程都能 `V`，因此更适合用作**信号通知**而非互斥。**计数信号量**（counting semaphore）计数可取任意非负值，天然用来管理"有限个同类资源"（如连接池里 N 个连接：初值 N，每借一个 `P`、每还一个 `V`）。

一个必须讲清、初学者极易混淆的差异是**信号量记忆计数、条件变量不记忆信号**。在没有任何线程等待时：对信号量 `V` 会把计数加一，这次"通知"被**记住**了，将来的 `P` 可以立刻消费它；而对条件变量 `signal` 若此刻无人 `wait`，这次唤醒**直接丢失**、不被记住。这个区别决定了用途——信号量适合"计数/许可"语义（丢不得），条件变量适合"配合外部谓词的等待"（谓词本身记录状态，signal 丢了靠 `while` 重检 + 后续 signal 兜底）。

初学者选型的粗略指引：**要表达"有几个资源可用"用信号量；要表达"等某个复杂条件成立、且需要在持锁下检查共享状态"用锁 + 条件变量。** 两者可以互相模拟，但用错工具会让代码别扭又易错。

可选本机实证（`sem_t` + `sem_wait`/`sem_post` 计数校验）本报告**未取**，如实标注——P/V 计数语义与"信号量记忆、条件变量不记忆"由 POSIX 规范 + Dijkstra 原始定义 + AMP 双三源锚定。

#### 来源与时效
- POSIX.1-2017 `sem_wait`（"If the semaphore value is currently zero, then the calling thread shall not return ... until it either locks the semaphore"）与 `sem_post`（"increments ... if the value ... is zero, then one of the threads blocked ... shall be allowed to return"）（核实 2026-07-30）：`P`/`V` 计数与阻塞/唤醒语义；主一手。
- Dijkstra 信号量（THE 系统，P=Probeer/V=Verhoog，1965/1968；核实 2026-07-30，NYU/伯克利并发讲义存档转述原始定义）：信号量与 P/V 的历史来源。
- AMP 第 8 章信号量小节（核实 2026-07-30）：counting/binary 信号量、与条件变量的对照；印证"信号量记忆计数、条件变量不记忆 signal"这一差异。
