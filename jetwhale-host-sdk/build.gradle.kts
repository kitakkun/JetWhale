@file:OptIn(ExperimentalAbiValidation::class)

import org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.jetbrainsCompose)
    alias(libs.plugins.publish)
}

kotlin {
    jvmToolchain(17)
    explicitApi()

    abiValidation {
        enabled.set(true)
    }
}

dependencies {
    implementation(compose.runtime)
    implementation(libs.kotlinxSerializationJson)
    api(projects.jetwhaleProtocol.core)
    api(projects.jetwhaleProtocol.host)
}

jetwhalePublish {
    artifactId = "jetwhale-host-sdk"
    name = "JetWhale Host Sdk"
}
