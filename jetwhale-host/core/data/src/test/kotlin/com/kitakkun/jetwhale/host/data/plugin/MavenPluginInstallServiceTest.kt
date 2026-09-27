package com.kitakkun.jetwhale.host.data.plugin

import com.kitakkun.jetwhale.host.data.AppDataDirectoryProvider
import com.kitakkun.jetwhale.host.model.AdditionalPluginDirectories
import com.kitakkun.jetwhale.host.model.FailedPluginJar
import com.kitakkun.jetwhale.host.model.MavenCoordinates
import com.kitakkun.jetwhale.host.model.PluginTrustService
import com.kitakkun.jetwhale.host.model.TrustedPluginEntry
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.IOException
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

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
        val installed = File(provider.getPluginDirectory(), coordinates.jarFileName()).apply { writeBytes(jarBytes("installed", "")) }
        val previousBytes = installed.readBytes()
        val trustService = FakeTrustService(onApprove = { _, approvedSha256 -> if (approvedSha256 == null) throw IOException("the trust registry could not be written") })

        assertFailsWith<PluginInstallationException> { service(trustService).installFirstAvailable(listOf(coordinates)) }

        assertEquals(installed.absolutePath, trustService.approvals.first().first)
        assertContentEquals(previousBytes, installed.readBytes())
        assertEquals(emptyList(), provider.getPluginStagingDirectory().listFiles().orEmpty().toList())
    }

    @Test
    fun `an update whose new jar fails to load puts back the previous jar and its approval`() = runBlocking {
        val installed = File(provider.getPluginDirectory(), coordinates.jarFileName()).apply { writeBytes(jarBytes("installed", "")) }
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

    @Test
    fun `a cancel that arrives while the plugin is being put in place lets the install finish`() = runBlocking {
        val loading = CompletableDeferred<Unit>()
        val loadGate = CompletableDeferred<Unit>()
        val trustService = FakeTrustService(onApprove = { _, _ ->
            loading.complete(Unit)
            loadGate.await()
        })
        val install = launch(Dispatchers.IO) { service(trustService).installFirstAvailable(listOf(coordinates)) }
        loading.await()

        install.cancel()
        loadGate.complete(Unit)
        install.join()

        assertTrue(File(provider.getPluginDirectory(), coordinates.jarFileName()).isFile)
        assertEquals(emptyList(), trustService.revoked)
        assertEquals(emptyList(), provider.getPluginStagingDirectory().listFiles().orEmpty().toList())
    }

    private fun service(trustService: PluginTrustService) = MavenPluginInstallService(
        appDataDirectoryProvider = provider,
        mavenArtifactResolver = MavenArtifactResolver(MockEngine { respond(jarBytes("downloaded", "")) }),
        pluginTrustService = trustService,
        pluginTrustRepository = trustRepository,
        pluginFactoryRepository = factoryRepository,
        pluginInstallProgressRepository = NoProgress,
    )
}
