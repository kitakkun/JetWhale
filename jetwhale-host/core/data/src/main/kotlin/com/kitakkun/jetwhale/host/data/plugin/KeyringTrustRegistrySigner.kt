package com.kitakkun.jetwhale.host.data.plugin

import com.github.javakeyring.Keyring
import com.github.javakeyring.PasswordAccessException
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.logging.Logger
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * [TrustRegistrySigner] backed by an HMAC-SHA256 key stored in the OS credential store (macOS
 * Keychain, Windows Credential Manager, or Linux Secret Service via java-keyring). The key never
 * touches the app data directory, so rewriting files under `~/.jetwhale` alone cannot produce a
 * registry that verifies.
 *
 * Reading the key never creates one: [provisionKey] is the only path that writes a key, and it runs
 * only when the user explicitly enables signing. A missing key is reported without a prompt (the
 * not-found path below), so a user who never enabled signing is never prompted on startup.
 *
 * The resolved key state is cached for the lifetime of the process so that `hasKey()`, `verify` and
 * `sign` together trigger at most one credential-store read (hence at most one macOS Keychain
 * prompt); [provisionKey] and [deleteKey] refresh the cache.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
@Inject
class KeyringTrustRegistrySigner : TrustRegistrySigner {
    private val logger = Logger.getLogger(KeyringTrustRegistrySigner::class.java.name)

    private sealed interface KeyState {
        /** A usable key is present. */
        data class Present(val key: ByteArray) : KeyState

        /** An item exists but is not a valid key (written by something else): fail-safe, never overwritten on read. */
        data object Corrupt : KeyState

        /** No key item exists (signing is off). Determined without a prompt. */
        data object Absent : KeyState

        /** The credential store could not be reached. */
        data object Unavailable : KeyState
    }

    @Volatile
    private var cachedState: KeyState? = null

    private fun keyState(): KeyState = cachedState ?: readKeyState().also { cachedState = it }

    private fun readKeyState(): KeyState = try {
        Keyring.create().use { keyring ->
            val stored = try {
                keyring.getPassword(KEYRING_SERVICE, KEYRING_ACCOUNT)
            } catch (e: PasswordAccessException) {
                // No key stored: java-keyring reports the missing item as not-found WITHOUT prompting,
                // which is what keeps the prompt-free-by-default property.
                return KeyState.Absent
            }
            val decoded = try {
                Base64.getDecoder().decode(stored).takeIf { it.size == KEY_LENGTH_BYTES }
            } catch (e: IllegalArgumentException) {
                null
            }
            if (decoded == null) {
                // An item exists but is not a valid key. Do NOT provision over it during a read; treat
                // it as present-but-unusable so verification fails safe (INVALID) instead of silently
                // downgrading to unsigned.
                logger.warning("Stored plugin trust registry key is corrupted; the registry will not verify.")
                KeyState.Corrupt
            } else {
                KeyState.Present(decoded)
            }
        }
    } catch (e: Exception) {
        logger.warning("OS credential store unavailable, plugin trust registry cannot be signature-checked: ${e.message}")
        KeyState.Unavailable
    }

    override fun hasKey(): Boolean = when (keyState()) {
        is KeyState.Present, KeyState.Corrupt -> true
        KeyState.Absent, KeyState.Unavailable -> false
    }

    override fun sign(payload: String): String? {
        val key = (keyState() as? KeyState.Present)?.key ?: return null
        return Base64.getEncoder().encodeToString(hmac(key, payload))
    }

    override fun verify(payload: String, signature: String?): TrustRegistrySigner.Verification = when (val state = keyState()) {
        KeyState.Absent -> TrustRegistrySigner.Verification.DISABLED

        KeyState.Unavailable -> TrustRegistrySigner.Verification.UNAVAILABLE

        KeyState.Corrupt -> TrustRegistrySigner.Verification.INVALID

        is KeyState.Present -> {
            if (signature == null) {
                // A key exists, so every registry this app writes is signed. A missing signature means
                // the file was replaced by something that could not sign it: reject.
                TrustRegistrySigner.Verification.INVALID
            } else {
                val expected = hmac(state.key, payload)
                val actual = try {
                    Base64.getDecoder().decode(signature)
                } catch (e: IllegalArgumentException) {
                    return TrustRegistrySigner.Verification.INVALID
                }
                if (MessageDigest.isEqual(expected, actual)) {
                    TrustRegistrySigner.Verification.VALID
                } else {
                    TrustRegistrySigner.Verification.INVALID
                }
            }
        }
    }

    override fun provisionKey() {
        // Idempotent: keep an existing valid key so its previous signatures stay valid. A corrupt or
        // absent item is replaced with a fresh key; an unreachable store surfaces the failure.
        if (keyState() is KeyState.Present) return
        val newKey = ByteArray(KEY_LENGTH_BYTES).also { SecureRandom().nextBytes(it) }
        Keyring.create().use { keyring ->
            keyring.setPassword(KEYRING_SERVICE, KEYRING_ACCOUNT, Base64.getEncoder().encodeToString(newKey))
        }
        cachedState = KeyState.Present(newKey)
    }

    override fun deleteKey() {
        Keyring.create().use { keyring ->
            try {
                keyring.deletePassword(KEYRING_SERVICE, KEYRING_ACCOUNT)
            } catch (e: PasswordAccessException) {
                // Already absent: nothing to delete.
            }
        }
        cachedState = KeyState.Absent
    }

    private fun hmac(key: ByteArray, payload: String): ByteArray {
        val mac = Mac.getInstance(HMAC_ALGORITHM)
        mac.init(SecretKeySpec(key, HMAC_ALGORITHM))
        return mac.doFinal(payload.toByteArray(Charsets.UTF_8))
    }

    companion object {
        private const val KEYRING_SERVICE = "JetWhale"
        private const val KEYRING_ACCOUNT = "plugin-trust-registry-hmac"
        private const val HMAC_ALGORITHM = "HmacSHA256"
        private const val KEY_LENGTH_BYTES = 32
    }
}
