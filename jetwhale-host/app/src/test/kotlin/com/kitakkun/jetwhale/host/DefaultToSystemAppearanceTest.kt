package com.kitakkun.jetwhale.host

import java.util.Properties
import kotlin.test.Test
import kotlin.test.assertEquals

class DefaultToSystemAppearanceTest {
    @Test
    fun `follows the system appearance when nothing chose one`() {
        val properties = Properties()

        defaultToSystemAppearance(properties)

        assertEquals("system", properties.getProperty("apple.awt.application.appearance"))
    }

    @Test
    fun `keeps an appearance the launcher or the command line chose`() {
        val properties = Properties().apply { setProperty("apple.awt.application.appearance", "NSAppearanceNameAqua") }

        defaultToSystemAppearance(properties)

        assertEquals("NSAppearanceNameAqua", properties.getProperty("apple.awt.application.appearance"))
    }
}
