package com.kitakkun.jetwhale.plugins.soil.host

import androidx.compose.runtime.Composable
import com.kitakkun.jetwhale.annotations.ExperimentalJetWhaleApi
import com.kitakkun.jetwhale.host.sdk.JetWhaleHostPlugin
import com.kitakkun.jetwhale.host.sdk.JetWhaleHostPluginFactory
import com.kitakkun.jetwhale.host.sdk.JetWhaleHostPluginUi
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpCapablePlugin
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpCommand
import com.kitakkun.jetwhale.host.sdk.JetWhaleMessagingHostPlugin
import com.kitakkun.jetwhale.plugins.soil.protocol.GetSoilCacheSnapshot
import com.kitakkun.jetwhale.plugins.soil.protocol.GetSoilEntryValue
import com.kitakkun.jetwhale.plugins.soil.protocol.RunSoilEntryAction
import com.kitakkun.jetwhale.plugins.soil.protocol.SoilCacheSnapshot
import com.kitakkun.jetwhale.plugins.soil.protocol.SoilEntriesChanged
import com.kitakkun.jetwhale.plugins.soil.protocol.SoilEntryAction
import com.kitakkun.jetwhale.plugins.soil.protocol.SoilEntryActionResult
import com.kitakkun.jetwhale.plugins.soil.protocol.SoilEntryValue
import com.kitakkun.jetwhale.protocol.messaging.JetWhaleMessageHandlers
import com.kitakkun.jetwhale.protocol.messaging.request
import java.time.ZoneId
import kotlin.time.Clock

// Instantiated by the host via the fully-qualified name declared in plugin-manifest.json.
@Suppress("UNUSED")
class SoilHostPluginFactory : JetWhaleHostPluginFactory {
    override fun createPlugin(): JetWhaleHostPlugin = SoilHostPlugin()
}

@OptIn(ExperimentalJetWhaleApi::class)
private class SoilHostPlugin :
    JetWhaleMessagingHostPlugin(),
    JetWhaleHostPluginUi,
    JetWhaleMcpCapablePlugin,
    SoilCacheClient {

    private val browser by lazy { SoilCacheBrowser(client = this, scope = pluginScope, clock = Clock.System) }
    private val timeOfDayFormatter = TimeOfDayFormatter(ZoneId.systemDefault())

    override fun JetWhaleMessageHandlers.configure() {
        onEvent { event: SoilEntriesChanged -> browser.adopt(event) }
    }

    override suspend fun onPrepare() {
        browser.load()
    }

    override suspend fun takeSnapshot(): SoilCacheSnapshot = messenger.request(GetSoilCacheSnapshot)

    override suspend fun readValue(handle: String): SoilEntryValue = messenger.request(GetSoilEntryValue(handle))

    override suspend fun runAction(handle: String, action: SoilEntryAction): SoilEntryActionResult = messenger.request(RunSoilEntryAction(handle = handle, action = action))

    @Composable
    override fun Content() {
        SoilInspectorScreenRoot(browser, timeOfDayFormatter)
    }

    override val mcpCommands: List<JetWhaleMcpCommand> by lazy {
        listOf(ListSoilEntriesCommand(browser), ListSoilEventsCommand(browser), GetSoilEntryCommand(browser, timeOfDayFormatter)) + SoilEntryAction.entries.map { SoilEntryActionCommand(browser, it) }
    }
}
