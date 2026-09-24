package com.kitakkun.jetwhale.plugins.mirror.host

internal enum class DevicePlatform(val label: String) {
    Android("Android"),
    Ios("iOS"),
}

internal enum class DeviceKind(val platform: DevicePlatform, val label: String) {
    AndroidEmulator(DevicePlatform.Android, "Emulator"),
    AndroidDevice(DevicePlatform.Android, "Device"),
    IosSimulator(DevicePlatform.Ios, "Simulator"),
    IosDevice(DevicePlatform.Ios, "Device"),
}

internal enum class DeviceButton(val label: String) {
    Home("Home"),
    Back("Back"),
    Power("Power"),
    VolumeUp("Volume up"),
    VolumeDown("Volume down"),
}

/**
 * What can be done to a device besides watching it. A physical iOS device is watch-only: idb
 * streams its screen but drives input only on simulators.
 */
internal data class DeviceCapabilities(
    val input: Boolean,
    val buttons: List<DeviceButton>,
    val recording: Boolean,
)

/** A device found on this machine, as the picker lists it. */
internal data class DeviceListing(
    val id: String,
    val name: String,
    val kind: DeviceKind,
    val osVersion: String?,
)

/** A device that can be mirrored: what it is, and how to drive it. */
internal class MirrorDevice(
    val listing: DeviceListing,
    val controller: DeviceController,
) {
    val id: String get() = listing.id
    val name: String get() = listing.name
    val kind: DeviceKind get() = listing.kind
}
