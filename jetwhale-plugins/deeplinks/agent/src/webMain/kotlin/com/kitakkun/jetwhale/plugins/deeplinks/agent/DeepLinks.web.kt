package com.kitakkun.jetwhale.plugins.deeplinks.agent

internal actual fun discoverDeclaredDeepLinks(): DeclaredDeepLinks = DeclaredDeepLinks(
    links = emptyList(),
    notes = listOf("A web app declares no links the platform can list. Register them as templates."),
)

actual fun DeepLinkOpener.Companion.platformDefault(): DeepLinkOpener = UnsupportedDeepLinkOpener("navigating would reload the app; pass a DeepLinkOpener that hands the link to the app's router")
