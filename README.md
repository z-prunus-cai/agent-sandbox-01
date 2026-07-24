# bean-lifecycle-lab

一个**能跑、会自己打印证据**的 Spring Boot 4.1 工程，把 Spring bean 生命周期的完整回调链（note `003` Table A：17 步 + 3 步销毁）从容器启动走到关停，每个回调都带全局递增序号打印到控制台。

> 配套精读文档：**[`docs/lifecycle-explained.md`](docs/lifecycle-explained.md)** —— 逐步精讲，每条结论都对应一行实测输出。
> 实测输出存档：**[`docs/transcript.txt`](docs/transcript.txt)**。

## 运行

```bash
mvn -q compile
mvn -q exec:java -Dexec.mainClass=com.example.lifecycle.LifecycleDemoApplication
```

- Spring Boot **4.1.0**（Spring Framework 7.1.x）、Java 21。
- 无 Web、无数据库，纯 `main()` 驱动，方便看清“启动 / 运行 / 关停”三阶段。
- Spring Boot 4.x 已移除 `spring-boot-starter-aop`，本工程用 `spring-aop`（随 `spring-context` 传递）+ `aspectjweaver` 直接启用 `@Aspect`。

## 它演示了什么

| 分支 | 靠哪个类逼出来 |
|---|---|
| 完整链 0→13、D1→D3 | `OrderService`（同时实现 5 个 Aware + `InitializingBean` + `DisposableBean` + `SmartLifecycle` + `@PostConstruct/@PreDestroy` + 自定义 init/destroy） |
| step 0 短路 + 0b | `ShortCircuitBean` + `bpp/ShortCircuitBPP` |
| step 3 三级缓存 | `CircularA` ↔ `CircularB`（需 `allow-circular-references=true`） |
| step 12 全就绪回调 | `AllSingletonsReadyReporter` |
| 原型：有初始化无销毁 | `PrototypeBean` |
| D3 close() 赢过 destroy-method | `ResourceHolder`（`AutoCloseable`） |
| 代理只在 step 10 出现 / 自调用绕过 | `LoggingAspect` + `OrderService.placeOrder` |
| 把 0/1/2/3/4/5/7/10/D1 打印出来 | `bpp/Tracking*BPP`（探针，只观察不改行为） |

“缺席即证据”：`ShortCircuitBean`、`PrototypeBean` 里那些标着 `SHOULD NOT PRINT` 的方法一次都不会打印——那正是短路/原型语义的证明。