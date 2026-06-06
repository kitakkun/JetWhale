plugins {
    alias(libs.plugins.jvm)
    alias(libs.plugins.compose)
    alias(libs.plugins.ksp)
}

// Distinct group so this module's coordinates don't collide with `example:host`.
group = "com.kitakkun.jetwhale.plugins.network"

dependencies {
    ksp(libs.autoServiceKsp)
    implementation(libs.autoServiceAnnotations)

    compileOnly(projects.jetwhaleHostSdk)
    compileOnly(libs.material3)
    compileOnly(libs.kotlinxSerializationJson)
    api(projects.jetwhalePlugins.network.protocol)
}

tasks.jar {
    val dependencies = configurations
        .runtimeClasspath
        .get()
        .map(::zipTree)
    from(dependencies)
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}
