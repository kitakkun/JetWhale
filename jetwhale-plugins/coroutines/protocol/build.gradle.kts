@file:OptIn(ExperimentalWasmDsl::class, ExperimentalAbiValidation::class)

import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation

plugins {
    alias(libs.plugins.multiplatform)
    alias(libs.plugins.kotlinxSerialization)
    alias(libs.plugins.publish)
}

// Distinct group: Gradle resolves projects with the same group and name as one module, and every
// plugin has a `protocol` module.
group = "com.kitakkun.jetwhale.plugins.coroutines"

kotlin {
    abiValidation {
    }

    android.namespace = "com.kitakkun.jetwhale.plugins.coroutines.protocol"
}

dependencies {
    commonMainApi(projects.jetwhaleProtocol.core)
    commonMainImplementation(libs.kotlinxSerializationJson)
}

jetwhalePublish {
    artifactId = "jetwhale-coroutine-inspector-protocol"
    name = "JetWhale Coroutine Inspector Protocol"
    description = "Protocol types shared by the JetWhale Coroutine Inspector agent and host plugins."
}
