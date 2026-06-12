@file:OptIn(ExperimentalWasmDsl::class)

import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.multiplatform)
    alias(libs.plugins.kotlinxSerialization)
    alias(libs.plugins.publish)
}

// Distinct group so this module's coordinates don't collide with `example:protocol`.
group = "com.kitakkun.jetwhale.plugins.network"

kotlin {
    android.namespace = "com.kitakkun.jetwhale.plugins.network.protocol"
}

dependencies {
    commonMainImplementation(libs.kotlinxSerializationJson)
}

jetwhalePublish {
    artifactId = "jetwhale-network-inspector-protocol"
    name = "JetWhale Network Inspector Protocol"
    description = "Transport-agnostic protocol types shared by the JetWhale Network Inspector agent and host."
}
