@file:OptIn(ExperimentalWasmDsl::class, ExperimentalAbiValidation::class)

import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation
import org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask

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

// Not the `multiplatform` convention's targets: `androidx.compose.ui` is not published for linux or
// mingw, and Compose rejects macOS unless the whole build opts into its experimental macOS support.
kotlin {
    abiValidation()

    jvm()
    jvmToolchain(17)

    js {
        browser()
        nodejs()
    }

    // Browser only: Compose's wasm runtime does not load under Node, so a wasmJs Node test fails
    // before reaching any assertion.
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
            api(libs.jetbrainsComposeUi)
            implementation(libs.kotlinxCoroutinesCore)
        }
        androidMain.dependencies {
            compileOnly(libs.androidxRecyclerView)
        }
        commonTest.dependencies {
            implementation(libs.kotlinTest)
        }
    }
}

// Compose Multiplatform 1.11 ships org.jetbrains.* and androidx.* klibs under the same unique
// names, so the iOS metadata compilation loads each twice and the KLIB loader warns. Which copy
// wins is not up to this module, so the warning must not fail the build.
tasks.withType<KotlinCompilationTask<*>>().matching { it.name == "compileIosMainKotlinMetadata" }.configureEach {
    compilerOptions.allWarningsAsErrors = false
}

jetwhalePublish {
    artifactId = "jetwhale-compose-semantics-inspector-agent"
    name = "JetWhale Compose Semantics Inspector Agent"
    description = "Reads the node tree of a running app — Compose semantics, plus the Android Views around and inside it — for the JetWhale Compose Semantics Inspector."
}
