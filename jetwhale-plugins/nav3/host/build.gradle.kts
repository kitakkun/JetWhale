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
    abiValidation()

    target.compilations.create("preview") {
        associateWith(target.compilations.getByName("main"))
    }
}

// Distinct group so this module's coordinates don't collide with the other plugins' `host` modules.
group = "com.kitakkun.jetwhale.plugins.nav3"

jetwhalePlugin {
    pluginArchiveName.set("jetwhale-nav3-navigator")
}

dependencies {
    compileOnly(projects.jetwhaleHostSdk)
    compileOnly(projects.jetwhaleHostUi)
    compileOnly(compose.desktop.currentOs)
    compileOnly(libs.material3)
    compileOnly(libs.kotlinxSerializationJson)
    api(projects.jetwhalePlugins.nav3.protocol)
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
    artifactId = "jetwhale-nav3-navigator"
    name = "JetWhale Nav3 Navigator"
    description = "JetWhale host plugin for inspecting and driving an app's Navigation 3 back stack."
}
