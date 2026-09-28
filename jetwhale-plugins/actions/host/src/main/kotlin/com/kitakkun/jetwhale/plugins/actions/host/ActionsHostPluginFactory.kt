package com.kitakkun.jetwhale.plugins.actions.host

import androidx.compose.runtime.Composable
import com.kitakkun.jetwhale.annotations.ExperimentalJetWhaleApi
import com.kitakkun.jetwhale.host.sdk.JetWhaleHostPlugin
import com.kitakkun.jetwhale.host.sdk.JetWhaleHostPluginFactory
import com.kitakkun.jetwhale.host.sdk.JetWhaleHostPluginUi
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpCapablePlugin
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpCommand
import com.kitakkun.jetwhale.host.sdk.JetWhaleMessagingHostPlugin
import com.kitakkun.jetwhale.plugins.actions.protocol.ActionCatalog
import com.kitakkun.jetwhale.plugins.actions.protocol.ActionOptions
import com.kitakkun.jetwhale.plugins.actions.protocol.ActionResult
import com.kitakkun.jetwhale.plugins.actions.protocol.ActionsChanged
import com.kitakkun.jetwhale.plugins.actions.protocol.CancelActionRun
import com.kitakkun.jetwhale.plugins.actions.protocol.CancelResult
import com.kitakkun.jetwhale.plugins.actions.protocol.GetActionOptions
import com.kitakkun.jetwhale.plugins.actions.protocol.ListActions
import com.kitakkun.jetwhale.plugins.actions.protocol.RunAction
import com.kitakkun.jetwhale.protocol.messaging.JetWhaleMessageHandlers
import com.kitakkun.jetwhale.protocol.messaging.request
import kotlinx.serialization.json.JsonObject

// Instantiated by the host via the fully-qualified name declared in plugin-manifest.json.
@Suppress("UNUSED")
class ActionsHostPluginFactory : JetWhaleHostPluginFactory {
    override fun createPlugin(): JetWhaleHostPlugin = ActionsHostPlugin()
}

@OptIn(ExperimentalJetWhaleApi::class)
private class ActionsHostPlugin :
    JetWhaleMessagingHostPlugin(),
    JetWhaleHostPluginUi,
    JetWhaleMcpCapablePlugin,
    ActionsClient {

    private val browser by lazy { ActionsBrowser(client = this, scope = pluginScope) }

    override fun JetWhaleMessageHandlers.configure() {
        onEvent { event: ActionsChanged -> browser.adopt(event.catalog) }
    }

    // The agent pushes only changes, so the whole catalog is fetched once per connection.
    override suspend fun onPrepare() {
        browser.load()
    }

    override suspend fun listActions(): ActionCatalog = messenger.request(ListActions)

    override suspend fun options(actionId: String, parameter: String): ActionOptions = messenger.request(GetActionOptions(actionId = actionId, parameter = parameter))

    override suspend fun run(runId: String, actionId: String, arguments: JsonObject, confirmedDestructive: Boolean): ActionResult = messenger.request(RunAction(runId = runId, actionId = actionId, arguments = arguments, confirmedDestructive = confirmedDestructive))

    override suspend fun cancel(runId: String): CancelResult = messenger.request(CancelActionRun(runId))

    @Composable
    override fun Content() {
        ActionsScreenRoot(browser)
    }

    // Lazy because the browser needs pluginScope; the host reads the commands only once the plugin
    // is bound.
    override val mcpCommands: List<JetWhaleMcpCommand> by lazy {
        listOf(ListActionsCommand(browser), RunActionCommand(browser))
    }
}
