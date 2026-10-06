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
    implementation(libs.kotlinxCoroutinesDebug)
    // Provides Dispatchers.Main on the desktop JVM, which the Coroutines tab's main-thread demo
    // runs on.
    implementation(libs.kotlinxCoroutinesSwing)

    implementation(libs.ktorServerNetty)
}
