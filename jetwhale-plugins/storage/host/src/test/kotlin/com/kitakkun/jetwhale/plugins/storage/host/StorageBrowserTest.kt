package com.kitakkun.jetwhale.plugins.storage.host

import com.kitakkun.jetwhale.plugins.storage.protocol.KeyValueEntry
import com.kitakkun.jetwhale.plugins.storage.protocol.KeyValueStoreContent
import com.kitakkun.jetwhale.plugins.storage.protocol.MAX_FILE_READ_BYTES
import com.kitakkun.jetwhale.plugins.storage.protocol.StorageLocations
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StorageBrowserTest {
    private val client = FakeStorageClient(
        directories = mutableMapOf(
            location("Files") to listOf(directoryEntry("datastore"), fileEntry("notes.txt", sizeBytes = 5)),
            location("Files", "datastore") to listOf(fileEntry("settings.preferences_pb", sizeBytes = 5)),
        ),
        files = mapOf(location("Files", "notes.txt") to "hello".encodeToByteArray()),
        stores = mutableMapOf(
            "first" to listOf(KeyValueEntry(key = "a", value = "1", type = "Int")),
            "second" to emptyList(),
        ),
    )

    // The fake answers without suspending, so every launched call has finished by the time launch returns.
    private val browser = StorageBrowser(client, CoroutineScope(Dispatchers.Unconfined))

    @Test
    fun `loading opens the first key-value store`() {
        runBlocking { browser.load() }

        assertEquals("first", browser.selectedStore)
        assertEquals(1, browser.storeContent?.entries?.size)
    }

    @Test
    fun `a store read that finishes after another store was picked is dropped`() {
        val firstStoreGate = CompletableDeferred<Unit>()
        val slowFirstStore = object : StorageClient by client {
            override suspend fun readKeyValueStore(storeName: String): KeyValueStoreContent {
                if (storeName == "first") firstStoreGate.await()
                return client.readKeyValueStore(storeName)
            }
        }
        val browser = StorageBrowser(slowFirstStore, CoroutineScope(Dispatchers.Unconfined))

        browser.selectStore("first")
        browser.selectStore("second")
        firstStoreGate.complete(Unit)

        assertEquals(emptyList(), browser.storeContent?.entries)
    }

    @Test
    fun `a reload that moves the selection to another store shows nothing until that store is read`() {
        var firstStoreRemoved = false
        val secondStoreGate = CompletableDeferred<Unit>()
        val changingApp = object : StorageClient by client {
            override suspend fun locations(): StorageLocations {
                val locations = client.locations()
                if (!firstStoreRemoved) return locations
                return locations.copy(keyValueStores = locations.keyValueStores.filterNot { it.name == "first" })
            }

            override suspend fun readKeyValueStore(storeName: String): KeyValueStoreContent {
                if (storeName == "second") secondStoreGate.await()
                return client.readKeyValueStore(storeName)
            }
        }
        val reloadingBrowser = StorageBrowser(changingApp, CoroutineScope(Dispatchers.Unconfined))
        runBlocking { reloadingBrowser.load() }
        firstStoreRemoved = true

        reloadingBrowser.refresh()

        assertEquals("second", reloadingBrowser.selectedStore)
        assertNull(reloadingBrowser.storeContent)
        secondStoreGate.complete(Unit)
    }

    @Test
    fun `saving a file larger than one read copies all of it`() {
        val large = ByteArray(2 * MAX_FILE_READ_BYTES + 123) { (it % 251).toByte() }
        val app = FakeStorageClient(
            directories = mutableMapOf(location("Files") to listOf(fileEntry("large.bin", sizeBytes = large.size.toLong()))),
            files = mapOf(location("Files", "large.bin") to large),
            stores = mutableMapOf(),
        )
        val target = File.createTempFile("storage-save", ".bin").apply { deleteOnExit() }

        StorageBrowser(app, CoroutineScope(Dispatchers.Unconfined)).saveFile(location("Files", "large.bin"), target)

        assertContentEquals(large, target.readBytes())
        assertEquals(3, app.fileReads.size)
    }

    @Test
    fun `a failed save leaves no partial file behind`() {
        val target = File.createTempFile("storage-save", ".bin")

        browser.saveFile(location("Files", "missing.bin"), target)

        assertFalse(target.exists())
        assertEquals(true, browser.status?.isError)
    }

    @Test
    fun `expanding a subtree opens every directory below it`() {
        runBlocking { browser.load() }

        browser.toggleSubtree(location("Files"))

        assertEquals(listOf("Files", "datastore", "settings.preferences_pb", "notes.txt"), browser.treeRows.map { it.location.name })
    }

    @Test
    fun `collapsing a subtree closes the directories below it too`() {
        runBlocking { browser.load() }
        browser.toggleSubtree(location("Files"))

        browser.toggleSubtree(location("Files"))
        browser.toggleDirectory(location("Files"))

        assertEquals(listOf("Files", "datastore", "notes.txt"), browser.treeRows.map { it.location.name })
    }

    @Test
    fun `expanding a very large subtree stops at the entry limit`() {
        // 30 directories of 30 directories each: 930 entries, well past the limit.
        val directories = mutableMapOf(location("Cache") to (0 until 30).map { directoryEntry("d$it") })
        (0 until 30).forEach { outer -> directories[location("Cache", "d$outer")] = (0 until 30).map { directoryEntry("e$it") } }
        val wideApp = FakeStorageClient(directories = directories, files = emptyMap(), stores = mutableMapOf())
        val wideBrowser = StorageBrowser(wideApp, CoroutineScope(Dispatchers.Unconfined))
        runBlocking { wideBrowser.load() }

        wideBrowser.toggleSubtree(location("Cache"))

        val shown = wideBrowser.treeRows.size
        assertTrue(shown in 500..600, "shown $shown rows")
        assertEquals(false, wideBrowser.status?.isError)
    }

    @Test
    fun `selecting a collapsed directory opens it`() {
        runBlocking { browser.load() }

        browser.select(browser.treeRows.single())

        assertEquals(listOf("Files", "datastore", "notes.txt"), browser.treeRows.map { it.location.name })
    }

    @Test
    fun `selecting a file reads it for the preview`() {
        runBlocking { browser.load() }
        browser.toggleDirectory(location("Files"))

        browser.select(browser.treeRows.first { it.location.name == "notes.txt" })

        assertEquals("hello", browser.loadedFile?.bytes?.decodeToString())
    }

    @Test
    fun `deleting a directory clears a selection inside it and relists its parent`() {
        runBlocking { browser.load() }
        browser.toggleDirectory(location("Files"))
        browser.toggleDirectory(location("Files", "datastore"))
        browser.select(browser.treeRows.first { it.location.name == "settings.preferences_pb" })

        browser.delete(location("Files", "datastore"))

        assertNull(browser.selectedRow)
        assertEquals(listOf("Files", "notes.txt"), browser.treeRows.map { it.location.name })
    }
}
