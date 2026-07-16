// Standalone build (NOT included in the root build): it compiles a consumer app against
// published JetWhale artifacts with an arbitrary Kotlin version to verify the minimum
// supported Kotlin version. See README.md in this directory.
pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
    val kotlinVersion = providers.gradleProperty("kotlinVersion").get()
    resolutionStrategy {
        eachPlugin {
            if (requested.id.id.startsWith("org.jetbrains.kotlin")) {
                useVersion(kotlinVersion)
            }
        }
    }
}
dependencyResolutionManagement {
    repositories {
        // mavenLocal first so a locally published (pre-release) JetWhale can be tested
        // via ./gradlew publishToMavenLocal in the root build.
        mavenLocal()
        mavenCentral()
        google()
    }
}
rootProject.name = "jetwhale-compat-test"
