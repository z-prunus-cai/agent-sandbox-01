# Java 平台与企业级框架生态情报简报
**日期：2026-06-27 | 覆盖时间窗口：过去 48 小时及近期重大进展**

---

## 🔴 Tier 1：核心突破与范式转移

### 1. Project Valhalla 十年磨剑：JEP 401 值类型正式进入 JDK 28 预览 `[JEP 重大进展]` `[范式转移]`

**事件全景**

Project Valhalla 是 Java 历史上规模最大、持续时间最长的语言设计工程之一。2026 年 6 月中旬，OpenJDK 官方确认 JEP 401（Value Classes and Objects）已被接受并将以预览特性形式集成至 JDK 28（目标 GA 时间：2027 年 3 月），实际主线集成（integration into mainline）预计发生在 2026 年 7 月。相关 Pull Request 涉及 1,816 个文件、超过 197,000 行代码——这是 OpenJDK 历史上单次合并规模最大的变更之一。

其历史痛点在于 Java 对象模型的根本性局限：JVM 的"一切皆对象"哲学带来了无处不在的堆分配与指针间接访问开销。以 `Integer` 为例，每个被装箱的整数在堆上占据至少 16 字节，并通过引用访问，大量 `Integer` 或 `Double` 的集合实际上是指针数组，极度缓存不友好，且对 GC 造成持续压力。这一问题在数值密集型计算（金融风控、科学计算、游戏引擎）和高吞吐微服务中尤为突出。

**底层机制与设计哲学解析**

JEP 401 的核心是引入 `value class` 与 `value record` 关键字，允许开发者声明其身份为纯数据、无可变状态的类型。值类的对象不具备身份标识（identity），JVM 可对其进行**扁平化（flattening）**与**标量化（scalarization）**：当值类实例作为字段存储于另一对象时，其字段可内联（inline）到宿主对象的内存布局中，消除指针间接层；当值类实例作为方法参数传递时，可直接通过寄存器传递而非堆分配。此外，现有 JDK 中被标注为"基于值"（value-based）的类（包括所有原始包装类 `Integer`、`Long`、`Double` 等）将在预览下迁移为值类，使得 "cheap boxing"（廉价装箱）成为可能。需明确的是，本次预览**不包含**：空值受限类型（null-restricted types）、完整的特化泛型（specialized generics）以及 JEP 402 所描述的高级特性。

**生产架构影响与指导**

Valhalla 对 Java 生产生态的冲击是全方位的。对框架层而言，Spring、Hibernate 等基于反射与代理机制的框架需评估其对值类代理限制的处理：值类不可被继承，JDK 动态代理和 CGLIB 子类代理对值类失效，框架侧需转向接口代理或 AOT 代码生成路径。对性能层而言，数值密集型数据结构（如量化金融中的价格序列、时序数据库中的点集合）可望获得显著的内存与 GC 压力降低——内存布局密度提升直接作用于 CPU 缓存命中率。技术团队当前的行动要点：(1) 在 JDK 28 EA builds 上试验性地将现有 Value-Based 类迁移为 `value class` 并量化内存收益；(2) 提前评估框架侧的代理策略兼容性，制定 AOT 代理替换方案；(3) 注意 `--enable-preview` 限制，预览特性不跨 JDK 版本保证二进制兼容，生产使用需等待标准化。

---

### 2. JDK 27 特性冻结：九项 JEP 锁定，G1 成唯一默认 GC，对象头收缩 33% `[GA 预期 2026-09]` `[性能跃升]`

**事件全景**

2026 年 6 月 4 日，JDK 27 正式进入 Rampdown Phase One，特性集合完全冻结，不再接受新 JEP。九项 JEP 已锁定，目标 GA 日期为 **2026 年 9 月 14 日**。这次发布在 JVM 底层有两项对生产环境影响深远的变更：**JEP 523（G1 成为所有环境的默认 GC）**与 **JEP 534（紧凑对象头默认启用）**。前者打破了 G1 仅在"服务器级"JVM 中为默认的历史约束，后者将标准对象头从 12 字节压缩至 8 字节，是 JVM 内存效率的一次结构性优化。

**底层机制解析**

**JEP 523（G1 全场景默认）**：此前，HotSpot 在非服务器模式（如单核或 1GB 以下 JVM 参数）下会选择 Serial GC。JDK 26 对 G1 的吞吐量优化已使其在低端硬件上接近 Serial GC 的吞吐效率，同时保留了 G1 的停顿控制与并发收集能力。统一默认 GC 消除了因部署环境差异（开发机 vs. 容器）引发的 GC 行为鸿沟，提升了可预测性。

**JEP 534（紧凑对象头默认启用）**：64 位 JVM 中每个对象携带一个 12 字节的对象头（8 字节 mark word + 4 字节压缩类指针）。紧凑对象头通过将类指针编码压缩进 mark word 本身，将对象头统一降至 8 字节，节省每对象 4 字节。该特性在 JDK 25 中以实验性标记首次引入，JDK 26 中以 Incubator 推进，JDK 27 正式默认启用。

**性能基准**：SPECjbb2015 测试中，堆空间消耗减少 **22%**，CPU 时间节省 **8%**，GC 次数降低 **15%**（G1 与 Parallel GC 均验证）；高并发 JSON 解析场景耗时降低 **10%**。

**生产架构影响与指导**

对象头收缩对内存密集型应用（对象图庞大的 ORM 缓存、大规模 DDD 聚合根集合）效果尤为显著，堆空间节省 22% 直接意味着同等内存下可承载更高的数据密度或更长的 GC 间隔。需注意：少数依赖 JVM 对象头布局的底层工具（如 JVM TI agent、某些字节码操纵工具）可能需验证兼容性；`-XX:-UseCompactObjectHeaders` 可回退。G1 统一默认则要求团队在资源受限的容器中（如 256MB Sidecar 容器）重新确认 GC 配置，避免 G1 的保留区（Reserved Region）占用引发 OOM。

---

### 3. Project Leyden 纵深扩展：AOT 缓存向自定义类加载器延伸，Spring PetClinic 启动提速 41% `[JEP 进展]` `[启动性能]`

**事件全景**

Project Leyden 的核心命题是：在不依赖 GraalVM native image 的前提下，通过构建期缓存将大量运行时工作前移，大幅压缩 JVM 冷启动延迟。随着 JDK 26 中 JEP 516（AOT Object Caching with Any GC）的落地，Leyden 扫除了此前不支持 ZGC 的关键障碍。而近期最新的进展显示，Leyden 团队正在推动 AOT 缓存机制对**自定义类加载器**的支持——将 AOT/CDS 收益从系统类加载器和平台类加载器扩展至"安全合理的"非子类化 `URLClassLoader` 实例。这对插件化架构（如 IDE、应用服务器）和动态部署框架影响重大。

**底层机制解析**

Leyden 的 AOT 工作流分三个阶段：(1) **Training Run**：以训练模式运行应用，JVM 记录类加载顺序、JIT 编译热点与对象图初始化路径；(2) **Cache Generation**：利用训练数据生成 AOT Code Cache（含已编译原生代码）与 AOT Object Cache（含预初始化的堆快照）；(3) **Execution Run**：JVM 直接从缓存恢复已加载、已链接、已编译的类与对象，大幅跳过启动期的解释与 JIT warmup 阶段。

Spring PetClinic 实测数据：约 21,000 个类在启动时直接从缓存装载，启动时间提速 **41%**。与 GraalVM Native Image 相比，Leyden 的优势在于：保留完整的动态反射与动态类加载能力，无需 Native Image 所需的闭世界假设（closed-world assumption）及大量 `reflect-config.json` 配置，且不牺牲运行期 JIT 的峰值性能。其代价是仍有数秒的 JVM 启动时间，不适合毫秒级冷启动的 Serverless 极端场景（此类场景仍需 Native Image）。

**生产架构影响与指导**

Leyden 的成熟标志着 Java 在云原生启动性能竞争中拥有了**第三条路线**：以往的选择是"标准 JVM（峰值性能好但冷启动慢）"vs"GraalVM Native Image（冷启动极快但失去动态性）"，现在 Leyden 提供了兼顾动态性与启动速度的中间道路。Spring Boot 3.3+ 已原生集成 Leyden AOT 缓存支持，Quarkus 团队亦发布了 Leyden 集成博文。团队行动指南：(1) 升级至 Spring Boot 3.3+（或 4.x），启用 `-Dspring.context.exit=onRefresh` 训练模式生成缓存；(2) 在 Kubernetes Pod 启动延迟监控中设置基线对比，量化 Leyden 收益；(3) 使用 Leyden AOT Cache 诊断工具（Maria Arias de Reyna 的相关文章为参考）排查缓存未命中。

---

### 4. Spring Boot 4.1 正式发布：原生 gRPC 自动配置、SSRF 主动防护、Kotlin 2.3 `[GA 正式版]` `[框架里程碑]`

**事件全景**

2026 年 6 月 10 日，Broadcom 正式发布 Spring Boot 4.1.0。这是构建于 Spring Framework 7（Jakarta EE 11 基线）之上的重要迭代版本，核心亮点涵盖企业级 gRPC 一流支持、HTTP 客户端 SSRF 防护机制、异步上下文传播改善，以及对 Kotlin 2.3 的全面适配。背景上，Spring Boot 3.5 的生命周期终止日为 **2026 年 6 月 30 日**，意味着当前的升级窗口具有高度紧迫性。

**底层机制解析**

**gRPC 自动配置**：Spring Boot 4.1 通过三个专用模块（`spring-boot-grpc-server`、`spring-boot-grpc-client`、`spring-boot-grpc-test`）提供对 Spring gRPC 1.1.0（底层 grpc-java 1.80.0）的自动配置支持。服务端支持独立 Netty 与 Servlet HTTP/2 两种传输模式；引入 `@GrpcAdvice` 注解实现集中式 gRPC 异常处理，类比 `@ControllerAdvice` 的设计哲学；`ObservationGrpcServerInterceptor` 自动装配使 gRPC 服务自动接入 Micrometer 可观测体系，无需手动埋点。

**SSRF 主动防护**：`InetAddressFilter` 是 Boot 4.1 中最具安全工程价值的新增特性。它可统一应用于 WebFlux 的 `WebClient` 与同步 `RestClient`，通过声明式黑名单（RFC 1918 私有地址段、169.254.x.x 链路本地地址）阻断应用向内部基础设施发起的出站请求，从根本上切断云环境中常见的通过应用层访问 Instance Metadata Service（如 AWS EC2 `169.254.169.254`）的 SSRF 攻击路径。

**生产架构影响与指导**

Spring Boot 4.1 的 gRPC 支持结束了此前团队需手动整合 `grpc-spring-boot-starter`（非官方社区库）的碎片化局面，统一了 Spring 生态下 REST 与 gRPC 的可观测性、安全性与配置管理。升级检查要点：(1) Java 最低要求为 17，Jakarta EE 11 意味着所有 `javax.*` 包引用已被 `jakarta.*` 替换，迁移工具 `spring-boot-migrator` 可辅助自动化重命名；(2) 确认现有 `RestTemplate` 使用是否需迁移至 `RestClient`（Boot 4.x 中 `RestTemplate` 进入维护模式）；(3) 在生产环境配置 `InetAddressFilter` 并测试所有合法的对外 HTTP 调用不在黑名单范围内。

---

## 🟡 Tier 2：重要迭代与应用生态

### 1. JEP 527：后量子混合密钥交换默认进入 TLS 1.3，零代码变更即可防御量子威胁 `[JDK 27 集成]` `[安全]`

**核心增量**：JEP 527 在 JDK 27 的 TLS 1.3 实现中默认启用三种后量子混合密钥交换方案：`X25519MLKEM768`（ECDH+X25519 与 ML-KEM-768 的混合，默认置于偏好列表首位）、`SecP256r1MLKEM768` 与 `SecP384r1MLKEM1024`。其防御目标是"现在截获、未来解密"（harvest now, decrypt later）威胁模型——攻击者当下囤积加密流量，待量子计算机成熟后解密。混合方案兼顾传统算法（防当前攻击）与 PQC 算法（防未来量子攻击），且符合 NIST ML-KEM（FIPS 203）标准。

**核心工程思想**：默认优先而非可选机制是本 JEP 最重要的工程决策。只要应用不强制指定密钥交换算法，JDK 27 升级后 TLS 握手自动升级至后量子防护，对存量代码零侵入。对等端（如未升级的老版本 JDK）若不支持混合方案，TLS 协商自动回退至传统 ECDH，不影响连接建立。

**落地行动指南**：金融、政务等需满足数据长期保密性要求的场景应将 JDK 27 升级纳入 2026 年 Q4 安全计划。检查现有 `SSLParameters.setNamedGroups()` 调用是否强制排除了新方案；在金融网关与数据库连接中验证 TLS 握手升级未引入额外延迟（ML-KEM 密钥封装计算量有限，通常 <1ms 额外开销）。

---

### 2. Spring Framework 7.0.8 发布：Jakarta EE 11 基线稳固，JPA 3.2 与 Servlet 6.1 完整支持 `[Patch 维护]`

**核心增量**：2026 年 6 月 8 日发布的 7.0.8 是 Spring Framework 7.x 系列的最新维护版本。该系列将 Jakarta EE 11 作为基线（Servlet 6.1、JPA 3.2），对 Jakarta EE 12（早期阶段）提供初步适配，最低 Java 要求为 17，兼容范围覆盖至 Java 25。Spring 6.1.x 与 5.3.x 整条线均已过生命周期（EOL），Spring 6.2.x EOL 时间亦为 **2026 年 6 月 30 日**，实质上强制团队向 Spring Framework 7 迁移。

**核心工程思想**：Spring Framework 7 的 AOT 处理引擎在构建期分析应用上下文，生成 `BeanDefinitionRegistrar` 代码替换运行时反射初始化；与 GraalVM Native Image 协同时，减少 50~70% 的反射 hint 手动配置量。虚拟线程已深度集成至 `TaskExecutor` 与 `@Scheduled`，IO 密集型任务无需切换 WebFlux 即可获得高并发能力。

**落地行动指南**：当前持有 Spring Boot 3.x（基于 Spring Framework 6.x）的团队需在 2026 年 Q3 前完成迁移规划；Spring Boot 4.x 依赖 Spring Framework 7，jakarta EE 11 迁移是其中最大的破坏性变更点，应使用 OpenRewrite 的 `UpgradeSpringBoot_4_0` recipe 进行自动化代码重写。

---

### 3. GraalVM 转向月度特性发布节奏，25.1 预计 2026 年 6 月落地 `[发布策略]` `[云原生]`

**核心增量**：Oracle 宣布 GraalVM 从季度更新节奏转向**月度特性发布（monthly feature releases）**，GraalVM 25.x LTS 仍继续季度 CPU（Critical Patch Update）安全维护。25.1 预计 2026 年 6 月发布，后续 25.2、25.3 按月跟进。GraalVM 25.0 作为稳定长期支持轨道持续接收安全与关键 bug 修复。

**核心工程思想**：月度节奏旨在加快 Native Image 特性（如更精准的可达性分析、Build-time 初始化诊断改进、SBOM 生成）到达生产用户的速度。这也与 GraalVM 和 OpenJDK 在某些 JEP 实现上的分叉策略相关——GraalVM 明确允许 AI 生成代码贡献（与 OpenJDK 的 GenAI 禁令形成直接对比）。

**落地行动指南**：对于使用 GraalVM Native Image 的 Quarkus/Spring Native 项目，建议跟踪 GraalVM 月度发布以获取 Native Image 构建性能与兼容性改进；在 CI/CD 中将 GraalVM 版本锁定策略从"最新稳定"调整为"按月评估更新"。

---

### 4. JEP 531 惰性常量（第三预览）：JIT 的最佳搭档，消除初始化竞争 `[Preview 预览特性]`

**核心增量**：JEP 531（Lazy Constants，第三预览）在 JDK 27 中移除了底层方法 `isInitialized` 和 `orElse`，新增 `Set.ofLazy(...)` 工厂方法，完善了惰性版本的三种基础集合类型（List、Set、Map）。惰性常量本质上是 `java.lang.LazyConstant<T>` 容器：持有一个数据值，首次访问前由计算函数初始化，此后不可变。

**核心工程思想**：惰性常量解决了两类痛点：(1) **初始化时序问题**——现有 `static final` 字段在类加载时强制初始化，导致依赖关系复杂时的启动顺序陷阱；(2) **JIT 优化机会**——JIT 编译器可将 `LazyConstant` 的值识别为"一旦初始化即永久不变"，从而应用比普通字段更激进的常量折叠与内联优化，特别适合框架配置对象、应用上下文单例等重量级延迟初始化场景。

**落地行动指南**：目前仍处预览阶段，需 `--enable-preview`，不建议生产使用。架构师可提前在配置容器、懒加载注册表等场景做原型验证，对比 `Supplier` 与 `LazyConstant` 的性能差异。

---

### 5. JEP 533 结构化并发（第七预览）：异常类型参数化，Loom 拼图趋近完整 `[Preview 预览特性]`

**核心增量**：JEP 533 在第七轮预览中为 `StructuredTaskScope` 和 `Joiner` 接口新增第三个类型参数 `R_X`，明确表示 `join()` 方法可抛出的异常类型。这一改动使结构化并发作用域的异常契约在类型系统层面得到精确表达，消除了此前需要捕获 `Exception` 的宽泛异常处理模式。

**核心工程思想**：结构化并发要求所有子任务的生命周期严格限定在父作用域内，天然防止"线程泄漏"与"孤儿线程"问题——这是传统 `ExecutorService` + `Future` 模式的顽疾。第七次预览说明 API 仍在打磨，但核心并发模型已高度稳定。与虚拟线程（Project Loom 的另一半）组合，结构化并发提供了对 IO 密集型服务编排的高可读性替代方案，代码结构天然映射调用树形态。

**落地行动指南**：现有基于 `CompletableFuture` 或 `ExecutorService` 的微服务内聚合调用（如并行调用多个下游服务、超时熔断组合）可逐步评估向结构化并发迁移。建议在非关键业务模块先行试点，积累运营经验后再推广。

---

### 6. Spring AI 1.1.5 生态成熟：MCP Java SDK 贡献 Anthropic，20+ 模型后端 + Advisors API `[AI 集成]`

**核心增量**：Spring AI 1.1.5（2026 年 4 月 27 日发布）是当前最新稳定版，支持超过 20 个 AI 模型提供商（OpenAI GPT-5、Anthropic Claude、Google Gemini、Azure OpenAI、Ollama 等）。Spring 团队将 MCP Java SDK 贡献给 Anthropic，成为 Model Context Protocol 的官方 Java 实现。Spring Boot 4.1 应用可同时作为 MCP 服务端（将业务逻辑暴露为 MCP Tool）和客户端，实现与任意 MCP 兼容 Agent 的双向互操作。

**核心工程思想**：Advisors API 提供了标准化的 RAG 管道与对话记忆管理抽象；Spring AI Agentic Patterns（Agent Skills 模块化设计）将 Agent 能力单元化，支持跨应用复用。这标志着 Java 企业级 AI 集成已从"LLM 调用"成熟到"多步骤 Agent 编排"阶段。

**落地行动指南**：评估从 LangChain4j 切换至 Spring AI 的迁移成本（Spring AI 的 Advisors API 更具 Spring Boot 开发体验一致性，但 LangChain4j 的工具生态仍更广）；使用 Spring AI 的 `InetAddressFilter`（Boot 4.1）和 MCP 工具调用的权限边界控制应对 Prompt Injection 与 Tool Abuse 安全风险。

---

### 7. Hibernate ORM 7.2.x 持续迭代：Jakarta Data 1.0 全面落地，Apache License 纯净化 `[稳定维护]`

**核心增量**：Hibernate ORM 7.2 系列（最新版本在 2026 年持续补丁）完整实现 Jakarta Persistence 3.2 与 Jakarta Data 1.0，是首个完全支持 Jakarta EE 11 的生产就绪版本。同时，7.x 是 Hibernate 首个全系列采用 Apache Software License 的版本，彻底解决了此前 LGPL 许可证在企业法务层面的引用顾虑。

**核心工程思想**：Jakarta Data 1.0 引入了基于接口的 Repository 抽象（类似 Spring Data JPA 的体验），但标准化为 Jakarta 规范层，使其可在 Quarkus Panache、Micronaut Data 与 Hibernate 之间实现统一编程模型。JPA 3.2 的增量查询 API（`CriteriaBuilder` 改进）与 UUID 主键原生支持减少了大量样板代码。

**落地行动指南**：从 Hibernate 6.x 升级至 7.x 的主要破坏点在于 Jakarta EE 11 包名切换（`javax.persistence.*` → `jakarta.persistence.*`）与部分废弃 API 移除；建议使用 Hibernate 官方迁移指南配合 OpenRewrite 脚本批量重写。

---

### 8. OpenJDK 对生成式 AI 贡献实施禁令，GraalVM 走分叉路线 `[治理]` `[行业风向]`

**核心增量**：OpenJDK 于近期正式发布生成式 AI 贡献政策，**明确禁止**将 LLM、扩散模型或类似深度学习系统生成的内容（包括源代码、文字、图片）提交至 OpenJDK Git 仓库、GitHub PR、邮件列表、Wiki 页面及 JBS issue 跟踪系统。贡献者将需通过 Skara 系统中的合规复选框确认遵守。有趣的是，Oracle 的 GraalVM 采取了**完全相反的策略**，允许 AI 生成代码贡献，形成同一公司旗下两个开源项目的明确政策分叉。

**核心工程思想**：OpenJDK 的禁令基于三点考量：AI 生成代码的审查负担（大量貌似正确实则错误的代码冲淡人工审查资源）、JVM 安全性与正确性风险（JDK 是安全关键基础设施），以及知识产权不确定性（AI 训练数据版权问题可能导致 OCA 许可违规）。此政策对 OpenJDK 贡献者工作流影响显著——私下使用 AI 协助理解与调试代码被明确允许，但提交物的"手写"属性须得到保证。

---

## 🟢 Tier 3：行业风向与速递

- **JDK 28 Expert Group（JSR 403）正式成立**：成员包括 Simon Ritter（Azul Systems）、Iris Clark（Oracle，规范负责人）、Stephan Herrmann（Eclipse Foundation）、Christoph Langer（SAP SE）。JDK 28 Early-Access Build 0 与 Build 1 已对外发布，GA 目标为 2027 年 3 月，届时 JEP 401 将以预览形式随行。

- **JDK 27 后量子安全质量外展（Quality Outreach）启动**：Inside.java 于 5 月 17 日发布 JEP 527 质量外展公告，呼吁社区在主流库（Netty、OkHttp、Apache HttpClient）与框架中测试后量子 TLS 握手兼容性，特别关注客户端 TLS 配置中硬编码 `NamedGroup` 的潜在问题。

- **JEP 526（Argon2 原生支持）进入 Submitted 状态**：将 RFC 9106 标准的 Argon2 密码哈希算法集成至 JDK，提供 `java.security` API 下的标准化接口，目标 JDK 为 27 或 28。此前 Java 生态需依赖 Bouncy Castle 或 Spring Security 封装的第三方实现，原生支持可统一算法实现、简化 FIPS 合规路径。

- **JDK 26 AOT with ZGC（JEP 516）正式发布**：JDK 26 引入了支持 ZGC 的 AOT 对象缓存，解除了 JDK 24/25 中 Leyden AOT 特性不兼容 ZGC 的限制。低延迟服务（金融交易、实时报价）现可同时享受 ZGC 亚毫秒停顿与 AOT 启动加速。

- **Quarkus 3.36.3 发布，3.33 为当前 LTS**：Quarkus 维持每 4~6 周一个 minor 版本的高速节奏，3.33 LTS 支持至 2027 年 3 月 25 日。Quarkus 已发布官方 Leyden 集成博文，描述在 native image 之外的 Leyden AOT 缓存集成方案，形成与 Spring Boot 同步的 Leyden 覆盖格局。

- **Gradle 9.6.0 发布（2026-06-20）**：Configuration Cache 精确追踪通过系统属性（`org.gradle.project.<n>`）与环境变量（`ORG_GRADLE_PROJECT_*`）传入的项目属性变更，仅在相关属性被配置阶段实际使用时才使 Cache 失效，消除 CI 环境中大量误失效场景，显著提升大型多模块项目构建缓存命中率。

- **Micronaut 5.0.2 安全补丁发布**：本次补丁修复了两项安全漏洞：`DefaultHttpClient` 在跨域重定向时转发敏感请求头（SSRF 相关），以及潜在的无限重定向循环。更新同步覆盖 Micronaut Data、Micronaut for Spring、Micronaut LangChain4j、Micronaut GraphQL 等子模块。

- **JDK 28 专家组同期启动与 JDK 27 Rampdown 并行推进**：OpenJDK 社区呈现出清晰的"双轨并行"研发节奏——JDK 27 进入质量固化阶段的同时，JDK 28 特性征集与讨论已在邮件列表活跃展开（JEP 401 主线集成讨论即在 `jdk-dev@openjdk.org` 持续进行）。

- **GlassFish Arquillian Connectors Suite 发布**：为 Jakarta EE TCK 测试提供官方 Arquillian 连接器套件，简化 Jakarta EE 规范合规性测试流程，对 GlassFish 8.0 用户的 Jakarta EE 11 认证验证有直接价值。

- **Spring Boot 3.5 EOL 倒计时（2026-06-30）**：Spring Boot 3.5 将于本月底到期，Spring Boot 6.2.x（Spring Framework 基线）亦同步 EOL，仍在使用这些版本的团队面临断崖式升级压力，需立即规划向 Spring Boot 4.x 的迁移路径。

- **Spring I/O 2026 会后资料发布**："The Spring AI Ecosystem in 2026: From Foundations to Agents"成为会议热门议题，Spring gRPC、Leyden AOT 集成与 MCP 工具开发实践等专题同步引发广泛讨论，会后视频与讲义已陆续上线。

- **虚拟线程 vs WebFlux 选型争论持续成熟**：2026 年社区基准数据趋于一致——对于 IO 密集型典型场景（数据库查询、HTTP 调用），虚拟线程 + 阻塞式编程的吞吐量已与 Project Reactor 响应式编程持平甚至略优（因消除了响应式操作符本身的调度开销），但 Reactor 在背压控制、复杂流处理与超低内存场景仍具独特价值；新项目推荐优先选择虚拟线程简化代码复杂度，对背压有强需求的数据管道继续使用 WebFlux。
