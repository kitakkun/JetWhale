@file:OptIn(ExperimentalAbiValidation::class)

import org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation

plugins {
    alias(libs.plugins.jvm)
    alias(libs.plugins.serialization)
    alias(libs.plugins.publish)
}

kotlin {
    explicitApi()

    abiValidation {
        enabled.set(true)
    }
}

dependencies {
    api(projects.jetwhaleProtocol.core)
}

jetwhalePublish {
    artifactId = "jetwhale-protocol-host"
    name = "Jetwhale Protocol Host"
    description = "Jetwhale Protocol definitions for Host"
}
