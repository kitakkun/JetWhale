import com.kitakkun.kotrail.gradle.KotrailExtension

plugins {
    alias(libs.plugins.jvm)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.jetbrainsCompose)
    alias(libs.plugins.kotlinxSerialization)
    // Provides packagePlugin / installPlugin / stageDevPlugin / runJetWhale / runJetWhaleHot (published).
    alias(libs.plugins.jetwhalePlugin)
    // In-repo only: adds runJetWhaleLocal, which launches the local :jetwhale-host:app project.
    alias(libs.plugins.jetwhaleHostLaunch)
}

kotlin {
    // A compilation of its own for the previews, so that they are compiled and rule-checked on
    // every build without reaching the plugin jar. Associating it with `main` also lets a preview
    // call an internal declaration.
    target.compilations.create("preview") {
        associateWith(target.compilations.getByName("main"))
    }
}

// Distinct group so this module's coordinates don't collide with the other plugins' `host` modules.
group = "com.kitakkun.jetwhale.plugins.mirror"

jetwhalePlugin {
    // Unique name so the packaged plugin jar doesn't collide with the other plugins (also project
    // name "host") in ~/.jetwhale/plugins/ or the dev staging directory.
    pluginArchiveName.set("jetwhale-device-mirror")
}

dependencies {
    // Provided by the host at runtime, so compileOnly: these must be neither bundled into the
    // plugin jar nor listed in its dependency manifest.
    compileOnly(projects.jetwhaleHostSdk)
    compileOnly(projects.jetwhaleHostUi)
    compileOnly(compose.desktop.currentOs)
    compileOnly(libs.material3)
    compileOnly(libs.kotlinxSerializationJson)
    // The emulator's gRPC screen stream is plain HTTP/2 with prior knowledge; okhttp speaks it,
    // and the two messages it needs are encoded by hand rather than pulling in grpc-java. H.264
    // from adb screenrecord and idb is decoded by the ffmpeg command installed on the machine.
    implementation(libs.okhttp)
    "previewImplementation"(projects.jetwhaleHostUi)
    "previewImplementation"(compose.desktop.currentOs)
    "previewImplementation"(libs.jetbrainsComposePreview)
    testImplementation(projects.jetwhaleHostSdk)
    testImplementation(projects.jetwhaleHostUi)
    testImplementation(libs.kotlinTest)
    testImplementation(libs.okhttpMockwebserver)
    testImplementation(libs.kotlinxSerializationJson)
    testImplementation(libs.kotlinxCoroutinesTest)
    testImplementation(libs.jetbrainsComposeUiTestJUnit4)
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
