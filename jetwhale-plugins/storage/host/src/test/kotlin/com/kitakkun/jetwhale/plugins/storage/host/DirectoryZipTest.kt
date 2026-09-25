package com.kitakkun.jetwhale.plugins.storage.host

import com.kitakkun.jetwhale.plugins.storage.protocol.MAX_FILE_READ_BYTES
import kotlinx.coroutines.runBlocking
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DirectoryZipTest {
    private val large = ByteArray(2 * MAX_FILE_READ_BYTES + 7) { (it % 253).toByte() }
    private val app = appWith(
        files = mutableMapOf(
            location("Cache", "cache", "a.txt") to "hello".encodeToByteArray(),
            location("Cache", "cache", "images", "b.bin") to large,
        ),
    )

    private fun appWith(files: MutableMap<FileLocation, ByteArray>) = FakeStorageClient(
        directories = mutableMapOf(
            location("Cache") to listOf(directoryEntry("cache")),
            location("Cache", "cache") to listOf(
                directoryEntry("empty"),
                directoryEntry("images"),
                fileEntry("a.txt", sizeBytes = 5).copy(lastModifiedEpochMillis = 1_700_000_000_000),
                fileEntry("elsewhere", sizeBytes = 0).copy(isSymbolicLink = true, linkTarget = "/data/other"),
            ),
            location("Cache", "cache", "empty") to emptyList(),
            location("Cache", "cache", "images") to listOf(fileEntry("b.bin", sizeBytes = large.size.toLong())),
        ),
        files = files,
        stores = mutableMapOf(),
    )

    @Test
    fun `the zip holds the tree under the directory's own name, empty directories included and links left out`() {
        val entries = unzip(zipOf(location("Cache", "cache")))

        assertEquals(
            setOf("cache/", "cache/a.txt", "cache/empty/", "cache/images/", "cache/images/b.bin"),
            entries.keys,
        )
        assertContentEquals("hello".encodeToByteArray(), entries.getValue("cache/a.txt"))
    }

    @Test
    fun `names that could climb out of the extraction folder are made into plain segments`() {
        assertEquals(".._outside", zipSafeSegment("../outside"))
        assertEquals("a_b", zipSafeSegment("a\\b"))
        assertEquals("C_", zipSafeSegment("C:"))
        assertEquals("_", zipSafeSegment(".."))
        assertEquals("_", zipSafeSegment(""))
        assertEquals("cache", zipSafeSegment("cache"))
    }

    @Test
    fun `siblings whose names sanitize alike are both kept under distinct names`() {
        val clashing = FakeStorageClient(
            directories = mutableMapOf(location("Logs") to listOf(fileEntry("a:b", sizeBytes = 1), fileEntry("a_b", sizeBytes = 1))),
            files = mutableMapOf(location("Logs", "a:b") to "1".encodeToByteArray(), location("Logs", "a_b") to "2".encodeToByteArray()),
            stores = mutableMapOf(),
        )
        val output = ByteArrayOutputStream()

        val error = runBlocking { ZipOutputStream(output).use { clashing.zipDirectory(location("Logs"), it) {} } }

        assertNull(error)
        val entries = unzip(output.toByteArray())
        assertContentEquals("1".encodeToByteArray(), entries.getValue("Logs/a_b"))
        assertContentEquals("2".encodeToByteArray(), entries.getValue("Logs/a_b (2)"))
    }

    @Test
    fun `a file larger than one read arrives whole`() {
        val entries = unzip(zipOf(location("Cache", "cache")))

        assertContentEquals(large, entries.getValue("cache/images/b.bin"))
        assertEquals(3, app.fileReads.count { it.first == location("Cache", "cache", "images", "b.bin") })
    }

    @Test
    fun `an entry keeps the file's modification time`() {
        val zip = zipOf(location("Cache", "cache"))

        val time = ZipInputStream(ByteArrayInputStream(zip)).use { input ->
            generateSequence { input.nextEntry }.first { it.name == "cache/a.txt" }.time
        }
        // DOS timestamps keep two-second precision.
        assertTrue(abs(time - 1_700_000_000_000) < 2_000, "time was $time")
    }

    @Test
    fun `a file the app cannot read stops the zip and is named`() {
        val appMissingAFile = appWith(files = mutableMapOf(location("Cache", "cache", "images", "b.bin") to large))

        val error = runBlocking { ZipOutputStream(ByteArrayOutputStream()).use { appMissingAFile.zipDirectory(location("Cache", "cache"), it) {} } }

        assertNotNull(error)
        assertTrue("cache/a.txt" in error, error)
    }

    @Test
    fun `the running count reaches every file`() {
        var last = 0
        val error = runBlocking { ZipOutputStream(ByteArrayOutputStream()).use { app.zipDirectory(location("Cache", "cache"), it) { count -> last = count } } }

        assertNull(error)
        assertEquals(2, last)
    }

    private fun zipOf(directory: FileLocation): ByteArray {
        val output = ByteArrayOutputStream()
        val error = runBlocking { ZipOutputStream(output).use { app.zipDirectory(directory, it) {} } }
        assertNull(error)
        return output.toByteArray()
    }

    private fun unzip(zip: ByteArray): Map<String, ByteArray> = ZipInputStream(ByteArrayInputStream(zip)).use { input ->
        generateSequence { input.nextEntry }.associate { it.name to input.readBytes() }
    }
}
