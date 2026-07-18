package com.kitakkun.jetwhale.host.data.plugin

import com.kitakkun.jetwhale.host.data.AppDataDirectoryProvider
import kotlinx.coroutines.runBlocking
import java.io.File
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

    // A fresh repository each time so we exercise the on-disk read path, not just the in-memory cache.
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
        override fun sign(payload: String): String? = if (available) "signed:${payload.hashCode()}" else null

        override fun verify(payload: String, signature: String?): TrustRegistrySigner.Verification = when {
            !available -> TrustRegistrySigner.Verification.UNAVAILABLE
            signature == null -> if (keyPreexisted) TrustRegistrySigner.Verification.INVALID else TrustRegistrySigner.Verification.VALID
            signature == "signed:${payload.hashCode()}" -> TrustRegistrySigner.Verification.VALID
            else -> TrustRegistrySigner.Verification.INVALID
        }
    }

    @Test
    fun `trusted entries survive a read back from disk`() = runBlocking {
        newRepository().apply {
            trust("/plugins/a.jar", "hash-a")
            trust("/plugins/b.jar", "hash-b")
        }

        val reloaded = newRepository()
        assertEquals("hash-a", reloaded.trustedEntry("/plugins/a.jar")?.sha256)
        assertEquals("hash-b", reloaded.trustedEntry("/plugins/b.jar")?.sha256)
    }

    @Test
    fun `revoke removes the entry and persists`() = runBlocking {
        newRepository().apply {
            trust("/plugins/a.jar", "hash-a")
            revoke("/plugins/a.jar")
        }

        assertNull(newRepository().trustedEntry("/plugins/a.jar"))
    }

    @Test
    fun `a tampered registry is rejected wholesale`() = runBlocking {
        newRepository().trust("/plugins/a.jar", "hash-a")

        // Simulate a malicious process rewriting the file: swap in a different hash while leaving
        // the recorded signature untouched.
        val registryFile = AppDataDirectoryProvider().getTrustRegistryFile()
        registryFile.writeText(registryFile.readText().replace("hash-a", "forged-hash"))

        assertNull(newRepository().trustedEntry("/plugins/a.jar"))
    }

    @Test
    fun `an unsigned registry is rejected when a signing key already exists`() = runBlocking {
        newRepository(FakeTrustRegistrySigner(available = false)).trust("/plugins/a.jar", "hash-a")

        assertNull(newRepository(FakeTrustRegistrySigner(keyPreexisted = true)).trustedEntry("/plugins/a.jar"))
    }

    @Test
    fun `an unsigned registry is accepted on first run with a new signing key`() = runBlocking {
        // Upgrade path: the registry predates registry signing, so no signature exists yet. The
        // key being brand new proves no signed registry could ever have been written.
        newRepository(FakeTrustRegistrySigner(available = false)).trust("/plugins/a.jar", "hash-a")

        val reloaded = newRepository(FakeTrustRegistrySigner(keyPreexisted = false))
        assertEquals("hash-a", reloaded.trustedEntry("/plugins/a.jar")?.sha256)
    }

    @Test
    fun `registry is loaded without verification when the credential store is unavailable`() = runBlocking {
        newRepository().trust("/plugins/a.jar", "hash-a")

        val reloaded = newRepository(FakeTrustRegistrySigner(available = false))
        assertEquals("hash-a", reloaded.trustedEntry("/plugins/a.jar")?.sha256)
    }
}
