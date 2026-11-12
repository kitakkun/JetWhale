package com.kitakkun.jetwhale.host.data.plugin

import com.kitakkun.jetwhale.host.data.AppDataDirectoryProvider
import com.kitakkun.jetwhale.host.model.ArrivedPluginJar
import com.kitakkun.jetwhale.host.model.DeclaredPlugin
import com.kitakkun.jetwhale.host.model.LoadedHostPlugin
import com.kitakkun.jetwhale.host.model.PluginFactoryRepository
import com.kitakkun.jetwhale.host.model.PluginJarSwapService
import com.kitakkun.jetwhale.host.model.PluginTrustRepository
import com.kitakkun.jetwhale.host.model.PluginTrustService
import com.kitakkun.jetwhale.host.sdk.JetWhaleHostPluginManifest
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.logging.Logger

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
@Inject
class DefaultPluginTrustService(
    private val appDataDirectoryProvider: AppDataDirectoryProvider,
    private val pluginTrustRepository: PluginTrustRepository,
    private val pluginFactoryRepository: PluginFactoryRepository,
    private val pluginJarSwapService: PluginJarSwapService,
    private val trustRegistrySigner: TrustRegistrySigner,
) : PluginTrustService {
    private val logger = Logger.getLogger(DefaultPluginTrustService::class.java.name)

    override val untrustedJarPathsFlow: Flow<List<String>>
        field = MutableStateFlow(emptyList())

    override val arrivedJarsFlow: StateFlow<List<ArrivedPluginJar>>
        field = MutableStateFlow(emptyList())

    /**
     * Serializes approval with the directory watcher's reconciliation, so that a jar being approved
     * is not recorded as untrusted by a reconciliation that read it just before.
     */
    private val jarStateMutex = Mutex()

    override val verifyingTrustRegistryFlow: StateFlow<Boolean>
        field = MutableStateFlow(false)

    override val signingEnabledFlow: StateFlow<Boolean>
        field = MutableStateFlow(false)

    override suspend fun loadTrustedPlugins() {
        // The verifying status gives a possible macOS Keychain prompt visible context; with no key
        // the check is instant, so the status never renders.
        verifyingTrustRegistryFlow.value = true
        try {
            val signingEnabled = withContext(Dispatchers.IO) { trustRegistrySigner.hasKey() }
            signingEnabledFlow.value = signingEnabled
            verifyingTrustRegistryFlow.value = signingEnabled
            withContext(Dispatchers.IO) { pluginTrustRepository.load() }
        } finally {
            verifyingTrustRegistryFlow.value = false
        }
        val untrusted = mutableListOf<String>()
        for (jarPath in appDataDirectoryProvider.getAllPluginJarFilePaths()) {
            val trustedSha256 = trustedSha256(jarPath)
            if (trustedSha256 != null) {
                pluginFactoryRepository.loadPlugin(jarPath, trustedSha256)
            } else {
                logger.warning("Skipping untrusted plugin jar (not approved or content changed): $jarPath")
                untrusted += jarPath
            }
        }
        untrustedJarPathsFlow.value = untrusted

        // Jars from --plugin-dir skip the trust registry, as the dev plugins directory does: naming
        // the directory on the command line is the approval, and trust is defined over the managed
        // directory alone.
        for (jarPath in appDataDirectoryProvider.getAdditionalPluginJarFilePaths()) {
            pluginFactoryRepository.loadPlugin(jarPath, expectedSha256 = null)
        }
    }

    override suspend fun trustAndLoad(jarPath: String, approvedSha256: String?, replaceOtherVersions: Boolean) {
        require(appDataDirectoryProvider.isManagedPluginJarPath(jarPath)) {
            "Refusing to trust a jar outside the managed plugins directory: $jarPath"
        }
        jarStateMutex.withLock {
            val pinnedSha256 = approvedSha256 ?: computeSha256(jarPath)
            pluginTrustRepository.trust(jarPath, pinnedSha256)
            untrustedJarPathsFlow.update { it - jarPath }
            loadApproved(jarPath, pinnedSha256)
            val loadFailure = pluginFactoryRepository.failedJarsFlow.first().firstOrNull { it.jarPath == jarPath }?.reason
            if (loadFailure != null && computeSha256(jarPath) != pinnedSha256) {
                pluginTrustRepository.revoke(jarPath)
                untrustedJarPathsFlow.update { if (jarPath in it) it else it + jarPath }
                val arrivedJar = describeArrivedJar(jarPath)
                arrivedJarsFlow.update { arrived -> arrived.filterNot { it.jarPath == jarPath } + arrivedJar }
                return@withLock
            }
            arrivedJarsFlow.update { arrived ->
                if (loadFailure == null) {
                    arrived.filterNot { it.jarPath == jarPath }
                } else {
                    arrived.map { if (it.jarPath == jarPath) it.copy(loadFailure = loadFailure) else it }
                }
            }
            if (loadFailure == null && replaceOtherVersions) removeOtherVersions(jarPath)
        }
    }

    override suspend fun removePluginJar(jarPath: String) {
        require(appDataDirectoryProvider.isManagedPluginJarPath(jarPath)) {
            "Refusing to remove a jar outside the managed plugins directory: $jarPath"
        }
        jarStateMutex.withLock { removeJar(jarPath) }
    }

    /**
     * Removes the installed jars of the other versions of the plugins [jarPath] provides. A jar that
     * also provides a plugin [jarPath] does not is kept, since removing it would take that plugin
     * away too; so is a jar outside the managed directory, which the user put there by hand.
     */
    private suspend fun removeOtherVersions(jarPath: String) {
        val replacingPluginIds = pluginFactoryRepository.findPluginIdsByJarPath(jarPath).toSet()
        val otherJarPaths = replacingPluginIds
            .flatMap { pluginId -> pluginFactoryRepository.loadedPluginVersions[pluginId].orEmpty() }
            .map(LoadedHostPlugin::jarPath)
            .filter { it != jarPath && appDataDirectoryProvider.isManagedPluginJarPath(it) }
            .distinct()
        for (otherJarPath in otherJarPaths) {
            if (replacingPluginIds.containsAll(pluginFactoryRepository.findPluginIdsByJarPath(otherJarPath))) {
                removeJar(otherJarPath)
            } else {
                logger.info("Keeping $otherJarPath: it also provides plugins that $jarPath does not replace")
            }
        }
    }

    private suspend fun removeJar(jarPath: String) {
        pluginTrustRepository.revoke(jarPath)
        pluginJarSwapService.remove(jarPath)
        withContext(Dispatchers.IO) { File(jarPath).delete() }
        forget(jarPath)
    }

    override suspend fun onPluginJarsChanged(jarPaths: Set<String>): Unit = jarStateMutex.withLock {
        jarPaths.forEach { jarPath ->
            if (!File(jarPath).isFile) {
                forget(jarPath)
                pluginJarSwapService.remove(jarPath)
                return@forEach
            }
            val trustedSha256 = trustedSha256(jarPath)
            if (trustedSha256 != null) {
                forget(jarPath)
                if (pluginFactoryRepository.findPluginIdsByJarPath(jarPath).isEmpty()) {
                    loadApproved(jarPath, trustedSha256)
                }
            } else {
                logger.warning("Found an untrusted plugin jar at runtime: $jarPath")
                untrustedJarPathsFlow.update { if (jarPath in it) it else it + jarPath }
                val arrivedJar = describeArrivedJar(jarPath)
                arrivedJarsFlow.update { arrived -> arrived.filterNot { it.jarPath == jarPath } + arrivedJar }
            }
        }
    }

    /**
     * Loads [jarPath] against [approvedSha256]. A jar whose plugin versions already run, from this
     * path or from another jar it takes over, goes through the swap service, which disposes their instances
     * and scenes before their classloader is closed.
     */
    private suspend fun loadApproved(jarPath: String, approvedSha256: String) {
        val declared = withContext(Dispatchers.IO) { readDeclaredPluginManifestsOrEmpty(File(jarPath)) }
        val replacesRunningPlugins = pluginFactoryRepository.findPluginIdsByJarPath(jarPath).isNotEmpty() ||
            declared.any { manifest ->
                pluginFactoryRepository.loadedPluginVersions[manifest.pluginId].orEmpty().any { it.manifest.version == manifest.version }
            }
        if (replacesRunningPlugins) {
            pluginJarSwapService.reload(jarPath, approvedSha256)
        } else {
            pluginFactoryRepository.loadPlugin(jarPath, approvedSha256)
        }
    }

    override fun postponeArrivedJar(jarPath: String) {
        arrivedJarsFlow.update { arrived -> arrived.filterNot { it.jarPath == jarPath } }
    }

    private fun forget(jarPath: String) {
        untrustedJarPathsFlow.update { it - jarPath }
        arrivedJarsFlow.update { arrived -> arrived.filterNot { it.jarPath == jarPath } }
    }

    /**
     * Describes [jarPath] from its bytes and manifest alone; none of its classes are loaded. Size,
     * hash and manifest all come from one snapshot, so what the banner names is what its hash pins
     * even if the file is replaced meanwhile.
     */
    private suspend fun describeArrivedJar(jarPath: String): ArrivedPluginJar {
        var unreadableReason: String? = null
        val snapshot = withContext(Dispatchers.IO) { File.createTempFile("jetwhale-arrived-", ".jar") }
        val (manifest, sha256, sizeBytes) = try {
            withContext(Dispatchers.IO) {
                File(jarPath).copyTo(snapshot, overwrite = true)
                val manifest = try {
                    readJetWhaleHostPluginManifestFile(snapshot)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    unreadableReason = e.message ?: e.javaClass.simpleName
                    null
                }
                Triple(manifest, snapshot.sha256Hex(), snapshot.length())
            }
        } finally {
            snapshot.delete()
        }
        val declaredPluginManifests = manifest?.plugins.orEmpty()
        return ArrivedPluginJar(
            jarPath = jarPath,
            sizeBytes = sizeBytes,
            sha256 = sha256,
            declaredPlugins = declaredPluginManifests.map(JetWhaleHostPluginManifest::toDeclaredPlugin),
            unreadableReason = unreadableReason,
            loadFailure = null,
            replacedPlugins = pluginFactoryRepository.findPluginIdsByJarPath(jarPath).flatMap { pluginId ->
                pluginFactoryRepository.loadedPluginVersions[pluginId].orEmpty()
                    .filter { it.jarPath == jarPath }
                    .map { it.manifest.toDeclaredPlugin() }
            },
            otherVersions = declaredPluginManifests.flatMap { declared ->
                pluginFactoryRepository.loadedPluginVersions[declared.pluginId].orEmpty()
                    .filter { it.jarPath != jarPath && it.manifest.version != declared.version }
                    .map { it.manifest.toDeclaredPlugin() }
            },
        )
    }

    override suspend fun revokeTrust(jarPath: String): Unit = jarStateMutex.withLock {
        pluginTrustRepository.revoke(jarPath)
        pluginJarSwapService.remove(jarPath)
        if (File(jarPath).exists()) {
            untrustedJarPathsFlow.update { if (jarPath in it) it else it + jarPath }
        }
    }

    override suspend fun setSigningEnabled(enabled: Boolean): Unit = withContext(Dispatchers.IO) {
        if (enabled) {
            trustRegistrySigner.provisionKey()
        } else {
            trustRegistrySigner.deleteKey()
        }
        pluginTrustRepository.resign()
        signingEnabledFlow.value = trustRegistrySigner.hasKey()
    }

    /**
     * The pinned hash of [jarPath] when it has a trusted entry that matches the jar's current bytes,
     * or null. Loading passes it on, so the copy the classloader opens is checked against it too.
     */
    private suspend fun trustedSha256(jarPath: String): String? {
        val entry = pluginTrustRepository.trustedEntry(jarPath) ?: return null
        val currentSha256 = try {
            computeSha256(jarPath)
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            logger.warning("Failed to hash plugin jar, treating as untrusted: $jarPath (${e.message})")
            return null
        } catch (e: SecurityException) {
            logger.warning("Not allowed to read plugin jar, treating as untrusted: $jarPath (${e.message})")
            return null
        }
        return entry.sha256.takeIf { it == currentSha256 }
    }

    private suspend fun computeSha256(jarPath: String): String = withContext(Dispatchers.IO) { File(jarPath).sha256Hex() }
}

private fun JetWhaleHostPluginManifest.toDeclaredPlugin(): DeclaredPlugin = DeclaredPlugin(pluginId = pluginId, pluginName = pluginName, version = version)
