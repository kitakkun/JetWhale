package com.kitakkun.jetwhale.plugins.deeplinks.agent

// A JVM app has no platform registry of the links it handles, and a scheme registered with the OS
// routes to an installed app, not to the process being debugged.
internal actual fun discoverDeclaredDeepLinks(): DeclaredDeepLinks = DeclaredDeepLinks(
    links = emptyList(),
    notes = listOf("A JVM app declares no links the platform can list. Register them as templates."),
)

actual fun DeepLinkOpener.Companion.platformDefault(): DeepLinkOpener =
    UnsupportedDeepLinkOpener("the JVM has no way to route a link into the running app; pass a DeepLinkOpener that hands it to the app's router")
