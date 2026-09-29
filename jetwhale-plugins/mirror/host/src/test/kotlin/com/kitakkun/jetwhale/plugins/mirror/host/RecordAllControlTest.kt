package com.kitakkun.jetwhale.plugins.mirror.host

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import com.kitakkun.jetwhale.host.ui.JwTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class RecordAllControlTest {
    private val targets = listOf(
        DeviceListing("emulator-5554", "Pixel 9", DeviceKind.AndroidEmulator, osVersion = null),
        DeviceListing("sim-1", "iPhone 16", DeviceKind.IosSimulator, osVersion = "iOS 18.5"),
    )

    @Test
    fun `the first Record all asks before recording, and Don't show again keeps it from asking`() = runComposeUiTest {
        val actions = CountingActions()
        var suppressed by mutableStateOf(false)
        actions.onSuppress = { suppressed = true }
        setContent {
            JwTheme(darkTheme = true) {
                RecordAllButton(GridRecordingState(recordingDeviceIds = emptySet(), recordableDevices = targets, warningSuppressed = suppressed), actions)
            }
        }

        onNodeWithContentDescription("Record every device", useUnmergedTree = true).performClick()
        onNodeWithText("Record 2 devices at once?").assertExists()
        assertEquals(0, actions.recorded)

        onNodeWithText("Don't show again").performClick()
        onNodeWithText("Record").performClick()
        waitForIdle()
        assertEquals(1, actions.recorded)
        assertTrue(suppressed)

        onNodeWithContentDescription("Record every device", useUnmergedTree = true).performClick()
        waitForIdle()
        onNodeWithText("Record 2 devices at once?").assertDoesNotExist()
        assertEquals(2, actions.recorded)
    }

    @Test
    fun `cancelling the warning records nothing and asks again next time`() = runComposeUiTest {
        val actions = CountingActions()
        setContent {
            JwTheme(darkTheme = true) {
                RecordAllButton(GridRecordingState(recordingDeviceIds = emptySet(), recordableDevices = targets, warningSuppressed = false), actions)
            }
        }

        onNodeWithContentDescription("Record every device", useUnmergedTree = true).performClick()
        onNodeWithText("Cancel").performClick()
        onNodeWithContentDescription("Record every device", useUnmergedTree = true).performClick()

        onNodeWithText("Record 2 devices at once?").assertExists()
        assertEquals(0, actions.recorded)
        assertEquals(0, actions.suppressed)
    }

    @Test
    fun `while devices record the control stops them all`() = runComposeUiTest {
        val actions = CountingActions()
        setContent {
            JwTheme(darkTheme = true) {
                RecordAllButton(GridRecordingState(recordingDeviceIds = setOf("emulator-5554", "sim-1"), recordableDevices = emptyList(), warningSuppressed = false), actions)
            }
        }

        onNodeWithText("Stop all (2)").performClick()

        assertEquals(1, actions.stopped)
    }

    @Test
    fun `the load note names the simulators, which encode on this Mac`() {
        val note = recordAllLoadNote(listOf(DeviceKind.AndroidEmulator, DeviceKind.IosSimulator, DeviceKind.IosSimulator))

        assertTrue("The 2 iOS simulators encode their video on this Mac" in note, note)
    }
}

private class CountingActions : GridRecordingActions {
    var recorded = 0
    var stopped = 0
    var suppressed = 0
    var onSuppress: () -> Unit = {}

    override fun recordAll() {
        recorded++
    }

    override fun stopAll() {
        stopped++
    }

    override fun suppressWarning() {
        suppressed++
        onSuppress()
    }
}
