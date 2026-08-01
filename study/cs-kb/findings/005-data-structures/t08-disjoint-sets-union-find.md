# L2-01·大主题8 不相交集合（并查集）

> 基线 Py3.11.15/np2.4.6/gcc13.3 @2026-07-25 ｜ 核实日期：2026-07-26 ｜ 先修：大主题1（渐近分析与均摊分析）、大主题2（数组）、大主题5（树与森林术语） ｜ 一手锚点：CLRS《Introduction to Algorithms》4th ed(2022) Ch19（Data Structures for Disjoint Sets，§19.1 操作 / §19.2 链表表示 / §19.3 森林表示 / §19.4 反阿克曼分析）；Berkeley CS61B Spring 2024（Disjoint Sets 讲次，sp24.datastructur.es，Java）；Sedgewick & Wayne《Algorithms》4th ed §1.5（Union-Find）；Tarjan(1975) 紧界原始论文（旁证） ｜ 成熟度：GA/稳定
>

本报告的 Python 实测基于 Python 3.11.15、Linux 6.18.5 x86_64，对应上面的基线串。所有复杂度与公式系数均取自下列一手来源的交叉核对（见各节「来源与时效」），未凭记忆填数。需要特别说明的一处来源情况：Pat Morin《Open Data Structures》并无专门的并查集章节，故本报告以 CLRS Ch19 与 CS61B 为两个独立一手主源，并以 Sedgewick & Wayne《Algorithms》4th 与 Tarjan(1975) 作交叉源。

---

## 8.1 不相交集合 ADT — MAKE-SET / UNION / FIND-SET

### 8.1.1 不相交集合是什么

不相交集合数据结构（disjoint-set data structure，俗称并查集 union-find）维护一族**互不相交**的动态集合的集合

S = {S₁, S₂, …, Sₖ}

其中任意两个集合没有公共元素，每个元素恰好属于一个集合。每个集合由它内部的某个元素充当「代表元」（representative）来指代——你不关心具体是谁当代表，只要求同一个集合每次问到的代表相同、不同集合的代表不同。

它要解决的核心问题只有两个：把两个集合**合并**成一个，以及查询**两个元素是否在同一个集合里**。因为集合始终保持不相交，合并才有明确语义：合并后原来的两个集合作为独立个体消失，变成一个新集合。

初学者最好把它理解成「维护一种动态的等价关系」。等价关系（自反、对称、传递）会把全体元素划分成若干等价类（equivalence class），每个等价类就是一个不相交集合。随着不断声明「a 和 b 等价」，等价类被逐步合并——这正是并查集干的事。

### 8.1.2 三个核心操作

不相交集合 ADT 的接口由三个操作组成，CLRS §19.1 给出的定义如下：

MAKE-SET(x)：新建一个只含元素 x 的集合，x 自任代表（要求 x 不在任何已有集合中）。

UNION(x, y)：把包含 x 的集合与包含 y 的集合合并成一个新集合，新集合的代表通常取原两个代表之一；原两个集合从族中移除。

FIND-SET(x)：返回 x 所在集合的代表。

「两个元素是否同属一个集合」这个查询不是独立的第四个操作，而是用 FIND-SET 拼出来的：

x 与 y 同集合  ⟺  FIND-SET(x) == FIND-SET(y)

这是理解并查集的第一个关键点——所有「连通性/等价性」判断都归结为「两次 FIND 的结果是否相等」，因为同一集合的所有元素共享同一个代表。

UNION 的前提是两个集合原本不相交。如果 x、y 本就在同一集合，规范实现会先各做一次 FIND-SET 发现代表相同，然后什么都不做（这也是为什么实现里 UNION 内部先调用两次 FIND-SET）。

### 8.1.3 典型应用：无向图连通分量与 Kruskal

并查集最直接的应用是**离线维护无向图的连通分量**：初始每个顶点 MAKE-SET 各成一组；每读到一条边 (u, v) 就 UNION(u, v)；处理完后，两个顶点连通当且仅当它们的 FIND-SET 相等。CLRS §19.1 正是用「CONNECTED-COMPONENTS / SAME-COMPONENT」这对过程引入整章。

它也是 Kruskal 最小生成树算法的核心数据结构：按权重从小到大扫边，用 FIND-SET 判断这条边的两个端点是否已连通，若否则 UNION 并把边收入生成树——用并查集在近常数时间内完成「加这条边会不会成环」的判断。CS61B 的 Disjoint Sets 讲次同样以「动态连通性（dynamic connectivity）」问题引入，与 CLRS 的连通分量视角一致。

要点在于「离线/动态但不删」：并查集擅长「只增不减」的合并——它天然支持 UNION，但**不支持把一个集合再拆开**（没有高效的 split/delete）。需要删除的场景要换别的结构，这是初学者常踩的边界。

#### 来源与时效
- 锚点：CLRS 4th ed(2022) §19.1（Disjoint-set operations，MAKE-SET/UNION/FIND-SET 定义与 CONNECTED-COMPONENTS 应用），核实 2026-07-26。
- 锚点：Berkeley CS61B Spring 2024，Disjoint Sets 讲次（以 dynamic connectivity 引入，接口 connect / isConnected），sp24.datastructur.es，核实 2026-07-26。
- 交叉源：Sedgewick & Wayne《Algorithms》4th §1.5 Union-Find（union / find / connected API），与上面两源接口一致。
- 版本注记：CLRS **4th 版本章号为 Ch19**；3rd 版同一内容位于 Ch21。引用 §编号时须认准版本，本报告统一用 4th 的 19.x。

## 8.2 森林表示 — 以父指针表示集合树

### 8.2.1 从链表表示到森林表示

在讲高效实现前先交代动机。CLRS §19.2 先给一个朴素的**链表表示**：每个集合是一条链表，每个元素额外存一个指回「集合对象/代表」的指针。这种表示 FIND-SET 是 O(1)（直接读指针），但 UNION 要把一条链表接到另一条上，并逐个更新被并入元素的代表指针，最坏 O(n)。

森林表示（disjoint-set forest，§19.3）是当前的主流实现，把每个集合组织成一棵**有根树**：树里每个结点是一个元素，结点只保存一个指向**父结点**的指针，树根指向自己（p[root] = root），树根就是这个集合的代表。整族集合就是若干棵这样的树，合起来是一片森林。

这样表示的好处是 UNION 极快：只要把一棵树的根挂到另一棵树的根下面，改一个指针即可。代价转移到了 FIND-SET——它要顺着父指针一路往上走到根。8.3、8.4 两节的全部努力，就是让这条「往上走」的路尽量短。

### 8.2.2 父指针数组与两个基本过程

当元素可以编号为 0…n−1 时，森林最省事的落地就是一个「父指针数组」`parent[]`，`parent[i]` 存 i 的父结点编号，根满足 `parent[i] == i`。这也是 CS61B「Quick Union」讲法的核心表示。三个操作的骨架（未加任何启发式，即朴素 Quick Union）：

MAKE-SET(x): parent[x] = x

FIND-SET(x): while parent[x] ≠ x: x = parent[x]; return x

UNION(x, y): parent[FIND-SET(x)] = FIND-SET(y)

FIND-SET 的代价正比于结点到根的路径长度，也就是它在树中的深度；UNION 由两次 FIND-SET 加一次改指针构成，所以 UNION 的代价也由树高决定。

初学者要区分开两个数组语义：一个是这里的「父指针」，指向**树中的父亲**（可能要走多步才到根）；另一个是链表表示里的「代表指针」，直接指向根/代表。森林表示故意放弃了「一步到代表」，换来 UNION 的廉价，这是本大主题第一个权衡。

### 8.2.3 朴素实现为什么会退化

朴素 Quick Union 的致命问题：如果每次 UNION 都把「大树」挂到「小结点」下面，树会长成一条**几乎线性的链**。极端情形下，按 UNION(0,1), UNION(1,2), …, UNION(n−2, n−1) 这样连续合并，就可能得到一条高度 Θ(n) 的链，此时单次 FIND-SET 退化到

FIND-SET 最坏代价 = Θ(n)

于是 m 次操作最坏 Θ(m·n)——比链表表示还糟。这与大主题5 里「有序插入让 BST 退化成链表」是同一种病：结构本身没规定怎么合并，坏的合并顺序就把树拉高了。

这正是引出 8.3 的动机：既然树高决定代价，就得在 UNION 时**有意识地控制合并方向**，别让树越长越高。

#### 来源与时效
- 锚点：CLRS 4th ed(2022) §19.2（链表表示，FIND O(1)/UNION 需更新代表指针）、§19.3（disjoint-set forests，父指针、MAKE-SET/UNION/FIND-SET 伪码），核实 2026-07-26。
- 锚点：Berkeley CS61B Spring 2024，Disjoint Sets 讲次（Quick Find → Quick Union → Weighted Quick Union 演进，parent[] 数组、根存自身/负数约定），核实 2026-07-26。
- 交叉源：Sedgewick & Wayne《Algorithms》4th §1.5（quick-union 的 id[] 数组与树高退化示例）。
- 一致性：三源对「朴素 Quick Union 最坏树高 Θ(n)、故需加权」的判断一致，无冲突。

## 8.3 按秩/按大小合并 — 控制树高的合并启发

### 8.3.1 按秩合并（union by rank）

按秩合并是 CLRS §19.3 采用的第一个启发式：给每个结点维护一个整数 `rank`，它是该结点**为根时子树高度的一个上界**。MAKE-SET 时 rank 置 0。UNION 时比较两个根的 rank，把**秩较小**的根挂到秩较大的根下面；两根秩相等时任选其一作新根，并把新根的秩加 1。CLRS 的 LINK 过程如下：

若 rank[x] > rank[y]：parent[y] = x

否则：parent[x] = y；若 rank[x] == rank[y] 则 rank[y] = rank[y] + 1

直觉是「矮树挂到高树下」：把矮的接到高的下面，整体高度不变；只有两棵一样高的树合并时，高度才被迫加 1。这样高度增长得极慢。

一旦叠加了 8.4 的路径压缩，rank 就**不再等于真实高度**，只是高度的一个上界——路径压缩会把结点提上来、降低真实高度，但 CLRS 约定 rank 不回退。所以严格说它叫「秩」而不叫「高度」，这个命名是有意的。

### 8.3.2 按大小合并（union by size / 加权快速合并）

CS61B 与 Sedgewick & Wayne 走的是另一条等效路线：**按大小合并**（union by size），也叫加权快速合并（Weighted Quick Union）。每个根维护它所辖子树的**结点数**（size）；UNION 时把结点数**较少**的树挂到结点数较多的树下面。

一个常见的省内存实现技巧（CS61B 讲法）：不用单独的 size 数组，而让根在 parent[] 数组里存**负的集合大小**，非根仍存父指针——一个数组同时编码了「谁是根」和「根有多大」。

按大小和按秩是两个**不同但目的相同**的启发式，都能保证：

任一树的高度 ≤ ⌊log₂ n⌋

按大小合并容易证明这个界：每当一个结点的深度加 1，它所在的树至少翻倍（因为它被并入了一棵不小于自己的树），而翻倍最多发生 log₂ n 次。

### 8.3.3 O(log n) 高度界，以及两源的分歧

只用合并启发式（还没有路径压缩）时，两种方式都把单次操作压到 O(log n)，于是 m 次操作

总代价 = O(m log n)

这已经比朴素实现的 O(m·n) 好得多。

这里点明一处来源分歧，供读者对齐不同教材：**CLRS 用「按秩」，CS61B / Sedgewick 用「按大小」**。二者都保证 O(log n) 高度，实践中差别很小；区别在于——「大小」是精确的结点计数、与路径压缩无关，永远准确；而「秩」是高度上界，在路径压缩后会高估真实高度（但不影响正确性和渐近界）。CLRS 选秩是因为它在与路径压缩合用的反阿克曼分析里更好处理；CS61B 选大小是因为实现直观、便于用负数编码。两者不是对错之争，是不同教材的取舍。

支撑「合并启发式确实压住了树高」的本机测量见 8.4.3——那里把「无启发式 / 只合并 / 只压缩 / 两者都用」四种组合放在一起对比。

#### 来源与时效
- 锚点：CLRS 4th ed(2022) §19.3（union by rank、LINK 伪码、rank 为高度上界的约定），核实 2026-07-26。
- 锚点：Berkeley CS61B Spring 2024，Disjoint Sets 讲次（Weighted Quick Union = union by size、根存负 size、高度 O(log n)），核实 2026-07-26。
- 交叉源：Sedgewick & Wayne《Algorithms》4th §1.5（weighted quick-union，树高 ≤ lg n 的证明）。
- 冲突项：合并启发式的度量不同——CLRS「按秩（rank，高度上界）」vs CS61B/Sedgewick「按大小（size，精确结点数）」；两边高度界均为 O(log n)，正确性等价，差异在于秩在路径压缩后高估真实高度。两种表述本报告都记录，不取舍。

## 8.4 路径压缩与近常数均摊 — α(n) 反阿克曼界

### 8.4.1 路径压缩（path compression）

路径压缩是 CLRS §19.3 的第二个启发式，也是让并查集从 O(log n) 跨到「近常数」的关键。它的思想极简：**每次 FIND-SET 沿途走过的所有结点，事后都直接改指向根**。这样这条路径上的结点下次再 FIND 就一步到根，把「走过一次的长路」永久摊平。两趟式实现（先找到根，再回来重挂）：

FIND-SET(x): if x ≠ parent[x]: parent[x] = FIND-SET(parent[x]); return parent[x]

这段递归的精妙在于返回时的赋值 `parent[x] = …`：它在回溯路上把每个结点重挂到根。注意路径压缩**只改父指针、不改 rank**——这就是 8.3.1 里说「rank 从此只是高度上界」的原因。

直觉上这是一种「自我优化」：结构在被查询的过程中顺手把自己变得更扁，查询越多、树越扁，后续越快。这也是为什么它的收益必须用**均摊分析**来刻画（承接大主题1）——单次 FIND-SET 最坏仍可能较长，但把「压平」的一次性投入摊到后续大量廉价查询上，平均代价极低。

### 8.4.2 α(n) 反阿克曼界

CLRS §19.4 的主结论（其定理 19.14）是：在**同时**使用按秩合并与路径压缩的森林上，一串共 m 个操作（其中 n 个是 MAKE-SET）的最坏总代价为

O(m · α(n))

其中 α(n) 是一个增长极其缓慢的函数——**反阿克曼函数**（inverse Ackermann function）。它是阿克曼函数 Aₖ(j) 的某种「反函数」，CLRS 定义为

α(n) = min { k : Aₖ(1) ≥ n }

阿克曼函数增长快到离谱，因此它的反函数增长慢到离谱。用 CLRS 的定义代入几个值就能体会：

A₀(1) = 2，A₁(1) = 3，A₂(1) = 7，A₃(1) = 2047，A₄(1) 是一个远超宇宙原子数的天文数字

于是 α(n) 的取值分段是：n ≤ 2 时 α = 0；n ≤ 3 时 α = 1；n ≤ 7 时 α = 2；n ≤ 2047 时 α = 3；一直到 n ≤ A₄(1) 时 α = 4。也就是说：

对任何你在现实中能存下的 n，α(n) ≤ 4

因此「O(m α(n)) 均摊」在工程上等价于「每次操作近乎常数」——但它**不是真正的 O(1)**，α(n) 确实会随 n 增长，只是慢到永远碰不到 5。初学者只要记住这个直觉即可；完整的势能法证明属专家级纵深，本报告不展开（这正是本大主题在 v3 下「给直觉、不做完整证明」的边界）。

还需交代两个「只用一个启发式」的对照界，以看清必须两者合用：只用按秩合并（无压缩）是 O(m log n)；只用路径压缩（无按秩/按大小合并）也能达到近线性但不如两者合用。历史上，Tarjan(1975) 进一步证明了这个界是**紧的**（Θ(m α(n))），即在一般模型下无法做到严格线性——CLRS 证的是上界 O，紧下界归功于 Tarjan 的原始论文。

### 8.4.3 组合效果的本机测量

下面这段本机实测把「无启发式 / 只按秩合并 / 只路径压缩 / 两者都用」四种组合放在同一规模下对比，度量的是每次 FIND-SET 沿父指针向上走的**跳数**（hop 数，直接反映路径长度/树高）。它用来加固上文的定性结论，不替代多来源比对。

复现脚本（Python 3.11.15，Linux 6.18.5 x86_64；FIND 用迭代式，路径压缩为两趟法）：

```python
import random

class UF:
    def __init__(self, n, use_rank=True, use_pc=True):
        self.p = list(range(n)); self.rank = [0]*n
        self.use_rank = use_rank; self.use_pc = use_pc
        self.steps = 0  # 统计 FIND 中向上走的父指针跳数

    def find(self, x):
        if self.use_pc:
            root = x
            while self.p[root] != root:
                self.steps += 1; root = self.p[root]
            while self.p[x] != root:          # 两趟法：回来把路径重挂到根
                nxt = self.p[x]; self.p[x] = root; x = nxt
            return root
        else:
            while self.p[x] != x:
                self.steps += 1; x = self.p[x]
            return x

    def union(self, a, b):
        ra, rb = self.find(a), self.find(b)
        if ra == rb: return
        if self.use_rank:
            if self.rank[ra] < self.rank[rb]: ra, rb = rb, ra
            self.p[rb] = ra
            if self.rank[ra] == self.rank[rb]: self.rank[ra] += 1
        else:
            self.p[ra] = rb                    # 朴素：固定方向挂，制造退化

def run(n, trials, use_rank, use_pc, seed=42):
    random.seed(seed); uf = UF(n, use_rank, use_pc)
    for _ in range(n-1): uf.union(random.randrange(n), random.randrange(n))
    uf.steps = 0
    for _ in range(trials): uf.find(random.randrange(n))
    return uf.steps

n, trials = 100000, 100000
print(f"n={n}, FIND trials={trials}")
for rank, pc in [(False,False),(True,False),(False,True),(True,True)]:
    s = run(n, trials, rank, pc)
    print(f"union_by_rank={rank!s:5} path_compression={pc!s:5} -> total hops={s:>9}  avg={s/trials:.3f}")
```

```
n=100000, FIND trials=100000
union_by_rank=False path_compression=False -> total hops=560853999  avg=5608.540
union_by_rank=True  path_compression=False -> total hops=   190154  avg=1.902
union_by_rank=False path_compression=True  -> total hops=   141711  avg=1.417
union_by_rank=True  path_compression=True  -> total hops=   101257  avg=1.013
```

n = 10 万时，朴素实现平均每次 FIND 要走约 5600 跳（树被拉成长链，量级与 n 相当）；只要加上任意一个启发式，就骤降到平均约 1–2 跳；两者合用后平均约 1.01 跳，几乎一步到根。这与「两者合用 → O(α(n)) 近常数」的理论结论方向一致（注意这是随机合并输入下的平均跳数示意，不是最坏界的严格测量，最坏界仍以教材证明为准）。

### 8.4.4 常见易错点

第一，路径压缩单独用时**不需要维护 rank/size**，但要拿到那条 O(α(n)) 的最优均摊界，教材要求**两个启发式同时上**；只压缩不加权的常数会略差（见上表 avg 1.417 vs 1.013）。

第二，别把 α(n) ≤ 4 误说成「并查集是 O(1)」。它是**均摊近常数**，形式上是 O(α(n))，α 仍是增函数；单次操作最坏也并非严格常数。

第三，路径压缩后 rank 不再是真实高度，若程序里有依赖「rank == 高度」的逻辑（例如想反推树高）会出错——rank 只保证是上界。

#### 来源与时效
- 锚点：CLRS 4th ed(2022) §19.3（path compression，两趟 FIND-SET 伪码）、§19.4（union by rank + path compression 的 O(m α(n)) 分析、α(n)=min{k:Aₖ(1)≥n} 定义、Aₖ 取值与 α(n)≤4 论断），核实 2026-07-26。
- 锚点：Berkeley CS61B Spring 2024，Disjoint Sets 讲次（path compression 使 Weighted Quick Union 达到近常数均摊、给出 α 直觉），核实 2026-07-26。
- 交叉源：Sedgewick & Wayne《Algorithms》4th §1.5（weighted quick-union with path compression 近线性）；Tarjan(1975)《Efficiency of a Good But Not Linear Set Union Algorithm》（Θ(m α(n)) 紧界的原始出处）。
- 本机实测：Python 3.11.15 / Linux 6.18.5 x86_64，四组合平均 FIND 跳数 5608.540 / 1.902 / 1.417 / 1.013（随机合并输入，示意性加固，非最坏界证明）。
- 一致性与分歧：各源对「两启发式合用 → 近常数均摊」结论一致；α(n) 的精确定义式随教材写法略有出入（CLRS 用 Aₖ(1)≥n 版本），本报告采用 CLRS 4th 的定义；「只压缩不加权」的精确界（Tarjan 的 Θ(n+f·(1+log_{2+f/n} n)) 型式）属专家级细节，本报告只作定性提及，不展开为结论。
