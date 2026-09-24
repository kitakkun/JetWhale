@file:OptIn(ExperimentalWasmDsl::class, ExperimentalAbiValidation::class)

import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKotlinMultiplatformLibrary)
    alias(libs.plugins.publish)
}

group = "com.kitakkun.jetwhale.plugins.storage"

// The storage agent's targets, all of which datastore-preferences-core also publishes.
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
        namespace = "com.kitakkun.jetwhale.plugins.storage.agent.datastore"
        compileSdk = 37
        minSdk = 23
    }

    sourceSets {
        commonMain.dependencies {
            api(projects.jetwhalePlugins.storage.agent)
            api(libs.androidxDatastorePreferencesCore)
            implementation(libs.kotlinxCoroutinesCore)
        }
        jvmTest.dependencies {
            implementation(libs.kotlinTest)
            implementation(libs.kotlinxCoroutinesTest)
        }
    }
}

jetwhalePublish {
    artifactId = "jetwhale-storage-inspector-agent-datastore"
    name = "JetWhale Storage Inspector Agent (DataStore)"
    description = "Jetpack DataStore adapter that shows an app's Preferences DataStores in the JetWhale Storage Inspector."
}
