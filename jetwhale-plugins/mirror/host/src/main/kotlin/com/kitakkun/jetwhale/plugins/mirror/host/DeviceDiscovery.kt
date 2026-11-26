package com.kitakkun.jetwhale.plugins.mirror.host

import androidx.annotation.VisibleForTesting
import com.kitakkun.jetwhale.plugins.xctestrunner.XcTestRunners
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import kotlin.io.path.createTempFile
import kotlin.io.path.deleteIfExists
import kotlin.io.path.readText
import kotlin.time.Duration.Companion.seconds

/**
 * How long a tool may take to list its devices. Listing can first start adb's server, or the services
 * behind simctl and devicectl. A look waits for every listing, so one that never finished would stop
 * the device list from updating.
 */
private val DEVICE_LISTING_TIMEOUT = 30.seconds

/** What one look for devices found, and what kept it from finding more. */
internal class Discovery(
    val devices: List<MirrorDevice>,
    val missingTools: List<String>,
)

/**
 * Lists Android emulators and devices through adb, and booted iOS simulators and USB iOS devices
 * through Xcode's simctl and devicectl. A device keeps its controller from one look to the next, so a
 * device that is streaming keeps what its stream holds.
 *
 * iOS input, and a simulator's live video, go through [xcTestRunners], null without Xcode; a
 * physical iPhone's input also needs `iproxy`. An iPhone's screen comes from [iphoneScreenCapturePool],
 * also null without Xcode, which stops the capture of an iPhone once it is gone.
 *
 * An Android device is asked once whether it is an Android XR device, which takes input in its
 * own way; an XR emulator's comes through [xrEmulatorClients].
 *
 * A look waits for every tool it is given, so a tool is reported missing only once it has been
 * looked for.
 */
internal class DeviceDiscovery(
    private val toolPaths: Deferred<MirrorToolPaths>,
    private val iphoneScreenCapturePool: Deferred<IphoneScreenCapturePool?>,
    private val xcTestRunners: Deferred<XcTestRunners?>,
    private val emulatorScreens: EmulatorScreens,
    private val xrEmulatorClients: XrEmulatorClients,
) {
    private val known = mutableMapOf<String, MirrorDevice>()

    /** Whether each Android device listed is an Android XR device, by serial, once it has said. */
    private val isXrDeviceBySerial = mutableMapOf<String, Boolean>()

    private val looking = Mutex()

    suspend fun discover(): Discovery = looking.withLock {
        val locatedToolPaths = toolPaths.await()
        val iphoneScreenCapturePool = iphoneScreenCapturePool.await()
        val xcTestRunnerClient = xcTestRunners.await()?.let(::XcTestRunnerClient)
        val looks = listOf(
            listAndroid(locatedToolPaths) to setOf(DeviceKind.AndroidEmulator, DeviceKind.AndroidDevice, DeviceKind.AndroidXrEmulator, DeviceKind.AndroidXrDevice),
            listSimulators(locatedToolPaths) to setOf(DeviceKind.IosSimulator),
            listIosDevices(locatedToolPaths) to setOf(DeviceKind.IosDevice),
        )
        val listings = looks.flatMap { (listed, kinds) -> listed ?: known.values.filter { it.listing.kind in kinds }.map(MirrorDevice::listing) }
        val devices = listings.map { listing -> known[listing.id]?.takeIf { it.listing == listing } ?: MirrorDevice(listing, controllerFor(listing, locatedToolPaths, iphoneScreenCapturePool, xcTestRunnerClient)) }
        val gone = known.values.filter { known -> devices.none { it.id == known.id } }
        gone.filter { it.listing.kind == DeviceKind.IosDevice }.forEach { iphoneScreenCapturePool?.stopCaptureEvenIfInUse(it.id) }
        known.keys.retainAll(devices.map(MirrorDevice::id).toSet())
        devices.forEach { known[it.id] = it }
        val isIphoneListed = devices.any { it.kind == DeviceKind.IosDevice }
        Discovery(devices = devices, missingTools = missingTools(locatedToolPaths, xcTestRunnerClient, isIphoneListed))
    }

    private suspend fun listAndroid(locatedToolPaths: MirrorToolPaths): List<DeviceListing>? {
        val adbPath = locatedToolPaths.adbPath ?: return emptyList()
        val listings = tryList { parseAdbDevices(runCommandChecked(DEVICE_LISTING_TIMEOUT, adbPath, "devices", "-l").stdoutText) } ?: return null
        isXrDeviceBySerial.keys.retainAll(listings.map(DeviceListing::id).toSet())
        return listings.map { listing ->
            val isXrDevice = isXrDeviceBySerial[listing.id] ?: readIsXrDevice(adbPath, listing.id)?.also { isXrDeviceBySerial[listing.id] = it }
            when {
                isXrDevice != true -> listing
                listing.kind == DeviceKind.AndroidEmulator -> listing.copy(kind = DeviceKind.AndroidXrEmulator)
                else -> listing.copy(kind = DeviceKind.AndroidXrDevice)
            }
        }
    }

    /** Whether the Android device with [serial] is an Android XR device, or null while it cannot say, as when it is still starting. */
    private suspend fun readIsXrDevice(adbPath: String, serial: String): Boolean? = try {
        isXrSpatialFeatureListed(runCommandChecked(DEVICE_LISTING_TIMEOUT, adbPath, "-s", serial, "shell", "pm", "list", "features").stdoutText)
    } catch (_: DeviceControlException) {
        null
    }

    private suspend fun listSimulators(locatedToolPaths: MirrorToolPaths): List<DeviceListing>? {
        val xcrunPath = locatedToolPaths.xcrunPath ?: return emptyList()
        return tryList { parseBootedSimulators(runCommandChecked(DEVICE_LISTING_TIMEOUT, xcrunPath, "simctl", "list", "devices", "booted", "-j").stdoutText) }
    }

    private suspend fun listIosDevices(locatedToolPaths: MirrorToolPaths): List<DeviceListing>? {
        val xcrunPath = locatedToolPaths.xcrunPath ?: return emptyList()
        return tryList {
            withContext(Dispatchers.IO) {
                // devicectl writes its JSON only to a file; what it prints is for people and may
                // change.
                val jsonFile = createTempFile(prefix = "jetwhale-devicectl-", suffix = ".json")
                try {
                    runCommandChecked(DEVICE_LISTING_TIMEOUT, xcrunPath, "devicectl", "list", "devices", "--json-output", jsonFile.toString())
                    parseDevicectlDevices(jsonFile.readText())
                } finally {
                    jsonFile.deleteIfExists()
                }
            }
        }
    }

    private suspend fun controllerFor(listing: DeviceListing, locatedToolPaths: MirrorToolPaths, iphoneScreenCapturePool: IphoneScreenCapturePool?, xcTestRunnerClient: XcTestRunnerClient?): DeviceController = when (listing.kind) {
        DeviceKind.AndroidEmulator -> AndroidDeviceController(adbPath = checkNotNull(locatedToolPaths.adbPath), serial = listing.id, emulatorScreens = emulatorScreens, ffmpegPath = locatedToolPaths.ffmpegPath)

        DeviceKind.AndroidDevice -> AndroidDeviceController(adbPath = checkNotNull(locatedToolPaths.adbPath), serial = listing.id, emulatorScreens = null, ffmpegPath = locatedToolPaths.ffmpegPath)

        DeviceKind.AndroidXrEmulator -> AndroidXrEmulatorDeviceController(
            androidDeviceController = AndroidDeviceController(adbPath = checkNotNull(locatedToolPaths.adbPath), serial = listing.id, emulatorScreens = emulatorScreens, ffmpegPath = locatedToolPaths.ffmpegPath),
            xrEmulatorClient = xrEmulatorClients.clientFor(listing.id),
        )

        DeviceKind.AndroidXrDevice -> AndroidXrHeadsetDeviceController(AndroidDeviceController(adbPath = checkNotNull(locatedToolPaths.adbPath), serial = listing.id, emulatorScreens = null, ffmpegPath = locatedToolPaths.ffmpegPath))

        DeviceKind.IosSimulator -> IosSimulatorDeviceController(
            udid = listing.id,
            iosMajorVersion = majorVersionOf(listing.osVersion),
            xcrunPath = checkNotNull(locatedToolPaths.xcrunPath),
            xcTestRunnerClient = xcTestRunnerClient,
        )

        DeviceKind.IosDevice -> IosPhysicalDeviceController(
            udid = listing.id,
            deviceName = listing.name,
            iosMajorVersion = majorVersionOf(listing.osVersion),
            iphoneScreenCapturePool = checkNotNull(iphoneScreenCapturePool),
            ffmpegPath = locatedToolPaths.ffmpegPath,
            xcTestRunnerClient = xcTestRunnerClient,
        )
    }

    private fun missingTools(locatedToolPaths: MirrorToolPaths, xcTestRunnerClient: XcTestRunnerClient?, isIphoneListed: Boolean): List<String> = buildList {
        if (locatedToolPaths.adbPath == null) add("adb was not found, so Android devices are not listed. Install the Android SDK platform tools.")
        if (isIphoneListed && xcTestRunnerClient != null && locatedToolPaths.iproxyPath == null) add("iproxy was not found, so iPhones and iPads are shown without input: $IPROXY_INSTALL")
        if (locatedToolPaths.ffmpegPath == null && (locatedToolPaths.adbPath != null || isIphoneListed)) add("ffmpeg was not found, so Android devices, and emulators without their own screen stream, are shown through screenshots at a few frames a second, and iOS devices cannot be mirrored. $FFMPEG_INSTALL")
    }

    private suspend fun tryList(list: suspend () -> List<DeviceListing>?): List<DeviceListing>? = try {
        list()
    } catch (_: DeviceControlException) {
        null
    } catch (_: IOException) {
        null
    }
}

private const val IPROXY_INSTALL = "brew install libimobiledevice"

/** The major version in [osVersion], such as 17 for `iOS 17.5`; null when it has none. */
@VisibleForTesting
internal fun majorVersionOf(osVersion: String?): Int? = osVersion?.let { Regex("""\d+""").find(it)?.value?.toIntOrNull() }
