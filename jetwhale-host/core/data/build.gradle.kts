plugins {
    alias(libs.plugins.jvm)
    alias(libs.plugins.metro)
    alias(libs.plugins.mokkery)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.jetbrainsCompose)
}

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-opt-in=com.kitakkun.jetwhale.annotations.InternalJetWhaleApi")
    }
}

dependencies {
    implementation(projects.jetwhaleHostSdk)
    implementation(projects.jetwhaleHost.core.model)
    implementation(projects.jetwhaleHost.core.mcp)
    implementation(projects.jetwhaleProtocol.core)

    implementation(compose.desktop.currentOs)

    implementation(libs.soilQueryCore)
    implementation(libs.kotlinxCoroutinesCore)
    implementation(libs.kotlinxDatetime)
    implementation(libs.kotlinxCollectionsImmutable)
    implementation(libs.androidxDatastorePreferences)

    implementation(libs.kotlinxSerializationJson)
    // Used in dev hot-reload to obtain a JVM Instrumentation handle (self-attach) for in-place class
    // redefinition. Dormant in production: only touched when the dev plugins directory is configured.
    implementation(libs.byteBuddyAgent)
    implementation(libs.bundles.ktorServer)
    implementation(libs.logbackClassic)
    implementation(libs.kotlinTest)
}
