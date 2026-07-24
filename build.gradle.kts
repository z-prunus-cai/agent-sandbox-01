// ---------------------------------------------------------------------------
// Self-contained database change (Flyway) project
//   - Spring Boot 4.1.0  (manages Flyway 12.4.0 and mssql-jdbc 13.4.0.jre11)
//   - Flyway 12.4.0 with SQL Server (T-SQL) support
//   - Java 21 (LTS)
//
// Two ways to run migrations, sharing the SAME SQL scripts:
//   1. Boot "migration job"  ->  ./gradlew bootRun   (or  java -jar build/libs/*.jar)
//        Reads YAML/profiles/env vars, migrates, then exits. This is the CI entry point.
//   2. Flyway Gradle tasks   ->  ./gradlew flywayInfo / flywayValidate
//        Dev-only convenience for inspecting/validating without booting the app.
// ---------------------------------------------------------------------------

// The Flyway Gradle plugin needs the database-specific module on its OWN
// (buildscript) classpath to understand SQL Server. Pinned to the exact version
// Spring Boot 4.1.0 manages at runtime, so the CLI and the app never drift.
buildscript {
    repositories { mavenCentral() }
    dependencies {
        classpath("org.flywaydb:flyway-sqlserver:12.4.0")
    }
}

plugins {
    java
    id("org.springframework.boot") version "4.1.0"
    id("io.spring.dependency-management") version "1.1.7"
    id("org.flywaydb.flyway") version "12.4.0"
}

group = "com.example.db"
// Artifact version is single-sourced from gradle.properties and overridable in
// CI, e.g. `./gradlew bootJar -Pversion=1.4.2` or from a git tag. It ends up in
// the jar name (db-migration-<version>.jar) and in build-info.properties.
version = providers.gradleProperty("version").getOrElse("0.0.1-SNAPSHOT")
description = "Self-contained Flyway database migration project for SQL Server (extensible)"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

// Short git commit for build traceability; "unknown" outside a git checkout.
fun gitCommit(): String = try {
    providers.exec {
        commandLine("git", "rev-parse", "--short", "HEAD")
    }.standardOutput.asText.get().trim().ifEmpty { "unknown" }
} catch (ex: Exception) {
    "unknown"
}

// Generate META-INF/build-info.properties (version, build time, group/artifact)
// plus the git commit, so a deployed jar can report exactly what it is.
springBoot {
    buildInfo {
        properties {
            additional.put("commit", gitCommit())
        }
    }
}

repositories {
    mavenCentral()
}

// ---------------------------------------------------------------------------
// Integration tests live in their own source set + task so the DEFAULT build
// needs no Docker. `./gradlew test` runs only fast unit tests; the container
// tests run only when you explicitly ask for them (and self-skip if Docker is
// absent, see @EnabledIf in FlywayMigrationIT).
//
// Declared before `dependencies` so the integrationTest* configurations exist
// when the dependency block references them.
// ---------------------------------------------------------------------------
sourceSets {
    create("integrationTest") {
        compileClasspath += sourceSets["main"].output
        runtimeClasspath += sourceSets["main"].output
    }
}

// Let integrationTest reuse the regular test dependencies (JUnit, AssertJ, ...).
configurations["integrationTestImplementation"].extendsFrom(configurations["testImplementation"])
configurations["integrationTestRuntimeOnly"].extendsFrom(configurations["testRuntimeOnly"])

dependencies {
    // Boot 4 is modular: the Flyway starter brings flyway-core + the Boot
    // auto-configuration that runs migrations on startup.
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation("org.springframework.boot:spring-boot-starter-jdbc")

    // SQL Server support (primary target). Swap/extend this pair to add databases.
    implementation("org.flywaydb:flyway-sqlserver")
    runtimeOnly("com.microsoft.sqlserver:mssql-jdbc")

    // --- To extend to another database, add its Flyway module + JDBC driver, e.g. ---
    // implementation("org.flywaydb:flyway-database-postgresql")
    // runtimeOnly("org.postgresql:postgresql")

    // Fast unit tests (no external infrastructure) run in the default `test` task.
    testImplementation("org.springframework.boot:spring-boot-starter-test")

    // Testcontainers is only needed by the integration tests (Docker required).
    // Testcontainers 2.x prefixes every module artifact with "testcontainers-".
    "integrationTestImplementation"("org.springframework.boot:spring-boot-testcontainers")
    "integrationTestImplementation"("org.testcontainers:testcontainers-junit-jupiter")
    "integrationTestImplementation"("org.testcontainers:testcontainers-mssqlserver")
}

val integrationTest = tasks.register<Test>("integrationTest") {
    description = "Runs integration tests against real infrastructure (requires Docker)."
    group = "verification"
    testClassesDirs = sourceSets["integrationTest"].output.classesDirs
    classpath = sourceSets["integrationTest"].runtimeClasspath
    shouldRunAfter(tasks.named("test"))
}

// Deliberately NOT wired into `check`/`build`: the default verification path
// stays Docker-free. Run integration tests on demand: `./gradlew integrationTest`.

tasks.withType<Test> {
    useJUnitPlatform()
}

// ---------------------------------------------------------------------------
// Flyway Gradle plugin config (dev convenience: flywayInfo / flywayValidate).
// Credentials come from environment variables — never hard-code secrets here.
// The Boot app is the source of truth for real migrations; these locations
// mirror the layered layout for one env, chosen with -PflywayEnv (default local):
//   ./gradlew flywayInfo -PflywayEnv=dev
// ---------------------------------------------------------------------------
val flywayEnv = (project.findProperty("flywayEnv") as String?) ?: "local"
flyway {
    url = System.getenv("DB_URL")
    user = System.getenv("DB_USER")
    password = System.getenv("DB_PASSWORD")
    schemas = arrayOf("app")
    defaultSchema = "app"
    locations = arrayOf(
        "filesystem:src/main/resources/db/migration/sqlserver/ddl",
        "filesystem:src/main/resources/db/migration/sqlserver/masterdata/common",
        "filesystem:src/main/resources/db/migration/sqlserver/env/$flywayEnv/masterdata",
    )
    cleanDisabled = true
}
