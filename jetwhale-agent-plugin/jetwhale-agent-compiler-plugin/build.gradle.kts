plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.mavenPublish)
    alias(libs.plugins.publish)
}

group = "com.kitakkun.jetwhale"
version = libs.versions.jetwhale.get() + if (hasProperty("jetwhaleSnapshot")) "-SNAPSHOT" else ""

/**
 * Which Kotlin compiler API this plugin is compiled against — that is, which version's JAR ships.
 *
 * Deliberately separate from `kotlin.compiler`, which is the *consumer's* Kotlin and drives the
 * Kotlin Gradle plugin for the whole build. Keeping them apart is what lets CI compile `sample` with
 * Kotlin 2.3 while the plugin acting on it was built against 2.4 — the arrangement a consumer on 2.3
 * actually gets from a single published artifact, and therefore the one worth testing.
 *
 * Set both to the same value to sweep source compatibility instead: `-Pkotlin.compiler=2.3.0
 * -Pkotlin.plugin.api=2.3.0` proves the source only touches API that 2.3 already had.
 */
val kotlinPluginApi: String = providers.gradleProperty("kotlin.plugin.api")
    .orElse(libs.versions.kotlin)
    .get()

kotlin {
    jvmToolchain(21)
    compilerOptions {
        // The plugin API is @ExperimentalCompilerApi by design; opting in per-file would be noise.
        freeCompilerArgs.add("-opt-in=org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi")
    }
}

dependencies {
    // compileOnly, always: kotlinc already has these on its classloader, and bundling them again
    // produces duplicate-class conflicts that stop the plugin loading at all.
    compileOnly("org.jetbrains.kotlin:kotlin-compiler-embeddable:$kotlinPluginApi")
    compileOnly(kotlin("stdlib"))
}

jetwhalePublish {
    artifactId = "jetwhale-agent-compiler-plugin"
    name = "JetWhale Agent Compiler Plugin"
    description = "Kotlin compiler plugin that bakes the build machine's address into buildMachineWss(port)."
}
