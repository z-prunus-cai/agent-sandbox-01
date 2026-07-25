# Round 2 · 第 7 组「数据管理」定方向

> 组内课程：L4-03 数据库系统（DM，L4，P0）· L6-04 数据库内核与存储系统（DM，L6，P2）· L6-09 数据密集型应用/DDIA2（DM，L6，✅新增）
> 基线 Py3.11.15/np2.4.6/gcc13.3 @2026-07-25 ｜ 本机 DB 工具链核实：PostgreSQL 16.13 服务端可起（`pg_ctlcluster 16 main`）+ psql 16.13 客户端；Python `sqlite3` 3.45.1（内置，含 `EXPLAIN QUERY PLAN`）；⚠ 无 `sqlite3` CLI、无 duckdb、无 mysql 服务端。
> 产物性质：本组为**阶段2 定方向**，非逐条取证；下列对比表/方向/一手源为 R3 造 prompt、R4 取证的入口。文中标「✅本机已验」处为本轮 R2 真跑的可行性冒烟测试（贴真实输出），坐实 R4 可实证。

---

## §0 三门课的分工主线（一句话地图）

- **L4-03 = 教材级「关系数据库怎么用、怎么保证对」**：以单机关系型 DBMS 为对象，讲关系模型/SQL/范式/索引(B+树)/查询优化/事务 ACID/并发控制(锁·MVCC)/日志恢复(WAL·ARIES)。心智模型 = 「一个正确的单机 OLTP 引擎里发生了什么」。
- **L6-04 = 内核级「现代存储/执行引擎怎么造、怎么快」**：以 DBMS 内部构件为对象，讲 LSM 树 vs B+树的写放大权衡、列存+压缩、向量化执行 vs 查询编译(JIT)、以及把事务推到多节点的分布式事务(2PC/Percolator/Calvin)。心智模型 = 「拆开引擎，为 OLAP/高写入/多核重造每一层」。
- **L6-09 = 应用架构级「面对海量数据，选型与权衡怎么做」**：以 DDIA 第2版为骨架，讲复制(单主/多主/无主)、分区(哈希/范围)、流批处理、存储引擎选型——**站在系统设计者视角做取舍**，而非造引擎。心智模型 = 「给定负载与故障模型，怎样拼装可靠可扩展的数据系统」。

> 递进关系：L4-03 建立「单机正确性」的地基 → L6-04 向**下**钻进引擎实现把它做快/做大 → L6-09 向**上**升到多机架构把它做可靠/可扩展。三者对同一批概念（事务、索引、复制）分别是 *规范级用法 / 实现级机制 / 架构级选型*，视角不同不重复（见 §2 边界）。

---

## §1 组内选型/对比表（高价值横向对比）

### 表 1 · B+树 vs LSM树（存储引擎两大范式，L4-03 教 B+树、L6-04 教 LSM、L6-09 讲选型）
| 维度 | B+树（就地更新） | LSM树（追加+合并） |
|------|------------------|--------------------|
| 写路径 | 原地改页，随机写；先 WAL 后刷脏页 | 顺序写 memtable→WAL→flush 成不可变 SSTable |
| 写放大 | 中（页级重写 + WAL） | 高（后台 compaction 反复重写），但**写吞吐**通常更高（顺序 I/O） |
| 读路径 | 稳定 O(log n)，一次树查找 | 可能查多层 SSTable + memtable，靠 Bloom filter 剪枝 |
| 读放大/空间 | 读放大低；页内碎片、~70% 填充 | 读/空间放大随层数波动；旧版本延迟回收 |
| 典型代表 | PostgreSQL/SQLite/MySQL-InnoDB 主索引 | RocksDB/LevelDB/Cassandra/HBase |
| 适配负载 | 读多、点查、范围扫描稳定 | 写密集、时序、日志型摄入 |
| 一手源 | Postgres `src/backend/access/nbtree/` | RocksDB wiki + 源码 `db/`、O'Neil 1996 LSM 论文 |

### 表 2 · 行存 vs 列存（OLTP vs OLAP 物理布局，L4-03 默认行存、L6-04 教列存）
| 维度 | 行存(row-store) | 列存(column-store) |
|------|-----------------|--------------------|
| 布局 | 一行的所有列相邻 | 同一列的值相邻存 |
| 擅长 | 点查/整行读写(OLTP) | 大范围聚合、少数列扫描(OLAP) |
| 压缩 | 一般 | 极好（同列同类型，RLE/字典/位图/Delta） |
| 执行配合 | 火山模型一次一行即可 | 与**向量化/SIMD**天然契合（批列处理） |
| 代表 | Postgres/InnoDB | Parquet/ORC、ClickHouse、DuckDB、Redshift |

### 表 3 · 锁(2PL) vs MVCC（并发控制两条路线，L4-03 教两者、L6-04 讲实现细节）
| 维度 | 严格两阶段锁 2PL | 多版本并发控制 MVCC |
|------|------------------|---------------------|
| 核心思想 | 读写互斥，冲突即等待/阻塞 | 写产生新版本，读旧快照——**读写不互斥** |
| 读的代价 | 读要加共享锁，读阻塞写 | 读走快照，不加行锁（Postgres/InnoDB 默认路线） |
| 隔离实现 | 靠锁范围（行/间隙锁防幻读） | 靠可见性判定 + 版本链；写写冲突仍需锁/串行化检查 |
| 异常处理 | 死锁需检测/超时回滚 | 快照隔离下有 write-skew，需 SSI(可串行化快照)补 |
| 代表 | 传统商用 DB 的 SERIALIZABLE | Postgres(全 MVCC)、InnoDB、Oracle；Postgres SSI |

### 表 4 · OLTP vs OLAP（负载画像，贯穿三门）
| 维度 | OLTP | OLAP |
|------|------|------|
| 访问模式 | 大量短事务、点查/小范围、读写混合 | 少量长查询、全表/大范围聚合、几乎只读 |
| 数据规模/新鲜度 | GB–TB，强一致、实时 | TB–PB，可容忍延迟(ETL/批) |
| 引擎取向 | 行存 + B+树 + MVCC/2PL | 列存 + 向量化/编译 + 无锁批处理 |
| 代表 | Postgres/MySQL | ClickHouse/DuckDB/Spark/Snowflake；HTAP 试图两全 |
| 归属课 | L4-03 主场 | L6-04(引擎)/L6-09(选型)主场 |

### 表 5 · 向量化执行 vs 查询编译(JIT)（L6-04 内核级两条加速路线）
| 维度 | 向量化(pull-based batch) | 查询编译(codegen/JIT) |
|------|--------------------------|------------------------|
| 思路 | 算子一次处理一批(1024行)列向量，摊薄解释开销 | 把查询计划编译成机器码，消除解释与虚函数 |
| 代表 | DuckDB、ClickHouse、Vectorwise | HyPer、Spark(whole-stage codegen)、Postgres(LLVM JIT 表达式) |
| 权衡 | 实现简单、缓存友好；仍有批间物化 | 编译开销 vs 短查询、复杂度高、调试难 |
| 一手源 | 「MonetDB/X100」论文；DuckDB 源码 | Neumann 2011「Efficiently Compiling…」；Postgres `llvmjit` |

### 表 6 · 三门课分工总表（同概念的三层视角，界定不重复）
| 概念 | L4-03 教材级 | L6-04 内核级 | L6-09 架构级 |
|------|--------------|--------------|--------------|
| 索引 | B+树原理、建索引、看 EXPLAIN | B+树 vs LSM 实现权衡、列存索引 | 按负载选存储引擎 |
| 事务 | ACID、隔离级别、单机 2PL/MVCC | MVCC 版本链/GC、分布式事务 2PC/Percolator | 跨副本一致性、恰好一次语义 |
| 复制 | （几乎不讲） | 复制协议实现 | **主场**：单主/多主/无主、冲突解决 |
| 分区 | （不讲） | 分片路由基础 | **主场**：哈希/范围分区、再平衡、路由 |
| 执行 | 查询优化器/代价模型入门 | 向量化/编译/列存执行 | 批/流处理框架选型(Spark/Flink/Kafka) |

---

## §2 逐课重点方向 + 边界

### L4-03 数据库系统（P0，L4，教材级地基）
重点方向（4–6）：
1. **关系模型与 SQL 语义**：关系代数↔SQL 映射、连接/聚合/子查询、NULL 三值逻辑。
2. **范式与模式设计**：函数依赖、1NF–BCNF、反范式的工程权衡。
3. **索引与 B+树**：B+树结构、聚簇 vs 二级索引、覆盖索引；**EXPLAIN 读执行计划**（本机主实证点）。
4. **查询优化**：代价模型、基数估计、连接顺序、`ANALYZE`/统计信息对计划的影响。
5. **事务 ACID 与并发控制**：隔离级别(RU/RC/RR/Serializable)、脏读/不可重复读/幻读/write-skew、2PL 与 MVCC 入门。
6. **日志与恢复**：WAL、redo/undo、ARIES、检查点、崩溃恢复。

边界：只做**单机关系型**，索引只到 B+树（LSM 留 L6-04），事务只到单机，复制/分区完全不碰（留 L6-09）。是「怎么正确地用一个引擎」。

### L6-04 数据库内核与存储系统（P2，L6，内核级）
重点方向（4–6）：
1. **LSM 树 vs B+树**：memtable/SSTable/compaction 策略(leveled/tiered)、写放大/读放大/空间放大三角、Bloom filter。
2. **列存与压缩**：列式布局、字典/RLE/位图/Delta 编码、late materialization。
3. **向量化执行 vs 查询编译**：火山模型的开销、批列执行、JIT codegen（见表 5）。
4. **MVCC 实现内幕**：版本链/可见性判定/vacuum-GC、Postgres 堆元组 vs InnoDB undo 段的差异。
5. **分布式事务**：2PC 及其阻塞问题、Percolator(快照+2PC)、Calvin(确定性)、Spanner(TrueTime) 概览。
6.（可选）**HTAP/新硬件**：行列混合、NVM/内存引擎。

边界：面向**引擎实现者**，剖开每一层做快/做大；不承担应用架构选型的系统设计叙事（留 L6-09）。以源码(RocksDB/Postgres)+论文承重。

### L6-09 数据密集型应用 / DDIA 第2版（P2→建议 P1，L6，架构级）
重点方向（4–6）：
1. **复制**：单主/多主/无主、同步 vs 异步、复制延迟三读一致性(读己写/单调读/一致前缀)、冲突解决(LWW/CRDT)。
2. **分区(sharding)**：按键范围 vs 哈希、热点、二级索引分区(本地 vs 全局)、再平衡与请求路由。
3. **事务的分布式视角**：线性一致性 vs 可串行化、隔离异常谱、恰好一次语义。
4. **批处理 vs 流处理**：MapReduce/Spark 的批模型、Kafka/Flink 的流模型、变更数据捕获(CDC)、事件溯源。
5. **存储引擎选型**：结合表 1/2/4，按负载在 B+树/LSM、行/列、SQL/NoSQL 间取舍。
6. **一致性与共识（重点章，2版重写）**：CAP 的正确表述、线性一致性代价、共识(Raft/Paxos)与 2PC 的关系。

边界：**设计者视角**，用大量真实系统举例讲权衡，不逐行读引擎源码、不讲单机 SQL 语法。与第 8 组「分布式系统(L5-01)」的界：L5-01 深挖共识算法/一致性理论本身，L6-09 讲「如何在数据系统里用这些性质做取舍」——理论 vs 应用孪生，需 R3 时与分布式组显式划线防重。

> 三门递进小结：**用对(L4-03) → 造快造大(L6-04) → 拼可靠可扩展(L6-09)**。同一「事务/索引/复制」在三门分别是规范级/实现级/架构级，视角正交不重复。

---

## §3 一手源候选 + 本机实证点

### 一手源候选（R4 承重，按可信度）
- **规范/标准**：ISO/IEC 9075 SQL 标准（隔离级别定义在 SQL-92 起，含四级异常语义）；⚠标准正文需付费，可用免费草案 + 各 DB 文档交叉。
- **官方文档（一手·契约）**：
  - PostgreSQL 16 官方手册——「Concurrency Control / MVCC」「Transaction Isolation」「Indexes / Index Types(B-tree)」「EXPLAIN」「WAL」章。
  - SQLite 官方文档——「Query Planner」「EXPLAIN QUERY PLAN」「WAL mode」「Isolation」。
  - RocksDB Wiki——LSM/Compaction/Bloom。DuckDB 文档——向量化/列存。
- **源码本地副本（一手·实现，R4 clone 到 `.reference/`）**：
  - PostgreSQL 源码 `src/backend/access/nbtree/`(B+树)、`access/heap/` 与 `storage/`(MVCC 堆元组)、`optimizer/`(优化器)、`access/transam/xlog*`(WAL)。
  - SQLite amalgamation（单文件，读 B+树 `btree.c`、VDBE）。
  - RocksDB（LSM）、DuckDB（列存+向量化）。
- **教材/课程**：Silberschatz《Database System Concepts》(L4-03 主教材)；CMU **15-445**（Intro，✅核实：**Spring 2026** 学期在线，Andy Pavlo，Postgres-based）；CMU **15-721**（Advanced，✅核实：**Fall 2025** 学期在线，OLAP/内核，对应 L6-04）；Berkeley CS186。
- **书**：**DDIA 第2版**（✅核实：Kleppmann + Riccomini，印刷版约 **2026-02** 发布、~650 页，第10章一致性/共识近乎重写；2版为 L6-09 骨架，1版仍可用但共识章已过时）；O'Neil《The LSM-Tree》1996；Neumann《Efficiently Compiling Efficient Query Plans》2011；MonetDB/X100 论文。
- **论文（L6-04 分布式事务）**：Google Percolator(2010)、Calvin(2012)、Spanner(2012)。

### 本机实证点（R4 必跑；本轮已冒烟验证可行性）
1. **✅本机已验 · Postgres 建索引前后 EXPLAIN 计划翻转**（L4-03 核心实证）：
   `CREATE TABLE t(id int,v int); INSERT ... generate_series(1,100000)` 后 `EXPLAIN SELECT * FROM t WHERE id=42` →
   建索引前 `Seq Scan on t (cost=0.00..1693.00 rows=1)`；`CREATE INDEX idx_t_id ON t(id); ANALYZE;` 后 → `Index Scan using idx_t_id on t (cost=0.29..8.31 rows=1)`。计划真实翻转。复现：`pg_ctlcluster 16 main start` 后以 postgres 用户 psql。
2. **✅本机已验 · Postgres MVCC 快照隔离复现**（L4-03/L6-04）：会话 A `BEGIN ISOLATION LEVEL REPEATABLE READ; SELECT bal` 读到 100；会话 B `UPDATE ... SET bal=999` 并提交；A 再读仍 **100**（快照不变）；A 提交后新事务读到 **999**。真实输出证 MVCC 读不被并发写影响。→ R4 可扩展做 RC vs RR 差异、write-skew、SSI(SERIALIZABLE) 阻断。
3. **✅本机已验 · SQLite B+树索引查询计划**（L4-03，Python `sqlite3` 3.45.1）：10 万行表 `EXPLAIN QUERY PLAN SELECT * FROM t WHERE id=42` → 无索引 `SCAN t`；`CREATE INDEX` 后 → `SEARCH t USING INDEX idx (id=?)`。
4. **待跑(R4)**：SQLite WAL 模式 vs rollback journal 行为；Postgres `pg_stat`/`EXPLAIN ANALYZE` 看真实行数 vs 估计；死锁检测(两会话交叉 UPDATE 触发 `deadlock detected`)；隔离级别下脏读/不可重复读/幻读逐一复现。
5. **LSM/列存实证**（L6-04）：⚠ 本机无 RocksDB/DuckDB，R4 需 `pip install`（DuckDB 可 pip 装，注意不持久化）或 clone 源码编译；LSM 写放大观察建议装 RocksDB python 绑定或用 LevelDB 小实验。

---

## §4 优先级校准

| 课程 | 种子优先级 | 本组建议 | 理由 |
|------|-----------|----------|------|
| L4-03 数据库系统 | **P0** | 维持 **P0** | 枢纽：所有数据管理的地基，本机三腿实证最扎实（Postgres+SQLite 全在），性价比最高，R4 应最先出。 |
| L6-04 数据库内核 | P2 | 维持 **P2** | 深化课，依赖 L4-03；源码承重重、实证需额外装 RocksDB/DuckDB；分布式事务与第8组有交叉，宜后置。 |
| L6-09 DDIA2 | P2 | **建议升 P1** | 1) 覆盖复制/分区/流批这一大块「架构选型」，是 L4-03/L6-04 都不碰的独立价值；2) 一手源(DDIA2)成熟、2026-02 刚出新版、时效极佳；3) 工程读者需求高。⚠与 L5-01 分布式需在 R3 划清边界防重。 |

组内 R4 建议顺序：**L4-03 → L6-09 → L6-04**（地基 → 架构选型 → 内核深挖；把最难装实证环境的 L6-04 放最后）。

---

### 一手引用清单（脚注定义，R4 落定位）
- [^1] PostgreSQL 16 Manual: Concurrency Control (MVCC) / Transaction Isolation / Indexes(B-tree) / EXPLAIN / WAL. https://www.postgresql.org/docs/16/
- [^2] SQLite Docs: The Query Planner / EXPLAIN QUERY PLAN / WAL mode. https://www.sqlite.org/queryplanner.html
- [^3] ISO/IEC 9075 (SQL) — 隔离级别与异常语义（⚠正文付费，草案+DB文档交叉）。
- [^4] CMU 15-445 Intro to DB (Spring 2026) https://15445.courses.cs.cmu.edu/ ；15-721 Advanced DB (Fall 2025) https://www.cs.cmu.edu/~15721-f25/ （核实 2026-07-25）。
- [^5] Kleppmann & Riccomini, *Designing Data-Intensive Applications*, 2nd ed., O'Reilly (印刷版约 2026-02；第10章重写)。https://martin.kleppmann.com/2026/03/24/designing-data-intensive-applications-2e.html
- [^6] O'Neil et al., *The Log-Structured Merge-Tree (LSM-Tree)*, 1996；Neumann, *Efficiently Compiling Efficient Query Plans for Modern Hardware*, VLDB 2011；MonetDB/X100 (CIDR 2005)。
- [^7] 源码副本(R4 clone)：PostgreSQL `src/backend/access/nbtree|heap|transam`；SQLite `btree.c`/VDBE；RocksDB；DuckDB。

### 二三手（非承重）
- csdiy.wiki CMU 15-445 条目（⚠仅指路课程资源）。
