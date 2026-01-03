plugins {
    alias(libs.plugins.debuggerComposeFeature)
}

dependencies {
    implementation(projects.jetwhaleHostSdk)
    implementation(projects.jetwhaleHost.core.model)
    implementation(projects.jetwhaleHost.core.ui)
    implementation(projects.jetwhaleHost.core.architecture)

    implementation(libs.kotlinxCollectionsImmutable)

    implementation(libs.soilQueryCompose)
    implementation(libs.soilReacty)
    implementation(libs.lifecycleRuntimeCompose)
}

compose.resources {
    packageOfResClass = "com.kitakkun.jetwhale.host.plugin"
}
