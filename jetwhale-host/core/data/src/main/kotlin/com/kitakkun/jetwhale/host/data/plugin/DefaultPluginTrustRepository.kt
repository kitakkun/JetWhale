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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * JSON-file-backed trust registry stored at `~/.jetwhale/trusted-plugins.json`. Reads itself once on
 * construction so the trust decision is available before any plugin jar is loaded; every mutation
 * updates the in-memory snapshot and rewrites the file under a mutex.
 *
 * The JSON is built and parsed with the [JsonObject] tree API rather than `@Serializable` classes on
 * purpose: this module does not apply the kotlinx.serialization compiler plugin, so a generated
 * serializer for a local data class would not exist and `encodeToString` would fail at runtime.
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
            val root = json.parseToJsonElement(file.readText()).jsonObject
            val entries = root[ENTRIES_KEY]?.jsonObject ?: return emptyMap()
            entries.mapValues { (path, element) ->
                val entry = element.jsonObject
                TrustedPluginEntry(
                    jarPath = path,
                    sha256 = entry.getValue(SHA256_KEY).jsonPrimitive.content,
                    trustedAtEpochMillis = entry.getValue(TRUSTED_AT_KEY).jsonPrimitive.long,
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
        val root = buildJsonObject {
            put(
                ENTRIES_KEY,
                JsonObject(
                    entries.mapValues { (_, entry) ->
                        buildJsonObject {
                            put(SHA256_KEY, entry.sha256)
                            put(TRUSTED_AT_KEY, entry.trustedAtEpochMillis)
                        }
                    },
                ),
            )
        }
        // Write to a sibling temp file and move it into place so an interrupted write can never
        // leave a truncated registry behind (which would fail-safe but wipe all trust decisions).
        val tempFile = File(file.parentFile, "${file.name}.tmp")
        tempFile.writeText(json.encodeToString(JsonElement.serializer(), root))
        try {
            Files.move(tempFile.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (e: AtomicMoveNotSupportedException) {
            Files.move(tempFile.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    companion object {
        private const val ENTRIES_KEY = "entries"
        private const val SHA256_KEY = "sha256"
        private const val TRUSTED_AT_KEY = "trustedAtEpochMillis"
        private val json = Json { prettyPrint = true }
    }
}
