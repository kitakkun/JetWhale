plugins {
    alias(libs.plugins.jvm)
    alias(libs.plugins.serialization)
    application
}

application {
    applicationName = "mcp-workflow"
    mainClass = "com.kitakkun.jetwhale.tools.mcpworkflow.MainKt"
    // record and serve speak MCP over stdout, so nothing else may print there: kotlin-logging
    // announces itself on stdout unless told not to.
    applicationDefaultJvmArgs = listOf("-Dkotlin-logging.logStartupMessage=false")
}

// Kept free of every JetWhale module: the tool speaks plain MCP to any server, and is laid out so it
// can move to a repository of its own.
dependencies {
    implementation(libs.mcpKotlinSdk)
    implementation(libs.kotlinxSerializationJson)
    implementation(libs.kotlinxCoroutinesCore)
    implementation(libs.kaml)
    implementation(libs.ktorClientCio)
    implementation(libs.ktorServerNetty)
    implementation(libs.ktorServerSse)
    // Gives the MCP SDK's SLF4J logging somewhere quiet to go instead of a stderr warning on every run.
    runtimeOnly(libs.slf4jNop)

    testImplementation(libs.kotlinTest)
    testImplementation(libs.kotlinxCoroutinesTest)
}
