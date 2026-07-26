# Round3c · L6-05 分布式与云系统专题 · 报告 prompt（P2）

```text
[P2] L6-05·大主题1 分布式文件/对象存储 — 报告prompt
【任务】为「L6-05·大主题1 分布式文件/对象存储」产出一份 report-format v3（广度优先·教辅式）报告。先读 study/cs-kb/report-format.md（v3 模板）、study/cs-kb/brief.md、study/cs-kb/round3b-subtopics/g06-networks-distributed.md 中 L6-05 大主题 C1 那一节（小主题 1.1–1.5 + 一句话范围 + 本机实证机会），据此自足展开。

【v3 格式硬性】
- 标题层级严格 #→##→###：# 报告标题；## 小主题（章节）；### 每个内容项一节。
- ### 下不写「核心概念：」「辅助说明：」等任何标签，直接正文分段：先核心说明（是什么/定义/关键结论，正确），再充分辅助说明（面向初学者讲懂所需的直觉/为什么/最小例子/易错点/关联，必需且充分，不是点缀）。
- 协议流程/架构图/数据布局等结构性图示独占块（代码块或独立行），不内联埋进句子。
- 教辅口吻；广度优先——每个内容项都讲到初学者能懂，但不做专家级纵深（不写长篇机制深挖/推导/设计权衡长论）。

【小主题清单（##章节，逐一覆盖，下游可回读 round3b 的 C1 节取全内容项）】
- 1.1 GFS 架构：单 master + chunkserver、大文件分块、元数据集中。
- 1.2 GFS 一致性与租约：记录追加语义、chunk 租约与变更顺序。
- 1.3 一致性哈希与分区：哈希环、虚拟节点、最小再平衡。
- 1.4 Dynamo 无主复制与 quorum：sloppy quorum、hinted handoff、N/R/W 可调。
- 1.5 冲突检测与解决：向量时钟版本分歧、读修复、Merkle 树反熵。
防重：本课讲存储「集群架构」，存储「引擎内部」（LSM/B-tree/列存/向量化）属 L6-04，不越界。

【验证纪律（强制）】
- 多来源比对：每个内容项 ≥2 个独立来源交叉核对，优先一手（GFS SOSP2003 / Dynamo SOSP2007 原始论文、MIT 6.5840 lecture）；来源打架时两边都记、点明分歧、不和稀泥。
- 本机模拟可选：一致性哈希环迁移量、N/R/W 读写交集、Merkle 树 diff 可用 Python 玩具快速加固，但不替代多来源比对，也不逐项强制。
- 不编造：具体数值/默认值/参数来自比对通过的来源，否则标「待核」；每份标核实日期。
- 章节末集中列来源与时效，不逐项脚注。

【权威锚点清单（取自 round3a，L6-05）】
- MIT 6.5840（前 6.824，已更名）Distributed Systems，Spring 2026 schedule：https://pdos.csail.mit.edu/6.824/schedule.html（核实 2026-07-25）。
- GFS（SOSP 2003）、Dynamo（SOSP 2007）原始论文（承重）。
- DDIA《Designing Data-Intensive Applications》2nd ed（Kleppmann & Riccomini，2026-02 发行）作交叉参照。
- Drexel CS647 等研究生 reading list 交叉非承重。

【粒度判定】下游先判「1 份 or 拆 N 份 + 理由」（C1 含 GFS 系与 Dynamo 系两条机制线，若过长可 029-...-a/-b）。

【落盘】自己 Write 到 study/cs-kb/findings/029-distributed-cloud-systems.md（若拆分按 a/b 后缀）。报告不得残留工具标签/控制字符。
```

```text
[P2] L6-05·大主题2 复制状态机与协调服务落地 — 报告prompt
【任务】为「L6-05·大主题2 复制状态机与协调服务落地」产出一份 report-format v3（广度优先·教辅式）报告。先读 study/cs-kb/report-format.md（v3 模板）、study/cs-kb/brief.md、study/cs-kb/round3b-subtopics/g06-networks-distributed.md 中 L6-05 大主题 C2 那一节（小主题 2.1–2.5 + 一句话范围 + 本机实证机会），据此自足展开。

【v3 格式硬性】
- 标题层级严格 #→##→###：# 报告标题；## 小主题（章节）；### 每个内容项一节。
- ### 下不写任何标签，直接正文分段：先核心说明（是什么/定义/关键结论，正确），再充分辅助说明（面向初学者的直觉/为什么/最小例子/易错点/关联，必需且充分）。
- 复制拓扑/日志与快照/链式写传播等结构图独占块，不内联。
- 教辅口吻；广度优先——讲到初学者懂，不做专家纵深。

【小主题清单（##章节，逐一覆盖，下游可回读 round3b 的 C2 节取全内容项）】
- 2.1 日志压缩与快照：把共识做成 KV 时的状态快照与日志 GC 工程。
- 2.2 集群成员变更：joint consensus 安全增删节点配置。
- 2.3 线性一致读：ReadIndex/租约读避免读到陈旧值。
- 2.4 ZooKeeper/Chubby 协调服务：Zab、znode/watch 与锁/选主等协调原语。
- 2.5 链式复制：链式写传播 + 尾节点读的高吞吐强一致。
防重：承接 L5-01 D3/D4（复制状态机/共识「为什么对」），本课只讲「把共识做大做稳」的工程，不重证 safety/liveness。

【验证纪律（强制）】
- 多来源比对：每个内容项 ≥2 个独立来源，优先一手（Raft 论文相关工程扩展、ZooKeeper ATC2010、链式复制原始论文、MIT 6.5840 L6-9/L13 lecture）；冲突两边都记、点明分歧。
- 本机模拟可选：日志截断+快照、两阶段配置切换不脑裂、ReadIndex 读优化对拍可用 Python 玩具加固，不替代比对、不逐项强制。
- 不编造：数值/默认值/版本来自比对通过来源，否则标「待核」；标核实日期。
- 章节末集中列来源与时效。
- ⚙演进快：具体协调服务/共识库版本行为处标「⚙演进快·锚版本」，不作定论。

【权威锚点清单（取自 round3a，L6-05）】
- MIT 6.5840 Spring 2026 schedule：https://pdos.csail.mit.edu/6.824/schedule.html（核实 2026-07-25）。
- ZooKeeper（ATC 2010）原始论文；Raft（ATC 2014）作为承接锚（safety 证明归 L5-01）。
- DDIA 2nd ed（2026-02）交叉参照；Drexel CS647 reading list 非承重。

【粒度判定】下游先判「1 份 or 拆 N 份 + 理由」。

【落盘】自己 Write 到 study/cs-kb/findings/029-distributed-cloud-systems.md（多份按 a/b 后缀）。报告不得残留工具标签/控制字符。
```

```text
[P2] L6-05·大主题3 全球分布式事务与外部一致性 — 报告prompt
【任务】为「L6-05·大主题3 全球分布式事务与外部一致性」产出一份 report-format v3（广度优先·教辅式）报告。先读 study/cs-kb/report-format.md（v3 模板）、study/cs-kb/brief.md、study/cs-kb/round3b-subtopics/g06-networks-distributed.md 中 L6-05 大主题 C3 那一节（小主题 3.1–3.4 + 一句话范围 + 本机实证机会），据此自足展开。

【v3 格式硬性】
- 标题层级严格 #→##→###：# 报告标题；## 小主题（章节）；### 每个内容项一节。
- ### 下不写任何标签，直接正文分段：先核心说明，再充分辅助说明（面向初学者的直觉/为什么/最小例子/易错点/关联，必需且充分）。
- commit-wait 时间线、2PC over Paxos 组合、分片放置等结构图独占块，不内联。
- 教辅口吻；广度优先——讲到初学者懂，不做专家纵深。

【小主题清单（##章节，逐一覆盖，下游可回读 round3b 的 C3 节取全内容项）】
- 3.1 TrueTime 与有界时钟不确定性：GPS/原子钟给出时间区间 + commit-wait。
- 3.2 外部一致性：时间戳 + TrueTime 保证事务的全局外部序（承接 D5.1 线性一致）。
- 3.3 2PC over Paxos：Paxos 组做可靠参与者、其上跨组 2PC（承接 D7.2/D4）。
- 3.4 分片、目录与数据放置：tablet/directory 划分与就近放置。
防重：承接 L5-01 D7（2PC 理论）/D2（时间/逻辑序），本课讲把它们做成可跑的全球数据库，不重证理论。

【验证纪律（强制）】
- 多来源比对：每个内容项 ≥2 个独立来源，优先一手（Spanner OSDI2012 原始论文、MIT 6.5840 L12 lecture）；冲突两边都记、点明分歧。
- 本机模拟可选：带误差区间的时钟 + commit-wait 玩具可用 Python 加固，不替代比对、不逐项强制。
- 不编造：TrueTime 误差界/时钟参数等数值来自比对通过来源，否则标「待核」；标核实日期。
- 章节末集中列来源与时效。

【权威锚点清单（取自 round3a，L6-05）】
- MIT 6.5840 Spring 2026 schedule：https://pdos.csail.mit.edu/6.824/schedule.html（核实 2026-07-25）。
- Spanner（OSDI 2012）原始论文（承重）。
- DDIA 2nd ed（2026-02）交叉参照；Drexel CS647 reading list 非承重。

【粒度判定】下游先判「1 份 or 拆 N 份 + 理由」（C3 仅 4 小主题、单系统 Spanner，通常 1 份即可，理由须写明）。

【落盘】自己 Write 到 study/cs-kb/findings/029-distributed-cloud-systems.md。报告不得残留工具标签/控制字符。
```

```text
[P2] L6-05·大主题4 批处理数据处理框架 — 报告prompt
【任务】为「L6-05·大主题4 批处理数据处理框架」产出一份 report-format v3（广度优先·教辅式）报告。先读 study/cs-kb/report-format.md（v3 模板）、study/cs-kb/brief.md、study/cs-kb/round3b-subtopics/g06-networks-distributed.md 中 L6-05 大主题 C4 那一节（小主题 4.1–4.4 + 一句话范围 + 本机实证机会），据此自足展开。

【v3 格式硬性】
- 标题层级严格 #→##→###：# 报告标题；## 小主题（章节）；### 每个内容项一节。
- ### 下不写任何标签，直接正文分段：先核心说明，再充分辅助说明（面向初学者的直觉/为什么/最小例子/易错点/关联，必需且充分）。
- map/shuffle/reduce 数据流、RDD 血统 DAG、宽/窄依赖等结构图独占块，不内联。
- 教辅口吻；广度优先——讲到初学者懂，不做专家纵深。

【小主题清单（##章节，逐一覆盖，下游可回读 round3b 的 C4 节取全内容项）】
- 4.1 MapReduce 编程模型：map/shuffle/reduce 数据流抽象。
- 4.2 MapReduce 容错与落后者：任务重执行、backup task 缓解 straggler。
- 4.3 Spark RDD 与血统：不可变数据集、窄/宽依赖、血统重算容错。
- 4.4 DAG 调度与内存迭代：阶段划分、缓存中间结果加速迭代。
防重：编程模型/集群执行在本课；DDIA 综述视角/数据密集型整体叙事归 N-13，不越界。

【验证纪律（强制）】
- 多来源比对：每个内容项 ≥2 个独立来源，优先一手（MapReduce OSDI2004、Spark NSDI2012 原始论文、MIT 6.5840 L1 lecture、DDIA 2E 批处理章）；冲突两边都记、点明分歧。
- 本机模拟可选：单机 MapReduce wordcount、注入慢/失败任务重执行、惰性 DAG 复现 lineage 重算可用 Python 玩具加固，不替代比对、不逐项强制。
- 不编造：数值/默认值/版本来自比对通过来源，否则标「待核」；标核实日期。
- 章节末集中列来源与时效。
- ⚙演进快：Spark 等框架版本相关默认/API 处标「⚙演进快·锚版本」，不作定论。

【权威锚点清单（取自 round3a，L6-05）】
- MIT 6.5840 Spring 2026 schedule：https://pdos.csail.mit.edu/6.824/schedule.html（核实 2026-07-25）。
- MapReduce（OSDI 2004）、Spark/RDD（NSDI 2012）原始论文（承重）。
- DDIA《Designing Data-Intensive Applications》2nd ed（2026-02）批处理章（承重）。
- Drexel CS647 reading list 交叉非承重。

【粒度判定】下游先判「1 份 or 拆 N 份 + 理由」。

【落盘】自己 Write 到 study/cs-kb/findings/029-distributed-cloud-systems.md。报告不得残留工具标签/控制字符。
```

```text
[P2] L6-05·大主题5 流处理与数据密集型架构 — 报告prompt
【任务】为「L6-05·大主题5 流处理与数据密集型架构」产出一份 report-format v3（广度优先·教辅式）报告。先读 study/cs-kb/report-format.md（v3 模板）、study/cs-kb/brief.md、study/cs-kb/round3b-subtopics/g06-networks-distributed.md 中 L6-05 大主题 C5 那一节（小主题 5.1–5.5 + 一句话范围 + 本机实证机会），据此自足展开。

【v3 格式硬性】
- 标题层级严格 #→##→###：# 报告标题；## 小主题（章节）；### 每个内容项一节。
- ### 下不写任何标签，直接正文分段：先核心说明，再充分辅助说明（面向初学者的直觉/为什么/最小例子/易错点/关联，必需且充分）。
- 事件时间/水位线时间线、窗口切分、checkpoint 重放、lambda/kappa 架构等结构图独占块，不内联。
- 教辅口吻；广度优先——讲到初学者懂，不做专家纵深。

【小主题清单（##章节，逐一覆盖，下游可回读 round3b 的 C5 节取全内容项）】
- 5.1 流处理模型与消息日志：事件流、生产/消费、日志式消息（Kafka 心智）。
- 5.2 事件时间 vs 处理时间与水位线：乱序事件下的 watermark 触发。
- 5.3 窗口（滚动/滑动/会话）：无界流上按时间/会话切分的有界聚合。
- 5.4 exactly-once 与检查点容错：精确一次语义与状态快照（Flink 心智）。
- 5.5 批流统一与 lambda/kappa：批流融合的架构演进取舍。
防重：本课聚焦流处理机制；数据密集型系统整体叙事归 N-13，不越界。

【验证纪律（强制）】
- 多来源比对：每个内容项 ≥2 个独立来源，优先一手（DDIA 2E 流处理章、Dataflow/Kafka/Flink 官方文档或原始论文、MIT 6.5840 cache/Ray 邻接 lecture）；冲突两边都记、点明分歧。
- 本机模拟可选：append-only 日志 + offset 消费、乱序流 + watermark 推进、滚动/滑动窗口聚合、checkpoint + 故障重放不重计可用 Python 玩具加固，不替代比对、不逐项强制。
- 不编造：数值/默认值/版本来自比对通过来源，否则标「待核」；标核实日期。
- 章节末集中列来源与时效。
- ⚙演进快：Kafka/Flink/Dataflow 等框架与云服务版本相关行为处标「⚙演进快·锚版本」，不作定论。

【权威锚点清单（取自 round3a，L6-05）】
- DDIA《Designing Data-Intensive Applications》2nd ed（Kleppmann & Riccomini，2026-02 发行）流处理章（承重）。
- MIT 6.5840 Spring 2026 schedule：https://pdos.csail.mit.edu/6.824/schedule.html（核实 2026-07-25，cache/Ray 邻接）。
- Kafka/Flink/Dataflow 官方文档或原始论文作一手来源；Drexel CS647 reading list 非承重。

【粒度判定】下游先判「1 份 or 拆 N 份 + 理由」。

【落盘】自己 Write 到 study/cs-kb/findings/029-distributed-cloud-systems.md。报告不得残留工具标签/控制字符。
```

```text
[P2] L6-05·大主题6 集群资源调度与编排 — 报告prompt
【任务】为「L6-05·大主题6 集群资源调度与编排」产出一份 report-format v3（广度优先·教辅式）报告。先读 study/cs-kb/report-format.md（v3 模板）、study/cs-kb/brief.md、study/cs-kb/round3b-subtopics/g06-networks-distributed.md 中 L6-05 大主题 C6 那一节（小主题 6.1–6.5 + 一句话范围 + 本机实证机会），据此自足展开。

【v3 格式硬性】
- 标题层级严格 #→##→###：# 报告标题；## 小主题（章节）；### 每个内容项一节。
- ### 下不写任何标签，直接正文分段：先核心说明，再充分辅助说明（面向初学者的直觉/为什么/最小例子/易错点/关联，必需且充分）。
- Borgmaster/Borglet 架构、bin-packing、两级 offer vs 共享状态调度、serverless 执行时间线等结构图独占块，不内联。
- 教辅口吻；广度优先——讲到初学者懂，不做专家纵深。

【小主题清单（##章节，逐一覆盖，下游可回读 round3b 的 C6 节取全内容项）】
- 6.1 集群管理与 Borg 架构：Borgmaster/Borglet、作业-任务模型与优先级。
- 6.2 调度算法与装箱：资源打包/可行性、优先级抢占。
- 6.3 两级/共享状态调度：Mesos offer、YARN、Omega 共享状态乐观并发。
- 6.4 Serverless / Lambda 执行模型：事件驱动、无状态函数与冷启动。
- 6.5 数据流/actor 调度（Ray）：细粒度任务/actor 的动态调度。
防重：调度「算法/资源管理」在本课；容器运行时（namespaces/cgroups）与 K8s「编排落地」归 L5-13（N-08），不越界。

【验证纪律（强制）】
- 多来源比对：每个内容项 ≥2 个独立来源，优先一手（Borg EuroSys2015、Mesos/YARN/Omega、Ray 原始论文、MIT 6.5840 L17 Lambda/L18 Ray lecture）；冲突两边都记、点明分歧。
- 本机模拟可选：bin-packing/优先级调度、offer vs 共享状态冲突重试玩具可用 Python 加固，不替代比对、不逐项强制。
- 不编造：数值/默认值/版本来自比对通过来源，否则标「待核」；标核实日期。
- 章节末集中列来源与时效。
- ⚙演进快：Ray/serverless（AWS Lambda 等）云服务演进处标「⚙演进快·锚版本」，不作定论。

【权威锚点清单（取自 round3a，L6-05）】
- MIT 6.5840 Spring 2026 schedule：https://pdos.csail.mit.edu/6.824/schedule.html（核实 2026-07-25，L17 AWS Lambda / L18 Ray）。
- Borg（EuroSys 2015）原始论文（承重）；Mesos/YARN/Omega、Ray 原始论文作一手来源。
- DDIA 2nd ed（2026-02）交叉参照；Drexel CS647 reading list 非承重。

【粒度判定】下游先判「1 份 or 拆 N 份 + 理由」。

【落盘】自己 Write 到 study/cs-kb/findings/029-distributed-cloud-systems.md。报告不得残留工具标签/控制字符。
```

共 6 条 prompt
