package com.kitakkun.jetwhale.agent.runtime

import com.kitakkun.jetwhale.annotations.ExperimentalJetWhaleApi
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ConnectionEndpointTest {
    // Pins the deprecated host and port for as long as they remain in the API.
    @Suppress("DEPRECATION")
    @Test
    fun `the deprecated host and port stand in for undeclared endpoints`() {
        val resolved = literalAddresses {
            host = "192.168.3.26"
            port = 5443
            ssl { trustServerCertificate() }
        }

        assertEquals(listOf(ResolvedEndpoint("192.168.3.26", 5443, useWss = true)), resolved)
    }

    /** Only for configurations without a discovered candidate, which would browse the network for real. */
    private fun literalAddresses(
        configure: JetWhaleConnectionConfigurationScope.() -> Unit,
    ): List<ResolvedEndpoint> = runBlocking {
        JetWhaleConnectionConfiguration().apply(configure).endpointResolver().resolve()
    }

    @Test
    fun `undeclared endpoints keep the old defaults`() {
        assertEquals(listOf(ResolvedEndpoint("localhost", 8080, useWss = false)), literalAddresses { })
    }

    @Test
    fun `candidates are kept in the order they were declared`() {
        val declared = candidates {
            endpoints {
                wss("localhost", 5443)
                discoverWss { allowAll() }
                ws("localhost", 5080)
            }
        }

        assertEquals(
            listOf(
                EndpointCandidate.Static("localhost", 5443, useWss = true),
                EndpointCandidate.Dynamic(emptyList(), emptyList(), acceptsAnyHost = true),
                EndpointCandidate.Static("localhost", 5080, useWss = false),
            ),
            declared,
        )
    }

    private fun candidates(
        configure: JetWhaleConnectionConfigurationScope.() -> Unit,
    ): List<EndpointCandidate> = JetWhaleConnectionConfiguration().apply(configure).candidates

    @Test
    fun `the scheme is per candidate, so one configuration can mix them`() {
        val resolved = literalAddresses {
            endpoints {
                wss("192.168.3.26", 5443)
                ws("localhost", 5080)
            }
        }

        assertEquals(
            listOf(
                ResolvedEndpoint("192.168.3.26", 5443, useWss = true),
                ResolvedEndpoint("localhost", 5080, useWss = false),
            ),
            resolved,
        )
    }

    @Test
    fun `a candidate's scheme owes nothing to the ssl block`() {
        val resolved = literalAddresses {
            endpoints { ws("localhost", 5080) }
            ssl { trustServerCertificate() }
        }

        assertEquals(listOf(ResolvedEndpoint("localhost", 5080, useWss = false)), resolved)
    }

    @Test
    fun `a repeated endpoints block adds to the list rather than replacing it`() {
        val declared = candidates {
            endpoints { ws("first", 1) }
            endpoints { ws("second", 2) }
        }

        assertEquals(listOf("first", "second"), declared.map { (it as EndpointCandidate.Static).host })
    }

    @Test
    fun `a discovered candidate carries its allowlists`() {
        val declared = candidates {
            endpoints {
                discoverWss {
                    allowHostName("build-machine")
                    allowHostName("spare-machine")
                    allowAddress("192.168.3.26")
                    allowAddress("192.168.3.27")
                }
            }
        }

        assertEquals(
            EndpointCandidate.Dynamic(
                hostNames = listOf("build-machine", "spare-machine"),
                addresses = listOf("192.168.3.26", "192.168.3.27"),
                acceptsAnyHost = false,
            ),
            declared.single(),
        )
    }

    @Test
    fun `discovery takes any host only when allowAll says so`() {
        val open = assertIs<EndpointCandidate.Dynamic>(
            candidates { endpoints { discoverWss { allowAll() } } }.single(),
        )

        assertTrue(open.acceptsAnyHost)
        assertEquals(emptyList(), open.hostNames)
        assertEquals(emptyList(), open.addresses)
    }

    @Test
    fun `a discovery block that states nothing accepts nothing`() {
        val silent = assertIs<EndpointCandidate.Dynamic>(
            candidates { endpoints { discoverWss { } } }.single(),
        )

        assertTrue(!silent.acceptsAnyHost)
        assertTrue(
            HostDiscoveryConfig(
                hostNames = silent.hostNames,
                addresses = silent.addresses,
                acceptsAnyHost = silent.acceptsAnyHost,
            ).acceptsNothing,
        )
    }

    @Test
    fun `an unrewritten buildMachineWss contributes no candidate`() {
        val declared = candidates {
            endpoints {
                @OptIn(ExperimentalJetWhaleApi::class)
                buildMachineWss(5443)
            }
        }

        assertEquals(emptyList(), declared)
    }

    @Test
    fun `an unrewritten buildMachineWss does not disturb the order of its neighbours`() {
        val declared = candidates {
            endpoints {
                ws("localhost", 5080)
                @OptIn(ExperimentalJetWhaleApi::class)
                buildMachineWss(5443)
                wss("192.168.3.26", 5443)
            }
        }

        assertEquals(
            listOf(
                EndpointCandidate.Static("localhost", 5080, useWss = false),
                EndpointCandidate.Static("192.168.3.26", 5443, useWss = true),
            ),
            declared,
        )
    }

    @Test
    fun `the same address declared twice is dialed once`() {
        val resolved = literalAddresses {
            endpoints {
                ws("localhost", 5080)
                ws("localhost", 5080)
            }
        }

        assertEquals(1, resolved.size)
    }
}
