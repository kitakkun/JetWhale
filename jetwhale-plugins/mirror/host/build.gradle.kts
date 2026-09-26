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

// Not published: the H.264 decoder ships ffmpeg natives for one OS (see below), and the host's
// "Install from Maven" route has no way yet to pick the natives for the machine it runs on.

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
    // H.264 decoding of the adb screenrecord / idb video-stream output. Only the ffmpeg bindings
    // of javacv are used, so its other presets stay out; the natives are the build machine's own.
    implementation(libs.javacv) {
        isTransitive = false
    }
    implementation(libs.javacpp)
    implementation(variantOf(libs.javacpp) { classifier(nativeClassifier()) })
    implementation(libs.bytedecoFfmpeg)
    implementation(variantOf(libs.bytedecoFfmpeg) { classifier(nativeClassifier()) })
    "previewImplementation"(projects.jetwhaleHostUi)
    "previewImplementation"(compose.desktop.currentOs)
    "previewImplementation"(libs.jetbrainsComposePreview)
    testImplementation(projects.jetwhaleHostSdk)
    testImplementation(projects.jetwhaleHostUi)
    testImplementation(libs.kotlinTest)
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

fun nativeClassifier(): String {
    val osName = System.getProperty("os.name").lowercase()
    val arch = System.getProperty("os.arch").lowercase()
    return when {
        osName.contains("mac") && arch == "aarch64" -> "macosx-arm64"
        osName.contains("mac") -> "macosx-x86_64"
        osName.contains("windows") -> "windows-x86_64"
        arch == "aarch64" -> "linux-arm64"
        else -> "linux-x86_64"
    }
}
