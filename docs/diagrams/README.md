# diagrams/

存放独立的 `.puml` 文件(可选)。本 POC 直接把 PlantUML 内联在页面 `.adoc` 里
(见 `../index/02-architecture.adoc`、`03-flow.adoc`)。

Confluence Publisher 会把 PlantUML 渲染成 PNG,作为附件上传到对应页面。

若要引用外部 `.puml`,在 adoc 中这样写:

```asciidoc
plantuml::diagrams/my-diagram.puml[format=png]
```
