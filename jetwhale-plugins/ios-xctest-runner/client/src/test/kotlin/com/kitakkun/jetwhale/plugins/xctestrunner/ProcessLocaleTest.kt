package com.kitakkun.jetwhale.plugins.xctestrunner

import kotlin.test.Test
import kotlin.test.assertEquals

class ProcessLocaleTest {
    @Test
    fun `a process started without a locale gets a UTF-8 one`() {
        val processBuilder = ProcessBuilder("xcrun", "xcodebuild", "-version").apply { environment().remove("LANG") }

        processBuilder.setUtf8LangIfUnset()

        assertEquals("en_US.UTF-8", processBuilder.environment()["LANG"])
    }

    @Test
    fun `a process keeps the locale the user set`() {
        val processBuilder = ProcessBuilder("xcrun", "xcodebuild", "-version").apply { environment()["LANG"] = "ja_JP.UTF-8" }

        processBuilder.setUtf8LangIfUnset()

        assertEquals("ja_JP.UTF-8", processBuilder.environment()["LANG"])
    }
}
