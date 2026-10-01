plugins {
    alias(libs.plugins.jvm)
    alias(libs.plugins.serialization)
    alias(libs.plugins.publish)
    application
}

application {
    mainClass = "com.kitakkun.jetwhale.tools.qaagent.MainKt"
}

dependencies {
    implementation(projects.jetwhaleAgentRuntime)
    implementation(projects.jetwhalePlugins.network.agent)
    implementation(projects.jetwhalePlugins.network.agentKtor)

    implementation(libs.ktorClientCio)
    implementation(libs.ktorServerNetty)
    implementation(libs.ktorServerContentNegotiation)
    implementation(libs.ktorSerializationKotlinxJson)

    testImplementation(libs.kotlinTest)
}

jetwhalePublish {
    artifactId = "jetwhale-qa-agent"
    name = "JetWhale QA Agent"
    description = "Headless JetWhale debuggee that drives host plugins through a local control API."
}
