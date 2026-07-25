# 视角② 行业实践图谱 · 核实 2026-07-25

> 基线 Py3.11.15/np2.4.6/gcc13.3 @2026-07-25
> 立场：工程界/业界视角，非唯学术。业界来源多为社区二手（roadmap.sh、自学社区），**不承重**，仅提示线索；能指回一手（官方发布/标准）的已标出。
> 关键对照事实：连**面向工程师的自学正典** teachyourselfcs.com 也只列 9 门系统课（Programming / Computer Architecture / Algorithms & DS / Math for CS / Operating Systems / Networking / Databases / Languages & Compilers / Distributed Systems），并**明确不含**版本控制/测试/CI-CD/系统设计/可观测性/容器/API 设计/性能剖析——它把这些留给"在岗按需学"。[^1] 也就是说：这些主题被业界视为核心，却**普遍被学术型课表（含种子）排除或弱化**，正是本视角要补的缺口。roadmap.sh 的处理方式相反——它把 System Design、API Design、DevOps、Backend 各列为**独立 roadmap**，侧证业界视其为一等主题。[^2]

## A. 业界核心但种子缺失的主题（逐条：主题 | 建议归属层 | 工程重要性理由 | 来源）

### A1. 版本控制与协作工作流（Git 内部模型 / 分支策略 / PR 评审 / 合并冲突）
- **归属层**：第四层，隶属或紧邻 `L4-06 软件工程`（种子已把"版本控制/协作"塞进 L4-06 括号，但只是词条，未单列、无深度）。
- **工程重要性**：几乎所有工程协作的物理基座；DAG/对象存储/三向合并是可讲清的**机制**（可对应种子"文·证"取证——git 本机实证），不是软技能。种子把它降格为软工课的一个括号词，是典型的"学术弱化"。
- **来源**：teachyourselfcs 明确不含版本控制[^1]；roadmap.sh CS/Backend 均把 Git 列为起步必备[^2]。一手可指回 **Pro Git（官方书）+ `git` 源码/`git cat-file` 本机实证**（本轮未取，标待补）。⚠二手（线索层）

### A2. 系统设计（System Design：需求→容量估算→组件选型→权衡）
- **归属层**：第五层，横跨 `L4-02 网络`/`L4-03 数据库`/`L5-01 分布式`，但**本身是独立的"综合设计"能力**，种子无对应对象。
- **工程重要性**：中高级/资深工程师面试与实岗的核心考核项——负载均衡、缓存策略、分片/复制、CDN、消息队列（Kafka）、一致性与容错的**工程折衷**。种子的 `L5-01 分布式系统` 覆盖的是**理论**（Paxos/Raft/CAP/一致性模型），而 system design 是把这些理论**装配成可运行架构并做容量/成本权衡**的实践层，两者不可互相替代。
- **来源**：roadmap.sh 独立 System Design roadmap[^2]；system design 面试主题综述（HLD 考 API 设计/缓存/一致性/可观测性）⚠二手[^3]。理论侧一手仍落在 L5-01 的论文脚注，实践侧一手薄弱（业界经验为主）。⚠二手

### A3. 可观测性 / 监控 / 日志 / 追踪（Observability：metrics-logs-traces + SLI/SLO/错误预算）
- **归属层**：第四/五层，**种子完全无对应对象**（最接近的 L4-01 OS 只到 syscall/中断，不涉运行时可观测）。
- **工程重要性**：生产系统"什么坏了、为什么坏"的唯一系统化答案；三支柱（metrics/logs/traces）+ SLI/SLO/错误预算是 Google SRE 体系化的一等工程学科。种子六层几乎纯"构建视角"，**缺整个"运行/运维视角"**——这是学术课表最系统的盲区。
- **来源**：**一手** = Google《Site Reliability Engineering》/《SRE Workbook》官方全文 sre.google（SLO/SLI/错误预算/Alerting on SLOs 章节）[^4]。三支柱概念综述 ⚠二手[^5]。（注：SRE 为 Google 一方发布，属可追溯一手；三支柱说法为社区惯用框架，非标准。）

### A4. 测试策略与 CI/CD（测试金字塔 / TDD / 覆盖率 / 流水线自动化）
- **归属层**：第四层，`L4-06 软件工程` 括号内已列"测试/CI"，但**同样只是词条、被弱化**。
- **工程重要性**：单元/集成/端到端分层、CI 门禁、每次 push 自动测试与部署是现代交付基座；GitLab 报告称 DevOps 技能未来五年需求增长 ~122%（⚠二手数字，未核一手）。种子把测试+CI 与需求/架构/重构一起挤进单门 P1 软工课，权重明显偏低（见 B1）。
- **来源**：roadmap.sh Backend 列 unit/functional/integration + GitHub Actions CI/CD[^2]；teachyourselfcs 明确不含测试/CI[^1]。⚠二手

### A5. 容器与云原生（Docker / Kubernetes / 编排 / 不可变部署）
- **归属层**：第五层（方向），**种子无对应对象**（L5-07 方向选修可挂靠，但未登记）。
- **工程重要性**：当代部署与资源隔离的事实标准；容器机制本身可讲清（namespaces/cgroups——正好接 `L4-01 OS` 的进程/资源隔离，是 OS 知识的工程落地），编排解决调度/服务发现/弹性。种子有 L6-05"云系统专题"（研究生层、论文驱动），但缺**工程实践层**的容器/编排入门。
- **来源**：roadmap.sh 独立 DevOps roadmap + Backend 列 Docker/Kubernetes[^2]。一手可指回 **OCI 运行时规范 / Kubernetes 官方文档 / Linux namespaces & cgroups man page**（本轮未取，标待补）。⚠二手（线索层）

### A6. API 设计（REST 资源建模 / 版本化 / 认证授权 OAuth-JWT / 幂等 / 契约）
- **归属层**：第四/五层，可挂靠 `L4-02 网络`（HTTP 之上）或系统设计（A2）。种子 L4-02 到 HTTP/TLS 为止，**不涉 API 契约设计**。
- **工程重要性**：服务间协作的接口契约；REST/GraphQL、版本兼容、认证授权、幂等与错误语义是后端日常核心。种子网络课停在协议层，缺"如何设计好一个对外接口"的一等主题。
- **来源**：roadmap.sh 独立 API Design roadmap[^2]；system design HLD 明确考 API 设计[^3]。一手可指回 **RFC 9110（HTTP 语义）/ OAuth 2.0 RFC 6749 / OpenAPI 规范**（本轮未取，标待补）。⚠二手（线索层）

### A7. 性能剖析与工程调优（profiling / benchmarking / 火焰图 / 瓶颈定位）
- **归属层**：第五层，种子仅在 `L5-05 高级体系结构` 提"perf 实证"，但那是**硬件微架构视角**，非**应用级 profiling 方法学**（perf/flamegraph/采样 vs 插桩/基准测试统计有效性）。
- **工程重要性**：从"知道算法复杂度"到"在真实系统里定位真实瓶颈"的必经桥梁；与种子 L3-01 算法（渐进复杂度）互补而非重复——渐进分析给上界，profiling 给实测热点。
- **来源**：属工程通识，一手可指回 **`perf` / `gprof` man page + Brendan Gregg 方法学**（本轮未系统取证）。⚠二手（线索层）

> 说明：A3/A4/A5 合起来指向种子最大的结构性缺口——**"运行与交付视角"（Ops/Delivery）整体缺席**。种子六层是纯"从数学到系统的构建阶梯"，几乎不含系统上线后的运维、交付、可观测。若要贴合业界能力图谱，建议在第四/五层之间补一条"工程交付与运维"支线（或大幅扩容 L4-06）。

## B. 种子已有课的权重校正（低估/高估 + 依据）

### B1. `L4-06 软件工程`——**严重低估（欠拆分）**
- 现状：单门 P1，括号里塞了"需求/架构/**版本控制/测试/CI**/重构/协作"六七个主题，依赖标"经验前置"。
- 校正：业界把其中至少三块（版本控制 A1、测试+CI/CD A4、系统设计 A2 的雏形）当**各自独立的一等能力**。把它们压进一门 P1 课 = 结构性低估。建议：软工课拆分或至少把 A1/A4 提为单列子对象，权重上调。
- 依据：roadmap.sh 将 Git、Testing、CI/CD、System Design 分列为多个独立 roadmap[^2]；teachyourselfcs 干脆不收（视为在岗技能）[^1]——两种权威处理都说明"塞进一门课"不符合业界结构。⚠二手

### B2. `L5-01 分布式系统`——**理论权重合理，但工程落地被低估**
- 现状：P1，覆盖时钟/复制/共识/CAP/一致性模型（纯理论，论文驱动）。
- 校正：理论深度对标学术无误；但业界"分布式能力"= 理论 + **system design 装配**（A2）+ **可观测性**（A3）。种子只保留理论一半。建议：不改 L5-01 理论定位，另立 A2/A3 作为其工程孪生。
- 依据：system design 面试把分布式理论与 API/缓存/可观测性**一起**考[^3]。⚠二手

### B3. `L5-05 高级体系结构` 的"perf 实证"——**与应用级 profiling 混淆，需分账**
- 现状：P2，`perf` 本机实证挂在超标量/乱序/缓存一致性下。
- 校正：这属**微架构性能**，不等于**应用/服务级性能剖析**（A7）。二者取证工具重叠（perf）但方法学与目标不同，宜分账，避免读者以为"学了体系结构 = 会做应用调优"。
- 依据：A7 线索。⚠二手

### B4. `L1-04 微积分`——业界权重相对偏高（面向纯工程路径时）
- 现状：P1。
- 校正：对纯软件工程/后端路径，微积分日常使用频率低于线代/离散/概率；仅在 ML（L5-02/L6-03）/图形（L5-03）/数值（L6-06）方向承重。**注意**：这是"工程实践使用频率"口径的观察，不否定其作为地基的学术必要性——种子的层级依赖（微积分→概率→ML）本身成立。仅提示"通用工程路径"下可后置。⚠二手/口径差异，非硬结论。

> 未见明显"业界高估"的种子课：种子偏学术，问题几乎全在**弱化/缺失**方向，而非"列了业界用不上的"。L1–L4 系统课与 teachyourselfcs 的 9 门高度重合[^1]，说明地基选型与业界共识一致。

## C. 来源清单

一手 / 可追溯官方发布：
[^4]: Google, *Site Reliability Engineering* & *The Site Reliability Workbook*, sre.google（SLO/SLI/错误预算/Alerting on SLOs 等章节）。https://sre.google/workbook/index/ ——Google 一方发布，可追溯，属可承重一手（用于可观测性/SLO 定义）。核实 2026-07-25。

二手 / 社区curation（**不承重**，仅线索）：
[^1]: Teach Yourself CS, https://teachyourselfcs.com/ ——9 门核心系统课清单；WebFetch 直读确认其不含 VC/测试/CI-CD/系统设计/可观测性/容器/API/profiling。⚠二手（观点性正典，广泛引用）。核实 2026-07-25。
[^2]: roadmap.sh —— Computer Science https://roadmap.sh/computer-science ；Backend、System Design、API Design（https://roadmap.sh/pdfs/roadmaps/api-design.pdf ）、DevOps 各为独立 roadmap。⚠二手（社区 curation）。核实 2026-07-25。
[^3]: System design 面试主题综述（HLD 考 API/缓存/一致性/可观测性），engineeringenablement.substack.com 等。⚠二手。核实 2026-07-25。
[^5]: "Three pillars of observability"（metrics/logs/traces）社区框架综述，strongdm.com 等。⚠二手（概念框架，非标准；一手落 [^4]）。核实 2026-07-25。

待补一手（本轮未取证，供后续下沉）：Pro Git 官方书 + git 源码（A1）；RFC 9110 HTTP 语义 / RFC 6749 OAuth2 / OpenAPI 规范（A6）；OCI 运行时规范 / Kubernetes 官方文档 / namespaces & cgroups man page（A5）；`perf`/`gprof` man page + Brendan Gregg 方法学（A7）。
