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
group = "com.kitakkun.jetwhale.plugins.semantics"

kotlin {
    abiValidation()

    android.namespace = "com.kitakkun.jetwhale.plugins.semantics.protocol"
}

jetwhaleProtocol {
    pluginId.set("com.kitakkun.jetwhale.semantics")
    protocolRevision.set(1)
    contractQualifiedName.set("com.kitakkun.jetwhale.plugins.semantics.protocol.SemanticsPluginContract")
}

dependencies {
    commonMainApi(projects.jetwhaleProtocol.core)
    commonMainImplementation(libs.kotlinxSerializationJson)
    commonTestImplementation(libs.kotlinTest)
}

jetwhalePublish {
    artifactId = "jetwhale-compose-semantics-inspector-protocol"
    name = "JetWhale Compose Semantics Inspector Protocol"
    description = "Transport-agnostic protocol types shared by the JetWhale Compose Semantics Inspector agent and host."
}
