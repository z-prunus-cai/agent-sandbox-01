# L5-12·大主题6 traces 支柱与分布式追踪

> 基线 Py3.11.15/np2.4.6/gcc13.3 @2026-07-25 ｜核实日期：2026-08-01 ｜先修：本课大主题4/5（metrics、logs 两支柱）、L4-02 请求链与 HTTP、L5-01 因果序/happens-before ｜一手锚点：W3C Trace Context Level 1（Recommendation, 2021-11-23）/ Level 2（Candidate Recommendation Draft, 2024-03-28）、OpenTelemetry Traces 规范（opentelemetry.io/docs/specs）、Google Dapper 论文（2010）｜成熟度：追踪概念 GA；W3C Trace Context L1=Rec、L2=CR Draft；OpenTelemetry Traces GA/stable（⚙演进快·锚版本）

分布式追踪回答的是一个 metrics 和 logs 都答不好的问题：一次用户请求穿过十几个微服务，到底慢在哪、错在哪、走了哪条路。metrics 告诉你"整体 p99 延迟涨了"，logs 告诉你"某台机器某一行报错了"，但只有 trace 能把同一次请求在所有服务里的每一步串成一条带因果关系的时间线。本报告按 span 结构、上下文传播、采样策略、实际用途四个小主题展开。

业界常说的可观测性"三支柱（metrics/logs/traces）"是社区流行的组织框架（⚠二手概念，非规范），不是某个标准的正式定义；本报告承重的是 W3C Trace Context 标准与 OpenTelemetry Traces 规范这类一手来源，"支柱"一词仅作叙述性归类。

## 12-6.1 span 树与因果关系

### span 是一次操作的基本单元

一个 span 表示分布式系统里"一段有始有终的工作"——通常是一次 RPC 调用、一次数据库查询、一段函数执行。它记录这段工作的名字、开始时间、结束时间（因而有了耗时），以及一批描述性的属性。span 是构成 trace 的最小积木。

这个概念直接来自 Google 2010 年的 Dapper 论文：Dapper 把追踪树里的节点叫做 span，每个 span 对应"某台机器上某一次 RPC 的执行"，携带起止时间和用户标注。今天 OpenTelemetry 里的 span 是这个定义的直系后代，只是不再局限于 RPC，任何一段可命名的操作都能开一个 span。初学者可以把 span 想成"一根计时的秒表 + 一张便利贴"：秒表量出这段耗了多久，便利贴写上这段在干什么。

### trace 是同一次请求的所有 span 组成的树

一个 trace 代表"一次端到端的请求或事务"，由这次请求触发的所有 span 组成。这些 span 通过父子引用连成一棵树（更严格说是有向无环图），共享同一个 trace id。

因为一个操作常常并发触发多个下游操作。比如网关收到请求后同时去查用户服务和库存服务，这两个调用就是网关那个 span 的两个子 span，彼此是兄弟。共享同一个 trace id 是关键——正是它让分散在几十台机器、几十个进程里的 span 事后能被聚拢成同一次请求。没有共同的 trace id，你手里就只是一堆互不相干的计时片段。

### 父子关系用 parent_id 捕获因果序

树的边表示因果关系：子 span 是被父 span 引发的。这个关系通过子 span 记录父 span 的 span id（即 parent_id 字段）来表达。Dapper 论文里每个 span 就存三样东西——自己的 span id、父 span 的 id、以及 trace id。

这正好呼应 L5-01 的因果序（happens-before）：父操作"发生在"子操作之前并导致了它。要强调的是，trace 里的因果是"记录下来的调用引发关系"，不是靠时钟比较推断出来的——即使各机器时钟有偏差，parent_id 这条显式引用仍然准确地说明"谁调用了谁"。这是分布式追踪相比"把各机器日志按时间戳排序"的根本优势：它不依赖全局同步时钟来重建顺序。

### span 的组成：context、attributes、events、links、status、kind

一个 span 通常携带这几类信息。span context 是 span 上不可变的核心标识，包含 trace id、span id、trace flags、trace state 四项；它是要跨进程传播的那部分（见 12-6.2）。attributes 是键值对元数据（如 http.method、db.statement、user.id）。events 是 span 生命周期内某个瞬间的结构化标记（本质是带时间戳的一条结构化日志）。links 把本 span 关联到另一个 span，常用于跨 trace 的异步场景（如批处理里一个消费 span 关联多个生产 span）。status 表示这段操作的结果，取值 Unset（默认）、Ok、Error。span kind 标明操作类型：Server/Client（远程同步调用的两端）、Internal（进程内部操作）、Producer/Consumer（异步消息的产出与处理端）。

初学者常把 events 和子 span 搞混：events 是"这一个 span 内部的一个瞬间"，没有独立的持续时间和自己的 id；子 span 是"一段独立的、有起止的工作"，有自己的 span id 能再长出孙子。判断标准是——它本身还需要被细分和计时吗？需要就开子 span，只是打个时间点标记就用 event。links 与 parent 也要分清：parent 是"谁直接调用了我"（同一 trace），link 是"我和另外某个（可能属于别的 trace 的）span 有关联"，语义更松。

### root span 与 trace 的边界

root span 是整棵树里唯一没有父 span 的那个——它没有 parent_id，锚定整个层级。它通常在请求进入系统的最外层入口（如 API 网关、前端服务）创建，标志着这次 trace 的开始。

判断一个服务该"新建 root span"还是"续接已有 trace"，取决于入站请求里有没有带 traceparent 头。带了，就说明上游已经开了 trace，本服务应当把自己的 span 挂到传入的 span id 之下（成为子 span）；没带，本服务就是链路起点，新建 root span 并生成新的 trace id。这个"看请求头决定接续还是新建"的动作，正是下一节上下文传播要解决的问题。

#### 来源与时效
- OpenTelemetry Traces 概念文档 opentelemetry.io/docs/concepts/signals/traces/（span/trace/parent_id/span context/kind/events/links/status/root span 定义，核实 2026-08-01）— 强·一手（⚙演进快·锚版本：OpenTelemetry Traces GA/stable，CNCF 已毕业 2026-05）。
- Google《Dapper, a Large-Scale Distributed Systems Tracing Infrastructure》（Google Technical Report dapper-2010-1, 2010；research.google.com/archive/papers/dapper-2010-1.pdf）— 强·一手（tree/span/annotation 三概念、span 存 span id + parent id + trace id、边表因果）。
- 二者一致：span 为基本单元、trace 为 span 树、parent_id 表因果。无冲突。"三支柱"框架为 ⚠社区二手概念，仅作归类，不承重。
- 因果序底层理论回指 L5-01 happens-before（一手回指）。

## 12-6.2 context 传播与 traceparent

### 上下文传播是把 trace 缝合起来的机制

上下文传播（context propagation）指：当一个服务调用另一个服务时，把当前 span 的标识（trace id、当前 span id、采样决定等）随请求一起带给下游，使下游能把自己的 span 挂进同一棵树。没有传播，每个服务各开各的 trace，就永远拼不成端到端的一条链。

跨进程时这些信息只能"塞进请求本身"来传递——对 HTTP 就是塞进请求头，对消息队列就是塞进消息元数据。W3C Trace Context 标准的存在意义，就是规定一套所有厂商都认的标准头，让不同追踪系统的服务互相调用时也能接上 trace，而不是各用各的私有头导致链路断裂。这是一个跨厂商互操作标准，回指 L4-02 的 HTTP 请求头机制。

### traceparent 头格式

W3C Trace Context 规定的核心头是 traceparent，由四个用连字符分隔的字段组成，全部为十六进制。其规范布局与一个示例值如下：

```
traceparent: <version>-<trace-id>-<parent-id>-<trace-flags>
traceparent: 00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01
```

四个字段的含义与长度：version 为 2 个十六进制字符（当前值 00）；trace-id 为 32 个十六进制字符（16 字节，整条 trace 的全局唯一 id）；parent-id 为 16 个十六进制字符（8 字节，代表发出本请求的那个 span 的 id，在接收端即成为其父）；trace-flags 为 2 个十六进制字符（8 位标志位）。

这里的 parent-id 是"发送方当前 span 的 id"。下游收到后，它自己新建的 span 会把这个值当作 parent_id，于是父子关系跨进程被建立起来。字段名叫 parent 正是站在接收方视角——"你的父亲是谁"。另外注意 trace-id 全 0 或 parent-id 全 0 是非法值，收到应视为无效。

### tracestate 头携带厂商私有状态

除 traceparent 外，标准还定义了 tracestate 头，用来携带各追踪厂商的私有键值对，多个条目以逗号分隔，例如：

```
tracestate: rojo=00f067aa0ba902b7,congo=t61rcWkgMzE
```

traceparent 负责所有系统都必须理解的通用信息（哪条 trace、父是谁、采不采样），tracestate 则是给各厂商留的"扩展位"，让它们塞自己需要而别人不必懂的东西。规范建议：某系统修改了自己的条目后，应把它移到列表最左端。初学者容易忽略 tracestate 而只处理 traceparent——只处理 traceparent 通常能让基本链路工作，但会丢掉厂商特定的采样/关联信息。

### trace-flags 与 sampled 位

trace-flags 是 8 位标志字段，目前只定义了最低位——sampled（采样）位。当它为 1（如示例末尾的 01），表示上游"可能已经记录了本 trace 的数据"，提示下游也应记录，以保持整条 trace 的完整；为 0（00）则相反。

这个位解决的是采样一致性问题：如果每个服务各自独立随机决定采不采，一条 trace 就会东一块西一块地缺失，拼不完整。sampled 位把"采样决定"随传播一路带下去，让整条链要么一起被采、要么一起不采（详见 12-6.3 的一致性采样）。易错点：sampled=1 是"建议/表明可能已记录"，不是所有实现都保证严格强制；规范用的措辞是 caller "may have recorded"，属于协作性信号而非硬约束。

### W3C Trace Context Level 1 与 Level 2 的状态与差异

这套标准有两个版本，状态不同，必须分账并硬标时效（核实 2026-08-01）：

Level 1 是正式的 W3C Recommendation（发布 2021-11-23），即已定型的稳定标准，version 字节为 00，就是上面描述的四字段格式。

Level 2 是 W3C Candidate Recommendation Draft（草案，2024-03-28），属演进中、随时可能变更的状态（⚙演进快）。它相对 L1 的主要新增是：对 trace-id 和 span-id 的生成给出了考量，并引入一个 random-trace-id 标志——即把 trace-flags 的次低位（第 2 低位）定义为"随机 trace-id 标志"，置位表示 trace-id 至少最右 7 字节是随机（或伪随机）生成的。这个标志的用途是帮助下游/后端做基于 trace-id 的一致性概率采样时，能确信 id 有足够随机性。

因为 L2 还是草案，其新标志在不同实现里支持程度不一，不能当作已普遍可用的东西来依赖；而 L1 是可放心依赖的稳定基线。

### 向后兼容与版本解析规则

L2 刻意保持了对 version 00 的向后兼容。接收方遇到比自己已知版本更高的 traceparent 时，应仍按 version 00 的规则去解析 trace-id、parent-id 和 sampled 位——因为这几项在更高版本里布局不变。对于自己不认识的 trace-flags 标志位，转发出站请求时必须把它们置 0，从而保证老系统能安全忽略新标志而继续正常工作。

这条设计的直觉是"新老共存不互相破坏"：新版本只在保留旧字段语义的前提下往后追加信息，老实现看不懂新位就当它不存在。初学者实现解析器时的常见错误，是遇到 version 不等于 00 就整头丢弃；正确做法是尽力按已知的 00 布局提取前几个字段，而不是拒绝整条上下文——否则一次版本升级就会让链路大面积断裂。

#### 来源与时效
- W3C Trace Context, Level 1 — W3C Recommendation, 2021-11-23（w3.org/TR/trace-context/）— 强·一手标准（traceparent 四字段布局与长度、tracestate、sampled 位、示例值，核实 2026-08-01）。
- W3C Trace Context, Level 2 — W3C Candidate Recommendation Draft, 2024-03-28（w3.org/TR/trace-context-2/）— 强·一手草案（random trace-id 标志=trace-flags 次低位、向后兼容 version 00 的解析规则，⚙演进快·随时变，核实 2026-08-01）。
- 两级差异已分账：L1=Rec（稳定基线）、L2=CR Draft（草案，加随机 trace-id 标志、兼容 v00）。无实质冲突，属版本演进。
- OpenTelemetry Traces 概念文档（span context 含 trace id/span id/trace flags/trace state）与 W3C 定义一致，互为交叉印证。

## 12-6.3 头采样 vs 尾采样

### 为什么要采样

生产系统里每秒可能有成千上万次请求，若为每一次都完整存下所有 span，存储与传输成本会爆炸，且绝大多数正常请求的 trace 事后无人查看。采样就是"只保留一部分 trace 的完整数据"，在成本和可观测性之间取平衡。核心矛盾在于：你想省钱（少存），又想保证真正有价值的 trace（出错的、异常慢的）不被丢掉。头采样与尾采样是解决这个矛盾的两种时机不同的策略。

### 头采样（head sampling）在链路早期就决定

头采样指在 trace 刚开始、尚未看到完整 trace 的时候就做出采不采的决定。最常见的做法是一致性概率采样：根据 trace id 和设定的采样率（如"采 5%"）算出一个决定，并通过传播（sampled 位）让整条链沿用同一决定。

它的优点是简单、易配置、开销低——入口处一个随机决定就搞定，下游无需缓存任何东西。它的根本缺点是：决定时还没看到 trace 后面会发生什么，所以无法保证"出错的 trace 一定被采到"。一个请求在入口被判为"不采"，即使它后来在第 8 跳报了错、耗时暴涨，这条最该看的 trace 也已经被丢了。头采样把决定权交给了"运气"和"入口时刻能看到的信息"。

### 尾采样（tail sampling）在链路结束后决定

尾采样把决定推迟到 trace 里的 span 基本都完成之后，基于整条 trace 的完整信息再决定留不留。因为此时已经能看到全貌，就可以采取更聪明的策略：把所有含错误的 trace 都留下、把延迟超过某阈值的都留下、对特定服务采更高比例、对不同服务用不同采样率等。

代价也很实在：它需要先把一条 trace 的所有 span 在内存里攒齐才能判断，因此组件是有状态的、吃资源的——流量大时可能需要相当规模的算力节点来缓冲；实现也更复杂、需要持续调策略；开源现成方案相对少，常与具体厂商绑定。初学者要理解这个"攒齐才能判"的本质：正因为要等 trace 收尾，尾采样天然要面对"一条 trace 的 span 分散到达、要缓存多久才算收尾"这类工程难题。

### 一致性采样与 parent-based 避免破碎 trace

无论头还是尾，都要防止"同一条 trace 一半被采一半没采"的破碎结果。头采样常用两种手段保证一致：一是 parent-based（基于父的采样）——下游直接沿用上游经 sampled 位传下来的决定，父采则子采；二是一致性概率采样——所有服务用同一个 trace id 和同一采样率算，天然得出相同结论。

这解释了 12-6.2 里 sampled 位的意义：它就是把入口的采样决定广播给全链，让 parent-based 成为可能。易错点：如果链路中途某个服务无视传入的 sampled 位、自作主张重新随机采，就会制造断裂的半截 trace，事后拼图缺角。

### 头采样与尾采样的取舍对比

头采样"便宜但会漏掉出错/慢的 trace"，尾采样"能精准留住有价值的 trace 但贵且复杂"。实践中常组合使用——用头采样先做一层粗筛降量，再用尾采样在关键路径上按错误/延迟精挑，或干脆分层部署。

选择的判断依据是：你最在意成本可控且能接受偶尔漏采异常，就偏头采样；你最在意"绝不能漏掉出错和慢请求的证据"、且愿意为此投入有状态的采样基础设施，就偏尾采样。没有绝对优劣，取决于系统规模、预算和排障需求。

#### 来源与时效
- OpenTelemetry 采样概念文档 opentelemetry.io/docs/concepts/sampling/（head vs tail sampling 定义、一致性概率采样、parent-based、tail sampling 需有状态组件与其取舍，核实 2026-08-01）— 强·一手（⚙演进快·锚版本：OpenTelemetry Traces GA/stable）。
- W3C Trace Context L1（sampled 位语义：caller "may have recorded"，用于跨服务采样一致性）— 强·一手，与 OTel parent-based 采样互相印证。
- 两来源无冲突：sampled 位（W3C）是实现 parent-based 头采样（OTel）的传播载体。尾采样"需数十计算节点/常厂商绑定"等规模性描述来自 OTel 文档定性表述，具体数值随部署而变，标定性、不量化。

## 12-6.4 追踪的用途

### 延迟根因定位与关键路径

trace 最直接的用途是回答"这次请求为什么慢"。因为每个 span 都带起止时间，把一条 trace 的所有 span 按时间线画出来（火焰图/瀑布图），一眼就能看到哪个 span 占了大头——是某次数据库查询卡了 800ms，还是某个下游服务整体拖慢。这条决定总耗时的最长串联路径就是关键路径。

相比只看聚合 metrics（"p99 涨了"）或翻单机 logs（"某行报错"），trace 的优势是把一次具体请求在所有服务里的耗时拆解摊在一起，让你不必猜。初学者的心智模型：metrics 告诉你"发烧了"，trace 告诉你"病灶在哪个器官"。这回指 L4-02 的请求链——trace 就是把一条请求链的每一跳都量化计时后的产物。

### 服务依赖图

大量 trace 汇总起来，就能自动画出"谁调用谁"的服务依赖拓扑图。因为每条 trace 的 span 树本身就记录了调用关系，把成千上万条 trace 的边叠加统计，就得到系统真实的运行时依赖结构，还能标出每条边的调用量和典型延迟。

这个用途在微服务架构里尤其值钱：系统演化久了，没人能靠记忆说清全部服务间调用关系，而依赖图是从真实流量里"测量"出来的，不是靠人工维护的架构文档（后者往往过时）。它能帮你发现意料之外的依赖、评估某服务故障的爆炸半径。

### 跨服务瓶颈与扇出分析

trace 能暴露单看任何一个服务都看不出的跨服务问题。典型的是扇出放大：某个上游 span 下面并发挂了几十个下游子 span（比如循环里逐条去调用而非批量），或者某个下游被反复调用（N+1 调用）。在 span 树里这类模式一目了然——一个父 span 底下密密麻麻一排同类子 span。

它也能定位"瓶颈在服务边界而非服务内部"的问题，比如两个服务都各自很快，但它们之间的串行等待、排队或网络往返累加起来才是罪魁。这类问题在单服务视角（各自的 metrics 都正常）里是隐形的，只有端到端 trace 能显形。

### 与 metrics、logs 的关联

trace 的第四类价值是把三种遥测数据串起来。因为 trace id、span id 可以写进日志行，也可以作为 exemplar（样例）附在 metrics 的直方图桶上，于是你能从"一个异常的 metrics 数据点"跳到"造成它的那条具体 trace"，再从 trace 的某个 span 跳到"那一刻这台机器的日志"。

这正是把 metrics（本课大主题4）、logs（大主题5）、traces 三者从孤立数据变成"可互相钻取"的一张网的关键。初学者的实用直觉：先用 metrics 发现"哪段时间、哪个服务不对劲"，再用 trace 定位"具体是哪条请求、哪一跳的问题"，最后用 logs 看"那一跳当时到底发生了什么细节"。三者按"面→线→点"层层收敛，而 trace id 是把它们粘在一起的胶水。这也是 OpenTelemetry 把三信号统一在一个规范体系下的动机（详见本课大主题7）。

#### 来源与时效
- OpenTelemetry Traces 概念文档 opentelemetry.io/docs/concepts/signals/traces/（span 计时构成瀑布/关键路径、span 树表调用关系可聚合成依赖图，核实 2026-08-01）— 强·一手（⚙演进快·锚版本：Traces GA/stable）。
- Google《Dapper》论文（2010，research.google.com/archive/papers/dapper-2010-1.pdf）— 强·一手（分布式追踪最初动机即"在大规模 RPC 系统里做延迟根因与依赖分析"，与今日用途一脉相承）。
- 用途性描述（延迟根因/依赖图/扇出/三信号关联）两来源方向一致；具体可视化形式（火焰图等）为工具实现细节，随产品而变，不锚定具体产品版本。请求链概念回指 L4-02（一手回指）。
