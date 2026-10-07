package com.kitakkun.jetwhale.plugins.mirror.host

import kotlinx.coroutines.Deferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** What one look for devices found, and what kept it from finding more. */
internal class Discovery(
    val devices: List<MirrorDevice>,
    val missingTools: List<String>,
)

/**
 * Lists Android emulators and devices through adb, booted iOS simulators through simctl, and USB
 * iOS devices through idb_companion. A device keeps its controller from one look to the next, so a
 * device that is streaming keeps what its stream holds, such as an idb companion.
 *
 * A look waits for [toolPaths] and [companions], so a tool is reported missing only once it has
 * been looked for.
 */
internal class DeviceDiscovery(
    private val toolPaths: Deferred<MirrorToolPaths>,
    private val companions: Deferred<IdbCompanions?>,
    private val emulatorScreens: EmulatorScreens,
) {
    private val known = mutableMapOf<String, MirrorDevice>()

    private val looking = Mutex()

    suspend fun discover(): Discovery = looking.withLock {
        val locatedToolPaths = toolPaths.await()
        val sharedCompanions = companions.await()
        val looks = listOf(
            listAndroid(locatedToolPaths) to setOf(DeviceKind.AndroidEmulator, DeviceKind.AndroidDevice),
            listSimulators(locatedToolPaths) to setOf(DeviceKind.IosSimulator),
            listIosDevices(locatedToolPaths, sharedCompanions) to setOf(DeviceKind.IosDevice),
        )
        // A tool that fails to list (adb's server starting, say) keeps the devices it listed before,
        // so a passing failure neither drops the selected device nor forgets an iPhone's companion.
        val listings = looks.flatMap { (listed, kinds) -> listed ?: known.values.filter { it.listing.kind in kinds }.map(MirrorDevice::listing) }
        val devices = listings.map { listing -> known[listing.id]?.takeIf { it.listing == listing } ?: MirrorDevice(listing, controllerFor(listing, locatedToolPaths, sharedCompanions)) }
        val gone = known.values.filter { known -> devices.none { it.id == known.id } }
        gone.filter { it.listing.kind == DeviceKind.IosDevice }.forEach { sharedCompanions?.stopCompanionEvenIfInUse(it.id) }
        known.keys.retainAll(devices.map(MirrorDevice::id).toSet())
        devices.forEach { known[it.id] = it }
        Discovery(devices = devices, missingTools = missingTools(locatedToolPaths))
    }

    private suspend fun listAndroid(locatedToolPaths: MirrorToolPaths): List<DeviceListing>? {
        val adbPath = locatedToolPaths.adbPath ?: return emptyList()
        return tryList { parseAdbDevices(runCommandChecked(adbPath, "devices", "-l").stdoutText) }
    }

    private suspend fun listSimulators(locatedToolPaths: MirrorToolPaths): List<DeviceListing>? {
        val xcrunPath = locatedToolPaths.xcrunPath ?: return emptyList()
        return tryList { parseBootedSimulators(runCommandChecked(xcrunPath, "simctl", "list", "devices", "booted", "-j").stdoutText) }
    }

    private suspend fun listIosDevices(locatedToolPaths: MirrorToolPaths, sharedCompanions: IdbCompanions?): List<DeviceListing>? {
        val idbPath = locatedToolPaths.idbPath ?: return emptyList()
        val idbCompanionPath = locatedToolPaths.idbCompanionPath ?: return emptyList()
        if (sharedCompanions == null) return emptyList()
        // idb list-targets goes through idb's Python client, which takes 2–7 s and 0.45–0.73 s of
        // CPU on every look; the companion lists the same devices in about 0.7 s for 0.25 s of CPU.
        // idb stays for a companion that fails or prints output this does not read.
        return tryList { parseCompanionDevices(runCommandChecked(idbCompanionPath, "--list", "1", "--only", "device").stdoutText) }
            ?: tryList { parseIdbDevices(runCommandChecked(idbPath, "list-targets").stdoutText) }
    }

    private fun controllerFor(listing: DeviceListing, locatedToolPaths: MirrorToolPaths, sharedCompanions: IdbCompanions?): DeviceController = when (listing.kind) {
        DeviceKind.AndroidEmulator -> AndroidDeviceController(adbPath = checkNotNull(locatedToolPaths.adbPath), serial = listing.id, emulatorScreens = emulatorScreens, ffmpegPath = locatedToolPaths.ffmpegPath)
        DeviceKind.AndroidDevice -> AndroidDeviceController(adbPath = checkNotNull(locatedToolPaths.adbPath), serial = listing.id, emulatorScreens = null, ffmpegPath = locatedToolPaths.ffmpegPath)
        DeviceKind.IosSimulator -> IosSimulatorDeviceController(udid = listing.id, xcrunPath = checkNotNull(locatedToolPaths.xcrunPath), idbPath = locatedToolPaths.idbPath)
        DeviceKind.IosDevice -> IosPhysicalDeviceController(udid = listing.id, idbPath = checkNotNull(locatedToolPaths.idbPath), companions = checkNotNull(sharedCompanions), ffmpegPath = locatedToolPaths.ffmpegPath)
    }

    private fun missingTools(locatedToolPaths: MirrorToolPaths): List<String> = buildList {
        if (locatedToolPaths.adbPath == null) add("adb was not found, so Android devices are not listed. Install the Android SDK platform tools.")
        if (locatedToolPaths.xcrunPath != null && locatedToolPaths.idbPath == null) add("idb was not found, so iOS simulators are shown without live video or input, and iOS devices are not listed. $IDB_MISSING")
        if (locatedToolPaths.idbPath != null && locatedToolPaths.idbCompanionPath == null) add("idb_companion was not found, so iOS devices are not listed: $IDB_INSTALL")
        if (locatedToolPaths.ffmpegPath == null && (locatedToolPaths.adbPath != null || locatedToolPaths.idbCompanionPath != null)) add("ffmpeg was not found, so Android devices, and emulators without their own screen stream, are shown through screenshots at a few frames a second, and iOS devices cannot be mirrored. $FFMPEG_INSTALL")
    }

    private suspend fun tryList(list: suspend () -> List<DeviceListing>?): List<DeviceListing>? = try {
        list()
    } catch (_: DeviceControlException) {
        null
    }
}
