# 第12组 工程与交付 · Round 3b 小主题分解 · 核实 2026-07-25

> 基线 Py3.11.15/np2.4.6/gcc13.3 @2026-07-25；本组实证工具链：git 2.43.0 / docker 29.3.1（**client-only，daemon 不可达**）/ unshare(util-linux 2.39.3) / cgroup **v1 hybrid** / 内核 6.18.5。
> 输入：`round3a-topics/g12-engineering-delivery.md`（大主题清单+权威锚点）。本文件把每个大主题（原编号）再拆为小主题 `X.Y`：次级条目、覆盖全、少重叠、按教学序，每条一句话范围 + 标本机实证机会。
> **承重纪律**：Pro Git / Google SRE 书+Workbook / OCI 规范 / K8s 官方文档 / Linux man page / W3C / OpenTelemetry 承重；业界二手（roadmap.sh、Cohn/Fowler/Beck、CI 工具文档、"三支柱"综述）打 ⚠，仅指路/补留白，不承重。锚定权威不编造；不确定标"待核"。清单非讲解。核实 2026-07-25。

---

## L4-06 软件工程（并入并扩 VC/测试/CI-CD）

> 锚点：Pro Git 2nd ed（git-scm.com/book/en/v2，强·一手）· MIT 6.031 sp22（准一手课程）· Cohn/Fowler/Beck（⚠准一手）· GitHub/GitLab CI docs（⚠二手）。实证主场：Git 对象/引用/合并可 `git cat-file`/`hash-object`/`merge-base` 硬实证；CI 走 git hook 最小演示。

### 06-1 软工过程与工程质量观
- **06-1.1 软件生命周期阶段** — 需求→设计→实现→演进→维护的活动划分与"维护占主要成本"直觉。
- **06-1.2 瀑布 vs 迭代/敏捷** — 大批量顺序 vs 小批量迭代的反馈周期取舍（⚠行业框架，非规范）。
- **06-1.3 工程质量属性** — 正确/健壮 vs 可读/可维护/可演进的外部与内部质量分层（MIT 6.031「safe from bugs / easy to understand / ready for change」）。
- **06-1.4 面向变更的设计总纲** — 把"为演进而设计"作为统摄本组其余单元的元原则。

### 06-2 规约、契约与抽象数据类型
- **06-2.1 方法规约：前置/后置条件** — 用 requires/effects 把实现与客户端解耦为契约（MIT 6.031 Specifications）。
- **06-2.2 规约的强弱与替换** — 更强前置=更弱、更强后置=更强；规约比较与安全替换直觉（Designing Specifications）。
- **06-2.3 ADT 操作分类** — creator/producer/observer/mutator 四类，以操作而非表示定义类型（Abstract Data Types）。
- **06-2.4 表示不变量 RI 与抽象函数 AF** — RI 界定合法表示、AF 把表示映射到抽象值，checkRep 守护（AF & RI）。
- **06-2.5 表示暴露与防御拷贝** — rep exposure 破坏不变量的机理与不可变/拷贝防御。

### 06-3 静态检查与类型安全防御
- **06-3.1 静态 vs 动态检查** — 编译期可消除的错误类别与"越早越省"（MIT 6.031 Static Checking）。
- **06-3.2 类型安全与不可变性作防御** — 用类型/final/不可变把整类错误设计消除。
- **06-3.3 "避免调试"纪律** — fail-fast、断言、局部化错误传播（Avoiding Debugging）。
- **06-3.4 linter 与静态分析工具链** — 编译器警告/类型检查器/静态分析在门禁中的定位（⚠工具生态）。

### 06-4 Git 对象模型（内容寻址 DAG）★本组最强实证点
- **06-4.1 blob 与内容寻址** — 文件内容→"blob "+len+NUL+内容取 SHA；`git hash-object`/`cat-file` 手算可硬实证（Pro Git §10.2）。
- **06-4.2 tree 对象与目录快照** — mode+type+sha+name 表示目录；`git cat-file -p <tree>` 实证。
- **06-4.3 commit 对象与父指针 DAG** — tree+parent(s)+author/committer+msg 构成不可变提交图；手拼 commit 复算 SHA 实证。
- **06-4.4 annotated tag 对象** — 指向对象+标注者+消息的第四类对象 vs 轻量 tag（仅引用）。
- **06-4.5 对象存储：loose vs packfile** — zlib 压缩、松散对象、pack 与 delta 压缩（§10.4 Packfiles）；`git gc`/`verify-pack` 可观察。SHA-1→SHA-256 过渡状态待核本机 git 2.43。

### 06-5 Git 引用/分支/HEAD 指针机制
- **06-5.1 refs/heads 分支=轻量指针** — 分支只是指向 commit 的 41 字节文件（Pro Git §10.3）；`cat .git/refs/heads/*` 实证。
- **06-5.2 HEAD 与 detached HEAD** — 符号引用 vs 直接指向 commit 的分离态。
- **06-5.3 tags：轻量 vs 附注** — refs/tags 引用与附注 tag 对象的区别。
- **06-5.4 reflog 与可恢复性** — 引用移动历史作"后悔药"；`git reflog` 找回悬空 commit。
- **06-5.5 远程跟踪引用** — refs/remotes/* 作本地缓存的远端状态。

### 06-6 三向合并与 merge-base（rebase vs merge）
- **06-6.1 merge-base 与共同祖先** — `git merge-base` 求最近公共祖先作三向基点（可实证）。
- **06-6.2 三向合并算法** — base/ours/theirs 三方 diff 合成结果的机制。
- **06-6.3 快进 vs 真合并** — fast-forward 移指针 vs 生成合并 commit（两父）。
- **06-6.4 rebase 线性改写与黄金规则** — 重放提交改写历史；"勿 rebase 已推送公共分支"（Pro Git §3.6）。
- **06-6.5 冲突标记与解决** — `<<<<<<<`/`=======`/`>>>>>>>` 区块；语义解决属人工（教学边界）。

### 06-7 分布式协作、PR 评审与传输协议
- **06-7.1 分布式模型** — clone/fetch/push 的完整历史复制语义 vs 集中式。
- **06-7.2 贡献/维护工作流** — 集成管理者、dictator-lieutenants 等协作拓扑（Pro Git ch5/ch6）。
- **06-7.3 refspec 映射** — `+src:dst` 如何决定 fetch/push 的引用对应。
- **06-7.4 传输协议** — local/smart-http/ssh 的握手与数据交换（§10.6；跨 L4-02）。
- **06-7.5 代码评审门禁** — PR 评审作变更进主干的人工质量关（MIT Code Review）。

### 06-8 分支策略
- **06-8.1 trunk-based development** — 短命名分支/直接主干、频繁集成（⚠行业）。
- **06-8.2 GitFlow/长命名分支模型** — develop/release/hotfix 多长命名分支及其开销。
- **06-8.3 feature flag 与发布解耦** — 用开关把"部署"与"发布"解耦，允许主干半成品。
- **06-8.4 策略选型** — 按团队规模/发布节奏/评审文化取舍（团队策略非机制）。

### 06-9 测试金字塔与测试分层
- **06-9.1 unit/integration/e2e 分层** — 三层的速度/稳定性/成本结构（Cohn ⚠）。
- **06-9.2 冰淇淋筒反模式** — 重 e2e 轻 unit 导致慢而脆（Fowler ⚠）。
- **06-9.3 测试用例选择** — 等价类划分、边界、glass-box vs black-box（MIT Testing）。
- **06-9.4 契约/组件测试** — 微服务语境下替代重 e2e 的消费者驱动契约（⚠）。

### 06-10 测试替身与覆盖率解读
- **06-10.1 替身谱系** — dummy/stub/spy/mock/fake 五类区分（Meszaros/Fowler ⚠）。
- **06-10.2 状态验证 vs 行为验证** — 断言结果 vs 断言交互的取舍。
- **06-10.3 覆盖率类型与陷阱** — 行/分支/路径覆盖；"高覆盖率≠高质量"。

### 06-11 TDD 与测试作为设计压力
- **06-11.1 红-绿-重构循环** — 先失败测试→最小实现→重构的节奏（Beck ⚠）。
- **06-11.2 测试先行作设计压力** — 可测性倒逼解耦/依赖注入。
- **06-11.3 重构安全网** — 回归测试保障重构不改行为。

### 06-12 CI 持续集成与门禁 ★git hook 可演示最小 CI
- **06-12.1 CI 核心实践** — 频繁集成、主干始终可构建可发布（⚠工具无关概念）。
- **06-12.2 流水线门禁阶段** — 构建→单测→静态检查→合并的自动关卡。
- **06-12.3 制品与构建缓存** — artifact 产出/版本化与缓存加速。
- **06-12.4 git hooks 本地门禁** — pre-commit/pre-push 拦截；本机 `.git/hooks` 可写脚本硬实证最小 CI。
- **06-12.5 构建可复现与依赖锁定** — lockfile/固定版本保证可重复构建。

### 06-13 CD：持续交付 vs 持续部署
- **06-13.1 delivery vs deployment 分账** — "随时可发布" vs "自动发布到生产"的界线。
- **06-13.2 环境晋级流水线** — dev→staging→prod 逐级门禁晋级。
- **06-13.3 部署策略** — 蓝绿/滚动/金丝雀（机制细节跨 L5-12 发布工程、L5-13 编排）。
- **06-13.4 回滚与可回退性** — 快速回退作发布安全网。

---

## L5-11 系统设计（取证难度最高，一手最弱）

> 锚点：DDIA 2nd ed（准一手教材，佐证非规范）· roadmap.sh（⚠指路）· 各组件官方文档 + 理论回指 L5-01（一手回指）。凡无组件官方文档支撑者一律 ⚠。实证：容量估算走 Python 数量级演算。

### 11-1 需求与非功能目标定义
- **11-1.1 功能 vs 非功能需求分离** — 把"做什么"与"多快/多可靠/多省"分开（DDIA2 Defining Nonfunctional Requirements）。
- **11-1.2 SLA/延迟/吞吐/成本目标量化** — 把业务约束翻成可度量目标（跨 L5-12 SLI/SLO）。
- **11-1.3 数据规模与增长假设** — 当前量级与增长率作为设计输入。
- **11-1.4 约束→架构驱动因子映射** — 从目标推导必需的容量/复制/分片决策。

### 11-2 容量估算方法学（back-of-envelope）★Python 数量级演算实证
- **11-2.1 数量级基准数** — 延迟/带宽/字节大小的量级直觉（"latency numbers"）。
- **11-2.2 QPS 反推** — 从 DAU/请求率推峰值 QPS（读写比、峰均比）。
- **11-2.3 存储与带宽估算** — 记录大小×量×保留期→容量；Python `assert` 数量级演算可实证。
- **11-2.4 实例数/资源反推** — 单机吞吐→所需实例数×冗余系数（SRE Workbook ch12 NALSD 一手佐证）。

### 11-3 负载均衡与流量分发
- **11-3.1 L4 vs L7 负载均衡** — 传输层转发 vs 应用层路由（Envoy/Nginx docs 一手回指）。
- **11-3.2 均衡算法** — round-robin/least-conn/一致性哈希及其粘性。
- **11-3.3 健康检查与故障剔除** — 主动/被动探测下线不健康后端。
- **11-3.4 横向扩展与无状态化** — 会话外置以支撑任意实例扩缩（跨 L5-13 Service）。

### 11-4 缓存策略
- **11-4.1 多层缓存拓扑** — 客户端/CDN/应用/DB 缓存的层次与命中递进。
- **11-4.2 读写模式** — cache-aside/read-through/write-through/write-back 取舍（Redis docs 回指）。
- **11-4.3 失效与 TTL** — 过期/主动失效与一致性窗口。
- **11-4.4 命中率与病理** — 缓存穿透/击穿/雪崩及防护。

### 11-5 数据分片 sharding
- **11-5.1 范围分区** — 按 key 区间划分，利于范围查但易热点（DDIA2 Partitioning）。
- **11-5.2 哈希分区/一致性哈希** — 打散热点 vs 丧失范围性；扩缩迁移量。
- **11-5.3 二级索引分区** — 本地（scatter-gather）vs 全局（词条分区）。
- **11-5.4 再平衡与热点治理** — rebalancing 成本与热点分裂（理论回指 L5-01）。

### 11-6 复制 replication
- **11-6.1 单主复制** — 主写从读、故障切换（DDIA2 Replication）。
- **11-6.2 多主复制与冲突** — 多写点的写冲突与收敛。
- **11-6.3 无主/quorum** — R+W>N 的读写仲裁。
- **11-6.4 同步 vs 异步与复制滞后** — 持久性 vs 延迟；读己之写等异常（协议正确性回指 L5-01）。

### 11-7 一致性-可用性-延迟折衷
- **11-7.1 CAP 工程解读** — 网络分区下必舍 C 或 A（DDIA2 ch10；定理证明归 L5-01）。
- **11-7.2 PACELC** — 无分区时延迟 vs 一致性的常态折衷。
- **11-7.3 一致性谱系工程含义** — 强/因果/最终一致对应用可见行为的影响。
- **11-7.4 AP/CP 选型后果** — 按业务对陈旧/不可用的容忍度选边。

### 11-8 消息队列与异步/事件驱动
- **11-8.1 削峰/解耦/异步动机** — 队列缓冲突发、解耦生产消费。
- **11-8.2 投递语义** — at-most/at-least/exactly-once 与去重代价（Kafka/RabbitMQ docs 回指）。
- **11-8.3 日志型 vs 队列型** — Kafka 顺序高吞吐 vs RabbitMQ 灵活路由的取舍。
- **11-8.4 事件驱动架构与背压** — 事件流拓扑与消费滞后/背压（顺序/复制回指 L5-01）。

### 11-9 容错工程模式
- **11-9.1 超时/重试/退避** — 指数退避+抖动避免重试风暴。
- **11-9.2 熔断器** — circuit breaker 快速失败保护下游。
- **11-9.3 舱壁隔离** — bulkhead 限制故障爆炸半径。
- **11-9.4 幂等性与去重** — 幂等键使重试安全。
- **11-9.5 优雅降级** — 部分功能可用优于整体不可用（SRE 书 ch22 级联失败回指）。

### 11-10 读写路径与数据模型选型
- **11-10.1 访问模式驱动选型** — 读多/写多/查询形态决定存储选择（DDIA2 Data Models）。
- **11-10.2 关系 vs 文档 vs 图** — 数据模型与查询能力/连接代价匹配。
- **11-10.3 读写分离/物化视图** — 预计算换读放大（跨 L4-03、L6-04）。
- **11-10.4 CQRS 与事件溯源直觉** — 读写模型分离与以事件为真相来源。

---

## L5-12 可观测性 / SRE（性价比最高，一手最硬）

> 锚点：Google SRE 书 + SRE Workbook（强·一手，全文在线）· OpenTelemetry 规范（CNCF 已毕业 2026-05，一手）· W3C Trace Context（L1 Rec / L2 CR Draft，一手标准）· Prometheus/OpenMetrics（一手）。分账：①"三支柱"社区框架(⚠) vs OTel/W3C 规范(一手)；②SRE 文化(方法学) vs 支柱(技术)。实证：错误预算/燃烧率纯算术可硬实证。

### 12-1 SRE 原则与 DevOps 关系
- **12-1.1 SRE 定义** — 以软件工程手段解决运维问题（SRE 书 ch1）。
- **12-1.2 拥抱风险** — 可靠性并非越高越好，100% 是错误目标（ch3）。
- **12-1.3 SRE vs DevOps** — "class SRE implements DevOps"的关系（Workbook ch1）。
- **12-1.4 SRE 团队约定** — 运维负载上限（如 ≤50%）等工程化约束。

### 12-2 SLI / SLO / 错误预算 ★纯算术硬实证
- **12-2.1 SLI 定义** — 好指标属性（用户视角、可聚合、比例式）（SRE 书 ch4）。
- **12-2.2 SLO 目标设定** — 目标与窗口选择，避免过严/过松（Workbook ch2）。
- **12-2.3 错误预算 =(1−SLO)×窗口** — 预算=允许的不可靠量；Python 算术可硬实证。
- **12-2.4 SLA vs SLO** — 对外合约(含罚则) vs 对内目标的区分与后果。

### 12-3 基于 SLO 的告警（burn-rate）
- **12-3.1 传统阈值告警的问题** — 逐指标阈值导致噪声/漏报（SRE 书 ch10）。
- **12-3.2 燃烧率概念** — burn rate = 预算消耗速度；1×=恰好耗尽（Workbook ch5）；算术可实证。
- **12-3.3 多窗口多燃烧率告警** — 快慢窗口组合兼顾灵敏与稳健。
- **12-3.4 告警质量指标** — precision/recall/detection time/reset time 评估。

### 12-4 metrics 支柱与四黄金信号
- **12-4.1 时序数据模型与聚合** — 数值时序的采样/聚合/降采样（SRE 书 ch6）。
- **12-4.2 四黄金信号** — 延迟/流量/错误/饱和度。
- **12-4.3 Prometheus 拉模型/OpenMetrics** — 抓取模型、PromQL、暴露格式（一手）。
- **12-4.4 分位数/直方图与陷阱** — 平均值误导、分位数不可跨实例平均、直方图桶。

### 12-5 logs 支柱
- **12-5.1 结构化 vs 非结构化日志** — 键值结构化利于查询与聚合。
- **12-5.2 日志级别与采样/降级** — 高流量下的采样与优先级降级。
- **12-5.3 日志聚合管道** — 采集→传输→索引→查询链路。
- **12-5.4 OTel Logs 规范 vs "支柱"概念** — 规范(GA，一手) 与社区"三支柱"框架(⚠) 分账。

### 12-6 traces 支柱与分布式追踪
- **12-6.1 span 树与因果关系** — trace 由带父子的 span 组成（跨 L5-01 因果序）。
- **12-6.2 context 传播与 traceparent** — W3C Trace Context 头格式（L1 Rec；L2 CR Draft 加随机 trace-id 标志，向后兼容 v00）。
- **12-6.3 头采样 vs 尾采样** — 入口决定 vs 完成后按结果采样的取舍。
- **12-6.4 追踪的用途** — 延迟根因、依赖图、跨服务瓶颈定位（跨 L4-02 请求链）。

### 12-7 OpenTelemetry 统一遥测
- **12-7.1 OTel 架构** — API/SDK/Collector/OTLP 分层（opentelemetry.io/docs/specs）。
- **12-7.2 三信号统一与语义约定** — semantic conventions 统一属性命名。
- **12-7.3 信号成熟度硬标** — traces/metrics/logs GA；profiling **RC（Q1 2026）→目标 GA Q3 2026**（核实 2026-07-25，须实证基线可用性）。
- **12-7.4 instrumentation** — 自动（zero-code）vs 手动埋点。

### 12-8 消除 toil 与自动化
- **12-8.1 toil 定义与特征** — 手动/重复/可自动化/无长期价值/随规模线性增长（SRE 书 ch5）。
- **12-8.2 toil 量化与预算** — 度量 toil 占比并设上限。
- **12-8.3 自动化演进阶梯** — 从无自动化到自主系统的层级（ch7）。
- **12-8.4 工程 vs 运维时间平衡** — 用工程时间偿还 toil（Workbook ch6）。

### 12-9 事件响应与无指责事后复盘
- **12-9.1 on-call 与事件管理** — 事件指挥体系(ICS)、角色分工（SRE 书 ch14）。
- **12-9.2 无指责 postmortem 文化** — 对事不对人的复盘（ch15；Workbook ch10）。
- **12-9.3 从失败学习与行动项跟踪** — 复盘产出可执行改进并追踪闭环。
- **12-9.4 响应指标** — MTTD/MTTR 等衡量恢复效率。

### 12-10 发布工程与金丝雀发布
- **12-10.1 发布工程原则** — 自服务、快速、可复现、一致（SRE 书 ch8）。
- **12-10.2 金丝雀发布机制** — 小流量灰度+指标对比（Workbook ch16）。
- **12-10.3 错误预算驱动发布决策** — 预算耗尽则冻结发布。
- **12-10.4 渐进式交付与自动回滚** — 分阶段放量与异常自动回退（跨 L4-06 CD、L5-13 部署）。

### 12-11 过载处理与级联失败
- **12-11.1 限流 rate limiting** — 客户端/服务端配额保护容量（SRE 书 ch21）。
- **12-11.2 降载 load shedding** — 过载时优先丢弃低价值请求。
- **12-11.3 级联失败机理** — 重试放大/资源耗尽/雪崩（ch22）。
- **12-11.4 排队论直觉与容量余量** — 利用率逼近 1 时延迟爆炸，需留余量（Workbook ch11）。

---

## L5-13 容器与云原生（实证走 unshare；cgroup v1/v2 分账）

> 锚点：OCI Runtime Spec v1.3.0（2025-11-04）· OCI Image/Distribution Spec v1.1.0（2024-02-15）· K8s 官方文档 v1.36（当前稳定）· Linux man page namespaces(7)/cgroups(7)/unshare(1)/user_namespaces(7)（均强·一手）。实证：Docker daemon 不可达→走 `unshare`/`nsenter`/读 `/sys/fs/cgroup`；本机 cgroup **v1 hybrid**，讲 v2 须标"本机基线 v1、v2 为现代默认"。K8s 深度归 L6-05。

### 13-1 容器 vs VM 与隔离模型
- **13-1.1 hypervisor 整机虚拟化 vs 共享内核** — 独立内核/整机 vs 共享宿主内核的进程隔离（跨 L5-05）。
- **13-1.2 容器三要素** — 进程 + namespaces（视图）+ cgroups（资源）的组合。
- **13-1.3 "容器不是轻量 VM"辨析** — 无独立内核，隔离强度与攻击面差异。
- **13-1.4 隔离谱系与用例** — 进程/容器/微 VM/VM 的隔离-开销权衡。

### 13-2 Linux namespaces（视图隔离）★强实证 unshare
- **13-2.1 namespaces 总览** — pid/net/mnt/uts/ipc/user/cgroup/time 八种（namespaces(7)）。
- **13-2.2 PID/mount ns** — 进程视图与挂载视图隔离；`unshare --pid --mount --fork` 硬实证。
- **13-2.3 network ns** — 独立网络栈/接口；`unshare --net` 或 `ip netns` 实证。
- **13-2.4 user ns 与 rootless** — uid/gid 映射使非特权用户获容器内 root（user_namespaces(7)）；`unshare --user --map-root-user` 实证。
- **13-2.5 UTS/IPC/cgroup/time ns** — 主机名/域名、SysV IPC、cgroup 视图、时钟偏移隔离。

### 13-3 cgroups（资源限额，v1 vs v2）★读 /sys/fs/cgroup 实证
- **13-3.1 cgroup 概念** — 控制器/层级/任务模型（cgroups(7)）。
- **13-3.2 cpu 控制器** — 份额(shares/weight) vs 配额(quota/period) 限流；读 `/sys/fs/cgroup` 实证。
- **13-3.3 memory 控制器与 OOM** — 内存上限触发 OOM kill/回收。
- **13-3.4 pids/io 控制器** — 进程数上限与块设备 I/O 权重/上限。
- **13-3.5 v1 hybrid vs v2 统一层级** — 本机 v1 hybrid（多层级）vs v2 单一统一层级 + `cgroup.controllers`；须 delta 分账（本机基线 v1、v2 现代默认），`cat /proc/filesystems`+挂载点实证。

### 13-4 OCI 镜像格式与分层/union FS
- **13-4.1 镜像 manifest 与 config** — manifest 索引层与 config、config 记录运行参数（OCI Image Spec v1.1.0）。
- **13-4.2 层与内容寻址** — 每层为 tar，按 digest 内容寻址去重。
- **13-4.3 union FS/OverlayFS CoW** — 只读层叠加 + 可写层写时复制；本机可挂 overlayfs 观察（待核权限）。
- **13-4.4 不可变镜像与可复现构建** — 镜像不可变作可靠部署基座。

### 13-5 OCI 运行时与容器运行时链
- **13-5.1 filesystem bundle + config.json** — rootfs + 配置构成运行时 bundle（OCI Runtime Spec v1.3.0）。
- **13-5.2 容器生命周期状态机** — creating/created/running/stopped 状态转换。
- **13-5.3 高层 vs 低层运行时** — containerd/CRI-O（镜像/生命周期）→ runc/crun（OCI 运行时）分工。
- **13-5.4 runc 创建容器机制** — clone/unshare + setns + pivot_root + cgroup 装配的落地（回指 13-2/13-3）。

### 13-6 OCI 分发与镜像仓库
- **13-6.1 registry pull/push API** — blob 与 manifest 的分发协议（OCI Distribution Spec v1.1.0）。
- **13-6.2 内容寻址与去重** — 按 digest 共享层减少传输。
- **13-6.3 tag vs digest 引用** — 可变 tag vs 不可变 digest 的可重复性（跨 L4-02 HTTP）。
- **13-6.4 镜像签名与供应链** — cosign/attestation 等验真（⚠前沿，待核规范化程度）。

### 13-7 K8s 工作负载对象
- **13-7.1 Pod 最小调度单元** — 共享网络/存储的容器组（K8s docs Workloads v1.36）。
- **13-7.2 ReplicaSet/Deployment** — 副本管理与滚动更新/回滚。
- **13-7.3 StatefulSet/DaemonSet** — 有稳定标识的有状态 vs 每节点一实例。
- **13-7.4 Job/CronJob** — 一次性与定时批处理（深度归 L6-05）。

### 13-8 K8s 声明式模型与 reconcile 循环
- **13-8.1 声明式 API** — 声明期望态而非命令步骤（Cluster Architecture v1.36）。
- **13-8.2 控制器 reconcile 循环** — 持续把实际态收敛到期望态。
- **13-8.3 控制平面架构** — API server/etcd/scheduler/controller-manager 分工。
- **13-8.4 CRD/operator 扩展** — 自定义资源 + 控制器扩展声明式模型。

### 13-9 K8s 服务发现/负载均衡/网络
- **13-9.1 Service 与 ClusterIP** — 为动态 Pod 提供稳定虚拟 IP（Services & Networking v1.36）。
- **13-9.2 kube-proxy/EndpointSlice/DNS** — 端点跟踪与集群内服务发现。
- **13-9.3 Ingress 与外部入口** — L7 路由暴露服务到集群外（跨 L5-11 LB）。
- **13-9.4 网络模型与 CNI/NetworkPolicy** — 每 Pod 一 IP、扁平网络与策略隔离（跨 L4-02）。

### 13-10 K8s 调度/抢占/驱逐
- **13-10.1 调度器过滤+打分** — filter 可行节点、score 择优（Scheduling v1.36）。
- **13-10.2 亲和/反亲和/污点容忍** — 约束 Pod 放置的软硬规则。
- **13-10.3 requests/limits 与 QoS** — 资源声明决定 Guaranteed/Burstable/BestEffort。
- **13-10.4 抢占与驱逐** — 高优先级抢占、节点压力驱逐。

### 13-11 K8s 配置与存储
- **13-11.1 ConfigMap/Secret** — 配置与密钥同镜像解耦（Configuration & Storage v1.36）。
- **13-11.2 Volume 类型** — emptyDir/hostPath/投射卷等生命周期差异。
- **13-11.3 PV/PVC/StorageClass** — 供给-申领分离与动态供给。
- **13-11.4 CSI 存储接口** — 标准化第三方存储插件（跨 L4-06 配置管理）。

### 13-12 容器安全边界
- **13-12.1 共享内核攻击面** — 单一内核=容器逃逸风险面（跨 L5-04）。
- **13-12.2 能力/seccomp/LSM 收窄** — capabilities 削权、seccomp 过滤 syscall、AppArmor/SELinux。
- **13-12.3 rootless/user ns 强化** — 用 user ns 降低逃逸后权限。
- **13-12.4 gVisor/Kata 沙箱** — 用户态内核 vs 微 VM 强隔离（⚠前沿）。

---

## 统计

| 课程 | 大主题数 | 小主题数 |
|------|----------|----------|
| L4-06 软件工程（VC/测试/CI-CD） | 13 | 56 |
| L5-11 系统设计 | 10 | 41 |
| L5-12 可观测性/SRE | 11 | 44 |
| L5-13 容器与云原生 | 12 | 50 |
| **合计** | **46** | **191** |

> 每大主题小主题数落在 3–8 区间（本组实际 3–5）。承重分布：Git 机制（06-4/5/6）与 Linux ns/cgroup（13-2/3）为本组硬实证脊柱；SLO/错误预算/燃烧率（12-2/3）为纯算术实证。二手承重项已 ⚠：06-1.2/06-8/06-9/06-10/06-11（软工方法学）、11 门多组件回指、12-5.4"三支柱"框架、13-6.4/13-12.4 供应链与沙箱前沿。待核项：SHA-1→256 过渡(06-4.5)、OTel profiling GA 时效(12-7.3)、overlayfs 挂载权限(13-4.3)、镜像签名规范化(13-6.4)。
