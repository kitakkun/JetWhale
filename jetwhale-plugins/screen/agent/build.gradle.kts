@file:OptIn(ExperimentalWasmDsl::class, ExperimentalAbiValidation::class, ExperimentalKotlinGradlePluginApi::class)

import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKotlinMultiplatformLibrary)
    alias(libs.plugins.jetbrainsCompose)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.publish)
}

// Distinct group so these plugin modules don't share coordinates with the other plugins' modules
// (which also have leaf names protocol/agent/host) and get substituted during resolution.
group = "com.kitakkun.jetwhale.plugins.screen"

// The targets an app built with Compose Multiplatform runs on, so an app can register the plugin
// from common code. Only Android captures so far; the others report the stream as unsupported.
kotlin {
    abiValidation {
    }

    jvm()
    jvmToolchain(17)

    js {
        browser()
        nodejs()
    }
    wasmJs {
        browser()
    }

    iosArm64()
    iosSimulatorArm64()

    android {
        namespace = "com.kitakkun.jetwhale.plugins.screen.agent"
        compileSdk = 37
        minSdk = 23
    }

    applyDefaultHierarchyTemplate {
        common {
            group("unsupported") {
                withJvm()
                withJs()
                withWasmJs()
                withIos()
            }
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(projects.jetwhalePlugins.screen.protocol)
            api(projects.jetwhaleAgentSdk)
            // Modifier.maskedInScreenStream is public API.
            api(libs.jetbrainsComposeUi)
            implementation(libs.kotlinxCoroutinesCore)
        }
        commonTest.dependencies {
            implementation(libs.kotlinTest)
        }
    }
}

jetwhalePublish {
    artifactId = "jetwhale-screen-stream-agent"
    name = "JetWhale Screen Stream Agent"
    description = "Agent plugin that streams an app's own windows to the JetWhale host."
}
