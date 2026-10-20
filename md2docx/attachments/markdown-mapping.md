# Markdown 元素与 Word 结构的对应

本附件列出每种 Markdown 元素在生成的 .docx 中变成什么，以及实现它的位置。代码位置写成 `文件名` 加方法名，文件都在 `examples/src/md2docx/` 下；“测试”一列是验证该行的测试方法，在 `tests/md2docx/` 下。Markdown 由 commonmark-java 0.30.1 解析，启用了表格、删除线、任务列表、脚注、自动链接 5 个扩展（`Composition.java` 的 `PARSER`）。

## 块元素

| Markdown | Word | 实现 | 测试 |
| --- | --- | --- | --- |
| `#` 到 `######` 标题 | 样式 `Heading1` 到 `Heading9` 的段落，外面包一个书签 `_Heading<序号>`；级别按章节树平移 | `Fragment.renderBlock`、`Fragment.heading`、`Anchors.bookmarkHeading` | `CoreCompositionTest.fragmentHeadingsMoveBelowTheSectionHeadingKeepingTheirRelativeLevels` |
| 段落 | 无样式段落；源码中的换行变成空格，两侧都是中日韩宽字符时不加空格 | `Fragment.renderInline` | `CoreCompositionTest.sourceLineBreakBetweenChineseCharactersAddsNoSpace` |
| 无序列表 | 样式 `ListParagraph`，Word 编号（●、○、■ 按层级循环），嵌套一层编号级别加一 | `Lists.render` | `CoreCompositionTest.listsUseWordNumberingAndNestedItemsUseTheNextLevel` |
| 有序列表 | 同上，第 n 级编号格式 `%n.`；每个列表一个编号实例，在自己所在的级别从自己的起始数字开始 | `Lists.render` | `CrossFragmentTest.everyOrderedListStartsAtItsOwnFirstNumber` |
| 任务列表 `- [x]`、`- [ ]` | 列表项前加 `☒ ` 或 `☐ `（U+2612、U+2610） | `Lists.render` | `CoreCompositionTest.taskListItemsGetBallotBoxes` |
| 代码块（围栏或缩进） | 一个样式 `SourceCode` 的段落（等宽字体、底纹、边框）中的一个 run，行与行之间是换行符 `<w:br/>`（文字中不含换行字符），制表符换成 4 个空格 | `Fragment.renderCode` | `CoreCompositionTest.codeBlockKeepsIndentationAndEmptyLines` |
| 引用块 | 样式 `Quote`（左边框、灰色文字），每嵌套一层左缩进增加 | `Fragment.renderBlock` | `CoreCompositionTest.quotesNestAndThematicBreakIsABottomBorder` |
| 分割线 `---` | 带下边框的空段落 | `Fragment.renderBlock` | 同上 |
| GFM 表格 | 表格样式 `MarkdownTable`（边框来自样式，不设直接边框），宽度等于正文宽度，固定布局；表头行加粗、底色 `E8EEF7`、跨页重复；所有行不跨页拆分；列对齐转成段落对齐 | `Tables.render` | `CoreCompositionTest.tableKeepsAlignmentRepeatsTheHeaderAndDoesNotSplitRows` |
| 表格列宽 | 每列按表头计 4 到 16 个字符，按最长的正文单元格计至多 40 个字符，取较大者；中日韩宽字符计 2 | `Tables.columnWidths` | `CoreCompositionTest.columnWidthsFollowContentWithWideCharactersCountingDouble` |
| HTML 块 | 去掉注释和标签、解码 `&amp;` 等字符引用后的文字，作为普通段落 | `Texts.htmlText` | `CoreCompositionTest.breakTagsBecomeLineBreaksAndOtherHtmlKeepsOnlyText` |
| 脚注定义 `[^1]: 内容` | Word 脚注，在第一次被引用的位置创建；定义可以嵌在引用块、列表中，标签不区分大小写 | `Footnotes` | `CrossFragmentTest.sameFootnoteLabelInTwoFragmentsGivesTwoFootnotes` |

## 行内元素

| Markdown | Word | 实现 | 测试 |
| --- | --- | --- | --- |
| `**粗体**`、`*斜体*`、`~~删除线~~` | run 的粗体、斜体、删除线属性，可叠加 | `Fragment.renderInline` | `CoreCompositionTest.inlineFormattingBecomesRunProperties` |
| `` `代码` `` | 字符样式 `VerbatimChar`（等宽字体、底纹）；在链接内时用链接样式 | `Fragment.renderInline` | 同上，以及 `CoreCompositionTest.codeInsideALinkTakesTheLinkStyle` |
| `[文字](http://example.org)` 及 https、mailto、ftp | 外部超链接，字符样式 `Hyperlink`；一个 Markdown 链接是一个 `w:hyperlink`，不论其中有几个 run | `Links.target`、`Fragment.run` | `ImagesAndSafetyTest.onlyWebAndMailLinksBecomeHyperlinks` |
| 裸网址 `https://example.org` | 同上（自动链接扩展） | 同上 | 同上 |
| `[文字](#锚点)` | 指向标题书签的文档内链接；找不到目标时变成普通文字 | `Anchors.link`、`Anchors.resolve` | `CrossFragmentTest.linkFindsAHeadingInAnotherFragmentAndPrefersItsOwnFragment`、`CrossFragmentTest.linkWithoutTargetBecomesPlainText` |
| 其他链接（相对路径、`javascript:`、`file:`、`data:`） | 只有文字 | `Links.target` | `ImagesAndSafetyTest.onlyWebAndMailLinksBecomeHyperlinks` |
| `![替代文字](路径)` | 嵌入图片，替代文字写入 `wp:docPr` 和 `pic:cNvPr` 的 `descr` 属性；只有图片的段落居中；读不到、或位于脚注中时输出 `[替代文字]`，没有替代文字时输出 `[image]` | `Images.render`、`ImageResolver` | `ImagesAndSafetyTest` 的各个图片测试 |
| `[^1]` 脚注引用 | Word 脚注引用（字符样式 `FootnoteReference`，上标）；没有对应定义时原样输出 `[^1]` | `Footnotes.reference` | `CrossFragmentTest.referenceWithoutDefinitionStaysText`、`footnoteReferenceAndTextUseTheFootnoteStyles` |
| 硬换行（行尾两个空格或 `\`）、`<br>`、`<br/>` | 换行符 `<w:br/>` | `Fragment.renderInline` | `CoreCompositionTest.breakTagsBecomeLineBreaksAndOtherHtmlKeepsOnlyText`、`hardLineBreaksBecomeLineBreaks` |
| 其他内联 HTML 标签 | 去掉标签，保留标签之间的文字 | `Fragment.renderInline` | 同上 |

## 不支持

LaTeX 公式按原文输出；代码块不做语法高亮；HTML 块不渲染，只保留文字；远程图片不读取（可以自己实现 `ImageResolver`）。
