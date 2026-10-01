@file:OptIn(ExperimentalAbiValidation::class)

import org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation

plugins {
    alias(libs.plugins.multiplatform)
    alias(libs.plugins.publish)
}

kotlin {
    explicitApi()

    abiValidation {
    }

    android.namespace = "com.kitakkun.jetwhale.annotations"

    sourceSets.commonMain.dependencies {
        api(libs.kotlinxSerializationCore)
    }
}

jetwhalePublish {
    artifactId = "jetwhale-annotations"
    name = "JetWhale Annotations"
    description = "Annotations for JetWhale"
}
