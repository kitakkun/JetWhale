plugins {
    alias(libs.plugins.jvm)
    alias(libs.plugins.jetbrainsCompose)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.metro)
}

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-opt-in=com.kitakkun.jetwhale.annotations.InternalJetWhaleApi")
    }
}

dependencies {
    implementation(projects.jetwhaleHostSdk)
    implementation(libs.soilQueryCore)
    implementation(libs.kotlinxCollectionsImmutable)
    implementation(libs.kotlinxDatetime)
    implementation(libs.jetbrainsComposeRuntime)
    implementation(compose.desktop.currentOs)
    implementation(libs.aboutLibrariesCore)
}
