package com.kitakkun.jetwhale.host.drawer

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Devices
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.tooling.preview.Preview
import com.kitakkun.jetwhale.host.Res
import com.kitakkun.jetwhale.host.model.DebugSession
import com.kitakkun.jetwhale.host.model.SessionTransportSecurity
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
import kotlinx.collections.immutable.persistentListOf
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
    modifier: Modifier = Modifier,
) {
    val activeSessions = remember(sessions) { sessions.filter(DebugSession::isActive) }
    val devices = remember(activeSessions) {
        activeSessions.groupBy(DebugSession::groupingDeviceId).entries.toList()
    }
    val selectedDeviceId = selectedSession?.groupingDeviceId
    val appsForSelectedDevice = remember(devices, selectedDeviceId) {
        devices.firstOrNull { it.key == selectedDeviceId }?.value.orEmpty()
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(JwSpacing.extraSmall)) {
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
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedDevice = devices.firstOrNull { it.key == selectedDeviceId }?.value?.firstOrNull()

    JwDropdownButton(
        modifier = modifier,
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
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    JwDropdownButton(
        modifier = modifier,
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
        session?.appIconPngBase64?.let(::decodeIconOrNull)
    }
    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = null,
            modifier = Modifier.size(JwMetrics.iconSize),
        )
    } else {
        // An app sends no icon from a platform the agent cannot read one on (desktop, web), so the
        // fallback must not name a platform.
        JwIcon(imageVector = Icons.Default.Apps, contentDescription = null)
    }
}

@Preview
@Composable
private fun SessionSelectorViewPreview() {
    val session = DebugSession(
        id = "session-1",
        name = "Sample app",
        isActive = true,
        transportSecurity = SessionTransportSecurity.TLS,
        installedPlugins = persistentListOf(),
        appName = "Sample app",
        deviceId = "device-1",
        deviceName = "Pixel 9",
    )
    SessionSelectorView(
        selectedSession = session,
        sessions = persistentListOf(session),
        onSelectSession = {},
    )
}

@Preview
@Composable
private fun AppIconPreview() {
    AppIcon(session = null)
}
