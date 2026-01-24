@file:OptIn(ExperimentalWasmDsl::class)

import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.multiplatform)
    alias(libs.plugins.kotlinxSerialization)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.jetbrainsCompose)
    alias(libs.plugins.androidKotlinMultiplatformLibrary)
}

kotlin {
    androidLibrary {
        namespace = "com.kitakkun.jetwhale.plugins.example"
        compileSdk = 36
    }
}

dependencies {
    commonMainApi(projects.jetwhalePlugins.example.protocol)

    commonMainApi(libs.jetbrainsComposeRuntime)
    commonMainApi(projects.jetwhaleAgentSdk)
    commonMainApi(libs.kotlinxSerializationJson)
    commonMainApi(libs.kotlinxCoroutinesCore)
}
