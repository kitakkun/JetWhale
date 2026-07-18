plugins {
    alias(libs.plugins.jvm)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.jetbrainsCompose)
    // Provides packagePlugin / installPlugin / stageDevPlugin / runJetWhale / runJetWhaleHot (published).
    alias(libs.plugins.jetwhalePlugin)
    // In-repo only: adds runJetWhaleLocal, which launches the local :jetwhale-host:app project.
    alias(libs.plugins.jetwhaleHostLaunch)
    alias(libs.plugins.publish)
}

// Distinct group so this module's coordinates don't collide with the other `host` plugin modules.
group = "com.kitakkun.jetwhale.plugins.mirror"

jetwhalePlugin {
    // Unique name so the packaged plugin jar doesn't collide with the other plugin modules (also
    // project name "host") in ~/.jetwhale/plugins/ or the dev staging directory.
    pluginArchiveName.set("jetwhale-device-mirror")
}

dependencies {
    // Provided by the host at runtime, so compileOnly: these must be neither bundled into the
    // plugin jar nor listed in its dependency manifest.
    compileOnly(projects.jetwhaleHostSdk)
    compileOnly(compose.desktop.currentOs)
    compileOnly(libs.material3)
    compileOnly(libs.kotlinxSerializationJson)
    testImplementation(projects.jetwhaleHostSdk)
    testImplementation(libs.kotlinTest)
    testImplementation(libs.kotlinxSerializationJson)
    testImplementation(compose.desktop.currentOs)
    testImplementation(libs.material3)
}

jetwhalePublish {
    artifactId = "jetwhale-device-mirror"
    name = "JetWhale Device Mirror"
    description = "JetWhale host plugin that mirrors Android emulator / iOS simulator screens and drives them interactively or via MCP."
}
