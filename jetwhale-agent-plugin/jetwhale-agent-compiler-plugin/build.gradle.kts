plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.mavenPublish)
    alias(libs.plugins.publish)
}

group = "com.kitakkun.jetwhale"
version = libs.versions.jetwhale.get() + if (hasProperty("jetwhaleSnapshot")) "-SNAPSHOT" else ""

/**
 * Which Kotlin compiler this plugin is built against.
 *
 * The one switch the whole supported range turns on: `-Pkotlin.compiler=2.3.21` rebuilds and tests
 * against that compiler without touching a source file. CI sweeps it across every version in
 * `supportedKotlinVersions` (see the workflow); a version that is not swept is not supported,
 * whatever the README says.
 */
val kotlinCompiler: String = providers.gradleProperty("kotlin.compiler")
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
    compileOnly("org.jetbrains.kotlin:kotlin-compiler-embeddable:$kotlinCompiler")
    compileOnly(kotlin("stdlib"))
}

jetwhalePublish {
    artifactId = "jetwhale-agent-compiler-plugin"
    name = "JetWhale Agent Compiler Plugin"
    description = "Kotlin compiler plugin that bakes the build machine's address into buildMachineWss(port)."
}
