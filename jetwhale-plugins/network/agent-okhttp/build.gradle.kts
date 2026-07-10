plugins {
    alias(libs.plugins.jvm)
    alias(libs.plugins.publish)
}

// Distinct group so this module doesn't collide with other leaf-name-sharing modules.
group = "com.kitakkun.jetwhale.plugins.network"

dependencies {
    api(projects.jetwhalePlugins.network.agent)
    api(libs.okhttp) // Interceptor appears in the public API surface

    testImplementation(libs.kotlinTest)
    testImplementation(libs.okhttpMockwebserver)
    testImplementation(libs.kotlinxCoroutinesCore)
    testImplementation(libs.kotlinxSerializationJson)
}

jetwhalePublish {
    artifactId = "jetwhale-network-inspector-agent-okhttp"
    name = "JetWhale Network Inspector Agent (OkHttp)"
    description = "OkHttp interceptor that wires the JetWhale Network Inspector into an OkHttpClient."
}
