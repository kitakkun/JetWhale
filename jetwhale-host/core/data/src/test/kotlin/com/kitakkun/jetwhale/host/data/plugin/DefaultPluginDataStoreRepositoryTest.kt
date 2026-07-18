package com.kitakkun.jetwhale.host.data.plugin

import com.kitakkun.jetwhale.host.data.AppDataDirectoryProvider
import com.kitakkun.jetwhale.host.sdk.InternalJetWhaleHostApi
import com.kitakkun.jetwhale.host.sdk.JetWhaleHostPlugin
import com.kitakkun.jetwhale.host.sdk.get
import com.kitakkun.jetwhale.host.sdk.getFlow
import com.kitakkun.jetwhale.host.sdk.put
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(InternalJetWhaleHostApi::class)
class DefaultPluginDataStoreRepositoryTest {
    private val originalUserHome: String? = System.getProperty("user.home")

    @AfterTest
    fun restoreUserHome() {
        if (originalUserHome != null) System.setProperty("user.home", originalUserHome)
    }

    // Points AppDataDirectoryProvider at a throwaway home so each test gets its own plugin-data tree.
    private fun newRepository(): DefaultPluginDataStoreRepository {
        val tempHome = Files.createTempDirectory("jetwhale-plugin-data-test").toString()
        System.setProperty("user.home", tempHome)
        return DefaultPluginDataStoreRepository(AppDataDirectoryProvider())
    }

    @Test
    fun `stores and reads back primitive and structured values`() = runBlocking {
        val storage = newRepository().storageFor("plugin.a")

        // Primitives plus structured data. Collections resolve their serializer from the type, just
        // as a plugin's own @Serializable classes do (plugins apply the serialization compiler plugin).
        storage.put("filter", "errors-only")
        storage.put("pinned", listOf("a", "b", "c"))
        storage.put("counts", mapOf("x" to 1, "y" to 2))

        assertEquals("errors-only", storage.get<String>("filter"))
        assertEquals(listOf("a", "b", "c"), storage.get<List<String>>("pinned"))
        assertEquals(mapOf("x" to 1, "y" to 2), storage.get<Map<String, Int>>("counts"))
    }

    @Test
    fun `data is isolated between plugins`() = runBlocking {
        val repository = newRepository()
        val a = repository.storageFor("plugin.a")
        val b = repository.storageFor("plugin.b")

        a.put("secret", "owned-by-a")

        // plugin.b can only ever obtain its own scoped handle; it has no way to name plugin.a's id,
        // and even the same key resolves to a separate, empty store.
        assertEquals("owned-by-a", a.get<String>("secret"))
        assertNull(b.get<String>("secret"))
        assertFalse(b.contains("secret"))
    }

    @Test
    fun `remove and clear delete only the targeted data`() = runBlocking {
        val storage = newRepository().storageFor("plugin.a")
        storage.put("keep", 1)
        storage.put("drop", 2)

        storage.remove("drop")
        assertTrue(storage.contains("keep"))
        assertFalse(storage.contains("drop"))

        storage.clear()
        assertFalse(storage.contains("keep"))
        assertEquals(emptySet(), storage.keysFlow.first())
    }

    @Test
    fun `getFlow emits current value and reflects updates`() = runBlocking {
        val storage = newRepository().storageFor("plugin.a")

        assertNull(storage.getFlow<Int>("count").first())
        storage.put("count", 42)
        assertEquals(42, storage.getFlow<Int>("count").first())
    }

    @Test
    fun `storage migration hook runs once with the stored version`() = runBlocking {
        val repository = newRepository()
        // Seed pre-versioning (v1) data directly through the raw repository storage.
        repository.storageFor("plugin.migrating").put("draft", "hello")

        val plugin = object : JetWhaleHostPlugin() {
            override val storageVersion: Int = 2
            var migratedFrom: Int? = null

            override suspend fun onStorageMigrate(fromVersion: Int) {
                migratedFrom = fromVersion
                val old = storage.get<String>("draft")
                if (old != null) {
                    storage.put("draft-input", old)
                    storage.remove("draft")
                }
            }

            suspend fun readDraftInput(): String? = storage.get<String>("draft-input")
            suspend fun readDraft(): String? = storage.get<String>("draft")
        }
        plugin.bindStorage(repository.storageFor("plugin.migrating"))

        assertEquals("hello", plugin.readDraftInput())
        assertNull(plugin.readDraft())
        assertEquals(1, plugin.migratedFrom)

        // A second instance with the same version must not migrate again.
        val second = object : JetWhaleHostPlugin() {
            override val storageVersion: Int = 2
            var migrationRan = false

            override suspend fun onStorageMigrate(fromVersion: Int) {
                migrationRan = true
            }

            suspend fun readDraftInput(): String? = storage.get<String>("draft-input")
        }
        second.bindStorage(repository.storageFor("plugin.migrating"))
        assertEquals("hello", second.readDraftInput())
        assertFalse(second.migrationRan)
    }

    @Test
    fun `fresh store skips migration and version key stays hidden`() = runBlocking {
        val repository = newRepository()
        val plugin = object : JetWhaleHostPlugin() {
            override val storageVersion: Int = 3
            var migrationRan = false

            override suspend fun onStorageMigrate(fromVersion: Int) {
                migrationRan = true
            }

            suspend fun writeAndListKeys(): Set<String> {
                storage.put("only-key", 1)
                return storage.keysFlow.first()
            }
        }
        plugin.bindStorage(repository.storageFor("plugin.fresh"))

        assertEquals(setOf("only-key"), plugin.writeAndListKeys())
        assertFalse(plugin.migrationRan)
    }
}
