@file:OptIn(ExperimentalWasmDsl::class)

import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.multiplatform)
    alias(libs.plugins.kotlinxSerialization)
    alias(libs.plugins.publish)
}

// Distinct group so this module's coordinates don't collide with the other plugins' `protocol`.
group = "com.kitakkun.jetwhale.plugins.semantics"

kotlin {
    android.namespace = "com.kitakkun.jetwhale.plugins.semantics.protocol"
}

dependencies {
    commonMainApi(projects.jetwhaleProtocol.core)
    commonMainImplementation(libs.kotlinxSerializationJson)
}

jetwhalePublish {
    artifactId = "jetwhale-compose-semantics-inspector-protocol"
    name = "JetWhale Compose Semantics Inspector Protocol"
    description = "Transport-agnostic protocol types shared by the JetWhale Compose Semantics Inspector agent and host."
}
