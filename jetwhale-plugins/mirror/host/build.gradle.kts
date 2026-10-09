@file:OptIn(ExperimentalAbiValidation::class)

import com.kitakkun.kotrail.gradle.KotrailExtension
import org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation

plugins {
    alias(libs.plugins.jvm)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.jetbrainsCompose)
    alias(libs.plugins.kotlinxSerialization)
    alias(libs.plugins.jetwhalePlugin)
    alias(libs.plugins.jetwhaleHostLaunch)
    alias(libs.plugins.publish)
    alias(libs.plugins.docsScreenshots)
}

kotlin {
    abiValidation()

    target.compilations.create("preview") {
        associateWith(target.compilations.getByName("main"))
    }
}

// Distinct group so this module's coordinates don't collide with the other plugins' `host` modules.
group = "com.kitakkun.jetwhale.plugins.mirror"

jetwhalePlugin {
    pluginArchiveName.set("jetwhale-device-mirror")
}

dependencies {
    compileOnly(projects.jetwhaleHostSdk)
    compileOnly(projects.jetwhaleHostUi)
    compileOnly(compose.desktop.currentOs)
    compileOnly(libs.material3)
    compileOnly(libs.kotlinxSerializationJson)
    compileOnly(libs.androidxAnnotation)
    // okhttp speaks the emulator's gRPC screen stream (plain HTTP/2 with prior knowledge), and its
    // two messages are encoded by hand rather than pulling in grpc-java. H.264 is decoded by the
    // ffmpeg installed on the machine.
    implementation(libs.okhttp)
    implementation(projects.jetwhalePlugins.iosXctestRunner.client)
    "previewImplementation"(projects.jetwhaleHostUi)
    "previewImplementation"(compose.desktop.currentOs)
    "previewImplementation"(libs.jetbrainsComposePreview)
    testImplementation(projects.jetwhaleHostSdk)
    testImplementation(projects.jetwhaleHostUi)
    testImplementation(libs.kotlinTest)
    testImplementation(libs.okhttpMockwebserver)
    testImplementation(libs.kotlinxSerializationJson)
    testImplementation(libs.kotlinxCoroutinesTest)
    testImplementation(libs.jetbrainsComposeUiTestJUnit4)
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
    artifactId = "jetwhale-device-mirror"
    name = "JetWhale Device Mirror"
    description = "JetWhale host plugin that mirrors and drives Android devices, iOS simulators and iPhones."
}
