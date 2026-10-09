package com.kitakkun.jetwhale.plugins.mirror.host

import kotlin.test.Test
import kotlin.test.assertEquals

class DeviceButtonMappingTest {
    @Test
    fun `recent apps on Android sends the app switch key`() {
        assertEquals("KEYCODE_APP_SWITCH", androidKeycodeOf(DeviceButton.Recents))
    }

    @Test
    fun `every Android button maps to its own key`() {
        val keycodes = DeviceButton.entries.map(::androidKeycodeOf)

        assertEquals(keycodes.size, keycodes.toSet().size)
    }

    @Test
    fun `a recording's elapsed time reads as minutes and seconds, with hours once past one`() {
        assertEquals("0:00", recordingElapsed(0))
        assertEquals("0:12", recordingElapsed(12_400))
        assertEquals("10:05", recordingElapsed(605_000))
        assertEquals("1:02:03", recordingElapsed(3_723_000))
    }
}
