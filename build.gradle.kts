import com.kitakkun.kotrail.gradle.KotrailExtension
import org.jetbrains.kotlin.gradle.targets.js.testing.KotlinJsTest
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

spotless {
    kotlin {
        target("**/*.kt")
        targetExclude("**/build/**", "**/testData/**")
        ktlint()
    }
    kotlinGradle {
        target("**/*.gradle.kts")
        targetExclude("**/build/**")
        ktlint()
    }
}

allprojects {
    group = "com.kitakkun.jetwhale"
    version = rootProject.libs.versions.jetwhale.get() +
        if (rootProject.hasProperty("jetwhaleSnapshot")) "-SNAPSHOT" else ""
}

// Each browser test task starts its own Karma, webpack and ChromeHeadless, 1–2 GB together. Several
// at once, beside the Gradle and Kotlin daemons, run the 16 GB CI runner out of memory and it shuts
// down mid-build.
abstract class BrowserTestSlot : BuildService<BuildServiceParameters.None>

val browserTestSlot = gradle.sharedServices.registerIfAbsent("browserTestSlot", BrowserTestSlot::class) {
    maxParallelUsages = 1
}

subprojects {
    tasks.withType<KotlinJsTest>().named { it.endsWith("BrowserTest") }.configureEach {
        usesService(browserTestSlot)
    }

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
