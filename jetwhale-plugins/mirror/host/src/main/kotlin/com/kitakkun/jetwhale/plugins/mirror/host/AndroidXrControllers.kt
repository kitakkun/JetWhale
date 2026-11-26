package com.kitakkun.jetwhale.plugins.mirror.host

import androidx.annotation.VisibleForTesting
import kotlin.math.atan
import kotlin.math.tan

/**
 * The Android XR emulator. Its screen is a view into a 3D scene where apps float as panels, and
 * adb's `input tap` lands on an invisible SystemUI window in front of them all, which hides every
 * panel; a panel's own views sit in coordinates unrelated to where the view draws it. So taps,
 * swipes and the view go through [xrEmulator], whose hand points and pinches where the view shows
 * something. An emulator whose gRPC endpoint takes no token from here has no [xrEmulator], and then
 * takes no input. Buttons, text, the stream and recordings are [androidDeviceController]'s.
 */
internal class AndroidXrEmulatorController(
    private val androidDeviceController: AndroidDeviceController,
    private val xrEmulator: XrEmulatorClient?,
) : DeviceController by androidDeviceController,
    XrViewController {
    override val capabilities = androidDeviceController.capabilities.copy(
        inputRefusal = XR_EMULATOR_WITHOUT_TOKEN.takeIf { xrEmulator == null },
        buttons = if (xrEmulator == null) emptyList() else listOf(DeviceButton.Home, DeviceButton.Back, DeviceButton.Recents, DeviceButton.AllApps, DeviceButton.Power, DeviceButton.VolumeUp, DeviceButton.VolumeDown),
    )

    // `screencap` draws the scene on its own, two pixels narrower and a few pixels apart at the
    // edges from the framebuffer that the stream shows and the hand points into.
    override suspend fun captureScreenshot(): ByteArray = xrEmulator?.captureScreenshotPng() ?: androidDeviceController.captureScreenshot()

    override suspend fun tap(x: Int, y: Int) = requireXrEmulator().tap(x, y)

    override suspend fun swipe(fromX: Int, fromY: Int, toX: Int, toY: Int, durationMillis: Int) = requireXrEmulator().swipe(fromX = fromX, fromY = fromY, toX = toX, toY = toY, durationMillis = durationMillis)

    override suspend fun pressButton(button: DeviceButton) {
        requireXrEmulator()
        androidDeviceController.pressButton(button)
    }

    override suspend fun inputText(text: String) {
        requireXrEmulator()
        androidDeviceController.inputText(text)
    }

    override suspend fun turnView(rightDegrees: Double, upDegrees: Double) = requireXrEmulator().turnHead(upRadians = Math.toRadians(upDegrees).toFloat(), leftRadians = -Math.toRadians(rightDegrees).toFloat())

    override suspend fun turnViewWithDrag(dragX: Float, dragY: Float) = turnView(rightDegrees = -xrEmulatorDegreesAcross(dragX), upDegrees = xrEmulatorDegreesAcross(dragY))

    override suspend fun recenterView() = requireXrEmulator().recenterHead()

    // The emulator may round the coefficient before applying it, so it shows the room from one half up.
    override suspend fun isPassthroughOn(): Boolean = requireXrEmulator().readXrOptions().passthroughCoefficient >= 0.5f

    override suspend fun setPassthrough(on: Boolean) {
        val xrEmulator = requireXrEmulator()
        // Every field of the options is written, so the environment and dimming are written back as they are.
        xrEmulator.writeXrOptions(xrEmulator.readXrOptions().copy(passthroughCoefficient = if (on) 1f else 0f))
    }

    private fun requireXrEmulator(): XrEmulatorClient = xrEmulator ?: throw deviceControlError(XR_EMULATOR_WITHOUT_TOKEN)
}

/**
 * The angle at the middle of the XR emulator's view that a drag across [widths] of its screen spans:
 * the emulator projects [XR_EMULATOR_FIELD_OF_VIEW_DEGREES] across the screen's width as a pinhole
 * camera does, the same scale up and down.
 */
@VisibleForTesting
internal fun xrEmulatorDegreesAcross(widths: Float): Double = Math.toDegrees(atan(widths * 2 * tan(Math.toRadians(XR_EMULATOR_FIELD_OF_VIEW_DEGREES / 2))))

/** Measured: a 0.1 radian turn moves the middle of the 2560-pixel-wide view by 152 pixels. */
private const val XR_EMULATOR_FIELD_OF_VIEW_DEGREES = 80.0

private const val XR_EMULATOR_WITHOUT_TOKEN =
    "Mirror drives an XR emulator through its gRPC endpoint, which takes no token Mirror can read; start the emulator with -grpc-use-token"

/**
 * An Android XR headset, shown without input. adb is the only way in, and its taps would land on the
 * invisible SystemUI window in front of every app and hide them all, as they do on the XR emulator,
 * which has the gRPC hand input Mirror uses instead.
 */
internal class AndroidXrHeadsetController(private val androidDeviceController: AndroidDeviceController) : DeviceController by androidDeviceController {
    override val capabilities = androidDeviceController.capabilities.copy(inputRefusal = XR_HEADSET_INPUT, buttons = emptyList())

    override suspend fun tap(x: Int, y: Int) = throw deviceControlError(XR_HEADSET_INPUT)

    override suspend fun swipe(fromX: Int, fromY: Int, toX: Int, toY: Int, durationMillis: Int) = throw deviceControlError(XR_HEADSET_INPUT)

    override suspend fun pressButton(button: DeviceButton) = throw deviceControlError(XR_HEADSET_INPUT)

    override suspend fun inputText(text: String) = throw deviceControlError(XR_HEADSET_INPUT)
}

private const val XR_HEADSET_INPUT =
    "Mirror sends no input to an Android XR headset: on Android XR, adb's taps land on an invisible system window in front of every app and hide them all, and only the XR emulator has the gRPC hand input Mirror uses instead"
