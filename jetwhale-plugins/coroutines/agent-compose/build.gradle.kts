@file:OptIn(ExperimentalAbiValidation::class)

import org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation

plugins {
    alias(libs.plugins.multiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.jetbrainsCompose)
    alias(libs.plugins.publish)
}

group = "com.kitakkun.jetwhale.plugins.coroutines"

kotlin {
    abiValidation {
    }

    android.namespace = "com.kitakkun.jetwhale.plugins.coroutines.agent.compose"
}

dependencies {
    commonMainApi(projects.jetwhalePlugins.coroutines.agent)
    // A separate module so that the core agent stays free of the Compose runtime.
    commonMainApi(libs.jetbrainsComposeRuntime)
    commonMainImplementation(libs.kotlinxCoroutinesCore)

    "jvmTestImplementation"(libs.kotlinTest)
    "jvmTestImplementation"(libs.kotlinxCoroutinesTest)
}

jetwhalePublish {
    artifactId = "jetwhale-coroutine-inspector-agent-compose"
    name = "JetWhale Coroutine Inspector Agent (Compose)"
    description = "Shows the coroutines an app's Composables start (LaunchedEffect, rememberCoroutineScope, produceState) in the JetWhale Coroutine Inspector."
}
