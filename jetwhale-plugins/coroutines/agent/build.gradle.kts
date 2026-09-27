@file:OptIn(ExperimentalAbiValidation::class)

import org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation

plugins {
    alias(libs.plugins.multiplatform)
    alias(libs.plugins.publish)
}

// Distinct group so this module's coordinates don't collide with the other plugins' `agent`.
group = "com.kitakkun.jetwhale.plugins.coroutines"

kotlin {
    abiValidation {
    }

    android.namespace = "com.kitakkun.jetwhale.plugins.coroutines.agent"
}

dependencies {
    commonMainApi(projects.jetwhalePlugins.coroutines.protocol)
    commonMainApi(projects.jetwhaleAgentSdk)
    // CoroutineScope, Job, CoroutineDispatcher and Flow are in this module's public API.
    commonMainApi(libs.kotlinxCoroutinesCore)
    // The dump reads DebugProbes only when the app has put kotlinx-coroutines-debug on its classpath
    // and installed them; the agent must not bring the library (and its bytecode agent) along.
    "jvmMainCompileOnly"(libs.kotlinxCoroutinesDebug)

    commonTestImplementation(libs.kotlinTest)
    commonTestImplementation(libs.kotlinxCoroutinesTest)
    "jvmTestImplementation"(libs.kotlinxCoroutinesDebug)
}

jetwhalePublish {
    artifactId = "jetwhale-coroutine-inspector-agent"
    name = "JetWhale Coroutine Inspector Agent"
    description = "Agent plugin that shows an app's coroutines, dispatchers and flows in the JetWhale host."
}
