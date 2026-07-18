package com.kitakkun.jetwhale.plugins.mirror.host

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MirrorScreen(
    devices: List<MirrorDevice>,
    selectedDeviceId: String?,
    onSelectDevice: (String) -> Unit,
    frames: Map<String, ImageBitmap>,
    mirroringEnabled: Boolean,
    onToggleMirroring: (Boolean) -> Unit,
    errorMessage: String?,
    onRefreshDevices: () -> Unit,
    onTap: (device: MirrorDevice, x: Int, y: Int) -> Unit,
    onSwipe: (device: MirrorDevice, fromX: Int, fromY: Int, toX: Int, toY: Int) -> Unit,
    onPressButton: (DeviceButton) -> Unit,
    onInputText: (String) -> Unit,
    onSaveScreenshot: () -> Unit,
    isRecording: Boolean,
    onToggleRecording: () -> Unit,
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
            MirrorToolbar(
                mirroringEnabled = mirroringEnabled,
                onToggleMirroring = onToggleMirroring,
                onRefreshDevices = onRefreshDevices,
            )
            ControlButtonsRow(
                platform = selectedDevice?.platform,
                enabled = selectedDevice != null,
                onPressButton = onPressButton,
                onSaveScreenshot = onSaveScreenshot,
                isRecording = isRecording,
                onToggleRecording = onToggleRecording,
            )
            TextInputRow(enabled = selectedDevice != null, onInputText = onInputText)
            errorMessage?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            if (devices.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "Boot an Android emulator or iOS simulator to start mirroring.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            } else {
                // All connected devices side by side; the highlighted one receives the
                // button/text controls, while taps and swipes go to the frame under the cursor.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    devices.forEach { device ->
                        DeviceMirrorPane(
                            device = device,
                            frame = frames[device.id],
                            isSelected = device.id == selectedDeviceId,
                            onSelect = { onSelectDevice(device.id) },
                            onTap = onTap,
                            onSwipe = onSwipe,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MirrorToolbar(
    mirroringEnabled: Boolean,
    onToggleMirroring: (Boolean) -> Unit,
    onRefreshDevices: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
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
    isRecording: Boolean,
    onToggleRecording: () -> Unit,
) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Button(onClick = onSaveScreenshot, enabled = enabled) { Text("Screenshot") }
        Button(onClick = onToggleRecording, enabled = enabled || isRecording) {
            Text(if (isRecording) "Stop Rec" else "Record")
        }
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
            placeholder = { Text("Type text to send to the selected device") },
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
private fun DeviceMirrorPane(
    device: MirrorDevice,
    frame: ImageBitmap?,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onTap: (device: MirrorDevice, x: Int, y: Int) -> Unit,
    onSwipe: (device: MirrorDevice, fromX: Int, fromY: Int, toX: Int, toY: Int) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxHeight(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "${device.name} (${device.platform})",
            style = MaterialTheme.typography.labelMedium,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (frame == null) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .aspectRatio(9f / 16f)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(4.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text("Waiting…", style = MaterialTheme.typography.bodySmall)
            }
            return@Column
        }
        // aspectRatio makes the image composable's bounds coincide exactly with the drawn frame,
        // so pointer offsets scale linearly to device pixels.
        Image(
            bitmap = frame,
            contentDescription = "Mirrored screen of ${device.name}",
            modifier = Modifier
                .weight(1f)
                .aspectRatio(frame.width.toFloat() / frame.height.toFloat())
                .border(
                    width = 2.dp,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                    shape = RoundedCornerShape(4.dp),
                )
                .pointerInput(device.id, frame.width, frame.height) {
                    detectTapGestures { offset ->
                        onSelect()
                        val scale = frame.width.toFloat() / size.width
                        onTap(device, (offset.x * scale).toInt(), (offset.y * scale).toInt())
                    }
                }
                .pointerInput(device.id, frame.width, frame.height) {
                    var dragStart = Offset.Zero
                    var dragEnd = Offset.Zero
                    detectDragGestures(
                        onDragStart = { offset ->
                            onSelect()
                            dragStart = offset
                            dragEnd = offset
                        },
                        onDrag = { change, _ -> dragEnd = change.position },
                        onDragEnd = {
                            val scale = frame.width.toFloat() / size.width
                            onSwipe(
                                device,
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
