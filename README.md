# 设计文档 → Confluence 流水线(POC)

把 `docs/**/*.adoc`(含 PlantUML 图表)通过 **Confluence Publisher CLI** 发布到 Confluence。
**Git 是唯一事实来源(SSOT)**,Confluence 是单向只读镜像。Gradle 调用 CLI,纯 JVM,无需 Docker。

本仓库是对应设计方案的**可运行 POC**,已用离线 `convertOnly` 端到端验证通过。

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

## 目录结构(Confluence 页面树约定)

```
docs/
  index.adoc              # 根页「产品设计文档:订单履约系统」(含"勿编辑"横幅)
  index/                  # index.adoc 的子页(folder 名 = 父文件名去掉 .adoc)
    01-overview.adoc      #   子页「概述」
    02-architecture.adoc  #   子页「系统架构」(内联 PlantUML → PNG 附件)
    03-flow.adoc          #   子页「履约流程」(内联 PlantUML → PNG 附件)
  images/                 # 静态图片(本 POC 未用)
  diagrams/               # 外部 .puml(本 POC 内联,未用)
build.gradle.kts          # Gradle 调 CLI:confluenceConvert / confluencePublish
gradle.properties         # CLI 版本锁
.github/workflows/docs-confluence.yml
```

约定:每个非 include 的 `.adoc` = 一个 Confluence 页,页标题取文档首个 `= 一级标题`;
`foo.adoc` 与同名 `foo/` 文件夹配对形成父子层级。

## 验证结果(本机实跑 `confluenceConvert`)

| 验证点 | 结果 |
|---|---|
| Gradle 调 CLI | ✅ 解析 CLI 0.35.0 + 依赖,`JavaExec` 运行,`BUILD SUCCESSFUL` |
| 离线转换 | ✅ `convertOnly=true` 走本地分支,不连 Confluence |
| AsciiDoc→存储格式 | ✅ 输出合法 Confluence XHTML;NOTE/TIP → `ac:structured-macro`(info/tip 宏),表格、中英混排、内联样式正常 |
| PlantUML(Smetana) | ✅ 组件图 + 时序图渲染成 **PNG**,经 `<ac:image><ri:attachment>` 作为**附件**引用,**无 Graphviz** |
| 中文 | ✅ XHTML 与 PNG 中文均正确渲染(非方块) |
| 页面树 | ✅ `index` 根页 + 3 子页,folder 约定生效 |

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
