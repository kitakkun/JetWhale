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
}
