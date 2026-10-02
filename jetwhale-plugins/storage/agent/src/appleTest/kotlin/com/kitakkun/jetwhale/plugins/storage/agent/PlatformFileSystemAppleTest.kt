package com.kitakkun.jetwhale.plugins.storage.agent

import com.kitakkun.jetwhale.plugins.storage.protocol.FileEntry
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSFileManager
import platform.Foundation.NSString
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSUUID
import platform.Foundation.create
import platform.Foundation.dataUsingEncoding
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
class PlatformFileSystemAppleTest {
    private val directory = "${NSTemporaryDirectory().trimEnd('/')}/storage-agent-test-${NSUUID().UUIDString}"
    private val fileManager = NSFileManager.defaultManager

    init {
        fileManager.createDirectoryAtPath(directory, withIntermediateDirectories = true, attributes = null, error = null)
    }

    @AfterTest
    fun cleanUp() {
        fileManager.removeItemAtPath(directory, error = null)
    }

    @Test
    fun `a listing tells files from directories and reports sizes`() {
        fileManager.createDirectoryAtPath("$directory/cache", withIntermediateDirectories = true, attributes = null, error = null)
        writeText("$directory/notes.txt", "hello")

        val entries = listDirectoryEntries(directory).associateBy(FileEntry::name)

        assertEquals(true, entries.getValue("cache").isDirectory)
        assertEquals(5, entries.getValue("notes.txt").sizeBytes)
    }

    @Test
    fun `a read starts at the offset and stops at the end of the file`() {
        writeText("$directory/notes.txt", "hello")

        assertEquals("llo", readFileBytes("$directory/notes.txt", offset = 2, maxBytes = 100).decodeToString())
        assertEquals(5, fileSize("$directory/notes.txt"))
    }

    @Test
    fun `deleting a directory removes everything in it`() {
        fileManager.createDirectoryAtPath("$directory/cache/images", withIntermediateDirectories = true, attributes = null, error = null)
        writeText("$directory/cache/images/a.txt", "a")

        deleteRecursively("$directory/cache")

        assertFalse(fileManager.fileExistsAtPath("$directory/cache"))
    }

    @Test
    fun `a listing marks a symbolic link and names its target`() {
        writeText("$directory/target.txt", "hello")
        fileManager.createSymbolicLinkAtPath("$directory/link", withDestinationPath = "$directory/target.txt", error = null)

        val entries = listDirectoryEntries(directory).associateBy(FileEntry::name)

        assertEquals(true, entries.getValue("link").isSymbolicLink)
        assertEquals("$directory/target.txt", entries.getValue("link").linkTarget)
        assertEquals(false, entries.getValue("target.txt").isSymbolicLink)
        assertEquals(true, entries.getValue("target.txt").writable)
    }

    @Test
    fun `measuring counts everything below and does not follow links`() {
        fileManager.createDirectoryAtPath("$directory/outside", withIntermediateDirectories = true, attributes = null, error = null)
        writeText("$directory/outside/big.txt", "x".repeat(1000))
        fileManager.createDirectoryAtPath("$directory/cache/images", withIntermediateDirectories = true, attributes = null, error = null)
        writeText("$directory/cache/a.txt", "hello")
        writeText("$directory/cache/images/b.txt", "0123456789")
        fileManager.createSymbolicLinkAtPath("$directory/cache/link", withDestinationPath = "$directory/outside", error = null)

        val measurement = measureDirectoryTree("$directory/cache", entryLimit = 100)

        assertEquals(15, measurement.totalSizeBytes)
        assertEquals(3, measurement.fileCount)
        assertEquals(1, measurement.directoryCount)
    }

    @Test
    fun `listing a missing directory is an error`() {
        assertFailsWith<IllegalStateException> { listDirectoryEntries("$directory/missing") }
    }

    @Test
    fun `an upload in chunks replaces the target only on the last chunk`() {
        writeText("$directory/settings.bin", "old")
        val staging = "$directory/.settings.bin.jetwhale-upload-1"

        receiveUploadChunk(staging, "$directory/settings.bin", offset = 0, bytes = byteArrayOf(1, 2, 3), isLast = false)
        assertEquals("old", readFileBytes("$directory/settings.bin", offset = 0, maxBytes = 100).decodeToString())

        receiveUploadChunk(staging, "$directory/settings.bin", offset = 3, bytes = byteArrayOf(4, 5), isLast = true)

        assertContentEquals(byteArrayOf(1, 2, 3, 4, 5), readFileBytes("$directory/settings.bin", offset = 0, maxBytes = 100))
        assertFalse(fileManager.fileExistsAtPath(staging))
    }

    @Test
    fun `an empty upload writes an empty file`() {
        receiveUploadChunk("$directory/.empty.jetwhale-upload-1", "$directory/empty", offset = 0, bytes = ByteArray(0), isLast = true)

        assertEquals(0, fileSize("$directory/empty"))
    }

    @Test
    fun `an upload never replaces a directory`() {
        fileManager.createDirectoryAtPath("$directory/cache", withIntermediateDirectories = true, attributes = null, error = null)

        assertFailsWith<IllegalStateException> {
            receiveUploadChunk("$directory/.cache.jetwhale-upload-1", "$directory/cache", offset = 0, bytes = byteArrayOf(1), isLast = true)
        }
        assertFalse(fileManager.fileExistsAtPath("$directory/.cache.jetwhale-upload-1"))
    }

    private fun writeText(path: String, text: String) {
        val data = NSString.create(string = text).dataUsingEncoding(NSUTF8StringEncoding)
        fileManager.createFileAtPath(path, contents = data, attributes = null)
    }
}
