package com.kitakkun.jetwhale.tools.qaagent

import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Every dropped send looks the same to the caller, so the hint is the only thing telling a QA run
 * whether to wait, fix the host, or give up on that app.
 */
class SendDropHintTest {
    @Test
    fun `a disconnected app says the session is gone rather than telling the caller to wait`() {
        val hint = sendDropHint(
            pluginId = "com.example.myplugin",
            appName = "checkout",
            appConnected = false,
            activated = true,
            ready = true,
        )

        assertTrue(hint.contains("/disconnect"), hint)
        assertTrue(!hint.contains("poll"), "a stopped session never becomes ready: $hint")
    }

    @Test
    fun `a plugin the host never enabled names the app it was not enabled for`() {
        val hint = sendDropHint(
            pluginId = "com.example.myplugin",
            appName = "checkout",
            appConnected = true,
            activated = false,
            ready = false,
        )

        assertTrue(hint.contains("com.example.myplugin"), hint)
        assertTrue(hint.contains("checkout"), hint)
        assertTrue(!hint.contains("poll"), "waiting does not enable a plugin: $hint")
    }

    @Test
    fun `an activated but unprepared plugin is the one case worth waiting out`() {
        val hint = sendDropHint(
            pluginId = "com.example.myplugin",
            appName = "checkout",
            appConnected = true,
            activated = true,
            ready = false,
        )

        assertTrue(hint.contains("poll"), hint)
    }

    @Test
    fun `an otherwise healthy drop suggests buffering`() {
        val hint = sendDropHint(
            pluginId = "com.example.myplugin",
            appName = "checkout",
            appConnected = true,
            activated = true,
            ready = true,
        )

        assertTrue(hint.contains("QUEUE"), hint)
    }
}
