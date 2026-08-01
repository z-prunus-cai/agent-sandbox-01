# L2-01·大主题3 受限接口结构：栈/队列/双端队列

> 基线 Py3.11.15/np2.4.6/gcc13.3 @2026-07-25 ｜ 核实日期：2026-07-26 ｜ 先修：大主题1（ADT 与渐近分析）、大主题2（数组与链表） ｜ 一手锚点：CLRS《Introduction to Algorithms》4th ed(2022) Ch10（§10.1 简单数组结构：栈与队列，§10.2 链表）；Berkeley CS61B（sp24.datastructur.es，Lists / Deque 讲次与 Project 1 Deques，Java）；Pat Morin《Open Data Structures》Ch2（ArrayStack/ArrayQueue/ArrayDeque）、Ch3（SLList/DLList）；CPython 源码 Modules/_collectionsmodule.c @tag v3.11.15（`collections.deque`） ｜ 成熟度：GA/稳定
>

本报告中的 Python 实测基于 Python 3.11.15、Linux 6.18.5 x86_64，均对应上面的基线串；CPython 实现细节引用自源码标签 v3.11.15。

---

## 3.1 栈 LIFO — push/pop、括号匹配/表达式求值应用

### 3.1.1 栈的定义与 LIFO 语义

栈（stack）是一种**受限的线性表**：所有插入和删除都只发生在同一端（称为栈顶，top）。它遵循**后进先出**（LIFO，Last-In-First-Out）原则——最后被放进去的元素，最先被取出来。CLRS 4th §10.1 明确用「INSERT 操作叫 PUSH、DELETE 操作叫 POP，且不带参数」来刻画这种受限接口。

「受限」二字是理解栈的关键。数组允许你在任意下标读写，而栈**故意**把接口砍到只剩栈顶一个位置可操作。这不是能力退化，而是一种契约：调用者放弃「随机访问」，换来的是实现方可以把每个操作都做成常数时间、且语义简单到不易出错。

最直观的心智模型是一摞盘子：你只能往最上面放一个（push），也只能从最上面拿一个（pop）；想拿底下那个，必须先把上面的都拿走。日常里「浏览器后退按钮」「编辑器撤销（Ctrl+Z）」「函数调用栈」都是栈——它们的共同点是「最近发生的事情要最先被回退」。

初学者最常见的混淆是把「栈这种数据结构」和「程序运行时的调用栈内存区」当成两个东西。其实后者正是前者的一个实例：每次函数调用把一个栈帧 push 上去，返回时 pop 下来，完全是 LIFO（见 L1-01 大主题4）。

### 3.1.2 核心操作与复杂度

栈的标准接口通常是四个操作：push（压入栈顶）、pop（弹出并返回栈顶）、peek/top（只看栈顶不弹出）、isEmpty（是否为空）。它们全部是常数时间：

push, pop, peek, isEmpty ： O(1)

需要区分两种「O(1)」。用链表实现（在头部插删）时每个操作是**最坏情况 O(1)**；用动态数组实现时，push 在需要扩容的那一次要复制整个底层数组，因此是**均摊 O(1)**（amortized，见大主题1）——即任意连续 n 次 push 的总代价是 O(n)，平摊到每次仍是常数。Open Data Structures 的 ArrayStack 正是把 push/pop 在栈顶的代价证明为均摊 O(1)。

一个易错点是 pop/peek 前必须先判空。对空栈 pop 是错误操作（下溢，underflow）：CLRS 给的伪码里 POP 会先检查 STACK-EMPTY 并在空栈时报 underflow 错误。在 Python 里对空 list 调用 `pop()` 会抛 `IndexError`，务必先判 `if st:`。

Python 里通常直接把内建 `list` 当栈用：`append()` 是 push、`pop()`（无参，默认弹末尾）是 pop、`st[-1]` 是 peek。之所以选「末尾」而非「开头」，是因为 list 底层是动态数组，末尾增删是均摊 O(1)，而开头增删要搬移全部元素、是 O(n)（这个陷阱在 3.2.3 展开）。

### 3.1.3 应用：括号匹配

栈的第一个经典应用是**检查括号是否正确配对**（如 `{[()]}` 合法、`(]` 与 `(()` 非法）。算法是：从左到右扫描，遇到左括号就 push；遇到右括号就 pop 出栈顶，检查它是否与当前右括号「同类型」，不匹配或栈已空则判定失败；扫描结束后栈必须为空。

为什么是栈而不是别的结构？因为括号的嵌套天然是 LIFO：**最近打开的括号必须最先被关闭**。`([)]` 之所以非法，正是因为它违反了这个「后开先关」的顺序，而栈的 pop 恰好总是取出「最近 push 的」那个，天然能捕捉这种违规。

本机实测（Python 3.11.15）：

```python
def balanced(s):
    pairs = {')': '(', ']': '[', '}': '{'}
    st = []
    for c in s:
        if c in '([{':
            st.append(c)
        elif c in ')]}':
            if not st or st.pop() != pairs[c]:
                return False
    return not st            # 结束时必须空
```

```
'(a[b]{c})'  -> True
'(]'         -> False
'(()'        -> False
'{[()]}'     -> True
```

不要忘了「扫描结束时栈必须为空」这一步。只检查每个右括号能匹配、却漏掉这步，就会把 `(()` 这种「有多余左括号」的串误判为合法——上面 `'(()' -> False` 正是靠最后的 `return not st` 拦下的。

### 3.1.4 应用：表达式求值

栈的第二个经典应用是**算术表达式求值**，通常分两步：先把人类习惯的中缀表达式（如 `3 + 4 * 2`）转换成后缀表达式（逆波兰式，`3 4 2 * +`），再对后缀式求值。两步都用栈。

后缀求值最简单：从左到右扫描，遇到操作数就 push，遇到运算符就 pop 出两个操作数、算完把结果 push 回去，扫描完栈里剩下的唯一元素就是答案。之所以用栈，是因为运算符要作用的两个操作数，永远是「最近刚算出/读入的两个」，正好在栈顶。

中缀转后缀用的是 Dijkstra 的**调度场算法**（shunting-yard）：用一个栈暂存运算符，遇到新运算符时，把栈里**优先级不低于**它的先弹出输出，再把它压入，从而正确处理 `*` 先于 `+`、以及括号。这里栈解决的核心难题是「运算符优先级导致的求值顺序」——高优先级、后读入的运算符要先参与运算，正符合 LIFO。

知道「表达式求值靠两个栈式流程、栈负责暂存待定的运算符/操作数」即可。完整文法、错误恢复、一元运算符等属于解析器工程，深挖归 L3-01 / 编译课 L2-16。

#### 来源与时效
- 锚点：CLRS 4th ed (2022) §10.1「简单数组结构：栈与队列」——PUSH/POP 伪码、STACK-EMPTY 判空、下溢/上溢定义（核实 2026-07-26）。
- 锚点：Open Data Structures（Morin，开放获取版）§2.2 ArrayStack——push/pop 在栈顶均摊 O(1) 的界（核实 2026-07-26）。
- 锚点：CS61B sp24（sp24.datastructur.es）Lists / ADT 讲次——栈作为受限 List 接口（核实 2026-07-26）。
- 锚点：调度场算法（shunting-yard，Dijkstra）为经典算法，本报告仅取「用栈暂存运算符」直觉；完整文法归 L3-01/编译课。
- 一致性：三源对栈接口与 LIFO/O(1) 无分歧；差异仅在「均摊 O(1)（数组实现）」vs「最坏 O(1)（链表实现）」，属实现选择不同，已在 3.1.2 分账说明。

## 3.2 队列 FIFO — enqueue/dequeue 语义

### 3.2.1 队列定义与 FIFO 语义

队列（queue）也是受限线性表，但插入和删除发生在**两个不同的端**：从队尾（tail/rear）插入、从队首（head/front）删除。它遵循**先进先出**（FIFO，First-In-First-Out）——先排队的先得到服务。CLRS 4th §10.1 把插入操作叫 ENQUEUE、删除操作叫 DEQUEUE。

食堂窗口先到先打饭，后来的人排到队尾。凡是「按到达顺序公平处理」的场景都是队列——打印任务队列、消息队列、操作系统就绪进程调度、以及图的广度优先搜索（BFS，见大主题9）里「先发现的结点先扩展」。

栈和队列的唯一本质区别就是**取出顺序**：栈是 LIFO（取最新），队列是 FIFO（取最旧）。把这一句记牢，两者其余性质都能推出来。

### 3.2.2 enqueue/dequeue/front 操作与复杂度

队列的标准接口：enqueue（入队，加到队尾）、dequeue（出队，移除并返回队首）、front/peek（看队首）、isEmpty。同样都是常数时间：

enqueue, dequeue, front, isEmpty ： O(1)

要做到这个 O(1)，实现上必须两端都能高效操作。有两种主流做法：

- 链表实现：用单链表并**同时维护 head 和 tail 两个指针**，队首删除动 head、队尾插入动 tail，都是 O(1)。若只有 head 指针，队尾插入要遍历到末尾，就退化成 O(n)——这是初学者写链表队列最常见的坑。
- 数组实现：用**环形（循环）数组** + 两个下标，避免出队后整体前移。这正是 3.5 环形缓冲区的主题，其 enqueue/dequeue 是均摊 O(1)（Open Data Structures 的 ArrayQueue 给出此界）。

同样，链表实现是最坏 O(1)，环形数组实现（要扩容时）是均摊 O(1)，两者分账。

### 3.2.3 易错点：用 Python list 做队列的 O(n) 头删陷阱

把 Python `list` 当队列，用 `append()` 入队、用 `pop(0)` 出队。功能上对，性能上错——

list.pop(0) 单次代价 ： O(n)

因为 list 底层是连续数组，删掉 0 号元素后，后面所有元素都要**整体向前搬移一格**来填补空洞，代价与当前长度成正比。于是「清空一个长度 n 的队列」总代价是 O(n²)。正确做法是用 `collections.deque`，它的 `popleft()` 是 O(1)（原理见 3.3.2）。

本机实测（Python 3.11.15，逐个出队直到清空，计总墙钟时间）：

```
n= 10000  list.pop(0) 全部出队=    7.16 ms   deque.popleft() 全部出队=    0.31 ms   比值=  23.0x
n= 20000  list.pop(0) 全部出队=   34.03 ms   deque.popleft() 全部出队=    0.65 ms   比值=  52.4x
n= 40000  list.pop(0) 全部出队=  141.45 ms   deque.popleft() 全部出队=    1.21 ms   比值= 117.0x
```

n 每翻一倍，`list.pop(0)` 的总时间约翻**四倍**（7→34→141 ms），符合 O(n²) 总代价（即每次 O(n)）；`deque.popleft()` 总时间只约翻**一倍**（0.31→0.65→1.21 ms），符合 O(n) 总代价（即每次 O(1)）。这就是「结构选错、复杂度阶数就错」的最直观证据。

#### 来源与时效
- 锚点：CLRS 4th ed (2022) §10.1——ENQUEUE/DEQUEUE 伪码，队列用循环数组、head/tail 下标带回绕（核实 2026-07-26）。
- 锚点：Open Data Structures（Morin）§2.3 ArrayQueue——环形数组 + 模运算实现队列，add/remove 均摊 O(1)（核实 2026-07-26）。
- 锚点：CS61B sp24 Lists/Deque 讲次——队列作为 FIFO 受限接口（核实 2026-07-26）。
- 锚点：Python 官方文档 `collections.deque`——「list 的 `pop(0)`/`insert(0,...)` 是 O(n)，deque 两端是 O(1)」的性能提示（核实 2026-07-26）；本机实测已复现该阶数差异。
- 一致性：CLRS 与 Open Data Structures 均以「环形数组」作为数组式队列的标准实现，无分歧。

## 3.3 双端队列 deque — 两端 O(1) 增删

### 3.3.1 双端队列定义与四个端操作

双端队列（deque，double-ended queue，读作 "deck"）是把栈和队列「合并」的结构：**两端都能插入、也都能删除**。它有四个端操作：从头插入、从头删除、从尾插入、从尾删除（Python `collections.deque` 命名为 `appendleft` / `popleft` / `append` / `pop`）。

deque 是更一般的受限线性表：它同时是栈（只用一端）和队列（一端进另一端出）的超集。因此若你不确定将来只需 LIFO 还是 FIFO，用 deque 最保险。CS61B sp24 的 Project 1 就以实现 Deque（数组版 `ArrayDeque` 与链表版 `LinkedListDeque`）作为入门大作业，正因为它一次性覆盖了两端操作的全部边界情形。

四个端操作全部是常数时间（数组实现为均摊、链表实现为最坏）：

addFirst, removeFirst, addLast, removeLast ： O(1)

### 3.3.2 两端 O(1) 的实现要点：CPython deque 的块状双向链表

要让**两端**都 O(1)，普通动态数组不行（一端搬移是 O(n)）。两条主流路子：其一是环形数组（Open Data Structures 的 ArrayDeque，用模运算让两端都不搬移，均摊 O(1)）；其二是双向链表（两端各持一个指针，最坏 O(1)）。

CPython 的 `collections.deque` 走的是**块状双向链表**的折中路线。源码 `Modules/_collectionsmodule.c`（标签 v3.11.15）中的注释写道：deque 的数据存放在「a doubly-linked list of fixed length blocks（定长块组成的双向链表）」，这样「appends or pops never move any other data elements（增删永不搬移其它数据元素）」。每个块的长度由宏定义：

```c
#define BLOCKLEN 64
```

即 64 个元素一块。两端增删时，只在当前块内移动一个下标指针，块满/块空时才分配或释放整块；数据元素本身从不移动，因此 `append`/`appendleft`/`pop`/`popleft` 都是 O(1)。这是「链表节点粒度太细、缓存不友好」与「纯数组两端不可兼得」之间的工程折中——用「块」把多个元素打包，兼顾两端 O(1) 与一定的连续性。

deque 靠「不搬移已有元素、只改指针/下标」实现两端 O(1)；具体块大小是 CPython 的实现选择（BLOCKLEN=64），不是语言规范，别当成通用定义背。

### 3.3.3 deque 作为统一 ADT：既是栈也是队列

因为两端都能 O(1) 增删，deque 可以「一物多用」：只用 `append`+`pop`（同一端）就是栈；用 `append`+`popleft`（异端）就是队列。这解释了为什么 Python 官方推荐「需要队列时用 `collections.deque` 而不是 list」——它把队列缺的那个「O(1) 头删」补上了。

不要把 `collections.deque` 和内建 `list` 的性能画像搞混。list 支持 O(1) 随机下标访问 `a[i]` 但头部增删是 O(n)；deque 头尾增删都是 O(1) 但**中间随机访问是 O(n)**（要顺着块链走）。选哪个取决于你的访问模式：频繁两端进出选 deque，频繁随机下标选 list。

#### 来源与时效
- 锚点：CPython 源码 `Modules/_collectionsmodule.c` @tag v3.11.15——`#define BLOCKLEN 64`、块状双向链表注释、两端 O(1) 的机制（原文经 raw.githubusercontent.com 核对，核实 2026-07-26）。
- 锚点：Open Data Structures（Morin）§2.4 ArrayDeque——环形数组式双端队列，两端 add/remove 均摊 O(1)（核实 2026-07-26）。
- 锚点：CS61B sp24 Project 1「Deques」——ArrayDeque61B / LinkedListDeque61B 两种实现的两端操作规格（核实 2026-07-26）。
- 冲突/分歧：deque 的**内存布局**在各源不同——CPython 用「块状双向链表（BLOCKLEN=64）」，Open Data Structures / CS61B 教学版用「环形数组」或「纯双向链表」。三者对外接口（两端 O(1)）一致，差异仅在实现层，已分账；BLOCKLEN=64 属 CPython 实现细节、非通用规范。
- 说明：CLRS 4th 正文未把 deque 列为独立主线结构（栈/队列为主，deque 见习题）；此处第二独立一手源由 Open Data Structures 与 CS61B 承担。

## 3.4 数组实现 vs 链表实现权衡

### 3.4.1 数组实现（含均摊扩容）

用数组实现栈/队列/deque，意味着元素存在一段**连续内存**里，靠下标定位端点。栈只需一个 top 下标；队列/deque 需要 head 与 tail（或 head + size）两个下标并配合环形回绕（3.5）。当元素个数超过当前容量时，分配一个更大的数组（通常**倍增**，见大主题2）并复制过去，因此端点增删是均摊 O(1)：

n 次 push 的总复制代价 = O(n)  ⟹  均摊每次 O(1)

数组实现的优点：内存连续、无每结点指针开销、缓存友好（顺序访问命中率高）；缺点：需要预留/成倍扩容可能浪费空间、扩容那一次有 O(n) 的复制尖峰。

### 3.4.2 链表实现（结点 + 指针）

用链表实现，则每个元素是一个独立结点，靠指针串起来。栈用单链表在头部插删即可（最坏 O(1)）；队列需单链表 + head/tail 双指针；deque 需**双向链表**（否则 removeLast 找不到前驱、退化 O(n)）。

链表实现的优点：每个操作是**最坏情况 O(1)**（没有扩容尖峰）、容量按需增长不浪费预留空间；缺点：每个结点额外存 1~2 个指针（空间常数因子大）、结点在堆上分散分布导致**缓存不友好**、且频繁 malloc/free 有分配器开销。

### 3.4.3 权衡对比：空间/常数因子/扩容/缓存

把两者并排看，权衡是清楚的：

| 维度 | 数组实现 | 链表实现 |
|---|---|---|
| 端点操作复杂度 | 均摊 O(1)（扩容那次 O(n)） | 最坏 O(1)（无尖峰） |
| 每元素额外空间 | 无指针，但有预留空位 | 1~2 个指针/结点 |
| 内存布局 | 连续，缓存友好 | 分散，缓存不友好 |
| 扩容 | 需倍增复制 | 不需要，按需分配 |
| 随机下标访问 | O(1) | O(n) |

绝大多数通用场景下，**数组实现的常数因子和缓存友好性使它实际更快**，这也是 Python `list` 和 C++ `std::vector`/`std::stack` 默认底层用数组的原因（缓存友好性对比实测见大主题2「连续 vs 分散布局代价画像」）。链表实现在「必须保证每次操作最坏 O(1)（不能容忍扩容尖峰，如实时系统）」或「元素极大、复制昂贵」时才更有优势。

初学者常见误解是「链表增删一定比数组快」。这只在「已经拿到目标结点指针」时成立；对栈/队列这种只在端点操作的场景，数组端点操作同样是（均摊）O(1)，且因缓存友好通常更快。别把「链表中间插入 O(1)」错误地套到「端点操作」上。

#### 来源与时效
- 锚点：CLRS 4th ed (2022) §10.1（数组式栈/队列）与 §10.2（链表）——两种表示的操作与代价（核实 2026-07-26）。
- 锚点：Open Data Structures（Morin）Ch2「Array-Based Lists」vs Ch3「Linked Lists」——数组式均摊 O(1) 与链表式最坏 O(1) 的对照、每结点指针开销讨论（核实 2026-07-26）。
- 锚点：CS61B sp24 Lists/Deque 讲次与 Project 1——ArrayDeque vs LinkedListDeque 权衡（核实 2026-07-26）。
- 旁证：CPython `list`（数组）与 `collections.deque`（块状链表）的实际选型印证「通用场景多用数组式」（核实 2026-07-26）。
- 一致性：三源对「数组=均摊 O(1)+缓存友好、链表=最坏 O(1)+指针开销」的定性一致；「谁更快」依赖具体负载，属工程权衡而非硬结论，已如实标为定性对比。

## 3.5 环形缓冲区 — 模运算首尾指针、定容队列

### 3.5.1 环形缓冲区结构与模运算指针

环形缓冲区（circular / ring buffer）是队列的一种**数组实现**：把一段定长数组「首尾相接」当成环来用，配一个 head 下标（指向队首）和一个 size（当前元素数），队尾位置由它们算出。核心是**模运算回绕**——下标走到数组末尾后用取模跳回开头：

tail = (head + size) mod capacity

出队时 head 前进一格、size 减一：

head = (head + 1) mod capacity

这样出队**不搬移任何元素**（对比 3.2.3 的 `list.pop(0)` 要整体前移），入队也只是往算出的 tail 位置写值。这正是 CLRS §10.1 与 Open Data Structures ArrayQueue 给出的标准数组队列实现，把队列的 enqueue/dequeue 都做成 O(1)。

想象一个圆形停车场，车位编号 0..capacity-1，编号 capacity 又回到 0。head 指向「最早进场、下一个要离场的车」，尾巴顺着环往前接新车；只要没坐满，环可以无限循环使用同一批车位，不必「把所有车往前挪」。

### 3.5.2 定容队列：满与空的判别

环形缓冲区通常是**定容**（固定容量）的，因此要能判断「满」和「空」。若同时维护 size 计数，判别很直接：

空 ： size == 0        满 ： size == capacity

本机实测（Python 3.11.15，容量 3 的定容环形队列，含回绕覆盖）：

```python
class Ring:
    def __init__(self, cap):
        self.buf = [None]*cap; self.cap = cap; self.head = 0; self.size = 0
    def enqueue(self, x):
        if self.size == self.cap: raise IndexError("full")
        tail = (self.head + self.size) % self.cap
        self.buf[tail] = x; self.size += 1
    def dequeue(self):
        if self.size == 0: raise IndexError("empty")
        x = self.buf[self.head]; self.head = (self.head + 1) % self.cap; self.size -= 1
        return x
```

```
enqueue 1, enqueue 2, dequeue -> 1
enqueue 3, enqueue 4        # 4 回绕写到下标 0
head=1  size=3  buf=[4, 2, 3]
dequeue -> 2, 3, 4          # 仍严格 FIFO
```

dequeue 掉 1 后 head 前进到 1，随后 enqueue 4 时 `tail=(1+2)%3=0`，把 4 写回下标 0（覆盖了已出队的 1 留下的空位）——底层数组变成 `[4,2,3]` 但逻辑顺序仍是 2,3,4，出队顺序严格 FIFO。这就是「模运算复用空位、不搬移」的效果。

若**不额外存 size**，只用 head 和 tail 两个下标，则「满」和「空」都会表现为 `head == tail`，无法区分。经典解法有两种：额外存一个 size/count 计数（如上），或**牺牲一个槽位**（约定「tail 的下一格是 head 就算满」，即始终留一个空格），二者取其一。这是环形缓冲最出名的坑。

### 3.5.3 应用与代价画像

环形缓冲区的操作代价是**真正的最坏 O(1)**（定容、不扩容、不搬移）：

enqueue, dequeue ： O(1)（最坏）

它的用武之地正是「容量固定、要求稳定低延迟、且生产/消费速率相近」的场景：操作系统与硬件的 I/O 缓冲、键盘/网络收发缓冲、音视频流的定长缓冲、生产者-消费者队列、日志滚动缓冲（写满后覆盖最旧数据）。相比会扩容的动态数组队列，它没有扩容尖峰，延迟更可预测——代价是容量写死，满了要么阻塞、要么丢弃、要么覆盖最旧（取决于策略）。

环形缓冲 = 「队列（3.2）的定容数组实现」，用的是「数组实现（3.4.1）+ 模运算回绕」；若两端都做回绕就得到环形数组式的 deque（3.3.2 的 Open Data Structures ArrayDeque 走的就是这条路）。把 3.2–3.5 串起来看，它们是同一族「端点受限线性结构」在不同底层（连续数组 / 链表 / 环形数组 / 块状链表）上的落地变体。

#### 来源与时效
- 锚点：CLRS 4th ed (2022) §10.1——队列用循环数组、head/tail 下标回绕、满/空边界处理（核实 2026-07-26）。
- 锚点：Open Data Structures（Morin）§2.3 ArrayQueue——`a[(j+i) mod a.length]` 模运算索引映射、环形队列 O(1) 均摊（核实 2026-07-26）。
- 锚点：CS61B sp24 ArrayDeque（Project 1）——环形数组两端回绕的教学实现（核实 2026-07-26）。
- 本机实测：容量 3 的环形队列已复现「模运算回绕 + 不搬移 + FIFO 保持」（Python 3.11.15，见 3.5.2）。
- 一致性：CLRS 与 Open Data Structures 对模运算回绕的公式一致（`(head+i) mod capacity`）；「满/空判别」两源都指出需额外计数或留空槽，无分歧。
