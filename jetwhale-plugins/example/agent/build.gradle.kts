@file:OptIn(ExperimentalWasmDsl::class)

import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinxSerialization)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.jetbrainsCompose)
    alias(libs.plugins.androidKotlinMultiplatformLibrary)
}

kotlin {
    jvm()

    jvmToolchain(17)

    wasmJs {
        browser()
        nodejs()
    }

    androidLibrary {
        namespace = "com.kitakkun.jetwhale.plugins.example"
        compileSdk = 36
    }
}

dependencies {
    commonMainApi(projects.jetwhalePlugins.example.protocol)

    commonMainCompileOnly(libs.jetbrainsComposeRuntime)
    commonMainCompileOnly(projects.jetwhaleAgentSdk)
    commonMainCompileOnly(libs.kotlinxSerializationJson)
    commonMainCompileOnly(libs.kotlinxCoroutinesCore)
}
