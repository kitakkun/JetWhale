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

// Distinct group so these plugin modules don't share coordinates with the other plugins' modules
// (which also have leaf names protocol/agent/host) and get substituted during resolution.
group = "com.kitakkun.jetwhale.plugins.mainthread"

// The same targets as the other agents. Android and the JVM share their thread and stack code;
// Apple and web targets report that they cannot watch their main thread yet.
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
        namespace = "com.kitakkun.jetwhale.plugins.mainthread.agent"
        compileSdk = 37
        minSdk = 23
    }

    // JVM and Android share java.lang.Thread stacks and locks. The Android library target is not
    // matched by withAndroidTarget(), so it is picked by platform type instead.
    applyDefaultHierarchyTemplate {
        common {
            group("jvmCommon") {
                withJvm()
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
