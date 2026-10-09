package com.kitakkun.jetwhale.plugins.mirror.host

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class DeviceDiscoveryTest {
    @Test
    fun `a major OS version is the first number of an OS version`() {
        assertEquals(26, majorVersionOf("iOS 26.2"))
        assertEquals(16, majorVersionOf("16.7.10"))
        assertNull(majorVersionOf(null))
    }

    private val folder: File = Files.createTempDirectory("mirror-discovery").toFile()

    // Slow to answer, so the simultaneous looks overlap.
    private val fakeAdb = File(folder, "adb").apply {
        writeText("#!/bin/sh\nsleep 0.2\nprintf 'List of devices attached\\nemulator-5554\\tdevice product:sdk model:Pixel_9 device:emu\\n'\n")
        setExecutable(true)
    }

    private val devicectlFixture = checkNotNull(DeviceDiscoveryTest::class.java.getResource("/devicectl/list-devices.json")).readText()

    private val devicectlJsonFile = File(folder, "devicectl-list-devices.json").apply { writeText(devicectlFixture) }

    /** An xcrun that lists no booted simulator, and the devices of [devicectlJsonFile] through devicectl. */
    private val fakeXcrun = File(folder, "xcrun").apply {
        writeText(
            """
            #!/bin/sh
            case "${'$'}1" in
              simctl) printf '{"devices":{}}' ;;
              devicectl) [ "${'$'}2 ${'$'}3 ${'$'}4" = 'list devices --json-output' ] || exit 64; cp '${devicectlJsonFile.path}' "${'$'}5" ;;
              *) exit 64 ;;
            esac
            """.trimIndent() + "\n",
        )
        setExecutable(true)
    }

    @AfterTest
    fun cleanUp() {
        folder.deleteRecursively()
    }

    @Test
    fun `looks at once hand a device one controller`() = runBlocking {
        assumeShellScriptsLaunch()
        val discovery = DeviceDiscovery(CompletableDeferred(MirrorToolPaths(adbPath = fakeAdb.absolutePath, idbPath = null, idbCompanionPath = null, xcrunPath = null, ffmpegPath = null)), companions = CompletableDeferred(value = null), xcTestRunners = CompletableDeferred(value = null), iproxyPath = CompletableDeferred(value = null), emulatorScreens = EmulatorScreens(runningDirectories = emptyList()))

        val looks = List(SIMULTANEOUS_LOOKS) { async(Dispatchers.Default) { discovery.discover() } }.awaitAll()

        assertEquals(1, looks.map { it.devices.single().controller }.toSet().size)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `a look waits for the tools to be located before it reports one missing`() = runTest {
        assumeShellScriptsLaunch()
        val toolPaths = CompletableDeferred<MirrorToolPaths>()
        val discovery = DeviceDiscovery(toolPaths, companions = CompletableDeferred(value = null), xcTestRunners = CompletableDeferred(value = null), iproxyPath = CompletableDeferred(value = null), emulatorScreens = EmulatorScreens(runningDirectories = emptyList()))

        val look = async { discovery.discover() }
        runCurrent()
        assertFalse(look.isCompleted)
        toolPaths.complete(MirrorToolPaths(adbPath = fakeAdb.absolutePath, idbPath = null, idbCompanionPath = null, xcrunPath = null, ffmpegPath = null))

        val found = look.await()
        assertEquals(listOf("emulator-5554"), found.devices.map(MirrorDevice::id))
        assertTrue(found.missingTools.none { it.startsWith("adb was not found") })
    }

    @Test
    fun `a listing that fails keeps the devices it listed before with their controllers`() = runBlocking {
        assumeShellScriptsLaunch()
        val discovery = DeviceDiscovery(CompletableDeferred(MirrorToolPaths(adbPath = fakeAdb.absolutePath, idbPath = null, idbCompanionPath = null, xcrunPath = null, ffmpegPath = null)), companions = CompletableDeferred(value = null), xcTestRunners = CompletableDeferred(value = null), iproxyPath = CompletableDeferred(value = null), emulatorScreens = EmulatorScreens(runningDirectories = emptyList()))
        val before = discovery.discover().devices.single()
        fakeAdb.writeText("#!/bin/sh\necho 'daemon not running' >&2\nexit 1\n")

        val after = discovery.discover().devices.single()

        assertSame(before.controller, after.controller)
    }

    @Test
    fun `a listing that succeeds without a device drops it`() = runBlocking {
        assumeShellScriptsLaunch()
        val discovery = DeviceDiscovery(CompletableDeferred(MirrorToolPaths(adbPath = fakeAdb.absolutePath, idbPath = null, idbCompanionPath = null, xcrunPath = null, ffmpegPath = null)), companions = CompletableDeferred(value = null), xcTestRunners = CompletableDeferred(value = null), iproxyPath = CompletableDeferred(value = null), emulatorScreens = EmulatorScreens(runningDirectories = emptyList()))
        discovery.discover()
        fakeAdb.writeText("#!/bin/sh\nprintf 'List of devices attached\\n'\n")

        assertEquals(emptyList(), discovery.discover().devices)
    }

    @Test
    fun `iPhones and iPads on USB are listed through devicectl without idb`() = runBlocking {
        assumeShellScriptsLaunch()

        val listed = iosDeviceDiscovery(idbPath = null, idbCompanionPath = null).discover().devices.map(MirrorDevice::listing)

        assertEquals(
            listOf(
                DeviceListing(id = "00008110-000A1B2C3D4E5F60", name = "Test iPhone", kind = DeviceKind.IosDevice, osVersion = "iOS 26.6.1"),
                DeviceListing(id = "00008112-0001A2B3C4D5E6F7", name = "Test iPad", kind = DeviceKind.IosDevice, osVersion = "iOS 26.6.1"),
            ),
            listed,
        )
    }

    @Test
    fun `no idb companion is asked for and no idb is asked to be installed while no iPhone is listed`() = runBlocking {
        assumeShellScriptsLaunch()
        devicectlJsonFile.writeText(devicectlFixture.replace("\"wired\"", "\"localNetwork\""))
        val discovery = DeviceDiscovery(
            CompletableDeferred(MirrorToolPaths(adbPath = null, idbPath = null, idbCompanionPath = null, xcrunPath = fakeXcrun.absolutePath, ffmpegPath = null)),
            companions = CompletableDeferred(),
            xcTestRunners = CompletableDeferred(value = null),
            iproxyPath = CompletableDeferred(value = null),
            emulatorScreens = EmulatorScreens(runningDirectories = emptyList()),
        )

        val found = withTimeout(10.seconds) { discovery.discover() }

        assertEquals(emptyList(), found.devices)
        assertTrue(found.missingTools.none { "idb" in it })
    }

    @Test
    fun `a machine without idb is told to install it from facebook's tap once an iPhone is listed`() = runBlocking {
        assumeShellScriptsLaunch()

        val notice = iosDeviceDiscovery(idbPath = null, idbCompanionPath = null).discover().missingTools.single { it.startsWith("idb was not found") }

        assertContains(notice, "brew install facebook/fb/idb")
    }

    @Test
    fun `a machine with idb but without its companion is told to install idb from facebook's tap once an iPhone is listed`() = runBlocking {
        assumeShellScriptsLaunch()

        val notice = iosDeviceDiscovery(idbPath = "/usr/bin/true", idbCompanionPath = null).discover().missingTools.single { it.startsWith("idb_companion was not found") }

        assertContains(notice, "brew install facebook/fb/idb")
    }

    private fun iosDeviceDiscovery(idbPath: String?, idbCompanionPath: String?) = DeviceDiscovery(
        CompletableDeferred(MirrorToolPaths(adbPath = null, idbPath = idbPath, idbCompanionPath = idbCompanionPath, xcrunPath = fakeXcrun.absolutePath, ffmpegPath = "/usr/bin/true")),
        companions = CompletableDeferred(value = null),
        xcTestRunners = CompletableDeferred(value = null),
        iproxyPath = CompletableDeferred(value = null),
        emulatorScreens = EmulatorScreens(runningDirectories = emptyList()),
    )

    @Test
    fun `a machine without ffmpeg is told that Android devices fall back to screenshots and how to install it`() = runBlocking {
        val discovery = DeviceDiscovery(CompletableDeferred(MirrorToolPaths(adbPath = fakeAdb.absolutePath, idbPath = null, idbCompanionPath = null, xcrunPath = null, ffmpegPath = null)), companions = CompletableDeferred(value = null), xcTestRunners = CompletableDeferred(value = null), iproxyPath = CompletableDeferred(value = null), emulatorScreens = EmulatorScreens(runningDirectories = emptyList()))

        val notice = discovery.discover().missingTools.single { "ffmpeg" in it }

        assertContains(notice, "screenshots")
        assertContains(notice, "brew install ffmpeg")
    }

    @Test
    fun `a machine with ffmpeg is not told about it`() = runBlocking {
        val discovery = DeviceDiscovery(CompletableDeferred(MirrorToolPaths(adbPath = fakeAdb.absolutePath, idbPath = null, idbCompanionPath = null, xcrunPath = null, ffmpegPath = "/usr/bin/true")), companions = CompletableDeferred(value = null), xcTestRunners = CompletableDeferred(value = null), iproxyPath = CompletableDeferred(value = null), emulatorScreens = EmulatorScreens(runningDirectories = emptyList()))

        assertTrue(discovery.discover().missingTools.none { "ffmpeg" in it })
    }
}

private const val SIMULTANEOUS_LOOKS = 4
