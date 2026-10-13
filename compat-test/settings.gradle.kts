pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
    val kotlinVersion = providers.gradleProperty("kotlinVersion").orNull
        ?: error("Missing -PkotlinVersion=<version>, e.g. -PkotlinVersion=2.3.0")
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
        mavenLocal()
        mavenCentral()
        google()
    }
}
rootProject.name = "jetwhale-compat-test"
