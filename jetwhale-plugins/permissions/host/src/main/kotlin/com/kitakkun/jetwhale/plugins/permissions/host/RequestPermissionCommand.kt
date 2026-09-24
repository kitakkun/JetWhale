package com.kitakkun.jetwhale.plugins.permissions.host

import com.kitakkun.jetwhale.annotations.ExperimentalJetWhaleApi
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArguments
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpCommand
import com.kitakkun.jetwhale.plugins.permissions.protocol.PermissionActionResult

@OptIn(ExperimentalJetWhaleApi::class)
internal class RequestPermissionCommand(
    private val client: PermissionsClient,
) : JetWhaleMcpCommand() {
    override val name = "$TOOL_PREFIX.requestPermission"
    override val description =
        "Asks the user for a permission on the device: a runtime permission shows the system dialog in the app, a special access opens its settings screen. The result says whether the request was started, not what the user chose; call listPermissions afterwards to see the outcome."

    private val id by string("The permission's id, as listPermissions reports it (e.g. android.permission.CAMERA or ios.camera).")

    override suspend fun execute(arguments: JetWhaleMcpArguments): String =
        McpJson.encodeToString(PermissionActionResult.serializer(), client.request(arguments[id]))
}
