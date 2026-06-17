// Convention for runnable Spring Boot applications (backend, batch).
// Layers Spring Boot on top of the common Java config.
plugins {
    id("buildlogic.java-common-conventions")
    id("org.springframework.boot")
}

// Import the Spring Boot BOM as a native Gradle platform instead of the
// io.spring.dependency-management plugin. Platform constraints propagate
// transitively, so the `coverage` aggregation project (and any consumer) can
// resolve the managed versions of starters declared without an explicit version.
val libs = the<VersionCatalogsExtension>().named("libs")
val springBootVersion = libs.findVersion("springBoot").get().requiredVersion

dependencies {
    add("implementation", platform("org.springframework.boot:spring-boot-dependencies:$springBootVersion"))
}
