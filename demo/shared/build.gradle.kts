@file:OptIn(ExperimentalKotlinGradlePluginApi::class, ExperimentalWasmDsl::class)

import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKotlinMultiplatformLibrary)
    alias(libs.plugins.jetbrainsCompose)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    jvm()
    jvmToolchain(17)

    js(IR) {
        browser {
            testTask {
                enabled = false
            }
        }
        nodejs()
    }

    wasmJs {
        browser()
    }

    androidLibrary {
        namespace = "com.kitakkun.jetwhale.demo.shared"
        compileSdk = 36
    }

    dependencies {
        implementation(libs.material3)
        implementation(libs.jetbrainsComposeRuntime)
        implementation(projects.jetwhaleAgentRuntime)
        implementation(projects.jetwhaleAgentSdk)
        implementation(projects.jetwhalePlugins.example.agent)
        implementation(projects.jetwhalePlugins.example.protocol)
    }
}
