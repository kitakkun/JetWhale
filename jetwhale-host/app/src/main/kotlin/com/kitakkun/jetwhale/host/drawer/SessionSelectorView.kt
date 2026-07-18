package com.kitakkun.jetwhale.host.drawer

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kitakkun.jetwhale.host.Res
import com.kitakkun.jetwhale.host.model.DebugSession
import com.kitakkun.jetwhale.host.no_session_available
import com.kitakkun.jetwhale.host.select_app
import com.kitakkun.jetwhale.host.select_device
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

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeviceSelector(
    devices: List<Map.Entry<String, List<DebugSession>>>,
    selectedDeviceId: String?,
    onSelectDevice: (List<DebugSession>) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedDevice = devices.firstOrNull { it.key == selectedDeviceId }?.value?.firstOrNull()

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it && devices.isNotEmpty() },
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 56.dp)
                .menuAnchor(type = ExposedDropdownMenuAnchorType.PrimaryEditable),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Icon(imageVector = Icons.Default.Devices, contentDescription = null)
                Text(
                    text = when {
                        selectedDevice != null -> selectedDevice.deviceDisplayName
                        devices.isNotEmpty() -> stringResource(Res.string.select_device)
                        else -> stringResource(Res.string.no_session_available)
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (selectedDevice != null) {
                    SessionSecurityIcon(selectedDevice.transportSecurity)
                }
            }
        }
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            devices.forEach { entry ->
                val representative = entry.value.first()
                DropdownMenuItem(
                    leadingIcon = {
                        if (entry.key == selectedDeviceId) {
                            Icon(imageVector = Icons.Default.Check, contentDescription = null)
                        } else {
                            Icon(imageVector = Icons.Default.Devices, contentDescription = null)
                        }
                    },
                    trailingIcon = { SessionSecurityIcon(representative.transportSecurity) },
                    text = { Text(representative.deviceDisplayName) },
                    onClick = {
                        onSelectDevice(entry.value)
                        expanded = false
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppSelector(
    apps: List<DebugSession>,
    selectedSession: DebugSession?,
    onSelectSession: (DebugSession) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it && apps.isNotEmpty() },
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 56.dp)
                .menuAnchor(type = ExposedDropdownMenuAnchorType.PrimaryEditable),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                AppIcon(selectedSession)
                Text(
                    text = selectedSession?.appDisplayName ?: stringResource(Res.string.select_app),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (selectedSession != null) {
                    SessionSecurityIcon(selectedSession.transportSecurity)
                }
            }
        }
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            apps.forEach { app ->
                DropdownMenuItem(
                    leadingIcon = {
                        if (app.id == selectedSession?.id) {
                            Icon(imageVector = Icons.Default.Check, contentDescription = null)
                        } else {
                            AppIcon(app)
                        }
                    },
                    trailingIcon = { SessionSecurityIcon(app.transportSecurity) },
                    text = { Text(app.appDisplayName) },
                    onClick = {
                        onSelectSession(app)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun AppIcon(session: DebugSession?) {
    val bitmap: ImageBitmap? = remember(session?.appIconPngBase64) {
        session?.appIconPngBase64?.let { decodeIconOrNull(it) }
    }
    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
        )
    } else {
        Icon(imageVector = Icons.Default.Android, contentDescription = null)
    }
}
