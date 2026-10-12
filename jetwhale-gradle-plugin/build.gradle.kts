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
    // compileOnly: the protocol module brings its own Kotlin Gradle plugin, and its own
    // kotlinx.serialization is what reads its descriptors.
    compileOnly(libs.kotlinGradlePlugin)
    compileOnly(libs.kotlinxSerializationJson)

    testImplementation(kotlin("test"))
}

// The functional tests' fixtures apply these by id. TestKit puts them in the same class loader as
// the plugin under test, which is where the protocol plugin looks for the Kotlin Gradle plugin.
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
