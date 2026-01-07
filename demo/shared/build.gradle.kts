@file:OptIn(ExperimentalKotlinGradlePluginApi::class)

import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKotlinMultiplatformLibrary)
    alias(libs.plugins.jetbrainsCompose)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    jvm()
    jvmToolchain(17)

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
