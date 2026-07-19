package com.kitakkun.jetwhale.host.data.plugin

import com.kitakkun.jetwhale.host.data.AppDataDirectoryProvider
import kotlinx.coroutines.runBlocking
import java.io.File
import java.security.MessageDigest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DefaultPluginTrustRepositoryTest {
    private var originalUserHome: String? = null
    private lateinit var tempHome: File

    @BeforeTest
    fun setUp() {
        // AppDataDirectoryProvider resolves its paths from user.home in field initializers, so point
        // it at an isolated temp home before any provider is constructed in a test.
        originalUserHome = System.getProperty("user.home")
        tempHome = File.createTempFile("jetwhale-home-", "").apply {
            delete()
            mkdirs()
        }
        System.setProperty("user.home", tempHome.absolutePath)
    }

    @AfterTest
    fun cleanup() {
        originalUserHome?.let { System.setProperty("user.home", it) }
        tempHome.deleteRecursively()
    }

    // A fresh repository each time so we exercise the on-disk read path, not just the in-memory
    // cache. The caller decides whether signing is on by passing `signingEnabled` to load/trust; the
    // repository holds no policy of its own.
    private fun newRepository(signer: TrustRegistrySigner = FakeTrustRegistrySigner()) = DefaultPluginTrustRepository(AppDataDirectoryProvider(), signer)

    /**
     * Deterministic stand-in for the keyring-backed signer: the "signature" is a digest of the
     * payload (not the payload itself, so editing the file cannot keep it self-consistent), which
     * preserves the property under test — a signature only verifies against the exact payload it
     * was produced for.
     */
    private class FakeTrustRegistrySigner(
        private val available: Boolean = true,
        private val keyPreexisted: Boolean = true,
    ) : TrustRegistrySigner {
        override fun sign(payload: String): String? = if (available) digest(payload) else null

        override fun verify(payload: String, signature: String?): TrustRegistrySigner.Verification = when {
            !available -> TrustRegistrySigner.Verification.UNAVAILABLE
            signature == null -> if (keyPreexisted) TrustRegistrySigner.Verification.INVALID else TrustRegistrySigner.Verification.VALID
            signature == digest(payload) -> TrustRegistrySigner.Verification.VALID
            else -> TrustRegistrySigner.Verification.INVALID
        }

        private fun digest(payload: String): String = "signed:" + MessageDigest.getInstance("SHA-256").digest(payload.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    /**
     * A signer that fails the test if it is ever consulted. Used to assert that with signing off no
     * code path reaches the signer (and therefore the OS credential store).
     */
    private class ThrowingTrustRegistrySigner : TrustRegistrySigner {
        override fun sign(payload: String): String? = error("signer must not be invoked when signing is disabled")

        override fun verify(payload: String, signature: String?): TrustRegistrySigner.Verification = error("signer must not be invoked when signing is disabled")
    }

    @Test
    fun `trusted entries survive a read back from disk`() = runBlocking {
        newRepository().apply {
            trust("/plugins/a.jar", "hash-a", signingEnabled = true)
            trust("/plugins/b.jar", "hash-b", signingEnabled = true)
        }

        val reloaded = newRepository().apply { load(signingEnabled = true) }
        assertEquals("hash-a", reloaded.trustedEntry("/plugins/a.jar")?.sha256)
        assertEquals("hash-b", reloaded.trustedEntry("/plugins/b.jar")?.sha256)
    }

    @Test
    fun `revoke removes the entry and persists`() = runBlocking {
        newRepository().apply {
            trust("/plugins/a.jar", "hash-a", signingEnabled = true)
            revoke("/plugins/a.jar", signingEnabled = true)
        }

        val reloaded = newRepository().apply { load(signingEnabled = true) }
        assertNull(reloaded.trustedEntry("/plugins/a.jar"))
    }

    @Test
    fun `a tampered registry is rejected wholesale`() = runBlocking {
        newRepository().trust("/plugins/a.jar", "hash-a", signingEnabled = true)

        // Simulate a malicious process rewriting the file: swap in a different hash while leaving
        // the recorded signature untouched.
        val registryFile = AppDataDirectoryProvider().getTrustRegistryFile()
        registryFile.writeText(registryFile.readText().replace("hash-a", "forged-hash"))

        val reloaded = newRepository().apply { load(signingEnabled = true) }
        assertNull(reloaded.trustedEntry("/plugins/a.jar"))
    }

    @Test
    fun `an unsigned registry is rejected when a signing key already exists`() = runBlocking {
        newRepository(FakeTrustRegistrySigner(available = false)).trust("/plugins/a.jar", "hash-a", signingEnabled = true)

        val reloaded = newRepository(FakeTrustRegistrySigner(keyPreexisted = true)).apply { load(signingEnabled = true) }
        assertNull(reloaded.trustedEntry("/plugins/a.jar"))
    }

    @Test
    fun `an unsigned registry is accepted on first run with a new signing key`() = runBlocking {
        // Upgrade path: the registry predates registry signing, so no signature exists yet. The
        // key being brand new proves no signed registry could ever have been written.
        newRepository(FakeTrustRegistrySigner(available = false)).trust("/plugins/a.jar", "hash-a", signingEnabled = true)

        val reloaded = newRepository(FakeTrustRegistrySigner(keyPreexisted = false)).apply { load(signingEnabled = true) }
        assertEquals("hash-a", reloaded.trustedEntry("/plugins/a.jar")?.sha256)
    }

    @Test
    fun `registry is loaded without verification when the credential store is unavailable`() = runBlocking {
        newRepository().trust("/plugins/a.jar", "hash-a", signingEnabled = true)

        val reloaded = newRepository(FakeTrustRegistrySigner(available = false)).apply { load(signingEnabled = true) }
        assertEquals("hash-a", reloaded.trustedEntry("/plugins/a.jar")?.sha256)
    }

    @Test
    fun `the signer is never consulted when signing is disabled`() = runBlocking {
        newRepository(signer = ThrowingTrustRegistrySigner())
            .trust("/plugins/a.jar", "hash-a", signingEnabled = false)

        val reloaded = newRepository(signer = ThrowingTrustRegistrySigner()).apply { load(signingEnabled = false) }
        assertEquals("hash-a", reloaded.trustedEntry("/plugins/a.jar")?.sha256)
    }

    @Test
    fun `resign signs a previously unsigned registry so it verifies with signing on`() = runBlocking {
        // Approve while signing is off: the registry is written unsigned.
        newRepository(signer = ThrowingTrustRegistrySigner())
            .trust("/plugins/a.jar", "hash-a", signingEnabled = false)

        // Re-sign the current registry (as setSigningEnabled(true) does), then a signing-on reload
        // with a pre-existing key verifies the freshly written signature instead of rejecting the
        // file as unsigned-but-signing-on.
        newRepository(FakeTrustRegistrySigner(keyPreexisted = true)).apply {
            load(signingEnabled = false)
            resign(signingEnabled = true)
        }

        val reloaded = newRepository(FakeTrustRegistrySigner(keyPreexisted = true)).apply { load(signingEnabled = true) }
        assertEquals("hash-a", reloaded.trustedEntry("/plugins/a.jar")?.sha256)
    }
}
