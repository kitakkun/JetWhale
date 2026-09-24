package com.kitakkun.jetwhale.plugins.permissions.agent

import com.kitakkun.jetwhale.plugins.permissions.protocol.PermissionActionResult
import com.kitakkun.jetwhale.plugins.permissions.protocol.PermissionState

/** Reads and requests permissions on one platform. */
internal interface PermissionSource {
    val platform: String

    /** Why the platform reports nothing, or null when it is supported. */
    val unsupportedReason: String?

    suspend fun read(): List<PermissionState>

    suspend fun request(id: String): PermissionActionResult

    suspend fun openAppSettings(): PermissionActionResult
}

internal expect fun platformPermissionSource(): PermissionSource
