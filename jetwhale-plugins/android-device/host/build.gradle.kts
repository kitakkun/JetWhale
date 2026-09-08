@file:OptIn(ExperimentalAbiValidation::class)

import org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation

plugins {
    alias(libs.plugins.jvm)
    // Provides packagePlugin / installPlugin / stageDevPlugin / runJetWhale / runJetWhaleHot (published).
    alias(libs.plugins.jetwhalePlugin)
    // In-repo only: adds runJetWhaleLocal, which launches the local :jetwhale-host:app project.
    alias(libs.plugins.jetwhaleHostLaunch)
    alias(libs.plugins.publish)
}

kotlin {
    abiValidation {
    }
}

// Distinct group so this module's coordinates don't collide with the other plugins' `host`.
group = "com.kitakkun.jetwhale.plugins.androiddevice"

jetwhalePlugin {
    // Unique name so the packaged plugin jar doesn't collide with the other plugin modules (also
    // project name "host") in ~/.jetwhale/plugins/ or the dev staging directory.
    pluginArchiveName.set("jetwhale-android-device")
}

dependencies {
    // Provided by the host at runtime, so compileOnly: these must be neither bundled into the
    // plugin jar nor listed in its dependency manifest. This plugin is headless, so it needs no
    // Compose artifacts at all.
    compileOnly(projects.jetwhaleHostSdk)
    compileOnly(libs.kotlinxSerializationJson)

    testImplementation(projects.jetwhaleHostSdk)
    testImplementation(libs.kotlinTest)
    testImplementation(libs.kotlinxSerializationJson)
    testImplementation(libs.kotlinxCoroutinesCore)
}

// The jetwhalePlugin convention publishes the `packageMavenPlugin` jar (the module's classes plus a
// manifest of its runtime dependencies) as the main artifact; the host's "Install from Maven"
// feature downloads it and fetches the listed dependencies itself.
jetwhalePublish {
    artifactId = "jetwhale-android-device"
    name = "JetWhale Android Device"
    description = "JetWhale host plugin that drives a connected Android device over adb and exposes it over MCP."
}
