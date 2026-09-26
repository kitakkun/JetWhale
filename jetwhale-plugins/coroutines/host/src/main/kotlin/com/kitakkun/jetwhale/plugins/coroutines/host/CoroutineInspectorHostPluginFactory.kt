package com.kitakkun.jetwhale.plugins.coroutines.host

import androidx.compose.runtime.Composable
import com.kitakkun.jetwhale.annotations.ExperimentalJetWhaleApi
import com.kitakkun.jetwhale.host.sdk.JetWhaleHostPlugin
import com.kitakkun.jetwhale.host.sdk.JetWhaleHostPluginFactory
import com.kitakkun.jetwhale.host.sdk.JetWhaleHostPluginUi
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpCapablePlugin
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpCommand
import com.kitakkun.jetwhale.host.sdk.JetWhaleMessagingHostPlugin
import com.kitakkun.jetwhale.plugins.coroutines.protocol.ClearLongRuns
import com.kitakkun.jetwhale.plugins.coroutines.protocol.ClearedLongRuns
import com.kitakkun.jetwhale.plugins.coroutines.protocol.CoroutineDetail
import com.kitakkun.jetwhale.plugins.coroutines.protocol.CoroutineDump
import com.kitakkun.jetwhale.plugins.coroutines.protocol.CoroutineTree
import com.kitakkun.jetwhale.plugins.coroutines.protocol.DispatcherStatsReport
import com.kitakkun.jetwhale.plugins.coroutines.protocol.DumpCoroutines
import com.kitakkun.jetwhale.plugins.coroutines.protocol.GetCoroutineDetail
import com.kitakkun.jetwhale.plugins.coroutines.protocol.GetCoroutineTree
import com.kitakkun.jetwhale.plugins.coroutines.protocol.GetDispatcherStats
import com.kitakkun.jetwhale.plugins.coroutines.protocol.GetTrackedFlows
import com.kitakkun.jetwhale.plugins.coroutines.protocol.TrackedFlowReport
import com.kitakkun.jetwhale.protocol.messaging.request

// Instantiated by the host via the fully-qualified name declared in plugin-manifest.json.
@Suppress("UNUSED")
class CoroutineInspectorHostPluginFactory : JetWhaleHostPluginFactory {
    override fun createPlugin(): JetWhaleHostPlugin = CoroutineInspectorHostPlugin()
}

@OptIn(ExperimentalJetWhaleApi::class)
private class CoroutineInspectorHostPlugin :
    JetWhaleMessagingHostPlugin(),
    JetWhaleHostPluginUi,
    JetWhaleMcpCapablePlugin,
    CoroutineInspectorClient {

    private val state by lazy { CoroutineInspectorState(client = this, scope = pluginScope) }

    override suspend fun coroutineTree(): CoroutineTree = messenger.request(GetCoroutineTree)

    override suspend fun dispatcherStats(): DispatcherStatsReport = messenger.request(GetDispatcherStats)

    override suspend fun trackedFlows(): TrackedFlowReport = messenger.request(GetTrackedFlows)

    override suspend fun dump(): CoroutineDump = messenger.request(DumpCoroutines)

    override suspend fun coroutineDetail(id: String): CoroutineDetail = messenger.request(GetCoroutineDetail(id))

    override suspend fun clearLongRuns(): ClearedLongRuns = messenger.request(ClearLongRuns)

    @Composable
    override fun Content() {
        CoroutineInspectorScreenRoot(state)
    }

    override val mcpCommands: List<JetWhaleMcpCommand> = listOf(
        GetCoroutineTreeCommand(this),
        GetDispatcherStatsCommand(this),
        GetTrackedFlowsCommand(this),
        GetCoroutineDetailCommand(this),
        DumpCoroutinesCommand(this),
    )
}
