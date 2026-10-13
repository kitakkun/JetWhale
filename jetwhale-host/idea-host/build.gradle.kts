plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.jetbrainsCompose)
    alias(libs.plugins.metro)
}

kotlin {
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
