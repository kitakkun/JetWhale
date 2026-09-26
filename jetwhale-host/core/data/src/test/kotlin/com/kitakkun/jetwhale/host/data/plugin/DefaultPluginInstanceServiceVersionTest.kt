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
    private val forOldAgents = version("1.2.0", minAgent = "1.0.0", maxAgent = "1.2.9")
    private val forNewAgents = version("1.3.0", minAgent = "1.3.0", maxAgent = null)

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
        factoryRepository.load(forOldAgents, forNewAgents)

        service.initializePluginInstancesForSessionsIfNeeded(pluginId, mapOf("old-app" to "1.2.0", "new-app" to "1.3.1"))

        assertEquals("1.2.0", service.boundVersionsFlow.value.versionOf("old-app", pluginId))
        assertEquals("1.3.0", service.boundVersionsFlow.value.versionOf("new-app", pluginId))
        assertEquals(
            setOf("old-app" to "1.2.0", "new-app" to "1.3.0"),
            service.getLoadedPluginInstances().map { it.sessionId to it.version }.toSet(),
        )
    }

    @Test
    fun `the host session is bound to the newest version`() {
        factoryRepository.load(forOldAgents, forNewAgents)

        service.initializePluginInstancesForSessionsIfNeeded(pluginId, mapOf(HostSession.ID to null))

        assertEquals("1.3.0", service.boundVersionsFlow.value.versionOf(HostSession.ID, pluginId))
    }

    @Test
    fun `the host session moves to a newer version as soon as it is installed`() {
        factoryRepository.load(forOldAgents)
        service.initializePluginInstancesForSessionsIfNeeded(pluginId, mapOf(HostSession.ID to null))

        factoryRepository.load(forOldAgents, forNewAgents)
        service.initializePluginInstancesForSessionsIfNeeded(pluginId, mapOf(HostSession.ID to null))

        assertEquals("1.3.0", service.boundVersionsFlow.value.versionOf(HostSession.ID, pluginId))
    }

    @Test
    fun `a session that no version accepts gets no instance`() {
        factoryRepository.load(forOldAgents, forNewAgents)

        val initialized = service.initializePluginInstancesForSessionsIfNeeded(pluginId, mapOf("ancient-app" to "0.9.0"))

        assertEquals(emptySet(), initialized)
        assertNull(service.getPluginInstanceForSession(pluginId, "ancient-app"))
    }

    @Test
    fun `a running session keeps its version when a newer one that also fits is installed`() {
        val unbounded = version("1.2.0", minAgent = null, maxAgent = null)
        factoryRepository.load(unbounded)
        service.initializePluginInstancesForSessionsIfNeeded(pluginId, mapOf("app" to "1.3.0"))
        val instanceBefore = service.getPluginInstanceForSession(pluginId, "app")

        factoryRepository.load(unbounded, forNewAgents)
        service.initializePluginInstancesForSessionsIfNeeded(pluginId, mapOf("app" to "1.3.0", "second-app" to "1.3.0"))

        assertEquals("1.2.0", service.boundVersionsFlow.value.versionOf("app", pluginId))
        assertEquals(instanceBefore, service.getPluginInstanceForSession(pluginId, "app"))
        assertEquals("1.3.0", service.boundVersionsFlow.value.versionOf("second-app", pluginId))
    }

    @Test
    fun `a reload of a session's version keeps the session on that version`() {
        factoryRepository.load(version("1.2.0", minAgent = null, maxAgent = null))
        service.initializePluginInstancesForSessionsIfNeeded(pluginId, mapOf("app" to "1.3.0"))
        val instanceBefore = service.getPluginInstanceForSession(pluginId, "app")

        factoryRepository.load(version("1.2.0", minAgent = null, maxAgent = null), forNewAgents)
        service.initializePluginInstancesForSessionsIfNeeded(pluginId, mapOf("app" to "1.3.0"))

        assertEquals("1.2.0", service.boundVersionsFlow.value.versionOf("app", pluginId))
        assertNotSame(instanceBefore, service.getPluginInstanceForSession(pluginId, "app"))
    }

    @Test
    fun `a session whose version is removed moves to another version that fits it`() {
        val unbounded = version("1.2.0", minAgent = null, maxAgent = null)
        factoryRepository.load(unbounded)
        service.initializePluginInstancesForSessionsIfNeeded(pluginId, mapOf("app" to "1.3.0"))

        service.unloadPluginInstancesForJar(unbounded.jarPath)
        factoryRepository.load(forNewAgents)
        service.initializePluginInstancesForSessionsIfNeeded(pluginId, mapOf("app" to "1.3.0"))

        assertEquals("1.3.0", service.boundVersionsFlow.value.versionOf("app", pluginId))
    }

    @Test
    fun `unloading one version's jar leaves the sessions bound to other versions running`() {
        factoryRepository.load(forOldAgents, forNewAgents)
        service.initializePluginInstancesForSessionsIfNeeded(pluginId, mapOf("old-app" to "1.2.0", "new-app" to "1.3.0"))

        service.unloadPluginInstancesForJar(forOldAgents.jarPath)

        assertNull(service.getPluginInstanceForSession(pluginId, "old-app"))
        assertEquals("1.3.0", service.boundVersionsFlow.value.versionOf("new-app", pluginId))
    }

    private fun version(version: String, minAgent: String?, maxAgent: String?) = LoadedHostPlugin(
        manifest = JetWhaleHostPluginManifest(
            pluginId = pluginId,
            pluginName = "Example",
            version = version,
            factoryClass = "com.example.Factory",
            agentVersionRange = JetWhaleHostPluginManifest.AgentVersionRange(min = minAgent, max = maxAgent),
        ),
        factory = object : JetWhaleHostPluginFactory {
            override fun createPlugin(): JetWhaleHostPlugin = object : JetWhaleHostPlugin() {}
        },
        jarPath = "/plugins/example-$version.jar",
    )

    private class FakePluginFactoryRepository : PluginFactoryRepository {
        private val versions = MutableStateFlow<Map<String, List<LoadedHostPlugin>>>(emptyMap())
        override val loadedPluginVersionsFlow: Flow<Map<String, List<LoadedHostPlugin>>> = versions
        override val loadedPluginVersions: Map<String, List<LoadedHostPlugin>> get() = versions.value
        override val loadedPlugins: Map<String, LoadedHostPlugin> get() = versions.value.mapValues { (_, loaded) -> loaded.first() }
        override val loadedPluginsFlow: Flow<Map<String, LoadedHostPlugin>> = MutableStateFlow(emptyMap())
        override val failedJarsFlow: Flow<List<FailedPluginJar>> = MutableStateFlow(emptyList())

        fun load(vararg plugins: LoadedHostPlugin) {
            versions.value = plugins.groupBy { it.manifest.pluginId }
                .mapValues { (_, loaded) -> loaded.sortedWith(compareByDescending(PluginVersionOrder) { it.manifest.version }) }
        }

        override suspend fun loadPlugin(pluginJarPath: String, expectedSha256: String?) = Unit
        override suspend fun unloadPluginJar(pluginJarPath: String) = Unit
        override fun findPluginIdsByJarPath(pluginJarPath: String): List<String> = emptyList()
        override suspend fun reloadPlugin(pluginJarPath: String, expectedSha256: String?): List<String> = emptyList()
        override fun tryRedefinePlugin(pluginJarPath: String): List<String> = emptyList()
    }
}
