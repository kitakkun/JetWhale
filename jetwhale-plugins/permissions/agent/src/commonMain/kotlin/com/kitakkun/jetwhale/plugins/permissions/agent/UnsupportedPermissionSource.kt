package com.kitakkun.jetwhale.plugins.permissions.agent

import com.kitakkun.jetwhale.plugins.permissions.protocol.PermissionActionResult
import com.kitakkun.jetwhale.plugins.permissions.protocol.PermissionState

/** A platform with no permission model the agent can read: it reports [reason] and nothing else. */
internal class UnsupportedPermissionSource(
    override val platform: String,
    private val reason: String,
) : PermissionSource {
    override val unsupportedReason: String get() = reason

    override suspend fun read(): List<PermissionState> = emptyList()

    override suspend fun request(id: String): PermissionActionResult = PermissionActionResult(message = "", error = reason)

    override suspend fun openAppSettings(): PermissionActionResult = PermissionActionResult(message = "", error = reason)
}
