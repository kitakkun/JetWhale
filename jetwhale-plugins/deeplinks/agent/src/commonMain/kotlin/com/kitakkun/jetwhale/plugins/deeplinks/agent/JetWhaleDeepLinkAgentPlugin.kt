package com.kitakkun.jetwhale.plugins.deeplinks.agent

import com.kitakkun.jetwhale.agent.sdk.JetWhaleAgentPlugin
import com.kitakkun.jetwhale.plugins.deeplinks.protocol.DEEP_LINKS_PLUGIN_ID
import com.kitakkun.jetwhale.plugins.deeplinks.protocol.DeepLinkCatalog
import com.kitakkun.jetwhale.plugins.deeplinks.protocol.DeepLinkOpenResult
import com.kitakkun.jetwhale.plugins.deeplinks.protocol.DeepLinkTemplate
import com.kitakkun.jetwhale.plugins.deeplinks.protocol.GetDeepLinkCatalog
import com.kitakkun.jetwhale.plugins.deeplinks.protocol.OpenDeepLink
import com.kitakkun.jetwhale.protocol.messaging.JetWhaleMessageHandlers
import com.kitakkun.jetwhale.protocol.messaging.reply
import kotlin.coroutines.cancellation.CancellationException

/**
 * Agent plugin that lists the deep links an app declares and opens them on the host's request.
 *
 * Most apps want [platformDefaults]: on Android the links come from the app's own intent filters, on
 * iOS and macOS from its URL types, and a link is opened the way the platform opens it when it is
 * followed from outside the app.
 *
 * ```kotlin
 * startJetWhale { plugins { register(JetWhaleDeepLinkAgentPlugin.platformDefaults()) } }
 * ```
 *
 * Links the platform cannot list — iOS universal links, anything on the JVM or the web — can be
 * offered as [templates], with `{name}` placeholders the host turns into a form:
 *
 * ```kotlin
 * JetWhaleDeepLinkAgentPlugin(
 *     templates = listOf(DeepLinkTemplate(name = "Product", template = "https://example.com/product/{id}", description = null)),
 *     opener = DeepLinkOpener.platformDefault(),
 * )
 * ```
 *
 * @param opener Opens a link. On the JVM and the web there is no platform way to route a link into
 *   the running app, so pass one that hands the link to the app's own router.
 */
class JetWhaleDeepLinkAgentPlugin(
    private val templates: List<DeepLinkTemplate>,
    private val opener: DeepLinkOpener,
) : JetWhaleAgentPlugin() {
    override val pluginId: String get() = DEEP_LINKS_PLUGIN_ID
    override val pluginVersion: String get() = "1.0.0"

    override fun JetWhaleMessageHandlers.configure() {
        onRequest { _: GetDeepLinkCatalog -> reply(catalog()) }
        onRequest { request: OpenDeepLink -> reply(open(request.url)) }
    }

    private fun catalog(): DeepLinkCatalog {
        val discovery = try {
            discoverDeclaredDeepLinks()
        } catch (e: Exception) {
            DeclaredDeepLinks(links = emptyList(), notes = listOf("Reading the app's declared links failed: ${e.message ?: e::class.simpleName}"))
        }
        return DeepLinkCatalog(declared = discovery.links, templates = templates, canOpen = opener.canOpen, notes = discovery.notes)
    }

    private suspend fun open(url: String): DeepLinkOpenResult = try {
        opener.open(url)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        DeepLinkOpenResult(opened = false, handledBy = emptyList(), error = e.message ?: e::class.simpleName ?: "unknown error")
    }

    companion object {
        /** A plugin with no templates that opens links the platform's way. */
        fun platformDefaults(): JetWhaleDeepLinkAgentPlugin = JetWhaleDeepLinkAgentPlugin(templates = emptyList(), opener = DeepLinkOpener.platformDefault())
    }
}
