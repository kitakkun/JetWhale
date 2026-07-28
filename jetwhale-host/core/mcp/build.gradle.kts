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
        // The MCP server is the consumer of the experimental plugin-facing MCP API, and host tools
        // are written with its parameter DSL, so every file in this module opts in.
        freeCompilerArgs.add("-opt-in=com.kitakkun.jetwhale.host.sdk.ExperimentalJetWhaleApi")
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
