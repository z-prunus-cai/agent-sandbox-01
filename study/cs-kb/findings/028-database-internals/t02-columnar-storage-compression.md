# L6-04·大主题04-2 列存布局与压缩编码

> 基线 Py3.11.15/np2.4.6/gcc13.3 @2026-07-25 ｜核实日期：2026-08-01 ｜先修：L4-03·03-5 物理存储（行存 NSM 元组布局、页/槽结构）｜一手锚点：CMU 15-721 Fall 2025 L02-03（https://www.cs.cmu.edu/~15721-f25/）＋ MonetDB/X100（CIDR 2005）＋ C-Store（VLDB 2005）＋ Abadi et al. 编码-执行集成（SIGMOD 2006）＋ Apache Parquet / Apache ORC 官方格式规范 ｜成熟度：列存核心机制与轻量编码 GA（2005 前后定型、跨实现稳定）；开放格式的版本细节与新编码 ⚙演进快·锚版本·随时变

本主题承接行存物理布局，只讲"列存怎么实现"——把一张表在磁盘/内存里怎么摆、怎么把每一列压小、压小之后查询怎么还能快跑。至于"某个负载到底该选行存还是列存"的选型叙事，不在这里展开。全篇从"为什么把同一列的值堆在一起"出发，逐步拼到工业界的开放列存文件格式。列存的核心机制在 2005 年前后（C-Store、MonetDB/X100）已基本定型、跨实现口径一致，可放心作定论；只有开放格式的具体默认值和新增编码仍在演进，凡涉及具体数值处都锚定来源或标「待核」。

---

## 04-2.1 列式存储模型（DSM）与 PAX 混合布局

### 04-2.1.1 行存 NSM 的回顾与列存的动机

行存（NSM，N-ary Storage Model）把一条元组的所有字段连续摆在一起，一页里一行接一行。这对"取整行"的 OLTP 很友好，但分析型查询往往只读一张宽表的少数几列（如只对一列做聚合），NSM 却被迫把每一行整段读进来，其中大部分字节是本次查询用不到的其他列。

分析负载的这个特征决定了列存的动机：如果把同一列的所有值连续摆在一起，那么"只读某几列"就变成"只读几段连续区域"，磁盘/内存带宽不再浪费在无关列上。更关键的是，同一列的值同质（类型相同、取值分布相近），连续摆放后极易压缩，也极易用一条指令批量处理。一句话对比——NSM 优化"取一整行"，列存优化"扫某几列的很多行"。

初学者常见的误解是把"列存"等同于"压缩"。压缩只是列存带来的红利之一；列存首先是一种"布局"（同列相邻），布局本身就直接省了 I/O，压缩和向量化执行是在这个布局上叠加的进一步收益。

### 04-2.1.2 DSM 分解存储模型

DSM（Decomposition Storage Model）是列存的理论原型：把一张有 n 列的表垂直切成 n 个"子表"，每个子表只存一列。每个子表在概念上是一串 (代理键, 该列值) 的二元组，代理键用来在需要时把同一逻辑行的各列重新对上。

```
逻辑表 T(id, city, amount)
行存 NSM:  [1,"NY",30][2,"LA",50][3,"NY",20] ...   (一行连续)

DSM 三个列子表(概念):
  col_id:     (r1,1)(r2,2)(r3,3) ...
  col_city:   (r1,"NY")(r2,"LA")(r3,"NY") ...
  col_amount: (r1,30)(r2,50)(r3,20) ...
```

DSM 由 Copeland 与 Khoshafian 在 1985 年提出，是"列存"这一想法的最早系统性表述。它讲清了一个关键权衡：垂直切分让单列扫描变快，但把一行的各列"拼回去"（元组重组）需要按代理键做连接，这是列存要付出的固有代价（见 04-2.4）。现代列存的一个核心优化就是去掉显式代理键——只要各列按同样的行序稠密存放，第 i 个位置天然就是同一行的第 i 个值，用位置（隐式偏移）代替显式键，重组从"连接"退化为"按下标取值"。这一点是理解后续所有布局与延迟物化的基础。

### 04-2.1.3 PAX 混合布局

PAX（Partition Attributes Across）是行存与列存之间的折中：仍以行存的"页"为单位（一页装若干行），但在页内部把数据再按列切成一个个连续的小区（minipage），同一列的值在页内相邻。跨页看是行式分块，页内看是列式排布。

```
NSM 页内:  行1全字段 | 行2全字段 | 行3全字段 ...
PAX 页内:  [列A的所有值][列B的所有值][列C的所有值]  (页头记录各minipage偏移)
纯列存:    整列跨很多页连续，一页只含一列的数据
```

PAX 由 Ailamaki 等人在 2001 年提出，最初的目标不是省磁盘 I/O（一页里所有列还是都在），而是省 CPU cache miss：扫某列时，页内该列的值紧挨着，一个 cache line 拉进来全是有用值，不像 NSM 那样每读一个字段就跨过一堆无关字段。它的价值在于"不改变以页为单位的存储/缓冲/恢复框架，就拿到列式的 cache 友好性"。

PAX 的思想深刻影响了今天的开放列存格式：Parquet 的 row group、ORC 的 stripe 本质上都是"先把表按行切成大块，块内再按列存"——这正是 PAX 的放大版（把"页"换成几十上百 MB 的行组）。理解了 PAX，就理解了为什么工业格式几乎都是"行分组 + 组内列存"而非"整列一根到底"：后者在分布式/可切分读取、写入缓冲和 schema 演进上都不好办。

### 04-2.1.4 位置对齐与元组重组的隐式键

在现代列存里，一行的身份由它在各列中的"位置"（position/row id，即第几个值）隐式确定，而不是靠显式存一个键。前提是所有列按同一行序、稠密（无空洞）存放，于是"取第 k 行的所有列"就是"到每一列的第 k 个位置各取一个值"。

这个设计把 DSM 里代价高昂的"按键连接重组"降为"按下标随机访问"，是列存能高效重组元组的关键。它也解释了列存为什么偏爱定长编码和"逻辑删除标记 + 后台整理"而非原地删除：一旦某列中间物理删掉一个值，各列的位置对齐就被破坏，第 k 个位置不再对应同一行。因此列存普遍用位图或删除向量标记被删的行、扫描时跳过，真正的物理紧凑留给后台的压缩/合并阶段完成。初学者要记住：列存的"行"是算出来的（位置），不是存出来的（键）。

#### 来源与时效
- CMU 15-721 Fall 2025 L02–L03（Data Formats / Storage Models 讲）https://www.cs.cmu.edu/~15721-f25/ ｜核实 2026-08-01
- Copeland, Khoshafian, "A Decomposition Storage Model", SIGMOD 1985（DSM 原始提出）｜核实 2026-08-01
- Ailamaki, DeWitt, Hill, Skounakis, "Weaving Relations for Cache Performance", VLDB 2001（PAX 原始提出）｜核实 2026-08-01
- Abadi, Boncz, Harizopoulos et al., "The Design and Implementation of Modern Column-Oriented Database Systems", Foundations and Trends in Databases, 2013（列存综述，位置对齐/重组口径）｜核实 2026-08-01
- 冲突项：未见口径分歧。DSM/NSM/PAX 三者定义在 15-721 讲义与 2013 综述中一致。

## 04-2.2 轻量编码：字典 / RLE / 位图 / Delta / Frame-of-Reference

### 04-2.2.1 字典编码

字典编码把一列里出现的不同取值收集成一张字典（distinct value → 小整数码），列数据本身只存这些小整数码。取值域小（低基数列，如国家、状态、类别）时压缩极为显著：原本每个值可能几十字节的字符串，编码后每个位置只占几个 bit 的整数码。

```
原列:    "NY" "LA" "NY" "SF" "NY" "LA"
字典:    0->"NY" 1->"LA" 2->"SF"
码流:     0    1    0    2    0    1     (再对码流做 bit-packing)
```

字典编码是列存最重要的编码，原因有三：一是压缩比高且稳定；二是它把变长的字符串列变成定长小整数列，让后续所有整数编码（RLE、bit-packing）和 SIMD 批处理都能用上；三是很多谓词能直接在字典上算——例如 `city = 'NY'` 只需在字典里找到 'NY' 的码（比如 0），扫描时整段比较小整数 `code == 0`，完全不碰原字符串。初学者要注意字典编码的适用边界：高基数列（如唯一 ID、自由文本）字典本身会膨胀到和数据一样大，收益消失，这时通常改用别的编码或直接通用压缩。字典是全局（整列一张）还是局部（每个数据块一张）是实现差异——局部字典利于并行和块级自适应，但跨块比较需要重映射。

### 04-2.2.2 游程编码（RLE）

RLE（Run-Length Encoding）把连续重复的相同值压成 (值, 重复次数) 对。它对"已排序或高度聚集"的列极其有效——排序后相同值扎堆，一段几千个相同值只需存一对。

```
原列:  A A A A B B C C C C C
RLE:  (A,4) (B,2) (C,5)
```

RLE 的威力和"排序"深度绑定：C-Store 提出对每张表维护多个不同排序的物理副本（projection），使不同列都能落在某个"排好序因而 RLE 友好"的副本里。这也是列存和排序天然亲和的原因——列存改一行代价大、批量导入为主，正好有机会在写入时排序换取长游程。易错点：RLE 只对"长游程"有利，对随机分布的列反而会因为大量 (值,1) 对而变大；实现上常和字典编码级联（先字典化成小整数、再对小整数码流做 RLE）。此外要区分两种 RLE 变体——纯 (值,长度) 对，和"位置游程"（记录值+起始位置+长度），后者便于按位置随机定位。

### 04-2.2.3 位图编码与 bit-packing

位图编码为一列中的每个 distinct 值维护一个位向量（bitmap），第 i 位为 1 表示第 i 行取该值。对极低基数列（如性别、布尔标志、状态枚举）非常合适：几个位图就覆盖全列，且位图之间可以直接做 AND/OR/NOT 位运算来算组合谓词，一条机器指令处理 64 行。

```
列: 值域 {M,F}
bitmap_M: 1 0 1 1 0   (第0,2,3行是 M)
bitmap_F: 0 1 0 0 1
谓词 sex='M' 直接返回 bitmap_M；'M' OR 缺失... 用位运算组合
```

与位图相邻的是 bit-packing（位紧凑）：如果一列（或字典码流）的值都落在 [0, 2^b) 内，就用固定 b 个 bit 存每个值，而非整字节对齐的 8/16/32 位。它不是"找重复"，而是"砍掉高位的浪费 bit"，几乎总能和字典/FOR 叠用。易错点：位图在高基数列会爆炸（每个 distinct 值一个全长位向量），因此工程上对高基数场景改用压缩位图（如 Roaring bitmap，⚙演进快·锚版本），用分块 + 稀疏/稠密自适应容器来控制体积——但那更多用在索引层而非列数据本身。

### 04-2.2.4 Delta 编码

Delta 编码只存相邻值之间的差，而非绝对值。对单调或缓变的列（时间戳、自增 ID、有序主键、传感器读数）尤其有效：绝对值可能很大（需要 64 位），但相邻差很小（几个 bit 就够），差值再交给 bit-packing 压紧。

```
原列:   1000  1003  1007  1008  1012
Delta:  1000   +3    +4    +1    +4     (首值绝对，其余存差；差值 bit-pack)
```

Delta 常和 FOR、bit-packing 组成一条编码链，是时序/日志类数据的主力。易错点有两个：一是差值可能为负，需要 zigzag 之类的编码把有符号数映射到无符号再 bit-pack；二是纯 delta 是"前缀依赖"的——要取第 k 个值得从头累加，破坏随机访问。工程上用"分块 delta"（每块开头存一个绝对基准值，块内 delta），在压缩比和随机访问/并行解码之间折中，这也是 Parquet 的 DELTA_BINARY_PACKED 等编码采取的做法。

### 04-2.2.5 Frame-of-Reference（FOR）与 patched 变体

FOR（Frame of Reference）为一个数据块选一个参考基准值（通常是块内最小值），块内每个值只存"相对基准的偏移"，偏移小、可 bit-pack。它和 delta 的区别在于：delta 是"相对前一个值"，FOR 是"相对块内一个固定基准值"——FOR 保留了块内随机访问（不必累加），delta 压得更狠但要顺序解。

```
块: 100 102 101 105 103   基准=min=100
FOR偏移: 0  2  1  5  3     (每个偏移只需 3 bit)
```

FOR 的痛点是"离群值"：块里只要有一个远离基准的大值，就把所需 bit 宽度整体抬高，浪费所有其他值的空间。PFOR（Patched Frame-of-Reference，Zukowski 等人 2006）解决它——按"多数值"选一个较窄的 bit 宽度，少数放不下的离群值当作"异常"另存到一个补丁区（exception list），解码时先按窄宽度铺开、再把补丁打回对应位置。PFOR 及其变体（PFOR-DELTA、FastPFOR 等，⚙演进快·锚版本）在整数列压缩里是速度/压缩比俱佳的经典方案，被搜索引擎倒排表和多个列存广泛采用。初学者只需抓住主线：FOR 去掉"共同的高位基准"，PFOR 再容忍少数离群，两者都为了让 bit-packing 的宽度尽量窄。

#### 来源与时效
- CMU 15-721 Fall 2025 L03（Storage / Compression 讲）https://www.cs.cmu.edu/~15721-f25/ ｜核实 2026-08-01
- Abadi, Madden, Ferreira, "Integrating Compression and Execution in Column-Oriented Database Systems", SIGMOD 2006（字典/RLE/位图/FOR 在列存中的系统梳理）｜核实 2026-08-01
- Goldstein, Ramakrishnan, Shaft, "Compressing Relations and Indexes", ICDE 1998（Frame-of-Reference 原始提出）｜核实 2026-08-01
- Zukowski, Heman, Nes, Boncz, "Super-Scalar RAM-CPU Cache Compression", ICDE 2006（PFOR / PFOR-DELTA / PDICT）｜核实 2026-08-01
- Apache Parquet Encodings 规范（RLE/BIT_PACKED、PLAIN_DICTIONARY/RLE_DICTIONARY、DELTA_BINARY_PACKED、DELTA_BYTE_ARRAY 等）https://parquet.apache.org/docs/file-format/data-pages/encodings/ ｜⚙演进快·核实 2026-08-01
- Roaring bitmap 属压缩位图前沿实现，⚙演进快·锚版本·随时变；上文只作定性引用，未引具体压缩比（待核）。
- 冲突项：术语"RLE"在不同实现含义有别——Parquet 的 "RLE" 实为 RLE 与 bit-packing 的混合编码（hybrid），并非纯游程；教材语境的 RLE 指纯 (值,长度) 对。使用时须按实现规范核对，二者都记。

## 04-2.3 编码-执行协同：编码态直接计算与通用压缩取舍

### 04-2.3.1 在编码态上直接计算

列存编码的最大价值不只是省空间，而是让查询执行能"不解码就算"——直接在压缩后的表示上完成过滤、聚合甚至连接。这是列存相对"先解压再当普通数据处理"的根本性能杠杆，由 Abadi 等人 2006 年系统论证。

字典编码下，`WHERE city='NY'` 先把 'NY' 翻成字典码 0，扫描时只比较整数 `code==0`，既不碰字符串也不解码；RLE 下，对一段 (值 V, 长度 L) 的游程求 COUNT 直接加 L、求 SUM 直接加 `V*L`，一对游程顶几千行；FOR/bit-packing 下，SIMD 可以一次对多个紧凑值做比较。执行器为此需要"编码感知"的算子——同一个过滤算子要能识别输入是 RLE、字典还是 FOR，走对应的快路径。易错点：并非所有算子都能在编码态算，有些操作（如跨不同局部字典的两列比较）必须先对齐/解码；好的引擎会尽量把解码点后推（见 04-2.4）、只在真正必要时才物化成明文值。

### 04-2.3.2 轻量编码 vs 通用块压缩（LZ4/zstd/Snappy/gzip）

列存里有两类压缩，容易混淆。一类是上一节的轻量编码（字典/RLE/FOR/delta），它们数据库"看得懂"结构、能在编码态直接算；另一类是通用字节流压缩器（LZ4、Snappy、zstd、gzip），把一段字节当作无结构比特流去找重复子串压缩，数据库对其内部一无所知，用前必须整块解压。

取舍的核心是"解压成本 vs 压缩比 vs 能否编码态计算"：

```
轻量编码:  压缩比中等 | 解码极廉/可省 | 可在编码态直接算 | 数据库感知结构
通用压缩:  压缩比更高 | 必须整块解压才能用 | 不能编码态算  | 视数据为字节流
     LZ4/Snappy: 偏速度(解压快、压缩比中)
     gzip:      偏压缩比(慢)
     zstd:      可调级别，速度/比折中，近年默认首选(⚙演进快·锚版本)
```

工业格式普遍两类叠用：先对每列做轻量编码（拿到结构收益和编码态计算能力），再对编码后的字节页可选套一层通用压缩（榨干剩余冗余）。选谁取决于负载：CPU 富余、追求最小存储/网络传输选 zstd/gzip；扫描密集、CPU 是瓶颈选 LZ4/Snappy 或干脆只用轻量编码。易错点：对已经字典+bit-packed 的高熵字节流再套重型通用压缩，收益可能很小却白付解压 CPU——是否叠加、叠哪个应按列、按块实测，不能一刀切。具体压缩比高度依赖数据分布，本篇不给数值（待核）。

### 04-2.3.3 编码级联与按块自适应选择

真实列存很少对整列用单一编码，而是把编码级联（先字典化再 RLE 再 bit-pack）并按数据块自适应选择：把一列切成若干块，对每块统计其分布，挑当前块压缩效果最好的编码组合，块头记录用了哪种编码。同一列不同段可以用不同编码。

这么做的原因是数据分布沿列会变化——前一万行可能高度重复（RLE 划算），后一万行可能单调递增（delta+FOR 划算）。按块自适应让每段各取所长，也让解码可以块级并行。DuckDB、ClickHouse、Parquet/ORC 写入器都走这条路（各自的编码集合与自动选择策略是实现差异，⚙演进快·锚版本）。初学者可以这样记：列存的"压缩"不是一个开关，而是一条"每块自己挑"的编码流水线；理解了这点，就不会奇怪为什么同一列的物理大小在不同数据下差异巨大。

#### 来源与时效
- Abadi, Madden, Ferreira, "Integrating Compression and Execution in Column-Oriented Database Systems", SIGMOD 2006（编码态直接计算的系统论证，核心承重）｜核实 2026-08-01
- CMU 15-721 Fall 2025 L03（Compression / operate-on-compressed 讲）https://www.cs.cmu.edu/~15721-f25/ ｜核实 2026-08-01
- Apache Parquet 规范：Encodings + Compression（页级编码后再套 codec：SNAPPY/GZIP/LZ4/ZSTD 等）https://parquet.apache.org/docs/ ｜⚙演进快·核实 2026-08-01
- DuckDB 官方文档：Lightweight Compression / Storage（按块自适应编码：RLE、Dictionary、FOR、Bit-packing、FSST 等）https://duckdb.org/docs/ ｜⚙演进快·核实 2026-08-01
- 冲突项：无口径冲突；各来源一致区分"数据库感知的轻量编码"与"通用字节流 codec"。具体压缩比/解压吞吐数值依数据与硬件而异，本篇未给（待核）。

## 04-2.4 late materialization（延迟物化）：物化时机与元组重组代价

### 04-2.4.1 early vs late materialization 的定义

在列存里，"物化"（materialization）指把分散在各列的编码值拼回成完整（明文）元组这一动作。物化时机分两派：early materialization 在查询早期就把需要的列拼成行、之后按行式处理；late materialization 尽量推迟拼接，先在各列上以位置为纽带跑完过滤/聚合，只在最后（或非拼不可时）才去各列取值组装结果。

```
early:  扫各列 -> 立即按行拼成元组 -> 在元组上过滤/聚合/输出
late:   扫某列做过滤 -> 得到通过的位置集(选择向量) -> 用位置集去其他列取值 -> 最后才组装
```

这个概念由 Abadi 等人 2007 年在 C-Store 上系统研究。直觉上 late 更契合列存：过滤阶段只碰谓词列、且能在编码态算（04-2.3），大量行在拼接前就被淘汰，省下的正是"给最终不会输出的行做无谓拼接"的开销。

### 04-2.4.2 位置列表 / 选择向量作为纽带

延迟物化的实现纽带是"位置"这种紧凑中间表示：过滤算子不产出行，而是产出一串"哪些位置通过了谓词"，可以是位置列表（通过的 row id 数组）或选择向量（一个标记每行通过与否的位图/掩码）。下游算子拿着这串位置，只到真正需要的列里按位置取值。

```
scan amount, 谓词 amount>40:
  amount列: 30 50 20 60 45
  选择向量: 0  1  0  1  1      (通过的位置: 1,3,4)
下游需要 city 时，只取 city[1], city[3], city[4]，其余不碰
```

这套机制把"过滤"与"取值"解耦，是列存高效的关键之一：位置表示极紧凑（一位一行或几字节一个 id），可在算子间廉价传递，还能和向量化执行、编码态计算无缝配合。易错点：位置若很稀疏（大量行被过滤掉后剩零星几个），按位置去列里随机取值会退化成随机访问、cache 不友好；引擎会根据选择率在"按位置聚集取值"和"顺序扫整列再筛"之间权衡。

### 04-2.4.3 元组重组代价与何时 early 反而更好

延迟物化不是免费的：推迟拼接意味着可能要多次访问同一列（一次用于过滤、一次用于取值），且按位置取值可能是随机访问。当选择率很高（几乎所有行都通过）、或某列既要过滤又要输出、或需要立刻按行做多列组合运算时，early materialization 一次拼好、顺序处理反而更省——省掉了"记位置、再回列取值"的往返。

因此现代引擎不押单一策略，而是按算子和选择率动态选择：高选择率或多列一起用时倾向 early；低选择率、谓词列少、能在编码态算时倾向 late。理解这条权衡的关键，是回到 04-2.1.4 的隐式位置对齐——正因为"第 k 个位置在每列都指同一行"，late 才能用廉价的位置往返代替 DSM 式的按键连接；一旦重组代价（多趟列访问 + 随机取值）超过它省下的"无谓拼接"，天平就倒向 early。初学者记住结论即可：物化时机是"过滤省下的拼接" 对 "推迟带来的多趟列访问"之间的账，没有恒优解。

#### 来源与时效
- Abadi, Myers, DeWitt, Madden, "Materialization Strategies in a Column-Oriented DBMS", ICDE 2007（early vs late materialization 原始系统研究，核心承重）｜核实 2026-08-01
- Abadi et al., "The Design and Implementation of Modern Column-Oriented Database Systems", FnT Databases 2013（物化策略与选择向量综述）｜核实 2026-08-01
- CMU 15-721 Fall 2025 L05–L06（Vectorized Execution / late materialization 相关讲）https://www.cs.cmu.edu/~15721-f25/ ｜核实 2026-08-01
- 冲突项：无定义冲突。各来源一致，仅强调"最优时机随选择率/查询形态而变"，不存在"late 恒优"的结论。

## 04-2.5 开放列存格式：Parquet / ORC 的 row-group / page / 统计布局

### 04-2.5.1 Parquet 的层次布局

Apache Parquet 是应用最广的开放列存文件格式，其布局是 PAX 思想的放大版："文件 → 行组（row group）→ 列块（column chunk）→ 页（page）"四层。一个文件按行切成若干 row group（大块，通常几十上百 MB）；每个 row group 内按列切成 column chunk；每个 column chunk 再切成若干 page（读写和编码/压缩的最小单元）。文件尾部（footer）集中存元数据：schema、每个 row group / column chunk 的位置和统计信息。

```
Parquet file
├─ Row Group 0
│   ├─ Column Chunk: colA ─ [Page][Page]...   (含 Dictionary Page + Data Pages)
│   ├─ Column Chunk: colB ─ [Page][Page]...
│   └─ Column Chunk: colC ─ [Page][Page]...
├─ Row Group 1
│   └─ ...
└─ Footer(FileMetaData): schema + 各 RowGroup/ColumnChunk 的 offset 与 min/max 统计
```

这个布局同时满足几件事：row group 让文件可被切成并行读取单元（大数据里一个 row group 常对应一个任务）；组内列存让"只读几列"只读对应 column chunk；page 让编码/压缩/统计的粒度足够细。读一个查询时，先读 footer 拿到统计，跳过不相关的 row group 和列，只 seek 命中的 column chunk。row group 大小、page 大小是可配置项，且默认值随实现与版本变化（parquet-mr、Arrow C++、各引擎写入器各有默认，⚙演进快·锚版本，具体默认值待核，不在此写死）。

### 04-2.5.2 ORC 的层次布局

Apache ORC（Optimized Row Columnar，源自 Hive）与 Parquet 高度同构但术语不同："文件 → stripe → column → row group（行组，ORC 内部的细粒度索引单位）"。stripe 对应 Parquet 的 row group（大的行分块）；stripe 内每列的数据分开存；ORC 还在 stripe 内部按固定行数（row index stride）建轻量行组索引，支持在 stripe 内进一步跳读。

```
ORC file
├─ Stripe 0
│   ├─ Index Data   (每列的 min/max 等 + 每个 row-index-stride 的位置)
│   ├─ Row Data     (各列的编码数据，按列分开)
│   └─ Stripe Footer(各列流的编码与位置)
├─ Stripe 1 ─ ...
└─ File Footer + Postscript(schema、各 stripe 统计、压缩参数)
```

ORC 的一个特色是内建的三级统计：file 级、stripe 级、row-group（stride）级，越细的层级支持越精细的跳读。stripe 大小、row index stride 是可配置项，ORC 规范/配置各有默认值（如 stripe 默认量级、stride 默认行数），但具体默认随版本演进（⚙演进快·锚版本，精确默认值以对应版本配置为准，待核）。初学者只需抓住：ORC 与 Parquet 是"同一套 PAX 放大思想的两种工业实现"，差异在术语、默认值、内建索引粒度和对特定生态（ORC 偏 Hive/Hadoop）的贴合度，而非布局哲学。

### 04-2.5.3 统计信息与谓词下推、分区裁剪

开放列存格式在各粒度（文件/行组/页 或 file/stripe/stride）随数据一起存统计信息——最常见是每段的 min/max（值域）、null 计数、有时有 distinct 计数。查询引擎据此做"下推裁剪"：一个谓词 `WHERE amount>1000`，若某 row group 的 amount 统计是 [0,50]，则整段不可能命中，直接跳过、连读都不读。这类块级 min/max 常被称为 zone map / data skipping。

```
谓词 amount > 1000
RowGroup0 amount统计 [0,50]     -> 整段跳过(min/max 排除)
RowGroup1 amount统计 [900,3000] -> 需要读，进一步看 page 统计
```

除 min/max 外，两种格式都支持更强的跳读手段：字典下推（若谓词值不在该块字典里，整块跳过）、可选的布隆过滤器（Bloom filter，用于等值谓词判"该块一定不含此值"，对高基数列尤其有用，⚙演进快·锚版本）。再上一层是分区裁剪——按目录/文件把数据按某列分区（如按日期分目录），查询直接不碰无关分区文件，这发生在读文件之前，是最廉价的裁剪。易错点：min/max 裁剪的效果强依赖数据在块内的聚集程度——若数据随机分布，几乎每个块的 [min,max] 都很宽、裁不掉，所以写入时按常用过滤列排序（让值域窄而聚集）能大幅提升 data skipping 效果，这也把 04-2.2 的"排序换压缩"和这里的"排序换跳读"串成了同一件事。

### 04-2.5.4 Parquet 与 ORC 的差异比对

两者布局哲学一致（PAX 放大 + 多级统计 + 编码后可选通用压缩），差异集中在细节与生态。术语上 Parquet 叫 row group / column chunk / page，ORC 叫 stripe / stream / row group(stride)。索引粒度上 ORC 内建 stripe 内 row-index-stride 级细索引是其显著特征；Parquet 早期以 row group + page 统计为主，后续通过 page index、column index、offset index 等补齐页级跳读能力（⚙演进快·锚版本）。嵌套数据上 Parquet 采用 Dremel 的 repetition/definition level 方案编码嵌套/重复结构，是其一大特色。编码集合、默认压缩 codec、对各引擎的支持度也各有侧重。

选型层面（此处只点到，不作定论）：Parquet 在 Spark/Arrow/云数据湖生态里更通用、跨引擎支持最广；ORC 在 Hive/Hadoop 栈和某些 ACID/事务表场景里更贴合。两者都在持续演进（新编码、更细的页级索引、更强的统计），任何"谁更快/更小"的结论都强依赖数据、引擎版本与配置，须按当下版本实测，本篇不给排名与数值（待核）。初学者的正确心智模型是：Parquet 和 ORC 是"同一类东西的两个方言"，先理解通用的四层 PAX 布局与多级统计裁剪，再按生态选方言即可。

#### 来源与时效
- Apache Parquet 官方格式规范（File Format / Metadata / Encodings / Page Index）https://parquet.apache.org/docs/file-format/ ｜⚙演进快·锚版本·核实 2026-08-01
- Apache ORC 官方规范（ORC Specification v1：file / stripe / row group / index）https://orc.apache.org/specification/ ｜⚙演进快·锚版本·核实 2026-08-01
- Melnik et al., "Dremel: Interactive Analysis of Web-Scale Datasets", VLDB 2010（Parquet 嵌套编码所本的 repetition/definition level）｜核实 2026-08-01
- CMU 15-721 Fall 2025 L02（Data Formats：Parquet/ORC/Arrow 布局与统计）https://www.cs.cmu.edu/~15721-f25/ ｜核实 2026-08-01
- Ailamaki et al., VLDB 2001（PAX，作为 row group/stripe 布局的思想源）｜核实 2026-08-01
- 冲突项与待核：Parquet 的 row group / page 默认大小、ORC 的 stripe 大小与 row index stride 默认值，因实现（parquet-mr / Arrow / 各引擎）与版本而异，本篇一律标「待核」，未写死具体数值；如需精确值须查对应版本的写入器配置。术语层面 Parquet 与 ORC 对"行组/条带"的命名不同，已在正文两边都记。
