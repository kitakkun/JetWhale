package com.kitakkun.jetwhale.plugins.permissions.host

import com.kitakkun.jetwhale.annotations.ExperimentalJetWhaleApi
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArguments
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpCommand
import com.kitakkun.jetwhale.plugins.permissions.protocol.PermissionActionResult

@OptIn(ExperimentalJetWhaleApi::class)
internal class OpenAppSettingsCommand(
    private val client: PermissionsClient,
) : JetWhaleMcpCommand() {
    override val name = "$TOOL_PREFIX.openAppSettings"
    override val description =
        "Opens the app's page in the system settings on the device, where a permanently denied permission can be changed by hand."

    override suspend fun execute(arguments: JetWhaleMcpArguments): String =
        McpJson.encodeToString(PermissionActionResult.serializer(), client.openAppSettings())
}
