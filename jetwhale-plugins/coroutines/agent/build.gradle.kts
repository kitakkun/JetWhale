@file:OptIn(ExperimentalAbiValidation::class)

import org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation

plugins {
    alias(libs.plugins.multiplatform)
    alias(libs.plugins.publish)
}

// Distinct group: Gradle resolves projects with the same group and name as one module, and every
// plugin has an `agent` module.
group = "com.kitakkun.jetwhale.plugins.coroutines"

kotlin {
    abiValidation {
    }

    android.namespace = "com.kitakkun.jetwhale.plugins.coroutines.agent"
}

dependencies {
    commonMainApi(projects.jetwhalePlugins.coroutines.protocol)
    commonMainApi(projects.jetwhaleAgentSdk)
    commonMainApi(libs.kotlinxCoroutinesCore)
    // The app adds kotlinx-coroutines-debug only if it wants stacks: the library brings ByteBuddy
    // and a JVM agent along.
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
