# 设计文档 → PDF 流水线(POC)

把 `docs/**/*.adoc`(含 PlantUML 图表)构建成**单个可复现的中文 PDF**,带封面、目录、
章节编号。面向非技术读者(PM)。CI 容器只需一个 JDK —— 无 `gem install` / `apt install`
/ `npm install`,不引入 Chromium / 系统级 Ruby / Graphviz。

本仓库是对应设计方案的**可运行 POC**,已端到端验证通过。

## 快速开始

```bash
./gradlew asciidoctorPdf
# 产物:build/docs/pdf/index.pdf
```

## 技术栈(五个版本全部锁死,见 `gradle.properties`)

| 层 | 组件 | 版本 |
|---|---|---|
| 构建入口 | `org.asciidoctor.jvm.pdf` | 4.0.4 |
| 转换引擎 | AsciidoctorJ | 3.0.0 |
| PDF 后端 | asciidoctorj-pdf | 2.3.19 |
| 图表后端 | asciidoctorj-diagram | 2.3.1 |
| Ruby 运行时 | JRuby | 9.4.8.0 |

图表用 **PlantUML + Smetana** 布局(纯 JVM,免 Graphviz),输出 **SVG**(矢量,放大不糊)。
中文字体是仓库内的 Noto Sans/Serif SC(`docs/theme/fonts/`),通过 `pdf-fontsdir` 指定。

## 目录结构

```
docs/
  index.adoc              # 入口,doctype=book,include 各章节
  chapters/*.adoc         # 章节(含内联 PlantUML)
  images/                 # 静态图片(本 POC 未用)
  diagrams/               # 外部 .puml(本 POC 内联,未用)
  theme/
    default-theme.yml     # PDF 主题:字体 catalog / 页脚
    fonts/                # Noto Sans/Serif SC 静态 TTF(400/700)
build.gradle.kts          # 单 task,无自定义 task,无 Exec
gradle.properties         # 五个版本锁
.github/workflows/docs-pdf.yml
```

## 验证结果(本机 JDK 21 实跑)

| 验证点 | 结果 |
|---|---|
| 构建 | `asciidoctorPdf` 单 task,~20s,`BUILD SUCCESSFUL` |
| 中文正文 | ✅ 6 页,641 个 CJK 字符正确渲染,文字可选可搜(非方块) |
| **中文粗体**(已知坑 3) | ✅ catalog 四 variant 指路径,粗体不 fallback、不丢字 |
| PlantUML(Smetana) | ✅ 组件图 + 时序图,中文标签,SVG 嵌入,**无 Graphviz** |
| doctype=book | ✅ 封面 / 章节编号(Chapter N)/ 小节编号 / TOC |
| icons=font | ✅ TIP admonition 的 FontAwesome 图标正常 |
| **可复现性**(硬约束) | ✅ 设 `:reproducible:` 后同 commit 两次构建 **PDF 字节级一致**(MD5 相同),SVG 也字节一致 —— 优于设计预期的"视觉级" |

> 复现性验证命令:
> ```bash
> ./gradlew clean asciidoctorPdf && md5sum build/docs/pdf/index.pdf
> ./gradlew clean asciidoctorPdf && md5sum build/docs/pdf/index.pdf   # 同一 MD5
> ```

## 与设计文档的对应关系

- **决策 1 字体入库**:`docs/theme/fonts/` 内 4 个静态 TTF(Sans/Serif × Regular/Bold,
  由 Noto SC 变量字体 `varLib.instancer` 实例化)。
- **决策 2 Smetana**:每个 PlantUML block 首行 `!pragma layout smetana`。
- **决策 3 SVG**:`:plantuml-format: svg` + block 属性 `svg`。
- **决策 4 doctype=book**:`index.adoc` 及构建属性。
- **决策 5 版本锁死**:五个版本在 `gradle.properties` 显式声明。
- **已知坑 2**:`.asciidoctor/` 已入 `.gitignore`。
- **已知坑 3**:`default-theme.yml` 的 `font.catalog` 显式给 CJK 的
  normal/bold/italic/bold_italic 四路径(italic→regular、bold_italic→bold,CJK 无真斜体)。

## 实现说明:JRuby 锁定方式

`asciidoctorj { setJrubyVersion(...) }` 在 project 级配置时会触发插件内部
`updateConfiguration()` 的时序 NullPointerException。POC 改用等价、更可控的
`resolutionStrategy.force("org.jruby:jruby:9.4.8.0")` 锁定(见 `build.gradle.kts`),
效果与设计意图一致。

## 需要拍板的点(设计 §8,POC 已给默认选择,可调)

- 字体:**入库**(当前)。仓库因此约 +49MB。若嫌大,可改为内部 Maven artifact 解压。
- 输出粒度:**单个大 PDF**(当前)。
- HTML 版:未产出(可加 `org.asciidoctor.jvm.convert` 一并生成)。
- 品牌化:未加封面 logo / 公司页眉(theme 已预留 `title-page`/`footer` 扩展点)。
