package com.kitakkun.jetwhale.host.data.plugin

import com.kitakkun.jetwhale.host.data.AppDataDirectoryProvider
import com.kitakkun.jetwhale.host.model.PluginTrustRepository
import com.kitakkun.jetwhale.host.model.TrustedPluginEntry
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * JSON-file-backed trust registry stored at `~/.jetwhale/trusted-plugins.json`. Reads itself once on
 * construction so the trust decision is available before any plugin jar is loaded; every mutation
 * updates the in-memory snapshot and rewrites the file under a mutex.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
@Inject
class DefaultPluginTrustRepository(
    private val appDataDirectoryProvider: AppDataDirectoryProvider,
    private val trustRegistrySigner: TrustRegistrySigner,
) : PluginTrustRepository {
    private val writeMutex = Mutex()

    override val trustedEntriesFlow: Flow<Map<String, TrustedPluginEntry>>
        field = MutableStateFlow(readFromDisk())

    override suspend fun trustedEntry(jarPath: String): TrustedPluginEntry? = trustedEntriesFlow.value[jarPath]

    override suspend fun trust(jarPath: String, sha256: String): Unit = writeMutex.withLock {
        val updated = trustedEntriesFlow.value + (jarPath to TrustedPluginEntry(jarPath, sha256, System.currentTimeMillis()))
        persist(updated)
        trustedEntriesFlow.value = updated
    }

    override suspend fun revoke(jarPath: String): Unit = writeMutex.withLock {
        if (jarPath !in trustedEntriesFlow.value) return@withLock
        val updated = trustedEntriesFlow.value - jarPath
        persist(updated)
        trustedEntriesFlow.value = updated
    }

    private fun readFromDisk(): Map<String, TrustedPluginEntry> {
        val file = appDataDirectoryProvider.getTrustRegistryFile()
        if (!file.exists()) return emptyMap()
        return try {
            val registry = json.decodeFromString<TrustRegistryFile>(file.readText())
            // Verify the HMAC over the exact re-encoding of the entries map — the same string
            // persist() signed. An INVALID result means the file was modified by something other
            // than this app (or the key store was reset): fail safe, trust nothing.
            val verification = trustRegistrySigner.verify(json.encodeToString(registry.entries), registry.signature)
            when (verification) {
                TrustRegistrySigner.Verification.VALID -> Unit

                TrustRegistrySigner.Verification.INVALID -> {
                    println("Plugin trust registry failed signature verification, treating all plugins as untrusted.")
                    return emptyMap()
                }

                TrustRegistrySigner.Verification.UNAVAILABLE -> {
                    println("OS credential store unavailable; loading plugin trust registry without signature verification.")
                }
            }
            registry.entries.mapValues { (path, entry) ->
                TrustedPluginEntry(
                    jarPath = path,
                    sha256 = entry.sha256,
                    trustedAtEpochMillis = entry.trustedAtEpochMillis,
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            // A corrupt or unreadable registry must fail safe: treat everything as untrusted rather
            // than risk loading a jar we cannot prove was approved.
            println("Failed to read plugin trust registry, treating all plugins as untrusted: ${e.message}")
            emptyMap()
        }
    }

    private fun persist(entries: Map<String, TrustedPluginEntry>) {
        val file = appDataDirectoryProvider.getTrustRegistryFile()
        file.parentFile?.mkdirs()
        val storedEntries = entries.mapValues { (_, entry) ->
            StoredTrustedPluginEntry(
                sha256 = entry.sha256,
                trustedAtEpochMillis = entry.trustedAtEpochMillis,
            )
        }
        val registry = TrustRegistryFile(
            entries = storedEntries,
            signature = trustRegistrySigner.sign(json.encodeToString(storedEntries)),
        )
        // Write to a sibling temp file and move it into place so an interrupted write can never
        // leave a truncated registry behind (which would fail-safe but wipe all trust decisions).
        val tempFile = File(file.parentFile, "${file.name}.tmp")
        tempFile.writeText(json.encodeToString(registry))
        try {
            Files.move(tempFile.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (e: AtomicMoveNotSupportedException) {
            Files.move(tempFile.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    /**
     * On-disk shape of the registry. [signature] is the HMAC over the serialized [entries] map;
     * it is `null` when the credential store was unavailable at write time, and absent in files
     * written before registry signing existed.
     */
    @Serializable
    private data class TrustRegistryFile(
        val entries: Map<String, StoredTrustedPluginEntry>,
        val signature: String? = null,
    )

    @Serializable
    private data class StoredTrustedPluginEntry(
        val sha256: String,
        val trustedAtEpochMillis: Long,
    )

    companion object {
        private val json = Json { prettyPrint = true }
    }
}
