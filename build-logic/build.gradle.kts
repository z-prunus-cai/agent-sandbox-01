plugins {
    `kotlin-dsl`
}

// Convention plugins live here. To apply third-party plugins (Spring Boot,
// node-gradle, ...) from inside a precompiled script plugin, the plugin's
// marker artifact must be on this project's classpath.
dependencies {
    implementation(plugin(libs.plugins.spring.boot))
    implementation(plugin(libs.plugins.node.gradle))
}

// Turns a `[plugins]` catalog entry into the Maven coordinate of its marker
// artifact (id:id.gradle.plugin:version) so it can be used as a dependency.
fun plugin(plugin: Provider<PluginDependency>) =
    plugin.map { "${it.pluginId}:${it.pluginId}.gradle.plugin:${it.version}" }
