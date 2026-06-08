plugins {
    alias(libs.plugins.jvm)
    alias(libs.plugins.compose)
    // Provides packagePlugin / installPlugin / stageDevPlugin / runJetWhale / runJetWhaleHot (published).
    alias(libs.plugins.jetwhalePlugin)
    // In-repo only: adds runJetWhaleLocal, which launches the local :jetwhale-host:app project.
    alias(libs.plugins.jetwhaleHostLaunch)
}

dependencies {
    compileOnly(projects.jetwhaleHostSdk)
    compileOnly(libs.material3)
    compileOnly(libs.kotlinxSerializationJson)
    api(projects.jetwhalePlugins.example.protocol)
}
