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
import kotlin.test.assertIs
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

    private val captureScope = CoroutineScope(SupervisorJob())

    @AfterTest
    fun cleanUp() {
        captureScope.cancel()
        folder.deleteRecursively()
    }

    @Test
    fun `looks at once hand a device one controller`() = runBlocking {
        assumeShellScriptsLaunch()
        val discovery = DeviceDiscovery(CompletableDeferred(MirrorToolPaths(adbPath = fakeAdb.absolutePath, xcrunPath = null, ffmpegPath = null, iproxyPath = null)), iphoneScreenCapturePool = CompletableDeferred(value = null), xcTestRunners = CompletableDeferred(value = null), emulatorScreens = EmulatorScreens(runningDirectories = emptyList()), xrEmulatorClients = XrEmulatorClients(runningDirectories = emptyList()))

        val looks = List(SIMULTANEOUS_LOOKS) { async(Dispatchers.Default) { discovery.discover() } }.awaitAll()

        assertEquals(1, looks.map { it.devices.single().controller }.toSet().size)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `a look waits for the tools to be located before it reports one missing`() = runTest {
        assumeShellScriptsLaunch()
        val toolPaths = CompletableDeferred<MirrorToolPaths>()
        val discovery = DeviceDiscovery(toolPaths, iphoneScreenCapturePool = CompletableDeferred(value = null), xcTestRunners = CompletableDeferred(value = null), emulatorScreens = EmulatorScreens(runningDirectories = emptyList()), xrEmulatorClients = XrEmulatorClients(runningDirectories = emptyList()))

        val look = async { discovery.discover() }
        runCurrent()
        assertFalse(look.isCompleted)
        toolPaths.complete(MirrorToolPaths(adbPath = fakeAdb.absolutePath, xcrunPath = null, ffmpegPath = null, iproxyPath = null))

        val found = look.await()
        assertEquals(listOf("emulator-5554"), found.devices.map(MirrorDevice::id))
        assertTrue(found.missingTools.none { it.startsWith("adb was not found") })
    }

    @Test
    fun `a listing that fails keeps the devices it listed before with their controllers`() = runBlocking {
        assumeShellScriptsLaunch()
        val discovery = DeviceDiscovery(CompletableDeferred(MirrorToolPaths(adbPath = fakeAdb.absolutePath, xcrunPath = null, ffmpegPath = null, iproxyPath = null)), iphoneScreenCapturePool = CompletableDeferred(value = null), xcTestRunners = CompletableDeferred(value = null), emulatorScreens = EmulatorScreens(runningDirectories = emptyList()), xrEmulatorClients = XrEmulatorClients(runningDirectories = emptyList()))
        val before = discovery.discover().devices.single()
        fakeAdb.writeText("#!/bin/sh\necho 'daemon not running' >&2\nexit 1\n")

        val after = discovery.discover().devices.single()

        assertSame(before.controller, after.controller)
    }

    @Test
    fun `a listing that succeeds without a device drops it`() = runBlocking {
        assumeShellScriptsLaunch()
        val discovery = DeviceDiscovery(CompletableDeferred(MirrorToolPaths(adbPath = fakeAdb.absolutePath, xcrunPath = null, ffmpegPath = null, iproxyPath = null)), iphoneScreenCapturePool = CompletableDeferred(value = null), xcTestRunners = CompletableDeferred(value = null), emulatorScreens = EmulatorScreens(runningDirectories = emptyList()), xrEmulatorClients = XrEmulatorClients(runningDirectories = emptyList()))
        discovery.discover()
        fakeAdb.writeText("#!/bin/sh\nprintf 'List of devices attached\\n'\n")

        assertEquals(emptyList(), discovery.discover().devices)
    }

    @Test
    fun `an emulator that lists the spatial XR feature is listed as an XR emulator once it can say so`() = runBlocking {
        assumeShellScriptsLaunch()
        val featuresFile = File(folder, "features.txt").apply { writeText("") }
        fakeAdb.writeText(
            """
            #!/bin/sh
            case "${'$'}*" in
              *"pm list features"*) cat '${featuresFile.path}' ;;
              *) printf 'List of devices attached\nemulator-5554\tdevice product:sdk_xr model:Android_XR device:emu\n' ;;
            esac
            """.trimIndent() + "\n",
        )
        val discovery = DeviceDiscovery(CompletableDeferred(MirrorToolPaths(adbPath = fakeAdb.absolutePath, xcrunPath = null, ffmpegPath = null, iproxyPath = null)), iphoneScreenCapturePool = CompletableDeferred(value = null), xcTestRunners = CompletableDeferred(value = null), emulatorScreens = EmulatorScreens(runningDirectories = emptyList()), xrEmulatorClients = XrEmulatorClients(runningDirectories = emptyList()))

        val starting = discovery.discover().devices.single()
        featuresFile.writeText("feature:android.software.xr.api.spatial=3\n")
        val started = discovery.discover().devices.single()

        assertIs<AndroidXrEmulatorDeviceController>(started.controller)
        assertEquals(DeviceKind.AndroidEmulator, starting.kind)
        assertEquals(DeviceKind.AndroidXrEmulator, started.kind)
    }

    @Test
    fun `an emulator started in place of another under the same serial is asked again whether it is an XR device`() = runBlocking {
        assumeShellScriptsLaunch()
        val modelFile = File(folder, "model.txt").apply { writeText("Android_XR") }
        val featuresFile = File(folder, "features.txt").apply { writeText("feature:android.software.xr.api.spatial=3\n") }
        fakeAdb.writeText(
            """
            #!/bin/sh
            case "${'$'}*" in
              *"pm list features"*) cat '${featuresFile.path}' ;;
              *) printf 'List of devices attached\nemulator-5554\tdevice product:sdk model:%s device:emu\n' "${'$'}(cat '${modelFile.path}')" ;;
            esac
            """.trimIndent() + "\n",
        )
        val discovery = DeviceDiscovery(CompletableDeferred(MirrorToolPaths(adbPath = fakeAdb.absolutePath, xcrunPath = null, ffmpegPath = null, iproxyPath = null)), iphoneScreenCapturePool = CompletableDeferred(value = null), xcTestRunners = CompletableDeferred(value = null), emulatorScreens = EmulatorScreens(runningDirectories = emptyList()), xrEmulatorClients = XrEmulatorClients(runningDirectories = emptyList()))

        val xrEmulator = discovery.discover().devices.single()
        modelFile.writeText("Pixel_9")
        featuresFile.writeText("feature:android.hardware.camera\n")
        val phoneEmulator = discovery.discover().devices.single()

        assertEquals(DeviceKind.AndroidXrEmulator, xrEmulator.kind)
        assertEquals(DeviceKind.AndroidEmulator, phoneEmulator.kind)
    }

    @Test
    fun `an XR emulator gets a controller that takes input once a gRPC token appears, keeps it, and loses input once the token goes`() = runBlocking {
        assumeShellScriptsLaunch()
        fakeAdb.writeText(
            """
            #!/bin/sh
            case "${'$'}*" in
              *"pm list features"*) printf 'feature:android.software.xr.api.spatial=3\n' ;;
              *) printf 'List of devices attached\nemulator-5554\tdevice product:sdk_xr model:Android_XR device:emu\n' ;;
            esac
            """.trimIndent() + "\n",
        )
        val runningDirectory = File(folder, "running").apply { mkdirs() }
        val discoveryFile = File(runningDirectory, "pid_1.ini").apply { writeText("port.serial=5554\ngrpc.port=8554\n") }
        val discovery = DeviceDiscovery(CompletableDeferred(MirrorToolPaths(adbPath = fakeAdb.absolutePath, xcrunPath = null, ffmpegPath = null, iproxyPath = null)), iphoneScreenCapturePool = CompletableDeferred(value = null), xcTestRunners = CompletableDeferred(value = null), emulatorScreens = EmulatorScreens(runningDirectories = emptyList()), xrEmulatorClients = XrEmulatorClients(runningDirectories = listOf(runningDirectory)))

        val withoutToken = discovery.discover().devices.single()
        discoveryFile.writeText("port.serial=5554\ngrpc.port=8556\ngrpc.token=secret\n")
        val withToken = discovery.discover().devices.single()
        val lookedAgain = discovery.discover().devices.single()
        discoveryFile.writeText("port.serial=5554\ngrpc.port=8558\n")
        val withoutTokenAgain = discovery.discover().devices.single()

        assertTrue(withoutToken.controller.capabilities.inputRefusal != null)
        assertNull(withToken.controller.capabilities.inputRefusal)
        assertSame(withToken.controller, lookedAgain.controller)
        assertTrue(withoutTokenAgain.controller.capabilities.inputRefusal != null)
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
        CompletableDeferred(MirrorToolPaths(adbPath = null, xcrunPath = fakeXcrun.absolutePath, ffmpegPath = "/usr/bin/true", iproxyPath = null)),
        xcTestRunners = CompletableDeferred(value = null),
        iphoneScreenCapturePool = CompletableDeferred(
            IphoneScreenCapturePool(
                processLauncher = { throw deviceControlError("no capture helper in tests") },
                idleTimeout = 1.minutes,
                failureRetentionPeriod = 1.minutes,
                timeSource = TimeSource.Monotonic,
                scope = captureScope,
                helperExecutable = CompletableDeferred(File("jetwhale-iphone-capture")),
            ),
        ),
        emulatorScreens = EmulatorScreens(runningDirectories = emptyList()),
        xrEmulatorClients = XrEmulatorClients(runningDirectories = emptyList()),
    )

    @Test
    fun `a machine without ffmpeg is told that Android devices fall back to screenshots and how to install it`() = runBlocking {
        val discovery = DeviceDiscovery(CompletableDeferred(MirrorToolPaths(adbPath = fakeAdb.absolutePath, xcrunPath = null, ffmpegPath = null, iproxyPath = null)), iphoneScreenCapturePool = CompletableDeferred(value = null), xcTestRunners = CompletableDeferred(value = null), emulatorScreens = EmulatorScreens(runningDirectories = emptyList()), xrEmulatorClients = XrEmulatorClients(runningDirectories = emptyList()))

        val notice = discovery.discover().missingTools.single { "ffmpeg" in it }

        assertContains(notice, "screenshots")
        assertContains(notice, "brew install ffmpeg")
    }

    @Test
    fun `a machine with ffmpeg is not told about it`() = runBlocking {
        val discovery = DeviceDiscovery(CompletableDeferred(MirrorToolPaths(adbPath = fakeAdb.absolutePath, xcrunPath = null, ffmpegPath = "/usr/bin/true", iproxyPath = null)), iphoneScreenCapturePool = CompletableDeferred(value = null), xcTestRunners = CompletableDeferred(value = null), emulatorScreens = EmulatorScreens(runningDirectories = emptyList()), xrEmulatorClients = XrEmulatorClients(runningDirectories = emptyList()))

        assertTrue(discovery.discover().missingTools.none { "ffmpeg" in it })
    }
}

private const val SIMULTANEOUS_LOOKS = 4
