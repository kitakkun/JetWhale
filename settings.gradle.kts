rootProject.name = "JetWhale"

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    includeBuild("gradle-conventions")
    includeBuild("jetwhale-gradle-plugin")
    includeBuild("jetwhale-agent-plugin")
    // Kotrail is pinned to one timestamped snapshot build so that CI and every machine run the
    // same rule set. The plugin marker only names 0.1.0-SNAPSHOT, so the module is resolved here;
    // the compiler plugin's build is kotrail.compilerPluginVersion in gradle.properties.
    resolutionStrategy {
        eachPlugin {
            if (requested.id.id == "com.kitakkun.kotrail") {
                useModule("com.kitakkun.kotrail:kotrail-gradle-plugin:${requested.version}")
            }
        }
    }
    repositories {
        mavenCentral()
        gradlePluginPortal()
        google()
        // A locally published Kotrail (publishToMavenLocal) wins over the Central snapshot when
        // present; CI and other machines fall through to Central.
        mavenLocal {
            content { includeGroupByRegex("com\\.kitakkun\\.kotrail.*") }
        }
        maven("https://central.sonatype.com/repository/maven-snapshots/") {
            content { includeGroupByRegex("com\\.kitakkun\\.kotrail.*") }
        }
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        google()
        // A locally published Kotrail (publishToMavenLocal) wins over the Central snapshot when
        // present; CI and other machines fall through to Central.
        mavenLocal {
            content { includeGroupByRegex("com\\.kitakkun\\.kotrail.*") }
        }
        maven("https://central.sonatype.com/repository/maven-snapshots/") {
            content { includeGroupByRegex("com\\.kitakkun\\.kotrail.*") }
        }
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

// Also at the top level, not only in pluginManagement: that block resolves the *plugin*, while the
// compiler-plugin JAR the plugin points `getPluginArtifact()` at is an ordinary dependency, and only
// a top-level include substitutes the local project for those coordinates. Without this the demo
// resolves jetwhale-agent-compiler-plugin from Maven Central, where the version being developed does
// not exist yet.
includeBuild("jetwhale-agent-plugin")

include(":jetwhale-annotations")
include(":jetwhale-protocol:core")

include(":jetwhale-agent-sdk")
include(":jetwhale-agent-runtime")

include(":jetwhale-host-sdk")
include(":jetwhale-host-ui")

include(":jetwhale-host:app")
include(":jetwhale-host:idea-plugin")
include(":jetwhale-host:idea-host")
include(":jetwhale-host:core:data")
include(":jetwhale-host:core:mcp")
include(":jetwhale-host:core:ui")
include(":jetwhale-host:core:architecture")
include(":jetwhale-host:core:model")
include(":jetwhale-host:ksp-processor")
include(":jetwhale-host:feature:settings")
include(":jetwhale-host:feature:plugin")

include(":jetwhale-plugins:example:host")
include(":jetwhale-plugins:example:protocol")
include(":jetwhale-plugins:example:agent")

include(":jetwhale-plugins:network:protocol")
include(":jetwhale-plugins:network:agent")
include(":jetwhale-plugins:network:agent-ktor")
include(":jetwhale-plugins:network:agent-okhttp")
include(":jetwhale-plugins:network:host")

include(":jetwhale-plugins:nav3:protocol")
include(":jetwhale-plugins:nav3:agent")
include(":jetwhale-plugins:nav3:host")

include(":jetwhale-plugins:semantics:protocol")
include(":jetwhale-plugins:semantics:agent")
include(":jetwhale-plugins:semantics:host")
include(":jetwhale-plugins:storage:protocol")
include(":jetwhale-plugins:storage:agent")
include(":jetwhale-plugins:storage:agent-datastore")
include(":jetwhale-plugins:storage:host")
include(":jetwhale-plugins:mainthread:protocol")
include(":jetwhale-plugins:mainthread:agent")

include(":test-annotations")

include(":tools:qa-agent")

include(":demo:shared")
include(":demo:android")
include(":demo:desktop")
include(":demo:web")
