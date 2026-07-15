plugins {
    // 仅为 clean 等生命周期 task,不做任何编译
    base
}

repositories {
    mavenCentral()
}

// ---------------------------------------------------------------------------
// Gradle 调 Confluence Publisher CLI:
//   把 CLI 及其全部依赖(含 asciidoctorj-diagram + PlantUML)解析成一个
//   configuration,再用 JavaExec 以官方 main 类运行。纯 JVM,无需 Docker。
// ---------------------------------------------------------------------------
val confluencePublisherCliVersion: String by project

val confluenceCli: Configuration by configurations.creating {
    // 声明标准 JVM 属性,否则 guava 的 android/jre 变体无法消歧
    attributes {
        attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME))
        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.LIBRARY))
        attribute(
            TargetJvmEnvironment.TARGET_JVM_ENVIRONMENT_ATTRIBUTE,
            objects.named(TargetJvmEnvironment.STANDARD_JVM)
        )
    }
}

dependencies {
    confluenceCli(
        "org.sahli.asciidoc.confluence.publisher:asciidoc-confluence-publisher-cli:$confluencePublisherCliVersion"
    )
}

val cliMainClass = "org.sahli.asciidoc.confluence.publisher.cli.AsciidocConfluencePublisherCommandLineClient"
val docsRoot = layout.projectDirectory.dir("docs").asFile
val convertOutput = layout.buildDirectory.dir("confluence").get().asFile

// 从 -P 属性或环境变量读取,convert(离线)用占位默认值即可
fun cfg(prop: String, env: String, default: String): String =
    (findProperty(prop) as String?) ?: System.getenv(env) ?: default

// CLI 参数是 key=value 形式;这些是 convert / publish 共用的部分
fun baseArgs(): List<String> = listOf(
    "asciidocRootFolder=${docsRoot.absolutePath}",
    "asciidocBuildFolder=${convertOutput.absolutePath}",
    "rootConfluenceUrl=${cfg("confluenceUrl", "CONFLUENCE_URL", "https://your-org.atlassian.net/wiki")}",
    "spaceKey=${cfg("confluenceSpaceKey", "CONFLUENCE_SPACE_KEY", "DOCS")}",
    // ancestorId:发布锚点页(不是空间根),孤儿删除也只在这棵子树内生效
    "ancestorId=${cfg("confluenceAncestorId", "CONFLUENCE_ANCESTOR_ID", "000000")}",
    "username=${cfg("confluenceUsername", "CONFLUENCE_USERNAME", "")}",
    "password=${cfg("confluencePassword", "CONFLUENCE_PASSWORD", "unused-in-convert-only")}",
    "publishingStrategy=APPEND_TO_ANCESTOR",
    "orphanRemovalStrategy=REMOVE_ORPHANS",
    "notifyWatchers=false",
    "versionMessage=${cfg("versionMessage", "VERSION_MESSAGE", "Published from Git")}",
)

// 离线转换校验:convertOnly=true 走本地分支,不连 Confluence。
// 输出 XHTML + PlantUML 渲染的 PNG 附件到 build/confluence/,可直接检查。
tasks.register<JavaExec>("confluenceConvert") {
    group = "documentation"
    description = "本地转换校验(convertOnly,不连 Confluence,CI 校验文档没写坏)"
    classpath = confluenceCli
    mainClass.set(cliMainClass)
    args = baseArgs() + "convertOnly=true"
}

// 真正发布到 Confluence:需要凭据(Cloud = 邮箱 + API token)。
tasks.register<JavaExec>("confluencePublish") {
    group = "documentation"
    description = "发布到 Confluence(需 -PconfluenceUsername/-PconfluencePassword 或环境变量)"
    classpath = confluenceCli
    mainClass.set(cliMainClass)
    args = baseArgs() + "convertOnly=false"
    doFirst {
        require(cfg("confluenceUsername", "CONFLUENCE_USERNAME", "").isNotBlank()) {
            "缺少凭据:请设置 -PconfluenceUsername + -PconfluencePassword(或 CONFLUENCE_USERNAME/CONFLUENCE_PASSWORD 环境变量)"
        }
        require(cfg("confluenceAncestorId", "CONFLUENCE_ANCESTOR_ID", "000000") != "000000") {
            "请设置真实的 ancestorId(-PconfluenceAncestorId 或 CONFLUENCE_ANCESTOR_ID)"
        }
    }
}
