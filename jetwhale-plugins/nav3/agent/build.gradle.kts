@file:OptIn(ExperimentalWasmDsl::class, ExperimentalAbiValidation::class)

import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation

plugins {
    alias(libs.plugins.multiplatform)
    alias(libs.plugins.kotlinxSerialization)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.jetbrainsCompose)
    alias(libs.plugins.publish)
}

// Distinct group so these plugin modules don't share coordinates with the other plugins' modules
// (which also have leaf names protocol/agent/host) and get substituted during resolution.
group = "com.kitakkun.jetwhale.plugins.nav3"

kotlin {
    abiValidation()

    android.namespace = "com.kitakkun.jetwhale.plugins.nav3.agent"
}

dependencies {
    commonMainApi(projects.jetwhalePlugins.nav3.protocol)
    commonMainApi(projects.jetwhaleAgentSdk)
    commonMainApi(libs.navigation3Runtime)
    commonMainApi(libs.jetbrainsComposeRuntime)
    commonMainImplementation(libs.kotlinxCoroutinesCore)
    commonMainImplementation(libs.kotlinxSerializationJson)

    commonTestImplementation(libs.kotlinTest)
}

jetwhalePublish {
    artifactId = "jetwhale-nav3-agent"
    name = "JetWhale Nav3 Agent"
    description = "Agent plugin that exposes an app's Navigation 3 back stack to the JetWhale host."
}
