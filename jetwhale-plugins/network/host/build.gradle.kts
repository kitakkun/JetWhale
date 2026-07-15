plugins {
    alias(libs.plugins.jvm)
    alias(libs.plugins.compose)
    alias(libs.plugins.jetwhalePlugin)
    alias(libs.plugins.jetwhaleHostLaunch)
}

// Distinct group so this module's coordinates don't collide with `example:host`.
group = "com.kitakkun.jetwhale.plugins.network"

jetwhalePlugin {
    pluginArchiveName.set("jetwhale-network-inspector")
}

dependencies {
    compileOnly(projects.jetwhaleHostSdk)
    compileOnly(libs.material3)
    compileOnly(libs.kotlinxSerializationJson)
    api(projects.jetwhalePlugins.network.protocol)
    testImplementation(libs.kotlinTest)
}
