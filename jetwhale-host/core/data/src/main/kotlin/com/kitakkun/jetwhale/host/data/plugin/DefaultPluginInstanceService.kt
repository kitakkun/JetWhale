package com.kitakkun.jetwhale.host.data.plugin

import com.kitakkun.jetwhale.host.model.HeadlessPlugins
import com.kitakkun.jetwhale.host.model.HostPluginFrameSender
import com.kitakkun.jetwhale.host.model.HostSession
import com.kitakkun.jetwhale.host.model.LoadedHostPlugin
import com.kitakkun.jetwhale.host.model.LoadedPluginInstance
import com.kitakkun.jetwhale.host.model.PluginDataStoreRepository
import com.kitakkun.jetwhale.host.model.PluginFactoryRepository
import com.kitakkun.jetwhale.host.model.PluginInstanceEvent
import com.kitakkun.jetwhale.host.model.PluginInstanceService
import com.kitakkun.jetwhale.host.model.PluginInstanceState
import com.kitakkun.jetwhale.host.sdk.InternalJetWhaleHostApi
import com.kitakkun.jetwhale.host.sdk.JetWhaleHostPlugin
import com.kitakkun.jetwhale.host.sdk.JetWhaleHostPluginFactory
import com.kitakkun.jetwhale.host.sdk.JetWhaleHostPluginUi
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import java.util.logging.Level
import java.util.logging.Logger

private data class PluginInstanceKey(val pluginId: String, val sessionId: String)

/**
 * A plugin instance paired with the messaging peer that delivers its frames. The peer's outbound
 * frames are sent to this instance's session; inbound frames are routed to it by the server.
 *
 * @property factory The factory that produced [plugin]; identifies the classloader generation this
 *   instance belongs to.
 * @property peer Null for a pure (non-messaging) plugin: no peer is created for it.
 * @property prepareJob The preparation job; joined before the peer is closed so its ready-gate open
 *   cannot outrace disposal. Null for a pure plugin.
 * @property instanceScope Backs the plugin's `pluginScope`; cancelled when the instance is disposed.
 */
private class LoadedInstance(
    val factory: JetWhaleHostPluginFactory,
    val plugin: JetWhaleHostPlugin,
    val peer: JetWhalePluginPeer?,
    val prepareJob: Job?,
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

    override val headlessPluginsFlow: StateFlow<HeadlessPlugins>
        field = MutableStateFlow(HeadlessPlugins.Empty)

    /** The last creation failure of each key that has no instance, until an attempt succeeds or it is unloaded. */
    private val creationFailures: ConcurrentHashMap<PluginInstanceKey, Throwable> = ConcurrentHashMap()

    /** A snapshot of [loadedPlugins] and [creationFailures], republished on every change so a caller can wait for an instance. */
    private val instanceStatesFlow = MutableStateFlow<Map<PluginInstanceKey, PluginInstanceState>>(emptyMap())
    private val publicationLock = Any()

    override fun getLoadedPluginInstances(): List<LoadedPluginInstance> = loadedPlugins.entries.map { (key, instance) ->
        LoadedPluginInstance(pluginId = key.pluginId, sessionId = key.sessionId, plugin = instance.plugin)
    }

    override fun getPluginInstanceForSession(pluginId: String, sessionId: String): JetWhaleHostPlugin? = loadedPlugins[PluginInstanceKey(pluginId, sessionId)]?.plugin

    override fun pluginInstanceStateFlow(pluginId: String, sessionId: String): Flow<PluginInstanceState> = instanceStatesFlow
        .map { it[PluginInstanceKey(pluginId, sessionId)] ?: PluginInstanceState.Absent }
        .distinctUntilChanged()

    override fun initializePluginInstancesForSessionsIfNeeded(pluginId: String, sessionIds: Set<String>): Set<String> {
        val loaded = pluginFactoryRepository.loadedPlugins[pluginId] ?: return emptySet()

        // Reinstalling or reloading a jar yields a new factory behind a new classloader; instances the
        // previous factory produced hold classes from a classloader that is already closed, so drop
        // them and let the loop below rebuild them from the current code.
        loadedPlugins.entries
            .filter { (key, instance) -> key.pluginId == pluginId && instance.factory !== loaded.factory }
            .map { it.key }
            .forEach { disposeInstance(it) }

        val newlyInitializedSessions = mutableSetOf<String>()
        for (sessionId in sessionIds) {
            if (createInstanceIfAbsent(pluginId, sessionId, loaded)) newlyInitializedSessions += sessionId
        }

        publishInstances()
        newlyInitializedSessions.forEach { sessionId ->
            emitEvent(PluginInstanceEvent.Ready(pluginId, sessionId))
        }
        return newlyInitializedSessions
    }

    /**
     * Creates the session's instance unless one is already loaded, reporting whether it created one.
     */
    private fun createInstanceIfAbsent(pluginId: String, sessionId: String, loaded: LoadedHostPlugin): Boolean {
        val key = PluginInstanceKey(pluginId, sessionId)
        var created = false
        @Suppress("KOTRAIL_CATCH_TOO_BROAD")
        try {
            loadedPlugins.computeIfAbsent(key) {
                created = true
                createInstance(pluginId, sessionId, loaded)
            }
            creationFailures.remove(key)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            // A factory (or plugin constructor) that throws must not abort the caller's
            // reconciliation loop — every other plugin and session still needs its instance.
            logger.log(Level.WARNING, "Creating an instance of plugin '$pluginId' for session '$sessionId' failed", e)
            creationFailures[key] = e
            return false
        }
        return created
    }

    private fun createInstance(pluginId: String, sessionId: String, loaded: LoadedHostPlugin): LoadedInstance {
        val plugin = loaded.factory.createPlugin()
        if (!loaded.manifest.requiresAgent && plugin is JetWhaleMessagingHostPlugin) {
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

        // User code below (registerHandlers, onCreate) is guarded: this runs inside the map's
        // computeIfAbsent, and a throwing plugin must neither leak the just-created peer/scope nor
        // abort loading for the caller.
        // Only messaging plugins get a peer; a pure plugin pays none of the messaging cost.
        val descriptor = "plugin '$pluginId' in session '$sessionId'"
        val peer = if (plugin is JetWhaleMessagingHostPlugin) {
            createPeer(pluginId = pluginId, sessionId = sessionId, plugin = plugin, descriptor = descriptor)
        } else {
            null
        }
        dispatchCreateGuarded(plugin, descriptor)
        val prepareJob = if (peer != null && plugin is JetWhaleMessagingHostPlugin) {
            instanceScope.launchPeerPreparation(
                peer = peer,
                descriptor = descriptor,
                prepareTimeoutMillis = plugin.prepareTimeoutMillis(),
                dispatchPrepare = plugin::dispatchPrepare,
                warn = { message, throwable -> logger.log(Level.WARNING, message, throwable) },
                onReady = {},
            )
        } else {
            null
        }
        return LoadedInstance(loaded.factory, plugin, peer, prepareJob, instanceScope)
    }

    /**
     * Builds the messaging peer for one instance and registers the plugin's handlers on it. Null
     * when registration failed: the instance still loads, but without messaging.
     */
    private fun createPeer(
        pluginId: String,
        sessionId: String,
        plugin: JetWhaleMessagingHostPlugin,
        descriptor: String,
    ): JetWhalePluginPeer? {
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
            return newPeer
        }
        // Registration failed: discard the half-configured peer (mirrors the agent's bail-out).
        // Subsequent frames fast-fail via the no-peer path in routeFrame.
        scope.launch { newPeer.close() }
        return null
    }

    /** Runs the plugin's `onCreate`; a throwing plugin must not abort loading for the caller. */
    private fun dispatchCreateGuarded(plugin: JetWhaleHostPlugin, descriptor: String) {
        @Suppress("KOTRAIL_CATCH_TOO_BROAD")
        try {
            plugin.dispatchCreate()
        } catch (e: Throwable) {
            logger.warning("onCreate for $descriptor failed: ${e.message}")
        }
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
        creationFailures.keys.removeIf { it.sessionId == sessionId }
        loadedPlugins.keys.filter { it.sessionId == sessionId }.forEach { disposeInstance(it) }
        publishInstances()
    }

    override fun unloadPluginInstancesForPlugin(pluginId: String) {
        creationFailures.keys.removeIf { it.pluginId == pluginId }
        loadedPlugins.keys.filter { it.pluginId == pluginId }.forEach { disposeInstance(it) }
        publishInstances()
    }

    override fun clearAppSessionPluginInstances() {
        creationFailures.keys.removeIf { !HostSession.isHost(it.sessionId) }
        loadedPlugins.keys.filterNot { HostSession.isHost(it.sessionId) }.forEach { disposeInstance(it, emitEvent = false) }
        publishInstances()
    }

    private fun disposeInstance(key: PluginInstanceKey, emitEvent: Boolean = true) {
        val removed = loadedPlugins.remove(key) ?: return
        // Published before onDispose runs plugin code, so a caller waiting on the instance stops
        // seeing it as soon as it is unreachable.
        publishInstances()
        @Suppress("KOTRAIL_CATCH_TOO_BROAD")
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

    /**
     * Recomputes the published instances and the headless set from the live instances. Republishing
     * the whole set (rather than patching it) is what keeps it correct across a reload, where the same
     * pluginId is replaced by an instance from a new classloader that may not answer the same way.
     *
     * Both are derived from one read and published under [publicationLock]: two threads publishing
     * at once would otherwise let the one that read the instances first overwrite the other's newer
     * state, and the two flows could each end up describing a different moment.
     */
    private fun publishInstances() = synchronized(publicationLock) {
        val instances = loadedPlugins.mapValues { (_, instance) -> instance.plugin }
        // The headless set goes first, so a screen that sees the instance already knows not to
        // build a scene for it.
        headlessPluginsFlow.value = HeadlessPlugins(
            instances.entries
                .filter { (_, plugin) -> plugin !is JetWhaleHostPluginUi }
                .groupBy({ it.key.sessionId }, { it.key.pluginId })
                .mapValues { (_, pluginIds) -> pluginIds.toSet() },
        )
        instanceStatesFlow.value = creationFailures.mapValues { (_, cause) -> PluginInstanceState.FailedToStart(cause) } +
            instances.mapValues { (_, plugin) -> PluginInstanceState.Running(plugin) }
    }

    private fun emitEvent(event: PluginInstanceEvent) {
        if (!mutablePluginInstanceEventFlow.tryEmit(event)) {
            logger.warning("Plugin instance event dropped (buffer full): $event")
        }
    }
}
