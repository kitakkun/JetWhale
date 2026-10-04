@file:OptIn(ExperimentalWasmDsl::class, ExperimentalAbiValidation::class)

import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKotlinMultiplatformLibrary)
    alias(libs.plugins.publish)
}

// Gradle treats projects with the same group and name as one module during resolution, and every
// plugin has protocol, agent and host projects.
group = "com.kitakkun.jetwhale.plugins.background"

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
        namespace = "com.kitakkun.jetwhale.plugins.background.agent"
        compileSdk = 37
        minSdk = 23

        withHostTest {
            isIncludeAndroidResources = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(projects.jetwhalePlugins.background.protocol)
            api(projects.jetwhaleAgentSdk)
            api(libs.kotlinxCoroutinesCore)
        }
        commonTest.dependencies {
            implementation(libs.kotlinTest)
            implementation(libs.kotlinxCoroutinesTest)
        }
        getByName("androidHostTest").dependencies {
            implementation(libs.robolectric)
            implementation(libs.androidxTestCore)
            implementation(libs.junit)
        }
    }
}

tasks.withType<Test>().configureEach {
    // Robolectric loads a whole Android framework into the test JVM.
    maxHeapSize = "2g"
}

jetwhalePublish {
    artifactId = "jetwhale-background-work-agent"
    name = "JetWhale Background Work Agent"
    description = "Agent plugin that reports an app's scheduled background work to the JetWhale host and lets it cancel or run that work."
}
