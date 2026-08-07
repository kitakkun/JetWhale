package com.kitakkun.jetwhale.tools.qaagent

import com.kitakkun.jetwhale.plugins.network.agent.JetWhaleNetworkAgentPlugin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class QaAgentOptionsTest {
    @Test
    fun `a run without --app connects as the single default app`() {
        val options = parseArgs(arrayOf("--plugin", "com.example.myplugin"))

        assertEquals(listOf(DEFAULT_APP_NAME), options.apps)
    }

    @Test
    fun `each --app becomes its own app, in the order given`() {
        val options = parseArgs(arrayOf("--app", "checkout", "--app", "catalog"))

        assertEquals(listOf("checkout", "catalog"), options.apps)
    }

    @Test
    fun `a repeated app name is rejected because names address sessions`() {
        val failure = assertFailsWith<IllegalArgumentException> {
            parseArgs(arrayOf("--app", "checkout", "--app", "checkout"))
        }

        assertTrue(failure.message.orEmpty().contains("checkout"), "the message should name the duplicate")
    }

    @Test
    fun `every app registers the same plugins`() {
        val options = parseArgs(arrayOf("--app", "checkout", "--app", "catalog", "--plugin", "com.example.a", "--plugin", "com.example.b@2.0.0"))

        assertEquals(mapOf("com.example.a" to DEFAULT_PLUGIN_VERSION, "com.example.b" to "2.0.0"), options.plugins)
    }

    @Test
    fun `connection options fall back to the local host defaults`() {
        val options = parseArgs(emptyArray())

        assertEquals("localhost", options.hostName)
        assertEquals(DEFAULT_HOST_PORT, options.hostPort)
        assertEquals(DEFAULT_CONTROL_PORT, options.controlPort)
    }

    @Test
    fun `the built-in network plugin cannot be registered again`() {
        val failure = assertFailsWith<IllegalArgumentException> {
            parseArgs(arrayOf("--plugin", BUILT_IN_NETWORK_PLUGIN_ID))
        }

        assertTrue(failure.message.orEmpty().contains(BUILT_IN_NETWORK_PLUGIN_ID), "the message should name the plugin")
    }

    @Test
    fun `the built-in network plugin id matches the plugin the agent actually registers`() {
        assertEquals(JetWhaleNetworkAgentPlugin().pluginId, BUILT_IN_NETWORK_PLUGIN_ID)
    }

    @Test
    fun `--help does not exit the process from the parser`() {
        assertFailsWith<HelpRequestedException> { parseArgs(arrayOf("--help")) }
    }
}
