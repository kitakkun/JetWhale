@file:OptIn(ExperimentalAbiValidation::class)

import com.kitakkun.kotrail.gradle.KotrailExtension
import org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation

plugins {
    alias(libs.plugins.jvm)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.jetbrainsCompose)
    alias(libs.plugins.jetwhalePlugin)
    alias(libs.plugins.jetwhaleHostLaunch)
    alias(libs.plugins.publish)
}

kotlin {
    abiValidation {
    }

    // A separate compilation keeps the previews out of the plugin jar, the publication and the ABI
    // dump; associateWith lets them use main's internal declarations.
    target.compilations.create("preview") {
        associateWith(target.compilations.getByName("main"))
    }
}

// Distinct group: Gradle resolves projects with the same group and name as one module, and every
// plugin has a `host` module.
group = "com.kitakkun.jetwhale.plugins.coroutines"

jetwhalePlugin {
    pluginArchiveName.set("jetwhale-coroutine-inspector")
}

dependencies {
    compileOnly(projects.jetwhaleHostSdk)
    compileOnly(projects.jetwhaleHostUi)
    compileOnly(compose.desktop.currentOs)
    compileOnly(libs.material3)
    compileOnly(libs.kotlinxSerializationJson)
    api(projects.jetwhalePlugins.coroutines.protocol)
    "previewImplementation"(projects.jetwhaleHostUi)
    "previewImplementation"(compose.desktop.currentOs)
    "previewImplementation"(libs.jetbrainsComposePreview)
    "previewImplementation"(libs.kotlinxSerializationJson)
    testImplementation(projects.jetwhaleHostSdk)
    testImplementation(projects.jetwhaleHostUi)
    testImplementation(libs.kotlinTest)
    testImplementation(libs.kotlinxSerializationJson)
    testImplementation(compose.desktop.currentOs)
    testImplementation(libs.material3)
}

tasks.named("check") {
    dependsOn("compilePreviewKotlin")
}

configure<KotrailExtension> {
    compilation("main") { configFile = file("kotrail-main.yaml") }
    compilation("preview") { configFile = file("kotrail-preview.yaml") }
}

jetwhalePublish {
    artifactId = "jetwhale-coroutine-inspector"
    name = "JetWhale Coroutine Inspector"
    description = "JetWhale host plugin for inspecting an app's coroutines, dispatchers and flows."
}
