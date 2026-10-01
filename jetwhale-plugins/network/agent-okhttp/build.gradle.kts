@file:OptIn(ExperimentalAbiValidation::class)

import org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation

plugins {
    alias(libs.plugins.jvm)
    alias(libs.plugins.publish)
}

kotlin {
    abiValidation {
    }
}

group = "com.kitakkun.jetwhale.plugins.network"

dependencies {
    api(projects.jetwhalePlugins.network.agent)
    api(libs.okhttp)

    testImplementation(libs.kotlinTest)
    testImplementation(libs.okhttpMockwebserver)
    testImplementation(libs.kotlinxCoroutinesCore)
    testImplementation(libs.kotlinxSerializationJson)
}

jetwhalePublish {
    artifactId = "jetwhale-network-inspector-agent-okhttp"
    name = "JetWhale Network Inspector Agent (OkHttp)"
    description = "OkHttp interceptor that wires the JetWhale Network Inspector into an OkHttpClient."
}
