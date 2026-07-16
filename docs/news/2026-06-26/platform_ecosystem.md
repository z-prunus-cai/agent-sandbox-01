# 云原生平台、数据工程与开发者生态综合情报简报

**情报窗口**：2026-06-24 至 2026-06-26（含弹性追溯至近期关键节点）
**情报等级**：L1 核心突破 / L2 关键演进 / L3 日常速递

---

## 🔴 Tier 1：核心突破、重大变革与范式巨震

---

### [Breaking Changes] Kubernetes v1.36 "Haru"：Ingress NGINX 正式退役，Admission Webhook 范式终结

**事件/架构全景**

Kubernetes v1.36（代号 Haru，发布于 2026-04-22）标志着 Kubernetes 生态中两条历史性遗留路径的同步终结。其一：SIG Network 与安全响应委员会于 2026-03-24 宣布 **Ingress NGINX 正式退役**，停止所有版本发布、漏洞修复与安全更新，意味着全球数以万计依赖该控制器的生产集群面临安全裸奔风险。其二：**MutatingAdmissionPolicies（MAP）晋级 Stable**，以基于 CEL（Common Expression Language）的原生 API Server 内联能力彻底替代 Mutating Webhook，传统 Webhook 在 v1.36 语境下被降格为"仅限需要外部调用的极窄场景"的临时过渡方案。此外，`gitRepo` volume 插件（KEP-5040）因允许以 root 身份在节点运行任意代码的根本性安全缺陷被永久移除，`externalIPs` 服务字段被标记为弃用（计划 v1.43 彻底删除）。

**底层机制/演进逻辑分析**

Kubernetes 正在执行其自 v1.25 以来酝酿的"清账"战略：通过将准入控制逻辑内化至 API Server（CEL 表达式评估在进程内完成），彻底消除 Webhook 的网络往返延迟（典型场景下降低 5~50ms/请求）、外部依赖可用性风险与扩展攻击面。MutatingAdmissionPolicies 使集群运维团队能够以声明式 YAML + CEL 表达式替换原有 Go/Python Webhook 服务器的完整生命周期（容器化、滚动更新、TLS 证书轮换、负载均衡）。同时，DRA（Dynamic Resource Allocation）在 v1.36 引入**优先设备列表（Prioritized List）Beta**——调度器可顺序评估 `H100 → A100 → 降级 GPU` 等偏好链，并将节点级 Taint/Toleration 模型延伸至设备层，使 AI 训练/推理工作负载的异构硬件调度从"尽力而为"升级为"拓扑感知的确定性策略"。

**生产架构影响与迁移指南**

凡运行 Ingress NGINX 的集群，需在 **2026-06-28**（Kubernetes 1.33 同步 EOL）前完成控制器迁移，推荐路径为 Gateway API + Envoy Gateway / Cilium Ingress / Istio Ingress Gateway 三选一，其中 Gateway API 已于 v1.2 正式 GA，具备最佳的长期官方支持承诺。对于 Mutating Webhook，团队应立即审计现有 Webhook 所执行的变更逻辑，凡不依赖外部 API 调用者可一对一迁移至 MAP，并通过 `audit` 模式先行验证 CEL 表达式。针对 DRA 能力，需确保 GPU 驱动（NVIDIA、AMD ROCm）已升级至支持 DRA ResourceClaim API 的版本；EKS 用户须额外关注 Amazon EKS 的 1.36 版本支持时间表并评估 Device Plugin → DRA 的并行迁移路径。

---

### [存储引擎重构] PostgreSQL 19 Beta 1：SQL/PGQ 属性图查询 + 并行自清理正式亮相

**事件/架构全景**

PostgreSQL Global Development Group 于 **2026-06-04** 发布 PostgreSQL 19 Beta 1，预计 GA 版本将在 2026 年 9-10 月落地。此版本的核心是 **SQL/PGQ（Property Graph Query）属性图查询**的首次实现——这是 ISO SQL:2023 标准新增的属性图查询语法，允许在关系型数据库中直接描述与遍历图结构（顶点/边），使 PostgreSQL 无需引入独立图数据库即可原生支持社交网络分析、知识图谱、供应链关系等图计算场景。其次，**并行 Autovacuum**（通过 `autovacuum_max_parallel_workers` 配置）解决了长期困扰高写入场景的单线程 Vacuum 瓶颈；**REPACK 命令**提供零停机在线表重组能力，替代了对 `pg_repack` 扩展的外部依赖；**ON CONFLICT DO SELECT** 允许 UPSERT 操作在冲突时返回现有行而不必执行多余的写操作，显著简化幂等写入逻辑。

**底层机制/演进逻辑分析**

SQL/PGQ 的引入标志着关系型数据库向"多模型融合"范式的实质性跨越：传统 JOIN 在深度图遍历（k-hop 查询）时呈指数级代价爆炸，而 PGQ 的 MATCH 语法允许声明式路径描述并由优化器选择最优遍历策略。PostgreSQL 19 同时在 I/O 层延续了 v18 引入的异步 I/O 子系统（`io_method=worker` 自动根据 `io_min_workers/io_max_workers` 动态扩缩 I/O 工作线程），配合新增的 `pg_plan_advice` 扩展实现查询计划锚定（Plan Stabilization），为金融、电商等对执行计划漂移高度敏感的场景提供生产级保障。`default_toast_compression` 默认值切换为 `lz4`（原 pglz），在压缩速度提升 3~10x 的同时保持相近压缩率，对大量存储 JSONB/TEXT 的业务场景有直接吞吐收益。JIT 默认禁用则是对"JIT 编译开销 > 查询加速收益"这一常见生产坑点的系统性修正。

**生产架构影响与迁移指南**

图计算需求团队应立即将 SQL/PGQ Beta 纳入技术评估，尤其是已使用 Apache AGE 或 Neo4j 承载中等规模图查询的架构；PostgreSQL 19 + PGQ 的图查询能力在数百万节点规模下已具备竞争力，且消除了独立图数据库的运维、同步与一致性负担。对于 Autovacuum 优化：高写入 OLTP 场景应预设 `autovacuum_max_parallel_workers = 4`（建议不超过核心数的 1/4），并结合新 Autovacuum 优先级打分系统调整每表阈值。REPACK 命令可替代现有 `pg_repack` 运维脚本，迁移成本极低。所有使用 `ON CONFLICT DO NOTHING/UPDATE` 返回值的应用层逻辑可评估迁移至 `ON CONFLICT DO SELECT`，减少一次 SELECT 往返。Beta 阶段请勿在生产使用，预计 RC 窗口在 2026-08 前后。

---

### [开源协议变更] Redis/Elastic AGPL 回归潮：BSL/SSPL 的市场反制机制全景

**事件/架构全景**

继 Elastic 于 2024-08 率先将 Elasticsearch 和 Kibana 切回 AGPL 之后，Redis 创始人 Salvatore "antirez" Sanfilippo 主导完成了 **Redis 8 对 AGPLv3 的回归**（2025-05 落地），彻底告别 2024-03 强行切换 SSPL 所引发的生态危机。与此同时，HashiCorp 被 IBM 以 64 亿美元收购后，Terraform 的 BSL（Business Source License）桎梏继续生效，直接催熟了 Linux Foundation 旗下的 **OpenTofu 分叉**——后者以 HCL 兼容切入并承诺长期 Apache 2.0 开源授权，目前已进入 CNCF TOC 审议阶段，社区采用率在企业级用户中快速上升。这一系列事件正在重塑云原生技术栈的选型基本盘。

**底层机制/演进逻辑分析**

BSL 与 SSPL 的本质是"开源清洗（Open Washing）"：在项目成熟度临界点以竞争性条款锁定商业化上游，试图从云厂商变现。AWS/Google/Oracle 等云厂商通过主导社区分叉（Valkey、OpenSearch、OpenTofu）的方式完成了有效反制：分叉项目获得主要云厂商的工程投入与托管支持，形成可持续竞争生态，对原厂商形成"分叉倒逼"压力，最终逼使 Redis 和 Elastic 相继回归 OSI 认可的 AGPL。AGPL 作为妥协方案的逻辑在于：它允许任何人自由使用、修改、分发，但要求基于 AGPL 代码的网络服务（SaaS）也必须开放源码，形成对云厂商"免费托管"的柔性对冲，无需诉诸 SSPL 的粗暴条款。当前 OSI 认可许可证占比从 2022 年的 82% 下滑至 73%（2025 年），BSL/SSPL 仍集中在战略性高价值项目中。

**生产架构影响与迁移指南**

技术选型团队必须将开源协议健康度纳入数据基础设施选型的一级评估维度。实操建议：凡采用 MongoDB（SSPL）、MinIO（AGPL）作为核心存储层的团队，需要法务明确评估其生产使用场景是否触发协议义务，尤其是作为 SaaS 服务内嵌组件时的开源义务链条。对于 Terraform 用户，应尽快评估迁移至 OpenTofu（工具链兼容、状态文件可直接复用），锁定于 Terraform BSL 将在合规审计中面临持续升级的法律不确定性，尤其是涉及金融、医疗等受监管行业。Redis 8 回归 AGPL 意味着 Valkey 与 Redis 在纯开源属性上趋于对等，但 Valkey 已构建独立的社区动能与分布式治理结构，架构选型时需同时评估社区活跃度与长期供应商风险。

---

### [云原生大版本] Kubernetes AI-Native 转型：DRA GA + GPU 推理调度 + CNCF llm-d 贡献

**事件/架构全景**

2026 年上半年，Kubernetes 的战略重心完成了从"容器调度平台"到"AI 基础设施底座"的正式跨越。CNCF 公布数据：**66% 的组织已在 Kubernetes 上运行生成式 AI 推理工作负载**，其中 82% 使用 Kubernetes 管理 AI 基础设施。2026-06-02，CNCF 官方博客宣告"云原生即 AI 原生（Cloud native is now AI-native）"战略转型。与此配套：**Gateway API Inference Extension（推理网关）正式 GA**，提供基于模型名称、LoRA 适配器、端点健康度的 Kubernetes 原生推理流量路由；CNCF 接收 Red Hat 贡献的 **llm-d 框架**，专为跨 Kubernetes 集群分布式 AI 推理工作负载设计；Volcano 调度器完成"批处理 → AI 原生统一调度平台"的定位进化，支持分布式训练中的资源死锁规避与 Gang Scheduling。

**底层机制/演进逻辑分析**

DRA（Dynamic Resource Allocation）在 Kubernetes 1.34 正式 GA，从根本上解决了 Device Plugin 模型的核心局限——Device Plugin 仅能暴露设备数量，无法表达 GPU 间 NVLink 拓扑、NUMA 亲和性或 MIG（Multi-Instance GPU）切片信息。DRA 通过 `ResourceClaim` + `ResourceSlice` API 将设备拓扑信息以声明式结构体暴露给调度器，配合 CEL 过滤规则实现真正的拓扑感知调度。v1.36 进一步引入**设备健康信号（Device Health Signaling）**，驱动可向调度器上报设备运行时健康状态，使调度器能在故障设备检出后实时排除——这是大规模 GPU 集群中"坏卡静默污染训练任务"这一顽疾的根治方案。推理网关将服务路由从"IP/端口"抽象提升至"模型语义层"，允许同一 Kubernetes Service 背后的多个推理服务实例按模型版本、LoRA 版本分流，为多租户模型推理平台奠定基础。

**生产架构影响与迁移指南**

AI 基础设施团队应立即将 Device Plugin 迁移计划提上日程，路径为：评估现有 NVIDIA/AMD 驱动是否已发布 DRA 插件（NVIDIA GPU Operator v24.x 已包含 DRA 支持）；以双写方式并行运行 Device Plugin 与 DRA 至少一个迭代周期以验证调度一致性；逐步将新提交的 AI 工作负载切换至 `ResourceClaim` 声明。推理平台团队应调研 Gateway API Inference Extension，将现有 Nginx/Envoy 路由规则迁移至基于模型语义的 InferencePool/InferenceModel 资源，以支持未来多模型、多租户灰度发布场景。平台工程团队应跟进 Volcano 与 llm-d 的上游进展，尤其是其对 KubeFlow Pipeline / Ray 的集成路径。

---

## 🟡 Tier 2：关键演进、生产工程实践与生态风向

---

### [GA 正式版] OpenTelemetry Blueprints 计划正式发布：企业级可观测性标准化的关键拐点

`[平台工程实践]`

**核心增量**：2026-06 OpenTelemetry 正式推出 "Blueprints" 计划（[官方公告](https://opentelemetry.io/blog/2026/blueprints-intro/)），提供面向 Kubernetes 可观测性、Kubernetes 外基础设施监控、集中式遥测平台三大场景的规范化参考实现架构，Adobe、Skyscanner 等真实落地案例已作为首批参考实现开源。

**核心工程思想**：Blueprints 的本质是解决"意外复杂性（Accidental Complexity）"——企业在无中央化标准的情况下有机采用 OTel，导致遥测管道碎片化、Semantic Convention 不一致、跨服务上下文传播断裂。Blueprint 提供可组合的规范化模式库，团队按需组合而非从零构建。

**落地行动指南**：平台工程团队应立即对照 Kubernetes 可观测性 Blueprint 评估现有 OTel Collector 部署拓扑，重点检查 Context Propagation 完整性（W3C TraceContext + Baggage）以及 Resource Attribute 的一致性（`service.name`、`k8s.cluster.name` 等）；将 Blueprint 参考实现作为内部 IDP（Internal Developer Platform）可观测性模板的规范基线，强制新服务接入时通过模板校验。

---

### [GA 正式版] DuckLake 1.0：SQL 元数据驱动的 Lakehouse 格式进入生产就绪

`[存储引擎重构]`

**核心增量**：DuckLake 1.0 于 2026-04-13 发布生产就绪版本（[官方公告](https://ducklake.select/2026/04/13/ducklake-10/)），DuckDB v1.5.2 内置 `ducklake` 扩展实现参考实现。Spec 1.0 新增特性：已排序表（Sorted Tables）、桶分区（Bucket Partitioning）、数据内联（Data Inlining）、几何数据支持、以及与 Apache Iceberg 兼容的删除向量（Deletion Vectors）。元数据存储后端支持 SQLite、PostgreSQL、DuckDB 三种 SQL 系统。

**核心工程思想**：DuckLake 以"元数据即关系表"颠覆了 Iceberg/Delta 元数据存储于 Parquet+JSON 文件的传统模型，使 Schema Evolution、Time Travel、ACID 事务全部转化为 SQL 事务操作，规避了对象存储上的分布式锁与列表操作瓶颈——这正是 Iceberg 在高频小文件提交场景下的历史顽疾。

**落地行动指南**：对于使用 DuckDB 进行嵌入式分析的团队，DuckLake 1.0 已可作为轻量级 Lakehouse 层引入，尤其适合 SaaS 产品内的多租户分析场景（每个租户一个 DuckLake catalog）。Iceberg 用户可通过 `Iceberg-compatible deletion vectors` 特性进行互操作性评估；MotherDuck 已提供托管 DuckLake 服务，可减少运维负担。DuckLake v1.1 规格预计 2026-09 发布，建议主要特性待 v1.1 后再做大规模生产迁移。

---

### [Breaking Changes] Kubernetes v1.36 DRA 调度深化：优先级设备列表与设备污点/容忍机制

`[云原生大版本]`

**核心增量**：v1.36 中 DRA 调度器引入 Prioritized Device List（Beta），允许在 `ResourceClaimSpec` 中声明 `{"H100": priority 1, "A100": priority 2, "V100": priority 3}` 的降级偏好链。Device Taints and Tolerations 亦晋升至 Beta，使运维团队可将"硬件维护中"、"轻微性能降级"等状态以 Taint 形式标注，工作负载通过 Toleration 声明接受程度——与节点污点完全对称的语义模型。调度器设备排序现基于 ResourcePool 与 ResourceSlice 名称的字典序，驱动开发者可通过命名约定影响调度优先级。

**核心工程思想**：优先级设备列表解决了 AI 集群最典型的"高性价比 GPU 利用率洼地"问题——大量 A100 闲置而所有任务排队等 H100，根因是缺乏弹性降级的 API 表达能力。现在调度器可自动将任务降级到次优设备，显著提升集群整体吞吐量。

**落地行动指南**：AI 平台团队应立即在开发集群启用 DRA + Prioritized List Beta，重新设计 AI 训练/推理 Job 的 `ResourceClaim` 规范，为关键任务设定严格偏好（H100-only），为批量实验任务设定宽松降级链。基于此调度策略，可显著降低 GPU 集群整体 Reserved 成本。

---

### [GA 正式版] ClickHouse 25.8 LTS：向量搜索 HNSW 正式 GA + 二进制量化

`[存储引擎重构]`

**核心增量**：ClickHouse 25.8 LTS 版本将向量相似性搜索（基于 HNSW 算法）的状态提升至 **General Availability**，支持 `bf16`、`i8`、`b1` 三种量化精度（Binary Quantization），并引入 Index-Only Reading、Fetch Multiplier 与过采样+重排序（Oversampling + Rescoring）机制。同版本新增 PromQL 支持增强与 Apache Iceberg 集成改进。

**核心工程思想**：二进制量化（b1）将每个向量的内存占用从 `维度×4字节` 压缩至 `维度/8 字节`（压缩比 32×），配合 HNSW 图索引的局部近邻搜索语义，使 ClickHouse 在内存受限的大规模 RAG 场景（千万级向量）中具备与专用向量数据库（Weaviate、Qdrant）竞争的实际可行性，同时保留 ClickHouse 强大的 SQL 分析能力——实现"分析+向量"的统一查询平面。

**落地行动指南**：对于已使用 ClickHouse 作为事件/日志/行为数据主存储的团队，25.8 LTS 使"在同一存储层叠加语义搜索"成为可行路径：将 Embedding 列直接存入 ClickHouse，通过 `CREATE INDEX ... USING hnsw` 建立 HNSW 索引，免去额外向量数据库的同步开销与一致性问题。对内存敏感场景优先评估 `b1` 量化，配合 Rescoring 保持召回质量。

---

### [Breaking Changes] Swift 6.3 正式引入 Android SDK：Apple 生态跨平台边界全面打开

`[生态政策调整]`

**核心增量**：Apple 于 2026-03-24 发布 Swift 6.3，首次包含**官方 Android SDK**，支持将 Swift 代码直接编译为原生 Android ARM 二进制，性能对标 Android NDK 的 C++ 代码。Swift 可通过 Swift-Java / Swift-Java-JNI-Core 与现有 Kotlin/Java Android 工程深度集成，允许在 Kotlin 协程生命周期内调用 Swift 模块。

**核心工程思想**：Swift 6.3 Android 支持从生态竞争层面直接压缩了 Flutter/KMP 的差异化空间，具备 Swift 代码库的 iOS 团队现可以"业务逻辑层共享"的方式延伸至 Android，而无需重写为 Kotlin 或引入 Dart 运行时。这与 Kotlin Multiplatform 的策略镜像对称——双方都在以"本语言延伸至对方平台"的方式争夺共享逻辑层的主导权。

**落地行动指南**：纯 iOS 团队若有 Android 扩展计划，应将 Swift 6.3 Android SDK 纳入技术评估（尤其是算法密集型业务逻辑，如密码学、ML 推理）。对于已有 Kotlin Multiplatform 或 Flutter 布局的团队，Swift Android 当前仍缺乏完整的 UI 层方案，不建议用于全栈跨端开发，应定位为"高性能本地逻辑层的补充路径"。

---

### [平台工程实践] Flutter 3.32 "The Great Thread Merge" GA：同步 FFI 重构原生交互模型

`[Breaking Changes]`

**核心增量**：Flutter 3.32 Stable 将 UI Thread 与 Platform Thread 合并默认启用（iOS/Android），Dart 代码直接运行于原生平台线程。Flutter 3.33 Beta 已将此特性延伸至 Windows/macOS。合并后，开发者可通过 FFI 同步调用 Swift/Kotlin 原生 API，彻底消除 Platform Channel 的异步封装开销（典型往返延迟从 0.5~5ms 降至微秒级）。

**核心工程思想**：Platform Channel 的异步模型虽提供了线程安全隔离，但引入了不可避免的序列化（MethodChannel 数据需经 codec 编解码）与调度延迟，是高频原生调用场景（如相机帧处理、蓝牙实时流）的瓶颈根源。Thread Merge 以牺牲"绝对线程隔离"换取"零拷贝同步调用"，是对过度设计的矫正。

**落地行动指南**：Flutter 团队应检查现有 Platform Channel 代码是否存在对"UI/Platform 线程分离"的隐式假设（尤其是在原生侧直接操作 UI 组件的场景），并在升级至 3.32 时启用 opt-out flag 过渡。计划新建模块的团队可直接采用 FFI 同步路径替代 MethodChannel，代码简洁度与性能双提升。

---

### [生态政策调整] OpenTofu 进入 CNCF 审议 + Crossplane v2 重塑 Kubernetes-Native IaC

`[平台工程实践]`

**核心增量**：OpenTofu（Linux Foundation 旗下 Terraform BSL 分叉）正式进入 CNCF TOC 审议阶段，将与 Argo CD、Flux 共同形成"GitOps + IaC"的 CNCF 标准工具链闭环。Crossplane v2（2025-08 GA）全面重构：Composite/Managed Resources 默认命名空间化，移除独立 Claim 概念，将 Composition 从 patch-and-transform 迁移至 Composition Functions（可用任意语言编写），并允许 Composition 包含任意 Kubernetes 资源——而不仅限于 Crossplane 管理的基础设施。

**核心工程思想**：Crossplane v2 的本质转变是将"IaC 控制平面"从"Terraform 的 K8s 适配层"进化为"真正的 Kubernetes-Native 基础设施 API"——任何云资源、Helm Chart、自定义 CRD 都可统一纳入 Crossplane 的 Composite Resource 抽象，实现平台工程团队与开发团队的界面分离。

**落地行动指南**：Terraform 用户应优先评估 OpenTofu 迁移（状态文件完全兼容，Provider 生态已大幅追平），尤其是受合规审计约束的金融/医疗团队应将 BSL 商业使用风险上报法务评估。对 Crossplane 已有部署的团队，Crossplane v2 的 breaking changes（命名空间化、Claim 移除）需要一次有计划的迁移窗口，建议在新集群先行试点。

---

## 🟢 Tier 3：日常风向与情报速递

---

- **Lima → CNCF Incubating（2026-06-24）**：轻量级 Linux 虚拟机管理器 Lima（macOS 上的 Colima 底层）正式晋升 CNCF Incubating，为 macOS 开发者本地 Kubernetes 开发环境提供了更稳固的上游治理保障。

- **Microcks → CNCF Incubating（2026-05-07）**：API Mocking 与测试工具 Microcks（支持 OpenAPI/AsyncAPI/gRPC/GraphQL）完成沙箱到孵化阶段晋升，2025 年容器镜像下载量达 250 万次（3x 增长），34 家公开采用者，契合微服务测试左移趋势。

- **Kubernetes 1.33 将于 2026-06-28 正式 EOL**：仍在运行 v1.33 的生产集群需在本周内完成升级规划，推荐直接跳至 v1.35 或 v1.36 以获得最长支持窗口。

- **Kubernetes 1.37 Production Readiness Freeze（2026-06-10）已通过**，Enhancement Freeze 于 06-17 锁定，Release 目标日期为 2026-08-26；平台团队可开始跟踪 KEP 状态以评估新特性采用时机。

- **CNCF 云原生开发者社区规模突破 2000 万**：Q1 2026 数据显示全球云原生开发者达 1990 万，6 个月增长 28%；AI 开发者中 730 万已被归类为"云原生 AI 开发者"，印度社区规模达 225 万居全球第四。

- **KubeCon + CloudNativeCon India 2026（06-18/19，孟买）落幕**：55 场会议聚焦 AI 工作负载编排、GPU 管理、可观测性与平台工程成熟度；CNCF 宣布新增 14 名 Silver 成员（含 Cast AI、Chainguard、Microsoft Azure、VMware by Broadcom 等白金赞助商）。

- **PostgreSQL 18.4/17.10/16.14 安全维护版本同步发布**：本轮补丁修复若干 SQL 注入防护与权限检查漏洞，建议所有 PostgreSQL 生产实例在一个维护窗口内完成更新。

- **DuckLake v1.1 规格预计 2026-09 发布**：将引入更多 Iceberg 互操作特性，建议大规模生产迁移等待 v1.1 稳定后进行。

- **Kotlin Multiplatform 采用率一年内从 7% 跃升至 18%**（JetBrains 开发者生态调查）：Google 官方宣布在 I/O 2024 后持续加大对 KMP 的工程投入，跨端业务逻辑共享层主战场格局趋于明朗。

- **Flutter 3.33 Beta 已将 Thread Merge 延伸至 Windows/macOS**：意味着同步 FFI 调用能力将跨全平台统一落地，桌面平台 Flutter 应用与原生 Windows/macOS API 的高频交互场景将迎来显著性能红利。

- **OpenTofu 1.8+ 已实现 Provider-Defined Functions 与 Early Variable Evaluation**：这两项特性 HashiCorp Terraform 均未发布，OpenTofu 正式进入功能超越阶段，对尚在观望的团队构成迁移催化剂。

- **CNCF Kubernetes AI Conformance Program 强制要求**：In-Place Pod Resizing（推理模型资源无重启动态调整）与工作负载感知调度（防分布式训练死锁）已列为 K8s AI 合规认证的必需能力，云厂商托管 K8s 服务将陆续跟进合规认证。

- **pg_duckdb 扩展成熟度持续提升**：通过将 DuckDB 引擎嵌入运行中的 PostgreSQL 进程，实现对同一数据集的 OLTP 写入 + OLAP 分析，ClickBench 排名已进入前 10，是中小规模分析场景规避存算分离架构复杂度的可行路径。

- **Volcano 调度器正式定位为"AI 原生统一调度平台"**（CNCF 博客 2026-03）：支持 Gang Scheduling、Queue/Priority 管理、分布式训练资源死锁规避，已成为 Kubernetes 上 AI 批处理工作负载的事实调度标准之一，建议 AI 平台团队与 NVIDIA GPU Operator DRA 插件联合评估。
