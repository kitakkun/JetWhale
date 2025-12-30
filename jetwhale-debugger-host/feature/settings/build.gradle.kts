plugins {
    alias(libs.plugins.debuggerComposeFeature)
}

dependencies {
    implementation(projects.jetwhaleDebuggerHost.core.model)
    implementation(projects.jetwhaleDebuggerHost.core.architecture)
    implementation(libs.kotlinxCollectionsImmutable)
    implementation(libs.rin)
}

compose.resources {
    packageOfResClass = "com.kitakkun.jetwhale.debugger.host.settings"
}
