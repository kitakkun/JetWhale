@file:OptIn(ExperimentalWasmDsl::class)

import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.multiplatform)
    alias(libs.plugins.kotlinxSerialization)
    alias(libs.plugins.publish)
}

// Distinct group so these plugin modules don't share coordinates with the `example` plugin
// modules (which also have leaf names protocol/agent/host) and get substituted during resolution.
group = "com.kitakkun.jetwhale.plugins.network"

kotlin {
    android.namespace = "com.kitakkun.jetwhale.plugins.network.agent"
}

dependencies {
    commonMainApi(projects.jetwhalePlugins.network.protocol)
    commonMainApi(projects.jetwhaleAgentSdk)
    commonMainImplementation(libs.kotlinxCoroutinesCore)
    commonMainImplementation(libs.kotlinxSerializationJson)

    commonTestImplementation(libs.kotlinTest)
}

jetwhalePublish {
    artifactId = "jetwhale-network-inspector-agent"
    name = "JetWhale Network Inspector Agent"
    description = "Transport-agnostic agent core for the JetWhale Network Inspector (HTTP capture and mocking)."
}
