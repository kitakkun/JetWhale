@file:OptIn(ExperimentalWasmDsl::class)

import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.multiplatform)
    alias(libs.plugins.kotlinxSerialization)
}

// Distinct group so this module's coordinates don't collide with `example:protocol`.
group = "com.kitakkun.jetwhale.plugins.network"

kotlin {
    android.namespace = "com.kitakkun.jetwhale.plugins.network.protocol"
}

dependencies {
    commonMainImplementation(libs.kotlinxSerializationJson)
}
