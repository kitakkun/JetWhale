@file:OptIn(ExperimentalWasmDsl::class, ExperimentalAbiValidation::class)

import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation

plugins {
    alias(libs.plugins.multiplatform)
    alias(libs.plugins.kotlinxSerialization)
    alias(libs.plugins.publish)
}

// Distinct group so this module's coordinates don't collide with the other plugins' `protocol`.
group = "com.kitakkun.jetwhale.plugins.background"

kotlin {
    abiValidation {
    }

    android.namespace = "com.kitakkun.jetwhale.plugins.background.protocol"
}

dependencies {
    commonMainApi(projects.jetwhaleProtocol.core)
    commonMainImplementation(libs.kotlinxSerializationJson)
}

jetwhalePublish {
    artifactId = "jetwhale-background-work-protocol"
    name = "JetWhale Background Work Protocol"
    description = "Protocol types shared by the JetWhale Background Work agent and host plugins."
}
