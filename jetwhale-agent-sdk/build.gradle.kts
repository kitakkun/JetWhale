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
    }

    android.namespace = "com.kitakkun.jetwhale.agent.sdk"
}

dependencies {
    commonMainApi(projects.jetwhaleProtocol.core)
    commonMainImplementation(libs.kotlinxCoroutinesCore)

    commonTestImplementation(libs.kotlinTest)
}

jetwhalePublish {
    artifactId = "jetwhale-agent-sdk"
    name = "JetWhale Agent SDK"
    description = "SDK for developing JetWhale Agent plugins"
}
