plugins {
    alias(libs.plugins.jvm)
    alias(libs.plugins.serialization)
    application
}

application {
    mainClass = "com.kitakkun.jetwhale.tools.qaagent.MainKt"
}

dependencies {
    // Deliberately independent of :demo — this is development infrastructure, not a usage example,
    // and it must stay driveable without a UI toolkit on the classpath.
    implementation(projects.jetwhaleAgentRuntime)
    implementation(projects.jetwhalePlugins.network.agent)
    implementation(projects.jetwhalePlugins.network.agentKtor)

    // Outbound: the instrumented client whose traffic the Network Inspector captures.
    implementation(libs.ktorClientCio)
    // Inbound: the control API this agent is driven through.
    implementation(libs.ktorServerNetty)
    implementation(libs.ktorServerContentNegotiation)
    implementation(libs.ktorSerializationKotlinxJson)
}
