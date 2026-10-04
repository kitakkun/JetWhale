package com.kitakkun.jetwhale.host.launcher

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LauncherArgumentsTest {
    @Test
    fun `takes --after for itself and passes everything else to the host`() {
        val arguments = LauncherArguments.parse(listOf("--server-port", "5103", "--after", "4242", "--headless"))

        assertEquals(4242L, arguments.afterPid)
        assertEquals(listOf("--server-port", "5103", "--headless"), arguments.hostArguments)
        assertTrue(arguments.headless)
    }

    @Test
    fun `takes --retry for itself`() {
        val arguments = LauncherArguments.parse(listOf("--retry", "1.0.0-alpha15", "--after", "4242", "--plugin-dir", "/plugins"))

        assertEquals("1.0.0-alpha15", arguments.retryVersion)
        assertEquals(4242L, arguments.afterPid)
        assertEquals(listOf("--plugin-dir", "/plugins"), arguments.hostArguments)
    }

    @Test
    fun `ignores an --after without a process ID`() {
        val arguments = LauncherArguments.parse(listOf("--log-level", "INFO", "--after"))

        assertEquals(null, arguments.afterPid)
        assertEquals(null, arguments.retryVersion)
        assertEquals(listOf("--log-level", "INFO"), arguments.hostArguments)
        assertFalse(arguments.headless)
    }
}
