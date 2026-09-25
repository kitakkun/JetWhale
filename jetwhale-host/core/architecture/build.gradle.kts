plugins {
    alias(libs.plugins.jvm)
    alias(libs.plugins.compose)
}

dependencies {
    implementation(projects.jetwhaleHostUi)
    implementation(libs.soilQueryCompose)
    implementation(libs.soilReacty)

    testImplementation(libs.kotlinTest)
    testImplementation(compose.desktop.currentOs)
    testImplementation(libs.jetbrainsComposeUiTestJUnit4)
}
