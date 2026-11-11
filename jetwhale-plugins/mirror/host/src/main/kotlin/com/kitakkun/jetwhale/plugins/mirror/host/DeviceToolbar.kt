package com.kitakkun.jetwhale.plugins.mirror.host

import androidx.annotation.VisibleForTesting
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kitakkun.jetwhale.annotations.ExperimentalJetWhaleApi
import com.kitakkun.jetwhale.host.sdk.rememberPersistent
import com.kitakkun.jetwhale.host.ui.JwBanner
import com.kitakkun.jetwhale.host.ui.JwButton
import com.kitakkun.jetwhale.host.ui.JwButtonStyle
import com.kitakkun.jetwhale.host.ui.JwEmptyState
import com.kitakkun.jetwhale.host.ui.JwHorizontalDivider
import com.kitakkun.jetwhale.host.ui.JwIcon
import com.kitakkun.jetwhale.host.ui.JwIconButton
import com.kitakkun.jetwhale.host.ui.JwShapes
import com.kitakkun.jetwhale.host.ui.JwSpacing
import com.kitakkun.jetwhale.host.ui.JwSplitPane
import com.kitakkun.jetwhale.host.ui.JwTag
import com.kitakkun.jetwhale.host.ui.JwText
import com.kitakkun.jetwhale.host.ui.JwTextField
import com.kitakkun.jetwhale.host.ui.JwTheme
import com.kitakkun.jetwhale.host.ui.JwTone
import com.kitakkun.jetwhale.host.ui.JwTooltip
import com.kitakkun.jetwhale.host.ui.JwVerticalDivider
import com.kitakkun.jetwhale.host.ui.jwAiOperatingRingWhileRunning
import com.kitakkun.jetwhale.host.ui.rememberJwSplitPaneState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.IOException
import kotlin.time.Clock

/**
 * The device picker and the device's controls, in groups that wrap to another line as a whole when the window is
 * too narrow for one: every control stays visible, and the capture group keeps to the line's end.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalJetWhaleApi::class)
@Composable
internal fun DeviceToolbar(
    pane: DevicePaneState,
    actions: MirrorActions,
    mcpCommands: MirrorMcpCommands,
    showCaptures: Boolean,
    livenessOf: (String) -> DeviceLiveness,
    onToggleCaptures: () -> Unit,
    onShowGrid: () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .background(JwTheme.colors.toolbarBackground)
                .padding(horizontal = JwSpacing.medium, vertical = JwSpacing.extraSmall),
            horizontalArrangement = Arrangement.spacedBy(JwSpacing.large),
            verticalArrangement = Arrangement.spacedBy(JwSpacing.extraSmall),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            DevicePicker(
                pane.devices,
                pane.device,
                livenessOf,
                onSelect = actions::select,
                onShowAll = onShowGrid,
                modifier = Modifier.widthIn(max = PICKER_MAX_WIDTH).jwAiOperatingRingWhileRunning(mcpCommands.listDevices, shape = JwShapes.small),
            )
            ButtonGroup(pane.capabilities.buttons.filter(NAVIGATION_AND_POWER_BUTTONS::contains), actions, mcpCommands)
            VolumeGroup(pane.device.kind, pane.capabilities, actions, mcpCommands)
            ScreenPowerButton(pane.screenPower, actions, mcpCommands)
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(JwSpacing.extraSmall, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                JwVerticalDivider(Modifier.height(CAPTURE_DIVIDER_HEIGHT).padding(end = JwSpacing.extraSmall))
                CaptureActions(pane.capabilities, pane.recordingSinceMillis, actions, mcpCommands)
                JwIconButton(tooltip = if (showCaptures) "Hide captures" else "Captures", onClick = onToggleCaptures, selected = showCaptures) {
                    JwIcon(imageVector = CapturesIcon, contentDescription = null)
                }
            }
        }
        JwHorizontalDivider()
    }
}

private val NAVIGATION_AND_POWER_BUTTONS = setOf(DeviceButton.Home, DeviceButton.Back, DeviceButton.Recents, DeviceButton.Power)

/**
 * Rings the group as a whole while an agent presses a button: [MirrorMcpCommands.pressButton] is one
 * command for every button, and which one it presses is not known here.
 */
@OptIn(ExperimentalJetWhaleApi::class)
@Composable
private fun ButtonGroup(buttons: List<DeviceButton>, actions: MirrorActions, mcpCommands: MirrorMcpCommands) {
    if (buttons.isEmpty()) return
    Row(Modifier.jwAiOperatingRingWhileRunning(mcpCommands.pressButton, shape = JwShapes.small), verticalAlignment = Alignment.CenterVertically) {
        buttons.forEach { button ->
            JwIconButton(tooltip = button.label, onClick = { actions.pressButton(button) }) {
                JwIcon(imageVector = button.icon, contentDescription = null)
            }
        }
    }
}

@Composable
private fun VolumeGroup(kind: DeviceKind, capabilities: DeviceCapabilities, actions: MirrorActions, mcpCommands: MirrorMcpCommands) {
    val volume = listOf(DeviceButton.VolumeUp, DeviceButton.VolumeDown)
    when {
        volume.any { it in capabilities.buttons } -> ButtonGroup(capabilities.buttons.filter(volume::contains), actions, mcpCommands)

        kind == DeviceKind.IosSimulator -> Row(verticalAlignment = Alignment.CenterVertically) {
            volume.forEach { button ->
                JwIconButton(tooltip = "${button.label}: a simulator's volume buttons cannot be pressed from here", onClick = {}, enabled = false) {
                    JwIcon(imageVector = button.icon, contentDescription = null)
                }
            }
        }

        else -> Unit
    }
}

@OptIn(ExperimentalJetWhaleApi::class)
@Composable
private fun ScreenPowerButton(screenPower: ScreenPower?, actions: MirrorActions, mcpCommands: MirrorMcpCommands) {
    val ring = Modifier.jwAiOperatingRingWhileRunning(mcpCommands.setScreen, shape = JwShapes.small)
    when (screenPower?.awake) {
        true -> JwIconButton(tooltip = "Screen off", onClick = actions::sleep, modifier = ring) { JwIcon(imageVector = ScreenOffIcon, contentDescription = null) }
        false -> JwIconButton(tooltip = "Wake", onClick = actions::wake, modifier = ring) { JwIcon(imageVector = WakeIcon, contentDescription = null) }
        null -> Unit
    }
}

/**
 * Starting and stopping a recording both ring whichever of the two buttons is shown, so the ring stays
 * on the control while it switches from one to the other.
 */
@OptIn(ExperimentalJetWhaleApi::class)
@Composable
private fun CaptureActions(capabilities: DeviceCapabilities, recordingSinceMillis: Long?, actions: MirrorActions, mcpCommands: MirrorMcpCommands) {
    val recordingRing = Modifier.jwAiOperatingRingWhileRunning(mcpCommands.startRecording, mcpCommands.stopRecording, shape = JwShapes.small)
    when {
        recordingSinceMillis != null -> RecordingButton(recordingSinceMillis, modifier = recordingRing, onStop = actions::stopSelectedRecording)

        capabilities.recording -> JwIconButton(tooltip = "Record", onClick = actions::recordSelectedDevice, modifier = recordingRing) {
            JwIcon(imageVector = RecordIcon, contentDescription = null)
        }

        else -> Unit
    }
    JwIconButton(tooltip = "Screenshot", onClick = actions::saveScreenshot, modifier = Modifier.jwAiOperatingRingWhileRunning(mcpCommands.captureScreenshot, shape = JwShapes.small)) {
        JwIcon(imageVector = ScreenshotIcon, contentDescription = null)
    }
}

/** A running recording can't be missed: red, counting, and a click away from stopping. */
@Composable
private fun RecordingButton(sinceMillis: Long, modifier: Modifier, onStop: () -> Unit) {
    val elapsed by produceState(recordingElapsed(System.currentTimeMillis() - sinceMillis), sinceMillis) {
        while (true) {
            delay(RECORDING_TICK_MILLIS)
            value = recordingElapsed(System.currentTimeMillis() - sinceMillis)
        }
    }
    JwTooltip(text = "Stop recording") {
        JwButton(
            text = elapsed,
            onClick = onStop,
            tone = JwTone.Error,
            leadingIcon = { RecordingDot() },
            modifier = modifier.semantics { contentDescription = "Stop recording, $elapsed recorded" },
        )
    }
}

@Composable
internal fun RecordingDot() {
    Box(Modifier.size(RECORDING_DOT_SIZE).background(JwTheme.colors.error, CircleShape))
}

private val RECORDING_DOT_SIZE = 8.dp
private val CAPTURE_DIVIDER_HEIGHT = 20.dp
private const val RECORDING_TICK_MILLIS = 1_000L

/** "0:12", or "1:02:03" past an hour. */
@VisibleForTesting
internal fun recordingElapsed(millis: Long): String {
    val seconds = (millis / 1_000).coerceAtLeast(0)
    val hours = seconds / 3_600
    val minutes = seconds / 60 % 60
    val rest = seconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, rest) else "%d:%02d".format(minutes, rest)
}
