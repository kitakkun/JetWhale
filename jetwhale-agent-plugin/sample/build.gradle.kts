import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    alias(libs.plugins.kotlinJvm)
}

kotlin {
    jvmToolchain(21)
}

/**
 * A fixed, documentation-only address (RFC 5737 TEST-NET-2).
 *
 * Detection is deliberately bypassed here: the sample asserts on an exact string, and a test that
 * depended on whatever network the CI runner happened to be on would be a flake generator.
 * Detection itself is covered where it belongs, in the Gradle plugin's own tests.
 */
val sampleAddress = "198.51.100.7"

// The plugin is wired by raw -Xplugin= rather than by applying com.kitakkun.jetwhale.agent, so the
// artifact under test is the JAR this build just produced. Applying the Gradle plugin would resolve
// the compiler plugin from Maven Central by coordinates and test a previous release instead.
val compilerPlugin: Configuration by configurations.creating

dependencies {
    compilerPlugin(project(":jetwhale-agent-compiler-plugin"))
    testImplementation(kotlin("test"))
}

tasks.withType<KotlinCompile>().configureEach {
    inputs.files(compilerPlugin)
    compilerOptions.freeCompilerArgs.addAll(
        compilerPlugin.elements.map { files ->
            listOf(
                "-Xplugin=${files.first().asFile.absolutePath}",
                "-P",
                "plugin:com.kitakkun.jetwhale.agent:buildMachineAddress=$sampleAddress",
            )
        },
    )
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
