package com.kitakkun.jetwhale.agent.runtime

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class HostCandidateResolverTest {
    @BeforeTest
    fun setUp() = JetWhaleBuildEnvironment.clear()

    @AfterTest
    fun tearDown() = JetWhaleBuildEnvironment.clear()

    @Test
    fun `no injected candidates and default host yields localhost only`() {
        val candidates = buildHostCandidates(configuredHost = "localhost", configuredPort = 5080, hostExplicitlySet = false)
        assertEquals(listOf("localhost" to 5080), candidates.map { it.host to it.port })
    }

    @Test
    fun `injected addresses are tried before localhost when no explicit host`() {
        JetWhaleBuildEnvironment.registerHostCandidates(hostName = "build-mac", addresses = listOf("192.168.1.10", "10.0.0.5"))

        val candidates = buildHostCandidates(configuredHost = "localhost", configuredPort = 5443, hostExplicitlySet = false)

        assertEquals(
            listOf("192.168.1.10", "10.0.0.5", "build-mac", "localhost"),
            candidates.map { it.host },
        )
        candidates.forEach { assertEquals(5443, it.port) }
    }

    @Test
    fun `explicit host wins and is tried first`() {
        JetWhaleBuildEnvironment.registerHostCandidates(hostName = null, addresses = listOf("192.168.1.10"))

        val candidates = buildHostCandidates(configuredHost = "192.168.9.9", configuredPort = 5080, hostExplicitlySet = true)

        assertEquals(
            listOf("192.168.9.9", "192.168.1.10", "localhost"),
            candidates.map { it.host },
        )
    }

    @Test
    fun `duplicate host and port are removed`() {
        JetWhaleBuildEnvironment.registerHostCandidates(hostName = null, addresses = listOf("localhost"))

        val candidates = buildHostCandidates(configuredHost = "localhost", configuredPort = 5080, hostExplicitlySet = false)

        assertEquals(1, candidates.size)
    }

    @Test
    fun `registration is idempotent per address`() {
        JetWhaleBuildEnvironment.registerHostCandidates(hostName = "m", addresses = listOf("192.168.1.10"))
        JetWhaleBuildEnvironment.registerHostCandidates(hostName = "m", addresses = listOf("192.168.1.10"))

        val addresses = JetWhaleBuildEnvironment.candidates(5080).map { it.host }
        assertEquals(listOf("192.168.1.10", "m"), addresses)
    }
}
