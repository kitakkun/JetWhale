package com.kitakkun.jetwhale.plugins.mirror.host

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ScreenPowerParsingTest {
    @Test
    fun `an asleep device behind its lock screen reads as off and locked`() {
        val output = "  mWakefulness=Asleep\n    isKeyguardShowing=true\n"

        assertEquals(ScreenPower(awake = false, locked = true), parseScreenPower(output))
    }

    @Test
    fun `an awake unlocked device reads as on and unlocked`() {
        val output = "  mWakefulness=Awake\n    isKeyguardShowing=false\n"

        assertEquals(ScreenPower(awake = true, locked = false), parseScreenPower(output))
    }

    @Test
    fun `an always-on display counts as off and a screensaver as on`() {
        assertEquals(false, parseScreenPower("mWakefulness=Dozing")?.awake)
        assertEquals(true, parseScreenPower("mWakefulness=Dreaming")?.awake)
    }

    @Test
    fun `a device that reports no keyguard line reads as unlocked`() {
        assertEquals(ScreenPower(awake = true, locked = false), parseScreenPower("mWakefulness=Awake\n"))
    }

    @Test
    fun `output without a wakefulness line has no screen state`() {
        assertNull(parseScreenPower("error: device offline\n"))
    }
}
