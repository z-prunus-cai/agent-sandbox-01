# Java 平台与企业级框架生态情报简报
**日期：2026-06-22 | 覆盖时间窗口：近 48 小时核心动态，辅以近两周关键事件补全**

---

## 🔴 Tier 1：核心突破与范式转移

### 1. Project Valhalla JEP 401 正式进军 JDK 28 主线：十年磨剑，值类型 Preview 终于落地 `[JEP 重大进展]`

**事件全景**

6 月 15 日，The Register 独家披露：Oracle 工程师 Lois Foltan 在 jdk-dev 邮件列表正式确认，JEP 401（Value Classes and Objects）已提名并接受为 JDK 28 的 Preview 特性，代码集成计划于 2026 年 7 月合并主线，GA 版本随 JDK 28 在 2027 年 3 月发布。这一 Pull Request 新增超过 **19.7 万行代码，横跨 1,816 个文件**，是 OpenJDK 历史上规模最大的单次功能集成之一，其他 Committer 被明确要求在此期间避免大规模提交以降低合并风险。

Java 长达 30 年来的核心设计约束是：除了 `int`、`char`、`byte`、`double` 等原始类型外，所有类型皆为引用类型（Reference Types）。引用类型天然携带**对象身份（Object Identity）**，意味着每个实例在堆上拥有独立的内存地址，JVM 必须通过指针间接访问数据，带来了不可忽视的**内存碎片化、缓存局部性差和装箱开销**。以金融、高频交易、游戏物理引擎等场景为例，大量短生命周期的 `Point(x, y)`、`Money(amount, currency)`、`Complex(real, imag)` 等对象，每个都要在堆上分配 16 字节以上的对象头 + 字段数据，GC 压力巨大。

**底层机制与设计哲学解析**

JEP 401 引入 **Value Objects**：一种没有对象身份、仅通过字段值相互区分的类实例。开发者可以使用 `value class` 和 `value record` 声明，JVM 获得保证——同字段值的两个 value 实例在语义上等价，因此可以自由地**标量化（Scalarization）**和**内联展平（Flattening）**：

- **标量化**：value 实例无需在堆上分配，可以直接在栈帧或寄存器中传递，彻底绕过 GC。
- **内联展平**：数组 `ValuePoint[]` 在内存中可以以密集的 `[x₀,y₀,x₁,y₁,...]` 连续布局存储，而非当前的指针数组 `[ptr₀,ptr₁,...]`，CPU 缓存行命中率大幅提升。
- **廉价装箱（Cheaper Boxing）**：JDK 标准库中现有的"值语义基类"（如 `Integer`、`Double`、`Long`）在 Preview 期间迁移为 value class，旧有装箱代价有望大幅降低。

JEP 401 仍处于 Preview 阶段，启用需 `--enable-preview`，设计团队对其在 JDK 29 LTS（2027 年 9 月）退出 Preview 持"乐观但审慎"态度。

**生产架构影响与指导**

1. **量化收益预期**：在高频对象创建场景（如每秒百万级 DTO 实例化），消除堆分配可减少 GC 停顿 30%~60%，与此同时内存带宽消耗因连续布局下降显著。
2. **迁移准备**：无需立即行动，但技术负责人应开始审查系统中哪些"轻量值对象"（坐标、货币、时间戳）是优质的 value class 候选；同时关注 `==` 语义变化——value 对象的 `==` 比较字段值而非地址，迁移现有使用 `==` 进行引用比较的代码时须严格评审。
3. **生态影响**：序列化框架（Jackson、Kryo）、JPA（Hibernate ORM）、反射代码需适配无对象身份的新语义，预计 Preview 期间生态适配工作将迅速展开。

---

### 2. Project Leyden JEP 516 GA：AOT 对象缓存突破 GC 限制，HotSpot 冷启动问题进入终局 `[GA 正式版]` `[范式转移]`

**事件全景**

JDK 26 于今年 3 月正式 GA，其中 JEP 516（Ahead-of-Time Object Caching with Any GC）是 Project Leyden 系列 JEP 的关键一步。Inside.java 于 6 月 9 日发布了全面的 JDK 26 性能提升总结，进一步佐证其对生产环境的实质性冲击。以 Spring PetClinic 为基准，在标准容器化部署环境下，Leyden AOT Cache 带来约 **41% 的启动时间缩短**，并在 P99 延迟上显著改善服务 scale-out 阶段的"冷流量延迟尖刺"问题。

**底层机制与设计哲学解析**

Leyden 的设计哲学是**有意识地将运行时工作前移（Shifting Work Earlier）**，采用三阶段工作流：

1. **Training Run（训练运行）**：用真实或模拟流量跑一次完整应用，JVM 记录类加载顺序、JIT 编译决策、堆对象的初始化形态（如 Spring `ApplicationContext`）。
2. **Cache Construction（缓存构建）**：将训练结果序列化为 AOT Cache 文件，内容涵盖：已加载类元数据、方法字节码 Profile、C2 编译产出的机器码、以及 JEP 515 引入的预初始化堆对象快照。
3. **Production Run（生产运行）**：JVM 直接从 AOT Cache 拉取已编译机器码与已初始化堆对象，跳过类加载解析、解释执行和 JIT 热身阶段。

JEP 516 的关键突破在于引入了 **GC 无关的可流式对象格式（GC-agnostic Streamable Object Format）**：此前 AOT 堆对象缓存仅支持 G1GC，因为不同 GC 的内存布局与对象格式差异巨大；JEP 516 通过一套抽象的中间格式解决了此问题，**ZGC 用户现在可以同时享受 AOT Cache 与亚毫秒级 GC 停顿**，消除了"要么低延迟、要么快启动"的历史二选一困境。

此外，JDK 26 的 C2 JIT 已完成 AOT 代码缓存集成：AOT Cache 中不仅有数据，还有可以在生产运行时直接使用的已优化机器码，彻底消除了热身期（Warmup）开销。

**生产架构影响与指导**

1. **与 GraalVM Native Image 的定位分野**：Native Image 通过闭世界分析实现极致冷启动，但牺牲了动态类加载和 JIT 自适应优化；Leyden AOT Cache 属于开世界方案，保留完整 JVM 语义与动态能力，适合需要兼顾启动速度与峰值吞吐的大型 Spring/Quarkus 应用。
2. **Kubernetes 场景直接受益**：HPA 横向扩容时，新 Pod 冷启动从数十秒压缩到数秒，彻底改变基于 JVM 的服务的弹性伸缩经济性。
3. **集成行动**：使用 JDK 26+ 的团队应立即开始试验 `-XX:CacheDataStore=app.jsa` 等 AOT Cache 参数；Spring Tools 的 Leyden AOT Cache 配置 Wiki 已提供详细集成指南。

---

### 3. Spring Boot 4.1.0 + Spring AI 2.0.0 GA 连续发布：Java AI-First 生产栈正式成型 `[GA 正式版]` `[范式转移]`

**事件全景**

6 月 10 日 Spring Boot 4.1.0 GA，6 月 12 日 Spring AI 2.0.0 GA，两大版本 48 小时内先后落地，标志着 Broadcom/VMware Spring 团队完成了面向 AI 原生企业应用的全栈重构：**Spring Framework 7.0 → Spring Boot 4.x → Spring AI 2.0** 三层体系正式对齐，共同以 Jakarta EE 11、Java 21+ 为运行基线。

Spring Boot 4.1.0 的核心贡献是将 **gRPC 从可选扩展提升为一等公民**，通过三大自动配置模块（`spring-boot-grpc-server`、`spring-boot-grpc-client`、`spring-boot-grpc-test`）覆盖完整的 gRPC 生命周期，背后依托 Spring gRPC 1.1.0 和 grpc-java 1.80.0。服务端既可以独立 Netty 进程启动，也可以通过 Servlet HTTP/2 集成在同一端口上同时服务 REST 和 gRPC。

Spring AI 2.0.0 则代表了 Java 企业 AI 集成的**第一个生产级稳定 API**：彻底重构了 Options 系统（不可变 Builder 模式、无反射合并）、全面采用 MCP（Model Context Protocol）2025-11-25 规范，并将 Streamable HTTP 设为默认传输层替代过时的 SSE。

**底层机制与设计哲学解析**

**Spring Boot 4.1.0 gRPC 集成机制**：
- 服务端：`@GrpcService` 注解自动发现并注册 gRPC Service Stub，与 Spring Security 深度集成，内置 SSL Bundle 支持双向 TLS，同时自动曝露 Actuator 健康检查端点 `/actuator/health/grpc`。
- 客户端：`GrpcChannelFactory` 自动管理 Channel 生命周期，支持负载均衡配置与 SSL Bundle。
- **SSRF 防护**（`InetAddressFilter`）：通过拦截 HTTP Client 出站请求的目标地址，阻止内网地址（如 169.254.x.x、10.x.x.x）访问，解决云原生环境下 SSRF 高危漏洞的系统性防护问题。
- **Maven AOT 变更**：`-DskipTests` 不再跳过 AOT 处理，AOT 处理必须显式通过 `-Dmaven.test.skip=true` 禁用，确保 CI 构建不会意外绕过 AOT 代码生成。

**Spring AI 2.0.0 核心架构**：
- **MCP 集成重构**：MCP WebMVC 与 WebFlux 传输层从 MCP Java SDK 移入 Spring AI，天然继承 Spring Security（OAuth 2.0 + API Key）和 Micrometer OpenTelemetry 观测栈。
- **ToolSearchToolCallingAdvisor**：实现"渐进式工具披露（Progressive Tool Disclosure）"——当可用工具达数百个时，不再一次性将全部 Tool Schema 塞入 Context Window（这会消耗大量 Token 并降低推理质量），而是按语义检索仅传递与当前任务相关的工具子集。
- **JSpecify null-safety**：全量代码库采用 JSpecify 注解，Kotlin 互操作可在编译期捕获空安全违规。

**生产架构影响与指导**

1. **gRPC 标准化机会**：现有使用社区 `grpc-spring-boot-starter` 的项目应规划迁移至官方支持模块，以获得 Security 集成和 Actuator 可观测性。
2. **Spring Boot 3.5 EOL 临近（6 月 30 日）**：必须在本月内完成升级计划，否则进入无支持状态。
3. **AI 集成标准路径确立**：企业 Java AI 项目应以 Spring AI 2.0 + MCP 为标准架构起点，避免直接依赖厂商 SDK 导致锁定；MCP 协议已成为 Java 与 LLM 之间的通用互操作层。

---

### 4. JDK 27 特性冻结：紧凑对象头默认开启 + 后量子 TLS 上线，静默性能革命落地 `[重大进展]`

**事件全景**

JDK 27 于 6 月 4 日正式进入 Rampdown Phase One（主线 Fork），计划 2026 年 9 月 14 日 GA，共收录 9 条 JEP。InfoQ 于 5 月 18 日的 OpenJDK 新闻专题揭示了其中两项影响深远但极少被关注的底层变更：**Compact Object Headers（紧凑对象头）正式成为默认值**，以及 **Post-Quantum Hybrid Key Exchange for TLS 1.3 正式进入 JDK 标准库**。

**底层机制解析**

**Compact Object Headers**（原来需要 `-XX:+UseCompactObjectHeaders` 手动开启）：HotSpot 将 64 位系统上的对象头从 **96 bits（12 字节）压缩至 64 bits（8 字节）**，节省了 HashCode 字段与 Class Word 部分冗余位。对大量堆对象的应用（如大型缓存、批处理），等效于免费获得约 **8~15% 的堆密度提升**（具体取决于对象平均大小），同时改善 CPU 数据缓存命中率，减少 GC 扫描时间。历史上此优化因与某些序列化框架的内存布局假设冲突而停留在实验阶段；JDK 27 将其设为默认意味着生态兼容性测试已通过。

**Post-Quantum TLS 1.3（JEP 496）**：JDK 27 在 `javax.net.ssl` 层引入三种新的混合密钥交换算法：`X25519MLKEM768`（性能最优，默认首选）、`SecP256r1MLKEM768`、`SecP384r1MLKEM1024`。所谓"混合"是指将传统 ECDH（防现有对手）与 ML-KEM（CRYSTALS-Kyber，防量子对手）组合在单次握手中——任意一方安全即整体安全。**应用代码无需修改**，所有使用 Java TLS 栈的框架（Netty、OkHttp、Spring WebClient、gRPC-Java）在 JDK 27+ 自动受益。

此外，**G1GC 正式成为 JDK 27 的明确默认 GC**（文档层确认，行为层早已如此），配合 JEP 516 的 AOT Cache 支持，为 2027 年的 LTS（JDK 29）铺路。

**生产架构影响与指导**

1. **堆优化的零成本路径**：升级到 JDK 27 对绝大多数应用而言是透明的，紧凑对象头的收益不需要任何代码改动即可获得，建议将 JDK 27 列入 2026 Q4 升级计划。
2. **量子安全合规**：金融、政府、医疗行业面临即将到来的后量子密码学合规要求（如 NIST PQC 标准），JDK 27 是达到 TLS 层合规的最低成本路径。
3. **注意事项**：检查依赖 JVM 对象头布局进行内存优化的框架（如 Chronicle Map、某些 off-heap 库），可能需要适配新的头部大小。

---

## 🟡 Tier 2：重要迭代与应用生态

### 1. Micronaut Framework 5.0 GA：全平台重构，70+ 模块统一升级 `[GA 正式版]` `[性能跃升]`

**核心增量**

Micronaut Foundation 于 5 月 20 日发布 Micronaut 5.0.0 GA，6 月 3 日随即推出 5.0.2 修复版，本次是 Micronaut 4（2023 年）以来的第一个大版本。5.0 的升级范围横跨 **70 余个 Micronaut 模块**，实现跨模块的编译时 DI、AOT 和 GraalVM Native Image 支持的一致性收敛。

**核心工程思想**

- 底层 HTTP 服务器升级至 **Netty 4.2.15.Final**（含多项 CVE 修复），彻底解决历史版本中特定 HTTP/2 场景下的安全漏洞。
- **Micronaut Langchain4j** 模块首次纳入官方发行版，提供编译时 AI 工具注册与类型安全的 LLM 调用接口。
- 实验性 **Loom Carrier Mode**（4.9 引入，5.0 稳定化）：允许 Micronaut 响应式管道以虚拟线程作为载体线程运行，在保持 Reactive 编程模型的同时降低上下文切换开销。
- `micronaut-spring` 模块更新至与 Spring Boot 4.x 兼容，方便从 Spring 生态向 Micronaut 渐进式迁移。

**落地行动指南**

从 Micronaut 4.x 升级时需关注：Java 基线提升至 21；Netty 4.2.x 相较 4.1.x 有若干 API 细节变动；编译时 AOP 代理的生成策略有小幅调整，建议在 CI 中运行完整集成测试套件确认行为一致性。Langchain4j 集成尝鲜需确认 `micronaut-langchain4j` 与目标 LLM Provider SDK 版本兼容性。

---

### 2. Hibernate ORM 7.3.8.Final 发布：字节码增强自动化 + Jackson 3 双轨支持 `[重要迭代]`

**核心增量**

6 月 7 日发布的 Hibernate ORM 7.3.8.Final 是 7.3 系列的最新维护版，同期 **7.4 已成为最新稳定主线**。7.3 引入的两项特性在当前生产实践中具有直接价值：

1. **字节码增强器自动注入默认构造函数**：Hibernate 的延迟加载（Lazy Loading）代理机制依赖实体类存在无参构造器，此前开发者必须手动添加（常遗漏导致运行时异常）。7.3+ 字节码增强器在构建期自动为缺少默认构造函数的 `@Entity`、`@MappedSuperclass`、`@Embeddable` 类注入，消除这一历史"踩坑点"。

2. **Jackson 3 JSON/XML FormatMapper 双轨支持**：Hibernate 现同时支持 Jackson 2 与 Jackson 3 的 FormatMapper，与 Spring Boot 4.x（Jackson 3 迁移）形成完整对齐；`@JdbcTypeCode(SqlTypes.JSON)` 等 JSON 类型映射无需额外适配代码。

**落地行动指南**

Spring Boot 4.x 项目配套使用 Hibernate ORM 7.x 时，需确认 Jackson 版本对齐（Spring Boot 4 默认 Jackson 3）；旧代码库若显式依赖 Jackson 2，需检查 `hibernate-core` 的 FormatMapper 配置，避免序列化行为回归。

---

### 3. Gradle 9.6.0 发布：Configuration Cache 命中率精准提升 `[重要迭代]`

**核心增量**

6 月 20 日发布的 Gradle 9.6.0 在 **Configuration Cache** 层面带来精准跟踪改进：此前 Gradle 无法区分通过系统属性或环境变量传入的 Project Property 是否实际影响配置结果，导致任何环境变量变化都会使 Cache 失效；9.6.0 实现了精细粒度的属性使用追踪，**显著提升 CI/CD 流水线中 Configuration Cache 的命中率**，减少冷构建次数。

额外亮点：
- CLI 新增 `--non-interactive` 参数，禁止交互式提示，适配 CI 自动化场景；
- 支持 `NO_COLOR` 环境变量禁用终端颜色，改善日志解析；
- HTML 测试报告支持列排序。

**落地行动指南**

重要 **破坏性变更预告**：父项目中的隐式属性/方法查找（未通过 `project:` 限定的跨项目属性访问）现在会触发弃用警告，计划在 Gradle 10 中移除。多项目构建的团队应尽早检查并修复此类用法，避免未来升级受阻。

---

### 4. Spring AI 2.0.0 × MCP：Java AI 工程的标准化时刻 `[范式转移]` `[GA 正式版]`

（已在 Tier 1 第 3 条中深入分析核心架构，此处补充工程落地细节）

**核心工程亮点**

- **RAG 管道成熟化**：`VectorStore` 抽象已稳定，覆盖 Chroma、Pinecone、PgVector、Redis Stack 等主流向量数据库；`QuestionAnswerAdvisor` 提供开箱即用的 RAG 链。
- **spring-ai-session** 社区项目：提供事件溯源式对话历史 + LLM 驱动的自动摘要，解决 Context Window 膨胀导致长对话成本失控的痛点。
- **与 Spring Boot 4.1 gRPC 的联动**：AI 推理服务可直接通过 gRPC 对外暴露，结合 Spring Security TLS + 健康检查，构成完整的 Production-Ready AI 微服务模板。

---

### 5. GraalVM 加速发布节奏：从半年一次切换至月度 Feature Release `[生态演进]`

**核心增量**

Oracle 于 5 月发布博文宣布 GraalVM 加速发布策略：从 2026 年 6 月的 25.1 版本起，Oracle GraalVM 和社区版均切换至**月度 Feature Release 模式**，每季度叠加一次 Critical Patch Update（CPU），与 Oracle Java SE 的 CPU 节奏对齐。GraalVM 25.1.3 于 6 月底发布（CPU Level 3）。

**工程影响**

月度发布意味着 Native Image 的 Bug Fix 和性能优化将更快抵达用户，但同时对 CI 构建中固定 GraalVM 版本的项目提出了更高的版本管理要求。建议使用 GraalVM Toolchain（如 Gradle Toolchain 支持、`setup-graalvm` GitHub Action）统一管理版本，而非硬编码到构建脚本中。

---

### 6. Jakarta EE 12 进度更新：Q4 2026 Core Profile 目标，Data 与 NoSQL 规范推进 `[重要迭代]`

**核心增量**

Jakarta EE 12 当前目标为 2026 年 Q4 发布 Core Profile，Web Profile 和完整 Platform 推迟至 2027 年 Q1~Q2。主要规范进展：

- **Jakarta Query 1.0**（里程碑 2）：为 Jakarta Persistence 引入更强大的类型安全查询 DSL，填补 JPQL 表达力不足的痛点。
- **Jakarta NoSQL 1.1**：规范化与各 NoSQL 数据库的集成契约，与 Spring Data 的 Repository 抽象理念对齐。
- **SecurityManager 彻底清理**：JDK 17 弃用、JDK 24 永久禁用的 `SecurityManager` 将在 EE 12 各规范中完成移除，扫清历史遗留技术债。
- **HTTP/3 准备**：Servlet 5.1 开始布局 HTTP/3 支持的接口预留。

**落地行动指南**

当前使用 Jakarta EE 11（JDK 21 基线）的项目无需立即升级；EE 12 的 JDK 21 最低要求与 EE 11 一致，迁移阻力较小。关注 Jakarta Query 的规范成熟进度，有望替代 Criteria API 的复杂样板代码。

---

## 🟢 Tier 3：行业风向与速递

- **JDK 28 专家组（Expert Group）正式成立**（6 月 1 日 InfoQ 报道）：JDK 28 目标 2027 年 3 月 GA，Project Valhalla JEP 401 Preview 是最重要的锚点特性，其余特性提案征集阶段仍在进行。

- **JEP 533：Structured Concurrency 第 7 次 Preview 锁定 JDK 27**：此轮变更聚焦类型系统精化——`StructuredTaskScope` 和 `Joiner` 接口新增第三个类型参数 `R_X` 代表 `join()` 可抛出的异常类型，提升编译期异常传播安全性；API 形态自第 5 次 Preview 以来已稳定，本轮属于收尾性改动，预计 JDK 28 或 JDK 29 退出 Preview。

- **"Ephemeral Threads"早期讨论出现在 OpenJDK 邮件列表**：这一概念挑战 30 年来"线程是 GC Root"的铁律，允许已创建但尚未完成的线程被 GC 回收（适用于 fire-and-forget 场景），目前处于极早期草案阶段，尚无对应 JEP。

- **JDK 26 虚拟线程改进**：虚拟线程在等待其他线程完成类初始化（Class Initializer）时，现在能正确卸载（Unmount）其载体平台线程，防止平台线程被阻塞性等待占用，改善高并发冷启动场景下的线程利用率。

- **ZGC 非世代模式正式移除**：JDK 21 引入 ZGC 世代模式（Generational ZGC）作为实验特性，JDK 23 设为默认，JDK 25/26 完成非世代模式的彻底清理。现存使用 `-XX:+UseZGC -XX:-ZGenerational` 的生产配置脚本**必须删除该参数**，否则在 JDK 26+ 启动时将报错。

- **JDK 26 G1 + THP（Transparent Huge Pages）Bug 修复**：`-XX:+UseTransparentHugePages` 在 G1 下的配置回归（导致 THP 实际未生效）已在 JDK 26 修复，内存密集型应用可重新评估启用 THP 带来的吞吐收益。

- **Quarkus 3.34 新特性接续 3.33 LTS**：3.33 作为 LTS 仅收录 Bugfix，3.34 是与其同日发布的 Feature 版本，主要增量包括 Security 强化、Cache 扩展以及对 Jakarta EE 11 更深度的规范集成。

- **Spring Data 2026.0.0 GA 发布**（6 月 8 日 InfoQ）：与 Spring Boot 4.1 和 Spring Framework 7.0.8 对齐，全面适配 Jakarta Persistence 3.2 和 Hibernate ORM 7.x，Repository 层统一支持 `@NativeQuery` 类型安全化改进。

- **Spring 全家桶密集点版发布**（6 月 8 日周）：Spring Security、Spring Session、Spring Integration、Spring HATEOAS、Spring Modulith、Spring AMQP、Spring for Apache Kafka、Spring LDAP、Spring Vault、Spring gRPC 同步推出维护版本，建议随 Spring Boot 4.1 升级一并纳入依赖锁定。

- **A2A Java SDK 1.0 GA**（Google Agent-to-Agent 协议）：Java 生态获得首个 GA 级别的 A2A 客户端 SDK，支持跨 AI Agent 协作协议，与 Spring AI MCP 形成互补的多 Agent 互操作层。

- **Netty 4.2.15.Final 发布**（6 月 2 日）：修复多项 CVE，Micronaut 5.0.2 第一时间集成。使用旧版 Netty 的项目（如自定义 Netty Server 或依赖 Netty 的框架）应优先评估升级。

- **Gradle 9.6.0 同日（6 月 20 日）发布，Gradle 9.4 早前已完成 Java 26 Toolchain 支持**：全面适配 JDK 26 的 Release Flag 和 API 变更，保障 Java 26 项目的构建兼容性。

- **Open Liberty 2026 年 6 月 Beta 发布**：IBM 的 Jakarta EE 运行时持续对齐 EE 12 规范草案，Beta 版中已包含 Jakarta Query 1.0 早期实现供社区测试反馈。

- **JDK 26 密码学性能提升**（Inside.java 6 月 9 日）：AES-GCM、ML-DSA（后量子数字签名）、Elliptic Curve P-256 等算法通过 CPU 特有 Intrinsic 优化和算法层精简，在无需代码改动的前提下获得可测量的吞吐提升，TLS 握手密集型服务（API Gateway、Service Mesh Sidecar）受益最为明显。

- **Lazy Constants API（JEP 502，Preview in JDK 26）**：`java.lang.LazyConstant` 接口允许开发者声明延迟初始化的"常量"，JVM 可将其视同 `final` 字段进行常量折叠优化，但无需在类初始化时急切求值，预计对配置值、国际化资源等全局单例场景有直接价值。

---

*情报来源：OpenJDK 邮件列表（jdk-dev@openjdk.org）、Inside.java、InfoQ Java News Roundup、The Register DevOps、JVM Weekly、spring.io 官方博客、micronaut.io、quarkus.io、hibernate.org、gradle.org、jakartaee.github.io*
