package com.kitakkun.jetwhale.plugins.mirror.host

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
    private val tools: MirrorTools,
    private val companions: IdbCompanions?,
) {
    private val known = mutableMapOf<String, MirrorDevice>()

    suspend fun discover(): Discovery {
        val listings = listAndroid() + listSimulators() + listIosDevices()
        val devices = listings.map { listing -> known[listing.id]?.takeIf { it.listing == listing } ?: MirrorDevice(listing, controllerFor(listing)) }
        known.keys.retainAll(devices.map(MirrorDevice::id).toSet())
        devices.forEach { known[it.id] = it }
        return Discovery(devices = devices, missingTools = missingTools())
    }

    private suspend fun listAndroid(): List<DeviceListing> {
        val adb = tools.adb ?: return emptyList()
        return tryList { parseAdbDevices(runCommandChecked(adb, "devices", "-l").stdoutText) }
    }

    private suspend fun listSimulators(): List<DeviceListing> {
        val xcrun = tools.xcrun ?: return emptyList()
        return tryList { parseBootedSimulators(runCommandChecked(xcrun, "simctl", "list", "devices", "booted", "-j").stdoutText) }
    }

    private suspend fun listIosDevices(): List<DeviceListing> {
        val idb = tools.idb ?: return emptyList()
        if (companions == null) return emptyList()
        return tryList { parseIdbDevices(runCommandChecked(idb, "list-targets").stdoutText) }
    }

    private fun controllerFor(listing: DeviceListing): DeviceController = when (listing.kind) {
        DeviceKind.AndroidEmulator, DeviceKind.AndroidDevice -> AndroidDeviceController(adb = checkNotNull(tools.adb), serial = listing.id)
        DeviceKind.IosSimulator -> IosSimulatorController(udid = listing.id, xcrun = checkNotNull(tools.xcrun), idb = tools.idb)
        DeviceKind.IosDevice -> IosDeviceController(udid = listing.id, idb = checkNotNull(tools.idb), companions = checkNotNull(companions))
    }

    private fun missingTools(): List<String> = buildList {
        if (tools.adb == null) add("adb was not found, so Android devices are not listed. Install the Android SDK platform tools.")
        if (tools.xcrun != null && tools.idb == null) add("idb was not found, so iOS simulators are shown without live video or input, and iOS devices are not listed. $IDB_MISSING")
        if (tools.idb != null && tools.idbCompanion == null) add("idb_companion was not found, so iOS devices are not listed: brew install idb-companion")
    }

    // A tool that fails to list (adb's server starting, say) leaves its devices out of this look only.
    private suspend fun tryList(list: suspend () -> List<DeviceListing>): List<DeviceListing> = try {
        list()
    } catch (_: DeviceControlException) {
        emptyList()
    }
}
