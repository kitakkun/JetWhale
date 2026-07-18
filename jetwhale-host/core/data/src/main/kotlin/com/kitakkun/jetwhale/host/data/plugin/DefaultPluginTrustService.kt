package com.kitakkun.jetwhale.host.data.plugin

import com.kitakkun.jetwhale.host.data.AppDataDirectoryProvider
import com.kitakkun.jetwhale.host.model.PluginFactoryRepository
import com.kitakkun.jetwhale.host.model.PluginTrustRepository
import com.kitakkun.jetwhale.host.model.PluginTrustService
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
@Inject
class DefaultPluginTrustService(
    private val appDataDirectoryProvider: AppDataDirectoryProvider,
    private val pluginTrustRepository: PluginTrustRepository,
    private val pluginFactoryRepository: PluginFactoryRepository,
) : PluginTrustService {
    override val untrustedJarPathsFlow: Flow<List<String>>
        field = MutableStateFlow(emptyList())

    override suspend fun loadTrustedPlugins() {
        val untrusted = mutableListOf<String>()
        for (jarPath in appDataDirectoryProvider.getAllPluginJarFilePaths()) {
            if (isTrusted(jarPath)) {
                pluginFactoryRepository.loadPlugin(jarPath)
            } else {
                println("Skipping untrusted plugin jar (not approved or content changed): $jarPath")
                untrusted += jarPath
            }
        }
        untrustedJarPathsFlow.value = untrusted
    }

    override suspend fun trustAndLoad(jarPath: String) {
        require(appDataDirectoryProvider.isManagedPluginJarPath(jarPath)) {
            "Refusing to trust a jar outside the managed plugins directory: $jarPath"
        }
        pluginTrustRepository.trust(jarPath, computeSha256(jarPath))
        untrustedJarPathsFlow.update { it - jarPath }
        pluginFactoryRepository.loadPlugin(jarPath)
    }

    override suspend fun revokeTrust(jarPath: String) {
        pluginTrustRepository.revoke(jarPath)
        // Unload everything this jar provided so revoking trust takes effect immediately, without a
        // restart. The jar file itself stays in the directory, so it becomes untrusted-but-present.
        pluginFactoryRepository.findPluginIdsByJarPath(jarPath).forEach { pluginFactoryRepository.unloadPlugin(it) }
        if (File(jarPath).exists()) {
            untrustedJarPathsFlow.update { if (jarPath in it) it else it + jarPath }
        }
    }

    /** True only if [jarPath] has a trusted entry whose pinned hash matches the jar's current bytes. */
    private suspend fun isTrusted(jarPath: String): Boolean {
        val entry = pluginTrustRepository.trustedEntry(jarPath) ?: return false
        val currentSha256 = try {
            computeSha256(jarPath)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // A jar we cannot read is a jar we cannot verify: fail safe as untrusted instead of
            // letting an IO error abort loading of every other plugin.
            println("Failed to hash plugin jar, treating as untrusted: $jarPath (${e.message})")
            return false
        }
        return entry.sha256 == currentSha256
    }

    private suspend fun computeSha256(jarPath: String): String = withContext(Dispatchers.IO) {
        val digest = MessageDigest.getInstance("SHA-256")
        File(jarPath).inputStream().use { stream ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = stream.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        digest.digest().joinToString("") { "%02x".format(it) }
    }
}
