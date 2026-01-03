rootProject.name = "JetWhale"

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    includeBuild("gradle-conventions")
    repositories {
        mavenCentral()
        gradlePluginPortal()
        google()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        google()
    }
}

include(":jetwhale-protocol")
include(":jetwhale-protocol:core")
include(":jetwhale-protocol:host")
include(":jetwhale-protocol:agent")

include(":jetwhale-agent-sdk")
include(":jetwhale-agent-runtime")

include(":jetwhale-host-sdk")

include(":jetwhale-host:app")
include(":jetwhale-host:core:data")
include(":jetwhale-host:core:ui")
include(":jetwhale-host:core:architecture")
include(":jetwhale-host:core:model")
include(":jetwhale-host:ksp-processor")
include(":jetwhale-host:feature:settings")
include(":jetwhale-host:feature:plugin")

include(":jetwhale-plugins:example:host")
include(":jetwhale-plugins:example:protocol")
include(":jetwhale-plugins:example:agent")

include(":demo")
