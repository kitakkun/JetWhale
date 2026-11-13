package com.kitakkun.jetwhale.plugins.permissions.host

import com.kitakkun.jetwhale.plugins.permissions.protocol.PermissionActionResult
import com.kitakkun.jetwhale.plugins.permissions.protocol.PermissionCategory
import com.kitakkun.jetwhale.plugins.permissions.protocol.PermissionReport
import com.kitakkun.jetwhale.plugins.permissions.protocol.PermissionState
import com.kitakkun.jetwhale.plugins.permissions.protocol.PermissionStatus

internal class FakePermissionsClient : PermissionsClient {
    var cameraStatus = PermissionStatus.Denied
    var requestError: String? = null
    val requestedPermissionIds = mutableListOf<String>()

    override suspend fun getPermissions() = PermissionReport(
        platform = "Android",
        unsupportedReason = null,
        permissions = listOf(
            PermissionState("android.permission.CAMERA", "CAMERA", PermissionCategory.Runtime, "dangerous", cameraStatus, requestable = true, note = null),
        ),
    )

    override suspend fun requestPermission(permissionId: String): PermissionActionResult {
        requestedPermissionIds += permissionId
        return PermissionActionResult(message = "asked", error = requestError)
    }

    override suspend fun openAppSettings() = PermissionActionResult(message = "opened", error = null)
}
