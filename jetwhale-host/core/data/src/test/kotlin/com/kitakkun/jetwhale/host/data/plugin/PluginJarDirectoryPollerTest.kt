package com.kitakkun.jetwhale.host.data.plugin

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class PluginJarDirectoryPollerTest {
    private val directory: File = Files.createTempDirectory("plugin-jar-poller").toFile()

    @AfterTest
    fun cleanUp() {
        directory.deleteRecursively()
    }

    @Test
    fun `jars already in the directory at the start are not reported`() {
        jar("installed.jar", byteArrayOf(1), modifiedAt = 1_000)
        val poller = PluginJarDirectoryPoller(directory)

        assertEquals(emptySet(), poller.poll())
        assertEquals(emptySet(), poller.poll())
    }

    @Test
    fun `a new jar is reported once it reads the same on two polls in a row`() {
        val poller = PluginJarDirectoryPoller(directory)
        val added = jar("added.jar", byteArrayOf(1, 2), modifiedAt = 1_000)

        assertEquals(emptySet(), poller.poll())
        assertEquals(setOf(added.absolutePath), poller.poll())
        assertEquals(emptySet(), poller.poll())
    }

    @Test
    fun `a change the caller could not handle is reported again`() {
        val poller = PluginJarDirectoryPoller(directory)
        val added = jar("added.jar", byteArrayOf(1, 2), modifiedAt = 1_000)
        poller.poll()
        poller.poll()

        poller.redeliver(added.absolutePath)

        assertEquals(setOf(added.absolutePath), poller.poll())
        assertEquals(emptySet(), poller.poll())
    }

    @Test
    fun `a removal the caller could not handle is reported again`() {
        val removed = jar("removed.jar", byteArrayOf(1), modifiedAt = 1_000)
        val poller = PluginJarDirectoryPoller(directory)
        removed.delete()
        poller.poll()
        poller.poll()

        poller.redeliver(removed.absolutePath)

        assertEquals(setOf(removed.absolutePath), poller.poll())
    }

    @Test
    fun `a directory that cannot be listed is not taken as empty`() {
        val installed = jar("installed.jar", byteArrayOf(1), modifiedAt = 1_000)
        val poller = PluginJarDirectoryPoller(directory)
        val hidden = File(directory.parentFile, "${directory.name}-hidden")
        check(directory.renameTo(hidden))

        assertEquals(emptySet(), poller.poll())
        assertEquals(emptySet(), poller.poll())

        check(hidden.renameTo(directory))
        assertEquals(emptySet(), poller.poll())
        assertEquals(true, installed.exists())
    }

    @Test
    fun `a jar replaced by a rename that keeps its size and time is reported`() {
        val installed = jar("installed.jar", byteArrayOf(1, 2), modifiedAt = 1_000)
        val poller = PluginJarDirectoryPoller(directory)
        val replacement = File(directory, "replacement.tmp").apply {
            writeBytes(byteArrayOf(3, 4))
            setLastModified(1_000)
        }

        Files.move(replacement.toPath(), installed.toPath(), StandardCopyOption.REPLACE_EXISTING)

        assertEquals(emptySet(), poller.poll())
        assertEquals(setOf(installed.absolutePath), poller.poll())
    }

    @Test
    fun `a jar still being written is not reported until it stops changing`() {
        val poller = PluginJarDirectoryPoller(directory)
        val growing = jar("growing.jar", byteArrayOf(1), modifiedAt = 1_000)
        assertEquals(emptySet(), poller.poll())

        growing.appendBytes(byteArrayOf(2, 3))
        growing.setLastModified(2_000)
        assertEquals(emptySet(), poller.poll())

        assertEquals(setOf(growing.absolutePath), poller.poll())
    }

    @Test
    fun `a removed jar is reported once it stays gone for two polls`() {
        val removed = jar("removed.jar", byteArrayOf(1), modifiedAt = 1_000)
        val poller = PluginJarDirectoryPoller(directory)

        removed.delete()

        assertEquals(emptySet(), poller.poll())
        assertEquals(setOf(removed.absolutePath), poller.poll())
        assertEquals(emptySet(), poller.poll())
    }

    @Test
    fun `a jar deleted and copied back between polls is one change and never a removal`() {
        val replaced = jar("replaced.jar", byteArrayOf(1), modifiedAt = 1_000)
        val poller = PluginJarDirectoryPoller(directory)

        replaced.delete()
        assertEquals(emptySet(), poller.poll())
        jar("replaced.jar", byteArrayOf(9, 9, 9), modifiedAt = 2_000)
        assertEquals(emptySet(), poller.poll())

        assertEquals(setOf(replaced.absolutePath), poller.poll())
    }

    @Test
    fun `files that are not jars are ignored`() {
        val poller = PluginJarDirectoryPoller(directory)
        File(directory, "notes.txt").writeText("hello")
        File(directory, "download.jar.part").writeBytes(byteArrayOf(1))

        assertEquals(emptySet(), poller.poll())
        assertEquals(emptySet(), poller.poll())
    }

    private fun jar(name: String, content: ByteArray, modifiedAt: Long): File = File(directory, name).apply {
        writeBytes(content)
        setLastModified(modifiedAt)
    }
}
