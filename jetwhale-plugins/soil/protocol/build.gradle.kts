@file:OptIn(ExperimentalWasmDsl::class, ExperimentalAbiValidation::class)

import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation

plugins {
    alias(libs.plugins.multiplatform)
    alias(libs.plugins.kotlinxSerialization)
    alias(libs.plugins.publish)
}

// Distinct group so these plugin modules don't share coordinates with the other plugins' modules
// (which also have leaf names protocol/agent/host) and get substituted during resolution.
group = "com.kitakkun.jetwhale.plugins.soil"

kotlin {
    abiValidation()

    android.namespace = "com.kitakkun.jetwhale.plugins.soil.protocol"
}

dependencies {
    commonMainApi(projects.jetwhaleProtocol.core)
    commonMainApi(libs.kotlinxSerializationJson)

    commonTestImplementation(libs.kotlinTest)
}

jetwhalePublish {
    artifactId = "jetwhale-soil-inspector-protocol"
    name = "JetWhale Soil Inspector Protocol"
    description = "Protocol types shared by the JetWhale Soil Inspector agent and host plugins."
}
