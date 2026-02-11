package com.kitakkun.jetwhale.plugins.example.host

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.google.auto.service.AutoService
import com.kitakkun.jetwhale.host.sdk.JetWhaleDebugOperationContext
import com.kitakkun.jetwhale.host.sdk.JetWhaleHostPlugin
import com.kitakkun.jetwhale.host.sdk.JetWhaleHostPluginFactory
import com.kitakkun.jetwhale.host.sdk.JetWhalePluginIcon
import com.kitakkun.jetwhale.host.sdk.JetWhalePluginMetaData
import com.kitakkun.jetwhale.host.sdk.jetWhalePluginMetaData
import com.kitakkun.jetwhale.host.sdk.pluginIcon
import com.kitakkun.jetwhale.plugins.example.protocol.ExampleEvent
import com.kitakkun.jetwhale.plugins.example.protocol.ExampleMethod
import com.kitakkun.jetwhale.plugins.example.protocol.ExampleMethodResult
import com.kitakkun.jetwhale.protocol.host.JetWhaleHostPluginProtocol
import com.kitakkun.jetwhale.protocol.host.kotlinxSerializationJetWhaleHostPluginProtocol

@Suppress("UNUSED")
@AutoService(JetWhaleHostPluginFactory::class)
class ExampleHostPluginFactory : JetWhaleHostPluginFactory {
    override val meta: JetWhalePluginMetaData = jetWhalePluginMetaData(
        pluginId = "com.kitakkun.jetwhale.example",
        pluginName = "Example JetWhale Plugin",
        version = "1.0.0",
    )
    override val icon: JetWhalePluginIcon = pluginIcon(
        activeIconPath = "icons/window_filled.svg",
        inactiveIconPath = "icons/window_outlined.svg",
    )

    override fun createPlugin(): JetWhaleHostPlugin<*, *, *> {
        return ExampleHostPlugin()
    }

    override fun isCompatibleWithAgentPlugin(agentVersion: String): Boolean {
        return agentVersion == this.meta.version
    }
}

private class ExampleHostPlugin() : JetWhaleHostPlugin<ExampleEvent, ExampleMethod, ExampleMethodResult>() {
    override val protocol: JetWhaleHostPluginProtocol<ExampleEvent, ExampleMethod, ExampleMethodResult> = kotlinxSerializationJetWhaleHostPluginProtocol()

    private val eventLogs: SnapshotStateList<String> = mutableStateListOf()

    override fun onEvent(event: ExampleEvent) {
        when (event) {
            is ExampleEvent.ButtonClicked -> eventLogs.add("Event: $event")
        }
    }

    @Composable
    override fun Content(context: JetWhaleDebugOperationContext<ExampleMethod, ExampleMethodResult>) {
        ExamplePluginContent(
            eventLogs = eventLogs,
            context = context,
        )
    }
}
