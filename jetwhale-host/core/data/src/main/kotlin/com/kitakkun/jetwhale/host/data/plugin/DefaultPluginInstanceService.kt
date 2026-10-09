package com.kitakkun.jetwhale.host.data.plugin

import com.kitakkun.jetwhale.host.model.AgentVersionCompatibility
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
 * @property loadedVersion The plugin version that produced [plugin]. Its factory identifies the classloader
 *   generation this instance belongs to.
 * @property peer Null for a pure (non-messaging) plugin: no peer is created for it.
 * @property prepareJob The preparation job; joined before the peer is closed so its ready-gate open
 *   cannot outrace disposal. Null for a pure plugin. For a plugin that requires an agent it waits,
 *   unstarted, for [PluginInstanceService.startPluginInstancePreparation].
 * @property instanceScope Backs the plugin's `pluginScope`; cancelled when the instance is disposed.
 */
private class LoadedInstance(
    val loadedVersion: LoadedHostPlugin,
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

    /**
     * The version each app instance ran when it was disposed to be rebuilt from new code, until the
     * session gets an instance again. A reload disposes before it loads the jar again, so by the time
     * the instance is rebuilt nothing else records which version the session was on.
     */
    private val versionsBeforeRebuild: ConcurrentHashMap<PluginInstanceKey, String> = ConcurrentHashMap()

    // Unbounded: the server stopping disposes every app session's instances in one burst, and a
    // dropped Disposed would leave that instance's MCP tools listed.
    private val mutablePluginInstanceEventFlow: MutableSharedFlow<PluginInstanceEvent> = MutableSharedFlow(extraBufferCapacity = Channel.UNLIMITED)
    override val pluginInstanceEventFlow: SharedFlow<PluginInstanceEvent> = mutablePluginInstanceEventFlow.asSharedFlow()

    override val headlessPluginsFlow: StateFlow<HeadlessPlugins>
        field = MutableStateFlow(HeadlessPlugins.Empty)

    override val boundPluginVersionsFlow: StateFlow<BoundPluginVersions>
        field = MutableStateFlow(BoundPluginVersions.Empty)

    override fun getLoadedPluginInstances(): List<LoadedPluginInstance> = loadedPlugins.entries.map { (key, instance) ->
        LoadedPluginInstance(pluginId = key.pluginId, sessionId = key.sessionId, plugin = instance.plugin, version = instance.loadedVersion.manifest.version)
    }

    override fun getPluginInstanceForSession(pluginId: String, sessionId: String): JetWhaleHostPlugin? = loadedPlugins[PluginInstanceKey(pluginId, sessionId)]?.plugin

    override fun initializePluginInstancesForSessionsIfNeeded(pluginId: String, agentVersionsBySession: Map<String, String?>): Set<String> {
        val versions = pluginFactoryRepository.loadedPluginVersions[pluginId].orEmpty()
        if (versions.isEmpty()) return emptySet()

        // The host session lasts as long as the host, so it moves to the newest version as soon as
        // one loads; an app session keeps its version until that version is reloaded or removed.
        loadedPlugins.entries
            .filter { (key, instance) ->
                key.pluginId == pluginId &&
                    if (key.sessionId == HostSession.ID) {
                        instance.loadedVersion.factory !== versions.first().factory
                    } else {
                        versions.none { it.factory === instance.loadedVersion.factory }
                    }
            }
            .forEach { (key, instance) -> disposeInstanceRememberingVersion(key, instance) }

        val newlyInitializedSessions = mutableSetOf<String>()
        for ((sessionId, agentVersion) in agentVersionsBySession) {
            val key = PluginInstanceKey(pluginId, sessionId)
            val loaded = selectVersionToBind(versions, agentVersion, versionBeforeRebuild = versionsBeforeRebuild[key]) ?: continue
            if (createInstanceIfAbsent(pluginId, sessionId, loaded)) {
                versionsBeforeRebuild.remove(key)
                newlyInitializedSessions += sessionId
            }
        }

        publishHeadlessPluginsAndBoundVersions()
        newlyInitializedSessions.forEach { sessionId ->
            val version = loadedPlugins[PluginInstanceKey(pluginId, sessionId)]?.loadedVersion?.manifest?.version ?: return@forEach
            mutablePluginInstanceEventFlow.tryEmit(PluginInstanceEvent.Ready(pluginId = pluginId, sessionId = sessionId, version = version))
        }
        return newlyInitializedSessions
    }

    /**
     * The version a session gets: the one it ran before its instance was rebuilt, while that version is
     * still loaded and still serves the session's agent, otherwise the newest that does.
     */
    private fun selectVersionToBind(versions: List<LoadedHostPlugin>, agentVersion: String?, versionBeforeRebuild: String?): LoadedHostPlugin? {
        val compatibility = AgentVersionCompatibility(agentVersion)
        return compatibility.newestCompatibleOf(versions.filter { it.manifest.version == versionBeforeRebuild })
            ?: compatibility.newestCompatibleOf(versions)
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
        versionsBeforeRebuild.keys.removeIf { it.sessionId == sessionId }
        loadedPlugins.keys.filter { it.sessionId == sessionId }.forEach(::disposeInstance)
    }

    override fun unloadPluginInstancesForPlugin(pluginId: String) {
        versionsBeforeRebuild.keys.removeIf { it.pluginId == pluginId }
        loadedPlugins.keys.filter { it.pluginId == pluginId }.forEach(::disposeInstance)
    }

    override fun unloadPluginInstancesForJar(jarPath: String) {
        loadedPlugins.entries.filter { (_, instance) -> instance.loadedVersion.jarPath == jarPath }.forEach { (key, instance) -> disposeInstanceRememberingVersion(key, instance) }
    }

    override fun clearAppSessionPluginInstances() {
        versionsBeforeRebuild.clear()
        loadedPlugins.keys.filterNot { HostSession.isHost(it.sessionId) }.forEach(::disposeInstance)
    }

    private fun disposeInstanceRememberingVersion(key: PluginInstanceKey, instance: LoadedInstance) {
        if (!HostSession.isHost(key.sessionId)) versionsBeforeRebuild[key] = instance.loadedVersion.manifest.version
        disposeInstance(key)
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
        publishHeadlessPluginsAndBoundVersions()
        mutablePluginInstanceEventFlow.tryEmit(PluginInstanceEvent.Disposed(key.pluginId, key.sessionId))
    }

    /**
     * Recomputes the headless set and the bound versions from the live instances. Republishing them
     * whole (rather than patching them) is what keeps them correct across a reload, where the same
     * pluginId is replaced by an instance from a new classloader that may not answer the same way.
     */
    private fun publishHeadlessPluginsAndBoundVersions() {
        // toMutableList() copies the live view in one pass; toList() on a single instance reads the
        // size, then the entry, and throws if the instance leaves in between.
        val entries = loadedPlugins.entries.toMutableList()
        headlessPluginsFlow.value = HeadlessPlugins(
            entries
                .filter { (_, instance) -> instance.plugin !is JetWhaleHostPluginUi }
                .groupBy({ it.key.sessionId }, { it.key.pluginId })
                .mapValues { (_, pluginIds) -> pluginIds.toSet() },
        )
        boundPluginVersionsFlow.value = BoundPluginVersions(
            entries
                .groupBy({ it.key.sessionId }, { (key, instance) -> key.pluginId to instance.loadedVersion.manifest.version })
                .mapValues { (_, versions) -> versions.toMap() },
        )
    }
}
