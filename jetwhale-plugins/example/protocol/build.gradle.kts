@file:OptIn(ExperimentalWasmDsl::class)

import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.multiplatform)
    alias(libs.plugins.kotlinxSerialization)
    alias(libs.plugins.jetwhaleProtocol)
}

kotlin {
    android.namespace = "com.kitakkun.jetwhale.plugins.example.protocol"
}

jetwhaleProtocol {
    pluginId.set("com.kitakkun.jetwhale.example")
    protocolRevision.set(1)
    contractQualifiedName.set("com.kitakkun.jetwhale.plugins.example.protocol.ExamplePluginContract")
}

dependencies {
    commonMainApi(projects.jetwhaleProtocol.core)
    commonMainImplementation(libs.kotlinxSerializationJson)
}
