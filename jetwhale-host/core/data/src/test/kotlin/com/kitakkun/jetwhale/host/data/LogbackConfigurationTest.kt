package com.kitakkun.jetwhale.host.data

import org.slf4j.LoggerFactory
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LogbackConfigurationTest {
    @Test
    fun `the host's own loggers keep logging at trace`() {
        assertTrue(LoggerFactory.getLogger("com.kitakkun.jetwhale.host.data.SomeService").isTraceEnabled)
    }

    @Test
    fun `jmDNS logs only at info and above`() {
        val jmdns = LoggerFactory.getLogger("javax.jmdns.impl.JmDNSImpl")

        assertFalse(jmdns.isDebugEnabled)
        assertTrue(jmdns.isInfoEnabled)
    }

    @Test
    fun `Ktor logs only at info and above`() {
        val routing = LoggerFactory.getLogger("io.ktor.server.routing.Routing")

        assertFalse(routing.isDebugEnabled)
        assertTrue(routing.isInfoEnabled)
    }

    @Test
    fun `the MCP SDK logs only warnings and above, its tool registry included`() {
        listOf("io.modelcontextprotocol.kotlin.sdk.shared.Protocol", "FeatureRegistry[Tool]").forEach { name ->
            val logger = LoggerFactory.getLogger(name)

            assertFalse(logger.isInfoEnabled, name)
            assertTrue(logger.isWarnEnabled, name)
        }
    }
}
