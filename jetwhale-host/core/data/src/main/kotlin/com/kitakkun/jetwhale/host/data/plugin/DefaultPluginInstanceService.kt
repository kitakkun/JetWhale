package com.kitakkun.jetwhale.host.data.plugin

import com.kitakkun.jetwhale.host.model.HeadlessPlugins
import com.kitakkun.jetwhale.host.model.HostPluginFrameSender
import com.kitakkun.jetwhale.host.model.LoadedHostPlugin
import com.kitakkun.jetwhale.host.model.LoadedPluginInstance
import com.kitakkun.jetwhale.host.model.PluginDataStoreRepository
import com.kitakkun.jetwhale.host.model.PluginFactoryRepository
import com.kitakkun.jetwhale.host.model.PluginInstanceEvent
import com.kitakkun.jetwhale.host.model.PluginInstanceService
import com.kitakkun.jetwhale.host.sdk.InternalJetWhaleHostApi
import com.kitakkun.jetwhale.host.sdk.JetWhaleHostPlugin
import com.kitakkun.jetwhale.host.sdk.JetWhaleHostPluginContext
import com.kitakkun.jetwhale.host.sdk.JetWhaleHostPluginFactory
import com.kitakkun.jetwhale.host.sdk.JetWhaleHostPluginScope
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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import java.util.logging.Level
import java.util.logging.Logger

/** Identifies one plugin instance. [sessionId] is null for the single instance of a host-scoped plugin. */
private data class PluginInstanceKey(val pluginId: String, val sessionId: String?)

/**
 * A plugin instance paired with the messaging peer that delivers its frames. The peer's outbound
 * frames are sent to this instance's session; inbound frames are routed to it by the server.
 */
private class LoadedInstance(
    /** The factory that produced [plugin]; identifies the classloader generation this instance belongs to. */
    val factory: JetWhaleHostPluginFactory,
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
    private val hostPluginContext: JetWhaleHostPluginContext,
) : PluginInstanceService {
    private val logger = Logger.getLogger(DefaultPluginInstanceService::class.java.name)

    /** Parent scope for every plugin peer; each peer also gets its own child supervisor. */
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val loadedPlugins: ConcurrentHashMap<PluginInstanceKey, LoadedInstance> = ConcurrentHashMap()

    private val mutablePluginInstanceEventFlow: MutableSharedFlow<PluginInstanceEvent> = MutableSharedFlow(extraBufferCapacity = 64)
    override val pluginInstanceEventFlow: SharedFlow<PluginInstanceEvent> = mutablePluginInstanceEventFlow.asSharedFlow()

    override val headlessPluginsFlow: StateFlow<HeadlessPlugins>
        field = MutableStateFlow(HeadlessPlugins.Empty)

    override fun getLoadedPluginInstances(): List<LoadedPluginInstance> = loadedPlugins.entries.map { (key, instance) ->
        LoadedPluginInstance(pluginId = key.pluginId, sessionId = key.sessionId, plugin = instance.plugin)
    }

    override fun getPluginInstanceForSession(pluginId: String, sessionId: String): JetWhaleHostPlugin? = loadedPlugins[PluginInstanceKey(pluginId, sessionId)]?.plugin

    override fun getHostScopedInstance(pluginId: String): JetWhaleHostPlugin? = loadedPlugins[PluginInstanceKey(pluginId, null)]?.plugin

    override fun initializeHostScopedInstanceIfNeeded(pluginId: String): Boolean {
        val loaded = pluginFactoryRepository.loadedPlugins[pluginId] ?: return false
        dropInstancesFromStaleFactories(pluginId, loaded)

        val key = PluginInstanceKey(pluginId, sessionId = null)
        var created = false
        try {
            // compute, not computeIfAbsent: a refused instance (a messaging plugin declared
            // host-scoped) maps to no entry at all, which computeIfAbsent cannot express.
            loadedPlugins.compute(key) { _, existing ->
                existing ?: createInstance(pluginId, sessionId = null, loaded = loaded)?.also { created = true }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            logger.log(Level.WARNING, "Creating the host-scoped instance of plugin '$pluginId' failed", e)
            return false
        }

        publishHeadlessPlugins()
        if (created) emitEvent(PluginInstanceEvent.Ready(pluginId, sessionId = null))
        return created
    }

    /**
     * Drops the instances a previous factory generation produced. Reinstalling or reloading a jar
     * yields a new factory behind a new classloader, and instances the previous factory produced hold
     * classes from a classloader that is already closed.
     */
    private fun dropInstancesFromStaleFactories(pluginId: String, loaded: LoadedHostPlugin) {
        loadedPlugins.entries
            .filter { (key, instance) -> key.pluginId == pluginId && instance.factory !== loaded.factory }
            .map { it.key }
            .forEach { disposeInstance(it) }
    }

    override fun initializePluginInstancesForSessionsIfNeeded(pluginId: String, sessionIds: Set<String>): Set<String> {
        val loaded = pluginFactoryRepository.loadedPlugins[pluginId] ?: return emptySet()

        // Drop the instances of a previous factory generation so the loop below rebuilds them from
        // the current code.
        dropInstancesFromStaleFactories(pluginId, loaded)

        val newlyInitializedSessions = mutableSetOf<String>()
        for (sessionId in sessionIds) {
            val key = PluginInstanceKey(pluginId, sessionId)
            var created = false
            try {
                loadedPlugins.compute(key) { _, existing ->
                    existing ?: createInstance(pluginId, sessionId, loaded)?.also { created = true }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                // A factory (or plugin constructor) that throws must not abort the caller's
                // reconciliation loop — every other plugin and session still needs its instance.
                logger.log(Level.WARNING, "Creating an instance of plugin '$pluginId' for session '$sessionId' failed", e)
                continue
            }
            if (created) newlyInitializedSessions += sessionId
        }

        publishHeadlessPlugins()
        newlyInitializedSessions.forEach { sessionId ->
            emitEvent(PluginInstanceEvent.Ready(pluginId, sessionId))
        }
        return newlyInitializedSessions
    }

    private fun createInstance(pluginId: String, sessionId: String?, loaded: LoadedHostPlugin): LoadedInstance? {
        val plugin = loaded.factory.createPlugin(hostPluginContext)
        if (loaded.manifest.scope == JetWhaleHostPluginScope.HOST && plugin is JetWhaleMessagingHostPlugin) {
            // A host-scoped instance belongs to no session, so there is no connection its messenger
            // could ever reach. Skipping the instance surfaces the misconfiguration instead of
            // running a plugin whose every request times out.
            logger.warning(
                "Plugin '$pluginId' declares scope=host but its factory returns a JetWhaleMessagingHostPlugin; " +
                    "a host-scoped plugin has no agent to talk to, so no instance was created.",
            )
            return null
        }
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

        val descriptor = if (sessionId == null) "host-scoped plugin '$pluginId'" else "plugin '$pluginId' in session '$sessionId'"

        // User code below (registerHandlers, onCreate) is guarded: this runs inside the map's
        // computeIfAbsent, and a throwing plugin must neither leak the just-created peer/scope nor
        // abort loading for the caller.
        // Only messaging plugins get a peer; a pure plugin pays none of the messaging cost. A
        // host-scoped plugin is never a messaging one (refused above), so it never reaches this.
        val peer = if (plugin is JetWhaleMessagingHostPlugin && sessionId != null) {
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
            logger.warning("onCreate for $descriptor failed: ${e.message}")
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
        return LoadedInstance(loaded.factory, plugin, peer, prepareJob, instanceScope)
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
        // A host-scoped instance carries a null sessionId and outlives every session, so it never matches.
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
            logger.warning("onDispose for plugin '${key.pluginId}' (session '${key.sessionId}') failed: ${e.message}")
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
        publishHeadlessPlugins()
        if (emitEvent) emitEvent(PluginInstanceEvent.Disposed(key.pluginId, key.sessionId))
    }

    /**
     * Recomputes the headless set from the live instances. Republishing the whole set (rather than
     * patching it) is what keeps it correct across a reload, where the same pluginId is replaced by
     * an instance from a new classloader that may not answer the same way.
     */
    private fun publishHeadlessPlugins() {
        val headless = loadedPlugins.entries.filter { (_, instance) -> instance.plugin !is JetWhaleHostPluginUi }.map { it.key }
        headlessPluginsFlow.value = HeadlessPlugins(
            pluginIdsBySession = headless
                .mapNotNull { key -> key.sessionId?.let { it to key.pluginId } }
                .groupBy({ it.first }, { it.second })
                .mapValues { (_, pluginIds) -> pluginIds.toSet() },
            hostScopedPluginIds = headless.filter { it.sessionId == null }.mapTo(mutableSetOf()) { it.pluginId },
        )
    }

    private fun emitEvent(event: PluginInstanceEvent) {
        if (!mutablePluginInstanceEventFlow.tryEmit(event)) {
            logger.warning("Plugin instance event dropped (buffer full): $event")
        }
    }
}
