package com.kitakkun.jetwhale.plugins.mirror.host

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.kitakkun.jetwhale.host.ui.JwButton
import com.kitakkun.jetwhale.host.ui.JwButtonStyle
import com.kitakkun.jetwhale.host.ui.JwCheckbox
import com.kitakkun.jetwhale.host.ui.JwDialog
import com.kitakkun.jetwhale.host.ui.JwIcon
import com.kitakkun.jetwhale.host.ui.JwIconButton
import com.kitakkun.jetwhale.host.ui.JwText
import com.kitakkun.jetwhale.host.ui.JwTone

/**
 * What the grid's Record all control shows.
 *
 * @property recordableDevices the devices Record all would start: those that can record and are not recording.
 * @property warningSuppressed the user asked not to be warned about the load again.
 */
internal class GridRecordingState(
    val recordingDeviceIds: Set<String>,
    val recordableDevices: List<DeviceListing>,
    val warningSuppressed: Boolean,
)

/** What the grid's Record all control can ask for. */
internal interface GridRecordingActions {
    fun recordAll()

    fun stopAll()

    /** Records that the load warning is not to be shown again. */
    fun suppressWarning()
}

/**
 * Record all, or Stop all while any device records. The first Record all warns about the load
 * first, until the user asks not to be warned again.
 */
@Composable
internal fun RecordAllButton(state: GridRecordingState, actions: GridRecordingActions) {
    var confirming by remember { mutableStateOf(false) }
    if (state.recordingDeviceIds.isNotEmpty()) {
        JwButton(text = "Stop all (${state.recordingDeviceIds.size})", onClick = actions::stopAll, tone = JwTone.Error, leadingIcon = { RecordingDot() })
    } else {
        JwIconButton(
            tooltip = "Record every device",
            enabled = state.recordableDevices.isNotEmpty(),
            onClick = { if (state.warningSuppressed) actions.recordAll() else confirming = true },
        ) {
            JwIcon(imageVector = RecordIcon, contentDescription = null)
        }
    }
    if (confirming) {
        RecordAllWarningDialog(
            targets = state.recordableDevices,
            onRecord = { dontShowAgain ->
                confirming = false
                if (dontShowAgain) actions.suppressWarning()
                actions.recordAll()
            },
            onCancel = { confirming = false },
        )
    }
}

@Composable
internal fun RecordAllWarningDialog(targets: List<DeviceListing>, onRecord: (dontShowAgain: Boolean) -> Unit, onCancel: () -> Unit) {
    var dontShowAgain by remember { mutableStateOf(false) }
    JwDialog(
        title = "Record ${targets.size} ${if (targets.size == 1) "device" else "devices"} at once?",
        closeLabel = "Cancel",
        onDismissRequest = onCancel,
        confirmButton = { JwButton(text = "Record", style = JwButtonStyle.Primary, onClick = { onRecord(dontShowAgain) }) },
        dismissButton = { JwButton(text = "Cancel", onClick = onCancel) },
    ) {
        JwText(text = recordAllLoadNote(targets.map(DeviceListing::kind)))
        JwCheckbox(checked = dontShowAgain, label = "Don't show again", onCheckedChange = { dontShowAgain = it })
    }
}

/** Why recording [kinds] at once weighs on this machine, naming the kinds that weigh the most. */
internal fun recordAllLoadNote(kinds: List<DeviceKind>): String {
    val simulators = kinds.count { it == DeviceKind.IosSimulator }
    val general = "Recording several devices at once is heavy on this machine and can make the live views drop frames."
    val light = "Android devices encode on the device, and physical iPhones save a copy of the video they already send."
    if (simulators == 0) return "$general $light"
    val simulatorNote = if (simulators == 1) "The iOS simulator encodes its video on this Mac, which weighs the most." else "The $simulators iOS simulators encode their video on this Mac, which weighs the most."
    return "$general $simulatorNote $light"
}
