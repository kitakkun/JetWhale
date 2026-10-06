package com.kitakkun.jetwhale.plugins.permissions.host

import com.kitakkun.jetwhale.plugins.permissions.protocol.PermissionActionResult
import com.kitakkun.jetwhale.plugins.permissions.protocol.PermissionCategory
import com.kitakkun.jetwhale.plugins.permissions.protocol.PermissionReport
import com.kitakkun.jetwhale.plugins.permissions.protocol.PermissionState
import com.kitakkun.jetwhale.plugins.permissions.protocol.PermissionStatus

internal class FakePermissionsClient : PermissionsClient {
    var cameraStatus = PermissionStatus.Denied
    var nextError: String? = null
    val requests = mutableListOf<String>()

    override suspend fun report() = PermissionReport(
        platform = "Android",
        unsupportedReason = null,
        permissions = listOf(
            PermissionState("android.permission.CAMERA", "CAMERA", PermissionCategory.Runtime, "dangerous", cameraStatus, requestable = true, note = null),
        ),
    )

    override suspend fun request(id: String): PermissionActionResult {
        requests += id
        return PermissionActionResult(message = "asked", error = nextError)
    }

    override suspend fun openAppSettings() = PermissionActionResult(message = "opened", error = null)
}
