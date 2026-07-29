# L4-03·大主题03-2 SQL 语言（基础→高级）

> 基线 Py3.11.15/np2.4.6/gcc13.3 @2026-07-25 ｜ 核实日期：2026-07-29 ｜ 先修：本课大主题03-1（关系模型：关系/元组/属性/域、键与参照完整性、关系代数 σ/π/⋈/ρ、关系的集合语义 vs SQL 多集语义）｜ 一手锚点：《Database System Concepts》7th ed（Silberschatz/Korth/Sudarshan, 2019，简称 DBSC 7e）ch3「Introduction to SQL」、ch4「Intermediate SQL」、ch5「Advanced SQL」；CMU 15-445 Spring 2026 L02「Modern SQL」（https://15445.courses.cs.cmu.edu/spring2026/）；SQL 标准 ISO/IEC 9075（现行版 SQL:2023，历史里程碑 SQL-92/SQL:1999/SQL:2003 按内容项标注）；实现侧 PostgreSQL 官方文档（以 PostgreSQL 17 为基线，17 为本报告核实时的最新 GA 大版本）与 SQLite 官方文档（本机 sqlite3 库版本 3.45.1）｜ 成熟度：GA/稳定（SQL 核心语法数十年稳定；窗口函数/CTE/JSON 等高级特性引入版本单独标注；方言差异逐项分账）

> 粒度判定：**1 份，不拆**。本大主题 6 个小主题（03-2.1–03-2.6）沿一条主线层层递进——先用 DDL 把表和约束立起来（2.1），再用最基础的单表 SELECT-FROM-WHERE 取数（2.2），然后横向连表与纵向聚合（2.3），接着把查询嵌进查询、用 CTE 组织复杂查询（2.4），再补上贯穿所有前面章节的 NULL 三值逻辑这块"暗礁"（2.5），最后收束到视图/触发器/授权/窗口这组高级特性（2.6）。六节咬合紧密、共享一套小样例表，合为一份可读性更好；虽然内容项密集，但每项讲解浅（教辅口径），总篇幅可控，故按 report-format v3 §一不拆 `-a/-b`。

> 一条主线心智模型：**SQL 是一门声明式语言——你描述"想要什么结果"，而不是"怎么一步步算"**。DDL 定义数据长什么样（结构与约束），DML 描述要什么数据（查询与增删改），DCL 管谁能碰数据（授权）。所有查询在概念上都按同一套逻辑处理顺序求值（FROM→WHERE→GROUP BY→HAVING→SELECT→DISTINCT→ORDER BY→LIMIT），记住这个顺序几乎能解释本报告一半的"为什么"。

> 规范 vs 实现总纲（本大主题反复用到）：SQL 标准（ISO/IEC 9075）规定语义，但**没有一个数据库完整实现标准，且各家在关键点上互相打架**。本报告把标准语义、PostgreSQL 行为、SQLite 行为**分账记录**，冲突处两边都写、点明偏离。最典型的四处分歧是：SQLite 的动态类型亲和性 vs 标准/PostgreSQL 的静态强类型；SQLite 默认**不强制**外键 vs PostgreSQL 默认强制；SQLite 视图**一律只读** vs PostgreSQL 简单视图自动可更新；SQLite **无用户/权限体系**（无 GRANT）vs PostgreSQL 完整的角色授权。

> 本报告 SQL 行为多处在本机以 Python 内置 `sqlite3` 模块实跑取证（SQLite 库版本 3.45.1 @2026-07-29，Linux 6.18.5），真实输出贴入正文对应节；测试脚本仅存 scratchpad、不入库。本机未安装 PostgreSQL 服务与 `sqlite3` CLI（**待核**：PostgreSQL 侧行为依据官方文档，未在本机实跑坐实）。实机验证是多来源比对的**补充**，不替代比对。

---

## 03-2.1 DDL 与类型/约束

### 03-2.1.1 DDL 三件套：CREATE / ALTER / DROP

DDL（Data Definition Language，数据定义语言）是 SQL 中用来定义和修改数据库对象结构的子语言，核心是 `CREATE`（创建对象，如表/索引/视图）、`ALTER`（修改已有对象结构）、`DROP`（删除对象）。它操作的是"表长什么样"这个骨架，而不是表里的具体数据。一条最基本的建表语句形如：

```sql
CREATE TABLE instructor (
    ID        VARCHAR(5),
    name      VARCHAR(20) NOT NULL,
    dept_name VARCHAR(20),
    salary    NUMERIC(8,2),
    PRIMARY KEY (ID),
    FOREIGN KEY (dept_name) REFERENCES department (dept_name)
);
```

初学者要先分清 DDL 和 DML 的分工：DDL 管"表的结构"（列名、类型、约束），DML（下一小主题）管"表里的行"（增删改查数据）。一个易混点是 `DROP TABLE t`（删掉整张表连结构一起没）与 `DELETE FROM t`（只删所有行、表结构还在）完全不同；还有个介于两者之间的 `TRUNCATE TABLE t`（快速清空所有行、保留结构，标准 SQL:2008 引入，PostgreSQL 支持、SQLite 不支持而用 `DELETE FROM t` 替代——**方言差异**）。`ALTER TABLE` 用来事后改结构，例如 `ALTER TABLE instructor ADD COLUMN phone VARCHAR(15)`。这里有明显方言差异：PostgreSQL 的 `ALTER TABLE` 功能很全（改类型、加删约束、改默认值等）；SQLite 的 `ALTER TABLE` 历史上很受限，目前只支持 `RENAME TABLE`、`RENAME COLUMN`、`ADD COLUMN`、`DROP COLUMN`（`DROP COLUMN` 自 SQLite 3.35.0 起，2021），改列类型这类操作需靠"建新表→拷数据→换名"的迂回做法。

### 03-2.1.2 数据类型与"标准强类型 vs SQLite 类型亲和性"

SQL 标准规定列必须声明数据类型，且是**静态强类型**：往 `INTEGER` 列写字符串会被拒绝。常见标准类型分几族——字符串（`CHAR(n)` 定长、`VARCHAR(n)` 变长）、精确数值（`INTEGER`、`SMALLINT`、`NUMERIC(p,s)`/`DECIMAL(p,s)`）、近似数值（`REAL`、`DOUBLE PRECISION`/`FLOAT`）、日期时间（`DATE`、`TIME`、`TIMESTAMP`）、布尔（`BOOLEAN`，SQL:1999 引入）。PostgreSQL 基本遵循标准并大幅扩展（`TEXT`、`SERIAL`、`JSONB`、数组、`UUID` 等）。

这里是本大主题最典型的规范 vs 实现分歧之一：**SQLite 不是静态强类型，而是"类型亲和性（type affinity）+ 动态类型"**。在普通表里，列声明的类型只是一个"倾向"，SQLite 实际允许你往任何列存任何类型的值（除 `INTEGER PRIMARY KEY` 等少数例外），它会尽量按亲和性转换、转不了就原样存。也就是说 SQLite 里 `age INTEGER` 列存进字符串 `'hello'` 不会报错。为收紧这一点，SQLite 3.37.0（2021）引入了 **STRICT 表**（建表时加 `) STRICT;`），此时列类型被严格执行、更接近标准。初学者从别的数据库转来学 SQLite 时最容易被这条坑到：以为类型能挡住脏数据，其实默认挡不住。反过来，PostgreSQL 的 `CHAR(n)` 会用空格把值补齐到 n 位（标准行为），而实务中几乎总用 `VARCHAR`/`TEXT`，`CHAR` 只在极特殊场景用。

### 03-2.1.3 完整性约束：PRIMARY KEY / UNIQUE / NOT NULL / CHECK / DEFAULT

约束（constraint）是写在 DDL 里、由数据库**自动强制**的数据规则，违反约束的增删改会被拒绝。五个最常用的是：`NOT NULL`（该列不能是 NULL）；`UNIQUE`（该列/列组的值在全表不重复，但标准里允许多行 NULL 因为 NULL≠NULL）；`PRIMARY KEY`（主键，等价于 `UNIQUE` + `NOT NULL`，每表至多一个，作行的唯一标识）；`CHECK (条件)`（每行必须让条件为真，如 `CHECK (salary > 0)`）；`DEFAULT 值`（插入时没给该列就用默认值）。

约束的价值在于把"数据必须满足的规则"从应用代码下沉到数据库，无论谁从哪条路径写数据都绕不过去，这是关系数据库保证数据质量的核心手段。初学者常见困惑：`UNIQUE` 和 `PRIMARY KEY` 都保证唯一，区别在主键还隐含非空且全表唯一一个、通常是行的"身份证"，而 `UNIQUE` 可以有多个、且（标准下）允许 NULL。本机实测 SQLite 的 `CHECK` 确实被强制——插入违反 `CHECK (n>0)` 的行抛 `IntegrityError`：

```
CREATE TABLE c(n INTEGER CHECK (n>0));  INSERT INTO c VALUES (-1);
-> IntegrityError  (SQLite 3.45.1 @2026-07-29)
```

一个方言注意点：`CHECK` 约束里 SQL 标准允许引用子查询，但 PostgreSQL 和 SQLite 都**不支持** `CHECK` 中用子查询（只能引用本行的列），跨行/跨表的复杂规则要用触发器或断言（`ASSERTION` 标准里有但几乎没有数据库实现）。

### 03-2.1.4 外键与参照完整性（含 SQLite 默认不强制的陷阱）

外键（`FOREIGN KEY`）约束让一张表的某列（组）的值必须在另一张表的主键/唯一列里存在，从而维护**参照完整性**——不允许出现"引用了不存在的东西"的悬空引用。声明形如：

```sql
FOREIGN KEY (dept_name) REFERENCES department (dept_name)
    ON DELETE CASCADE  ON UPDATE RESTRICT
```

`ON DELETE`/`ON UPDATE` 后跟的是**参照动作（referential action）**，规定当被引用行被删/改时怎么办：`CASCADE`（级联删/改子行）、`SET NULL`（把外键列置空）、`SET DEFAULT`（置为默认值）、`RESTRICT`/`NO ACTION`（有子行引用就禁止操作；两者细微差别在检查时机，`NO ACTION` 允许延迟到语句末检查）。这让"删一个系，它的老师怎么办"这类联动有了声明式答案。

本节是又一处关键的规范 vs 实现分歧：**SQLite 默认不强制外键**。SQLite 会解析并存储 `FOREIGN KEY` 声明，但除非在每个数据库连接上显式打开开关，它根本不检查。本机实测默认值确为关闭：

```
PRAGMA foreign_keys;  -> 0   (SQLite 3.45.1 @2026-07-29，默认关闭)
```

要启用需每连接执行 `PRAGMA foreign_keys = ON;`。这是初学者用 SQLite 时的巨坑：以为写了外键就有保护，实际默认形同虚设、能插入悬空引用。对照之下，PostgreSQL 默认强制外键、无需任何开关，且外键检查可声明为 `DEFERRABLE INITIALLY DEFERRED` 以延迟到事务提交时才校验（便于处理循环引用）。

#### 来源与时效
- DBSC 7e §3.2（基本类型）、§4.4（完整性约束：not null/unique/check/参照完整性与 referential action）、§3.6/§4.7（DDL、SQL 数据类型），核实 2026-07-29。
- CMU 15-445 Spring 2026 L02「Modern SQL」讲义（DDL 与约束概览），核实 2026-07-29。
- SQL 标准 ISO/IEC 9075（`CREATE/ALTER TABLE`、约束、参照动作、静态强类型语义；`TRUNCATE` SQL:2008、`BOOLEAN` SQL:1999）——措辞级比对，核实 2026-07-29。
- PostgreSQL 17 官方文档「Data Definition」章（Constraints、Foreign Keys、`ALTER TABLE`、`DEFERRABLE`），核实 2026-07-29。
- SQLite 官方文档：`datatype3`（类型亲和性）、`stricttables`（STRICT 表，3.37.0）、`foreignkeys`（默认不强制、`PRAGMA foreign_keys`）、`lang_altertable`（受限 ALTER，`DROP COLUMN` 3.35.0），核实 2026-07-29。
- 本机实证：Python `sqlite3` 3.45.1，`PRAGMA foreign_keys` 默认 0、`CHECK` 违反抛 `IntegrityError`（@2026-07-29）。
- 冲突项：类型系统（标准/PG 静态强类型 vs SQLite 亲和性动态类型，STRICT 表可收紧）；外键强制（PG 默认开 vs SQLite 默认关，需 `PRAGMA foreign_keys=ON`）；`ALTER TABLE` 能力（PG 全 vs SQLite 受限）——三处两边均记。

## 03-2.2 基础 DML：SELECT-FROM-WHERE、集合运算、DISTINCT/排序

### 03-2.2.1 SELECT-FROM-WHERE 三部曲与逻辑处理顺序

DML（Data Manipulation Language）里最核心的是查询语句 `SELECT`。最基础的单表查询由三个子句构成：`SELECT`（要哪些列/表达式）、`FROM`（从哪张表）、`WHERE`（保留满足条件的行）。例如：

```sql
SELECT name, salary
FROM   instructor
WHERE  dept_name = 'Comp. Sci.' AND salary > 70000;
```

理解 SQL 的关键不是它的书写顺序，而是它的**逻辑处理顺序（概念求值顺序）**：

```
FROM → WHERE → GROUP BY → HAVING → SELECT → DISTINCT → ORDER BY → LIMIT/OFFSET
```

也就是说数据库概念上先从 `FROM` 拿到行、用 `WHERE` 过滤、（若有）分组聚合、再算 `SELECT` 里的表达式、最后排序取前几行。这个顺序解释了初学者最常撞的两个报错：其一，`WHERE` 里不能用 `SELECT` 中定义的列别名（因为 `WHERE` 先于 `SELECT` 求值，别名还不存在）；其二，`WHERE` 里不能直接写聚合函数（聚合发生在 `GROUP BY` 阶段，晚于 `WHERE`；过滤聚合结果要用后面讲的 `HAVING`）。注意这只是**语义上的**顺序，真正的执行由查询优化器决定（留大主题03-7/03-8）。另一个易错点：`SELECT *` 取所有列方便但生产中不推荐（列顺序/数量一变，依赖它的代码就坏）。

### 03-2.2.2 关系的集合语义 vs SQL 的多集（bag）语义与 DISTINCT

这是承接大主题03-1、贯穿全 SQL 的一条根本区别：**关系模型在理论上是集合语义（元组不重复），但 SQL 实现默认是多集（bag / multiset）语义（结果里允许重复行）**。所以 `SELECT dept_name FROM instructor` 会把每个系名按老师人数重复列出，而不是去重。要恢复"集合"式的去重，得显式写 `DISTINCT`：

```sql
SELECT DISTINCT dept_name FROM instructor;
```

为什么 SQL 默认不去重？因为去重要额外排序/散列、代价不小，而很多查询本就不需要去重；把去重交给用户显式声明更高效。初学者最该记住：**默认留重复、`DISTINCT` 才去重**，这是标准与所有主流实现一致的地方（DBSC 7e 明确对照关系代数的集合语义指出此差异）。一个和下一小主题相关的细节：`DISTINCT` 判断"重复"时，把多个 NULL 视为**彼此相等**（都算同一个值），因此 `SELECT DISTINCT` 只保留一个 NULL——这与 `WHERE` 里 `NULL = NULL` 结果为 unknown 的规则看似矛盾，实为标准对 DISTINCT/GROUP BY 特意规定的"NULL 互相不作区分"。本机实测（列 `k` 取值 NULL,NULL,1）：

```
SELECT count(*) FROM (SELECT DISTINCT k FROM g);  -> 2   (两个 NULL 合成一个)
```

### 03-2.2.3 集合运算：UNION / INTERSECT / EXCEPT 与 ALL 变体

SQL 提供在两个查询结果之间做集合运算的操作符：`UNION`（并）、`INTERSECT`（交）、`EXCEPT`（差，部分数据库叫 `MINUS`）。它们的默认行为与单表 `SELECT` 相反——**默认去重**（体现集合语义）；要保留重复得加 `ALL`，即 `UNION ALL`、`INTERSECT ALL`、`EXCEPT ALL`。例如：

```sql
SELECT course_id FROM section WHERE semester = 'Fall'
UNION
SELECT course_id FROM section WHERE semester = 'Spring';
```

参与集合运算的两个查询必须**列数相同、对应列类型兼容**（并集相容），结果列名取第一个查询的。初学者要抓住三点：一是 `UNION` 会去重、`UNION ALL` 不去重且更快（不需去重排序），能确定无重复或不在乎重复时优先 `UNION ALL`；二是这里的去重与前一节 `DISTINCT` 一样把 NULL 当相等；三是**方言差异**——PostgreSQL 三种运算及其 `ALL` 变体全支持；SQLite 支持 `UNION`/`UNION ALL`/`INTERSECT`/`EXCEPT`，但**不支持** `INTERSECT ALL` 和 `EXCEPT ALL`（待用其它写法模拟）。

### 03-2.2.4 排序 ORDER BY 与分页 LIMIT/OFFSET

`ORDER BY` 对结果行排序，默认升序（`ASC`），可写 `DESC` 降序，可按多列排序（先按第一列、并列再按第二列）：

```sql
SELECT name, salary
FROM   instructor
ORDER BY salary DESC, name ASC;
```

这里有一条必须记住的规则——**不写 `ORDER BY` 时，结果行的顺序是不确定的**（数据库可按任意物理顺序返回），绝不能依赖"看起来有序"。排序发生在逻辑顺序的很晚阶段（`SELECT` 之后），所以 `ORDER BY` 里可以用 `SELECT` 的列别名，也可以按列序号（如 `ORDER BY 2`，指第二个选择列，但可读性差不推荐）。

NULL 在排序中的位置需要留意：SQL 标准把 NULL 视为"比所有值大或都小"由实现和 `NULLS FIRST`/`NULLS LAST` 子句决定。PostgreSQL 默认 `ASC` 时 NULL 排在最后（`NULLS LAST`）、`DESC` 时排最前，且支持显式 `NULLS FIRST/LAST`；SQLite 默认把 NULL 视为最小（`ASC` 时排最前），也支持 `NULLS FIRST/LAST`（3.30.0 起）——**方言差异**，跨库时别假设 NULL 的位置。分页用 `LIMIT n`（取前 n 行）配 `OFFSET m`（跳过前 m 行）；`LIMIT/OFFSET` 是 PostgreSQL/SQLite/MySQL 的通用写法，SQL 标准的对应语法是 `OFFSET m ROWS FETCH FIRST n ROWS ONLY`（SQL:2008，PostgreSQL 支持、SQLite 不支持——又一方言差异）。

#### 来源与时效
- DBSC 7e §3.3（基本 SELECT-FROM-WHERE）、§3.5（集合运算 union/intersect/except 默认去重、ALL 保留重复）、§3.7（`ORDER BY`、DISTINCT），核实 2026-07-29。
- CMU 15-445 Spring 2026 L02「Modern SQL」（查询基础、集合运算、输出控制/排序分页），核实 2026-07-29。
- SQL 标准 ISO/IEC 9075（`<query specification>` 逻辑求值语义、`UNION/INTERSECT/EXCEPT [ALL]`、`ORDER BY`、`FETCH FIRST … ROWS ONLY` SQL:2008、`NULLS FIRST/LAST`），核实 2026-07-29。
- PostgreSQL 17 官方文档「Queries」（`SELECT` 列表、`WHERE`、组合查询、`ORDER BY` 默认 `NULLS LAST`、`LIMIT`/`FETCH`），核实 2026-07-29。
- SQLite 官方文档 `lang_select`（compound select 不支持 `INTERSECT ALL`/`EXCEPT ALL`、`ORDER BY` NULL 默认最小、`LIMIT`/`OFFSET`），核实 2026-07-29。
- 本机实证：SQLite 3.45.1 `SELECT DISTINCT` 把两个 NULL 合并为一（@2026-07-29）。
- 冲突项：集合运算 ALL 变体（PG 全支持 vs SQLite 缺 `INTERSECT ALL`/`EXCEPT ALL`）；NULL 排序默认位置（PG `NULLS LAST`@ASC vs SQLite NULL 最小@ASC）；分页语法（`FETCH FIRST` PG 支持、SQLite 不支持）——均两边记。

## 03-2.3 连接与聚合

### 03-2.3.1 连接类型：CROSS / INNER / LEFT / RIGHT / FULL OUTER

连接（join）把多张表按条件横向拼接成更宽的结果。类型谱系是：`CROSS JOIN`（笛卡尔积，每行两两配对，无条件）；`INNER JOIN … ON 条件`（只保留满足条件的配对行）；三种外连接 `LEFT`/`RIGHT`/`FULL [OUTER] JOIN`（在内连接基础上，把在另一侧找不到匹配的行也保留，缺失的一侧列填 NULL）。一个内连接例子：

```sql
SELECT s.name, t.title
FROM   student s
JOIN   takes  t ON s.ID = t.ID;
```

初学者先建立直觉：`INNER JOIN` 只留"两边都对得上"的行；`LEFT JOIN` 额外保留左表所有行（右表没匹配就填 NULL），常用来做"主表全保留、附信息可有可无"的查询（如"列出所有学生及其选课，没选课的也要列出来"）；`RIGHT JOIN` 是左右对调的 `LEFT JOIN`；`FULL OUTER JOIN` 两边不匹配的行都保留。**方言差异**：PostgreSQL 一直全支持这五类；SQLite 历史上只支持 `LEFT JOIN`，直到 **SQLite 3.39.0（2022）才加入 `RIGHT JOIN` 和 `FULL OUTER JOIN`**。本机 SQLite 3.45.1 实测 `FULL OUTER JOIN` 可用：

```
FULL OUTER JOIN 结果 -> [(None, 3), (1, None), (2, 2)]   (SQLite 3.45.1 @2026-07-29)
```

即左表 {1,2}、右表 {2,3} 全连接得到匹配行 (2,2) 与两侧各自的孤儿行 (1,NULL)、(NULL,3)。

### 03-2.3.2 连接条件写法：ON / USING / NATURAL 与自连接

连接条件有三种写法。`ON 布尔条件` 最通用、最清晰（`ON a.x = b.y`）。`USING (col, …)` 是当两表要连的列**同名**时的简写，且结果里该列**只出现一次**（`ON` 写法则两列都保留）。`NATURAL JOIN` 更激进——自动把两表**所有同名列**作为连接条件、并合并同名列，不用写任何条件。

```sql
SELECT * FROM takes NATURAL JOIN course;
```

`NATURAL JOIN` 看着省事，却是公认的易错点/反模式：它依赖"同名即应连接"的隐含假设，一旦两表恰好有个无关的同名列（如都有 `id` 或 `created_at`），连接条件就悄悄变了、结果错得没有报错提示。教辅建议初学者优先用显式 `ON`，少用 `NATURAL`。另外，一张表可以和自己连接（**自连接**），通过起不同别名区分，用于"同表内行与行的比较"，如找"和某人同系的其他人"。

### 03-2.3.3 聚合函数：COUNT / SUM / AVG / MIN / MAX（含 NULL 处理）

聚合函数把**多行**压成**一个值**：`COUNT`（计数）、`SUM`（求和）、`AVG`（平均）、`MIN`/`MAX`（最小/最大）。关键语义（承接三值逻辑，与 03-2.5 呼应）：**除 `COUNT(*)` 外，聚合函数都忽略 NULL**。即 `COUNT(col)` 只数非 NULL 的行，`AVG(col)` 的分母也只算非 NULL 的行。本机实测（列 v 取值 10, NULL, 30）：

```
COUNT(*) -> 3      COUNT(v) -> 2      SUM(v) -> 40      AVG(v) -> 20.0
```

`COUNT(*)=3` 数所有行，`COUNT(v)=2` 跳过 NULL；`AVG(v)=20` 是 40/2 而非 40/3——**分母不含 NULL 行**，这是初学者算平均值时最常算错的地方。此外可用 `COUNT(DISTINCT col)`、`SUM(DISTINCT col)` 先去重再聚合。一个边界：对空结果集，`COUNT` 返回 0，但 `SUM`/`AVG`/`MIN`/`MAX` 返回 **NULL**（不是 0）。

### 03-2.3.4 GROUP BY 与 HAVING

`GROUP BY` 把行按指定列的值分组，然后**每组**算一个聚合值；`HAVING` 则是"作用在分组之后的 WHERE"，用来过滤**组**（而 `WHERE` 过滤的是分组前的行）。典型形态：

```sql
SELECT dept_name, AVG(salary) AS avg_sal
FROM   instructor
WHERE  salary > 0            -- 先按行过滤（分组前）
GROUP BY dept_name
HAVING AVG(salary) > 42000   -- 再按组过滤（分组后）
ORDER BY avg_sal DESC;
```

对照逻辑处理顺序就清楚了：`WHERE`（分组前，不能含聚合）→ `GROUP BY`（分组）→ `HAVING`（分组后，可含聚合）→ `SELECT`。初学者最重要的一条硬规则：**`SELECT` 列表里，凡不在聚合函数内的列，必须都出现在 `GROUP BY` 里**（否则语义不明——一组里非分组列可能有多个不同值，不知道该显示哪个）。SQL 标准和 PostgreSQL 严格执行此规则并报错；SQLite 相对宽松、允许这种写法并从组内**任意**取一行的值（**方言差异**，且这种"任取"是不确定行为，别依赖）。另外 `GROUP BY` 把 NULL 归为同一组（与 `DISTINCT` 一致），本机实测（k 取值 NULL,NULL,1）：

```
SELECT k, count(*) FROM g GROUP BY k;  -> [(None, 2), (1, 1)]   (两个 NULL 归一组)
```

`HAVING` 也可不配 `GROUP BY` 单独用——此时整张表当作一个组，本机实测 `SELECT count(*) FROM t HAVING count(*)>1` 在两行表上返回 `2`。

### 03-2.3.5 高级聚合：GROUPING SETS / ROLLUP / CUBE、FILTER（方言）

标准 SQL（SQL:1999/2003）提供更强的分组：`GROUPING SETS`（一次算多组不同维度的聚合）、`ROLLUP`（层级小计+总计，如按 (系, 课) 再给按系小计和总计）、`CUBE`（所有维度组合的交叉汇总）。还有 `FILTER (WHERE 条件)` 子句（SQL:2003），可给单个聚合加过滤条件，如：

```sql
SELECT COUNT(*) AS total,
       COUNT(*) FILTER (WHERE salary > 80000) AS high_paid
FROM   instructor;
```

这些属于"进阶但值得知道"的广度项。**方言差异**要点：`ROLLUP`/`CUBE`/`GROUPING SETS` PostgreSQL 全支持（9.5 起），SQLite **不支持**；`FILTER` 子句 PostgreSQL 支持，SQLite 也支持（3.30.0 起，2019）。初学者阶段能认得这些关键字、知道 `ROLLUP` 是"带小计的分组"、`FILTER` 是"给聚合加条件"即可，不必深挖。

#### 来源与时效
- DBSC 7e §3.3.3（自然连接/连接概念）、§3.7（聚合 avg/min/max/sum/count、`GROUP BY`、`HAVING`、聚合忽略 NULL）、§4.1（连接类型 inner/outer、`ON`/`USING`/`NATURAL`），核实 2026-07-29。
- CMU 15-445 Spring 2026 L02「Modern SQL」（聚合与 `GROUP BY`/`HAVING`、连接、`FILTER`/`ROLLUP` 属现代 SQL 特性），核实 2026-07-29。
- SQL 标准 ISO/IEC 9075（`<joined table>` 的 inner/outer/`USING`/`NATURAL`、`GROUP BY` 与聚合语义、`GROUPING SETS`/`ROLLUP`/`CUBE` SQL:1999、`FILTER` SQL:2003、聚合忽略 NULL），核实 2026-07-29。
- PostgreSQL 17 官方文档「Queries: Table Expressions」（join 类型/`USING`/`NATURAL`）、「Aggregate Functions」与「GROUPING SETS, CUBE, ROLLUP」、「Aggregate Expressions」（`FILTER`），核实 2026-07-29。
- SQLite 官方文档 `lang_select`（`RIGHT`/`FULL JOIN` 自 3.39.0；`GROUP BY` 中非聚合列宽松取值）、`lang_aggfunc`（`FILTER` 自 3.30.0），核实 2026-07-29。
- 本机实证：SQLite 3.45.1 `FULL OUTER JOIN`、`COUNT(*)`=3/`COUNT(v)`=2/`AVG(v)`=20、`GROUP BY` NULL 归一组、无 `GROUP BY` 的 `HAVING`（@2026-07-29）。
- 冲突项：`FULL/RIGHT JOIN`（PG 一直有 vs SQLite 3.39.0 起）；`SELECT` 非聚合列约束（标准/PG 严格报错 vs SQLite 宽松任取）；`ROLLUP/CUBE/GROUPING SETS`（PG 支持 vs SQLite 不支持）——均两边记。

## 03-2.4 子查询与 CTE

### 03-2.4.1 标量/表子查询与它们能出现的位置

子查询（subquery）是嵌在另一条 SQL 语句里的 `SELECT`。按返回形状分：**标量子查询**（返回单行单列的一个值，可用在任何需要单值的地方，如 `SELECT` 列表或 `WHERE` 的比较右侧）；**行/表子查询**（返回多行，常用在 `FROM`（叫派生表 derived table，须起别名）或集合谓词 `IN`/`EXISTS` 里）。例如用标量子查询做"高于全校平均工资"：

```sql
SELECT name, salary
FROM   instructor
WHERE  salary > (SELECT AVG(salary) FROM instructor);
```

初学者的直觉抓手：子查询就是"先算一个中间结果，再拿它去参与外层查询"。放在 `FROM` 里的子查询（派生表）相当于一张临时表，必须起别名才能被引用。一个易错点：标量子查询若实际返回了多行，会运行时报错（"more than one row"）——它要求你保证只返回一个值。

### 03-2.4.2 相关子查询（correlated）vs 非相关子查询

非相关子查询独立于外层、只需算一次；**相关子查询**则在其内部**引用了外层查询的列**，因此概念上要对外层的**每一行**重新求值一次。例如"找出比本系平均工资高的老师"，内层的平均是"本系"的，依赖外层当前行的 `dept_name`：

```sql
SELECT name, dept_name, salary
FROM   instructor AS i1
WHERE  salary > (SELECT AVG(salary)
                 FROM   instructor AS i2
                 WHERE  i2.dept_name = i1.dept_name);  -- 引用外层 i1
```

关键在于识别"内层是否用到了外层的别名/列"——用到了就是相关子查询。教辅要点：相关子查询在概念上是"逐行嵌套循环"，直觉上较慢（但优化器常能改写成连接，实际未必慢，留大主题03-8）。初学者写相关子查询时最容易忘的是给内外层表起不同别名以消歧义。

### 03-2.4.3 EXISTS / NOT EXISTS 与 IN / NOT IN

`IN`/`NOT IN` 判断某值是否（不）在一个集合/子查询结果里；`EXISTS`/`NOT EXISTS` 判断一个（通常相关的）子查询是否（不）返回任何行——`EXISTS` 只关心"有没有行"，不关心行的内容，所以里面常写 `SELECT 1`。本机实测相关 `EXISTS`：

```
SELECT id FROM t x WHERE EXISTS (SELECT 1 FROM g WHERE g.k = x.id);  -> [(1,)]
```

这里最重要、最容易埋雷的教辅点是 **`NOT IN` 遇到 NULL 的陷阱**（与三值逻辑 03-2.5 强相关）：如果 `NOT IN` 的集合里含有 NULL，整个 `NOT IN` 对任何值都不会返回 TRUE，而是返回 unknown，导致该行被 `WHERE` 过滤掉——查询悄悄少了行还不报错。本机实测：

```
SELECT (2 NOT IN (1, NULL)) IS NULL;  -> 1   (即结果是 unknown，不是 TRUE)
```

背后的原因是 `2 NOT IN (1, NULL)` 等价于 `2<>1 AND 2<>NULL`，后半是 unknown，`TRUE AND unknown = unknown`。因此凡子查询列可能有 NULL，**优先用 `NOT EXISTS` 而不是 `NOT IN`**——`NOT EXISTS` 基于"有没有行"判断，不受 NULL 三值逻辑干扰，语义更稳。这是老手也常踩的坑，务必记住。

### 03-2.4.4 WITH：公用表表达式（CTE）与可读性

CTE（Common Table Expression，公用表表达式）用 `WITH 名字 AS (子查询)` 在查询开头定义一个或多个命名的临时结果集，后面像用表一样引用它。它主要解决可读性——把嵌套很深的子查询"拉平"成自上而下的若干步骤：

```sql
WITH dept_avg AS (
    SELECT dept_name, AVG(salary) AS avg_sal
    FROM   instructor
    GROUP BY dept_name
)
SELECT i.name, i.salary, d.avg_sal
FROM   instructor i JOIN dept_avg d ON i.dept_name = d.dept_name
WHERE  i.salary > d.avg_sal;
```

CTE 是 SQL:1999 引入、PostgreSQL 与 SQLite 都支持的标准特性。教辅要点：CTE 让复杂查询"读起来像一步步推导"，可定义多个（逗号分隔），后面的可引用前面的。一个进阶但重要的实现差异（**方言/版本**）：PostgreSQL **12 之前** CTE 总是被"物化"（当作优化屏障、先算好再用），12 起默认对只被引用一次的非递归 CTE 做**内联**（可用 `MATERIALIZED`/`NOT MATERIALIZED` 显式控制）；这影响性能而非结果，初学者了解"新版 PG 的 CTE 不再天然是优化屏障"即可。

### 03-2.4.5 递归 CTE：WITH RECURSIVE

在 `WITH` 后加 `RECURSIVE`，CTE 可以引用自己，从而表达**层级/图遍历**这类原本 SQL 难以表达的计算（如组织树、物料清单、图的可达性）。结构固定为"基础查询 `UNION [ALL]` 引用自身的递归查询"：

```sql
WITH RECURSIVE counter(n) AS (
    SELECT 1                      -- 基础：起点
    UNION ALL
    SELECT n + 1 FROM counter WHERE n < 5   -- 递归：每步引用上一步
)
SELECT n FROM counter;
```

本机实测该递归 CTE 生成 1..5：

```
group_concat(n) -> '1,2,3,4,5'   (SQLite 3.45.1 @2026-07-29)
```

它的工作过程可以这样直观理解——先跑一次基础查询得到初始行，再反复跑递归部分（每次拿"上一轮新产生的行"当输入），直到不再产生新行为止，把所有轮次的结果并起来。递归 CTE 是 SQL:1999 标准，PostgreSQL 和 SQLite 都支持。初学者最常见的错是忘了写终止条件（`WHERE n < 5`）导致无限递归——务必保证递归部分最终会停。

#### 来源与时效
- DBSC 7e §3.8（嵌套子查询：`in`/`some`/`all`/`exists`、相关子查询、`from` 子查询、标量子查询）、§4.5（`WITH` 子句/CTE），核实 2026-07-29。
- CMU 15-445 Spring 2026 L02「Modern SQL」（嵌套查询、CTE 含递归 CTE 示例），核实 2026-07-29。
- SQL 标准 ISO/IEC 9075（`<subquery>`、`EXISTS`/`IN`/quantified comparison、`WITH [RECURSIVE]` SQL:1999），核实 2026-07-29。
- PostgreSQL 17 官方文档「WITH Queries (Common Table Expressions)」（递归 CTE、`MATERIALIZED`/`NOT MATERIALIZED`、12 起默认内联），核实 2026-07-29。
- SQLite 官方文档 `lang_with`（CTE 与 `RECURSIVE`）、`lang_expr`（`EXISTS`/`IN`/子查询），核实 2026-07-29。
- 本机实证：SQLite 3.45.1 相关 `EXISTS`、`2 NOT IN (1,NULL)` 结果为 unknown、递归 CTE 生成 1..5（@2026-07-29）。
- 冲突项：CTE 优化行为（PG<12 总物化 vs PG≥12 默认内联；SQLite 有自身内联启发式）——版本相关，两边记，属性能非结果差异。

## 03-2.5 NULL 与三值逻辑

### 03-2.5.1 NULL 是什么：不是零、不是空串，而是"未知/不适用"

NULL 是 SQL 用来表示"缺失/未知/不适用"的特殊标记。它**不是**数字 0、**不是**空字符串 `''`、**不是**任何具体值——它表示"这里没有值"或"值未知"。正因如此，任何普通算术或比较只要碰到 NULL，结果通常也变成"未知"。例如：

```sql
SELECT 5 + NULL;      -- 结果 NULL（不知道加数是几，和自然是未知）
SELECT NULL = NULL;   -- 结果 NULL（两个未知是否相等？未知）
```

初学者要立刻扭转一个直觉误区：`NULL = NULL` **不是 TRUE**。因为 NULL 代表未知值，"两个未知值是否相等"这件事本身也是未知。这条规则是后面所有"反直觉"行为的总根源。本机实测：

```
SELECT (NULL = NULL) IS NULL;  -> 1   (即 NULL=NULL 的结果是 NULL/unknown)
SELECT (1 = NULL) IS NULL;     -> 1
```

### 03-2.5.2 三值逻辑：TRUE / FALSE / UNKNOWN

因为比较可能产生"未知"，SQL 的布尔逻辑不是两值而是**三值逻辑（three-valued logic）**：TRUE、FALSE、UNKNOWN。`AND`/`OR`/`NOT` 按下表运算（记住两条捷径即可）：

```
UNKNOWN AND FALSE = FALSE      -- 只要有一个 FALSE，AND 就是 FALSE
UNKNOWN OR  TRUE  = TRUE       -- 只要有一个 TRUE，OR 就是 TRUE
UNKNOWN AND TRUE  = UNKNOWN
UNKNOWN OR  FALSE = UNKNOWN
NOT UNKNOWN       = UNKNOWN
```

可以这样把握直觉——`AND` 遇到一个确定的 FALSE 就能拍板 FALSE（不管另一个是否未知），`OR` 遇到一个确定的 TRUE 就能拍板 TRUE；其余含 UNKNOWN 的组合结果仍是 UNKNOWN。本机实测两条捷径如下：

```
SELECT (NULL OR 1);    -> 1   (unknown OR true = true)
SELECT (NULL AND 0);   -> 0   (unknown AND false = false)
```

这套逻辑是 SQL 标准明确规定的，PostgreSQL 和 SQLite 一致遵循（SQLite 用 0/1 表示 false/true，NULL 表示 unknown）。

### 03-2.5.3 WHERE / JOIN 只保留 TRUE 的行（UNKNOWN 被丢弃）

三值逻辑最重要的实际后果：**`WHERE`（及 `ON`、`HAVING`）只保留条件求值为 TRUE 的行；求值为 FALSE 或 UNKNOWN 的行都被丢弃**。也就是说 UNKNOWN 在过滤时和 FALSE 一样待遇——被排除。本机实测（列 v 取值 10, NULL, 30，条件 `v <> 10`）：

```
SELECT id FROM t WHERE v <> 10;  -> [(3,)]   (只剩 id=3)
```

注意 id=2 那行（v 为 NULL）被排除了：`NULL <> 10` 结果是 UNKNOWN，不是 TRUE，所以不保留。这就是初学者最常被坑的地方——你以为 `WHERE v <> 10` 会返回"所有不等于 10 的行（含未知的）"，实际 NULL 行被悄悄漏掉。一个经典陷阱是想"取出全部行"却写成 `WHERE v = 10 OR v <> 10`——NULL 行两个条件都 UNKNOWN，仍被漏掉。要包含 NULL 行必须显式写 `OR v IS NULL`。

### 03-2.5.4 判空只能用 IS NULL / IS NOT NULL

正因为 `= NULL` 永远得 UNKNOWN（不会是 TRUE），**判断一个值是否为 NULL 绝不能用 `= NULL`，必须用 `IS NULL` / `IS NOT NULL`**：

```sql
SELECT name FROM instructor WHERE salary IS NULL;      -- 正确
-- WHERE salary = NULL   -- 错！永远匹配不到任何行
```

`IS NULL` 是专门的谓词，它返回确定的 TRUE/FALSE（不是三值），能可靠地把 NULL 行挑出来。这是初学者最该形成的肌肉记忆：**看到"是不是空"，就写 `IS [NOT] NULL`**。相关工具还有 `COALESCE(a, b, …)`（返回第一个非 NULL 的参数，常用来给 NULL 兜底默认值）和 `NULLIF(a, b)`（a=b 时返回 NULL，否则返回 a）——这两个是标准函数，PostgreSQL 和 SQLite 都支持。

### 03-2.5.5 NULL 在聚合、DISTINCT、GROUP BY、UNIQUE 中的"不一致"待遇

一个让初学者困惑的点：NULL 在不同场景下的"是否相等"待遇看似矛盾，其实是标准的分场景规定，值得单独记清：

聚合函数（除 `COUNT(*)`）**忽略** NULL——`COUNT(col)`/`SUM`/`AVG` 都跳过 NULL 行（本机实测 `COUNT(v)=2`、`AVG(v)=20`，见 03-2.3.3）。

`DISTINCT`、`GROUP BY`、集合运算（`UNION` 等）里，多个 NULL 被当作**彼此相等/不作区分**——`DISTINCT` 把多个 NULL 合成一个、`GROUP BY` 把 NULL 行归为一组（本机实测见 03-2.2.2、03-2.3.4）。

而 `UNIQUE` 约束和普通 `=` 比较里，NULL **不等于** 任何值（含另一个 NULL）——所以标准下 `UNIQUE` 列允许多行为 NULL（它们互不冲突）。

把这三条并排看：**"过滤/比较"场景 NULL 互不相等（走三值逻辑），"分组/去重"场景 NULL 互相视为同一个**。这不是矛盾，而是标准针对不同操作的语义分别规定。PostgreSQL 与 SQLite 在这些点上都遵循标准（`UNIQUE` 允许多 NULL 也一致；注意 PostgreSQL 15 起 `UNIQUE` 可加 `NULLS NOT DISTINCT` 选项把多个 NULL 视为冲突——**版本/方言差异**，默认仍是 NULLS DISTINCT）。

#### 来源与时效
- DBSC 7e §3.6（NULL 值、三值逻辑 unknown、`WHERE` 谓词为 unknown 视为 false 而排除、`is null`/`is not null`）、§3.7（聚合忽略 NULL、`DISTINCT`/`GROUP BY` 视 NULL 相等），核实 2026-07-29。
- CMU 15-445 Spring 2026 L02（NULL 处理与 `IS NULL` 属现代 SQL 基础），核实 2026-07-29。
- SQL 标准 ISO/IEC 9075（三值逻辑真值表、`<null predicate>`、`WHERE`/`HAVING` 保留 TRUE、聚合忽略 NULL、`DISTINCT`/`GROUP BY` 的 NULL "not distinct"、`UNIQUE` 的 NULL 语义、`COALESCE`/`NULLIF`），核实 2026-07-29。
- PostgreSQL 17 官方文档「Comparison Functions and Operators」（`IS NULL`）、「Data Manipulation / Constraints」（`UNIQUE` 与 `NULLS NOT DISTINCT`，15 起），核实 2026-07-29。
- SQLite 官方文档 `nulls`（NULL 语义、三值逻辑、`IS NULL`、`UNIQUE`/`DISTINCT`/`GROUP BY` 中 NULL 处理），核实 2026-07-29。
- 本机实证：SQLite 3.45.1 `NULL=NULL`→unknown、`NULL OR 1`→1、`NULL AND 0`→0、`WHERE v<>10` 漏掉 NULL 行、`2 NOT IN (1,NULL)`→unknown（@2026-07-29）。
- 冲突项：`UNIQUE` 多 NULL 默认允许（标准/PG/SQLite 一致）但 PG15 起可选 `NULLS NOT DISTINCT` 收紧——记为版本差异。

## 03-2.6 视图、触发器、授权与窗口函数

### 03-2.6.1 视图（VIEW）与可更新性（标准 vs 实现）

视图是一条被命名的查询，`CREATE VIEW v AS SELECT …` 后，`v` 可像表一样被查询，但它本身不存数据（每次查它时执行底层查询）。视图用于封装复杂查询、简化访问、做权限隔离（只暴露部分列/行）。

```sql
CREATE VIEW faculty AS
    SELECT ID, name, dept_name FROM instructor;
```

视图的核心难点是**可更新性**——能不能对视图做 `INSERT`/`UPDATE`/`DELETE`（并作用到底层表）？这是又一处规范 vs 实现大分歧。SQL 标准规定"足够简单"的视图（单表、无聚合/`DISTINCT`/`GROUP BY`/集合运算等）是自动可更新的。PostgreSQL 遵循此思路：**简单视图（9.3 起）自动可更新**，复杂视图需定义 `INSTEAD OF` 触发器或规则来支持更新，还可加 `WITH CHECK OPTION` 防止更新出"从视图里看不见的行"。而 **SQLite 的视图一律只读**——任何对视图的写操作直接报错，除非你为它定义 `INSTEAD OF` 触发器。本机实测 SQLite 视图不可直接写：

```
INSERT INTO v VALUES (3);
-> OperationalError: cannot modify v because it is a view   (SQLite 3.45.1 @2026-07-29)
```

初学者记住结论即可：**视图默认是"只读的查询快捷方式"；能不能写、怎么写，强烈依赖数据库**（PG 简单视图能写、SQLite 一律要靠 `INSTEAD OF` 触发器）。另有"物化视图（materialized view）"把结果真正存下来、需刷新，PostgreSQL 支持、SQLite 不支持（**方言差异**）。

### 03-2.6.2 触发器（TRIGGER）：BEFORE / AFTER / INSTEAD OF

触发器是"当某表发生 `INSERT`/`UPDATE`/`DELETE` 时自动执行"的一段过程逻辑，用于审计、维护派生数据、强制复杂业务规则等。三个时机：`BEFORE`（操作前，可改将写入的值或阻止操作）、`AFTER`（操作后，常用于记录/联动）、`INSTEAD OF`（"替代"，专用于视图，把对视图的写"翻译"成对底层表的写）。粒度上分**行级**（`FOR EACH ROW`，每受影响行触发一次）和**语句级**（`FOR EACH STATEMENT`，每条语句触发一次）。

```sql
CREATE TRIGGER log_salary_change
AFTER UPDATE OF salary ON instructor
FOR EACH ROW
WHEN (NEW.salary <> OLD.salary)
BEGIN
    INSERT INTO salary_log VALUES (OLD.ID, OLD.salary, NEW.salary);
END;
```

触发器体里用 `OLD`/`NEW` 引用被改行的旧值/新值（`INSERT` 只有 `NEW`，`DELETE` 只有 `OLD`）。**方言差异**要点：PostgreSQL 支持行级和语句级、`BEFORE`/`AFTER`/`INSTEAD OF`，触发器体须调用一个返回 `trigger` 的函数（用 PL/pgSQL 等语言写）；SQLite 支持 `BEFORE`/`AFTER`/`INSTEAD OF` 但**只有行级**（`FOR EACH ROW`，不支持语句级），触发器体直接写 SQL 语句序列。初学者层面掌握"触发器 = 自动响应表变更的钩子、有前/后/替代三种时机、能读 OLD/NEW"即可，别一开始就重度依赖触发器（逻辑藏在数据库里、难调试）。

### 03-2.6.3 授权：GRANT / REVOKE 与角色（标准 vs SQLite 无授权）

DCL（Data Control Language）里 `GRANT` 授予权限、`REVOKE` 收回权限，控制"谁能对哪个对象做什么"。权限粒度包括 `SELECT`/`INSERT`/`UPDATE`/`DELETE`/`REFERENCES` 等，可授给用户或**角色（role）**（角色是权限的集合，可授给用户，便于批量管理）：

```sql
GRANT SELECT, INSERT ON instructor TO some_role;
REVOKE INSERT ON instructor FROM some_role;
```

`WITH GRANT OPTION` 允许被授权者再把权限转授他人。这里是最彻底的一处规范 vs 实现分歧：**SQLite 根本没有用户/权限/角色的概念——它是嵌入式库、无服务器无登录，也就没有 `GRANT`/`REVOKE`**。SQLite 的"访问控制"靠操作系统文件权限，外加可选的 C 层 `sqlite3_set_authorizer()` 回调。而 PostgreSQL 有完整的角色系统与 `GRANT`/`REVOKE`（遵循 SQL 标准并扩展）。初学者要理解：授权是"客户端-服务器型数据库"（PostgreSQL/MySQL/Oracle）才有的机制；用嵌入式的 SQLite 时，安全边界在文件系统层面，SQL 里没有授权语句。

### 03-2.6.4 窗口函数（OVER）：概念与 PARTITION BY / ORDER BY

窗口函数（window function，SQL:2003 引入）在**不折叠行**的前提下，对与当前行"相关的一组行（窗口）"做计算——每行仍然保留，但多出一列基于窗口算出的值。这与聚合的根本区别是：聚合 `GROUP BY` 把多行**压成一行**，窗口函数**保留所有行**、只是给每行附加一个跨行计算结果。语法核心是 `函数() OVER (窗口定义)`：

```sql
SELECT name, dept_name, salary,
       AVG(salary) OVER (PARTITION BY dept_name) AS dept_avg,
       RANK()      OVER (PARTITION BY dept_name ORDER BY salary DESC) AS rk
FROM   instructor;
```

`PARTITION BY` 把行分成若干"窗口分区"（类似 `GROUP BY` 但不折叠），函数在每个分区内独立算；`ORDER BY` 定义分区内的顺序（排名类和"累计"类函数需要它）。直觉抓手：窗口函数适合"既要明细行、又要每行旁边带上分组统计/排名/累计"的场景，如"列出每个人及其在本系的工资排名"。窗口函数是 SQL:2003 标准，PostgreSQL（8.4 起）与 SQLite（3.25.0 起，2018）都支持。

### 03-2.6.5 窗口函数分类：排名 / 聚合窗口 / 偏移函数

窗口函数按用途分三类。排名类：`ROW_NUMBER()`（每行唯一序号 1,2,3…，并列也不同号）、`RANK()`（并列同号、之后跳号）、`DENSE_RANK()`（并列同号、之后不跳号）、`NTILE(n)`（均分成 n 桶）。聚合窗口：把 `SUM`/`AVG`/`COUNT` 等加 `OVER` 变成窗口版（做分区统计或累计）。偏移/取值类：`LAG(col, k)`/`LEAD(col, k)`（取分区内前/后第 k 行的值，做同比环比）、`FIRST_VALUE`/`LAST_VALUE`/`NTH_VALUE`。本机实测 `ROW_NUMBER` vs `RANK`（id 序列 1,2,2,3）：

```
id | row_number | rank
 1 |     1      |  1
 2 |     2      |  2
 2 |     3      |  2      <- 并列：row_number 仍递增，rank 相同
 3 |     4      |  4      <- rank 跳号到 4（跳过 3）
```

这清楚展示三者区别：`ROW_NUMBER` 永远严格递增不并列；`RANK` 并列同号且后续跳号（此处从 2 跳到 4）；`DENSE_RANK`（未在此例）则会给 3 而不跳号。初学者最常混淆 `RANK` 与 `DENSE_RANK`——记"RANK 跳号、DENSE 不跳"。

### 03-2.6.6 窗口帧（frame）与默认帧陷阱

窗口"帧（frame）"精确规定当前行的窗口到底包含分区内哪些行，用 `ROWS`/`RANGE`/`GROUPS` + `BETWEEN … AND …` 指定，如：

```sql
SUM(salary) OVER (ORDER BY hire_date
                  ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW)
```

这里最关键、最反直觉的教辅点是**默认帧**：当写了 `ORDER BY` 但**没写帧子句**时，默认帧是

```
RANGE BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW
```

注意是 `RANGE` 而非 `ROWS`。`RANGE` 模式下，`CURRENT ROW` 包含**所有与当前行排序值相同的"同伴（peers）"**，所以有并列值时，这些并列行会共享同一个累计结果（而不是逐行递增）。本机实测（val 按 id 排序，id 有并列的 2）：

```
id | running_sum (默认帧, ORDER BY id)
 1 |   10
 2 |   35      <- id=2 的两行(20,5)是同伴，都拿到累计 10+20+5=35
 2 |   35
 3 |   65
```

若想要"严格逐行累计"（每行只加到自己为止），必须显式写 `ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW`。这是初学者做"累计求和/移动平均"时最隐蔽的坑——不写帧、又有并列值，结果会和预期的逐行累计不同。默认帧规则是 SQL 标准规定的，PostgreSQL 和 SQLite 都遵循（本机 SQLite 3.45.1 实测印证）。另外，无 `ORDER BY` 时默认帧是整个分区（所有行），此时聚合窗口就是"整分区的统计值"。

#### 来源与时效
- DBSC 7e §4.2（视图与视图可更新性、`WITH CHECK OPTION`）、§5.3（触发器 `before`/`after`、`OLD`/`NEW`、行级/语句级）、§4.6（授权 `GRANT`/`REVOKE`/角色/`WITH GRANT OPTION`）、§5.5（窗口函数 `OVER`/`PARTITION BY`/排名/帧），核实 2026-07-29。
- CMU 15-445 Spring 2026 L02（窗口函数 `OVER`/排名/`LAG`/`LEAD`、视图/CTE 属现代 SQL），核实 2026-07-29。
- SQL 标准 ISO/IEC 9075（可更新视图定义、`INSTEAD OF` 触发器、行级/语句级触发器、`GRANT`/`REVOKE`/角色 SQL:1999、窗口函数 `<window function>` SQL:2003、默认帧 `RANGE UNBOUNDED PRECEDING`、`RANK`/`DENSE_RANK`/`ROW_NUMBER`/`LAG`/`LEAD`），核实 2026-07-29。
- PostgreSQL 17 官方文档「CREATE VIEW」（自动可更新视图、`WITH CHECK OPTION`，9.3 起）、「Materialized Views」、「CREATE TRIGGER」（行级/语句级、`BEFORE`/`AFTER`/`INSTEAD OF`）、「GRANT」/「REVOKE」/「Roles」、「Window Functions」（8.4 起、默认帧、frame 子句），核实 2026-07-29。
- SQLite 官方文档 `lang_createview`（视图只读、需 `INSTEAD OF` 触发器）、`lang_createtrigger`（仅 `FOR EACH ROW`、`INSTEAD OF` 用于视图）、`windowfunctions`（3.25.0 起、frame、默认帧 RANGE）、`security`（无 GRANT，靠 `sqlite3_set_authorizer`/文件权限），核实 2026-07-29。
- 本机实证：SQLite 3.45.1 视图直接 `INSERT` 报错、`ROW_NUMBER` vs `RANK` 并列跳号、默认帧 `RANGE` 使同伴共享累计值（@2026-07-29）。
- 冲突项：视图可更新性（PG 简单视图自动可更新 vs SQLite 一律只读需 `INSTEAD OF`）；物化视图（PG 有 vs SQLite 无）；触发器粒度（PG 行级+语句级 vs SQLite 仅行级）；授权（PG 完整 `GRANT`/角色 vs SQLite 无授权体系）——均两边记。
```
