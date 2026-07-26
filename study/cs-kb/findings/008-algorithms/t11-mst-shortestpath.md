# L3-01·大主题11 最小生成树与最短路

> 基线 Py3.11.15/np2.4.6/gcc13.3 @2026-07-25 ｜ 核实日期：2026-07-26 ｜ 先修：大主题1（渐进记号 O/Θ、最坏情形分析）、大主题8（贪心选择性质与交换论证）、大主题7（动态规划、最优子结构）、大主题10（图的表示、BFS/DFS、拓扑排序）、大主题6（优先队列/堆、并查集）、L1-02 离散数学（图论基础、松弛/归纳） ｜ 一手锚点：CLRS《Introduction to Algorithms》4th ed (2022, MIT Press) Ch21「Minimum Spanning Trees」（§21.1–21.2）、Ch22「Single-Source Shortest Paths」（§22.1–22.5）、Ch23「All-Pairs Shortest Paths」（§23.1–23.3）；MIT 6.046J Design & Analysis of Algorithms (Spring 2015) syllabus「Graph Algorithms」模块 ｜ 成熟度：GA/稳定（经典理论；CLRS 4th 为截至 2026-07-25 最新版，无更晚版本）

> 粒度判定：**1 份**（不拆）。理由：本大主题 4 个小主题（11.1–11.4）落在 CLRS 4e 相邻的 Ch21–23，串成一条咬合的主线——都是「在带权图上做优化」，共用两把工具：**贪心 + 安全边**（MST）与**松弛（relaxation）**（最短路）。差分约束（11.3）本身就是单源最短路（11.2 的 Bellman–Ford）的一个直接应用，篇幅短；全对最短路（11.4）的 Johnson 算法又把 Bellman–Ford（11.2.2）和 Dijkstra（11.2.4）当零件复用。四节机制同源、互相调用，拆开反而割裂调用链，且总篇幅与同课已产报告（如 t04 排序横跨 Ch6–8 为单篇）相当，未触及 report-format v3 §一「小主题多/跨机制/过长」的拆分线，故单篇。跨课边界：用 Fibonacci 堆把 Dijkstra/Prim 的优先队列操作加速到 O(E + V lg V) 属 L6-01 #01（高级数据结构与摊还加速），本报告点名指路、不展开实现。

本报告正文的复杂度界、松弛正确性条件、章节号均来自 CLRS 4e 与至少一个独立来源交叉核对（见各章末「来源与时效」），未凭记忆填。可选的本机实测基于 Python 3.11.15、numpy 2.4.6、scipy 1.17.1、sympy 1.14.0、Linux 6.18.5 x86_64（对应基线串）；测试脚本仅存于仓库外 scratchpad，跑完即清，下文只贴真实输出。实测是补充，正确性仍以多来源比对为准。

伪代码沿用 CLRS 风格；正文里图的顶点数记 V、边数记 E（也写作 |V|、|E|），`lg` 表以 2 为底的对数。

---

## 11.1 最小生成树

### 11.1.1 最小生成树问题与通用贪心框架

给一个**连通、无向、带权**的图 G=(V,E)，每条边 (u,v) 有权重 w(u,v)。**生成树**是一棵连接了全部 V 个顶点、恰好用 V−1 条边、且无环的子图。**最小生成树（MST, minimum spanning tree）**就是所有生成树里边权之和

w(T) = Σ_{(u,v)∈T} w(u,v)

最小的那一棵。典型场景：用最少的电缆总长把所有楼连通。

两大经典算法（Kruskal、Prim）都是同一个**通用贪心框架**的具体化。框架维护一个边集合 A，它始终是「某棵 MST 的子集」，每一步往 A 里加一条**安全边（safe edge）**——加进去后 A 仍是某棵 MST 的子集——直到 A 长成完整的 MST：

```
GENERIC-MST(G, w)
  A = ∅
  while A 还不构成一棵生成树
    找一条对 A 安全的边 (u,v)
    A = A ∪ {(u,v)}
  return A
```

怎么判断哪条边「安全」？靠**割（cut）与轻边（light edge）定理**。一个割 (S, V−S) 把顶点分成两堆；一条边若两端分属两堆，就说它**横跨**这个割。若一个割**不切断** A 里任何已选边（术语：割**尊重（respect）**A），那么横跨这个割的**权重最小的边（轻边）**对 A 就是安全的：

若割 (S,V−S) 尊重 A，且 (u,v) 是横跨该割的一条轻边，则 (u,v) 对 A 安全。

初学者直觉：MST 的核心是一句「贪心不会吃亏」的承诺。想象你要把两片还没连起来的区域接通，那条最便宜的跨界边一定可以放心选——因为任何一棵 MST 如果没用它，就必然用了另一条更贵的跨界边，把那条换成这条不会让总权变大（这正是大主题8「交换论证」的套路）。这就是「安全」的含义：选它，不会毁掉「最终能长成 MST」的可能性。

易错点一：MST 针对**无向连通**图；有向图的对应问题（最小树形图）是另一回事，不在本节。易错点二：当边权**互不相同**时 MST **唯一**；有相同权重时 MST 可能不唯一，但最小总权 w(T) 仍唯一。易错点三：「安全」是相对于「还能不能补成 MST」而言，不是「当前看起来最省」——两个说法在轻边定理下恰好一致，但要理解的是前者。

### 11.1.2 Kruskal 算法

Kruskal 把 GENERIC-MST 具体化为：**把所有边按权重从小到大排序，依次考察；若一条边的两个端点当前不在同一棵树里，就选它，否则丢弃**。它维护的 A 是一个**森林**（多棵树），每选一条边就把两棵树合并成一棵。

```
MST-KRUSKAL(G, w)
  A = ∅
  for 每个顶点 v ∈ V:  MAKE-SET(v)
  按 w 非降序排序 E 的所有边
  for 每条边 (u,v)（按序）:
    if FIND-SET(u) ≠ FIND-SET(v):
      A = A ∪ {(u,v)}
      UNION(u, v)
  return A
```

判断「两端点是否已在同一棵树」用的是**不相交集合（并查集）**（大主题6 的 6.7）：FIND-SET 查代表元、UNION 合并。用「按秩合并 + 路径压缩」时，这些操作近乎常数摊还。整体运行时间由排序主导：

O(E lg E) = O(E lg V)

（因为 E ≤ V²，所以 lg E ≤ 2 lg V = O(lg V)，两种写法等价。）

初学者直觉：Kruskal 是「全局从最便宜的边开始捡，只要不成环就要」。它对应的割定理视角是：当你考虑当前最小的那条跨树边时，把「它连接的那棵树」当作 S，这条边就是尊重 A 的割上的轻边，故安全。「不成环」这个判据等价于「两端点不在同一棵树」——若已在同一棵树，加它必成环，也就不是横跨两片的边了。易错点：必须按**全局权重升序**扫，不是从某个点出发；这也是它和 Prim 最大的外观差异。

### 11.1.3 Prim 算法

Prim 把 A 始终维护成**一棵树**（不是森林）：从任选的根 r 出发，每一步把「离当前树最近的那个树外顶点」连进来，直到所有顶点都进树。它对每个树外顶点 v 维护一个键值 v.key = 「v 到当前树的最小连接边权」，用**最小优先队列**反复取出 key 最小者。

```
MST-PRIM(G, w, r)
  for 每个顶点 u ∈ V:  u.key = ∞;  u.π = NIL
  r.key = 0
  Q = V                        # 最小优先队列，按 key
  while Q ≠ ∅:
    u = EXTRACT-MIN(Q)         # 取离树最近的树外点，纳入树
    for 每个邻居 v ∈ Adj[u]:
      if v ∈ Q and w(u,v) < v.key:
        v.π = u
        v.key = w(u,v)         # DECREASE-KEY
  # 结果 MST = { (v, v.π) : v ∈ V − {r} }
```

用**二叉堆**实现优先队列时（EXTRACT-MIN 与 DECREASE-KEY 各 O(lg V)）：

O(E lg V)

初学者直觉：Prim 像「滚雪球」——树从一个点开始，每次沿最便宜的一条边把最近的邻居吸进来。它对应的割是「已进树的点 S」和「树外点 V−S」，每步选的正是横跨这个割的轻边，故安全。和 Kruskal 的对照：Kruskal 是「全局排序、可能同时长多棵小树最后合并」，Prim 是「单点起步、始终一棵连通的树」。两者都由割定理保证正确，最终总权相同（若权互异则得到同一棵树）。

跨课边界：把二叉堆换成 **Fibonacci 堆**可把 Prim 降到 O(E + V lg V)（DECREASE-KEY 摊还 O(1)）——这属 L6-01 #01，本课点名即可，不展开。

#### 来源与时效
- 锚点：CLRS 4th ed (2022) Ch21——§21.1「Growing a minimum spanning tree」（GENERIC-MST、割/轻边/安全边定理）、§21.2「The algorithms of Kruskal and Prim」（两算法伪代码、Kruskal O(E lg V)、Prim 二叉堆 O(E lg V)、Fibonacci 堆 O(E + V lg V)）；核实 2026-07-26。
- 锚点：MIT 6.046J (Spring 2015) syllabus「Greedy Algorithms / Graph Algorithms」模块列 MST 为贪心典型；核实 2026-07-26。
- 二手交叉核对（补充、非承重）：Wikipedia「Kruskal's algorithm」「Prim's algorithm」与 walkccc.me/CLRS Ch 均确认 Kruskal O(E lg E)=O(E lg V)、Prim 二叉堆 O(E lg V)、Fibonacci 堆 O(E + V lg V)，与 CLRS 一致，无分歧。
- 版本/章号提示：MST 在 CLRS **3e 为 Ch23**，4e 迁至 **Ch21**（因 4e 把并查集提前到 Ch19、基础图算法为 Ch20）；节内标题不变。引用时用 4e 章号。
- 实测：Kruskal 与 Prim 的 MST 总权在 300 组随机连通图上逐一相等，且与 scipy `minimum_spanning_tree` 一致；见文末实测汇总。
- 成熟度：经典，稳定，无版本漂移。

## 11.2 单源最短路

### 11.2.1 最短路问题、松弛与最优子结构

给一个**带权有向图** G=(V,E) 和源点 s，**单源最短路（single-source shortest paths）**问题要对每个顶点 v 求出从 s 到 v 的最短路权重 δ(s,v)（一条路径的权重 = 其上各边权之和；δ 取所有 s→v 路径里的最小值，不可达则 δ=∞）。

所有单源最短路算法都靠同一个原子操作——**松弛（relaxation）**。为每个顶点维护一个「当前已知最短路上界」v.d（初始 s.d=0、其余 =∞）和前驱 v.π。松弛一条边 (u,v) 就是问：「绕道 u 会不会让到 v 更近？」

```
INITIALIZE-SINGLE-SOURCE(G, s)
  for 每个顶点 v ∈ V:  v.d = ∞;  v.π = NIL
  s.d = 0

RELAX(u, v, w)
  if v.d > u.d + w(u,v)
    v.d = u.d + w(u,v)
    v.π = u
```

松弛的判据式（本问题的灵魂）单独成行：

v.d > u.d + w(u,v)  ⟹  更新 v.d = u.d + w(u,v)

底层依据是**三角不等式**（对任意边 (u,v)）：

δ(s,v) ≤ δ(s,u) + w(u,v)

以及**最优子结构**：最短路的任意子路径也是最短路（这是大主题7 DP 与本章共享的性质）。还有两个会反复用到的正确性引理：**收敛性质**——若某刻 u.d=δ(s,u)，其后再松弛 (u,v) 就会令 v.d=δ(s,v) 并从此不变；**路径松弛性质**——若沿某条最短路 s→v 的边按其在路径上的先后顺序各松弛一次（中间穿插别的松弛也无妨），则结束时 v.d=δ(s,v)。

初学者直觉：v.d 是「到 v 的当前最好估计」，永远是真值 δ 的**上界**，松弛只会把它往下压、绝不压过头（这叫「不越界」）。不同算法的差别只在**用什么顺序、松弛几次每条边**——顺序对了，估计就收敛到真值。易错点：**负权边**允许存在（只要没有从 s 可达的负权环），但**负权环**会让「最短路」无下界（可以绕环无限减小），此时 δ 无定义。谁能处理负权、谁不能，正是下面三个算法的分水岭。

### 11.2.2 Bellman–Ford 算法

Bellman–Ford 解**一般**单源最短路：允许负权边，并能**检测负权环**。做法极朴素——把**每一条边都松弛，重复 |V|−1 轮**；之后再扫一遍，若还能松弛成功，就说明存在从 s 可达的负权环。

```
BELLMAN-FORD(G, w, s)
  INITIALIZE-SINGLE-SOURCE(G, s)
  for i = 1 to |V| − 1:
    for 每条边 (u,v) ∈ E:  RELAX(u, v, w)
  for 每条边 (u,v) ∈ E:            # 第 |V| 轮检测
    if v.d > u.d + w(u,v):  return FALSE   # 存在可达负权环
  return TRUE
```

运行时间单独成行：

O(V·E)

初学者直觉：为什么松弛 |V|−1 轮就够？因为任何最短路径（无环时）至多 V−1 条边；由「路径松弛性质」，第 k 轮结束后，所有「至多 k 条边的最短路」都已求对。所以 V−1 轮覆盖最长的无环最短路。第 |V| 轮如果还能压下去，只可能是负权环在作怪——正是检测负环的原理。易错点：这里必须**每轮把所有边都松弛一遍**（外层 |V|−1 次、内层遍历全部 E），不能只松弛一次；O(V·E) 的两个因子就来自这个双重循环。

### 11.2.3 DAG 上的单源最短路

如果图是**有向无环图（DAG）**，可以先**拓扑排序**（大主题10 的 10.4），再**按拓扑序**把每个顶点的出边各松弛一次即可，一遍搞定。

```
DAG-SHORTEST-PATHS(G, w, s)
  拓扑排序 G 的顶点
  INITIALIZE-SINGLE-SOURCE(G, s)
  for 每个顶点 u（按拓扑序）:
    for 每个邻居 v ∈ Adj[u]:  RELAX(u, v, w)
```

运行时间单独成行：

Θ(V + E)

初学者直觉：拓扑序保证「当轮到松弛 u 的出边时，u.d 已经是终值 δ(s,u)」——因为所有能到 u 的边都排在 u 前面、已松弛过。于是每条边只需松弛一次，比 Bellman–Ford 快得多。它照样能处理**负权边**（DAG 里根本不可能有环，自然没有负权环）。用途举例：任务调度求关键路径、PERT 图。易错点：只对无环图成立；一旦有环就不能拓扑排序，得退回 Bellman–Ford 或 Dijkstra。

### 11.2.4 Dijkstra 算法

Dijkstra 解**非负权**单源最短路（要求所有 w(u,v) ≥ 0），比 Bellman–Ford 快。它是贪心的：维护一个「已确定最短路的顶点集合 S」，反复从「未确定集合」里取出 d 值最小的顶点 u，把它加入 S（此刻 u.d 已是终值），再松弛它的所有出边。

```
DIJKSTRA(G, w, s)
  INITIALIZE-SINGLE-SOURCE(G, s)
  S = ∅
  Q = V                          # 最小优先队列，按 d
  while Q ≠ ∅:
    u = EXTRACT-MIN(Q)
    S = S ∪ {u}
    for 每个邻居 v ∈ Adj[u]:
      RELAX(u, v, w)             # 若 v.d 减小则 DECREASE-KEY(Q, v)
```

用**二叉堆**实现优先队列时运行时间（单独成行）：

O((V + E) lg V) = O(E lg V)（连通图）

用简单数组实现是 O(V²)（稠密图反而更划算）；用 Fibonacci 堆是 O(V lg V + E)（→ L6-01 #01，本课不展开）。

初学者直觉：Dijkstra 每次「锁定」当前 d 最小的未定点——因为边权非负，绕更远的点回来只会更贵，所以这个最小值不可能再被压低，可以放心定案（这一步正是「贪心选择性质」，靠边权非负保证）。这也点明了它的**硬前提**：**边权必须非负**。若有负权边，一个已锁定的点后来可能被一条负权边压得更小，贪心就错了——这时只能用 Bellman–Ford。易错点：不要把 Dijkstra 用到含负权边的图上（哪怕没有负权环，结果也可能错）；这是最常见的踩坑。与无权图的 BFS 对照：BFS（大主题10 的 10.2）是「所有边权=1」时 Dijkstra 的特例。

#### 来源与时效
- 锚点：CLRS 4th ed (2022) Ch22——§22.1「The Bellman-Ford algorithm」（O(V·E)、负环检测）、§22.2「Single-source shortest paths in directed acyclic graphs」（拓扑序松弛、Θ(V+E)）、§22.3「Dijkstra's algorithm」（非负权、二叉堆 O(E lg V)、Fibonacci 堆 O(V lg V+E)）、§22.5「Proofs of shortest-paths properties」（三角不等式、收敛/路径松弛性质）；核实 2026-07-26。
- 锚点：MIT 6.046J (Spring 2015) syllabus「Graph Algorithms」列 Bellman–Ford / Dijkstra；核实 2026-07-26。
- 二手交叉核对（补充、非承重）：Wikipedia「Bellman–Ford algorithm」（O(V·E)、负环检测）、「Dijkstra's algorithm」（非负权前提、二叉堆 O((V+E)log V)、Fibonacci 堆 O(V log V+E)）与 walkccc.me/CLRS 均与 CLRS 一致，无分歧。
- 版本/章号提示：单源最短路 CLRS **3e 为 Ch24**，4e 为 **Ch22**；节标题不变。
- 实测：非负权随机图上，自实现 Dijkstra 与 Bellman–Ford 的距离数组逐点相等，且与 scipy `dijkstra` 一致（300 组）；负权环图上 Bellman–Ford 正确报 FALSE、含负权边但无环的图上正确报 TRUE；见文末实测汇总。
- 成熟度：经典，稳定，无版本漂移。

## 11.3 差分约束系统

### 11.3.1 差分约束与线性规划特例

**差分约束系统（system of difference constraints）**是一类特殊的线性规划：要找一组实数 x₁,…,xₙ，满足若干形如

x_j − x_i ≤ b_k

的约束（每个不等式恰好含两个变量、系数为 +1 与 −1）。它是线性规划 A·x ≤ b 的特例（矩阵 A 每行恰好一个 +1 和一个 −1）。典型应用：给若干事件安排发生时刻，约束形如「事件 j 至多比事件 i 晚 b 个单位」。

核心结论：差分约束系统的可行性与解，可以**完全化归为单源最短路**——不必动用通用 LP 求解器。

初学者直觉：把「x_j − x_i ≤ b」和最短路的三角不等式「δ(s,j) ≤ δ(s,i) + w(i,j)」并排看——只要令 w(i,j)=b，两者形状一模一样。所以最短路的 δ 值天然就是一组满足全部约束的解。这就是把约束问题「翻译」成图问题的巧劲。

### 11.3.2 约束图与用 Bellman–Ford 求解

构造**约束图（constraint graph）**：每个变量 x_i 对应一个顶点 v_i；对每条约束 x_j − x_i ≤ b_k，加一条有向边

(v_i, v_j)，权重 w(v_i, v_j) = b_k

再加一个**超级源点** v₀，向每个 v_i 连一条权重为 0 的边（保证所有顶点可达）。然后从 v₀ 跑 Bellman–Ford：

- 若图**无负权环**：令 x_i = δ(v₀, v_i)，这组值满足全部约束，是一个**可行解**。
- 若图**有负权环**（Bellman–Ford 返回 FALSE）：系统**无解**（不可行）。

初学者直觉：为什么 x_i = δ(v₀, v_i) 一定满足每条约束？因为对边 (v_i, v_j)，最短路的三角不等式给出 δ(v₀,v_j) ≤ δ(v₀,v_i) + b_k，移项就是 x_j − x_i ≤ b_k——正是那条约束。负权环则意味着一圈约束加起来推出「某个量严格小于自己」，逻辑上不可能同时满足，故无解。一个好用的性质：若 (x₁,…,xₙ) 是解，则给每个变量同加一个常数 d 后 (x₁+d,…,xₙ+d) 仍是解（约束只关心差值），所以解不唯一、可平移。运行时间就是 Bellman–Ford 的 O(V·E)。

#### 来源与时效
- 锚点：CLRS 4th ed (2022) §22.4「Difference constraints and shortest paths」——差分约束定义、约束图构造（超级源点 + 0 权边）、x_i=δ(v₀,v_i) 为可行解、负权环⟺不可行、解可整体平移；核实 2026-07-26。
- 二手交叉核对（补充、非承重）：Wikipedia「Bellman–Ford algorithm」应用小节与多所高校讲义均确认「差分约束经约束图 + Bellman–Ford 求解、负环即不可行」，与 CLRS 一致，无分歧。
- 实测：对一组 8 条约束的示例系统，取 x_i=δ(v₀,v_i) 得解后代回逐条验证 x_j−x_i≤b 全部成立、且负环标志为 False；见文末实测汇总。
- 成熟度：经典，稳定，无版本漂移。

## 11.4 全对最短路

### 11.4.1 全对问题与最短路的矩阵乘法法

**全对最短路（all-pairs shortest paths）**要一次算出**每一对**顶点 (i,j) 之间的最短路权重 δ(i,j)，结果是一个 V×V 的距离矩阵。输入通常用邻接矩阵 W 给出（W[i][j]=边 (i,j) 权，无边为 ∞，对角 0）。允许负权边，但不允许负权环。

一个思路是把它做成**类矩阵乘法的递推**。定义 L⁽ᵐ⁾[i][j] = 「至多用 m 条边的 i→j 最短路权」，则

L⁽ᵐ⁾[i][j] = min_{k} ( L⁽ᵐ⁻¹⁾[i][k] + W[k][j] )

这正是普通矩阵乘法把「乘」换成「加」、把「求和 Σ」换成「取最小 min」的**（min, +）矩阵乘法**（也叫热带半环乘法）。逐条边延伸要算到 m=V−1，朴素做法是 Θ(V⁴)；但因为只关心最终的 L⁽ⱽ⁻¹⁾，可用**反复平方（repeated squaring）**（L⁽²ᵐ⁾ 由 L⁽ᵐ⁾「自乘」得到），把轮数从 V 降到 lg V：

Θ(V³ lg V)

初学者直觉：把「一步步延长路径」看成「反复做一种特殊的矩阵乘法」，就能借矩阵幂的反复平方技巧加速。这一节的主要价值是**建立视角**（最短路 ↔ 矩阵幂 ↔ 半环），它在实践中不是最快的（下面的 Floyd–Warshall 更简单更快），但揭示了漂亮的代数结构。易错点：这里的「乘法」不是普通乘法，是 (min,+)；别套用普通矩阵乘法的性质（如没有减法/逆）。

### 11.4.2 Floyd–Warshall 算法

Floyd–Warshall 是全对最短路的**动态规划**解法，简洁到只有三重循环。它按「允许经过的中转顶点集合」逐步放开来递推：设 d⁽ᵏ⁾[i][j] = 「只允许用编号 ≤ k 的顶点做中转时，i→j 的最短路权」，递推式单独成行：

d⁽ᵏ⁾[i][j] = min( d⁽ᵏ⁻¹⁾[i][j], d⁽ᵏ⁻¹⁾[i][k] + d⁽ᵏ⁻¹⁾[k][j] )

```
FLOYD-WARSHALL(W)
  n = W.rows;  D = W                       # D⁽⁰⁾ = W
  for k = 1 to n:
    for i = 1 to n:
      for j = 1 to n:
        D[i][j] = min( D[i][j], D[i][k] + D[k][j] )
  return D
```

运行时间单独成行：

Θ(V³)

初学者直觉：第 k 轮在问「让 k 号点当中转，i→j 会不会更近？」——要么不走 k（沿用上一轮的 d[i][j]），要么走 k（i→k 再 k→j，两段都只用编号 <k 的中转）。把 k 从 1 扫到 V，就依次「解锁」每个可能的中转点，最后得到允许任意中转的真最短路。它**能处理负权边**（无负权环即可）；还能顺带检测负权环——若某个 d[i][i] 变成负数，就说明存在负权环。稍加改造（记录后继或用逻辑 OR/AND 代替 min/+）还能求**传递闭包**（谁能到谁）。易错点：三重循环里 **k 必须在最外层**；把 k 放到内层是最经典的错误，会算出错的结果。

### 11.4.3 Johnson 算法

Johnson 算法专为**稀疏图**（E 远小于 V²）优化全对最短路。核心巧思是**重赋权（reweighting）**：先想办法把所有边权变成非负，再从每个顶点跑一次快速的 Dijkstra（Dijkstra 只吃非负权）。步骤：

1. 加一个**超级源点** q，向每个顶点连一条 0 权边；从 q 跑一次 Bellman–Ford 得到势函数 h(v)=δ(q,v)（若报负权环，直接判定整图有负权环、终止）。
2. 对每条边重赋权（式子单独成行）：

w'(u,v) = w(u,v) + h(u) − h(v)  ≥ 0

3. 用新权 w' 从**每个**顶点各跑一次 Dijkstra，得到 δ'。
4. 还原真距离（式子单独成行）：

δ(u,v) = δ'(u,v) − h(u) + h(v)

用 **Fibonacci 堆**做 Dijkstra 的优先队列时运行时间（单独成行）：

O(V² lg V + V·E)

（用二叉堆则为 O(V·E lg V)。）

初学者直觉：重赋权像给每个顶点标一个「海拔」h(v)，把每条边的权换成「考虑海拔落差后的等效权」。关键是这套换算**不改变任何两点间的最短路径本身**（只是给每条路径的总权整体加了 h(起点)−h(终点)，同一对起终点的所有路径都加同一个量，谁最短不变），却能把所有边权抬成非负——于是就能对每个源点用又快又稳的 Dijkstra。选它的判据：稀疏图上 O(V² lg V + V·E) 优于 Floyd–Warshall 的 Θ(V³)；稠密图（E≈V²）则 Floyd–Warshall 更简单划算。易错点：重赋权用的 h 必须来自能处理负权的 Bellman–Ford，不能用 Dijkstra 去算 h（会循环依赖）。

#### 来源与时效
- 锚点：CLRS 4th ed (2022) Ch23——§23.1「Shortest paths and matrix multiplication」（(min,+) 递推、反复平方 Θ(V³ lg V)、朴素 Θ(V⁴)）、§23.2「The Floyd-Warshall algorithm」（DP 递推 d⁽ᵏ⁾、Θ(V³)、传递闭包、负环检测）、§23.3「Johnson's algorithm for sparse graphs」（重赋权 w'=w+h(u)−h(v)、Fibonacci 堆 O(V² lg V+V·E)）；核实 2026-07-26。
- 二手交叉核对（补充、非承重）：Wikipedia「Floyd–Warshall algorithm」确认 Θ(V³)、DP、可处理负权边（无负环）、对角负值检测负环；「Johnson's algorithm」确认 Fibonacci 堆下 O(V² log V + V·E)、四步（加源点→Bellman–Ford 求 h→重赋权→逐点 Dijkstra）——均与 CLRS 一致，无分歧。
- 版本/章号提示：全对最短路 CLRS **3e 为 Ch25**，4e 为 **Ch23**；节标题不变。walkccc.me/CLRS 页面按 3e 编号（Ch23/24/25 = MST/单源/全对），与 4e（Ch21/22/23）差 2；本报告一律用**官方 4e TOC 核定的 4e 章号**（见文末来源清单），引用勿混。
- 实测：随机有向图（含负权边、无负权环）上，自实现 Floyd–Warshall 与 Johnson 的全对距离矩阵逐元素相等，且与 scipy `shortest_path(method='J')` 一致；见文末实测汇总。
- 成熟度：经典，稳定，无版本漂移。

## 实测汇总（可选补充，不替代多来源比对）

环境：Python 3.11.15 / numpy 2.4.6 / scipy 1.17.1 / sympy 1.14.0 / Linux 6.18.5 x86_64（基线串 Py3.11.15/np2.4.6/gcc13.3 @2026-07-25）。脚本仅存于仓库外 scratchpad，跑完即清。以下为真实输出。

对拍要点（完整脚本已清理，关键逻辑）：MST 用并查集实现 Kruskal、二叉堆实现 Prim；单源最短路自实现 Bellman–Ford（含第 |V| 轮负环检测）、DAG 松弛、二叉堆 Dijkstra；差分约束按约束图 + 超级源点 + Bellman–Ford 求解并代回验证；全对最短路自实现 Floyd–Warshall（k 在最外层）与 Johnson（Bellman–Ford 求 h → 重赋权 → 逐点 Dijkstra）。独立对拍基准为 scipy.sparse.csgraph 的 `minimum_spanning_tree`、`dijkstra`、`shortest_path(method='J')`。随机图构造时对同一 (u,v) 只保留最小权边（与「取最小平行边」的最短路/ MST 语义一致，避免 scipy 对重复坐标求和造成的伪差异）。

真实输出：

```
[MST] Kruskal==Prim==scipy over 300 random graphs: True
[SSSP] Dijkstra==Bellman-Ford==scipy (non-neg) over 300 graphs: True
[neg-cycle] detected on cyclic-neg graph: True; false on neg-edge-no-cycle graph: True
[diff-constraints] solution x={1: 0, 2: 0, 3: -5, 4: -4, 5: -1}; satisfies all constraints: True; infeasible-flag(neg cycle)=False
[APSP] Floyd-Warshall==Johnson==scipy over random graphs (no neg cycle): True
```

读数说明：Kruskal 与 Prim 的 MST 总权在 300 组随机连通图上完全相等且与 scipy 一致（11.1，两条贪心路线殊途同归）；非负权图上 Dijkstra、Bellman–Ford、scipy 三方距离逐点相等（11.2，非负权前提下等价）；Bellman–Ford 在含可达负权环的图上正确报告存在负环、在「有负权边但无负环」的图上正确返回可解（11.2.2 负环检测）；差分约束示例取 x_i=δ(v₀,v_i) 后代回，8 条约束全部满足、负环标志为假（11.3 化归正确）；随机有向图（含负权边、无负环）上 Floyd–Warshall 与 Johnson 的全对距离矩阵逐元素相等且与 scipy Johnson 法一致（11.4，两种全对算法结果一致）。这些实测佐证了正文的正确性主线，但复杂度界与前提条件仍以上述多来源比对为准。

## 来源清单（一手锚点，全课通用 + 本报告专用）

- CLRS《Introduction to Algorithms》4th ed (2022, MIT Press)，书页 https://mitpress.mit.edu/9780262046305/introduction-to-algorithms/ ；本报告章节号经**官方 4e 目录 PDF** https://mitp-content-server.mit.edu/books/content/sectbyfn/books_pres_0/11599/4e_toc.pdf 逐条核定：Ch21 Minimum Spanning Trees（21.1 Growing a minimum spanning tree / 21.2 The algorithms of Kruskal and Prim）、Ch22 Single-Source Shortest Paths（22.1 The Bellman-Ford algorithm / 22.2 …in directed acyclic graphs / 22.3 Dijkstra's algorithm / 22.4 Difference constraints and shortest paths / 22.5 Proofs of shortest-paths properties）、Ch23 All-Pairs Shortest Paths（23.1 Shortest paths and matrix multiplication / 23.2 The Floyd-Warshall algorithm / 23.3 Johnson's algorithm for sparse graphs）。核实 2026-07-26，4e 为截至该日最新版。
- MIT 6.046J Design & Analysis of Algorithms (Spring 2015) syllabus，https://ocw.mit.edu/courses/6-046j-design-and-analysis-of-algorithms-spring-2015/pages/syllabus/ ，「Graph Algorithms / Greedy Algorithms」模块。核实 2026-07-26。
- 二三手（非承重、仅交叉核对复杂度界与算法步骤）：Wikipedia 各算法条目（Kruskal / Prim / Bellman–Ford / Dijkstra / Floyd–Warshall / Johnson）、walkccc.me/CLRS（注意其页面用 3e 章号 Ch23/24/25，与本报告 4e 章号 Ch21/22/23 相差 2，节标题一致）。
