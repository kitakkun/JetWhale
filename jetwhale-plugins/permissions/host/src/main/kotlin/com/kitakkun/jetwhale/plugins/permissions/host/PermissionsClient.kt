package com.kitakkun.jetwhale.plugins.permissions.host

import com.kitakkun.jetwhale.plugins.permissions.protocol.PermissionActionResult
import com.kitakkun.jetwhale.plugins.permissions.protocol.PermissionReport

/**
 * The app's permissions as the UI and the MCP commands reach them; the host plugin is the only real
 * implementation. Each call throws `JetWhaleMessagingException` when the app cannot be reached.
 */
internal interface PermissionsClient {
    suspend fun report(): PermissionReport

    suspend fun request(id: String): PermissionActionResult

    suspend fun openAppSettings(): PermissionActionResult
}
