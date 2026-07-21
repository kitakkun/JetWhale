@file:OptIn(ExperimentalAbiValidation::class)

import org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation

plugins {
    alias(libs.plugins.multiplatform)
    alias(libs.plugins.serialization)
    alias(libs.plugins.androidKotlinMultiplatformLibrary)
    alias(libs.plugins.publish)
}

kotlin {
    explicitApi()

    abiValidation {
    }

    android {
        namespace = "com.kitakkun.jetwhale.agent.runtime"
        compileSdk = 37
    }

    sourceSets {
        commonMain.dependencies {
            api(projects.jetwhaleProtocol.core)
            api(projects.jetwhaleAgentSdk)

            implementation(libs.bundles.ktorClient)
            implementation(libs.kotlinxCoroutinesCore)
            implementation(libs.kermit)
        }

        // The websocket client test spins up a real Ktor server (ktor-server-test-host), which
        // is JVM-only and pulls ktor-network; keeping it here avoids leaking node:net into the
        // JS browser test bundle.
        jvmMain.dependencies {
            // JVM mDNS/DNS-SD browsing for zero-config host discovery.
            implementation(libs.jmdns)
        }

        jvmTest.dependencies {
            implementation(projects.testAnnotations)
            implementation(libs.kotlinTest)
            implementation(libs.ktorServerTestHost)
            implementation(libs.ktorServerWebSockets)
        }

        webMain.dependencies {
            implementation(libs.ktorClientJs)
        }

        appleMain.dependencies {
            implementation(libs.ktorClientDarwin)
        }

        mingwMain.dependencies {
            implementation(libs.ktorClientWinHttp)
        }

        linuxMain.dependencies {
            implementation(libs.ktorClientCurl)
        }
    }
}

jetwhalePublish {
    artifactId = "jetwhale-agent-runtime"
    name = "JetWhale Agent Runtime"
    description = "Runtime library for JetWhale Agent"
}
