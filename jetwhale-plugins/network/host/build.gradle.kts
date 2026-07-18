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

// Distinct group so this module's coordinates don't collide with `example:host`.
group = "com.kitakkun.jetwhale.plugins.network"

jetwhalePlugin {
    // Unique name so the packaged plugin jar doesn't collide with `example:host` (also project name
    // "host") in ~/.jetwhale/plugins/ or the dev staging directory.
    pluginArchiveName.set("jetwhale-network-inspector")
}

dependencies {
    // Provided by the host at runtime, so compileOnly: these must be neither bundled into the
    // plugin jar nor listed in its dependency manifest.
    compileOnly(projects.jetwhaleHostSdk)
    compileOnly(compose.desktop.currentOs)
    compileOnly(libs.material3)
    compileOnly(libs.kotlinxSerializationJson)
    api(projects.jetwhalePlugins.network.protocol)
    testImplementation(libs.kotlinTest)
    testImplementation(libs.kotlinxSerializationJson)
    testImplementation(compose.desktop.currentOs)
    testImplementation(libs.material3)
}

// The jetwhalePlugin convention publishes the `packageMavenPlugin` jar (the module's classes plus a
// manifest of its runtime dependencies) as the main artifact; the host's "Install from Maven"
// feature downloads it and fetches the listed dependencies itself.
jetwhalePublish {
    artifactId = "jetwhale-network-inspector"
    name = "JetWhale Network Inspector"
    description = "JetWhale host plugin for the Network Inspector (HTTP capture and mocking)."
}
