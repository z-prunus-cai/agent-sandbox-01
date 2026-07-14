import org.asciidoctor.gradle.jvm.pdf.AsciidoctorPdfTask

plugins {
    // (1/5) 构建入口:提供 asciidoctorPdf task。版本来自 gradle.properties。
    id("org.asciidoctor.jvm.pdf") version "4.0.4"
}

repositories {
    mavenCentral()
}

// ---------------------------------------------------------------------------
// 版本锁定:五个都显式声明,不让插件填默认值(设计决策 5)
// ---------------------------------------------------------------------------
val asciidoctorjVersion: String by project
val asciidoctorjPdfVersion: String by project
val asciidoctorjDiagramVersion: String by project
val jrubyVersion: String by project

asciidoctorj {
    // (2/5) 转换引擎
    setVersion(asciidoctorjVersion)

    modules {
        // (3/5) PDF 后端
        pdf.version(asciidoctorjPdfVersion)
        // (4/5) 图表后端
        diagram.version(asciidoctorjDiagramVersion)
    }
}

// (5/5) 底层 Ruby 运行时 —— 显式锁死。
// 注:asciidoctorj 扩展的 setJrubyVersion() 在 project 级配置时会触发插件内部
// 的 updateConfiguration() 时序 NPE,故改用 resolutionStrategy.force 锁定,
// 效果等价且更可控(设计决策 5:五个版本全部显式声明)。
configurations.configureEach {
    resolutionStrategy {
        force("org.jruby:jruby:$jrubyVersion")
        force("org.jruby:jruby-complete:$jrubyVersion")
    }
}

tasks.withType<AsciidoctorPdfTask>().configureEach {
    // 入口文档,doctype=book(设计决策 4:封面 / 章节分页 / 罗马数字前言页码)
    baseDirFollowsSourceFile()
    sourceDir(file("docs"))
    sources { include("index.adoc") }
    setOutputDir(layout.buildDirectory.dir("docs/pdf").get().asFile)

    // 启用 diagram 扩展(PlantUML 拦截)
    asciidoctorj {
        modules {
            diagram.use()
        }
    }

    // 主题目录 + 字体目录(设计决策 1:字体入库,pdf-fontsdir 指向仓库内文件)
    attributes(
        mapOf(
            "doctype" to "book",
            "toc" to "",
            "toclevels" to "3",
            "sectnums" to "",
            "sectnumlevels" to "3",
            "icons" to "font",
            "source-highlighter" to "rouge",
            "pdf-theme" to "default",
            "pdf-themesdir" to file("docs/theme").absolutePath,
            "pdf-fontsdir" to file("docs/theme/fonts").absolutePath,
            // 图表默认输出 SVG(设计决策 3:矢量,PDF 内无限缩放不糊)
            "diagram-format" to "svg",
            // 可复现:固定日期,避免 revdate/footer 随构建时间漂移
            "reproducible" to "",
        )
    )
}
