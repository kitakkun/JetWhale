package com.kitakkun.jetwhale.plugins.example.devpreview

/**
 * Entry point for the dev-host hot reload spike.
 *
 * This deliberately mirrors what a plugin author's own module would contain: a one-line `main` that
 * delegates to the (future, published) [launchJetWhaleDevHost] launcher. The plugin to develop is
 * picked up from this module's classpath dependency (`:jetwhale-plugins:example:host`).
 *
 * Run on the JetBrains Runtime:
 *   ./gradlew :jetwhale-plugins:example:dev-preview:hotRun --auto
 */
fun main() = launchJetWhaleDevHost()
