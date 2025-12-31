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

include(":jetwhale-debugger-protocol")

include(":jetwhale-debugger-agent-sdk")
include(":jetwhale-debugger-agent-runtime")

include(":jetwhale-debugger-host-sdk")

include(":jetwhale-debugger-host:app")
include(":jetwhale-debugger-host:core:data")
include(":jetwhale-debugger-host:core:ui")
include(":jetwhale-debugger-host:core:architecture")
include(":jetwhale-debugger-host:core:model")
include(":jetwhale-debugger-host:ksp-processor")
include(":jetwhale-debugger-host:feature:settings")
include(":jetwhale-debugger-host:feature:plugin")
