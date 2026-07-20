package com.kitakkun.jetwhale.host.data.plugin

/**
 * Signs and verifies the serialized plugin trust registry so that a plain rewrite of the JSON file
 * is not enough to forge a trust decision. The HMAC key lives outside the file, in the OS credential
 * store, so an attacker must compromise that store as well, not just write a file.
 *
 * "Signing is enabled" is defined solely by the **presence of a key** in the credential store, never
 * by a writable flag: an attacker who can only write files cannot disable signing, because removing
 * the key needs credential-store access. Reading the key never provisions one — [provisionKey] is the
 * only way a key is created, and only when the user explicitly enables signing.
 */
interface TrustRegistrySigner {
    /**
     * Returns the signature for [payload] using the existing key, or `null` when no key exists (or
     * the credential store is unreachable). Never provisions a key. A `null` result means the
     * registry is persisted unsigned.
     */
    fun sign(payload: String): String?

    fun verify(payload: String, signature: String?): Verification

    /**
     * Whether a signing key currently exists. Checking for an **absent** key is prompt-free (a
     * missing credential-store item reports not-found), so a user who never enabled signing is never
     * prompted. Confirming a **present** key requires reading it, which may prompt (macOS Keychain);
     * that is acceptable because [verify] reads the same key anyway and the implementation caches it.
     */
    fun hasKey(): Boolean

    /** Creates and stores a fresh random key if none is usable. Idempotent. Used only when enabling signing. */
    fun provisionKey()

    /** Removes the signing key if present. Used only when disabling signing. */
    fun deleteKey()

    enum class Verification {
        /** The signature matches [sign]'s output for the payload — the registry is authentic. */
        VALID,

        /** The signature is missing or does not match while a key exists — the registry must not be trusted. */
        INVALID,

        /** A signing key exists but the credential store could not be read, so authenticity is unknown. */
        UNAVAILABLE,

        /** No signing key exists (signing is off): the registry is loaded unverified. Reported prompt-free. */
        DISABLED,
    }
}
