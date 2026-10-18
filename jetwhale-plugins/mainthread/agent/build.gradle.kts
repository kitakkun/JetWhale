@file:OptIn(ExperimentalWasmDsl::class, ExperimentalAbiValidation::class, ExperimentalKotlinGradlePluginApi::class)

import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKotlinMultiplatformLibrary)
    alias(libs.plugins.publish)
}

// Gradle substitutes projects that share a group and name for each other, and every plugin has
// protocol, agent and host modules.
group = "com.kitakkun.jetwhale.plugins.mainthread"

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
    macosArm64()

    android {
        namespace = "com.kitakkun.jetwhale.plugins.mainthread.agent"
        compileSdk = 37
        minSdk = 23
    }

    applyDefaultHierarchyTemplate {
        common {
            group("jvmCommon") {
                withJvm()
                // withAndroidTarget() does not match the Android KMP library target, so it is
                // matched by platform type.
                withCompilations { it.platformType == KotlinPlatformType.androidJvm }
            }
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(projects.jetwhalePlugins.mainthread.protocol)
            api(projects.jetwhaleAgentSdk)
        }
        commonTest.dependencies {
            implementation(libs.kotlinTest)
        }
    }
}

jetwhalePublish {
    artifactId = "jetwhale-main-thread-monitor-agent"
    name = "JetWhale Main Thread Monitor Agent"
    description = "Agent plugin that reports what blocks an app's main thread to the JetWhale host."
}
