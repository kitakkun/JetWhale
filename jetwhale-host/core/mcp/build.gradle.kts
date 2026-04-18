plugins {
    alias(libs.plugins.jvm)
    alias(libs.plugins.metro)
    alias(libs.plugins.kotlinxSerialization)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.jetbrainsCompose)
    alias(libs.plugins.mokkery)
}

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-opt-in=com.kitakkun.jetwhale.annotations.InternalJetWhaleApi")
    }
}

dependencies {
    implementation(projects.jetwhaleHostSdk)
    implementation(projects.jetwhaleHost.core.model)

    implementation(compose.desktop.currentOs)

    implementation(libs.mcpKotlinSdk)
    implementation(libs.ktorServerNetty)
    implementation(libs.ktorServerSse)
    implementation(libs.ktorServerContentNegotiation)
    implementation(libs.ktorSerializationKotlinxJson)

    implementation(libs.kotlinxCoroutinesCore)
    implementation(libs.kotlinxSerializationJson)
    implementation(libs.kotlinxCollectionsImmutable)
    implementation(libs.logbackClassic)

    testImplementation(libs.kotlinTest)
    testImplementation(libs.ktorClientCio)
}
