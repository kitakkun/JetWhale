package com.kitakkun.jetwhale.plugins.mirror.host

import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.FilterMipmap
import org.jetbrains.skia.FilterMode
import org.jetbrains.skia.Image
import org.jetbrains.skia.MipmapMode
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Surface
import java.io.File
import java.io.InputStream
import kotlin.concurrent.thread

/** Tall enough for a grid cell on a Retina display, small enough to keep a hundred in memory. */
internal const val THUMBNAIL_HEIGHT = 240

/**
 * The thumbnail of [capture], made on first request and read from its cache file after that; null
 * when the capture cannot be read, or is a recording and there is no [ffmpeg] to read it with.
 * Blocks while it decodes, so call it off the UI thread.
 *
 * Only one full-size image is ever decoded at a time, and every native object is closed before
 * this returns: a list of captures must not hold their pixels.
 */
internal fun thumbnailOf(library: CaptureLibrary, capture: Capture, ffmpeg: String?): File? {
    val cached = library.thumbnailFileOf(capture.file)
    if (cached.isFile) return cached
    val png = when (capture.info.kind) {
        CaptureKind.Screenshot -> shrinkScreenshot(capture.file)
        CaptureKind.Recording -> ffmpeg?.let { posterFrame(it, capture.file) }
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

/** The first frame of a video, shrunk by ffmpeg to the thumbnail height and encoded as PNG. */
private fun posterFrame(ffmpeg: String, file: File): ByteArray? {
    val command = listOf(
        ffmpeg, "-hide_banner", "-loglevel", "error", "-i", file.absolutePath,
        "-frames:v", "1", "-vf", "scale=-2:$THUMBNAIL_HEIGHT:flags=area", "-f", "image2pipe", "-c:v", "png", "pipe:1",
    )
    val process = try {
        SystemProcessLauncher.start(command)
    } catch (_: DeviceControlException) {
        return null
    }
    // ffmpeg's log is dropped, but read, so a full pipe cannot stall it.
    thread(isDaemon = true, name = "mirror-thumbnail-log") { process.errorStream.use(InputStream::readAllBytes) }
    val png = process.inputStream.use(InputStream::readAllBytes)
    return png.takeIf { process.waitFor() == 0 && it.isNotEmpty() }
}
