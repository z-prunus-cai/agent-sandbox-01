# 设计文档 → Confluence 流水线(POC)

把 `docs/**/*.adoc`(含 PlantUML 图表)通过 **Confluence Publisher CLI** 发布到 Confluence。
**Git 是唯一事实来源(SSOT)**,Confluence 是单向只读镜像。Gradle 调用 CLI,纯 JVM,无需 Docker。

本仓库是对应设计方案的**可运行 POC**,已用离线 `convertOnly` 端到端验证通过。

## 多版本发布(本次新增)

由 `docs-versions.yaml`(仓库根,SSOT)定义**要同步的所有 git 标签 / 分支 / sha 及其展示名**。
构建时,在 Confluence 根页(`ancestorId`,即"根目录")下,为清单里**每个 ref + 一个 `latest`**
各生成一个「版本页」,每个版本页的整棵子树 = 对应 ref 检出时的 `docs/` 原样内容 ——
**各版本互不影响,分别反映各自 ref 的文档**。`latest` 指向"自己"(当前检出/工作区 HEAD)。

```yaml
# docs-versions.yaml
latestName: "latest(当前)"     # latest 版本页的展示名(latest = 自己)
versions:
  - ref: ec067a6                # tag / branch / sha 均可
    name: "v2.0"
  - ref: b4d8f0b
    name: "v1.0"
```

装配由 Gradle 的 `assembleVersionedDocs` 完成(`confluenceConvert` / `confluencePublish` 会自动先跑它):

- `latest` 取**当前工作区** `docs/`;其余版本用 `git archive <ref> docs` 取历史内容;
- 产物在 `build/versioned-docs/`:顶层每个 `<slug>.adoc` 是版本落地页(展示名作标题 + ref 元信息 +
  只读横幅),其子文件夹 `<slug>/` 是该 ref 的 `docs/` 全树;
- 为避开 **Confluence 全空间标题唯一**的硬限制,装配时给每个版本子树内所有页标题统一加
  `[展示名]` 前缀。

> CI 注意:历史版本靠 `git archive` 取,checkout 必须是**全历史**(`fetch-depth: 0`,已在 workflow 配好),
> 否则浅克隆里找不到旧 ref。

## 快速开始

```bash
# 1) 离线转换校验(不连 Confluence,CI/本地校验文档没写坏)
./gradlew confluenceConvert
# 产物:build/confluence/  (Confluence 存储格式 XHTML + PlantUML 渲染的 PNG 附件)

# 2) 真正发布(需凭据)
./gradlew confluencePublish \
  -PconfluenceUrl=https://your-org.atlassian.net/wiki \
  -PconfluenceSpaceKey=DOCS \
  -PconfluenceAncestorId=123456 \
  -PconfluenceUsername=you@org.com \
  -PconfluencePassword=<API_TOKEN>
```

也可用环境变量:`CONFLUENCE_URL / CONFLUENCE_SPACE_KEY / CONFLUENCE_ANCESTOR_ID / CONFLUENCE_USERNAME / CONFLUENCE_PASSWORD`。

## 技术栈(版本锁定,见 `gradle.properties`)

| 项 | 值 |
|---|---|
| 同步工具 | Confluence Publisher CLI `0.35.0` |
| 调用方式 | Gradle `JavaExec` 调官方 main 类(纯 JVM,免 Docker) |
| 图表 | PlantUML + Smetana(`!pragma layout smetana`,免 Graphviz)→ PNG 附件 |
| 运行要求 | JDK 11+;构建环境需有 **CJK 字体**(PlantUML 把中文光栅化进 PNG) |

CLI 及其依赖(含 `asciidoctorj-diagram` + `plantuml`)由 Gradle 从 Maven Central 解析为
`confluenceCli` configuration,用 `JavaExec` 运行 main 类
`org.sahli.asciidoc.confluence.publisher.cli.AsciidocConfluencePublisherCommandLineClient`。

## 目录结构

```
docs-versions.yaml        # 版本清单(SSOT):要同步的 tag/branch/sha + 展示名;latest = 自己
docs/                     # 「当前(latest)」版本的源
  index.adoc              #   根页「产品设计文档:订单履约系统」(含"勿编辑"横幅)
  index/                  #   index.adoc 的子页(folder 名 = 父文件名去掉 .adoc)
    01-overview.adoc      #     子页「概述」
    02-architecture.adoc  #     子页「系统架构」(内联 PlantUML → PNG 附件)
    03-flow.adoc          #     子页「履约流程」(内联 PlantUML → PNG 附件)
  images/                 #   静态图片(本 POC 未用)
  diagrams/               #   外部 .puml(本 POC 内联,未用)
build.gradle.kts          # Gradle:assembleVersionedDocs / confluenceConvert / confluencePublish
gradle.properties         # CLI 版本锁
.github/workflows/docs-confluence.yml
```

`assembleVersionedDocs` 会把上面 + 各历史 ref 装配到 `build/versioned-docs/`(真正喂给 CLI 的根)。

约定:每个 `.adoc` = 一个 Confluence 页,页标题取文档首个 `= 一级标题`;
`foo.adoc` 与同名 `foo/` 文件夹配对形成父子层级。多版本下,顶层每个 `<slug>.adoc` = 一个版本页,
其 `<slug>/` 子树 = 该 ref 的整棵 `docs/`。

### 哪些 `.adoc` **不**成页(include 片段的排除规则)

想让某个 `.adoc` 只作被 `include::` 的**片段**、而不单独生成一个 Confluence 页,有两种办法
(均为 Confluence Publisher 的原生判定,本仓库已实测):

1. **文件名以 `_` 开头** —— 如 `_shared-legal.adoc`。它不会成页,但仍可被 `include::_shared-legal.adoc[]` 内联进正文。**这是首选、最明确的写法。**
2. **放进"孤儿文件夹"** —— 即某文件夹没有与它同名的配对 `.adoc`(如 `partials/`、或 `_partials/`)。
   该文件夹整体不会被下钻,里面的 `.adoc` 都不成页(旧 `v1.0` 版本的 `chapters/` 就是这样,只出 1 页)。

```
docs/
  index.adoc                # 成页
  _shared-legal.adoc        # ✗ 不成页(_ 前缀);被 index.adoc include
  index/
    01-overview.adoc        # 成页
    _partials/              # ✗ 整个文件夹不下钻(孤儿 + _ 前缀)
      tip.adoc              #   只作片段,被 01-overview.adoc include
```

> 片段自身**不要**用 `= 一级标题`(那是页标题级),否则被 include 进 article 会触发
> asciidoctor 的 “level 0 sections can only be used when doctype is book”。片段用 `==`、
> 纯内容或 `[NOTE]` 之类即可;需要保留层级时用 `include::x.adoc[leveloffset=+1]`。

装配时,给各版本页标题加 `[展示名]` 前缀的这步**只作用于"会成页"的 `.adoc`**(按上面同一套规则判定),
不碰 include 片段,避免破坏 include。

## 验证结果(本机实跑 `confluenceConvert`)

| 验证点 | 结果 |
|---|---|
| Gradle 调 CLI | ✅ 解析 CLI 0.35.0 + 依赖,`JavaExec` 运行,`BUILD SUCCESSFUL` |
| 离线转换 | ✅ `convertOnly=true` 走本地分支,不连 Confluence |
| AsciiDoc→存储格式 | ✅ 输出合法 Confluence XHTML;NOTE/TIP → `ac:structured-macro`(info/tip 宏),表格、中英混排、内联样式正常 |
| PlantUML(Smetana) | ✅ 组件图 + 时序图渲染成 **PNG**,经 `<ac:image><ri:attachment>` 作为**附件**引用,**无 Graphviz** |
| 中文 | ✅ XHTML 与 PNG 中文均正确渲染(非方块) |
| 页面树 | ✅ `index` 根页 + 3 子页,folder 约定生效 |
| **多版本装配** | ✅ `docs-versions.yaml`(latest + 2 个 ref)→ 根页下 3 个版本页,共 12 页;标题 `[展示名]` 前缀去重 |
| **各版本内容独立** | ✅ `latest`/`v2.0` 为多页树(图表出 PNG);`v1.0`(旧 commit)为单页(章节 include 内联,图表出 SVG)—— 各自反映各自 ref |
| **latest = 自己** | ✅ `latest` 取当前工作区 `docs/`(HEAD),ref 元信息显示解析出的短 sha |

## 关键设计点

- **单向 + 幂等**:`orphanRemovalStrategy=REMOVE_ORPHANS`(默认)+ 增量发布 —— Git 删/改页自动同步,
  Confluence 成只读镜像。根页顶部有"请勿编辑"横幅。
- **发布锚点是页面而非空间根**:`ancestorId`(必填)指定一个 Confluence 页作为根;
  所有内容挂在这棵子树下,**孤儿删除也只在这棵子树内生效**,不动空间里其他页。
  → 务必用**专用 ancestor 页 / 专用空间**,别指向别人维护的页面树。
- **`APPEND_TO_ANCESTOR`(默认)**:文档作为 ancestor 的子页;
  若想让 ancestor 页**本身**变成文档根,改用 `REPLACE_ANCESTOR`(要求根 `.adoc` 唯一)。
- **凭据不入库**:本地用 `-P` 参数,CI 用 secrets(`confluencePublish` 会在缺凭据/占位 ancestorId 时报错阻止误发)。
  Cloud 认证 = 邮箱(username)+ API token(password)。

## 已知坑

1. **PlantUML 中文依赖构建环境的 CJK 字体**:PNG 光栅化走 JVM/AWT + fontconfig。
   本机有文泉驿;CI 精简镜像需显式安装(见 workflow 的 `apt-get install fonts-noto-cjk`),否则中文出方块。
2. **标题全空间唯一**是 Confluence 硬限制;多来源发同一空间时用 `pageTitlePrefix`/`pageTitleSuffix`。
3. **CLI `notifyWatchers` 默认为 `true`**(与 Maven 插件不同),本 POC 显式设 `false` 避免刷屏。
4. Confluence Publisher 仍是 **0.x**:功能成熟、活跃维护(0.35.0 发布于 2026-07),但不承诺 API 稳定,升级读 changelog。

## 与上一版(PDF 流水线)的关系

本次按需求改造:**去掉 PDF、不引入 Markdown**,改为 AsciiDoc → Confluence。
随之移除了 PDF 专用的 `asciidoctor-pdf` 插件、`docs/theme/` 主题与 49MB CJK 字体
(Confluence 自带渲染,无需入库字体)。PlantUML 输出从 SVG 改为 PNG(Confluence 附件对 PNG 更稳)。
