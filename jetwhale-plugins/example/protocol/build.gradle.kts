@file:OptIn(ExperimentalWasmDsl::class)

import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.multiplatform)
    alias(libs.plugins.kotlinxSerialization)
}

kotlin {
    androidLibrary.namespace = "com.kitakkun.jetwhale.plugins.example.protocol"
}

dependencies {
    commonMainImplementation(libs.kotlinxSerializationJson)
}
