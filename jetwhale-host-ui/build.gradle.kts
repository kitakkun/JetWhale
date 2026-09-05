@file:OptIn(ExperimentalAbiValidation::class)

import org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.jetbrainsCompose)
    alias(libs.plugins.publish)
}

kotlin {
    jvmToolchain(17)
    explicitApi()

    abiValidation {
    }
}

dependencies {
    // Every one of these is exposed in the public API (composable signatures take Modifier, Color,
    // ColorScheme, ...), and every one is provided by the host at runtime: a plugin consumes this
    // module as compileOnly, exactly like jetwhale-host-sdk.
    api(compose.runtime)
    api(compose.foundation)
    api(compose.ui)
    api(libs.material3)
    api(projects.jetwhaleHostSdk)
    testImplementation(libs.kotlinTest)
    testImplementation(compose.desktop.currentOs)
    testImplementation(libs.jetbrainsComposeUiTestJUnit4)
}

jetwhalePublish {
    artifactId = "jetwhale-host-ui"
    name = "JetWhale Host UI"
    description = "Theme and UI components shared by the JetWhale host and its plugins"
}
