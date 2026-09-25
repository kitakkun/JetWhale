package com.kitakkun.jetwhale.host.data.plugin

import com.kitakkun.jetwhale.host.data.AppDataDirectoryProvider
import com.kitakkun.jetwhale.host.model.AdditionalPluginDirectories
import com.kitakkun.jetwhale.host.model.DeclaredPlugin
import com.kitakkun.jetwhale.host.model.FailedPluginJar
import com.kitakkun.jetwhale.host.model.LoadedHostPlugin
import com.kitakkun.jetwhale.host.model.PluginFactoryRepository
import com.kitakkun.jetwhale.host.model.PluginJarSwapService
import com.kitakkun.jetwhale.host.model.PluginTrustRepository
import com.kitakkun.jetwhale.host.model.TrustedPluginEntry
import com.kitakkun.jetwhale.host.sdk.JetWhaleHostPlugin
import com.kitakkun.jetwhale.host.sdk.JetWhaleHostPluginFactory
import com.kitakkun.jetwhale.host.sdk.JetWhaleHostPluginManifest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.io.File
import java.security.MessageDigest
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
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
    private lateinit var swapService: FakePluginJarSwapService
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
        swapService = FakePluginJarSwapService()
        signer = FakeTrustRegistrySigner(keyPresent = false)
        service = DefaultPluginTrustService(AppDataDirectoryProvider(AdditionalPluginDirectories(emptyList())), trustRepository, factoryRepository, swapService, signer)
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

        val diskRepository = DefaultPluginTrustRepository(AppDataDirectoryProvider(AdditionalPluginDirectories(emptyList())), diskSigner)
        val diskFactory = FakePluginFactoryRepository()
        val diskService = DefaultPluginTrustService(AppDataDirectoryProvider(AdditionalPluginDirectories(emptyList())), diskRepository, diskFactory, FakePluginJarSwapService(), diskSigner)

        diskService.trustAndLoad(jar.absolutePath)
        diskService.setSigningEnabled(true)

        // Fresh start with the same (now-present) key. Without the re-sign the unsigned registry would
        // verify INVALID and drop every plugin; re-signing lets it verify and load.
        val reloadedRepository = DefaultPluginTrustRepository(AppDataDirectoryProvider(AdditionalPluginDirectories(emptyList())), diskSigner)
        val reloadedFactory = FakePluginFactoryRepository()
        val reloadedService = DefaultPluginTrustService(AppDataDirectoryProvider(AdditionalPluginDirectories(emptyList())), reloadedRepository, reloadedFactory, FakePluginJarSwapService(), diskSigner)
        reloadedService.loadTrustedPlugins()

        assertEquals(listOf(jar.absolutePath), reloadedFactory.loadedJarPaths)
    }

    @Test
    fun `a jar that appears at runtime is offered and listed as untrusted without being loaded`() = runBlocking {
        val jar = pluginJar("network.jar", networkManifest(version = "1.3.0"))

        service.onPluginJarsChanged(setOf(jar.absolutePath))

        val offered = service.arrivedJarsFlow.value.single()
        assertEquals(listOf(networkPlugin(version = "1.3.0")), offered.declaredPlugins)
        assertEquals(emptyList(), offered.replacedPlugins)
        assertEquals(jar.length(), offered.sizeBytes)
        assertEquals(sha256Of(jar), offered.sha256)
        assertEquals(listOf(jar.absolutePath), service.untrustedJarPathsFlow.first())
        assertEquals(emptyList(), factoryRepository.loadedJarPaths)
    }

    @Test
    fun `a jar without a readable manifest is still offered with the reason`() = runBlocking {
        val jar = File(pluginsDir, "mystery.jar").apply { writeBytes(byteArrayOf(1, 2, 3)) }

        service.onPluginJarsChanged(setOf(jar.absolutePath))

        val offered = service.arrivedJarsFlow.value.single()
        assertEquals(emptyList(), offered.declaredPlugins)
        assertTrue(offered.unreadableReason != null)
    }

    @Test
    fun `a trusted jar put back into the directory is loaded without asking`() = runBlocking {
        val jar = pluginJar("network.jar", networkManifest(version = "1.3.0"))
        trustRepository.entries[jar.absolutePath] = TrustedPluginEntry(jar.absolutePath, sha256Of(jar), 0L)

        service.onPluginJarsChanged(setOf(jar.absolutePath))

        assertEquals(listOf(jar.absolutePath), factoryRepository.loadedJarPaths)
        assertEquals(sha256Of(jar), factoryRepository.expectedSha256ByJar.getValue(jar.absolutePath))
        assertEquals(emptyList(), service.arrivedJarsFlow.value)
    }

    @Test
    fun `approving an offered jar trusts and loads the content that was shown even if the file changed since`() = runBlocking {
        val jar = pluginJar("network.jar", networkManifest(version = "1.3.0"))
        service.onPluginJarsChanged(setOf(jar.absolutePath))
        val shownSha256 = service.arrivedJarsFlow.value.single().sha256

        pluginJar("network.jar", networkManifest(version = "6.6.6"))
        service.trustAndLoad(jar.absolutePath)

        assertEquals(shownSha256, trustRepository.entries.getValue(jar.absolutePath).sha256)
        assertEquals(shownSha256, factoryRepository.expectedSha256ByJar.getValue(jar.absolutePath))
    }

    @Test
    fun `approving an offered update reloads it against the content that was shown`() = runBlocking {
        val jar = pluginJar("network.jar", networkManifest(version = "1.3.0"))
        factoryRepository.runningPluginsByJar[jar.absolutePath] = listOf(runningNetwork(version = "1.2.0"))
        service.onPluginJarsChanged(setOf(jar.absolutePath))
        val shownSha256 = service.arrivedJarsFlow.value.single().sha256

        pluginJar("network.jar", networkManifest(version = "6.6.6"))
        service.trustAndLoad(jar.absolutePath)

        assertEquals(shownSha256, swapService.expectedSha256ByJar.getValue(jar.absolutePath))
    }

    @Test
    fun `a jar written over a running one is offered as an update while the old code keeps running`() = runBlocking {
        val jar = pluginJar("network.jar", networkManifest(version = "1.3.0"))
        trustRepository.entries[jar.absolutePath] = TrustedPluginEntry(jar.absolutePath, "hash of 1.2.0", 0L)
        factoryRepository.runningPluginsByJar[jar.absolutePath] = listOf(runningNetwork(version = "1.2.0"))

        service.onPluginJarsChanged(setOf(jar.absolutePath))

        assertEquals(listOf(networkPlugin(version = "1.2.0")), service.arrivedJarsFlow.value.single().replacedPlugins)
        assertEquals(emptyList(), swapService.reloadedJarPaths)
        assertEquals(emptyList(), swapService.removedJarPaths)
        assertEquals(emptyList(), factoryRepository.loadedJarPaths)
    }

    @Test
    fun `approving an update reloads the running plugins rather than loading the jar again`() = runBlocking {
        val jar = pluginJar("network.jar", networkManifest(version = "1.3.0"))
        factoryRepository.runningPluginsByJar[jar.absolutePath] = listOf(runningNetwork(version = "1.2.0"))
        service.onPluginJarsChanged(setOf(jar.absolutePath))

        service.trustAndLoad(jar.absolutePath)

        assertEquals(listOf(jar.absolutePath), swapService.reloadedJarPaths)
        assertEquals(emptyList(), factoryRepository.loadedJarPaths)
        assertEquals(sha256Of(jar), trustRepository.entries.getValue(jar.absolutePath).sha256)
        assertEquals(emptyList(), service.arrivedJarsFlow.value)
        assertEquals(emptyList(), service.untrustedJarPathsFlow.first())
    }

    @Test
    fun `a removed jar has its plugins removed and leaves both lists`() = runBlocking {
        val jar = pluginJar("network.jar", networkManifest(version = "1.3.0"))
        service.onPluginJarsChanged(setOf(jar.absolutePath))

        jar.delete()
        service.onPluginJarsChanged(setOf(jar.absolutePath))

        assertEquals(listOf(jar.absolutePath), swapService.removedJarPaths)
        assertEquals(emptyList(), service.arrivedJarsFlow.value)
        assertEquals(emptyList(), service.untrustedJarPathsFlow.first())
    }

    @Test
    fun `a postponed jar is no longer offered but stays untrusted`() = runBlocking {
        val jar = pluginJar("network.jar", networkManifest(version = "1.3.0"))
        service.onPluginJarsChanged(setOf(jar.absolutePath))

        service.postponeArrivedJar(jar.absolutePath)

        assertEquals(emptyList(), service.arrivedJarsFlow.value)
        assertEquals(listOf(jar.absolutePath), service.untrustedJarPathsFlow.first())
    }

    @Test
    fun `an approved jar that fails to load stays offered with the reason`() = runBlocking {
        val jar = pluginJar("network.jar", networkManifest(version = "1.3.0"))
        factoryRepository.failingJars[jar.absolutePath] = "a declared dependency is missing"
        service.onPluginJarsChanged(setOf(jar.absolutePath))

        service.trustAndLoad(jar.absolutePath)

        assertEquals("a declared dependency is missing", service.arrivedJarsFlow.value.single().loadFailure)
    }

    @Test
    fun `an offered jar that changes again is offered once with its new content`() = runBlocking {
        val jar = pluginJar("network.jar", networkManifest(version = "1.3.0"))
        service.onPluginJarsChanged(setOf(jar.absolutePath))

        pluginJar("network.jar", networkManifest(version = "1.3.1"))
        service.onPluginJarsChanged(setOf(jar.absolutePath))

        assertEquals("1.3.1", service.arrivedJarsFlow.value.single().declaredPlugins.single().version)
        assertEquals(listOf(jar.absolutePath), service.untrustedJarPathsFlow.first())
    }

    private fun pluginJar(name: String, manifestJson: String): File = File(pluginsDir, name).apply {
        JarOutputStream(outputStream()).use { jar ->
            jar.putNextEntry(JarEntry(PLUGIN_MANIFEST_PATH))
            jar.write(manifestJson.toByteArray())
            jar.closeEntry()
        }
    }

    private fun networkManifest(version: String): String = """{"plugins":[{"pluginId":"com.example.network","pluginName":"Network Inspector","version":"$version","factoryClass":"com.example.Factory"}]}"""

    private fun networkPlugin(version: String) = DeclaredPlugin(pluginId = "com.example.network", pluginName = "Network Inspector", version = version)

    private fun runningNetwork(version: String) = JetWhaleHostPluginManifest(
        pluginId = "com.example.network",
        pluginName = "Network Inspector",
        version = version,
        factoryClass = "com.example.Factory",
    )

    private fun sha256Of(file: File): String = MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it) }

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

        /** Plugins the test treats as already running, by the jar they came from. */
        val runningPluginsByJar = mutableMapOf<String, List<JetWhaleHostPluginManifest>>()
        override val loadedPluginsFlow: Flow<Map<String, LoadedHostPlugin>> = MutableStateFlow(emptyMap())
        override val loadedPlugins: Map<String, LoadedHostPlugin>
            get() = runningPluginsByJar.values.flatten().associate { it.pluginId to LoadedHostPlugin(it, UnusedFactory) }

        /** Jars whose load the test makes fail, with the reason recorded. */
        val failingJars = mutableMapOf<String, String>()
        override val failedJarsFlow = MutableStateFlow(emptyList<FailedPluginJar>())

        /** The hash each load was asked to check the opened jar against, by jar path. */
        val expectedSha256ByJar = mutableMapOf<String, String?>()

        override suspend fun loadPlugin(pluginJarPath: String, expectedSha256: String?) {
            loadedJarPaths.add(pluginJarPath)
            expectedSha256ByJar[pluginJarPath] = expectedSha256
            failingJars[pluginJarPath]?.let { reason -> failedJarsFlow.value += FailedPluginJar(pluginJarPath, reason) }
        }

        override suspend fun unloadPluginJar(pluginJarPath: String) = Unit

        override fun findPluginIdsByJarPath(pluginJarPath: String): List<String> = runningPluginsByJar[pluginJarPath].orEmpty().map(JetWhaleHostPluginManifest::pluginId)

        override suspend fun reloadPlugin(pluginJarPath: String, expectedSha256: String?): List<String> = emptyList()

        override fun tryRedefinePlugin(pluginJarPath: String): List<String> = emptyList()
    }

    private object UnusedFactory : JetWhaleHostPluginFactory {
        override fun createPlugin(): JetWhaleHostPlugin = error("the trust service never creates plugins")
    }

    private class FakePluginJarSwapService : PluginJarSwapService {
        val reloadedJarPaths = mutableListOf<String>()
        val removedJarPaths = mutableListOf<String>()
        override val pluginReloadedFlow: SharedFlow<String> = MutableSharedFlow()

        override suspend fun hotSwap(jarPath: String) = error("the trust service never hot-swaps")

        val expectedSha256ByJar = mutableMapOf<String, String?>()

        override suspend fun reload(jarPath: String, expectedSha256: String?) {
            reloadedJarPaths += jarPath
            expectedSha256ByJar[jarPath] = expectedSha256
        }

        override suspend fun remove(jarPath: String) {
            removedJarPaths += jarPath
        }
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
