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
    // Deliberately independent of :demo — this is development infrastructure, not a usage example,
    // and it must stay driveable without a UI toolkit on the classpath.
    implementation(projects.jetwhaleAgentRuntime)
    // The one plugin compiled in: `/fire` injects traffic for the bundled Network Inspector. Every
    // other plugin is reached over the raw messaging layer, so no dependency on it is needed.
    implementation(projects.jetwhalePlugins.network.agent)
    implementation(projects.jetwhalePlugins.network.agentKtor)

    // Outbound: the instrumented client whose traffic the Network Inspector captures.
    implementation(libs.ktorClientCio)
    // Inbound: the control API this agent is driven through.
    implementation(libs.ktorServerNetty)
    implementation(libs.ktorServerContentNegotiation)
    implementation(libs.ktorSerializationKotlinxJson)

    testImplementation(libs.kotlinTest)
}

// Published so plugin authors outside this repository can run it — `runJetWhaleQaAgent` in the
// Gradle plugin resolves exactly these coordinates.
jetwhalePublish {
    artifactId = "jetwhale-qa-agent"
    name = "JetWhale QA Agent"
    description = "Headless JetWhale debuggee that drives host plugins through a local control API."
}
