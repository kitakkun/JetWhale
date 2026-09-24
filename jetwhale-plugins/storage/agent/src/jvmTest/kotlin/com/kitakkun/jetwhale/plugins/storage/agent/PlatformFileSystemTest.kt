package com.kitakkun.jetwhale.plugins.storage.agent

import com.kitakkun.jetwhale.plugins.storage.protocol.FileEntry
import java.io.File
import java.io.IOException
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class PlatformFileSystemTest {
    private val directory: File = Files.createTempDirectory("storage-agent-test").toFile()

    @AfterTest
    fun cleanUp() {
        directory.deleteRecursively()
    }

    @Test
    fun `a listing tells files from directories and reports sizes`() {
        File(directory, "cache").mkdir()
        File(directory, "notes.txt").writeText("hello")

        val entries = listDirectoryEntries(directory.path).associateBy(FileEntry::name)

        assertEquals(true, entries.getValue("cache").isDirectory)
        assertEquals(5, entries.getValue("notes.txt").sizeBytes)
    }

    @Test
    fun `a read starts at the offset and stops at the end of the file`() {
        File(directory, "notes.txt").writeText("hello")

        assertEquals("llo", readFileBytes("${directory.path}/notes.txt", offset = 2, maxBytes = 100).decodeToString())
    }

    @Test
    fun `a read past the end is empty`() {
        File(directory, "notes.txt").writeText("hello")

        assertEquals(0, readFileBytes("${directory.path}/notes.txt", offset = 10, maxBytes = 100).size)
    }

    @Test
    fun `deleting a directory removes everything in it`() {
        File(directory, "cache/images").mkdirs()
        File(directory, "cache/images/a.png").writeBytes(byteArrayOf(1, 2, 3))

        deleteRecursively("${directory.path}/cache")

        assertFalse(File(directory, "cache").exists())
    }

    @Test
    fun `listing a file is an error`() {
        File(directory, "notes.txt").writeText("hello")

        assertFailsWith<IOException> { listDirectoryEntries("${directory.path}/notes.txt") }
    }
}
