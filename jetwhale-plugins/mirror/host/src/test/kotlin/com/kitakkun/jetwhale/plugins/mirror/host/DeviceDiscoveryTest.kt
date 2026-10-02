package com.kitakkun.jetwhale.plugins.mirror.host

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

class DeviceDiscoveryTest {
    private val folder: File = Files.createTempDirectory("mirror-discovery").toFile()

    // Slow to answer, so the simultaneous looks overlap.
    private val fakeAdb = File(folder, "adb").apply {
        writeText("#!/bin/sh\nsleep 0.2\nprintf 'List of devices attached\\nemulator-5554\\tdevice product:sdk model:Pixel_9 device:emu\\n'\n")
        setExecutable(true)
    }

    private val idbRuns = File(folder, "idb-runs")

    // Lists one iPhone, and notes each run so a test can tell whether the companion was enough.
    private val fakeIdb = File(folder, "idb").apply {
        writeText("#!/bin/sh\necho run >> '${idbRuns.path}'\nprintf 'iPhone | 00008150-LISTED-BY-IDB | Booted | device | iOS 26.0 | arm64e | No Companion Connected\\n'\n")
        setExecutable(true)
    }

    private val fakeCompanion = File(folder, "idb_companion")

    private val companionScope = CoroutineScope(SupervisorJob())

    @AfterTest
    fun cleanUp() {
        companionScope.cancel()
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
    fun `iPhones are listed by idb_companion without running idb`() = runBlocking {
        companionPrints("""{"udid":"00008150-LISTED-BY-COMPANION","name":"iPhone","os_version":"iOS 26.0","type":"Device","state":"Booted"}""")

        val listed = iosDeviceDiscovery().discover().devices.map(MirrorDevice::id)

        assertEquals(listOf("00008150-LISTED-BY-COMPANION"), listed)
        assertFalse(idbRuns.exists())
    }

    @Test
    fun `a companion that lists nothing means no iPhone, without asking idb`() = runBlocking {
        companionPrints("")

        val listed = iosDeviceDiscovery().discover().devices

        assertEquals(emptyList(), listed)
        assertFalse(idbRuns.exists())
    }

    @Test
    fun `a companion that fails leaves the listing to idb`() = runBlocking {
        fakeCompanion.writeText("#!/bin/sh\necho 'no such option' >&2\nexit 1\n")
        fakeCompanion.setExecutable(true)

        val listed = iosDeviceDiscovery().discover().devices.map(MirrorDevice::id)

        assertEquals(listOf("00008150-LISTED-BY-IDB"), listed)
    }

    @Test
    fun `a companion whose answer is not a list of targets leaves the listing to idb`() = runBlocking {
        companionPrints("Usage: idb_companion [options]")

        val listed = iosDeviceDiscovery().discover().devices.map(MirrorDevice::id)

        assertEquals(listOf("00008150-LISTED-BY-IDB"), listed)
    }

    private fun companionPrints(output: String) {
        val quoted = output.replace("'", "'\\''")
        fakeCompanion.writeText("#!/bin/sh\n[ \"$*\" = '--list 1 --only device' ] || { echo \"unexpected arguments: $*\" >&2; exit 64; }\nprintf '%s' '$quoted'\n")
        fakeCompanion.setExecutable(true)
    }

    private fun iosDeviceDiscovery() = DeviceDiscovery(
        MirrorToolPaths(adbPath = null, idbPath = fakeIdb.absolutePath, idbCompanionPath = fakeCompanion.absolutePath, xcrunPath = null, ffmpegPath = null),
        companions = IdbCompanions(
            idbCompanionPath = fakeCompanion.absolutePath,
            idbPath = fakeIdb.absolutePath,
            launcher = SystemProcessLauncher,
            commands = { command -> runCommandChecked(*command.toTypedArray()) },
            ports = LocalPorts,
            idleTimeout = 1.minutes,
            scope = companionScope,
        ),
        emulatorScreens = EmulatorScreens(runningDirectories = emptyList()),
    )

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
