package com.kitakkun.jetwhale.host.model

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * The security boundary in front of dynamic plugin loading.
 *
 * JetWhale loads plugin jars from `~/.jetwhale/plugins`, and loading a jar executes attacker-
 * controlled code (the factory constructor, the plugin's Compose content). Because any process
 * running as the user can drop a jar into that directory, blindly loading every jar there is a code-
 * execution hole. This service closes it by loading **only** jars the user has explicitly approved,
 * each pinned to the content hash it had at approval time (see [PluginTrustRepository]): a jar that
 * was never approved — or whose bytes changed after approval — is surfaced for review instead of
 * being executed.
 *
 * This is an entry-side defense by design. Once untrusted code is loaded into the JVM it cannot be
 * reliably sandboxed (the `SecurityManager` is removed in modern JDKs), so the only effective control
 * is to never load code the user did not vouch for.
 */
interface PluginTrustService {
    /**
     * Jars currently present in the plugins directory that are **not** trusted: either never approved,
     * or approved under a different content hash (i.e. the jar was replaced/tampered after approval).
     * These are not loaded; the UI surfaces them so the user can review and approve them.
     */
    val untrustedJarPathsFlow: Flow<List<String>>

    /**
     * True while [loadTrustedPlugins] is reading the OS credential store to verify the signed trust
     * registry. Only ever true when registry signing is enabled — on macOS the read raises a blocking
     * Keychain prompt, and the UI observes this flag to show that prompt with visible context. When
     * signing is off the credential store is never touched, so this stays false.
     */
    val verifyingTrustRegistryFlow: StateFlow<Boolean>

    /**
     * Loads every trusted jar from the plugins directory and records the rest as untrusted (see
     * [untrustedJarPathsFlow]). Call once on startup in place of loading the directory wholesale.
     */
    suspend fun loadTrustedPlugins()

    /**
     * Approves [jarPath]: pins its current content hash in the trust registry and loads it. This is
     * the consent point — call it only in response to an explicit user action (installing a jar via
     * the file picker, or approving a surfaced untrusted jar).
     */
    suspend fun trustAndLoad(jarPath: String)

    /** Revokes approval for [jarPath], unloads the plugins it provided, and re-flags it as untrusted. */
    suspend fun revokeTrust(jarPath: String)

    /**
     * Turns trust-registry signing on or off and persists the choice. Enabling it re-signs the
     * current registry (provisioning the signing key, a one-time credential-store prompt) so a later
     * startup never encounters an unsigned registry while signing is on; disabling it just records
     * the preference and leaves the credential store untouched.
     */
    suspend fun setSigningEnabled(enabled: Boolean)
}
