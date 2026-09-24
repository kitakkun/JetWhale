package com.kitakkun.jetwhale.plugins.storage.host

import com.kitakkun.jetwhale.annotations.ExperimentalJetWhaleApi
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArgumentException
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArguments
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpCommand
import com.kitakkun.jetwhale.plugins.storage.protocol.KeyValueEntry
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
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
        val result = ListDirectoryCommand(client).run(
            buildJsonObject {
                put("root", "Files")
                put("path", "/datastore/")
            },
        )

        assertEquals(listOf("empty.preferences_pb"), result.getValue("entries").jsonArray.map { it.jsonObject.getValue("name").jsonPrimitive.content })
    }

    @Test
    fun `readFile returns text as text`() {
        val result = ReadFileCommand(client).run(file("notes.txt"))

        assertEquals("utf-8", result.getValue("encoding").jsonPrimitive.content)
        assertEquals("hello", result.getValue("content").jsonPrimitive.content)
    }

    @Test
    fun `readFile returns binary content as Base64`() {
        val result = ReadFileCommand(client).run(file("app.db"))

        assertEquals("base64", result.getValue("encoding").jsonPrimitive.content)
        assertEquals(binary.toList(), Base64.decode(result.getValue("content").jsonPrimitive.content).toList())
    }

    @Test
    fun `readFile decodes a preferences DataStore file it read whole`() {
        val result = ReadFileCommand(client).run(file("datastore/empty.preferences_pb"))

        assertEquals(0, result.getValue("preferences").jsonArray.size)
    }

    @Test
    fun `readFile refuses to read a root`() {
        assertFailsWith<JetWhaleMcpArgumentException> { ReadFileCommand(client).run(file("")) }
    }

    @Test
    fun `readFile passes the page the caller asked for`() {
        ReadFileCommand(client).run(
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
        RemoveKeyValueCommand(client).run(
            buildJsonObject {
                put("store", "settings")
                put("key", "onboarded")
            },
        )

        val result = ReadKeyValueStoreCommand(client).run(buildJsonObject { put("store", "settings") })
        assertEquals(0, result.getValue("entries").jsonArray.size)
    }

    @Test
    fun `listLocations names the roots and stores the other tools take`() {
        val result = ListStorageLocationsCommand(client).run()

        assertEquals("Files", result.getValue("fileRoots").jsonArray.single().jsonObject.getValue("name").jsonPrimitive.content)
        assertFalse(result.getValue("keyValueStores").jsonArray.isEmpty())
    }

    @Test
    fun `measureDirectory adds up everything below the directory`() {
        val result = MeasureDirectoryCommand(client).run(buildJsonObject { put("root", "Files") })

        assertEquals(10, result.getValue("totalSizeBytes").jsonPrimitive.content.toLong())
        assertEquals(1, result.getValue("directoryCount").jsonPrimitive.content.toInt())
    }

    @Test
    fun `hashFile returns the SHA-256 of the whole file`() {
        val result = HashFileCommand(client).run(file("notes.txt"))

        assertEquals("2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824", result.getValue("sha256").jsonPrimitive.content)
    }

    @Test
    fun `hashFile reports a file it cannot read`() {
        val result = HashFileCommand(client).run(file("missing.txt"))

        assertEquals(false, "sha256" in result)
        assertEquals(true, "error" in result)
    }

    private fun file(path: String): JsonObject = buildJsonObject {
        put("root", "Files")
        put("path", path)
    }
}

@OptIn(ExperimentalJetWhaleApi::class)
private fun JetWhaleMcpCommand.run(arguments: JsonObject = buildJsonObject { }): JsonObject = runBlocking {
    Json.parseToJsonElement(execute(JetWhaleMcpArguments(arguments))).jsonObject
}
