package com.kitakkun.jetwhale.host.data.util

import com.kitakkun.jetwhale.host.model.HostOs
import org.junit.Assume.assumeFalse
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AdbLocatorTest {
    private val folder: File = Files.createTempDirectory("adb-locator").toFile()
    private val home = File(folder, "home").apply { mkdirs() }
    private val emptyDirectory = File(folder, "empty").apply { mkdirs() }
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
        fixedDirectories = emptyList(),
    )

    private fun executableAdbIn(directory: File): File = File(directory.apply { mkdirs() }, if (onWindows) "adb.exe" else "adb").apply {
        writeText("#!/bin/sh\n")
        setExecutable(true)
    }
}
