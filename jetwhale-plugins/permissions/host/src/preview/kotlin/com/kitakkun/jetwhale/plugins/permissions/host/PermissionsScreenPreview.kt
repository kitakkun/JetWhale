package com.kitakkun.jetwhale.plugins.permissions.host

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.kitakkun.jetwhale.host.ui.JwTheme
import com.kitakkun.jetwhale.plugins.permissions.protocol.PermissionCategory
import com.kitakkun.jetwhale.plugins.permissions.protocol.PermissionChange
import com.kitakkun.jetwhale.plugins.permissions.protocol.PermissionReport
import com.kitakkun.jetwhale.plugins.permissions.protocol.PermissionState
import com.kitakkun.jetwhale.plugins.permissions.protocol.PermissionStatus

private object NoActions : PermissionsActions {
    override fun refresh() = Unit

    override fun request(id: String) = Unit

    override fun openAppSettings() = Unit
}

private val previewReport = PermissionReport(
    platform = "Android",
    unsupportedReason = null,
    permissions = listOf(
        PermissionState("android.permission.CAMERA", "CAMERA", PermissionCategory.Runtime, "dangerous", PermissionStatus.Denied, requestable = true, note = "Denied once; a request shows the dialog again."),
        PermissionState("android.permission.POST_NOTIFICATIONS", "POST_NOTIFICATIONS", PermissionCategory.Runtime, "dangerous", PermissionStatus.Granted, requestable = false, note = null),
        PermissionState("android.permission.SCHEDULE_EXACT_ALARM", "SCHEDULE_EXACT_ALARM", PermissionCategory.SpecialAccess, "signature|appop", PermissionStatus.Denied, requestable = true, note = "Granted by the user on a settings screen of its own."),
        PermissionState("android.permission.INTERNET", "INTERNET", PermissionCategory.InstallTime, "normal", PermissionStatus.Granted, requestable = false, note = null),
    ),
)

@Preview
@Composable
private fun PermissionsScreenPreview() {
    JwTheme(darkTheme = false) {
        PermissionsScreen(
            report = previewReport,
            timeline = listOf(PermissionChange("android.permission.CAMERA", "CAMERA", PermissionStatus.Granted, PermissionStatus.Denied, 1_760_000_000_000)),
            status = PermissionsStatus(message = "Asked for CAMERA; the user's choice arrives as a change.", isError = false),
            actions = NoActions,
        )
    }
}

@Preview
@Composable
private fun PermissionsScreenUnsupportedPreview() {
    JwTheme(darkTheme = true) {
        PermissionsScreen(
            report = PermissionReport(platform = "JVM", unsupportedReason = "A desktop JVM app has no permission model of its own.", permissions = emptyList()),
            timeline = emptyList(),
            status = null,
            actions = NoActions,
        )
    }
}
