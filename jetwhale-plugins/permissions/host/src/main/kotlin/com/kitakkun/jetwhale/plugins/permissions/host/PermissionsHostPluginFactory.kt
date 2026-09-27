package com.kitakkun.jetwhale.plugins.permissions.host

import androidx.compose.runtime.Composable
import com.kitakkun.jetwhale.annotations.ExperimentalJetWhaleApi
import com.kitakkun.jetwhale.host.sdk.JetWhaleHostPlugin
import com.kitakkun.jetwhale.host.sdk.JetWhaleHostPluginFactory
import com.kitakkun.jetwhale.host.sdk.JetWhaleHostPluginUi
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpCapablePlugin
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpCommand
import com.kitakkun.jetwhale.host.sdk.JetWhaleMessagingHostPlugin
import com.kitakkun.jetwhale.plugins.permissions.protocol.GetPermissions
import com.kitakkun.jetwhale.plugins.permissions.protocol.OpenAppSettings
import com.kitakkun.jetwhale.plugins.permissions.protocol.PermissionActionResult
import com.kitakkun.jetwhale.plugins.permissions.protocol.PermissionReport
import com.kitakkun.jetwhale.plugins.permissions.protocol.PermissionsChanged
import com.kitakkun.jetwhale.plugins.permissions.protocol.RequestPermission
import com.kitakkun.jetwhale.protocol.messaging.JetWhaleMessageHandlers
import com.kitakkun.jetwhale.protocol.messaging.request

// Instantiated by the host via the fully-qualified name declared in plugin-manifest.json.
@Suppress("UNUSED")
class PermissionsHostPluginFactory : JetWhaleHostPluginFactory {
    override fun createPlugin(): JetWhaleHostPlugin = PermissionsHostPlugin()
}

@OptIn(ExperimentalJetWhaleApi::class)
private class PermissionsHostPlugin :
    JetWhaleMessagingHostPlugin(),
    JetWhaleHostPluginUi,
    JetWhaleMcpCapablePlugin,
    PermissionsClient {

    private val board by lazy { PermissionsBoard(client = this, scope = pluginScope) }

    override fun JetWhaleMessageHandlers.configure() {
        onEvent { event: PermissionsChanged -> board.onChanged(event.changes) }
    }

    // Change events carry only what changed, so the full report is fetched once per connection.
    override suspend fun onPrepare() {
        board.load()
    }

    override suspend fun report(): PermissionReport = messenger.request(GetPermissions)

    override suspend fun request(id: String): PermissionActionResult = messenger.request(RequestPermission(id))

    override suspend fun openAppSettings(): PermissionActionResult = messenger.request(OpenAppSettings)

    @Composable
    override fun Content() {
        PermissionsScreenRoot(board)
    }

    override val mcpCommands: List<JetWhaleMcpCommand> = listOf(
        ListPermissionsCommand(this),
        RequestPermissionCommand(this),
        OpenAppSettingsCommand(this),
    )
}
