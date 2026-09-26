package com.kitakkun.jetwhale.host.data.plugin

import com.kitakkun.jetwhale.host.model.BoundPluginVersions
import com.kitakkun.jetwhale.host.model.HeadlessPlugins
import com.kitakkun.jetwhale.host.model.HostPluginFrameSender
import com.kitakkun.jetwhale.host.model.HostSession
import com.kitakkun.jetwhale.host.model.LoadedHostPlugin
import com.kitakkun.jetwhale.host.model.LoadedPluginInstance
import com.kitakkun.jetwhale.host.model.PluginFactoryRepository
import com.kitakkun.jetwhale.host.model.PluginInstanceEvent
import com.kitakkun.jetwhale.host.model.PluginInstanceService
import com.kitakkun.jetwhale.host.model.PluginStorageService
import com.kitakkun.jetwhale.host.model.newestFor
import com.kitakkun.jetwhale.host.sdk.InternalJetWhaleHostApi
import com.kitakkun.jetwhale.host.sdk.JetWhaleHostPlugin
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
import kotlinx.coroutines.CoroutineStart
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
 * @property loaded The plugin version that produced [plugin]. Its factory identifies the classloader
 *   generation this instance belongs to.
 * @property peer Null for a pure (non-messaging) plugin: no peer is created for it.
 * @property prepareJob The preparation job; joined before the peer is closed so its ready-gate open
 *   cannot outrace disposal. Null for a pure plugin. For a plugin that requires an agent it waits,
 *   unstarted, for [PluginInstanceService.startPluginInstancePreparation].
 * @property instanceScope Backs the plugin's `pluginScope`; cancelled when the instance is disposed.
 */
private class LoadedInstance(
    val loaded: LoadedHostPlugin,
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
    private val pluginStorageService: PluginStorageService,
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

    override val boundVersionsFlow: StateFlow<BoundPluginVersions>
        field = MutableStateFlow(BoundPluginVersions.Empty)

    override fun getLoadedPluginInstances(): List<LoadedPluginInstance> = loadedPlugins.entries.map { (key, instance) ->
        LoadedPluginInstance(pluginId = key.pluginId, sessionId = key.sessionId, plugin = instance.plugin, version = instance.loaded.manifest.version)
    }

    override fun getPluginInstanceForSession(pluginId: String, sessionId: String): JetWhaleHostPlugin? = loadedPlugins[PluginInstanceKey(pluginId, sessionId)]?.plugin

    override fun initializePluginInstancesForSessionsIfNeeded(pluginId: String, sessions: Map<String, String?>): Set<String> {
        val versions = pluginFactoryRepository.loadedPluginVersions[pluginId].orEmpty()
        if (versions.isEmpty()) return emptySet()

        // Instances of a reloaded or removed version hold classes from a closed classloader, so they go
        // and the loop below rebuilds them; an app keeps a version that is still loaded. The host
        // session lasts as long as the host, so it moves to the newest at once rather than on restart.
        val previousVersions = mutableMapOf<String, String>()
        loadedPlugins.entries
            .filter { (key, instance) ->
                key.pluginId == pluginId &&
                    if (key.sessionId == HostSession.ID) {
                        instance.loaded.factory !== versions.first().factory
                    } else {
                        versions.none { it.factory === instance.loaded.factory }
                    }
            }
            .forEach { (key, instance) ->
                if (key.sessionId != HostSession.ID) previousVersions[key.sessionId] = instance.loaded.manifest.version
                disposeInstance(key)
            }

        val newlyInitializedSessions = mutableSetOf<String>()
        for ((sessionId, agentVersion) in sessions) {
            val loaded = versions.bindingFor(agentVersion, previousVersions[sessionId]) ?: continue
            if (createInstanceIfAbsent(pluginId, sessionId, loaded)) newlyInitializedSessions += sessionId
        }

        publishInstanceState()
        newlyInitializedSessions.forEach { sessionId ->
            val version = loadedPlugins[PluginInstanceKey(pluginId, sessionId)]?.loaded?.manifest?.version ?: return@forEach
            mutablePluginInstanceEventFlow.tryEmit(PluginInstanceEvent.Ready(pluginId = pluginId, sessionId = sessionId, version = version))
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
        val plugin = loaded.factory.createPlugin()
        if (!loaded.manifest.requiresAgent && plugin is JetWhaleMessagingHostPlugin) {
            logger.warning(
                "Plugin '$pluginId' declares requiresAgent=false but its factory returns a JetWhaleMessagingHostPlugin; " +
                    "its messenger will never reach an agent.",
            )
        }
        val instanceScope = CoroutineScope(scope.coroutineContext + SupervisorJob(scope.coroutineContext[Job]))
        plugin.bindPluginScope(instanceScope)

        plugin.bindStorage(pluginStorageService.storageFor(loaded))

        val descriptor = "plugin '$pluginId' in session '$sessionId'"
        val peer = if (plugin is JetWhaleMessagingHostPlugin) {
            createPeer(pluginId = pluginId, sessionId = sessionId, plugin = plugin, descriptor = descriptor)
        } else {
            null
        }
        dispatchCreateGuarded(plugin, descriptor)
        val prepareJob = if (peer != null && plugin is JetWhaleMessagingHostPlugin) {
            instanceScope.launch(start = if (loaded.manifest.requiresAgent) CoroutineStart.LAZY else CoroutineStart.DEFAULT) {
                launchPeerPreparation(
                    peer = peer,
                    descriptor = descriptor,
                    prepareTimeoutMillis = plugin.prepareTimeoutMillis(),
                    dispatchPrepare = plugin::dispatchPrepare,
                    warn = { message, throwable -> logger.log(Level.WARNING, message, throwable) },
                    onReady = {},
                )
            }
        } else {
            null
        }
        return LoadedInstance(loaded, plugin, peer, prepareJob, instanceScope)
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

    override fun startPluginInstancePreparation(pluginId: String, sessionId: String) {
        loadedPlugins[PluginInstanceKey(pluginId, sessionId)]?.prepareJob?.start()
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

    override fun unloadPluginInstancesForJar(jarPath: String) {
        loadedPlugins.entries.filter { (_, instance) -> instance.loaded.jarPath == jarPath }.forEach { disposeInstance(it.key) }
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
        publishInstanceState()
        mutablePluginInstanceEventFlow.tryEmit(PluginInstanceEvent.Disposed(key.pluginId, key.sessionId))
    }

    /**
     * Recomputes the headless set and the bound versions from the live instances. Republishing them
     * whole (rather than patching them) is what keeps them correct across a reload, where the same
     * pluginId is replaced by an instance from a new classloader that may not answer the same way.
     */
    private fun publishInstanceState() {
        val entries = loadedPlugins.entries.toList()
        headlessPluginsFlow.value = HeadlessPlugins(
            entries
                .filter { (_, instance) -> instance.plugin !is JetWhaleHostPluginUi }
                .groupBy({ it.key.sessionId }, { it.key.pluginId })
                .mapValues { (_, pluginIds) -> pluginIds.toSet() },
        )
        boundVersionsFlow.value = BoundPluginVersions(
            entries
                .groupBy({ it.key.sessionId }, { (key, instance) -> key.pluginId to instance.loaded.manifest.version })
                .mapValues { (_, versions) -> versions.toMap() },
        )
    }
}

/**
 * The version a session gets: the one it had before its instance was dropped for a reload, while that
 * version is still loaded and still accepts the session's agent, otherwise the newest that fits.
 */
private fun List<LoadedHostPlugin>.bindingFor(agentVersion: String?, previousVersion: String?): LoadedHostPlugin? = filter { it.manifest.version == previousVersion }.newestFor(agentVersion) ?: newestFor(agentVersion)
