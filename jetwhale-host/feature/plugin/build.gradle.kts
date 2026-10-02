plugins {
    alias(libs.plugins.debuggerComposeFeature)
}

dependencies {
    implementation(projects.jetwhaleHostSdk)
    implementation(projects.jetwhaleHostUi)
    implementation(projects.jetwhaleHost.core.model)
    implementation(projects.jetwhaleHost.core.ui)
    implementation(projects.jetwhaleHost.core.architecture)

    implementation(libs.kotlinxCollectionsImmutable)

    implementation(libs.soilQueryCompose)
    implementation(libs.soilReacty)
    implementation(libs.lifecycleRuntimeCompose)
    testImplementation(libs.kotlinTest)
    testImplementation(compose.desktop.currentOs)
    testImplementation(libs.jetbrainsComposeUiTestJUnit4)
}

compose.resources {
    packageOfResClass = "com.kitakkun.jetwhale.host.plugin"
}
