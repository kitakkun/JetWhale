plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.jetbrainsCompose)
}

kotlin {
    jvmToolchain(17)
}

compose.desktop {
    application {
        mainClass = "com.kitakkun.jetwhale.demo.MainKt"
    }
}

dependencies {
    implementation(projects.demo.shared)
    implementation(compose.desktop.currentOs)
    implementation(projects.jetwhalePlugins.semantics.agent)
    // Lets the Coroutine Inspector's Dump tab show suspension stacks; only the JVM can install the probes.
    implementation(libs.kotlinxCoroutinesDebug)

    implementation(libs.ktorServerNetty)
}
