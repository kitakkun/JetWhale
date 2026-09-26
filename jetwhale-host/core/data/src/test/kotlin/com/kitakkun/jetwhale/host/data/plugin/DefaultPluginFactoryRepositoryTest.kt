package com.kitakkun.jetwhale.host.data.plugin

import com.kitakkun.jetwhale.host.data.AppDataDirectoryProvider
import com.kitakkun.jetwhale.host.model.AdditionalPluginDirectories
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.io.File
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DefaultPluginFactoryRepositoryTest {
    private var originalUserHome: String? = null
    private lateinit var tempHome: File
    private lateinit var pluginsDir: File
    private lateinit var repository: DefaultPluginFactoryRepository

    @BeforeTest
    fun setUp() {
        originalUserHome = System.getProperty("user.home")
        tempHome = File.createTempFile("jetwhale-home-", "").apply {
            delete()
            mkdirs()
        }
        System.setProperty("user.home", tempHome.absolutePath)
        pluginsDir = File(tempHome, ".jetwhale/plugins").apply { mkdirs() }
        repository = DefaultPluginFactoryRepository(AppDataDirectoryProvider(AdditionalPluginDirectories(emptyList())))
    }

    @AfterTest
    fun cleanup() {
        originalUserHome?.let { System.setProperty("user.home", it) }
        tempHome.deleteRecursively()
    }

    @Test
    fun `a jar whose bytes no longer have the approved hash is not loaded`() = runBlocking {
        val jar = File(pluginsDir, "network.jar").apply { writeBytes(byteArrayOf(1, 2, 3)) }

        repository.loadPlugin(jar.absolutePath, expectedSha256 = "0".repeat(64))

        val failure = repository.failedJarsFlow.first().single()
        assertEquals(jar.absolutePath, failure.jarPath)
        assertTrue(APPROVAL_MISMATCH in failure.reason)
        assertEquals(emptyMap(), repository.loadedPlugins)
    }

    @Test
    fun `a jar that still has the approved hash goes on to load`() = runBlocking {
        val jar = File(pluginsDir, "network.jar").apply { writeBytes(byteArrayOf(1, 2, 3)) }

        repository.loadPlugin(jar.absolutePath, expectedSha256 = jar.sha256Hex())

        // The bytes are no plugin, so the load fails later, for that reason rather than the hash.
        assertFalse(repository.failedJarsFlow.first().single().reason.contains(APPROVAL_MISMATCH))
    }

    @Test
    fun `unloading a jar that failed to load drops its failure`() = runBlocking {
        val jar = File(pluginsDir, "network.jar").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        repository.loadPlugin(jar.absolutePath, expectedSha256 = null)

        repository.unloadPluginJar(jar.absolutePath)

        assertEquals(emptyList(), repository.failedJarsFlow.first())
    }

    @Test
    fun `a manifest too large to be real fails the load instead of being read whole`() = runBlocking {
        val jar = File(pluginsDir, "huge.jar")
        JarOutputStream(jar.outputStream()).use { archive ->
            archive.putNextEntry(JarEntry(PLUGIN_MANIFEST_PATH))
            archive.write(ByteArray(MAX_PLUGIN_MANIFEST_BYTES + 1) { ' '.code.toByte() })
            archive.closeEntry()
        }

        repository.loadPlugin(jar.absolutePath, expectedSha256 = null)

        assertTrue(repository.failedJarsFlow.first().single().reason.contains("larger than"))
    }

    private companion object {
        const val APPROVAL_MISMATCH = "changed after it was approved"
    }
}
