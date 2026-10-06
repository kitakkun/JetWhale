@file:OptIn(ExperimentalWasmDsl::class, ExperimentalAbiValidation::class)

import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation

plugins {
    alias(libs.plugins.multiplatform)
    alias(libs.plugins.kotlinxSerialization)
    alias(libs.plugins.publish)
}

// Distinct group: the other plugins also have a `protocol` module, and Gradle substitutes projects
// that share coordinates during resolution.
group = "com.kitakkun.jetwhale.plugins.deeplinks"

kotlin {
    abiValidation()

    android.namespace = "com.kitakkun.jetwhale.plugins.deeplinks.protocol"
}

dependencies {
    commonMainApi(projects.jetwhaleProtocol.core)
    commonMainImplementation(libs.kotlinxSerializationJson)
}

jetwhalePublish {
    artifactId = "jetwhale-deep-links-protocol"
    name = "JetWhale Deep Links Protocol"
    description = "Protocol types shared by the JetWhale Deep Links agent and host plugins."
}
