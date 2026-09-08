plugins {
    alias(libs.plugins.jvm)
    alias(libs.plugins.compose)
}

dependencies {
    implementation(projects.jetwhaleHostUi)
    implementation(libs.soilQueryCompose)
    implementation(libs.soilReacty)
}
