rootProject.name = "agent-sandbox-01"

// Share build logic (convention plugins) as an included build.
// This is the modern alternative to buildSrc and gives faster, more
// cacheable configuration for a multi-module monorepo.
pluginManagement {
    includeBuild("build-logic")
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

include(
    "common",
    "backend",
    "batch",
    "frontend",
    "coverage",
)
