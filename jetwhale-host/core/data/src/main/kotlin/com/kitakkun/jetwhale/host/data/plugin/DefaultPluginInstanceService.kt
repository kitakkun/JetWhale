package com.kitakkun.jetwhale.host.data.plugin

import com.kitakkun.jetwhale.host.model.HostPluginFrameSender
import com.kitakkun.jetwhale.host.model.LoadedPluginInstance
import com.kitakkun.jetwhale.host.model.PluginDataStoreRepository
import com.kitakkun.jetwhale.host.model.PluginFactoryRepository
import com.kitakkun.jetwhale.host.model.PluginInstanceEvent
import com.kitakkun.jetwhale.host.model.PluginInstanceService
import com.kitakkun.jetwhale.host.sdk.InternalJetWhaleHostApi
import com.kitakkun.jetwhale.host.sdk.JetWhaleHostPlugin
import com.kitakkun.jetwhale.host.sdk.JetWhaleMessagingHostPlugin
import com.kitakkun.jetwhale.protocol.messaging.JetWhalePluginPeer
import com.kitakkun.jetwhale.protocol.messaging.PluginFrame
import com.kitakkun.jetwhale.protocol.messaging.configurePeerGuarded
import com.kitakkun.jetwhale.protocol.messaging.launchPeerPreparation
import com.kitakkun.jetwhale.protocol.messaging.replyPeerUnavailable
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import java.util.logging.Level
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
    /** The preparation job; joined before the peer is closed so its ready-gate open cannot outrace disposal. null for a pure plugin. */
    val prepareJob: Job?,
    /** Backs the plugin's `pluginScope`; cancelled when the instance is disposed. */
    val instanceScope: CoroutineScope,
)

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

        // Hand the plugin a storage handle already scoped to its own pluginId, so it can never
        // name or reach another plugin's data.
        plugin.bindStorage(pluginDataStoreRepository.storageFor(pluginId))

        val descriptor = "plugin '$pluginId' in session '$sessionId'"

        // User code below (registerHandlers, onCreate) is guarded: this runs inside the map's
        // computeIfAbsent, and a throwing plugin must neither leak the just-created peer/scope nor
        // abort loading for the caller.
        // Only messaging plugins get a peer; a pure plugin pays none of the messaging cost.
        val peer = if (plugin is JetWhaleMessagingHostPlugin) {
            val newPeer = JetWhalePluginPeer(
                pluginId = pluginId,
                parentScope = scope,
                sendFrame = { frame -> frameSender.sendFrame(sessionId, frame) },
                awaitReady = true,
            )
            val configured = configurePeerGuarded(
                peer = newPeer,
                descriptor = descriptor,
                registerHandlers = { plugin.registerHandlers(this) },
                warn = { message, throwable -> logger.log(Level.WARNING, message, throwable) },
            )
            if (configured) {
                plugin.bindMessenger(newPeer.messenger)
                newPeer
            } else {
                // Registration failed: discard the half-configured peer (mirrors the agent's bail-out).
                // The instance still loads, but without messaging — subsequent frames fast-fail via the
                // no-peer path in routeFrame.
                scope.launch { newPeer.close() }
                null
            }
        } else {
            null
        }
        try {
            plugin.dispatchCreate()
        } catch (e: Throwable) {
            logger.warning("onCreate for plugin '$pluginId' in session '$sessionId' failed: ${e.message}")
        }
        val prepareJob = if (peer != null && plugin is JetWhaleMessagingHostPlugin) {
            instanceScope.launchPeerPreparation(
                peer = peer,
                descriptor = descriptor,
                prepareTimeoutMillis = plugin.prepareTimeoutMillis(),
                dispatchPrepare = { plugin.dispatchPrepare() },
                warn = { message, throwable -> logger.log(Level.WARNING, message, throwable) },
                onReady = {},
            )
        } else {
            null
        }
        return LoadedInstance(plugin, peer, prepareJob, instanceScope)
    }

    override suspend fun routeFrame(sessionId: String, frame: PluginFrame) {
        val peer = loadedPlugins[PluginInstanceKey(frame.pluginId, sessionId)]?.peer
        if (peer != null) {
            peer.onFrame(frame)
            return
        }
        // No instance for this frame in this session: fast-fail a request so the agent-side
        // requester does not wait out the timeout.
        replyPeerUnavailable(
            scope = scope,
            frame = frame,
            errorMessage = "Plugin '${frame.pluginId}' is not loaded in session '$sessionId'.",
            send = { failureFrame -> frameSender.sendFrame(sessionId = sessionId, frame = failureFrame) },
            warn = { message, throwable -> logger.log(Level.WARNING, message, throwable) },
        )
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
            // Join the prepare job first so its finally (which opens the ready gate) cannot run after
            // close() and resurrect dispatch on a peer that is being torn down.
            removed.peer?.let { peer ->
                scope.launch {
                    removed.prepareJob?.cancelAndJoin()
                    peer.close()
                }
            }
        }
        if (emitEvent) emitEvent(PluginInstanceEvent.Disposed(key.pluginId, key.sessionId))
    }

    private fun emitEvent(event: PluginInstanceEvent) {
        if (!mutablePluginInstanceEventFlow.tryEmit(event)) {
            logger.warning("Plugin instance event dropped (buffer full): $event")
        }
    }
}
