package com.kitakkun.jetwhale.plugins.mirror.host

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class MirrorToolLocatorTest {
    private val folder: File = Files.createTempDirectory("mirror-tool-locator").toFile()
    private val loginShellDirectory = File(folder, "login").apply { mkdirs() }
    private val hostPathDirectory = File(folder, "host").apply { mkdirs() }

    @AfterTest
    fun cleanUp() {
        folder.deleteRecursively()
    }

    @Test
    fun `a tool on the login shell's PATH is found before one on the host's own PATH`() {
        val loginShellIdb = executableIdbIn(loginShellDirectory)
        executableIdbIn(hostPathDirectory)

        val located = MirrorToolLocator(toolDirectories(loginShellPathVariable = loginShellDirectory.path, pathVariable = hostPathDirectory.path), androidSdkDirectories = emptyList()).locateToolPaths()

        assertEquals(loginShellIdb, located.idbPath?.let(::File))
    }

    @Test
    fun `without the login shell's PATH a tool on the host's own PATH is still found`() {
        val hostPathIdb = executableIdbIn(hostPathDirectory)

        val located = MirrorToolLocator(toolDirectories(loginShellPathVariable = null, pathVariable = hostPathDirectory.path), androidSdkDirectories = emptyList()).locateToolPaths()

        assertEquals(hostPathIdb, located.idbPath?.let(::File))
    }

    @Test
    fun `Homebrew's directories are searched after the login shell's PATH and the host's own`() {
        val loginShellPathVariable = listOf("/opt/login/bin", "/usr/bin").joinToString(File.pathSeparator)

        val directories = toolDirectories(loginShellPathVariable = loginShellPathVariable, pathVariable = "/bin")

        assertEquals(listOf("/opt/login/bin", "/usr/bin", "/bin", "/opt/homebrew/bin", "/usr/local/bin"), directories)
    }

    private fun executableIdbIn(directory: File): File = File(directory, "idb").apply {
        writeText("#!/bin/sh\n")
        setExecutable(true)
    }
}
