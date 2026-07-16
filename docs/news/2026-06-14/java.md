# Java 平台与企业级框架生态情报简报
**日期：2026-06-14 | 覆盖窗口：过去 48 小时及近期重大事件**

---

## 🔴 Tier 1：核心突破与范式转移

---

### 1. Spring AI 2.0.0 正式 GA：Java 企业级 AI 工程的新基线 `[GA 正式版]`

**事件全景**

2026年6月12日，Spring AI 2.0.0 正式版（GA）发布，同日附带 Spring AI 1.0.9 与 1.1.8 的安全维护更新同步登陆 Maven Central。这一发布标志着 Spring 生态对 AI 原生应用工程的系统性重构正式落地，而非停留在"接入层封装"层面。此前 Spring AI 1.0（2025年5月）首发时以模型适配为核心，1.1（2025年11月）引入 MCP（Model Context Protocol）协议支持和 Advisors API，而 2.0.0 则将 AI 能力深度熔合进 Spring 应用的生命周期与类型系统之中。

**底层机制与设计哲学解析**

Spring AI 2.0.0 完全基于 Spring Boot 4 / Spring Framework 7 体系重建，核心设计哲学有三：

- **JSpecify 全域 Null Safety**：2.0 版本在整个 API 表面引入 JSpecify 注解（`@NonNull`/`@Nullable`），配合 NullAway 在编译期强制校验，消除了以往 AI SDK 中大量"对象为 null 则崩溃"的运行时陷阱。这对 RAG 管道、流式输出回调等高度异步的 AI 链路尤为关键。

- **MCP 原生一等公民**：Spring AI 2.0 将 Model Context Protocol 的 Server 端与 Client 端均做了 Spring 组件化封装，工具定义（Tool Definition）与 Bean 注册流程统一，Agent 可以通过标准 Spring DI 消费来自不同 MCP Server 的工具，无需手动管理协议握手。

- **声明式 HTTP 接口 OTel 自动传播**：基于 Spring Framework 7 的 Native OTel 支持，所有通过 Spring AI 声明式 HTTP 接口发出的 AI 调用，Trace ID 自动传播到 OTel Collector，无需手动注入，使 AI 链路可观测性成本接近零。

**生产架构影响与指导**

从 Spring AI 1.x 升级至 2.0 需注意：底层依赖 Spring Boot 4（最低 Java 17），如仍在 Spring Boot 3.x 上需先完成框架升级。API 变动主要集中在 `ChatClient` builder 链与 `Advisor` 注册方式，官方提供了迁移指南。对于已投产 RAG 与 Agent 流水线的团队，建议重点审查 null 安全相关的编译警告并逐步启用 NullAway 检测，这将显著提升 AI 业务逻辑的可维护性。

---

### 2. Spring Boot 4.1.0 GA 与 Spring Framework 7.0.8：企业 Java 新世代基线全面成熟 `[GA 正式版]`

**事件全景**

2026年6月10日，Spring Boot 4.1.0 正式发布；次日（6月11日）Spring Modulith 2.1 GA、Spring Cloud 2025.0.3（Northfields）和 2025.1.2（Oakwood）双轨并行落地。同期 Spring Boot 4.0.7 补丁版于6月12日发布，修复了 4.0.x 生产线的若干回归问题。至此，以 Spring Framework 7 为基底的全 Spring 生态在同一周内完成了一次集体性成熟里程碑。Spring Boot 4.1.0 内置 Spring Framework 7.0.8、Spring Security 7.1.0、Spring Kafka 4.1.0，构成当前企业 Java 应用的最新推荐基线。

**底层机制与设计哲学解析**

Spring Framework 7 的两项根本性变革在 4.1.0 中已深度集成：

- **彻底模块化**：整个 Spring Boot 代码库按技术维度拆分为粒度更细的 JAR，每个 starter 对应独立模块，减少了无关依赖被传递引入的可能，对 GraalVM Native Image 的 AOT 静态分析和 reachability 元数据生成尤为友好，AOT 构建时间可减少 20~30%。

- **一等公民 API 版本控制**：Spring Framework 7 内置四种 API 版本策略（路径前缀、HTTP Header、Query Parameter、Media Type 参数），彻底终结了企业项目中靠自研拦截器实现 API 版本路由的野路子，这是多年来 REST API 治理的顽疾。

- **内建弹性原语 `@Retryable` 与 `@ConcurrencyLimit`**：无需引入 Resilience4j 即可在 Service 层声明重试与并发限制，减少了对外部弹性库的依赖，但对于复杂熔断场景 Resilience4j 仍是首选。

**生产架构影响与指导**

Spring Boot 3.5.x OSS 支持将于2026年6月30日终止，团队需在本季度内规划升级路径：3.5 → 4.0 → 4.1。4.x 强制 Java 17 最低版本，Jakarta EE 10 命名空间（`javax.*` → `jakarta.*`）在 4.0 已完成，4.1 在此基础上增强了与 Jakarta EE 12 的对齐。Spring Security 7.1.0 对 Reactive OAuth2 的改进（ID Token 在 Refresh Token 后正确更新）在反应式网关架构中尤为重要，需重点回归测试。

---

### 3. JDK 27 进入 Rampdown Phase 1：九大 JEP 特性冻结，GA 9月落地 `[JEP 重大进展]`

**事件全景**

2026年6月1日，JDK 27 官方进入 Rampdown Phase 1（RDP1），主线代码库已完成分支，JEP 集合彻底冻结，目标 GA 日期为2026年9月14日。InfoQ 本周披露的 JDK 27 最终 JEP 名单包含九项特性，均已确认 Targeted 状态。这是自 JDK 21 LTS 以来最具规模的 JVM 运行时能力跃迁，且 JDK 27 与下一个 LTS（JDK 29，预期2027年9月）之间的铺垫意义重大。已明确纳入的 JEP 包括：

| JEP | 特性 | 状态 |
|-----|------|------|
| JEP 523 | Make G1 the Default GC in All Environments | Targeted |
| JEP 527 | Post-Quantum Hybrid Key Exchange for TLS 1.3 | Targeted |
| JEP 534 | Compact Object Headers by Default | Targeted |
| JEP 536 | JFR In-Process Data Redaction | Targeted |
| JEP 537 | Vector API（第12次孵化） | Targeted |
| JEP 538 | PEM Encodings（第3次预览） | Targeted |
| JEP 532 | Primitive Types in Patterns（第5次预览） | Targeted |
| JEP 533 | Structured Concurrency（第7次预览） | Targeted |

**底层机制与设计哲学解析**

两项最具生产冲击的特性值得深挖：

**JEP 534（Compact Object Headers 默认启用）**：在 JDK 25（JEP 519）将紧凑对象头作为可选产品特性引入后，JDK 27 将其设为默认开启。对象头从 12 字节压缩至 8 字节，节省幅度达 33%。Amazon 在数百个生产服务的实测中得出：SPECjbb2015 堆使用减少 22%、CPU 时间减少 8%、GC 次数减少 15%；SAP 已在 SapMachine 中默认启用此特性并通过大规模客户验证。禁用开关 `-XX:-UseCompactObjectHeaders` 将在未来版本弃用并移除。

**JEP 523（G1 成为所有环境默认 GC）**：此前 G1 仅在服务器环境（堆 ≥ 1GB、多核）为默认，小实例/容器环境仍使用 Serial GC。JDK 27 之后 G1 在所有环境统一为默认，这消除了"测试环境正常、生产容器行为不一致"的经典坑，但也意味着极小内存容器（<256MB）的默认 GC 行为会发生变化，运维需关注。

**生产架构影响与指导**

JDK 27 是 GA 前最后一个非 LTS 版本，技术团队应开始基于 JDK 27 EA 构建验证，尤其关注：（1）紧凑对象头对 JNI 互操作和低级内存操作库（如 Unsafe）的潜在影响；（2）G1 默认化后容器环境 JVM 参数配置需要复盘；（3）Final 字段反射变更（JEP 500）带来的运行时警告——JDK 26 已默认开启 warn 模式，JDK 27 将向 deny 方向迈进，Google Gson 等主流库已提 issue 跟进，需排查自身代码中的 `Field.setAccessible(true)` 使用。

---

### 4. Project Leyden JEP 516 AOT 对象缓存：41% 启动提速的生产化验证 `[GA 正式版]`

**事件全景**

JDK 26（2026年3月17日 GA）正式落地 JEP 516（Ahead-of-Time Object Caching with Any GC），这是 Project Leyden 在 JDK 主线中的关键交付物。inside.java 于6月9日发布了《Performance Improvements in JDK 26》深度文章，系统性梳理了已进入生产的性能增益数据，为 Leyden 路线图提供了里程碑式的性能验证报告。Spring PetClinic 演示应用在生产运行时启动速度提升 41%，背后是缓存使能了约 21,000 个类直接以"已加载已链接"状态出现，绕过了完整的类加载与字节码验证流程。

**底层机制与设计哲学解析**

JEP 516 的核心突破在于打破了 AOT 缓存与 GC 选择之间的耦合依赖。前作 JEP 483（JDK 24）的 AOT Class Loading & Linking Cache 仅支持 G1/Serial GC，原因是缓存的对象图需要 GC 可知地址来维护引用完整性。JEP 516 引入 **GC 无关格式（GC-independent format）**，并采用**并发对象预热**策略：在应用启动阶段，缓存对象的加载与应用主逻辑并行执行，避免了串行加载对启动延迟的叠加影响。这使得 ZGC 用户（以往为保持超低延迟而无法使用 AOT 缓存）首次能同时享有毫秒级 GC 停顿与亚秒级启动。

JDK 26 起还附带基线 AOT 缓存（覆盖 JDK 核心类库），即便应用跳过自定义训练运行，也能获得小幅默认提升。

**生产架构影响与指导**

对于追求快速扩缩容的云原生 Java 服务，启用 Leyden AOT 缓存的操作路径为：执行一次"训练运行"（`-XX:AOTMode=record`），生成缓存文件，生产容器镜像使用 `-XX:AOTMode=replay` 挂载缓存。关键点：缓存文件与构建产物绑定，需纳入镜像构建流水线而非运行时动态生成。结合 JEP 483（JDK 24）+ JEP 516（JDK 26）的组合效果，Leyden 正逐步将"热身即代价"这一 JVM 长期原罪缩减至与 GraalVM Native Image 相近的数量级，同时保留了动态类加载与完整 JVM 语义的优势。

---

### 5. Project Valhalla JEP 401 EA3：值类型上路，Java 内存模型历史性重构 `[JEP 重大进展]`

**事件全景**

Project Valhalla 于 2026 年 3 月发布了 JEP 401（Value Classes and Objects）的第三轮早期访问构建（EA Build 27-jep401ea3+1-1），目前状态为 Submitted，预期在 JDK 27 或 JDK 28 以 Preview 形式进入主线。这是 Java 基础内存模型十余年来最根本性的变革提案：允许开发者声明无身份标识（no identity）的值类型，JVM 可以将其字段直接"打平"（flatten）内联存储于数组和包含对象中，彻底消除堆分配开销。

**底层机制与设计哲学解析**

值类（Value Class）通过关键字 `value` 修饰（上下文关键字，向后兼容），约束条件为：不可变（所有字段 final）、禁止同步（`synchronized` 非法）、不能持有可变身份状态。JVM 利用无身份约束可以：

- **内联存储**：`ValueType[]` 数组在堆中以连续字段布局存储，而非指针数组，缓存局部性（cache locality）显著改善，与 C# 的 struct 数组布局对齐。
- **栈逃逸分析增强**：JIT 编译器可以更激进地将值对象分配在栈帧上，无需触发 GC。
- **等价性语义**：`==` 比较值内容而非引用，消除了 `Point p1 = new Point(1,1); Point p2 = new Point(1,1); p1 == p2` 返回 `false` 的长年语义陷阱。

对于 Protobuf 消息、坐标/向量、货币金额等高频创建的小对象场景，内存节省幅度可达 50-80%（消除对象头 + 指针追踪开销）。

**生产架构影响与指导**

Valhalla 的成熟将使 Vector API（目前因 JEP 401 阻塞而长期处于孵化状态）最终完成正式化，是高性能数值计算库（如科学计算、ML 推理）在纯 JVM 上实现 SIMD 加速的关键解锁条件。团队现阶段行动：下载 EA 构建，在金融业务中高频使用的小型不可变领域对象（如 `Money`、`TradeId`）上做原型实验，提前感知语义约束；同时关注序列化框架对值类型的适配进展（Jackson、Kryo 均有相关 issue 跟踪）。

---

## 🟡 Tier 2：重要迭代与应用生态

---

### 1. Spring Cloud 2025.0.3 / 2025.1.2 双轨发布 `[GA 正式版]`

**核心增量**：2026年6月11日，Spring Cloud Northfields（2025.0.x）与 Oakwood（2025.1.x）两条维护线同日发布补丁版。Oakwood 轨道对应 Spring Boot 4.x，强化了与 Spring Framework 7 模块化体系的对齐，ServiceDiscovery、LoadBalancer、Gateway 组件在虚拟线程（Project Loom）环境下的线程安全性修复是本次的核心变化。

**核心工程思想**：Spring Cloud Gateway 在虚拟线程混合模式下存在过 `ThreadLocal` 传播失效问题，本次通过 `ScopedValue` 迁移部分关键上下文（与 JDK 25 GA 的 JEP 506 Scoped Values 对齐）来修复，是 Loom 生产化过程中"隐性坑"的典型案例。

**落地行动指南**：两条版本线均为强烈推荐升级的安全补丁级别。Northfields 用于 Spring Boot 3.x 生产线，Oakwood 用于 Spring Boot 4.x 新项目。需特别关注 Gateway 路由过滤器中自定义 `ThreadLocal` 的用法，审查是否需要迁移至 `ScopedValue`。

---

### 2. Spring Modulith 2.1 GA：模块化架构治理的新高度 `[GA 正式版]`

**核心增量**：2026年6月11日，Spring Modulith 2.1 GA 发布（同期维护 2.0.7 与 1.4.12）。2.1 版本显著强化了模块间事件流（Application Module Events）的可观测性，Actuator 端点现在可以实时展示跨模块事件的传播路径和处理状态，Event Externalization（将内部事件推送至 Kafka/RabbitMQ）的配置也得到简化。

**核心工程思想**：Spring Modulith 的核心价值在于在 Monorepo/Modular Monolith 场景中提供"软边界"——用测试断言和架构规则替代微服务的网络隔离，实现低成本架构守护。2.1 对 GraalVM Native Image 的 AOT Hints 完善使模块化单体的原生镜像构建更加可靠。

**落地行动指南**：适合正在规划"模块化单体先行、微服务后拆"策略的团队。注意 2.1 要求 Spring Boot 4.x，若当前在 3.x 上，使用 1.4.x 维护线。

---

### 3. JDK 26 性能改进全景盘点（inside.java 深度文章，2026-06-09）`[性能跃升]`

**核心增量**：inside.java 官方于6月9日发布系统性的 JDK 26 性能改进综述，覆盖四大领域：

- **JDK Libraries**：`LazyConstant`（懒加载常量预览 API）使编译器可对延迟初始化值执行类似 `final` 字段的优化，无需 eager 初始化，对大型框架（如 Spring 的 `ApplicationContext`）内的延迟初始化模式有直接意义。
- **GC 层**：G1 GC 减少了 GC 线程与应用线程之间的同步开销，提升了吞吐量稳定性；ZGC 与 AOT 缓存的兼容性首次实现（JEP 516）。
- **编译器**：C2 JIT 新增对大参数量方法的支持（原先 C2 会直接放弃编译此类方法，回退到解释执行，影响性能），覆盖了部分代码生成工具产出的宽参数方法。
- **运行时**：默认初始堆大小缩小，容器化部署时 JVM 冷启动内存足迹降低，Kubernetes Pod 资源请求可以更精准。

**落地行动指南**：已在 JDK 26 上运行的生产服务建议开启 JFR 做一轮基线性能采样，对比前述改进点，识别可受益的热路径。

---

### 4. JEP 534 紧凑对象头默认化 & JEP 536 JFR 数据脱敏：JDK 27 的两把运维利器 `[JEP 重大进展]`

**核心增量**：

**JEP 534（Compact Object Headers by Default）**：从"可选启用"到"默认开启"，Amazon 和 SAP 的大规模生产验证数据（堆节省 22%、CPU 减少 8%）给予了社区充分信心。这是不改一行业务代码、仅升级 JDK 版本即可获得的最大"免费性能红包"之一。

**JEP 536（JFR In-Process Data Redaction）**：JDK Flight Recorder 现在可以在事件数据离开进程之前，对命令行参数、环境变量和系统属性中的敏感信息进行自动脱敏（Redact）。这解决了企业合规场景（金融、医疗）中 JFR 录制文件可能泄露密钥/密码等敏感配置的长期隐患。

**落地行动指南**：JEP 536 需在 JFR 配置文件中显式声明 redaction 规则。建议安全团队在 JDK 27 发布后制定统一的 JFR 脱敏策略模板，纳入企业 JVM 运维基线规范。

---

### 5. Jakarta EE 12 里程碑进展：Jakarta Query 统一 & Agentic AI 规范立项 `[Preview 预览特性]`

**核心增量**：Jakarta EE 12 计划于2026年7月 GA，当前 Milestone 2 已完成交付。最重要的两个增量：（1）**Jakarta Query** 规范——统一了 Persistence（JPA JPQL）、Data（Jakarta Data）、NoSQL 三个子生态的查询语言表达，消除跨存储查询的方言碎片化；（2）**Jakarta Agentic AI** 规范正式通过立项评审，将为 Jakarta EE 运行时提供标准化的 AI 代理构建、部署和运维 API，与 Spring AI 2.0 的 MCP 路线形成竞合。此外，Jakarta Faces 5.0、Jakarta Connectors 3.0 均进入 Milestone 2。

**核心工程思想**：Jakarta EE 12 将平台基线对齐 Java SE 21（API 源码级），同时支持 Java SE 25 运行时，体现了企业 Java 对 LTS 稳定性的优先承诺。

**落地行动指南**：Hibernate ORM 7.3.0.Final 已发布，是 Jakarta EE 12 的首选 JPA 实现，新增 `@NaturalIdClass` 注解和 `KeyType` 枚举支持多键 `find()` 操作，建议 ORM 密集型团队关注。

---

### 6. Quarkus 3.24 Dev Assistant：AI 驱动的开发时调试体验变革 `[性能跃升]`

**核心增量**：Quarkus 3.24 正式引入 Dev Assistant 功能，在开发模式（Dev UI）中嵌入 AI 助手，可在应用热运行状态下实时分析异常堆栈、生成单元测试骨架、自动补全 TODO 标注代码段，并理解当前项目上下文（CDI Bean 图、REST 端点列表、配置属性）。此外，Quarkus 3.26 对 Dev UI 的 HQL Console 完成重新设计，增加语法高亮、自动补全、查询历史和 Hibernate Assistant。

**核心工程思想**：Quarkus 生态一贯将"开发时体验"与"生产运行时效率"并重，Dev Assistant 是在不牺牲原生镜像构建能力的前提下，将 AI 辅助开发直接内嵌进框架 Dev Loop 的首次系统性尝试，区别于外部 IDE 插件方案，它对 Quarkus 特有的 CDI/Reactive 上下文有更深的语义理解。

**落地行动指南**：Dev Assistant 需在 Dev UI 中手动启用并配置 LLM 后端（支持 Ollama 本地模型及多种云端 API），适合希望在不引入外部 AI 工具链的前提下提升 Quarkus 项目开发效率的团队。

---

### 7. GraalVM 加速发布节奏：月度特性版本轨道启动 `[性能跃升]`

**核心增量**：GraalVM 团队于5月发布博文《Accelerating the GraalVM Release Train》，宣布正式建立月度特性版本发布节奏（25.1+ 轨道），在季度 CPU 安全版本之外增加月度功能增量交付。当前 GraalVM 25.1.3 预计于6月25日发布（对应第三个 CPU 级别）。技术亮点包括 GraalPy、GraalJS、GraalWasm 的新字节码解释器，在 JIT 编译启动之前即可显著降低 warmup 内存和时间，并以 Spring Boot 微服务从 JVM 4秒启动缩至原生镜像 100ms 以内、内存占用降低 75% 的基准数据作为旗舰指标。

**落地行动指南**：新发布节奏对工具链集成（Maven/Gradle GraalVM 插件）的版本追踪提出了更高要求，建议将 GraalVM 版本固定为 BOM 管理对象，避免在 CI 中隐式拉取最新构建。

---

## 🟢 Tier 3：行业风向与速递

- **JDK 28 专家组正式成立**：InfoQ 6月1日报道，JDK 28 Expert Group 已完成组建，计划于 2027 年 3 月发布，LTS 版本 JDK 29（2027年9月）的特性候选期实质上已开启。

- **JEP 533 结构化并发第7次预览（JDK 27）**：Structured Concurrency API 持续在预览中稳定，含更好的作用域误用错误提示和虚拟线程调度器集成优化，社区预期在 JDK 27 或 28 最终确定。

- **JEP 527 后量子混合密钥交换（TLS 1.3）**：JDK 27 将内置基于 NIST 标准的后量子算法（ML-KEM）与传统 X25519 的混合密钥交换，无需第三方安全库，合规要求较高的金融和政府系统值得优先关注。

- **Spring Boot 4.0.7 补丁发布（2026-06-12）**：修复了 4.0.x 生产线若干回归 Bug，包含 Spring Framework 7.0.8、Spring Kafka 4.0.5，4.0.x 线用户建议立即升级。

- **Spring Shell 4.0.3 & 3.4.3 发布（2026-06-11）**：命令行工具框架双版本线同步维护，4.x 适配 Spring Boot 4，3.4.x 适配 Spring Boot 3。

- **Spring gRPC 1.1.0 发布（2026-06-10）**：Spring 生态中 gRPC 的官方集成组件升级，强化与 Spring Framework 7 可观测性（Micrometer + OTel）的原生整合。

- **JEP 538 PEM Encodings 第3次预览**：因最后时刻收到重大社区反馈，从 JDK 27 最终化退回第3次预览，显示 OpenJDK 在密码学 API 设计上的审慎态度；提案提供了 `PEMEncoder`/`PEMDecoder` 用于处理密钥、证书及 CRL 的 PEM 格式互转。

- **JEP 532 原始类型模式匹配第5次预览**：`instanceof int`、`switch` on primitive 特性持续迭代，在四轮预览后本次无实质变化，等待 Valhalla 值类型的解锁而稳定。

- **JEP 500 "Final 即 Final" 推进**：JDK 26 已默认对非法的 final 字段反射变更发出运行时警告（warn 模式），JDK 27 将向 deny 方向演进。Google Gson、多个 Apache 项目已开 issue 修复，生产团队需排查反射操作。

- **Infinispan 点版本更新**：作为 Java 主流分布式缓存/数据网格，本周发布维护版本，增强了与 Jakarta EE 12 和 Hibernate ORM 7 的兼容性声明。

- **Kotlin 点版本发布**：Kotlin 作为 JVM 主流替代语言持续维护节奏稳定，K2 编译器在本次版本中的性能改进包括增量编译速度优化，对 Gradle 多模块项目构建时间有实测收益。

- **GlassFish Arquillian Connectors Suite**：GlassFish 发布用于 Jakarta EE TCK 的 Arquillian Connector 套件，为 Jakarta EE 12 的合规性测试基础设施提供了重要支持，对应用服务器厂商（如 Payara、Open Liberty）的 EE12 认证流程有直接意义。

- **Open Liberty June 2026 Beta**：IBM 的企业 Java 运行时发布6月 Beta 版，重点是对 Jakarta EE 12 Milestone 2 规范的早期适配，以及 MicroProfile 7.0 的集成验证。

- **OpenJDK 宣布禁止 AI 生成代码贡献政策**：JVM Weekly 第171期披露，OpenJDK 社区正式形成不接受 AI 生成代码提交的政策共识，理由是代码归属权、版权溯源和质量审查的复杂性。这是开源 JVM 生态对 Copilot 等工具的一次明确边界设定，预计引发其他 Apache 项目的跟随效仿。

- **Spring AI vs LangChain4j 生态分化明朗化**：LangChain4j 当前版本 1.10.x，以每月一版的节奏持续迭代，在多步骤 Agent、提供商覆盖广度上领先；Spring AI 2.0 则以框架集成深度、null safety 和 Spring 体系一致性取胜。选型建议：Spring 团队优先考虑 Spring AI 2.0，需要灵活 Agent 架构或广泛 LLM 提供商切换能力的团队选择 LangChain4j。

- **Gradle 9.5.1 稳定 / 9.6.0 RC1 预览**（2026-05-12/05-28）：Gradle 9.x 在 Kotlin DSL 体验、构建缓存可靠性、JDK 26 工具链支持上持续完善；Gradle 10 路线图已发布，预计年内落地，引入更激进的构建隔离模型。

- **Maven 4.x RC 进行中**：Maven 4 仍处于 Release Candidate 阶段，带来 POM 4.1 格式（改进父 POM 解析）与依赖管理增强，部分大型企业已在非生产环境试用，正式 GA 发布预计下半年。

---

*情报截止时间：2026-06-14 | 信息来源：inside.java、InfoQ Java 周报、Spring 官方博客、OpenJDK JEP 追踪、JVM Weekly、GraalVM 团队博客、Jakarta EE 官方规范站*
