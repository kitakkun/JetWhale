@file:OptIn(ExperimentalWasmDsl::class, ExperimentalAbiValidation::class)

import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKotlinMultiplatformLibrary)
    alias(libs.plugins.publish)
}

// Gradle takes projects with the same group and name for one module and substitutes one for the
// other; every plugin has an `agent` module, so each plugin has its own group.
group = "com.kitakkun.jetwhale.plugins.permissions"

kotlin {
    abiValidation()

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
