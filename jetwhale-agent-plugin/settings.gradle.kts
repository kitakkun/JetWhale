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

pluginManagement {
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
