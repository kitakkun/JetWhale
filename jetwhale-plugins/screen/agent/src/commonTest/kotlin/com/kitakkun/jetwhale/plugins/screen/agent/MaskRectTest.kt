package com.kitakkun.jetwhale.plugins.screen.agent

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MaskRectTest {
    @Test
    fun `a mask is scaled with the frame and grown to whole pixels`() {
        val rect = MaskRect(left = 101f, top = 51f, right = 203f, bottom = 99f).toFrame(scale = 0.5f, frameWidth = 540, frameHeight = 1200)

        assertEquals(FrameRect(left = 50, top = 25, right = 102, bottom = 50), rect)
    }

    @Test
    fun `a mask running off the frame is clamped to it`() {
        val rect = MaskRect(left = -20f, top = 1150f, right = 100f, bottom = 1300f).toFrame(scale = 1f, frameWidth = 1080, frameHeight = 1200)

        assertEquals(FrameRect(left = 0, top = 1150, right = 100, bottom = 1200), rect)
    }

    @Test
    fun `a mask wholly outside the frame is dropped`() {
        assertNull(MaskRect(left = 2000f, top = 0f, right = 2100f, bottom = 50f).toFrame(scale = 1f, frameWidth = 1080, frameHeight = 1200))
    }
}
