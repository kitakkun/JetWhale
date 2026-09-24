package com.kitakkun.jetwhale.demo.shared

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.navigation3.runtime.NavKey
import com.kitakkun.jetwhale.plugins.deeplinks.agent.DeepLinkOpener
import com.kitakkun.jetwhale.plugins.deeplinks.protocol.DeepLinkOpenResult
import com.kitakkun.jetwhale.plugins.deeplinks.protocol.DeepLinkTemplate

/**
 * The demo's deep links: `demo://item/{id}`, `demo://settings/{section}`, and the same item page as
 * `https://demo.jetwhale.example/item/{id}`. The platform hands a link to [handle]; the app shows it
 * by pushing the matching screen onto its Navigation 3 back stack.
 */
object DemoDeepLinks {
    /** The link waiting to be shown, taken by the app's composition. */
    var pending: String? by mutableStateOf(null)
        private set

    fun handle(url: String) {
        pending = url
    }

    fun consume(): String? = pending.also { pending = null }

    val templates: List<DeepLinkTemplate> = listOf(
        DeepLinkTemplate(name = "Item", template = "demo://item/{id}", description = "Opens the item's detail screen"),
        DeepLinkTemplate(name = "Settings section", template = "demo://settings/{section}", description = "General, Privacy or About"),
        DeepLinkTemplate(name = "Item (web link)", template = "https://demo.jetwhale.example/item/{id}", description = "The item page as an App Link"),
    )
}

/** The screen [url] leads to, or null for a link the demo does not know. */
fun navKeyForDeepLink(url: String): NavKey? {
    val scheme = url.substringBefore("://", missingDelimiterValue = "")
    val segments = url.substringAfter("://").substringBefore('?').split('/').filter(String::isNotEmpty)
    // A custom scheme's first segment is the route; an https link's first segment is its host.
    val route = if (scheme == "https") segments.drop(1) else segments
    return when (route.firstOrNull()) {
        "item" -> route.getOrNull(1)?.let { DemoDetailKey(itemId = it) }
        "settings" -> route.getOrNull(1)?.let { name -> DemoSettingsSection.entries.firstOrNull { it.name.equals(name, ignoreCase = true) } }?.let(::DemoSettingsKey)
        else -> null
    }
}

/**
 * Opens a link on platforms that cannot route one into the running app (the JVM and the web): it
 * goes straight to the demo's own handling.
 */
internal object DemoRouterDeepLinkOpener : DeepLinkOpener {
    override val canOpen: Boolean get() = true

    override suspend fun open(url: String): DeepLinkOpenResult {
        if (navKeyForDeepLink(url) == null) {
            return DeepLinkOpenResult(opened = false, handledBy = emptyList(), error = "the demo has no screen for $url")
        }
        DemoDeepLinks.handle(url)
        return DeepLinkOpenResult(opened = true, handledBy = listOf("DemoDeepLinks"), error = null)
    }
}

/** The platform's own opener where there is one (Android, iOS), the demo's router elsewhere. */
internal expect fun demoDeepLinkOpener(): DeepLinkOpener
