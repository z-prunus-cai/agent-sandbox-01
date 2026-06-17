import org.gradle.language.jvm.tasks.ProcessResources

plugins {
    id("buildlogic.spring-app-conventions")
}

dependencies {
    implementation(project(":common"))
    implementation(libs.spring.boot.starter.web)
    testImplementation(libs.spring.boot.starter.test)
}

// --- Consume the frontend SPA as static resources -------------------------
//
// The frontend module publishes its built assets through a consumable
// configuration. We declare a matching resolvable configuration here and let
// Gradle resolve the artifact (and wire the build dependency) for us — no
// reaching into another project's tasks, so project isolation is preserved.
val frontendAssetsAttribute = Attribute.of("com.example.frontend-assets", String::class.java)

// Declarable "dependency scope" config holds the project dependency; the
// resolvable config (with the matching attribute) actually resolves the files.
// This split is required by modern Gradle's configuration roles.
val frontendAssetsDeps = configurations.dependencyScope("frontendAssetsDependencies")

val frontendAssets = configurations.resolvable("frontendAssets") {
    extendsFrom(frontendAssetsDeps.get())
    attributes { attribute(frontendAssetsAttribute, "static") }
}

dependencies {
    frontendAssetsDeps(project(":frontend"))
}

// `-Pskip.frontend=true` lets backend-only iterations skip the npm build.
val skipFrontend = providers.gradleProperty("skip.frontend")
    .map { it.toBoolean() }
    .getOrElse(false)

tasks.named<ProcessResources>("processResources") {
    if (!skipFrontend) {
        // Copies the built SPA into BOOT-INF/classes/static, which Spring Boot
        // serves from the root context out of the box.
        from(frontendAssets) {
            into("static")
        }
    }
}
