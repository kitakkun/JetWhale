package com.kitakkun.jetwhale.plugins.mirror.host

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
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
import kotlin.time.Duration.Companion.minutes
import kotlin.time.TimeSource

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

    private val capturesScope = CoroutineScope(SupervisorJob())

    @AfterTest
    fun cleanUp() {
        capturesScope.cancel()
        folder.deleteRecursively()
    }

    @Test
    fun `looks at once hand a device one controller`() = runBlocking {
        assumeShellScriptsLaunch()
        val discovery = DeviceDiscovery(CompletableDeferred(MirrorToolPaths(adbPath = fakeAdb.absolutePath, idbPath = null, idbCompanionPath = null, xcrunPath = null, ffmpegPath = null)), iphoneCaptures = CompletableDeferred(value = null), xcTestRunners = CompletableDeferred(value = null), iproxyPath = CompletableDeferred(value = null), emulatorScreens = EmulatorScreens(runningDirectories = emptyList()))

        val looks = List(SIMULTANEOUS_LOOKS) { async(Dispatchers.Default) { discovery.discover() } }.awaitAll()

        assertEquals(1, looks.map { it.devices.single().controller }.toSet().size)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `a look waits for the tools to be located before it reports one missing`() = runTest {
        assumeShellScriptsLaunch()
        val toolPaths = CompletableDeferred<MirrorToolPaths>()
        val discovery = DeviceDiscovery(toolPaths, iphoneCaptures = CompletableDeferred(value = null), xcTestRunners = CompletableDeferred(value = null), iproxyPath = CompletableDeferred(value = null), emulatorScreens = EmulatorScreens(runningDirectories = emptyList()))

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
        val discovery = DeviceDiscovery(CompletableDeferred(MirrorToolPaths(adbPath = fakeAdb.absolutePath, idbPath = null, idbCompanionPath = null, xcrunPath = null, ffmpegPath = null)), iphoneCaptures = CompletableDeferred(value = null), xcTestRunners = CompletableDeferred(value = null), iproxyPath = CompletableDeferred(value = null), emulatorScreens = EmulatorScreens(runningDirectories = emptyList()))
        val before = discovery.discover().devices.single()
        fakeAdb.writeText("#!/bin/sh\necho 'daemon not running' >&2\nexit 1\n")

        val after = discovery.discover().devices.single()

        assertSame(before.controller, after.controller)
    }

    @Test
    fun `a listing that succeeds without a device drops it`() = runBlocking {
        assumeShellScriptsLaunch()
        val discovery = DeviceDiscovery(CompletableDeferred(MirrorToolPaths(adbPath = fakeAdb.absolutePath, idbPath = null, idbCompanionPath = null, xcrunPath = null, ffmpegPath = null)), iphoneCaptures = CompletableDeferred(value = null), xcTestRunners = CompletableDeferred(value = null), iproxyPath = CompletableDeferred(value = null), emulatorScreens = EmulatorScreens(runningDirectories = emptyList()))
        discovery.discover()
        fakeAdb.writeText("#!/bin/sh\nprintf 'List of devices attached\\n'\n")

        assertEquals(emptyList(), discovery.discover().devices)
    }

    @Test
    fun `iPhones and iPads on USB are listed through devicectl`() = runBlocking {
        assumeShellScriptsLaunch()

        val listed = iosDeviceDiscovery().discover().devices.map(MirrorDevice::listing)

        assertEquals(
            listOf(
                DeviceListing(id = "00008110-000A1B2C3D4E5F60", name = "Test iPhone", kind = DeviceKind.IosDevice, osVersion = "iOS 26.6.1"),
                DeviceListing(id = "00008112-0001A2B3C4D5E6F7", name = "Test iPad", kind = DeviceKind.IosDevice, osVersion = "iOS 26.6.1"),
            ),
            listed,
        )
    }

    private fun iosDeviceDiscovery() = DeviceDiscovery(
        CompletableDeferred(MirrorToolPaths(adbPath = null, idbPath = null, idbCompanionPath = null, xcrunPath = fakeXcrun.absolutePath, ffmpegPath = "/usr/bin/true")),
        xcTestRunners = CompletableDeferred(value = null),
        iproxyPath = CompletableDeferred(value = null),
        iphoneCaptures = CompletableDeferred(
            IphoneScreenCaptures(
                launcher = { throw deviceControlError("no capture helper in tests") },
                idleTimeout = 1.minutes,
                failureReuse = 1.minutes,
                timeSource = TimeSource.Monotonic,
                scope = capturesScope,
                helperExecutable = CompletableDeferred(File("jetwhale-iphone-capture")),
            ),
        ),
        emulatorScreens = EmulatorScreens(runningDirectories = emptyList()),
    )

    @Test
    fun `a machine without ffmpeg is told that Android devices fall back to screenshots and how to install it`() = runBlocking {
        val discovery = DeviceDiscovery(CompletableDeferred(MirrorToolPaths(adbPath = fakeAdb.absolutePath, idbPath = null, idbCompanionPath = null, xcrunPath = null, ffmpegPath = null)), iphoneCaptures = CompletableDeferred(value = null), xcTestRunners = CompletableDeferred(value = null), iproxyPath = CompletableDeferred(value = null), emulatorScreens = EmulatorScreens(runningDirectories = emptyList()))

        val notice = discovery.discover().missingTools.single { "ffmpeg" in it }

        assertContains(notice, "screenshots")
        assertContains(notice, "brew install ffmpeg")
    }

    @Test
    fun `a machine with ffmpeg is not told about it`() = runBlocking {
        val discovery = DeviceDiscovery(CompletableDeferred(MirrorToolPaths(adbPath = fakeAdb.absolutePath, idbPath = null, idbCompanionPath = null, xcrunPath = null, ffmpegPath = "/usr/bin/true")), iphoneCaptures = CompletableDeferred(value = null), xcTestRunners = CompletableDeferred(value = null), iproxyPath = CompletableDeferred(value = null), emulatorScreens = EmulatorScreens(runningDirectories = emptyList()))

        assertTrue(discovery.discover().missingTools.none { "ffmpeg" in it })
    }
}

private const val SIMULTANEOUS_LOOKS = 4
