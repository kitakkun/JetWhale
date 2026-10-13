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
group = "com.kitakkun.jetwhale.plugins.nav3"

kotlin {
    abiValidation()

    android.namespace = "com.kitakkun.jetwhale.plugins.nav3.protocol"
}

dependencies {
    commonMainApi(projects.jetwhaleProtocol.core)
    commonMainApi(libs.kotlinxSerializationJson)
}

jetwhalePublish {
    artifactId = "jetwhale-nav3-protocol"
    name = "JetWhale Nav3 Protocol"
    description = "Protocol types shared by the JetWhale Navigation 3 back stack agent and host plugins."
}
