package com.kitakkun.jetwhale.host.data.plugin

import com.kitakkun.jetwhale.host.data.AppDataDirectoryProvider
import com.kitakkun.jetwhale.host.model.DebuggerSettingsRepository
import com.kitakkun.jetwhale.host.model.FailedPluginJar
import com.kitakkun.jetwhale.host.model.LoadedHostPlugin
import com.kitakkun.jetwhale.host.model.PluginFactoryRepository
import com.kitakkun.jetwhale.host.model.PluginTrustRepository
import com.kitakkun.jetwhale.host.model.TrustedPluginEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import java.io.File
import java.security.MessageDigest
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
    private lateinit var settingsRepository: FakeDebuggerSettingsRepository
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
        settingsRepository = FakeDebuggerSettingsRepository(signingEnabled = false)
        service = DefaultPluginTrustService(AppDataDirectoryProvider(), trustRepository, factoryRepository, settingsRepository)
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

    @Test
    fun `setSigningEnabled true persists the flag and re-signs the registry`() = runBlocking {
        service.setSigningEnabled(true)

        assertEquals(true, settingsRepository.readSignPluginTrustRegistry())
        assertEquals(1, trustRepository.resignCount)
        assertEquals(true, trustRepository.lastSigningEnabled)
    }

    @Test
    fun `setSigningEnabled false persists the flag without re-signing`() = runBlocking {
        service.setSigningEnabled(false)

        assertEquals(false, settingsRepository.readSignPluginTrustRegistry())
        assertEquals(0, trustRepository.resignCount)
    }

    @Test
    fun `enabling signing re-signs a previously unsigned registry so it still loads`() = runBlocking {
        // End-to-end migration over a real disk-backed registry: approve while signing is off, then
        // turn signing on and confirm a fresh signing-on startup still trusts and loads the jar.
        val jar = File(pluginsDir, "plugin.jar").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val settings = FakeDebuggerSettingsRepository(signingEnabled = false)

        val diskRepository = DefaultPluginTrustRepository(AppDataDirectoryProvider(), DeterministicTrustRegistrySigner())
        val diskFactory = FakePluginFactoryRepository()
        val diskService = DefaultPluginTrustService(AppDataDirectoryProvider(), diskRepository, diskFactory, settings)

        // Approve with signing off: the registry is written unsigned.
        diskService.trustAndLoad(jar.absolutePath)
        // Turn signing on: the setting flips and the existing registry is re-signed.
        diskService.setSigningEnabled(true)

        // Fresh start with signing on and a pre-existing key. Without the re-sign the unsigned
        // registry would verify INVALID and drop every plugin; re-signing lets it verify and load.
        val reloadedRepository = DefaultPluginTrustRepository(AppDataDirectoryProvider(), DeterministicTrustRegistrySigner(keyPreexisted = true))
        val reloadedFactory = FakePluginFactoryRepository()
        val reloadedService = DefaultPluginTrustService(AppDataDirectoryProvider(), reloadedRepository, reloadedFactory, settings)
        reloadedService.loadTrustedPlugins()

        assertEquals(listOf(jar.absolutePath), reloadedFactory.loadedJarPaths)
    }

    private class FakePluginTrustRepository : PluginTrustRepository {
        val entries = mutableMapOf<String, TrustedPluginEntry>()

        /** Last `signingEnabled` value passed to a persisting call, or null if none was persisted. */
        var lastSigningEnabled: Boolean? = null
        var resignCount = 0

        override val trustedEntriesFlow: Flow<Map<String, TrustedPluginEntry>> = MutableStateFlow(emptyMap())

        override suspend fun trustedEntry(jarPath: String): TrustedPluginEntry? = entries[jarPath]

        // The in-memory map is already the source of truth, so there is nothing to read from disk.
        override suspend fun load(signingEnabled: Boolean) = Unit

        override suspend fun trust(jarPath: String, sha256: String, signingEnabled: Boolean) {
            entries[jarPath] = TrustedPluginEntry(jarPath, sha256, 0L)
            lastSigningEnabled = signingEnabled
        }

        override suspend fun revoke(jarPath: String, signingEnabled: Boolean) {
            entries.remove(jarPath)
            lastSigningEnabled = signingEnabled
        }

        override suspend fun resign(signingEnabled: Boolean) {
            resignCount++
            lastSigningEnabled = signingEnabled
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

    /**
     * Deterministic stand-in for the keyring-backed signer: the "signature" is a digest of the
     * payload, so a signature only verifies against the exact payload it was produced for.
     */
    private class DeterministicTrustRegistrySigner(
        private val keyPreexisted: Boolean = true,
    ) : TrustRegistrySigner {
        override fun sign(payload: String): String? = digest(payload)

        override fun verify(payload: String, signature: String?): TrustRegistrySigner.Verification = when {
            signature == null -> if (keyPreexisted) TrustRegistrySigner.Verification.INVALID else TrustRegistrySigner.Verification.VALID
            signature == digest(payload) -> TrustRegistrySigner.Verification.VALID
            else -> TrustRegistrySigner.Verification.INVALID
        }

        private fun digest(payload: String): String = "signed:" + MessageDigest.getInstance("SHA-256").digest(payload.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    /**
     * Settings stand-in backing the opt-in flag with a [MutableStateFlow] so the flow and the
     * suspend read stay consistent: [updateSignPluginTrustRegistry] flows through the same state.
     */
    private class FakeDebuggerSettingsRepository(signingEnabled: Boolean) : DebuggerSettingsRepository {
        private val signPluginTrustRegistry = MutableStateFlow(signingEnabled)

        override val signPluginTrustRegistryFlow: StateFlow<Boolean> = signPluginTrustRegistry
        override val adbAutoPortMappingEnabledFlow: StateFlow<Boolean> = MutableStateFlow(false)
        override val checkForUpdatesOnStartupFlow: StateFlow<Boolean> = MutableStateFlow(true)
        override val persistDataFlow: StateFlow<Boolean> = MutableStateFlow(false)
        override val serverPortFlow: StateFlow<Int> = MutableStateFlow(0)
        override val mcpServerPortFlow: StateFlow<Int> = MutableStateFlow(0)
        override val wssPortFlow: StateFlow<Int> = MutableStateFlow(0)
        override val wssEnabledFlow: StateFlow<Boolean> = MutableStateFlow(true)

        override suspend fun readSignPluginTrustRegistry(): Boolean = signPluginTrustRegistry.value
        override suspend fun updateSignPluginTrustRegistry(enabled: Boolean) {
            signPluginTrustRegistry.value = enabled
        }

        override suspend fun readServerPort(): Int = 0
        override suspend fun readMcpServerPort(): Int = 0
        override suspend fun readWssPort(): Int = 0
        override suspend fun updatePersistData(enabled: Boolean) = Unit
        override suspend fun updateAdbAutoPortMappingEnabled(enabled: Boolean) = Unit
        override suspend fun readCheckForUpdatesOnStartup(): Boolean = true
        override suspend fun updateCheckForUpdatesOnStartup(enabled: Boolean) = Unit
        override suspend fun updateServerPort(port: Int) = Unit
        override suspend fun updateMcpServerPort(port: Int) = Unit
        override suspend fun updateWssPort(port: Int) = Unit
        override suspend fun updateWssEnabled(enabled: Boolean) = Unit
    }
}
