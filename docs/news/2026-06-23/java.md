# Java 平台与企业级框架生态情报简报
**日期：2026-06-23 | 覆盖窗口：过去 48–72 小时及近期关键进展**

---

## 🔴 Tier 1：核心突破与范式转移

### 1. Project Valhalla 十年磨一剑：JEP 401 Value Classes 正式目标 JDK 28，代码冻结启动 `[JEP 重大进展]`

**事件全景**

6 月 15 日，Oracle 工程师 Lois Foltan 宣布 JEP 401（Value Classes and Objects，Preview）正式目标 JDK 28（预计 2027 年 3 月 GA），集成到 OpenJDK 主线的时间窗口锁定在 2026 年 7 月初，valhalla fork 的代码冻结已于 6 月 19 日启动。该 Pull Request 横跨 1,816 个修改文件、净增逾 197,000 行代码，是过去十年 Java 语言层面规模最大的单次变更。此举打破了"Java 对象必然携带身份（identity）"这一自 1.0 时代延续三十年的核心假设。

**底层机制与设计哲学**

JEP 401 在语言层引入 `value` 修饰符，`value class` 的实例称为 value objects，其核心特征是"无身份（identity-free）"——不可被 `==` 比较引用地址，不可同步（synchronized），也不可弱引用。这种约束反过来给 JVM 打开了两扇性能之门：

- **堆扁平化（Heap Flattening）**：当 A 类的字段持有 value object B 时，JVM 可以将 B 的字段值内联进 A 的内存区域，完全消除指针跳转，实现真正的内存连续布局。这对于 `Point[]`、`Complex[]` 等数值密集型数组而言，与传统 boxed 对象数组相比，内存访问的缓存命中率呈数量级跃升。
- **引用标量化（Reference Scalarization）**：方法参数与局部变量若为 value object，JVM 可将其分解为若干基本类型局部变量，在寄存器或栈上直接传递，彻底跳过 GC 堆分配。

官方基准测试表明，对值类型密集型工作负载（如金融计算、坐标系运算）的迭代场景，接近 3 倍的速度提升具有可重复性。

**生产架构影响与指导**

JEP 401 将以 Preview 形式在 JDK 28 落地，需 `--enable-preview` 才能使用，且预计在 JDK 29（下一个 LTS，2027 年 9 月）仍处于 Preview 阶段。团队现阶段行动要点：① 识别系统中高频分配的不可变数据载体（Money、Coordinate、RGB、DateRange 等），标记为 Valhalla 候选改造目标；② 评估现有代码中依赖对象身份的逻辑（`synchronized`、`WeakReference`、`IdentityHashMap`），这些将成为迁移障碍；③ 注意 `value class` 字段必须全部为 `final`，现有可变 DTO 类不适合直接改造。框架层（如 Hibernate、Jackson）的适配将是后续的生态关键节点，尤其是序列化与反序列化路径对身份语义的隐性依赖。

---

### 2. JDK 27 特性冻结：9 大 JEP 全景解析，G1GC 全域默认与后量子密码同步落地 `[JEP 重大进展]`

**事件全景**

2026 年 6 月 4 日，JDK 27 正式进入 Rampdown Phase One，主线 fork 完成，特性列表锁定为 9 个 JEP，GA 目标日期为 2026 年 9 月 14 日。这是继 JDK 26 之后又一个"非 LTS"的节奏版本，但其中两个新特性的生产影响力远超节奏版本的常规预期。

**底层机制——四个正式新特性解析**

- **JEP 523：G1 成为全环境默认 GC**：自 JDK 9 起，G1 已是 server-class 机器（多核 + ≥1.8GB）的默认 GC，但低于阈值（单核、<1792MB）时仍回落到 Serial GC。JEP 523 彻底消除此分叉，使容器化部署（常见低资源配置）默认享受 G1 的增量回收与并发 GC 能力，消除 "Single CPU 容器跑 Serial GC" 这一长期陷阱。

- **JEP 534：Compact Object Headers 默认启用**：JDK 25 引入该特性为实验性 opt-in（`-XX:+UseCompactObjectHeaders`），JDK 27 将其翻转为默认 on。对象头从 96 位压缩至 64 位，堆内存节省 10-20%。不兼容时仍可用 `-XX:-UseCompactObjectHeaders` 关闭，但主流框架（Spring、Netty、Hibernate）已完成验证，生产可信度高。

- **JEP 527：Post-Quantum Hybrid Key Exchange for TLS 1.3**：JDK 的 TLS 1.3 实现新增三种混合后量子密钥交换方案：`X25519MLKEM768`、`SecP256r1MLKEM768`、`SecP384r1MLKEM1024`，将 ML-KEM（NIST 后量子标准）与传统 ECDHE 结合，默认将 `X25519MLKEM768` 置于 named groups 列表首位。**对现有代码零改动即可获得量子抗性保护**，除非代码显式指定了特定密钥交换方案。这是 Java 生态对 "Harvest Now, Decrypt Later" 攻击的主动防御里程碑。

- **JEP 536：JFR In-Process Data Redaction**：JDK Flight Recorder 新增脱敏能力，在录制文件落盘前即完成对环境变量值、系统属性值、命令行参数的原地替换（替换为 `[REDACTED]`）。脱敏规则通过 `redact-key` 配置项以分号分隔过滤器指定，支持从 `@filename` 加载规则文件。这直接解决了合规场景下 JFR 录制文件泄露密钥/令牌的痛点。

**生产架构影响与指导**

- G1 全域默认：团队应在升级 JDK 27 前评估已有 GC 调优参数（Serial GC 的 `-XX:NewRatio` 等无效化），并为低内存容器补充 G1 特有调优（如 `-XX:MaxGCPauseMillis`）。
- Compact Object Headers：内存密集型应用可通过前/后对比 `jmap -histo` 直观验证收益，理论上可在不扩容前提下提升 10-20% 的有效堆容量。
- Post-Quantum TLS：需要检查 JSSE 代码中是否存在硬编码 named groups 指定（`SSLParameters::setNamedGroups`），否则自动受益；关注与老版本 TLS 端点的握手兼容性。
- JFR 脱敏：在 Kubernetes 场景下结合 Continuous Profiling（如 Pyroscope）时，务必在 JFR 配置中覆盖数据库连接串、JWT secret 等敏感属性键名，利用此特性实现合规托管。

---

### 3. Spring Boot 4.1 GA：gRPC 原生自动配置、SSRF 防御内置化，企业云原生协议栈大跃进 `[GA 正式版]`

**事件全景**

2026 年 6 月 10 日，Broadcom 正式发布 Spring Boot 4.1.0，这是继 4.0 奠定 Java 25 / Spring Framework 7.0 基线后的首个 minor 版本。核心诉求聚焦三大工程痛点：gRPC 服务接入的标准化、HTTP 客户端的安全默认值、以及可观测性链路的深度增强。同期，Spring Boot 3.5 OSS 支持将于 2026 年 6 月 30 日到期，Stack 迁移窗口进入倒计时。

**底层机制与设计哲学**

① **原生 gRPC 自动配置**：Spring Boot 4.1 将 Spring gRPC 1.1 纳入 Auto-Configuration 体系，支持独立 Netty 传输与 Servlet HTTP/2 两种服务器模式，无需第三方 starter。关键引入：`@GrpcAdvice` 集中处理 gRPC 异常（对应 HTTP 侧的 `@ControllerAdvice`）；`ObservationGrpcServerInterceptor` 自动注入，将 gRPC 请求纳入 Micrometer 指标与 Distributed Tracing 链路，无需手动注册拦截器。客户端侧支持通过 `@GrpcClient` 注解以 DI 方式注入 Stub，与 Spring 生命周期深度绑定。

② **HTTP Client SSRF 缓解**：引入 `InetAddressFilter`，可配置地址白名单/黑名单，对 RestClient 和 WebClient 的出站请求统一拦截，阻断对内部网段（如 169.254.0.0/16 元数据 API、10.0.0.0/8 内网服务）的请求。这是 Spring 首次在框架层对 SSRF 提供默认防线，直接解决 OWASP Top 10 中 "Server-Side Request Forgery" 在微服务场景下的系统性风险。

③ **懒加载数据源**：`spring.datasource.connection-fetch=lazy` 将 DataSource 包装为 `LazyConnectionDataSourceProxy`，物理连接推迟至首次 SQL 执行时建立。对于批量处理或读写分离场景，能显著降低非关键路径的连接池压力，提升启动速度。

④ **@Async 上下文自动传播**：Micrometer 上下文（含 TraceId、SpanId）现在在 `@Async` 方法的线程切换中自动传播，无需手动包装 `Executor`。这补全了可观测性链路中异步断点的最后一块拼图。

⑤ **Derby 宣告退休**：Apache Derby 项目正式宣告停止维护，Spring Boot 4.1 将其标记为 Deprecated，预计后续版本移除。嵌入式数据库建议迁移至 H2 或 HSQLDB。

**生产架构影响与指导**

gRPC 自动配置要求从 Spring gRPC 1.0 迁移到 1.1 参照官方迁移指南；同时注意 JPA Bootstrap 的 `deferred` 模式现在要求必须存在 `AsyncTaskExecutor` Bean，否则启动失败——这是 4.1 最常见的升级陷阱。JDK 基线依旧是 Java 17，兼容至 Java 26，整体迁移风险可控，但 Spring Boot 3.5 用户面临强制升级时间压力（2026-06-30 OSS 到期）。

---

### 4. Project Leyden AOT 链路完整落地：Spring Boot 冷启动压缩至 0.27 秒，云原生成本重构 `[范式转移]`

**事件全景**

Project Leyden 经过三个版本的 JEP 递进已形成完整 AOT 链路：JDK 24 的 JEP 483（AOT Cache 基础）→ JDK 25 的 JEP 514（命令行人机工程优化）+ JEP 515（方法剖析驱动的 JIT 预热加速）→ JDK 26 的 JEP 516（AOT Cache GC 无关化，支持 ZGC，且 JDK 本身随发行版附带基线缓存）。这使 Leyden 从实验性工具演变为生产就绪的冷启动解决方案，与 GraalVM Native Image 形成差异化竞争，也与 CRaC（Coordinated Restore at Checkpoint）构成互补选择。

**底层机制**

Leyden 的核心思路是"将工作从运行时迁移至一次性训练运行"，具体三层叠加：
1. **类与字节码缓存**（JEP 483）：将已解析的类数据持久化到 AOT Cache 文件，消除重复类加载与验证开销。
2. **方法剖析预热**（JEP 515）：训练运行时记录"热方法"分布，生产启动时优先编译这些方法，将 JIT 达峰时间从分钟级压缩到秒级。
3. **AOT Code Cache**（Premain 分支实验）：将 native 编译代码直接存入缓存，首次启动即以 native 速度执行，无任何 JIT 延迟——此特性尚未正式 JEP 化，但已在 Early Access 可用。

Spring Boot 3.3+ 已内置对 Leyden AOT Cache 的支持，实测数据：常规 Spring Boot 应用冷启动从 1.1 秒降至 0.27 秒（约 4 倍提速），相比 GraalVM Native Image 的优势在于：无需改变构建流程、无需静态分析（反射/动态代理仍正常工作）、不牺牲完整 JVM 调试与监控能力。

**生产架构影响与指导**

对云原生部署团队的直接意义：Serverless / FaaS 场景下 Java 冷启动的竞争力与 Go/Node 接近但无需放弃 JVM 生态；Kubernetes HPA 横向扩容的冷启动延迟（影响 SLO）显著改善。行动建议：JDK 26+ 用户今天就能获得基线 AOT 缓存的被动收益；进一步优化可增加自定义训练运行步骤（`java -XX:AOTMode=record` 生成缓存 → 生产使用 `-XX:AOTMode=load`）。CRaC vs Leyden 选择：CRaC 适合启动时间极度敏感（<100ms 目标）的场景，但需要文件系统快照基础设施；Leyden 更适合标准 CI/CD 流水线，零基础设施依赖。

---

### 5. Micronaut 5.0 GA：Java 25 LTS 重新奠基，70+ 模块全面刷新 `[GA 正式版]`

**事件全景**

2026 年 5 月 20 日，Micronaut Framework 5.0.0 正式发布，距上一个大版本 Micronaut 4 已近三年。5.0 以 Java 25（最新 LTS）为最低版本基线，同时刷新超过 70 个模块，是该框架有史以来最大规模的平台级升级。6 月 1 日已发布维护版 5.0.1。

**底层机制与设计哲学**

① **Java 25 特性深度整合**：框架原生利用 Virtual Threads（虚拟线程）处理 I/O 绑定的请求上下文；Scoped Values 作为上下文传播的替代实现方案（默认仍使用 ThreadLocal，可切换），为结构化并发（Structured Concurrency）的嵌套范围提供更清晰的生命周期管理；Pattern Matching for switch 和 Record Patterns 大量出现在框架内部的 DSL 实现中。

② **构建时优化**：序列化器与反序列化器现在在**构建时**通过 SourceGen 生成（而非运行时反射），与 GraalVM 原生镜像的兼容性大幅改善，同时降低了运行时初始化开销。

③ **GraalVM 25.0.3 升级**：Native Image 构建链锁定最新 GraalVM 25 系列，享受其在 JVM Compiler Interface 和 SubstrateVM 层面的持续优化。

④ **控制面板全面升级**：Micronaut Control Panel 新增 Cache、DataSource、Hibernate、Kafka Streams、Object Storage、Disabled Beans、Metrics 面板，并支持认证/授权访问控制，使运行时可视化能力接近 Spring Boot Actuator + Admin Server 的水准。

**生产架构影响与指导**

Micronaut 4 用户升级 5.0 面临 Java 21 → Java 25 的跨 LTS 基线调整，依赖于 Micronaut 4.x 的第三方库需评估兼容性。对于已在 GraalVM Native Image 路线上投入的团队，SourceGen 序列化是关键亮点——消除了大量运行时反射注册的样板，Native Image 构建失败率预期显著下降。

---

## 🟡 Tier 2：重要迭代与应用生态

### 1. Hibernate ORM 7.4：根治分页 + 集合 Fetch Join 的性能顽疾 `[性能跃升]`

**核心增量**：Hibernate 7.4 之前，对含 `@OneToMany` 的实体执行分页查询（`setMaxResults`）时，ORM 层会警告并将 limit 逻辑下移至 JVM 内存，导致数据库实际全量传输后再内存截断——对大表而言是灾难性的性能反模式。7.4 通过**嵌套子查询**彻底修复：先对父实体 ID 执行带 limit 的子查询，再以该 ID 集合为条件 fetch 子集合，实现分页完全在数据库侧发生。

**核心工程思想**：同版本引入原生时态历史表（`@TemporalHistory`）与审计表（`@Audited`）支持，将原本依赖 Hibernate Envers 的时间旅行查询能力内置，减少外部依赖链路。新增 `CacheMode.REFRESH_SESSION` 用于高效刷新一级缓存，支持 Google Cloud Spanner PostgreSQL 兼容 Dialect。

**落地行动指南**：直接升级 7.4，分页 + Fetch Join 场景无需修改代码即可获益。若现有代码显式启用了 `QueryHints.HINT_PASS_DISTINCT_THROUGH` 等绕过措施，可评估回退至更简洁写法。Hibernate Envers 用户可规划向内置审计迁移，简化依赖树。

---

### 2. Hibernate ORM 8.0 Beta1：向量搜索 + Jakarta Data 技术预览，下一代 ORM 形态浮现 `[Preview 预览特性]`

**核心增量**：8.0.0.Beta1 在 7.x 技术债清理（Jakarta EE 10/11 基线切换）基础上，首次引入向量数据类型支持（包含 SQL Server），直接对接 pgvector 等主流向量数据库扩展，为 RAG（Retrieval-Augmented Generation）场景提供 ORM 层抽象。Jakarta Data（JPA 的现代化替代规范）以技术预览形式进入 Hibernate 生态，Repository 编程模型得到原生支持。

**核心工程思想**：`FindMultipleOptions` 提供类型安全的多记录批量查找 API；Records 现在可作为 `@IdClass` 使用，与 Java 语言现代习惯对齐；`@EmbeddedTable` 允许嵌入式对象映射到独立子表而非同一行，拓宽了数据建模维度。

**落地行动指南**：8.0 仍为 Beta，不建议直接上生产。向量支持的早期验证可在 staging 环境中结合 pgvector 测试 AI 相似度搜索路径，提前积累调优经验。Jakarta Data 的编程模型与 Spring Data 高度相似，但规范层更轻量，未来具备跨框架移植性。

---

### 3. JEP 531：Lazy Constants 三度 Preview（JDK 27），惰性初始化跻身 JVM 一等公民 `[Preview 预览特性]`

**核心增量**：从 JDK 25 的 JEP 502（`StableValue`）→ JDK 26 的 JEP 526（改名为 `LazyConstant`，精简 API）→ JDK 27 的 JEP 531（第三次 Preview），该特性持续收敛。第三轮新增：① 移除低层次方法 `isInitialized()` 和 `orElse()`（鼓励使用高层 `get()` 惰性访问）；② 新增 `Set.ofLazy()` 工厂方法；③ 提供惰性版本的 `List`、`Set`、`Map` 三种核心集合类型，覆盖枚举注册表、国际化资源等高频惰性初始化场景。

**核心工程思想**：Lazy Constants 允许 JVM 将延迟初始化的字段视同 `final` 字段处理，在值初始化后触发与常量相同的 JIT 优化路径（内联、常量折叠），同时规避 double-checked locking 的并发复杂性。相比 `volatile` + DCL 模式，代码安全性与可读性大幅提升。

**落地行动指南**：JDK 27 Preview 用户可开始改造现有的单例注册表、配置缓存等惰性初始化模式。注意第三轮 Preview 存在 API 破坏性变更（移除方法），从第二轮（JDK 26）迁移需检查调用站点。

---

### 4. JEP 527：后量子 TLS 1.3 零配置接入，ML-KEM 混合方案正式落地 JDK 27 `[GA 正式版]`

**核心增量**：`X25519MLKEM768` 默认置于 TLS 1.3 named groups 首位，实现零配置量子抗性保护。混合方案的设计逻辑是：即使其中一种算法（量子或经典）被攻破，另一种仍提供安全保障，最坏情况等同现有安全水平。支持通过 `jdk.tls.namedGroups` 系统属性自定义优先级，或通过 `SSLParameters::setNamedGroups` API 精确控制。

**落地行动指南**：升级到 JDK 27 无需代码修改即受益。需重点检查两类代码：① 显式指定 named groups 的 SSL 配置（会覆盖默认行为）；② 连接不支持后量子方案的老旧 TLS 端点时，需确认是否退回经典算法而不是握手失败。金融、政务类系统建议将此列入 JDK 27 升级的合规背书材料。

---

### 5. Spring Tools 5.2.0：内嵌 Claude Code MCP Server，IDE 工具链进入 AI 原生时代 `[范式转移]`

**核心增量**：Spring Tools 5.2.0（基于 Eclipse 2026-06）发布的最大亮点是"实验性 Claude Code Plugin"——在 IDE 内嵌入 MCP（Model Context Protocol）Server，并预置 Claude Code Skills，使 AI 辅助编程能够直接理解 Spring 上下文（Bean 定义、配置属性、AOP 切面）。同步引入 Spring AI 支持，以及将 String 形式的属性引用自动重构为类型安全引用的工具链。

**落地行动指南**：Claude Code Plugin 当前为实验性，适合 Spring 开发团队在非生产环境率先试用，评估 AI 对 Spring 专属代码结构的理解深度。类型安全属性引用功能可立即用于生产，对大型 Spring Boot 项目的 Refactoring 安全性有实质性帮助。

---

### 6. GlassFish 8.0.3：Jakarta Faces 渲染性能翻倍 `[性能跃升]`

**核心增量**：GlassFish 8.0.3 带来两项显著的性能改进：① 多项 Embedded GlassFish 启动速度优化，降低嵌入式测试场景的等待成本；② Jakarta Faces（前身 JSF）页面渲染性能**提升 2 倍**，这对仍在维护遗留 JSF/Faces 应用的企业具有直接落地价值。

**落地行动指南**：使用 GlassFish 的 Jakarta EE 项目可直接升级获益，无破坏性变更。Faces 渲染性能提升主要来自模板编译缓存优化，对组件树复杂度高的页面收益更为显著。

---

### 7. Infinispan 16.2.0 "Arctic Panzer Wolf"：RESP 协议扩展与 PEM 证书统一配置 `[重要迭代]`

**核心增量**：Infinispan 16.2.0 在 Redis 序列化协议（RESP）兼容层新增 `BITFIELD`、`DELEX`、`COPY`、`DIGEST` 命令实现，同时引入概率数据结构类（Bloom Filter、HyperLogLog 等）的 RESP 封装，使 Infinispan 作为 Redis 替代的协议覆盖率进一步提升。PEM 证书的统一简化配置消除了 TLS 证书管理的碎片化问题。

**落地行动指南**：将 Infinispan 作为 Redis 兼容层使用的团队可测试新增 RESP 命令的兼容性，扩展客户端迁移可行性边界。PEM 证书配置简化对自动化 mTLS 证书轮转（如 cert-manager）场景有直接帮助。

---

### 8. Helidon 4.5.0：API 稳定性项目收官，HTTP/2 HPACK 安全加固 `[重要迭代]`

**核心增量**：Helidon 4.5.0 完成了 API 稳定性项目（借助 `@Api` 注解体系，对 API 成员明确标注 `internal`、`preview`、`incubating`、`stable` 语义），提升了第三方生态的接入确定性。加固 Helidon Config 中共享密钥的值加密；在 HTTP/2 HPACK 头压缩层拒绝格式错误的整数，防范 HPACK 解析层的潜在拒绝服务攻击面。

---

## 🟢 Tier 3：行业风向与速递

- **Quarkus CVE-2026-50559 紧急补丁**（CVSS 7.5）：HTTP 路径授权策略可通过 URL 编码的分号（`;`）、斜杠（`/`）、反斜杠（`\`）绕过，影响所有受支持流版本。已修复版本：3.37.0、3.36.3、3.33.2.1/3.33.3（LTS）、3.27.4.1/3.27.5、3.20.6.2。**使用 Quarkus 路径鉴权的项目必须立即升级。**

- **Apache TomEE 11.0.0-M1 发布**：首个里程碑版本支持 Jakarta EE 11 与 MicroProfile 7.1，但 Apache OpenJPA 仍停留在 Jakarta EE 10 兼容级别，JPA 部分存在临时降级。面向完整 Jakarta EE 11 的生产部署仍需等待后续里程碑。

- **Open Liberty 26.0.0.6 Beta**：预览基于 Netty 的 HTTP 传输层，修复了 `LibertyHttpObjectAggregator` 实例造成的死锁超时问题，为传输层解耦探路。

- **JobRunr 8.7.0**：新增懒加载服务器初始化能力，`BackgroundJobServer` 和 `WebServer` 可在 Fluent API 配置下按需启动，降低非后台处理模式下的资源占用。

- **JEP 537：Vector API（第 12 次 Incubator）目标 JDK 27**：Vector API 自 JDK 16 起持续孵化，第 12 次进入 Incubator 无实质 API 变更，等待 Project Valhalla 的值类型支持落地后才能真正稳定，预计在 JDK 29 前后与 value arrays 协同 GA。

- **JEP 532：Primitive Types in Patterns（第 5 次 Preview）**：此轮与上轮（JDK 26）相比无变化，意在等待上层特性生态稳定后一并 finalize，打通 Pattern Matching 对基本类型的完整覆盖。

- **JEP 538：PEM Encodings of Cryptographic Objects（第 3 次 Preview）**：持续预览，小幅 API 精化；完整落地依赖安全工作组对 PEM 标准化处理 API 的最终定稿。

- **JEP 530：Structured Concurrency（Preview 继续演进）**：在 JDK 27 中带有轻微 API 修订（方法签名调整），仍处 Preview 阶段。虚拟线程生态完善后预计在 JDK 28-29 区间 GA。

- **JEP 526 / 531 Lazy Constants 再预览**：见 Tier 2 详述，此处仅记录其在 JDK 27 以 JEP 531 第三轮预览的状态变更。

- **Kotlin 2.3 支持 Java 25 字节码生成**：Kotlin 编译器可生成 Java 25 class 文件级别字节码；新增实验性"未使用返回值检查器"，对非 `Unit`/`Nothing` 返回值的未使用表达式发出警告，与 Spring Boot 4.1 的 Kotlin 2.3 基线同步对齐。Kotlin 2.4-RC2 已支持 Java 26。

- **LangChain4j 1.11.8（2026-05-16 最新稳定版）**：Java AI 生态持续演进，与 Spring AI 1.0 GA 并驾齐驱争夺 JVM AI 框架主导地位；LangGraph4j 提供面向 Java 的 Agent 工作流编排，MCP 协议支持进一步增强框架互操作性。

- **Gradle 9.2.0**：新增 Windows ARM64 支持；工作图构建速度提升 34-42%，内存占用减少 7-12%，对大型多模块 Monorepo 效益显著。Gradle 9.x 已成为 Android 与 Java 生态的主流迁移目标。

- **OkHttp、Okio、Retrofit、SQLDelight 加入 Commonhaus Foundation**：开源基础设施治理强化，四个 Square 系移动/网络库进入中立基金会维护，供应链可信度提升，减少"依赖某单一商业公司生死存亡"的风险。

- **Spring Security 7.1.0 + Micrometer 1.17.0**：作为 Spring Boot 4.1 核心依赖同步发布，Spring Security 7.1 在 OAuth2 / OIDC 流程中增强了对 PKCE 的默认启用范围；Micrometer 1.17.0 为 OTLP exemplars 提供原生支持。

- **Apache Derby 宣告项目退休**：这个曾是 Java EE 标准测试数据库的嵌入式引擎正式 EOL，Spring Boot 4.1 同步 Deprecated 相关驱动支持。H2 / HSQLDB 是现成替代，对于需要接近生产行为的集成测试，Testcontainers 已是实际标准。

- **Spring Boot 3.5 OSS 支持 2026-06-30 到期**：已在 3.5 系列的团队须在月底前完成升级计划评估，目标 4.0 或 4.1；3.5 升 4.0 的主要 breaking change 集中在 Jakarta EE 10 命名空间（`javax.*` → `jakarta.*`）与 Spring Security 7 的 Lambda DSL 强制化。
