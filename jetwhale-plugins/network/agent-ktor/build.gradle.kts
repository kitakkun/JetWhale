@file:OptIn(ExperimentalWasmDsl::class)

import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.multiplatform)
    alias(libs.plugins.publish)
}

group = "com.kitakkun.jetwhale.plugins.network"

kotlin {
    android.namespace = "com.kitakkun.jetwhale.plugins.network.agent.ktor"
}

dependencies {
    commonMainApi(projects.jetwhalePlugins.network.agent)
    commonMainApi(libs.ktorClientCore)
    commonMainImplementation(libs.kotlinxCoroutinesCore)
}

jetwhalePublish {
    artifactId = "jetwhale-network-inspector-agent-ktor"
    name = "JetWhale Network Inspector Agent (Ktor)"
    description = "Ktor client adapter that wires the JetWhale Network Inspector into an HttpClient."
}
