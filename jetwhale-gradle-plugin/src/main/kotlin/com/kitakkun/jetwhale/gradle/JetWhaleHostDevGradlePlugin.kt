package com.kitakkun.jetwhale.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.jvm.tasks.Jar

/**
 * A Gradle plugin to assist in developing JetWhale host plugins.
 */
class JetWhaleHostDevGradlePlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            tasks.register("buildJetWhalePluginJar", Jar::class.java) {
                it.group = "build"
                // include all runtime dependencies into the jar to make sure
                // the plugin with additional dependencies works correctly
                val dependencies = configurations
                    .getByName("runtimeClasspath")
                    .map(::zipTree)
                it.from(dependencies)
                it.duplicatesStrategy = DuplicatesStrategy.EXCLUDE
            }
        }
    }
}
