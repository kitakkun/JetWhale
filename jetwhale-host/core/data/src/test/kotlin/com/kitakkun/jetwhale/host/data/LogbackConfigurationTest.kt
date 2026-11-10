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
    fun `timestamps carry the calendar year, not the week-based one that turns over in late December`() {
        val config = checkNotNull(LogbackConfigurationTest::class.java.getResource("/logback.xml")).readText()

        assertFalse("YYYY" in config)
    }

    @Test
    fun `Ktor logs only at info and above`() {
        val routing = LoggerFactory.getLogger("io.ktor.server.routing.Routing")

        assertFalse(routing.isDebugEnabled)
        assertTrue(routing.isInfoEnabled)
    }

    @Test
    fun `the MCP SDK logs only warnings and above, its feature registries included`() {
        listOf("io.modelcontextprotocol.kotlin.sdk.shared.Protocol", "FeatureRegistry[Tool]", "FeatureRegistry[Prompt]", "FeatureRegistry[Resource]", "FeatureRegistry[ResourceTemplate]").forEach { name ->
            val logger = LoggerFactory.getLogger(name)

            assertFalse(logger.isInfoEnabled, name)
            assertTrue(logger.isWarnEnabled, name)
        }
    }
}
