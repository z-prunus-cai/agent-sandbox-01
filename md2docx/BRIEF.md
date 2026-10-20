# 多个 Markdown 片段拼接成一个 Word 文档（Java）

## 1. 范围

用 Java 把多个 Markdown 片段按一棵章节树拼成一个 .docx 文档。章节树决定标题层级，片段内部的标题整体平移到所在章节之下；脚注、有序列表编号、锚点链接在片段之间互不干扰；图片只从指定目录或 `data:` 数据读取，不访问网络；可选生成标题、目录、标题编号、分页、页码，或套用 .docx / .dotx 模板。每项功能放在独立的源文件中（见 3.2），可以只取需要的部分。目标阅读软件是 Microsoft Word。本文所有结论都在 Linux 上用 JDK 25 实测；生成的文档按 ECMA-376（Office Open XML，即 .docx 格式的国际标准）的完整 schema（XML 结构定义）校验，并用 LibreOffice 24.2 渲染检查；**没有在 Microsoft Word 中实际打开过**。

本文用到的术语：

- **片段**：一段 Markdown 文本，通常是一个 .md 文件的内容。
- **章节**：章节树中的一个节点（类 `Section`），可以有标题、一个片段、图片目录和子章节。
- **POI**：Apache POI，读写 Office 文件的 Java 库；**XMLBeans** 是 POI 用来读写 XML 的库。**commonmark-java**：Markdown 解析库。**GFM**：GitHub Flavored Markdown，GitHub 的 Markdown 方言，表格、删除线、任务列表来自它。
- **run**：Word 段落中格式相同的一段文字，粗体、斜体等属性设在 run 上。
- **twip**：Word 的长度单位，1/20 磅；1440 twip = 1 英寸 = 2.54 cm。**EMU**：图片尺寸单位，1 英寸 = 914400 EMU，1 twip = 635 EMU。
- **域（field）**：Word 中由指令生成内容的区域，例如目录（`TOC`）和页码（`PAGE`）。
- **书签（bookmark）**：Word 文档中有名字的位置，文档内链接以书签为目标。
- **部件（part）**：.docx 是一个 ZIP 包，其中每个 XML 文件是一个部件，例如正文 `word/document.xml`；部件之间通过“关系”（relationship，记录在 `_rels/*.rels` 中）互相引用。
- **JEP**：JDK Enhancement Proposal，JDK 的功能提案。

## 2. 环境

| 组件 | 版本 | 用途 |
| --- | --- | --- |
| JDK | 25（实测 Temurin 25.0.4.1，Eclipse Adoptium 发布的 OpenJDK 构建） | 编译与运行；`java` 直接运行多文件源码需要 JDK 22 及以上 |
| `org.apache.poi:poi-ooxml` | 5.5.1 | 生成 .docx |
| `org.commonmark:commonmark` 及 5 个扩展 | 0.30.1 | 解析 Markdown |
| `org.junit.platform:junit-platform-console-standalone` | 6.1.3 | 仅测试：JUnit 命令行运行器 |
| `org.apache.poi:poi-ooxml-full` | 5.5.1 | 仅测试：完整的 ECMA-376 schema |
| LibreOffice（命令 `soffice`） | 24.2.7.2 | 仅用于渲染检查，运行示例和测试不需要 |
| pdftotext（poppler-utils） | 24.02.0 | 仅用于渲染检查：从 PDF 取出文字 |

本目录不带构建工程，也不下载依赖。依赖用 Gradle 的写法列出如下，交给你自己的构建工具解析：

```kotlin
repositories {
    mavenCentral()
}

dependencies {
    implementation("org.apache.poi:poi-ooxml:5.5.1")
    implementation("org.commonmark:commonmark:0.30.1")
    implementation("org.commonmark:commonmark-ext-gfm-tables:0.30.1")
    implementation("org.commonmark:commonmark-ext-gfm-strikethrough:0.30.1")
    implementation("org.commonmark:commonmark-ext-task-list-items:0.30.1")
    implementation("org.commonmark:commonmark-ext-footnotes:0.30.1")
    implementation("org.commonmark:commonmark-ext-autolink:0.30.1")

    testImplementation("org.junit.platform:junit-platform-console-standalone:6.1.3")
    testImplementation("org.apache.poi:poi-ooxml-full:5.5.1")
}
```

这 7 个运行时坐标连同传递依赖一共 20 个 jar（实测解析结果）：`poi-5.5.1`、`poi-ooxml-5.5.1`、`poi-ooxml-lite-5.5.1`、`xmlbeans-5.3.0`、`commons-codec-1.20.0`、`commons-math3-3.6.1`、`SparseBitSet-1.3`、`commons-compress-1.28.0`、`commons-lang3-3.18.0`、`commons-io-2.21.0`、`curvesapi-1.08`、`log4j-api-2.24.3`、`commons-collections4-4.5.0`、7 个 commonmark jar（`commonmark`、5 个 `commonmark-ext-*`，均为 0.30.1），以及 `autolink-0.12.0`。

第 3、6 节的命令都在本目录（`md2docx/`）下执行，并假定已设置三个环境变量：

```bash
export CLASSPATH=<上面 20 个运行时 jar 的路径，用 : 分隔>
export JUNIT_JAR=<junit-platform-console-standalone-6.1.3.jar 的路径>
export POI_FULL_JAR=<poi-ooxml-full-5.5.1.jar 的路径>
java -version     # 必须是 25
```

`java` 运行源码时从 `CLASSPATH` 环境变量读取类路径。

## 3. 示例代码

### 3.1 目录

```
md2docx/
  examples/
    src/md2docx/         拼接器（包 md2docx），每项功能一个文件，见 3.2
    src/demo/            两个可运行的示例入口
    markdown/minimal/    最简示例的两个片段
    markdown/full/       完整示例的四个片段和一张图片
  tests/md2docx/         JUnit 测试，与 examples/src 一起编译
```

### 3.2 功能与文件

| 功能 | 文件 | 内容 |
| --- | --- | --- |
| 公开接口 | `Section.java` | 章节树节点：`of`、`titled`、`fromMarkdown`、`untitled`，`baseDirectory`、`pageBreakBefore`、`child` |
| | `ComposeOptions.java` | 文档级选项（构建器） |
| | `MarkdownDocxComposer.java` | 入口：`compose(sections, out)`、`compose(sections)` |
| | `ImageResolver.java` | 图片读取接口和默认实现 `localFiles()` |
| 核心拼接 | `Composition.java` | 一次拼接：创建文档、遍历章节树、计算章节标题层级、收尾 |
| | `Fragment.java` | 渲染一个片段：按节点类型分派给各功能类，计算片段内标题的平移，处理段落、引用、代码、行内格式和链接 |
| | `Container.java` | 块元素的去处：正文或脚注 |
| | `Styles.java` | 内置样式定义；套用模板时按名称查找样式 |
| | `Lists.java` | 列表：每个 Markdown 列表一个编号实例，有序列表从自己的起始数字开始，任务列表的勾选框 |
| | `Tables.java` | 表格：列宽、表头重复、行不跨页、相邻表格分隔 |
| | `PageSetup.java` | 正文区域尺寸（`of`）、新文档的 A4 页面（`applyA4`）、页码（`addPageNumbers`） |
| | `Texts.java` | 写入文字、去除 XML 不允许的字符、HTML 块取文字、宽字符计数 |
| 跨片段处理 | `Footnotes.java` | 收集脚注定义、按片段隔离、渲染脚注 |
| | `Anchors.java` | 标题书签、GitHub 锚点规则、链接解析 |
| 图片与安全 | `ImageResolver.java` | 只读 `baseDirectory` 内的文件和 `data:` 数据 |
| | `Images.java` | 识别格式、按可用宽度缩放、替代文字 |
| | `Links.java` | 只让 http、https、mailto、ftp 生成超链接 |
| 文档级排版 | `FrontMatter.java` | 标题、副标题、目录 |
| | `HeadingNumbering.java` | 标题自动编号 |
| | `Templates.java` | 打开模板、.dotx 转 .docx、清空正文 |

文件之间的引用关系（不含注释中的引用，用 `grep -w` 逐个类名检查得到）：

| 文件 | 用到的其他文件 |
| --- | --- |
| `MarkdownDocxComposer` | `ComposeOptions`、`Composition`、`Section` |
| `Composition` | `Anchors`、`ComposeOptions`、`Footnotes`、`Fragment`、`FrontMatter`、`HeadingNumbering`、`Lists`、`PageSetup`、`Section`、`Styles`、`Templates`、`Texts` |
| `Fragment` | `Composition` 的字段（`anchors`、`document`、`footnotes`、`headingNumbering`、`lists`、`options`、`page`、`styles`）、`Container`、`Footnotes`、`Images`、`Links`、`Lists`、`Styles`、`Tables`、`Texts` |
| `FrontMatter` | `Anchors`、`ComposeOptions`、`Composition`（`insertBefore`）、`HeadingNumbering`、`Styles`、`Texts` |
| `HeadingNumbering` | `Composition`（`MAXIMUM_LEVEL`）、`Lists`、`Styles` |
| `Footnotes` | `Container`、`Styles`、`Texts` |
| `Styles` | `ComposeOptions`、`PageSetup`、`Texts` |
| `Lists`、`Links` | `Styles`、`Texts`（`Links` 只用 `Texts`） |
| `Tables` | `Container`、`Texts` |
| `Images` | `ImageResolver`、`Texts` |
| `ComposeOptions` | `ImageResolver` |
| `Anchors`、`Container`、`ImageResolver`、`PageSetup`、`Section`、`Templates`、`Texts` | 无 |

只实现一部分功能时，从 `Composition` 和 `Fragment` 中删去不需要的功能的调用，再删掉不再被引用的文件。下面两种删法都实测过：删完后 `javac` 编译通过，`MinimalDemo` 正常输出 `wrote m.docx`。

不要脚注：

1. 删除 `Footnotes.java`；
2. `Composition.java`：从 `PARSER` 的扩展列表中去掉 `FootnotesExtension.create()` 及其 import，删去字段 `footnotes` 和构造函数中的 `footnotes = new Footnotes(document, styles);`；
3. `Fragment.java`：删去字段 `footnoteDefinitions` 及其赋值，删去 `renderInline` 中处理 `FootnoteReference` 的分支。

不要文档级排版（标题、目录、标题编号、页码、模板）：

1. 删除 `FrontMatter.java`、`Templates.java`、`HeadingNumbering.java`；
2. `Composition.java`：构造函数中只保留 `document = new XWPFDocument(); PageSetup.applyA4(document);` 和 `styles = Styles.create(document, options);`，删去字段 `headingNumbering` 及其赋值；`compose` 中删去从注释 `the front matter is inserted` 到 `enforceUpdateFields` 的 6 行；
3. `Fragment.java`：把 `String number = composition.headingNumbering == null ? null : composition.headingNumbering.next(level);` 改成 `String number = null;`。

### 3.3 最简示例

`examples/src/demo/MinimalDemo.java` 把两个文件各放在一个标题下，再加一段话：

```java
byte[] document = new MarkdownDocxComposer().compose(List.of(
        Section.of("文档一", Files.readString(input.resolve("first.md"))),
        Section.of("文档二", Files.readString(input.resolve("second.md"))),
        Section.of("其他", "这是另一段话。")));
```

`first.md` 以 `# 背景` 开头，`second.md` 以 `# 用法` 开头。运行：

```
$ java examples/src/demo/MinimalDemo.java
2026-10-20T10:47:05.164585332Z main ERROR Log4j API could not find a logging provider.
wrote minimal.docx
```

第一行来自 POI 使用的日志接口，不影响结果（见第 5 节）。`minimal.docx` 用 LibreOffice 转成 PDF 后的文字（`soffice --headless --convert-to pdf minimal.docx`，再 `pdftotext -layout minimal.pdf -`）：

```
文档一

背景
第一个文件的正文。


目标

●   第一项
●   第二项



文档二

用法
第二个文件的正文，见背景。



其他
这是另一段话。
```

`文档一` 是 1 级标题，文件自己的 `# 背景` 变成 2 级、`## 目标` 变成 3 级。`second.md` 中的 `[背景](#背景)` 在本片段找不到目标，于是到全文查找，链到 `first.md` 的“背景”。

### 3.4 完整示例

`examples/src/demo/FullDemo.java` 打开全部选项：

```java
ComposeOptions.Builder options = ComposeOptions.builder()
        .title("示例文档")
        .subtitle("由四个 Markdown 文件拼接")
        .tableOfContents(3)
        .numberHeadings(true)
        .pageBreakBetweenTopLevelSections(true)
        .pageNumbers(true);
if (args.length > 2) options.template(Path.of(args[2]));

List<Section> sections = List.of(
        Section.fromMarkdown(Files.readString(input.resolve("01-overview.md"))),
        Section.titled("设计")
                .child(Section.untitled(Files.readString(input.resolve("02-structure.md"))).baseDirectory(input))
                .child(Section.fromMarkdown(Files.readString(input.resolve("03-interface.md")))),
        Section.of("附注", Files.readString(input.resolve("04-notes.md"))));
```

```
$ java examples/src/demo/FullDemo.java
2026-10-20T10:47:07.311230389Z main ERROR Log4j API could not find a logging provider.
wrote full.docx
```

第三个参数可以传入一个模板文件：`java examples/src/demo/FullDemo.java examples/markdown/full full.docx 模板.docx`。

`full.docx` 共 4 页，LibreOffice 渲染后的文字（节选，`pdftotext -layout full.pdf -`）：

```
                         示例文档
                     由四个 Markdown 文件拼接


                           目录

1. 概述

   1.1 范围

   1.2 术语

2. 设计

   2.1 结构

        2.1.1 处理顺序

   2.2 接口

3. 附注

   3.1 说明
```

```
2.2 接口

    方法                                                   说明                返回
    compose(sections)                                  生成文档              byte[]
    compose(sections, out)                            写入输出流                  无

3.    编号从 3 开始
4.    接着是 4
●     ☒ 已完成的任务
●     ☐ 未完成的任务

另一个脚注2。




2
    这是第三个文件的脚注，标签与第一个文件相同。
```

各片段在章节树中的位置与结果：

| 章节 | 片段开头 | 结果 |
| --- | --- | --- |
| `fromMarkdown(01-overview.md)` | `# 概述` | 第 1 章“1. 概述”，取自片段，正文中不再重复 |
| `titled("设计")` | 无正文 | 第 2 章，只用来归组 |
| `untitled(02-structure.md)` | `## 结构` | 没有章节标题；片段中最浅的标题与同级章节标题平级，成为“2.1 结构” |
| `fromMarkdown(03-interface.md)` | `# 接口` | “2.2 接口” |
| `of("附注", 04-notes.md)` | `# 说明` | “3. 附注”，片段的 `# 说明` 成为“3.1 说明” |

`01-overview.md` 和 `03-interface.md` 都用了脚注标签 `[^1]`，结果是两个独立的脚注 1 和 2。`03-interface.md` 的有序列表以 `3.` 开头，结果从 3 开始编号。

### 3.5 接口

`Section` 的四种构造方式：

| 写法 | 章节标题 | 片段中最浅的标题 |
| --- | --- | --- |
| `Section.of(title, markdown)` | 传入的 `title` | 章节标题的下一级 |
| `Section.fromMarkdown(markdown)` | 片段第一个块是标题时取它，并从正文中去掉；否则没有标题 | 同上 |
| `Section.titled(title)` | 传入的 `title`，没有正文，通常配合 `.child(Section.of("子章节", markdown))` | — |
| `Section.untitled(markdown)` | 没有，正文直接接在上级标题下 | 与同级章节标题平级 |

深度为 d 的章节标题是 d 级标题，片段中的标题保持相对层级平移；超过 9 级的按 9 级处理。`title` 为空或只有空白时抛出 `IllegalArgumentException`。`baseDirectory(path)` 设置本章节图片的根目录，子章节不继承；`pageBreakBefore(boolean)` 对本章节覆盖分页选项。

`ComposeOptions.builder()` 的选项：

| 方法 | 默认值 | 作用 |
| --- | --- | --- |
| `title(String)`、`subtitle(String)` | 无 | 文档开头的标题、副标题 |
| `tableOfContents(int)` | 0（没有目录） | 收录 1 到 n 级标题的目录；n 超出 0 到 9 时抛出 `IllegalArgumentException` |
| `tableOfContentsTitle(String)` | `目录` | 目录上方的标题 |
| `updateFieldsOnOpen(boolean)` | 开 | 有目录时，让 Word 打开文档时更新域 |
| `numberHeadings(boolean)` | 关 | 标题自动编号 1.、1.1、1.1.1 |
| `pageBreakBetweenTopLevelSections(boolean)` | 关 | 第一个顶级章节之后，每个顶级章节另起一页 |
| `pageBreakAfterFrontMatter(boolean)` | 开 | 有标题、副标题或目录时，正文另起一页 |
| `pageNumbers(boolean)` | 关 | 页脚居中页码；模板已有页脚时不添加 |
| `template(Path)` | 无 | 套用 .docx / .dotx 模板 |
| `imageResolver(ImageResolver)` | `ImageResolver.localFiles()` | 图片读取方式 |
| `fonts(latin, eastAsian, monospace)` | Calibri、Microsoft YaHei、Consolas | 不用模板时的西文、东亚、等宽字体 |

`MarkdownDocxComposer` 不保存状态，同一个实例可以被多个线程同时调用；`compose(sections, out)` 写完后不关闭 `out`。模板文件不存在时抛出 `NoSuchFileException`，不是 .docx / .dotx 时抛出 `IOException`（消息含 `not a .docx or .dotx`）。

Markdown 元素对应的 Word 结构见 [attachments/markdown-mapping.md](attachments/markdown-mapping.md)。

## 4. 要点

出处写成 `.reference-cache/<owner>/<name>@<version>/<路径>:<行号>`，对应的上游仓库和版本见第 8 节。ECMA-376 第 1 部分是 PDF，引用的是用 pdftotext 24.02.0 以 `-layout` 转出的文本 `part1.txt` 的行号，同时给出章节号，以便在 PDF 中查找。

### 4.1 标题层级由章节树决定

规则：深度为 d 的章节标题是 d 级标题；片段中最浅的标题落在 d + 1 级，其余标题保持与它的相对差；超过 9 级的按 9 级处理。

机制：`Composition.render` 给每个片段传入 `levelBase`（有标题的章节为 d，没有标题的为 d − 1）。`Fragment.shallowestHeading` 先遍历一次语法树，找出最浅的标题级别（`fromMarkdown` 取走的标题和脚注中的标题除外），再把每个标题放到 `levelBase + 级别 − 最浅级别 + 1`，结果限制在 1 到 9 之间。

证据：3.3 节和 3.4 节的输出；测试 `CoreCompositionTest` 中以 `fragmentHeadings`、`fromMarkdown`、`titledSection`、`untitledSection`、`headingsDeeperThanNine` 开头的 6 个方法，以及 `CrossFragmentTest.headingInsideAFootnoteDoesNotChangeTheBodyHeadingLevels`。

不这样做：各片段自带的 `#` 直接成为 1 级标题，片段放在第二层时与上级章节同级，目录和编号都会错。

### 4.2 用 `java` 直接运行多文件源码

规则：JDK 22 起，`java 某文件.java` 可以运行分散在多个源文件中的程序。启动器根据被启动文件的包名和位置算出源码根目录，程序用到的其他类在这个根目录下按需查找、编译（`.reference-cache/openjdk/jep-458@final/jep-458.txt:3`、`:7`、`:129`、`:202`、`:216`）。`examples/src/demo/MinimalDemo.java` 在包 `demo` 中，所以根目录是 `examples/src`，`md2docx.*` 从 `examples/src/md2docx/` 编译。

类路径：JEP 458 的示例用 `--class-path`（`jep-458.txt:162`）；实测 `CLASSPATH` 环境变量同样有效，去掉它就编译失败（第 5 节）。

不这样做：JDK 21 只能运行单个源文件，找不到同一目录树中的其他类（第 5 节）。

### 4.3 每个 Markdown 列表一个编号实例

规则：Word 的列表编号分两层，`abstractNum` 定义格式，`num` 是引用某个定义的实例，段落引用实例（`.reference-cache/ecma-international/ecma-376@5th-part1/part1.txt:34603-34605`，§17.9.1；`:35758`、`:35768-35771`，§17.9.15）。引用同一实例的段落连续编号。实例可以用 `lvlOverride` / `startOverride` 重设某一级的起始值（`part1.txt:35222`，§17.9.8；`:36388-36389`，§17.9.26）。

机制：`Lists` 创建一个项目符号定义和一个数字定义；`Lists.render` 为每个 Markdown 列表新建一个实例。有序列表在该列表所在的级别（最外层为 0，嵌套一层加 1）写一个 `startOverride`，值为 Markdown 中第一个编号（commonmark 的 `OrderedList.getMarkerStartNumber()`，没有时为 null，此时用 1；`.reference-cache/commonmark/commonmark-java@0.30.1/commonmark/src/main/java/org/commonmark/node/OrderedList.java:26-29`）。新定义的 ID 从模板已用的最大定义 ID 加一开始（`Lists.nextDefinitionId`），新实例的 ID 从模板已用的最大实例 ID 加一开始（`Lists.addInstance`）。后者不能用 POI 的 `XWPFNumbering.addNum(BigInteger)`：它把实例数加一当作新 ID（`.reference-cache/apache/poi@5.5.1/poi-ooxml/src/main/java/org/apache/poi/xwpf/usermodel/XWPFNumbering.java:145-153`），模板的实例 ID 不连续时会与已有 ID 重复。

证据：测试 `CrossFragmentTest.everyOrderedListStartsAtItsOwnFirstNumber`、`nestedOrderedListStartsAtItsOwnFirstNumber`、`TemplateTest.listNumbersDoNotReuseTheTemplateNumberingIds`；3.4 节输出中“3. 编号从 3 开始”。

不这样做：所有有序列表共用一个实例时，第二个文件的列表接着第一个文件往下编号；只在第 0 级写起始值时，嵌套的 `5.` 列表从 1 开始。

### 4.4 脚注按片段隔离

规则：commonmark 只有在找到定义时才把 `[^标签]` 解析成脚注引用，否则保留为普通文字（`.reference-cache/commonmark/commonmark-java@0.30.1/commonmark-ext-footnotes/src/main/java/org/commonmark/ext/footnotes/internal/FootnoteLinkProcessor.java:48-53`）。标签不区分大小写并经过规范化（`.reference-cache/commonmark/commonmark-java@0.30.1/commonmark/src/main/java/org/commonmark/node/DefinitionMap.java:9-11`）；定义可以嵌在引用块、列表等容器中，即使没有引用也会被解析（`.reference-cache/commonmark/commonmark-java@0.30.1/commonmark-ext-footnotes/src/main/java/org/commonmark/ext/footnotes/FootnoteDefinition.java:16-18`）。

机制：`Footnotes.definitions` 遍历整个片段，把所有定义放进 commonmark 的 `DefinitionMap`，用与解析器相同的规则查找。`Footnotes.reference` 以定义节点本身为键：第一次引用时先创建 Word 脚注、登记它的 ID，再渲染脚注内容；之后的引用（包括脚注内容中对自己或其他脚注的引用）复用登记的 ID。每个片段有自己的定义表，所以两个片段的 `[^1]` 是两个脚注。脚注中的图片输出替代文字（见 4.7）。

不写分隔线脚注：`separator` 和 `continuationSeparator` 是特殊脚注，标准要求每个特殊脚注都列在设置的 `footnotePr` 中，否则不加载，文档也不符合标准（`part1.txt:37797-37800`，§17.11.9）。POI 没有登记它们的公开方法，所以不写；LibreOffice 渲染时照样画出分隔线（3.4 节的输出）。

证据：测试 `CrossFragmentTest` 中的 `sameFootnoteLabelInTwoFragmentsGivesTwoFootnotes`、`referenceWithoutDefinitionStaysText`、`footnoteReferringToAnotherFootnoteOrToItselfReusesTheFootnote`、`footnoteLabelsIgnoreCaseAndDefinitionsMayBeNested`。

不这样做：以标签为键时，第二个文件的 `[^1]` 指向第一个文件的脚注内容；只查顶层、按原样比较标签时，`[^Note]` 和引用块中的定义找不到，脚注内容丢失；在 `HashMap.computeIfAbsent` 的计算函数里渲染脚注时，脚注引用另一个脚注就抛出 `ConcurrentModificationException`（第 5 节）。

### 4.5 锚点链接与 GitHub 规则一致

规则：GitHub 为标题生成锚点的方式（`.reference-cache/gjtorikian/html-pipeline@2.14.3/lib/html/pipeline/toc_filter.rb:30`、`:43-48`、`:59-60`）：只把 ASCII 字母转成小写；删去字母、组合符号、数字、连接符、`-`、空格之外的字符；空格换成 `-`；同一文件中重复的锚点依次加 `-1`、`-2`。中文等非 ASCII 字母保留。

机制：`Anchors.anchor` 实现上面的规则。每个标题包在书签 `_Heading<序号>` 中；链接目标是书签名（`w:hyperlink` 的 `w:anchor`，`part1.txt:62987-62994`，§17.16.22）。链接在所有片段渲染完后解析（`Anchors.resolve`）：先查本片段，再查全文第一个同名锚点；都找不到时，把超链接元素换成其中的文字，并去掉链接字符样式。一个 Markdown 链接的所有 run 放在同一个 `w:hyperlink` 中（`Fragment` 的 `OpenLink`）。

证据：测试 `CrossFragmentTest.anchorsFollowGitHubRules`（6 组）、`linkFindsAHeadingInAnotherFragmentAndPrefersItsOwnFragment`、`repeatedHeadingsGetNumberedAnchorsWithinAFragment`、`linkWithoutTargetBecomesPlainText`、`oneMarkdownLinkIsOneWordHyperlink`。

不这样做：只在全文查找时，两个片段都有“安装”一节，链接总是跳到第一个；渲染时立即解析，则指向后面片段的链接全部失效；每个 run 单独调用 POI 的 `createHyperlinkRun` 时，`[**粗体**和*斜体*](url)` 变成 3 个链接和 3 个关系（第 5 节）。

### 4.6 表格

规则：`w:tblHeader` 让表头行在每一页顶端重复（只对从第一行开始连续的表头行有效）（`part1.txt:21734-21743`，§17.4.49）；`w:cantSplit` 让一行的内容不跨页（`part1.txt:19008-19015`，§17.4.6）。POI 的 `XWPFTableRow.setRepeatHeader`、`setCantSplitRow` 写入这两个元素（`.reference-cache/apache/poi@5.5.1/poi-ooxml/src/main/java/org/apache/poi/xwpf/usermodel/XWPFTableRow.java:257-260`、`:304-307`）。commonmark 的 `TableCell.isHeader()` 和 `getAlignment()` 给出表头与对齐（`.reference-cache/commonmark/commonmark-java@0.30.1/commonmark-ext-gfm-tables/src/main/java/org/commonmark/ext/gfm/tables/TableCell.java:13-26`）。表格没有直接设置边框时使用表格样式的边框（`part1.txt:21212-21213`，§17.4.38）。

机制：`Tables.render` 设表格样式，并调用 `removeBorders()` 去掉 POI 给新表格加的直接边框；表格宽度等于正文宽度、固定布局，列宽由 `Tables.columnWidths` 计算（每列按表头计 4 到 16 个字符、按最长的正文单元格计至多 40 个字符，宽字符计 2）；表头行设 `tblHeader`，所有行设 `cantSplit`。如果容器中上一个元素已经是表格，先插入一个空段落。

证据：测试 `CoreCompositionTest.tableKeepsAlignmentRepeatsTheHeaderAndDoesNotSplitRows`、`columnWidthsFollowContentWithWideCharactersCountingDouble`、`DocumentLayoutTest.tableUsesTheBordersOfItsStyle`、`headerTextCountsAtMostSixteenCharactersForTheColumnWidth`、`CrossFragmentTest.adjacentTablesFromTwoFragmentsAreKeptApart`。相邻表格的实验见第 5 节：两个紧挨的表格被 LibreOffice 读成一个表格，中间有段落时是两个。

### 4.7 图片只从本地目录读取

规则：`ImageResolver.localFiles()` 只接受 `data:image/<格式>;base64,<数据>` 形式的数据和 `baseDirectory` 内的普通文件，大小不超过 `ImageResolver.MAXIMUM_BYTES`（20 MB）；带协议的地址（`http:`、`file:` 等）、以 `/` 或 `\` 开头的路径、真实位置在 `baseDirectory` 之外的路径一律返回 null。

机制：根目录和文件都用 `toRealPath()` 取真实路径（跟随符号链接），再检查 `startsWith`。只做文字上的 `normalize()` 时，目录中一个指向外部的符号链接就能读出外部文件（第 5 节）。图片格式由文件开头的字节判断（PNG、JPEG、GIF、BMP），尺寸只读图片头，用 `MemoryCacheImageInputStream` 在内存中读。POI 的 `XWPFRun.addPicture` 的宽高单位是 EMU（`.reference-cache/apache/poi@5.5.1/poi-ooxml/src/main/java/org/apache/poi/xwpf/usermodel/XWPFRun.java:1256-1262`）；1 像素 = 9525 EMU，1 磅 = 12700 EMU（`.reference-cache/apache/poi@5.5.1/poi/src/main/java/org/apache/poi/util/Units.java:24-29`）。图片按像素尺寸放置，宽度超过可用宽度、或高度超过正文高度的 90% 时等比缩小；表格中的图片以单元格宽度减去左右边距为限。`addPicture` 把它的“文件名”参数同时写进 `wp:docPr` 和 `pic:cNvPr` 的 `descr` 属性（`XWPFRun.java:1316`、`:1335`），这两个属性都是供辅助技术使用的替代文字（`part1.txt:151991-151992`，§20.4.2.5），所以传入的是 Markdown 的替代文字。脚注中的图片输出 `[替代文字]`：POI 把图片关系加到正文部件上（`XWPFRun.java:1273-1286`、`:1342`），脚注部件中的图片引用会指向不存在的关系。读不到的图片也输出 `[替代文字]`，没有替代文字时输出 `[image]`。

证据：`ImagesAndSafetyTest` 的 15 个测试，其中 `localFilesNeverOpensANetworkConnection` 在本机开一个端口，确认解析 `http://127.0.0.1:<端口>/a.png` 时没有任何连接；`symbolicLinkLeavingTheDirectoryIsRefused`、`imagesAboveTheSizeLimitAreNotRead`、`alternativeTextIsWrittenToBothPictureDescriptions`、`imageInsideAFootnoteBecomesItsAlternativeText`。

不这样做：Markdown 来自用户或其他程序时，`![](../../etc/passwd)` 或 `![](http://内网地址/)` 会让服务端读取任意文件或替人发起内网请求。需要远程图片时，自己实现 `ImageResolver` 并在其中限定允许的地址。

### 4.8 链接只放行四种协议

规则：只有 `http`、`https`、`mailto`、`ftp` 生成超链接，其余（`javascript:`、`file:`、`data:`、相对路径）只输出链接文字。外部超链接由 POI 的 `XWPFParagraph.createHyperlinkRun` 写成一个外部关系（`.reference-cache/apache/poi@5.5.1/poi-ooxml/src/main/java/org/apache/poi/xwpf/usermodel/XWPFParagraph.java:1536-1544`）。自动链接扩展把裸网址和邮箱变成普通链接节点，`www.` 开头的补上 `http://`，邮箱补上 `mailto:`（`.reference-cache/commonmark/commonmark-java@0.30.1/commonmark-ext-autolink/src/main/java/org/commonmark/ext/autolink/internal/AutolinkPostProcessor.java:97-101`）。

机制：`Links.target` 先检查地址中是否有 XML 不允许的字符：commonmark 在源文本过滤之后才解码 `&#xFFFE;` 这样的字符引用，这样的地址写进关系部件后，POI 再也打不开这个文件（第 5 节）；有就只输出文字。尖括号中的地址可以含空格（`<http://example.com/a b>`），空格换成 `%20` 后再交给 `java.net.URI` 检查协议。

证据：测试 `ImagesAndSafetyTest.onlyWebAndMailLinksBecomeHyperlinks` 检查 `word/_rels/document.xml.rels` 中只有 5 个允许的目标；`linkTargetWithACharacterXmlDoesNotAllowStaysText`。

### 4.9 去掉 XML 不允许的字符，HTML 块只取文字

规则：XML 1.0 只允许 `#x9 | #xA | #xD | [#x20-#xD7FF] | [#xE000-#xFFFD] | [#x10000-#x10FFFF]`（`.reference-cache/w3c/xml@fifth-edition/xml.txt:414`）。复制粘贴或程序生成的文本中常有其他控制字符。

机制：Markdown 在解析前经过 `Texts.xmlSafe`，删除不在上述范围内的字符（包括单独的 UTF-16 代理项，即没有配对的 U+D800 到 U+DFFF）；解析后写入 run 的文字再过滤一次（`Texts.write`）。HTML 块由 `Texts.htmlText` 处理：一次扫描去掉注释和标签，再交给 commonmark 解码 `&amp;`、`&#233;` 等字符引用（除 `&`、`#`、`;` 之外的 ASCII 标点先加反斜杠转义，使它们不被当作 Markdown 语法）。

证据：不过滤时，POI 5.5.1 把 `"a\u0001b"` 写成 `<w:t>a?b</w:t>`（第 5 节）；测试 `ImagesAndSafetyTest.charactersXmlDoesNotAllowAreRemoved`、`htmlBlockCharacterReferencesAreDecoded`、`htmlBlockWithManyUnclosedCommentsAndTagsTakesLinearTime`。

### 4.10 目录是真正的 Word 域，结果预先填好

规则：复杂域由三个 `w:fldChar` 组成：`begin`、`separate`、`end`；`separate` 之前是域指令，之后是域的当前结果（`part1.txt:62544-62547`，§17.16.18；`:68406-68412`）。目录指令 `TOC \o "1-3" \h \z \u`：`\o` 收录 1 到 3 级标题样式的段落，`\h` 让目录项成为超链接，`\z` 在 Web 版式中隐藏制表前导符和页码，`\u` 使用段落的大纲级别（`part1.txt:61738`、`:61772`、`:61782-61786`、`:61806`、`:61809`，§17.16.5.68）。标题样式带 `w:outlineLvl`（`part1.txt:11694-11697`，§17.3.1.20）。

机制：`FrontMatter.tableOfContents` 写入上述指令，并把域结果预先填成每个标题一行、链接到标题书签的目录项（开启标题编号时带上编号）。这样不更新域的阅读软件也能看到目录。设置 `updateFieldsOnOpen(true)` 时写入 `w:updateFields`，表示打开文档时重新计算域结果（`part1.txt:53355-53358`，§17.15.1.90），页码由此补上；POI 的 `XWPFDocument.enforceUpdateFields` 写入这个设置，其 Javadoc 说明 Word 会因此在打开时询问（`.reference-cache/apache/poi@5.5.1/poi-ooxml/src/main/java/org/apache/poi/xwpf/usermodel/XWPFDocument.java:1462-1475`），这个询问没有在 Word 中实测。

标题、副标题和目录在所有标题都已知后插入到正文前面。POI 的 `insertNewParagraph` 每次都向前遍历新段落之前的所有同级元素（`XWPFDocument.java:859-866`），按阅读顺序逐个插在同一个位置之前，耗时随目录项数平方增长；所以 `FrontMatter.insert` 从最后一段往前插，每次插在正文最前面。文档标题同时写入文档属性的标题（`docProps/core.xml` 的 `dc:title`）。

证据：测试 `DocumentLayoutTest.contentsIsATocFieldWithPrefilledLinkedEntries`、`contentsWithoutUpdateOnOpenLeavesTheSettingOut`、`contentsForManyHeadingsTakesLinearTime`、`titleIsAlsoTheDocumentPropertyTitle`、`titleWithoutBodyAddsNoPageBreak`；3.4 节中 LibreOffice 渲染出的目录就是预先填入的结果（没有页码）；插入顺序的耗时对比见第 5 节。

### 4.11 标题编号挂在标题样式上

规则：段落编号也可以写在样式中，使用该样式的段落都带编号。`numId` 为 0 表示去掉编号（`part1.txt:35943-35945`，§17.9.18；同一标准 §17.3.1.19 的示例 `part1.txt:11672-11690` 把 0 当作普通实例 ID 使用，与此矛盾，实现以 §17.9.18 为准）。

机制：`HeadingNumbering.attach` 新建一个多级编号定义（第 1 级格式 `%1.`，第 2 级 `%1.%2`，第 3 级 `%1.%2.%3`，依此类推，每级用 `w:pStyle` 绑定对应标题样式），并在 `heading 1` 到 `heading 9` 样式中加入 `numPr`。模板的标题样式已有 `numPr` 时保留模板的编号；这时不知道模板的编号格式，目录项不带编号。目录中的编号由 `HeadingNumbering.next` 按同样规则算出。标题、副标题、目录标题设 `numId` 0，避免模板中基于“heading 1”的样式继承编号。

证据：测试 `DocumentLayoutTest.headingNumbersAreAWordListOnTheHeadingStylesAndAppearInTheContents`、`TemplateTest.headingNumbersAreNotAddedWhenTheTemplateHeadingAlreadyHasNumbering`、`contentsHasNoNumbersWhenTheTemplateNumbersItsHeadings`。

不这样做：把“1.1 ”写进标题文字，手动增删章节后编号不会更新。

### 4.12 模板：按样式名称匹配，只留下正文之外的部分

规则：`w:styleId` 只用于在文档内引用样式（`part1.txt:32166-32167`），`w:name` 是样式的主名称（`part1.txt:31658-31660`，§17.7.4.9）。主文档部件的内容类型有两种：`application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml` 和 `application/vnd.openxmlformats-officedocument.wordprocessingml.template.main+xml`（`part1.txt:2513-2515`，§11.3.10）；POI 的 `OPCPackage.replaceContentType` 用于把模板改成文档（`.reference-cache/apache/poi@5.5.1/poi-ooxml/src/main/java/org/apache/poi/openxml4j/opc/OPCPackage.java:1770-1801`）。

机制：`Templates.open` 只接受上面两种内容类型（启用宏的文件等其他类型抛出 `IOException`），模板类型换成文档类型。正文用 XML 游标删除除最后的节属性 `w:sectPr` 之外的全部内容：POI 的 `removeBodyElement` 只从 XML 中删除段落和表格（`XWPFDocument.java:1124-1141`），会留下内容控件 `w:sdt` 等其他块。之后在包一级删除正文部件中已无人引用的图片、链接、嵌入对象和图表关系及其目标部件。`Styles.fromTemplate` 对每个需要的样式先按名称（不区分大小写）查找，再按默认 ID 查找，都没有时加入内置定义。页面尺寸和边距取模板的最后一节（`PageSetup.of`，带单位的长度如 `2.54cm` 也由 POI 的 `POIXMLUnits.parseLength` 换算），表格和图片按它计算宽度。

证据：`TemplateTest` 的 10 个测试，其中模板的标题样式 ID 为 `1`、`2`，生成的段落样式也是 `1`、`2`；`contentControlsAndPicturesOfTheTemplateBodyAreDropped`、`macroEnabledFileIsRefused`。

### 4.13 分页、页码、页面尺寸

规则：`w:pageBreakBefore` 让段落从新的一页开始（`part1.txt:11896-11897`，§17.3.1.23），POI 的 `XWPFParagraph.setPageBreak` 写入它（`XWPFParagraph.java:916-920`）。`PAGE` 域给出当前页码（`part1.txt:60827-60833`，§17.16.5.44）。`w:pgSz` 和 `w:pgMar` 的单位是 twip（`part1.txt:29255`、`:29287`、`:28921`）。

机制：顶级章节分页时，给章节的第一个元素设 `pageBreakBefore`；第一个元素是表格时，在它前面插入一个设了分页的空段落。正文在标题、目录之后另起一页时，正文第一个元素是段落就设 `pageBreakBefore`，是表格就在前面最后一段的末尾加一个分页符 `<w:br w:type="page"/>`；正文为空时不分页。页码是页脚中的简单域 `PAGE`。新文档为 A4（11906 × 16838 twip），四边距 1440 twip，页眉距 851 twip（1.5 cm），页脚距 992 twip（1.75 cm）。

证据：`DocumentLayoutTest` 中的 `topLevelSectionsAfterTheFirstStartOnANewPageUnlessASectionSaysOtherwise`、`singleSectionCanAskForAPageBreakWithoutTheOption`、`sectionStartingWithATableGetsAnEmptyBreakingParagraphInFront`、`frontMatterPageBreakCanBeSwitchedOff`、`bodyStartingWithATableStartsOnANewPageAfterTheFrontMatter`、`titleWithoutBodyAddsNoPageBreak`、`pageNumbersAreACenteredPageFieldInTheFooter`、`newDocumentIsA4WithInchMarginsAndTheDefaultFonts`。

### 4.14 并发

规则：commonmark 的 `Parser` 构建一次后可以在多个线程中使用（`.reference-cache/commonmark/commonmark-java@0.30.1/README.md:265-269`；`.reference-cache/commonmark/commonmark-java@0.30.1/commonmark/src/main/java/org/commonmark/parser/Parser.java:81`）。

机制：`Composition` 和 `Texts` 中的解析器是静态常量；其余状态都在每次 `compose` 新建的 `Composition` 对象中，`MarkdownDocxComposer` 只持有不可变的选项。

证据：测试 `CoreCompositionTest.oneComposerGivesTheSameDocumentToConcurrentCallers`（8 个线程 32 次调用，结果与单线程相同）。

### 4.15 用完整 schema 校验生成的文档

规则：POI 的 `poi-ooxml-lite` 只包含 POI 自己的测试用到的 schema 类，完整的 ECMA-376 schema 在 `poi-ooxml-full` 中（`.reference-cache/apache/poi@5.5.1/src/documentation/content/xdocs/help/faq.xml:78-85`）。

机制：`SchemaValidationTest` 用 XMLBeans 按对应的文档类型解析并校验 `document`、`styles`、`numbering`、`footnotes`、`settings`、`footer` 6 个部件，覆盖默认选项、全部选项和空文档三种情况。图片的 `pic:pic` 元素位于 `a:graphicData` 的通配内容中，测试跳过关于它的报错，再把每个 `pic:pic` 单独按图片 schema 校验一次。

证据：第 6 节的测试输出；缺少 `poi-ooxml-full` 时的失败见第 5 节。这是 Word 之外能做到的最强检查，但它不能证明 Word 中的显示效果。

## 5. 陷阱

下表中“上一版”指本目录评审前的代码（每项问题都已修正），现象是用它实测的输出。

| 做法 | 现象（实测） | 解决 |
| --- | --- | --- |
| 用 JDK 21 运行 `java examples/src/demo/MinimalDemo.java` | `error: package md2docx does not exist`，退出码 1 | 使用 JDK 22 及以上（本文用 25） |
| 运行时没有设置 `CLASSPATH` | `error: package org.apache.poi.xwpf.usermodel does not exist`，退出码 2 | 按第 2 节设置 `CLASSPATH` |
| 测试类路径中没有 `poi-ooxml-full` | `fullSchemasAreOnTheClassPath` 失败；`demoDocumentsAndEveryFeatureValidate` 抛出 `org.apache.xmlbeans.SchemaTypeLoaderException: XML-BEANS compiled schema: Could not locate compiled schema resource org/apache/poi/schemas/ooxml/system/ooxml/ctmultileveltypea223type.xsb (org.apache.poi.schemas.ooxml.system.ooxml.ctmultileveltypea223type) - code 0` | 把 `POI_FULL_JAR` 加入测试类路径（放在 `poi-ooxml-lite` 之前或之后都可以，两种都实测通过） |
| 运行时没有 Log4j 实现 | 每次运行打印 `main ERROR Log4j API could not find a logging provider.` | 不影响生成结果；POI 依赖 `log4j-api`，需要日志时再加入一个 Log4j 实现 |
| 不过滤控制字符，直接 `run.setText("a\u0001b")` | 文档中出现 `a?b` | 见 4.9 |
| 两个表格之间不留段落 | 用 POI 写两个紧挨的表格，`word/document.xml` 中有 2 个 `<w:tbl>`；LibreOffice 转成 odt 后只有 1 个表格。拼接器的输出（中间有空段落）转换后是 2 个 | 见 4.6 |
| 在 `HashMap.computeIfAbsent` 的计算函数中渲染脚注（上一版） | 脚注内容引用另一个脚注时抛出 `java.util.ConcurrentModificationException` | 先创建并登记脚注，再渲染内容，见 4.4 |
| 用 `normalize()` 加 `startsWith` 检查图片路径（上一版） | 目录中指向 `../outside.png` 的符号链接被读出（`read 3 bytes`） | 用 `toRealPath()`，见 4.7 |
| 用 `ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))` 读图片尺寸（上一版） | 返回 `javax.imageio.stream.FileCacheImageInputStream`：每张图片都写一个临时文件 | 用 `MemoryCacheImageInputStream` |
| 把 Markdown 链接地址直接交给 `createHyperlinkRun`（上一版） | `[x](http://example.com/&#xFFFE;)` 生成的文件再用 POI 打开时抛出 `org.apache.poi.ooxml.POIXMLException: org.apache.poi.openxml4j.exceptions.InvalidFormatException: Failed to parse relationships`，日志中的原因是 `An invalid XML character (Unicode: 0xfffe) was found in the value of attribute "Target" and element is "Relationship".` | 见 4.8 |
| 链接的每个 run 各调用一次 `createHyperlinkRun`（上一版） | `[**b** and *i*](https://example.org)` 生成 3 个 `w:hyperlink` | 见 4.5 |
| 把目录项按阅读顺序逐个插在正文第一个元素之前（上一版） | 8000 个标题：无目录 930 ms，有目录 2702 ms；16000 个标题：无目录 240 ms，有目录 8050 ms。修正后 16000 个标题有目录 523 ms | 见 4.10 |
| 用 POI 的 `XWPFNumbering.addNum(BigInteger)` 新建编号实例 | 模板中已有实例 1 和 5 时，新实例的 ID 与模板重复（见 4.3 引用的源码） | `Lists.addInstance` |
| 断言设置项时写 `<w:updateFields w:val="true"/>` 或 `<w:pageBreakBefore/>` | 断言失败：POI 写的是 `w:val="on"` | 按实际 XML 断言 |
| 用 `XWPFParagraph.getText()` 检查含脚注引用的段落 | 结果中带有脚注内容，例如 `x[footnoteRef:1] y[footnoteRef:2] [1:  case note]` | 只拼接各 run 的 `text()`（测试中的 `TestSupport.runText`） |

## 6. 检查

在 `md2docx/` 下，按第 2 节设置好 `CLASSPATH`、`JUNIT_JAR`、`POI_FULL_JAR` 后运行：

```bash
java examples/src/demo/MinimalDemo.java
java examples/src/demo/FullDemo.java
javac -d build/test-classes -cp "$JUNIT_JAR:$POI_FULL_JAR:$CLASSPATH" $(find examples/src tests -name "*.java")
java -jar "$JUNIT_JAR" execute -cp "build/test-classes:$POI_FULL_JAR:$CLASSPATH" --scan-classpath build/test-classes --disable-banner --details=summary
```

`--scan-classpath` 扫描给定目录中的全部测试类，`-cp` 提供其他类路径（`.reference-cache/junit-team/junit-framework@6.1.3/junit-platform-console/src/main/java/org/junit/platform/console/options/TestDiscoveryOptionsMixin.java:58-59`、`:207-208`）。

预期输出（时间戳与耗时每次不同）：

```
$ java examples/src/demo/MinimalDemo.java
2026-10-20T11:17:25.576105497Z main ERROR Log4j API could not find a logging provider.
wrote minimal.docx
$ java examples/src/demo/FullDemo.java
2026-10-20T11:17:27.879704193Z main ERROR Log4j API could not find a logging provider.
wrote full.docx
$ javac -d build/test-classes -cp "$JUNIT_JAR:$POI_FULL_JAR:$CLASSPATH" $(find examples/src tests -name "*.java")
$ java -jar "$JUNIT_JAR" execute -cp "build/test-classes:$POI_FULL_JAR:$CLASSPATH" --scan-classpath build/test-classes --disable-banner --details=summary
2026-10-20T11:17:31.154186284Z main ERROR Log4j API could not find a logging provider.

Test run finished after 2745 ms
[        10 containers found      ]
[         0 containers skipped    ]
[        10 containers started    ]
[         0 containers aborted    ]
[        10 containers successful ]
[         0 containers failed     ]
[        85 tests found           ]
[         0 tests skipped         ]
[        85 tests started         ]
[         0 tests aborted         ]
[        85 tests successful      ]
[         0 tests failed          ]
```

四条命令的退出码都是 0。要看渲染效果，用 LibreOffice 转成 PDF 再取出文字：

```bash
soffice --headless --convert-to pdf minimal.docx full.docx
pdftotext -layout minimal.pdf -
pdftotext -layout full.pdf -
```

文字应与 3.3、3.4 节一致。生成的 `minimal.docx`、`full.docx`、`minimal.pdf`、`full.pdf` 和 `build/` 已在本目录的 `.gitignore` 中。

## 7. 附件

- [attachments/markdown-mapping.md](attachments/markdown-mapping.md)：每种 Markdown 元素变成什么 Word 结构、在哪里实现、由哪个测试验证；改动某个元素的渲染前先读。

## 8. 出处

`.reference-cache/` 是编写本文时使用的只读源码与文档缓存，不随本目录发布。每个前缀对应下面的上游版本，路径和行号与上游该版本一致：

| 前缀 | 上游 |
| --- | --- |
| `.reference-cache/apache/poi@5.5.1/` | https://github.com/apache/poi ，标签 `REL_5_5_1` |
| `.reference-cache/commonmark/commonmark-java@0.30.1/` | https://github.com/commonmark/commonmark-java ，标签 `commonmark-parent-0.30.1` |
| `.reference-cache/junit-team/junit-framework@6.1.3/` | https://github.com/junit-team/junit-framework ，标签 `r6.1.3` |
| `.reference-cache/gjtorikian/html-pipeline@2.14.3/` | https://github.com/gjtorikian/html-pipeline ，标签 `v2.14.3` |
| `.reference-cache/ecma-international/ecma-376@5th-part1/part1.txt` | ECMA-376 第 5 版第 1 部分（2016 年 12 月），https://ecma-international.org/wp-content/uploads/ECMA-376-1_5th_edition_december_2016.zip 中的 PDF，用 pdftotext 24.02.0 的 `-layout` 选项转成文本 |
| `.reference-cache/openjdk/jep-458@final/jep-458.txt` | https://openjdk.org/jeps/458 ，删去 `script`、`style` 元素和全部标签、解码字符引用后的文本 |
| `.reference-cache/w3c/xml@fifth-edition/xml.txt` | https://www.w3.org/TR/2008/REC-xml-20081126/ ，按同样方法转成的文本；引用的是第 2.2 节的产生式 [2] `Char` |
