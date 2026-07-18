package com.kitakkun.jetwhale.plugins.mirror.host

import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MirrorScreen(
    devices: List<MirrorDevice>,
    selectedDeviceId: String?,
    onSelectDevice: (String) -> Unit,
    frame: ImageBitmap?,
    mirroringEnabled: Boolean,
    onToggleMirroring: (Boolean) -> Unit,
    errorMessage: String?,
    onRefreshDevices: () -> Unit,
    onTap: (x: Int, y: Int) -> Unit,
    onSwipe: (fromX: Int, fromY: Int, toX: Int, toY: Int) -> Unit,
    onPressButton: (DeviceButton) -> Unit,
    onInputText: (String) -> Unit,
    onSaveScreenshot: () -> Unit,
) {
    val selectedDevice = devices.firstOrNull { it.id == selectedDeviceId }
    Scaffold(
        topBar = { TopAppBar(title = { Text("Device Mirror") }) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            DeviceSelectorRow(
                devices = devices,
                selectedDevice = selectedDevice,
                onSelectDevice = onSelectDevice,
                onRefreshDevices = onRefreshDevices,
                mirroringEnabled = mirroringEnabled,
                onToggleMirroring = onToggleMirroring,
            )
            ControlButtonsRow(
                platform = selectedDevice?.platform,
                enabled = selectedDevice != null,
                onPressButton = onPressButton,
                onSaveScreenshot = onSaveScreenshot,
            )
            TextInputRow(enabled = selectedDevice != null, onInputText = onInputText)
            errorMessage?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            MirrorFrame(
                frame = frame,
                hasDevice = selectedDevice != null,
                onTap = onTap,
                onSwipe = onSwipe,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeviceSelectorRow(
    devices: List<MirrorDevice>,
    selectedDevice: MirrorDevice?,
    onSelectDevice: (String) -> Unit,
    onRefreshDevices: () -> Unit,
    mirroringEnabled: Boolean,
    onToggleMirroring: (Boolean) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        var expanded by remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it },
            modifier = Modifier.weight(1f),
        ) {
            OutlinedTextField(
                value = selectedDevice?.let { "${it.name} (${it.platform})" } ?: "No device",
                onValueChange = {},
                readOnly = true,
                singleLine = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier.menuAnchor().fillMaxWidth(),
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                devices.forEach { device ->
                    DropdownMenuItem(
                        text = { Text("${device.name} (${device.platform})") },
                        onClick = {
                            onSelectDevice(device.id)
                            expanded = false
                        },
                    )
                }
            }
        }
        OutlinedButton(onClick = onRefreshDevices) { Text("Refresh") }
        Text("Mirror", style = MaterialTheme.typography.labelMedium)
        Switch(checked = mirroringEnabled, onCheckedChange = onToggleMirroring)
    }
}

@Composable
private fun ControlButtonsRow(
    platform: DevicePlatform?,
    enabled: Boolean,
    onPressButton: (DeviceButton) -> Unit,
    onSaveScreenshot: () -> Unit,
) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Button(onClick = onSaveScreenshot, enabled = enabled) { Text("Screenshot") }
        val buttons = when (platform) {
            DevicePlatform.IOS -> listOf(DeviceButton.HOME, DeviceButton.POWER, DeviceButton.BACKSPACE, DeviceButton.ENTER)
            else -> DeviceButton.entries
        }
        buttons.forEach { button ->
            OutlinedButton(onClick = { onPressButton(button) }, enabled = enabled) {
                Text(
                    when (button) {
                        DeviceButton.HOME -> "Home"
                        DeviceButton.BACK -> "Back"
                        DeviceButton.POWER -> "Power"
                        DeviceButton.VOLUME_UP -> "Vol +"
                        DeviceButton.VOLUME_DOWN -> "Vol -"
                        DeviceButton.BACKSPACE -> "⌫"
                        DeviceButton.ENTER -> "⏎"
                    },
                )
            }
        }
    }
}

@Composable
private fun TextInputRow(
    enabled: Boolean,
    onInputText: (String) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        var text by remember { mutableStateOf("") }
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            singleLine = true,
            placeholder = { Text("Type text to send to the device") },
            modifier = Modifier.weight(1f),
        )
        Button(
            onClick = {
                onInputText(text)
                text = ""
            },
            enabled = enabled && text.isNotEmpty(),
        ) {
            Text("Send")
        }
    }
}

@Composable
private fun MirrorFrame(
    frame: ImageBitmap?,
    hasDevice: Boolean,
    onTap: (x: Int, y: Int) -> Unit,
    onSwipe: (fromX: Int, fromY: Int, toX: Int, toY: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        if (frame == null) {
            Text(
                if (hasDevice) "Waiting for the first frame…" else "Boot an Android emulator or iOS simulator to start mirroring.",
                style = MaterialTheme.typography.bodyMedium,
            )
            return@Box
        }
        // aspectRatio makes the image composable's bounds coincide exactly with the drawn frame,
        // so pointer offsets scale linearly to device pixels.
        Image(
            bitmap = frame,
            contentDescription = "Mirrored device screen",
            modifier = Modifier
                .aspectRatio(frame.width.toFloat() / frame.height.toFloat())
                .pointerInput(frame.width, frame.height) {
                    detectTapGestures { offset ->
                        val scale = frame.width.toFloat() / size.width
                        onTap((offset.x * scale).toInt(), (offset.y * scale).toInt())
                    }
                }
                .pointerInput(frame.width, frame.height) {
                    var dragStart = Offset.Zero
                    var dragEnd = Offset.Zero
                    detectDragGestures(
                        onDragStart = { offset ->
                            dragStart = offset
                            dragEnd = offset
                        },
                        onDrag = { change, _ -> dragEnd = change.position },
                        onDragEnd = {
                            val scale = frame.width.toFloat() / size.width
                            onSwipe(
                                (dragStart.x * scale).toInt(),
                                (dragStart.y * scale).toInt(),
                                (dragEnd.x * scale).toInt(),
                                (dragEnd.y * scale).toInt(),
                            )
                        },
                    )
                },
        )
    }
}
