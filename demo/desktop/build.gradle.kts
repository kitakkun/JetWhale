plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.jetbrainsCompose)
}

kotlin {
    jvmToolchain(17)
}

compose.desktop {
    application {
        mainClass = "com.kitakkun.jetwhale.demo.MainKt"
    }
}

dependencies {
    implementation(projects.demo.shared)
    implementation(compose.desktop.currentOs)
    // Compose Semantics Inspector probe: on desktop it is scoped to a window, so it is wired up here
    // rather than in the shared module.
    implementation(projects.jetwhalePlugins.semantics.agent)

    // Self-contained local API the demo client calls, so the demo never depends on a public endpoint.
    implementation(libs.ktorServerNetty)
}
