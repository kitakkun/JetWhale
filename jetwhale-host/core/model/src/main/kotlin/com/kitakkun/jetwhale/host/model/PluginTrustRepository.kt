package com.kitakkun.jetwhale.host.model

import kotlinx.coroutines.flow.Flow

/**
 * A jar the user has explicitly approved for loading, pinned to the content hash it had at the
 * moment of approval. On startup only jars whose current content still matches their pinned
 * [sha256] are loaded; anything else (a jar that was never approved, or one whose bytes changed
 * after approval) is treated as untrusted and is not executed.
 */
data class TrustedPluginEntry(
    val jarPath: String,
    val sha256: String,
    val trustedAtEpochMillis: Long,
)

/**
 * Persists the plugin trust registry: the set of jars the user has explicitly approved, each pinned
 * to its content hash (see [TrustedPluginEntry]). This is the single data source backing the trust
 * decision; the business logic that compares a jar's current hash against its pinned value lives in
 * [PluginTrustService].
 */
interface PluginTrustRepository {
    val trustedEntriesFlow: Flow<Map<String, TrustedPluginEntry>>

    suspend fun trustedEntry(jarPath: String): TrustedPluginEntry?

    /**
     * Populates [trustedEntriesFlow] from disk. Call once before reading any trust decision. The
     * registry is signed (and verified) iff a signing key exists in the OS credential store; that
     * decision is owned by the signer, not by this repository.
     */
    suspend fun load()

    /** Records (or replaces) the trusted entry for a jar, pinning [sha256] as its approved content. */
    suspend fun trust(jarPath: String, sha256: String)

    /** Removes any trusted entry for [jarPath]. */
    suspend fun revoke(jarPath: String)

    /**
     * Re-persists the current in-memory entries so the on-disk registry matches the current signing
     * key state. Called after enabling signing (to sign a previously-unsigned registry) or disabling
     * it (to strip the signature).
     */
    suspend fun resign()
}
