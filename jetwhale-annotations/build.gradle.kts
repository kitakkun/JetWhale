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
        // Exposed in public API: McpDescription is a @SerialInfo annotation, and consumers read it
        // back off a SerialDescriptor.
        api(libs.kotlinxSerializationCore)
    }
}

jetwhalePublish {
    artifactId = "jetwhale-annotations"
    name = "JetWhale Annotations"
    description = "Annotations for JetWhale"
}
