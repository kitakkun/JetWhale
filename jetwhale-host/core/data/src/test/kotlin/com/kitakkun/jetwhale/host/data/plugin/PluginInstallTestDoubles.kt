package com.kitakkun.jetwhale.host.data.plugin

import com.kitakkun.jetwhale.host.model.ArrivedPluginJar
import com.kitakkun.jetwhale.host.model.FailedPluginJar
import com.kitakkun.jetwhale.host.model.LoadedHostPlugin
import com.kitakkun.jetwhale.host.model.PluginFactoryRepository
import com.kitakkun.jetwhale.host.model.PluginInstallProgress
import com.kitakkun.jetwhale.host.model.PluginInstallProgressRepository
import com.kitakkun.jetwhale.host.model.PluginTrustRepository
import com.kitakkun.jetwhale.host.model.PluginTrustService
import com.kitakkun.jetwhale.host.model.TrustedPluginEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.ByteArrayOutputStream
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream

internal fun jarBytes(entryName: String, entryContent: String): ByteArray = ByteArrayOutputStream().also { bytes ->
    JarOutputStream(bytes).use { jar ->
        jar.putNextEntry(JarEntry(entryName))
        jar.write(entryContent.toByteArray())
        jar.closeEntry()
    }
}.toByteArray()

internal class FakeTrustService(private val onApprove: suspend (jarPath: String, approvedSha256: String?) -> Unit) : PluginTrustService {
    val approvals = mutableListOf<Pair<String, String?>>()
    val revoked = mutableListOf<String>()
    override val untrustedJarPathsFlow: Flow<List<String>> = MutableStateFlow(emptyList())
    override val arrivedJarsFlow: StateFlow<List<ArrivedPluginJar>> = MutableStateFlow(emptyList())
    override val verifyingTrustRegistryFlow: StateFlow<Boolean> = MutableStateFlow(false)
    override val signingEnabledFlow: StateFlow<Boolean> = MutableStateFlow(false)
    override suspend fun loadTrustedPlugins() = Unit

    override suspend fun trustAndLoad(jarPath: String, approvedSha256: String?) {
        approvals += jarPath to approvedSha256
        onApprove(jarPath, approvedSha256)
    }

    override suspend fun onPluginJarsChanged(jarPaths: Set<String>) = Unit
    override fun postponeArrivedJar(jarPath: String) = Unit

    override suspend fun revokeTrust(jarPath: String) {
        revoked += jarPath
    }

    override suspend fun setSigningEnabled(enabled: Boolean) = Unit
}

internal class FakeTrustRepository : PluginTrustRepository {
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

internal class FakeFactoryRepository : PluginFactoryRepository {
    override val loadedPluginsFlow: Flow<Map<String, LoadedHostPlugin>> = MutableStateFlow(emptyMap())
    override val loadedPlugins: Map<String, LoadedHostPlugin> = emptyMap()
    override val failedJarsFlow = MutableStateFlow(emptyList<FailedPluginJar>())
    override suspend fun loadPlugin(pluginJarPath: String, expectedSha256: String?) = Unit
    override suspend fun unloadPluginJar(pluginJarPath: String) = Unit
    override fun findPluginIdsByJarPath(pluginJarPath: String): List<String> = emptyList()
    override suspend fun reloadPlugin(pluginJarPath: String, expectedSha256: String?): List<String> = emptyList()
    override fun tryRedefinePlugin(pluginJarPath: String): List<String> = emptyList()
}

internal object NoProgress : PluginInstallProgressRepository {
    override val progressFlow: Flow<PluginInstallProgress?> = MutableStateFlow(null)
    override fun update(progress: PluginInstallProgress?) = Unit
}
