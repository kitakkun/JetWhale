package com.kitakkun.jetwhale.tools.qaagent

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AppResolutionTest {
    @Test
    fun `a single app makes the app field optional`() {
        assertEquals(AppResolution.Resolved("qa-agent"), resolveAppName(requested = null, known = listOf("qa-agent")))
    }

    @Test
    fun `a named app is resolved even when it is the only one`() {
        assertEquals(AppResolution.Resolved("qa-agent"), resolveAppName(requested = "qa-agent", known = listOf("qa-agent")))
    }

    @Test
    fun `a named app is resolved among several`() {
        assertEquals(AppResolution.Resolved("catalog"), resolveAppName(requested = "catalog", known = listOf("checkout", "catalog")))
    }

    @Test
    fun `omitting the app among several is refused rather than guessed`() {
        val resolution = resolveAppName(requested = null, known = listOf("checkout", "catalog"))

        val error = assertIsFailed(resolution)
        assertTrue(error.contains("checkout") && error.contains("catalog"), "the message should list the choices: $error")
    }

    @Test
    fun `an unknown app name reports what was started instead`() {
        val resolution = resolveAppName(requested = "payments", known = listOf("checkout", "catalog"))

        val error = assertIsFailed(resolution)
        assertTrue(error.contains("payments"), "the message should quote the unknown name: $error")
        assertTrue(error.contains("checkout"), "the message should list what is running: $error")
    }

    @Test
    fun `no app at all fails instead of resolving to nothing`() {
        assertIsFailed(resolveAppName(requested = null, known = emptyList()))
    }

    private fun assertIsFailed(resolution: AppResolution): String {
        assertTrue(resolution is AppResolution.Failed, "expected a failure but got $resolution")
        return resolution.error
    }
}
