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

// Consumes the compiler plugin JAR the way a real project's compilation does, and asserts on the
// result. Not published — its job is to fail CI when the shipped artifact stops transforming code
// compiled by a Kotlin version we claim to support.
include("sample")

pluginManagement {
    // `kotlin.compiler` is the consumer's Kotlin: it drives the Kotlin Gradle plugin, and so the
    // version that compiles `sample`. The compiler plugin's own `kotlin.plugin.api` is separate and
    // defaults to the shipped version, so CI can point a 2.4-built plugin at a 2.3 consumer — which
    // is the arrangement consumers actually get, and the one worth proving.
    val shippedKotlin = java.io.File(settingsDir, "../gradle/libs.versions.toml")
        .readLines()
        .first { it.startsWith("kotlin = ") }
        .substringAfter('"')
        .substringBefore('"')
    plugins {
        kotlin("jvm") version providers.gradleProperty("kotlin.compiler").getOrElse(shippedKotlin)
    }

    // Reuse the internal `publish` convention (jetwhalePublish { ... }) that simplifies maven-publish.
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
