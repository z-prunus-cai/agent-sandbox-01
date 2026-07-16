# 前端语言与 Web 工程化情报简报

**情报时间窗口：2026-06-22 ~ 2026-06-24（含回溯扩展至 2026-06-03）**
**发布日期：2026-06-24**

---

## 🔴 Tier 1：核心突破与范式转移

### 1. TypeScript 7.0 RC：Go 编译器落地，编译性能跃升 10 倍

`[TS 7.0 RC]` `[范式转移]` `[编译器革新]`

**事件全景**

TypeScript 历史上最重要的单次版本发布。2026 年 6 月 18 日，Microsoft 正式发布 TypeScript 7.0 RC，核心突破是：**整个 TypeScript 编译器与语言服务均已从 TypeScript/JavaScript 迁移至 Go 语言实现**。这不是另起炉灶的重写，而是对原有类型语义的精确移植。TypeScript 历史上最大的痛点之一——大型仓库的类型检查速度——自此得到根本性解决。以 VS Code 代码库（约 150 万行 TypeScript）为基准，类型检查时间从 **77 秒降至 7.5 秒**，约 10 倍提速，是编译器基础设施领域近年来罕见的突破性工程成就。

**底层原理解析**

Go 编译器在三个维度实现性能飞跃：

- **真正并发化**：解析（Parsing）、类型检查（Type-checking）、代码生成（Emit）三阶段首次实现真正并行。Go 协程模型（goroutines）天然契合 TypeScript 编译阶段的任务划分，无需外部线程池或复杂的进程间通信。新增 `--checkers <N>` / `--builders <N>` CLI 参数用于精细调节并发度，`--singleThreaded` 保留用于调试场景。
- **内存局部性优化**：Go 的内存分配器与 GC 针对编译器工作负载（大量短寿命 AST 节点的密集分配）显著优于 V8 的通用 GC，减少了 Stop-the-World 停顿频率与时长。
- **冷启动消除**：原始实现依赖 Node.js 进程启动（JIT 预热开销），Go 二进制冷启动趋近于零，对 `tsc --noEmit` 等一次性类型检查命令影响尤为显著。`--watch` 模式底层监听引擎亦已重构，派生自 Parcel 的高可靠跨平台文件监听器，解决了历史上大型 monorepo 下 watch 偶发漏报的问题。

**7.0 的破坏性变更**（均为 6.0 弃用期的硬化）：`target: es5` 直接报错；`moduleResolution: node`（legacy）直接报错；`module: amd/umd/systemjs` 直接报错；`baseUrl` 脱离 `paths` 单独使用直接报错。

**前端工程影响与指导**

这是自 TypeScript 1.0 以来最具工程价值的单次变更。对于中大型团队：**CI 构建成本可直接减半乃至缩减至原来的十分之一**——TypeScript 类型检查通常是现代前端 CI 管道中耗时最长的单步。IDE 体验同步质变，代码补全与类型错误高亮延迟从数百毫秒降至近乎实时。**迁移准备**：立即用 `npm install -D typescript@rc` 在测试环境验证；重点审查上述四类破坏性变更是否存在于现有 `tsconfig.json`（6.0 已发弃用警告，存量修复工作量通常极低）。正式版预计 2026 年 7 月中下旬发布，建议将其排入下一版本迭代的升级计划。

---

### 2. ECMAScript 2026 定稿 + Temporal 正式进入 Stage 4：Date 时代终结

`[TC39 Stage 4]` `[ES2026]` `[日期/时间范式更替]`

**事件全景**

ECMAScript 2026（第 17 版）已于 2026 年 3 月 TC39 会议定稿，并伴随 **Temporal API 正式进入 Stage 4**——这是历经 9 年、拥有 4500 余条测试用例的最大单体 JS 提案，终于完成标准化。ES2026 一次性落地约 16 项语言特性，横跨精确计算、二进制编解码、集合代数、正则增强、异步迭代等多个基础领域，彻底告别"语言标准滞后于生态实践"的历史窘境。Node.js 26 已将 Temporal 默认开启，Chrome、Firefox 提供原生支持，TypeScript 6.0 内置类型定义，生态链路完整。

**底层原理解析**

以 Temporal 为核心展开：`Date` 对象的根本缺陷并非 API 丑陋，而是**架构性错误**——可变性（`Date.setFullYear()` 直接修改实例，并发场景引入隐性副作用）、时区语义缺失（仅存储 UTC 毫秒，`toLocaleString()` 依赖运行时 locale 致结果不可复现）、隐式类型转换（`new Date("2024-01-01")` 解析为 UTC，`new Date("2024/01/01")` 解析为本地时间，跨引擎行为不一致）。Temporal 的架构决策：**完全不可变**（所有操作返回新实例）；**精确类型分层**（`PlainDate` 无时区、`ZonedDateTime` 含时区、`Instant` 绝对时间点，类型携带的语义精确匹配业务场景）；**DST 感知**（`Duration.add()` 在 `ZonedDateTime` 上自动处理夏令时跳变，彻底消除"25 小时这天"/"23 小时那天"的历史顽疾）。

ES2026 其余高价值特性：`Map.prototype.getOrInsert(key, default)` / `getOrInsertComputed(key, fn)` 消灭 `if (!map.has(k)) map.set(k, ...)` 样板代码；`Math.sumPrecise(iterable)` 提供浮点累加精度保证，直接解决金融/数据聚合场景痛点；`Uint8Array.prototype.toHex()` / `toBase64()` / `fromHex()` / `fromBase64()` 提供原生二进制-字符串互转；`RegExp.escape(str)` 终结手写转义正则的潜在安全漏洞；`Promise.try(fn)` 统一同步抛错与异步 reject 的捕获路径；`Set` 集合代数全套（`union`/`intersection`/`difference`/`symmetricDifference`/`isSubsetOf`/`isSupersetOf`/`isDisjointFrom`）；`Float16Array` 与 DataView 扩展（面向 ML/WebGPU 工作负载）。

**前端工程影响与指导**

**立即可行的迁移路径**：Node.js 26 默认开启 Temporal，无需 flag；对于需支持旧浏览器的项目，官方 polyfill `@js-temporal/polyfill` 提供完整 API 覆盖，可逐步替换 `date-fns` / `dayjs` / `moment`。`Set` 运算方法使 Zustand/Jotai 中 immutable 集合操作代码行数减少 60% 以上，无需引入 Immer 处理集合场景。`Map.getOrInsert` 直接优化缓存管理、路由表合并等高频操作的可读性与性能（零运行时成本，纯语义增强）。

---

### 3. Bun Zig→Rust 架构迁移完成：v1.3.14 携 HTTP/3 全栈上线

`[运行时革新]` `[架构迁移]` `[HTTP/3]`

**事件全景**

2026 年 5 月，Bun 核心团队完成了最具野心的架构迁移：**将 Bun 从 Zig 语言实现重写为 Rust**。利用 AI 编程工具辅助，团队在数天内合并了超过一百万行代码变更，并通过了全平台测试套件。v1.3.14（2026 年 6 月发布）是最后一个 Zig 代码基版本，此后版本均为 Rust 实现。同时，v1.3.14 带来了 **HTTP/3（QUIC）全栈支持**（`fetch()` 客户端 + `Bun.serve()` 服务端）、内置图像处理 API（`Bun.Image`，无需 Sharp/libvips 原生扩展），以及全面的性能数字：v1.3.13 中 `bun install` tarball 流式落盘内存占用降低 **17 倍**，Source Maps 内存降低 **8 倍**，gzip 速度提升 **5.5 倍**（zlib-ng）。

**底层原理解析**

**Zig→Rust 迁移的工程理性**：Zig 为 Bun 早期提供了极低的运行时开销与手动内存控制能力，但在大型代码库维护、IDE 支持、FFI 生态方面显著落后 Rust。Rust 的所有权模型与 Zig 手动内存管理在"无 GC 高性能"目标上殊途同归，但 Rust 提供借用检查器消除内存泄漏（本次迁移修复了若干已知泄漏），二进制体积缩减 3–8 MB（Rust LTO + strip 优化优于 Zig 工具链在此配置下的产出）。**HTTP/3 技术实现**：基于 QUIC 协议（UDP 上的多路复用流），天然解决 TCP 队头阻塞（HoL Blocking）问题。`Bun.serve()` 的 HTTP/3 实现允许服务端与客户端建立无头阻塞的并发流，对高并发 SSE（Server-Sent Events）、流式 JSON API 场景的延迟改善显著。**`Bun.Image`**：内置图像解码/变换/编码，底层为 Rust 原生实现，免去了 Sharp 在 CI 跨平台场景下频繁触发的原生扩展编译问题——这是 Docker CI 环境中极为普遍的工程痛点。

**前端工程影响与指导**

**CI 安装加速**：`bun install --frozen-lockfile` 在 monorepo 场景下冷启动速度是 npm 的 30~50 倍；暖启动（有缓存）新增 7 倍全局存储缓存加速。**HTTP/3 生产部署**：`Bun.serve` HTTP/3 当前标记为 experimental，适合作为内部 API 网关的先行实验，不建议直接暴露于公网生产环境。**`Bun.Image` 替代方案评估**：若团队 CI 中存在 Sharp 安装不稳定的问题，Bun 作为 Node.js 运行时直接替换 + `Bun.Image` 组合是值得评估的工程优化路径。

---

### 4. Astro 7.0：Rust 编译器 + Rolldown + AI 原生开发服务器

`[GA 正式版]` `[范式转移]` `[构建革新]`

**事件全景**

2026 年 6 月 22 日，Astro 7.0 正式发布（6 月 23 日 7.0.2 跟进热修）。这是 Astro 历史上变化幅度最大的版本：Go 编译器（`@astrojs/compiler`）被 **Rust 实现**（`@astrojs/compiler-rs`）取代；Vite 升至 8.0（底层使用 Rolldown Rust 打包器）；Markdown 处理器从 remark/rehype 切换为 `satteri()`；引入 **AI 代理友好的后台开发服务器**（`astro dev --background`）；并彻底移除 `@astrojs/db` 生态。实测构建速度提升 **15–61%**（内容驱动型站点受益最明显）。

**底层原理解析**

**Rust 编译器的关键约束变化**：原 Go 编译器对未闭合 HTML 标签静默忽略，Rust 编译器对此**强制报错**——这是 7.0 最大的隐性破坏性变更，大量使用非标准 HTML 的存量项目必须修复。这一严格性对齐 HTML5 标准，有助于消除跨浏览器边缘渲染差异。**Rolldown 集成路径**：Astro 通过 Vite 8 间接使用 Rolldown（Rust 实现，替代 esbuild + Rollup 双轨架构）。Rolldown 的单一打包路径（dev 与 prod 使用同一 bundler）消除了经典 Vite 痛点——因 esbuild 与 Rollup 插件语义不完全一致而导致的"dev 可用但 prod 构建异常"问题。**AI 原生开发服务器**：这是前端框架中首次将 AI 代理协同工作流作为正式 CLI 特性纳入核心。`.astro/dev.json` lock 文件使 AI agent 可感知开发服务器状态；`astro dev logs --follow` 提供结构化日志流；`create-astro` 脚手架默认生成 `AGENTS.md`（含 `CLAUDE.md` 软链），直接面向 AI 编程助手的工作约定。**Sätteri**（Rust 实现的新 Markdown 处理器）取代 remark/rehype（JS 实现），对大型内容仓库的 Markdown 编译速度有数量级提升。

**前端工程影响与指导**

**升级路径**：`npx @astrojs/upgrade` 可处理大多数自动迁移；核心审查项：①HTML 未闭合标签（必须修复，无豁免）；②自定义 remark 插件须安装 `@astrojs/markdown-remark` 并显式配置；③`@astrojs/db` 依赖必须替换（推荐 Turso/Drizzle/Neon）；④废弃的 `astro:transitions` 常量 API 迁移到字符串事件名（如 `'astro:before-preparation'`）。**AI 开发流程**：`--background` 模式是当前前端框架中最完善的 AI agent 协同支持，建议评估"agent 驱动的内容批量更新 + 实时热更新观察"工作流。

---

## 🟡 Tier 2：重要迭代与应用生态

### 1. Angular 22：Signal-First 架构正式成熟

`[GA 正式版]` `[Breaking Changes]` `[信号架构]`

**核心增量**

2026 年 6 月 3 日发布的 Angular 22 是 Angular 信号架构的"成熟稳定版"。**Signal Forms 正式稳定**：表单状态以信号驱动，`FormField.parseErrors` 仅在依赖变更时重新计算（告别"每次按键触发全量验证"的性能噩梦）。**Selectorless Components 稳定**：组件可直接在模板中 import 使用，无需字符串 selector，消除大型 monorepo 中的命名冲突与重构摩擦。`@angular/aria` 包提供无头、全键盘可访问的 directive 集合，信号驱动，与 Signal Forms 无缝集成。新增 `debounced()` 信号原语，专为搜索框、防抖触发场景设计；`resource()` / `rxResource()` / `httpResource` 三类资源原语同步稳定，提供类型安全的异步数据加载，消除手写 `BehaviorSubject` + `async pipe` 的样板代码。

**核心工程思想**

Angular 22 完成了从"Zone.js 变更检测"到"响应式信号图"的范式转移：信号的 push-pull 混合追踪机制在状态更新时仅重新渲染真正依赖该状态的视图节点，从根本上告别 Zone.js 全局副作用追踪的性能天花板。`ChangeDetectionStrategy.Eager` 迁移工具链支持渐进式替换，无需一次性重构整个应用。

**落地行动指南**

Breaking Changes 核心：强制要求 TypeScript ≥ 6.0（含 `strict: true` 默认值）；重复 `@Input`/`@Output` 现在报编译错误；旧版 Shadow DOM selector 支持移除。升级前须先完成 TypeScript 6.0 迁移，并修复 `strict: true` 暴露的存量类型问题。Angular 22 LTS 支持至 2028 年 5 月，投入升级具有长期价值。

---

### 2. Vue 3.6.0-beta.17：Vapor Mode 特性完整，虚拟 DOM 进入退场倒计时

`[Beta]` `[范式切换]` `[响应式革新]`

**核心增量**

2026 年 6 月 24 日发布的 beta.17 标志着 Vapor Mode 在功能上完全对齐 VDom 模式（Suspense 除外）。通过 `<script setup vapor>` 为单个组件启用 Vapor，彻底绕过虚拟 DOM diff，直接生成 DOM 指令序列。性能基准：极端场景渲染速度最高快 97%；10 万组件挂载约 100ms；Vapor 专属组件 bundle 体积缩减 20–50%，在第三方测评中与 Solid.js、Svelte 5 的原生 DOM 性能持平。`@vue/reactivity` 已基于 **alien-signals** 重构，在高频状态更新场景下调度效率与内存占用均有可观提升。Beta 迭代节奏：过去 7 周连续 7 个 beta，工程投入密度高。

**核心工程思想**

Vapor Mode 的本质是**编译时响应式绑定**替代运行时 VDom diff：编译器将 `<template>` 中的响应式引用转换为精确的 DOM mutation 调用（类似 Solid.js 的 fine-grained reactivity）。VDom 的开销在"模板结构稳定、数据动态"的主流场景（表单、Dashboard、数据展示）中属于无效计算，Vapor 从根本上将这一开销清零。VDom + Vapor 混用在同一应用中受支持，迁移路径为渐进式。

**落地行动指南**

当前仍为 beta，不建议生产使用；可在新的独立组件或原型项目中实验性采用。关注 Suspense 兼容性进展，这是 Vapor Mode 离稳定版的最后一个主要障碍。alien-signals 重构后的响应式层可能影响部分依赖 Vue 内部 reactivity API 的第三方库，升级前需测试生态兼容性。

---

### 3. Vite 8.1.0 + Cloudflare 收购 VoidZero：工具链格局重塑

`[重大收购]` `[Minor Release]` `[生态整合]`

**核心增量**

2026 年 6 月 23 日发布的 Vite 8.1.0 是 3 月份 Vite 8.0（首次以 Rolldown 为默认打包器）的迭代修复版，主要解决 Rolldown 迁移遗留的回归问题（v8.0.14 补丁已合并 28 项高关注度修复）。Vite 8 核心性能数字：dev 构建速度 10–30 倍于 Vite 7；统一的 dev/prod bundler（Rolldown）终结了 esbuild/Rollup 双轨语义差异。本周更大的战略事件：**Cloudflare 于 6 月 4 日宣布收购 VoidZero**（Vite、Rolldown、Oxc 的母公司），这是前端工具链史上最大规模的战略并购之一。

**核心工程思想**

VoidZero 将 Oxc（Rust JS 工具链，含 Linter、Transformer、Minifier）、Rolldown（Rust Bundler）与 Vite（大一统插件接口）作为一体化 Rust 前端工具栈构建。Cloudflare 的战略意图：Workers + Pages 边缘部署需要极速构建管道，Rolldown 的 Rust 构建性能与 Cloudflare 边缘基础设施高度契合，长期或推动 Vite 与 Workers 运行时的深度一体化集成。

**落地行动指南**

Vite 8 要求 Node.js ≥ 20（使用 Node.js 18 的项目须先升级运行时）。Cloudflare 收购短期内不改变 Vite 开源策略（官方声明团队与 roadmap 不变），但值得关注 Cloudflare Workers 与 Vite 构建管道一体化的后续动向。`create-vite@9.1.0` 同步发布，新项目推荐直接使用。

---

### 4. Rspack 2.1.0-beta：React Compiler 原生集成进 Rust 构建管道

`[Beta]` `[React Compiler]` `[构建优化]`

**核心增量**

2026 年 6 月 18 日发布的 Rspack 2.1.0-beta.0 将 **React Compiler 直接集成到 `builtin:swc-loader`**，无需额外配置 Babel 插件。React Compiler 在编译时分析组件的依赖关系，自动插入等价于 `useMemo`/`useCallback` 的缓存边界，消除手动记忆化的心智负担。同步开启的**副作用无关函数（side-effect-free functions）分析默认在生产构建中启用**，配合 `#__NO_SIDE_EFFECTS__` 注解，Tree-Shaking 颗粒度精细到函数级别。此外新增 `import.meta.glob` 支持（对齐 Vite/Turbopack），`getProvidedExports()` JS API，以及 CSS Modules 增强选项。Rspack 2.0 的整体性能：相比 Rspack 1.0 提升最高 +100%，持久化缓存命中率下构建速度再提升 50%，内存占用降低 20%。

**核心工程思想**

`builtin:swc-loader` 是 Rspack 基于 SWC 的内置 Transform 管道（Rust 原生实现）。将 React Compiler 的 IR 分析直接嵌入该管道（而非调用外部 Babel 插件）意味着 React Compiler 转换与 SWC TypeScript 类型剥离在同一 pass 完成，避免了多次 AST 序列化/反序列化的性能损耗——这对于大型 React 项目的构建时间改善尤为显著。

**落地行动指南**

注意 Rspack 2.0.7 存在运行时 Bug（已回退），从 2.0.6 以下应直接升级至 2.0.8。2.1.0 beta 阶段，React Compiler 集成适合在测试/staging 环境先行验证，重点检查计算密集型组件的 memoization 是否符合预期（React Compiler 存在已知的"过度 memo"情形，需通过 babel plugin 的 `compilationMode` 配置调节）。

---

### 5. XState 6.0 Alpha 系列：状态机 API 的彻底重塑

`[Breaking Changes]` `[Alpha]` `[架构重构]`

**核心增量**

2026 年 6 月 20–24 日，XState 连续发布 5 个 alpha 版本（alpha.1–alpha.5），揭示 v6 的 API 方向：**所有 action creator（`assign`、`raise`、`sendTo` 等）被移除，改为直接使用纯 JS 函数**；`interpret()` 替换为 `createActor()`；状态机类型系统从 `types: {}` 迁移到 `schemas` 对象，`schemas.children` 支持对子 actor 引用按 ID 类型化；alpha.5 进一步移除了 transition config 中的字符串 target 简写——必须使用带显式 `target` 属性的对象形式。alpha.3 引入了函数的可移植代码序列化格式（`@type: 'code'`），保留可视化编辑器的序列化能力。

**核心工程思想**

v6 的哲学从"DSL-heavy（领域语言重型）"转向"Plain JS-first（普通 JS 优先）"。原版 XState 为支持 Stately Visual Editor 的可序列化状态机，强制要求所有 action 通过 creator 函数声明，造成极高的 API 学习成本。v6 在保留序列化能力的前提下解除了对 creator 函数的强制依赖，大幅降低入门与迁移门槛。Actor logic 现在从常规转换和初始转换均返回 effects，使状态机行为更加一致和可预测。

**落地行动指南**

当前 alpha 阶段 API 仍快速演化（alpha.5 已有新增 Breaking Change），不适合生产使用。v5 stable 线（5.32.2，6 月 23 日）保持正常维护，存量项目无需着急迁移。建议提前阅读 v6 alpha 变更日志，评估 `assign`/`raise`/`sendTo` 替换成本，待官方 beta 发布后再制定迁移计划。

---

### 6. Node.js 发布节奏重构 + 双高危 CVE 紧急安全补丁

`[运行时架构]` `[安全补丁]` `[发布策略]`

**核心增量**

**安全层面（立即行动）**：2026 年 6 月 18 日，Node.js 三条活跃线同步发布安全版本（22.23.0 / 24.17.0 / 26.3.1），修复两个 HIGH 级别 CVE：WebCrypto 崩溃漏洞与 TLS 通配符证书绕过漏洞，同时升级 llhttp 9.4.2、nghttp2 1.69.0、OpenSSL 3.5.7、undici。所有生产环境须立即升级，无豁免场景。

**架构层面**：Node.js 项目组宣布自 Node.js 27 起实施全新发布模型：**每年一个主版本，所有版本均进入 LTS**（取消偶/奇年号区分）；版本号与发布年份对齐（Node 27 = 2027 年发布的主版本）；引入 Alpha 频道（`27.0.0-alpha.1` 格式）供早期测试，Node 27 Alpha 预计 2026 年 10 月开放。Node.js 26（5 月 5 日发布）携带 V8 14.6 与默认开启的 Temporal API，是当前尝试 Temporal 零成本路径。

**落地行动指南**

立即升级至上述安全版本是本周前端基础设施的首要任务。新发布模型意味着"版本焦虑"结束——不再需要记忆哪个版本号是 LTS，每个主版本都有生产质量的长期支持保证，长期规划上更为简洁。

---

### 7. Deno 2.8：`deno publish` npm 互操作 + 惰性模块加载

`[运行时迭代]` `[npm 互操作]` `[模块系统]`

**核心增量**

Deno 2.8（5 月 22 日，6 月 11 日 2.8.3 补丁）是迄今最大的 Deno minor 版本：`deno publish` 将 JSR/Deno 项目自动转换为 npm 兼容 tarball（生成 `package.json` 含条件 exports、转译 JS、提取 `.d.ts` 文件），极大降低 Deno 库发布到 npm 生态的门槛。**惰性模块加载（Lazy Module Loading）**：加载和解析模块时不执行顶层代码，导出仅在首次访问时求值，对齐 TC39 defer-import-eval 提案（与 TypeScript 5.9 的 `import defer` 语法在标准层同步映射）。新增 `deno audit fix` 自动升级受影响依赖包，`min-release-age` 配置防止安装过新的包版本（供应链安全防护）。冷安装性能：并行依赖解析 + libdeflater 压缩 + tarball 分离 CPU/IO 处理，显著提升大型依赖树的安装速度。

**落地行动指南**

Deno 库作者可评估将 JSR 发布目标同步扩展到 npm，减少双重维护负担。`deno audit fix` 值得在 CI 中常态化引入，`min-release-age` 设置（如 7 天）可有效规避 npm 供应链抢注攻击（即发布恶意小版本后立即被锁版本引入的攻击向量）。

---

## 🟢 Tier 3：行业风向与速递

- **React 19.2.7 / 19.1.8 / 19.0.7**（6 月 1 日）：三条版本线同步修复 Server Actions 中 `FormData` 条目被静默丢弃的回归（由 5 月 6 日的类型强化补丁引入），无新 API，生产项目建议立即升级。
- **Next.js 16.2.7 stable + 16.3.0-canary.65**（6 月 24 日）：16.2 稳定线主推 Cache Components（`"use cache"` directive 稳定）+ PPR（Partial Prerendering）默认开启，布局去重使链接密集页面 prefetch 数据传输量减少 60–80%；canary 持续推进 Turbopack chunk 合并优化与 SWC 70 升级。Next.js 15 LTS 将于 2026 年 10 月 21 日终止支持，须制定迁移计划。
- **Svelte 5.56.4**（6 月 23 日）：模板内 `{@const}` 声明语法稳定，提供模板逻辑的就地抽取能力；底层切换 `createElement`（快于 innerHTML 解析）与 `current_sources` O(1) Set 查找持续优化运行时性能；`vite-plugin-svelte` 现在在 dev 服务端环境启用优化器，开发/生产环境行为更一致。
- **Svelte 生态**：`svelte-language-tools` 完整支持 TypeScript 6.0（语言服务器、`svelte2tsx`、`svelte-check` 全线同步）。
- **Nuxt 4.4.8 / 3.21.8 hotfix**（6 月 8 日）：修复 macOS 下 dev server socket 名超长导致崩溃；`unhead` 回退至 v2（v2→v3 的 Breaking Change 影响 Nuxt 用户）；Nuxt 3 EOL 时间线正在 Discussion #33918 中讨论，存量项目应制定迁移 Nuxt 4 的计划。
- **TanStack Query v5.101.1**（6 月 23 日）：修复水合（hydration）时 pending query 先于水合 resolve 时 `dataUpdatedAt` 未设置的问题；`@tanstack/lit-query` 早期包已添加（LitElement 生态支持）；`@tanstack/svelte-query` 维持独立 v6 主版本线。
- **TanStack AI Beta**（6 月 9 日宣布）：框架无关、provider 无关的 AI 工具包；在 2026 年 6 月 22 日阿姆斯特丹开源奖中同时拿下"AI Project of the Year"（TanStack AI）与"Breakthrough of the Year"（TanStack Start），生态势头强劲。
- **shadcn/ui 4.11.0**（6 月 8 日）：**GitHub Registries** 功能——任何公开 GitHub 仓库只需添加 `registry.json` 即可成为 shadcn 组件注册中心，可分发组件、hooks、design tokens、项目规范、测试配置、CI/CD workflow 等，彻底将 shadcn 从组件库转型为"前端工程规范分发平台"。
- **shadcn `npx shadcn eject`**（4.9.0，5 月 31 日）：允许将 registry 组件弹出到项目本地代码库，完整接管所有权，消除对上游的长期依赖风险——适合对 UI 层有深度定制需求的产品团队。
- **Jotai v2.20.1**（6 月 11 日）：存储订阅解析修复（`reviver` 在解析订阅更新时正确应用，同步栈溢出错误正确抛出）；v2.20.0 对高吞吐状态更新场景有性能优化与内部类型收窄改进。
- **XState v5.32.2**（6 月 23 日）：修复通配符事件描述符回退行为（当精确描述符 guard 失败时应回退到通配符，此前行为不正确）；v5 稳定线正常维护，与 v6 alpha 并行。
- **TypeScript 5.9（Q1 2026，延伸背景）**：`import defer` 语法支持（对齐 TC39 惰性模块提案）；`strictInference` 编译器新标志；条件类型 union 判别缩窄（消除联合类型手写类型断言的必要性）；TC39 Decorator Metadata 完全稳定。
- **Cloudflare 收购 VoidZero**（6 月 4 日）：Vite 主要发展公司归入 Cloudflare 麾下，短期开源策略不变，是前端工具链史上最大战略并购之一；长期关注 Workers/Pages 与 Vite 构建管道一体化集成的走向。
- **Vue 3.5.38**（6 月 11 日）：最新稳定补丁；Vapor Mode 专属 beta.x 通道以每周一个版本的节奏持续迭代（过去 7 周 7 个 beta）。
- **TanStack Router（6 月 16 日）**：新增 Rsbuild preview SSR middleware 支持；修复 `useMatch` selector 抛错问题；router-core 轻量路由匹配缓存。
- **Rspack 2.0.6**（6 月 2 日）：RSC 构建中新增 `server-only`/`client-only` 标记验证，确保服务端专属模块不泄漏至客户端 bundle，提升全栈 React 应用的安全边界保障。
