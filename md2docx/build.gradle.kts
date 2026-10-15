plugins {
    `java-library`
    application
}

group = "com.example"
version = "0.1.0-SNAPSHOT"
description = "Compose multiple Markdown fragments into one Word (.docx) document"

val poiVersion = "5.5.1"
val commonmarkVersion = "0.30.1"
val junitVersion = "5.11.4"

repositories {
    mavenCentral()
}

dependencies {
    // Apache-2.0
    implementation("org.apache.poi:poi-ooxml:$poiVersion")
    // BSD-2-Clause; the autolink extension pulls in autolink-java (MIT)
    implementation("org.commonmark:commonmark:$commonmarkVersion")
    implementation("org.commonmark:commonmark-ext-gfm-tables:$commonmarkVersion")
    implementation("org.commonmark:commonmark-ext-gfm-strikethrough:$commonmarkVersion")
    implementation("org.commonmark:commonmark-ext-task-list-items:$commonmarkVersion")
    implementation("org.commonmark:commonmark-ext-footnotes:$commonmarkVersion")
    implementation("org.commonmark:commonmark-ext-autolink:$commonmarkVersion")
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 17
    options.encoding = "UTF-8"
}

testing {
    suites {
        val test by getting(JvmTestSuite::class) {
            useJUnitJupiter(junitVersion)
        }

        // Validates generated documents against the complete ECMA-376 schemas. poi-ooxml-lite (what the library
        // ships with) only contains the schema classes POI itself uses, so this suite swaps in poi-ooxml-full.
        register<JvmTestSuite>("schemaTest") {
            useJUnitJupiter(junitVersion)
            dependencies {
                implementation(project())
                implementation("org.apache.poi:poi-ooxml-full:$poiVersion")
            }
            targets.all {
                testTask.configure {
                    description = "Validates generated .docx parts against the full OOXML schemas."
                    shouldRunAfter(test)
                }
            }
        }
    }
}

configurations.named("schemaTestCompileClasspath") { exclude(group = "org.apache.poi", module = "poi-ooxml-lite") }
configurations.named("schemaTestRuntimeClasspath") { exclude(group = "org.apache.poi", module = "poi-ooxml-lite") }

tasks.named("check") {
    dependsOn(testing.suites.named("schemaTest"))
}

application {
    // ./gradlew run                       -> examples/report -> build/example-report.docx
    // ./gradlew run --args="in-dir out.docx"
    mainClass = "com.example.md2docx.example.ExampleMain"
}
