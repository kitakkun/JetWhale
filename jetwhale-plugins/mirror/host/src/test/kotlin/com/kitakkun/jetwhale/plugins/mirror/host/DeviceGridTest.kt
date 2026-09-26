package com.kitakkun.jetwhale.plugins.mirror.host

import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.kitakkun.jetwhale.host.ui.JwTheme
import kotlinx.coroutines.awaitCancellation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class DeviceGridTest {
    private val devices = List(12) { index -> DeviceListing("device-$index", "Device $index", DeviceKind.AndroidEmulator, osVersion = null) }

    @Test
    fun `only the tiles on screen poll, and a tile that scrolls away stops`() = runComposeUiTest {
        val polling = mutableSetOf<String>()
        setContent {
            JwTheme(darkTheme = true) {
                DeviceGrid(
                    devices = devices,
                    selectedId = null,
                    missingTools = emptyList(),
                    notices = IgnoredNotices,
                    thumbnailOf = { DeviceThumbnail(image = null, updatedAtMillis = null, state = ThumbnailState.Loading) },
                    poll = { id, _ ->
                        polling += id
                        try {
                            awaitCancellation()
                        } finally {
                            polling -= id
                        }
                    },
                    livenessOf = { DeviceLiveness.Unknown },
                    onOpen = {},
                    onScreenshot = {},
                    onScreenshotAll = {},
                    modifier = Modifier.size(width = 400.dp, height = 400.dp),
                )
            }
        }
        waitForIdle()

        assertTrue(polling.isNotEmpty())
        assertTrue(polling.size < devices.size, "every tile polled: $polling")
        assertTrue("device-0" in polling)
        assertTrue("device-11" !in polling)
    }

    @Test
    fun `clicking a tile opens that device`() = runComposeUiTest {
        var opened: String? = null
        setContent {
            JwTheme(darkTheme = true) {
                DeviceGrid(
                    devices = devices.take(2),
                    selectedId = null,
                    missingTools = emptyList(),
                    notices = IgnoredNotices,
                    thumbnailOf = { DeviceThumbnail(image = null, updatedAtMillis = null, state = ThumbnailState.Loading) },
                    poll = { _, _ -> awaitCancellation() },
                    livenessOf = { DeviceLiveness.Unknown },
                    onOpen = { opened = it },
                    onScreenshot = {},
                    onScreenshotAll = {},
                )
            }
        }

        onNodeWithText("Device 1").performClick()

        assertEquals("device-1", opened)
    }

    @Test
    fun `the device picker lists every device and selects the one clicked`() = runComposeUiTest {
        var selected: String? = null
        setContent {
            JwTheme(darkTheme = true) {
                DevicePicker(
                    devices = devices.take(3),
                    selected = devices.first(),
                    livenessOf = { DeviceLiveness.Unknown },
                    onSelect = { selected = it },
                    onShowAll = {},
                )
            }
        }

        onNodeWithText("Device 0").performClick()
        onNodeWithText("Device 2").performClick()

        assertEquals("device-2", selected)
    }

    @Test
    fun `picking all devices from the picker shows the grid`() = runComposeUiTest {
        var showedAll = false
        setContent {
            JwTheme(darkTheme = true) {
                DevicePicker(
                    devices = devices.take(3),
                    selected = devices.first(),
                    livenessOf = { DeviceLiveness.Unknown },
                    onSelect = {},
                    onShowAll = { showedAll = true },
                )
            }
        }

        onNodeWithText("Device 0").performClick()
        onNodeWithText("All devices", substring = true).performClick()

        assertTrue(showedAll)
    }

    @Test
    fun `with no devices the grid says how to get one and offers the commands`() = runComposeUiTest {
        setContent {
            JwTheme(darkTheme = true) {
                DeviceGrid(
                    devices = emptyList(),
                    selectedId = null,
                    missingTools = emptyList(),
                    notices = IgnoredNotices,
                    thumbnailOf = { DeviceThumbnail(image = null, updatedAtMillis = null, state = ThumbnailState.Loading) },
                    poll = { _, _ -> awaitCancellation() },
                    livenessOf = { DeviceLiveness.Unknown },
                    onOpen = {},
                    onScreenshot = {},
                    onScreenshotAll = {},
                )
            }
        }

        onNodeWithText("No devices").assertExists()
        onNodeWithText("adb devices").assertExists()
        onNodeWithText("xcrun simctl list devices available").assertExists()
    }

    @Test
    fun `the grid's picker names how many devices it shows`() = runComposeUiTest {
        setContent {
            JwTheme(darkTheme = true) {
                DevicePicker(devices = devices.take(3), selected = null, livenessOf = { DeviceLiveness.Unknown }, onSelect = {}, onShowAll = {})
            }
        }

        onNodeWithText("All devices (3)").assertExists()
    }

    @Test
    fun `hovering a tile offers open and screenshot for that device`() = runComposeUiTest {
        var screenshotOf: String? = null
        setContent {
            JwTheme(darkTheme = true) {
                DeviceGrid(
                    devices = devices.take(2),
                    selectedId = null,
                    missingTools = emptyList(),
                    notices = IgnoredNotices,
                    thumbnailOf = { DeviceThumbnail(image = null, updatedAtMillis = null, state = ThumbnailState.Loading) },
                    poll = { _, _ -> awaitCancellation() },
                    livenessOf = { DeviceLiveness.Unknown },
                    onOpen = {},
                    onScreenshot = { screenshotOf = it },
                    onScreenshotAll = {},
                )
            }
        }
        onNodeWithContentDescription("Screenshot of Device 1").assertDoesNotExist()

        onNodeWithText("Device 1").performMouseInput { moveTo(center) }
        onNodeWithContentDescription("Screenshot of Device 1").performClick()

        assertEquals("device-1", screenshotOf)
    }

    @Test
    fun `enter on a focused tile opens that device`() = runComposeUiTest {
        var opened: String? = null
        setContent {
            JwTheme(darkTheme = true) {
                DeviceGrid(
                    devices = devices.take(2),
                    selectedId = null,
                    missingTools = emptyList(),
                    notices = IgnoredNotices,
                    thumbnailOf = { DeviceThumbnail(image = null, updatedAtMillis = null, state = ThumbnailState.Loading) },
                    poll = { _, _ -> awaitCancellation() },
                    livenessOf = { DeviceLiveness.Unknown },
                    onOpen = { opened = it },
                    onScreenshot = {},
                    onScreenshotAll = {},
                )
            }
        }

        onNodeWithText("Device 1").requestFocus()
        onNodeWithText("Device 1").performKeyInput { pressKey(Key.Enter) }

        assertEquals("device-1", opened)
    }
}

/** Notice actions for tests that show no notice. */
private object IgnoredNotices : MirrorNoticeActions {
    override val notice: MirrorNotice? get() = null

    override fun perform(action: NoticeAction) = Unit

    override fun dismiss() = Unit

    override fun hold(held: Boolean) = Unit
}
