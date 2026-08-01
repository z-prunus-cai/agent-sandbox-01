# L5-12·大主题7 OpenTelemetry 统一遥测

> 基线 Py3.11.15/np2.4.6/gcc13.3 @2026-07-25 ｜核实日期：2026-08-01 ｜先修：本课大主题4/5/6（metrics/logs/traces 三支柱）、L4-02 请求链 ｜一手锚点：OpenTelemetry 官方规范 opentelemetry.io/docs/specs（CNCF 已毕业 2026-05-21；traces/metrics/logs 规范级 GA/stable，profiling 公开 alpha→目标 GA Q3 2026）、W3C Trace Context ｜成熟度：⚙演进快·锚版本·随时变（核心三信号 GA，profiling 前沿；Collector 仍 0.x）

OpenTelemetry（简称 OTel）把前面几个大主题分别讲的 metrics、logs、traces 三类遥测数据，收拢到一套统一的规范、数据模型和线格式里。它既是一份写在 opentelemetry.io/docs/specs 下的开放规范，也是一组各语言的实现（API/SDK）、一个数据管道进程（Collector）和一套跨厂商的传输协议（OTLP）。它由 CNCF（云原生计算基金会）托管，并已在 2026-05-21 毕业（graduated），成为可观测性事实标准。本报告统一采用「规范语义 vs 具体实现行为」分账的写法：规范说某信号 stable，不等于每种语言的 SDK 都已 stable。

---

## 12-7.1 OTel 架构：API / SDK / Collector / OTLP 分层

### 12-7.1.1 OpenTelemetry 的定位：一套厂商中立的遥测规范与实现

OpenTelemetry 是一个厂商中立（vendor-neutral）的可观测性框架，目标是让应用只用一套 API 产生遥测数据，而把「数据发到哪个后端」这件事推迟到运行期配置，从而摆脱对某一家监控厂商 SDK 的绑定。它同时包含四样东西：规范文本（specification）、每种语言的 API 与 SDK 实现、一个独立的数据收集处理进程 Collector，以及一套统一的传输协议 OTLP。

OpenTelemetry 只负责「产生、加工、搬运」遥测数据，它本身不是存储或可视化后端。你仍然需要一个后端（如 Prometheus、Jaeger、Loki 或各家商业平台）来存储和查询数据。OTel 的价值在于把「埋点」和「后端选型」解耦——换后端时业务代码里的埋点不用改，只改导出目的地。它替代的是过去每家后端各自的专用 agent 和 SDK，而不是后端本身。

OTel 由多个此前独立的项目合并而来，其历史前身是 OpenTracing（追踪 API 标准）和 OpenCensus（Google 的指标+追踪库），两者于 2019 年合并为 OpenTelemetry。理解这段来历有助于明白为什么 OTel 从一开始就特别强调「API 与实现分离」和「跨厂商互操作」。

### 12-7.1.2 API 层：与实现解耦的埋点接口

API 层是应用代码直接调用的那组接口，用来创建 span、记录 metric、发出 log 记录。它的关键设计约束是「零依赖、可空实现」：如果程序里只引入了 API 而没有配置 SDK，那么所有 API 调用都会退化成什么都不做的无操作（no-op），既不报错也不产生开销。

这条约束是给库作者用的。一个第三方库（比如一个 HTTP 客户端库）可以放心地在自己代码里调用 OTel API 埋点，而不用担心强制用户承担一套完整遥测实现的依赖——用户不装 SDK 时，这些埋点就是静默的。这正是「API/实现分离」的实际意义：库依赖 API（轻、稳定），应用负责装配 SDK（重、可换）。

API 的稳定性承诺也比 SDK 更强。规范对已 stable 的信号的 API 提供长期支持（LTS）保证：一旦某个 API 标为 stable，就不会做破坏性变更，这样依赖它埋点的海量三方库才不会被上游 API 变动连累。

### 12-7.1.3 SDK 层：采样、批处理与导出的具体实现

SDK 是 API 的官方实现，负责把 API 调用产生的数据真正加工并送出去。它提供了一条可插拔的处理链，典型环节包括采样器（sampler，决定哪些 trace 被保留）、处理器（processor，通常做批量攒批 batching）、导出器（exporter，把数据按某种协议发到目的地），以及描述「谁产生了这些数据」的 Resource。

把这些放在 SDK 而非 API，是为了让「怎么处理数据」成为运行期配置而不是代码决策。同一份埋点代码，可以在开发环境全量采样、直接打到控制台导出器，在生产环境改成按比例采样、批量经 OTLP 发往 Collector——业务代码一行不改，只换 SDK 的装配。

一个重要的实现细节分账：规范把某信号标为 stable，指的是该信号的 API/SDK 规范定稿；但具体某语言的 SDK 实现进度可能落后。例如在核实日（2026-08-01）官方状态页显示，logs 信号在 C++/.NET/PHP 已 stable，而在 Go/Rust 仍是 beta，在 Python/JavaScript 等仍处于开发（development）阶段。用哪种语言、看它对应信号的实现档位，不能只看规范档位（来源 opentelemetry.io/status/，⚙演进快·锚版本·随时变·核实 2026-08-01）。

### 12-7.1.4 OTLP：统一的遥测传输协议

OTLP（OpenTelemetry Protocol）是 OTel 自带的线格式（wire format），用一套协议同时承载 traces、metrics、logs（profiles 的协议部分尚在开发中）。它定义了基于 Protocol Buffers 的编码，可以走两种传输：

OTLP/gRPC 与 OTLP/HTTP（HTTP 下 payload 可用 protobuf 二进制或 JSON 编码）

OTLP 的意义在于「一根管子传三种信号」。在 OTel 之前，指标走 Prometheus 抓取格式、追踪走 Jaeger/Zipkin 各自协议、日志走五花八门的管道；OTLP 把它们统一成同一套语义和编码，SDK、Collector、后端之间只要都讲 OTLP 就能互通。这也是「统一遥测」这个大主题名字的技术落点之一。

规范状态上，OTLP 对 traces、metrics、logs 三个信号均为 stable（核实 2026-08-01，见来源）。初学者常把 OTLP 和语义约定搞混：OTLP 管的是「数据怎么打包传输」（结构与编码），语义约定管的是「字段该叫什么名字、什么含义」（词汇表），两者正交，见 12-7.2。

### 12-7.1.5 Collector：接收-处理-导出的独立数据管道

Collector 是一个独立于应用的进程，充当遥测数据的中转与加工站。它的内部是一条可配置的管道（pipeline），由三类组件串成：

receivers（接收，支持 OTLP 及大量三方格式）→ processors（处理，如批处理、过滤、改属性、采样）→ exporters（导出，发往一个或多个后端）

有了 Collector，应用侧的 SDK 只需把数据用 OTLP 发给本地 Collector，剩下的重活（重试、批量、脱敏、路由到多后端、格式转换）都交给 Collector，业务进程更轻、后端配置也集中可控。它常见两种部署形态：agent 模式（与应用同机/同 Pod，就近收集）和 gateway 模式（集群级独立集群，统一出口）。

要点提醒（⚙演进快·锚版本·随时变·核实 2026-08-01）：Collector 整体仍处于 0.x 版本线，核实日的近期版本约为 v0.157.0（2026-07-22 前后），官方状态页把 Collector 标为 mixed（因为 core 与 contrib 里各组件成熟度参差）。也就是说，尽管核心三信号的 API/SDK/OTLP 已 GA，但「Collector 本体」尚未发布 1.0 稳定版——这是把 OTel 当「全部 GA」时最容易踩的时效坑，具体组件是否可靠要逐个看其自身 stability 标记。

#### 来源与时效
- 一手：OpenTelemetry 规范总览与状态 opentelemetry.io/docs/specs、opentelemetry.io/status/、opentelemetry.io/docs/specs/status/（核实 2026-08-01）——API/SDK/OTLP 的 stable 档位、Collector「mixed」、各语言实现档位差异均取自此。
- 一手：OpenTelemetry Collector 文档 opentelemetry.io/docs/collector/ 与 open-telemetry/opentelemetry-collector-releases（GitHub Releases，核实 v0.157.0/2026-07-22 前后）。
- 交叉核对（⚠二手，仅指路/补留白）：CNCF 项目页 cncf.io/projects/opentelemetry/、InfoQ「OpenTelemetry Graduates to CNCF's Highest Maturity Level」(2026-07)。
- ⚙演进快·锚版本·随时变：Collector 版本号与各语言 SDK 实现档位随发布快速变动，以上为 2026-08-01 快照。
- 分账：OTLP（传输/编码，stable）vs 语义约定（词汇表，另见 12-7.2）；规范档位 vs 具体语言实现档位，分开标。

---

## 12-7.2 三信号统一与语义约定

### 12-7.2.1 三信号（trace/metric/log）在同一模型下统一

OTel 把 traces、metrics、logs 建模为同一套数据体系下的三种「信号」（signal），共享同一份 Resource 描述、同一套属性模型（attributes）和同一条上下文传播机制，并用同一套 OTLP 传输。这就是「统一遥测」相对旧世界「三套各自为政的工具链」的核心改进。

在旧模式里，metrics、logs、traces 往往来自三套不同的 SDK、走三种协议、进三个后端，彼此对不上号——同一次请求出错，指标里看到错误率上升、日志里看到堆栈、追踪里看到慢 span，却很难自动把它们串成一条线。OTel 让三者共用标识与上下文，就为「跨信号关联」打下了基础。

需要与社区「三支柱（three pillars）」框架分账：所谓 metrics/logs/traces 三支柱是业界的一个概念性叙事（⚠社区框架，非规范），常被批评为「三个割裂的筒仓」。OTel 的官方立场恰恰是反对把三者当作割裂支柱，而是强调统一关联；报告里凡用到「三支柱」一词，应理解为通俗叙事而非 OTel 规范术语。

### 12-7.2.2 跨信号关联：trace_id / span_id 与 Context

三信号能真正串起来，靠的是把同一个执行上下文里的标识写进每条数据。追踪里的 trace_id 和 span_id 可以被附到同一请求产生的日志记录上，也可以作为示例（exemplar）挂到指标上，于是从一条慢指标能跳到具体 trace、再从 trace 跳到对应日志。

支撑这一切的是 Context（上下文）和上下文传播（context propagation）。Context 是一个随执行流传递的载体，跨线程、跨异步、跨进程携带当前的 trace_id/span_id 等信息；跨进程时通过注入/提取到传输载体（如 HTTP 头）完成传播，其中最主流的格式是 W3C Trace Context 标准的 traceparent 头（本课大主题6详述）。有了统一传播，一次请求经过多个服务时，各服务产生的三类信号都带着同一个 trace_id，天然可关联。

可以把 trace_id 想成一次请求的「订单号」，请求流经的每一站（每个服务、每条日志、每个指标样本）都盖上同一个订单号，事后就能按号把散落各处的记录归拢到一起。

### 12-7.2.3 Resource：描述「谁产生了这些数据」

Resource 是一组描述遥测来源实体的属性集合，比如服务名、服务版本、实例 id、部署环境、所在主机/容器/K8s Pod 等。同一个进程产生的所有信号共享同一个 Resource，于是三类数据都知道自己来自「哪个服务的哪个实例」。

Resource 让统一遥测在「来源维度」上也对齐：查询时可以按 service.name 把某服务的指标、日志、追踪一起筛出来。Resource 的属性名同样由语义约定规定（见下一节），例如服务名统一叫 service.name 而不是各团队各写各的 svc/app/service_name，这样跨团队、跨系统的数据才能用同一套维度聚合。

### 12-7.2.4 语义约定（semantic conventions）：统一属性命名的词汇表

语义约定是 OTel 规定的一份共享词汇表，约定了属性名、含义、取值、以及 span 名称/种类、指标的名称与单位等。它解决的问题是：光有统一的传输和数据结构还不够，如果 A 团队把 HTTP 状态码写成 status，B 团队写成 http_code，数据还是没法跨系统聚合分析。语义约定强制大家对同一概念用同一个键名。

例如 HTTP 相关属性统一命名为 http.request.method、http.response.status_code、url.path 等，而不是各自造词。语义约定独立于核心规范单独版本化，核实日（2026-08-01）的版本约为 semconv 1.43.0。要点是：属性名的稳定性由语义约定的版本与各领域的 stability 标记决定，与「信号本身 GA」是两回事。

时效与分账（⚙演进快·锚版本·随时变·核实 2026-08-01）：不同领域的语义约定成熟度不一。HTTP 领域的 span/metric 语义约定较早趋于稳定，但官方文档对 HTTP 语义约定整体仍标为 mixed，历史上还提供过迁移开关 OTEL_SEMCONV_STABILITY_OPT_IN 让旧埋点平滑切换到新版命名。较新的领域如 GenAI（面向 LLM/AI 应用的语义约定）在 2026 年随毕业前后受到重点推进，属活跃演进对象（⚠新领域·待核其稳定档位）。引用任何具体属性名时都应锚定 semconv 版本号。

#### 来源与时效
- 一手：OpenTelemetry 语义约定 opentelemetry.io/docs/specs/semconv/（核实 semconv 1.43.0 / 2026-08-01）、HTTP 语义约定 opentelemetry.io/docs/specs/semconv/http/。
- 一手：OTel 数据模型与 Context/传播规范 opentelemetry.io/docs/specs/otel/、opentelemetry.io/docs/concepts/signals/。
- 一手（跨主题）：W3C Trace Context w3.org/TR/trace-context（traceparent 头，详见本课大主题6）。
- 交叉核对（⚠二手，仅指路）：oneuptime.com「How to Apply HTTP Semantic Conventions」「Implement Semantic Conventions」(2026-01/02)。
- ⚙演进快·锚版本·随时变：semconv 版本号、HTTP「mixed」状态、GenAI 语义约定进度均随版本变动，以上为 2026-08-01 快照。
- 分账：OTel 统一模型（规范，一手）vs 社区「三支柱」框架（⚠概念叙事，非规范）；GenAI 语义约定档位待核。

---

## 12-7.3 信号成熟度硬标（⚙演进快·锚版本·随时变）

### 12-7.3.1 CNCF 毕业与「事实标准」定位

OpenTelemetry 于 2026-05-21 由 CNCF 宣布毕业（graduated），达到 CNCF 的最高成熟度等级，官方与媒体口径称其巩固了「可观测性事实标准」的地位。毕业是 CNCF 对项目治理成熟度、采用广度、可持续性的背书，不等于其中每个子组件都已技术定稿。

毕业（项目治理层面的成熟度）和各信号/组件的 stability（技术接口层面的稳定承诺）是两套不同的标尺。下面几项按「技术 stability」逐一硬标，避免把「已毕业」误读为「所有东西都 GA」。

### 12-7.3.2 traces：规范级 GA/stable

追踪信号是 OTel 最成熟的信号，其 API、SDK、OTLP 协议在规范层面均为 stable，且享受长期支持（LTS）——规范原文称追踪规范「已完全稳定并受长期支持覆盖」。

这是为什么追踪在各语言实现里也最齐整：核实日（2026-08-01）状态页显示 traces 在 C++/.NET/Go/Java/JavaScript/PHP/Python/Ruby/Swift 等主流语言均为 stable，仅个别语言（如 Rust）为 beta。选型时追踪是最可放心用于生产的信号。

### 12-7.3.3 metrics：规范级 GA，SDK 实现档位分账

指标信号的 API 与 OTLP 协议在规范层面为 stable，但规范把 metrics 的 SDK 整体标为 mixed，反映各语言 SDK 的成熟度参差。

metrics 概念上已经可以生产使用，但要具体确认你用的那种语言的 metrics SDK 档位。核实日状态页显示 metrics 在多数主流语言为 stable，而在 Rust 为 beta，在 Erlang/Elixir、Kotlin、Ruby、Swift 等仍处开发（development）。规范 GA ≠ 你的语言 SDK 已 GA，这是本节反复强调的分账。

### 12-7.3.4 logs：规范级 GA，多语言实现仍在追赶

日志信号的 Bridge API、SDK 与 OTLP 协议在规范层面为 stable。OTel 的日志设计特点是「桥接（bridge）」——不另造一套面向用户的日志门面，而是提供 Bridge API 让现有日志库（如 Python logging、Log4j 等）把日志接入 OTel 管道，从而与 trace/metric 统一。

时效分账（⚙演进快·核实 2026-08-01）：logs 虽在规范层 stable，但语言实现明显落后于 traces。状态页显示 logs 在 C++/.NET/PHP 为 stable，在 Go/Rust 为 beta，而在 JavaScript、Python、Kotlin、Ruby、Swift、Erlang/Elixir 等仍为开发（development）。因此把 OTel logs 用于生产前，务必核对目标语言的实现档位。

### 12-7.3.5 profiles：公开 alpha（前沿），目标 GA Q3 2026——含来源冲突记录

性能剖析（profiling/profiles）是 OTel 的第四个信号，用于持续采集 CPU/内存等资源消耗的调用栈剖面（基于 Linux perf 格式与 eBPF 采集），补上前三信号回答不了的「资源到底花在哪段代码」。它是本主题最前沿、最不稳定的部分，必须硬标状态、不可作已 GA 结论。

核实日（2026-08-01）一手与近期一手博客口径：profiles 信号于 2026 年 3 月进入公开 alpha（public alpha），OTel 官方规范状态页把 profiles 的协议部分标为 development，状态页显示其仅在 Java 有 development 级实现、其他语言尚不可用；Profiling SIG 明确警告「不应用于关键生产负载」。目标是在 2026 年内经评估走向 beta 并冲刺 GA（多方口径指向 Q3 2026 前后为 GA 目标窗口，属目标非既成）。

来源冲突（两边都记，不和稀泥）：本库编排清单在 2026-07-25 记录 profiles 为「RC（Q1 2026）→目标 GA Q3 2026」；而本次核实（2026-08-01）的一手/近一手来源一致显示其为「公开 alpha（2026-03）」、规范档位 development，尚未达到 RC。两者对「当前档位」的判断不一致：以官方状态页与官方博客为准，profiles 在核实日仍属 alpha/development 前沿状态，GA Q3 2026 为目标而非已达成。GA 时间点标「待核·随时变」，不作已 GA 结论。

一句话小结（硬标）:

traces/metrics/logs = 规范级 GA（但按语言实现档位分账）；profiles = 公开 alpha / development（目标 GA Q3 2026，待核）；Collector 本体仍 0.x（mixed）

#### 来源与时效
- 一手：OpenTelemetry 规范状态 opentelemetry.io/docs/specs/status/、组件状态 opentelemetry.io/status/、版本与稳定性 opentelemetry.io/docs/specs/otel/versioning-and-stability/（核实 2026-08-01）。
- 一手：OpenTelemetry 博客「Profiles Enters Public Alpha」opentelemetry.io/blog/2026/profiles-alpha/、「OpenTelemetry Has Graduated… Now what?」opentelemetry.io/blog/2026/otel-grad-now-what/。
- 一手：CNCF 毕业公告 cncf.io/announcements/2026/05/21/...（2026-05-21）。
- 交叉核对（⚠二手，仅指路/补留白）：InfoQ (2026-07)、DevOps.com、ClickHouse「OTel profiles signal enters public alpha」、Horovits/Medium (2026-05)。
- ⚙演进快·锚版本·随时变：所有 stability 档位、各语言实现进度、profiles 的 GA 时间点均快速变动，以上为 2026-08-01 快照；GA Q3 2026 为目标，标「待核」。
- 冲突记录：本库编排清单（2026-07-25）标 profiles 为「RC」，与核实日官方来源「public alpha / development」不一致——以官方状态页/官方博客为准，判为 alpha 前沿状态。
- 分账：CNCF 毕业（项目治理成熟度）vs 各信号/组件 stability（技术接口稳定度），分开标；规范档位 vs 语言实现档位，分开标。

---

## 12-7.4 instrumentation：自动（zero-code）vs 手动埋点

### 12-7.4.1 instrumentation 是什么

instrumentation（插桩/埋点）指让代码产生遥测数据的过程——在合适的位置创建 span、记录 metric、发出结构化日志。OTel 把埋点大致分为两条路径：手动埋点（在自己代码里显式调用 API）和自动/零代码埋点（不改业务代码，由外部机制注入）。两者常配合使用。

埋点就是给程序装「传感器」。手动埋点是自己动手在关心的地方装传感器，能精准贴合业务；自动埋点是买一套通用传感器一键铺满常见位置（HTTP、RPC、DB 调用等），省事但粒度较粗。

### 12-7.4.2 手动埋点（manual instrumentation）

手动埋点是在应用代码里直接引入 OTel API/SDK，显式地创建 span、设置属性、记录指标。它的优势是精准——可以针对具体业务逻辑（比如「计算推荐结果」这段）打点、加业务维度属性，这是自动埋点看不见的内部语义。

代价是要改代码、要维护。最常见的易错点是 span 生命周期管理：忘记结束（end）span 会导致 span 丢失或时长错误，以及要正确地把当前 span 放进 Context 并随执行流传递，否则父子关系断裂、trace 变成一堆孤立 span。手动埋点通常用于补齐自动埋点覆盖不到的关键业务路径。

### 12-7.4.3 库原生埋点与 instrumentation 库

介于全手动和全自动之间，还有两种常见来源。一是库原生埋点（native instrumentation）：一些库/框架自己内置了 OTel API 调用，用户装了 SDK 就能直接产出数据。二是 instrumentation 库（instrumentation libraries）：为某个流行库单独提供的埋点适配包，用户引入后即可自动为该库（如某 HTTP 框架、某数据库驱动）产出标准化遥测。

这体现了前面 12-7.1.2 说的 API/实现分离的价值：库作者只依赖轻量的 OTel API 就能内置埋点，用户不装 SDK 时这些调用是 no-op，装了才生效。对初学者而言，实际项目里的遥测数据往往是「库原生/instrumentation 库自动产出 + 自己手动补关键业务点」的混合体。

### 12-7.4.4 自动 / 零代码埋点（zero-code instrumentation）

零代码埋点指不修改应用源码就注入遥测能力，靠语言运行时的机制在加载期/运行期动态织入。典型手段随语言而异：Java 用 java agent（JVM 字节码增强），Python/Node.js 用启动包装器或 monkey-patch 常见库，.NET 用 profiler API 等。OTel 常把这些能力打包成一个「distro（发行版）」或 agent，一条命令或一个环境变量即可开启。

它的最大价值是「上手快、覆盖广」：无需改代码，就能自动为常见的 HTTP 服务端/客户端、RPC、数据库、消息队列等产生符合语义约定的 trace 和 metric，非常适合快速为存量系统接入可观测性。

自动埋点只覆盖它「认识」的库和框架，看不见你的私有业务逻辑，粒度也较粗；不同语言的支持范围和成熟度差异较大（Java 生态通常最完善）。所以业界常见做法是「自动埋点铺底 + 手动埋点补关键业务路径」，两者互补而非二选一。相关自动埋点组件与 distro 也在快速演进（⚙演进快·锚版本·随时变·核实 2026-08-01），引用具体 agent/distro 能力时应锚定其版本。

#### 来源与时效
- 一手：OpenTelemetry 文档 instrumentation 概念 opentelemetry.io/docs/concepts/instrumentation/（含 zero-code 与 code-based 两类）、各语言 instrumentation 指南 opentelemetry.io/docs/languages/（核实 2026-08-01）。
- 一手：OTel API/SDK 分离与 no-op 语义 opentelemetry.io/docs/specs/otel/（library guidelines）。
- 交叉核对（⚠二手，仅指路/补留白）：oneuptime.com OTel 系列 (2026-01/02)、techbytes/CORE 等 2026 综述（仅补背景，不承重）。
- ⚙演进快·锚版本·随时变：各语言 zero-code agent/distro 的覆盖范围与成熟度随发布快速变动，以上为 2026-08-01 快照。
