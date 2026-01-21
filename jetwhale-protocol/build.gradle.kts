plugins {
    alias(libs.plugins.multiplatform)
    alias(libs.plugins.serialization)
    alias(libs.plugins.publish)
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

subprojects {
    group = "com.kitakkun.jetwhale.protocol"
}

jetwhalePublish {
    artifactId = "jetwhale-protocol"
    name = "JetWhale Protocol"
    description = "Protocol libraries for JetWhale"
}
