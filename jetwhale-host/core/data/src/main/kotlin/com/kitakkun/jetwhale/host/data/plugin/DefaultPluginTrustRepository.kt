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
import java.util.logging.Logger

/**
 * JSON-file-backed trust registry stored at `~/.jetwhale/trusted-plugins.json`. [load] reads it once
 * (typically on startup, before any plugin jar is loaded); every mutation updates the in-memory
 * snapshot and rewrites the file under a mutex.
 *
 * The registry is signed iff a signing key exists in the OS credential store — that decision is owned
 * entirely by [TrustRegistrySigner], not by any writable flag. With no key, [TrustRegistrySigner.sign]
 * returns `null` (the registry is written unsigned) and [TrustRegistrySigner.verify] reports
 * [TrustRegistrySigner.Verification.DISABLED] (the registry is loaded unverified) — both prompt-free.
 * The SHA-256 content pinning of approved jars is enforced by [PluginTrustService] regardless.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
@Inject
class DefaultPluginTrustRepository(
    private val appDataDirectoryProvider: AppDataDirectoryProvider,
    private val trustRegistrySigner: TrustRegistrySigner,
) : PluginTrustRepository {
    private val logger = Logger.getLogger(DefaultPluginTrustRepository::class.java.name)
    private val writeMutex = Mutex()

    override val trustedEntriesFlow: Flow<Map<String, TrustedPluginEntry>>
        field = MutableStateFlow(emptyMap())

    override suspend fun trustedEntry(jarPath: String): TrustedPluginEntry? = trustedEntriesFlow.value[jarPath]

    override suspend fun load(): Unit = writeMutex.withLock {
        trustedEntriesFlow.value = readFromDisk()
    }

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

    override suspend fun resign(): Unit = writeMutex.withLock {
        persist(trustedEntriesFlow.value)
    }

    private fun readFromDisk(): Map<String, TrustedPluginEntry> {
        val file = appDataDirectoryProvider.getTrustRegistryFile()
        if (!file.exists()) return emptyMap()
        return try {
            val registry = json.decodeFromString<TrustRegistryFile>(file.readText())
            // Verify the HMAC over the exact re-encoding of the entries map — the same string
            // persist() signed. The signer's answer depends only on whether a key exists:
            //  - DISABLED   → no key, so signing is off: load the registry unverified (prompt-free).
            //  - VALID      → the signature matches: load.
            //  - INVALID    → a key exists but the signature is missing/forged (the file was rewritten
            //                 by something that could not sign it): fail safe, trust nothing.
            //  - UNAVAILABLE→ a key may exist but the store could not be read: load with a warning.
            when (trustRegistrySigner.verify(json.encodeToString(registry.entries), registry.signature)) {
                TrustRegistrySigner.Verification.VALID,
                TrustRegistrySigner.Verification.DISABLED,
                -> Unit

                TrustRegistrySigner.Verification.INVALID -> {
                    logger.warning("Plugin trust registry failed signature verification, treating all plugins as untrusted.")
                    return emptyMap()
                }

                TrustRegistrySigner.Verification.UNAVAILABLE -> {
                    logger.warning("OS credential store unavailable; loading plugin trust registry without signature verification.")
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
            logger.warning("Failed to read plugin trust registry, treating all plugins as untrusted: ${e.message}")
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
        // sign() returns the signature when a key exists, or null when it does not (signing off) —
        // so the registry is signed iff a key exists, with no separate flag to keep in sync.
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
