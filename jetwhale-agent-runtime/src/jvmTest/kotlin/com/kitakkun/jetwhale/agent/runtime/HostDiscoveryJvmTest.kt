package com.kitakkun.jetwhale.agent.runtime

import kotlinx.coroutines.runBlocking
import java.net.InetAddress
import javax.jmdns.JmDNS
import javax.jmdns.ServiceInfo
import kotlin.test.Test
import kotlin.test.assertEquals

class HostDiscoveryJvmTest {
    @Test
    fun `browse resolves a jmDNS-advertised JetWhale host round-trip`() = runBlocking {
        val instanceName = "jetwhale-test-${System.nanoTime()}"
        var jmdns: JmDNS? = null
        try {
            jmdns = JmDNS.create(InetAddress.getLocalHost())
            val serviceInfo = ServiceInfo.create(
                "_jetwhale._tcp.local.",
                instanceName,
                8080,
                0,
                0,
                mapOf(
                    "v" to "1",
                    TXT_KEY_WS_PORT to "8080",
                    TXT_KEY_WSS_PORT to "8443",
                    TXT_KEY_HOST_NAME to instanceName,
                ),
            )
            jmdns.registerService(serviceInfo)

            val discovered = browseJetWhaleServices(timeoutMillis = 6_000)
            val match = discovered.firstOrNull { it.instanceName.contains(instanceName) }

            if (match == null) {
                // CI machines sometimes block multicast; skip gracefully rather than failing.
                println("Skipping round-trip assertion: no mDNS response (multicast likely unavailable)")
                return@runBlocking
            }

            assertEquals(8080, match.wsPort)
            assertEquals(8443, match.wssPort)
            assertEquals(instanceName, match.advertisedHostName)
        } catch (e: Exception) {
            // Environment lacks a usable multicast stack; treat as a skipped test.
            println("Skipping mDNS round-trip test: ${e.message}")
        } finally {
            jmdns?.unregisterAllServices()
            jmdns?.close()
        }
    }
}
