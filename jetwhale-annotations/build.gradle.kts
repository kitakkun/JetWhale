@file:OptIn(ExperimentalAbiValidation::class)

import org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation

plugins {
    alias(libs.plugins.multiplatform)
    alias(libs.plugins.publish)
}

kotlin {
    explicitApi()

    abiValidation {
        enabled.set(true)
    }

    androidLibrary.namespace = "com.kitakkun.jetwhale.annotations"
}

jetwhalePublish {
    artifactId = "jetwhale-annotations"
    name = "JetWhale Annotations"
    description = "Annotations for JetWhale"
}
