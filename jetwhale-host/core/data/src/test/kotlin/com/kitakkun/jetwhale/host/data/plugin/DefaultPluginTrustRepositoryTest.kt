package com.kitakkun.jetwhale.host.data.plugin

import com.kitakkun.jetwhale.host.data.AppDataDirectoryProvider
import kotlinx.coroutines.runBlocking
import java.io.File
import java.security.MessageDigest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
    // cache. Whether the registry is signed is decided entirely by the injected signer (key present
    // or not); the repository holds no policy of its own.
    private fun newRepository(signer: TrustRegistrySigner = FakeTrustRegistrySigner()) = DefaultPluginTrustRepository(AppDataDirectoryProvider(), signer)

    /**
     * Deterministic stand-in for the keyring-backed signer. "Signing enabled" is modeled by
     * [keyPresent]; the "signature" is a digest of the payload (not the payload itself, so editing
     * the file cannot keep it self-consistent). Reading never provisions — only [provisionKey] flips
     * a key into existence — which is the property the security tests rely on.
     */
    private class FakeTrustRegistrySigner(
        keyPresent: Boolean = true,
        private val available: Boolean = true,
    ) : TrustRegistrySigner {
        var keyPresent = keyPresent
            private set

        override fun hasKey(): Boolean = available && keyPresent

        override fun sign(payload: String): String? = if (available && keyPresent) digest(payload) else null

        override fun verify(payload: String, signature: String?): TrustRegistrySigner.Verification = when {
            !available -> TrustRegistrySigner.Verification.UNAVAILABLE
            !keyPresent -> TrustRegistrySigner.Verification.DISABLED
            signature == null -> TrustRegistrySigner.Verification.INVALID
            signature == digest(payload) -> TrustRegistrySigner.Verification.VALID
            else -> TrustRegistrySigner.Verification.INVALID
        }

        override fun provisionKey() {
            keyPresent = true
        }

        override fun deleteKey() {
            keyPresent = false
        }

        private fun digest(payload: String): String = "signed:" + MessageDigest.getInstance("SHA-256").digest(payload.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    @Test
    fun `trusted entries survive a read back from disk`() = runBlocking {
        newRepository().apply {
            trust("/plugins/a.jar", "hash-a")
            trust("/plugins/b.jar", "hash-b")
        }

        val reloaded = newRepository().apply { load() }
        assertEquals("hash-a", reloaded.trustedEntry("/plugins/a.jar")?.sha256)
        assertEquals("hash-b", reloaded.trustedEntry("/plugins/b.jar")?.sha256)
    }

    @Test
    fun `revoke removes the entry and persists`() = runBlocking {
        newRepository().apply {
            trust("/plugins/a.jar", "hash-a")
            revoke("/plugins/a.jar")
        }

        val reloaded = newRepository().apply { load() }
        assertNull(reloaded.trustedEntry("/plugins/a.jar"))
    }

    @Test
    fun `a tampered registry is rejected wholesale`() = runBlocking {
        newRepository().trust("/plugins/a.jar", "hash-a")

        // Simulate a malicious process rewriting the file: swap in a different hash while leaving the
        // recorded signature untouched. With a key present the signature no longer matches → INVALID.
        val registryFile = AppDataDirectoryProvider().getTrustRegistryFile()
        registryFile.writeText(registryFile.readText().replace("hash-a", "forged-hash"))

        val reloaded = newRepository().apply { load() }
        assertNull(reloaded.trustedEntry("/plugins/a.jar"))
    }

    @Test
    fun `a signed registry with its signature stripped is rejected`() = runBlocking {
        // The core attack: with a key present, an attacker rewrites trusted-plugins.json and drops the
        // signature it cannot produce. A key exists, so a missing signature must be rejected wholesale.
        newRepository().trust("/plugins/a.jar", "hash-a")

        val registryFile = AppDataDirectoryProvider().getTrustRegistryFile()
        val stripped = registryFile.readText().replace(Regex("\"signature\"\\s*:\\s*\"[^\"]*\""), "\"signature\": null")
        registryFile.writeText(stripped)

        val reloaded = newRepository().apply { load() }
        assertNull(reloaded.trustedEntry("/plugins/a.jar"))
    }

    @Test
    fun `no signing key loads the registry unverified and never provisions a key`() = runBlocking {
        // Signing off (no key): the registry is written and read unsigned, and merely reading it must
        // never bring a key into existence.
        val signer = FakeTrustRegistrySigner(keyPresent = false)
        newRepository(signer).trust("/plugins/a.jar", "hash-a")

        val reloaded = newRepository(signer).apply { load() }
        assertEquals("hash-a", reloaded.trustedEntry("/plugins/a.jar")?.sha256)
        assertFalse(signer.hasKey(), "reading/verifying must not provision a key")
    }

    @Test
    fun `registry is loaded without verification when the credential store is unavailable`() = runBlocking {
        newRepository().trust("/plugins/a.jar", "hash-a")

        val reloaded = newRepository(FakeTrustRegistrySigner(available = false)).apply { load() }
        assertEquals("hash-a", reloaded.trustedEntry("/plugins/a.jar")?.sha256)
    }

    @Test
    fun `resign signs a previously unsigned registry so it verifies once a key exists`() = runBlocking {
        // Approve while signing is off (no key) → the registry is written unsigned but kept in memory.
        val signer = FakeTrustRegistrySigner(keyPresent = false)
        val repository = newRepository(signer).apply { trust("/plugins/a.jar", "hash-a") }

        // A key is provisioned (as setSigningEnabled(true) does); re-signing rewrites the in-memory
        // entries with a signature, so a fresh signing-on load verifies instead of rejecting.
        signer.provisionKey()
        repository.resign()

        val reloaded = newRepository(FakeTrustRegistrySigner(keyPresent = true)).apply { load() }
        assertEquals("hash-a", reloaded.trustedEntry("/plugins/a.jar")?.sha256)
    }
}
