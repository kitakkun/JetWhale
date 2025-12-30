@file:OptIn(ExperimentalAbiValidation::class)

import org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation

plugins {
    alias(libs.plugins.multiplatform)
    alias(libs.plugins.serialization)
}

kotlin {
    explicitApi()

    abiValidation {
        enabled.set(true)
    }

    androidLibrary.namespace = "com.kitakkun.jetwhale.agent.sdk"
}

dependencies {
    commonMainImplementation(projects.jetwhaleDebuggerProtocol)
    commonMainImplementation(libs.kotlinxCoroutinesCore)
}
