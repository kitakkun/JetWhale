package com.kitakkun.jetwhale.plugins.storage.host

import com.kitakkun.jetwhale.annotations.ExperimentalJetWhaleApi
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArgumentException
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArguments
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpCommand
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpContent
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpResult
import com.kitakkun.jetwhale.plugins.storage.protocol.KeyValueEntry
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalJetWhaleApi::class)
class StorageMcpCommandsTest {
    private val binary = byteArrayOf(0x53, 0x51, 0x4C, 0x00, 0x01, 0x02)
    private val client = FakeStorageClient(
        directories = mutableMapOf(
            location("Files") to listOf(directoryEntry("datastore"), fileEntry("notes.txt", sizeBytes = 5)),
            location("Files", "datastore") to listOf(fileEntry("empty.preferences_pb", sizeBytes = 5)),
        ),
        files = mapOf(
            location("Files", "notes.txt") to "hello".encodeToByteArray(),
            location("Files", "app.db") to binary,
            location("Files", "datastore", "empty.preferences_pb") to ByteArray(0),
        ),
        stores = mutableMapOf("settings" to listOf(KeyValueEntry(key = "onboarded", value = "true", type = "Boolean"))),
    )

    @Test
    fun `listDirectory splits the path into the segments below the root`() {
        val result = ListDirectoryCommand(client).structuredAnswer(
            buildJsonObject {
                put("root", "Files")
                put("path", "/datastore/")
            },
        )

        assertEquals(listOf("empty.preferences_pb"), result.getValue("entries").jsonArray.map { it.jsonObject.getValue("name").jsonPrimitive.content })
    }

    @Test
    fun `readFile returns text as text`() {
        val result = ReadFileCommand(client).structuredAnswer(file("notes.txt"))

        assertEquals("utf-8", result.getValue("encoding").jsonPrimitive.content)
        assertEquals("hello", result.getValue("content").jsonPrimitive.content)
    }

    @Test
    fun `readFile returns binary content as Base64`() {
        val result = ReadFileCommand(client).structuredAnswer(file("app.db"))

        assertEquals("base64", result.getValue("encoding").jsonPrimitive.content)
        assertEquals(binary.toList(), Base64.decode(result.getValue("content").jsonPrimitive.content).toList())
    }

    @Test
    fun `readFile decodes a preferences DataStore file it read whole`() {
        val result = ReadFileCommand(client).structuredAnswer(file("datastore/empty.preferences_pb"))

        assertEquals(0, result.getValue("preferences").jsonArray.size)
    }

    @Test
    fun `readFile refuses to read a root`() {
        assertFailsWith<JetWhaleMcpArgumentException> { ReadFileCommand(client).structuredAnswer(file("")) }
    }

    @Test
    fun `readFile passes the page the caller asked for`() {
        ReadFileCommand(client).structuredAnswer(
            buildJsonObject {
                put("root", "Files")
                put("path", "notes.txt")
                put("offset", 2)
                put("maxBytes", 2)
            },
        )

        assertEquals(Triple(location("Files", "notes.txt"), 2L, 2), client.fileReads.single())
    }

    @Test
    fun `removeKeyValue removes the key from the store`() {
        RemoveKeyValueCommand(client).structuredAnswer(
            buildJsonObject {
                put("store", "settings")
                put("key", "onboarded")
            },
        )

        val result = ReadKeyValueStoreCommand(client).structuredAnswer(buildJsonObject { put("store", "settings") })
        assertEquals(0, result.getValue("entries").jsonArray.size)
    }

    @Test
    fun `listLocations names the roots and stores the other tools take`() {
        val result = ListStorageLocationsCommand(client).structuredAnswer()

        assertEquals("Files", result.getValue("fileRoots").jsonArray.single().jsonObject.getValue("name").jsonPrimitive.content)
        assertFalse(result.getValue("keyValueStores").jsonArray.isEmpty())
    }

    @Test
    fun `measureDirectory adds up everything below the directory`() {
        val result = MeasureDirectoryCommand(client).structuredAnswer(buildJsonObject { put("root", "Files") })

        assertEquals(10, result.getValue("totalSizeBytes").jsonPrimitive.content.toLong())
        assertEquals(1, result.getValue("directoryCount").jsonPrimitive.content.toInt())
    }

    @Test
    fun `hashFile returns the SHA-256 of the whole file`() {
        val result = HashFileCommand(client).structuredAnswer(file("notes.txt"))

        assertEquals("2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824", result.getValue("sha256").jsonPrimitive.content)
    }

    @Test
    fun `hashFile reports a file it cannot read as a failed call`() {
        val result = HashFileCommand(client).answer(file("missing.txt"))

        assertTrue(result.isError)
        assertEquals("'missing.txt' is not a file", result.text())
    }

    @Test
    fun `a reply the app marks as failed is a failed call`() {
        val listing = ListDirectoryCommand(client).answer(file("notes.txt"))
        val read = ReadFileCommand(client).answer(file("missing.txt"))
        val store = ReadKeyValueStoreCommand(client).answer(buildJsonObject { put("store", "missing") })

        assertEquals(listOf(true, true, true), listOf(listing.isError, read.isError, store.isError))
        assertEquals("'notes.txt' is not a directory", listing.text())
    }

    @Test
    fun `a successful reply leaves out the error the app did not report`() {
        val listing = ListDirectoryCommand(client).structuredAnswer(buildJsonObject { put("root", "Files") })

        assertFalse("error" in listing)
    }

    @Test
    fun `deleteFileEntry answers ok once the app deleted the file`() {
        val result = DeleteFileEntryCommand(client).structuredAnswer(file("notes.txt"))

        assertEquals("true", result.getValue("ok").jsonPrimitive.content)
        assertEquals(listOf(location("Files", "notes.txt")), client.deleted)
    }

    private fun file(path: String): JsonObject = buildJsonObject {
        put("root", "Files")
        put("path", path)
    }
}

@OptIn(ExperimentalJetWhaleApi::class)
private fun JetWhaleMcpCommand.answer(arguments: JsonObject): JetWhaleMcpResult = runBlocking { run(JetWhaleMcpArguments(arguments)) }

@OptIn(ExperimentalJetWhaleApi::class)
private fun JetWhaleMcpCommand.structuredAnswer(arguments: JsonObject = buildJsonObject { }): JsonObject = checkNotNull(answer(arguments).structuredContent)

@OptIn(ExperimentalJetWhaleApi::class)
private fun JetWhaleMcpResult.text(): String = (content.single() as JetWhaleMcpContent.Text).text
