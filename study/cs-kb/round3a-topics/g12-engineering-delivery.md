# 第12组 工程与交付 · Round 3a 大主题分解 · 核实 2026-07-25

> 基线 Py3.11.15/np2.4.6/gcc13.3 @2026-07-25；本组实证工具链：git 2.43.0 / docker 29.3.1（**client-only，daemon 不可达**）/ unshare(util-linux 2.39.3) / cgroup **v1 hybrid** / 内核 6.18.5。
> 本组 4 门：L4-06 软件工程（并入并扩 VC/测试/CI-CD）· L5-11 系统设计 · L5-12 可观测性·SRE · L5-13 容器与云原生。
> 本文件为 Round 3a **大主题分解**产物：每门课锚定权威目录（URL+版本+核实日），列全教学单元级大主题，标一手/二手承重与跨课重叠。非正式报告。
> **一手承重纪律**：Pro Git / Google SRE 书+Workbook / OCI 规范 / K8s 官方文档 / Linux man page 承重；业界二手（roadmap.sh、Fowler/Cohn、CI 工具文档、三支柱综述）打 ⚠，仅指路/补留白，不承重。L5-11 系统设计**一手最弱**，凡无组件官方文档支撑者一律 ⚠。

---

## 权威目录锚定表（URL + 版本 + 核实 2026-07-25）

| 课程 | 权威锚点 | URL | 版本/状态（核实 2026-07-25） | 承重强度 |
|------|----------|-----|------------------------------|----------|
| L4-06 | **Pro Git**（Chacon & Straub, 2nd ed） | git-scm.com/book/en/v2 | 站点显示 v2.1.450；书 2nd ed（2014 初版，在线持续修订） | **强·一手** |
| L4-06 | **MIT 6.031 Software Construction**（过程/规约/测试/评审锚点） | web.mit.edu/6.031/www/sp22/ | sp22 版；主题表已核（static checking→specs→ADT→testing→code review→VC→concurrency） | 准一手（课程） |
| L4-06 | 测试金字塔源 Mike Cohn《Succeeding with Agile》(2009) + M. Fowler 站点 | martinfowler.com | 概念源，非规范 | ⚠准一手 |
| L4-06 | CI/CD：GitHub Actions / GitLab CI 文档 | docs.github.com / docs.gitlab.com | 无单一规范 | ⚠二手 |
| L5-11 | **DDIA 2nd ed**（Kleppmann & Riccomini） | oreilly.com/library/view/…/9781098119058；martin.kleppmann.com 2026-03-24 公告 | 2nd ed（较 1st 增约 60 页，ch10 一致性/共识几近重写；新增"Trade-offs in Data Systems Architecture""Defining Nonfunctional Requirements"） | 准一手教材（佐证非规范） |
| L5-11 | roadmap.sh System Design | roadmap.sh/system-design | 社区 roadmap，列 LB/缓存/队列/分片/复制/CDN 等组件 | ⚠二手（指路） |
| L5-11 | 各组件官方文档 + 理论回指 L5-01 论文 | Kafka/Redis/Envoy/Nginx docs；Raft/Dynamo/Spanner | 逐组件拆引 | 一手（回指） |
| L5-12 | **Google《Site Reliability Engineering》** | sre.google/sre-book/table-of-contents/ | 34 章 5 部（O'Reilly 2016/2017），全文在线免费 | **强·一手** |
| L5-12 | **《The Site Reliability Workbook》** | sre.google/workbook/table-of-contents/ | 21 章 3 部（O'Reilly 2018），全文在线免费 | **强·一手** |
| L5-12 | **OpenTelemetry** 规范（CNCF） | opentelemetry.io/docs/specs | **CNCF 已毕业（graduated, 2026-05）**；traces/metrics/logs 三信号均 GA/stable；profiling 信号 RC（Q1 2026）→目标 GA Q3 2026 | 强·一手 |
| L5-12 | **W3C Trace Context** | w3.org/TR/trace-context（L1）/ w3.org/TR/trace-context-2（L2） | Level 1 = Recommendation；**Level 2 = Candidate Recommendation Draft**（加随机 trace-id 标志，向后兼容 v00） | 强·一手（标准） |
| L5-12 | Prometheus / OpenMetrics | prometheus.io/docs | metrics 事实标准 | 一手 |
| L5-13 | **OCI Runtime Spec** | opencontainers.org | **v1.3.0（2025-11-04 发布）** | **强·一手** |
| L5-13 | **OCI Image Spec / Distribution Spec** | opencontainers.org | 均 **v1.1.0（2024-02-15）** | 强·一手 |
| L5-13 | **Kubernetes 官方文档** | kubernetes.io/docs/concepts/ | **当前稳定版 v1.36**（页面显示，前序 v1.35/34/33/32） | 强·一手 |
| L5-13 | **Linux man page** | kernel.org / 本机 man | namespaces(7) / cgroups(7) / unshare(1) / user_namespaces(7) | 强·一手 |

---

## L4-06 软件工程（并入并扩 VC/测试/CI-CD）· P0-adjacent

> 边界：本课 = **流程与质量**（变更如何安全进主干）。不含架构折衷(→L5-11)、运行时运维(→L5-12)、部署编排(→L5-13)。需求/设计轻量化，重心 = 可 git 硬实证的 VC + 测试 + CI/CD。本课是 N-06+N-07 并入后的三合一合并体，故大主题数偏多。

| 编号 | 大主题 | 一句话范围 | 来源锚点 | 跨课重叠提示 |
|------|--------|-----------|----------|--------------|
| 06-1 | 软工过程与工程质量观 | 生命周期（需求/设计/演进/维护）与瀑布 vs 敏捷/迭代的轻量框架 | MIT 6.031 课程框架（准一手）+ 行业 ⚠ | 元框架，统摄本组其余课 |
| 06-2 | 规约、契约与抽象数据类型 | 前后置条件/不变式作模块契约；ADT 封装 + 抽象函数/表示不变量 | MIT 6.031 Specs / Designing Specs / ADT / AF&RI | 跨 L3-04 OOP（封装）、L5-06 PL 理论（契约） |
| 06-3 | 静态检查与类型安全防御 | 编译期消除整类错误、"避免调试"的设计纪律 | MIT 6.031 Static Checking / Avoiding Debugging | 跨 L5-06 类型系统、L4-04 编译前端检查 |
| 06-4 | **Git 对象模型（内容寻址 DAG）** | blob/tree/commit/tag 四对象 + SHA 内容寻址 + 提交=不可变对象图 | **Pro Git §10.2 Git Objects**（一手承重）；`git cat-file` + SHA 手算可硬实证 | 本组最强实证点；交付基座 fan-out 全库 |
| 06-5 | Git 引用/分支/HEAD 指针机制 | refs/heads、tags、HEAD、reflog 作可变指针；分支=轻量指针 | Pro Git §3.1 + §10.3 Git References | — |
| 06-6 | 三向合并与 merge-base（rebase vs merge） | 共同祖先→三向 diff→冲突区计算；线性改写 vs 保留分叉的取舍 | Pro Git §3.2、§3.6、§7.8；`git merge-base` 可实证 | 冲突**语义**解决属人工（边界） |
| 06-7 | 分布式协作、PR 评审与传输协议 | 贡献/维护工作流、代码评审门禁、refspec、smart-http/ssh 传输 | Pro Git ch5/ch6 + §10.5-10.6；MIT Code Review | 跨 L4-02 网络（传输协议之上） |
| 06-8 | 分支策略 | trunk-based vs GitFlow 的团队取舍 | Pro Git §3.4 Branching Workflows + 行业 ⚠ | 团队策略，非机制 |
| 06-9 | 测试金字塔与测试分层 | unit/integration/e2e 分层的成本/信噪比结构；冰淇淋筒反模式 | Cohn/Fowler ⚠准一手；MIT Testing | 结构层（与 06-11 TDD 节奏分账） |
| 06-10 | 测试替身与覆盖率解读 | mock/stub/fake/spy 区分；覆盖率≠质量 | MIT Testing ⚠ | — |
| 06-11 | TDD 与测试作为设计压力 | 红-绿-重构循环；测试驱动设计的节奏/流程 | Kent Beck TDD ⚠ | 节奏/流程（与 06-9 结构分账） |
| 06-12 | CI 持续集成与门禁 | 自动构建/测试门禁/制品(artifact)/构建缓存 | GitHub Actions/GitLab CI docs ⚠；git pre-commit hook 可演示最小 CI | 跨全库（交付所有课产出） |
| 06-13 | CD：持续交付 vs 持续部署 | 发布流水线、环境晋级；delivery ≠ deployment | 工具文档 ⚠二手 | 跨 L5-13 部署、L5-12 发布工程/金丝雀 |

**建议大主题数：K = 13**

---

## L5-11 系统设计 · P1（取证难度最高，一手最弱）

> 边界：本课 = **架构折衷**（把组件装配成满足容量/成本/SLA 的系统）。头号分账 = **vs L5-01 分布式系统**：出现"证明/线性一致性定义/FLP"→L5-01（理论/正确性）；出现"估算 QPS/选 Kafka 还是 Rabbit/缓存失效策略"→L5-11（工程/装配）。二者不可互替。**vs DDIA(L6-04/L6-05/N-13)**：DDIA 深入存储引擎/复制协议内部机制，本课停在"选型+容量+折衷"装配层，DDIA 作准一手佐证。

| 编号 | 大主题 | 一句话范围 | 来源锚点 | 跨课重叠提示 |
|------|--------|-----------|----------|--------------|
| 11-1 | 需求与非功能目标定义 | 把业务约束翻译成 SLA/容量/成本/延迟目标 | DDIA2「Defining Nonfunctional Requirements」章（准一手） | 跨 L5-12 SLI/SLO（目标度量化） |
| 11-2 | **容量估算方法学**（back-of-envelope） | QPS/存储/带宽数量级反推实例数 | roadmap.sh ⚠ + SRE Workbook ch12 NALSD（一手佐证）；Python 数量级演算可实证 | 本课唯一独立于他课的核心能力 |
| 11-3 | 负载均衡与流量分发 | L4/L7、一致性哈希；横向扩展入口 | Envoy/Nginx docs（一手回指） | 跨 L4-02 网络、L5-13 K8s Service/Ingress |
| 11-4 | 缓存策略 | 客户端/CDN/应用/DB 多层；失效/TTL、命中率 vs 一致性 | Redis docs / CDN 文档（一手回指） | 跨 L4-03 索引局部性 |
| 11-5 | 数据分片 sharding | 范围/哈希/目录分区；均衡 vs 热点 vs 重分片成本 | DDIA2 Partitioning（准一手） | 跨 L5-01 分区理论 |
| 11-6 | 复制 replication | 主从/多主/quorum；可用性/读扩展折衷 | DDIA2 Replication；**协议正确性回指 L5-01** | 理论承重在 L5-01（边界） |
| 11-7 | 一致性-可用性-延迟折衷 | CAP 工程解读 + PACELC；选 AP/CP 的业务后果 | DDIA2 ch10（准一手）；**定理证明归 L5-01** | 与 L5-01 头号分账 |
| 11-8 | 消息队列与异步/事件驱动 | 削峰/解耦；at-least/exactly-once 语义、吞吐 vs 灵活路由 | Kafka/RabbitMQ docs（一手回指） | 跨 L5-01 顺序/复制 |
| 11-9 | 容错工程模式 | 超时/重试/退避/熔断/舱壁/幂等；优雅降级 | 各库文档 ⚠ + SRE 书 ch22 级联失败 | 跨 L5-12 过载处理 |
| 11-10 | 读写路径与数据模型选型 | 读多写少/写重、CQRS/事件溯源直觉；按访问模式设计 | DDIA2 Data Models（准一手） | 跨 L4-03 数据库、L6-04 存储内核 |

**建议大主题数：K = 10**（API 设计已并入 L4-02/N-09；系统设计面试方法论为纯二手，不单列）

---

## L5-12 可观测性 / SRE · P1（性价比最高，一手最硬）

> 边界：本课 = **运维**（生产运行时的度量与止血）。不含系统怎么设计(→L5-11)、不含 OS 内部机制(L4-01 到 syscall 为止，本课接其上方运行时)。**三处分账**：①"三支柱"是社区框架(⚠) vs OpenTelemetry/W3C(规范一手)——须分开；②SRE 文化/流程(方法学) vs 三支柱(技术)；③OTel 各信号 GA 状态须硬标时效（已核 2026-07-25：traces/metrics/logs GA，profiling RC）。

| 编号 | 大主题 | 一句话范围 | 来源锚点 | 跨课重叠提示 |
|------|--------|-----------|----------|--------------|
| 12-1 | SRE 原则与 DevOps 关系 | 风险接纳、可靠性作为特性、SRE vs DevOps | SRE 书 ch1/ch3 + Workbook ch1（一手承重） | 运维工程化元框架 |
| 12-2 | **SLI / SLO / 错误预算** | 可靠性指标定义→目标→预算=(1−SLO)×窗口 | SRE 书 ch4 + Workbook ch2（一手承重）；错误预算纯算术可硬实证 | 跨 L5-11 非功能目标；本组第二强实证点 |
| 12-3 | 基于 SLO 的告警（burn-rate） | 燃烧率多窗口告警 vs 传统阈值告警 | Workbook ch5「Alerting on SLOs」+ SRE 书 ch10（一手承重） | — |
| 12-4 | metrics 支柱与监控分布式系统 | 数值时序聚合、四黄金信号（延迟/流量/错误/饱和度） | SRE 书 ch6 + Prometheus/OpenMetrics（一手） | 跨 L2-03 概率（基准统计有效性/N-10） |
| 12-5 | logs 支柱 | 离散结构化事件、采样/降级；无单一标准 | OTel Logs（GA，一手规范）；概念框架 ⚠ | 概念(⚠) vs OTel 规范(一手) 分账 |
| 12-6 | traces 支柱与分布式追踪 | span 树、context 传播、头采样 vs 尾采样、traceparent 格式 | **W3C Trace Context**（L1 Rec / L2 CR Draft）+ OTel Traces（GA） | 跨 L5-01 因果序、L4-02 请求链 |
| 12-7 | OpenTelemetry 统一遥测 | 把三支柱统一到一套 SDK/API/OTLP；信号 GA 状态 | opentelemetry.io/docs/specs（CNCF 已毕业 2026-05；须硬标各信号 GA 时效） | 统一"三支柱概念"的规范实体 |
| 12-8 | 消除 toil 与自动化 | 重复运维劳动的量化与消除、自动化演进 | SRE 书 ch5/ch7 + Workbook ch6（一手承重） | — |
| 12-9 | 事件响应与无指责事后复盘 | 中断处理、事件管理、postmortem 学习文化 | SRE 书 ch14/ch15 + Workbook ch9/ch10（一手承重） | 方法学/流程（与技术支柱分账） |
| 12-10 | 发布工程与金丝雀发布 | 可重复发布、canary、错误预算驱动发布决策 | SRE 书 ch8 + Workbook ch16（一手承重） | 跨 L4-06 CD、L5-13 部署 |
| 12-11 | 过载处理与级联失败 | 限流/降载/防雪崩、处理过载 | SRE 书 ch21/ch22 + Workbook ch11（一手承重） | 跨 L5-11 容错工程 |

**建议大主题数：K = 11**

---

## L5-13 容器与云原生 · P1（实证走 unshare；cgroup v1/v2 分账）

> 边界：本课 = **部署**（打包/隔离/编排）。**机制根落在 L4-01 OS**（namespaces/cgroups 是 OS 资源隔离），本课是其工程落地；编排(K8s)是新增调度/服务发现/弹性层。**K8s 深度专题归 L6-05 云系统**，本课只做"编排解决什么 + 核心对象心智"。**实证约束**：本沙箱 Docker daemon 不可达 → 走 `unshare`/`nsenter`/读 `/sys/fs/cgroup`；本机 cgroup v1 hybrid，讲 v2 统一层级须标"基线本机 v1、v2 为现代默认"。

| 编号 | 大主题 | 一句话范围 | 来源锚点 | 跨课重叠提示 |
|------|--------|-----------|----------|--------------|
| 13-1 | 容器 vs VM 与隔离模型 | 共享内核进程 + ns + cgroups vs hypervisor 整机；容器不是轻量 VM | Linux man page + OCI（一手） | 跨 L4-01 OS 隔离；L5-05 虚拟化 |
| 13-2 | **Linux namespaces（视图隔离）** | pid/net/mnt/uts/ipc/user/cgroup/time 八种 | **namespaces(7)**（一手承重）；`unshare --pid/--net` 硬实证 | 机制根 L4-01 OS；本组最强实证点之一 |
| 13-3 | **cgroups（资源限额，v1 vs v2）** | cpu/memory/pids/io 控制器；v1 hybrid(本机) vs v2 统一层级 | **cgroups(7)**（一手承重）；读 `/sys/fs/cgroup` 实证 | 跨 L4-01 OS 资源管理；须 delta 分账 |
| 13-4 | OCI 镜像格式与分层/union FS | 只读层 + 可写层 CoW、内容寻址 manifest、不可变部署 | **OCI Image Spec v1.1.0**（一手承重） | 跨 L4-01 文件系统 |
| 13-5 | OCI 运行时与容器运行时链 | filesystem bundle→exec；containerd/runc → OCI runtime | **OCI Runtime Spec v1.3.0**（一手承重） | — |
| 13-6 | OCI 分发与镜像仓库 | registry push/pull 协议 | **OCI Distribution Spec v1.1.0**（一手承重） | 跨 L4-02 HTTP |
| 13-7 | K8s 工作负载对象 | Pod/Deployment/控制器等运行单元 | **K8s docs Workloads v1.36**（一手承重） | 深度归 L6-05（边界） |
| 13-8 | K8s 声明式模型与 reconcile 循环 | 期望态收敛、控制器模式 | K8s docs Cluster Architecture v1.36（一手承重） | 云原生核心心智 |
| 13-9 | K8s 服务发现/负载均衡/网络 | Service/Ingress、集群内外访问 | K8s docs Services & Networking v1.36（一手） | 跨 L4-02 网络、L5-11 LB |
| 13-10 | K8s 调度/抢占/驱逐 | Pod 放置与资源管理 | K8s docs Scheduling/Preemption/Eviction v1.36（一手） | — |
| 13-11 | K8s 配置与存储 | ConfigMap/Secret、Volume/PV/PVC；配置与持久化解耦 | K8s docs Configuration & Storage v1.36（一手） | 跨 L4-06 配置管理 |
| 13-12 | 容器安全边界 | 共享内核攻击面；gVisor/Kata（用户态内核/微 VM） | K8s docs Security（一手）+ ⚠前沿 | 跨 L5-04 安全 |

**建议大主题数：K = 12**

---

## 汇总

| 课程 | 建议大主题数 K |
|------|----------------|
| L4-06 软件工程（VC/测试/CI-CD） | 13 |
| L5-11 系统设计 | 10 |
| L5-12 可观测性/SRE | 11 |
| L5-13 容器与云原生 | 12 |
| **合计** | **46** |

> 分账备忘（供 Round 4）：①L5-11 工程装配 vs L5-01 理论证明（判据已给）；②L5-12 "三支柱"社区框架(⚠) vs OTel/W3C 规范(一手)；③L5-13 cgroup v1(本机基线) vs v2(现代默认)；④L5-11 vs DDIA(存储/复制内部机制归 L6-04/L6-05)；⑤L4-06 CD vs L5-13 部署 vs L5-12 发布工程/金丝雀（交付链三段，分别为流水线机制/编排落地/发布决策）。
