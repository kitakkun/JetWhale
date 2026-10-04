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

// Gradle takes projects with the same group and name for one module and substitutes one for the
// other, and every plugin has a `host` module, so each plugin has its own group.
group = "com.kitakkun.jetwhale.plugins.permissions"

jetwhalePlugin {
    pluginArchiveName.set("jetwhale-permissions-inspector")
}

dependencies {
    compileOnly(projects.jetwhaleHostSdk)
    compileOnly(projects.jetwhaleHostUi)
    compileOnly(compose.desktop.currentOs)
    compileOnly(libs.material3)
    compileOnly(libs.kotlinxSerializationJson)
    api(projects.jetwhalePlugins.permissions.protocol)
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
    artifactId = "jetwhale-permissions-inspector"
    name = "JetWhale Permissions"
    description = "JetWhale host plugin for inspecting and requesting an app's permissions."
}
