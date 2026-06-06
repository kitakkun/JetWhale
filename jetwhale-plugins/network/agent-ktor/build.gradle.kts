@file:OptIn(ExperimentalWasmDsl::class)

import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.multiplatform)
}

group = "com.kitakkun.jetwhale.plugins.network"

kotlin {
    android.namespace = "com.kitakkun.jetwhale.plugins.network.agent.ktor"
}

dependencies {
    commonMainApi(projects.jetwhalePlugins.network.agent)
    commonMainApi(libs.ktorClientCore)
    commonMainImplementation(libs.kotlinxCoroutinesCore)
}
