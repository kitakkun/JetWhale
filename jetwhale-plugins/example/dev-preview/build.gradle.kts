plugins {
    alias(libs.plugins.jvm)
    alias(libs.plugins.compose)
}

// Compose Hot Reload PoC: a tiny Compose Desktop harness that renders the example plugin's UI so it
// can be hot-reloaded with state preserved. Run on JetBrains Runtime:
//   ./gradlew :jetwhale-plugins:example:dev-preview:hotRun --auto
// then edit ExamplePluginView and save. See README.md in this module.
compose.desktop {
    application {
        mainClass = "com.kitakkun.jetwhale.plugins.example.devpreview.MainKt"
    }
}

dependencies {
    implementation(projects.jetwhalePlugins.example.host)
    implementation(projects.jetwhaleHostSdk)
    implementation(libs.kotlinxCoroutinesCore)
}
