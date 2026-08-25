package com.kitakkun.jetwhale.host.data.plugin

import com.kitakkun.jetwhale.host.data.AppDataDirectoryProvider
import com.kitakkun.jetwhale.host.model.AdditionalPluginDirectories
import com.kitakkun.jetwhale.host.sdk.JetWhaleHostPlugin
import com.kitakkun.jetwhale.host.sdk.JetWhaleHostPluginContext
import com.kitakkun.jetwhale.host.sdk.JetWhaleHostPluginFactory
import com.kitakkun.jetwhale.host.sdk.JetWhaleHostPluginScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.io.File
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Referenced by the manifests below; the jars under test carry no classes of their own. */
class ManifestTestPluginFactory : JetWhaleHostPluginFactory {
    override fun createPlugin(context: JetWhaleHostPluginContext): JetWhaleHostPlugin = object : JetWhaleHostPlugin() {}
}

class DefaultPluginFactoryRepositoryManifestTest {
    private val repository = DefaultPluginFactoryRepository(AppDataDirectoryProvider(AdditionalPluginDirectories(emptyList())))

    @Test
    fun `a host-scoped plugin that also requires an agent is rejected`() = runBlocking {
        val jar = jarWithManifest(scope = "host", requiresAgent = true)

        repository.loadPlugin(jar.absolutePath)

        assertTrue(repository.loadedPlugins.isEmpty())
        val failure = repository.failedJarsFlow.first().single()
        assertEquals(jar.absolutePath, failure.jarPath)
        assertTrue("requiresAgent" in failure.reason, "Expected the reason to name the offending field, but was: ${failure.reason}")
    }

    @Test
    fun `a host-scoped plugin without an agent loads`() = runBlocking {
        val jar = jarWithManifest(scope = "host", requiresAgent = false)

        repository.loadPlugin(jar.absolutePath)

        val loaded = repository.loadedPlugins.getValue(PLUGIN_ID)
        assertEquals(JetWhaleHostPluginScope.HOST, loaded.manifest.scope)
        assertTrue(repository.failedJarsFlow.first().isEmpty())
    }

    @Test
    fun `a plugin that declares no scope is session-scoped`() = runBlocking {
        val jar = jarWithManifest(scope = null, requiresAgent = true)

        repository.loadPlugin(jar.absolutePath)

        assertEquals(JetWhaleHostPluginScope.SESSION, repository.loadedPlugins.getValue(PLUGIN_ID).manifest.scope)
    }

    private fun jarWithManifest(scope: String?, requiresAgent: Boolean): File {
        val manifest = buildString {
            append("""{"plugins":[{"pluginId":"$PLUGIN_ID","pluginName":"Manifest Test",""")
            append(""""version":"1.0.0","factoryClass":"${ManifestTestPluginFactory::class.java.name}",""")
            append(""""requiresAgent":$requiresAgent""")
            if (scope != null) append(""","scope":"$scope"""")
            append("}]}")
        }
        val jar = File.createTempFile("jetwhale-manifest-test-", ".jar").apply { deleteOnExit() }
        JarOutputStream(jar.outputStream()).use { out ->
            out.putNextEntry(JarEntry("META-INF/jetwhale/plugin-manifest.json"))
            out.write(manifest.toByteArray())
            out.closeEntry()
        }
        return jar
    }

    private companion object {
        const val PLUGIN_ID = "com.example.manifesttest"
    }
}
