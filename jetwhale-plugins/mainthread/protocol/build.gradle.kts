@file:OptIn(ExperimentalWasmDsl::class, ExperimentalAbiValidation::class)

import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation

plugins {
    alias(libs.plugins.multiplatform)
    alias(libs.plugins.kotlinxSerialization)
    alias(libs.plugins.publish)
}

// Distinct group so this module's coordinates don't collide with the other plugins' `protocol`.
group = "com.kitakkun.jetwhale.plugins.mainthread"

kotlin {
    abiValidation {
    }

    android.namespace = "com.kitakkun.jetwhale.plugins.mainthread.protocol"
}

dependencies {
    commonMainApi(projects.jetwhaleProtocol.core)
    commonMainImplementation(libs.kotlinxSerializationJson)
}

jetwhalePublish {
    artifactId = "jetwhale-main-thread-monitor-protocol"
    name = "JetWhale Main Thread Monitor Protocol"
    description = "Protocol types shared by the JetWhale Main Thread Monitor agent and host plugins."
}
