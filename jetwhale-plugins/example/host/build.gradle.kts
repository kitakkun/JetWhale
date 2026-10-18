plugins {
    alias(libs.plugins.jvm)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.jetbrainsCompose)
    alias(libs.plugins.jetwhalePlugin)
    alias(libs.plugins.jetwhaleHostLaunch)
}

dependencies {
    compileOnly(projects.jetwhaleHostSdk)
    compileOnly(projects.jetwhaleHostUi)
    compileOnly(compose.desktop.currentOs)
    compileOnly(libs.material3)
    compileOnly(libs.jetbrainsComposePreview)
    compileOnly(libs.kotlinxSerializationJson)
    api(projects.jetwhalePlugins.example.protocol)
    testImplementation(projects.jetwhaleHostSdk)
    testImplementation(projects.jetwhaleHostUi)
    testImplementation(compose.desktop.currentOs)
    testImplementation(libs.material3)
    testImplementation(libs.kotlinTest)
    testImplementation(libs.kotlinxSerializationJson)
}
