package com.kitakkun.jetwhale.plugins.mainthread.host

import androidx.compose.runtime.Composable
import com.kitakkun.jetwhale.annotations.ExperimentalJetWhaleApi
import com.kitakkun.jetwhale.host.sdk.JetWhaleHostPlugin
import com.kitakkun.jetwhale.host.sdk.JetWhaleHostPluginFactory
import com.kitakkun.jetwhale.host.sdk.JetWhaleHostPluginUi
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpCapablePlugin
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpCommand
import com.kitakkun.jetwhale.host.sdk.JetWhaleMessagingHostPlugin
import com.kitakkun.jetwhale.plugins.mainthread.protocol.GetMainThreadReport
import com.kitakkun.jetwhale.plugins.mainthread.protocol.MainThreadReport
import com.kitakkun.jetwhale.plugins.mainthread.protocol.MonitorSettings
import com.kitakkun.jetwhale.plugins.mainthread.protocol.ResetMainThreadStats
import com.kitakkun.jetwhale.plugins.mainthread.protocol.UpdateMonitorSettings
import com.kitakkun.jetwhale.protocol.messaging.request

// Instantiated by the host via the fully-qualified name declared in plugin-manifest.json.
@Suppress("UNUSED")
class MainThreadHostPluginFactory : JetWhaleHostPluginFactory {
    override fun createPlugin(): JetWhaleHostPlugin = MainThreadHostPlugin()
}

@OptIn(ExperimentalJetWhaleApi::class)
private class MainThreadHostPlugin :
    JetWhaleMessagingHostPlugin(),
    JetWhaleHostPluginUi,
    JetWhaleMcpCapablePlugin,
    MainThreadClient {

    private val monitor by lazy { MainThreadMonitor(client = this, scope = pluginScope) }

    override suspend fun onPrepare() {
        monitor.load()
    }

    override suspend fun report(): MainThreadReport = messenger.request(GetMainThreadReport)

    override suspend fun updateSettings(settings: MonitorSettings): MonitorSettings = messenger.request(UpdateMonitorSettings(settings))

    override suspend fun reset(): MainThreadReport = messenger.request(ResetMainThreadStats)

    @Composable
    override fun Content() {
        MainThreadScreenRoot(monitor)
    }

    override val mcpCommands: List<JetWhaleMcpCommand> = listOf(
        GetHotspotsCommand(this),
        GetViolationsCommand(this),
        GetLongTasksCommand(this),
        GetFrameStatsCommand(this),
        ResetStatsCommand(this),
    )
}
