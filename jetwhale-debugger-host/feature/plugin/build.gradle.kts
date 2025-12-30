plugins {
    alias(libs.plugins.debuggerComposeFeature)
}


dependencies {
    implementation(projects.jetwhaleDebuggerHostSdk)
    implementation(projects.jetwhaleDebuggerHost.core.model)
    implementation(projects.jetwhaleDebuggerHost.core.ui)
    implementation(projects.jetwhaleDebuggerHost.core.architecture)

    implementation(libs.kotlinxCollectionsImmutable)

    implementation(libs.soilQueryCompose)
    implementation(libs.soilReacty)
}
