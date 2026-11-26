package com.kitakkun.jetwhale.plugins.mirror.host

import androidx.annotation.VisibleForTesting
import kotlin.math.atan
import kotlin.math.tan

/**
 * The Android XR emulator. Its screen is a view into a 3D scene where apps float as panels, and
 * adb's `input tap` lands on an invisible SystemUI window in front of them all, which hides every
 * panel; a panel's own views sit in coordinates unrelated to where the view draws it. So taps,
 * swipes and the view go through [xrEmulatorClient], whose hand points and pinches where the view
 * shows something. An emulator that left no gRPC token in its discovery file has no
 * [xrEmulatorClient], and then takes no input. Buttons, text, the stream and recordings are
 * [androidDeviceController]'s.
 */
internal class AndroidXrEmulatorDeviceController(
    private val androidDeviceController: AndroidDeviceController,
    private val xrEmulatorClient: XrEmulatorClient?,
) : DeviceController by androidDeviceController,
    XrViewController {
    override val capabilities = androidDeviceController.capabilities.copy(
        inputRefusal = XR_EMULATOR_WITHOUT_TOKEN_REFUSAL.takeIf { xrEmulatorClient == null },
        buttons = if (xrEmulatorClient == null) emptyList() else listOf(DeviceButton.Home, DeviceButton.Back, DeviceButton.Recents, DeviceButton.AllApps, DeviceButton.Power, DeviceButton.VolumeUp, DeviceButton.VolumeDown),
    )

    // `screencap` renders the scene on its own, two pixels narrower and a few pixels off at the
    // edges from the framebuffer the stream shows and the hand points into.
    override suspend fun captureScreenshot(): ByteArray = xrEmulatorClient?.captureScreenshotPng() ?: androidDeviceController.captureScreenshot()

    override suspend fun tap(x: Int, y: Int) = requireXrEmulatorClient().tap(x, y)

    override suspend fun swipe(fromX: Int, fromY: Int, toX: Int, toY: Int, durationMillis: Int) = requireXrEmulatorClient().swipe(fromX = fromX, fromY = fromY, toX = toX, toY = toY, durationMillis = durationMillis)

    override suspend fun pressButton(button: DeviceButton) {
        requireXrEmulatorClient()
        androidDeviceController.pressButton(button)
    }

    override suspend fun inputText(text: String) {
        requireXrEmulatorClient()
        androidDeviceController.inputText(text)
    }

    override suspend fun turnView(rightDegrees: Double, upDegrees: Double) = requireXrEmulatorClient().turnHead(upRadians = Math.toRadians(upDegrees).toFloat(), leftRadians = -Math.toRadians(rightDegrees).toFloat())

    override suspend fun turnViewWithDrag(dragX: Float, dragY: Float) = turnView(rightDegrees = -xrEmulatorDegreesAcross(dragX), upDegrees = xrEmulatorDegreesAcross(dragY))

    override suspend fun recenterView() = requireXrEmulatorClient().recenterHead()

    // The emulator may round the coefficient to an integer before applying it, so the room shows
    // from one half up.
    override suspend fun isPassthroughOn(): Boolean = requireXrEmulatorClient().readXrOptions().passthroughCoefficient >= 0.5f

    override suspend fun setPassthrough(on: Boolean) {
        val xrEmulatorClient = requireXrEmulatorClient()
        // setXrOptions sets every field, so the environment and dimming are written back as they
        // are.
        xrEmulatorClient.writeXrOptions(xrEmulatorClient.readXrOptions().copy(passthroughCoefficient = if (on) 1f else 0f))
    }

    private fun requireXrEmulatorClient(): XrEmulatorClient = xrEmulatorClient ?: throw deviceControlError(XR_EMULATOR_WITHOUT_TOKEN_REFUSAL)
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

private const val XR_EMULATOR_WITHOUT_TOKEN_REFUSAL =
    "Mirror drives an XR emulator through its gRPC endpoint, but this emulator left no gRPC token in its discovery file; start it with -grpc-use-token"

/**
 * An Android XR headset, shown without input. adb is the only way in, and its taps would land on the
 * invisible SystemUI window in front of every app and hide them all, as they do on the XR emulator,
 * which has the gRPC hand input Mirror uses instead.
 */
internal class AndroidXrHeadsetDeviceController(private val androidDeviceController: AndroidDeviceController) : DeviceController by androidDeviceController {
    override val capabilities = androidDeviceController.capabilities.copy(inputRefusal = XR_HEADSET_INPUT_REFUSAL, buttons = emptyList())

    override suspend fun tap(x: Int, y: Int) = throw deviceControlError(XR_HEADSET_INPUT_REFUSAL)

    override suspend fun swipe(fromX: Int, fromY: Int, toX: Int, toY: Int, durationMillis: Int) = throw deviceControlError(XR_HEADSET_INPUT_REFUSAL)

    override suspend fun pressButton(button: DeviceButton) = throw deviceControlError(XR_HEADSET_INPUT_REFUSAL)

    override suspend fun inputText(text: String) = throw deviceControlError(XR_HEADSET_INPUT_REFUSAL)
}

private const val XR_HEADSET_INPUT_REFUSAL =
    "Mirror sends no input to an Android XR headset: on Android XR, adb's taps land on an invisible system window in front of every app and hide them all, and only the XR emulator has the gRPC hand input Mirror uses instead"
