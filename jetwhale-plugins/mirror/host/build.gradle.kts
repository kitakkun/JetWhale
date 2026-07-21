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
    // H.264 mirror-stream decoding (adb screenrecord / idb video-stream). Bundled into the
    // plugin jar; the ffmpeg natives are restricted to the build machine's platform to keep the
    // artifact from carrying every OS's binaries.
    implementation(libs.javacv) {
        // javacv declares every bytedeco preset; only the ffmpeg bindings are used here.
        isTransitive = false
    }
    implementation("org.bytedeco:javacpp:${libs.versions.javacv.get()}")
    implementation(libs.bytedecoFfmpeg)
    implementation("org.bytedeco:ffmpeg:${libs.versions.bytedecoFfmpeg.get()}:${currentFfmpegClassifier()}")
    testImplementation(projects.jetwhaleHostSdk)
    testImplementation(libs.kotlinTest)
    testImplementation(libs.kotlinxSerializationJson)
    testImplementation(compose.desktop.currentOs)
    testImplementation(libs.material3)
}

fun currentFfmpegClassifier(): String {
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

jetwhalePublish {
    artifactId = "jetwhale-device-mirror"
    name = "JetWhale Device Mirror"
    description = "JetWhale host plugin that mirrors Android emulator / iOS simulator screens and drives them interactively or via MCP."
}
