package com.kitakkun.jetwhale.plugins.mirror.host

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

// Drawn here rather than taken from Material icons, which the host bundles for itself and does not
// offer plugins. Each is a 24-unit outline, tinted by JwIcon.

internal val DeviceButton.icon: ImageVector
    get() = when (this) {
        DeviceButton.Home -> HomeIcon
        DeviceButton.Back -> BackIcon
        DeviceButton.Recents -> RecentsIcon
        DeviceButton.Power -> PowerIcon
        DeviceButton.VolumeUp -> VolumeUpIcon
        DeviceButton.VolumeDown -> VolumeDownIcon
    }

internal val ScreenOffIcon: ImageVector = outlineIcon("ScreenOff") {
    // A phone with a line through it.
    moveTo(7f, 3f)
    lineTo(17f, 3f)
    lineTo(17f, 21f)
    lineTo(7f, 21f)
    close()
    moveTo(4f, 4f)
    lineTo(20f, 20f)
}

internal val WakeIcon: ImageVector = outlineIcon("Wake") {
    // A phone with a lit screen.
    moveTo(7f, 3f)
    lineTo(17f, 3f)
    lineTo(17f, 21f)
    lineTo(7f, 21f)
    close()
    moveTo(10f, 8f)
    lineTo(14f, 8f)
    moveTo(10f, 12f)
    lineTo(14f, 12f)
    moveTo(10f, 16f)
    lineTo(14f, 16f)
}

/** A cross: dismiss. */
internal val CloseIcon: ImageVector = outlineIcon("Close") {
    moveTo(6f, 6f)
    lineTo(18f, 18f)
    moveTo(18f, 6f)
    lineTo(6f, 18f)
}

/** A ring: start recording. */
internal val RecordIcon: ImageVector = outlineIcon("Record") {
    moveTo(12f, 5f)
    arcTo(horizontalEllipseRadius = 7f, verticalEllipseRadius = 7f, theta = 0f, isMoreThanHalf = true, isPositiveArc = true, x1 = 12f, y1 = 19f)
    arcTo(horizontalEllipseRadius = 7f, verticalEllipseRadius = 7f, theta = 0f, isMoreThanHalf = true, isPositiveArc = true, x1 = 12f, y1 = 5f)
    moveTo(12f, 10f)
    arcTo(horizontalEllipseRadius = 2f, verticalEllipseRadius = 2f, theta = 0f, isMoreThanHalf = true, isPositiveArc = true, x1 = 12f, y1 = 14f)
    arcTo(horizontalEllipseRadius = 2f, verticalEllipseRadius = 2f, theta = 0f, isMoreThanHalf = true, isPositiveArc = true, x1 = 12f, y1 = 10f)
}

/** A camera: take a screenshot. */
internal val ScreenshotIcon: ImageVector = outlineIcon("Screenshot") {
    moveTo(4f, 8f)
    lineTo(8f, 8f)
    lineTo(10f, 5f)
    lineTo(14f, 5f)
    lineTo(16f, 8f)
    lineTo(20f, 8f)
    lineTo(20f, 19f)
    lineTo(4f, 19f)
    close()
    moveTo(12f, 10f)
    arcTo(horizontalEllipseRadius = 3f, verticalEllipseRadius = 3f, theta = 0f, isMoreThanHalf = true, isPositiveArc = true, x1 = 12f, y1 = 16f)
    arcTo(horizontalEllipseRadius = 3f, verticalEllipseRadius = 3f, theta = 0f, isMoreThanHalf = true, isPositiveArc = true, x1 = 12f, y1 = 10f)
}

/** A stack of pictures: the capture library. */
internal val CapturesIcon: ImageVector = outlineIcon("Captures") {
    moveTo(7f, 4f)
    lineTo(20f, 4f)
    lineTo(20f, 16f)
    moveTo(4f, 7f)
    lineTo(17f, 7f)
    lineTo(17f, 20f)
    lineTo(4f, 20f)
    close()
    moveTo(4f, 17f)
    lineTo(8f, 13f)
    lineTo(11f, 16f)
    lineTo(13f, 14f)
    lineTo(17f, 18f)
}

/** Corners pointing outward: show this device on its own, at full size. */
internal val OpenFullIcon: ImageVector = outlineIcon("OpenFull") {
    moveTo(4f, 9f)
    lineTo(4f, 4f)
    lineTo(9f, 4f)
    moveTo(15f, 4f)
    lineTo(20f, 4f)
    lineTo(20f, 9f)
    moveTo(20f, 15f)
    lineTo(20f, 20f)
    lineTo(15f, 20f)
    moveTo(9f, 20f)
    lineTo(4f, 20f)
    lineTo(4f, 15f)
}

private val HomeIcon: ImageVector = outlineIcon("Home") {
    moveTo(4f, 11f)
    lineTo(12f, 4f)
    lineTo(20f, 11f)
    moveTo(6f, 10f)
    lineTo(6f, 20f)
    lineTo(18f, 20f)
    lineTo(18f, 10f)
}

private val BackIcon: ImageVector = outlineIcon("Back") {
    moveTo(16f, 5f)
    lineTo(8f, 12f)
    lineTo(16f, 19f)
    close()
}

private val RecentsIcon: ImageVector = outlineIcon("Recents") {
    // Two stacked screens: the one in front and the one behind it.
    moveTo(7f, 7f)
    lineTo(17f, 7f)
    lineTo(17f, 20f)
    lineTo(7f, 20f)
    close()
    moveTo(9f, 4f)
    lineTo(19f, 4f)
    lineTo(19f, 16f)
}

private val PowerIcon: ImageVector = outlineIcon("Power") {
    moveTo(12f, 3f)
    lineTo(12f, 11f)
    moveTo(7f, 6f)
    arcTo(horizontalEllipseRadius = 8f, verticalEllipseRadius = 8f, theta = 0f, isMoreThanHalf = true, isPositiveArc = false, x1 = 17f, y1 = 6f)
}

private val VolumeUpIcon: ImageVector = outlineIcon("VolumeUp") {
    speaker()
    moveTo(15f, 12f)
    lineTo(21f, 12f)
    moveTo(18f, 9f)
    lineTo(18f, 15f)
}

private val VolumeDownIcon: ImageVector = outlineIcon("VolumeDown") {
    speaker()
    moveTo(15f, 12f)
    lineTo(21f, 12f)
}

private fun PathBuilder.speaker() {
    moveTo(3f, 9f)
    lineTo(6f, 9f)
    lineTo(11f, 5f)
    lineTo(11f, 19f)
    lineTo(6f, 15f)
    lineTo(3f, 15f)
    close()
}

private fun outlineIcon(name: String, pathBuilder: PathBuilder.() -> Unit): ImageVector = ImageVector.Builder(
    name = name,
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).path(
    stroke = SolidColor(Color.Black),
    strokeLineWidth = 2f,
    strokeLineCap = StrokeCap.Round,
    strokeLineJoin = StrokeJoin.Round,
    pathBuilder = pathBuilder,
).build()
