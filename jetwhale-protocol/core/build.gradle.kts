@file:OptIn(ExperimentalAbiValidation::class)

import org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation

plugins {
    alias(libs.plugins.multiplatform)
    alias(libs.plugins.serialization)
    alias(libs.plugins.publish)
}

kotlin {
    androidLibrary {
        namespace = "com.kitakkun.jetwhale.protocol.core"
    }

    explicitApi()

    abiValidation {
        enabled.set(true)
    }

    sourceSets.commonMain.dependencies {
        api(projects.jetwhaleAnnotations)
        implementation(libs.kotlinTest)
    }
}

jetwhalePublish {
    artifactId = "jetwhale-protocol-core"
    name = "Jetwhale Protocol Core"
    description = "Core definitions for Jetwhale Protocol"
}
