# L6-04·大主题04-6 连接与聚合算法的内核实现

> 基线 Py3.11.15/np2.4.6/gcc13.3 @2026-07-25 ｜核实日期：2026-08-01 ｜先修：L4-03·03-7 查询执行（连接算法原理：嵌套循环/排序归并/哈希连接的 relational 语义，聚合的 group-by 语义）｜一手锚点：CMU 15-721 Fall 2025 L09–L10（Joins / Aggregations，https://www.cs.cmu.edu/~15721-f25/）＋ Kitsuregawa et al. GRACE hash join（1983）＋ DeWitt et al. hybrid hash join（1984）＋ Blanas et al.（SIGMOD 2011）＋ Balkesen et al.（ICDE 2013 / VLDB 2014）＋ Kim et al. Sort-vs-Hash（VLDB 2009）＋ Graefe《Query Evaluation Techniques》（1993）＋ DuckDB / PostgreSQL 官方文档 ｜成熟度：连接/聚合的核心算法与缓存感知实现 GA（1980s–2010s 定型、跨实现口径稳定）；具体引擎的分区数/内存阈值/自适应策略 ⚙演进快·锚版本·随时变

本主题承接查询执行中的连接与聚合算法原理，只讲"怎么把它们实现得快"——同样是哈希连接，为什么天真写法在大表上会因为 cache/TLB miss 慢几倍，工程上怎么用分区、缓存感知布局、并行、溢出处理把它救回来。原理层面（哪种算法算的是什么关系代数结果）默认已掌握，这里聚焦内核工程：内存层次、并行度、当数据放不下内存时怎么办。连接与聚合的核心实现技术在 1980 年代到 2010 年代之间已基本定型、经典论文口径一致，可放心作定论；只有各引擎具体的分区数、内存阈值、自适应切换点在持续演进，凡涉及具体默认值处都锚定来源版本或标「待核」。

---

## 04-6.1 hash join 实现：radix-partitioned vs non-partitioned、缓存感知

### 04-6.1.1 内存哈希连接的两阶段骨架

内存哈希连接（in-memory hash join）分两个阶段：build 阶段扫描较小的一张表（build side，通常是连接两表中较小者），把它的每一行按连接键插入一张哈希表；probe 阶段扫描另一张表（probe side），对每一行用同样的哈希函数在哈希表里查找匹配，命中就输出连接结果。

```
build 阶段:  for r in R(小表):  HT[hash(r.key)] += r       构建哈希表
probe 阶段:  for s in S(大表):  for r in HT[hash(s.key)]:   探测并输出匹配
                 if r.key == s.key: emit(r, s)
```

之所以选小表做 build side，是因为哈希表要常驻内存，越小越好装下、越可能整个塞进 CPU 缓存。哈希连接的理论复杂度是两表大小之和的线性级别，远优于嵌套循环连接的乘积级别，这是它在等值连接（equi-join，连接条件是"键相等"）上成为主力的根本原因。初学者要记住一个前提：哈希连接只适用于等值连接；连接条件是范围或不等式时用不了哈希，得回退到排序归并或嵌套循环。

### 04-6.1.2 缓存感知问题：随机探测导致的 cache / TLB miss

天真哈希连接的性能杀手不在指令数，而在内存访问模式。probe 阶段对每一行都要按哈希值跳到哈希表的一个几乎随机的位置去查，当哈希表大到装不进 CPU 末级缓存（LLC）时，几乎每次探测都是一次缓存未命中（cache miss），要从主存拉一条缓存行进来；哈希表更大时，连虚拟地址翻译用的 TLB（Translation Lookaside Buffer）也频繁未命中，每次探测可能触发一次页表遍历。

这就是"缓存感知"（cache-conscious）成为哈希连接实现核心议题的原因：算法的渐进复杂度是线性的，但常数被内存延迟撑大了几倍。一次 LLC 未命中的主存访问延迟通常是命中缓存的几十倍到上百倍（具体数值随平台而异，此处不锚定），当探测阶段几乎全是未命中时，CPU 大部分时间在等内存而非计算。理解这一点，才能理解后面所有"分区"技巧的动机：它们不改变算法算的东西，只是重排访问模式，让每次探测落在缓存里。

### 04-6.1.3 GRACE 与 hybrid hash join：分区把大表拆成能装进内存的小块

当 build 表本身就大到装不进内存时，用 GRACE hash join：先用哈希函数把两张表都按连接键分成 N 个分区（partition），保证同一个键的两表行一定落进同一对分区；然后逐对分区做内存哈希连接，一次只处理一对，只要单个分区能装进内存即可。

```
分区阶段:  R --hash1--> R0 R1 ... R(N-1)   (落盘/落内存的 N 个桶)
           S --hash1--> S0 S1 ... S(N-1)   (同一 hash1，保证配对)
连接阶段:  for i in 0..N-1:  内存哈希连接(Ri, Si)   一次只加载一对分区
```

GRACE hash join 由 Kitsuregawa 等人在 1983 年为日本的 GRACE 数据库机提出，思想是"分而治之：把放不下的问题切成一堆放得下的小问题"。DeWitt 等人 1984 年提出的 hybrid hash join 是它的优化：分区时把第 0 号分区直接留在内存里当哈希表用，扫描时属于第 0 分区的行立即参与连接、无需落盘再读回，只有其余分区落盘。当内存比 build 表小一点点时，hybrid 相比 GRACE 能省掉大量 I/O，因此成为磁盘时代关系数据库的标准哈希连接实现。这里初学者容易混淆：GRACE/hybrid 的分区最初是为了"数据放不下内存"（省 I/O），而下一节的 radix 分区是为了"哪怕全在内存里也要贴合缓存"（省 cache miss）——两者形式相似、目的不同。

### 04-6.1.4 radix-partitioned join：多趟 radix 分区与 fan-out 约束

radix-partitioned hash join 把分区思想搬到纯内存场景：先按连接键哈希值的若干个比特（radix bits）把两表切成许多小分区，让每个分区连同它的哈希表都能装进 CPU 缓存，再逐分区做内存哈希连接。这样 build 与 probe 都发生在缓存内，几乎消灭了 04-6.1.2 里的随机主存访问。

一趟分区能切出的分区数（fan-out）受 TLB 条目数和缓存限制——分区就是往许多输出缓冲区分散写（scatter），若同时活跃的输出缓冲区太多，写操作本身又开始 TLB/cache 未命中，分区这一步就先慢了。因此高分区数要用多趟 radix 分区（multi-pass radix partitioning）：每趟只看键的一小段比特、切出适中数量的分区，多趟叠加达到很高的总分区数，同时每趟的 fan-out 都控制在硬件友好的范围内。

```
一趟 radix (fan-out 太大 → 输出缓冲区超过 TLB/cache → scatter 也慢)
多趟 radix:  第1趟 看 bits[0:B] → 切 2^B 个分区
             第2趟 对每个分区再看 bits[B:2B] → 再切 2^B
             两趟共 2^(2B) 个小分区，但每趟 fan-out 只有 2^B
```

radix join 由 Manegold、Boncz、Kersten 等人在 MonetDB 语境下提出（"cache-conscious" / radix cluster 系列工作），是"硬件感知连接"这条线的奠基。它引入了额外的分区扫描开销，换取连接阶段几乎全缓存命中——是否划算取决于硬件与数据规模，这正是下一节争论的焦点。单个分区目标大小、fan-out 的具体比特数是随硬件调的参数，此处不锚定具体数值（待核，随平台）。

### 04-6.1.5 no-partitioning（共享哈希表）并行连接与 SMT

no-partitioning hash join（也叫 shared hash table join）走相反路线：不做任何分区，所有线程 build 阶段并发往同一张共享哈希表里插，probe 阶段并发查同一张表。它接受"探测是随机主存访问、会缓存未命中"这个事实，转而用大量并发线程把内存延迟藏起来——一个线程等内存时，CPU 的同时多线程（SMT，即超线程）切去执行另一个线程，让内存访问的延迟被计算重叠掩盖。

它的优点是实现简单、对数据分布不敏感（分区法遇到数据倾斜时某些分区会超大、拖慢整体），且省掉了分区这一整趟扫描与物化。Blanas 等人（SIGMOD 2011）系统评测后主张：一个非常简单的 no-partitioning 哈希连接，配合 SMT 硬件，就已经很有竞争力，未必需要复杂的硬件感知分区。初学者可以这样把握两条路线：radix 是"把工作重排得贴合缓存"，no-partitioning 是"不重排，用并发把延迟藏掉"。

### 04-6.1.6 radix vs no-partitioning 之争（来源分歧，两边都记）

这两条路线孰优是文献里一场有据可查的争论，且不同论文结论不一致，这里两边都记、点明分歧。

Blanas 等人（SIGMOD 2011）在 x86 上测得 no-partitioning 与 radix 差距很小，主张简单的 no-partitioning 已足够有竞争力，其优势来自与 SMT 的良好配合。Balkesen 等人（ICDE 2013）反驳：当两种实现都被同等程度地针对硬件优化后，硬件感知的 radix join 明显更快，分区连接总体更优。分歧的根源在于"是否两边都做到了同等的硬件优化"以及测试的机器/数据规模不同——这说明这类微基准结论对实现质量和硬件极其敏感，不能脱离条件下断言"哪个一定更快"。

更近的工程视角（如 Bandle 等人"To Partition, or Not to Partition"一类在真实系统里的研究）进一步指出：在一个完整数据库系统里（而非孤立微基准），是否分区还要看它和其余算子、并行调度、内存管理的耦合，答案往往是"看情况、甚至自适应地选"。因此现代引擎的实用取向不是二选一站队，而是根据数据规模、倾斜程度和硬件在两者间自适应选择。

#### 来源与时效
- CMU 15-721 Fall 2025 L09（Joins）https://www.cs.cmu.edu/~15721-f25/ ｜核实 2026-08-01
- Kitsuregawa, Tanaka, Moto-oka, "Application of Hash to Data Base Machine and Its Architecture", New Generation Computing, 1983（GRACE hash join 原始提出）｜核实 2026-08-01
- DeWitt, Katz, Olken, Shapiro, Stonebraker, Wood, "Implementation Techniques for Main Memory Database Systems", SIGMOD 1984（hybrid hash join）｜核实 2026-08-01
- Manegold, Boncz, Kersten, "Optimizing Main-Memory Join on Modern Hardware", IEEE TKDE 2002（radix cluster / cache-conscious join）｜核实 2026-08-01
- Blanas, Li, Patel, "Design and Evaluation of Main Memory Hash Join Algorithms for Multi-core CPUs", SIGMOD 2011（no-partitioning 主张一方）｜核实 2026-08-01
- Balkesen, Teubner, Alonso, Özsu, "Main-Memory Hash Joins on Multi-Core CPUs: Tuning to the Underlying Hardware", ICDE 2013（radix 明显更快一方）https://15721.courses.cs.cmu.edu/spring2019/papers/17-hashjoins/balkesen-icde2013.pdf ｜核实 2026-08-01
- Bandle, Giceva, Neumann, "To Partition, or Not to Partition, That is the Join Question in a Real System", SIGMOD 2021（真实系统里"看情况/自适应"）https://db.in.tum.de/~bandle/papers/bandle-partitionVsNonPartition.pdf ｜核实 2026-08-01
- 冲突项：no-partitioning vs radix 孰优——Blanas 2011（差距小、no-partitioning 够用）vs Balkesen 2013（同等优化下 radix 明显更快）；根因是"是否同等硬件优化 + 机器/数据规模"，结论对条件极敏感，现代取向为自适应。
- ⚙演进快·锚版本：具体 fan-out 比特数、单分区目标大小、内存哈希表实现（开放寻址 vs 链式）随引擎/硬件而变，此处不锚定具体数值（待核）。

## 04-6.2 join 变体：多路 join、semi/anti、runtime/bloom filter 下推

### 04-6.2.1 semi-join 与 anti-join 的实现

semi-join（半连接）只判断左表的行"在右表有没有匹配"，有就输出左表这一行（且只输出一次），不拼接右表列；anti-join（反半连接）相反，输出左表中在右表"没有"匹配的行。它们对应 SQL 里的 `EXISTS` / `IN` 与 `NOT EXISTS` / `NOT IN`。

实现上仍用哈希表：把右表 build 成哈希表，probe 左表时，semi-join 一旦查到匹配就输出该左行并停止继续找它的其他匹配（避免重复输出），anti-join 则是探测完整张右表哈希表都没匹配才输出。相比普通 inner join，semi/anti 的好处是右表只需存键、不需存其余列（因为不拼接输出），哈希表更小、更易进缓存。易错点是 `NOT IN` 遇到 NULL 的三值逻辑：右表含 NULL 时 `NOT IN` 的语义会变，anti-join 实现必须特殊处理 NULL，否则结果错——这是优化器和执行器都要小心的经典坑。

### 04-6.2.2 outer join 与 mark join

left outer join（左外连接）要求左表每一行至少输出一次：有匹配就正常拼接，无匹配则拼接右表列为 NULL。哈希连接实现 left outer 时，通常让左表做 probe side，probe 每一行时若哈希表无匹配就补 NULL 输出；若左表做 build side，则要在哈希表每个条目上记一个"是否被匹配过"的标记位，probe 结束后再扫一遍哈希表，把从未被标记命中的 build 行补 NULL 输出。

mark join 是一种把"是否匹配"物化成一个布尔标记列的连接变体，常用于把子查询（尤其带 NULL 语义的 `IN` / `EXISTS`）改写成连接后仍保持正确的三值逻辑。它是优化器把相关子查询"去关联化"（decorrelation）后落到执行器的一种实现手段，DuckDB 等引擎用它统一处理这类子查询。初学者只需记住：外连接和这些标记式变体的实现难点不在算法，而在"没匹配的行也要被记住并正确补齐"，需要哈希条目上的标记位或额外一趟扫描。

### 04-6.2.3 多路 join：左深流水线与哈希表复用

多路连接（连接三张以上的表）在执行器里通常不是一次性多表哈希，而是拆成一串二元哈希连接，按优化器定的连接顺序串成流水线。最常见的形态是左深树（left-deep tree）：把前面连接的结果作为下一个连接的 probe side，每张待加入的表各自 build 成一张哈希表。

```
左深流水线连接 A⋈B⋈C⋈D:
  build HT_B, HT_C, HT_D  (右侧各表各建一张哈希表)
  for a in A:                      A 只扫一遍，流水线穿过所有探测
     for b in HT_B[a.k1]:
        for c in HT_C[b.k2]:
           for d in HT_D[c.k3]: emit(a,b,c,d)
```

最左那张（通常最大的）表只需流式扫一遍，中间结果不必全部物化落地，一行数据被"推着"穿过后续所有探测。多张哈希表可以在 build 阶段一次性都建好常驻内存，probe 阶段共享。这解释了为什么优化器偏好把小表放右侧做 build、大表放最左做流式 probe——哈希表总量要能装下，而流的那一侧不占额外内存。

### 04-6.2.4 runtime filter 与 sideways information passing

runtime filter（运行时过滤器，又称 sideways information passing，SIP）是一种跨算子的动态优化：在连接的 build 阶段，顺便收集 build 表连接键的信息（如取值范围 min/max，或一个近似的成员集合），把它作为一个过滤条件"侧向"传给 probe 侧的扫描算子，让 probe 表在最底层扫描时就把"绝不可能匹配"的行提前扔掉，不必等它们一路走到连接算子才被丢弃。

它的收益在于：连接是选择性的（大量 probe 行最终没有匹配），若能在扫描阶段就用 build 侧的键信息把这些注定失败的行拦下，就省掉了它们后续的探测、甚至省掉从存储读它们的 I/O（下推到存储层时）。min/max 过滤对有序或分区数据尤其有效——一整个数据块的键范围与 build 侧范围不相交时可整块跳过。这类过滤是"动态"的，因为过滤条件在运行时由 build 表实际内容生成，而非查询编译期就固定，因此能捕捉静态优化看不到的选择性。

### 04-6.2.5 bloom filter 下推的实现

bloom filter 下推是 runtime filter 最常用的具体形态：build 阶段把 build 表所有连接键塞进一个 Bloom 过滤器（一个位数组 + 多个哈希函数的近似集合结构），probe 侧扫描时先拿每行的键问 Bloom 过滤器"这个键可能在 build 表里吗"，回答"一定不在"的行直接丢弃、不进连接。

Bloom 过滤器的关键性质是"无假阴性、有假阳性"：它说"不在"就一定不在（可放心丢弃，不会漏结果），说"可能在"则有小概率其实不在（这些漏网行进到连接算子再被正常剔除，只是白走一趟，不影响正确性）。这个不对称正是它能安全下推的原因——丢弃只发生在"确定不匹配"上。工程要点是过滤器大小与哈希函数个数要按 build 表基数调，太小则假阳性率高、过滤效果差，太大则占内存、构建慢；分布式场景里还要把 Bloom 过滤器从 build 节点广播到各 probe 节点，让过滤在扫描/网络传输前就发生，省的是跨网络的数据量。假阳性率的具体取值是按基数与内存预算调的参数，各引擎默认不同（待核，随实现）。

#### 来源与时效
- CMU 15-721 Fall 2025 L09（Joins；semi/anti、runtime filter）https://www.cs.cmu.edu/~15721-f25/ ｜核实 2026-08-01
- Graefe, "Query Evaluation Techniques for Large Databases", ACM Computing Surveys, 1993（连接变体、外连接标记、多路连接流水线的经典综述）｜核实 2026-08-01
- Bloom, "Space/Time Trade-offs in Hash Coding with Allowable Errors", CACM 1970（Bloom filter 原始，无假阴性有假阳性性质）｜核实 2026-08-01
- Ives, Taylor, "Sideways Information Passing for Push-Style Query Processing", ICDE 2008（SIP / runtime filter）｜核实 2026-08-01
- DuckDB 官方文档与博客（mark join / 子查询去关联化、runtime filter 实现）https://duckdb.org/docs/ ｜核实 2026-08-01
- 冲突项：未见核心机制口径分歧。`NOT IN` 的 NULL 语义在 SQL 标准与各实现一致（三值逻辑），差异仅在优化器是否/如何改写。
- ⚙演进快·锚版本：Bloom filter 假阳性率默认、runtime filter 是否下推到存储层、多路连接的自适应重排随引擎版本变（待核）。

## 04-6.3 并行外部排序实现

### 04-6.3.1 外部归并排序回顾：run generation 与 merge

外部排序（external sort）处理"数据大于内存"的排序：分两阶段，run generation（生成初始有序段）阶段尽量填满内存、把一块数据排好序、作为一个有序段（run）写到磁盘，重复直到全部数据变成若干个有序 run；merge（归并）阶段把这些 run 用多路归并合成一个全局有序结果。

```
run generation:  内存装满 → 排序 → 写出 run_0 → 再装 → run_1 → ... run_k
merge:           多路归并 run_0..run_k (每次从各 run 头部取最小) → 全局有序输出
```

之所以要外部排序，是因为排序需要看到全体数据才能定序，而内存放不下。核心代价是磁盘 I/O：每个 run 至少被写一次读一次。当 run 太多、一趟多路归并的扇入（同时打开的 run 数）受内存缓冲限制装不下时，要做多趟归并，I/O 随之翻倍。因此外部排序实现的两个优化方向就是"让初始 run 尽量长（减少 run 数）"和"让归并尽量一趟完成（提高扇入）"。数据库里排序无处不在——`ORDER BY`、`GROUP BY`、排序归并连接、去重、建索引都依赖它。

### 04-6.3.2 缓存与寄存器友好的排序内核

run generation 阶段在内存里排一块数据，用什么排序内核也讲究缓存感知。天真的快速排序对大数组有不错的缓存局部性，但现代 CPU 上更快的做法常是分层的：最底层用排序网络（sorting network，一串固定的比较-交换序列，无分支、可用 SIMD 向量指令并行比较）排很小的块，再用归并把小块拼成大块，让归并尽量在缓存内进行。

这条思路和哈希连接的缓存感知同源：算法复杂度不变，重点是让比较-交换和归并的访问落在缓存/寄存器里、并利用 SIMD 数据并行。排序网络之所以适合 SIMD，是因为它的比较位置在编译期就固定、没有数据依赖的分支，几个元素能被打包进一个向量寄存器一次比较。初学者不必记具体网络结构，只需理解"内存里那一块的排序，也被拆成缓存友好、向量化的小内核"这一层，与 04-6.1 的分区思想是一套哲学。

### 04-6.3.3 并行外部排序：本地排序 + 分区/归并

多核并行外部排序把工作切给多个线程，主流有两条组织方式。一是"本地排序 + 归并"：每个线程各自排一部分数据成本地有序 run，最后做并行多路归并；二是"分区 + 本地排序"：先按键的范围（range）把数据划分成不相交的桶（桶 i 的所有键都小于桶 i+1），每个线程独占排一个桶，桶内有序且桶间有序，拼起来天然全局有序、无需最终归并。

```
分区式并行排序:
  采样估计分位点 → 定出 range 边界 [., k1)[k1,k2)...[km, .)
  scatter: 每行按键落进对应桶 (桶间已有序)
  parallel: 各线程独立排各自的桶  → 顺序拼接即全局有序
```

分区式的难点是负载均衡：要让每个桶大小相近，否则某线程分到超大桶会拖慢整体。而键的分布事先未知，所以要先对数据采样、估计分位点（quantile）来定桶边界——采样定界是并行分区排序的关键工程步骤。这与并行哈希连接遇到的数据倾斜问题是同一类挑战：静态均分假设数据均匀，倾斜时就要靠采样或运行时再平衡来救。归并式则相反，分工天然均衡，代价是最后那趟归并是同步点、并行度受限。两条路各有取舍，实现常按数据规模与倾斜情况选择。

### 04-6.3.4 sort-merge join 与 sort-vs-hash 之争

排序归并连接（sort-merge join）先把两表都按连接键排序，再像拉链一样同步扫过两个有序序列输出匹配。它是哈希连接之外的另一大等值连接实现，且不像哈希连接那样局限于等值——排好序后也能高效做带范围的连接与合并。当输入已经有序（如来自有序索引扫描）时，可省掉排序、直接归并，非常划算。

排序归并 vs 哈希是又一场有记录的长期争论。经典观点认为哈希连接在大表等值连接上通常更快（少一次全排序的开销）；但 Kim 等人（VLDB 2009）在现代多核 + SIMD 硬件上论证：随着核数增多、SIMD 变宽，排序（用向量化排序网络）的可扩展性和硬件亲和性提升，排序归并有望重新追平甚至反超哈希，尤其在 NUMA 大规模并行下。Balkesen 等人（VLDB 2014，"Sort vs. Hash Revisited"）进一步系统对比，结论仍是"看硬件参数与数据规模"，未一边倒。两边都记：哈希在多数当前场景仍是默认主力，但排序归并在结果需要有序、输入已有序、或超大规模 NUMA 并行时有其位置，孰快取决于硬件与负载，不宜绝对化。

#### 来源与时效
- CMU 15-721 Fall 2025 L10（Sorting / Aggregations）https://www.cs.cmu.edu/~15721-f25/ ｜核实 2026-08-01
- Graefe, "Query Evaluation Techniques for Large Databases", ACM Computing Surveys, 1993（外部归并排序、run generation、多路归并的经典综述）｜核实 2026-08-01
- Kim, Sedlar, Chhugani et al., "Sort vs. Hash Revisited: Fast Join Implementation on Modern Multi-Core CPUs", VLDB 2009（SIMD/多核下排序归并追平哈希的论证）｜核实 2026-08-01
- Balkesen, Alonso, Teubner, Özsu, "Multi-Core, Main-Memory Joins: Sort vs. Hash Revisited", VLDB 2014 http://www.vldb.org/pvldb/vol7/p85-balkesen.pdf ｜核实 2026-08-01
- Chhugani et al., "Efficient Implementation of Sorting on Multi-Core SIMD CPU Architecture", VLDB 2008（排序网络 + SIMD 缓存友好排序内核）｜核实 2026-08-01
- 冲突项：sort-merge vs hash join 孰优——经典观点（hash 通常更快）vs Kim 2009 / Balkesen 2014（现代硬件下 sort 可追平/反超，取决于核数/SIMD 宽度/数据规模）；两边均以硬件条件为前提，非绝对结论。
- ⚙演进快·锚版本：具体归并扇入、采样率、SIMD 排序网络宽度随硬件/实现而变（待核）。

## 04-6.4 聚合实现：hash aggregation、两阶段/并行聚合、溢出处理

### 04-6.4.1 hash aggregation 与 sort-based aggregation

分组聚合（`GROUP BY` + 聚合函数）有两条实现路线。hash aggregation（哈希聚合）建一张以分组键为键的哈希表，每读一行就找到它所属分组、在对应的聚合状态（如 sum、count 的累加器）上更新；扫完全部输入后哈希表里每个条目就是一个分组的最终结果。sort-based aggregation（排序聚合）先按分组键排序，让同组的行相邻，再顺序扫一遍、组边界一变就吐出上一组的聚合结果。

```
hash aggregation:  for r in input:  st = HT[r.group_key]; st.update(r)   → 扫完输出各组
sort aggregation:  sort(input by group_key); 顺序扫，遇到组切换就 emit 上一组
```

哈希聚合不需要排序、通常更快，但要把整张分组哈希表放内存（分组数=哈希表条目数）；排序聚合内存友好（一次只需持有当前一组的状态）且输出天然按键有序（若后续要 `ORDER BY` 同一键可省一次排序），但要付一次全排序代价。分组数（基数）小时哈希表小、哈希聚合完胜；分组数极大到哈希表放不下时，排序聚合或"哈希聚合 + 溢出"（见 04-6.4.5）才顶得住。这是优化器要在两者间抉择的核心权衡。

### 04-6.4.2 两阶段并行聚合：partial + final

并行聚合的主力形态是两阶段聚合（partial aggregation + final aggregation）。第一阶段每个线程/分区对自己那份数据先做一次局部聚合，产出"部分聚合结果"（partial states）；第二阶段把各线程产出的、同一分组的部分结果再合并成最终结果。

```
第一阶段 (并行，各线程本地):
  线程1: {A: sum=10, B: sum=5}    线程2: {A: sum=7, C: sum=3}   ...
                     │  各自本地哈希聚合，先把数据缩小
第二阶段 (合并同组部分结果):
  A: 10+7=17    B: 5    C: 3        →  最终结果
```

它之所以高效，是因为第一阶段就把数据量大幅缩小——原始几亿行经本地聚合后，每个线程只剩"分组数"那么多条部分结果，第二阶段要跨线程搬运/合并的数据因此小得多，大大减少了线程间同步与数据交换。关键前提是聚合函数可分解、可结合：sum/count/min/max 天然满足（部分和再相加就是总和）；avg 要拆成 sum 和 count 两个部分状态、最后再相除，不能直接对局部平均再平均——这是初学者最常踩的坑。像 `count(distinct)` 这类不可简单结合的聚合，两阶段要用近似结构（如 HyperLogLog sketch）或退化处理。

### 04-6.4.3 分区并行聚合（radix-partitioned aggregation）

两阶段聚合的第二阶段（合并各线程部分结果）本身也可能成为瓶颈：分组数很大时，跨线程合并同一分组要么争抢共享哈希表、要么搬运大量部分结果。分区并行聚合借用连接里的 radix 思想解决这一点：按分组键的哈希把数据分区，保证同一分组的所有行只落进一个分区，于是每个分区可由一个线程独立完整聚合、分区间无需再合并。

```
按 group_key 哈希分区 → 同一 group 的行必落同一分区
  分区0 → 线程A 完整聚合 (该分区内的组不会出现在别的分区)
  分区1 → 线程B 完整聚合
  ...   分区间无交集 → 无需最终合并，直接拼接
```

它把"共享写竞争"换成"分区扫描 + 无竞争的独立聚合"，与 radix hash join 是同一套缓存/并发感知哲学，也继承同样的软肋：数据倾斜时某分区超大会拖慢。现代内存分析引擎（如 DuckDB）的并行哈希聚合就采用分区式设计以避开共享哈希表的争用。是"两阶段（partial+final）"还是"分区式（无 final）"更好，同样取决于分组基数与倾斜，实现常自适应选择或组合。

### 04-6.4.4 高基数 vs 低基数与自适应聚合

聚合性能对分组基数（distinct 分组数）极其敏感。低基数（分组少，如按国家聚合）时，哈希表极小、常驻缓存，每行更新几乎零成本，两阶段的本地聚合能把数据缩到极小，效率最高。高基数（分组多到接近行数，如按用户 ID 聚合近乎不重复）时，哈希表巨大、放不进缓存甚至放不进内存，本地聚合几乎缩不了数据（每组就一两行），两阶段的第一阶段白忙、还多占内存。

因此不少引擎做自适应聚合：运行时先观察前一批数据的分组命中率/去重率，若发现基数很高、本地预聚合根本缩不动数据，就动态放弃或跳过本地预聚合、直接走分区或直传合并，避免为无收益的预聚合白付哈希表开销。这解释了一个初学者常困惑的现象——同样的 `GROUP BY`，按低基数列聚合飞快、按高基数列聚合可能慢一个量级且吃大量内存：不是算法退化，是基数决定了哈希表能否留在缓存、预聚合能否缩数据。自适应的具体触发阈值是随引擎版本调的内部参数（待核，随实现）。

### 04-6.4.5 溢出（spill）处理

当分组哈希表大到超过分配给它的内存预算时，就要溢出（spill）到磁盘，否则要么内存耗尽崩溃、要么优化器只能保守地根本不敢选哈希聚合。溢出的典型做法是分区式：内存不够时，把一部分分区（连同其部分聚合状态）写到磁盘上的溢出文件，内存里只保留仍在活跃聚合的分区；输入扫完后，再把落盘的分区逐个读回、继续聚合完成。

PostgreSQL 是这条演进的清晰例子：早期版本的哈希聚合不能溢出，一旦优化器估计哈希表会超过 `work_mem` 就干脆不选哈希聚合、改走排序聚合，估错时（低估基数）则可能内存暴涨。PostgreSQL 13 引入了基于磁盘的哈希聚合（disk-based / spill hash aggregation）：内存超过 `work_mem` 乘以 `hash_mem_multiplier` 后进入 spill 模式——不再新建分组，能匹配已有组的行照常累加，会产生新组的行写到逻辑磁带（分区溢出文件）留待后续读回处理。相应地旧的 `enable_hashagg_disk` 参数被 `hashagg_avoid_disk_plan` 取代。

溢出的代价是额外 I/O，因此实现会尽量少溢出（先溢出选中的整批分区而非零散行）、并把 spill 的读写做成顺序 I/O。哈希连接的 build 表放不下时也是同一套溢出机制（回到 GRACE/hybrid 的分区落盘）。初学者要抓住的因果链是：内存有限 → 哈希结构可能放不下 → 要么事先选内存友好的排序法、要么让哈希法学会分区溢出；现代引擎大多选后者，让哈希聚合/连接在内存不足时优雅降级而非失败。`work_mem`、`hash_mem_multiplier` 的默认值随 PostgreSQL 版本而定（⚙演进快·锚版本，此处按 PG13 引入的机制描述，具体默认值待核对应版本）。

#### 来源与时效
- CMU 15-721 Fall 2025 L10（Aggregations）https://www.cs.cmu.edu/~15721-f25/ ｜核实 2026-08-01
- Graefe, "Query Evaluation Techniques for Large Databases", ACM Computing Surveys, 1993（hash vs sort aggregation、两阶段聚合口径）｜核实 2026-08-01
- PostgreSQL 官方文档与 PostgreSQL 13 Release Notes（Disk-based Hash Aggregation；`hash_mem_multiplier`、`hashagg_avoid_disk_plan`）https://www.postgresql.org/docs/ ｜核实 2026-08-01
- PostgreSQL 提交与设计讨论 "Memory-Bounded Hash Aggregation"（Jeff Davis，v13 spill 设计：spill 模式、逻辑磁带、分区溢出）https://www.postgresql.org/message-id/507ac540ec7c20136364b5272acbcd4574aa76ef.camel@j-davis.com ｜核实 2026-08-01
- DuckDB 官方文档（并行哈希聚合、分区式聚合、自适应/溢出）https://duckdb.org/docs/ ｜核实 2026-08-01
- 冲突项：hash vs sort aggregation 无绝对优劣，取决于分组基数与是否需要有序输出，各来源口径一致（以基数为决定因素）。
- ⚙演进快·锚版本：PostgreSQL 哈希聚合溢出为 v13 引入（此前不溢出）；`work_mem` / `hash_mem_multiplier` 默认值、自适应聚合触发阈值随版本与引擎变（待核对应版本）。
