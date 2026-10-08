package com.kitakkun.jetwhale.host.data.util

import com.kitakkun.jetwhale.host.model.HostOs
import org.junit.Assume.assumeFalse
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.seconds

class AdbLocatorTest {
    private val folder: File = Files.createTempDirectory("adb-locator").toFile()
    private val home = File(folder, "home").apply { mkdirs() }
    private val emptyDirectory = File(folder, "empty").apply { mkdirs() }
    private val shellRunLogFile = File(folder, "shell-runs")
    private val onWindows = HostOs.current == HostOs.WINDOWS

    @AfterTest
    fun cleanUp() {
        folder.deleteRecursively()
    }

    @Test
    fun `no Android SDK and no adb on PATH means adb is not found`() {
        val locator = locator(environment = mapOf("PATH" to emptyDirectory.path), isWindows = onWindows)

        assertNull(locator.find())
    }

    @Test
    fun `adb on PATH is found`() {
        val adb = executableAdbIn(File(folder, "bin"))

        val locator = locator(environment = mapOf("PATH" to listOf(emptyDirectory.path, adb.parent).joinToString(File.pathSeparator)), isWindows = onWindows)

        assertEquals(adb, locator.find())
    }

    @Test
    fun `the SDK named by ANDROID_HOME is found before an adb on PATH`() {
        val sdkAdb = executableAdbIn(File(folder, "sdk/platform-tools"))
        val pathAdb = executableAdbIn(File(folder, "bin"))

        val locator = locator(environment = mapOf("ANDROID_HOME" to File(folder, "sdk").path, "PATH" to pathAdb.parent), isWindows = onWindows)

        assertEquals(sdkAdb, locator.find())
    }

    @Test
    fun `on Windows a PATH spelled Path is still searched`() {
        val adb = File(File(folder, "bin").apply { mkdirs() }, "adb.exe").apply {
            writeText("")
            setExecutable(true)
        }

        val locator = locator(environment = mapOf("Path" to adb.parent), isWindows = true)

        assertEquals(adb, locator.find())
    }

    @Test
    fun `the default SDK location is searched before the host's PATH`() {
        val sdkAdb = executableAdbIn(File(home, "Library/Android/sdk/platform-tools"))
        val pathAdb = executableAdbIn(File(folder, "bin"))

        val locator = locator(environment = mapOf("PATH" to pathAdb.parent), isWindows = onWindows)

        assertEquals(sdkAdb, locator.find())
    }

    @Test
    fun `adb on the host's PATH is found before one in a well-known directory`() {
        assumeFalse("a Windows path holds the colon that separates the entries of this PATH", onWindows)
        val hostPathAdb = executableAdbIn(File(folder, "host"))
        val wellKnownAdb = executableAdbIn(File(folder, "well-known"))

        val locator = AdbLocator(environment = mapOf("PATH" to hostPathAdb.parent), userHome = home.path, isWindows = false, wellKnownDirectories = listOf(wellKnownAdb.parent), loginShellPathVariableResolver = null)

        assertEquals(hostPathAdb, locator.find())
    }

    @Test
    fun `adb in a well-known directory is found without starting the login shell`() {
        assumeFalse("the login shell stands in as a /bin/sh script, which Windows cannot launch", onWindows)
        val wellKnownAdb = executableAdbIn(File(folder, "well-known"))
        val loginShellAdb = executableAdbIn(File(folder, "login"))

        val locator = AdbLocator(
            environment = mapOf("PATH" to emptyDirectory.path),
            userHome = home.path,
            isWindows = false,
            wellKnownDirectories = listOf(wellKnownAdb.parent),
            loginShellPathVariableResolver = loginShellPrinting(loginShellAdb.parent),
        )

        assertEquals(wellKnownAdb, locator.find())
        assertFalse(shellRunLogFile.exists())
    }

    @Test
    fun `adb on the login shell's PATH is found when no SDK location or PATH entry or well-known directory has one`() {
        assumeFalse("the login shell stands in as a /bin/sh script, which Windows cannot launch", onWindows)
        val loginShellAdb = executableAdbIn(File(folder, "login"))

        val locator = AdbLocator(
            environment = mapOf("PATH" to emptyDirectory.path),
            userHome = home.path,
            isWindows = false,
            wellKnownDirectories = listOf(emptyDirectory.path),
            loginShellPathVariableResolver = loginShellPrinting("${emptyDirectory.path}:${loginShellAdb.parent}"),
        )

        assertEquals(loginShellAdb, locator.find())
    }

    @Test
    fun `adb is not found when the login shell gives no PATH and no other place has one`() {
        assumeFalse("the login shell stands in as a /bin/sh script, which Windows cannot launch", onWindows)
        val failingShell = File(folder, "shell").apply {
            writeText("#!/bin/sh\nexit 1\n")
            setExecutable(true)
        }

        val locator = AdbLocator(
            environment = mapOf("PATH" to emptyDirectory.path),
            userHome = home.path,
            isWindows = false,
            wellKnownDirectories = listOf(emptyDirectory.path),
            loginShellPathVariableResolver = LoginShellPathVariableResolver(failingShell.path, timeout = 5.seconds),
        )

        assertNull(locator.find())
    }

    @Test
    fun `the login shell is read once however often adb is looked for`() {
        assumeFalse("the login shell stands in as a /bin/sh script, which Windows cannot launch", onWindows)
        val locator = AdbLocator(
            environment = mapOf("PATH" to emptyDirectory.path),
            userHome = home.path,
            isWindows = false,
            wellKnownDirectories = emptyList(),
            loginShellPathVariableResolver = loginShellPrinting(emptyDirectory.path),
        )

        locator.find()
        locator.find()

        assertEquals(1, shellRunLogFile.readLines().size)
    }

    @Test
    fun `an adb file that cannot be executed is not taken`() {
        assumeFalse("Windows has no execute permission for java.io.File to clear", onWindows)
        val notExecutable = File(File(folder, "bin").apply { mkdirs() }, "adb").apply {
            writeText("")
            setExecutable(false)
        }

        val locator = locator(environment = mapOf("PATH" to notExecutable.parent), isWindows = false)

        assertNull(locator.find())
    }

    private fun locator(environment: Map<String, String>, isWindows: Boolean) = AdbLocator(
        environment = environment,
        userHome = home.path,
        isWindows = isWindows,
        wellKnownDirectories = emptyList(),
        loginShellPathVariableResolver = null,
    )

    /** A resolver whose login shell is a script that sets [pathVariable] as its PATH and logs each run to [shellRunLogFile]. */
    private fun loginShellPrinting(pathVariable: String): LoginShellPathVariableResolver {
        val shell = File(folder, "shell").apply {
            writeText("#!/bin/sh\necho run >> '${shellRunLogFile.path}'\nPATH='$pathVariable'\neval \"\$4\"\n")
            setExecutable(true)
        }
        return LoginShellPathVariableResolver(shell.path, timeout = 5.seconds)
    }

    private fun executableAdbIn(directory: File): File = File(directory.apply { mkdirs() }, if (onWindows) "adb.exe" else "adb").apply {
        writeText("#!/bin/sh\n")
        setExecutable(true)
    }
}
