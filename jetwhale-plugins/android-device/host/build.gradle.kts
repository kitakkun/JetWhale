@file:OptIn(ExperimentalAbiValidation::class)

import org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation

plugins {
    alias(libs.plugins.jvm)
    alias(libs.plugins.jetwhalePlugin)
    alias(libs.plugins.jetwhaleHostLaunch)
    alias(libs.plugins.publish)
}

kotlin {
    abiValidation()
}

// Distinct group so this module's coordinates don't collide with the other plugins' `host` modules.
group = "com.kitakkun.jetwhale.plugins.androiddevice"

jetwhalePlugin {
    pluginArchiveName.set("jetwhale-android-device")
}

dependencies {
    compileOnly(projects.jetwhaleHostSdk)
    compileOnly(libs.kotlinxSerializationJson)

    testImplementation(projects.jetwhaleHostSdk)
    testImplementation(libs.kotlinTest)
    testImplementation(libs.kotlinxSerializationJson)
    testImplementation(libs.kotlinxCoroutinesCore)
}

jetwhalePublish {
    artifactId = "jetwhale-android-device"
    name = "JetWhale Android Device"
    description = "JetWhale host plugin that drives a connected Android device over adb and exposes it over MCP."
}
