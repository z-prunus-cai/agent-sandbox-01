# L5-12·大主题4 metrics 支柱与监控分布式系统

> 基线 Py3.11.15/np2.4.6/gcc13.3 @2026-07-25 ｜核实日期：2026-08-01 ｜先修：本课大主题2（SLI/SLO/错误预算）、L2-03 概率（分位数/统计有效性回指）｜一手锚点：Google《Site Reliability Engineering》ch6「Monitoring Distributed Systems」（sre.google/sre-book，O'Reilly 2016）＋ Prometheus 官方文档 prometheus.io/docs（锚 Prometheus 3.x）＋ OpenMetrics 1.0 规范（prometheus/OpenMetrics）｜成熟度：核心概念 GA；Prometheus/OpenMetrics 工具链 ⚙演进快·锚版本·随时变

监控分布式系统的核心手段之一是 metrics（度量）：把系统运行状态压缩成一串随时间变化的数值，便于长期存储、聚合与告警。业界常把可观测性拆成 metrics / logs / traces「三支柱」，这是社区流传的框架而非任何官方规范（⚠ 社区框架，与下文 Prometheus/OpenMetrics 的规范文本分账）。本报告聚焦 metrics 这一支柱：先讲时序数据本身怎么建模与聚合，再讲选什么指标（四黄金信号），接着讲事实标准 Prometheus/OpenMetrics 的采集与暴露方式，最后讲分位数与直方图这些最容易用错的统计陷阱。

## 4.1 时序数据模型与聚合

### 4.1.1 时间序列是什么

时间序列（time series）是「同一个被观测量随时间产生的一串带时间戳的数值」。最朴素的形式就是一串二元组：每个点有一个时间戳和一个浮点值，例如每 15 秒记录一次某台机器的 CPU 使用率。metrics 系统存的就是海量这样的序列。

对初学者，关键是把时间序列和「日志」区分开。日志是一条条离散的事件文本，信息量大但难以直接做数学聚合；时间序列是规整的数值流，天生适合求和、求平均、算增长率、画趋势图。正因为它是数值且规整，才能低成本地长期保留和快速查询——这也是 metrics 作为「支柱」的价值所在：它便宜、可聚合、适合告警，代价是丢掉了单次事件的细节（细节要靠 logs/traces 补）。

一个常见误解是把时间序列当成「一张大表」。实际实现里，每条序列是独立存储和寻址的对象，写入是「不断往序列尾部追加点」，这决定了它的读写特性和后面要讲的聚合方式。

### 4.1.2 采样、标签维度与基数

真实系统不会连续记录，而是按固定间隔「采样」——每隔一段时间（抓取间隔，如 15s）取一个当前值。采样间隔决定了时间分辨率：间隔越短越能看清瞬时抖动，但存储和抓取开销也越大。

现代 metrics 模型的关键是给每条序列打上「标签」（label，也叫维度/tag）来区分。同一个指标名配上不同的标签组合，就是不同的独立序列。例如请求计数这个指标，按方法、路径、状态码打标签后：

http_requests_total{method="GET", path="/api", status="200"}

和

http_requests_total{method="POST", path="/api", status="500"}

是两条完全独立的序列。这让你能事后按任意维度切片聚合（「只看 500 的」「只看 GET 的」），非常灵活。

初学者最需要记住的陷阱是「基数爆炸」（cardinality explosion）：序列总数约等于各标签取值数量的乘积。把高基数的东西（如用户 ID、完整 URL、请求 ID）放进标签，会让序列数以乘积级膨胀，直接拖垮存储和查询。经验法则是标签只放取值有限、可枚举的维度；高基数信息该进日志或追踪，不进 metrics 标签。

### 4.1.3 跨时间与跨序列的聚合

聚合有两个正交的方向，初学者常混为一谈。

一是「跨时间」聚合：在单条序列上，把一个时间窗口内的多个采样点压成一个数。典型如对计数器求某段时间的增长速率：

rate = (窗口末值 − 窗口初值) / 窗口时长

二是「跨序列」聚合：在同一时刻，把多条序列（不同标签）合并成一条。典型如把所有实例的请求速率相加得到集群总速率，或按某个标签分组求和。

这两个方向可以叠加使用：先对每条实例序列算速率（跨时间），再把各实例的速率相加（跨序列），得到「集群每秒总请求数」。区分清楚顺序很重要——尤其对分位数这类不可交换的量，先聚合再算和先算再聚合结果完全不同（详见 4.4.4）。

### 4.1.4 降采样与数据保留

原始高分辨率数据（如 15s 一个点）存久了会非常占空间，但看几个月前的趋势时并不需要秒级精度。降采样（downsampling）就是把老数据按更粗的间隔重新聚合（如把 15s 点合并成 5min 点），用分辨率换存储。

配套的是保留策略（retention）：高分辨率数据只保留较短时间，降采样后的低分辨率数据保留更久。这是所有时序数据库的通用做法。

初学者要注意，降采样会改变可回答的问题：一旦把点合并成 5min 的平均/最大，就再也无法还原那 5 分钟内的秒级尖刺。所以降采样规则本身是个权衡，通常对不同聚合口径（avg/max/min）分别保存，避免只留平均而丢掉峰值信息。Prometheus 本体历史上不做自动降采样（靠 Thanos/Cortex/Mimir 等生态组件或 recording rules 实现），这点属实现差异，需按所用系统核对（⚠ 具体默认值以所用系统文档为准）。

#### 来源与时效

- 一手：Google《Site Reliability Engineering》ch6「Monitoring Distributed Systems」，sre.google/sre-book/monitoring-distributed-systems/（O'Reilly 2016，全文在线）——时间序列、标签维度、聚合的监控视角。
- 一手：Prometheus 官方文档「Data model」「Metric types」，prometheus.io/docs/concepts/data_model/（锚 Prometheus 3.x，核实 2026-08-01）——标签模型、序列即「指标名+标签集」的定义、基数概念。
- 交叉核对：SRE 书给「为什么这样监控」的方法学，Prometheus 文档给「数据模型如何落地」，两者对「序列=指标名+标签、聚合按标签切片」一致。
- 待核/实现分账：降采样与保留的默认间隔、是否内置，因系统而异（Prometheus 本体 vs Thanos/Mimir），以所用系统文档为准，勿凭记忆填具体天数。

## 4.2 四黄金信号

### 4.2.1 四黄金信号总览

「四黄金信号」（Four Golden Signals）是 Google SRE 书 ch6 提出的一组最基本的面向用户的监控指标：延迟（latency）、流量（traffic）、错误（errors）、饱和度（saturation）。原文的说法是：如果一个面向用户的系统你只能监控四个指标，就监控这四个。

它的价值在于给「该测什么」一个可复用的起点。初学者面对一个新服务常不知从何测起，四黄金信号提供了一张覆盖「用户体验（延迟、错误）+ 负载（流量）+ 系统余量（饱和度）」的最小检查表。它是方法学而非规范，具体每个信号用什么指标实现，要结合系统类型选取。

### 4.2.2 延迟 latency

延迟是「处理一个请求所花的时间」。它直接对应用户感受到的快慢，是最重要的用户侧信号之一。

SRE 书特别强调一个易错点：必须把成功请求的延迟和失败请求的延迟分开测。失败请求往往会很快返回错误（如立即 5xx），如果混在一起统计，一堆快速失败反而会把整体延迟拉低，制造「系统很快」的假象，掩盖了错误。所以正确做法是分别观察成功与失败两条延迟曲线。此外延迟绝不能只看平均值，要看分布/分位数（原因见 4.4）。

### 4.2.3 流量 traffic

流量衡量「系统正在承受多少需求」，是一种对负载的度量。对 Web 服务通常是每秒请求数（QPS/RPS），对存储系统可能是每秒事务数或每秒读写字节数，对流媒体可能是并发会话数。

对初学者，流量的作用是提供「基线与语境」：延迟升高、错误增多时，先看流量——是因为负载激增导致的，还是负载没变系统自身出了问题？两种情况的处置完全不同。流量也是容量规划和自动扩缩的输入。

### 4.2.4 错误 errors

错误是「请求失败的比率」。失败有三种形态：显式失败（如 HTTP 500）、隐式失败（返回了 200 但内容其实是错的）、以及基于策略的失败（如响应时间超过约定阈值就算失败）。

隐式失败是初学者最容易漏掉的：只统计 5xx 会漏掉「返回 200 却给了错误数据」的情况。SRE 书指出这类错误往往需要在应用内部埋点或用端到端探测才能捕捉。错误率通常和 SLI/SLO（本课大主题2）直接挂钩——错误率的补数就是可用性类 SLI。

### 4.2.5 饱和度 saturation

饱和度衡量「系统离资源用尽还有多远」，即最受限资源（CPU、内存、磁盘 I/O、网络带宽、连接数等）的占用程度。它是四个信号里最偏预测性的——用来提前发现「快撑不住了」。

关键直觉是许多系统在资源接近满载之前，性能就已经开始劣化（延迟随利用率逼近上限而急剧上升，排队论直觉见本课大主题11）。所以饱和度的告警阈值通常设在远低于 100% 的地方（如 80%），留出反应余量。饱和度常需要结合对系统瓶颈的理解来选测哪个资源，比纯粹的「CPU 使用率」更需要判断。

### 4.2.6 RED 方法与 USE 方法（社区框架，⚠）

除四黄金信号外，业界还有两个流传很广的简化框架，初学者常会混淆，这里辨析（⚠ 二者均为社区/个人提出的方法学，非官方规范，仅指路）。

RED 方法（Tom Wilkie 提出）针对「请求驱动的服务」，测三样：Rate（每秒请求数）、Errors（每秒失败数或失败率）、Duration（请求耗时分布）。可以看成四黄金信号去掉饱和度、聚焦服务外部表现的版本。

USE 方法（Brendan Gregg 提出）针对「资源」，对每类资源测：Utilization（利用率）、Saturation（饱和/排队程度）、Errors（错误数）。它是从基础设施/资源视角出发，和 RED 的服务视角互补。

三者关系可以这样记：四黄金信号 = 延迟+流量+错误+饱和度（最全，面向服务且含资源余量）；RED ≈ 服务外部视角（延迟/流量/错误）；USE ≈ 资源内部视角（利用率/饱和度/错误）。实践中常 RED 测服务、USE 测底层资源，两者配合。

#### 来源与时效

- 一手：Google《SRE》ch6「Monitoring Distributed Systems」§「The Four Golden Signals」，sre.google/sre-book/monitoring-distributed-systems/——四黄金信号定义、成功/失败延迟须分开、错误的三种形态、饱和度需留余量，均直接来自原文。
- 交叉核对（⚠ 二手/个人方法学，不承重，仅指路）：RED 方法（Tom Wilkie，Weaveworks/Grafana 语境的公开演讲与博客）；USE 方法（Brendan Gregg 个人站 brendangregg.com/usemethod.html）。二者用于与四黄金信号对照，概念一致处相互印证、差异处已点明。
- 分歧点：三套框架对「是否包含饱和度/资源视角」取舍不同（黄金信号含饱和度，RED 不含，USE 专注资源），非矛盾而是适用对象不同——两边都记。

## 4.3 Prometheus 拉模型 / OpenMetrics

### 4.3.1 拉（pull）抓取模型

Prometheus 采用「拉」（pull）模型：由 Prometheus 服务器主动、周期性地去各个目标（target）的 HTTP 端点（约定路径常为 /metrics）抓取当前指标快照，而不是由被监控方主动「推」数据过来。

Prometheus 掌握抓取节奏（便于统一采样间隔），能天然知道某个目标「抓不到了」（即实例可能宕机，这本身就是一个信号——内置 up 指标），且被监控方只需暴露一个只读端点、无需知道监控系统地址，利于横向扩展与服务发现。

代价与边界：拉模型不适合生命周期极短、Prometheus 来不及抓的批处理任务——这类用 Pushgateway 中转（把它当作「代拉」的缓冲，而非改成推模型）。是否用拉是架构选择，业界也有推模型系统（如部分 OTLP/StatsD 场景），并非唯一正确，需与场景匹配（⚠ 推/拉之争属工程取舍，两派都有理）。

### 4.3.2 数据模型：指标名 + 标签

Prometheus 里每条时间序列由「指标名 + 一组标签键值对」唯一标识，这正是 4.1.2 讲的模型的具体规范化。形式上：

<metric_name>{<label_name>=<label_value>, ...}

指标名说明「测的是什么」（如 http_requests_total），标签给出维度切分。历史上指标名/标签名受 [a-zA-Z_][a-zA-Z0-9_]* 之类正则约束；自 Prometheus 3.0（2024-11）起默认允许任意 UTF-8 字符作指标名与标签名（⚙演进快，见 4.3.6），这让 OpenTelemetry 那种带点号的命名不必再改写。核实所用版本行为为准。

### 4.3.3 四种指标类型

Prometheus/OpenMetrics 定义了几种指标类型，客户端库据此约束用法。初学者必须分清前两种最基础的：

counter（计数器）：只增不减的累计值（如请求总数、错误总数）。它的绝对值本身意义不大，有意义的是它的增长速率——因此几乎总是配合 rate()/increase() 使用。进程重启会归零，rate() 能自动处理这种重置。

gauge（仪表盘）：可增可减的瞬时值（如当前温度、当前内存占用、队列长度）。直接取值就有意义，可求 avg/max/min。

histogram（直方图）：把观测值（如请求延迟）分到若干预设桶里累计计数，用于事后估算分位数（详见 4.4.3）。

summary（摘要）：在客户端侧直接计算并暴露分位数（详见 4.4.5）。

把「本该只增的量」用成 gauge，或把「速率」直接对 counter 取值而不用 rate()。命名约定上 counter 通常以 _total 结尾。

### 4.3.4 文本暴露格式与 OpenMetrics

目标把指标以简单的基于行的文本格式暴露在 /metrics 上，每行大致是「指标名{标签} 数值」，配 # HELP（说明）和 # TYPE（类型）注释行。这就是 Prometheus 文本暴露格式。

OpenMetrics 是在此基础上标准化出来的开放规范（曾试图独立成 IETF 标准未果）。重要时效：OpenMetrics 项目已于 2024 年（CNCF TOC 通过归档，2024-09 公布）归档并合并回 Prometheus 项目维护；OpenMetrics 1.0 是稳定规范，社区正在 Prometheus 下推进 OpenMetrics 2.0（截至核实日期仍属实验/进行中）（⚙演进快·锚 2026-08-01·随时变，未定稿细节勿引为定论）。对初学者，实践含义是：把 OpenMetrics 理解为「Prometheus 暴露格式的标准化版本」即可，二者高度兼容。

直方图在文本格式里的落地形态（承接 4.4.3）：一个名为 X 的 histogram 会暴露成一族序列——若干 X_bucket{le="<上界>"}（累计到该上界的计数）、一个 X_sum（观测值之和）、一个 X_count（观测总数）。这个 le（less-or-equal）桶结构是后面算分位数的原料。

### 4.3.5 PromQL 查询

PromQL 是 Prometheus 的查询语言，用来对时间序列做筛选、聚合与计算。初学者掌握几个高频构件即可读懂大部分查询。

选择：写指标名加标签匹配（含正则 =~）筛出一组序列；[5m] 这种「区间向量」选出每条序列过去一段时间的一串点。

对 counter 求速率：

rate(http_requests_total[5m])

它给出过去 5 分钟每条序列的平均每秒增长率，且能正确跨越计数器重置。

跨序列聚合：用 sum/avg/max by(label) 按标签分组合并，例如按状态码分组求总速率：

sum by (status) (rate(http_requests_total[5m]))

算直方图分位数（承 4.4）：

histogram_quantile(0.99, sum by (le) (rate(http_request_duration_seconds_bucket[5m])))

注意这里先对 _bucket 序列按 le 聚合、再算分位数的顺序——这是 4.4.4 那条「分位数不能跨实例直接平均」的正确解法：应聚合桶计数再求分位，而不是先各自求分位再平均。具体函数语义以所用版本文档为准（⚠ 语法细节勿凭记忆，须比对官方文档）。

### 4.3.6 生态版本状态（⚙演进快·锚版本·随时变）

metrics 工具链演进较快，几个需硬标时效的锚点（核实 2026-08-01）：

Prometheus 3.0 于 2024-11-14 发布，是自 2.0（2017）以来首个大版本。主要变化含：默认支持 UTF-8 指标/标签名；对 OpenTelemetry 的原生 OTLP 摄取支持增强；Remote-Write 2.0（新增 metadata、exemplars、created timestamp、native histograms 传输）。截至核实日期处于 3.x 系列，确切最新补丁版本「待核」，勿凭记忆填。

native histograms（原生直方图）作为经典直方图的高效替代仍为实验特性、默认未开启，需 --enable-feature=native-histograms 打开（详见 4.4.6）。

OpenMetrics：1.0 稳定并已归档合并入 Prometheus（2024-09 公布）；2.0 在 Prometheus 社区工作组推进中，属实验/未定稿。

以上均为快速演进对象，引用前请核对官方发布说明与所用版本，不确定处标「待核」。

#### 来源与时效

- 一手：Prometheus 官方文档——「Data model」prometheus.io/docs/concepts/data_model/、「Metric types」prometheus.io/docs/concepts/metric_types/、「Querying/PromQL」prometheus.io/docs/prometheus/latest/querying/basics/、「Exposition formats」（锚 Prometheus 3.x，核实 2026-08-01）。
- 一手：OpenMetrics 1.0 规范，github.com/prometheus/OpenMetrics（specification/OpenMetrics.md @ v1.0.0）；OpenMetrics 2.0 [EXPERIMENTAL] prometheus.io/docs/specs/om/open_metrics_spec_2_0/（未定稿）。
- 一手/公告：Prometheus 官方博客「Announcing Prometheus 3.0」prometheus.io/blog/2024/11/14/prometheus-3-0/（3.0 发布、UTF-8、native histograms 实验状态、Remote-Write 2.0）；「UTF-8 in Prometheus」prometheus.io/docs/guides/utf8/。
- 交叉核对（⚠ 二手，仅佐证时效不承重）：CNCF 博客「OpenMetrics is archived, merged into Prometheus」cncf.io/blog/2024/09/18/…（OpenMetrics 归档并入 Prometheus、OpenMetrics 2.0 工作组）。与 prometheus/OpenMetrics 仓库状态一致。
- 分账：「三支柱」为社区框架（⚠）；Prometheus/OpenMetrics 为规范/事实标准（一手），二者已在正文分开标注。
- 待核：Prometheus 3.x 当前最新补丁版本号；OpenMetrics 2.0 定稿内容——均勿凭记忆填。

## 4.4 分位数 / 直方图与陷阱

### 4.4.1 平均值为何误导

延迟这类指标绝不能只看平均值。平均值会被大量快速请求「稀释」，把少数很慢的请求藏起来——而恰恰是那些慢请求最伤用户体验。

100 个请求里 99 个耗时 10ms、1 个耗时 5000ms，平均约 60ms，看着还行；但那 1 个 5 秒的请求对应的是一个被卡住的真实用户。SRE 书明确主张关注延迟的分布而非均值。平均值还对长尾极端值敏感却又表达不出「有多少比例的用户受影响」，所以要改用分位数。

### 4.4.2 分位数 percentile

分位数回答「有百分之多少的观测值不超过某个数」。第 p 百分位（记作 pXX）指：把所有观测从小到大排，处在 p% 位置的那个值。例如 p99 延迟 = 200ms，意思是 99% 的请求快于等于 200ms，只有最慢的 1% 更慢。

常用的是 p50（中位数，典型体验）、p90/p95/p99/p99.9（尾延迟，衡量最差的一小撮用户）。越靠近尾部的分位数越能暴露少数用户的糟糕体验，也越受个别极端值影响、越难稳定测准。分位数的好处是自带「多少比例受影响」的语义，正好补上平均值的短板，也直接对应 SLO 常用的「p99 < X ms」这类目标（本课大主题2）。

### 4.4.3 直方图与桶

要在分布式系统里高效算分位数，常用直方图（histogram）：预先划定若干值域「桶」（bucket），观测发生时只把对应桶的计数加一，从而用一组计数器近似表示整个分布，事后再从桶计数估算分位数。

Prometheus 经典直方图的桶是「累计」的：每个桶 X_bucket{le="b"} 记录「观测值 ≤ b 的总个数」，并总有一个 le="+Inf" 的桶等于总数 X_count（承 4.3.4）。算分位数时用 histogram_quantile()，它在目标分位数落入的那个桶内做线性插值来估值。

两个关键易错点。其一，估算精度取决于桶边界选得好不好：若目标分位数落在一个很宽的桶里，插值误差就大，所以桶要围绕关心的延迟区间（如 SLO 阈值附近）加密——桶选错会让 p99 估计严重失真。其二，落在 +Inf 桶（超过最大有限桶上界）的观测无法被插值定位，分位数只能给出下界。

### 4.4.4 分位数不可跨实例/跨序列平均

这是分布式监控里最经典、最反直觉的陷阱：分位数不是可加/可平均的量。把多台实例各自的 p99 再取平均（或求和），得到的数在数学上没有意义，通常也不等于整体的 p99。

直觉上，平均是线性运算，而「排序取某个位置」不是线性的——A 机的 p99 和 B 机的 p99 平均，压根不对应「把 A、B 请求合起来的 p99」。

把各实例同一 le 的桶计数先相加（跨序列聚合的是「计数」，计数可加），再对合并后的直方图算一次分位数。对应 4.3.5 的 PromQL 就是先 sum by (le) 再 histogram_quantile。同理，跨时间也不能把每分钟的 p99 再平均——直方图之所以设计成暴露可加的桶计数，正是为了让跨实例、跨时间的正确聚合成为可能。这条也解释了 summary 类型的一大局限（见 4.4.5）。

### 4.4.5 summary vs histogram 的取舍

Prometheus 的 summary 类型在客户端本地直接计算分位数并暴露成 X{quantile="0.99"} 这样的序列，同时给 X_sum 与 X_count。它的好处是分位数在客户端算好、精度不受桶边界限制、查询端零计算。

但它有一个由 4.4.4 直接推出的致命局限：summary 暴露的分位数不能在服务端跨实例聚合。你拿到的是每个实例各自的 p99，无法据此得到集群整体 p99。此外分位数要预先在客户端指定，事后想换个分位数（如临时想看 p95）就没有数据。

histogram 反之：客户端只累加桶计数（便宜、可聚合），分位数在查询时算，可跨实例聚合、可事后任选分位数，代价是精度受桶划分限制、且要多存若干桶序列。经验取舍是——需要跨实例聚合或事后灵活选分位数就用 histogram（分布式场景的默认选择）；只在单实例看、且要求分位数精确时才考虑 summary。

### 4.4.6 native histograms（⚙演进快·锚版本·随时变）

经典直方图的痛点是桶边界要人工预设、选不好就不准，且桶多了序列数膨胀。native histograms（原生直方图）是 Prometheus 引入的新方案：桶边界按指数增长自动、预设，无需人工挑选，能以更低成本覆盖很宽的值域并保持较好精度。

时效硬标（核实 2026-08-01）：native histograms 随 Prometheus 3.0（2024-11）演进，但截至核实日期仍为实验特性、默认未启用，需以特性开关开启（如 --enable-feature=native-histograms），其存储/查询/远程写细节仍在演进（Remote-Write 2.0 已含其传输）。属⚙演进快·锚版本·随时变对象，引用前务必核对所用 Prometheus 版本的发布说明，未稳定细节勿作定论、不确定处标「待核」。

#### 来源与时效

- 一手：Google《SRE》ch6「Monitoring Distributed Systems」——关注延迟分布而非平均值、尾延迟的重要性；sre.google/sre-book/monitoring-distributed-systems/。
- 一手：Prometheus 官方文档「Metric types」（histogram/summary 定义、_bucket/_sum/_count、summary 的分位数不可聚合、histogram 可查询端算分位）prometheus.io/docs/concepts/metric_types/；「Querying functions」histogram_quantile 语义与线性插值 prometheus.io/docs/prometheus/latest/querying/functions/（锚 Prometheus 3.x，核实 2026-08-01）。
- 一手/公告：Prometheus 官方博客「Announcing Prometheus 3.0」prometheus.io/blog/2024/11/14/prometheus-3-0/ 与 native histograms 相关文档——native histograms 仍为实验特性、需特性开关。
- 交叉核对：SRE 书给「为什么不能用平均、为什么看分位数」的方法学，Prometheus 文档给「直方图桶如何算分位、summary 为何不可跨实例聚合」的机制，两者对「分位数不可平均、应聚合桶计数再求分位」一致。
- 待核：native histograms 是否已转默认/稳定、其精确桶方案与远程写细节——随版本变动，勿凭记忆填。
