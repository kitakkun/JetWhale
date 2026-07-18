package com.kitakkun.jetwhale.host.data.plugin

import com.kitakkun.jetwhale.host.data.AppDataDirectoryProvider
import com.kitakkun.jetwhale.host.model.FailedPluginJar
import com.kitakkun.jetwhale.host.model.LoadedHostPlugin
import com.kitakkun.jetwhale.host.model.PluginFactoryRepository
import com.kitakkun.jetwhale.host.model.PluginTrustRepository
import com.kitakkun.jetwhale.host.model.TrustedPluginEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DefaultPluginTrustServiceTest {
    private var originalUserHome: String? = null
    private lateinit var tempHome: File
    private lateinit var pluginsDir: File

    private lateinit var trustRepository: FakePluginTrustRepository
    private lateinit var factoryRepository: FakePluginFactoryRepository
    private lateinit var service: DefaultPluginTrustService

    @BeforeTest
    fun setUp() {
        originalUserHome = System.getProperty("user.home")
        tempHome = File.createTempFile("jetwhale-home-", "").apply {
            delete()
            mkdirs()
        }
        System.setProperty("user.home", tempHome.absolutePath)
        pluginsDir = File(tempHome, ".jetwhale/plugins").apply { mkdirs() }

        trustRepository = FakePluginTrustRepository()
        factoryRepository = FakePluginFactoryRepository()
        service = DefaultPluginTrustService(AppDataDirectoryProvider(), trustRepository, factoryRepository)
    }

    @AfterTest
    fun cleanup() {
        originalUserHome?.let { System.setProperty("user.home", it) }
        tempHome.deleteRecursively()
    }

    @Test
    fun `trustAndLoad accepts a jar inside the plugins directory`() = runBlocking {
        val jar = File(pluginsDir, "plugin.jar").apply { writeBytes(byteArrayOf(1, 2, 3)) }

        service.trustAndLoad(jar.absolutePath)

        assertEquals(listOf(jar.absolutePath), factoryRepository.loadedJarPaths)
        assertEquals(setOf(jar.absolutePath), trustRepository.entries.keys)
    }

    @Test
    fun `trustAndLoad rejects a jar outside the plugins directory`() = runBlocking {
        val outsideJar = File(tempHome, "evil.jar").apply { writeBytes(byteArrayOf(1)) }

        assertFailsWith<IllegalArgumentException> { service.trustAndLoad(outsideJar.absolutePath) }
        assertEquals(emptyList(), factoryRepository.loadedJarPaths)
        assertEquals(emptySet(), trustRepository.entries.keys)
    }

    @Test
    fun `trustAndLoad rejects a path escaping the plugins directory via dot-dot`() = runBlocking {
        val outsideJar = File(tempHome, "evil.jar").apply { writeBytes(byteArrayOf(1)) }
        val sneakyPath = "${pluginsDir.absolutePath}/../../${outsideJar.name}"

        assertFailsWith<IllegalArgumentException> { service.trustAndLoad(sneakyPath) }
        assertEquals(emptyList(), factoryRepository.loadedJarPaths)
    }

    @Test
    fun `trustAndLoad rejects a non-jar file`() = runBlocking {
        val notAJar = File(pluginsDir, "plugin.zip").apply { writeBytes(byteArrayOf(1)) }

        assertFailsWith<IllegalArgumentException> { service.trustAndLoad(notAJar.absolutePath) }
        assertEquals(emptyList(), factoryRepository.loadedJarPaths)
    }

    @Test
    fun `loadTrustedPlugins treats an unhashable jar as untrusted instead of aborting`() = runBlocking {
        val readable = File(pluginsDir, "good.jar").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        service.trustAndLoad(readable.absolutePath)
        factoryRepository.loadedJarPaths.clear()

        // A directory named *.jar is enumerated as a plugin jar but cannot be opened as a stream,
        // so hashing it fails — the failure must skip this entry, not abort the whole load.
        val unhashable = File(pluginsDir, "broken.jar").apply { mkdirs() }
        trustRepository.entries[unhashable.absolutePath] = TrustedPluginEntry(unhashable.absolutePath, "irrelevant", 0L)

        service.loadTrustedPlugins()

        assertEquals(listOf(readable.absolutePath), factoryRepository.loadedJarPaths)
    }

    private class FakePluginTrustRepository : PluginTrustRepository {
        val entries = mutableMapOf<String, TrustedPluginEntry>()
        override val trustedEntriesFlow: Flow<Map<String, TrustedPluginEntry>> = MutableStateFlow(emptyMap())

        override suspend fun trustedEntry(jarPath: String): TrustedPluginEntry? = entries[jarPath]

        override suspend fun trust(jarPath: String, sha256: String) {
            entries[jarPath] = TrustedPluginEntry(jarPath, sha256, 0L)
        }

        override suspend fun revoke(jarPath: String) {
            entries.remove(jarPath)
        }
    }

    private class FakePluginFactoryRepository : PluginFactoryRepository {
        val loadedJarPaths = mutableListOf<String>()
        override val loadedPluginsFlow: Flow<Map<String, LoadedHostPlugin>> = MutableStateFlow(emptyMap())
        override val loadedPlugins: Map<String, LoadedHostPlugin> = emptyMap()
        override val failedJarsFlow: Flow<List<FailedPluginJar>> = MutableStateFlow(emptyList())

        override suspend fun loadPlugin(pluginJarPath: String) {
            loadedJarPaths.add(pluginJarPath)
        }

        override suspend fun unloadPlugin(pluginId: String) = Unit

        override fun findPluginIdsByJarPath(pluginJarPath: String): List<String> = emptyList()

        override suspend fun reloadPlugin(pluginJarPath: String): List<String> = emptyList()

        override fun tryRedefinePlugin(pluginJarPath: String): List<String> = emptyList()
    }
}
