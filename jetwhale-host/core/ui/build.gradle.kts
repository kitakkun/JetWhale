plugins {
    alias(libs.plugins.jvm)
    alias(libs.plugins.compose)
}

dependencies {
    api(projects.jetwhaleHostUi)
    implementation(projects.jetwhaleHost.core.model)
}
