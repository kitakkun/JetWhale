package com.kitakkun.jetwhale.plugins.mirror.host

import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.Image
import java.io.InputStream

/** Where the device's screen arrives, how to read it, and how to stop it. */
internal sealed interface VideoStream {
    /** Stops the stream; reading it ends. Safe to call more than once. */
    fun close()

    /** Raw H.264, decoded on the host by the ffmpeg at [ffmpegPath]. */
    class H264(override val process: Process, val ffmpegPath: String) : ProcessVideoStream

    /** An emulator's gRPC screen stream: `Image` messages of RGBA pixels, read by [readEmulatorFramesInto]. */
    class EmulatorRgba(val frames: InputStream, private val cancel: () -> Unit) : VideoStream {
        override fun close() = cancel()
    }

    /**
     * Whole frames, each an image Skia decodes: an iOS simulator's XCTest runner sends JPEG, and the
     * simulator's PNG screenshots stand in until the runner is up. A frame is the screen at its own
     * size, as the interface turns it; a JPEG turned to landscape keeps portrait pixels and says so in
     * its EXIF orientation, which Skia applies.
     */
    class EncodedImages(private val images: Flow<ByteArray>) : VideoStream {
        private val closed = CompletableDeferred<Unit>()

        override fun close() {
            closed.complete(Unit)
        }

        /** Hands each image to [onImage] until the images end or [close] runs. */
        suspend fun collectUntilClosed(onImage: suspend (ByteArray) -> Unit) = coroutineScope {
            val collecting = launch { images.collect(onImage) }
            val closing = launch {
                closed.await()
                collecting.cancel()
            }
            collecting.join()
            closing.cancel()
        }
    }
}

/** A video stream a tool writes to its stdout; closing it ends the process. */
internal sealed interface ProcessVideoStream : VideoStream {
    val process: Process

    val frames: InputStream get() = process.inputStream

    override fun close() {
        process.destroyForcibly()
    }
}

/**
 * Decodes the images of [stream] into [target] until it ends or is closed. An image Skia cannot read
 * ends the stream.
 */
internal suspend fun readEncodedImagesInto(target: MirrorSurface.FrameStream, stream: VideoStream.EncodedImages, onFrame: () -> Unit) {
    stream.collectUntilClosed { encoded ->
        val started = System.nanoTime()
        // Skia throws IllegalArgumentException for bytes that are not an image, and a
        // RuntimeException for a damaged one.
        val decoded = try {
            Image.makeFromEncoded(encoded).use(Bitmap::makeFromImage)
        } catch (e: RuntimeException) {
            throw DeviceControlException("a frame of the video stream could not be read as an image", e)
        }
        target.recordDecode(System.nanoTime() - started)
        target.publishFrame(decoded)
        onFrame()
    }
}

/** Stores [frame], BGRA rows of [rowBytes], as the next frame of this stream. */
internal fun MirrorSurface.FrameStream.writeBgraFrame(frame: ByteArray, frameSize: IntSize, rowBytes: Int) {
    writeFrame(frameSize.width, frameSize.height, ColorType.BGRA_8888) { target ->
        val pixmap = target.peekPixels() ?: return@writeFrame false
        pixmap.writeRows(frame, sourceRowBytes = rowBytes, height = frameSize.height)
        true
    }
}

/**
 * [source], counting the time its reads spend blocked waiting for the device, so the stats can tell
 * a still screen (long waits) from slow decoding.
 */
internal class WaitTimingInputStream(private val source: InputStream) : InputStream() {
    @Volatile
    var waitedNanos: Long = 0
        private set

    override fun read(): Int = timed { source.read() }

    override fun read(b: ByteArray, off: Int, len: Int): Int = timed { source.read(b, off, len) }

    /** Runs [read] and gives [record] the time it took apart from waiting for the device. */
    inline fun <T> timingWork(record: (Long) -> Unit, read: () -> T): T {
        val started = System.nanoTime()
        val waitedBefore = waitedNanos
        return read().also { record(System.nanoTime() - started - (waitedNanos - waitedBefore)) }
    }

    override fun available(): Int = source.available()

    override fun close() = source.close()

    private inline fun timed(read: () -> Int): Int {
        val started = System.nanoTime()
        try {
            return read()
        } finally {
            waitedNanos += System.nanoTime() - started
        }
    }
}
