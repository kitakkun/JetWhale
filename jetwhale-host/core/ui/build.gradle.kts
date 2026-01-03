plugins {
    alias(libs.plugins.jvm)
    alias(libs.plugins.compose)
}

dependencies {
    implementation(libs.material3)
    implementation(projects.jetwhaleHost.core.model)
}
