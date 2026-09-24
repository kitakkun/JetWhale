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
group = "com.kitakkun.jetwhale.plugins.storage"

// Targets are the ones with an app sandbox worth browsing or a key-value store to read. Linux and
// mingw are left out: an app there has neither a sandbox nor a platform preferences store, so the
// plugin would have nothing to show by default.
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
        namespace = "com.kitakkun.jetwhale.plugins.storage.agent"
        compileSdk = 37
        minSdk = 23
    }

    // JVM and Android both reach files through java.io.File. The Android library target is not
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
            api(projects.jetwhalePlugins.storage.protocol)
            api(projects.jetwhaleAgentSdk)
        }
        commonTest.dependencies {
            implementation(libs.kotlinTest)
        }
    }
}

jetwhalePublish {
    artifactId = "jetwhale-storage-inspector-agent"
    name = "JetWhale Storage Inspector Agent"
    description = "Agent plugin that lets the JetWhale host browse an app's files and key-value stores."
}
