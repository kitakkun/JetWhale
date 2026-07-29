plugins {
    alias(libs.plugins.debuggerComposeFeature)
    alias(libs.plugins.kotlinxSerialization)
}

dependencies {
    implementation(projects.jetwhaleHost.core.model)
    implementation(projects.jetwhaleHost.core.navigation)
    implementation(projects.jetwhaleHost.feature.settings)
    implementation(projects.jetwhaleHost.feature.plugin)

    implementation(libs.bundles.navigation3)
    implementation(libs.kotlinxSerializationJson)

    testImplementation(libs.kotlinTest)
}
