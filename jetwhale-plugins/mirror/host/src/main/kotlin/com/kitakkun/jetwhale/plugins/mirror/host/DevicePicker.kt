package com.kitakkun.jetwhale.plugins.mirror.host

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.kitakkun.jetwhale.host.ui.JwDropdownButton
import com.kitakkun.jetwhale.host.ui.JwMenuItem
import com.kitakkun.jetwhale.host.ui.JwSectionHeader
import com.kitakkun.jetwhale.host.ui.JwStatusDot
import com.kitakkun.jetwhale.host.ui.JwText
import com.kitakkun.jetwhale.host.ui.JwTheme
import com.kitakkun.jetwhale.host.ui.JwTone

/** What the mirror last knew about a device's screen, shown as a dot next to its name. */
internal enum class DeviceLiveness(val tone: JwTone, val description: String) {
    Live(JwTone.Success, "Showing its screen"),
    ScreenOff(JwTone.Warning, "Screen off"),
    Unavailable(JwTone.Error, "Unavailable"),

    /** Not watched lately: neither the single view nor the grid has shown it. */
    Unknown(JwTone.Neutral, "Not watched"),
}

/**
 * What the mirror shows, and the way to change it: one device by name, or every device as a grid.
 * It takes the place of a device column, so the screen gets the window's width. [selected] is null
 * while the grid is shown.
 */
@Composable
internal fun DevicePicker(
    devices: List<DeviceListing>,
    selected: DeviceListing?,
    livenessOf: (String) -> DeviceLiveness,
    onSelect: (String) -> Unit,
    onShowAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    JwDropdownButton(
        text = selected?.name ?: "$ALL_DEVICES (${devices.size})",
        expanded = expanded,
        onExpandedChange = { expanded = it },
        leadingIcon = selected?.let { device -> { LivenessDot(livenessOf(device.id)) } },
        modifier = modifier,
    ) {
        JwMenuItem(
            text = ALL_DEVICES,
            selected = selected == null,
            trailingIcon = { SecondaryText("Grid of every screen") },
            onClick = {
                expanded = false
                onShowAll()
            },
        )
        DevicePlatform.entries.forEach { platform ->
            val onPlatform = devices.filter { it.kind.platform == platform }
            if (onPlatform.isEmpty()) return@forEach
            JwSectionHeader(title = platform.label, count = onPlatform.size)
            onPlatform.forEach { device ->
                JwMenuItem(
                    text = device.name,
                    selected = device.id == selected?.id,
                    leadingIcon = { LivenessDot(livenessOf(device.id)) },
                    trailingIcon = { SecondaryText(listOfNotNull(device.kind.label, device.osVersion, "View only".takeIf { device.kind == DeviceKind.IosDevice }).joinToString(" · ")) },
                    onClick = {
                        expanded = false
                        onSelect(device.id)
                    },
                )
            }
        }
    }
}

private const val ALL_DEVICES = "All devices"

@Composable
private fun SecondaryText(text: String) {
    JwText(
        text = text,
        style = JwTheme.textStyles.bodySmall,
        color = JwTheme.colors.textSecondary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
internal fun LivenessDot(liveness: DeviceLiveness) {
    JwStatusDot(
        tone = liveness.tone,
        filled = liveness != DeviceLiveness.Unknown,
        modifier = Modifier.semantics { contentDescription = liveness.description },
    )
}
