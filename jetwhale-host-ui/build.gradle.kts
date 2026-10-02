@file:OptIn(ExperimentalAbiValidation::class)

import com.kitakkun.kotrail.gradle.KotrailExtension
import org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.jetbrainsCompose)
    alias(libs.plugins.publish)
    alias(libs.plugins.roborazzi)
}

kotlin {
    jvmToolchain(17)
    explicitApi()

    abiValidation()

    target.compilations.create("preview") {
        associateWith(target.compilations.getByName("main"))
    }
}

dependencies {
    api(compose.runtime)
    api(compose.foundation)
    api(compose.ui)
    api(projects.jetwhaleHostSdk)
    "previewImplementation"(compose.runtime)
    "previewImplementation"(compose.foundation)
    "previewImplementation"(compose.ui)
    "previewImplementation"(libs.jetbrainsComposePreview)
    testImplementation(libs.kotlinTest)
    testImplementation(compose.desktop.currentOs)
    testImplementation(libs.jetbrainsComposeUiTestJUnit4)
    testImplementation(libs.roborazziComposeDesktop)
}

roborazzi {
    outputDir.set(file("screenshots"))
}

tasks.named("check") {
    dependsOn("compilePreviewKotlin")
}

configure<KotrailExtension> {
    compilation("main") { configFile = file("kotrail-main.yaml") }
    compilation("preview") { configFile = file("kotrail-preview.yaml") }
    test { configFile = file("kotrail-test.yaml") }
}

jetwhalePublish {
    artifactId = "jetwhale-host-ui"
    name = "JetWhale Host UI"
    description = "Theme and UI components shared by the JetWhale host and its plugins"
}
