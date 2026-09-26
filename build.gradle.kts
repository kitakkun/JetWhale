import com.kitakkun.kotrail.gradle.KotrailExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask

plugins {
    alias(libs.plugins.kotlinJvm) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.jetbrainsCompose) apply false
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidKotlinMultiplatformLibrary) apply false
    alias(libs.plugins.kotlinxSerialization) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.multiplatform) apply false
    alias(libs.plugins.serialization) apply false
    alias(libs.plugins.compose) apply false
    alias(libs.plugins.debuggerComposeFeature) apply false
    alias(libs.plugins.metro) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.mokkery) apply false
    alias(libs.plugins.aboutLibraries) apply false
    alias(libs.plugins.mavenPublish) apply false
    alias(libs.plugins.publish) apply false
    alias(libs.plugins.spotless)
    alias(libs.plugins.kotrail) apply false
}

// Spotless takes properties such as max_line_length from .editorconfig but ignores its rule switches,
// so these two are switched off here as well: they would join every wrapped signature that fits
// within the line length back onto one line.
val ktlintDisabledRules = mapOf(
    "ktlint_standard_function-signature" to "disabled",
    "ktlint_standard_class-signature" to "disabled",
)

spotless {
    kotlin {
        target("**/*.kt")
        // Compiler-plugin fixtures are compiled by the test framework and compared against IR dumps
        // that record source offsets, so reformatting them would invalidate the golden files rather
        // than tidy anything. Their shape is part of what they assert.
        targetExclude("**/build/**", "**/testData/**")
        ktlint().editorConfigOverride(ktlintDisabledRules)
    }
    kotlinGradle {
        target("**/*.gradle.kts")
        targetExclude("**/build/**")
        ktlint().editorConfigOverride(ktlintDisabledRules)
    }
}

allprojects {
    group = "com.kitakkun.jetwhale"
    // Pass -PjetwhaleSnapshot to publish a SNAPSHOT of the current version (overwritable, goes to the
    // Central snapshots repo) instead of a release.
    version = rootProject.libs.versions.jetwhale.get() +
        if (rootProject.hasProperty("jetwhaleSnapshot")) "-SNAPSHOT" else ""
}

subprojects {
    val kotlinPluginIds = listOf("org.jetbrains.kotlin.jvm", "org.jetbrains.kotlin.multiplatform")
    kotlinPluginIds.forEach { kotlinPluginId ->
        pluginManager.withPlugin(kotlinPluginId) {
            pluginManager.apply("com.kitakkun.kotrail")
            configure<KotrailExtension> {
                configFile = rootProject.layout.projectDirectory.file("kotrail.yaml")
                // The annotations artifact would otherwise land in `implementation`, and so in the
                // POM of every published JVM module.
                annotations = false
            }
        }
    }

    // The agent compiler plugin warns on purpose whenever it bakes the build machine's address into
    // a buildMachineWss call, which the demo does on every iOS build.
    if (path != ":demo:shared") {
        tasks.withType<KotlinCompilationTask<*>>().configureEach {
            compilerOptions.allWarningsAsErrors = true
        }
    }

    // Explicit backing fields are experimental, so published modules keep them out of the code
    // their consumers compile against.
    pluginManager.withPlugin("publish") {
        tasks.withType<KotlinCompilationTask<*>>().configureEach {
            compilerOptions.freeCompilerArgs.addAll(
                "-P",
                "plugin:com.kitakkun.kotrail:rules.preferExplicitBackingField=off",
            )
        }
    }
}
