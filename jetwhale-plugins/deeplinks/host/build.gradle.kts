@file:OptIn(ExperimentalAbiValidation::class)

import com.kitakkun.kotrail.gradle.KotrailExtension
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

    // A compilation of its own for the previews, so that they are compiled and rule-checked on
    // every build without reaching the plugin jar, the publication or the ABI dump. Associating it
    // with `main` also lets a preview call an internal declaration.
    target.compilations.create("preview") {
        associateWith(target.compilations.getByName("main"))
    }
}

// Distinct group so this module's coordinates don't collide with the other plugins' `host` modules.
group = "com.kitakkun.jetwhale.plugins.deeplinks"

jetwhalePlugin {
    // Unique name so the packaged plugin jar doesn't collide with the other plugins (also project
    // name "host") in ~/.jetwhale/plugins/ or the dev staging directory.
    pluginArchiveName.set("jetwhale-deep-links")
}

dependencies {
    // Provided by the host at runtime, so compileOnly: these must be neither bundled into the
    // plugin jar nor listed in its dependency manifest.
    compileOnly(projects.jetwhaleHostSdk)
    compileOnly(projects.jetwhaleHostUi)
    compileOnly(compose.desktop.currentOs)
    compileOnly(libs.material3)
    compileOnly(libs.kotlinxSerializationJson)
    api(projects.jetwhalePlugins.deeplinks.protocol)
    "previewImplementation"(projects.jetwhaleHostUi)
    "previewImplementation"(compose.desktop.currentOs)
    "previewImplementation"(libs.jetbrainsComposePreview)
    "previewImplementation"(libs.kotlinxSerializationJson)
    testImplementation(projects.jetwhaleHostSdk)
    testImplementation(projects.jetwhaleHostUi)
    testImplementation(libs.kotlinTest)
    testImplementation(libs.kotlinxSerializationJson)
    testImplementation(compose.desktop.currentOs)
    testImplementation(libs.material3)
}

tasks.named("check") {
    dependsOn("compilePreviewKotlin")
}

configure<KotrailExtension> {
    compilation("main") { configFile = file("kotrail-main.yaml") }
    compilation("preview") { configFile = file("kotrail-preview.yaml") }
}

// The jetwhalePlugin convention publishes the `packageMavenPlugin` jar (the module's classes plus a
// manifest of its runtime dependencies) as the main artifact; the host's "Install from Maven"
// feature downloads it and fetches the listed dependencies itself.
jetwhalePublish {
    artifactId = "jetwhale-deep-links"
    name = "JetWhale Deep Links"
    description = "JetWhale host plugin for listing and opening an app's deep links."
}
