# 第 6 组 · 网络与分布式（Round 2 定方向） · 核实 2026-07-25

> 基线 Py3.11.15/np2.4.6/gcc13.3 @2026-07-25 ｜ 参照系：IETF RFC（Standards Track）+ 经典分布式论文 + 顶校课程（MIT 6.5840 / Berkeley CS168 / CMU 15-440）。
> 本组课程：**L4-02 计算机网络（并入：API 设计 = N-09）**、**L5-01 分布式系统**、**L6-05 分布式与云系统专题**。
> 纪律：一手承重（RFC/论文），二三手仅指路；前沿/草案硬标状态；不确定留白。这是**定方向**产物（选型对比 + 重点方向 + 一手源候选 + 优先级），**不做**逐条取证（留 R4）。

---

## §0 · 组内地形与三课递进边界

三课是**同一主题的三个抽象层，逐层上升**，不是三份重复：

- **L4-02 网络** = "两台机器之间如何可靠通信"——分层协议栈、单条 TCP 连接的可靠性/拥塞、名字解析与安全信道（DNS/TLS/HTTP）。研究对象是**协议**，一手源是 **RFC**。API 设计（REST/OAuth/幂等/契约）并入此课，作为 **HTTP 语义之上的应用层约定**。
- **L5-01 分布式系统** = "N 台会各自失败、消息会乱序丢失的机器之间如何达成一致"——时间与因果序、复制、共识、CAP、一致性模型。研究对象是**算法/不可能性定理**，一手源是**论文**。
- **L6-05 云系统专题** = "把 L5-01 的理论装进能跑 PB 级数据的真实系统"——把一致性理论、容错复制落到 **具体系统**（GFS/MapReduce/Spanner/Dynamo/Raft-based KV/调度器/Spark 等）。研究对象是**系统设计与工程折衷**，一手源是**系统论文 + 源码**。

一句话边界：**L4-02 讲协议、L5-01 讲算法与定理、L6-05 讲把定理做成系统。** L5-01 与 L6-05 最易重复——分工见 §1-E 表：**L5-01 = "为什么这样才对"（正确性/不可能性），L6-05 = "如何做得快且能扩展"（吞吐/分片/调度/流处理）**。同一个 Raft，L5-01 关心 safety/liveness 证明，L6-05 关心它在 KV 存储里怎么分片、快照、成员变更。

---

## §1 · 组内选型 / 对比表（高价值横向对比）

### A. OSI 七层 vs TCP/IP 四层（分层参照系）

| 维度 | OSI 七层 | TCP/IP（Internet）四层 | 说明 |
|------|----------|------------------------|------|
| 定位 | ISO 参考模型（教学/术语） | 实际部署的协议族 | RFC 1122 定义 Internet 主机分层 |
| 层次 | 应用/表示/会话/传输/网络/数据链路/物理 | 应用 / 传输 / 网际(IP) / 链路 | OSI 的 5-7 层压进 TCP/IP "应用层" |
| 承重 | 概念脚手架，无对应协议实体 | 每层有 RFC 定义的真实协议 | 教学讲 OSI 定位，实证抓包看 TCP/IP |
| 本组落点 | 讲"分层解耦/封装"的心智模型 | TCP=传输、IP=网际、以太=链路、HTTP/DNS=应用 | — |

> 结论：**用 OSI 建立"分层/封装/对等层"术语，用 TCP/IP 落到真实协议与抓包。** 别把 OSI 会话/表示层硬套到 TCP/IP（它们被应用层吸收）。

### B. TCP vs UDP（传输层选型）

| 维度 | TCP | UDP |
|------|-----|-----|
| 一手 | RFC 9293（2022-08，obsoletes 793） | RFC 768（1980） |
| 连接 | 面向连接（三次握手/四次挥手） | 无连接 |
| 可靠性 | 序号/确认/重传/去重/按序交付 | 无（应用自理） |
| 流控/拥塞 | 滑动窗口 + 拥塞控制 | 无 |
| 头开销 | 20B+（选项更多） | 8B |
| 语义 | 字节流 | 数据报（保边界） |
| 典型上层 | HTTP/1.1、HTTP/2、TLS、SSH | DNS、DHCP、QUIC(→HTTP/3)、实时音视频 |

> 关键辨析：**QUIC 跑在 UDP 之上，但在用户态重建了可靠性 + 拥塞控制 + 多路复用**（RFC 9000-9002），是"UDP 上的类 TCP"，用于 HTTP/3（RFC 9114）。讲传输层务必点出这条"UDP 复兴"主线。

### C. 拥塞控制算法（丢包型 vs 时延/带宽型）

| 算法 | 一手 | 信号 | 心智模型 | 状态/基线 |
|------|------|------|----------|-----------|
| Reno / NewReno | RFC 5681 / RFC 6582 | 丢包（三重 dup-ACK） | AIMD 慢启动+拥塞避免+快重传快恢复 | 教学基准 |
| CUBIC | RFC 9438（2023-08，obsoletes 8312） | 丢包，窗口按三次函数增长 | 高带宽长肥管道更激进 | **Linux 默认**（`net.ipv4.tcp_congestion_control`） |
| BBR | ⚠ IETF **Internet-Draft**（draft-cardwell-iccrg-bbr-…），非 RFC | 估计瓶颈带宽 × RTT | 建模管道而非等丢包 | Google 部署广泛；本机可能可选 |
| ECN | RFC 3168 / L4S: RFC 9330 系列 | 显式拥塞标记（不丢包） | 路由器标记代替丢弃 | 演进中 |

> 校准：**AIMD/Reno 讲"为什么公平且收敛"（承重），CUBIC 讲基线默认，BBR 硬标 draft（非 GA、模型不同、勿当标准结论）。** 本机 `sysctl net.ipv4.tcp_available_congestion_control` 实证列出内核实际支持项。

### D. DNS / HTTP 演进 / TLS（应用层与安全信道）

| 对象 | 一手 | 要点 |
|------|------|------|
| DNS | RFC 1034/1035（+ 1123） | 层级名字空间、递归/迭代解析、缓存 TTL、UDP/53 主 |
| HTTP 语义 | **RFC 9110**（2022-06） | 方法/状态码/头/内容协商——与传输版本解耦 |
| HTTP/1.1 | RFC 9112 | 文本、队头阻塞 |
| HTTP/2 | RFC 9113 | 二进制分帧、多路复用、HPACK |
| HTTP/3 | RFC 9114（+ QPACK 9204） | 跑在 QUIC/UDP，消除 TCP 队头阻塞 |
| HTTP 缓存 | RFC 9111 | 幂等/可缓存性，接 REST |
| TLS 1.3 | **RFC 8446**（2018-08） | 1-RTT 握手、去除弱套件、前向保密 |

> 主线：**RFC 9110 把"HTTP 是什么"（语义）与"HTTP 怎么传"（1.1/2/3）分账**——这是理解现代 HTTP 的钥匙，也是 REST/API 设计的地基。

### E. L5-01 理论 vs L6-05 系统专题（组内最重要的分工表）

| 维度 | L5-01 分布式系统（理论） | L6-05 云系统专题（系统） |
|------|--------------------------|--------------------------|
| 问题 | 正确性：能不能一致？何时不可能？ | 工程：如何又快又可扩展又容错？ |
| 承重一手 | 定理/算法论文（Lamport/FLP/Paxos/Raft/CAP 证明） | 系统论文 + 源码（GFS/MapReduce/Spanner/Dynamo/Spark） |
| 典型对象 | 逻辑时钟、共识 safety/liveness、一致性模型谱系、CAP | 复制状态机落地、分片、调度器、大规模数据处理框架 |
| 本机实证 | Python 模拟时钟/共识（小规模、看性质） | 单机模拟 MapReduce / 读 Raft-KV 源码 |
| 心态 | "为什么这样才对" | "如何做得能上生产规模" |

> 反重复守则：讲 Raft，L5-01 停在"选举/日志复制/安全性论证"，L6-05 接"快照/成员变更/分片 KV/线性一致读"。**同一算法，一个证明它对，一个把它做大。**

### F. Paxos vs Raft（共识算法选型）

| 维度 | Paxos | Raft |
|------|-------|------|
| 一手 | Lamport《The Part-Time Parliament》1998 /《Paxos Made Simple》2001 | Ongaro & Ousterhout《In Search of an Understandable Consensus Algorithm》USENIX ATC 2014 |
| 目标 | 理论最小共识 | **可理解性**（explicit design goal） |
| 结构 | 单值 Paxos + Multi-Paxos（工程化留白多） | 强 leader + 日志复制 + 任期 |
| Leader | 弱化/隐式 | 强 leader，选举明确 |
| 上手 | 难（"如何做 Multi-Paxos"有名地含糊） | 易（labs/工业实现多，如 etcd） |
| 安全核心 | quorum 交集 | 选举限制 + 日志匹配性质 + 提交规则 |

> 校准：**Paxos 讲"共识的本质与 quorum 交集"（理论承重），Raft 讲"能落地的共识"（工程 + 本机实证承重）。** 二者等价于"同一问题的两种表述"，不是对立技术。

### G. 一致性模型谱系（强 → 弱）

| 模型 | 一手/出处 | 直觉 | 代价 |
|------|-----------|------|------|
| 线性一致 Linearizability | Herlihy & Wing 1990（TOPLAS） | 单副本假象 + 实时序 | 最强，需协调（受 CAP 限制） |
| 顺序一致 Sequential | Lamport 1979 | 存在一个全局序，各进程程序序保留，无实时约束 | 略弱于线性 |
| 因果一致 Causal | Ahamad et al. 1995 | 有因果关系的操作保序，并发操作可乱序 | 分区可用（COPS 等） |
| 最终一致 Eventual | Dynamo（SOSP 2007） | 不再更新则最终收敛 | 最弱，最高可用 |

> 谱系钥匙：**越强越需要协调（牺牲可用性/时延），越弱越可用**。CAP/PACELC 正是这条谱系的"选择理由"。因果一致是"分区下可用的最强模型"（重要甜点）。

### H. CAP vs PACELC（分区权衡）

| 定理 | 出处 | 说的是 |
|------|------|--------|
| CAP | Brewer 2000 猜想；Gilbert & Lynch 2002 证明 | 网络分区（P）发生时，一致性(C)与可用性(A)不可兼得 |
| PACELC | Abadi 2012 | 分区时 C/A 二选一；**Else（正常时）延迟(L) vs 一致性(C) 也要选** |

> 常见误解硬纠：**CAP 不是"三选二"**——P 是网络给的、不可放弃，真正的选择只有分区期间 C vs A。PACELC 补上了"没分区时也要在延迟和一致性间权衡"。

---

## §2 · 逐课重点方向 + 边界

### L4-02 计算机网络（并入 API 设计）· 建议 P0

重点方向（4–6 个）：
1. **分层与封装心智模型**：OSI 定位 vs TCP/IP 实体（§1-A）；一个包如何逐层加头/剥头。
2. **链路 + IP 路由**：以太帧/MAC、ARP；IP 编址/子网/最长前缀匹配、转发 vs 路由（RFC 791/8200 IPv6、1122 主机要求）。
3. **TCP 可靠传输 + 拥塞控制**（本课重心）：握手/状态机、序号-确认-重传、滑动窗口流控；AIMD/Reno→CUBIC→BBR(草案)（§1-B/C）。一手 RFC 9293。
4. **DNS-HTTP-TLS 应用与安全信道**：解析链路、HTTP 语义/版本分账、TLS 1.3 握手（§1-D）。
5. **API 设计（并入 = N-09）**：REST 资源模型（建立在 HTTP 9110 语义之上）、幂等性（PUT/DELETE 幂等、POST 非幂等、幂等键）、契约（OpenAPI）、鉴权 OAuth 2.0（RFC 6749）/ JWT。**API 设计是"HTTP 之上的应用层约定"，不是新协议层**——这是它并入网络课的逻辑。

边界：
- **网络 vs OS（已解耦，round1 §2-A1）**：socket 是 OS 提供的接口，但协议语义属网络课；并行学，网络不硬依赖 OS。
- **网络 vs 安全（L5-04）**：TLS/DNS 的**协议机制**在本课，密码学原语（AES/RSA/哈希/签名）在 L5-04。本课讲"TLS 握手怎么建信道"，L5-04 讲"底层密码为何安全"。
- **API 设计 vs 软工（L4-06）**：契约/版本化的**协议层**在此；测试/CI/协作流程在 L4-06。

### L5-01 分布式系统 · 建议 P1（AI/系统方向可上调）

重点方向（5 个）：
1. **时间与因果序**：物理时钟不可靠 → Lamport 逻辑时钟（happens-before, 1978）、向量时钟；NTP（RFC 5905）为何不能定序。
2. **复制**：主从 vs 多主 vs 无主；复制状态机（RSM）模型——共识的应用形态。
3. **共识 Paxos/Raft**（重心）：FLP 不可能性（1985，异步 + 1 故障不能确定性共识）划定理论边界；Paxos vs Raft（§1-F）；quorum 交集。
4. **CAP / 分区容错**：CAP 与 PACELC（§1-H）；分区期间的 C/A 抉择。
5. **一致性模型谱系**：线性/顺序/因果/最终（§1-G）；越强越贵。

边界：
- **理论 vs 系统（对 L6-05）**：见 §1-E——本课止于"正确性/不可能性/模型"，不深入系统级吞吐/分片/调度。
- **依赖**：round1 确认 L5-01 依赖 L4-01(OS) + L4-02(网络)——需要进程/消息传递 + 网络失败模型。
- **共识 vs 复制**：共识是"就单个值/日志条目达成一致"，复制是"用共识把状态机复制到多副本"——别混。

### L6-05 分布式与云系统专题 · 建议 P2

重点方向（4–6 个）：
1. **一致性理论落地**：Spanner（OSDI 2012，TrueTime + 外部一致性/线性一致的全球事务）——理论如何进真实系统。
2. **容错复制**：Raft/Paxos 在 KV 存储中的工程化（快照、成员变更、线性一致读）；Chubby/ZooKeeper(Zab) 作协调服务。
3. **调度器**：集群资源调度（Borg/Kubernetes、Mesos/YARN 的两级/共享状态调度）。
4. **大规模数据处理框架**：MapReduce（OSDI 2004）→ 批处理模型；Spark（RDD, NSDI 2012）内存迭代；Dataflow/流处理。
5. **存储系统**（与 L6-04 分账）：GFS（SOSP 2003）、Dynamo（SOSP 2007，无主 + 最终一致 + 一致性哈希）。

边界：
- **对 L5-01**：本课是"系统孪生"，承重系统论文 + 源码，不重证定理。
- **对 L6-04 数据库内核**：L6-04 讲单机/分布式**存储引擎内部**（LSM/列存/向量化执行）；L6-05 讲**集群级数据处理与调度框架**。分布式事务两边都碰，以"存储引擎 vs 处理框架"切。
- **对 N-04 系统设计 / N-13 DDIA**：round1 已将系统设计(N-04)、数据密集型应用(N-13)独立立项；L6-05 聚焦**经典系统论文 + 调度/框架机制**，避免与 N-04（容量估算/工程折衷）、N-13（DDIA 第2版）内容打架。

---

## §3 · 一手源候选 + 本机实证点

### 一手源候选（承重）

**网络（RFC，Standards Track）**
- TCP：**RFC 9293**（2022-08，obsoletes 793）— TCP 可靠传输承重源。
- UDP：RFC 768。IP：RFC 791（IPv4）/ RFC 8200（IPv6）/ RFC 1122（主机要求）。
- 拥塞：RFC 5681（Reno 标准）/ RFC 9438（CUBIC，2023）/ ⚠ BBR = IETF draft（非 RFC，硬标）。
- HTTP：**RFC 9110**（语义）/ 9111（缓存）/ 9112（1.1）/ 9113（2）/ 9114（3）/ 9204（QPACK）。
- QUIC：RFC 9000/9001/9002。TLS：**RFC 8446**（1.3）。DNS：RFC 1034/1035。
- API 设计：OAuth 2.0 = **RFC 6749**（承重）；⚠ **OAuth 2.1 = IETF Internet-Draft**（draft-ietf-oauth-v2-1-15，2026-03，预计 2026-09 过期，**尚未成 RFC**——硬标草案，勿作定型结论）；JWT = RFC 7519；OpenAPI 规范（Linux Foundation，非 RFC）。

**分布式（论文，承重）**
- Lamport《Time, Clocks, and the Ordering of Events in a Distributed System》CACM 1978（逻辑时钟/happens-before）。
- FLP：Fischer, Lynch, Paterson《Impossibility of Distributed Consensus with One Faulty Process》JACM 1985。
- Paxos：Lamport《The Part-Time Parliament》1998 /《Paxos Made Simple》2001。
- Raft：Ongaro & Ousterhout《In Search of an Understandable Consensus Algorithm》USENIX ATC 2014（+ 博士论文含成员变更/快照）。
- CAP：Gilbert & Lynch《Brewer's Conjecture and the Feasibility of Consistent, Available, Partition-Tolerant Web Services》SIGACT News 2002；Brewer PODC 2000 keynote；Abadi《Consistency Tradeoffs (PACELC)》IEEE Computer 2012。
- 一致性：Herlihy & Wing《Linearizability》TOPLAS 1990；Lamport《How to Make a Multiprocessor Computer That Correctly Executes Multiprocess Programs》1979（顺序一致）。

**云系统（系统论文，承重）**
- GFS（SOSP 2003）、MapReduce（OSDI 2004）、Dynamo（SOSP 2007）、Spanner（OSDI 2012）、Spark/RDD（NSDI 2012）、ZooKeeper（USENIX ATC 2010）、Borg（EuroSys 2015）。

**课程（顶校，交叉参照非承重）**
- **MIT 6.5840**（formerly 6.824，Spring 2026 在开，https://pdos.csail.mit.edu/6.824/）——Labs 1-5：MapReduce / KV server / Raft / Raft-KV / sharded-KV，Go 语言，**极佳的分布式实证脚手架**。
- Berkeley CS168（网络，先修仅 CS61B）、CMU 15-440/640（分布式）。

### 本机实证点（R4 待做，此处登记方向）

**网络（系统课 = 抓包 / socket）**
- `tcpdump` / `ss -ti`：抓真实三次握手、观察 cwnd/rtt/拥塞状态（`ss -ti` 显示 cubic、cwnd、rtt）。
- `sysctl net.ipv4.tcp_congestion_control` / `tcp_available_congestion_control`：查本机内核默认与可选拥塞算法（实证 CUBIC 默认 / BBR 是否可用）。
- Python `socket`：写最小 TCP 回显 client/server + UDP 版对比，抓包看差异。
- `dig` / Python `socket.getaddrinfo`：DNS 递归/迭代解析、TTL、缓存。
- `curl -v --http1.1 / --http2 / --http3`（如 curl 支持）+ `openssl s_client`：观察 TLS 1.3 握手、HTTP 版本协商。
- API：Python `http.server` + 手写幂等键，演示 PUT 幂等 vs POST 非幂等。

**分布式（Python 模拟，看性质而非规模）**
- Lamport 逻辑时钟 / 向量时钟：小规模事件模拟，验证 happens-before 偏序。
- Raft：Python 最小实现或读 MIT 6.5840 Lab 3 骨架，模拟选举 + 日志复制（看 safety，非性能）。
- 一致性：模拟线性一致 vs 最终一致读，构造违反例。
- MapReduce：单机多进程 word-count，演示 map/shuffle/reduce 数据流。

> 实证纪律：分布式实证只求"看清性质"（偏序成立/选举收敛/一致性违例），**不追规模/性能数字**（那属论文承重）；抓包/sysctl 输出以真实为准，严禁凭记忆写下标或默认值。

---

## §4 · 优先级校准

| 课程 | round1 优先级 | 本组校准建议 | 理由 |
|------|---------------|--------------|------|
| L4-02 网络（+API） | P0 | **维持 P0** | CS2023 NC 核心；下游支撑 L5-01/L5-04；与 OS 解耦后是并行关键路径。API 设计并入不改优先级。 |
| L5-01 分布式 | P1 | **维持 P1**（系统/云方向可视为 P0） | 依赖 L4-01+L4-02；是 L6-05 的理论前置枢纽。若用户主攻系统/云，上调。 |
| L6-05 云专题 | P2 | **维持 P2** | 研究生深化、论文驱动；依赖 L5-01。 |

组内建议执行序（R3/R4 分批）：**L4-02（先，P0，且是 L5-01 前置）→ L5-01（理论）→ L6-05（系统落地）**。与 round1 §4 批次一致（L4-02 在批3、L5-01 在批4、L6-05 在批5）。

组内子对象优先级微调（供 R3 拆 prompt）：
- L4-02 内：TCP 可靠+拥塞（P0）> HTTP/DNS/TLS（P0）> 分层/IP 路由（P1）> API 设计并入块（P1）。
- L5-01 内：共识 Paxos/Raft（P0 子）> 一致性模型谱系 + CAP（P0 子）> 逻辑时钟（P1）> 复制细分（P1）。
- L6-05 内：容错复制落地 + 一致性理论落地（P1 子）> MapReduce/Spark 数据处理（P1）> 调度器（P2）。

---

## §5 · 纠偏与留白（本组据一手核实的修正）

- **round1 抬头曾写"RFC（TCP 9293 等）"** —— 核实无误，RFC 9293（2022-08）确为现行 TCP，obsoletes 793。承重可用。
- ⚠ **OAuth 2.1 硬标草案**：截至 2026-07-25 仍是 IETF Internet-Draft（v15），**未成 RFC**；API 设计并入块的鉴权承重源应用 **OAuth 2.0 = RFC 6749**，2.1 仅作"演进方向"登记，不作定型结论。
- ⚠ **BBR 硬标草案**：非 RFC；拥塞控制承重讲 Reno(RFC 5681)/CUBIC(RFC 9438)，BBR 作"带宽建模型新范式"登记。
- **MIT 课程号更新**：6.824 已更名 **6.5840**（Spring 2026 在开），R3/R4 引用用新号。
- **留白（R4 需坐实）**：本机 `tcp_available_congestion_control` 具体是否含 bbr、curl 是否编入 HTTP/3、内核默认 cc 是否 cubic —— 均需 R4 就地实证，此处不凭记忆填。
