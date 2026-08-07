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

// Distinct group so these plugin modules don't share coordinates with the `example` / `network`
// plugin modules (which also have leaf names protocol/agent/host) and get substituted during
// resolution.
group = "com.kitakkun.jetwhale.plugins.nav3"

kotlin {
    abiValidation {
    }

    android.namespace = "com.kitakkun.jetwhale.plugins.nav3.agent"
}

dependencies {
    commonMainApi(projects.jetwhalePlugins.nav3.protocol)
    commonMainApi(projects.jetwhaleAgentSdk)
    // NavKey / NavBackStack are part of this module's public API. AndroidX publishes the Navigation 3
    // runtime as a multiplatform artifact, so the agent compiles for every target the app may run on.
    commonMainApi(libs.navigation3Runtime)
    // snapshotFlow: a NavBackStack is a Compose snapshot list, so observing it is a Compose concern
    // even though this module renders nothing.
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
