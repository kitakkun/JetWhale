package com.kitakkun.jetwhale.plugins.mirror.host

/** Where Open takes a capture. */
internal enum class CaptureDestination {
    /** Its device's captures panel, with the capture selected. */
    Panel,

    /** Its file in its folder, since its device is not connected and the panel cannot show it. */
    Folder,

    /** Nowhere: its file is gone. */
    Missing,
}

internal fun destinationOf(capture: Capture, connectedDeviceIds: Set<String>): CaptureDestination = when {
    !capture.file.exists() -> CaptureDestination.Missing
    capture.info.deviceId in connectedDeviceIds -> CaptureDestination.Panel
    else -> CaptureDestination.Folder
}
