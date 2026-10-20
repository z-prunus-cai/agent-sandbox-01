## 结构

![结构示意图](images/structure.png)

章节树的每个节点对应一个标题，见[接口](#接口)。

### 处理顺序

1. 解析 Markdown
2. 平移标题层级
3. 写入 Word 文档

```java
byte[] docx = new MarkdownDocxComposer(options).compose(sections);
```
