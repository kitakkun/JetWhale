package com.kitakkun.jetwhale.plugins.background.host

import androidx.compose.runtime.Composable
import com.kitakkun.jetwhale.annotations.ExperimentalJetWhaleApi
import com.kitakkun.jetwhale.host.sdk.JetWhaleHostPlugin
import com.kitakkun.jetwhale.host.sdk.JetWhaleHostPluginFactory
import com.kitakkun.jetwhale.host.sdk.JetWhaleHostPluginUi
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpCapablePlugin
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpCommand
import com.kitakkun.jetwhale.host.sdk.JetWhaleMessagingHostPlugin
import com.kitakkun.jetwhale.plugins.background.protocol.BackgroundWorkChanged
import com.kitakkun.jetwhale.plugins.background.protocol.BackgroundWorkSnapshot
import com.kitakkun.jetwhale.plugins.background.protocol.CancelTarget
import com.kitakkun.jetwhale.plugins.background.protocol.CancelWork
import com.kitakkun.jetwhale.plugins.background.protocol.GetBackgroundWork
import com.kitakkun.jetwhale.plugins.background.protocol.RunWorkNow
import com.kitakkun.jetwhale.plugins.background.protocol.WorkOperationResult
import com.kitakkun.jetwhale.protocol.messaging.JetWhaleMessageHandlers
import com.kitakkun.jetwhale.protocol.messaging.request

// Instantiated by the host via the fully-qualified name declared in plugin-manifest.json.
@Suppress("UNUSED")
class BackgroundWorkHostPluginFactory : JetWhaleHostPluginFactory {
    override fun createPlugin(): JetWhaleHostPlugin = BackgroundWorkHostPlugin()
}

@OptIn(ExperimentalJetWhaleApi::class)
private class BackgroundWorkHostPlugin :
    JetWhaleMessagingHostPlugin(),
    JetWhaleHostPluginUi,
    JetWhaleMcpCapablePlugin,
    BackgroundWorkClient {

    private val browser by lazy { BackgroundWorkBrowser(client = this, scope = pluginScope) }

    override fun JetWhaleMessageHandlers.configure() {
        onEvent { event: BackgroundWorkChanged -> browser.accept(event.snapshot, System.currentTimeMillis()) }
    }

    // Changes are pushed, but only from the moment the host is listening; the state before that
    // is fetched once per connection.
    override suspend fun onPrepare() {
        browser.load()
    }

    override suspend fun snapshot(): BackgroundWorkSnapshot = messenger.request(GetBackgroundWork)

    override suspend fun cancel(source: String, target: CancelTarget): WorkOperationResult = messenger.request(CancelWork(source = source, target = target))

    override suspend fun runNow(source: String, id: String): WorkOperationResult = messenger.request(RunWorkNow(source = source, id = id))

    @Composable
    override fun Content() {
        BackgroundWorkScreenRoot(browser)
    }

    override val mcpCommands: List<JetWhaleMcpCommand> = listOf(
        ListBackgroundWorkCommand(this),
        CancelWorkCommand(this),
        RunWorkNowCommand(this),
    )
}
