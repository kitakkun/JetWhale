plugins {
    alias(libs.plugins.multiplatform)
    alias(libs.plugins.serialization)
}

kotlin {
    androidLibrary.namespace = "com.kitakkun.jetwhale.protocol"

    sourceSets {
        commonMain.dependencies {
            api(projects.jetwhaleProtocol.core)
            api(projects.jetwhaleProtocol.agent)
        }

        jvmMain.dependencies {
            api(projects.jetwhaleProtocol.host)
        }
    }
}
