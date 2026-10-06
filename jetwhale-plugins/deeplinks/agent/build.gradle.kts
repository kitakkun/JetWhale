@file:OptIn(ExperimentalWasmDsl::class, ExperimentalAbiValidation::class)

import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKotlinMultiplatformLibrary)
    alias(libs.plugins.publish)
}

// Distinct group: the other plugins also have an `agent` module, and Gradle substitutes projects
// that share coordinates during resolution.
group = "com.kitakkun.jetwhale.plugins.deeplinks"

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
        namespace = "com.kitakkun.jetwhale.plugins.deeplinks.agent"
        compileSdk = 37
        minSdk = 23
        withHostTest {}
    }

    sourceSets {
        commonMain.dependencies {
            api(projects.jetwhalePlugins.deeplinks.protocol)
            api(projects.jetwhaleAgentSdk)
            implementation(libs.kotlinxCoroutinesCore)
        }
        commonTest.dependencies {
            implementation(libs.kotlinTest)
        }
    }
}

jetwhalePublish {
    artifactId = "jetwhale-deep-links-agent"
    name = "JetWhale Deep Links Agent"
    description = "Agent plugin that lists the deep links an app declares and opens them on the JetWhale host's request."
}
