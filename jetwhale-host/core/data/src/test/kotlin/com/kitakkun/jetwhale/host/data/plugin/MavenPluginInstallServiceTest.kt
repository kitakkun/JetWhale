package com.kitakkun.jetwhale.host.data.plugin

import com.kitakkun.jetwhale.host.data.AppDataDirectoryProvider
import com.kitakkun.jetwhale.host.model.AdditionalPluginDirectories
import com.kitakkun.jetwhale.host.model.ArrivedPluginJar
import com.kitakkun.jetwhale.host.model.MavenCoordinates
import com.kitakkun.jetwhale.host.model.PluginInstallProgress
import com.kitakkun.jetwhale.host.model.PluginInstallProgressRepository
import com.kitakkun.jetwhale.host.model.PluginTrustService
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

class MavenPluginInstallServiceTest {
    private var originalUserHome: String? = null
    private lateinit var tempHome: File
    private lateinit var provider: AppDataDirectoryProvider

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
        val coordinates = MavenCoordinates(groupId = "com.example", artifactId = "network", version = "1.3.0", repositoryUrl = "https://example.com/releases")
        val installed = File(provider.getPluginDirectory(), coordinates.jarFileName()).apply { writeBytes(jarBytes("installed")) }
        val previousBytes = installed.readBytes()
        val trustService = FailingTrustService()
        val service = MavenPluginInstallService(
            appDataDirectoryProvider = provider,
            mavenArtifactResolver = MavenArtifactResolver(MockEngine { respond(jarBytes("downloaded")) }),
            pluginTrustService = trustService,
            pluginInstallProgressRepository = NoProgress,
        )

        assertFailsWith<PluginInstallationException> { service.installFirstAvailable(listOf(coordinates)) }

        assertEquals(listOf(installed.absolutePath), trustService.approvedJarPaths)
        assertContentEquals(previousBytes, installed.readBytes())
        assertEquals(emptyList(), provider.getPluginStagingDirectory().listFiles().orEmpty().toList())
    }

    private fun jarBytes(entryName: String): ByteArray = ByteArrayOutputStream().also { bytes ->
        JarOutputStream(bytes).use { jar ->
            jar.putNextEntry(JarEntry(entryName))
            jar.closeEntry()
        }
    }.toByteArray()

    private class FailingTrustService : PluginTrustService {
        val approvedJarPaths = mutableListOf<String>()
        override val untrustedJarPathsFlow: Flow<List<String>> = MutableStateFlow(emptyList())
        override val arrivedJarsFlow: StateFlow<List<ArrivedPluginJar>> = MutableStateFlow(emptyList())
        override val verifyingTrustRegistryFlow: StateFlow<Boolean> = MutableStateFlow(false)
        override val signingEnabledFlow: StateFlow<Boolean> = MutableStateFlow(false)
        override suspend fun loadTrustedPlugins() = Unit
        override suspend fun trustAndLoad(jarPath: String, approvedSha256: String?) {
            approvedJarPaths += jarPath
            throw IOException("the trust registry could not be written")
        }
        override suspend fun onPluginJarsChanged(jarPaths: Set<String>) = Unit
        override fun postponeArrivedJar(jarPath: String) = Unit
        override suspend fun revokeTrust(jarPath: String) = Unit
        override suspend fun setSigningEnabled(enabled: Boolean) = Unit
    }

    private object NoProgress : PluginInstallProgressRepository {
        override val progressFlow: Flow<PluginInstallProgress?> = MutableStateFlow(null)
        override fun update(progress: PluginInstallProgress?) = Unit
    }
}
