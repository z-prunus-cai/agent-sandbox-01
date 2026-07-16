# 前端语言与 Web 工程化情报简报

**情报日期**：2026-06-22　　**情报窗口**：2026-06-14 至 2026-06-22（主窗口 48h + 弹性扩展）

---

## 🔴 Tier 1：核心突破与范式转移

### 1. TypeScript 7.0 RC 发布 — 编译器 Go 原生重写，性能跨越数量级 `[范式转移]` `[运行时革新]`

**事件全景**

2026 年 6 月 18 日，微软官方发布 TypeScript 7.0 RC（由 Daniel Rosenwasser 签发），这是 TypeScript 自 2012 年诞生以来最具架构历史意义的版本：整个编译器完整从 TypeScript 自托管实现迁移至 Go 语言原生二进制。TypeScript 6.0 于 3 月 23 日作为最后一个 JavaScript-based 版本正式发布（RC 在 3 月 6 日），当时已将严格模式设为默认、编译目标提升至 ES2025，并带来 40–60% 增量重建速度提升和 ~25% 内存峰值降低；它的核心使命是为 7.0 的激进重写铺平迁移路径。

**底层原理解析**

TypeScript 7.0 的 Go 移植（代号 "native port"）性能跃升来自两个根本机制：其一，**原生共享内存并行（shared-memory parallelism）**——Go 的 goroutine 模型允许类型检查、符号解析、语义分析等独立工作流真正并发执行，而 JS 单线程模型即便引入 Worker Threads 也存在序列化往返开销；其二，**消除 V8 JIT 预热延迟**——TypeScript 6.x 的 JS 实现在冷启动阶段依赖 V8 Turbofan JIT 编译预热，Go 编译为原生二进制后该延迟消失，冷启动立即达到峰值性能。Visual Studio 2026 18.6 Insiders 3 的实测数据印证了这一点：项目加载速度约 8x 提升，编译整体约 10x 快于 TS 6.0 基线。RC 发布通常约 1 个月后 GA，预计 7 月下旬进入稳定版。

**前端工程影响与指导**

IDE 体验将在大型 monorepo 中发生质变：VS Code + TypeScript Language Server 冷启动加速、实时类型提示响应延迟大幅缩短，Vue/Angular 项目的 Volar/Angular Language Service 需跟进适配。CI/CD 流水线中 `tsc --build` 在大型项目引用树中有望从分钟级缩短至秒级，TypeScript-only 类型检查流水线的 ROI 将大幅提升。迁移准备：TS 7.0 在类型语义上兼容 TS 6.0；应优先升级至 TS 6.0 并修复所有 strict mode 警告；ts-jest、ts-node、tsx 等依赖编译器 API 的工具需等待对应适配版本后方可在 CI 中切换。

> 来源：[Announcing TypeScript 7.0 RC](https://devblogs.microsoft.com/typescript/announcing-typescript-7-0-rc/) · [A 10x Faster TypeScript](https://devblogs.microsoft.com/typescript/typescript-native-port/) · Visual Studio Magazine

---

### 2. ECMAScript 2026 正式定案 — Temporal 终结 Date 三十年历史遗留 `[TC39 Stage 4]`

**事件全景**

ECMAScript 2026（第 17 版）规范已正式定案（https://tc39.es/ecma262/2026/）。最具里程碑意义的特性是 **Temporal API** 在 2026 年 3 月第 114 次 TC39 会议推进至 Stage 4：JavaScript 内置日期时间系统自 1995 年诞生以来从未被正式替换，`Date` 对象的可变性、时区处理缺陷与 Unix 纪元偏差长达 30 年困扰工程实践，Temporal 的定案终结了这段历史。同一次会议还将 `Intl era and monthCode` 推入 Stage 4。此前的 Stage 4 入场时间轴：`Upsert`（`Map/WeakMap.prototype.getOrInsert`）于 1 月 20 日定案，`JSON.parse source text access` 与 `Iterator Sequencing` 在 2025 年 11 月定案。

完整 ES2026 特性矩阵：**Temporal**（不可变时区感知日期/时间体系）、**`using` / `await using`**（`Symbol.dispose` 作用域资源释放）、**`Error.isError()`**（跨 realm 可靠错误检测）、**`Array.fromAsync()`**、**Import Attributes**（`import … with { type: 'json' }`）、**`Math.sumPrecise()`**（IEEE 754 补偿式求和）、**`Uint8Array` base64/hex 方法**（原生替代 `btoa`/`atob`）、**Set 集合运算**（`union`/`intersection`/`difference`/`symmetricDifference`）、**`RegExp.escape()`**、**inline regex modifier flags**、**`Promise.try()`**、**`Float16Array`**、**Iterator helpers**。

**底层原理解析**

Temporal 刻意围绕**不可变值对象**设计：`Temporal.Instant`（UTC 时间点）、`Temporal.ZonedDateTime`（带 IANA 时区）、`Temporal.PlainDate/Time`（无时区语义）等实例一旦创建不可修改，所有运算返回新实例，从根本上消除因可变 `Date` 共享引用引发的状态 Bug。时区处理使用 IANA 数据库，支持 DST 转换与历史时区变更查询。`using` 关键字依托 `Symbol.dispose` 协议，将 C# `using`/Python `with` 的资源管理范式引入 JavaScript，Node.js 24+ 原生支持（`ExplicitResourceManagement` 已进 V8 13.6）。Import Attributes 将模块安全性提升至语言层面，Node.js 20+、Deno、Chromium、esbuild 0.28+、Vite 8+ 均已支持。

**前端工程影响与指导**

ES2026 规范已定案，V8 14.6（Node.js 26）、SpiderMonkey、JavaScriptCore 均已全量实现；TypeScript 6.0+ 已原生支持所有 ES2026 类型；Tailwind CSS v4.3 以下的配置层如有 `Date` 类型运算可制定 Temporal polyfill（`@js-temporal/polyfill`）验证计划，建议观察至少 3 个月再在生产切换；`using` 关键字应立即用于替换文件句柄、数据库连接等资源管理模式的 try/finally 样板；Import Attributes 在 `tsconfig` 中启用 `"moduleResolution": "bundler"` 后可安全用于 JSON / CSS 模块导入。

> 来源：[ECMAScript® 2026 Language Specification](https://tc39.es/ecma262/2026/) · [TC39 Advances Temporal to Stage 4 — Socket.dev](https://socket.dev/blog/tc39-advances-temporal-to-stage-4) · [ES2026 Solves JavaScript Headaches — The New Stack](https://thenewstack.io/es2026-solves-javascript-headaches-with-dates-math-and-modules/)

---

### 3. Cloudflare 收购 VoidZero — 前端工具链结构性权力重组 `[行业范式转移]`

**事件全景**

2026 年 6 月 5 日，Cloudflare 宣布完成对 **VoidZero** 的收购。VoidZero 是由 Vue.js 作者 Evan You 于 2024 年创立的前端工具链公司，核心资产包括：**Vite**（100M+ 次/周 npm 下载）、**Rolldown**（Rust 实现的 Rollup 替代，1.0 稳定版于 5 月 7 日发布）、**Oxc**（Rust 实现的 JS/TS 解析器、编译器、Linter、Formatter 全套工具链）、**Vitest**（测试框架）、**tsdown**（TypeScript 库打包工具）。Evan You 及 VoidZero 团队整体并入 Cloudflare ETI（Engineering Tooling Infrastructure）部门。Cloudflare 承诺：所有项目保持开源与供应商中立，并设立 $1M 独立 Vite 生态基金支持社区贡献者。

**底层原理解析**

这一收购的战略逻辑指向 Cloudflare 的核心利益：**Workers 边缘平台需要与其运行时环境深度集成的前端工具链**。Rolldown + Oxc 的 Rust 实现可编译为 WebAssembly，在 Workers 边缘节点上执行构建和代码转换，无需依赖 Node.js 环境——这是"构建即基础设施（Build as Infrastructure）"的新范式。与此同时，**Vite 8.0**（3 月 12 日发布）已将 Rolldown 作为唯一底层打包引擎，替换原先 esbuild（开发）+ Rollup（生产）的双引擎架构，实测带来 10–30x 构建提速（Linear：46s → 6s；Ramp：-57%；Beehiiv：-64%）；`@vitejs/plugin-react v6` 同步切换至 Oxc 进行 React Refresh 转换，完全移除 Babel 依赖。**Vite+ CLI** 随之公布，将 Vite、Vitest、tsdown、Oxlint、Oxfmt 整合为单一 CLI 入口。

**前端工程影响与指导**

短期稳定：Cloudflare 的开源承诺与 $1M 基金消解了供应链单点化顾虑，第三方插件作者激励持续。长期格局：前端工具链首次与主流云厂商深度绑定，Netlify/Vercel 面临 Cloudflare"平台 + 工具链垂直整合"的竞争压力，可预期边缘计算优化的构建加速服务将陆续推出。工程团队行动建议：升级至 Vite 8.0+ 以获取 Rolldown 统一引擎红利；评估 `vite-plugin-oxc-lint`（Oxlint 集成）替换 ESLint——Oxlint 速度为 ESLint 的数量级倍数；对 Rolldown 插件 API 兼容性进行针对性测试，Rollup 插件迁移成本较低。

> 来源：[Cloudflare Acquires VoidZero — SiliconANGLE](https://siliconangle.com/2026/06/04/cloudflare-acquires-voidzero-maker-vite-javascript-toolchain/) · [Vite 8.0 is out!](https://vite.dev/blog/announcing-vite8) · [Rolldown 1.0 — VoidZero](https://voidzero.dev/posts/announcing-rolldown-1-0)

---

### 4. Vue 3.6 Vapor Mode beta.16 — 虚拟 DOM 的选择性终结 `[范式转移]` `[性能突破]`

**事件全景**

2026 年 6 月 17 日，Vue 3.6.0-beta.16 发布，Vapor Mode（无虚拟 DOM 编译模式）已宣布**功能完整（feature-complete）**，与现有 VDOM 模式的全量特性实现对等（Suspense 除外）。此前 beta.15（6 月 11 日）完成 Vapor 模式下的 KeepAlive、Teleport 和自定义元素支持；reactivity 核心已通过 alien-signals 重构重写（在 3.6.0-alpha.1 中引入）。基准测试数据：100,000 个组件挂载耗时约 100ms（远快于 VDOM 路径），渲染速度最高提升 97%，Vapor-only 组件路径 bundle 体积减少 20–50%，性能指标与 Solid.js 及 Svelte 5 持平。

**底层原理解析**

Vapor Mode 在 Vue SFC 编译阶段引入全新代码生成路径：不再生成 VNode 树，而是直接生成**命令式 DOM 操作指令**（类似 Svelte 5 的编译输出策略）。响应式追踪依赖 Vue 3 的 Signals 风格 `@vue/reactivity` 核心（`ref`/`computed` 底层为拉取式 reactive graph），细粒度订阅在 component setup 阶段完成建立，副作用与 DOM 节点直接绑定而非通过 VNode 层间接触达，消除了 diff 计算开销。关键架构决策是**完全向后兼容**：现有 VDOM 组件与 Vapor 组件可在同一应用中任意混用，无需整体迁移。

**前端工程影响与指导**

最受益场景：高密度数据表格、实时更新看板、动画密集型交互界面。迁移成本极低：在 SFC 文件头部声明 Vapor 模式或在 Nuxt/Vite 配置中按页面粒度启用，不涉及组件逻辑修改。时机建议：Vue 3.6 当前仍在 beta 阶段（最新 beta.16），预计 Q3 发布稳定版，建议于正式版发布后在非关键业务路径中率先试验；Nuxt 5 将基于 Vite Environment API 构建，届时 Vapor 路径将在 SSR 场景获得更完整的支持。

> 来源：[Vue 3.6.0-beta.16 GitHub](https://github.com/vuejs/core/releases/tag/v3.6.0-beta.16) · [vuejs/core CHANGELOG.md (minor branch)](https://raw.githubusercontent.com/vuejs/core/minor/CHANGELOG.md)

---

### 5. Node.js 三线同步安全发布 — 12 枚 CVE 含两枚 HIGH 级别漏洞 `[运行时革新]` `[安全紧急]`

**事件全景**

2026 年 6 月 18 日，Node.js 官方同步发布三条 active 支持线的安全补丁：**v26.3.1**、**v24.17.0**（Active LTS "Krypton"）、**v22.23.0**（Maintenance LTS "Jod"），修复 12 枚 CVE。此次安全批次是迄今最严重的 Node.js 安全发布之一。两枚 **HIGH** 级别漏洞值得即刻关注：**CVE-2026-48933**（WebCrypto AES 整数溢出）——当 `subtle.encrypt()` 输入长度为 2GiB 整数倍时触发整数溢出导致进程崩溃，攻击者可构造精确大小的密文输入实现远程 DoS；**CVE-2026-48618**（Unicode 点分隔符 TLS 通配符深度认证绕过）——通过 Unicode 点字符欺骗 TLS 主机名验证逻辑绕过通配符证书深度检查。

依赖库同步更新：llhttp 9.4.2、nghttp2 1.69.0、OpenSSL 3.5.7、undici 分别升至 8.5.0（v26）/7.28.0（v24）/6.27.0（v22）。

背景：Node.js 26 当前为 Current 线（5 月 5 日发布），Node.js 24 LTS "Krypton"（October 2025 入 LTS，支持至 April 2028），Node.js 20 已于 2026 年 3 月 24 日 EOL。Node.js 26 架构新增：Temporal API 默认开启、V8 14.6（含 `Map.getOrInsert`/`Iterator.concat` 等 ES2026 方法）、`node:ffi` 实验性外部函数接口（v26.1）、Buffer poolSize 从 16KiB 提升至 64KiB（v26.3）、`permission.drop()` 运行时权限降级 API。

**底层原理解析**

CVE-2026-48933 的根因在于 Node.js 的 WebCrypto 绑定层对 SubtleCrypto AES 操作的输入长度检查未使用无符号 64-bit 整数，当长度恰好等于 2GiB 时（`2^31` 字节）发生有符号整数回绕，传入底层 OpenSSL 的实际长度为 0，触发断言失败。CVE-2026-48618 则利用了 TLS 主机名验证逻辑对 Unicode 全角/半角点字符（U+FF0E 等）的规范化差异，使得野卡证书深度计算结果与实际 subject 不符。

**前端工程影响与指导**

即刻行动：所有在生产环境使用 Node.js 的团队需立即升级（v26 → 26.3.1，v24 → 24.17.0，v22 → 22.23.0），WebCrypto DoS 风险对 SSR 服务端和 API 层均构成威胁。Kubernetes/容器环境可通过 `node:slim` 镜像快速热更；使用 AWS Lambda Node.js 托管运行时的团队需等待 AWS 更新托管版本并手动切换。Node.js 20 已 EOL，仍在维护 Node 20 的项目应将升级至 22 或 24 提上紧急日程。

> 来源：[Node.js June 2026 Security Releases](https://nodejs.org/en/blog/vulnerability/june-2026-security-releases) · [Node.js 26.3.0 Release Blog](https://nodejs.org/en/blog/release/v26.3.0) · [Node.js 26.0.0 Release Blog](https://nodejs.org/en/blog/release/v26.0.0)

---

## 🟡 Tier 2：重要迭代与应用生态

### 1. Astro 7.0.0 正式版（2026-06-22 今日发布）— Rust 编译器 + Vite 8 全面落地 `[GA 正式版]` `[Breaking Changes]`

**核心增量**：Astro 7.0.0 于今日（6 月 22 日）正式发布，核心架构性升级包括：① **升级至 Vite v8**（Rolldown 统一构建引擎，10–30x 构建提速）；② **Go 编译器替换为 Rust 编译器**（`@astrojs/compiler-rs`），更快的构建速度同时执行更严格的 HTML 验证（未闭合 HTML 标签由静默修复改为抛错，需从 `experimental.rustCompiler` 迁移）；③ **Sätteri 替换 remark/rehype 成为默认 Markdown 处理器**（不兼容现有 remark 插件，需手动安装 `@astrojs/markdown-remark` 回退）；④ **高级路由（Advanced Routing）默认启用**，`src/fetch.ts` 取代 `src/app.ts` 成为入口点；⑤ **`@astrojs/db` 包完全移除**，需迁移至 Node.js SQLite、Drizzle ORM 等替代方案；⑥ **废弃 View Transitions 旧式事件名称**（`TRANSITION_BEFORE_PREPARATION` 等），改为字符串事件（`'astro:before-preparation'`）；⑦ **新增 `astro dev --background` 后台开发服务器 API**，专为 AI 编码智能体设计。`create-astro@5.1.0` 同步发布，默认生成 `AGENTS.md`（附带 `CLAUDE.md` 软链接）为 AI 工作流预置上下文。

**核心工程思想**：Rust 编译器使 Astro 进入与 Vite/Rolldown/Oxc 同一技术栈阵营，全栈 Rust 工具链在 CI 冷构建场景下具备极大的分钟级到秒级压缩潜力。`astro dev --background` 的 AI 集成是 AI-native 框架工具链化的具体信号。

**落地行动指南**：Breaking Changes 较多，升级前务必核查：自定义 remark 插件迁移（`@astrojs/markdown-remark`）、HTML 模板中未闭合标签修复、`@astrojs/db` 替代方案选型、View Transitions 事件名称更新、`src/fetch.ts` 入口文件创建。所有官方集成包（`@astrojs/react v6`、`@astrojs/vue v7`、`@astrojs/svelte v9` 等）已于今日同步发布适配版本。

> 来源：[withastro/astro releases](https://github.com/withastro/astro/releases)

---

### 2. Angular 22.0.2（2026-06-17）— Signal 优先时代正式确立 `[GA 正式版]` `[Breaking Changes]`

**核心增量**：Angular 22.0.0（6 月 3 日）标志着 Angular 的 Signal-first 时代正式到来。**Selectorless Components 稳定化**：模板中直接以类引用代替 `selector` 字符串，消除 Angular 最饱受诟病的样板式注册负担；**Signal Forms 稳定化**：完全基于 Angular Signals 的表单 API 取代 ReactiveFormsModule，支持 `reloadValidation`、`getError()`、异步验证器去抖、增强的 CVA 互操作性；**OnPush 成为新组件的默认变更检测策略**（Breaking Change）；**`FetchBackend` 替代 XHR 成为默认 `HttpBackend`**；**WebMCP**：表单/组件暴露为浏览器 AI 智能体可调用的工具接口；完整 TypeScript 6.0 支持。

**核心工程思想**：Angular 的 Zone.js 脏检查体系向 Signal pull-based 响应式图的迁移已完成，与 Vue/Svelte 的细粒度响应式内核设计趋同，Zone.js 从必需依赖逐渐退为可选。

**落地行动指南**：Breaking Changes 重点关注：`ComponentFactoryResolver`/`ComponentFactory` 移除、`createNgModuleRef` 移除、`ChangeDetectorRef.checkNoChanges` 移除、TS 5.9 及更早版本不再支持、Forms `min`/`max` 不再接受字符串、`provideRoutes()` 移除、Hammer.js 集成完全移除。Angular 20 LTS 支持截止 2026 年 11 月 28 日，需制定升级路线图。

> 来源：[Angular v22 release event](https://angular.dev/events/v22) · [angular/angular releases](https://github.com/angular/angular/releases) · [Angular 22 — ANGULARarchitects](https://www.angulararchitects.io/en/blog/angular-22-the-most-important-new-features-at-a-glance/)

---

### 3. React Router v8.0.0（2026-06-16）— ESM 化与开放治理首发 `[GA 正式版]` `[Breaking Changes]`

**核心增量**：React Router v8 于 6 月 16 日正式发布，是迁移至**开放治理模型**（年度 major 版本节奏）后的第一个版本。最重要的结构性变化是**彻底 ESM-only**：`react-router-dom` 包正式移除，所有导入统一走 `react-router` 和 `react-router/dom`，彻底告别 CJS 兼容层；`tsconfig` target 升至 ES2022；CSRF 验证逻辑从依赖 HTTP 头改为直接校验请求 URL 的 host（修复头部伪造攻击向量）。TanStack Start 同期进入 Release Candidate 阶段，v1.0 稳定版即将发布，是 React 生态外最值得关注的全栈框架选项。

**核心工程思想**：ESM-only 是前端生态去除历史 CJS 包袱的关键一步，与 pnpm 11 的 ESM 分发方向一致。CSRF 校验逻辑从"信任 HTTP 头"到"校验 URL host"的转变反映了对 SSRF/请求伪造攻击面的更深理解。

**落地行动指南**：Remix v2 和 React Router v6 已官方 EOL，不再提供安全更新；v7 仍接受安全补丁但不建议继续使用。迁移路径：替换 `react-router-dom` 包名引用、审查所有 CJS `require()` 调用、更新 `tsconfig.json`。

> 来源：[React Router v8 — remix.run](https://remix.run/blog/react-router-v8) · [GitHub Releases](https://github.com/remix-run/react-router/releases)

---

### 4. pnpm 11.7.0 + 11.0 供应链安全革新 `[GA 正式版]` `[Breaking Changes]`

**核心增量**：**pnpm 11.0**（4 月 28 日）是供应链安全导向的重大升级：要求 Node.js 22+、纯 ESM 分发；**SQLite 后端存储**（store v11）替换百万级 JSON 文件目录，单一 `$STORE/index.db`（MessagePack values + WAL 模式）带来更快的 install 速度和更少的 syscall；**`minimumReleaseAge` 默认 1440 分钟（24 小时）**：新版本包安装前必须存在满 1 天（防止恶意包在被发现前的时间窗口内传播）；**`blockExoticSubdeps` 默认 true**：阻止从 Git 仓库或直接 tarball URL 解析传递依赖。**pnpm 11.7.0**（6 月 15 日）新增 `frozenStore`（只读 store 适合 CI）、`--batch` 批量发布 workspace、scope 级鉴权 token。**11.5.3/10.34.2**（6 月 11 日）修复高危安全漏洞：`.npmrc`/`pnpm-workspace.yaml` 中 `${ENV_VAR}` 占位符可被恶意克隆仓库用于泄露环境变量密钥。

**落地行动指南**：使用 pnpm < 11.5.3 或 < 10.34.2 的团队需**立即**升级（有私有 registry token 在 `.npmrc` 中的项目尤为紧急）；Node.js 18 的项目升级 pnpm 11.0+ 前须先升级 Node 至 22+。

> 来源：[pnpm 11.0 blog](https://pnpm.io/blog/releases/11.0) · [pnpm 11.7 release](https://pnpm.io/blog/releases/11.7) · [pnpm supply chain security — Socket.dev](https://socket.dev/blog/pnpm-11-adds-new-supply-chain-protection-defaults)

---

### 5. Playwright v1.61.0（2026-06-15）— WebAuthn Passkey 虚拟认证器 `[GA 正式版]`

**核心增量**：`browserContext.credentials` 新 API 支持创建**虚拟 WebAuthn passkey 认证器**，可拦截并模拟 `navigator.credentials.create()` 和 `navigator.credentials.get()` 调用，使 passkey 注册与认证流程完全进入自动化测试覆盖范围，消除此前只能依赖 mock 策略的测试盲区；**HAR + WebSocket**：HAR 和 trace 录制现在完整捕获 WebSocket 请求（对 WebSocket 密集型应用的调试能力大幅提升）；新增 `page.localStorage` / `page.sessionStorage` 直接读写 API（`WebStorage` 接口）；视频录制新增 `'on-all-retries'`、`'retain-on-first-failure'`、`'retain-on-failure-and-retries'` 粒度模式；Ubuntu 26.04 支持；浏览器版本：Chromium 149、Firefox 151、WebKit 26.5。

**核心工程思想**：WebAuthn/Passkey 在 2026 年已成为 B2C 产品标配认证方案，原生虚拟认证器支持消除了密码类安全流程的测试基础设施壁垒，为安全测试的全自动化打通了最后一公里。

> 来源：[Playwright v1.61.0 GitHub](https://github.com/microsoft/playwright/releases/tag/v1.61.0) · [Playwright Release Notes](https://playwright.dev/docs/release-notes)

---

### 6. TanStack Table V9 beta.16（2026-06-18）— 插件化架构重写 `[TS Preview]`

**核心增量**：V9 架构从单体包转向**按需插件 Tree-shaking 模型**，简单表格的 bundle 从 15–20kb 降至 6–7kb（减少约 60%）；TypeScript 类型检查时间减少 62–86%（通过细化类型推断粒度、消除循环泛型推断链实现）；支持矩阵扩展至 React/Preact/Solid/Vue/Angular/Svelte/Lit 全家桶，单一 adapter pattern 统一不同框架绑定。6 月 18 日 beta.16 是当月多次迭代后的最新 snapshot（beta.14 在 6 月 5 日，beta.15 在 6 月 10 日）。

**落地行动指南**：API 仍在 beta 阶段，生产项目建议等待 RC；现有 V8 代码的 hook API 有较大重构（`useReactTable` 等入口 API 调整），建议在测试环境预演迁移成本。

> 来源：[TanStack Table V9 Beta — digestweb.dev](https://digestweb.dev/articles/2026-06-07/tanstack-table-v9-beta-architectural-shifts-form-learnings) · [TanStack Table V9 Blog](https://tanstack.com/blog/tanstack-table-v9-taking-form) · [GitHub Releases](https://github.com/TanStack/table/releases)

---

### 7. Tailwind CSS v4.3.1（2026-06-12）— 原生滚动条样式与逻辑属性全面扩展 `[GA 正式版]`

**核心增量**：v4.3（5 月 8 日）补齐两个长期需要 JS 方案处理的高频痛点：**原生滚动条样式工具**——`scrollbar-{auto,thin,none}`（映射 CSS `scrollbar-width`）、`scrollbar-thumb-*`/`scrollbar-track-*`（全色板 + 透明度）、`scrollbar-gutter-stable`/`scrollbar-gutter-both`（防止滚动条出现导致的布局偏移），全部基于 W3C CSS Scrollbars Level 1 标准而非 WebKit 私有 API；**逻辑属性工具全面扩展**——`mbs-*`/`mbe-*`/`pbs-*`/`pbe-*`/`inset-s-*`/`inset-e-*`/`block-*`/`inline-*`，旧版 `start-*`/`end-*` inset helpers 已标记弃用；新增 `@container-size` utility；`@variant` 支持堆叠和复合变体。v4.3.1（6 月 12 日 patch）修复 Rspack 兼容性、`@apply` 与 CSS Mixins 协作、`not-*` 取反与 `@container` 查询、节点 26+ `Module#registerHooks` 弃用警告等问题。

> 来源：[Tailwind CSS v4.3](https://tailwindcss.com/blog/tailwindcss-v4-3) · [GitHub Releases](https://github.com/tailwindlabs/tailwindcss/releases)

---

### 8. Biome v2.5 — 500 条规则、跨文件 Lint 与 Watch 模式 `[GA 正式版]`

**核心增量**：Biome v2.5 跨越 500 条 lint 规则里程碑；**Plugin Code Fix**：GritQL 插件现可附加代码修复（unsafe 修复默认需 `--write --unsafe`，可标记为 safe 直接随 `--write` 执行）；**跨文件 Lint**：`noFloatingPromises` 现在可追踪跨模块泛型包装函数中的 floating promise，文件监视器在变更时实时更新类型图和模块图；**`--watch` 模式**（v2.5 新增）：监听文件变更并实时输出 lint/format/check 诊断，无需手动触发；70+ 规则从 nursery 升为 stable；`delimiterSpacing` formatter 选项；concise reporter 模式。此前 v2.4（2 月）已实现不依赖 TypeScript 编译器的类型感知 lint（"Biotype" 特性）、嵌入 CSS/GraphQL 格式化/lint、HTML 可访问性 lint（15 条规则）。

**核心工程思想**：Biome 在 Rust 实现基础上实现了 TypeScript 类型感知 lint 而不启动 tsc 进程，是对 ESLint + `@typescript-eslint` + Prettier 三件套的最具颠覆性替代方案，大型项目 lint 速度可提升数量级。

> 来源：[Biome v2.5](https://biomejs.dev/blog/biome-v2-5/) · [Biome v2.4](https://biomejs.dev/internals/changelog/version/2-4-0/)

---

## 🟢 Tier 3：行业风向与速递

- **Deno v2.8.3（6 月 11 日）**：当前最新稳定版。v2.8.0（5 月 22 日）已内置 TypeScript 6.0.3、V8 14.9，新增 `deno bump-version`/`deno why`/`deno pack`/`deno transpile` 等 6 条子命令，冷 npm install **3.66x 加速**（3,319ms → 906ms），`node:buffer` base64 **3.07x** 加速，HTTP 吞吐 **2.21x** 加速；v2.8.2（6 月 3 日）新增后量子密码学：ML-DSA（FIPS 204）签名与 ML-KEM（FIPS 203）密钥封装；Deno 2.9 canary 中出现 `deno desktop` 命令，指向目录自动检测框架后将构建产物内嵌为带集成 WebView 的原生应用二进制，对标 Tauri 范式。

- **Bun v1.3.14（5 月 13 日）**：内置 `Bun.Image` API（libjpeg-turbo + spng + libwebp，镜像 Sharp API，无需 native addon 构建）；**HTTP/3（QUIC）`Bun.serve()`** 实验性支持；`fetch()` 实验性 HTTP/2+HTTP/3 客户端；隔离链接器全局虚拟 store 带来 **7x 更快的 warm install**；FreeBSD 和 Android 构建支持。Bun 2.0 预期 2026 年底发布。HTTP 吞吐基准：Bun 约 4x 快于 Node.js（合成基准），Lambda 冷启动快 **35%**（156ms vs 245ms），包安装约 **35x 快于 npm**。

- **Next.js 16.3.0-canary.60（6 月 21 日）**：最新 canary，正式版为 16.2.9 LTS（当前 stable，3 月 18 日 16.2 发布带来 Turbopack 默认开启、`next dev` 启动 +400%、`use cache` 指令、Cache Components、SRI 哈希验证，Node.js 20+ 要求）。canary.59（6 月 20 日）升级 swc 70，加入 Turbopack 模块排序优化和 OTEL spans for App Router；canary.52（6 月 16 日）集成 rspack 2.0 和实验性 React Compiler 支持。

- **Vue 3.6.0-beta.16（6 月 17 日）**：Vapor Mode 继续迭代，reactivity 核心通过 alien-signals 重构。Vue 3.5.38 为当前 stable patch 版本。

- **Angular 22.1.0-next.1（6 月 17 日）**：下一个 minor 预发布，包含 HTTP JSONP 弃用公告和跨核心模块安全修复。Angular 22.0.2（6 月 17 日）包含 CSS 转义、HTML 注释分隔符处理和 DOM Clobbering 防护的安全加固。

- **Storybook v10.4.6（6 月 16 日）/ v10.5.0-alpha.7（6 月 16 日）**：v10.x 系列（Storybook 9 在 2025 年 6 月发布，当前已到 v10）；alpha.7 新增 `storybook ai` MCP passthrough——Storybook 开始向 AI-native 组件开发工作流靠拢；CSF 全局变量分级覆盖支持。

- **Vitest v5.0.0-beta.5（6 月 15 日）/ v4.1.9（6 月 16 日）**：v5 beta 系列活跃迭代（beta.5 破坏性变更：内联 `@vitest/runner`，允许 happy-dom/jsdom 环境 window 对象 mutations）；v4 稳定系列同步维护（v4.0 于 6 月 4 日发布，Browser Mode 稳定化，Playwright Trace 支持，可视化回归测试支持）；v4.1（3 月）已新增 tags API 和不再内置独立 Vite 副本。

- **React 19.2.7（6 月 1 日）**：修复 Server Actions 中 `FormData` entries 丢失回归（19.2.6 引入）。无 React 20 计划信息。React Compiler（原 React Forget）已作为 React 19 标配稳定运行，自动 memoization 消除手动 `useMemo`/`useCallback`/`React.memo`。

- **Svelte 5.56.0 + TypeScript 6.0 支持**：`svelte@5.56.0` 支持模板内联 `let` 声明（值定义贴近使用位置）；`svelte-language-server`、`svelte2tsx`、`svelte-check` 全面升级至 TypeScript 6.0 兼容；SvelteKit 新增 `.live()` 实时服务端数据查询 API；Rich Harris 于 6 月 11 日在 Frontend Masters 举办全天 Svelte/SvelteKit 工作坊。

- **Nuxt 4.4.8（6 月 8 日）**：Nuxt 3 将于 2026 年 7 月底 EOL（当前仍接受维护补丁至该日期）。Nuxt 5 规划：基于 Nitro v3、h3 v2 和 Vite Environment API 构建。Nuxt 4.4 中同 key 的 `useFetch`/`useAsyncData` 调用共享 `data`/`error`/`status` refs，消除重复网络请求。

- **esbuild 0.28.1（6 月 11–12 日）**：修复两枚安全漏洞（GHSA-gv7w-rqvm-qjhr、GHSA-g7r4-m6w7-qqqr）。0.28.0（4 月 2 日）已新增 `with { type: 'text' }` 导入支持、`Uint8Array.fromBase64` 支持、Go 编译器升至 v1.25.4（影响 OS 最低要求：Linux kernel 3.2+、macOS 12+）。Vite 8 已将其替换为 Rolldown，esbuild 继续作为独立工具维护。

- **Oxc JetBrains 插件 v0.0.35（6 月 17 日）**：新增 Oxfmt + Oxlint 重启 action；Svelte 文件纳入 Oxfmt 默认格式化范围。Oxlint v1.70.0（同期）新增 React Compiler rule（Rust 移植版本已合入，PR #22942）、Vue `no-dupe-keys`、多条 Unicorn 规则；Oxfmt Beta（2 月 24 日）已达 100% Prettier JS/TS 一致性且最高快 36x；VoidZero 实验性 Oxc Angular 编译器（4 月）声称构建性能提升 20x。

- **Rolldown 1.0 稳定版（5 月 7 日）**：19,000 模块基准：1.61s（Rolldown）vs 40.10s（Rollup）= **25x 提速**；现为 Vite 8 全量用户的底层引擎；`^1.0.0` SemVer API 锁定。

- **shadcn/ui 持续更新（6 月）**：Rhea style（Luma 的更紧凑变体）发布；Admin Kit v2.2.0 加入项目管理和 Todo 板块；Blocks 生态突破 2000+ 个。CLI v4（3 月）已新增 AI agent skills 包（为 Radix/Base UI 提供编码智能体上下文）、设计系统预设（`--preset` 一键配置）、`--dry-run`/`--diff` 变更预览、双原语层支持（Radix UI + Base UI）。

- **TC39 Signals 提案**：仍处于 Stage 1，但 Angular、Vue、Solid、Svelte、MobX、Preact、Qwik、Ember、RxJS 等主流框架核心维护者均已参与共同设计；Chrome Platform Status 已有专项追踪页面；2026 年内晋级 Stage 2 可期。一篇 2026 版 Tasuke Hub 文章将其描述为"响应式成为 JavaScript 语言本身特性"的转折点。

- **WinterTC（Ecma TC55）**：原 W3C WinterCG 于 2024 年 12 月迁移为 Ecma TC55，Cloudflare、Vercel、Deno、Node.js 核心团队参与。可发布正式标准（而非仅社区报告），Core "Minimum Common API" 规定 Node.js/Deno/Bun/Cloudflare Workers/Vercel Edge 必须实现的浏览器兼容 API 子集，向"服务端 JS 的 Write Once, Run Anywhere"迈进。

- **CSS Baseline 5 月 2026 更新**：Container Style Queries（自定义属性）成为 Baseline Newly Available；`lh`/`rlh` CSS 单位（行高相关尺寸）进入 Widely Available；`image-rendering` 属性和 `text-decoration-skip-ink: all` 同步 Newly Available。容器尺寸查询全球支持率 ~92%，视口媒体查询正式退居次要地位。

- **TanStack Query v5.101.0（6 月 2 日）**：替换废弃的 `isServer` 为 `environmentManager.isServer()`；ESLint 插件改进 rest destructuring 检测；Node.js 版本锁定升至 v24.16.0。TanStack Router v1.170.16（6 月 16 日）：Rsbuild SSR 中间件预览支持、轻量级路由匹配缓存、`useMatch` selector 修复。

- **Node.js 未来发布节奏变更**：从 Node.js 27 起改为年度 major 发布（取代奇偶号策略），所有版本均进 LTS，30 个月总 LTS 支持窗口。JSR（JavaScript Registry）40,000+ 包，独立治理委员会已建立，双发布（JSR TypeScript 源码 + npm 编译产物）正成为 2026 年库作者的新最佳实践。
