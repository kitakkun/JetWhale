package com.kitakkun.jetwhale.plugins.deeplinks.host

import androidx.compose.runtime.Composable
import com.kitakkun.jetwhale.annotations.ExperimentalJetWhaleApi
import com.kitakkun.jetwhale.host.sdk.JetWhaleHostPlugin
import com.kitakkun.jetwhale.host.sdk.JetWhaleHostPluginFactory
import com.kitakkun.jetwhale.host.sdk.JetWhaleHostPluginUi
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpCapablePlugin
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpCommand
import com.kitakkun.jetwhale.host.sdk.JetWhaleMessagingHostPlugin
import com.kitakkun.jetwhale.plugins.deeplinks.protocol.DeepLinkCatalog
import com.kitakkun.jetwhale.plugins.deeplinks.protocol.DeepLinkOpenResult
import com.kitakkun.jetwhale.plugins.deeplinks.protocol.GetDeepLinkCatalog
import com.kitakkun.jetwhale.plugins.deeplinks.protocol.OpenDeepLink
import com.kitakkun.jetwhale.protocol.messaging.request

// Instantiated by the host via the fully-qualified name declared in plugin-manifest.json.
@Suppress("UNUSED")
class DeepLinkHostPluginFactory : JetWhaleHostPluginFactory {
    override fun createPlugin(): JetWhaleHostPlugin = DeepLinkHostPlugin()
}

@OptIn(ExperimentalJetWhaleApi::class)
private class DeepLinkHostPlugin :
    JetWhaleMessagingHostPlugin(),
    JetWhaleHostPluginUi,
    JetWhaleMcpCapablePlugin,
    DeepLinkClient {

    private val browser by lazy { DeepLinkBrowser(client = this, scope = pluginScope) }

    // The catalog changes only when the app is rebuilt, so one fetch per connection is enough.
    override suspend fun onPrepare() {
        browser.load()
    }

    override suspend fun catalog(): DeepLinkCatalog = messenger.request(GetDeepLinkCatalog)

    override suspend fun open(url: String): DeepLinkOpenResult = messenger.request(OpenDeepLink(url))

    @Composable
    override fun Content() {
        DeepLinksScreenRoot(browser)
    }

    override val mcpCommands: List<JetWhaleMcpCommand> = listOf(
        ListDeepLinksCommand(this),
        OpenDeepLinkCommand(this),
    )
}
