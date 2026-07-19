package com.kitakkun.jetwhale.host.data.plugin

import com.kitakkun.jetwhale.host.data.AppDataDirectoryProvider
import com.kitakkun.jetwhale.host.model.DebuggerSettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
    // cache. Signing defaults to enabled so the signer-backed behavior stays under test; the
    // opt-in-off path is exercised explicitly below.
    private fun newRepository(
        signer: TrustRegistrySigner = FakeTrustRegistrySigner(),
        settings: DebuggerSettingsRepository = FakeDebuggerSettingsRepository(signEnabled = true),
    ) = DefaultPluginTrustRepository(AppDataDirectoryProvider(), signer, settings)

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

        override fun verify(payload: String, signature: String?): TrustRegistrySigner.Verification =
            error("signer must not be invoked when signing is disabled")
    }

    /** Minimal settings stand-in exposing only the opt-in flag the trust repository reads. */
    private class FakeDebuggerSettingsRepository(signEnabled: Boolean) : DebuggerSettingsRepository {
        private var signPluginTrustRegistry = signEnabled

        override val signPluginTrustRegistryFlow: StateFlow<Boolean> = MutableStateFlow(signEnabled)
        override val adbAutoPortMappingEnabledFlow: StateFlow<Boolean> = MutableStateFlow(false)
        override val checkForUpdatesOnStartupFlow: StateFlow<Boolean> = MutableStateFlow(true)
        override val persistDataFlow: StateFlow<Boolean> = MutableStateFlow(false)
        override val serverPortFlow: StateFlow<Int> = MutableStateFlow(0)
        override val mcpServerPortFlow: StateFlow<Int> = MutableStateFlow(0)
        override val wssPortFlow: StateFlow<Int> = MutableStateFlow(0)
        override val wssEnabledFlow: StateFlow<Boolean> = MutableStateFlow(true)

        override suspend fun readSignPluginTrustRegistry(): Boolean = signPluginTrustRegistry
        override suspend fun updateSignPluginTrustRegistry(enabled: Boolean) {
            signPluginTrustRegistry = enabled
        }

        override suspend fun readServerPort(): Int = 0
        override suspend fun readMcpServerPort(): Int = 0
        override suspend fun readWssPort(): Int = 0
        override suspend fun updatePersistData(enabled: Boolean) = Unit
        override suspend fun updateAdbAutoPortMappingEnabled(enabled: Boolean) = Unit
        override suspend fun readCheckForUpdatesOnStartup(): Boolean = true
        override suspend fun updateCheckForUpdatesOnStartup(enabled: Boolean) = Unit
        override suspend fun updateServerPort(port: Int) = Unit
        override suspend fun updateMcpServerPort(port: Int) = Unit
        override suspend fun updateWssPort(port: Int) = Unit
        override suspend fun updateWssEnabled(enabled: Boolean) = Unit
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

    @Test
    fun `the signer is never consulted when signing is disabled`() = runBlocking {
        val settings = FakeDebuggerSettingsRepository(signEnabled = false)
        newRepository(signer = ThrowingTrustRegistrySigner(), settings = settings)
            .trust("/plugins/a.jar", "hash-a")

        val reloaded = newRepository(signer = ThrowingTrustRegistrySigner(), settings = settings)
        assertEquals("hash-a", reloaded.trustedEntry("/plugins/a.jar")?.sha256)
    }

    @Test
    fun `a registry written unsigned still loads once signing is enabled`() = runBlocking {
        // Approve while signing is off (registry written without a signature), then turn signing on:
        // the previously unsigned registry is treated as an upgrade and still loads.
        newRepository(settings = FakeDebuggerSettingsRepository(signEnabled = false))
            .trust("/plugins/a.jar", "hash-a")

        val reloaded = newRepository(
            signer = FakeTrustRegistrySigner(keyPreexisted = false),
            settings = FakeDebuggerSettingsRepository(signEnabled = true),
        )
        assertEquals("hash-a", reloaded.trustedEntry("/plugins/a.jar")?.sha256)
    }
}
