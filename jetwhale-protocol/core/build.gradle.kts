@file:OptIn(ExperimentalAbiValidation::class)

import org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation

plugins {
    alias(libs.plugins.multiplatform)
    alias(libs.plugins.serialization)
    alias(libs.plugins.publish)
}

kotlin {
    android {
        namespace = "com.kitakkun.jetwhale.protocol.core"
    }

    explicitApi()

    // Empty on purpose: configuring the extension is what turns ABI validation on for this module.
    abiValidation {
    }

    sourceSets.commonMain.dependencies {
        api(projects.jetwhaleAnnotations)
        api(libs.kotlinxCoroutinesCore)
    }

    sourceSets.commonTest.dependencies {
        implementation(libs.kotlinTest)
    }
}

jetwhalePublish {
    artifactId = "jetwhale-protocol-core"
    name = "Jetwhale Protocol Core"
    description = "Core definitions for Jetwhale Protocol"
}
