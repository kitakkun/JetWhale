@file:OptIn(ExperimentalAbiValidation::class)

import org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation

plugins {
    alias(libs.plugins.multiplatform)
    alias(libs.plugins.serialization)
    alias(libs.plugins.publish)
}

kotlin {
    explicitApi()

    abiValidation {
        enabled.set(true)
    }

    androidLibrary.namespace = "com.kitakkun.jetwhale.agent.sdk"
}

dependencies {
    commonMainApi(projects.jetwhaleProtocol.core)
    commonMainApi(projects.jetwhaleProtocol.agent)
    commonMainImplementation(libs.kotlinxCoroutinesCore)
}

jetwhalePublish {
    artifactId = "jetwhale-agent-sdk"
    name = "JetWhale Agent Sdk"
}
