# L3-01·大主题12 网络流与二部图匹配

> 基线 Py3.11.15/np2.4.6/gcc13.3 @2026-07-25 ｜ 核实日期：2026-07-26 ｜ 先修：大主题10（图的表示、BFS/DFS、O(V+E) 遍历——增广路要靠 BFS/DFS 找）、大主题11（贪心与松弛的最优性论证思路）、大主题8（贪心选择性质与交换论证，理解 Gale–Shapley 稳定性用得上）、L1-02 离散数学（图、集合、二部图、鸽巢与计数） ｜ 一手锚点：CLRS《Introduction to Algorithms》4th ed (2022, MIT Press) Ch24「Maximum Flow」(§24.1 Flow networks p671 / §24.2 The Ford-Fulkerson method p676 / §24.3 Maximum bipartite matching p693)、Ch25「Matchings in Bipartite Graphs」(§25.1 Maximum bipartite matching (revisited) p705 / §25.2 The stable-marriage problem p716 / §25.3 The Hungarian algorithm for the assignment problem p723)（章节页码取自 MIT Press 官方 4e 目录 PDF） ｜ 成熟度：GA/稳定（经典理论；CLRS 4th 为截至 2026-07-26 最新版，无更晚版本；Ch25 为 4e 相对 3e 新增的独立章）

> 覆盖映射与待核核定：本报告按 CLRS 4e 正文的**章节顺序**编排 12.1–12.6，与 prompt 的小主题清单对应关系为——12.1↔prompt 12.1（§24.1）、12.2↔prompt 12.2（§24.2）、12.3↔prompt 12.3（§24.3）、12.4↔prompt 12.4（§25.1 Hopcroft–Karp）、12.6↔prompt 12.5（§25.3 匈牙利）。两条 prompt 待核项均已逐条取证核定：**匈牙利算法节号 = §25.3**（非「待核」）；**稳定婚姻（Gale–Shapley）在 4e 为正文独立小节 §25.2**（不是习题级），据 prompt「证实则补入并锚定」要求，作为 12.5 补入。核定依据为 MIT Press 官方 4e 目录 PDF（见下各章「来源与时效」）。

本报告的可选实测基于 Python 3.11.15、numpy 2.4.6、scipy 1.17.1、Linux 6.18.5 x86_64，对应上面的基线串。测试脚本仅存于仓库外 scratchpad，跑完即清；下文只贴真实输出。实测为可选补充，正确性仍以多来源比对为准。

流的进阶（最小费用流、消负圈、Goldberg–Tarjan、push–relabel 预流推进）归 **L6-01 高级算法 #02「网络流进阶」**，本课止于 Ford–Fulkerson / Edmonds–Karp 与二部匹配，进阶只点名指路、不展开。一般图（非二部）匹配的 Edmonds「花树」算法也不在本课范围。

---

## 12.1 流网络与最大流

### 12.1.1 流网络的定义

流网络（flow network）是一个有向图 G = (V, E)，每条边 (u, v) 有一个非负**容量** c(u, v) ≥ 0（无边则记 c(u, v) = 0），并指定两个特殊结点：**源点** s（source）与**汇点** t（sink）。直觉上把边想成管道、容量是管道每秒最多能通过的水量，源点持续放水、汇点持续收水。

CLRS §24.1 对流网络作两条约定，以简化叙述（不失一般性）：若图中有边 (u, v)，则不同时有反向边 (v, u)（否则用拆点消除）；且每个结点都落在某条 s→t 路径上。

### 12.1.2 流的容量约束与流守恒约束

一个**流**（flow）是一个函数

f : V × V → ℝ

对每条边给出实际通过的流量，需同时满足两条约束。

容量约束（capacity constraint）——每条边的流量不超过其容量：

0 ≤ f(u, v) ≤ c(u, v)   对所有 u, v ∈ V

流守恒约束（flow conservation）——除源、汇外，每个结点的入流等于出流：

∑_{u∈V} f(u, v) = ∑_{w∈V} f(v, w)   对所有 v ∈ V − {s, t}

流的**值** |f| 定义为从源点净流出的总量（等于净流入汇点的量）：

|f| = ∑_{v∈V} f(s, v) − ∑_{v∈V} f(v, s)

**最大流问题**就是求一个使 |f| 最大的合法流。

容量约束保证不超载，流守恒保证「中间结点不囤水也不造水」，两者一起把「合法的水流」刻画清楚；最大流问题问的是「从 s 到 t 最多能稳定送多少水」。

为什么中间结点要守恒、而 s 和 t 不守恒？因为 s 是唯一的「水源」（只出不入或净流出）、t 是唯一的「水槽」（净流入），它们是系统的边界；中间结点只是转运站，进多少必须出多少，否则要么凭空造水要么水凭空消失，都不物理。初学者常见易错点：把流值 |f| 记成「所有边流量之和」——不对，|f| 只看**源点的净流出**（一条路径上的流量不能被重复计数）。CLRS 用的是「净流量」形式化（允许边上出现抵消），若采用「不带反向的非负流」定义则守恒式写法略有不同，但两种表述给出的最大流值一致。

### 12.1.3 残量网络与增广路

给定当前流 f，**残量网络**（residual network）G_f 刻画「每条边还能再加多少流、以及能撤回多少已加的流」。边 (u, v) 的**残量容量**（residual capacity）定义为

c_f(u, v) = c(u, v) − f(u, v)      （正向：还能加的量）
c_f(v, u) = f(u, v)                （反向：可撤回的量）

残量网络 G_f 只保留 c_f > 0 的边。G_f 中一条从 s 到 t 的简单路径叫**增广路**（augmenting path）；沿这条路径能额外推送的流量等于路径上各残量容量的最小值，即该路径的**瓶颈**：

c_f(p) = min { c_f(u, v) : (u, v) 在增广路 p 上 }

增广路是「当前还能整体加水的一条通道」，其中反向边代表「把之前某条边上的水抽回来改道」的机会——正是反向边让贪心式加流可以「反悔」，从而不会卡在次优解上。

初学者最难的一步就是**反向边**。举个最小例子：s→a→t 与 s→b→t 两条路，加上一条 a→b 容量为 1 的边。若第一步贪心把流走了 s→a→b→t，可能把后续更好的走法堵死；这时残量网络里出现反向边 b→a，第二次增广可以借它把 a 处的水「改道」回 s→a→t，同时 b 处改走 s→b→t，净效果是解锁了更大的流。记住：残量网络是随流 f 变化的动态图，每推一次流就要重算。与 12.2 的关系：Ford–Fulkerson 就是「反复在 G_f 里找增广路、沿瓶颈推流」直到再无增广路为止。

#### 来源与时效
- 锚点：CLRS 4e §24.1 Flow networks（p671，流网络/容量约束/流守恒/流值/残量网络/增广路定义）；核实 2026-07-26。
- 交叉核对：MIT 6.046J (Spring 2015) syllabus「Network Flow」模块（最大流问题与增广路框架，https://ocw.mit.edu/courses/6-046j-design-and-analysis-of-algorithms-spring-2015/pages/syllabus/ ）；cp-algorithms「Maximum flow - Ford-Fulkerson and Edmonds-Karp」对残量网络/增广路/瓶颈的定义与 CLRS 一致（https://cp-algorithms.com/graph/edmonds_karp.html ）。
- 冲突项：无。CLRS 采用「净流量（含抵消）」形式化，部分课程/竞赛资料采用「非负流 + 显式反向残量边」表述——二者最大流值等价，仅记法差异，非实质冲突。

## 12.2 Ford–Fulkerson 方法与最大流最小割定理

### 12.2.1 Ford–Fulkerson 方法

Ford–Fulkerson 是求最大流的**通用方法**（method，非单一算法）：只要残量网络里还存在增广路，就找一条、沿其瓶颈推流、更新残量网络；直到无增广路为止，此时的流即最大流。

```text
FORD-FULKERSON(G, s, t):
    对每条边 (u,v) 令 f(u,v) = 0
    while 残量网络 G_f 中存在一条 s→t 增广路 p:
        c_f(p) = min{ c_f(u,v) : (u,v) 在 p 上 }        // 瓶颈
        沿 p 把每条边的流量加 c_f(p)（反向边则减）
    return f
```

它之所以叫「方法」而非「算法」，是因为**「怎么找增广路」没定死**——不同的找法给出不同复杂度与终止性保证。

整数容量下 Ford–Fulkerson 一定终止且给出整数最大流；用朴素找路（如 DFS）时其运行时间为

O(E · |f*|)

其中 |f*| 是最大流的值。

为什么复杂度带 |f*|？因为每次增广至少把流值提高 1（整数容量下瓶颈 ≥ 1），最多增广 |f*| 次，每次找路 O(E)。这也暴露它的软肋：|f*| 可能很大（甚至与容量数值成正比而非与图规模成正比），最坏情况下会很慢；若容量是无理数，朴素 Ford–Fulkerson 甚至可能不终止、也不收敛到最大流。这正是需要 Edmonds–Karp 的动机。

### 12.2.2 Edmonds–Karp：用 BFS 选最短增广路

Edmonds–Karp 是 Ford–Fulkerson 的一个具体实例化：**每次用 BFS 找边数最少的增广路**。这个看似微小的选择把复杂度变成与流值无关的多项式：

O(V · E²)

唯一的改动是「找增广路时用 BFS 而非任意 DFS」，据此可证明增广次数被 O(V·E) 界住，与容量数值无关，因此对无理容量也多项式终止。

直觉上，BFS 保证每次走「最短」增广路，使得 s 到各点的残量最短距离随增广单调不减，从而每条边只能有限次成为瓶颈，增广总次数因此被封顶。初学者只需记住结论对照表：Ford–Fulkerson（朴素找路）O(E·|f*|)、依赖流值、无理容量可能不停；Edmonds–Karp（BFS 找路）O(V·E²)、与流值无关、恒多项式。更快的 Dinic / push–relabel 属 L6-01 #02，本课不展开。

### 12.2.3 割、最大流最小割定理

图的一个 **s–t 割** (S, T) 把结点集划分成两部分，s ∈ S、t ∈ T。割的**容量**是所有从 S 指向 T 的边容量之和：

c(S, T) = ∑_{u∈S} ∑_{v∈T} c(u, v)

**最大流最小割定理**（max-flow min-cut theorem）：对任一流网络，下面三条等价：

|f| 是最大流   ⟺   残量网络 G_f 中不存在增广路   ⟺   |f| = c(S, T) 对某个割 (S, T)

也就是说，**最大流的值 = 最小割的容量**。

这是网络流的核心定理，把「最大能送多少水（最大流）」和「最省力地切断多少水（最小割）」这两个看似不同的量画上等号，也给了 Ford–Fulkerson 的**终止即最优**证明——一旦没有增广路，当前流就等于某个割的容量，而任何流都不超过任何割的容量，故当前流最优。

怎么从「无增广路」读出最小割？取 S = {在残量网络里从 s 仍可达的结点}，T = 其余。因为 s→t 不可达，t ∈ T；且所有 S→T 的原边必满载（否则残量 > 0、v 就可达，矛盾），所有 T→S 的原边流量必为 0，于是 |f| = c(S, T)。最小例子：s→a 容量 3、a→t 容量 2，最大流 = 2，最小割就是切 a→t 这条边、容量 2。易错点：割只计**从 S 到 T 方向**的边容量，反方向 T→S 的边不计入割容量。

可选实测（不替代多源比对）：随机生成 3000 个小流网络，用 BFS 版 Ford–Fulkerson 求最大流值，再在残量网络里取 s 可达集算最小割容量，二者应恒相等：

```text
24.2 max-flow value == min-cut capacity (3000 random nets): True
```

3000 组全部相等，实测支持最大流最小割定理（定理来源仍以 CLRS §24.2 为准）。

#### 来源与时效
- 锚点：CLRS 4e §24.2 The Ford-Fulkerson method（p676，Ford–Fulkerson 方法、割与最大流最小割定理、Edmonds–Karp）；核实 2026-07-26。
- 交叉核对：cp-algorithms「Maximum flow - Ford-Fulkerson and Edmonds-Karp」——Ford–Fulkerson 整数容量 O(E·|f*|)、Edmonds–Karp O(V·E²) 且与流值无关、对无理容量仍多项式（https://cp-algorithms.com/graph/edmonds_karp.html ）；topcoder / brilliant.org 等独立资料给出同一 O(V·E²) 界。MIT 6.046J syllabus「Network Flow / Max-flow min-cut」模块印证定理与增广路框架。
- 冲突项：无。各源对两条复杂度界（O(E·|f*|) 与 O(V·E²)）表述一致。

## 12.3 最大二部匹配化为流

### 12.3.1 二部图与匹配

**二部图**（bipartite graph）的结点集能分成两块 L、R，使每条边都跨接一个 L 点和一个 R 点（同块内无边）。一个**匹配**（matching）M ⊆ E 是一组边，其中任意两条边不共享端点；**最大匹配**是边数最多的匹配。典型场景：L 是求职者、R 是岗位，边表示「此人能胜任此岗」，最大匹配就是最多能安排多少人上岗（一人一岗、一岗一人）。

最大二部匹配问题可以**化为一个单位容量的最大流问题**求解——这是 §24.3 的核心手法。

直觉上，匹配的「一人一岗」约束恰好就是流网络里「每条边至多走 1 单位流、每个点进出至多 1」的约束，所以匹配天然能翻译成流。

### 12.3.2 匹配化为流的构造

构造一个流网络 G′：加一个源点 s 连向所有 L 点、加一个汇点 t 被所有 R 点连入，原二部图的边从 L 指向 R，**所有边容量取 1**：

c(s, u) = 1  (u ∈ L)，  c(u, v) = 1  ((u,v) ∈ E)，  c(v, t) = 1  (v ∈ R)

则最大二部匹配的边数 = 该网络的最大流值：

|最大匹配| = |f*|

CLRS §24.3 的**整数性定理**（integrality theorem）保证：容量全为整数时，Ford–Fulkerson 求出的最大流每条边流量都是整数；单位容量下就是 0/1 流，取 f(u,v)=1 的中间边即得一个最大匹配。

为什么单位容量能强制「一人一岗」？s→u 容量 1 使每个 L 点最多送出 1 单位（一人最多配一岗）；v→t 容量 1 使每个 R 点最多收 1 单位（一岗最多配一人）；这两道「1 的闸门」把流的整数解逼成合法匹配。复杂度：因 |f*| ≤ min(|L|, |R|) ≤ V，用 Ford–Fulkerson 即 O(V·E)。

### 12.3.3 König 定理：最大匹配 = 最小顶点覆盖

**König 定理**（Kőnig's theorem）：在二部图中，最大匹配的边数等于最小顶点覆盖的点数：

|最大匹配| = |最小顶点覆盖|

（顶点覆盖 = 一个点集，使每条边至少有一个端点在其中。）

这条定理是最大流最小割定理在二部图上的对应物——最小割在这里就对应一个最小顶点覆盖，可由最大匹配构造性地读出。

它的意义在于「难与易的分水岭」——一般图的最小顶点覆盖是 NP 难（见大主题13），但**二部图**里靠 König 定理 + Hopcroft–Karp 可多项式求解。初学者记住：这是二部图特有的好性质，别推广到一般图。

可选实测（不替代多源比对）：随机生成 3000 个小二部图，用上面「单位容量流 + Ford–Fulkerson」求匹配基数，与 `scipy.sparse.csgraph.maximum_bipartite_matching` 对拍：

```text
24.3 flow-based matching size == scipy maximum_bipartite_matching (3000): True
```

3000 组匹配基数全一致，实测支持「匹配化为流」构造的正确性。

#### 来源与时效
- 锚点：CLRS 4e §24.3 Maximum bipartite matching（p693，匹配化为单位容量流、整数性定理）；核实 2026-07-26。
- 交叉核对：König 定理与 Hopcroft–Karp/最小顶点覆盖关系见 Wikipedia「Kőnig's theorem (graph theory)」（https://en.wikipedia.org/wiki/K%C5%91nig%27s_theorem_(graph_theory) ）与 Cornell CS6820 (Fall 2016/2024) 匹配讲义（https://www.cs.cornell.edu/courses/cs6820/2016fa/handouts/matchings.pdf ）；scipy 官方文档印证 `maximum_bipartite_matching` 语义（https://docs.scipy.org/doc/scipy/reference/generated/scipy.sparse.csgraph.maximum_bipartite_matching.html ）。
- 冲突项：无。

## 12.4 二部匹配的增广路刻画与 Hopcroft–Karp

> 本节对应 CLRS 4e §25.1「Maximum bipartite matching (revisited)」——4e 相对 3e 新增的 Ch25 首节，用增广路语言重讲二部匹配并给出 Hopcroft–Karp。

### 12.4.1 匹配中的增广路

给定匹配 M，一条**交替路径**（alternating path）沿路交替使用「非匹配边、匹配边」；两端都落在**未匹配点**上的交替路径叫**增广路径**（augmenting path，此处指匹配意义下，与流的增广路是同一思想的特例）。

Berge 定理：M 是最大匹配 ⟺ 不存在关于 M 的增广路径。

找到一条增广路径后，把路径上「匹配边↔非匹配边」的身份**整体翻转**（对称差），匹配的大小恰好 +1。

为什么翻转后匹配大小加一？增广路径两端是未匹配点，路径上非匹配边比匹配边恰好多一条，翻转后原来的非匹配边变匹配、匹配边变非匹配，净增一条匹配边，且仍是合法匹配。最小例子：路径 u₁—v₁=u₂—v₂（`=` 是当前匹配边，`—` 是非匹配边），u₁ 与 v₂ 未匹配，翻转后 u₁=v₁、u₂=v₂ 两条匹配边，比原来的 1 条多 1。这与 12.3 的「流增广」是同一件事：一次匹配增广对应流网络里一条 s→t 增广路。

### 12.4.2 Hopcroft–Karp 算法

朴素做法是「一次找一条增广路径、翻转」，共 O(V) 次、每次 O(E)，即 O(V·E)。**Hopcroft–Karp**（1973）改成分阶段：每个阶段用一次 BFS 找出**当前最短增广路径的长度**，再用 DFS 一次性找出一组**顶点不相交的最短增广路径**同时翻转。总复杂度：

O(E · √V)

靠「每阶段批量增广一组最短路径」，可证明阶段数只有 O(√V)，每阶段 O(E)，因此比朴素的 O(V·E) 快。

直觉是——最短增广路径的长度随阶段单调增加，长度超过 √V 后剩余增广路径最多 O(√V) 条，于是总阶段数被 √V 封顶。初学者不必掌握证明细节，只需记住：二部最大匹配的最好通用界是 O(E·√V)，由 Hopcroft–Karp 给出；它同时能配合 König 定理构造最小顶点覆盖。CLRS 4e 把 Hopcroft–Karp 作为习题/正文进阶内容放在 §25.1 一带（另见 walkccc CLRS 题解 26-6 对 Hopcroft–Karp 的整理，页面沿用旧章号 26，与 4e 正式版 Ch25 存在编号出入，以 MIT Press 官方目录 Ch25 为准）。

#### 来源与时效
- 锚点：CLRS 4e §25.1 Maximum bipartite matching (revisited)（p705，增广路刻画与 Hopcroft–Karp）；核实 2026-07-26。**节号核定**：Hopcroft–Karp/二部匹配增广路属 §25.1（MIT Press 官方 4e 目录 PDF）。
- 交叉核对：Hopcroft–Karp O(E·√V) 见 Cornell CS6820 匹配讲义、Columbia IEOR8100 Lec5（http://www.columbia.edu/~cs2035/courses/ieor8100.F12/lec5.pdf ）、Wikipedia「Hopcroft–Karp algorithm」；Berge 定理（增广路 ⟺ 最大匹配）多源一致。原始文献 Hopcroft & Karp 1973，O(√n·m)。
- 冲突项：网络二手题解（walkccc.me）沿用 3e 风格章号「26」，与 4e 官方 Ch25 编号不符——以 MIT Press 4e 官方目录为准，标注此编号出入。

## 12.5 稳定婚姻问题与 Gale–Shapley

> 取证核定项：prompt 曾标「稳定婚姻在 CLRS 疑为习题级、正文归属待核」。据 MIT Press 官方 4e 目录 PDF，稳定婚姻是 **4e 正文独立小节 §25.2「The stable-marriage problem」(p716)**，非习题级；据 prompt「证实则补入并锚定」，在此补入。

### 12.5.1 稳定婚姻问题与稳定性

有 n 个男士、n 个女士，每人对异性有一个完整的偏好排序。一个完美匹配（人人配对）称为**稳定**（stable），当且仅当不存在**阻塞对**（blocking pair）：一对未配成的男 m 与女 w，二人都**更偏好对方胜过各自当前的配偶**。**稳定婚姻问题**要求找一个稳定匹配。

稳定性不是要求「人人拿到最爱」（通常不可能），而是要求「没有一对人会同时想私奔」——即结果对所有人**联合自我强制**、无人有动机单方面破坏。

为什么要盯着「阻塞对」？如果存在一对 m、w 互相都比现配偶更中意，他们就会抛下各自配偶结合，匹配随即瓦解；杜绝所有这种对，匹配才「稳得住」。这与 12.1–12.4 的流/匹配是**不同类型**的匹配问题：那里最大化匹配的**大小**，这里在完美匹配中追求**偏好意义上的稳定**，目标函数完全不同。

### 12.5.2 Gale–Shapley 算法

**Gale–Shapley**（延迟接受，deferred acceptance）算法：每个未订婚男士向他名单上「尚未拒绝过他」的最高偏好女士求婚；每位女士暂时留住目前追求者中她最中意的一位、拒绝其余（已「订婚」的也可被更好的求婚者替换，原配重回单身）。重复到无人空闲。

```text
GALE-SHAPLEY(men_pref, women_pref):
    所有人初始为自由身
    while 存在自由且还有未求婚对象的男士 m:
        w = m 偏好表中下一位尚未求过的女士
        if w 自由: m、w 订婚
        elif w 更偏好 m 胜过其现任 m': 
            m、w 订婚；m' 恢复自由
        else: w 拒绝 m
    return 订婚结果
```

算法必终止，且总返回一个**稳定**的完美匹配；运行时间

O(n²)

（最多 n² 次求婚，每次 O(1)）。

为什么结果无阻塞对？女方的配偶只会「越换越好」（单调不降），男方沿偏好表「从高往低」求婚。反证：若 m、w 互为阻塞对（都更喜欢对方），那么 m 一定在中意现配偶之前就向 w 求过婚，w 要么当时拒了 m（说明 w 当时已有不差于 m 的对象、之后只会更好，故 w 现配偶 ≥ m，矛盾），要么留过 m 又被更好者替换（同样矛盾）。一个重要的非对称性：算法对**求婚方**（这里是男方）最优、对被求方最差——谁主动求婚谁占优。这与大主题8 的贪心/交换论证同源：每一步做局部不反悔的选择，最终得到全局稳定解。

可选实测（不替代多源比对）：随机生成 3000 个规模 ≤6 的偏好实例，跑 Gale–Shapley 并检验「输出是完美匹配且无阻塞对」：

```text
25.2 Gale-Shapley returns a perfect stable matching (3000): True
```

3000 组全部输出稳定完美匹配，实测支持算法正确性。

#### 来源与时效
- 锚点：CLRS 4e §25.2 The stable-marriage problem（p716，Gale–Shapley 延迟接受、稳定性、求婚方最优）；核实 2026-07-26。**正文归属核定**：§25.2 为 4e 正文独立小节（MIT Press 官方 4e 目录 PDF），非习题级——原「待核」结论更正为「正文保留」。
- 交叉核对：Gale & Shapley 1962 原始论文「College Admissions and the Stability of Marriage」；lastminutelecture 对 4e Ch25 的章节归纳（Hopcroft–Karp / Gale–Shapley / Hungarian 三节）与官方目录一致（https://www.lastminutelecture.com/2025/06/bipartite-matching-hopcroft-karp-gale-shapley-hungarian.html ）；O(n²) 界多源一致。
- 冲突项：与 3e 相比这是 4e 新增正文内容；prompt 原「疑习题级」的猜测经取证不成立，此处明确更正。

## 12.6 加权二部匹配：指派问题与匈牙利算法

> 本节对应 CLRS 4e §25.3「The Hungarian algorithm for the assignment problem」(p723)——即 prompt 小主题 12.5。**节号核定**：匈牙利算法 = §25.3（MIT Press 官方 4e 目录 PDF），原 prompt「节号待核」已核定，非待核。

### 12.6.1 指派问题

**指派问题**（assignment problem）：n 个工人、n 项任务，工人 i 做任务 j 的代价（或权重）为 w(i, j)；要求一一指派，使总代价最小（或总权重最大）：

minimize  ∑_{i} w(i, σ(i))    其中 σ 是 {1..n} 的一个排列

这是**加权完美二部匹配**——12.3 的最大匹配只数「配了几对」，这里每条边带权、要在完美匹配里选**总权最优**的那个。

暴力枚举所有排列是 n! 种，不可行；匈牙利算法在多项式时间内求出最优指派。

区分三类二部匹配问题以免混淆——最大基数匹配（§24.3/§25.1，边无权、最多配几对）、稳定匹配（§25.2，有偏好、求无阻塞对）、加权最优匹配（§25.3，边带权、求总权最优）。三者目标完全不同、算法也不同。

### 12.6.2 匈牙利算法

**匈牙利算法**（Hungarian algorithm，又称 Kuhn–Munkres）基于**对偶变量 / 顶点势**（potential）与「相等子图上找增广路」的思想：给每个点维护一个势，只在满足势条件的「紧边」组成的子图上找增广路扩充匹配，找不到时按最小松弛量调整势、暴露新紧边，直至得到完美匹配。它可看作最短路/最小费用流思想在指派问题上的特化。标准实现的时间复杂度：

O(V³)

匈牙利算法在 O(V³) 时间内求出**最优**（最小代价 / 最大权）的完美二部匹配，是指派问题的经典多项式解法。

初学者不必掌握势调整的全部推导，只需把握两点直觉——(1) 用「顶点势 + 紧边子图」把加权问题一层层化为无权的增广路问题（对偶把权重信息编码进势里）；(2) 每轮要么扩大匹配、要么调整势暴露新边，保证多项式步内收敛。它与最小费用流同源：指派问题是「单位供需、成本 w(i,j)」的最小费用流特例，这也是为什么进阶最小费用流（L6-01 #02）能统一处理它。实践中 `scipy.optimize.linear_sum_assignment` 就实现了求解指派问题的（Jonker–Volgenant 改进版）算法，同为 O(V³) 量级。

可选实测（不替代多源比对）：随机生成 2000 个 n≤5 的代价矩阵，用暴力枚举所有排列求最小总代价，与 `scipy.optimize.linear_sum_assignment` 对拍：

```text
25.3 scipy linear_sum_assignment == brute-force optimal (2000): True
```

2000 组最优总代价全一致，实测支持指派问题解法的正确性（O(V³) 界与算法归属仍以 CLRS §25.3 与多源比对为准）。

#### 来源与时效
- 锚点：CLRS 4e §25.3 The Hungarian algorithm for the assignment problem（p723，指派问题、顶点势/对偶、匈牙利算法）；核实 2026-07-26。**节号核定**：§25.3（MIT Press 官方 4e 目录 PDF），原「节号待核」已核定。
- 交叉核对：匈牙利/Kuhn–Munkres O(V³) 与「对偶/势 + 紧边增广路」思想见 scipy `linear_sum_assignment` 文档（实现为 O(n³) 的 Jonker–Volgenant 变体）与标准组合优化资料；指派问题作为最小费用流特例的关系见 CLRS 与 L6-01 #02 参考（Goldberg–Tarjan / 最小费用流）。
- 冲突项：「O(V³)」为标准 Hungarian/Kuhn–Munkres 与多数实现（含 scipy）一致的界；CLRS §25.3 正文给出的具体常数因子以其正文为准，此处仅取多源一致的量级 O(V³)，未凭记忆填 CLRS 专属常数。

---

## 全主题来源与时效汇总

- 一手主锚：CLRS《Introduction to Algorithms》4th ed (2022, MIT Press)。Ch24 Maximum Flow（§24.1 p671 / §24.2 p676 / §24.3 p693）、Ch25 Matchings in Bipartite Graphs（§25.1 p705 / §25.2 p716 / §25.3 p723）。章节标题与页码取自 MIT Press 官方 4e 目录 PDF（https://mitp-content-server.mit.edu/books/content/sectbyfn/books_pres_0/11599/4e_toc.pdf ）与书页（https://mitpress.mit.edu/9780262046305/introduction-to-algorithms/ ）。核实 2026-07-26。
- 二手交叉（补一手留白 / 交叉验证，不承重）：MIT 6.046J (Spring 2015) syllabus；cp-algorithms（Edmonds–Karp）；Wikipedia（König 定理、Hopcroft–Karp）；Cornell CS6820、Columbia IEOR8100 匹配讲义；scipy 官方文档（maximum_bipartite_matching、linear_sum_assignment）。
- 待核项核定小结（本报告两处逐条取证结论）：
  - 匈牙利算法节号 → **§25.3**（已核定，非待核）。
  - 稳定婚姻 / Gale–Shapley 正文归属 → **4e 正文独立小节 §25.2**（已核定为正文保留，非习题级；原「待核/疑习题级」结论更正）。
- 成熟度：GA/稳定。CLRS 4th ed 为截至 2026-07-26 最新版；Ch25 为 4e 相对 3e 的新增独立章。
- 已知编号出入：部分网络二手题解（walkccc.me）沿用旧章号将二部匹配问题标为「Chap26」，与 4e 官方 Ch25 不符——以 MIT Press 官方 4e 目录为准。
