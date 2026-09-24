plugins {
    alias(libs.plugins.jvm)
    alias(libs.plugins.serialization)
    application
}

application {
    applicationName = "mcp-workflow"
    mainClass = "com.kitakkun.jetwhale.tools.mcpworkflow.MainKt"
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

    testImplementation(libs.kotlinTest)
    testImplementation(libs.kotlinxCoroutinesTest)
}
