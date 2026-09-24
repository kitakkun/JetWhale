@file:OptIn(ExperimentalWasmDsl::class, ExperimentalAbiValidation::class)

import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKotlinMultiplatformLibrary)
    alias(libs.plugins.kotlinxSerialization)
    alias(libs.plugins.publish)
}

// Distinct group so these plugin modules don't share coordinates with the other plugins' modules
// (which also have leaf names protocol/agent/host) and get substituted during resolution.
group = "com.kitakkun.jetwhale.plugins.actions"

// The same targets as the storage agent: every platform an app built with Compose Multiplatform
// runs on, plus macOS. Linux and mingw are left out with it.
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
        namespace = "com.kitakkun.jetwhale.plugins.actions.agent"
        compileSdk = 37
        minSdk = 23
    }

    sourceSets {
        commonMain.dependencies {
            api(projects.jetwhalePlugins.actions.protocol)
            api(projects.jetwhaleAgentSdk)
            implementation(libs.kotlinxCoroutinesCore)
        }
        commonTest.dependencies {
            implementation(libs.kotlinTest)
            implementation(libs.kotlinxCoroutinesTest)
        }
    }
}

jetwhalePublish {
    artifactId = "jetwhale-debug-actions-agent"
    name = "JetWhale Debug Actions Agent"
    description = "Agent plugin that lets an app register debug actions the JetWhale host and AI agents can run."
}
