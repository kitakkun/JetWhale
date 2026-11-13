@file:OptIn(ExperimentalWasmDsl::class, ExperimentalAbiValidation::class)

import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation

plugins {
    alias(libs.plugins.multiplatform)
    alias(libs.plugins.kotlinxSerialization)
    alias(libs.plugins.publish)
    alias(libs.plugins.jetwhaleProtocol)
}

// Distinct group so this module's coordinates don't collide with the other plugins' `protocol`.
group = "com.kitakkun.jetwhale.plugins.storage"

kotlin {
    abiValidation()

    android.namespace = "com.kitakkun.jetwhale.plugins.storage.protocol"
}

jetwhaleProtocol {
    pluginId.set("com.kitakkun.jetwhale.storage")
    protocolRevision.set(1)
    contractQualifiedName.set("com.kitakkun.jetwhale.plugins.storage.protocol.StoragePluginContract")
}

dependencies {
    commonMainApi(projects.jetwhaleProtocol.core)
    commonMainImplementation(libs.kotlinxSerializationJson)
}

jetwhalePublish {
    artifactId = "jetwhale-storage-inspector-protocol"
    name = "JetWhale Storage Inspector Protocol"
    description = "Protocol types shared by the JetWhale Storage Inspector agent and host plugins."
}
