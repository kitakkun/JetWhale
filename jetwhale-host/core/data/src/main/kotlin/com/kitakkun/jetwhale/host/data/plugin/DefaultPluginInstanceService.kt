package com.kitakkun.jetwhale.host.data.plugin

import com.kitakkun.jetwhale.host.model.HostPluginFrameSender
import com.kitakkun.jetwhale.host.model.LoadedPluginInstance
import com.kitakkun.jetwhale.host.model.PluginDataStoreRepository
import com.kitakkun.jetwhale.host.model.PluginFactoryRepository
import com.kitakkun.jetwhale.host.model.PluginInstanceEvent
import com.kitakkun.jetwhale.host.model.PluginInstanceService
import com.kitakkun.jetwhale.host.sdk.InternalJetWhaleHostApi
import com.kitakkun.jetwhale.host.sdk.JetWhaleHostContext
import com.kitakkun.jetwhale.host.sdk.JetWhaleHostPlugin
import com.kitakkun.jetwhale.host.sdk.JetWhaleMessagingHostPlugin
import com.kitakkun.jetwhale.host.sdk.JetWhalePluginStorage
import com.kitakkun.jetwhale.protocol.messaging.JetWhalePluginPeer
import com.kitakkun.jetwhale.protocol.messaging.PluginFrame
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.util.concurrent.ConcurrentHashMap
import java.util.logging.Logger

private data class PluginInstanceKey(val pluginId: String, val sessionId: String)

/**
 * A plugin instance paired with the messaging peer that delivers its frames. The peer's outbound
 * frames are sent to this instance's session; inbound frames are routed to it by the server.
 */
private class LoadedInstance(
    val plugin: JetWhaleHostPlugin,
    // null for a pure (non-messaging) plugin: no peer is created for it.
    val peer: JetWhalePluginPeer?,
    /** Backs the plugin's `pluginScope`; cancelled when the instance is disposed. */
    val instanceScope: CoroutineScope,
)

private class PluginHostContext(
    override val storage: JetWhalePluginStorage,
) : JetWhaleHostContext

@OptIn(InternalJetWhaleHostApi::class)
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class DefaultPluginInstanceService(
    private val pluginFactoryRepository: PluginFactoryRepository,
    private val frameSender: HostPluginFrameSender,
    private val pluginDataStoreRepository: PluginDataStoreRepository,
) : PluginInstanceService {
    private val logger = Logger.getLogger(DefaultPluginInstanceService::class.java.name)

    /** Parent scope for every plugin peer; each peer also gets its own child supervisor. */
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val loadedPlugins: ConcurrentHashMap<PluginInstanceKey, LoadedInstance> = ConcurrentHashMap()

    private val mutablePluginInstanceEventFlow: MutableSharedFlow<PluginInstanceEvent> = MutableSharedFlow(extraBufferCapacity = 64)
    override val pluginInstanceEventFlow: SharedFlow<PluginInstanceEvent> = mutablePluginInstanceEventFlow.asSharedFlow()

    override fun getLoadedPluginInstances(): List<LoadedPluginInstance> = loadedPlugins.entries.map { (key, instance) ->
        LoadedPluginInstance(pluginId = key.pluginId, sessionId = key.sessionId, plugin = instance.plugin)
    }

    override fun getPluginInstanceForSession(pluginId: String, sessionId: String): JetWhaleHostPlugin? = loadedPlugins[PluginInstanceKey(pluginId, sessionId)]?.plugin

    override fun initializePluginInstancesForSessionsIfNeeded(pluginId: String, sessionIds: Set<String>): Set<String> {
        val loaded = pluginFactoryRepository.loadedPlugins[pluginId] ?: return emptySet()

        val newlyInitializedSessions = mutableSetOf<String>()
        for (sessionId in sessionIds) {
            val key = PluginInstanceKey(pluginId, sessionId)
            loadedPlugins.computeIfAbsent(key) {
                newlyInitializedSessions += sessionId
                createInstance(pluginId, sessionId, loaded.factory.createPlugin(), loaded.manifest.requiresAgent)
            }
        }

        newlyInitializedSessions.forEach { sessionId ->
            emitEvent(PluginInstanceEvent.Ready(pluginId, sessionId))
        }
        return newlyInitializedSessions
    }

    private fun createInstance(pluginId: String, sessionId: String, plugin: JetWhaleHostPlugin, requiresAgent: Boolean): LoadedInstance {
        if (!requiresAgent && plugin is JetWhaleMessagingHostPlugin) {
            // A messaging plugin without an agent counterpart waits out its prepare timeout on every
            // session and gets "not active" failures for every request — surface the misconfiguration
            // instead of degrading silently.
            logger.warning(
                "Plugin '$pluginId' declares requiresAgent=false but its factory returns a JetWhaleMessagingHostPlugin; " +
                    "its messenger will never reach an agent.",
            )
        }
        val instanceScope = CoroutineScope(scope.coroutineContext + SupervisorJob(scope.coroutineContext[Job]))
        plugin.bindPluginScope(instanceScope)

        // Hand the plugin a host context whose storage is already scoped to its own
        // pluginId, so it can never name or reach another plugin's data.
        try {
            plugin.dispatchAttach(PluginHostContext(pluginDataStoreRepository.storageFor(pluginId)))
        } catch (e: Throwable) {
            logger.warning("onAttach for plugin '$pluginId' in session '$sessionId' failed: ${e.message}")
        }

        // User code below (registerHandlers, onCreate) is guarded: this runs inside the map's
        // computeIfAbsent, and a throwing plugin must neither leak the just-created peer/scope nor
        // abort loading for the caller.
        // Only messaging plugins get a peer; a pure plugin pays none of the messaging cost.
        val peer = if (plugin is JetWhaleMessagingHostPlugin) {
            JetWhalePluginPeer(
                pluginId = pluginId,
                parentScope = scope,
                sendFrame = { frame -> frameSender.sendFrame(sessionId, frame) },
                awaitReady = true,
            ).also { peer ->
                try {
                    peer.configure { plugin.registerHandlers(this) }
                } catch (e: Throwable) {
                    logger.warning("Handler registration for plugin '$pluginId' in session '$sessionId' failed: ${e.message}")
                }
                plugin.bindMessenger(peer.messenger)
            }
        } else {
            null
        }
        try {
            plugin.dispatchCreate()
        } catch (e: Throwable) {
            logger.warning("onCreate for plugin '$pluginId' in session '$sessionId' failed: ${e.message}")
        }
        if (peer != null && plugin is JetWhaleMessagingHostPlugin) {
            // markReady() also runs on prepare timeout/failure: a degraded plugin beats a frozen one.
            instanceScope.launch {
                try {
                    withTimeout(plugin.prepareTimeoutMillis()) {
                        plugin.dispatchPrepare()
                    }
                } catch (e: TimeoutCancellationException) {
                    logger.warning("onPrepare for plugin '$pluginId' in session '$sessionId' did not complete in time; proceeding.")
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    logger.warning("onPrepare for plugin '$pluginId' in session '$sessionId' failed: ${e.message}; proceeding.")
                } finally {
                    peer.markReady()
                }
            }
        }
        return LoadedInstance(plugin, peer, instanceScope)
    }

    override suspend fun routeFrame(sessionId: String, frame: PluginFrame) {
        val peer = loadedPlugins[PluginInstanceKey(frame.pluginId, sessionId)]?.peer
        if (peer != null) {
            peer.onFrame(frame)
            return
        }
        // No instance for this frame in this session. If it expects a reply, fail it fast so the
        // agent-side requester does not wait for the timeout instead of silently dropping it.
        // Launch, don't await: sendFrame is a suspending socket write and routeFrame runs on the
        // server's single frame collector, so awaiting here would stall delivery for every other
        // session while this one failure reply is in flight.
        if (frame is PluginFrame.Request) {
            scope.launch {
                // Best effort: the session may already be half-closed; a failed failure-reply must
                // not escalate through the supervisor.
                try {
                    frameSender.sendFrame(
                        sessionId = sessionId,
                        frame = PluginFrame.Reply.Failure(
                            pluginId = frame.pluginId,
                            inReplyTo = frame.correlationId,
                            errorMessage = "Plugin '${frame.pluginId}' is not loaded in session '$sessionId'.",
                        ),
                    )
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    logger.warning("Failed to send fast-fail reply for plugin '${frame.pluginId}' in session '$sessionId': ${e.message}")
                }
            }
        }
    }

    override fun unloadPluginInstanceForSession(sessionId: String) {
        loadedPlugins.keys.filter { it.sessionId == sessionId }.forEach { disposeInstance(it) }
    }

    override fun unloadPluginInstancesForPlugin(pluginId: String) {
        loadedPlugins.keys.filter { it.pluginId == pluginId }.forEach { disposeInstance(it) }
    }

    override fun clearAllPluginInstances() {
        loadedPlugins.keys.toList().forEach { disposeInstance(it, emitEvent = false) }
    }

    private fun disposeInstance(key: PluginInstanceKey, emitEvent: Boolean = true) {
        val removed = loadedPlugins.remove(key) ?: return
        try {
            removed.plugin.dispatchDispose()
        } catch (e: Throwable) {
            // A throwing onDispose must not leak the scope/peer, nor abort disposing the session's
            // other plugins from the callers' forEach loops.
            logger.warning("onDispose for plugin '${key.pluginId}' in session '${key.sessionId}' failed: ${e.message}")
        } finally {
            removed.instanceScope.cancel()
            // close() suspends (it fails pending requests under a mutex), so run it off the caller.
            removed.peer?.let { peer -> scope.launch { peer.close() } }
        }
        if (emitEvent) emitEvent(PluginInstanceEvent.Disposed(key.pluginId, key.sessionId))
    }

    private fun emitEvent(event: PluginInstanceEvent) {
        if (!mutablePluginInstanceEventFlow.tryEmit(event)) {
            logger.warning("Plugin instance event dropped (buffer full): $event")
        }
    }
}
