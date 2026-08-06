package com.kitakkun.jetwhale.agent.runtime

import com.kitakkun.jetwhale.annotations.ExperimentalJetWhaleApi
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ConnectionEndpointTest {
    private fun candidates(
        configure: JetWhaleConnectionConfigurationScope.() -> Unit,
    ): List<EndpointCandidate> = JetWhaleConnectionConfiguration().apply(configure).candidates

    /** Only for configurations without a discovered candidate, which would browse the network for real. */
    private fun literalAddresses(
        configure: JetWhaleConnectionConfigurationScope.() -> Unit,
    ): List<ResolvedEndpoint> = runBlocking {
        JetWhaleConnectionConfiguration().apply(configure).endpointResolver().resolve()
    }

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

    @Test
    fun `the scheme is per candidate, so one configuration can mix them`() {
        // The whole point of stating it per candidate: the targets a single configuration serves do
        // not agree on whether wss is possible.
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
        // ssl { } says what to trust; the candidate says whether TLS is spoken at all.
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

        // Both allowlists accumulate, so neither call silently drops the one before it.
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
        // Discovery reaches every JetWhale host on the network, which on a shared one is other
        // people's. Saying nothing must not amount to taking all of them.
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
        // Without the agent Gradle plugin the call reaches its own body, which only explains itself.
        // Contributing a candidate here would be worse than contributing none: there is no address
        // to contribute, so anything it added would be invented.
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
        // It drops out of the list rather than leaving a gap, so whatever was declared after it is
        // still reached — and still in the order it was written.
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
    fun `the same address declared twice is dialled once`() {
        val resolved = literalAddresses {
            endpoints {
                ws("localhost", 5080)
                ws("localhost", 5080)
            }
        }

        assertEquals(1, resolved.size)
    }
}
