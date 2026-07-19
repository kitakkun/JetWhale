package com.kitakkun.jetwhale.plugins.mirror.host

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

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
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        MirrorToolbar(
            platform = selectedDevice?.platform,
            enabled = selectedDevice != null,
            mirroringEnabled = mirroringEnabled,
            onToggleMirroring = onToggleMirroring,
            onRefreshDevices = onRefreshDevices,
            onPressButton = onPressButton,
            onSaveScreenshot = onSaveScreenshot,
            isRecording = isRecording,
            onToggleRecording = onToggleRecording,
            onInputText = onInputText,
        )
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
                        frames = frames,
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

// One slim icon row: device controls on the left, the direct key-input field on the right.
@Composable
private fun MirrorToolbar(
    platform: DevicePlatform?,
    enabled: Boolean,
    mirroringEnabled: Boolean,
    onToggleMirroring: (Boolean) -> Unit,
    onRefreshDevices: () -> Unit,
    onPressButton: (DeviceButton) -> Unit,
    onSaveScreenshot: () -> Unit,
    isRecording: Boolean,
    onToggleRecording: () -> Unit,
    onInputText: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        ToolbarIconButton(onClick = onRefreshDevices, tooltip = "Refresh devices") {
            GlyphIcon("⟳")
        }
        ToolbarIconButton(onClick = { onToggleMirroring(!mirroringEnabled) }, tooltip = "Toggle mirroring") {
            GlyphIcon(if (mirroringEnabled) "⏸" else "▶")
        }
        ToolbarIconButton(onClick = onSaveScreenshot, enabled = enabled, tooltip = "Save screenshot") {
            GlyphIcon("📷")
        }
        ToolbarIconButton(onClick = onToggleRecording, enabled = enabled || isRecording, tooltip = "Record screen") {
            GlyphIcon(if (isRecording) "⏹" else "⏺", tint = if (isRecording) MaterialTheme.colorScheme.error else null)
        }
        ToolbarIconButton(onClick = { onPressButton(DeviceButton.HOME) }, enabled = enabled, tooltip = "Home") {
            GlyphIcon("⌂")
        }
        if (platform != DevicePlatform.IOS) {
            ToolbarIconButton(onClick = { onPressButton(DeviceButton.BACK) }, enabled = enabled, tooltip = "Back") {
                GlyphIcon("←")
            }
        }
        ToolbarIconButton(onClick = { onPressButton(DeviceButton.POWER) }, enabled = enabled, tooltip = "Power") {
            GlyphIcon("⏻")
        }
        if (platform != DevicePlatform.IOS) {
            ToolbarIconButton(onClick = { onPressButton(DeviceButton.VOLUME_UP) }, enabled = enabled, tooltip = "Volume up") {
                GlyphIcon("🔊")
            }
            ToolbarIconButton(onClick = { onPressButton(DeviceButton.VOLUME_DOWN) }, enabled = enabled, tooltip = "Volume down") {
                GlyphIcon("🔉")
            }
        }
        DirectKeyInputField(
            enabled = enabled,
            onSendText = onInputText,
            onSendKey = onPressButton,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ToolbarIconButton(
    onClick: () -> Unit,
    tooltip: String,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    TooltipArea(tooltip = { TooltipBubble(tooltip) }) {
        IconButton(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.size(28.dp),
        ) {
            content()
        }
    }
}

@Composable
private fun TooltipBubble(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 3.dp),
    )
}

@Composable
private fun GlyphIcon(glyph: String, tint: Color? = null) {
    Text(
        text = glyph,
        style = MaterialTheme.typography.bodyMedium,
        color = tint ?: MaterialTheme.colorScheme.onSurface,
    )
}

/**
 * Buffers typed text — including IME composition such as Japanese conversion, which must never
 * be interfered with mid-session — and sends the whole buffer to the selected device on Enter.
 * Backspace/Enter on an empty buffer are sent as device keys instead.
 */
@Composable
private fun DirectKeyInputField(
    enabled: Boolean,
    onSendText: (String) -> Unit,
    onSendKey: (DeviceButton) -> Unit,
) {
    var value by remember { mutableStateOf(TextFieldValue("")) }
    BasicTextField(
        value = value,
        onValueChange = { value = it },
        enabled = enabled,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurface),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        modifier = Modifier
            .width(220.dp)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp, vertical = 5.dp)
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                // While the IME is composing, every key (including Enter, which commits the
                // conversion) belongs to the IME — never intercept.
                if (value.composition != null) return@onPreviewKeyEvent false
                when {
                    event.key == Key.Enter && value.text.isNotEmpty() -> {
                        onSendText(value.text)
                        value = TextFieldValue("")
                        true
                    }

                    event.key == Key.Enter -> {
                        onSendKey(DeviceButton.ENTER)
                        true
                    }

                    event.key == Key.Backspace && value.text.isEmpty() -> {
                        onSendKey(DeviceButton.BACKSPACE)
                        true
                    }

                    else -> false
                }
            },
        decorationBox = { innerTextField ->
            Box(contentAlignment = Alignment.CenterStart) {
                if (value.text.isEmpty()) {
                    Text(
                        "⌨ Type, Enter to send",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                innerTextField()
            }
        },
    )
}

@Composable
private fun DeviceMirrorPane(
    device: MirrorDevice,
    frames: Map<String, ImageBitmap>,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onTap: (device: MirrorDevice, x: Int, y: Int) -> Unit,
    onSwipe: (device: MirrorDevice, fromX: Int, fromY: Int, toX: Int, toY: Int) -> Unit,
) {
    // Composition only depends on the frame's dimensions (via derivedStateOf), which change on
    // rotation at most; the pixels are read inside the draw phase, so a new frame triggers just a
    // repaint of this pane instead of recomposing and re-laying-out the whole screen.
    val frameSize by remember(device.id) {
        derivedStateOf { frames[device.id]?.let { IntSize(it.width, it.height) } }
    }
    Column(
        modifier = Modifier.fillMaxHeight(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "${device.name} (${device.platform})",
            style = MaterialTheme.typography.labelSmall,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val size = frameSize
        if (size == null) {
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
        // aspectRatio makes this pane's bounds coincide exactly with the drawn frame, so pointer
        // offsets scale linearly to device pixels.
        Box(
            modifier = Modifier
                .weight(1f)
                .aspectRatio(size.width.toFloat() / size.height.toFloat())
                .drawBehind {
                    frames[device.id]?.let { frame ->
                        drawImage(
                            image = frame,
                            dstSize = IntSize(this.size.width.roundToInt(), this.size.height.roundToInt()),
                        )
                    }
                }
                .border(
                    width = 2.dp,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                    shape = RoundedCornerShape(4.dp),
                )
                .pointerInput(device.id) {
                    detectTapGestures { offset ->
                        onSelect()
                        val frame = frames[device.id] ?: return@detectTapGestures
                        val scale = frame.width.toFloat() / this.size.width
                        onTap(device, (offset.x * scale).toInt(), (offset.y * scale).toInt())
                    }
                }
                .pointerInput(device.id) {
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
                            val frame = frames[device.id] ?: return@detectDragGestures
                            val scale = frame.width.toFloat() / this.size.width
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
