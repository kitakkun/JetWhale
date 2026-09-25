package com.kitakkun.jetwhale.host.data.plugin

import com.kitakkun.jetwhale.host.data.AppDataDirectoryProvider
import com.kitakkun.jetwhale.host.model.ArrivedPluginJar
import com.kitakkun.jetwhale.host.model.DeclaredPlugin
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
        // "Signing enabled" is defined by the presence of a key in the OS credential store, not a
        // writable flag. Show the "unlocking" status around the credential-store read so the macOS
        // Keychain prompt (if any) has visible context; a user with no key hits only a prompt-free
        // not-found check, which resolves instantly so the status never actually renders. The signer
        // caches the key, so load()'s verification below does not read it a second time.
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

        // Directories named with `--plugin-dir` load without consulting the trust registry, exactly as
        // the dev plugins directory already does. The registry answers "did the person running this
        // host approve this jar", and typing the directory on the command line is that approval —
        // there is also no UI path to approve them, since trusting is defined over the managed
        // directory alone.
        for (jarPath in appDataDirectoryProvider.getAdditionalPluginJarFilePaths()) {
            pluginFactoryRepository.loadPlugin(jarPath, expectedSha256 = null)
        }
    }

    override suspend fun trustAndLoad(jarPath: String, approvedSha256: String?) {
        require(appDataDirectoryProvider.isManagedPluginJarPath(jarPath)) {
            "Refusing to trust a jar outside the managed plugins directory: $jarPath"
        }
        jarStateMutex.withLock {
            // The load refuses a jar that no longer has the approved hash, so approving what a banner
            // showed never loads what replaced it.
            val pinnedSha256 = approvedSha256 ?: computeSha256(jarPath)
            // trust() signs the registry iff a key exists, so no signing flag is threaded through here.
            pluginTrustRepository.trust(jarPath, pinnedSha256)
            untrustedJarPathsFlow.update { it - jarPath }
            if (pluginFactoryRepository.findPluginIdsByJarPath(jarPath).isEmpty()) {
                pluginFactoryRepository.loadPlugin(jarPath, pinnedSha256)
            } else {
                pluginJarSwapService.reload(jarPath, pinnedSha256)
            }
            // An offered jar that fails to load stays offered with the reason, rather than vanishing
            // as if it had loaded.
            val loadFailure = pluginFactoryRepository.failedJarsFlow.first().firstOrNull { it.jarPath == jarPath }?.reason
            if (loadFailure != null && computeSha256(jarPath) != pinnedSha256) {
                // The jar changed after it was shown: what is there now was never approved, and the
                // watcher may already have reported it, so it is offered again here.
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
        }
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
                // The install flows load what they approve; this is a trusted jar put back by hand.
                if (pluginFactoryRepository.findPluginIdsByJarPath(jarPath).isEmpty()) {
                    pluginFactoryRepository.loadPlugin(jarPath, trustedSha256)
                }
            } else {
                logger.warning("Found an untrusted plugin jar at runtime: $jarPath")
                untrustedJarPathsFlow.update { if (jarPath in it) it else it + jarPath }
                val arrivedJar = describeArrivedJar(jarPath)
                arrivedJarsFlow.update { arrived -> arrived.filterNot { it.jarPath == jarPath } + arrivedJar }
            }
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
        return ArrivedPluginJar(
            jarPath = jarPath,
            sizeBytes = sizeBytes,
            sha256 = sha256,
            declaredPlugins = manifest?.plugins.orEmpty().map(JetWhaleHostPluginManifest::toDeclaredPlugin),
            unreadableReason = unreadableReason,
            loadFailure = null,
            replacedPlugins = pluginFactoryRepository.findPluginIdsByJarPath(jarPath).mapNotNull { pluginId ->
                pluginFactoryRepository.loadedPlugins[pluginId]?.manifest?.toDeclaredPlugin()
            },
        )
    }

    override suspend fun revokeTrust(jarPath: String): Unit = jarStateMutex.withLock {
        pluginTrustRepository.revoke(jarPath)
        // Dispose and unload everything this jar provided, as a deletion does, so revoking trust takes
        // effect immediately, without a restart. The jar file itself stays in the directory, so it
        // becomes untrusted-but-present.
        pluginJarSwapService.remove(jarPath)
        if (File(jarPath).exists()) {
            untrustedJarPathsFlow.update { if (jarPath in it) it else it + jarPath }
        }
    }

    override suspend fun setSigningEnabled(enabled: Boolean): Unit = withContext(Dispatchers.IO) {
        if (enabled) {
            // Provision a key, then re-sign the current registry so a later startup finds it signed.
            trustRegistrySigner.provisionKey()
        } else {
            // Deleting the key (needs credential-store access) is what actually turns signing off; an
            // attacker who can only write files cannot do this.
            trustRegistrySigner.deleteKey()
        }
        // Re-persist so the on-disk registry matches the new key state: signed after provisioning, or
        // unsigned after deletion (sign() returns null with no key).
        pluginTrustRepository.resign()
        // The flow reflects reality — the key that now does (or does not) exist — rather than the
        // requested value, so a failed provision/delete cannot leave the toggle lying.
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
        } catch (e: Exception) {
            // A jar we cannot read is a jar we cannot verify: fail safe as untrusted instead of
            // letting an IO error abort loading of every other plugin.
            logger.warning("Failed to hash plugin jar, treating as untrusted: $jarPath (${e.message})")
            return null
        }
        return entry.sha256.takeIf { it == currentSha256 }
    }

    private suspend fun computeSha256(jarPath: String): String = withContext(Dispatchers.IO) { File(jarPath).sha256Hex() }
}

private fun JetWhaleHostPluginManifest.toDeclaredPlugin(): DeclaredPlugin = DeclaredPlugin(pluginId = pluginId, pluginName = pluginName, version = version)
