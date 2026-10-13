package com.kitakkun.jetwhale.tools.docsscreenshots

import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs

/**
 * `docs/images`: one directory per guide page, holding that page's screenshots as WebP.
 *
 * A file is rewritten only when its picture changed. The same UI rendered twice on one machine gives
 * the same pixels, but a font or encoder update can move a few; such a change stays under
 * [MAX_CHANGED_PIXEL_FRACTION] and leaves the file, and its git history, alone.
 */
class DocsImagesDirectory(private val root: File) {
    /**
     * Encodes [image] as WebP and writes it to `<page>/<fileName>`, unless the file already there
     * shows the same picture. Returns whether the file was written.
     */
    fun writeWebpIfChanged(page: String, fileName: String, image: Image): Boolean {
        val pixels = image.readRgbaPixels()
        check((pixels.indices step BYTES_PER_PIXEL).all { pixels[it + ALPHA_OFFSET] == OPAQUE }) {
            "$page/$fileName has transparent pixels; give the screenshot a background"
        }
        val encoded = image.encodeOpaqueWebp()
        check(encoded.size <= MAX_FILE_BYTES) {
            "$page/$fileName is ${encoded.size / BYTES_PER_KB} KB, over the ${MAX_FILE_BYTES / BYTES_PER_KB} KB budget"
        }
        val file = root.resolve(page).resolve(fileName)
        if (file.exists() && isSamePicture(file.readBytes(), encoded)) return false
        file.parentFile.mkdirs()
        file.writeBytes(encoded)
        return true
    }

    private fun isSamePicture(storedWebp: ByteArray, newWebp: ByteArray): Boolean {
        if (storedWebp.contentEquals(newWebp)) return true
        val storedImage = Image.makeFromEncoded(storedWebp)
        val newImage = Image.makeFromEncoded(newWebp)
        if (storedImage.width != newImage.width || storedImage.height != newImage.height) return false
        val storedPixels = storedImage.readRgbaPixels()
        val newPixels = newImage.readRgbaPixels()
        val changedPixelCount = (storedPixels.indices step BYTES_PER_PIXEL).count { offset ->
            (0 until BYTES_PER_PIXEL).any { channel ->
                val index = offset + channel
                abs(storedPixels[index].toUByte().toInt() - newPixels[index].toUByte().toInt()) > CHANNEL_TOLERANCE
            }
        }
        return changedPixelCount <= storedImage.width * storedImage.height * MAX_CHANGED_PIXEL_FRACTION
    }
}

/**
 * The image as a WebP file of its VP8 data alone: whatever else the encoder puts into the container
 * (a color profile, EXIF) is left out, which an opaque still image does not need.
 *
 * Lossy only: skiko's `encodeToData` passes nothing but the quality to Skia's WebP encoder, whose
 * default compression is lossy, so a lossless file cannot be asked for.
 */
private fun Image.encodeOpaqueWebp(): ByteArray {
    val encoded = checkNotNull(encodeToData(EncodedImageFormat.WEBP, LOSSY_QUALITY)) { "Skia could not encode WebP" }.bytes
    val vp8Chunk = readRiffChunks(encoded).single { it.id == VP8_CHUNK_ID }
    return ByteBuffer.allocate(RIFF_HEADER_BYTES + vp8Chunk.bytes.size)
        .order(ByteOrder.LITTLE_ENDIAN)
        .put(RIFF_ID.toByteArray())
        .putInt(WEBP_ID.length + vp8Chunk.bytes.size)
        .put(WEBP_ID.toByteArray())
        .put(vp8Chunk.bytes)
        .array()
}

/** A RIFF chunk: its four-character [id] and its [bytes], header and padding included. */
private class RiffChunk(val id: String, val bytes: ByteArray)

private fun readRiffChunks(webp: ByteArray): List<RiffChunk> {
    val buffer = ByteBuffer.wrap(webp).order(ByteOrder.LITTLE_ENDIAN)
    check(String(webp, 0, RIFF_ID.length) == RIFF_ID && String(webp, RIFF_ID.length + Int.SIZE_BYTES, WEBP_ID.length) == WEBP_ID) { "not a WebP file" }
    val chunks = mutableListOf<RiffChunk>()
    var offset = RIFF_HEADER_BYTES
    while (offset < webp.size) {
        val payloadSize = buffer.getInt(offset + CHUNK_ID_BYTES)
        val chunkSize = CHUNK_HEADER_BYTES + payloadSize + payloadSize % 2
        chunks += RiffChunk(id = String(webp, offset, CHUNK_ID_BYTES), bytes = webp.copyOfRange(offset, offset + chunkSize))
        offset += chunkSize
    }
    return chunks
}

private fun Image.readRgbaPixels(): ByteArray {
    val bitmap = Bitmap()
    bitmap.allocPixels(ImageInfo(width, height, ColorType.RGBA_8888, ColorAlphaType.UNPREMUL))
    check(readPixels(bitmap)) { "could not read the image's pixels" }
    return checkNotNull(bitmap.readPixels())
}

private const val LOSSY_QUALITY = 88

private const val MAX_FILE_BYTES = 250 * 1024

private const val BYTES_PER_KB = 1024

private const val RIFF_ID = "RIFF"

private const val WEBP_ID = "WEBP"

private const val VP8_CHUNK_ID = "VP8 "

/** "RIFF", the size of what follows, "WEBP". */
private const val RIFF_HEADER_BYTES = 12

private const val CHUNK_ID_BYTES = 4

private const val CHUNK_HEADER_BYTES = 8

private const val BYTES_PER_PIXEL = 4

private const val ALPHA_OFFSET = 3

private const val OPAQUE: Byte = -1

/**
 * How far a channel may move before its pixel counts as changed: lossy encoding shifts flat areas
 * by a level or two when a neighboring block changes, while a hover tint moves them by about ten.
 */
private const val CHANNEL_TOLERANCE = 6

/** Share of pixels that may change before the picture counts as changed: anti-aliasing noise. */
private const val MAX_CHANGED_PIXEL_FRACTION = 0.001
