plugins {
    alias(libs.plugins.jvm)
    alias(libs.plugins.metro)
    alias(libs.plugins.kotlinxSerialization)
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
    implementation(libs.androidxDatastoreCoreOkio)

    implementation(libs.kotlinxSerializationJson)
    implementation(libs.javaKeyring)
    implementation(libs.byteBuddyAgent)
    implementation(libs.bundles.ktorServer)
    implementation(libs.ktorClientCore)
    implementation(libs.ktorClientCio)
    implementation(libs.conveyorControl)
    implementation(libs.logbackClassic)
    implementation(libs.bouncyCastleBcprov)
    implementation(libs.bouncyCastleBcpkix)
    implementation(libs.jmdns)
    compileOnly(libs.androidxAnnotation)
    testImplementation(libs.kotlinTest)
    testImplementation(libs.ktorClientMock)
    testImplementation(libs.kotlinxCoroutinesTest)
}
