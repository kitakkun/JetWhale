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
group = "com.kitakkun.jetwhale.plugins.actions"

kotlin {
    abiValidation()

    android.namespace = "com.kitakkun.jetwhale.plugins.actions.protocol"
}

jetwhaleProtocol {
    pluginId.set("com.kitakkun.jetwhale.actions")
    protocolRevision.set(1)
    contractQualifiedName.set("com.kitakkun.jetwhale.plugins.actions.protocol.ActionsPluginContract")
}

dependencies {
    commonMainApi(projects.jetwhaleProtocol.core)
    commonMainApi(libs.kotlinxSerializationJson)
}

jetwhalePublish {
    artifactId = "jetwhale-debug-actions-protocol"
    name = "JetWhale Debug Actions Protocol"
    description = "Protocol types shared by the JetWhale Debug Actions agent and host plugins."
}
