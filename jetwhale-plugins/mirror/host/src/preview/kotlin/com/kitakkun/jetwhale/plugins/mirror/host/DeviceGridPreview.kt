package com.kitakkun.jetwhale.plugins.mirror.host

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.kitakkun.jetwhale.host.ui.JwSnackbarHostState
import com.kitakkun.jetwhale.host.ui.JwTheme
import kotlinx.coroutines.awaitCancellation

private val gridDevices = listOf(
    DeviceListing(id = "emulator-5554", name = "Pixel 9", kind = DeviceKind.AndroidEmulator, osVersion = null),
    DeviceListing(id = "0A1B2C3D-SIMULATOR", name = "iPhone 16", kind = DeviceKind.IosSimulator, osVersion = "iOS 18.5"),
    DeviceListing(id = "00008110-DEVICE", name = "iPhone 13", kind = DeviceKind.IosDevice, osVersion = "iOS 26.6.1"),
)

private val gridStates = mapOf(
    "emulator-5554" to ThumbnailState.ScreenOff,
    "0A1B2C3D-SIMULATOR" to ThumbnailState.Loading,
    "00008110-DEVICE" to ThumbnailState.Failed("idb could not reach the device; unlock it and trust this Mac"),
)

@Preview
@Composable
private fun DeviceGridPreview() {
    JwTheme(darkTheme = true) {
        DeviceGrid(
            devices = gridDevices,
            selectedId = "emulator-5554",
            missingTools = emptyList(),
            snackbarHostState = remember(calculation = ::JwSnackbarHostState),
            thumbnailOf = { id -> DeviceThumbnail(image = null, updatedAtMillis = null, state = gridStates.getValue(id)) },
            poll = { _, _ -> awaitCancellation() },
            livenessOf = { DeviceLiveness.Unknown },
            onOpen = {},
            onScreenshot = {},
            onScreenshotAll = {},
        )
    }
}

@Preview
@Composable
private fun DeviceGridEmptyPreview() {
    JwTheme(darkTheme = true) {
        DeviceGrid(
            devices = emptyList(),
            selectedId = null,
            missingTools = listOf("adb was not found; install the Android SDK platform tools to mirror Android devices."),
            snackbarHostState = remember(calculation = ::JwSnackbarHostState),
            thumbnailOf = { DeviceThumbnail(image = null, updatedAtMillis = null, state = ThumbnailState.Loading) },
            poll = { _, _ -> awaitCancellation() },
            livenessOf = { DeviceLiveness.Unknown },
            onOpen = {},
            onScreenshot = {},
            onScreenshotAll = {},
        )
    }
}

@Preview
@Composable
private fun DevicePickerPreview() {
    JwTheme(darkTheme = true) {
        DevicePicker(
            devices = gridDevices,
            selected = gridDevices.first(),
            livenessOf = { id -> if (id == "emulator-5554") DeviceLiveness.Live else DeviceLiveness.Unknown },
            onSelect = {},
            onShowAll = {},
        )
    }
}

@Preview
@Composable
private fun LivenessDotPreview() {
    JwTheme(darkTheme = true) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DeviceLiveness.entries.forEach { LivenessDot(it) }
        }
    }
}
