plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.jetbrainsCompose)
    alias(libs.plugins.metro)
}

kotlin {
    // Matches the host app: this jar and its whole runtime classpath run inside the IDE's JVM, but
    // in their own classloader, so they can be 21 like the app rather than what the IDE was built with.
    jvmToolchain(21)
}

dependencies {
    implementation(projects.jetwhaleHost.app)
    implementation(projects.jetwhaleHost.core.architecture)
    implementation(projects.jetwhaleHost.core.data)
    implementation(projects.jetwhaleHost.core.model)
    implementation(projects.jetwhaleHost.core.ui)
    implementation(projects.jetwhaleHostUi)
    implementation(compose.desktop.currentOs)
}
