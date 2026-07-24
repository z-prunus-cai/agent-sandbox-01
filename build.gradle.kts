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

// docs/ 是"当前(latest)"版本的源;历史版本按 docs-versions.yaml 从 git 取。
val docsSource = layout.projectDirectory.dir("docs").asFile
val manifestFile = layout.projectDirectory.file("docs-versions.yaml").asFile

// 多版本装配产物:这才是真正喂给 CLI 的 asciidocRootFolder。
// 顶层每个 <slug>.adoc = Confluence 根页下的一个「版本页」,其内容树在 <slug>/。
val versionedDocsRoot = layout.buildDirectory.dir("versioned-docs").get().asFile
val convertOutput = layout.buildDirectory.dir("confluence").get().asFile

// ---------------------------------------------------------------------------
// docs-versions.yaml 解析(受控极简子集,零外部依赖)
//   latestName: <展示名>
//   versions:
//     - ref: <tag/branch/sha>
//       name: <展示名>
// 支持:整行 `#` 注释、` #` 行尾注释、值两端可加引号。
// ---------------------------------------------------------------------------
data class DocVersion(val ref: String, val name: String, val slug: String, val isLatest: Boolean)

fun unquote(s: String): String {
    val t = s.trim()
    return if (t.length >= 2 && ((t.startsWith("\"") && t.endsWith("\"")) || (t.startsWith("'") && t.endsWith("'"))))
        t.substring(1, t.length - 1) else t
}

fun splitKV(s: String): Pair<String, String> {
    val i = s.indexOf(':')
    return s.substring(0, i).trim() to s.substring(i + 1)
}

// 把展示名转成安全的文件/文件夹名(页面层级用),保证唯一
fun slugify(name: String, taken: MutableSet<String>): String {
    var base = buildString {
        for (c in name.lowercase()) {
            append(if (c in 'a'..'z' || c in '0'..'9') c else '-')
        }
    }.trim('-').replace(Regex("-+"), "-")
    if (base.isEmpty()) base = "v"
    var slug = base
    var n = 2
    while (!taken.add(slug)) { slug = "$base-$n"; n++ }
    return slug
}

fun parseManifest(file: File): Pair<String, List<Pair<String, String>>> {
    require(file.exists()) { "找不到版本清单文件:${file.absolutePath}" }
    var latestName = "latest"
    val entries = mutableListOf<LinkedHashMap<String, String>>()
    var inVersions = false
    for (rawLine in file.readLines()) {
        // 去掉行尾 ` #` 注释(值含 # 时请加引号)
        var line = rawLine
        val hash = line.indexOf(" #")
        if (hash >= 0) line = line.substring(0, hash)
        val trimmed = line.trim()
        if (trimmed.isEmpty() || trimmed.startsWith("#")) continue
        val indent = line.takeWhile { it == ' ' }.length

        if (indent == 0 && trimmed.startsWith("versions:")) { inVersions = true; continue }
        if (indent == 0 && !trimmed.startsWith("-") && trimmed.contains(":")) {
            val (k, v) = splitKV(trimmed)
            if (k == "latestName") latestName = unquote(v)
            inVersions = false
            continue
        }
        if (!inVersions) continue
        if (trimmed.startsWith("-")) {
            val m = LinkedHashMap<String, String>()
            entries.add(m)
            val rest = trimmed.removePrefix("-").trim()
            if (rest.contains(":")) { val (k, v) = splitKV(rest); m[k] = unquote(v) }
        } else if (trimmed.contains(":")) {
            val (k, v) = splitKV(trimmed)
            entries.lastOrNull()?.put(k, unquote(v))
        }
    }
    val versions = entries.map { m ->
        val ref = m["ref"] ?: error("版本清单条目缺少 ref:$m")
        val name = m["name"] ?: error("版本清单条目缺少 name:$m")
        ref to name
    }
    return latestName to versions
}

// latest + 所有历史版本,slug 唯一;latest 的 slug 固定为 "latest"
fun resolveVersions(): List<DocVersion> {
    val (latestName, versions) = parseManifest(manifestFile)
    val taken = mutableSetOf("latest")
    val out = mutableListOf(DocVersion("HEAD", latestName, "latest", isLatest = true))
    for ((ref, name) in versions) {
        out.add(DocVersion(ref, name, slugify(name, taken), isLatest = false))
    }
    // 展示名不得重复(Confluence 页标题全空间唯一)
    val dupNames = out.groupBy { it.name }.filterValues { it.size > 1 }.keys
    require(dupNames.isEmpty()) { "版本展示名重复(Confluence 标题必须唯一):$dupNames" }
    return out
}

// 解析 ref 为短 sha,仅用于展示;失败则回显原 ref
fun shortSha(ref: String): String = try {
    val out = java.io.ByteArrayOutputStream()
    exec {
        commandLine("git", "rev-parse", "--short", ref)
        standardOutput = out
        errorOutput = java.io.ByteArrayOutputStream()
        isIgnoreExitValue = true
    }
    out.toString().trim().ifEmpty { ref }
} catch (e: Exception) { ref }

// 收集"会真正变成 Confluence 页面"的 .adoc —— 复刻 Confluence Publisher 的判定规则:
//   1) 文件名以 `_` 开头的被当作 include 片段,不成页;
//   2) 只有与某个页 `foo.adoc` 同名配对的 `foo/` 文件夹才会被下钻,
//      没有配对 .adoc 的"孤儿"文件夹(如纯 include 的 partials/)整体跳过。
// 用它来界定"哪些是页",从而只给页加标题前缀、不动 include 片段。
fun collectPageFiles(dir: File): List<File> {
    val result = mutableListOf<File>()
    val adocs = dir.listFiles { f -> f.isFile && f.extension == "adoc" && !f.name.startsWith("_") }
        ?.sortedBy { it.name } ?: emptyList()
    for (adoc in adocs) {
        result.add(adoc)
        val childDir = File(dir, adoc.nameWithoutExtension)
        if (childDir.isDirectory) result.addAll(collectPageFiles(childDir))
    }
    return result
}

// 给一个 .adoc 的文档标题(首个 `= ` 行)加版本前缀,避免跨版本标题撞车
fun prefixDocTitle(adoc: File, prefix: String) {
    val lines = adoc.readLines().toMutableList()
    for (i in lines.indices) {
        val m = Regex("^=\\s+(.*)$").find(lines[i])
        if (m != null) {
            lines[i] = "= $prefix ${m.groupValues[1]}"
            adoc.writeText(lines.joinToString("\n") + "\n")
            return
        }
    }
}

// ---------------------------------------------------------------------------
// 多版本装配:为清单里的每个 ref + latest,在 versionedDocsRoot 下生成
//   <slug>.adoc  —— 版本落地页(展示名为标题 + ref 元信息 + 只读横幅)
//   <slug>/      —— 该 ref 的整棵 docs/ 内容(标题统一加 [展示名] 前缀)
// 结果:Confluence 根页(ancestorId)下 = 每个版本一个页,各自反映各自内容。
// ---------------------------------------------------------------------------
tasks.register("assembleVersionedDocs") {
    group = "documentation"
    description = "按 docs-versions.yaml 把各版本(latest + 各 git ref)装配成一个多版本页面树"
    inputs.file(manifestFile)
    inputs.dir(docsSource)
    outputs.dir(versionedDocsRoot)
    doLast {
        val versions = resolveVersions()
        delete(versionedDocsRoot)
        mkdir(versionedDocsRoot)

        for (v in versions) {
            val subtree = File(versionedDocsRoot, v.slug)
            mkdir(subtree)

            // 1) 取该版本的 docs/ 内容
            if (v.isLatest) {
                // latest = 自己(当前工作区)
                copy { from(docsSource); into(subtree) }
            } else {
                // 历史版本:git archive <ref> docs → tar → 解包
                val tar = File(temporaryDir, "${v.slug}.tar")
                val ok = exec {
                    commandLine("git", "archive", "--format=tar", "--output=${tar.absolutePath}", v.ref, "docs")
                    isIgnoreExitValue = true
                }.exitValue == 0
                require(ok) { "git archive 失败:ref='${v.ref}' 下没有 docs/ 或 ref 不存在" }
                val extract = File(temporaryDir, "${v.slug}-extract")
                delete(extract)
                mkdir(extract)
                exec { commandLine("tar", "-xf", tar.absolutePath, "-C", extract.absolutePath) }
                copy { from(File(extract, "docs")); into(subtree) }
            }

            // 2) 只给"会成页"的 .adoc 的文档标题加 [展示名] 前缀(去重跨版本标题);
            //    include 片段(`_` 开头 / 孤儿文件夹内)不算页,保持原样,避免破坏 include。
            val titlePrefix = "[${v.name}]"
            collectPageFiles(subtree).forEach { prefixDocTitle(it, titlePrefix) }

            // 3) 生成版本落地页 <slug>.adoc
            val sha = shortSha(v.ref)
            val refDesc = if (v.isLatest)
                "*latest* —— 指向当前检出(HEAD,`$sha`),即本仓库此刻的最新状态。"
            else
                "对应 git ref `${v.ref}`(解析为 `$sha`)。"
            File(versionedDocsRoot, "${v.slug}.adoc").writeText(
                """
                = ${v.name}
                :keywords: 版本, ${v.ref}

                [NOTE]
                ====
                本页及其所有子页由 Git 仓库经 *Confluence Publisher CLI* 自动发布(Git 为唯一事实来源)。
                **请勿在 Confluence 直接编辑** —— 改动会在下次同步时被覆盖。
                ====

                $refDesc

                下方子页即该版本 `docs/` 的原样内容;各版本互不影响,分别反映各自 ref 的文档。
                """.trimIndent() + "\n"
            )
        }

        logger.lifecycle("装配完成:${versions.size} 个版本页 → ${versionedDocsRoot}")
        versions.forEach { logger.lifecycle("  - ${it.slug}.adoc  =  ${it.name}  (ref=${it.ref})") }
    }
}

// 从 -P 属性或环境变量读取,convert(离线)用占位默认值即可
fun cfg(prop: String, env: String, default: String): String =
    (findProperty(prop) as String?) ?: System.getenv(env) ?: default

// CLI 参数是 key=value 形式;这些是 convert / publish 共用的部分
fun baseArgs(): List<String> = listOf(
    "asciidocRootFolder=${versionedDocsRoot.absolutePath}",
    "asciidocBuildFolder=${convertOutput.absolutePath}",
    "rootConfluenceUrl=${cfg("confluenceUrl", "CONFLUENCE_URL", "https://your-org.atlassian.net/wiki")}",
    "spaceKey=${cfg("confluenceSpaceKey", "CONFLUENCE_SPACE_KEY", "DOCS")}",
    // ancestorId:发布锚点页(不是空间根),即多版本页面树挂靠的"根目录";
    // 孤儿删除也只在这棵子树内生效
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
    dependsOn("assembleVersionedDocs")
    classpath = confluenceCli
    mainClass.set(cliMainClass)
    args = baseArgs() + "convertOnly=true"
    // 清掉上次转换残留,保证输出只反映本次的版本集合(便于人工检查)
    doFirst { delete(convertOutput) }
}

// 真正发布到 Confluence:需要凭据(Cloud = 邮箱 + API token)。
tasks.register<JavaExec>("confluencePublish") {
    group = "documentation"
    description = "发布到 Confluence(需 -PconfluenceUsername/-PconfluencePassword 或环境变量)"
    dependsOn("assembleVersionedDocs")
    classpath = confluenceCli
    mainClass.set(cliMainClass)
    args = baseArgs() + "convertOnly=false"
    doFirst {
        delete(convertOutput)
        require(cfg("confluenceUsername", "CONFLUENCE_USERNAME", "").isNotBlank()) {
            "缺少凭据:请设置 -PconfluenceUsername + -PconfluencePassword(或 CONFLUENCE_USERNAME/CONFLUENCE_PASSWORD 环境变量)"
        }
        require(cfg("confluenceAncestorId", "CONFLUENCE_ANCESTOR_ID", "000000") != "000000") {
            "请设置真实的 ancestorId(-PconfluenceAncestorId 或 CONFLUENCE_ANCESTOR_ID)"
        }
    }
}
