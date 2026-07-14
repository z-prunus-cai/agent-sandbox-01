# diagrams/

存放独立的 `.puml` 文件(可选)。本 POC 直接把 PlantUML 内联在章节 `.adoc` 里
(见 `../chapters/02-architecture.adoc`、`03-flow.adoc`)。

若要引用外部 `.puml`,在 adoc 中这样写:

```asciidoc
plantuml::diagrams/my-diagram.puml[format=svg]
```
