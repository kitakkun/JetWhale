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
 * physical iPhone's input also needs `iproxy` at [iproxyPath]. An iPhone's screen comes from
 * [iphoneScreenCaptures], also null without Xcode, which stop the capture of an iPhone once it is gone.
 *
 * A look waits for every tool it is given, so a tool is reported missing only once it has been
 * looked for.
 */
internal class DeviceDiscovery(
    private val toolPaths: Deferred<MirrorToolPaths>,
    private val iphoneScreenCaptures: Deferred<IphoneScreenCaptures?>,
    private val xcTestRunners: Deferred<XcTestRunners?>,
    private val iproxyPath: Deferred<String?>,
    private val emulatorScreens: EmulatorScreens,
) {
    private val known = mutableMapOf<String, MirrorDevice>()

    private val looking = Mutex()

    suspend fun discover(): Discovery = looking.withLock {
        val locatedToolPaths = toolPaths.await()
        val iphoneScreenCaptures = iphoneScreenCaptures.await()
        val runnerInput = xcTestRunners.await()?.let(::XcTestRunnerInput)
        val looks = listOf(
            listAndroid(locatedToolPaths) to setOf(DeviceKind.AndroidEmulator, DeviceKind.AndroidDevice),
            listSimulators(locatedToolPaths) to setOf(DeviceKind.IosSimulator),
            listIosDevices(locatedToolPaths) to setOf(DeviceKind.IosDevice),
        )
        val listings = looks.flatMap { (listed, kinds) -> listed ?: known.values.filter { it.listing.kind in kinds }.map(MirrorDevice::listing) }
        val devices = listings.map { listing -> known[listing.id]?.takeIf { it.listing == listing } ?: MirrorDevice(listing, controllerFor(listing, locatedToolPaths, iphoneScreenCaptures, runnerInput)) }
        val gone = known.values.filter { known -> devices.none { it.id == known.id } }
        gone.filter { it.listing.kind == DeviceKind.IosDevice }.forEach { iphoneScreenCaptures?.stopCaptureEvenIfInUse(it.id) }
        known.keys.retainAll(devices.map(MirrorDevice::id).toSet())
        devices.forEach { known[it.id] = it }
        val isIphoneListed = devices.any { it.kind == DeviceKind.IosDevice }
        Discovery(devices = devices, missingTools = missingTools(locatedToolPaths, runnerInput, iproxyPath.await(), isIphoneListed))
    }

    private suspend fun listAndroid(locatedToolPaths: MirrorToolPaths): List<DeviceListing>? {
        val adbPath = locatedToolPaths.adbPath ?: return emptyList()
        return tryList { parseAdbDevices(runCommandChecked(adbPath, "devices", "-l").stdoutText) }
    }

    private suspend fun listSimulators(locatedToolPaths: MirrorToolPaths): List<DeviceListing>? {
        val xcrunPath = locatedToolPaths.xcrunPath ?: return emptyList()
        return tryList { parseBootedSimulators(runCommandChecked(xcrunPath, "simctl", "list", "devices", "booted", "-j").stdoutText) }
    }

    private suspend fun listIosDevices(locatedToolPaths: MirrorToolPaths): List<DeviceListing>? {
        val xcrunPath = locatedToolPaths.xcrunPath ?: return emptyList()
        return tryList {
            withContext(Dispatchers.IO) {
                // devicectl writes JSON only to a file; what it prints is for people and may change.
                val jsonFile = createTempFile(prefix = "jetwhale-devicectl-", suffix = ".json")
                try {
                    runCommandChecked(xcrunPath, "devicectl", "list", "devices", "--json-output", jsonFile.toString())
                    parseDevicectlDevices(jsonFile.readText())
                } finally {
                    jsonFile.deleteIfExists()
                }
            }
        }
    }

    private fun controllerFor(listing: DeviceListing, locatedToolPaths: MirrorToolPaths, iphoneScreenCaptures: IphoneScreenCaptures?, runnerInput: XcTestRunnerInput?): DeviceController = when (listing.kind) {
        DeviceKind.AndroidEmulator -> AndroidDeviceController(adbPath = checkNotNull(locatedToolPaths.adbPath), serial = listing.id, emulatorScreens = emulatorScreens, ffmpegPath = locatedToolPaths.ffmpegPath)

        DeviceKind.AndroidDevice -> AndroidDeviceController(adbPath = checkNotNull(locatedToolPaths.adbPath), serial = listing.id, emulatorScreens = null, ffmpegPath = locatedToolPaths.ffmpegPath)

        DeviceKind.IosSimulator -> IosSimulatorDeviceController(
            udid = listing.id,
            iosMajorVersion = majorVersionOf(listing.osVersion),
            xcrunPath = checkNotNull(locatedToolPaths.xcrunPath),
            runnerInput = runnerInput,
        )

        DeviceKind.IosDevice -> IosPhysicalDeviceController(
            udid = listing.id,
            deviceName = listing.name,
            iosMajorVersion = majorVersionOf(listing.osVersion),
            iphoneScreenCaptures = checkNotNull(iphoneScreenCaptures),
            ffmpegPath = locatedToolPaths.ffmpegPath,
            runnerInput = runnerInput,
        )
    }

    private fun missingTools(locatedToolPaths: MirrorToolPaths, runnerInput: XcTestRunnerInput?, iproxyPath: String?, isIphoneListed: Boolean): List<String> = buildList {
        if (locatedToolPaths.adbPath == null) add("adb was not found, so Android devices are not listed. Install the Android SDK platform tools.")
        if (isIphoneListed && runnerInput != null && iproxyPath == null) add("iproxy was not found, so iPhones and iPads are shown without input: $IPROXY_INSTALL")
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
