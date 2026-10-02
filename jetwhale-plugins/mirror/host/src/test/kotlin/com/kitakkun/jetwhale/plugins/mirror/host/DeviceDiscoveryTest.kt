package com.kitakkun.jetwhale.plugins.mirror.host

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class DeviceDiscoveryTest {
    private val folder: File = Files.createTempDirectory("mirror-discovery").toFile()

    // Slow to answer, so the simultaneous looks overlap.
    private val fakeAdb = File(folder, "adb").apply {
        writeText("#!/bin/sh\nsleep 0.2\nprintf 'List of devices attached\\nemulator-5554\\tdevice product:sdk model:Pixel_9 device:emu\\n'\n")
        setExecutable(true)
    }

    @AfterTest
    fun cleanUp() {
        folder.deleteRecursively()
    }

    @Test
    fun `looks at once hand a device one controller`() = runBlocking {
        val discovery = DeviceDiscovery(MirrorToolPaths(adbPath = fakeAdb.absolutePath, idbPath = null, idbCompanionPath = null, xcrunPath = null, ffmpegPath = null), companions = null, emulatorScreens = EmulatorScreens(runningDirectories = emptyList()))

        val looks = List(SIMULTANEOUS_LOOKS) { async(Dispatchers.Default) { discovery.discover() } }.awaitAll()

        assertEquals(1, looks.map { it.devices.single().controller }.toSet().size)
    }

    @Test
    fun `a listing that fails keeps the devices it listed before with their controllers`() = runBlocking {
        val discovery = DeviceDiscovery(MirrorToolPaths(adbPath = fakeAdb.absolutePath, idbPath = null, idbCompanionPath = null, xcrunPath = null, ffmpegPath = null), companions = null, emulatorScreens = EmulatorScreens(runningDirectories = emptyList()))
        val before = discovery.discover().devices.single()
        fakeAdb.writeText("#!/bin/sh\necho 'daemon not running' >&2\nexit 1\n")

        val after = discovery.discover().devices.single()

        assertSame(before.controller, after.controller)
    }

    @Test
    fun `a listing that succeeds without a device drops it`() = runBlocking {
        val discovery = DeviceDiscovery(MirrorToolPaths(adbPath = fakeAdb.absolutePath, idbPath = null, idbCompanionPath = null, xcrunPath = null, ffmpegPath = null), companions = null, emulatorScreens = EmulatorScreens(runningDirectories = emptyList()))
        discovery.discover()
        fakeAdb.writeText("#!/bin/sh\nprintf 'List of devices attached\\n'\n")

        assertEquals(emptyList(), discovery.discover().devices)
    }

    @Test
    fun `a machine without ffmpeg is told that Android devices fall back to screenshots and how to install it`() = runBlocking {
        val discovery = DeviceDiscovery(MirrorToolPaths(adbPath = fakeAdb.absolutePath, idbPath = null, idbCompanionPath = null, xcrunPath = null, ffmpegPath = null), companions = null, emulatorScreens = EmulatorScreens(runningDirectories = emptyList()))

        val notice = discovery.discover().missingTools.single { "ffmpeg" in it }

        assertContains(notice, "screenshots")
        assertContains(notice, "brew install ffmpeg")
    }

    @Test
    fun `a machine without idb is told to install it from facebook's tap`() = runBlocking {
        val discovery = DeviceDiscovery(MirrorToolPaths(adbPath = null, idbPath = null, idbCompanionPath = null, xcrunPath = "/usr/bin/true", ffmpegPath = null), companions = null, emulatorScreens = EmulatorScreens(runningDirectories = emptyList()))

        val notice = discovery.discover().missingTools.single { it.startsWith("idb was not found") }

        assertContains(notice, "brew install facebook/fb/idb")
    }

    @Test
    fun `a machine with idb but without its companion is told to install idb from facebook's tap`() = runBlocking {
        val discovery = DeviceDiscovery(MirrorToolPaths(adbPath = null, idbPath = "/usr/bin/true", idbCompanionPath = null, xcrunPath = null, ffmpegPath = null), companions = null, emulatorScreens = EmulatorScreens(runningDirectories = emptyList()))

        val notice = discovery.discover().missingTools.single { it.startsWith("idb_companion was not found") }

        assertContains(notice, "brew install facebook/fb/idb")
    }

    @Test
    fun `a machine with ffmpeg is not told about it`() = runBlocking {
        val discovery = DeviceDiscovery(MirrorToolPaths(adbPath = fakeAdb.absolutePath, idbPath = null, idbCompanionPath = null, xcrunPath = null, ffmpegPath = "/usr/bin/true"), companions = null, emulatorScreens = EmulatorScreens(runningDirectories = emptyList()))

        assertTrue(discovery.discover().missingTools.none { "ffmpeg" in it })
    }
}

private const val SIMULTANEOUS_LOOKS = 4
