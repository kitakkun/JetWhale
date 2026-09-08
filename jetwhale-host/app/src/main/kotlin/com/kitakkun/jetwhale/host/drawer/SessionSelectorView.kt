package com.kitakkun.jetwhale.host.drawer

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Devices
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import com.kitakkun.jetwhale.host.Res
import com.kitakkun.jetwhale.host.model.DebugSession
import com.kitakkun.jetwhale.host.no_session_available
import com.kitakkun.jetwhale.host.select_app
import com.kitakkun.jetwhale.host.select_device
import com.kitakkun.jetwhale.host.ui.JwDropdownButton
import com.kitakkun.jetwhale.host.ui.JwIcon
import com.kitakkun.jetwhale.host.ui.JwMenuItem
import com.kitakkun.jetwhale.host.ui.JwMetrics
import com.kitakkun.jetwhale.host.ui.JwSpacing
import com.kitakkun.jetwhale.host.ui.JwStatusDot
import com.kitakkun.jetwhale.host.ui.JwTone
import kotlinx.collections.immutable.ImmutableList
import org.jetbrains.compose.resources.stringResource

/**
 * Two-level session selector. Only active sessions are shown; disconnected sessions are hidden
 * entirely while remaining in the repository. Sessions are grouped by device, and within the
 * selected device the concrete app (session) can be picked. Each entry keeps the transport-security
 * lock indicator so the connection type stays visible per session.
 */
@Composable
fun SessionSelectorView(
    selectedSession: DebugSession?,
    sessions: ImmutableList<DebugSession>,
    onSelectSession: (DebugSession) -> Unit,
) {
    val activeSessions = remember(sessions) { sessions.filter { it.isActive } }
    val devices = remember(activeSessions) {
        activeSessions.groupBy { it.groupingDeviceId }.entries.toList()
    }
    val selectedDeviceId = selectedSession?.groupingDeviceId
    val appsForSelectedDevice = remember(devices, selectedDeviceId) {
        devices.firstOrNull { it.key == selectedDeviceId }?.value.orEmpty()
    }

    Column(verticalArrangement = Arrangement.spacedBy(JwSpacing.extraSmall)) {
        DeviceSelector(
            devices = devices,
            selectedDeviceId = selectedDeviceId,
            onSelectDevice = { deviceSessions ->
                // Selecting a device selects its first app so a session is always active.
                deviceSessions.firstOrNull()?.let(onSelectSession)
            },
        )
        if (appsForSelectedDevice.size > 1 || selectedSession != null) {
            AppSelector(
                apps = appsForSelectedDevice,
                selectedSession = selectedSession,
                onSelectSession = onSelectSession,
            )
        }
    }
}

@Composable
private fun DeviceSelector(
    devices: List<Map.Entry<String, List<DebugSession>>>,
    selectedDeviceId: String?,
    onSelectDevice: (List<DebugSession>) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedDevice = devices.firstOrNull { it.key == selectedDeviceId }?.value?.firstOrNull()

    JwDropdownButton(
        text = when {
            selectedDevice != null -> selectedDevice.deviceDisplayName
            devices.isNotEmpty() -> stringResource(Res.string.select_device)
            else -> stringResource(Res.string.no_session_available)
        },
        expanded = expanded,
        onExpandedChange = { expanded = it && devices.isNotEmpty() },
        enabled = devices.isNotEmpty(),
        leadingIcon = {
            JwIcon(imageVector = Icons.Default.Devices, contentDescription = null)
        },
        trailingIcon = {
            if (selectedDevice != null) {
                JwStatusDot(tone = JwTone.Success)
                SessionSecurityIcon(selectedDevice.transportSecurity)
            }
        },
    ) {
        devices.forEach { entry ->
            val representative = entry.value.first()
            JwMenuItem(
                text = representative.deviceDisplayName,
                selected = entry.key == selectedDeviceId,
                leadingIcon = { JwIcon(imageVector = Icons.Default.Devices, contentDescription = null) },
                trailingIcon = { SessionSecurityIcon(representative.transportSecurity) },
                onClick = {
                    onSelectDevice(entry.value)
                    expanded = false
                },
            )
        }
    }
}

@Composable
private fun AppSelector(
    apps: List<DebugSession>,
    selectedSession: DebugSession?,
    onSelectSession: (DebugSession) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    JwDropdownButton(
        text = selectedSession?.appDisplayName ?: stringResource(Res.string.select_app),
        expanded = expanded,
        onExpandedChange = { expanded = it && apps.isNotEmpty() },
        enabled = apps.isNotEmpty(),
        leadingIcon = { AppIcon(selectedSession) },
        trailingIcon = {
            if (selectedSession != null) {
                SessionSecurityIcon(selectedSession.transportSecurity)
            }
        },
    ) {
        apps.forEach { app ->
            SessionMenuItem(
                selected = app.id == selectedSession?.id,
                session = app,
                displayName = app.appDisplayName,
                onClick = {
                    onSelectSession(app)
                    expanded = false
                },
            )
        }
    }
}

@Composable
internal fun AppIcon(session: DebugSession?) {
    val bitmap: ImageBitmap? = remember(session?.appIconPngBase64) {
        session?.appIconPngBase64?.let { decodeIconOrNull(it) }
    }
    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = null,
            modifier = Modifier.size(JwMetrics.iconSize),
        )
    } else {
        JwIcon(imageVector = Icons.Default.Android, contentDescription = null)
    }
}
