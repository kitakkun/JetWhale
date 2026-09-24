package com.kitakkun.jetwhale.plugins.storage.agent

import com.kitakkun.jetwhale.plugins.storage.protocol.DirectoryMeasurement
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
    fun `a symbolic link that leads outside the root is refused`() {
        val root = File(directory, "root").apply { mkdir() }
        val outside = File(directory, "outside").apply { mkdir() }
        Files.createSymbolicLink(File(root, "escape").toPath(), outside.toPath())

        assertFailsWith<IllegalArgumentException> { FileRoot(name = "Root", path = root.path).resolve(listOf("escape")) }
    }

    @Test
    fun `deleting a directory removes a link inside it but not what the link points to`() {
        val outside = File(directory, "outside").apply { mkdir() }
        File(outside, "keep.txt").writeText("keep")
        File(directory, "cache").mkdir()
        Files.createSymbolicLink(File(directory, "cache/link").toPath(), outside.toPath())

        deleteRecursively("${directory.path}/cache")

        assertFalse(File(directory, "cache").exists())
        assertEquals("keep", File(outside, "keep.txt").readText())
    }

    @Test
    fun `a listing marks a symbolic link and names its target`() {
        val target = File(directory, "target.txt").apply { writeText("hello") }
        Files.createSymbolicLink(File(directory, "link").toPath(), target.toPath())

        val entries = listDirectoryEntries(directory.path).associateBy(FileEntry::name)

        assertEquals(true, entries.getValue("link").isSymbolicLink)
        assertEquals(target.canonicalPath, entries.getValue("link").linkTarget)
        assertEquals(false, entries.getValue("target.txt").isSymbolicLink)
        assertEquals(true, entries.getValue("target.txt").readable)
        assertEquals(true, entries.getValue("target.txt").writable)
    }

    @Test
    fun `measuring counts everything below and does not follow links`() {
        val outside = File(directory, "outside").apply { mkdir() }
        File(outside, "big.bin").writeBytes(ByteArray(1000))
        File(directory, "cache/images").mkdirs()
        File(directory, "cache/a.txt").writeText("hello")
        File(directory, "cache/images/b.bin").writeBytes(ByteArray(10))
        Files.createSymbolicLink(File(directory, "cache/link").toPath(), outside.toPath())

        val measurement = measureDirectoryTree("${directory.path}/cache", entryLimit = 100)

        assertEquals(DirectoryMeasurement(totalSizeBytes = 15, fileCount = 3, directoryCount = 1, truncated = false, error = null), measurement)
    }

    @Test
    fun `measuring stops at the entry limit`() {
        repeat(10) { File(directory, "f$it").writeText("x") }

        val measurement = measureDirectoryTree(directory.path, entryLimit = 4)

        assertEquals(4, measurement.fileCount)
        assertEquals(true, measurement.truncated)
    }

    @Test
    fun `listing a file is an error`() {
        File(directory, "notes.txt").writeText("hello")

        assertFailsWith<IOException> { listDirectoryEntries("${directory.path}/notes.txt") }
    }
}
