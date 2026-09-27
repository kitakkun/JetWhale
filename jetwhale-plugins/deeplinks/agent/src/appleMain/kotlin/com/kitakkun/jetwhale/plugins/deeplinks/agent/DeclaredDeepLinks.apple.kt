package com.kitakkun.jetwhale.plugins.deeplinks.agent

import com.kitakkun.jetwhale.plugins.deeplinks.protocol.DeclaredDeepLink
import platform.Foundation.NSBundle

internal actual fun discoverDeclaredDeepLinks(): DeclaredDeepLinks {
    val urlTypes = NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleURLTypes") as? List<*>
    val links = urlTypes.orEmpty().mapNotNull { type ->
        val entry = type as? Map<*, *> ?: return@mapNotNull null
        val schemes = (entry["CFBundleURLSchemes"] as? List<*>).orEmpty().filterIsInstance<String>()
        if (schemes.isEmpty()) return@mapNotNull null
        DeclaredDeepLink(
            handler = entry["CFBundleURLName"] as? String ?: "URL type",
            schemes = schemes,
            hosts = emptyList(),
            paths = emptyList(),
            browsable = true,
            autoVerify = false,
        )
    }
    // Associated domains live in the code signature's entitlements, which an app cannot read about
    // itself at runtime.
    return DeclaredDeepLinks(
        links = links,
        notes = listOf("Universal links are not listed: associated domains cannot be read at runtime. Register them as templates."),
    )
}
