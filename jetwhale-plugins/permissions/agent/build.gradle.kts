@file:OptIn(ExperimentalWasmDsl::class, ExperimentalAbiValidation::class)

import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKotlinMultiplatformLibrary)
    alias(libs.plugins.publish)
}

// Distinct group so these plugin modules don't share coordinates with the other plugins' modules
// (which also have leaf names protocol/agent/host) and get substituted during resolution.
group = "com.kitakkun.jetwhale.plugins.permissions"

// Targets are the platforms an app ships on. Desktop and the web have no permission model the
// agent can read; they are kept so a multiplatform app can register the plugin in common code,
// and report themselves as unsupported.
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

    android {
        namespace = "com.kitakkun.jetwhale.plugins.permissions.agent"
        compileSdk = 37
        minSdk = 23
    }

    sourceSets {
        commonMain.dependencies {
            api(projects.jetwhalePlugins.permissions.protocol)
            api(projects.jetwhaleAgentSdk)
            implementation(libs.kotlinxCoroutinesCore)
        }
        commonTest.dependencies {
            implementation(libs.kotlinTest)
        }
    }
}

jetwhalePublish {
    artifactId = "jetwhale-permissions-agent"
    name = "JetWhale Permissions Agent"
    description = "Agent plugin that reports an app's permissions to the JetWhale host and requests them from it."
}
