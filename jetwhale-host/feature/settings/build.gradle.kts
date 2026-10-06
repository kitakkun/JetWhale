plugins {
    alias(libs.plugins.debuggerComposeFeature)
}

dependencies {
    implementation(projects.jetwhaleHostUi)
    implementation(projects.jetwhaleHost.core.model)
    implementation(projects.jetwhaleHost.releaseMetadata)
    implementation(projects.jetwhaleHost.core.ui)
    implementation(projects.jetwhaleHost.core.architecture)
    implementation(libs.kotlinxCollectionsImmutable)
    implementation(libs.kotlinxDatetime)
    implementation(libs.aboutLibrariesCore)
    compileOnly(libs.androidxAnnotation)
    testImplementation(libs.kotlinTest)
    testImplementation(compose.desktop.currentOs)
    testImplementation(libs.jetbrainsComposeUiTestJUnit4)
}

compose.resources {
    packageOfResClass = "com.kitakkun.jetwhale.host.settings"
}
