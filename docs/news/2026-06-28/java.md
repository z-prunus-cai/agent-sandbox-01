# Java 平台与企业级框架生态情报简报
**日期：2026-06-28 | 覆盖时间窗口：过去 48 小时重点事件及近期重大进展**

---

## 🔴 Tier 1：核心突破与范式转移

### 1. Project Valhalla 里程碑：JEP 401 值类型主线集成进入倒计时，OpenJDK 史上最大 PR 落地 `[JEP 重大进展]` `[范式转移]`

**事件全景**

Project Valhalla 历经十余年演进，JEP 401（Value Classes and Objects）已确认以预览特性身份集成至 JDK 28（GA 目标：2027 年 3 月）。其主线集成 Pull Request 横跨 **1,816 个文件、超过 197,000 行代码变更**——这是 OpenJDK 有史以来规模最大的单次合并，预计于 2026 年 7 月落入 JDK 28 主线。JDK 28 Early-Access Build 已对外可用，Valhalla 团队同步在 valhalla-dev 邮件列表探讨**类型类（type classes）原型**，标志着 Valhalla 不再局限于内存布局优化，而是向更宏观的类型系统扩展迈进。

**底层机制与设计哲学解析**

JEP 401 的核心是引入 `value` 修饰符：被标注的类实例不具备对象身份（identity），JVM 可对其执行**扁平化（flattening）与标量化（scalarization）**。当值类实例作为字段嵌入宿主对象时，其数据可直接内联至宿主的内存布局，消除一层指针间接访问；作为方法参数时，可通过寄存器传递而非堆分配。现有 JDK 中所有被标注为"基于值"的类（`Integer`、`Long`、`Double` 等所有原始包装类）将迁移为值类，实现"廉价装箱"（cheap boxing）。需明确：本次预览**不包含**空值受限类型（null-restricted types）与完整特化泛型——这些属于后续 JEP 的范畴。

对象身份语义的历史代价是巨大的：以 `List<Integer>` 为例，数百万条记录的整数集合实质上是指针数组，每个 `Integer` 在堆上独立占据 ≥16 字节且高度缓存不友好，GC 需持续追踪每一个引用。值类使得 `List<IntPoint>` 等数值密集型集合可存储为紧凑连续内存块，直接对应 CPU 缓存行，内存密度与 C/C++ 结构体数组媲美。

**生产架构影响与指导**

值类对 Spring/Hibernate 等框架的深层冲击在于**代理机制失效**：值类不可被继承，CGLIB 子类代理与 JDK 动态代理（依赖接口）均对无接口的值类无效，框架须切换至 AOT 代码生成路径。数值密集型应用（量化金融价格序列、时序数据库、游戏引擎物理向量）可期待显著的堆空间与 GC 压力降低。团队行动指南：（1）在 JDK 28 EA Builds 上试验现有 Value-Based 类向 `value class` 迁移，量化内存收益；（2）提前评估框架代理策略兼容性，制定接口代理或 AOT 替换方案；（3）预览特性不跨 JDK 版本保证二进制兼容，生产部署需等待 GA 标准化。

---

### 2. Spring 生态大收口：Boot 4.1 + Framework 7 + Security 7.1 三线 GA，3.x 时代终结 `[GA 正式版]` `[范式转移]`

**事件全景**

2026 年 6 月 9-12 日，Spring 生态经历了一次集中式里程碑发布：**Spring Boot 4.1.0**（June 10）、**Spring Security 7.1.0**（June 9）、**Spring Data 2026.0.0**（May 15）全部进入 GA，Spring Boot 3.5.16 于 **June 25** 发布后，**3.x 整条 OSS 线将于 2026 年 6 月 30 日到期**——这一日期形成了当前 Java 生态最大的单一迁移压力节点。Spring Framework 6.2.x 同步 EOL，意味着所有基于 Spring Framework 6.x 的团队面临硬性迁移窗口。

**底层机制与设计哲学解析**

Spring Framework 7（Boot 4.x 的基础）确立了三大底层转变：其一，**Jakarta EE 11 为强制基线**（Servlet 6.1、JPA 3.2），`javax.*` 包全面切换为 `jakarta.*`；其二，**Undertow 被完全移除**——因 Undertow 不支持 Servlet 6.1，Spring Framework 7 彻底删除所有 `UndertowWebServer*` 类，存量 Undertow 用户必须迁移至 Tomcat、Netty 或 Jetty；其三，**API 版本化原生支持**——服务端路由、`RestClient`、`WebClient`、`WebTestClient`、HTTP Interface Client 全部获得一流的 API 版本化能力，消除此前手动路径前缀或自定义拦截器的繁琐方案。

Spring Boot 4.1.0 新引入两项生产安全特性：**gRPC 自动配置**（`spring-boot-grpc-server/client/test` 三模块，`@GrpcAdvice` 集中异常处理，Micrometer 自动接入）与 **SSRF 主动防护**（`InetAddressFilter` 可统一阻断 `WebClient`/`RestClient` 对内网地址的出站请求，切断云环境下访问 Instance Metadata Service 的 SSRF 路径）。Spring Security 7.1.0 则在同批次修复了 **7 项 CVE**（涵盖 SAML 无界解压、X.509 用户冒充、开放重定向等高危漏洞），并首次提供 **WebAuthn/Passkeys 一流支持**（`@EnableWebAuthn`）与 DPoP Token Binding。

**生产架构影响与指导**

Spring Boot 4.x 将 Java 最低要求提升至 **21**，Jackson 3.x 作为默认序列化框架（2.x 已废弃，package 从 `com.fasterxml` 改为 `tools.jackson`），`RestTemplate` 正式进入废弃状态。迁移关键路径：（1）使用 OpenRewrite `UpgradeSpringBoot_4_0` recipe 自动化重写 `javax.*` → `jakarta.*` 及 `RestTemplate` → `RestClient` 调用；（2）核查所有 Undertow 依赖并切换 servlet 容器；（3）部署 `InetAddressFilter` 并压测合法的对外 HTTP 调用不受误拦截；（4）Spring Security CVE 修复应优先应用，注意 SAML 配置与证书链的兼容性验证。

---

### 3. Spring AI 2.0.0 GA：Java 进入 Agentic AI 纪元，MCP 2.0 成企业 Agent 标准接口 `[GA 正式版]` `[范式转移]`

**事件全景**

2026 年 6 月 12 日，**Spring AI 2.0.0 正式 GA**，成为 Java 企业级 AI 集成从"调用 LLM"跨越至"编排 AI Agent"的历史节点。此版本**强制依赖 Spring Boot 4.x（Spring Framework 7.0）与 Java 21+**，与 Spring Boot 3.x 完全不兼容——并行维护的 1.0.9/1.1.8 仍为 Boot 3.x 用户服务。在此之前，Spring AI 虽已支持 20+ 模型提供商，但 Agent 编排能力碎片化；2.0.0 通过架构性重组，将 Tool Calling、RAG、记忆管理、Multi-Agent 编排统一至一套可组合的 Advisor 体系。

**底层机制与设计哲学解析**

2.0.0 的核心架构重组体现在两个维度：其一，**Tool Calling 外置化**——工具调用执行循环从各 `ChatModel` 实现中剥离，统一由 `ChatClient` + `ToolCallingAdvisor` 组合承担，使得工具调用成为可组合、可测试、可替换的一等公民；`ToolSearchToolCallingAdvisor` 实现动态工具发现（progressive tool disclosure），在工具库规模达数百个时，无需一次性加载全部工具描述至 Context 窗口。其二，**MCP Java SDK 2.0.0 合规升级**——遵循 MCP spec 2025-11-25，Streamable HTTP 成为默认传输协议（SSE 标记为废弃），注解驱动模型（`@McpTool`、`@McpResource`、`@McpPrompt`）大幅降低 MCP 服务暴露成本，WebMVC/WebFlux 传输层从 MCP SDK 迁入 Spring AI，使 Spring Boot 应用可同时作为 MCP Server 和 Client。

此外，`StructuredOutputValidationAdvisor` 提供自动 JSON 校验与重试闭环；Agentic Patterns 模块化设计（Recursive Advisors、Subagent、A2A/ACP Protocols、LLM-as-Judge、MemGPT 记忆系统）将企业级 Agent 编排抽象为可复用的架构构件。支持的模型提供商包括：OpenAI（via 官方 openai-java SDK）、Anthropic、Amazon Bedrock、Google GenAI、Azure OpenAI、Mistral AI、DeepSeek、Ollama、OCI GenAI 等。

**生产架构影响与指导**

Spring AI 2.0.0 确立了 Java 生态的 MCP 标准实现地位（Spring 团队已将 MCP Java SDK 贡献给 Anthropic 作为官方实现）。对正在使用 LangChain4j 的团队：Boot 4.x 环境下可考虑迁移至 Spring AI，尤其是已深度依赖 Spring Boot 自动配置体系的项目；LangChain4j 的工具生态仍更广，迁移需评估工具覆盖差距。安全层面需重点关注：MCP Tool Calling 的权限边界管理（避免 Agent 调用超范围工具），以及 Prompt Injection 防御（对用户输入触发的 Tool 调用场景尤需审计）。Jackson 3 引入（`tools.jackson.*`）是 2.0.0 的重要依赖变化，需确认下游序列化逻辑兼容性。

---

### 4. Apache Kafka 4.3：ZooKeeper 时代落幕，KRaft 成唯一架构，生产基础设施里程碑 `[GA 正式版]` `[范式转移]`

**事件全景**

2026 年 5 月 22 日，**Apache Kafka 4.3.0** 正式发布（已有 4.3.1 补丁），完成了 Kafka 历史上最重要的架构清洁：**ZooKeeper 彻底从代码库中移除**，KRaft（Kafka Raft 元数据模式）成为唯一的集群元数据架构。这结束了 2022 年 Kafka 3.x 开始的 ZooKeeper 废弃迁移，意味着所有生产集群必须完成 KRaft 迁移，不再有回退路径。4.3.0 包含 25 项 KIP 改进、超过 600 次提交。

**底层机制与设计哲学解析**

ZooKeeper 在 Kafka 中承担集群元数据（Controller 选举、Topic 配置、消费者组协调）的分布式存储角色，但其独立进程模型带来了运维复杂度（单独的 ZooKeeper 集群管理、Java 堆调优、Session 超时处理）和性能瓶颈（Controller Failover 时间随 Partition 数量线性增长）。KRaft 将元数据日志内化为 Kafka 自身的 Raft 协议共识日志，消除外部依赖，Controller Failover 时间从分钟级降至秒级，并支持单集群千万级 Partition 规模。

4.3.0 引入的 **KIP-1066（Broker Cordoning）**是运维体验的重要跃升：通过 `cordoned.log.dirs` 标记磁盘目录为"已隔离"，新 Partition 不再被分配至该目录，使计划性磁盘替换可在不停机的情况下从容执行。**KIP-1274** 正式为 Classic Consumer Rebalance Protocol 引入废弃日志警告，新协议（KIP-848 Share Groups）已在 4.2.0 升至 GA，Teams 应规划切换窗口。Tiered Storage 改进使新副本可直接从远端存储拉取偏移量启动，无需重放完整本地日志。

**生产架构影响与指导**

所有仍在运行 ZooKeeper 模式的 Kafka 集群（Kafka 3.x 仍支持）面临强制迁移压力：Kafka 4.x 不提供 ZooKeeper 运行时，升级至 4.x 意味着迁移路径不可绕过。迁移步骤：（1）使用 `kafka-storage.sh` 将现有集群元数据从 ZooKeeper 迁移至 KRaft；（2）验证 Consumer Group 协调行为，评估切换至新 Consumer Rebalance Protocol 的时间窗口；（3）Java 客户端最低要求为 JDK 11，建议在 JDK 21 上运行以获得虚拟线程受益（Kafka Streams 事件循环解锁）；（4）关注 Kafka Share Groups（队列语义）——这是 Kafka 向消息队列场景扩展的重要特性，适合替换部分 RabbitMQ/ActiveMQ 的点对点消费模式。

---

## 🟡 Tier 2：重要迭代与应用生态

### 1. Kotlin 2.4.0 GA：上下文参数转正、Java 26 字节码支持、Kotlin/Native CMS GC 默认启用 `[GA 正式版]` `[性能跃升]`

**核心增量**：2026 年 6 月 3 日发布的 **Kotlin 2.4.0** 将**上下文参数（Context Parameters）**从实验性晋升为 Stable，提供比隐式接收者（implicit receiver）更可组合的依赖注入能力；**显式后备字段（Explicit Backing Fields）**同步转正；`kotlin.uuid.Uuid` API 进入 Stable。JVM 侧新增 **Java 26 字节码生成**支持，Kotlin/Native 启用 CMS GC 为默认（内存效率与停顿改善）；Kotlin/Wasm 开启增量编译并支持 WebAssembly Component Model。同期，Kotlin 2.3.0 已完整支持 Java 25，Kotlin 2.1.20（3 月 27 日）在 Gradle 构建中默认启用 K2 kapt 插件，并支持 Lombok `@SuperBuilder` 注解。

**核心工程思想**：上下文参数解决了基于接收者扩展函数在多参数组合时的歧义问题，为 Kotlin 协程作用域、事务边界、测试环境配置等场景提供了更优雅的能力透传机制，对于 Kotlin for Spring/Quarkus 开发者意义重大。编译器诊断改为通过 Gradle Problems API 路由，使问题与构建扫描深度集成。

**落地行动指南**：升级 Kotlin 2.4.0 需注意 Kotlin/Native CMS GC 的默认切换可能影响内存密集型 KN 应用行为；Kotlin metadata 注解默认启用可能影响依赖元数据检查的字节码工具。与 Spring Boot 4.1（Kotlin 2.3.21 baseline）配合时，确认 KSP 版本与 K2 插件的对应关系。

---

### 2. Micronaut 5.0 发布：以 Java 25 为基线，IoC 容器底层重构，Jackson 3 全面迁入 `[GA 正式版]` `[性能跃升]`

**核心增量**：**Micronaut 5.0.0 GA**（May 20, 2026）是 Micronaut 历史上基线跨度最大的主版本——**强制要求 Java 25**，彻底抛弃 Java 17/21。IoC 容器经历底层重构：Bean 解析、限定符处理与注解处理均已重写，减少运行时元数据扫描量并提升容器初始化的可预测性。Jackson 3 全面迁入（`tools.jackson.*` 包），JSpecify `@NullMarked` 应用至整个代码库，Groovy 5 与 Kotlin 2.3（KSP 2.3.21）同步支持。配置层引入 `PropertySourceImporter` SPI（支持文件、类路径、环境变量、Kubernetes ConfigMaps/Secrets 等），并内置 JSON Schema 配置校验器集成至 Maven/Gradle 插件。当前最新为 **5.0.2**（June 3，已修复两项安全 GHSA），Oracle 同步发布 **GDK for Micronaut 5**（June 23）并宣布 Micronaut 4.10 延长支持至 2027 年 7 月。

**核心工程思想**：Micronaut 以编译时 AOP 和编译时依赖注入著称，彻底规避运行时反射是其核心竞争力。5.0 的 IoC 重构进一步强化了这一优势——更少的运行时决策、更小的 AOT Native Image footprint。程序化重试/熔断 API（对比原有纯注解驱动）增强了动态策略切换能力。

**落地行动指南**：Micronaut 4.x 仍在 Java 17 上维护，5.0 的 Java 25 硬要求意味着需同步升级 JDK；Jackson 3 的 `tools.jackson.*` 包名变更是最大兼容性断点，依赖 Jackson 扩展模块的第三方库需确认其 Jackson 3 兼容版本可用。

---

### 3. Hibernate ORM 7.4 + 8.0 Beta1：`@Audited` 内建、JPA 4.0 实现、Spanner 原生集成 `[GA 正式版]` `[JEP 重大进展]`

**核心增量**：**Hibernate ORM 7.4.2.Final**（June 21）是当前最活跃的生产维护线，最重要的新能力是将 **`@Temporal`（时态数据）与 `@Audited`（审计日志）内建进 ORM 核心**——这意味着企业中普遍需要的"数据操作完整审计历史"功能无需再引入 Hibernate Envers 独立依赖，且与现有 Envers 审计表结构后向兼容。**Google Cloud Spanner** 通过 `SpannerPostgreSQLDialect` 完整集成至 ORM 核心，正式进入 Hibernate 官方支持数据库列表。分页查询中集合 Fetch Join 的数量限制现通过 SQL 子查询在数据库层处理，彻底消除了 `HibernateJpaDialect` 在内存中截断结果集的历史顽疾。**StateManagement SPI**（孵化中）提供实体状态追踪的底层扩展点。

**Hibernate ORM 8.0.0.Beta1**（June 16）实现 **Jakarta Persistence 4.0**（针对 Jakarta EE 12），引入 `EntityAgent` 契约、统一的 `Timeout` 类——是 Hibernate 为 Jakarta EE 12 认证准备的基础。

**落地行动指南**：从 7.2/7.3 升至 7.4 风险可控（同 7.x 系列，Apache License 统一），主要关注点是 PostgreSQL 最低版本要求升至 14；`@Audited` 内建对于长期依赖 Envers 的项目是重大利好，但迁移前需确认新内建实现与现有审计表结构完全兼容。Hibernate 8.0 生产就绪需等待 JakartaEE 12 GA（Q4 2026 预期）。

---

### 4. Spring Security 7 CVE 批量修复 + AI 生成漏洞报告洪峰警示 `[安全]` `[行业警示]`

**核心增量**：Spring Security **7.1.0 GA**（June 9）在功能层面引入 WebAuthn/Passkeys 一流支持（`@EnableWebAuthn`）、DPoP（OAuth2 Token Binding）、`AllRequiredFactorsAuthorizationManager.anyOf()` 多因子策略，以及 `MessageExpressionAuthorizationManager`（Spring Messaging SpEL 鉴权）。同批次，**7.0.6 与 6.5.11** 修复了 7 项高危 CVE：SAML DEFLATE 无界解压（CVE-2026-40988）、SAML2 原生反序列化（CVE-2026-40993）、未验证签名即解密 SAML（CVE-2026-41694）、`CookieRequestCache` 开放重定向（CVE-2026-41706）、X.509 用户冒充（CVE-2026-47838）等。

**核心工程思想**：Spring 团队在同期博文中披露，**2026 年 4 月单月收到 482 份 AI 生成的 CVE 报告**，是历史月均 6.5 份的 74 倍。这一洪峰正在重构安全团队的 CVE 分诊流程，业界应关注 AI 辅助安全扫描工具如何区分真实漏洞与 AI 幻觉漏洞报告。

**落地行动指南**：持有 Spring Security 6.5.x 或 7.0.x 的团队应立即升级至修复版本；SAML 配置需优先审查无界解压与未签名解密两类高危路径；WebAuthn 集成可借 Boot 4.1 的 `@EnableWebAuthn` 零样板启用，适合规划 2026 年 Passkeys 上线的安全架构评估。

---

### 5. Gradle 9.6.0 + Maven 4.0 RC：构建工具链双线并进 `[性能跃升]` `[稳定维护]`

**核心增量**：**Gradle 9.6.0**（June 20）精准修复了 Configuration Cache 在 CI 场景下的误失效问题——当项目属性通过系统属性（`org.gradle.project.<n>`）或环境变量（`ORG_GRADLE_PROJECT_*`）传入时，仅当配置阶段实际使用该属性时才失效缓存，消除了大规模多模块构建中因 CI 注入环境变量导致缓存命中率崩溃的顽疾。Gradle 9.5.0（April 28）已引入失败任务的注册来源追踪（定位到具体 build script 或插件）、Settings 插件的类型安全 Kotlin Accessor。**Maven 3.9.16**（May 13）修复了 3.9.15 的回归问题，是当前稳定线推荐版本。**Maven 4.0.0-rc-5**（Pre-GA）引入树形多模块并行生命周期、Consumer POM 生成（发布物去除构建时信息）、Java 17 运行时要求，GA 日期未承诺。

**落地行动指南**：使用 Gradle 的团队应升级至 9.6.0 并在 CI 中核查 Configuration Cache 命中率改善；Maven 4 的 Java 17 运行时要求已确定，规划 CI 构建环境升级时应纳入排期。

---

### 6. Resilience4j + JUnit 6.1 + Testcontainers 2：测试与韧性层虚拟线程适配完成 `[性能跃升]` `[稳定维护]`

**核心增量**：**Resilience4j**（March 15）全面将 `synchronized` 替换为 `ReentrantLock`，并引入无锁滑动窗口 `CircuitBreaker` 度量——**彻底消除了 Project Loom 虚拟线程的 synchronized 钉住（pinning）问题**（Issue #2232/2241），使 Resilience4j 熔断与限流可安全用于虚拟线程上下文。Resilience4j 3 提供内部 Scheduler 切换至虚拟线程的能力（需 Java 21+）。**JUnit 6.1.0**（May 19）引入内建的 `@DefaultLocale`/`@DefaultTimeZone` 扩展、系统属性清空/恢复扩展、可配置 `@TempDir` 删除策略、新并行测试执行器实现及超大测试套件内存清理模式（实验性）。**Testcontainers 2.0.5**（April 20）完成 2.x 的 JUnit 4 支持移除，所有模块 Artifact ID 添加 `testcontainers-` 前缀，已支持 Docker Engine 29+。

**落地行动指南**：升级 Testcontainers 至 2.x 须修改所有模块依赖坐标（`org.testcontainers:mysql` → `org.testcontainers:testcontainers-mysql`）；Resilience4j 虚拟线程适配是 JDK 25 迁移的配套必要条件，在切换至虚拟线程化 Spring Boot 4.x 应用前应同步升级。

---

### 7. Quarkus 3.37 + Road to Quarkus 4：jLink 模块化镜像、Hibernate 7.4 对齐、11 月 GA 路线图确立 `[性能跃升]` `[Preview 预览特性]`

**核心增量**：**Quarkus 3.37.0**（June 24）新增 `quarkus-jlink` 扩展，可生成仅包含所需 JDK 模块的最小化运行时镜像，大幅压缩部署包体积；Jackson 反射无关序列化器（编译时代码生成，非运行时反射）默认启用，与 GraalVM Native Image 协同效果显著提升；同步对齐 **Hibernate ORM 7.4.0.Final**（含内建审计、SQL 分页修复）。**Road to Quarkus 4**（GA 目标 2026 年 11 月）已确立：Java 21 为最低要求、JPMS 模块化架构、Vert.x 5 + Netty 4.2、HTTP/3、Jakarta EE 11、Jackson 3 全栈切换。此前 3.36.x 引入 Quarkus Signals 扩展与 Semeru AOT 支持（启动速度约 50% 提升），3.35.x 引入 JAR tree-shaking（CLI 体积减少 39.5%）与 PGO native 构建。

**落地行动指南**：当前 Quarkus LTS 为 3.33（支持至 2027 年 3 月），生产部署以此为稳定基线；3.37 新功能（jlink 扩展）适合在非关键服务先行试验；Quarkus 4 迁移需规划 Jakarta EE 11 命名空间与 Vert.x 5 API 的破坏性变更处理，OpenRewrite 工具链将提供迁移 recipe。

---

### 8. Helidon 4.5.0 宣告 JDK 版本对齐策略，框架版本号随 JDK 演进 `[发布策略]` `[稳定维护]`

**核心增量**：**Helidon 4.5.0**（June 19）完成 API 稳定性标注项目——全库系统性应用 `@internal`、`@preview`、`@incubating`、`@stable` 注解，提供明确的 API 承诺层级。新增加固配置加密格式（`${ENC=...}`），HTTP/2 HPACK 强化拒绝畸形整数。更具战略意义的是 **4.4.0（April）确立的版本对齐策略**：从 JDK 27（2026 年 9 月 GA）起，Helidon 版本号将与 JDK 大版本号直接对齐（Helidon 27、Helidon 28……），版本号即标明其最低推荐 JDK。Helidon 已被纳入 Oracle **Java Verified Portfolio（JVP）**，并完成 LangChain4j 1.11.0 + Helidon MCP 1.1 集成（含多具名模型支持）。

**落地行动指南**：Helidon JDK 版本对齐策略使版本选择与 JDK 升级路径深度绑定，团队应将 JDK LTS 升级（JDK 25 → JDK 29）与 Helidon 版本规划联动；Helidon Declarative 模式（Preview 状态）值得关注，其控制反转风格与 Spring Boot 开发体验高度接近，在 Oracle Cloud Native 场景下有一席之地。

---

## 🟢 Tier 3：行业风向与速递

- **GraalVM 25.1.3 CPU 补丁（June 25, 2026）发布**，这是本周最新的 48 小时内动态。GraalVM 明确宣布**不为 JDK 26/27/28 发布版本**，下一个 Feature Release 将对齐 JDK 29 LTS（2028 年）——仅维护基于 JDK 25 LTS 的 25.1.x 系列。这意味着 GraalVM Native Image 用户在整个 JDK 26-28 周期内无法获得新特性，只能在 JDK 25 LTS 轨道上获取安全修复。

- **JDK 27 特性冻结（June 4, 2026）确认九项 JEP 入选**，GA 目标 2026 年 9 月 14 日。G1GC 确认成为所有 JVM 配置（含单核/低内存容器）的统一默认 GC，消除"开发机 G1 vs 生产容器 Serial GC"的行为鸿沟。紧凑对象头（compact object headers，-4 bytes/对象）在 JDK 27 默认启用。

- **JDK 28 主线已切换**，Early-Access Build 活跃发布中。valhalla-dev 邮件列表上，Maurizio Cimadamore 发布了**类型类（type classes）原型的实验性探索帖**，引发社区对 Java 类型系统下一步演进方向的深度讨论。Alex Miller（Clojure 团队）在 loom-dev 提出**"短暂线程（ephemeral threads）"**概念——即 GC 可在虚拟线程完成前将其作为垃圾回收的可能性，目前处于早期探索阶段。

- **JEP 535（Shenandoah 分代模式默认化）进入 Candidate 状态**（2026 年 3 月），提案将 `ShenandoahGCMode=generational` 设为默认并废弃 SATB 非分代模式。分代 Shenandoah 已在 JDK 25 中升至生产就绪，此 JEP 是对其的正式背书。目标 JDK 版本尚未确认。

- **Project Babylon / HAT 首个 JavaOne 案例研究发布**（April 26, inside.java），展示了在 JVM 内通过纯 Java 代码编写 GPU 核函数（OpenCL / CUDA 后端），无需离开 JVM 也无需编写 C wrapper。相关 ONNX 生成式 AI 模型通过 Babylon Code Reflection Bridge 运行的 Demo 于 2025 年 11 月发布。交付时间未定，属于 JDK 28 之后的长远愿景。

- **Netty 5 正式宣告死亡**：最后一个 Alpha 为 2022 年 9 月的 5.0.0.Alpha5，此后无任何开发活动。Netty 主力线为 4.2.x（当前 4.2.15.Final，June 1）。重要安全变更：**TLS 客户端主机名验证默认开启**（此前默认关闭）——升级至 Netty 4.2 时需检查所有 TLS 客户端配置，避免自签证书场景的连接中断。

- **Apache Tomcat 三线同期发布**（June 17-18）：Tomcat 9.0.119（Java EE 8）、10.1.56（Jakarta EE 10）、11.0.23（Jakarta EE 11）。关键修复：HPACK 解码整数溢出修复、Default Servlet ETag 计算从 SHA-1 升级至 **SHA-256**、HTTP/2 `:scheme` 伪首部验证。Eclipse Jetty 9/10/11 已于 2026 年 1 月 1 日全线 EOL，仅 Jetty 12 仍获支持。

- **Vert.x 5.1.3**（June 23，本周最新补丁）：5.1.0 引入完整 **HTTP/3（QUIC）支持**与 Jackson 3 自动检测（classpath 无 Jackson 2 时自动切换 Jackson 3）。Vert.x 4.5.28 仍维护中（June 9 补丁）。

- **Spring Data 2026.0.0** 的类型安全属性路径（`PropertyPath.of(Person::getAddress)`）与 Redis 声明式 Pub/Sub（`@RedisListener`）是开发体验改善的代表性 API；多集合 MongoDB 批量写入（`Insert/Update/Delete` 混合，需 MongoDB 8.0+）为大规模数据迁移场景提供原子性保障。Spring Data 2025.0.13（June 24，本周最新补丁）与 2025.1.6（June 9）持续维护中。

- **Spring Tools 5.2.0 发布**，内嵌 MCP 服务端与 Claude Code 技能插件，Spring 属性引用支持类型安全跳转，与 Spring AI 2.0 MCP 生态形成工具链闭环。

- **Jakarta EE 12 GA 推迟至 Q4 2026**（原定 Q2-Q3）。新增独立规范 Jakarta Query 1.0（JPQL 从 JPA 中提取为独立规范）、Jakarta Persistence 4.0（EntityAgent 契约）、Jakarta Data 1.1。SecurityManager 在 JEP 486（JDK 24）中永久禁用后，Jakarta EE 12 正式从全部组件规范中移除相关 API。

- **Micrometer 1.15.0**（June 8）的里程碑版本开始发布至 Maven Central（此前仅发布至 Spring Milestone Repository），降低了早期采用者的配置负担；gRPC 观测上下文增强（peer name/port 自动记录）使 gRPC 服务与 Spring Boot 4.1 原生 gRPC 自动配置形成可观测性闭环。

- **Spring Security 482 条 AI 生成 CVE 报告事件**引发行业反思：AI 辅助安全扫描工具的误报率远高于人工审计，安全团队需建立 AI-generated CVE 过滤与优先级甄别机制；同时，Spring Security 持续 CVE 数量增加也反映了 Java 生态安全关注度和审计强度的系统性提升。

- **"Beyond Records" 载体类提案**（January 27, inside.java）——数据导向编程（DOP）的第二弧线：Carrier Classes 与 Carrier Interfaces 允许状态不完全声明为组件的类层级数据结构，补全了 Records（单一不可变数据载体）在类继承场景下的表达缺口。当前为设计文档阶段，预计在 Project Amber 框架内跟进 JEP。

- **Oracle Graal Dev Kit（GDK）for Micronaut 5** 于 June 23 发布，为 Micronaut 4.10.x（基于 Java 17/21）提供延长支持至 2027 年 7 月，给尚未准备好迁移至 Java 25 的团队提供了过渡窗口。

- **Apache Kafka 4.2.0（February 2026）回顾**：Kafka 队列（Share Groups）升至 GA、Kafka Streams 服务端再平衡 GA、异常处理器死信队列支持——标志着 Kafka 正式具备替代传统消息队列（RabbitMQ/ActiveMQ）的完整能力集。结合 4.3 的 ZooKeeper 移除，Kafka 已成为真正的统一流与队列平台。

- **`r2dbc-mssql` 驱动更新**（June 22），R2DBC 规范层无新版本（维持 1.0.0.RELEASE）。响应式数据库访问的开发活力集中在 Spring Data R2DBC（upsert 支持、2026.0.0 版本）与具体驱动实现，规范本身已趋于稳定。Project Reactor 的 3.7.x 系列（`reactor-core` 2024.0.x train）已正式结束 OSS 支持，所有存量 3.7.x 用户应升级至 3.8.x（2025.0.x train）。
