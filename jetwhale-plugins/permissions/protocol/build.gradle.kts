@file:OptIn(ExperimentalWasmDsl::class, ExperimentalAbiValidation::class)

import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation

plugins {
    alias(libs.plugins.multiplatform)
    alias(libs.plugins.kotlinxSerialization)
    alias(libs.plugins.publish)
}

// Gradle takes projects with the same group and name for one module and substitutes one for the
// other; every plugin has a `protocol` module, so each plugin has its own group.
group = "com.kitakkun.jetwhale.plugins.permissions"

kotlin {
    abiValidation()

    android.namespace = "com.kitakkun.jetwhale.plugins.permissions.protocol"
}

dependencies {
    commonMainApi(projects.jetwhaleProtocol.core)
    commonMainApi(libs.kotlinxSerializationJson)
}

jetwhalePublish {
    artifactId = "jetwhale-permissions-protocol"
    name = "JetWhale Permissions Protocol"
    description = "Protocol types shared by the JetWhale Permissions agent and host plugins."
}
