package com.kitakkun.jetwhale.plugins.mirror.host

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.time.Duration.Companion.seconds

class MirrorToolLocatorTest {
    private val folder: File = Files.createTempDirectory("mirror-tool-locator").toFile()
    private val hostPathDirectory = File(folder, "host").apply { mkdirs() }
    private val wellKnownDirectory = File(folder, "well-known").apply { mkdirs() }
    private val loginShellDirectory = File(folder, "login").apply { mkdirs() }
    private val shellRunLogFile = File(folder, "shell-runs")

    @AfterTest
    fun cleanUp() {
        folder.deleteRecursively()
    }

    @Test
    fun `a tool on the host's PATH is found before one in a well-known directory`() {
        val hostPathIdb = createExecutableIn(hostPathDirectory, "idb")
        createExecutableIn(wellKnownDirectory, "idb")

        val located = locator(loginShellPathVariableResolver = null).locateTools()

        assertEquals(hostPathIdb, located.toolPaths.idbPath?.let(::File))
    }

    @Test
    fun `a tool in a well-known directory is found when the host's PATH lacks it`() {
        val wellKnownIdb = createExecutableIn(wellKnownDirectory, "idb")

        val located = locator(loginShellPathVariableResolver = null).locateTools()

        assertEquals(wellKnownIdb, located.toolPaths.idbPath?.let(::File))
    }

    @Test
    fun `the login shell is not started when every tool is found without it`() {
        assumeShellScriptsLaunch()
        listOf("adb", "idb", "idb_companion", "ffmpeg").forEach { createExecutableIn(wellKnownDirectory, it) }

        val located = locator(loginShellPathVariableResolver = loginShellPrinting(loginShellDirectory.path)).locateTools()

        assertFalse(shellRunLogFile.exists())
        assertEquals(listOf(hostPathDirectory.path, wellKnownDirectory.path), located.searchedDirectories)
    }

    @Test
    fun `a tool missing from the host's PATH and the well-known directories is looked for on the login shell's PATH`() {
        assumeShellScriptsLaunch()
        val loginShellFfmpeg = createExecutableIn(loginShellDirectory, "ffmpeg")

        val located = locator(loginShellPathVariableResolver = loginShellPrinting(loginShellDirectory.path)).locateTools()

        assertEquals(loginShellFfmpeg, located.toolPaths.ffmpegPath?.let(::File))
    }

    @Test
    fun `a tool found before the login shell is read stays where it was found`() {
        assumeShellScriptsLaunch()
        val wellKnownIdb = createExecutableIn(wellKnownDirectory, "idb")
        createExecutableIn(loginShellDirectory, "idb")

        val located = locator(loginShellPathVariableResolver = loginShellPrinting(loginShellDirectory.path)).locateTools()

        assertEquals(wellKnownIdb, located.toolPaths.idbPath?.let(::File))
        assertEquals(1, shellRunLogFile.readLines().size)
    }

    @Test
    fun `the searched directories run from the host's PATH through the well-known directories to the login shell's without repeating one`() {
        assumeShellScriptsLaunch()
        val loginShellPathVariable = listOf(loginShellDirectory.path, hostPathDirectory.path).joinToString(File.pathSeparator)

        val located = locator(loginShellPathVariableResolver = loginShellPrinting(loginShellPathVariable)).locateTools()

        assertEquals(listOf(hostPathDirectory.path, wellKnownDirectory.path, loginShellDirectory.path), located.searchedDirectories)
    }

    @Test
    fun `adb in an Android SDK is found before one on the host's PATH`() {
        val adbFileName = if (runsOnWindows) "adb.exe" else "adb"
        val sdkAdb = createExecutableIn(File(folder, "sdk/platform-tools").apply { mkdirs() }, adbFileName)
        createExecutableIn(hostPathDirectory, adbFileName)

        val located = MirrorToolLocator(
            hostPathVariable = hostPathDirectory.path,
            wellKnownDirectories = emptyList(),
            androidSdkDirectories = listOf(File(folder, "sdk").path),
            loginShellPathVariableResolver = null,
        ).locateTools()

        assertEquals(sdkAdb, located.toolPaths.adbPath?.let(::File))
    }

    @Test
    fun `the well-known directories are Homebrew's then pipx's then pip's per-user ones newest Python first then pyenv's shims`() {
        val home = File(folder, "home")
        listOf("3.9", "3.12", "3.10").forEach { File(home, "Library/Python/$it/bin").mkdirs() }

        val directories = WellKnownToolDirectories(home).list()

        val expected = listOf("/opt/homebrew/bin", "/usr/local/bin", File(home, ".local/bin").path) +
            listOf("3.12", "3.10", "3.9").map { File(home, "Library/Python/$it/bin").path } +
            File(home, ".pyenv/shims").path
        assertEquals(expected, directories)
    }

    private fun locator(loginShellPathVariableResolver: LoginShellPathVariableResolver?) = MirrorToolLocator(
        hostPathVariable = hostPathDirectory.path,
        wellKnownDirectories = listOf(wellKnownDirectory.path),
        androidSdkDirectories = emptyList(),
        loginShellPathVariableResolver = loginShellPathVariableResolver,
    )

    /** A resolver whose login shell is a script that sets [pathVariable] as its PATH and logs each run to [shellRunLogFile]. */
    private fun loginShellPrinting(pathVariable: String): LoginShellPathVariableResolver {
        val shell = File(folder, "shell").apply {
            writeText("#!/bin/sh\necho run >> '${shellRunLogFile.path}'\nPATH='$pathVariable'\neval \"\$4\"\n")
            setExecutable(true)
        }
        return LoginShellPathVariableResolver(shell.path, timeout = 5.seconds)
    }

    private fun createExecutableIn(directory: File, fileName: String): File = File(directory, fileName).apply {
        writeText("#!/bin/sh\n")
        setExecutable(true)
    }
}
