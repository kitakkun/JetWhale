package com.kitakkun.jetwhale.plugins.permissions.host

import com.kitakkun.jetwhale.annotations.ExperimentalJetWhaleApi
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArguments
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpCommand
import com.kitakkun.jetwhale.plugins.permissions.protocol.PermissionReport

@OptIn(ExperimentalJetWhaleApi::class)
internal class ListPermissionsCommand(
    private val client: PermissionsClient,
) : JetWhaleMcpCommand() {
    override val name = "$TOOL_PREFIX.listPermissions"
    override val description =
        "Lists the app's permissions with their current status (Granted, Denied, NotDetermined, Limited, Restricted), category (Runtime, InstallTime, SpecialAccess), Android protection level, whether requestPermission can act on each right now, and a note on what the status cannot tell. On Android every permission the app declares is listed; on iOS the common privacy permissions. Revoking a permission is not possible from inside the app."

    override suspend fun execute(arguments: JetWhaleMcpArguments): String = McpJson.encodeToString(PermissionReport.serializer(), client.report())
}
