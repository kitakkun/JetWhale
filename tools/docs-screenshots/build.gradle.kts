plugins {
    alias(libs.plugins.jvm)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.jetbrainsCompose)
}

dependencies {
    api(compose.desktop.currentOs)
    api(libs.jetbrainsComposeUiTestJUnit4)
    api(projects.jetwhaleHostSdk)
    api(projects.jetwhaleHostUi)
    implementation(projects.jetwhaleHost.core.ui)
    implementation(projects.jetwhaleHost.core.model)
    implementation(libs.kotlinxSerializationJson)
}
