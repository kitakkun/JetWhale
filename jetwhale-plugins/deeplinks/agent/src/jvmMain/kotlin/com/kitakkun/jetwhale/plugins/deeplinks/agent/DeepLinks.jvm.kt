package com.kitakkun.jetwhale.plugins.deeplinks.agent

internal actual fun discoverDeclaredDeepLinks(): DeclaredDeepLinks = DeclaredDeepLinks(
    links = emptyList(),
    notes = listOf("A JVM app declares no links the platform can list. Register them as templates."),
)

// A scheme registered with the OS opens an installed app, not the process being debugged.
actual fun DeepLinkOpener.Companion.platformDefault(): DeepLinkOpener = UnsupportedDeepLinkOpener("the JVM has no way to route a link into the running app; pass a DeepLinkOpener that hands it to the app's router")
