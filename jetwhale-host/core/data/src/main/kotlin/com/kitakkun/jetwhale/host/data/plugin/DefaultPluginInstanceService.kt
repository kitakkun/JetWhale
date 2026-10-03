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
import com.kitakkun.jetwhale.host.sdk.InternalJetWhaleHostApi
import com.kitakkun.jetwhale.host.sdk.JetWhaleHostPlugin
import com.kitakkun.jetwhale.host.sdk.JetWhaleHostPluginContext
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
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
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
    private val pluginContext: JetWhaleHostPluginContext,
) : PluginInstanceService {
    private val logger = Logger.getLogger(DefaultPluginInstanceService::class.java.name)

    /** Parent scope for every plugin peer; each peer also gets its own child supervisor. */
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val loadedPlugins: ConcurrentHashMap<PluginInstanceKey, LoadedInstance> = ConcurrentHashMap()

    // Unbounded: the server stopping disposes every app session's instances in one burst, and a
    // dropped Disposed would leave that instance's MCP tools listed.
    private val mutablePluginInstanceEventFlow: MutableSharedFlow<PluginInstanceEvent> = MutableSharedFlow(extraBufferCapacity = Channel.UNLIMITED)
    override val pluginInstanceEventFlow: SharedFlow<PluginInstanceEvent> = mutablePluginInstanceEventFlow.asSharedFlow()

    override val headlessPluginsFlow: StateFlow<HeadlessPlugins>
        field = MutableStateFlow(HeadlessPlugins.Empty)

    override fun getLoadedPluginInstances(): List<LoadedPluginInstance> = loadedPlugins.entries.map { (key, instance) ->
        LoadedPluginInstance(pluginId = key.pluginId, sessionId = key.sessionId, plugin = instance.plugin)
    }

    override fun getPluginInstanceForSession(pluginId: String, sessionId: String): JetWhaleHostPlugin? = loadedPlugins[PluginInstanceKey(pluginId, sessionId)]?.plugin

    override fun initializePluginInstancesForSessionsIfNeeded(pluginId: String, sessionIds: Set<String>): Set<String> {
        val loaded = pluginFactoryRepository.loadedPlugins[pluginId] ?: return emptySet()

        loadedPlugins.entries
            .filter { (key, instance) -> key.pluginId == pluginId && instance.factory !== loaded.factory }
            .map { it.key }
            .forEach(::disposeInstance)

        val newlyInitializedSessions = mutableSetOf<String>()
        for (sessionId in sessionIds) {
            if (createInstanceIfAbsent(pluginId, sessionId, loaded)) newlyInitializedSessions += sessionId
        }

        publishHeadlessPlugins()
        newlyInitializedSessions.forEach { sessionId ->
            mutablePluginInstanceEventFlow.tryEmit(PluginInstanceEvent.Ready(pluginId, sessionId))
        }
        return newlyInitializedSessions
    }

    /**
     * Creates the session's instance unless one is already loaded, reporting whether it created one.
     */
    private fun createInstanceIfAbsent(pluginId: String, sessionId: String, loaded: LoadedHostPlugin): Boolean {
        val key = PluginInstanceKey(pluginId, sessionId)
        var created = false
        // A plugin whose factory throws fails only its own instance; the other plugins still get
        // theirs.
        @Suppress("KOTRAIL_CATCH_TOO_BROAD")
        try {
            loadedPlugins.computeIfAbsent(key) {
                created = true
                createInstance(pluginId, sessionId, loaded)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            logger.log(Level.WARNING, "Creating an instance of plugin '$pluginId' for session '$sessionId' failed", e)
            return false
        }
        return created
    }

    private fun createInstance(pluginId: String, sessionId: String, loaded: LoadedHostPlugin): LoadedInstance {
        val plugin = loaded.factory.createPlugin(pluginContext)
        if (!loaded.manifest.requiresAgent && plugin is JetWhaleMessagingHostPlugin) {
            logger.warning(
                "Plugin '$pluginId' declares requiresAgent=false but its factory returns a JetWhaleMessagingHostPlugin; " +
                    "its messenger will never reach an agent.",
            )
        }
        val instanceScope = CoroutineScope(scope.coroutineContext + SupervisorJob(scope.coroutineContext[Job]))
        plugin.bindPluginScope(instanceScope)

        plugin.bindStorage(pluginDataStoreRepository.storageFor(pluginId))

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
        scope.launch { newPeer.close() }
        return null
    }

    /** Runs the plugin's `onCreate`; a throwing plugin must not abort loading for the caller. */
    private fun dispatchCreateGuarded(plugin: JetWhaleHostPlugin, descriptor: String) {
        // onCreate is the plugin's code, and anything it throws must not abort loading for the
        // caller.
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
        replyPeerUnavailable(
            scope = scope,
            frame = frame,
            errorMessage = "Plugin '${frame.pluginId}' is not loaded in session '$sessionId'.",
            send = { failureFrame -> frameSender.sendFrame(sessionId = sessionId, frame = failureFrame) },
            warn = { message, throwable -> logger.log(Level.WARNING, message, throwable) },
        )
    }

    override fun unloadPluginInstanceForSession(sessionId: String) {
        loadedPlugins.keys.filter { it.sessionId == sessionId }.forEach(::disposeInstance)
    }

    override fun unloadPluginInstancesForPlugin(pluginId: String) {
        loadedPlugins.keys.filter { it.pluginId == pluginId }.forEach(::disposeInstance)
    }

    override fun clearAppSessionPluginInstances() {
        loadedPlugins.keys.filterNot { HostSession.isHost(it.sessionId) }.forEach(::disposeInstance)
    }

    private fun disposeInstance(key: PluginInstanceKey) {
        val removed = loadedPlugins.remove(key) ?: return
        // onDispose is the plugin's code; whatever it throws, its scope is still cancelled and its
        // peer closed.
        @Suppress("KOTRAIL_CATCH_TOO_BROAD")
        try {
            removed.plugin.dispatchDispose()
        } catch (e: Throwable) {
            logger.warning("onDispose for plugin '${key.pluginId}' in session '${key.sessionId}' failed: ${e.message}")
        } finally {
            removed.instanceScope.cancel()
            removed.peer?.let { peer ->
                scope.launch {
                    removed.prepareJob?.cancelAndJoin()
                    peer.close()
                }
            }
        }
        publishHeadlessPlugins()
        mutablePluginInstanceEventFlow.tryEmit(PluginInstanceEvent.Disposed(key.pluginId, key.sessionId))
    }

    /**
     * Recomputes the headless set from the live instances. Republishing the whole set (rather than
     * patching it) is what keeps it correct across a reload, where the same pluginId is replaced by
     * an instance from a new classloader that may not answer the same way.
     */
    private fun publishHeadlessPlugins() {
        headlessPluginsFlow.value = HeadlessPlugins(
            loadedPlugins.entries
                .filter { (_, instance) -> instance.plugin !is JetWhaleHostPluginUi }
                .groupBy({ it.key.sessionId }, { it.key.pluginId })
                .mapValues { (_, pluginIds) -> pluginIds.toSet() },
        )
    }
}
