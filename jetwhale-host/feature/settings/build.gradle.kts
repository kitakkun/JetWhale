plugins {
    alias(libs.plugins.debuggerComposeFeature)
}

dependencies {
    implementation(projects.jetwhaleHost.core.model)
    implementation(projects.jetwhaleHost.core.architecture)
    implementation(libs.kotlinxCollectionsImmutable)
    implementation(libs.rin)
    implementation(libs.aboutLibrariesComposeM3)
}

compose.resources {
    packageOfResClass = "com.kitakkun.jetwhale.host.settings"
}
