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
    private fun newRepository() = DefaultPluginTrustRepository(AppDataDirectoryProvider())

    @Test
    fun trustedEntriesSurviveAReadBackFromDisk() = runBlocking {
        // Regression guard for the original bug: persisting the registry must actually write valid
        // JSON. It previously threw a SerializationException at runtime (no serializer was generated
        // for the @Serializable model because this module lacks the serialization plugin); the
        // mutation swallowed it, so approving an untrusted jar silently did nothing.
        newRepository().apply {
            trust("/plugins/a.jar", "hash-a")
            trust("/plugins/b.jar", "hash-b")
        }

        val reloaded = newRepository()
        assertEquals("hash-a", reloaded.trustedEntry("/plugins/a.jar")?.sha256)
        assertEquals("hash-b", reloaded.trustedEntry("/plugins/b.jar")?.sha256)
    }

    @Test
    fun revokeRemovesTheEntryAndPersists() = runBlocking {
        newRepository().apply {
            trust("/plugins/a.jar", "hash-a")
            revoke("/plugins/a.jar")
        }

        assertNull(newRepository().trustedEntry("/plugins/a.jar"))
    }
}
