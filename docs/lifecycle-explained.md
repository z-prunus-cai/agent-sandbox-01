# Spring Bean 生命周期 —— 用可运行的证据走一遍

> 配套 note `003` 的 Table A（17 步 + 3 步销毁）。这里把那张抽象表变成一个**能跑、会自己打印证据**的 Spring Boot 4.1 工程：每一个回调都带全局递增序号打印到控制台，读控制台就是读“对象被一层层装配起来”的过程。
>
> - 运行环境：Spring Boot **4.1.0**（Spring Framework 7.1.x）、Java 21。
> - 一键运行：`mvn -q compile exec:java -Dexec.mainClass=com.example.lifecycle.LifecycleDemoApplication`
> - 完整实测输出存档：[`docs/transcript.txt`](./transcript.txt)。下文所有 `seqNN` 都能在那份存档里按序号定位。

本文所有结论**不是背书，是实测**。凡是文中说“某步此刻发生”，都对应存档里的一行带序号输出；凡是说“某步不发生”，都对应存档里**该行的缺席**（那些故意写着 `SHOULD NOT PRINT` 的方法一次都没打印）。

---

## 0. 这台“机器”里放了什么

为了把 note 003 里**没展开**的分支全部逼出来，工程里放了这些 bean 和 BPP：

| 角色 | 类 | 用来演示 |
|---|---|---|
| 主角 | `OrderService` | 一个 bean 同时实现 5 个 Aware + `InitializingBean` + `DisposableBean` + `SmartLifecycle` + `@PostConstruct/@PreDestroy` + 自定义 init/destroy-method —— 把 1'→13、D1→D3 全走一遍 |
| 构造器依赖 | `PaymentGateway` | 证明 step 1“造我之前先造它” |
| 字段依赖 | `InventoryClient` | 证明 `@Autowired` 字段在构造器里还是 null |
| 循环依赖 | `CircularA` ↔ `CircularB` | 逼出 step 3 `getEarlyBeanReference`（三级缓存） |
| 短路 | `ShortCircuitBean` + `ShortCircuitBPP` | 逼出 step 0 短路路径与 0b |
| 原型 | `PrototypeBean` | 证明原型**有初始化、无销毁** |
| 关闭资源 | `ResourceHolder` | 证明 D3 里 `close()` 和 destroy-method 互斥 |
| 全就绪 | `AllSingletonsReadyReporter` | 逼出 step 12 `SmartInitializingSingleton` |
| 探针 BPP ×5 | `bpp/*` | 把 0/1/2/3/4/5/7/10/D1 这些“内建 BPP 静默执行”的步骤打印出来 |
| 切面 | `LoggingAspect` | 让自动代理创建器在 step 10 把 `OrderService` 包成 CGLIB 代理 |

探针 BPP 会看到**容器里的每一个 bean**（几十个 Spring 内部基础设施 bean），所以它们只对 `Seq.TRACKED` 里那几个演示 bean 打印，其余静默，保证控制台干净。

---

## 1. 主角的一生：字段是怎样被一格一格填满的

这是全文最值钱的一段证据。`OrderService` 在每个回调里都打印一次**字段快照**，于是“哪个字段在第几步才有值”不再是口诀，而是四行实测：

```
seq39  STEP 1'   构造器      gateway=SET inventory=null timeout=0  name=null        ctx=null pool=null
seq43  STEP 6    setBeanName gateway=SET inventory=SET  timeout=30 name=orderService ctx=null pool=null
seq47  STEP 7a   setAppCtx   gateway=SET inventory=SET  timeout=30 name=orderService ctx=SET  pool=null
seq49  STEP 7b   @PostConstruct 建线程池                                                       pool=SET
```

逐格读这四行：

- **seq39（构造器 1'）**：只有 `gateway` 有值——它是构造器参数。`inventory`（`@Autowired` 字段）是 `null`，`timeout`（`@Value`）是 `0`。**这一行就是“为什么不能在构造器里读 `@Autowired` 字段”的铁证**：那些字段的注入要到 step 5 才发生。
- **seq43（step 6 `setBeanName`）**：`inventory` 已 `SET`、`timeout` 已 `30`。也就是说，**等 Aware 回调开始时，属性注入（step 5）早已完成**。所以在任何 Aware 方法或 `@PostConstruct` 里读注入进来的依赖，都是安全的。
- **seq47（step 7a `setApplicationContext`）**：`ctx` 才刚被塞进来。注意 `pool` 仍是 `null`——线程池还没建。
- **seq49（step 7b `@PostConstruct`）**：`pool=SET`。到这里对象才算“功能上完整”。

一句话：**你在第几步写代码，就只能看见那一步之前填好的东西。** 时间轴是 `1'(构造) → 5(注入) → 6/7a(Aware) → 7b(@PostConstruct)`。

---

## 2. 完整回调链逐步精讲（对照实测序号）

下面按**真实执行序**讲，每步给出存档里的证据行。序号 `seqNN` 是全局递增的，能证明先后。

### 启动前（BFPP 阶段，无对象）
容器还在 `refresh()`。此刻 `OrderService` 连对象都不存在，只有一张 BeanDefinition。`@Value("${order.timeout:30}")` 还是字面串，等注入时才由属性解析器换成 `30`。这一阶段本工程没有额外打印（BFPP 改的是“图纸”，不在回调链上）。

### step 0 · postProcessBeforeInstantiation —— “有谁想直接给我个成品吗？”
```
seq38  STEP 0   orderService     asked (returning null -> normal creation path)
seq36  STEP 0   shortCircuitBean RETURNS a ready object -> SHORT-CIRCUIT
```
对 `orderService`，探针返回 `null` → 走正常创建。对 `shortCircuitBean`，`ShortCircuitBPP` 返回了一个现成对象 → **短路**（详见 §3.1）。

### step 1 · determineCandidateConstructors —— 选构造器
```
seq29  STEP 1   paymentGateway   determineCandidateConstructors
（注意：orderService 没有这一行！）
```
**实测发现（比 note 更细）**：`orderService` 和 `resourceHolder` **没有 step 1**，而所有被扫描的、走构造器实例化的 bean（`paymentGateway`/`inventoryClient`/`circularA/B`/`prototypeBean`）都有。原因是这两个是 `@Bean` 工厂方法定义的——`createBeanInstance` 对工厂方法走的是 `instantiateUsingFactoryMethod`，**根本不做构造器选择**。这是 note 003 里“1' 有三条子路径”那句话的具体后果：走哪条实例化子路径，决定了 step 1 到底跑不跑。

### step 1' · 真正 new 出对象
```
seq30  STEP 1'  paymentGateway   constructed (must exist BEFORE OrderService can be new'd)
seq39  STEP 1'  orderService     constructor ran -> gateway=SET inventory=null ...
```
`paymentGateway` 的 seq30 在 `orderService` 的 seq39 **之前**——实证了 note 的 step 1 注解：“要 new `OrderService` 之前，容器先把构造器依赖 `PaymentGateway` 递归造完。”

### step 2 · postProcessMergedBeanDefinition —— 盘点注入点，只盘点不执行
```
seq40  STEP 2   orderService  cache injection-point metadata; nothing injected yet
```
在**已经 new 出对象之后、注入之前**触发。真正的注解处理器在这里扫描并缓存“哪些字段要注入、哪些方法是 `@PostConstruct/@PreDestroy`”。注意序号：seq40 在构造 seq39 之后、注入 seq42 之前。

### step 3 · getEarlyBeanReference —— 三级缓存，只有循环依赖才触发
```
seq03  STEP 1'  circularA  constructed
seq06  STEP 5   circularA  postProcessProperties           <- A 开始注入，需要 B
seq09  STEP 1'  circularB  constructed
seq12  STEP 5   circularB  postProcessProperties           <- B 开始注入，需要 A
seq13  STEP 3   circularA  getEarlyBeanReference INVOKED   <- 此刻把半成品 A 的早期引用交给 B
```
读这条链：A 造好开始注入（seq06）发现要 B → 容器转头把 B 整条造出来 → B 注入时（seq12）又要 A → 这时候 A 还没初始化完，容器就调 **A 的 `getEarlyBeanReference`（seq13）**，把 A 的“早期引用”塞给 B。所以 **step 3 只对被别人提前引用的那个 bean（A）触发，B 没有**。这就是“三级缓存打破循环”的实测形态。默认 `allow-circular-references=false`，本工程显式打开才看得到——这也印证了 note 里“step 3 默认跳过”。

### step 4 / 5 · 属性注入
```
seq41  STEP 4   orderService  postProcessAfterInstantiation (true -> 继续注入)
seq42  STEP 5   orderService  postProcessProperties (DI 点)
```
step 4 返回 `false` 可以整个中止注入（本工程返回 `true`）。step 5 是真正的依赖注入点——`@Autowired`/`@Value`/`@Resource` 在这里落地。证据见 §1：seq42 之后，seq43 的快照里 `inventory` 和 `timeout` 就有值了。

### step 6 · invokeAwareMethods —— 硬编码顺序的三个 Aware
```
seq43  STEP 6  BeanNameAware.setBeanName("orderService")
seq44  STEP 6  BeanClassLoaderAware.setBeanClassLoader(...)
seq45  STEP 6  BeanFactoryAware.setBeanFactory(...)
```
`BeanNameAware → BeanClassLoaderAware → BeanFactoryAware`，顺序写死在 `AbstractAutowireCapableBeanFactory#invokeAwareMethods`，与实测 seq43/44/45 完全一致。

### step 7a · 容器层 Aware（由 ApplicationContextAwareProcessor 注入）
```
seq46  STEP 7a  EnvironmentAware.setEnvironment(...)
seq47  STEP 7a  ApplicationContextAware.setApplicationContext(...)
```
这两个属于“context 层”Aware，走的是一个 BPP（`ApplicationContextAwareProcessor`），所以归在 step 7（before-init）里。注意它们**排在 step 6 那三个之后**——beanFactory 层先、context 层后。

### step 7b · @PostConstruct
```
seq49  STEP 7b  @PostConstruct init(): built thread pool -> ... pool=SET
seq50  --       inside @PostConstruct 'this' is the RAW object: com.example.lifecycle.domain.OrderService
```
seq50 是关键：**`@PostConstruct` 里的 `this` 是裸对象**，类名是 `OrderService`，不是代理。所以如果在 `init()` 里调 `this.placeOrder(...)`，`@Transactional`/切面**不会生效**——因为代理要到 step 10 才出现。

> 顺带说明 step 7 的探针行（seq48）夹在 7a 和 7b 之间：step 7“before-init”其实是一串 BPP 顺序执行，`ApplicationContextAwareProcessor`（7a）、我们的探针、`InitDestroyAnnotationBeanPostProcessor`（7b @PostConstruct）都是这串里的一环。7a 先于 7b 是稳定的，探针只是其中一环，位置由 BPP 排序决定。

### step 8 / 9 · 两个初始化方法，谁先谁后
```
seq51  STEP 8  InitializingBean.afterPropertiesSet() [init method (1)]
seq52  STEP 9  custom init-method customInit()        [init method (2)]
```
`afterPropertiesSet()`（接口回调）**先于**自定义 `init-method`（`@Bean(initMethod=...)`）。两者都在，就都跑，顺序固定。

### step 10 · AOP 织入 —— 代理在这一刻才诞生
```
seq53  STEP 10  orderService  ... -> returned a CGLIB PROXY: OrderService$$SpringCGLIB$$0
seq60  STEP 10  resourceHolder ... -> returned the raw bean
```
`OrderService` 因为有切面命中，被 `AbstractAutoProxyCreator#wrapIfNecessary` 换成 **CGLIB 代理**；`resourceHolder` 没被切面命中，返回裸对象。**从这一刻起，容器里存的、注入给别人的，是代理**——见 §4。

### step 11 · 登记销毁（只登记，不执行）
本工程无独立打印（登记动作本身无回调），但它的效果在关停阶段体现：只有被登记过的单例，关停时才会走 D1–D3。**原型和短路 bean 没被登记**，所以关停时它们一行都没有——见 §3。

### 就绪 → step 12 → step 13
```
seq61  STEP 12  (container)   afterSingletonsInstantiated() [ALL singletons ready]
seq62  STEP 13  orderService  SmartLifecycle.start() [finishRefresh -> now serving]
```
- **step 12** 是**容器级**回调，等所有非懒单例都跑完各自的 0–11 之后**一次性**触发（`EventListenerMethodProcessor` 就在这时扫 `@EventListener`）。
- **step 13** `SmartLifecycle.start()` 在 `finishRefresh()` 里触发，seq62 仍在 `SpringApplication.run()` 内部——所以它排在“PHASE 2 应用运行”横幅**之前**。

---

## 3. note 003 里“没有/略过”的分支，这里全部补齐并验证

### 3.1 step 0 短路：一票否决整条创建链
```
seq36  STEP 0   shortCircuitBean  RETURNS a ready object -> SHORT-CIRCUIT
seq37  STEP 10  shortCircuitBean  postProcessAfterInitialization boundary  (这就是 0b)
```
`postProcessBeforeInstantiation` 一旦返回非 null，`resolveBeforeInstantiation` 直接跳过整个 `doCreateBean`：**没有构造器、没有注入、没有 `@PostConstruct`、没有登记销毁**，只补跑一遍 after-init 链（note 里的 **0b**，即 seq37）。证据是**缺席**——`ShortCircuitBean` 里写着 `SHOULD NOT PRINT` 的默认构造器、`@PostConstruct`、`@PreDestroy` 在整份存档里一次都没出现。

### 3.2 自定义 init/destroy-method（step 9 / D3）
只有 `@Bean(initMethod=..., destroyMethod=...)` 能挂自定义方法，所以 `OrderService` 用 `@Bean` 注册而非 `@Service` 扫描。效果见 seq52（step 9）与 seq91（D3）。

### 3.3 D3 的互斥：close() 赢过 destroy-method
```
seq86  STEP D1  resourceHolder  postProcessBeforeDestruction boundary
seq87  STEP D3  resourceHolder  AutoCloseable.close() [close() WINS over destroy-method]
```
`ResourceHolder` 既是 `AutoCloseable` 又有一个 `customDestroy()` 方法，但用的是**推断销毁方法**。因为它是 `AutoCloseable`，适配器走 `close()` 分支，**`customDestroy()` 一次都没打印**（同样是“缺席即证据”）。对照 `OrderService`（非 `AutoCloseable`）：D2 `destroy()` 和 D3 `customDestroy()` **都执行**（seq90、seq91）——`DisposableBean.destroy()` 与 destroy-method 不互斥，`close()` 与 destroy-method 才互斥。

### 3.4 原型：有初始化，无销毁
```
seq77  STEP 7b  prototypeBean  @PostConstruct RAN (原型仍会初始化)
seq82  STEP 7b  prototypeBean  @PostConstruct RAN (第二个实例，又跑一次)
seq84  --       driver         same instance? false
（关停阶段：prototypeBean 没有任何 D 行）
```
两次 `getBean` 得到两个不同实例，各自跑完完整初始化（含 `@PostConstruct`）。但 step 11 登记被 `!mbd.isPrototype()` 挡掉，所以关停时 `PrototypeBean` 的 `@PreDestroy`（写着 `SHOULD NEVER PRINT`）**一次都没出现**。原型的销毁得你自己管。

### 3.5 SmartInitializingSingleton（step 12）
见 seq61。它证明了 note 的话：这是容器级、不属于任何单个 bean 的链，且发生在**所有**单例就绪之后。

---

## 4. 代理与“注解为什么不生效”——两行实测把坑钉死

```
seq63  --  driver        ctx.getBean(OrderService) 返回: ...OrderService$$SpringCGLIB$$0
seq64  --  driver          -> is it a CGLIB proxy? true
seq66  --  aspect        >> advice BEFORE placeOrder (我们走了代理)
seq67  --  orderService  placeOrder() body running on OrderService (proxy? false)
seq68  --  orderService  audit() body running (reached via this.audit() -> 切面被绕过)
seq69  --  aspect        << advice AFTER placeOrder
```

三件事一次说清：

1. **注入给别人的是代理**：seq63/64，`getBean` 拿到的是 CGLIB 代理。
2. **方法体内 `this` 是裸对象**：seq67 里 `proxy? false`——外面通过代理进来（seq66 切面触发），但一旦进入方法体，`this` 就是被代理包着的那个原始对象。
3. **自调用绕过代理**：`placeOrder` 内部 `this.audit()` 直接打到裸对象上，`aroundAudit` 那条 advice（写着 `SHOULD NOT PRINT`）**没出现**（seq68 前后没有 aspect 行）。这就是“同类方法互调，`@Transactional`/`@Async` 不生效”的根因。

**结论**：代理在 step 10 才诞生，所以 step 0–9 里的 `this` 全是裸对象；而任何走 `this.` 的自调用都不经过代理。Spring 里一大半“注解不生效”的问题都能用这两条解释。

---

## 5. 关停顺序：LIFO + stop 先于 destroy

```
seq85  STEP 13  orderService     SmartLifecycle.stop()          <- 先停生命周期
seq86  STEP D1  resourceHolder   ...                            \
seq88  STEP D1  orderService     ...                             |  再逐个销毁，
seq92  STEP D1  paymentGateway   ...                             |  顺序是创建的逆序
seq93  STEP D1  inventoryClient  ...                             |  (LIFO)
seq94  STEP D1  circularB        ...                             |
seq95  STEP D1  circularA        ...                            /
```

两条实测规律：

1. **`SmartLifecycle.stop()`（seq85）先于所有销毁回调**——生命周期先停，资源再拆。
2. **销毁是 LIFO**：`resourceHolder → orderService → paymentGateway → inventoryClient → circularB → circularA`，正好是创建顺序的逆序。后创建的先销毁，避免销毁时依赖已被拆掉。

`orderService` 的完整销毁链一步不落：
```
seq88 D1 postProcessBeforeDestruction  →  seq89 D1 @PreDestroy cleanup()
     →  seq90 D2 DisposableBean.destroy()  →  seq91 D3 customDestroy()
```
（`@PreDestroy` 和 D1 探针都属于“before-destruction”这一 BPP 阶段，故都标 D1；随后才是 D2、D3。）

---

## 6. 一张总账：四个内建 BPP 各插在哪几步

把 B 表（有哪些插口）和 C 表（内建功能插在哪个口）合起来，你天天用的注解其实就是四个内建 BPP 插在不同步骤上：

| 注解 / 能力 | 内建 BPP | 插在第几步（本工程实测行） |
|---|---|---|
| `@Autowired` / `@Value` | `AutowiredAnnotationBeanPostProcessor` | 1（构造器选择）、2（盘点）、5（注入） |
| `@PostConstruct` / `@PreDestroy` / `@Resource` | `CommonAnnotationBeanPostProcessor` | 2、5、7b（seq49）、D1（seq89） |
| `ApplicationContextAware` 一族 | `ApplicationContextAwareProcessor` | 7a（seq46/47） |
| `@Transactional` / `@Async` / 所有切面 | `AbstractAutoProxyCreator` | 0、3、10（seq53 造出代理） |

**排错方法**：遇到“某注解为什么在某处不生效”，查它属于哪个 BPP、插在第几步，再看你的代码在第几步——一比就知道。最典型的：`@Transactional` 属于第 4 行、插在 step 10，而 `@PostConstruct` 在 step 7b（早于 10）、自调用又绕过代理——两条一对，"为什么 @PostConstruct 里事务不生效""为什么自调用事务不生效"当场解释清楚。

---

## 附：如何自己复现

```bash
mvn -q compile
mvn -q exec:java -Dexec.mainClass=com.example.lifecycle.LifecycleDemoApplication
```

想改实验就改 `Seq.TRACKED`（决定哪些 bean 打印）、`application.properties` 里的 `spring.main.allow-circular-references`（关掉它，seq13 那条 step 3 就会消失，且循环依赖会直接启动失败）。每一步的“该出现/该缺席”都写在对应类的注释里，可以逐条对照 `docs/transcript.txt`。
