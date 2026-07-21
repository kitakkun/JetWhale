package com.kitakkun.jetwhale.host.data.discovery

import com.kitakkun.jetwhale.host.model.DebugServerStatusProvider
import com.kitakkun.jetwhale.host.model.DebugWebSocketServerStatus
import com.kitakkun.jetwhale.host.model.HostDiscoveryAdvertiser
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import java.net.InetAddress

/**
 * A [HostDiscoveryAdvertiser] that observes the debug server status and keeps a `_jetwhale._tcp`
 * service registered (via [MdnsRegistrar]) while the server is running, re-registering whenever the
 * advertised ports change.
 *
 * The service is registered with the plain-ws port as its primary port and carries the ws/wss ports
 * in TXT records so an agent can pick the connector matching its transport (wss when configured with
 * `ssl {}`, else ws).
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
@Inject
class DefaultHostDiscoveryAdvertiser(
    private val statusProvider: DebugServerStatusProvider,
    private val registrar: MdnsRegistrar,
) : HostDiscoveryAdvertiser {
    private val logger = LoggerFactory.getLogger(DefaultHostDiscoveryAdvertiser::class.java)
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.IO)

    private var observeJob: Job? = null

    // The ports the currently registered service was advertised with, used to skip redundant
    // re-registration when the status flow re-emits an equivalent Started state.
    private var advertisedPorts: Pair<Int, Int?>? = null

    override fun start() {
        if (observeJob != null) return
        observeJob = coroutineScope.launch {
            statusProvider.statusFlow.collect { status ->
                when (status) {
                    is DebugWebSocketServerStatus.Started -> advertise(status.port, status.wssPort)

                    is DebugWebSocketServerStatus.Stopped,
                    is DebugWebSocketServerStatus.Error,
                    -> unregister()

                    else -> Unit
                }
            }
        }
    }

    override fun stop() {
        observeJob?.cancel()
        observeJob = null
        advertisedPorts = null
        // Fully release the mDNS stack (not just unregister) so sockets/threads do not leak across
        // debug server restarts. JmDNS.close() can block, so run it off the caller's thread.
        coroutineScope.launch { registrar.close() }
    }

    private fun advertise(wsPort: Int, wssPort: Int?) {
        if (advertisedPorts == (wsPort to wssPort)) return
        registrar.register(instanceName(), wsPort, wssPort)
        advertisedPorts = wsPort to wssPort
    }

    private fun unregister() {
        if (advertisedPorts == null) return
        registrar.unregister()
        advertisedPorts = null
    }

    /**
     * The advertised instance name. The machine hostname keeps it recognizable to a developer picking
     * a host from multiple advertised machines and lets the agent's `hostName` filter target it; it
     * falls back to a constant when the hostname is unavailable.
     */
    private fun instanceName(): String = try {
        InetAddress.getLocalHost().hostName
    } catch (e: Exception) {
        logger.debug("Could not resolve local hostname for mDNS instance name; using default", e)
        DEFAULT_INSTANCE_NAME
    }

    private companion object {
        const val DEFAULT_INSTANCE_NAME = "JetWhale"
    }
}
