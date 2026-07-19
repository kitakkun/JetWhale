package com.kitakkun.jetwhale.host.data.plugin

/**
 * Signs and verifies the serialized plugin trust registry so that a plain rewrite of the JSON file
 * is not enough to forge a trust decision. The HMAC key lives outside the file (in the OS
 * credential store), so an attacker must compromise that store as well, not just write a file.
 */
interface TrustRegistrySigner {
    /**
     * Returns the signature for [payload], or `null` when no signing key is available (e.g. the OS
     * credential store cannot be reached). A `null` result means the registry is persisted unsigned.
     */
    fun sign(payload: String): String?

    fun verify(payload: String, signature: String?): Verification

    enum class Verification {
        /** The signature matches [sign]'s output for the payload — the registry is authentic. */
        VALID,

        /** The signature is missing or does not match — the registry must not be trusted. */
        INVALID,

        /** No signing key is available, so authenticity cannot be checked either way. */
        UNAVAILABLE,
    }
}
