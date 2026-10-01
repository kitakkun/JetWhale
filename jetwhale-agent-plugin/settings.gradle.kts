rootProject.name = "jetwhale-agent-plugin"

// A build of its own rather than modules inside jetwhale-gradle-plugin: that build's root applies
// `kotlin-dsl`, which puts an unversioned Kotlin Gradle plugin on the classpath and leaves
// subprojects unable to name the Kotlin version they compile against. Naming it is the whole point
// here — `-Pkotlin.compiler=<version>` is how the supported range is swept.
// Directory names match the published artifactIds on purpose: a composite build substitutes
// local projects for external coordinates by matching group + *project name*, not the
// maven-publish artifactId. Shorter names here would send the demo to Maven Central for a
// version that does not exist yet.
include("jetwhale-agent-compiler-plugin")
include("jetwhale-agent-gradle-plugin")

include("sample")

pluginManagement {
    // `kotlin.compiler` is the consumer's Kotlin, which compiles `sample`. The compiler plugin's
    // `kotlin.plugin.api` is separate and defaults to the shipped version, so CI can point a plugin
    // built with the shipped Kotlin at an older consumer, which is what consumers actually get.
    val shippedKotlin = java.io.File(settingsDir, "../gradle/libs.versions.toml")
        .readLines()
        .first { it.startsWith("kotlin = ") }
        .substringAfter('"')
        .substringBefore('"')
    plugins {
        kotlin("jvm") version providers.gradleProperty("kotlin.compiler").getOrElse(shippedKotlin)
    }

    includeBuild("../gradle-conventions")
    repositories {
        mavenCentral()
        gradlePluginPortal()
        google()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        google()
    }

    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}
