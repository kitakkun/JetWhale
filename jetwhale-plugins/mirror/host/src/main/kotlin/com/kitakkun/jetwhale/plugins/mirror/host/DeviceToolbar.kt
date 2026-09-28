package com.kitakkun.jetwhale.plugins.mirror.host

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
import com.kitakkun.jetwhale.host.sdk.rememberPersistent
import com.kitakkun.jetwhale.host.ui.JwBanner
import com.kitakkun.jetwhale.host.ui.JwButton
import com.kitakkun.jetwhale.host.ui.JwButtonStyle
import com.kitakkun.jetwhale.host.ui.JwEmptyState
import com.kitakkun.jetwhale.host.ui.JwHorizontalDivider
import com.kitakkun.jetwhale.host.ui.JwIcon
import com.kitakkun.jetwhale.host.ui.JwIconButton
import com.kitakkun.jetwhale.host.ui.JwSpacing
import com.kitakkun.jetwhale.host.ui.JwSplitPane
import com.kitakkun.jetwhale.host.ui.JwTag
import com.kitakkun.jetwhale.host.ui.JwText
import com.kitakkun.jetwhale.host.ui.JwTextField
import com.kitakkun.jetwhale.host.ui.JwTheme
import com.kitakkun.jetwhale.host.ui.JwTone
import com.kitakkun.jetwhale.host.ui.JwTooltip
import com.kitakkun.jetwhale.host.ui.JwVerticalDivider
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
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DeviceToolbar(
    pane: DevicePaneState,
    actions: MirrorActions,
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
            DevicePicker(pane.devices, pane.device, livenessOf, onSelect = actions::select, onShowAll = onShowGrid, modifier = Modifier.widthIn(max = PICKER_MAX_WIDTH))
            ButtonGroup(pane.capabilities.buttons.filter(NAVIGATION_BUTTONS::contains), actions)
            VolumeGroup(pane.device.kind, pane.capabilities, actions)
            ScreenPowerButton(pane.screenPower, actions)
            // Pushed to the line's end, and wrapped to a line of its own only as a whole.
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(JwSpacing.extraSmall, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                JwVerticalDivider(Modifier.height(CAPTURE_DIVIDER_HEIGHT).padding(end = JwSpacing.extraSmall))
                CaptureActions(pane.capabilities, pane.recordingSinceMillis, pane.recordingElsewhere, actions)
                JwIconButton(tooltip = if (showCaptures) "Hide captures" else "Captures", onClick = onToggleCaptures, selected = showCaptures) {
                    JwIcon(imageVector = CapturesIcon, contentDescription = null)
                }
            }
        }
        JwHorizontalDivider()
    }
}

private val NAVIGATION_BUTTONS = setOf(DeviceButton.Home, DeviceButton.Back, DeviceButton.Recents, DeviceButton.Power)

@Composable
private fun ButtonGroup(buttons: List<DeviceButton>, actions: MirrorActions) {
    if (buttons.isEmpty()) return
    Row(verticalAlignment = Alignment.CenterVertically) {
        buttons.forEach { button ->
            JwIconButton(tooltip = button.label, onClick = { actions.pressButton(button) }) {
                JwIcon(imageVector = button.icon, contentDescription = null)
            }
        }
    }
}

@Composable
private fun VolumeGroup(kind: DeviceKind, capabilities: DeviceCapabilities, actions: MirrorActions) {
    val volume = listOf(DeviceButton.VolumeUp, DeviceButton.VolumeDown)
    when {
        volume.any { it in capabilities.buttons } -> ButtonGroup(capabilities.buttons.filter(volume::contains), actions)

        // Shown disabled rather than left out, so their absence is explained rather than puzzling.
        kind == DeviceKind.IosSimulator -> Row(verticalAlignment = Alignment.CenterVertically) {
            volume.forEach { button ->
                JwIconButton(tooltip = "${button.label}: idb cannot press a simulator's volume buttons", onClick = {}, enabled = false) {
                    JwIcon(imageVector = button.icon, contentDescription = null)
                }
            }
        }

        else -> Unit
    }
}

// Unlike Power, which toggles, these say which way they go, and Wake also lifts a plain lock screen.
@Composable
private fun ScreenPowerButton(screenPower: ScreenPower?, actions: MirrorActions) {
    when (screenPower?.awake) {
        true -> JwIconButton(tooltip = "Screen off", onClick = actions::sleep) { JwIcon(imageVector = ScreenOffIcon, contentDescription = null) }
        false -> JwIconButton(tooltip = "Wake", onClick = actions::wake) { JwIcon(imageVector = WakeIcon, contentDescription = null) }
        null -> Unit
    }
}

@Composable
private fun CaptureActions(capabilities: DeviceCapabilities, recordingSinceMillis: Long?, recordingElsewhere: String?, actions: MirrorActions) {
    when {
        recordingElsewhere != null -> JwButton(
            text = "Stop recording on $recordingElsewhere",
            onClick = actions::toggleRecording,
            tone = JwTone.Error,
            leadingIcon = { RecordingDot() },
        )

        recordingSinceMillis != null -> RecordingButton(recordingSinceMillis, onStop = actions::toggleRecording)

        capabilities.recording -> JwIconButton(tooltip = "Record", onClick = actions::toggleRecording) {
            JwIcon(imageVector = RecordIcon, contentDescription = null)
        }

        else -> Unit
    }
    JwIconButton(tooltip = "Screenshot", onClick = actions::saveScreenshot) {
        JwIcon(imageVector = ScreenshotIcon, contentDescription = null)
    }
}

/** A running recording can't be missed: red, counting, and a click away from stopping. */
@Composable
private fun RecordingButton(sinceMillis: Long, onStop: () -> Unit) {
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
            modifier = Modifier.semantics { contentDescription = "Stop recording, $elapsed recorded" },
        )
    }
}

@Composable
private fun RecordingDot() {
    Box(Modifier.size(RECORDING_DOT_SIZE).background(JwTheme.colors.error, CircleShape))
}

private val RECORDING_DOT_SIZE = 8.dp
private val CAPTURE_DIVIDER_HEIGHT = 20.dp
private const val RECORDING_TICK_MILLIS = 1_000L

/** "0:12", or "1:02:03" past an hour. */
internal fun recordingElapsed(millis: Long): String {
    val seconds = (millis / 1_000).coerceAtLeast(0)
    val hours = seconds / 3_600
    val minutes = seconds / 60 % 60
    val rest = seconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, rest) else "%d:%02d".format(minutes, rest)
}
