plugins {
    alias(libs.plugins.jvm)
    alias(libs.plugins.serialization)
    application
}

application {
    applicationName = "mcp-workflow"
    mainClass = "com.kitakkun.jetwhale.tools.mcpworkflow.MainKt"
    // MCP over stdio owns stdout, and kotlin-logging prints a startup line to stdout unless this
    // property is false.
    applicationDefaultJvmArgs = listOf("-Dkotlin-logging.logStartupMessage=false")
}

dependencies {
    implementation(libs.mcpKotlinSdk)
    implementation(libs.kotlinxSerializationJson)
    implementation(libs.kotlinxCoroutinesCore)
    implementation(libs.kaml)
    implementation(libs.ktorClientCio)
    implementation(libs.ktorServerNetty)
    implementation(libs.ktorServerSse)
    // Without an SLF4J provider, SLF4J prints a warning to stderr on every run.
    runtimeOnly(libs.slf4jNop)

    testImplementation(libs.kotlinTest)
    testImplementation(libs.kotlinxCoroutinesTest)
}
