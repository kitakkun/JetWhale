@file:OptIn(ExperimentalKotlinGradlePluginApi::class, ExperimentalWasmDsl::class)

import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework

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

    iosX64()
    iosArm64()
    iosSimulatorArm64()

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

    val xcFrameworkName = "shared"
    val xcFramework = XCFramework(xcFrameworkName)

    targets.filterIsInstance<KotlinNativeTarget>()
        .forEach {
            it.binaries {
                framework {
                    isStatic = true
                    xcFramework.add(this)
                }
            }
        }
}
