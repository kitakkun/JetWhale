package com.kitakkun.jetwhale.host.sdk.web

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.kitakkun.jetwhale.host.sdk.ExperimentalJetWhaleApi
import com.kitakkun.jetwhale.host.sdk.InternalJetWhaleHostApi
import com.kitakkun.jetwhale.host.sdk.JetWhaleHostPlugin
import com.kitakkun.jetwhale.host.sdk.JetWhaleHostPluginFactory
import com.kitakkun.jetwhale.host.sdk.JetWhaleMessagingHostPlugin
import com.kitakkun.jetwhale.protocol.messaging.JetWhaleMessageHandlers

/**
 * Configuration for a pure web plugin, derived from its manifest `web` block by the host loader.
 *
 * @param classLoader the plugin jar's class loader (owns the bundled assets).
 * @param resourceRoot resource directory inside the jar holding the built web app.
 * @param entry entry document relative to [resourceRoot].
 * @param devServerUrl a dev-server URL to load instead of the bundle, or `null` to use the bundle.
 */
@InternalJetWhaleHostApi
public class WebPluginConfig(
    public val classLoader: ClassLoader,
    public val resourceRoot: String,
    public val entry: String,
    public val devServerUrl: String?,
)

/**
 * A [JetWhaleHostPluginFactory] the host synthesizes for a manifest that declares a `web` block, so a
 * pure web plugin needs no author code. The returned plugin forwards everything between the bundled
 * web UI and the agent generically:
 * - JS `window.jetwhale.send`/`request` → the agent (handled inside [JetWhaleWebView]);
 * - every inbound agent event → the web UI's `window.jetwhale.onMessage`, via a raw event handler.
 */
@InternalJetWhaleHostApi
public fun webManifestPluginFactory(config: WebPluginConfig): JetWhaleHostPluginFactory =
    object : JetWhaleHostPluginFactory {
        override fun createPlugin(): JetWhaleHostPlugin = WebManifestHostPlugin(config)
    }

@OptIn(ExperimentalJetWhaleApi::class, InternalJetWhaleHostApi::class)
private class WebManifestHostPlugin(
    private val config: WebPluginConfig,
) : JetWhaleMessagingHostPlugin(), JetWhaleWebHostPluginUi {

    private val bridge = JetWhaleWebBridge()

    override fun JetWhaleMessageHandlers.configure() {
        // Generic forwarding: any inbound agent event is delivered to the web UI as (type, payload)
        // without a Kotlin type per message.
        onRawEvent { messageType, payload -> bridge.emit(messageType, payload) }
    }

    @Composable
    override fun Content() {
        val source = config.devServerUrl?.let { JetWhaleWebSource.DevServer(it) }
            ?: JetWhaleWebSource.BundledAsset(
                classLoader = config.classLoader,
                resourceRoot = config.resourceRoot,
                entry = config.entry,
            )
        JetWhaleWebView(
            messenger = messenger,
            bridge = bridge,
            source = source,
            modifier = Modifier.fillMaxSize(),
        )
    }
}
