@file:OptIn(ExperimentalAbiValidation::class)

import org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation

plugins {
    alias(libs.plugins.jvm)
    alias(libs.plugins.serialization)
}

kotlin {
    explicitApi()

    abiValidation {
        enabled.set(true)
    }
}
