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

// Distinct group so these plugin modules don't share coordinates with the other plugins' modules
// (which also have leaf names protocol/agent/host) and get substituted during resolution.
group = "com.kitakkun.jetwhale.plugins.semantics"

// Targets follow Compose Multiplatform's own rather than the `multiplatform` convention's: reading
// a composition needs `androidx.compose.ui`, which is not published for linux or mingw — and a
// target Compose does not run on has no node tree to read in the first place. macOS is left out
// too: Compose rejects it unless the whole build opts into its experimental macOS support.
kotlin {
    abiValidation {
    }

    jvm()
    jvmToolchain(17)

    js {
        browser()
        nodejs()
    }

    // Browser only: the test bundle now carries Compose, whose wasm runtime does not load under
    // Node, so a wasmJs Node test fails before it reaches any assertion.
    wasmJs {
        browser()
    }

    iosArm64()
    iosSimulatorArm64()

    android {
        namespace = "com.kitakkun.jetwhale.plugins.semantics.agent"
        compileSdk = 37
        minSdk = 23
    }

    sourceSets {
        commonMain.dependencies {
            api(projects.jetwhalePlugins.semantics.protocol)
            api(projects.jetwhaleAgentSdk)
            implementation(libs.jetbrainsComposeUi)
            implementation(libs.kotlinxCoroutinesCore)
        }
        commonTest.dependencies {
            implementation(libs.kotlinTest)
        }
    }
}

jetwhalePublish {
    artifactId = "jetwhale-compose-semantics-inspector-agent"
    name = "JetWhale Compose Semantics Inspector Agent"
    description = "Reads the Compose semantics tree of a running app for the JetWhale Compose Semantics Inspector."
}
