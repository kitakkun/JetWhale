package com.kitakkun.jetwhale.host.data.plugin

import com.kitakkun.jetwhale.host.model.FailedPluginJar
import com.kitakkun.jetwhale.host.model.HostPluginFrameSender
import com.kitakkun.jetwhale.host.model.HostSession
import com.kitakkun.jetwhale.host.model.LoadedHostPlugin
import com.kitakkun.jetwhale.host.model.PluginFactoryRepository
import com.kitakkun.jetwhale.host.model.PluginStorageService
import com.kitakkun.jetwhale.host.model.PluginVersionOrder
import com.kitakkun.jetwhale.host.sdk.JetWhaleHostPlugin
import com.kitakkun.jetwhale.host.sdk.JetWhaleHostPluginFactory
import com.kitakkun.jetwhale.host.sdk.JetWhaleHostPluginManifest
import com.kitakkun.jetwhale.host.sdk.JetWhalePluginStorage
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.matcher.any
import dev.mokkery.mock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertNull

class DefaultPluginInstanceServiceVersionTest {
    private val pluginId = "com.example.plugin"
    private val versionForOldAgents = loadedPluginVersion("1.2.0", minAgentVersion = "1.0.0", maxAgentVersion = "1.2.9")
    private val versionForNewAgents = loadedPluginVersion("1.3.0", minAgentVersion = "1.3.0", maxAgentVersion = null)

    private val factoryRepository = FakePluginFactoryRepository()
    private val storageService = mock<PluginStorageService> {
        every { storageFor(any()) } returns mock<JetWhalePluginStorage>()
    }
    private val service = DefaultPluginInstanceService(
        pluginFactoryRepository = factoryRepository,
        frameSender = mock<HostPluginFrameSender>(),
        pluginStorageService = storageService,
    )

    @Test
    fun `two sessions whose agents need different versions are served by both at once`() {
        factoryRepository.replaceLoadedPluginVersions(versionForOldAgents, versionForNewAgents)

        service.initializePluginInstancesForSessionsIfNeeded(pluginId, mapOf("old-app" to "1.2.0", "new-app" to "1.3.1"))

        assertEquals("1.2.0", service.boundPluginVersionsFlow.value.versionOf("old-app", pluginId))
        assertEquals("1.3.0", service.boundPluginVersionsFlow.value.versionOf("new-app", pluginId))
        assertEquals(
            setOf("old-app" to "1.2.0", "new-app" to "1.3.0"),
            service.getLoadedPluginInstances().map { it.sessionId to it.version }.toSet(),
        )
    }

    @Test
    fun `the host session is bound to the newest version`() {
        factoryRepository.replaceLoadedPluginVersions(versionForOldAgents, versionForNewAgents)

        service.initializePluginInstancesForSessionsIfNeeded(pluginId, mapOf(HostSession.ID to null))

        assertEquals("1.3.0", service.boundPluginVersionsFlow.value.versionOf(HostSession.ID, pluginId))
    }

    @Test
    fun `the host session moves to a newer version as soon as it is installed`() {
        factoryRepository.replaceLoadedPluginVersions(versionForOldAgents)
        service.initializePluginInstancesForSessionsIfNeeded(pluginId, mapOf(HostSession.ID to null))

        factoryRepository.replaceLoadedPluginVersions(versionForOldAgents, versionForNewAgents)
        service.initializePluginInstancesForSessionsIfNeeded(pluginId, mapOf(HostSession.ID to null))

        assertEquals("1.3.0", service.boundPluginVersionsFlow.value.versionOf(HostSession.ID, pluginId))
    }

    @Test
    fun `a session that no version accepts gets no instance`() {
        factoryRepository.replaceLoadedPluginVersions(versionForOldAgents, versionForNewAgents)

        val initializedSessionIds = service.initializePluginInstancesForSessionsIfNeeded(pluginId, mapOf("ancient-app" to "0.9.0"))

        assertEquals(emptySet(), initializedSessionIds)
        assertNull(service.getPluginInstanceForSession(pluginId, "ancient-app"))
    }

    @Test
    fun `a running session keeps its version when a newer one that also fits is installed`() {
        val unboundedVersion = loadedPluginVersion("1.2.0", minAgentVersion = null, maxAgentVersion = null)
        factoryRepository.replaceLoadedPluginVersions(unboundedVersion)
        service.initializePluginInstancesForSessionsIfNeeded(pluginId, mapOf("app" to "1.3.0"))
        val instanceBefore = service.getPluginInstanceForSession(pluginId, "app")

        factoryRepository.replaceLoadedPluginVersions(unboundedVersion, versionForNewAgents)
        service.initializePluginInstancesForSessionsIfNeeded(pluginId, mapOf("app" to "1.3.0", "second-app" to "1.3.0"))

        assertEquals("1.2.0", service.boundPluginVersionsFlow.value.versionOf("app", pluginId))
        assertEquals(instanceBefore, service.getPluginInstanceForSession(pluginId, "app"))
        assertEquals("1.3.0", service.boundPluginVersionsFlow.value.versionOf("second-app", pluginId))
    }

    @Test
    fun `a reload of a session's version keeps the session on that version`() {
        val unboundedVersion = loadedPluginVersion("1.2.0", minAgentVersion = null, maxAgentVersion = null)
        factoryRepository.replaceLoadedPluginVersions(unboundedVersion)
        service.initializePluginInstancesForSessionsIfNeeded(pluginId, mapOf("app" to "1.3.0"))
        factoryRepository.replaceLoadedPluginVersions(unboundedVersion, versionForNewAgents)
        service.initializePluginInstancesForSessionsIfNeeded(pluginId, mapOf("app" to "1.3.0"))
        val instanceBefore = service.getPluginInstanceForSession(pluginId, "app")

        service.unloadPluginInstancesForJar(unboundedVersion.jarPath)
        factoryRepository.replaceLoadedPluginVersions(loadedPluginVersion("1.2.0", minAgentVersion = null, maxAgentVersion = null), versionForNewAgents)
        service.initializePluginInstancesForSessionsIfNeeded(pluginId, mapOf("app" to "1.3.0"))

        assertEquals("1.2.0", service.boundPluginVersionsFlow.value.versionOf("app", pluginId))
        assertNotSame(instanceBefore, service.getPluginInstanceForSession(pluginId, "app"))
    }

    @Test
    fun `a session still on a replaced factory is rebuilt from the same version`() {
        factoryRepository.replaceLoadedPluginVersions(loadedPluginVersion("1.2.0", minAgentVersion = null, maxAgentVersion = null))
        service.initializePluginInstancesForSessionsIfNeeded(pluginId, mapOf("app" to "1.3.0"))

        factoryRepository.replaceLoadedPluginVersions(loadedPluginVersion("1.2.0", minAgentVersion = null, maxAgentVersion = null), versionForNewAgents)
        service.initializePluginInstancesForSessionsIfNeeded(pluginId, mapOf("app" to "1.3.0"))

        assertEquals("1.2.0", service.boundPluginVersionsFlow.value.versionOf("app", pluginId))
    }

    @Test
    fun `a session that disconnects during a reload binds afresh when it returns`() {
        val unboundedVersion = loadedPluginVersion("1.2.0", minAgentVersion = null, maxAgentVersion = null)
        factoryRepository.replaceLoadedPluginVersions(unboundedVersion)
        service.initializePluginInstancesForSessionsIfNeeded(pluginId, mapOf("app" to "1.3.0"))
        factoryRepository.replaceLoadedPluginVersions(unboundedVersion, versionForNewAgents)
        service.initializePluginInstancesForSessionsIfNeeded(pluginId, mapOf("app" to "1.3.0"))

        service.unloadPluginInstancesForJar(unboundedVersion.jarPath)
        service.unloadPluginInstanceForSession("app")
        factoryRepository.replaceLoadedPluginVersions(loadedPluginVersion("1.2.0", minAgentVersion = null, maxAgentVersion = null), versionForNewAgents)
        service.initializePluginInstancesForSessionsIfNeeded(pluginId, mapOf("app" to "1.3.0"))

        assertEquals("1.3.0", service.boundPluginVersionsFlow.value.versionOf("app", pluginId))
    }

    @Test
    fun `a plugin disabled during a reload binds afresh when it is enabled again`() {
        val unboundedVersion = loadedPluginVersion("1.2.0", minAgentVersion = null, maxAgentVersion = null)
        factoryRepository.replaceLoadedPluginVersions(unboundedVersion)
        service.initializePluginInstancesForSessionsIfNeeded(pluginId, mapOf("app" to "1.3.0"))
        factoryRepository.replaceLoadedPluginVersions(unboundedVersion, versionForNewAgents)
        service.initializePluginInstancesForSessionsIfNeeded(pluginId, mapOf("app" to "1.3.0"))

        service.unloadPluginInstancesForJar(unboundedVersion.jarPath)
        service.unloadPluginInstancesForPlugin(pluginId)
        factoryRepository.replaceLoadedPluginVersions(loadedPluginVersion("1.2.0", minAgentVersion = null, maxAgentVersion = null), versionForNewAgents)
        service.initializePluginInstancesForSessionsIfNeeded(pluginId, mapOf("app" to "1.3.0"))

        assertEquals("1.3.0", service.boundPluginVersionsFlow.value.versionOf("app", pluginId))
    }

    @Test
    fun `a session whose version is removed moves to another version that fits it`() {
        val unboundedVersion = loadedPluginVersion("1.2.0", minAgentVersion = null, maxAgentVersion = null)
        factoryRepository.replaceLoadedPluginVersions(unboundedVersion)
        service.initializePluginInstancesForSessionsIfNeeded(pluginId, mapOf("app" to "1.3.0"))

        service.unloadPluginInstancesForJar(unboundedVersion.jarPath)
        factoryRepository.replaceLoadedPluginVersions(versionForNewAgents)
        service.initializePluginInstancesForSessionsIfNeeded(pluginId, mapOf("app" to "1.3.0"))

        assertEquals("1.3.0", service.boundPluginVersionsFlow.value.versionOf("app", pluginId))
    }

    @Test
    fun `unloading one version's jar leaves the sessions bound to other versions running`() {
        factoryRepository.replaceLoadedPluginVersions(versionForOldAgents, versionForNewAgents)
        service.initializePluginInstancesForSessionsIfNeeded(pluginId, mapOf("old-app" to "1.2.0", "new-app" to "1.3.0"))

        service.unloadPluginInstancesForJar(versionForOldAgents.jarPath)

        assertNull(service.getPluginInstanceForSession(pluginId, "old-app"))
        assertEquals("1.3.0", service.boundPluginVersionsFlow.value.versionOf("new-app", pluginId))
    }

    private fun loadedPluginVersion(version: String, minAgentVersion: String?, maxAgentVersion: String?) = LoadedHostPlugin(
        manifest = JetWhaleHostPluginManifest(
            pluginId = pluginId,
            pluginName = "Example",
            version = version,
            factoryClass = "com.example.Factory",
            agentVersionRange = JetWhaleHostPluginManifest.AgentVersionRange(min = minAgentVersion, max = maxAgentVersion),
        ),
        factory = object : JetWhaleHostPluginFactory {
            override fun createPlugin(): JetWhaleHostPlugin = object : JetWhaleHostPlugin() {}
        },
        jarPath = "/plugins/example-$version.jar",
    )

    private class FakePluginFactoryRepository : PluginFactoryRepository {
        private val mutableLoadedPluginVersionsFlow = MutableStateFlow<Map<String, List<LoadedHostPlugin>>>(emptyMap())
        override val loadedPluginVersionsFlow: Flow<Map<String, List<LoadedHostPlugin>>> = mutableLoadedPluginVersionsFlow
        override val loadedPluginVersions: Map<String, List<LoadedHostPlugin>> get() = mutableLoadedPluginVersionsFlow.value
        override val loadedPlugins: Map<String, LoadedHostPlugin> get() = mutableLoadedPluginVersionsFlow.value.mapValues { (_, loaded) -> loaded.first() }
        override val loadedPluginsFlow: Flow<Map<String, LoadedHostPlugin>> = MutableStateFlow(emptyMap())
        override val failedJarsFlow: Flow<List<FailedPluginJar>> = MutableStateFlow(emptyList())

        fun replaceLoadedPluginVersions(vararg plugins: LoadedHostPlugin) {
            mutableLoadedPluginVersionsFlow.value = plugins.groupBy { it.manifest.pluginId }
                .mapValues { (_, loaded) -> loaded.sortedWith(compareByDescending(PluginVersionOrder) { it.manifest.version }) }
        }

        override suspend fun loadPlugin(pluginJarPath: String, expectedSha256: String?) = Unit
        override suspend fun unloadPluginJar(pluginJarPath: String) = Unit
        override fun findPluginIdsByJarPath(pluginJarPath: String): List<String> = emptyList()
        override suspend fun reloadPlugin(pluginJarPath: String, expectedSha256: String?): List<String> = emptyList()
        override fun tryRedefinePlugin(pluginJarPath: String): List<String> = emptyList()
    }
}
