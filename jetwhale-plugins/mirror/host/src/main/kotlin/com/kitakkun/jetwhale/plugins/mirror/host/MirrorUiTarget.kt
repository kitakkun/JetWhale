package com.kitakkun.jetwhale.plugins.mirror.host

/**
 * A control of the mirror that an MCP command operates. Each command marks the target of the control
 * a user would use for the same thing, and the mirror rings that control while the command runs.
 */
internal sealed interface MirrorUiTarget {
    data object DevicePicker : MirrorUiTarget

    /** The live view, where a tap or a swipe lands. */
    data object Screen : MirrorUiTarget

    data class HardwareButton(val button: DeviceButton) : MirrorUiTarget

    data object ScreenPowerButton : MirrorUiTarget

    data object TextInput : MirrorUiTarget

    /** The record button, or the stop button that replaces it while a recording runs. */
    data object RecordingButton : MirrorUiTarget

    data object ScreenshotButton : MirrorUiTarget

    data object CaptureList : MirrorUiTarget
}
