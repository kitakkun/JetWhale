package com.kitakkun.jetwhale.plugins.mirror.host

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class DeviceDiscoveryTest {
    private val folder: File = Files.createTempDirectory("mirror-discovery").toFile()

    // An adb that takes a moment to answer, as a real one does while its server starts.
    private val adb = File(folder, "adb").apply {
        writeText("#!/bin/sh\nsleep 0.2\nprintf 'List of devices attached\\nemulator-5554\\tdevice product:sdk model:Pixel_9 device:emu\\n'\n")
        setExecutable(true)
    }

    @AfterTest
    fun cleanUp() {
        folder.deleteRecursively()
    }

    @Test
    fun `looks at once hand a device one controller`() = runBlocking {
        val discovery = DeviceDiscovery(MirrorTools(adb = adb.absolutePath, idb = null, idbCompanion = null, xcrun = null), companions = null)

        val looks = List(SIMULTANEOUS_LOOKS) { async(Dispatchers.Default) { discovery.discover() } }.awaitAll()

        assertEquals(1, looks.map { it.devices.single().controller }.toSet().size)
    }
}

private const val SIMULTANEOUS_LOOKS = 4
