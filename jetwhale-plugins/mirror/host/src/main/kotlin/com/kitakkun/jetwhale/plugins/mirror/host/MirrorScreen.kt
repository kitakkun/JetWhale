package com.kitakkun.jetwhale.plugins.mirror.host

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.kitakkun.jetwhale.host.sdk.rememberPersistent
import com.kitakkun.jetwhale.host.ui.JwBanner
import com.kitakkun.jetwhale.host.ui.JwButton
import com.kitakkun.jetwhale.host.ui.JwButtonStyle
import com.kitakkun.jetwhale.host.ui.JwEmptyState
import com.kitakkun.jetwhale.host.ui.JwListItem
import com.kitakkun.jetwhale.host.ui.JwSectionHeader
import com.kitakkun.jetwhale.host.ui.JwSpacing
import com.kitakkun.jetwhale.host.ui.JwSplitPane
import com.kitakkun.jetwhale.host.ui.JwTag
import com.kitakkun.jetwhale.host.ui.JwText
import com.kitakkun.jetwhale.host.ui.JwTextField
import com.kitakkun.jetwhale.host.ui.JwTheme
import com.kitakkun.jetwhale.host.ui.JwTone
import com.kitakkun.jetwhale.host.ui.JwToolbar
import com.kitakkun.jetwhale.host.ui.rememberJwSplitPaneState

/** The live screen keeps most of the pane while the captures sit beside it. */
private const val VIDEO_FRACTION = 0.55f

/** The device list is a column of names; the screen beside it needs the room. */
private const val DEVICE_LIST_FRACTION = 0.24f

/**
 * Binds the mirror to the screen, and keeps it working only while the screen is shown: the device
 * list refreshes and the selected device streams for as long as this is in composition.
 */
@Composable
internal fun MirrorScreenRoot(mirror: DeviceMirror, modifier: Modifier = Modifier) {
    LaunchedEffect(mirror) { mirror.keepDevicesCurrent() }
    val device = mirror.selectedDevice
    LaunchedEffect(device?.id) { device?.let { mirror.mirror(it) } }
    val captures = mirror.captures
    LaunchedEffect(captures) { captures.restoreFolder() }
    LaunchedEffect(captures.library, device?.listing) { captures.showDevice(device?.listing) }
    var showCaptures by rememberPersistent("showCaptures", default = false)
    MirrorScreen(
        devices = mirror.devices.map(MirrorDevice::listing),
        capabilities = device?.controller?.capabilities,
        missingTools = mirror.missingTools,
        selectedId = mirror.selectedId,
        state = mirror.state,
        status = mirror.status,
        screenPower = mirror.screenPower,
        recording = mirror.recordingDeviceId != null && mirror.recordingDeviceId == mirror.selectedId,
        surface = mirror.surface,
        actions = mirror,
        showCaptures = showCaptures,
        onToggleCaptures = { showCaptures = !showCaptures },
        capturesPanel = {
            CapturesPanel(
                captures = captures.captures,
                allDevices = captures.allDevices,
                kind = captures.kind,
                day = captures.day,
                selected = captures.selected,
                status = captures.status,
                thumbnails = captures,
                actions = captures,
            )
        },
        modifier = modifier,
    )
}

@Composable
internal fun MirrorScreen(
    devices: List<DeviceListing>,
    capabilities: DeviceCapabilities?,
    missingTools: List<String>,
    selectedId: String?,
    state: MirrorState,
    status: MirrorStatus?,
    screenPower: ScreenPower?,
    recording: Boolean,
    surface: MirrorSurface,
    actions: MirrorActions,
    showCaptures: Boolean,
    onToggleCaptures: () -> Unit,
    capturesPanel: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    JwSplitPane(
        modifier = modifier.fillMaxSize(),
        state = rememberJwSplitPaneState(DEVICE_LIST_FRACTION),
        first = { DeviceList(devices = devices, missingTools = missingTools, selectedId = selectedId, onSelect = actions::select) },
        second = {
            val device = devices.firstOrNull { it.id == selectedId }
            if (device == null || capabilities == null) {
                JwEmptyState(
                    title = "No device",
                    description = "Start an Android emulator, boot an iOS simulator, or connect a device by USB. It appears here within a few seconds.",
                )
            } else {
                val pane = DevicePaneState(device, capabilities, state, status, screenPower, recording)
                DevicePane(pane, surface, actions, showCaptures, onToggleCaptures, capturesPanel)
            }
        },
    )
}

@Composable
private fun DeviceList(
    devices: List<DeviceListing>,
    missingTools: List<String>,
    selectedId: String?,
    onSelect: (String) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        missingTools.forEach { JwBanner(text = it, tone = JwTone.Warning) }
        LazyColumn(Modifier.fillMaxSize()) {
            DevicePlatform.entries.forEach { platform ->
                val onPlatform = devices.filter { it.kind.platform == platform }
                if (onPlatform.isEmpty()) return@forEach
                item(key = platform) { JwSectionHeader(title = platform.label, count = onPlatform.size) }
                items(onPlatform, key = DeviceListing::id) { device ->
                    JwListItem(
                        text = device.name,
                        supportingText = listOfNotNull(device.kind.label, device.osVersion).joinToString(" · "),
                        selected = device.id == selectedId,
                        onClick = { onSelect(device.id) },
                        trailingContent = if (device.kind == DeviceKind.IosDevice) {
                            { JwTag(text = "View only") }
                        } else {
                            null
                        },
                    )
                }
            }
        }
    }
}

/** What the device pane shows about the selected device. */
private class DevicePaneState(
    val device: DeviceListing,
    val capabilities: DeviceCapabilities,
    val state: MirrorState,
    val status: MirrorStatus?,
    val screenPower: ScreenPower?,
    val recording: Boolean,
)

@Composable
private fun DevicePane(
    pane: DevicePaneState,
    surface: MirrorSurface,
    actions: MirrorActions,
    showCaptures: Boolean,
    onToggleCaptures: () -> Unit,
    capturesPanel: @Composable () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        JwToolbar(
            title = pane.device.name,
            actions = {
                DeviceActions(pane.capabilities, pane.screenPower, pane.recording, actions)
                JwButton(text = "Captures", onClick = onToggleCaptures, style = if (showCaptures) JwButtonStyle.Primary else JwButtonStyle.Secondary)
            },
        )
        pane.status?.let { JwBanner(text = it.message, tone = if (it.isError) JwTone.Error else JwTone.Neutral) }
        if (showCaptures) {
            JwSplitPane(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                state = rememberJwSplitPaneState(VIDEO_FRACTION),
                first = { LiveView(pane, surface, actions) },
                second = capturesPanel,
            )
        } else {
            Box(Modifier.weight(1f).fillMaxWidth()) { LiveView(pane, surface, actions) }
        }
    }
}

@Composable
private fun LiveView(pane: DevicePaneState, surface: MirrorSurface, actions: MirrorActions) {
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            MirrorVideo(surface = surface, interactive = pane.capabilities.input, onTap = actions::tap, onSwipe = actions::swipe, modifier = Modifier.fillMaxSize())
            StateOverlay(pane.device, pane.state)
            // A screen that is off streams nothing, so the mirror would otherwise just stay black.
            if (pane.screenPower?.awake == false) ScreenOffOverlay(onWake = actions::wake)
        }
        if (pane.capabilities.input) TextInput(onSend = actions::inputText)
        MirrorStatsLine(surface = surface, state = pane.state)
    }
}

@Composable
private fun DeviceActions(capabilities: DeviceCapabilities, screenPower: ScreenPower?, recording: Boolean, actions: MirrorActions) {
    Row(horizontalArrangement = Arrangement.spacedBy(JwSpacing.extraSmall), verticalAlignment = Alignment.CenterVertically) {
        capabilities.buttons.forEach { button ->
            JwButton(text = button.label, onClick = { actions.pressButton(button) }, style = JwButtonStyle.Text)
        }
        // Unlike Power, which toggles, these say which way they go, and Wake also lifts a plain lock screen.
        when (screenPower?.awake) {
            true -> JwButton(text = "Screen off", onClick = actions::sleep, style = JwButtonStyle.Text)
            false -> JwButton(text = "Wake", onClick = actions::wake, style = JwButtonStyle.Text)
            null -> Unit
        }
        if (capabilities.recording) {
            JwButton(text = if (recording) "Stop recording" else "Record", onClick = actions::toggleRecording, tone = if (recording) JwTone.Error else JwTone.Accent)
        }
        JwButton(text = "Screenshot", onClick = actions::saveScreenshot)
    }
}

/** What the mirror has to say in place of a picture: connecting, or why nothing arrives. */
@Composable
private fun StateOverlay(device: DeviceListing, state: MirrorState) {
    when (state) {
        is MirrorState.Idle, is MirrorState.Streaming, is MirrorState.Polling -> Unit

        is MirrorState.Connecting -> JwEmptyState(title = "Connecting to ${device.name}…")

        is MirrorState.NoFrames -> JwEmptyState(
            title = "No picture from ${device.name}",
            description = state.hints.joinToString("\n") { "• $it" },
        )

        is MirrorState.Failed -> JwEmptyState(title = "Cannot mirror ${device.name}", description = state.message)
    }
}

@Composable
private fun ScreenOffOverlay(onWake: () -> Unit) {
    Box(Modifier.fillMaxSize().background(JwTheme.colors.panelBackground)) {
        JwEmptyState(
            title = "The device's screen is off",
            description = "Nothing reaches the mirror until it is on again.",
            action = { JwButton(text = "Wake", onClick = onWake, style = JwButtonStyle.Primary) },
        )
    }
}

@Composable
private fun TextInput(onSend: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = JwSpacing.large, vertical = JwSpacing.small),
        horizontalArrangement = Arrangement.spacedBy(JwSpacing.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        JwTextField(value = text, onValueChange = { text = it }, placeholder = "Text to type on the device", modifier = Modifier.weight(1f))
        JwButton(
            text = "Type",
            style = JwButtonStyle.Secondary,
            enabled = text.isNotEmpty(),
            onClick = {
                onSend(text)
                text = ""
            },
        )
    }
}

@Composable
private fun MirrorStatsLine(surface: MirrorSurface, state: MirrorState) {
    val source = when (state) {
        is MirrorState.Streaming -> "Live"
        is MirrorState.Polling -> "Screenshots"
        else -> return
    }
    val stats = surface.stats
    JwText(
        text = "$source · ${stats.receivedFps} fps in, ${stats.displayedFps} shown · read + decode ${"%.1f".format(stats.decodeMillis)} ms · copy ${"%.1f".format(stats.copyMillis)} ms · draw ${"%.1f".format(stats.drawMillis)} ms",
        style = JwTheme.textStyles.labelSmall,
        color = JwTheme.colors.textSecondary,
        modifier = Modifier.padding(horizontal = JwSpacing.large, vertical = JwSpacing.extraSmall),
    )
}
