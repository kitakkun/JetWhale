package com.kitakkun.jetwhale.host.data.discovery

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import org.slf4j.LoggerFactory
import java.net.InetAddress
import javax.jmdns.JmDNS
import javax.jmdns.ServiceInfo

/**
 * A thin seam over the mDNS/DNS-SD stack so the advertising logic (status observation, port tracking)
 * can be unit-tested without a real multicast network.
 */
interface MdnsRegistrar {
    /**
     * Registers (or re-registers) the `_jetwhale._tcp.local.` service with the given ports. Replaces
     * any previously registered service.
     */
    fun register(instanceName: String, wsPort: Int, wssPort: Int?)

    /** Unregisters the currently registered service, if any, keeping the stack alive for re-use. */
    fun unregister()

    /**
     * Unregisters and releases the underlying mDNS stack (sockets/threads). Idempotent. A subsequent
     * [register] transparently recreates the stack. May block, so callers should invoke it off any
     * latency-sensitive thread.
     */
    fun close()
}

/** jmDNS-backed [MdnsRegistrar]. The [JmDNS] instance is created lazily on the first registration. */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
@Inject
class JmDnsRegistrar : MdnsRegistrar {
    private val logger = LoggerFactory.getLogger(JmDnsRegistrar::class.java)

    private var jmdns: JmDNS? = null
    private var registeredService: ServiceInfo? = null

    override fun register(instanceName: String, wsPort: Int, wssPort: Int?) {
        unregister()
        try {
            val instance = jmdns ?: JmDNS.create(InetAddress.getLocalHost()).also { jmdns = it }
            val props = buildMap {
                put(TXT_KEY_PROTOCOL_VERSION, PROTOCOL_VERSION)
                put(TXT_KEY_WS_PORT, wsPort.toString())
                if (wssPort != null) put(TXT_KEY_WSS_PORT, wssPort.toString())
                // Carry the hostname in a TXT record too: mDNS may uniquify the instance name on
                // collision (e.g. "name (2)"), but agent-side hostName filtering needs the stable
                // raw hostname.
                put(TXT_KEY_HOST_NAME, instanceName)
            }
            val serviceInfo = ServiceInfo.create(SERVICE_TYPE, instanceName, wsPort, 0, 0, props)
            instance.registerService(serviceInfo)
            registeredService = serviceInfo
            logger.info("Advertising JetWhale host over mDNS: $SERVICE_TYPE ws=$wsPort wss=$wssPort")
        } catch (e: Exception) {
            // mDNS advertising is a convenience; a failure (e.g. multicast blocked) must not stop the
            // debug server. Explicit host/port configuration remains the fallback for agents.
            logger.warn("Failed to advertise JetWhale host over mDNS; agents must use an explicit host", e)
        }
    }

    override fun unregister() {
        registeredService?.let { service ->
            try {
                jmdns?.unregisterService(service)
            } catch (e: Exception) {
                logger.warn("Failed to unregister JetWhale mDNS service", e)
            }
        }
        registeredService = null
    }

    override fun close() {
        unregister()
        // Idempotent: once closed, jmdns is null and a later register() recreates it on demand.
        val instance = jmdns ?: return
        jmdns = null
        try {
            instance.close()
        } catch (e: Exception) {
            logger.warn("Failed to close jmDNS", e)
        }
    }

    companion object {
        const val SERVICE_TYPE: String = "_jetwhale._tcp.local."
        const val TXT_KEY_PROTOCOL_VERSION: String = "v"
        const val PROTOCOL_VERSION: String = "1"
        const val TXT_KEY_WS_PORT: String = "wsPort"
        const val TXT_KEY_WSS_PORT: String = "wssPort"
        const val TXT_KEY_HOST_NAME: String = "hostName"
    }
}
