package com.kitakkun.jetwhale.plugins.mirror.host

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** What one look for devices found, and what kept it from finding more. */
internal class Discovery(
    val devices: List<MirrorDevice>,
    val missingTools: List<String>,
)

/**
 * Lists Android emulators and devices through adb, booted iOS simulators through simctl, and USB
 * iOS devices through idb. A device keeps its controller from one look to the next, so a device
 * that is streaming keeps what its stream holds, such as an idb companion.
 */
internal class DeviceDiscovery(
    private val toolPaths: MirrorToolPaths,
    private val companions: IdbCompanions?,
    private val emulatorScreens: EmulatorScreens,
) {
    private val known = mutableMapOf<String, MirrorDevice>()

    // The mirror's refresh loop and the listDevices tool look at the same time; one look at a time
    // keeps a device from getting two controllers.
    private val looking = Mutex()

    suspend fun discover(): Discovery = looking.withLock {
        val looks = listOf(
            listAndroid() to setOf(DeviceKind.AndroidEmulator, DeviceKind.AndroidDevice),
            listSimulators() to setOf(DeviceKind.IosSimulator),
            listIosDevices() to setOf(DeviceKind.IosDevice),
        )
        // A tool that fails to list (adb's server starting, say) keeps the devices it listed before,
        // so a passing failure neither drops the selected device nor forgets an iPhone's companion.
        val listings = looks.flatMap { (listed, kinds) -> listed ?: known.values.filter { it.listing.kind in kinds }.map(MirrorDevice::listing) }
        val devices = listings.map { listing -> known[listing.id]?.takeIf { it.listing == listing } ?: MirrorDevice(listing, controllerFor(listing)) }
        val gone = known.values.filter { known -> devices.none { it.id == known.id } }
        gone.filter { it.listing.kind == DeviceKind.IosDevice }.forEach { companions?.forget(it.id) }
        known.keys.retainAll(devices.map(MirrorDevice::id).toSet())
        devices.forEach { known[it.id] = it }
        Discovery(devices = devices, missingTools = missingTools())
    }

    private suspend fun listAndroid(): List<DeviceListing>? {
        val adbPath = toolPaths.adbPath ?: return emptyList()
        return tryList { parseAdbDevices(runCommandChecked(adbPath, "devices", "-l").stdoutText) }
    }

    private suspend fun listSimulators(): List<DeviceListing>? {
        val xcrunPath = toolPaths.xcrunPath ?: return emptyList()
        return tryList { parseBootedSimulators(runCommandChecked(xcrunPath, "simctl", "list", "devices", "booted", "-j").stdoutText) }
    }

    private suspend fun listIosDevices(): List<DeviceListing>? {
        val idbPath = toolPaths.idbPath ?: return emptyList()
        if (companions == null) return emptyList()
        return tryList { parseIdbDevices(runCommandChecked(idbPath, "list-targets").stdoutText) }
    }

    private fun controllerFor(listing: DeviceListing): DeviceController = when (listing.kind) {
        DeviceKind.AndroidEmulator -> AndroidDeviceController(adbPath = checkNotNull(toolPaths.adbPath), serial = listing.id, emulatorScreens = emulatorScreens, ffmpegPath = toolPaths.ffmpegPath)
        DeviceKind.AndroidDevice -> AndroidDeviceController(adbPath = checkNotNull(toolPaths.adbPath), serial = listing.id, emulatorScreens = null, ffmpegPath = toolPaths.ffmpegPath)
        DeviceKind.IosSimulator -> IosSimulatorDeviceController(udid = listing.id, xcrunPath = checkNotNull(toolPaths.xcrunPath), idbPath = toolPaths.idbPath)
        DeviceKind.IosDevice -> IosPhysicalDeviceController(udid = listing.id, idbPath = checkNotNull(toolPaths.idbPath), companions = checkNotNull(companions), ffmpegPath = toolPaths.ffmpegPath)
    }

    private fun missingTools(): List<String> = buildList {
        if (toolPaths.adbPath == null) add("adb was not found, so Android devices are not listed. Install the Android SDK platform tools.")
        if (toolPaths.xcrunPath != null && toolPaths.idbPath == null) add("idb was not found, so iOS simulators are shown without live video or input, and iOS devices are not listed. $IDB_MISSING")
        if (toolPaths.idbPath != null && toolPaths.idbCompanionPath == null) add("idb_companion was not found, so iOS devices are not listed: $IDB_COMPANION_INSTALL")
        if (toolPaths.ffmpegPath == null && (toolPaths.adbPath != null || toolPaths.idbCompanionPath != null)) add("ffmpeg was not found, so Android devices, and emulators without their own screen stream, are shown through screenshots at a few frames a second, and iOS devices cannot be mirrored. $FFMPEG_INSTALL")
    }

    private suspend fun tryList(list: suspend () -> List<DeviceListing>): List<DeviceListing>? = try {
        list()
    } catch (_: DeviceControlException) {
        null
    }
}
