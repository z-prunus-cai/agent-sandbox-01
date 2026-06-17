import com.github.gradle.node.npm.task.NpmTask

plugins {
    base
    alias(libs.plugins.node.gradle)
}

node {
    // Download and use a pinned Node/npm so the build is reproducible and does
    // not depend on what the developer/CI happens to have on PATH.
    version = "22.12.0"
    download = true
}

val frontendBuild by tasks.registering(NpmTask::class) {
    group = "build"
    description = "Builds the React SPA with Vite."
    dependsOn(tasks.named("npmInstall"))
    args = listOf("run", "build")
    inputs.dir("src")
    inputs.files("package.json", "package-lock.json", "vite.config.ts", "tsconfig.json", "index.html")
        .withPropertyName("frontendInputs")
    outputs.dir(layout.buildDirectory.dir("dist"))
        .withPropertyName("frontendDist")
}

val frontendTest by tasks.registering(NpmTask::class) {
    group = "verification"
    description = "Runs the Vitest suite with coverage."
    dependsOn(tasks.named("npmInstall"))
    args = listOf("run", "test")
    inputs.dir("src")
    inputs.files("package.json", "package-lock.json", "vite.config.ts")
        .withPropertyName("frontendTestInputs")
    outputs.dir(layout.buildDirectory.dir("coverage"))
        .withPropertyName("frontendCoverage")
}

// Hook into the standard lifecycle so `./gradlew build` / `test` / `check`
// all cover the SPA, matching the JVM modules' task names. Registering a
// `test` task means a root `./gradlew test` runs the frontend suite too.
tasks.named("assemble") { dependsOn(frontendBuild) }
tasks.register("test") { dependsOn(frontendTest) }
tasks.named("check") { dependsOn("test") }

// --- Publish the built SPA to other modules (backend) ---------------------
val frontendAssetsAttribute = Attribute.of("com.example.frontend-assets", String::class.java)

configurations.consumable("frontendAssets") {
    attributes { attribute(frontendAssetsAttribute, "static") }
    outgoing.artifact(layout.buildDirectory.dir("dist")) {
        builtBy(frontendBuild)
    }
}
