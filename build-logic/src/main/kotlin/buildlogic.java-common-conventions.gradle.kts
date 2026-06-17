// Base convention shared by every JVM module (common, backend, batch).
// Centralises the Java toolchain, the test framework and JaCoCo coverage so
// individual modules stay almost empty.
plugins {
    java
    jacoco
}

repositories {
    mavenCentral()
}

java {
    toolchain {
        // Gradle provisions/uses a matching JDK regardless of the machine's
        // default `java`, keeping builds reproducible across dev and CI.
        languageVersion = JavaLanguageVersion.of(21)
    }
}

// Modern test wiring via the JVM Test Suite plugin (built into Gradle).
testing {
    suites {
        val test by getting(JvmTestSuite::class) {
            useJUnitJupiter("5.11.4")
        }
    }
}

jacoco {
    toolVersion = "0.8.13"
}

tasks.named<Test>("test") {
    // Always (re)generate the coverage report after the tests run.
    finalizedBy(tasks.named("jacocoTestReport"))
}

// Bootstrap/config classes carry no meaningful logic to test, so they are
// excluded from both the report and the enforced threshold.
val coverageExclusions = listOf(
    "**/*Application.class",
    "**/config/**",
)

tasks.named<JacocoReport>("jacocoTestReport") {
    dependsOn(tasks.named("test"))
    classDirectories.setFrom(
        classDirectories.files.map { fileTree(it) { exclude(coverageExclusions) } }
    )
    reports {
        xml.required = true   // consumed by aggregation / CI tools (Sonar, Codecov)
        html.required = true  // human-friendly local report
    }
}

// Fail the build when a module drops below the coverage floor.
tasks.named<JacocoCoverageVerification>("jacocoTestCoverageVerification") {
    classDirectories.setFrom(
        classDirectories.files.map { fileTree(it) { exclude(coverageExclusions) } }
    )
    violationRules {
        rule {
            limit {
                counter = "LINE"
                value = "COVEREDRATIO"
                minimum = "0.50".toBigDecimal()
            }
        }
    }
}

// `./gradlew check` now also enforces the coverage floor.
tasks.named("check") {
    dependsOn(tasks.named("jacocoTestCoverageVerification"))
}
