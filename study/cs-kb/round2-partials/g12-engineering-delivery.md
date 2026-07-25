# 第12组 工程与交付 · 阶段2 定方向 · 核实 2026-07-25

> 基线 Py3.11.15/np2.4.6/gcc13.3 @2026-07-25；本组实证工具链已自检：git 2.43.0 / docker 29.3.1（**仅 client，daemon 不可达**）/ unshare(util-linux 2.39.3) / cgroup **v1 hybrid 布局** / 内核 6.18.5。
> 本组：L4-06 软件工程（并入并扩：版本控制 Git 机制、测试策略与 CI/CD）/ L5-11 系统设计 / L5-12 可观测性·SRE / L5-13 容器与云原生。
> 本组共性 = **"运行 / 交付视角"（Ops & Delivery）**——区别于前 11 组的"构建视角"。这是 round1-map §3-B 校出的种子最大结构性缺口（视角②：种子六层几乎纯"从数学到系统的构建阶梯"，缺整个上线后的运维/交付/可观测）。
> 本文件为 Round 2 **定方向**产物（挑深挖方向 + 一手源候选 + 本机实证点 + 优先级校准），非正式报告。

---

## 组内选型/对比表

### 表1 · 本组四课在"交付生命周期"中的分工（共性 = 运行/交付视角）
| 课程 | 生命周期位置 | 一句话职责 | 承重一手源强度 | 与"构建视角"课的接口 |
|------|--------------|-----------|----------------|----------------------|
| L4-06 软工（VC/测试/CI） | 写码→合并→门禁 | 流程与质量：变更如何安全进主干 | **强**（Pro Git 官方书 + git 源码可实证） | 承接所有 Lx 的"如何协作产出代码" |
| L5-11 系统设计 | 设计→装配 | 架构折衷：把组件拼成满足容量/成本/SLA 的系统 | **弱**（业界经验为主，一手散落各组件官方文档）⚠ | L5-01 分布式理论的"工程孪生" |
| L5-12 可观测性/SRE | 运行→度量→止血 | 运维：生产"坏没坏、为什么坏"的系统化答案 | **强**（Google SRE 书/Workbook 官方全文；OTel 规范） | L4-01 OS 之上的运行时视角 |
| L5-13 容器与云原生 | 打包→部署→编排 | 部署：不可变交付 + 资源隔离 + 调度 | **强**（OCI 规范 + K8s 官方文档 + Linux man page） | L4-01 OS 进程/资源隔离的工程落地 |

### 表2 · 测试金字塔各层（L4-06 核心选型）
| 层 | 隔离范围 | 数量/速度 | 反馈定位精度 | 脆性(flaky) | CI 中位置 |
|----|----------|-----------|--------------|-------------|-----------|
| 单元 unit | 单函数/类，依赖 mock | 最多、毫秒级 | 高（精确到函数） | 低 | 每次 push，本地即可 |
| 集成 integration | 跨模块/带真实依赖(DB/队列) | 中、秒级 | 中 | 中 | PR 门禁主力 |
| 端到端 e2e | 整系统黑盒 | 最少、分钟级 | 低（只知"坏了"） | 高 | 合并前/夜间 |
| （反模式：冰淇淋筒） | e2e 多、unit 少 | 慢且脆 | — | — | 业界公认反模式⚠二手 |
> 承重结论：金字塔形（底宽顶窄）是成本/信噪比最优；TDD 是"先写测试驱动设计"的**流程**，不是测试层级，Round 4 需分账（金字塔=结构，TDD=节奏）。

### 表3 · 系统设计常见组件选型（L5-11 核心，均须回指各组件官方文档一手，本表为方向线索⚠二手）
| 组件类 | 代表 | 解决什么 | 关键折衷轴 | 与理论课接口 |
|--------|------|----------|------------|--------------|
| 负载均衡 LB | L4/L7、一致性哈希 | 横向扩展、流量分发 | L4(快/无内容感知) vs L7(灵活/开销) | L4-02 网络 |
| 缓存 | Redis、CDN 边缘缓存 | 降延迟/减源站压力 | 命中率 vs 一致性(失效策略/TTL) | L4-03 索引局部性 |
| 消息队列 | Kafka、RabbitMQ | 削峰/解耦/异步 | 吞吐(Kafka) vs 灵活路由(Rabbit)；at-least/exactly-once | L5-01 复制/顺序 |
| 分片 sharding | 范围/哈希/目录 | 数据水平扩展 | 均衡 vs 热点 vs 重分片成本 | L5-01 分区 |
| 复制 replication | 主从/多主/quorum | 可用性/读扩展 | 一致性 vs 延迟(CAP/PACELC) | **L5-01 理论承重** |
| CDN | 边缘节点 | 就近静态/媒体分发 | 缓存新鲜度 vs 回源 | L4-02 DNS/anycast |
> 承重结论：系统设计的价值**不在"知道有哪些组件"，而在容量估算(QPS/存储/带宽反推实例数) + 显式折衷**。每个组件的机制一手落在其官方文档（Kafka/Redis/Envoy docs），CAP/一致性一手落 **L5-01**，本组只做"装配与权衡"层。

### 表4 · 可观测性三支柱 metrics vs logs vs traces（L5-12 核心）
| 支柱 | 数据形态 | 回答的问题 | 基数/成本 | 采样 | 一手/标准状态 |
|------|----------|-----------|-----------|------|----------------|
| metrics | 数值时序(聚合) | "有没有坏、坏多少"(趋势/告警) | 低基数、便宜 | 不采样(预聚合) | Prometheus/OpenMetrics；OTel Metrics |
| logs | 离散事件(文本/结构化) | "发生了什么"(单事件细节) | 高、量大 | 可采样/降级 | 无单一标准；OTel Logs 后到 |
| traces | 请求跨服务的因果链(span 树) | "慢在哪一跳"(分布式定位) | 高基数、贵 | 需采样(头/尾) | W3C Trace Context(标准) + OTel Traces |
> 承重结论："三支柱"是**社区框架、非标准**（round1-map [^5] 已标⚠）；真正的一手标准化力量是 **OpenTelemetry(CNCF)** 把三者统一到一套 SDK/协议(OTLP)——Round 4 须把"三支柱概念"(二手框架) 与"OTel/W3C 规范"(可承重一手) 分账。SLI/SLO/错误预算的一手落 **Google SRE 书**。

### 表5 · 容器 vs 虚拟机（L5-13 核心）
| 轴 | 容器(container) | 虚拟机(VM) |
|----|-----------------|-----------|
| 隔离机制 | 共享宿主内核 + namespaces(视图隔离) + cgroups(资源限额) | Hypervisor 虚拟化整机 + Guest OS |
| 隔离强度 | 进程级(弱、共享内核攻击面) | 硬件级(强) |
| 启动/开销 | 毫秒、MB 级 | 秒~分、GB 级 |
| 密度 | 高 | 低 |
| 镜像 | 分层只读(OCI Image)+可写层 | 整盘镜像 |
> 承重结论：容器**不是轻量 VM**，是"被 namespaces + cgroups 约束的普通进程"——这是接 L4-01 OS 的关键落点，可本机 `unshare` 直接实证(见下)。安全边界弱是其根本折衷，衍生 gVisor/Kata(用户态内核/微VM)⚠前沿。

### 表6 · Git 三向合并 vs 变基（L4-06 版本控制子方向）
| 轴 | merge(三向合并) | rebase(变基) |
|----|-----------------|--------------|
| 历史形态 | 保留分叉 + merge commit | 线性、改写提交 |
| 冲突解决 | 一次性(合并点) | 逐 commit 重放 |
| 共享分支安全 | 安全 | **危险**(改写已推送历史) |
| 三向合并基准 | merge-base(共同祖先) + 两侧 | 同样用 merge-base 但逐个 replay |
> 承重结论：两者底层都靠 **merge-base(最近公共祖先)** 做三向 diff；差别是"记录真相 vs 讲干净故事"的团队策略取舍，非对错。

---

## 逐课重点方向

### L4-06 软件工程（并入并扩 · 优先级 P1 → **建议上调 P0-adjacent**，见校准）
**边界**：本课 = **流程与质量**（变更如何安全进主干）。不含架构折衷(→L5-11)、不含运行时运维(→L5-12)、不含部署(→L5-13)。需求/架构分析可保留但轻量化，重心移到可实证的三块：版本控制、测试、CI/CD。

**重点方向（Round 4 深挖 3–6）**
1. **Git 对象模型（内容寻址存储）**：blob/tree/commit/tag 四对象 + SHA 内容寻址 + DAG。**本组最强可实证点**——`git cat-file` 直接扒对象。边界：不讲 Git 命令大全，只讲"提交=不可变对象图"的机制心智。
2. **三向合并与 merge-base**：分叉→共同祖先→三向 diff→冲突标记；rebase vs merge(表6)。边界：冲突的**语义**解决属人工，机制只到"如何算出冲突区"。
3. **测试金字塔与测试替身**：unit/integration/e2e 分层(表2) + mock/stub/fake/spy 区分 + 覆盖率的正确解读(覆盖率≠质量)。
4. **TDD 与测试作为设计压力**：红-绿-重构循环；边界：TDD 是**节奏/流程**，与金字塔(结构)分账。
5. **CI/CD 流水线**：门禁(gate)、构建缓存、制品(artifact)、CD vs Continuous Deployment 区分；**本机可用 git hook + 脚本演示最小 CI**（daemon 无关）。
6. （可选）**分支策略**：trunk-based vs GitFlow 的团队取舍。

**一手源候选**：**Pro Git**（Chacon & Straub 官方书，git-scm.com，第10章 Git Internals 承重 Git 对象模型）；**git 源码**（`.reference/` 下 clone，`Documentation/technical/` + `object.h`）；测试金字塔原始出处 **Mike Cohn《Succeeding with Agile》**(2009，概念源) + Martin Fowler 站点(⚠准一手/专家)；CI/CD 无单一规范，回指工具官方文档(GitHub Actions/GitLab CI docs)⚠二手。CS2023 SE KA。
**本机实证点（已实跑）**：`git init` + commit 后 `git cat-file -t/-p` 扒 commit→tree→blob；**实测** blob SHA `ce013625…` == `sha1('blob 6\0hello\n')`(手算核对通过)，坐实"内容寻址"非隐喻；`.git/objects/` 见 loose 对象目录(2字符前缀分桶)。可扩展：`git merge-base`、pre-commit hook 拦截、`git log --graph` 看 DAG。
**优先级校准**：**建议由 P1 上调**——理由见校准汇总(并入 N-06+N-07 后已是三门业界一等能力的合并体，且是唯一"可 git 硬实证"的交付课)。

### L5-11 系统设计（Round1 新增 · 优先级 P1，确认）
**边界**：本课 = **架构折衷**（把组件装配成满足容量/成本/SLA 的系统）。**与 L5-01 分布式系统的分账是本课头号边界问题**：
- **L5-01 = 理论/正确性**：时钟因果序、Paxos/Raft 共识、CAP/一致性模型的**证明与不可能性**。回答"什么在数学上可能/不可能"。
- **L5-11 = 工程/装配**：容量估算、组件选型(表3)、缓存分片复制的**成本-延迟-可用性折衷**。回答"给定约束，怎么拼一个能跑且划算的系统"。
- **分账判据**：出现"证明""线性一致性定义""FLP"→L5-01；出现"估算 QPS/选 Kafka 还是 Rabbit/缓存失效策略"→L5-11。二者**不可互替**(round1-map N-04：分布式理论的工程孪生)。

**重点方向（Round 4 深挖 3–6）**
1. **容量估算方法学**：QPS/存储/带宽的数量级反推(back-of-envelope)——本课**最能立住的独立能力**，其余课都没有。
2. **组件选型矩阵**：LB/缓存/队列/分片/复制/CDN 各自的折衷轴(表3)，每个回指官方文档一手。
3. **一致性-可用性-延迟折衷**：CAP 的工程解读 + PACELC；边界：定理证明归 L5-01，本课只做"选 AP 还是 CP 的业务后果"。
4. **容错工程**：超时/重试/退避/熔断/舱壁/幂等——业界模式，回指各库文档⚠。
5. （可选）**读写路径设计**：读多写少 vs 写重、CQRS/事件溯源直觉。

**一手源候选**：**⚠本课一手最弱**——业界经验为主，无权威规范。可承重的一手是**各组件的官方文档**(Kafka/Redis/Envoy/Nginx docs)拆开引用；理论侧一手全部回指 **L5-11 依赖的 L5-01 论文**(Raft/Dynamo/Spanner)。二手线索：roadmap.sh System Design、DDIA(第2版 2024，邻接 L6-05/N-13，**准一手教材**可佐证但非规范)。**纪律：本课结论凡无组件官方文档支撑者一律打 ⚠二手并列待补**。
**本机实证点**：系统设计偏"纸上折衷"，本机实证弱。可做：容量估算的 **Python 数量级演算脚本**(给定 DAU/请求大小→QPS/存储/带宽，sympy/numpy 核对量级)；用 locust/wrk 对本地服务压测看"延迟随并发变化"曲线(需起服务，daemon 无关)。
**优先级校准**：**维持 P1**。是资深工程能力核心，但一手薄弱决定其报告须大量标⚠并回指 L5-01/组件文档——Round 4 取证难度最高，宜安排熟手。

### L5-12 可观测性 / SRE（Round1 新增 · 优先级 P1，确认；round1-map 标 4–5 层）
**边界**：本课 = **运维**（生产运行时的度量与止血）。不含系统怎么设计(→L5-11)、不含 OS 内部机制(L4-01 到 syscall/中断为止，本课接其**上方**的运行时可观测)。

**重点方向（Round 4 深挖 3–6）**
1. **三支柱 metrics/logs/traces**(表4)：各自形态、成本、采样；**分账**："三支柱"是社区框架(⚠)，标准化实体是 **OpenTelemetry + W3C Trace Context**——Round 4 须把概念框架与规范分开。
2. **SLI / SLO / 错误预算**：可用性/延迟 SLI 定义 → SLO 目标 → 错误预算 = (1−SLO)×窗口 → 预算用尽即冻结发布。**本组第二强可实证点**：错误预算是**纯算术**，可 Python 精确核算(如 99.9% SLO/30天 = 43.2 分钟预算)。
3. **基于 SLO 的告警**：burn-rate(燃烧率)多窗口告警 vs 传统阈值告警——SRE 书专章，承重一手强。
4. **分布式追踪机制**：trace/span/context 传播、头采样 vs 尾采样、W3C `traceparent` 头格式。
5. **SRE 文化实践**：错误预算驱动的发布决策、无指责事后复盘(postmortem)、toil 消除——边界：属**方法学/流程**，与三支柱(技术)分账。
6. （可选）**四个黄金信号**(延迟/流量/错误/饱和度)作为 metrics 选型起点。

**一手源候选**：**Google《Site Reliability Engineering》+《The Site Reliability Workbook》官方全文**(sre.google，SLO/SLI/错误预算/Alerting on SLOs 章节)——**round1-map [^4] 已确认为可承重一手**(Google 一方发布、可追溯)；**OpenTelemetry 规范**(CNCF，opentelemetry.io/docs/specs)；**W3C Trace Context Recommendation**(w3c.org，正式标准)；Prometheus/OpenMetrics 文档。⚠二手："三支柱"概念综述(strongdm 等)仅指路。
**本机实证点（方向已定）**：**错误预算/SLO 的 Python 精确核算**(sympy 核对 99.9%→30天→43.2min、99.99%→4.32min，burn-rate 公式)——这是本课最扎实的实证腿，纯算术不涉编造；可扩展：起本地服务 + 计数器算 SLI、W3C traceparent 头解析脚本。**核实时效**：OTel 各信号 GA 状态(traces/metrics 早已 GA，logs 较晚)须 Round 4 联网核实到 2026-07-25 当前版本，不凭记忆。
**优先级校准**：**维持 P1**。是种子"运行视角"缺口的正中心 + 一手最硬(SRE 书)，性价比高，建议 Round 4 优先排。

### L5-13 容器与云原生（Round1 新增 · 优先级 P1，确认；接 L4-01 OS 落地）
**边界**：本课 = **部署**（打包/隔离/编排）。**机制根落在 L4-01 OS**（namespaces/cgroups 是 OS 资源隔离概念），本课是其**工程落地**；编排(K8s)是新增的调度/服务发现/弹性层，不与 L4-01 重叠。

**重点方向（Round 4 深挖 3–6）**
1. **Linux namespaces（视图隔离）**：pid/net/mnt/uts/ipc/user/cgroup/time 八种；**本组最强可实证点之一**——`unshare` 直接造隔离。
2. **cgroups（资源限额）**：cpu/memory/pids/io 控制器；**v1 vs v2 分账**(本机是 v1 hybrid，现代默认已是 v2 统一层级——须标 delta)。
3. **OCI 镜像分层与 union 文件系统**：只读层 + 可写层 + copy-on-write；不可变部署。
4. **容器 vs VM**(表5) + 容器运行时链(containerd/runc → OCI runtime spec)。
5. **Kubernetes 编排核心**：Pod/控制器(Deployment)/Service/调度器/声明式 reconcile 循环——边界：K8s 深度专题归 L6-05 云系统，本课只做"编排解决什么 + 核心对象心智"。
6. （可选）**容器安全边界**：共享内核攻击面 → gVisor/Kata⚠前沿。

**一手源候选**：**OCI Runtime Spec + Image Spec**(opencontainers.org，正式规范)；**Kubernetes 官方文档**(kubernetes.io/docs，含 API reference)；**Linux man page** `namespaces(7)`/`cgroups(7)`/`unshare(1)`/`user_namespaces(7)`(本机 man + kernel.org)；`.reference/` 可 clone runc 源码看 OCI 实现。⚠二手：Docker 官方博客/roadmap.sh DevOps 仅指路。
**本机实证点（已实跑）**：`unshare --pid --fork --mount-proc bash` → 新 PID namespace 内 `ps` 见 **PID 1 = bash**(实测输出坐实进程视图隔离)；`unshare --net` → 新网络 namespace；`/proc/self/ns/` 见八类 namespace inode。**重要约束**：本沙箱 **Docker daemon 不可达**(client-only，`docker info` 失败)——故 Round 4 容器实证须走 **`unshare`/`nsenter`/直接读 `/sys/fs/cgroup`**，不能靠 `docker run`；且本机 **cgroup 为 v1 hybrid 布局**(根无 `cgroup.controllers` 文件，控制器为 cpu/memory/pids 分离目录 + unified)，讲 v2 统一层级须显式标"基线本机是 v1，v2 为现代默认"。
**优先级校准**：**维持 P1**。接 L4-01 的落地价值高 + namespaces/cgroups 可硬实证，但 daemon 缺失 + cgroup v1/v2 分账增加取证复杂度，Round 4 须预留 unshare 实证方案。

---

## 下一层入口（按优先级排的方向清单）

1. **[P0-adj] L4-06 Git 对象模型（内容寻址 blob/tree/commit DAG）** —— 本组最强实证(`cat-file` + SHA 手算核对)，交付基座，fan-out 到所有协作。
2. **[P1★] L5-12 SLI/SLO/错误预算** —— 一手最硬(Google SRE 书) + 纯算术可实证，运行视角正中心，性价比最高。
3. **[P1] L5-13 namespaces + cgroups（容器机制根）** —— `unshare` 硬实证接 L4-01 OS；注意 daemon 缺失 + cgroup v1/v2 分账。
4. **[P0-adj] L4-06 测试金字塔 + CI/CD 门禁** —— 现代交付基座，可 git hook 演示最小 CI。
5. **[P1] L5-12 三支柱 metrics/logs/traces + OTel/W3C 分账** —— 概念框架(二手) vs 规范(一手)必须分账。
6. **[P1] L5-11 容量估算方法学** —— 系统设计唯一"独立于他课"的核心能力，Python 数量级演算可实证。
7. **[P1] L5-13 容器 vs VM + OCI 镜像分层** —— 部署心智，回指 OCI 规范。
8. **[P1] L5-11 组件选型 + CAP/一致性折衷（与 L5-01 分账）** —— 一手最弱、须大量⚠回指组件文档 + L5-01 论文，取证难度最高。
9. **[P1] L4-06 三向合并/merge-base + rebase vs merge** —— 团队策略取舍，`git merge-base` 可实证。
10. **[P1] L5-12 基于 SLO 的 burn-rate 告警 + SRE 文化(postmortem/toil)** —— SRE 书专章。

## 校准汇总

- **L4-06 软件工程：建议 P1 → 上调（P0-adjacent / 事实重要度提升）**。理由：(a) round1-map 已将 N-06 版本控制、N-07 测试与 CI/CD **并入并扩**，L4-06 现是**三门业界一等能力的合并体**，原 P1 定价基于"塞进一门课"的旧结构(view2 §B1 明指"结构性低估")；(b) 是本组**唯一可 git 硬实证**的课(对象模型 SHA 已本机核对通过)，取证性价比最高；(c) 全局 fan-out 广(所有课的产出都经 VC/测试/CI 交付)。**建议 Round 4 排批提前，且拆为 VC / 测试-CI 两个子对象群产出**(呼应 view2 §B1"拆分或单列子对象")。
- **L5-11 系统设计：维持 P1**，但标记**取证难度最高**——一手薄弱(无权威规范)，报告须大量⚠并回指 L5-01 论文 + 各组件官方文档；与 L5-01 的**理论/工程分账**是其头号边界(判据已给)。
- **L5-12 可观测性/SRE：维持 P1**，标记**性价比最高**(一手 Google SRE 书承重 + SLO 纯算术可实证)，建议优先排。核实点：OTel 各信号 GA 状态须 Round 4 联网核到当前。
- **L5-13 容器与云原生：维持 P1**，标记**实证方案约束**：本沙箱 daemon 不可达 → 走 `unshare`/`nsenter`；cgroup **v1 hybrid**(基线) vs v2(现代默认)须 delta 分账。
- **三处待 Round 4 落实的分账**：①L5-11 vs L5-01(工程装配 vs 理论证明，判据已给)；②L5-12 "三支柱"(社区框架⚠) vs OTel/W3C(规范一手)；③L5-13 cgroup v1(本机) vs v2(默认)。
- **跨组接口**：L5-11 组件选型↔L5-01 分布式(g? 分布式组)、↔L4-02 网络(LB/CDN/API 设计 N-09 已并入 L4-02)、↔L4-03 数据库(缓存/分片)；L5-13↔L4-01 OS(namespaces/cgroups 机制根)、↔L6-05 云系统专题(K8s 深度)；L4-06 CI↔全库(交付所有课产出)；L5-12 基准统计↔L2-03 概率(N-10 性能剖析的"基准统计有效性")。
- **一手承重纪律复述**：本组 L4-06(Pro Git)/L5-12(Google SRE 书 + OTel/W3C 规范)/L5-13(OCI 规范 + K8s 文档 + Linux man page) **一手强、承重**；L5-11 **一手弱、须打⚠回指**。所有版本/GA 状态时效核到 2026-07-25，不凭记忆。
