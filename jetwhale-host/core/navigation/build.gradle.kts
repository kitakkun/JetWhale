plugins {
    alias(libs.plugins.jvm)
    alias(libs.plugins.compose)
}

dependencies {
    implementation(projects.jetwhaleHost.core.ui)

    implementation(libs.bundles.navigation3)
}
