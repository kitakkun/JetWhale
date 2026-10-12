plugins {
    alias(libs.plugins.kotlinDsl)
    // Apply vanniktech (with the catalog version) before the `publish` convention, which applies it
    // without a version — so it resolves when this build is configured standalone (e.g. on CI).
    alias(libs.plugins.mavenPublish)
    alias(libs.plugins.publish)
}

group = "com.kitakkun.jetwhale"
version = libs.versions.jetwhale.get() + if (hasProperty("jetwhaleSnapshot")) "-SNAPSHOT" else ""

dependencies {
    // Both come from the protocol module at run time: the Kotlin Gradle plugin it applies, and the
    // kotlinx.serialization on its runtime classpath, which is what reads its descriptors.
    compileOnly(libs.kotlinGradlePlugin)
    compileOnly(libs.kotlinxSerializationJson)

    testImplementation(kotlin("test"))
}

// The functional tests' fixtures apply these by id. TestKit puts them in the plugin under test's
// class loader, which is where the protocol plugin looks up the Kotlin Gradle plugin's types.
val functionalTestKotlinPlugins: Configuration by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
}

dependencies {
    functionalTestKotlinPlugins(libs.kotlinGradlePlugin)
    functionalTestKotlinPlugins(libs.kotlinxSerializationGradlePlugin)
}

tasks.pluginUnderTestMetadata {
    pluginClasspath.from(functionalTestKotlinPlugins)
}

tasks.test {
    useJUnitPlatform()
    systemProperty("jetwhale.test.kotlinxSerializationVersion", libs.versions.kotlinxSerializationJson.get())
}

gradlePlugin {
    plugins {
        create("jetwhaleProtocol") {
            id = "com.kitakkun.jetwhale.protocol"
            displayName = "JetWhale Protocol"
            description = "Declares a JetWhale plugin's protocol revision and checks its wire schema."
            implementationClass = "com.kitakkun.jetwhale.protocol.gradle.JetWhaleProtocolPlugin"
        }
    }
}

jetwhalePublish {
    artifactId = "jetwhale-host-gradle-plugin"
    name = "JetWhale Host Gradle Plugin"
    description = "Gradle plugins for developing JetWhale plugins: packaging and running host plugins " +
        "(packagePlugin, runJetWhale, runJetWhaleHot), and declaring a protocol module's revision and wire schema."
}
