@file:OptIn(ExperimentalWasmDsl::class, ExperimentalAbiValidation::class)

import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation

plugins {
    alias(libs.plugins.multiplatform)
    alias(libs.plugins.kotlinxSerialization)
    alias(libs.plugins.publish)
    alias(libs.plugins.jetwhaleProtocol)
}

// Distinct group so this module's coordinates don't collide with `example:protocol`.
group = "com.kitakkun.jetwhale.plugins.network"

kotlin {
    abiValidation()

    android.namespace = "com.kitakkun.jetwhale.plugins.network.protocol"
}

jetwhaleProtocol {
    pluginId.set("com.kitakkun.jetwhale.network")
    protocolRevision.set(1)
    contractQualifiedName.set("com.kitakkun.jetwhale.plugins.network.protocol.NetworkPluginContract")
}

dependencies {
    commonMainApi(projects.jetwhaleProtocol.core)
    commonMainImplementation(libs.kotlinxSerializationJson)
}

jetwhalePublish {
    artifactId = "jetwhale-network-inspector-protocol"
    name = "JetWhale Network Inspector Protocol"
    description = "Transport-agnostic protocol types shared by the JetWhale Network Inspector agent and host."
}
