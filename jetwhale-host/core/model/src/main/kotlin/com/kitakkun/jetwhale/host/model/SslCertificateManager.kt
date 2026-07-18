package com.kitakkun.jetwhale.host.model

import kotlinx.coroutines.flow.StateFlow
import java.security.KeyStore

/**
 * Represents a single locally-issued TLS certificate entry.
 *
 * @property id Stable identifier of the entry.
 * @property name Human-readable name shown in the UI.
 * @property createdAt Creation timestamp (epoch millis).
 * @property caCertificatePem The CA certificate in PEM format. This is the trust anchor that target
 * apps must trust to establish a secure (wss) connection.
 * @property isActive Whether this entry is the one currently used by the server.
 */
data class SslCertificateEntry(
    val id: String,
    val name: String,
    val createdAt: Long,
    val caCertificatePem: String,
    val isActive: Boolean,
)

/**
 * Manages locally-issued TLS certificates used for secure WebSocket (wss) connections between the
 * JetWhale host and target apps.
 *
 * Each entry is a self-contained local PKI: a root CA plus a server certificate (signed by that CA)
 * with `localhost`/`127.0.0.1` Subject Alternative Names. The host serves wss using the server
 * certificate, while target apps trust the CA certificate exposed via [SslCertificateEntry.caCertificatePem].
 *
 * Multiple certificates can coexist so that a certificate can be rotated without immediately
 * invalidating apps still pinning the previous one.
 */
interface SslCertificateManager {
    /** Emits the current certificate entries, updated after every mutating operation. */
    val certificatesFlow: StateFlow<List<SslCertificateEntry>>

    /** Returns all certificate entries, ordered by creation time. */
    fun getAllCertificates(): List<SslCertificateEntry>

    /** Returns the active certificate entry, or null when none exists. */
    fun getActiveCertificate(): SslCertificateEntry?

    /** Returns true when at least one certificate exists. */
    fun hasCertificate(): Boolean

    /**
     * Generates a new local CA + server certificate, persists it and marks it active.
     *
     * @param name Optional display name; a timestamp-based name is generated when null.
     * @return The newly created entry.
     */
    fun generateAndAddCertificate(name: String?): SslCertificateEntry

    /**
     * Marks the certificate identified by [id] as active.
     *
     * @return true on success, false when no such certificate exists.
     */
    fun setActiveCertificate(id: String): Boolean

    /**
     * Deletes the certificate identified by [id]. When the deleted certificate was active, the
     * first remaining certificate (if any) becomes active.
     *
     * @return true when deleted, false when no such certificate exists.
     */
    fun deleteCertificate(id: String): Boolean

    /** Returns the [KeyStore] (server private key + certificate chain) for the active certificate, or null. */
    fun getActiveKeyStore(): KeyStore?

    /** Returns the password protecting the keystores. */
    fun getKeyStorePassword(): CharArray

    /** Returns the key alias of the active certificate inside its keystore, or null. */
    fun getActiveKeyAlias(): String?
}
