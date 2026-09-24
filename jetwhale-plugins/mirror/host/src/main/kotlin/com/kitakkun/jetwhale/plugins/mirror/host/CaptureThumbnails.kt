package com.kitakkun.jetwhale.plugins.mirror.host

import org.bytedeco.ffmpeg.global.avutil
import org.bytedeco.javacv.FFmpegFrameGrabber
import org.bytedeco.javacv.FrameGrabber
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.FilterMipmap
import org.jetbrains.skia.FilterMode
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.MipmapMode
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Surface
import java.io.File
import java.nio.ByteBuffer

/** Tall enough for a grid cell on a Retina display, small enough to keep a hundred in memory. */
internal const val THUMBNAIL_HEIGHT = 240

/**
 * The thumbnail of [capture], made on first request and read from its cache file after that; null
 * when the capture cannot be read. Blocks while it decodes, so call it off the UI thread.
 *
 * Only one full-size image or video frame is ever decoded at a time, and every native object is
 * closed before this returns: a list of captures must not hold their pixels.
 */
internal fun thumbnailOf(library: CaptureLibrary, capture: Capture): File? {
    val cached = library.thumbnailFileOf(capture.file)
    if (cached.isFile) return cached
    val png = when (capture.info.kind) {
        CaptureKind.Screenshot -> shrinkScreenshot(capture.file)
        CaptureKind.Recording -> posterFrame(capture.file)
    } ?: return null
    cached.parentFile.mkdirs()
    cached.writeBytes(png)
    return cached
}

private fun shrinkScreenshot(file: File): ByteArray? {
    val image = try {
        Image.makeFromEncoded(file.readBytes())
    } catch (_: IllegalArgumentException) {
        return null
    }
    return image.use(::encodeShrunk)
}

private fun encodeShrunk(image: Image): ByteArray? {
    val width = maxOf(1, image.width * THUMBNAIL_HEIGHT / image.height)
    return Surface.makeRasterN32Premul(width, THUMBNAIL_HEIGHT).use { surface ->
        surface.canvas.drawImageRect(
            image,
            Rect.makeWH(image.width.toFloat(), image.height.toFloat()),
            Rect.makeWH(width.toFloat(), THUMBNAIL_HEIGHT.toFloat()),
            FilterMipmap(FilterMode.LINEAR, MipmapMode.LINEAR),
            null,
            true,
        )
        surface.makeImageSnapshot().use { it.encodeToData(EncodedImageFormat.PNG)?.bytes }
    }
}

/** The first frame of a video, shrunk like a screenshot. */
private fun posterFrame(file: File): ByteArray? {
    val grabber = FFmpegFrameGrabber(file)
    grabber.pixelFormat = avutil.AV_PIX_FMT_BGRA
    return try {
        grabber.start()
        val frame = grabber.grabImage() ?: return null
        val pixels = frame.image?.firstOrNull() as? ByteBuffer ?: return null
        encodeBgraShrunk(pixels, width = frame.imageWidth, height = frame.imageHeight, rowBytes = frame.imageStride)
    } catch (_: FrameGrabber.Exception) {
        null
    } finally {
        grabber.close()
    }
}

private fun encodeBgraShrunk(pixels: ByteBuffer, width: Int, height: Int, rowBytes: Int): ByteArray? {
    val bytes = ByteArray(rowBytes * height).also { pixels.duplicate().get(it) }
    val info = ImageInfo(width, height, ColorType.BGRA_8888, ColorAlphaType.OPAQUE)
    return Bitmap().use { bitmap ->
        bitmap.allocPixels(info)
        bitmap.installPixels(info, bytes, rowBytes)
        Image.makeFromBitmap(bitmap).use(::encodeShrunk)
    }
}
