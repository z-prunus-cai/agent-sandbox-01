# Java 平台与企业级框架生态情报简报(2026-07-03)

> 统计窗口:核心事件覆盖 2026-06-25 至 2026-07-03(过去48小时至8天)。个别对理解当前技术脉络必不可少的背景事件(JSR 403 专家组成立、Leyden premain 分支实测数据、Netflix/Uber 生产复盘)追溯保留至 06-01,均已在正文中标注真实日期。

---

## 🔴 Tier 1:核心突破与范式转移

### 1. Project Valhalla JEP 401 集成 PR 曝光:19.7万行代码正式提交,标量化机制细节首次完整披露 `[JEP 重大进展]` `[范式转移]`

**事件全景**:Oracle 工程师 Lois Foltan 6 月 15 日在 jdk-dev 邮件列表确认 JEP 401《Value Classes and Objects (Preview)》将于 7 月初集成 openjdk/jdk 主线,目标 JDK 28(2027 年 3 月 GA)。对应的集成 PR(openjdk/jdk#31120,标题"8317277: Java language implementation of value classes and objects")已可查证:含 **2894 个 commit、19.7 万行代码、1816 个文件变更**,拆分为编译器(#31121)、JVM(#31122)、标准库(#31123)三个子审查,同时捆绑 JEP 8350458(Strict Field Initialization)。这是 Java 对象模型三十年来最大的一次结构性变革——自 1995 年以来,每个 Java 对象都被强制携带"身份"(identity)语义,这是装箱类型堆内存膨胀、缓存不友好、GC 扫描压力的根源。6 月 24 日 JSR 403(Java SE 28)专家组正式成立,成员含 Simon Ritter(Azul)、Iris Clark(Oracle,规范负责人)、Stephan Herrmann(Eclipse)、Christoph Langer(SAP),公众评审定于 2026 年 12 月至 2027 年 2 月。

**底层机制/设计哲学**:引入 value class/value record 取消对象身份,使 JVM 对合格值对象做**标量化**(scalarization:JIT 编译期把值对象拆解为寄存器/栈上离散标量,而非堆分配对象整体传递)与**堆扁平化**(heap flattening:数组/字段直接内联存储值编码本身而非指针跳转)。JDK 内置的 Integer 等 value-based 类将在预览模式下率先迁移为 value class,验证"codes like a class, works like an int"设计目标。Inside.java 披露的具体数字:`LocalDate` 迁移为 value class 后,数组场景下的扁平化存储可实现约 **3 倍**加速,企业级数据密集型场景(高频交易、实时分析)堆内存消耗预计降低 **15%-20%**,核心收益来自 CPU 缓存局部性的显著改善。构建该分支需 `--with-boot-jdk=<jdk-26>` 且禁用 ShenandoahGC,反映当前实现尚未覆盖全部 GC。

**生产架构影响与指导**:Brian Goetz 本人明确表态"这只是 Valhalla 的第一部分",预计 JEP 401 在下一个 LTS(JDK 29)中仍将保持 Preview 状态,"希望它在 Java 29 退出预览似乎有点乐观"——技术团队应据此调整预期,不要期待 2027-2029 年间 Valhalla 立即可用于生产。规模空前的合并(19.7 万行代码)期间 Oracle 已要求其他提交者暂缓大型重构,预示 7 月 openjdk/jdk 主线短期会有集成动荡。数值密集型系统(金融计算、图计算、序列化框架、Vector API 的下游使用方)应将 2027-2029 窗口纳入技术雷达,同时排查依赖对象身份语义(`==`、`synchronized`、弱引用)的代码,这些用法在向 value class 迁移时将不再适用。

---

### 2. Leyden premain 分支实测数据首度曝光:Spring Boot 启动最高提速 4.7 倍,AOT 与 ZGC 十年博弈就此终结 `[JEP 重大进展]` `[性能跃升]`

**事件全景**:与已交付 JDK 26 的 JEP 516(Ahead-of-Time Object Caching with Any GC)相印证,openjdk/leyden 仓库 premain 实验分支的 README 首次披露具体框架级基准数据(对比基线 JDK 25 build 25+37 vs Leyden premain 最新 commit):**Spring Boot 启动提速 4.13x–4.70x,Micronaut 2.91x–4.90x,Quarkus 2.97x–3.74x,Helidon 3.59x–4.11x**。这组数据首次把 Leyden 的抽象承诺("大幅缩短启动时间")兑现为可对比的框架级实测倍数,直接回应了"AOT 缓存到底能不能真正加速主流框架"这一企业最关心的问题。同时,6 月 30 日 Inside.java 发布 ZGC 十年回顾文章(作者 Stefan Johansson,ZGC 核心开发者),系统总结其从 JDK 11 实验特性到如今支撑关键在线服务的历程,暂停时间在 8MB 至 16TB 堆范围内均稳定低于 1 毫秒。

**底层机制**:JEP 516 用 GC 无关的中性格式缓存对象——以逻辑索引(logical index)而非物理地址存储对象引用,JVM 启动时后台线程流式读取缓存并将索引重映射为当前环境实际地址,彻底解决了 ZGC 着色指针(colored pointers)与传统固定地址 AOT 缓存格式不兼容的历史矛盾;需显式加 `-XX:+AOTStreamableObjects` 启用。premain 分支进一步叠加 **AOT Code Compilation**(训练期编译的机器码直接持久化,生产运行跳过解释执行→C1→C2 预热)、**Dynamic Proxy Generation**(预生成 Spring 等框架大量依赖的反射代理类)、**Class Not Found Cache**(缓存框架反复失败的 classpath 探测结果)三项默认启用特性,Spring PetClinic 生产基准显示启动提速 41%,约 2.1 万个类在启动时已处于"已加载已链接"状态。

**生产架构影响与指导**:当前限制明确——训练运行与生产运行必须匹配 CPU 型号及 GC 种类,premain 分支目前仅支持 G1/Serial/Parallel/Epsilon/Shenandoah,尚不含 ZGC(与已交付的 JEP 516 基线特性需区分:后者已支持 ZGC,但更激进的 AOT Code Compilation 原型仍受限)。作为路线图上更早交付的基石,JDK 25 的 JEP 515(AOT 方法性能画像)已实测让示例程序运行速度提升 19%,AOT cache 额外磁盘开销仅约 250KB,代价极低。对运行在 Kubernetes/Serverless 环境、频繁扩缩容的 Spring Boot/Micronaut/Quarkus/Helidon 工作负载而言,这组数据意味着"启动开销"这一 Java 相较 Go/Node.js 的历史短板,在 2027 年前后可能被系统性抹平——技术团队应将 Leyden 落地纳入下一代基础镜像选型评估,同时评估自身应用中动态代理框架(Spring AOP、Hibernate、CGLIB)占比,判断当前覆盖率是否满足实际收益。

---

### 3. HotSpot 逃逸分析的"流不敏感"缺陷曝光,Microsoft 提交栈上分配 JEP 提案:JIT 优化的下一个十年战场 `[JEP 重大进展]` `[范式转移]`

**事件全景**:hotspot-dev 邮件列表持续跟进的一项量化研究显示,C2 编译器现有 Escape Analysis(EA)存在系统性局限——约 **80% 的标量替换(SRA)/锁消除(LE)候选方法**因 EA 判定对象"逃逸"而被排除,约**三分之二**的 C2 编译方法甚至未被纳入 SRA/LE 候选考量。根本原因是当前 EA 是**流不敏感(flow-insensitive)**分析:只要对象在任意一条控制流路径上逃逸,就整体判定为逃逸,即便在其余路径上该对象完全可以栈分配。基于此,Microsoft Java Engineering Group 正式提交了 Stack Allocation JEP 提案(github.com/microsoft/openjdk-proposals),目标在 Renaissance、Scala DaCapo 等标准基准中把堆分配减少最多 15%、C2 生成代码单个分配点指令数减少最多 10%。

**底层机制/设计哲学**:研究提出 Partial Escape Analysis(PEA,控制流敏感变体)作为过渡方案,预计可将编译代码性能提升最多 10%、堆内存需求降低最多 8%。与标量替换(把对象拆解为独立字段,丢失对象整体形状)不同,栈分配保留对象完整形状——这使其能处理控制流合并点的对象,以及需要把结构本身传给"检查其结构"的方法的场景,是比标量替换更通用的优化手段。这一提案与 Valhalla 的值类型标量化形成互补而非替代关系:标量化面向声明为 value class 的类型做确定性优化,栈上分配则面向普通 identity 类的对象在 JIT 运行时按逃逸分析结果做启发式优化。

**生产架构影响与指导**:这是 JIT 编译器优化领域近年来少有的"大规模量化研究驱动提案"——过去逃逸分析改进多是零散补丁,此次由外部厂商(Microsoft)提交正式 JEP 意味着该领域可能进入系统性重构周期。技术团队短期无需行动(提案仍处早期阶段,未进入任何已发布/候选 JDK 版本),但应关注其后续走向:一旦落地,将直接影响 Java 相较 Go/Rust 在对象分配开销上的经典短板,对高频短生命周期对象分配的系统(网关、序列化框架、函数式编程风格代码)有直接吞吐量收益,值得纳入 2027+ 年度技术雷达。

---

### 4. Spring AI 2.0 工具调用架构完整重构:从"模型私有实现"到"Advisor 链一等公民" `[GA 正式版]` `[范式转移]`

**事件全景**:Spring 官方博客 6 月 15 日发布《Tool Calling in Spring AI 2.0: A Composable, Agentic Architecture》,首次完整披露 2.0 版本(5 月 28 日 GA)对工具调用机制的彻底重写。1.x 版本中,每个 `ChatModel` 实现(OpenAI、Anthropic、Bedrock 等)各自私有封装工具执行循环,无法钩入、无法观测中间步骤、无法与其他横切能力组合——这是企业级 Agentic AI 落地时长期存在的架构黑洞:工具调用出错时无法定位是哪一轮、哪个工具、传了什么参数。2.0 将工具调用重构为 `ChatClient` Advisor 链中的可组合一等公民,新增 `ToolCallingAdvisor`(自动注册)、`ToolSearchToolCallingAdvisor`(面向数百个工具的渐进式披露检索)、`StructuredOutputValidationAdvisor`(结构化输出自纠错重试)。

**底层机制/设计哲学**:核心设计是让 `ChatClient` 通过一条有序 Advisor 链驱动请求,支持循环重入下游链——同一套机制同时驱动工具调用循环、结构化输出重试循环、评估循环,而非为每种能力单独造轮子。设计目标是渐进式复杂度:从单个 `@Tool` 注解起步,逐步添加记忆(memory)与可观测性,接入 MCP(Model Context Protocol)工具,工具集增长时无缝切换到 `ToolSearchToolCallingAdvisor`,乃至扩展循环本身。同批集成 MCP Java SDK 2.0.0(对齐 2025-11-25 规范),新增注解驱动模型 `@McpTool`/`@McpResource`/`@McpPrompt`,默认传输改为 Streamable HTTP。

**生产架构影响与指导**:破坏性变更包括从各 Options 类移除全部 setter(强制 builder/构造函数保证并发场景下的不可变性)、配置属性移除 `.options` 段、用户面 API 从 `ChatModel` 转向 `ChatClient`。硬性依赖 Spring Boot 4.0/Framework 7(Jakarta EE 11、Java 17+),仍在 Boot 3.x 的团队应继续使用 Spring AI 1.x 维护线,不应盲目升级。对已在生产运行 Agentic AI 系统的团队,这次重构提供了此前缺失的可观测性接入点(OpenTelemetry 指标)与 OAuth 2.0 安全支持,建议优先评估迁移收益;新项目应直接以 Advisor 链架构设计工具编排逻辑,而非复刻 1.x 的私有循环模式。

---

## 🟡 Tier 2:重要迭代与应用生态

### 5. JDK 27 完整 JEP 清单锁定:9 个 Targeted + 3 个 Proposed,G1/紧凑对象头默认化叠加后量子加密 `[JEP 重大进展]` `[性能跃升]`

JDK 27 已于 6 月 4 日完成 Rampdown Phase One(特性冻结),GA 定档 9 月 14 日。9 个 Targeted JEP 中,**JEP 523**(G1 成为所有环境默认 GC,取消 server-class 检测)、**JEP 522**(G1 双 Card Table,写屏障指令数从约 50 条降至 12 条,吞吐提升 5%-15%)、**JEP 534**(紧凑对象头默认启用,SPECjbb2015 基准堆占用降 22%、CPU 降 8%)三者叠加构成一次"零代码改动"性能红利。**JEP 527**(TLS 1.3 后量子混合密钥交换,默认启用 X25519MLKEM768)使标准 `javax.net.ssl` 应用无需改代码即获得抗量子窃听能力。并发侧 **JEP 533**(结构化并发第七轮预览)API 变更:`Joiner<T, R>` 增加第三类型参数 `R_X` 用于声明 `join()` 异常类型,标准 Joiner 改抛标准 `ExecutionException`;**JEP 537**(Vector API 第 12 轮孵化)本轮无 API 变更,官方明确其升级为 Preview 需等待 Valhalla 扁平化值类型基础设施就绪。**落地指南**:运行在 1-2 vCPU 容器中的服务是 G1 默认化最大受益方,升级前必须重做 GC 基准测试;此前基于 Preview 6 编写的结构化并发代码需跟随 API 变更迁移。

### 6. GraalVM 转向月度发布节奏,Project Galahad 解散——Graal JIT 编译器确认不再并入 OpenJDK 主线 `[范式转移]`

GraalVM 自 25.1(2026 年 6 月)起 feature release 改为月度节奏,25.1.3 已于 6 月 30 日发布,Native Image 体积较基线再降 1%-2%(HelloWorld 镜像降至约 6.5MB,得益于 String.format 内联优化),Linux 构建默认生成 PIE(位置无关可执行文件),新增实验性 Web Image 后端(通过 `--tool:svm-wasm` 将 Java 应用 AOT 编译为 WebAssembly)。**核心工程思想**:GraalVM 团队用"一支 AI Agent 舰队"配合训练运行工作流,为约 1500 个第三方库自动生成 reachability metadata,大幅扩展 Native Image 开箱兼容库覆盖面。更关键的背景是:旨在把 Graal 编译器贡献进 OpenJDK 作为默认 JIT 的 Project Galahad 已于 3 月解散,官方原因是"2025 年 9 月已宣布 GraalVM 与 Java 生态解耦的决定使其失去存在意义"——这意味着 OpenJDK 的冷启动方案将坚定走 Leyden 自研 AOT 路线,与 GraalVM Native Image 的静态封闭世界路线并行而非合流。**落地指南**:选型时应认清二者是两条独立技术路线而非互补演进关系;Native Image 用户应关注月度发布带来的更快特性验证周期,但也需为更频繁的兼容性验证投入 CI 资源。**需特别警惕**:GraalVM 官方已同时宣布**跳过对 JDK 26/27/28 三个非 LTS 版本的专门支持**,直接对齐下一个 LTS(JDK 29)——计划升级到 JDK 27 的 Native Image 用户若想继续获得官方支持,需直接规划到 JDK 29,中间的非 LTS 版本将处于支持空窗;好消息是免费的 GFTC(GraalVM Free Terms and Conditions)版本已开放此前锁在企业版付费墙后的高级性能优化与 G1 GC 支持,显著降低了 Native Image 生产部署的采购门槛。

### 7. Spring Boot 3.5.16 收官,3.5/2025.0 系列全线 OSS EOL——企业面临强制升级窗口 `[GA 正式版]`

Spring Boot 3.5.16(6 月 25 日)、Spring Data 2025.0.13(6 月 24 日)、Spring Cloud 2025.0.x(Northfields)均已于 6 月 30 日正式停止开源支持,官方明确声明"3.5.16 是 3.5.x 系列最后一次 OSS 发布"。当前受支持版本收窄为 Spring Boot 4.1(支持至 2027 年 7 月)与 4.0(支持至 2026 年 12 月)。HeroDevs 已宣布"Never-Ending Support"从 7 月 1 日起为仍运行 3.5 的企业提供 CVE 补丁覆盖填补真空期;Chainguard 同期(6 月 24 日)GA 发布"Chainguard Libraries for Java",为 spring-boot/spring-framework/spring-security/h2database 提供不改 API 的 drop-in 回移补丁,帮企业在不做大版本升级前提下消解 CVE 积压。**落地指南**:仍在 3.5.x 的团队须在"升级到 4.x"与"采购商业延保"间尽快抉择,不作为将直接暴露于未来 CVE 无补丁窗口。

### 8. Hibernate ORM 8.0.0.Beta1:对齐 Jakarta Persistence 4.0,查询 API 按语义拆分 `[Preview 预览特性]`

6 月 16 日发布,核心变更是查询模型重构:selection(查询,`SelectionQuery` 现实现标准 `TypedQuery`)与 mutation(变更,新引入 `Statement` 契约由 `MutationQuery` 实现)彻底分离,终结了此前同一套 API 承载查改语义混淆的设计债务。Bean Validation 触发时机被重新定义,区分 EntityManager 级事件(pre-persist/pre-merge/pre-remove)与数据库级事件(pre-insert/pre-update/pre-upsert/pre-delete),默认仅 insert/update/upsert 触发校验。新增 DDL Schema 校验能力:`@NotNull` 等 Jakarta Validation 注解可直接驱动生成建表 DDL 的非空约束(`hibernate.tooling.schema.apply_validation_constraints`),消除"注解语义与实际表结构不一致"这一经典生产事故源。**落地指南**:Beta 阶段暂不建议生产采用,但类型安全查询 API 与校验-DDL 联动机制值得提前评估迁移成本。

### 9. Apache Kafka 4.3 双版本发布:分层存储运维能力增强 + Streams 内存泄漏根治 `[性能跃升]`

4.3.0(5 月 22 日,25 个 KIP、600+ commit)带来 KIP-1257(分区大小占保留量比例监控指标)、KIP-1066(cordon 机制隔离 broker/log 目录便于扩缩容)、KIP-1274(逐步废弃 Classic rebalance 协议)等运维能力增强;4.3.1(6 月 25 日)修复 KAFKA-20616/20688——RocksDBStore 在 rebalance 触发的分区迁移场景下反复 close 导致堆外内存无限增长直至 OOM,这是有状态 Streams 拓扑中极隐蔽但影响面广的问题,现象酷似"业务状态存储规模过大"极易误诊。**落地指南**:自动扩缩容频繁触发 rebalance 的有状态 Streams 部署应立即升级至 4.3.1;纯 Producer/Consumer 客户端不受影响。

### 10. 虚拟线程"两年生产复盘"共识收敛:不是万能药,pinning 仍是隐形杀手 `[性能跃升]`

多篇 6-7 月技术复盘文章(Java Code Geeks 系列)形成一致结论:虚拟线程解决的是"I/O 等待下的线程可扩展性"而非 CPU 利用率,高并发 REST/gRPC、阻塞式 DB/HTTP 调用是其目标场景,滥用会导致难以诊断的性能退化。JEP 491(JDK 24)从 JVM 层面修复了 `synchronized` 代码块导致虚拟线程"钉住"(pinning)载体线程的历史缺陷(按虚拟线程身份而非 OS 线程身份跟踪 monitor 所有权),HikariCP、Caffeine、Apache HttpClient、MySQL Connector/J 均因此问题被迫适配。但 **JNI 原生代码引起的 pinning 在所有 JDK 版本中依然存在**,需要将 JNI 调用隔离到独立的平台线程执行器处理。**核心工程思想**:用 `-Djdk.tracePinnedThreads=full` 结合 JFR 火焰图在预发环境专项排查,是当前虚拟线程迁移项目的标准诊断流程。

### 11. Netty/Kafka/Tomcat 高危 CVE 集中修复,生产网络栈打补丁窗口收紧 `[性能跃升]`

Netty 4.2.15.Final/4.1.135.Final(6 月初)一次性修复 23 个 CVE,含 CVE-2026-48043(HTTP/2 帧引用计数泄漏致内存耗尽 DoS)与 CVE-2026-47691(DNS 缓存投毒,`DnsResolveContext` 对 NS 记录 bailiwick 校验不足)两个高危项;Tomcat 集群会话复制补丁本身引入回归 RCE(CVE-2026-29146 修复后又爆出 CVE-2026-34486);Spring Security 2026.06 一次性修复 7 个 CVE(含 SAML 凭据不安全反序列化 CVE-2026-40993、X.509 证书身份冒用 CVE-2026-47838)。**核心工程教训**:Spring 官方 6 月 1 日博客披露,单是 4 月单月就收到 482 份安全报告(历史月均 6.5 份),AI 辅助漏洞挖掘正系统性改变开源项目安全响应节奏,其中 37% 为噪音但仍需人工分诊。**落地指南**:企业应把"季度手动升级"节奏调整为"月度自动化补丁"节奏,尤其是 Netty 这类几乎所有 Java 中间件的隐式依赖。

---

## 🟢 Tier 3:行业风向与速递

- **Micronaut Framework 5.0 GA**(5 月 20 日):Java 基线提升至 25(虚拟线程/结构化并发/Scoped Values 全面可用),GraalVM 基线升至 25.0.3;破坏性变更包括全面转向 Jackson 3、JSR-250 注解处理器迁至独立模块 `micronaut-security-processor`,覆盖超 70 个模块的平台级刷新。
- **Quarkus 3.37 系列**:3.37.0(6 月 24 日)默认启用 Jackson 无反射序列化器提升启动速度与原生镜像兼容性;3.37.1(7 月 1 日)修复一起显著 CPU 占用回归及 OIDC DPoP nonce 处理缺陷。
- **JUnit 6.1.1**(6 月 28 日):新增基于常规线程池而非 ForkJoinPool 的并行执行实现、实验性内存清理模式(面向大规模测试套件反 OOM)、测试事件可通过 socket 实时上报而非只写文件。
- **Testcontainers Java 2.0**:破坏性变更——彻底移除 JUnit 4 支持、模块坐标统一加 `testcontainers-` 前缀,新增面向 AI 网关场景的 `DockerMcpGatewayContainer`。
- **OpenTelemetry Java 生态**:CNCF 已于 5 月 21 日宣布 OTel 项目毕业,确立可观测性事实标准地位;但 opentelemetry-java-instrumentation 曾曝出 CVE-2026-33701(RMI 埋点反序列化 RCE,CVSS 9.3,JDK ≤16 且开放 JMX/RMI 端口时可远程执行代码,已修复至 2.26.1)。
- **Micrometer 1.17.0**:新增对齐 JDK 25 `ForkJoinPool#getDelayedTaskCount()` 与 JDK 26 `MemoryMXBean.getTotalGcCpuTime()` 的原生 JVM 指标,可观测性下沉到 GC CPU 耗时粒度。
- **Gradle 9.7.0-M2**(7 月 2 日):Isolated Projects 特性从实验态升级为孵化态,面向大型 monorepo 并行构建性能优化。
- **企业生产实践复盘**:某 8 万 BHCC/1 万坐席 Java+Kafka 呼叫中心平台披露真实教训——延迟敏感路径用 Kafka 做跨实例去重存在轮询间隔造成的延迟下限,改用 Redis 协调可消除;Spring Boot 应用上下文初始化会在 Kafka 消费者就绪前额外拖慢 Pod 启动 30-45 秒,叠加 JVM 启动时的事件重放"启动风暴"会使 K8s HPA 自动扩缩容失效。
- **Netflix Tech Blog**:内部 Nebula 插件套件将 ArchUnit 架构规则检查扩展到数万个仓库的多仓库(polyrepo)场景,自动追踪跨仓库对"实验性/已废弃"API 的下游使用情况。
- **阿里云 AgentScope Java 2.0**(6 月 8 日):面向生产的分布式企业级 AI Agent 框架,强调可直接嵌入现有 Spring Boot 微服务体系并支持 K8s 无状态水平扩展,与 `spring-ai-alibaba` 项目形成呼应。
- **Spring 生态周边**:Spring Modulith 2.1 GA 新增事件外部化 outbox 模式;Spring Batch 6.0.4 结合 Boot 4.1 的 MongoDB JobRepository 自动配置实现文档数据库全链路批处理;Open Liberty 26.0.0.5 新增支持直接运行 Spring Boot 4.0 应用,传统 Jakarta EE 应用服务器与 Spring 生态出现融合信号。
- **构建工具链**:Maven 核心线稳定在 3.9.16,4.0.0 仍卡在 rc-5、官方未给 GA 时间表,与 Gradle 更快的月度迭代节奏形成选型对比;RSocket-Java 自 2025 年 2 月起无新版本,活跃度告警。
- **JDK 26 已 GA(3 月 17 日)的滞后价值仍在释放**:`HttpClient` 新增 HTTP/3(基于 QUIC,消除队头阻塞)支持,默认仍走 HTTP/2 兼容(当前仅约三分之一网站支持 HTTP/3,需显式开启);`HttpRequest.Builder::timeout` 作用域从"仅到响应头"扩展为覆盖响应体消费全过程,修复了长期存在的超时语义误解。
- **G1 Remembered Set 内存优化(JDK 25)**:通过合并老年代 region 级 remembered set 并利用标记阶段收集的入引用计数改进候选回收 region 选择精度,64GB 堆场景下 remembered set 峰值内存从 2GB 降至 0.75GB,叠加 JDK 20+ 移除的标记位图,进一步降低容器化小内存部署的原生内存开销。
- **Eliya 25.0.3**:Asymm Systems 推出面向电信/银行/医疗/政府等合规敏感行业的"生产诊断"OpenJDK 25 LTS 下游发行版,新增 `-XX:EliyaProfile=Production` 一键开启 OOM 结构化堆转储、exit-on-OOM 配合编排系统重启、NMT summary 模式等诊断策略聚合能力,支持窗口对齐 JDK 25 LTS 至 2029 年 9 月。
- **JEP 500(Prepare to Make Final Mean Final,JDK 26 已交付)**:深度反射修改 final 字段默认发出运行时警告且 `--add-opens` 无法绕过,提供 warn/deny/allow 三档运行模式,为未来版本彻底禁止此类修改铺路,序列化框架维护者需提前评估影响。
- **Inside Java Podcast 第 60 期**(6 月 25 日):Nicolai Parlog 对话 Java 语言规范负责人 Alex Buckley,深入拆解 JEP 从草案到定案的治理机制,是理解本简报中大量 JEP 状态变更背后决策逻辑的一手信源。

