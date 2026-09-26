package com.kitakkun.jetwhale.host.data.plugin

import com.kitakkun.jetwhale.host.data.AppDataDirectoryProvider
import com.kitakkun.jetwhale.host.model.AdditionalPluginDirectories
import com.kitakkun.jetwhale.host.model.ArrivedPluginJar
import com.kitakkun.jetwhale.host.model.FailedPluginJar
import com.kitakkun.jetwhale.host.model.LoadedHostPlugin
import com.kitakkun.jetwhale.host.model.MavenCoordinates
import com.kitakkun.jetwhale.host.model.PluginFactoryRepository
import com.kitakkun.jetwhale.host.model.PluginInstallProgress
import com.kitakkun.jetwhale.host.model.PluginInstallProgressRepository
import com.kitakkun.jetwhale.host.model.PluginTrustRepository
import com.kitakkun.jetwhale.host.model.PluginTrustService
import com.kitakkun.jetwhale.host.model.TrustedPluginEntry
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class MavenPluginInstallServiceTest {
    private var originalUserHome: String? = null
    private lateinit var tempHome: File
    private lateinit var provider: AppDataDirectoryProvider
    private val coordinates = MavenCoordinates(groupId = "com.example", artifactId = "network", version = "1.3.0", repositoryUrl = "https://example.com/releases")
    private val trustRepository = FakeTrustRepository()
    private val factoryRepository = FakeFactoryRepository()

    @BeforeTest
    fun setUp() {
        originalUserHome = System.getProperty("user.home")
        tempHome = File.createTempFile("jetwhale-home-", "").apply {
            delete()
            mkdirs()
        }
        System.setProperty("user.home", tempHome.absolutePath)
        provider = AppDataDirectoryProvider(AdditionalPluginDirectories(emptyList()))
        provider.createAppDataDirectoriesIfNeeded()
    }

    @AfterTest
    fun cleanup() {
        originalUserHome?.let { System.setProperty("user.home", it) }
        tempHome.deleteRecursively()
    }

    @Test
    fun `an update whose approval fails puts back the jar it replaced`() = runBlocking {
        val installed = File(provider.getPluginDirectory(), coordinates.jarFileName()).apply { writeBytes(jarBytes("installed")) }
        val previousBytes = installed.readBytes()
        val trustService = FakeTrustService(onApprove = { _, approvedSha256 -> if (approvedSha256 == null) throw IOException("the trust registry could not be written") })

        assertFailsWith<PluginInstallationException> { service(trustService).installFirstAvailable(listOf(coordinates)) }

        assertEquals(installed.absolutePath, trustService.approvals.first().first)
        assertContentEquals(previousBytes, installed.readBytes())
        assertEquals(emptyList(), provider.getPluginStagingDirectory().listFiles().orEmpty().toList())
    }

    @Test
    fun `an update whose new jar fails to load puts back the previous jar and its approval`() = runBlocking {
        val installed = File(provider.getPluginDirectory(), coordinates.jarFileName()).apply { writeBytes(jarBytes("installed")) }
        val previousBytes = installed.readBytes()
        val previousSha256 = installed.sha256Hex()
        trustRepository.entries[installed.absolutePath] = TrustedPluginEntry(installed.absolutePath, previousSha256, 0L)
        val trustService = FakeTrustService(onApprove = { jarPath, approvedSha256 ->
            if (approvedSha256 == null) factoryRepository.failedJarsFlow.value = listOf(FailedPluginJar(jarPath, "a declared dependency is missing"))
        })

        val failure = assertFailsWith<PluginInstallationException> { service(trustService).installFirstAvailable(listOf(coordinates)) }

        assertEquals(true, failure.message?.contains("a declared dependency is missing"))
        assertContentEquals(previousBytes, installed.readBytes())
        assertEquals(installed.absolutePath to previousSha256, trustService.approvals.last())
    }

    @Test
    fun `a new install that fails to load is removed with its approval`() = runBlocking {
        val installed = File(provider.getPluginDirectory(), coordinates.jarFileName())
        val trustService = FakeTrustService(onApprove = { jarPath, _ ->
            factoryRepository.failedJarsFlow.value = listOf(FailedPluginJar(jarPath, "declares no plugins"))
        })

        assertFailsWith<PluginInstallationException> { service(trustService).installFirstAvailable(listOf(coordinates)) }

        assertFalse(installed.exists())
        assertEquals(listOf(installed.absolutePath), trustService.revoked)
    }

    private fun service(trustService: PluginTrustService) = MavenPluginInstallService(
        appDataDirectoryProvider = provider,
        mavenArtifactResolver = MavenArtifactResolver(MockEngine { respond(jarBytes("downloaded")) }),
        pluginTrustService = trustService,
        pluginTrustRepository = trustRepository,
        pluginFactoryRepository = factoryRepository,
        pluginInstallProgressRepository = NoProgress,
    )

    private fun jarBytes(entryName: String): ByteArray = ByteArrayOutputStream().also { bytes ->
        JarOutputStream(bytes).use { jar ->
            jar.putNextEntry(JarEntry(entryName))
            jar.closeEntry()
        }
    }.toByteArray()

    private class FakeTrustService(private val onApprove: (jarPath: String, approvedSha256: String?) -> Unit) : PluginTrustService {
        val approvals = mutableListOf<Pair<String, String?>>()
        val revoked = mutableListOf<String>()
        override val untrustedJarPathsFlow: Flow<List<String>> = MutableStateFlow(emptyList())
        override val arrivedJarsFlow: StateFlow<List<ArrivedPluginJar>> = MutableStateFlow(emptyList())
        override val verifyingTrustRegistryFlow: StateFlow<Boolean> = MutableStateFlow(false)
        override val signingEnabledFlow: StateFlow<Boolean> = MutableStateFlow(false)
        override suspend fun loadTrustedPlugins() = Unit

        override suspend fun trustAndLoad(jarPath: String, approvedSha256: String?, replaceOtherVersions: Boolean) {
            approvals += jarPath to approvedSha256
            onApprove(jarPath, approvedSha256)
        }

        override suspend fun onPluginJarsChanged(jarPaths: Set<String>) = Unit
        override fun postponeArrivedJar(jarPath: String) = Unit

        override suspend fun revokeTrust(jarPath: String) {
            revoked += jarPath
        }

        override suspend fun removePluginJar(jarPath: String) = Unit

        override suspend fun setSigningEnabled(enabled: Boolean) = Unit
    }

    private class FakeTrustRepository : PluginTrustRepository {
        val entries = mutableMapOf<String, TrustedPluginEntry>()
        override val trustedEntriesFlow: Flow<Map<String, TrustedPluginEntry>> = MutableStateFlow(emptyMap())
        override suspend fun trustedEntry(jarPath: String): TrustedPluginEntry? = entries[jarPath]
        override suspend fun load() = Unit

        override suspend fun trust(jarPath: String, sha256: String) {
            entries[jarPath] = TrustedPluginEntry(jarPath, sha256, 0L)
        }

        override suspend fun revoke(jarPath: String) {
            entries.remove(jarPath)
        }

        override suspend fun resign() = Unit
    }

    private class FakeFactoryRepository : PluginFactoryRepository {
        override val loadedPluginsFlow: Flow<Map<String, LoadedHostPlugin>> = MutableStateFlow(emptyMap())
        override val loadedPlugins: Map<String, LoadedHostPlugin> = emptyMap()
        override val loadedPluginVersionsFlow: Flow<Map<String, List<LoadedHostPlugin>>> = MutableStateFlow(emptyMap())
        override val loadedPluginVersions: Map<String, List<LoadedHostPlugin>> = emptyMap()
        override val failedJarsFlow = MutableStateFlow(emptyList<FailedPluginJar>())
        override suspend fun loadPlugin(pluginJarPath: String, expectedSha256: String?) = Unit
        override suspend fun unloadPluginJar(pluginJarPath: String) = Unit
        override fun findPluginIdsByJarPath(pluginJarPath: String): List<String> = emptyList()
        override suspend fun reloadPlugin(pluginJarPath: String, expectedSha256: String?): List<String> = emptyList()
        override fun tryRedefinePlugin(pluginJarPath: String): List<String> = emptyList()
    }

    private object NoProgress : PluginInstallProgressRepository {
        override val progressFlow: Flow<PluginInstallProgress?> = MutableStateFlow(null)
        override fun update(progress: PluginInstallProgress?) = Unit
    }
}
