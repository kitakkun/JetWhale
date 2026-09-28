package com.kitakkun.jetwhale.plugins.mirror.host

import androidx.compose.ui.unit.IntSize
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * Decodes the raw H.264 of [stream] into [surface] through the `ffmpeg` command it names, frame by
 * frame. [onFrame] is called after each frame so the caller can watch the stream's health. Returns
 * when the stream ends; blocks the calling thread, so run it off the UI.
 *
 * The device's bytes go to ffmpeg's stdin and BGRA frames come back on its stdout. ffmpeg shrinks
 * the frames to [outputSize] with area averaging, which keeps text crisp on every renderer and
 * leaves a fraction of the pixels to copy; without a size they keep the first frame's. Closing
 * [stream] ends the device's output, which ends ffmpeg's input and then this.
 */
internal fun decodeH264Into(surface: MirrorSurface, stream: VideoStream.H264, outputSize: IntSize?, onFrame: () -> Unit) {
    val ffmpegProcess = SystemProcessLauncher.start(ffmpegDecodeCommand(stream.ffmpegPath, outputSize))
    val log = FfmpegLog(ffmpegProcess.errorStream)
    val feeding = thread(isDaemon = true, name = "mirror-ffmpeg-input") { feed(stream.frames, ffmpegProcess) }
    val frames = try {
        // No size means ffmpeg ended before describing its output; the error check below says why.
        val frameSize = outputSize ?: log.outputSize.get()
        frameSize?.let { copyFrames(WaitTimingInputStream(ffmpegProcess.inputStream), it, surface, onFrame) } ?: 0
    } finally {
        ffmpegProcess.destroyForcibly()
        ffmpegProcess.waitFor(FFMPEG_EXIT_WAIT_MILLIS, TimeUnit.MILLISECONDS)
        feeding.join(FFMPEG_EXIT_WAIT_MILLIS)
    }
    // A stream that showed frames ends like any other, and the mirror opens the next; one that
    // decoded nothing says why when ffmpeg did.
    val failure = log.errors()
    if (frames == 0 && failure.isNotEmpty()) throw deviceControlError("the video stream could not be decoded: $failure")
}

/** Copies BGRA frames of [frameSize] from [output] into [surface] until it ends; returns how many. */
private fun copyFrames(output: WaitTimingInputStream, frameSize: IntSize, surface: MirrorSurface, onFrame: () -> Unit): Int {
    val frame = ByteArray(frameSize.width * frameSize.height * BYTES_PER_PIXEL)
    var frames = 0
    while (output.timingWork(surface::recordDecode) { output.readNBytes(frame, 0, frame.size) } == frame.size) {
        surface.writeBgraFrame(frame, frameSize, rowBytes = frameSize.width * BYTES_PER_PIXEL)
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
    addAll(listOf(ffmpegPath, "-hide_banner", "-nostats", "-loglevel", "info"))
    addAll(listOf("-flags", "low_delay", "-probesize", "65536", "-analyzeduration", "500000"))
    addAll(listOf("-f", "h264", "-i", "pipe:0"))
    outputSize?.let { addAll(listOf("-vf", "scale=${it.width}:${it.height}:flags=area")) }
    addAll(listOf("-fps_mode", "passthrough", "-f", "rawvideo", "-pix_fmt", "bgra", "pipe:1"))
}

/** Copies the device's bytes into ffmpeg until either side ends, then lets ffmpeg finish. */
private fun feed(source: InputStream, ffmpegProcess: Process) {
    try {
        ffmpegProcess.outputStream.use(source::transferTo)
    } catch (_: IOException) {
        // ffmpeg exited, or the device's stream was closed: either way the input is over.
    }
}

/**
 * Reads ffmpeg's log, which must be drained or ffmpeg stalls on a full pipe. It says the size of the
 * frames ffmpeg writes, needed when they were not asked for at a size, and its errors explain a
 * stream that decoded nothing.
 */
private class FfmpegLog(log: InputStream) {
    /** The size of the frames on stdout, or null when ffmpeg ended before saying. */
    val outputSize = CompletableFuture<IntSize?>()

    private val errorLines = ArrayDeque<String>()

    init {
        thread(isDaemon = true, name = "mirror-ffmpeg-log") {
            var describingOutput = false
            try {
                log.bufferedReader().forEachLine { line ->
                    if (line.startsWith("Output #")) describingOutput = true
                    if (describingOutput && !outputSize.isDone) parseRawvideoSize(line)?.let(outputSize::complete)
                    if (line.contains("error", ignoreCase = true) || line.contains("invalid", ignoreCase = true)) {
                        synchronized(errorLines) {
                            errorLines.addLast(line.trim())
                            if (errorLines.size > MAX_ERROR_LINES) errorLines.removeFirst()
                        }
                    }
                }
            } catch (_: IOException) {
                // The process was destroyed while its log was being read.
            } finally {
                outputSize.complete(null)
            }
        }
    }

    fun errors(): String = synchronized(errorLines) { errorLines.joinToString(" ") }.take(MAX_ERROR_CHARS)
}

/** The frame size in ffmpeg's description of a rawvideo output stream, as in `rawvideo (BGRA …), 1080x2400 [SAR …]`. */
internal fun parseRawvideoSize(line: String): IntSize? {
    if (!line.contains("Video: rawvideo")) return null
    val match = Regex("""[ ,](\d{2,5})x(\d{2,5})[ ,\[]""").find(line) ?: return null
    return IntSize(match.groupValues[1].toInt(), match.groupValues[2].toInt())
}

/** How to install ffmpeg, appended to every message about it missing. */
internal const val FFMPEG_INSTALL = "Install ffmpeg: brew install ffmpeg (macOS), winget install ffmpeg (Windows) or apt install ffmpeg (Linux)."

private const val BYTES_PER_PIXEL = 4

private const val FFMPEG_EXIT_WAIT_MILLIS = 2_000L

private const val MAX_ERROR_LINES = 5

private const val MAX_ERROR_CHARS = 500
