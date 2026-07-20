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
import java.security.MessageDigest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DefaultPluginTrustServiceTest {
    private var originalUserHome: String? = null
    private lateinit var tempHome: File
    private lateinit var pluginsDir: File

    private lateinit var trustRepository: FakePluginTrustRepository
    private lateinit var factoryRepository: FakePluginFactoryRepository
    private lateinit var signer: FakeTrustRegistrySigner
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
        signer = FakeTrustRegistrySigner(keyPresent = false)
        service = DefaultPluginTrustService(AppDataDirectoryProvider(), trustRepository, factoryRepository, signer)
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
    fun `loadTrustedPlugins reflects signing key presence in signingEnabledFlow`() = runBlocking {
        signer.provisionKey()

        service.loadTrustedPlugins()

        assertTrue(service.signingEnabledFlow.value)
    }

    @Test
    fun `loadTrustedPlugins reports signing off when no key exists`() = runBlocking {
        service.loadTrustedPlugins()

        assertFalse(service.signingEnabledFlow.value)
    }

    @Test
    fun `setSigningEnabled true provisions a key, re-signs, and reports enabled`() = runBlocking {
        service.setSigningEnabled(true)

        assertTrue(signer.hasKey())
        assertEquals(1, trustRepository.resignCount)
        assertTrue(service.signingEnabledFlow.value)
    }

    @Test
    fun `setSigningEnabled false deletes the key, re-signs, and reports disabled`() = runBlocking {
        service.setSigningEnabled(true)

        service.setSigningEnabled(false)

        assertFalse(signer.hasKey())
        assertEquals(2, trustRepository.resignCount)
        assertFalse(service.signingEnabledFlow.value)
    }

    @Test
    fun `enabling signing re-signs a previously unsigned registry so it still loads`() = runBlocking {
        // End-to-end migration over a real disk-backed registry: approve while signing is off (no
        // key), turn signing on, then confirm a fresh signing-on startup still trusts and loads it.
        val jar = File(pluginsDir, "plugin.jar").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val diskSigner = FakeTrustRegistrySigner(keyPresent = false)

        val diskRepository = DefaultPluginTrustRepository(AppDataDirectoryProvider(), diskSigner)
        val diskFactory = FakePluginFactoryRepository()
        val diskService = DefaultPluginTrustService(AppDataDirectoryProvider(), diskRepository, diskFactory, diskSigner)

        // Approve with signing off: the registry is written unsigned.
        diskService.trustAndLoad(jar.absolutePath)
        // Turn signing on: a key is provisioned and the existing registry is re-signed.
        diskService.setSigningEnabled(true)

        // Fresh start with the same (now-present) key. Without the re-sign the unsigned registry would
        // verify INVALID and drop every plugin; re-signing lets it verify and load.
        val reloadedRepository = DefaultPluginTrustRepository(AppDataDirectoryProvider(), diskSigner)
        val reloadedFactory = FakePluginFactoryRepository()
        val reloadedService = DefaultPluginTrustService(AppDataDirectoryProvider(), reloadedRepository, reloadedFactory, diskSigner)
        reloadedService.loadTrustedPlugins()

        assertEquals(listOf(jar.absolutePath), reloadedFactory.loadedJarPaths)
    }

    private class FakePluginTrustRepository : PluginTrustRepository {
        val entries = mutableMapOf<String, TrustedPluginEntry>()
        var resignCount = 0

        override val trustedEntriesFlow: Flow<Map<String, TrustedPluginEntry>> = MutableStateFlow(emptyMap())

        override suspend fun trustedEntry(jarPath: String): TrustedPluginEntry? = entries[jarPath]

        // The in-memory map is already the source of truth, so there is nothing to read from disk.
        override suspend fun load() = Unit

        override suspend fun trust(jarPath: String, sha256: String) {
            entries[jarPath] = TrustedPluginEntry(jarPath, sha256, 0L)
        }

        override suspend fun revoke(jarPath: String) {
            entries.remove(jarPath)
        }

        override suspend fun resign() {
            resignCount++
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
     * Deterministic stand-in for the keyring-backed signer. "Signing enabled" is modeled by whether a
     * key is present; [provisionKey]/[deleteKey] flip it. The "signature" is a digest of the payload,
     * so it only verifies against the exact payload it was produced for.
     */
    private class FakeTrustRegistrySigner(keyPresent: Boolean) : TrustRegistrySigner {
        private var keyPresent = keyPresent

        override fun hasKey(): Boolean = keyPresent

        override fun sign(payload: String): String? = if (keyPresent) digest(payload) else null

        override fun verify(payload: String, signature: String?): TrustRegistrySigner.Verification = when {
            !keyPresent -> TrustRegistrySigner.Verification.DISABLED
            signature == null -> TrustRegistrySigner.Verification.INVALID
            signature == digest(payload) -> TrustRegistrySigner.Verification.VALID
            else -> TrustRegistrySigner.Verification.INVALID
        }

        override fun provisionKey() {
            keyPresent = true
        }

        override fun deleteKey() {
            keyPresent = false
        }

        private fun digest(payload: String): String = "signed:" + MessageDigest.getInstance("SHA-256").digest(payload.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
