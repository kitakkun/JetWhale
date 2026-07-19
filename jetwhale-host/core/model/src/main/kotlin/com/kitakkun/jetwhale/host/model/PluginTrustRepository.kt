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
     * Populates [trustedEntriesFlow] from disk. Call once before reading any trust decision. When
     * [signingEnabled] is true the on-disk signature is verified (touching the OS credential store);
     * when false the registry is read as-is and the credential store is never accessed.
     */
    suspend fun load(signingEnabled: Boolean)

    /**
     * Records (or replaces) the trusted entry for a jar, pinning [sha256] as its approved content.
     * [signingEnabled] selects whether the rewritten registry is signed.
     */
    suspend fun trust(jarPath: String, sha256: String, signingEnabled: Boolean)

    /**
     * Removes any trusted entry for [jarPath]. [signingEnabled] selects whether the rewritten
     * registry is signed.
     */
    suspend fun revoke(jarPath: String, signingEnabled: Boolean)

    /**
     * Re-persists the current in-memory entries so the registry on disk matches [signingEnabled].
     * Used when signing is turned on to sign a previously-unsigned registry (a one-time credential
     * store prompt), so a later startup never sees an unsigned registry while signing is on.
     */
    suspend fun resign(signingEnabled: Boolean)
}
