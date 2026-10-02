package com.kitakkun.jetwhale.host.data.util

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

    @AfterTest
    fun cleanUp() {
        folder.deleteRecursively()
    }

    @Test
    fun `no Android SDK and no adb on PATH means adb is not found`() {
        val locator = locator(environment = mapOf("PATH" to emptyDirectory.path))

        assertNull(locator.find())
    }

    @Test
    fun `adb on PATH is found`() {
        val adb = executableAdbIn(File(folder, "bin"))

        val locator = locator(environment = mapOf("PATH" to listOf(emptyDirectory.path, adb.parent).joinToString(":")))

        assertEquals(adb, locator.find())
    }

    @Test
    fun `the SDK ANDROID_HOME names is found before an adb on PATH`() {
        val sdkAdb = executableAdbIn(File(folder, "sdk/platform-tools"))
        val pathAdb = executableAdbIn(File(folder, "bin"))

        val locator = locator(environment = mapOf("ANDROID_HOME" to File(folder, "sdk").path, "PATH" to pathAdb.parent))

        assertEquals(sdkAdb, locator.find())
    }

    @Test
    fun `an adb file that cannot be executed is not taken`() {
        val notExecutable = File(File(folder, "bin").apply { mkdirs() }, "adb").apply {
            writeText("")
            setExecutable(false)
        }

        val locator = locator(environment = mapOf("PATH" to notExecutable.parent))

        assertNull(locator.find())
    }

    private fun locator(environment: Map<String, String>) = AdbLocator(
        environment = environment,
        userHome = home.path,
        isWindows = false,
        fixedDirectories = emptyList(),
    )

    private fun executableAdbIn(directory: File): File = File(directory.apply { mkdirs() }, "adb").apply {
        writeText("#!/bin/sh\n")
        setExecutable(true)
    }
}
