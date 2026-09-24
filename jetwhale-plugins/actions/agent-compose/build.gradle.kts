@file:OptIn(ExperimentalWasmDsl::class, ExperimentalAbiValidation::class)

import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKotlinMultiplatformLibrary)
    alias(libs.plugins.jetbrainsCompose)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.publish)
}

group = "com.kitakkun.jetwhale.plugins.actions"

// Kept apart from the agent so an app without Compose does not get the Compose runtime; the
// targets are the agent's.
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
        nodejs()
    }

    iosArm64()
    iosSimulatorArm64()
    macosArm64()

    android {
        namespace = "com.kitakkun.jetwhale.plugins.actions.agent.compose"
        compileSdk = 37
        minSdk = 23
    }

    sourceSets {
        commonMain.dependencies {
            api(projects.jetwhalePlugins.actions.agent)
            api(libs.jetbrainsComposeRuntime)
        }
        jvmTest.dependencies {
            implementation(libs.kotlinTest)
            implementation(libs.kotlinxCoroutinesTest)
        }
    }
}

jetwhalePublish {
    artifactId = "jetwhale-debug-actions-agent-compose"
    name = "JetWhale Debug Actions Agent for Compose"
    description = "Registers JetWhale debug actions for as long as a composable is in composition."
}
