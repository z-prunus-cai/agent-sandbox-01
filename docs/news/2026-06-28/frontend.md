# JavaScript/TypeScript 语言演进与前端工程化情报简报
**日期：2026-06-28 | 情报窗口：过去 48 小时核心事件 + 近 8 天扩展范围**

---

## 🔴 Tier 1：核心突破与范式转移

### 1. TypeScript 7.0 RC 正式落地："Project Corsa" Go 原生编译器颠覆十年构建范式

`[运行时革新]` `[范式转移]`

**事件全景**

2026 年 6 月 18 日，Microsoft 正式发布 TypeScript 7.0 RC（`npm install -D typescript@rc`），这是 TypeScript 历史上最具架构意义的版本——编译器底层从 TypeScript/JavaScript 自举实现（内部代号 "Strada"）全量移植为 Go 语言原生实现（"Project Corsa"）。官方基准显示，在 VS Code（150 万行）代码库上，项目加载时间从 77.8 秒降至 7.5 秒（约 10x）；`tsc --build` 全量构建在多数真实大型项目中提速 8–10 倍；峰值内存使用约为原实现的 **50%**。这不仅仅是一次性能优化，它彻底改变了"TypeScript 类型检查是构建流水线最慢环节"这一持续多年的工程共识，并为 CI 等待时间、IDE 响应速度与 monorepo 规模化带来系统性的质变。

从历史背景来看，TypeScript 一直是用 TypeScript 自举编译（JS 层）的，这导致 V8 JIT 无法消除语言本身的跨运行时调度与 GC 停顿开销；随着前端 monorepo 工程规模指数式扩大，`tsc` 成为众多团队 CI 最大瓶颈已是不争的事实。Go 移植方案不是重写语义，而是在保留相同类型检查算法的前提下，通过原生二进制执行与共享内存多线程（goroutines）彻底释放硬件并发能力。

**底层原理解析**

Project Corsa 的核心设计原则是"语义等价，实现替换"——团队以文件为粒度，逐一将原有 JavaScript 算法迁移到 Go，刻意保留相同的数据结构与类型检查语义，确保 TS 6.x 能通过的代码在 TS 7 中行为完全一致。

Go 被选中的关键理由有三：(1) GC 共享内存模型与 TypeScript 编译器的内部循环引用（符号表、类型图中大量存在 cyclic reference）天然契合，无需引入复杂的所有权追踪（相比 Rust 方案）；(2) goroutine 的轻量线程语义使编译器可在 **同一符号表上并行** 运行多个 checker worker，而传统 JS 的 Worker 线程必须序列化/反序列化符号信息；(3) 原生二进制消除了 Node.js 进程的启动与 JIT 预热开销，小型项目的 `tsc` 感知延迟从 500ms 级降至 50ms 级。

TS 7 引入了三个并行度控制旗标：`--checkers N`（类型检查 worker 数）、`--builders N`（project reference 并行构建数）、`--singleThreaded`（强制单线程，用于调试或低内存环境）。值得注意的是：**编译产物与类型声明输出与 TS 6.x 完全兼容**，现有 `.d.ts` 消费者无需任何变更。

**前端工程影响与指导**

对 CI/CD 流水线的影响立竿见影：以往在 monorepo 中耗时 8–12 分钟的 `tsc --build --incremental` 全量构建，预期在 TS 7 下降至 1–2 分钟量级，直接改善 PR 反馈循环。对开发者体验的影响：IDE 语言服务器（tsserver 现亦已 Go 化）的首次项目加载、`go to definition`、`rename symbol` 等操作在大型项目中将从"偶尔卡顿"变为"实时响应"。

**需要注意的迁移事项**：TS 7 RC 目前不支持两个 TS 6.x 遗留行为：(1) TypeScript 插件系统（`plugins` 字段在 tsconfig 中）暂不支持——使用 TSServer 插件（如 `ts-plugin-*`）的项目需等待 GA 后的插件适配窗口；(2) 部分依赖 TypeScript Compiler API（`ts.createProgram`、`LanguageService`）的工具（如 ts-morph、ts-jest）需更新至支持 TS 7 的版本。GA 版本预计在 RC 后约一个月内发布，建议在 CI 中并行运行 `typescript@rc` 进行兼容性预检，在 `--noEmit` 模式下验证类型错误数无回归后，规划 GA 后的快速升级路径。

---

### 2. Deno 2.9 `deno desktop`：TypeScript 原生运行时进军桌面应用，Electron 统治地位面临挑战

`[运行时革新]` `[范式转移]`

**事件全景**

2026 年 6 月 25 日，Deno 2.9 发布，其中 `deno desktop`（实验性）是最具战略意义的新特性：该命令将一个 Web 项目——从单个 TypeScript 文件到完整的 Next.js/Astro/TanStack Start 应用——编译为一个包含 Deno 运行时的自包含跨平台桌面原生二进制。这是 Deno 自 2018 年诞生以来最大的战略边界扩展，将 TypeScript 运行时的应用场景从服务端/CLI 延伸至桌面原生应用领域，直接挑战 Electron（90MB+ 捆绑 Chromium）的统治地位。

传统 Web 技术栈进军桌面有三条路径：Electron 捆绑 Chromium（重），Tauri 使用系统 WebView（轻但 Rust 构建链复杂），以及 NW.js（历史遗留）。`deno desktop` 与 Tauri v2 路线最为接近——**默认使用系统 WebView**（macOS 上为 WKWebView，Windows 上为 WebView2，Linux 上为 WebKitGTK），无需捆绑浏览器引擎，在快速测试中 macOS 应用体积约 **68.5 MB**，而 Electron 等效应用为 **308.9 MB**，体积差距超过 4 倍。对于需要 Chromium 一致性渲染的项目，也可切换至 CEF 模式。

**底层原理解析**

`deno desktop` 的构建流水线分三个阶段：(1) **打包阶段**：内置打包器（基于 Rolldown 同源的 Rust 图遍历模块）分析入口文件，遍历 ESM 依赖图，执行 tree-shaking，输出单文件 bundle，`npm:` 与 `jsr:` 依赖在此阶段被展开内联；(2) **运行时封包阶段**：将 bundle 与 Deno 运行时二进制通过 `deno compile` 的 V8 snapshot 机制融合，bundle 被烘焙进 V8 快照以消除解析和编译开销；(3) **安装包生成阶段**：跨平台输出原生安装格式——macOS 的 `.app`/`.dmg`、Windows 的 `.exe`/`.msi`、Linux 的 `.AppImage`/`.deb`/`.rpm`，同一台 macOS 开发机可通过交叉编译目标同时输出三平台产物，无需 CI 矩阵多平台并发构建。

WebView 通信桥采用 Deno 现有的 Web API 标准（`fetch`、`WebSocket`、`localStorage`），前端 JavaScript 与 Deno 后端通过 `window.deno.invoke()` 进行 IPC，接口风格刻意贴近 Tauri v2 的 `invoke()` API，降低有 Tauri 经验的团队的迁移成本。

**前端工程影响与指导**

对于当前使用 Electron 的项目，`deno desktop` 在此阶段尚不构成替换建议（实验性标注意味着 API 随时可能变动），但有三个关键价值点可立即评估：(1) **体积**：如果目标 WebView 渲染一致性可以接受，`deno desktop` 将 Electron 应用体积压缩至 1/4；(2) **零额外构建依赖**：与 Tauri 要求 Rust 工具链不同，`deno desktop` 仅依赖 Deno 本身，前端团队可以在不引入任何新语言的情况下构建桌面应用；(3) **框架集成**：明确支持 Next.js、Astro、Deno Fresh、TanStack Start、Vite SSR，覆盖当前主流元框架，绿地桌面项目可立即实验。建议在非关键内部工具（管理后台、开发者工具）上率先实验，积累生产数据，等待 GA 稳定后再评估对 Electron 存量项目的替换可行性。

---

### 3. Vue 3.6 GA + Vapor Mode 特性完整：虚拟 DOM 的终结与细粒度渲染的生产级降临

`[GA 正式版]` `[范式转移]`

**事件全景**

2026 年 6 月 11 日，Vue 3.6 正式 GA，携 **Vapor Mode**（实验性但特性完整）登场。这是 Vue 自 3.0 引入 Proxy-based 响应式以来最具架构意义的版本：Vapor Mode 将编译器目标从"生成虚拟 DOM 操作"切换为"生成直接 DOM 操作"，在多项性能基准中将组件挂载速度提升高达 **97%**，实现了与 Solid.js、Svelte 5 相当的极致渲染性能，同时 Vapor-only 组件的打包体积比传统模式缩减 **20–50%**。

这个数字背后的意义在于：Vue 生态此前的性能上限受制于 Virtual DOM diffing 的固有开销——即使 Vue 3 的 `@vue/compiler-dom` 已通过静态提升（Static Hoisting）、补丁标记（Patch Flags）等编译优化大幅削减 diff 比较量，虚拟 DOM 树的创建与内存分配开销仍然存在。Vapor Mode 从编译期彻底绕过这条路径：**vnode 不再被创建，内存分配归零**，组件更新直接操作 DOM 节点引用，性能上限从"快速 diff"跃升为"无 diff"。

**底层原理解析**

Vapor Mode 编译器（`@vue/compiler-vapor`）将 Single File Component（`.vue` 文件）编译为两类输出产物：(1) **初始化代码**：静态分析模板，提取固定结构，生成一次性的 `createNode()`/`insertNode()` 调用序列，直接构建真实 DOM 树；(2) **更新代码**：分析每个响应式绑定的依赖范围，生成精准的 `effect(() => element.textContent = ...)` 副作用函数，在 Signal 变化时直接修改对应 DOM 节点属性，无需比较旧树。

这种编译策略依赖 Vue 3.6 同步强化的**细粒度响应式追踪**——`ref()`、`computed()` 在 Vue 3.6 中的内部实现已向 TC39 Signals 提案语义对齐，依赖追踪精度提升至单个 DOM 绑定粒度（而非组件粒度）。Vapor Mode 因此要求组件内的状态访问路径在编译期可静态分析，对于包含高度动态条件渲染的组件，编译器会自动降级回传统 vnode 模式（Hybrid Mode）——这保证了向后兼容性：同一应用中 Vapor 组件与传统组件可混用，无需全量迁移。

**前端工程影响与指导**

Vapor Mode 当前处于实验性状态，**不建议**在 Vue 3.6 中直接用于生产关键路径，但以下行动项价值明确：(1) 性能密集型组件（长列表、动画密集场景）可在 `<script vapor>` 标记下启用 Vapor 并在隔离分支上进行性能基准测试；(2) Vue 3.6 本身的响应式细粒度提升（即使不启用 Vapor Mode）已为非 Vapor 组件带来约 15% 的更新性能改善，无需任何迁移；(3) 打包体积：Vapor-only 组件不引入 vnode 运行时（`@vue/runtime-core` 的 patch/diff 逻辑）——如果项目将来全量迁移至 Vapor，理论上可以排除约 15KB（gzip）的 vnode 运行时代码。Breaking Changes 清单：Vue 3.6 要求 `@vue/reactivity` 的消费者更新至同版本；`defineComponent()` 的泛型推断在严格模式下有若干类型收窄变化，TypeScript 用户建议在升级时全量运行 `vue-tsc --noEmit` 验证。

---

### 4. ES2026 规范正式发布：Temporal API 终结 Date 对象、`using` 关键字带来确定性资源管理

`[TC39 Stage 4]` `[范式转移]`

**事件全景**

ECMAScript 2026 规范于 2025 年底定稿（正式版本号 ECMA-262 17th Edition），并于 2026 年正式由 Ecma General Assembly 批准发布。这是 ES 标准史上增量特性最密集的年份之一，核心条目包括：**Temporal API**（完全替代 `Date` 对象）、**Explicit Resource Management**（`using`/`await using` 关键字）、**Iterator Helpers**（`.map()`、`.filter()`、`.take()` 等惰性迭代器方法）、**Map.prototype.getOrInsert/getOrInsertComputed**（Upsert 模式原生支持）、**RegExp.escape()**、**Float16Array**、**Math.sumPrecise()**、**Uint8Array.prototype.toBase64()/fromBase64()**、**Error.isError()**。

其中 Temporal API 的着陆是 25 年来最重要的 JavaScript 内置对象替换：`Date` 自 1995 年起沿用至今，其设计问题（可变性、时区行为不一致、月份 0-indexed、单一 API 覆盖多种日历场景）是前端工程领域最臭名昭著的历史包袱之一，催生了 `moment.js`（gzipped 约 72KB）、`date-fns`、`dayjs` 等一系列第三方库的繁荣市场。Temporal 以不可变值对象 + 显式时区/日历建模为核心，一次性解决了 `Date` 的全部设计缺陷。

**底层原理解析**

**Temporal API** 的设计核心是"类型区分"：`Temporal.Instant`（精确机器时间，无时区）、`Temporal.ZonedDateTime`（有时区有日历的完整时刻）、`Temporal.PlainDate`/`PlainTime`/`PlainDateTime`（"无时区"的本地时间语义，适用于日历 UI）。所有 Temporal 对象均为**不可变值对象**，任何"修改"操作返回新实例，彻底消除了 `Date` 的可变性陷阱（`date.setFullYear()` 原地修改引用是多少 Bug 的根源）。V8、SpiderMonkey、JavaScriptCore 三大引擎均已实现 Temporal API，主流浏览器 Chrome 126+、Firefox 139+、Safari 17.4+ 原生支持，无需 polyfill。

**Explicit Resource Management**（`using`）的底层是 `Symbol.dispose`/`Symbol.asyncDispose` 协议：声明了 `[Symbol.dispose]()` 方法的对象可被 `using` 绑定，作用域退出时运行时自动调用该方法，语义等价于 C# 的 `using` 或 Python 的 `with`。此机制的工程价值在于：数据库连接、文件句柄、事件监听器、AbortController、PerformanceMark 等需要显式释放的资源，现可在语言层面获得作用域生命周期保证，无需 `try/finally` 样板代码。TypeScript 5.2+ 已完整支持该语法。

**前端工程影响与指导**

**Temporal 迁移优先级**：(1) 停止在新代码中引入 `moment.js`，其维护团队已建议迁移至 Temporal；(2) `dayjs` 和 `date-fns` 对应的 Temporal 接口层正在社区成型，可开始评估；(3) TypeScript 6.0 已内置 Temporal 类型声明，无需额外安装 `@js-temporal/polyfill` 的类型包。**`using` 立即适用场景**：React 的 `useEffect` cleanup、Vue 的 `onUnmounted` 资源释放、测试框架中的 mock/stub 恢复——这三个场景都是 `using` 的天然受益者，可立即在 TypeScript 5.2+ 项目中采用。**Iterator Helpers** 消除了大量 `Array.from(iter).map(fn)` 的中间数组分配，对于处理大型数据集的 pipeline 代码，惰性迭代器链（`.filter().map().take(100)`）可显著降低内存峰值。

---

## 🟡 Tier 2：重要迭代与应用生态

### 1. Next.js 16.3 Preview：AI 代理基础设施跃升与 Instant Navigations

`[TS Preview]`

**核心增量**

2026 年 6 月 26 日，Next.js 16.3 Preview 发布。两大方向并行推进：**AI 工作流增强**与**导航性能跃升**。AI 方向：AGENTS.md 机制（16.2 引入）进一步扩展为 first-party skills 体系，允许 AI 代理驱动多步骤工作流（代码生成 → 热更新 → 测试运行）；新增 **Agent Browser with React Introspection**，AI 助手可通过 MCP 协议实时查询 RSC 组件树的 props 状态与服务端渲染结果，无需在代码与浏览器 DevTools 之间人工传递上下文。导航方向：**Instant Navigations** 将部分预取（Partial Prefetching，Next.js 14 引入的 RSC 增量 Shell 预取）与客户端 SPA 跳转体验融合，在路由跳转时优先使用预取的 Shell 数据渲染骨架屏，同步发起数据请求，将用户感知的"页面切换延迟"降至视觉零延迟级别，补齐了 App Router 相较于传统 SPA 在导航响应感上的最后一块短板。

**核心工程思想**

Instant Navigations 的关键技术是"预取 Shell + 流式填充"：Next.js 在链接进入视口时预取目标路由的 RSC 静态 Shell（含 Suspense 骨架），点击时立即使用该 Shell 渲染，同时流式注入动态数据部分，用户界面在点击瞬间即有视觉反馈，而非等待完整数据就绪。这本质上将 SPA 的即时响应感与 RSC 的服务端数据能力无缝合并。

**落地行动指南**

16.3 Preview 处于预览阶段，建议在 `canary` 频道测试。Instant Navigations 在 App Router（`next/link`）下自动启用，Pages Router 用户暂不受益。Agent Browser 功能依赖 `next-devtools-mcp` 包，需手动安装集成。

---

### 2. Angular 22 "Signal-First"：Zone.js 进入历史，Zoneless 成为新生代默认

`[GA 正式版]` `[Breaking Changes]`

**核心增量**

2026 年 6 月 3 日 GA 的 Angular 22 是 Angular 自 v14 引入 Signals 以来三年渐进迁移的终章。两项决策标志着架构拐点：**`ChangeDetectionStrategy.OnPush` 成为所有新组件的默认策略**（打破"OnPush 是性能优化可选开关"的旧共识），以及 **Signal Forms 正式稳定**——`FormSignal` 替代 `FormControl.valueChanges` 这个 Observable，组件销毁时依赖图自动清理，无需手动 `unsubscribe`。无选择器组件（Selectorless Components）正式落地，组件直接通过 import 引用，无需 selector 字符串。Zone.js 明确进入"长期维护模式"，Angular 23 起不再为其添加任何新特性。

**核心工程思想**

OnPush 默认化的工程意义是改变调度契约：原 Default 策略下，Zone.js 在任何异步事件后触发全组件树脏检查；OnPush + Signal 组合让渲染调度接近 Solid.js 的细粒度——Angular 通过 Signal 依赖图精确知晓哪些视图节点需更新，大型应用的变更检测开销大幅收窄。

**落地行动指南**

Breaking Changes：TypeScript 最低版本升至 **6.0**，Node.js 最低版本升至 **22**。OnPush 默认化对存量代码中手动 `ChangeDetectorRef.markForCheck()` 的场景存在静默行为变化，升级前须全量搜索并覆盖测试。Signal Forms 提供与 `ReactiveFormsModule` 并行的迁移路径，可渐进替换，无需全量重写。

---

### 3. React Compiler 1.0 稳定发布：自动记忆化终结手动性能优化时代

`[GA 正式版]`

**核心增量**

React Compiler 于 2025 年 12 月进入 1.0 Stable（随 React 19 一同被纳入 Next.js 16 和 Remix 3 的生产配置默认项）。编译器在构建时静态分析组件依赖图，自动插入等价于 `useMemo`/`useCallback`/`React.memo` 的记忆化逻辑，开发者无需任何手动注解。实际生产数据：Sanity Studio 报告渲染时间与延迟降低 20–30%，1411 个组件中 1231 个被成功编译优化；Meta Quest Store 复杂商品页面交互延迟大幅改善。整体基准显示，复杂应用的重渲染次数减少 20–60%，首屏加载速度提升约 12%。

**核心工程思想**

编译器基于 **Forget**（React 内部代号）的数据流分析，在每次 render 调用中追踪变量的"稳定性"——若某个值在前后两次 render 间保证引用不变（如 `useState` 的 setter、在 render 外创建的常量），则不将其作为依赖项。这与手写 `useMemo` 依赖数组的等价语义完全一致，但由编译器静态保证正确性，消除了手写依赖数组遗漏或冗余的 Bug 来源。

**落地行动指南**

通过 `babel-plugin-react-compiler` 或 `@vitejs/plugin-react-compiler` 在现有项目启用。编译器会跳过其无法安全分析的组件（如直接修改 DOM 或 mutable ref 的非标准模式），打印 skip 日志便于排查。保留 `useMemo`/`useCallback` 作为精细控制逃逸口，`eslint-plugin-react-compiler` 提供规则检测与编译器不兼容的 Anti-Pattern。

---

### 4. Bun 1.3：零配置全栈运行时、原生 SQL 客户端与 HTML 直执行

`[GA 正式版]`

**核心增量**

Bun 1.3 是 Bun 自 1.0 以来功能覆盖度飞跃最大的版本，确立了"全栈 TypeScript 运行时"的市场定位。三大亮点：(1) **零配置前端开发** — `bun index.html` 直接启动开发服务器，自动解析 ES 模块、转译 TypeScript/JSX、热重载，无需 Vite、Webpack 或任何配置文件，首次实现了"从文件到运行"的零摩擦前端开发体验；(2) **Bun.SQL** — 内置统一数据库 API，原生支持 PostgreSQL、MySQL、MariaDB 与 SQLite，零外部依赖，以 JavaScriptCore 引擎的底层 C++ 绑定实现，连接延迟远低于 `pg`/`mysql2` 等纯 JS 驱动；(3) **性能基准** — HTTP 吞吐量 14,320 req/s，是 Node.js（5,240 req/s）的 2.7 倍，`bun install` 依赖安装速度是 npm 的 10–20 倍（二进制 lockfile + zero-copy clone）。5 月更新追加 `Bun.Archive` tarball API、`Bun.JSONC` 注释 JSON 解析器与 `Bun.build` 的 metafile 分析能力。

**核心工程思想**

`bun index.html` 的底层是 Bun 内置的 HTML 解析器 + JavaScriptCore 直接执行管道，完全绕过了 Node.js 生态的多层工具链抽象（webpack loader → babel transform → dev-server 中间件）。Bun.SQL 使用连接池 + pipeline 模式，同一个 TCP 连接上并发发送多个查询，吞吐量在高并发场景下接近原生 C 驱动。

**落地行动指南**

零配置前端适合绿地小型项目或快速原型；大型项目仍建议 Vite 8（更成熟的插件生态）。`bun test` 新增 `--shard` 支持，monorepo CI 测试分片并行化可即时采用。Node.js 兼容性约 98%，迁移前运行 `bunx knip` 排查不支持的 Node.js API 调用。

---

### 5. Vite 8 正式版：Rolldown 统一 Dev/Prod 流水线，10–30x 构建提速落地生产

`[GA 正式版]`

**核心增量**

Vite 8（2026 年 3 月 12 日 GA）是 Vite 架构的根本性重构：以 Rust 原生打包器 **Rolldown** 替换原有 esbuild（Dev 时依赖预构建）+ Rollup（生产打包）的双引擎架构，Dev 与 Prod 首次使用同一底层引擎。官方基准：生产构建速度提升 **10–30x**；Full Bundle Mode（实验性）显示 Dev Server 启动快 **3x**、完整热重载快 **40%**、网络请求减少 **10x**。统一引擎还解锁了原先双引擎架构无法实现的能力：模块级持久化缓存（跨重启的增量 HMR）、更灵活的 Chunk 分割策略、Module Federation 官方支持。新增特性：内置 Devtools（依赖分析、HMR 追踪）、`resolve.tsconfigPaths: true`（TypeScript 路径别名原生解析）、WebAssembly SSR 支持、浏览器 console 日志转发至终端。

**落地行动指南**

Vite 7 → 8 几乎无配置迁移成本，大多数项目零修改升级。重点排查：(1) 依赖 Rollup 内部 API（`options.plugins` 中调用 `rollup.*` 私有方法）的插件需更新；(2) 8.1 已修复 8.0 中 Rolldown dev 模式高内存消耗问题（7x 于 Vite 7），通过 `server.rolldownOptions.memoryThresholdMB` 配置内存压力阈值；(3) Node.js 最低要求 ≥ 20。

---

### 6. Svelte 5 生产化成熟：模板内联声明与 TypeScript 6.0 语言工具全面支持

`[GA 正式版]`

**核心增量**

Svelte 5（2024 年 10 月 GA）在 2026 年完成了"从发布到生产成熟"的过渡，5.56.0 落地了最受期待的 **模板内联声明（Template Inline Declarations）**：允许开发者直接在 HTML 标记内使用 `let x = derive(...)` 声明派生值，将派生状态的声明位置与使用位置共置，最大化代码的局部性（locality），消除 Runes 模式下"逻辑与视图被 `<script>` 块强制分离"的摩擦。编译器将其视为等价的 `$derived()` Rune 处理，零运行时额外开销。`svelte-language-tools` 同步完成了 TypeScript 6.0 适配（语言服务器、svelte2tsx、svelte-check 全系更新），Svelte 项目的 `strict: true` 类型推断现在正确响应 TS 6.0 的新默认值。

**核心工程思想**

Svelte 5 的 Runes（`$state`、`$derived`、`$effect`、`$props`）是**编译期 Signal**——编译器在构建时将其转换为高度优化的原生 DOM 操作代码，运行时零框架 overhead，与 Vue 3.6 Vapor Mode 同属"Signal 编译策略"阵营，但 Svelte 先行落地了完整生产级实现。

**落地行动指南**

模板内联声明向上兼容，无需迁移。`vite-plugin-svelte@7.1.0` 升级激活 SSR 模块优化器，SvelteKit SSR 热更新速度可观提升，建议随 Vite 8 同步升级。Svelte 4 语法在 Svelte 5 项目中继续有效，存量大型代码库可按组件渐进迁移至 Runes。

---

## 🟢 Tier 3：行业风向与速递

- **TypeScript 7.0 RC CI 预检窗口开启**：`npm install -D typescript@rc` 并以 `--noEmit` 试运行可即时验证兼容性，建议所有团队在 GA 前完成预检，重点排查依赖 `ts.createProgram` 等 Compiler API 的工具链兼容性（ts-morph、ts-jest 等需关注更新进展）。

- **TC39 Signals 提案持续推进**：目前处于 Stage 1，Polyfill（`@tc39/signals`）已达实验级稳定，Angular、Vue、Solid、Svelte、Preact、Qwik、MobX 等主流框架核心维护者持续参与联合设计。Lit 已提供 Signal 集成层，Web Components 项目可立即实验。Stage 2 预计 2027 年中进入讨论。

- **ES2026 Map.prototype.getOrInsert 浏览器实现中**：Chrome 与 Node.js 的 V8 实现正在进行中，终结"先 `has` 后 `set`"的低效双查找模式，`map.getOrInsert(key, defaultValue)` 与 `map.getOrInsertComputed(key, () => computeValue())` 即将原生可用。

- **Iterator Helpers Stage 4 确认进入 ES2026**：`.map()`、`.filter()`、`.take()`、`.drop()`、`.flatMap()`、`.reduce()`、`.toArray()` 惰性方法全部着陆，消除大量 `Array.from()` 中间数组分配，对大型数据集 pipeline 处理代码有实质性内存优化价值。

- **Deno 2.9 `--bundle` 实验标志**：打包为原生二进制前先行 tree-shaking 并输出单模块，重度依赖 npm 包的项目最终二进制体积可显著压缩，与 `deno desktop` 配合使用时对 WebView 应用体积指标改善明显。

- **Biome v2.3 类型感知 Lint 不依赖 tsc**："Biotype"架构实现自有类型推断引擎，无需调用 TypeScript 编译器即可执行类型级 Lint 检查，全量 Lint 流水线速度测试中从 ESLint + Biome 的 81 秒缩至 2.5 秒（Oxlint + Oxfmt 组合）；`@oxlint/migrate` 与 `oxfmt --migrate=prettier` 工具化降低迁移摩擦。

- **Nuxt 3 EOL 倒计时进入最后一个月**：Nuxt 3 End of Support 定于 **2026 年 7 月 31 日**，最新稳定版 Nuxt 4.4.8，使用 Nuxt 3 的团队迁移至 Nuxt 4 已进入最后窗口期，应立即启动。

- **React Server Components CVE-2025-55182 修复确认**：2025 年 12 月披露的 RSC Flight 序列化协议 RCE 漏洞（React2Shell，CVSS 9.8），Next.js 16.1+ 与 React 19.1+ 均已包含完整修复，尚未升级的 Next.js 13/14 项目面临高危风险敞口，升级为强制优先项。

- **Angular 22 升级触发 TS 6.0 最低版本要求**：Angular 22 强制 TypeScript ≥ 6.0，同步触发所有 Angular 项目的 TS 5.x 退出计划；TS 6.0 的 `types: []` 空数组默认值变更（需显式声明 `"types": ["node"]` 等）是升级中最常见的构建失败原因，建议配套检查 tsconfig 继承链中所有层级的 `types` 字段配置。

- **WebStorm 2026.1 内置 TypeScript Go 引擎支持**：JetBrains 已在 2026.1 版本中集成对 `tsgo`（Go 原生 TypeScript 编译器）的 Service-powered TypeScript Engine 支持，IDE 项目加载、代码补全与错误检测性能获得与 VS Code 等量级的 10x 提升，JetBrains 系 IDE 用户可在"TypeScript Engine"设置中切换启用。

- **SvelteKit Adapters 正式支持 Node.js 24 LTS**：`@sveltejs/adapter-vercel@6.2.0` 与 `@sveltejs/adapter-auto@7.0.0` 增加 Node.js 24 serverless 与 edge function 官方支持，为 SvelteKit SSR 部署提供更长生命周期运行时基础。

- **Astro + Cloudflare 深度整合持续推进**：自 Cloudflare 2026 年 1 月收购 Astro 后，Astro 5.x 的 Server Islands 与 Cloudflare Workers/Pages 深度集成已在路线图中，零 JS 默认架构 + 边缘计算原生的元框架定位持续分化主流元框架格局。

- **React Native 新架构（Bridgeless Mode）生产就绪**：React Native 0.78 将 New Architecture（JSI + Fabric + TurboModules）标记为默认启用，Bridgeless 模式消除旧版 Bridge 的异步序列化开销，原生模块调用延迟从毫秒级降至微秒级，React Native 社区状态管理从 Redux（38%）持续向 Zustand + TanStack Query 格局迁移。
