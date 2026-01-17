@file:OptIn(ExperimentalAbiValidation::class)

import org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation

plugins {
    alias(libs.plugins.multiplatform)
    alias(libs.plugins.serialization)
    alias(libs.plugins.publish)
}

kotlin {
    androidLibrary.namespace = "com.kitakkun.jetwhale.protocol.agent"

    explicitApi()

    abiValidation {
        enabled.set(true)
    }

    sourceSets.commonMain.dependencies {
        api(projects.jetwhaleProtocol.core)
    }
}

jetwhalePublish {
    artifactId = "jetwhale-protocol-agent"
    name = "Jetwhale Protocol Agent"
    description = "Jetwhale Protocol definitions for Agent"
}
