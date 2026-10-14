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
    Recents("Recent apps"),
    Power("Power"),
    VolumeUp("Volume up"),
    VolumeDown("Volume down"),
}

/**
 * What can be done to a device besides watching it.
 *
 * @property inputRefusal why the device takes no taps, swipes, buttons or text right now, or null
 *   when it takes them. A physical iPhone takes them only once a development team is set to sign
 *   the XCTest runner, and `iproxy` can reach it.
 * @property screenPower whether the screen's power can be read and switched. Android only: a
 *   simulator's screen never turns off, and nothing here can wake a physical iOS device.
 */
internal data class DeviceCapabilities(
    val inputRefusal: String?,
    val buttons: List<DeviceButton>,
    val recording: Boolean,
    val screenPower: Boolean,
)

/** The refusal for a device whose [DeviceCapabilities.screenPower] is false. */
internal const val NO_SCREEN_POWER = "an iOS device's screen cannot be switched on or off from here; only Android devices support it"

/** Whether the device's screen is on, and whether a lock screen covers it. */
internal data class ScreenPower(val awake: Boolean, val locked: Boolean)

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
