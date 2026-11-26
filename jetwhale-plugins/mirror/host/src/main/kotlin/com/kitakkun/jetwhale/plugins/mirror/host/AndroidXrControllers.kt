package com.kitakkun.jetwhale.plugins.mirror.host

import androidx.annotation.VisibleForTesting
import androidx.compose.ui.geometry.Offset
import kotlin.math.atan
import kotlin.math.tan

/**
 * The Android XR emulator, reached through its gRPC endpoint. Its screen is a view into a 3D scene
 * where apps float as panels, and adb's `input tap` lands on an invisible SystemUI window in front
 * of them all, which hides every panel; a panel's own views sit in coordinates unrelated to where the
 * view draws it. So taps, swipes and the view go through [xrEmulatorClient], whose hand points and
 * pinches where the view shows something. Buttons, text, the stream and recordings are
 * [androidDeviceController]'s.
 */
internal class AndroidXrEmulatorDeviceController(
    private val androidDeviceController: AndroidDeviceController,
    private val xrEmulatorClient: XrEmulatorClient,
) : DeviceController by androidDeviceController,
    XrViewController {
    override val capabilities = androidDeviceController.capabilities.copy(
        buttons = listOf(DeviceButton.Home, DeviceButton.Back, DeviceButton.Recents, DeviceButton.AllApps, DeviceButton.Power, DeviceButton.VolumeUp, DeviceButton.VolumeDown),
    )

    // `screencap` renders the scene on its own, two pixels narrower and a few pixels off at the
    // edges from the framebuffer the stream shows and the hand points into.
    override suspend fun captureScreenshot(): ByteArray = xrEmulatorClient.captureScreenshotPng()

    override suspend fun tap(x: Int, y: Int) = xrEmulatorClient.tap(x, y)

    override suspend fun swipe(fromX: Int, fromY: Int, toX: Int, toY: Int, durationMillis: Int) = xrEmulatorClient.swipe(fromX = fromX, fromY = fromY, toX = toX, toY = toY, durationMillis = durationMillis)

    override suspend fun turnView(rightDegrees: Double, upDegrees: Double) = xrEmulatorClient.turnHead(upRadians = Math.toRadians(upDegrees).toFloat(), leftRadians = -Math.toRadians(rightDegrees).toFloat())

    override suspend fun turnViewWithDrag(dragFromInScreenWidths: Offset, dragToInScreenWidths: Offset) {
        val dragRightDegrees = xrEmulatorDegreesAcross(dragToInScreenWidths.x) - xrEmulatorDegreesAcross(dragFromInScreenWidths.x)
        val dragDownDegrees = xrEmulatorDegreesAcross(dragToInScreenWidths.y) - xrEmulatorDegreesAcross(dragFromInScreenWidths.y)
        turnView(rightDegrees = -dragRightDegrees, upDegrees = dragDownDegrees)
    }

    override suspend fun recenterView() = xrEmulatorClient.recenterHead()

    // The emulator may round the coefficient to an integer before applying it, so the room shows
    // from one half up.
    override suspend fun isPassthroughOn(): Boolean = xrEmulatorClient.readXrOptions().passthroughCoefficient >= 0.5f

    override suspend fun setPassthrough(on: Boolean) {
        // setXrOptions replaces every field, so the environment and dimming are written back as
        // read.
        xrEmulatorClient.writeXrOptions(xrEmulatorClient.readXrOptions().copy(passthroughCoefficient = if (on) 1f else 0f))
    }
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

/**
 * An Android XR device shown without input, for [inputRefusal]: a headset, or an XR emulator Mirror
 * cannot reach over gRPC. adb is then the only way in, and on Android XR its taps land on the
 * invisible SystemUI window in front of every app and hide them all.
 */
internal class ViewOnlyAndroidXrDeviceController(
    private val androidDeviceController: AndroidDeviceController,
    private val inputRefusal: String,
) : DeviceController by androidDeviceController {
    override val capabilities = androidDeviceController.capabilities.copy(inputRefusal = inputRefusal, buttons = emptyList())

    override suspend fun tap(x: Int, y: Int) = throw deviceControlError(inputRefusal)

    override suspend fun swipe(fromX: Int, fromY: Int, toX: Int, toY: Int, durationMillis: Int) = throw deviceControlError(inputRefusal)

    override suspend fun pressButton(button: DeviceButton) = throw deviceControlError(inputRefusal)

    override suspend fun inputText(text: String) = throw deviceControlError(inputRefusal)
}
