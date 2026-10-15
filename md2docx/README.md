# md2docx

把**多个 Markdown 片段按规则拼接成一个 Word（.docx）文档**的 Java 示例实现。

- 解析：[commonmark-java](https://github.com/commonmark/commonmark-java)（BSD-2-Clause）
- 生成 Word：[Apache POI](https://poi.apache.org/)（Apache-2.0）
- 全部依赖均为 Apache-2.0 / BSD / MIT 许可，JDK 17+

## 快速开始

```bash
mvn test                          # 单元测试
mvn test -Pschema-validation      # 额外用完整 ECMA-376 schema 校验生成的 docx
mvn -q compile exec:java          # 用 examples/report 下的 4 个 md 生成 target/example-report.docx
```

```java
ComposeOptions options = ComposeOptions.builder()
        .title("订单中心技术方案")
        .subtitle("v1.0 · 2026 年 10 月")
        .tableOfContents(3)                       // 目录，收录 1~3 级标题
        .numberHeadings(true)                     // 标题自动编号 1. / 1.1 / 1.1.1
        .pageBreakBetweenTopLevelSections(true)   // 每章另起一页
        .pageNumbers(true)                        // 页脚页码
        .build();

List<DocSection> sections = List.of(
        DocSection.fromMarkdown(overviewMd),                       // 文件自带的 "# 项目概述" 作为第 1 章标题
        DocSection.titled("技术方案")                               // 只有标题的章节，用来归组
                .child(DocSection.fromMarkdown(archMd).baseDir(dir)) // 2.1，图片相对 dir 解析
                .child(DocSection.fromMarkdown(apiMd)),             // 2.2
        DocSection.of("风险与计划", risksMd));                      // 显式标题，第 3 章

try (OutputStream out = Files.newOutputStream(Path.of("report.docx"))) {
    new MarkdownDocxComposer(options).compose(sections, out);
}
```

完整示例见 `src/main/java/com/example/md2docx/example/ExampleMain.java` 和 `examples/report/`。

## 拼接规则

### 1. 标题层级由大纲决定，而不是由 Markdown 源码决定

`DocSection` 组成一棵树，深度为 `d` 的章节标题就是 `d` 级标题。片段**内部**的标题会整体平移：片段里最浅的那一级（不管写的是 `#` 还是 `##`）落在章节标题的下一级，相对层级保持不变。

所以各片段可以由不同的人、按各自习惯独立编写，随意挂到大纲的任何位置，不需要手工调整 `#` 的数量。

| 写法 | 标题来源 | 片段内标题的位置 |
|---|---|---|
| `DocSection.of(title, md)` | 显式传入 | 降到章节标题下一级 |
| `DocSection.fromMarkdown(md)` | 片段开头的标题（若第一个块是标题），并从正文中移除，避免重复 | 同上 |
| `DocSection.titled(title)` | 显式传入，无正文，通常配合 `.child(...)` | — |
| `DocSection.untitled(md)` | 无标题，内容直接接在上级标题下 | 与同级章节标题平级 |

标题最多 9 级，超出的部分按第 9 级处理。

### 2. 文档级规则（`ComposeOptions`）

| 选项 | 默认值 | 说明 |
|---|---|---|
| `title` / `subtitle` | 无 | 文档开头的标题与副标题 |
| `tableOfContents(n)` | 0（不生成） | 插入真正的 Word 目录域，收录 1~n 级标题。目录项会预先填好并可点击跳转，所以在 WPS、LibreOffice、预览器里也能直接看到目录；Word 更新域后会补上页码 |
| `updateFieldsOnOpen` | true | 让 Word 打开时更新目录。Word 会为此弹出一次确认框；设为 false 时需要用户手动按 F9 |
| `numberHeadings` | false | 用 Word 的多级列表给标题自动编号（不是把数字写成文字），手动增删章节后编号仍然正确 |
| `pageBreakBetweenTopLevelSections` | false | 除第一章外，每个顶级章节另起一页。单个章节可以用 `DocSection.pageBreakBefore(..)` 覆盖 |
| `pageBreakAfterFrontMatter` | true | 有标题或目录时，正文从新的一页开始 |
| `pageNumbers` | false | 页脚居中页码。模板已经有页脚时不会重复添加 |
| `template(path)` | 无 | 复用一个 .docx/.dotx 的样式、页面设置和页眉页脚，丢弃它的正文。详见下文 |
| `fonts(latin, eastAsia, mono)` | Calibri / Microsoft YaHei / Consolas | 不使用模板时的默认字体 |
| `imageResolver` | `ImageResolver.localFiles()` | 图片加载策略，见下文 |

### 3. 跨片段的处理

- **脚注**：脚注标签按片段隔离。两个文件都写了 `[^1]` 时，会生成两个独立的脚注，Word 会连续编号。
- **有序列表**：每个列表都会重新编号，并保留起始数字（`3. xxx` 从 3 开始），不会接着上一个文件的编号往下排。
- **锚点链接**：`[见接口设计](#接口设计)` 可以跳转到**任意片段**里的同名标题。先在本片段内查找，找不到再在全文查找；锚点按 GitHub 规则生成（小写、去掉标点、空格换成 `-`、保留中文）。找不到目标的链接只显示文字，不生成链接。
- **图片**：相对路径按所在章节的 `baseDir` 解析。
- **相邻表格**：两个片段首尾各有一个表格时，中间会自动插入一个空段落，否则 Word 会把它们合并成一个表格。

### 4. 模板

```java
ComposeOptions.builder().template(Path.of("公司模板.docx"))
```

- 样式**按名称匹配**（如 `heading 1`、`Quote`、`toc 1`）。中文版 Word 保存的模板，标题样式 ID 是 `1`、`2`……，也能正确识别。
- 模板里缺少的样式（如代码块、表格样式）会用内置的默认样式补上。
- 模板的标题样式如果已经带编号，就不会再叠加 `numberHeadings` 的编号。
- `.dotx` 会自动转换为 `.docx` 的内容类型。
- 中文文档的模板请为正文样式或文档默认值配置东亚字体。如果模板把东亚字体指向西文字体（例如 pandoc 的默认 `reference.docx`），部分阅读器里的中文可能显示为方块。

## Markdown 支持范围

| 支持 | 说明 |
|---|---|
| 标题、段落、粗体、斜体、删除线、行内代码 | |
| 有序、无序列表，多级嵌套，任务列表（☐ / ☒） | 使用 Word 的真编号 |
| GFM 表格 | 支持列对齐；表头跨页重复；行不跨页拆分；列宽按内容长度分配（中文字符按 2 个宽度计算） |
| 代码块 | 等宽字体，保留缩进和空行，带底纹和边框 |
| 引用块（可嵌套）、分割线 | |
| 图片 | PNG / JPEG / GIF / BMP。超出页面宽度时等比缩小；单独成段的图片居中 |
| 链接、自动识别的网址 | 只有 http、https、mailto、ftp 会生成超链接 |
| 脚注 | 生成 Word 原生脚注 |
| `<br>` | 换行。其他内联 HTML 标签会被忽略，只保留标签之间的文字 |

**不支持**：LaTeX 公式（原样输出为文本）、代码语法高亮、HTML 块（去掉标签后按纯文本输出）。

## 安全默认值

Markdown 来自用户或 LLM 时，以下默认值可以防止被滥用：

- `ImageResolver.localFiles()` 只读取 `baseDir` 目录内的文件和 `data:image/...;base64` 格式的图片，**不发起任何网络请求**（防 SSRF），并拒绝 `../`、绝对路径等越出 `baseDir` 的路径。需要读取对象存储或远程图片时，请自行实现 `ImageResolver`，并在实现里做好白名单控制。
- `javascript:`、`file:` 等协议的链接只显示文字。
- XML 1.0 不允许的控制字符会被清除。这类字符常见于复制粘贴或 LLM 的输出，混进 docx 会导致 Word 报"文件已损坏"。

## 如何确认"能正常渲染"

- `mvn test -Pschema-validation`：把 poi-ooxml-lite 换成 poi-ooxml-full，用 ECMA-376 schema 校验生成文档的 document、styles、numbering、footnotes、settings、footer 等部件，任何一个元素顺序或属性错误都会让测试失败。默认构建会跳过这个测试，因为 lite 包里缺少部分 schema 类。
- 单元测试覆盖：标题层级、跨片段脚注、列表重新编号、跨片段锚点、图片路径安全、宽图缩放、目录、分页、模板样式匹配、异常输入。
- 示例文档已在 LibreOffice 中渲染核对。**目前还没有在 Microsoft Word 和 WPS 中人工打开验证**，上线前建议各打开一次。

## 其他说明

- POI 使用 log4j-api 打日志。如果项目里没有日志实现，运行时会打印一行 `Log4j API could not find a logging provider`，不影响功能。需要时可以引入 `log4j-core`，或者用 `log4j-to-slf4j` 桥接到现有的日志框架。
- `MarkdownDocxComposer` 是无状态的，可以安全地在多个线程间共享，每次调用 `compose` 都会生成一份独立的文档。

## 代码结构

```
src/main/java/com/example/md2docx/
├── MarkdownDocxComposer.java   入口
├── DocSection.java             大纲节点（标题 / 正文 / 子章节）
├── ComposeOptions.java         文档级规则
├── ImageResolver.java          图片加载策略
├── Composition.java            单次拼接的状态：标题编号、目录、锚点、脚注、列表编号、页面设置
├── FragmentRenderer.java       单个 Markdown 片段 → Word 段落、表格、图片
├── Styles.java                 内置样式，以及与模板样式的按名匹配
└── Texts.java                  文本工具：XML 清洗、锚点 slug、中文宽度判断
```
