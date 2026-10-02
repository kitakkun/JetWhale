package com.kitakkun.jetwhale.plugins.mirror.host

import androidx.compose.ui.unit.IntSize
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * Decodes the raw H.264 of [stream] into [target] through the `ffmpeg` command it names, frame by
 * frame. [onInput] is called as the device's bytes arrive and [onFrame] after each frame, so the
 * caller can watch the stream's health: the decoder holds a frame back until the next one starts,
 * so a still screen sends bytes but decodes nothing. Returns when the stream ends; blocks the
 * calling thread, so run it off the UI.
 *
 * The device's bytes go to ffmpeg's stdin and BGRA frames come back on its stdout. ffmpeg shrinks
 * the frames to [outputSize] with area averaging, which keeps text crisp on every renderer and
 * leaves a fraction of the pixels to copy; without a size they keep the first frame's. Closing
 * [stream] ends the device's output, which ends ffmpeg's input and then this.
 *
 * A picture that changes size partway ends the decoding too, so the caller can open the next
 * stream at the new size: ffmpeg keeps writing frames of the first size and squeezes the new
 * picture into them.
 */
internal fun decodeH264Into(target: MirrorSurface.FrameStream, stream: VideoStream.H264, outputSize: IntSize?, onInput: () -> Unit, onFrame: () -> Unit) {
    val ffmpegProcess = SystemProcessLauncher.start(ffmpegDecodeCommand(stream.ffmpegPath, outputSize))
    // ffmpeg ends at the end of its input, which ends the copy without the error that killing it
    // mid-read would raise.
    val log = FfmpegLog(ffmpegProcess.errorStream, onInputResized = ffmpegProcess.outputStream::close)
    val input = object : FilterInputStream(stream.frames) {
        override fun read(b: ByteArray, off: Int, len: Int): Int = super.read(b, off, len).also { if (it > 0) onInput() }
    }
    val feeding = thread(isDaemon = true, name = "mirror-ffmpeg-input") { feed(input, ffmpegProcess) }
    val frames = try {
        val frameSize = outputSize ?: log.outputSize.get()
        frameSize?.let { copyFrames(WaitTimingInputStream(ffmpegProcess.inputStream), it, target, log::inputResized, onFrame) } ?: 0
    } finally {
        ffmpegProcess.destroyForcibly()
        ffmpegProcess.waitFor(FFMPEG_EXIT_WAIT_MILLIS, TimeUnit.MILLISECONDS)
        feeding.join(FFMPEG_EXIT_WAIT_MILLIS)
    }
    val failure = log.errors()
    if (frames == 0 && failure.isNotEmpty()) throw deviceControlError("the video stream could not be decoded: $failure")
}

/**
 * Copies BGRA frames of [frameSize] from [output] into [target] until it ends; returns how many.
 * Once [isResized], the frames left hold a squeezed picture and are dropped.
 */
private fun copyFrames(output: WaitTimingInputStream, frameSize: IntSize, target: MirrorSurface.FrameStream, isResized: () -> Boolean, onFrame: () -> Unit): Int {
    val frame = ByteArray(frameSize.width * frameSize.height * BYTES_PER_PIXEL)
    var frames = 0
    while (output.timingWork(target::recordDecode) { output.readNBytes(frame, 0, frame.size) } == frame.size) {
        if (isResized()) continue
        target.writeBgraFrame(frame, frameSize, rowBytes = frameSize.width * BYTES_PER_PIXEL)
        frames++
        onFrame()
    }
    return frames
}

/**
 * The ffmpeg command that turns raw H.264 on stdin into BGRA frames on stdout. BGRA is Skia's
 * native layout on a little-endian host, so a frame is copied, never converted.
 *
 * Probing is kept short: its defaults spend seconds before the first frame, which a live mirror
 * shows as lag. `low_delay` also turns off frame threading, which holds frames back.
 * `-fflags nobuffer` is left out on purpose: with it, adb's screenrecord stream decodes to nothing.
 * Every decoded frame is passed on as it is, without ffmpeg duplicating or dropping frames to hold
 * a frame rate the device never promised.
 */
internal fun ffmpegDecodeCommand(ffmpegPath: String, outputSize: IntSize?): List<String> = buildList {
    addAll(listOf(ffmpegPath, "-hide_banner", "-nostats", "-loglevel", "info")) // info, not error: FfmpegLog reads the frame sizes and size changes from this log.
    addAll(listOf("-flags", "low_delay", "-probesize", "65536", "-analyzeduration", "500000"))
    addAll(listOf("-f", "h264", "-i", "pipe:0"))
    outputSize?.let { addAll(listOf("-vf", "scale=${it.width}:${it.height}:flags=area")) }
    addAll(listOf("-fps_mode", "passthrough", "-f", "rawvideo", "-pix_fmt", "bgra", "pipe:1"))
}

/**
 * Copies the device's bytes into ffmpeg until either side ends, then lets ffmpeg finish. Each read
 * is flushed: the process's stdin is buffered, and would hold the end of a small frame back until
 * the next one arrived.
 */
internal fun feed(source: InputStream, ffmpegProcess: Process) {
    try {
        ffmpegProcess.outputStream.use { ffmpegInput ->
            val buffer = ByteArray(FEED_BUFFER_BYTES)
            while (true) {
                val read = source.read(buffer)
                if (read < 0) break
                ffmpegInput.write(buffer, 0, read)
                ffmpegInput.flush()
            }
        }
    } catch (_: IOException) {
    }
}

/**
 * Reads ffmpeg's log, which must be drained or ffmpeg stalls on a full pipe. It says the size of the
 * frames ffmpeg writes, needed when they were not asked for at a size, and its errors explain a
 * stream that decoded nothing. [onInputResized] is called when the picture changes size partway.
 */
private class FfmpegLog(log: InputStream, onInputResized: () -> Unit) {
    /** The size of the frames on stdout, or null when ffmpeg ended before saying. */
    val outputSize = CompletableFuture<IntSize?>()

    /** Whether the picture has changed size partway, which leaves the frames after it squeezed. */
    @Volatile
    var inputResized = false
        private set

    private val errorLines = ArrayDeque<String>()

    init {
        thread(isDaemon = true, name = "mirror-ffmpeg-log") {
            var describingOutput = false
            var inputSize: IntSize? = null
            try {
                log.bufferedReader().forEachLine { line ->
                    if (line.startsWith("Output #")) describingOutput = true
                    if (!describingOutput && inputSize == null) inputSize = parseStreamSize(line, codec = "h264")
                    if (describingOutput && !outputSize.isDone) parseStreamSize(line, codec = "rawvideo")?.let(outputSize::complete)
                    val resizedTo = parseChangedSize(line)
                    if (resizedTo != null && inputSize != null && resizedTo != inputSize) {
                        inputResized = true
                        onInputResized()
                    }
                    if (line.contains("error", ignoreCase = true) || line.contains("invalid", ignoreCase = true)) {
                        synchronized(errorLines) {
                            errorLines.addLast(line.trim())
                            if (errorLines.size > MAX_ERROR_LINES) errorLines.removeFirst()
                        }
                    }
                }
            } catch (_: IOException) {
            } finally {
                outputSize.complete(null)
            }
        }
    }

    fun errors(): String = synchronized(errorLines) { errorLines.joinToString(" ") }.take(MAX_ERROR_CHARS)
}

/**
 * The frame size in ffmpeg's description of a [codec] stream, as in
 * `Video: rawvideo (BGRA …), 1080x2400 [SAR …]` for its output or `Video: h264 (High), …, 1080x2400, 30 fps` for its input.
 */
internal fun parseStreamSize(line: String, codec: String): IntSize? {
    if (!line.contains("Video: $codec")) return null
    val match = Regex("""[ ,](\d{2,5})x(\d{2,5})[ ,\[]""").find(line) ?: return null
    return IntSize(match.groupValues[1].toInt(), match.groupValues[2].toInt())
}

/**
 * The new picture size in ffmpeg's note that the decoded frames changed partway, or null for any
 * other line. ffmpeg 7 and later write `Reconfiguring filter graph because video parameters changed
 * to yuv420p(…), 240x320, …`; earlier versions `… frame changed from size:320x240 fmt:yuv420p to
 * size:240x320 fmt:yuv420p`.
 */
internal fun parseChangedSize(line: String): IntSize? {
    val match = Regex("""changed.* to .*?(\d{2,5})x(\d{2,5})""").find(line) ?: return null
    return IntSize(match.groupValues[1].toInt(), match.groupValues[2].toInt())
}

/** How to install ffmpeg, appended to every message about it missing. */
internal const val FFMPEG_INSTALL = "Install ffmpeg: brew install ffmpeg (macOS), winget install ffmpeg (Windows) or apt install ffmpeg (Linux)."

private const val BYTES_PER_PIXEL = 4

private const val FEED_BUFFER_BYTES = 64 * 1024

private const val FFMPEG_EXIT_WAIT_MILLIS = 2_000L

private const val MAX_ERROR_LINES = 5

private const val MAX_ERROR_CHARS = 500
