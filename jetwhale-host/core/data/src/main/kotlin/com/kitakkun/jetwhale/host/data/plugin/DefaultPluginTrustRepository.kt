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
import kotlinx.coroutines.flow.asStateFlow
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
) : PluginTrustRepository {
    private val writeMutex = Mutex()

    private val mutableEntriesFlow: MutableStateFlow<Map<String, TrustedPluginEntry>> =
        MutableStateFlow(readFromDisk())
    override val trustedEntriesFlow: Flow<Map<String, TrustedPluginEntry>> = mutableEntriesFlow.asStateFlow()

    override suspend fun trustedEntry(jarPath: String): TrustedPluginEntry? = mutableEntriesFlow.value[jarPath]

    override suspend fun trust(jarPath: String, sha256: String): Unit = writeMutex.withLock {
        val updated = mutableEntriesFlow.value + (jarPath to TrustedPluginEntry(jarPath, sha256, System.currentTimeMillis()))
        persist(updated)
        mutableEntriesFlow.value = updated
    }

    override suspend fun revoke(jarPath: String): Unit = writeMutex.withLock {
        if (jarPath !in mutableEntriesFlow.value) return@withLock
        val updated = mutableEntriesFlow.value - jarPath
        persist(updated)
        mutableEntriesFlow.value = updated
    }

    private fun readFromDisk(): Map<String, TrustedPluginEntry> {
        val file = appDataDirectoryProvider.getTrustRegistryFile()
        if (!file.exists()) return emptyMap()
        return try {
            val registry = json.decodeFromString<TrustRegistryFile>(file.readText())
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
        val registry = TrustRegistryFile(
            entries = entries.mapValues { (_, entry) ->
                StoredTrustedPluginEntry(
                    sha256 = entry.sha256,
                    trustedAtEpochMillis = entry.trustedAtEpochMillis,
                )
            },
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

    @Serializable
    private data class TrustRegistryFile(
        val entries: Map<String, StoredTrustedPluginEntry>,
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
