package com.kitakkun.jetwhale.plugins.storage.agent

import com.kitakkun.jetwhale.plugins.storage.protocol.DirectoryMeasurement
import com.kitakkun.jetwhale.plugins.storage.protocol.FileEntry
import com.kitakkun.jetwhale.plugins.storage.protocol.MAX_FILE_READ_BYTES
import org.junit.Assume.assumeTrue
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class PlatformFileSystemTest {
    private val directory: File = Files.createTempDirectory("storage-agent-test").toFile()
    private val onWindows = System.getProperty("os.name").orEmpty().startsWith("Windows", ignoreCase = true)

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
    fun `deleting a directory removes a junction inside it but not what the junction points to`() {
        assumeTrue("junctions exist only on Windows", onWindows)
        val outside = File(directory, "outside").apply { mkdir() }
        File(outside, "keep.txt").writeText("keep")
        File(directory, "cache").mkdir()
        createJunction(File(directory, "cache/junction"), outside)

        deleteRecursively("${directory.path}/cache")

        assertFalse(File(directory, "cache").exists())
        assertEquals("keep", File(outside, "keep.txt").readText())
    }

    @Test
    fun `a junction that leads outside the root is refused`() {
        assumeTrue("junctions exist only on Windows", onWindows)
        val root = File(directory, "root").apply { mkdir() }
        val outside = File(directory, "outside").apply { mkdir() }
        createJunction(File(root, "escape"), outside)

        assertFailsWith<IllegalArgumentException> { FileRoot(name = "Root", path = root.path).resolve(listOf("escape")) }
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
    fun `a listing reached through a link to a directory does not mark the files in it as links`() {
        val real = File(directory, "real").apply { mkdir() }
        File(real, "file.txt").writeText("hello")
        val alias = File(directory, "alias")
        Files.createSymbolicLink(alias.toPath(), real.toPath())

        val entry = listDirectoryEntries(alias.path).single()

        assertEquals("file.txt", entry.name)
        assertEquals(false, entry.isSymbolicLink)
    }

    @Test
    fun `a listing marks a symbolic link whose target is missing`() {
        Files.createSymbolicLink(File(directory, "dangling").toPath(), File(directory, "missing.txt").toPath())

        val entry = listDirectoryEntries(directory.path).single()

        assertEquals("dangling", entry.name)
        assertEquals(true, entry.isSymbolicLink)
    }

    @Test
    fun `a link that loops does not fail the listing of its directory`() {
        Files.createSymbolicLink(File(directory, "loop").toPath(), File("loop").toPath())

        assertEquals(listOf("loop"), listDirectoryEntries(directory.path).map(FileEntry::name))
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
    fun `measuring a link to a directory counts the link and does not follow it`() {
        val target = File(directory, "target").apply { mkdir() }
        File(target, "big.bin").writeBytes(ByteArray(1000))
        Files.createSymbolicLink(File(directory, "link").toPath(), target.toPath())

        val measurement = measureDirectoryTree("${directory.path}/link", entryLimit = 100)

        assertEquals(DirectoryMeasurement(totalSizeBytes = 0, fileCount = 1, directoryCount = 0, truncated = false, error = null), measurement)
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

    @Test
    fun `an upload in chunks leaves the target untouched until the last chunk and then holds every byte`() {
        val target = File(directory, "settings.bin").apply { writeText("old") }
        val staging = File(directory, ".settings.bin.jetwhale-upload-1")

        writeUploadChunk(staging.path, target.path, offset = 0, bytes = byteArrayOf(1, 2, 3), isLast = false)
        writeUploadChunk(staging.path, target.path, offset = 3, bytes = byteArrayOf(4, 5), isLast = false)
        assertEquals("old", target.readText())

        writeUploadChunk(staging.path, target.path, offset = 5, bytes = byteArrayOf(6), isLast = true)

        assertContentEquals(byteArrayOf(1, 2, 3, 4, 5, 6), target.readBytes())
        assertFalse(staging.exists())
    }

    @Test
    fun `a symbolic link planted at the staging path is refused and its target kept`() {
        val linkTarget = File(directory, "other.bin").apply { writeText("keep") }
        val staging = File(directory, ".settings.bin.jetwhale-upload-1")
        Files.createSymbolicLink(staging.toPath(), linkTarget.toPath())

        assertFailsWith<IllegalArgumentException> {
            writeUploadChunk(staging.path, "${directory.path}/settings.bin", offset = 0, bytes = byteArrayOf(1, 2, 3), isLast = false)
        }

        assertEquals("keep", linkTarget.readText())
    }

    @Test
    fun `a dangling symbolic link planted at the staging path is refused without creating its target`() {
        val outside = File(Files.createTempDirectory("storage-outside").toFile(), "planted.bin")
        val staging = File(directory, ".settings.bin.jetwhale-upload-1")
        Files.createSymbolicLink(staging.toPath(), outside.toPath())

        assertFailsWith<IllegalArgumentException> {
            writeUploadChunk(staging.path, "${directory.path}/settings.bin", offset = 0, bytes = byteArrayOf(1, 2, 3), isLast = false)
        }

        assertFalse(outside.exists())
    }

    @Test
    fun `a directory already at the staging path is refused and left as it was`() {
        val staging = File(directory, ".settings.bin.jetwhale-upload-1").apply { mkdir() }
        File(staging, "keep.txt").writeText("keep")

        assertFailsWith<IllegalArgumentException> {
            writeUploadChunk(staging.path, "${directory.path}/settings.bin", offset = 0, bytes = byteArrayOf(1), isLast = true)
        }

        assertEquals("keep", File(staging, "keep.txt").readText())
    }

    @Test
    fun `a named pipe already at the staging path is refused without opening it`() {
        assumeTrue("mkfifo exists only on Unix", !onWindows)
        val staging = File(directory, ".settings.bin.jetwhale-upload-1")
        check(ProcessBuilder("mkfifo", staging.path).start().waitFor() == 0) { "mkfifo failed" }

        // Opening a FIFO for writing blocks until a reader appears; the timeout turns a
        // regression's hang into a failure.
        val outcome = CompletableFuture.supplyAsync {
            runCatching { writeUploadChunk(staging.path, "${directory.path}/settings.bin", offset = 0, bytes = byteArrayOf(1), isLast = true) }
        }.get(10, TimeUnit.SECONDS)

        assertIs<IllegalArgumentException>(outcome.exceptionOrNull())
        assertTrue(staging.exists())
    }

    @Test
    fun `a chunk at the wrong offset discards the upload and keeps the target`() {
        val target = File(directory, "settings.bin").apply { writeText("old") }
        val staging = File(directory, ".settings.bin.jetwhale-upload-1")
        writeUploadChunk(staging.path, target.path, offset = 0, bytes = byteArrayOf(1, 2, 3), isLast = false)

        assertFailsWith<IllegalArgumentException> {
            writeUploadChunk(staging.path, target.path, offset = 7, bytes = byteArrayOf(4), isLast = true)
        }

        assertEquals("old", target.readText())
        assertFalse(staging.exists())
    }

    @Test
    fun `a chunk over the size bound discards the upload and keeps the target`() {
        val target = File(directory, "settings.bin").apply { writeText("old") }
        val staging = File(directory, ".settings.bin.jetwhale-upload-1")
        writeUploadChunk(staging.path, target.path, offset = 0, bytes = ByteArray(MAX_FILE_READ_BYTES), isLast = false)

        assertFailsWith<IllegalArgumentException> {
            writeUploadChunk(staging.path, target.path, offset = MAX_FILE_READ_BYTES.toLong(), bytes = ByteArray(MAX_FILE_READ_BYTES + 1), isLast = true)
        }

        assertEquals("old", target.readText())
        assertFalse(staging.exists())
    }

    @Test
    fun `an upload into a missing directory fails without creating it`() {
        val missing = File(directory, "missing")

        assertFailsWith<IOException> {
            writeUploadChunk("${missing.path}/.a.jetwhale-upload-1", "${missing.path}/a", offset = 0, bytes = byteArrayOf(1), isLast = true)
        }
        assertFalse(missing.exists())
    }

    @Test
    fun `an upload never replaces a directory`() {
        File(directory, "cache").mkdir()

        assertFailsWith<IOException> {
            writeUploadChunk("${directory.path}/.cache.jetwhale-upload-1", "${directory.path}/cache", offset = 0, bytes = byteArrayOf(1), isLast = true)
        }
        assertEquals(true, File(directory, "cache").isDirectory)
    }

    @Test
    fun `an upload through a symbolic link that leads outside the root is refused`() {
        val root = File(directory, "root").apply { mkdir() }
        val outside = File(directory, "outside").apply { mkdir() }
        Files.createSymbolicLink(File(root, "escape").toPath(), outside.toPath())

        assertFailsWith<IllegalArgumentException> {
            FileRoot(name = "Root", path = root.path).uploadPaths(listOf("escape", "planted.txt"), uploadId = "1")
        }
    }

    private fun createJunction(link: File, target: File) {
        val mklink = ProcessBuilder("cmd", "/c", "mklink", "/J", link.path, target.path).redirectErrorStream(true).start()
        val output = mklink.inputStream.bufferedReader().readText()
        check(mklink.waitFor() == 0) { "mklink /J failed: $output" }
    }
}
