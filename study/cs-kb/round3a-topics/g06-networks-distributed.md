# Round 3a · 第 6 组「网络与分布式」· 大主题分解 · 核实 2026-07-25

> 基线 Py3.11.15/np2.4.6/gcc13.3 @2026-07-25 ｜ 承接 `round2-partials/g06-networks-distributed.md` 的三课递进边界。
> 任务：把每门课拆成**查全、少重叠、按教学序**的大主题清单，锚定权威教材目录/官方 syllabus。
> 本组三课递进边界（round2 §0 复述）：**L4-02 讲协议（RFC）· L5-01 讲算法与不可能性定理（论文）· L6-05 讲把定理做成能上规模的系统（系统论文+源码）。**

---

## 权威锚点清单（URL + 版本 + 核实 2026-07-25）

| 课程 | 主锚点 | 版本/时效 | URL | 核实 |
|------|--------|-----------|-----|------|
| L4-02 网络 | Kurose & Ross《Computer Networking: A Top-Down Approach》**8th ed**（2021）官方 TOC（作者 UMass 站） | 8E，作者站托管 | https://gaia.cs.umass.edu/kurose_ross/Kurose_Ross_TOC_8E.pdf | 2026-07-25 ✓ |
| L4-02 网络 | Stanford **CS144** Introduction to Computer Networking（官方课站，含 lab 增量搭 TCP/IP 栈） | 现行课站 | https://cs144.github.io/ | 2026-07-25 ✓ |
| L4-02 网络 | IETF **RFC**（Standards Track，承重）：9293(TCP)/768(UDP)/791+8200(IP)/1122/5681+9438(拥塞)/1034-1035(DNS)/9110-9114(HTTP)/8446(TLS1.3)/6749+7519(OAuth/JWT) | 见 round2 §3 | https://www.rfc-editor.org/ | 2026-07-25 ✓ |
| L5-01 分布式 | **MIT 6.5840**（前 6.824）Distributed Systems，**Spring 2026** schedule（在开） | Spring 2026 | https://pdos.csail.mit.edu/6.824/schedule.html | 2026-07-25 ✓ |
| L5-01 分布式 | 经典论文（承重）：Lamport 1978 / FLP 1985 / Paxos / Raft(ATC2014) / CAP(G&L 2002) / Herlihy&Wing 1990 | 见 round2 §3 | — | 2026-07-25 ✓ |
| L6-05 云专题 | MIT 6.5840 后半程 lecture（Spanner/ZooKeeper/分布式事务/链式复制/memcached/Lambda/Ray）+ 系统论文（GFS/MapReduce/Dynamo/Spanner/Spark/Borg） | Spring 2026 + 原始论文 | https://pdos.csail.mit.edu/6.824/schedule.html | 2026-07-25 ✓ |
| L6-05 云专题 | Kleppmann & Riccomini《Designing Data-Intensive Applications》**2nd ed**（发行 2026-02，批/流处理承重） | 2E 2026-02 | https://www.oreilly.com/library/view/designing-data-intensive-applications/9781098119058/ | 2026-07-25 ✓ |
| L6-05 云专题（交叉参照） | Drexel CS647 等研究生 cloud/distributed reading list（Borg EuroSys2015 / MapReduce OSDI2004 / Spark NSDI2012） | 交叉非承重 | https://www.cs.drexel.edu/~csg63/courses/cs647_sp23.html | 2026-07-25 ✓ |

> 时效纠偏（据 round2 §5 + 本轮核实）：MIT **6.824 已更名 6.5840**，Spring 2026 在开；DDIA **第 2 版已于 2026-02 发行**（Kleppmann & Riccomini）；**OAuth 2.1 仍是 IETF Internet-Draft**（未成 RFC），API 鉴权承重用 OAuth 2.0=RFC 6749；**BBR 仍为 draft**（非 RFC），拥塞承重用 Reno(5681)/CUBIC(9438)。

---

## L4-02 计算机网络（并入：API 设计 = N-09）

> 锚点：Kurose-Ross 8E TOC（top-down：应用→传输→网络→链路）+ CS144 + RFC。教学序此处按**自底向上收束到应用层再上探 API**呈现（分层心智→链路→网络→传输→应用→安全→API），与 round2 §2 L4-02 五方向对齐并展开到教学单元级。
> 三课划界：本课研究对象=**协议**（RFC 承重）；不碰共识/一致性定理（→L5-01）、不碰集群系统落地（→L6-05）。

| 编号 | 大主题 | 一句话范围 | 来源锚点 | 跨课重叠提示 |
|------|--------|-----------|----------|--------------|
| N1 | 分层与封装参照系 | OSI 七层 vs TCP/IP 四层、对等层、逐层加头/剥头的封装模型 | Kurose 8E ch1；RFC 1122 | 网络内基石；与 L5-01/L6-05 无重叠 |
| N2 | 链路层与局域网 | 以太帧/MAC 编址、ARP、差错检测(CRC)、交换机、多路访问 | Kurose 8E ch6；CS144 datagrams | 与 OS(L4-01) 解耦；不涉密码学 |
| N3 | 网络层·数据平面（IP 编址与转发） | IPv4/IPv6 编址、子网划分、最长前缀匹配转发、NAT、分片/MTU | Kurose 8E ch4；RFC 791/8200/1122 | 转发(数据面) vs 路由(N4)分账；与 L5-01"消息可丢/乱序"失败模型是上下游而非重叠 |
| N4 | 网络层·控制平面（路由） | 链路状态 vs 距离向量、域内(OSPF)、域间(BGP)、SDN 控制面 | Kurose 8E ch5 | 路由算法≠分布式共识：路由求可达性，L5-01 共识求单值一致，勿混 |
| N5 | 传输层与可靠数据传输 | 复用/分用、UDP 数据报、可靠传输原理(序号/ACK/重传)、TCP 连接管理(握手/挥手/状态机) | Kurose 8E ch3；RFC 9293/768 | 本课重心；socket 接口属 OS(L4-01)，协议语义属本课 |
| N6 | TCP 拥塞控制与流控 | 滑动窗口流控、AIMD/慢启动/快恢复(Reno)、CUBIC(默认)、BBR(草案硬标)、ECN | Kurose 8E ch3；RFC 5681/9438；⚠BBR=draft | 本课重心；纯传输层机制，无跨课重叠 |
| N7 | DNS 名字解析 | 层级名字空间、递归 vs 迭代解析、缓存与 TTL、UDP/53、记录类型 | Kurose 8E ch2；RFC 1034/1035 | 分布式命名≠分布式共识；不深入一致性模型(→L5-01) |
| N8 | HTTP 与 Web（含 QUIC/HTTP3） | HTTP 语义(9110)与传输版本分账(1.1/2/3)、缓存(9111)、QUIC/UDP 复兴、队头阻塞 | Kurose 8E ch2；RFC 9110-9114/9000 | REST/API 的地基(→N11)；Web 缓存≠分布式缓存一致性(→L6-05 memcached) |
| N9 | TLS 安全信道与网络安全基础 | TLS 1.3 握手(1-RTT/前向保密)、证书链、防火墙/IPsec 概念 | Kurose 8E ch8；RFC 8446 | **协议机制**在本课；**密码学原语**(AES/RSA/哈希/签名为何安全)在 L5-04 |
| N10 | 无线与移动网络（可选/低优先） | 802.11 WiFi、蜂窝/5G、移动管理与切换 | Kurose 8E ch7 | 查全补位项；教学可裁剪；无跨课重叠 |
| N11 | API 设计（并入 N-09） | REST 资源模型(建于 HTTP 9110 语义)、幂等(PUT/DELETE 幂等/幂等键)、版本化、OpenAPI 契约、鉴权 OAuth2.0(RFC 6749)/JWT(7519) | RFC 9110 语义之上；RFC 6749/7519；OpenAPI(LF) | "HTTP 之上应用层约定"非新协议层；契约测试/CI/协作流程属 L4-06 软工；⚠OAuth 2.1 仅登记为演进方向(仍 draft) |

**建议大主题数：K = 11**（核心 10：N1–N9+N11；N10 无线为查全补位可选项）

---

## L5-01 分布式系统

> 锚点：MIT 6.5840 Spring 2026 schedule（MapReduce/RPC/GFS/Paxos/Raft/一致性/ZooKeeper/分布式事务/Spanner…）+ 经典论文。教学序：模型→时间→复制→共识→一致性→CAP→事务。
> 三课划界：本课研究对象=**算法与不可能性定理**（论文承重），止于"**为什么这样才对**"（safety/liveness/正确性/不可能性）；系统级吞吐/分片/调度落地→L6-05。

| 编号 | 大主题 | 一句话范围 | 来源锚点 | 跨课重叠提示 |
|------|--------|-----------|----------|--------------|
| D1 | 系统模型、RPC 与失败模型 | 异步/同步模型、消息乱序/丢失、崩溃 vs 拜占庭失败、RPC 与并发原语、故障检测 | MIT 6.5840 L1-2(MapReduce/RPC&Threads) | 建于 L4-02 的"消息不可靠"事实之上；本课把它抽象成失败模型 |
| D2 | 时间、时钟与因果序 | 物理时钟不可靠、Lamport 逻辑时钟(happens-before)、向量时钟、NTP 为何不能定序 | Lamport CACM 1978；RFC 5905(NTP) | L6-05 Spanner 用 TrueTime 把"时间"做成系统能力——本课讲逻辑序，L6-05 讲物理时间落地 |
| D3 | 复制与复制状态机（RSM） | 主从 vs 多主 vs 无主复制、复制状态机模型(共识的应用形态)、读写 quorum | MIT 6.5840(Raft/一致性系列) | 复制≠共识：复制是目标，共识是达成手段；工程化(快照/成员变更/分片)→L6-05 |
| D4 | 共识：FLP / Paxos / Raft | FLP 不可能性(异步+1 故障不可确定性共识)划边界、Paxos(quorum 交集)、Raft(强 leader+日志复制+任期)、safety/liveness 论证 | FLP JACM 1985；Paxos 1998/2001；Raft ATC 2014；MIT L4/L6-7 | 本课停在"选举/日志复制/安全性证明"；快照/成员变更/分片 KV/线性一致读→L6-05 |
| D5 | 一致性模型谱系 | 线性一致(Herlihy&Wing)→顺序一致(Lamport)→因果一致→最终一致，越强越需协调 | Herlihy&Wing TOPLAS 1990；Lamport 1979；MIT L8(Linearizability) | 内存模型/缓存一致(硬件)在 L5-05/L4-05；本课讲分布式对象一致性语义 |
| D6 | CAP 与 PACELC 分区权衡 | CAP(分区时 C/A 不可兼得，非"三选二")、PACELC(无分区时 L vs C 也要选) | Gilbert&Lynch SIGACT 2002；Abadi 2012 | 谱系(D5)的"选择理由"；具体系统怎么选(Dynamo/Spanner)→L6-05 |
| D7 | 分布式事务与原子提交 | 2PC 两阶段提交、原子提交的阻塞问题、分布式快照隔离(理论机制) | MIT 6.5840 L11(Distributed Transactions) | 本课讲 2PC**理论机制/阻塞性**；Spanner 的分布式事务**系统实现**→L6-05 C3 |

**建议大主题数：K = 7**

---

## L6-05 分布式与云系统专题

> 锚点：MIT 6.5840 后半程 lecture + 系统论文(GFS/MapReduce/Dynamo/Spanner/Spark/Borg) + DDIA 2E(批/流处理)。教学序：存储→复制落地→全球事务→批处理→流处理→调度。
> 三课划界：本课研究对象=**系统设计与工程折衷**（系统论文+源码承重），讲"**如何做得快且能扩展**"（吞吐/分片/调度/流处理）；不重证 L5-01 的定理。
> 额外防重（round2 §2 边界）：容器运行时/K8s 编排落地→**L5-13 容器与云原生**(N-08 已独立立项)；容量估算/工程折衷方法学→**N-04 系统设计**；DDIA 综述叙事→**N-13**(独立)；单机/分布式**存储引擎内部**(LSM/列存/向量化)→**L6-04**。本课聚焦经典系统论文 + 集群级机制。

| 编号 | 大主题 | 一句话范围 | 来源锚点 | 跨课重叠提示 |
|------|--------|-----------|----------|--------------|
| C1 | 分布式文件/对象存储 | GFS(大文件/主从元数据/租约)、Dynamo(无主+一致性哈希+最终一致+读写 quorum) | GFS SOSP2003；Dynamo SOSP2007；MIT L3 | 存储**集群架构**在本课；存储**引擎内部**(LSM/B-tree/列存)→L6-04 |
| C2 | 复制状态机与协调服务落地 | Raft/Paxos 在 KV 存储的工程化(快照/成员变更/线性一致读)、ZooKeeper(Zab)/Chubby 作协调服务、链式复制 | MIT L6-9,L13；ZooKeeper ATC2010 | 承接 L5-01 D3/D4：本课讲"把共识做大做稳"的工程，不重证 safety |
| C3 | 全球分布式事务与外部一致性 | Spanner：TrueTime(有界时钟不确定性)、外部一致性/线性一致的全球事务、2PC over Paxos | Spanner OSDI2012；MIT L12 | 承接 L5-01 D7(2PC 理论)/D2(时间)：本课讲把它们做成可跑的全球数据库 |
| C4 | 批处理数据处理框架 | MapReduce(map/shuffle/reduce 数据流+容错)、Spark/RDD(内存迭代/血统重算) | MapReduce OSDI2004；Spark NSDI2012；MIT L1；DDIA 2E 批处理章 | 编程模型/集群执行在本课；DDIA 综述视角→N-13 |
| C5 | 流处理与数据密集型架构 | 流处理模型(事件时间/窗口/exactly-once)、Kafka/Flink/Dataflow、批流统一 | DDIA 2E 流处理章；MIT(cache/Ray 邻接) | 数据密集型系统整体叙事→N-13；本课聚焦流处理机制 |
| C6 | 集群资源调度与编排 | Borg(大规模集群管理)、Mesos/YARN(两级/共享状态调度)、调度算法与装箱、serverless/Lambda 执行模型 | Borg EuroSys2015；MIT L17(AWS Lambda)/L18(Ray) | 调度**算法/资源管理**在本课；容器运行时(namespaces/cgroups)与 K8s **编排落地**→L5-13(N-08) |

**建议大主题数：K = 6**

---

## 组内合计与防重自检

- **课程数 M = 3**；**大主题合计 T = 11 + 7 + 6 = 24**。
- 三课划界一句话：**L4-02 协议（RFC）· L5-01 算法与定理（论文）· L6-05 系统落地（系统论文+源码）**。
- 关键防重边界（本轮显式点出）：
  - N4 路由 ≠ D4 共识（可达性 vs 单值一致）；N7 DNS 命名 ≠ 分布式共识。
  - N9 TLS**协议机制** vs L5-04 密码**原语**；N11 API 契约层 vs L4-06 软工测试/CI。
  - L5-01 = "为什么对"（D1–D7 止于正确性/不可能性）；L6-05 = "如何做大"（C1–C6 系统工程）；同一 Raft/2PC/时间在两课分账（D3/D4/D7/D2 ↔ C2/C3）。
  - L6-05 对外防重：C1↔L6-04(存储引擎内部)、C4/C5↔N-13(DDIA 综述)、C6↔L5-13(容器/K8s 编排)、整体↔N-04(系统设计方法学)。
