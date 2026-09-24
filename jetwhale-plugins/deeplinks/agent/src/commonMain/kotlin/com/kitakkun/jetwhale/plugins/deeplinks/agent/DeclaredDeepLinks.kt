package com.kitakkun.jetwhale.plugins.deeplinks.agent

import com.kitakkun.jetwhale.plugins.deeplinks.protocol.DeclaredDeepLink

/**
 * What the platform reports about the links the app handles.
 *
 * @property notes What the platform could not report, for the host to show as is.
 */
internal class DeclaredDeepLinks(
    val links: List<DeclaredDeepLink>,
    val notes: List<String>,
)

internal expect fun discoverDeclaredDeepLinks(): DeclaredDeepLinks
