@file:OptIn(ExperimentalAbiValidation::class)

import org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.jetbrainsCompose)
    alias(libs.plugins.publish)
    alias(libs.plugins.kotlinxSerialization)
}

kotlin {
    jvmToolchain(17)
    explicitApi()

    abiValidation {
    }
}

dependencies {
    implementation(libs.jetbrainsComposeRuntime)
    api(libs.kotlinxSerializationJson)
    api(libs.kotlinxCoroutinesCore)
    api(projects.jetwhaleProtocol.core)
    testImplementation(libs.kotlinTest)
}

jetwhalePublish {
    artifactId = "jetwhale-host-sdk"
    name = "JetWhale Host SDK"
    description = "SDK for developing JetWhale Host plugins"
}
