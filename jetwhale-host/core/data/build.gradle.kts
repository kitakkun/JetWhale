plugins {
    alias(libs.plugins.jvm)
    alias(libs.plugins.metro)
    alias(libs.plugins.mokkery)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.jetbrainsCompose)
}

dependencies {
    implementation(projects.jetwhaleHostSdk)
    implementation(projects.jetwhaleHost.core.model)
    implementation(projects.jetwhaleProtocol.host)
    implementation(projects.jetwhaleProtocol.core)

    implementation(compose.desktop.currentOs)

    implementation(libs.soilQueryCore)
    implementation(libs.kotlinxCoroutinesCore)
    implementation(libs.kotlinxCollectionsImmutable)
    implementation(libs.androidxDatastorePreferences)

    implementation(libs.bundles.ktorServer)
    implementation(libs.logbackClassic)
    implementation(libs.kotlinTest)
}
