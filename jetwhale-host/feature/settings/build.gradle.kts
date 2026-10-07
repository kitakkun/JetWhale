plugins {
    alias(libs.plugins.debuggerComposeFeature)
    alias(libs.plugins.docsScreenshots)
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
    "docsScreenshotsImplementation"(projects.jetwhalePlugins.network.host)
}

compose.resources {
    packageOfResClass = "com.kitakkun.jetwhale.host.settings"
}
