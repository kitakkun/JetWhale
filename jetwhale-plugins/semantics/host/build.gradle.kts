@file:OptIn(ExperimentalAbiValidation::class)

import org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation

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

kotlin {
    abiValidation {
    }
}

// Distinct group so this module's coordinates don't collide with the other plugins' `host`.
group = "com.kitakkun.jetwhale.plugins.semantics"

jetwhalePlugin {
    // Unique name so the packaged plugin jar doesn't collide with the other plugin modules (also
    // project name "host") in ~/.jetwhale/plugins/ or the dev staging directory.
    pluginArchiveName.set("jetwhale-compose-semantics-inspector")
}

dependencies {
    // Provided by the host at runtime, so compileOnly: these must be neither bundled into the
    // plugin jar nor listed in its dependency manifest.
    compileOnly(projects.jetwhaleHostSdk)
    compileOnly(projects.jetwhaleHostUi)
    compileOnly(compose.desktop.currentOs)
    compileOnly(libs.material3)
    compileOnly(libs.kotlinxSerializationJson)
    api(projects.jetwhalePlugins.semantics.protocol)

    testImplementation(projects.jetwhaleHostSdk)
    testImplementation(projects.jetwhaleHostUi)
    testImplementation(libs.kotlinTest)
    testImplementation(libs.kotlinxSerializationJson)
    testImplementation(compose.desktop.currentOs)
    testImplementation(libs.material3)
}

// The jetwhalePlugin convention publishes the `packageMavenPlugin` jar (the module's classes plus a
// manifest of its runtime dependencies) as the main artifact; the host's "Install from Maven"
// feature downloads it and fetches the listed dependencies itself.
jetwhalePublish {
    artifactId = "jetwhale-compose-semantics-inspector"
    name = "JetWhale Compose Semantics Inspector"
    description = "JetWhale host plugin that browses the Compose node tree of a running app and exposes it over MCP."
}
