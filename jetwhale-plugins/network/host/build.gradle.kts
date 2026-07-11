plugins {
    alias(libs.plugins.jvm)
    alias(libs.plugins.compose)
}

// Distinct group so this module's coordinates don't collide with `example:host`.
group = "com.kitakkun.jetwhale.plugins.network"

dependencies {
    compileOnly(projects.jetwhaleHostSdk)
    compileOnly(libs.material3)
    compileOnly(libs.kotlinxSerializationJson)
    api(projects.jetwhalePlugins.network.protocol)
    testImplementation(libs.kotlinTest)
}

tasks.jar {
    // Unique name so the fat jar doesn't collide with `example:host` (also project name "host").
    archiveBaseName = "jetwhale-network-inspector"

    val dependencies = configurations
        .runtimeClasspath
        .get()
        .map(::zipTree)
    from(dependencies)
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}
