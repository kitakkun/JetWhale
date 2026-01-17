@file:OptIn(ExperimentalAbiValidation::class)

import org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation

plugins {
    alias(libs.plugins.multiplatform)
    alias(libs.plugins.serialization)
    alias(libs.plugins.publish)
}

kotlin {
    androidLibrary {
        namespace = "com.kitakkun.jetwhale.protocol.core"
    }

    explicitApi()

    abiValidation {
        enabled.set(true)
    }
}

jetwhalePublish {
    artifactId = "jetwhale-protocol-core"
    name = "Jetwhale Protocol Core"
}
