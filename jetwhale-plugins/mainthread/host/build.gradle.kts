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

// Gradle substitutes projects that share a group and name for each other, and every plugin has
// protocol, agent and host modules.
group = "com.kitakkun.jetwhale.plugins.mainthread"

jetwhalePlugin {
    pluginArchiveName.set("jetwhale-main-thread-monitor")
}

dependencies {
    compileOnly(projects.jetwhaleHostSdk)
    compileOnly(projects.jetwhaleHostUi)
    compileOnly(compose.desktop.currentOs)
    compileOnly(libs.material3)
    compileOnly(libs.kotlinxSerializationJson)
    compileOnly(libs.androidxAnnotation)
    api(projects.jetwhalePlugins.mainthread.protocol)
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
    artifactId = "jetwhale-main-thread-monitor"
    name = "JetWhale Main Thread Monitor"
    description = "JetWhale host plugin that shows what blocks an app's main thread."
}
