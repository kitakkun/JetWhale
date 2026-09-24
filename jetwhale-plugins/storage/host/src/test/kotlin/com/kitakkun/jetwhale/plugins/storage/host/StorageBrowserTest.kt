package com.kitakkun.jetwhale.plugins.storage.host

import com.kitakkun.jetwhale.plugins.storage.protocol.KeyValueEntry
import com.kitakkun.jetwhale.plugins.storage.protocol.KeyValueStoreContent
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

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
