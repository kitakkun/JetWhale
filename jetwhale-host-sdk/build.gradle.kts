@file:OptIn(ExperimentalAbiValidation::class)

import org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.jetbrainsCompose)
    alias(libs.plugins.publish)
    alias(libs.plugins.kotlinxSerialization)
}

kotlin {
    jvmToolchain(17)
    explicitApi()

    abiValidation {
    }
}

dependencies {
    implementation(libs.jetbrainsComposeRuntime)
    // Exposed in public API: the MCP parameter DSL takes KSerializer and hands back JsonObject.
    api(libs.kotlinxSerializationJson)
    // Exposed in public API: JetWhalePluginStorage returns Flow and rememberPersistent uses coroutines.
    api(libs.kotlinxCoroutinesCore)
    api(projects.jetwhaleProtocol.core)

    // Experimental web-based host plugins: JetWhaleWebView embeds a Chromium browser (KCEF) into the
    // plugin UI via SwingPanel, so the SDK needs Compose Foundation/UI (desktop) and the KCEF runtime.
    implementation(libs.jetbrainsComposeFoundation)
    implementation(libs.jetbrainsComposeUi)
    implementation(libs.kcef)
    // KCEF drives its browser callbacks on the AWT/Swing thread via the Swing coroutine dispatcher.
    implementation(libs.kotlinxCoroutinesSwing)

    testImplementation(libs.kotlinTest)
}

jetwhalePublish {
    artifactId = "jetwhale-host-sdk"
    name = "JetWhale Host SDK"
    description = "SDK for developing JetWhale Host plugins"
}
