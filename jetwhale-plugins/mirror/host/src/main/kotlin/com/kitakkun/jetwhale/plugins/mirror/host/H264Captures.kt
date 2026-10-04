package com.kitakkun.jetwhale.plugins.mirror.host

import androidx.annotation.VisibleForTesting
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * The first frame of the raw H.264 in [h264], encoded as PNG at the stream's own size by the
 * ffmpeg at [ffmpegPath]. Blocks until the frame is out, or until [timeoutMillis] pass without
 * one; the caller closes the source of [h264] afterwards.
 */
internal fun firstH264FrameAsPng(h264: InputStream, ffmpegPath: String, timeoutMillis: Long): ByteArray {
    val ffmpegProcess = SystemProcessLauncher.start(ffmpegFirstFramePngCommand(ffmpegPath))
    val log = thread(isDaemon = true, name = "mirror-still-ffmpeg-log") { ffmpegProcess.errorStream.use(InputStream::readAllBytes) }
    val feeding = thread(isDaemon = true, name = "mirror-still-ffmpeg-input") { feed(h264, ffmpegProcess) }
    val watchdog = thread(isDaemon = true, name = "mirror-still-watchdog") {
        if (!ffmpegProcess.waitFor(timeoutMillis, TimeUnit.MILLISECONDS)) ffmpegProcess.destroyForcibly()
    }
    val png = try {
        ffmpegProcess.inputStream.use(InputStream::readAllBytes)
    } finally {
        ffmpegProcess.waitFor(FFMPEG_EXIT_WAIT_MILLIS, TimeUnit.MILLISECONDS)
        ffmpegProcess.destroyForcibly()
        feeding.join(FFMPEG_EXIT_WAIT_MILLIS)
        watchdog.interrupt()
        log.join(FFMPEG_EXIT_WAIT_MILLIS)
    }
    if (png.isEmpty()) throw deviceControlError("the device's video stream sent no frame within ${timeoutMillis / 1000} s")
    return png
}

/**
 * The ffmpeg command that turns the first frame of raw H.264 on stdin into one PNG on stdout, at
 * the frame's own size. Probing is kept short for the same reason as [ffmpegDecodeCommand]'s.
 */
internal fun ffmpegFirstFramePngCommand(ffmpegPath: String): List<String> = listOf(
    ffmpegPath, "-hide_banner", "-nostats", "-loglevel", "error",
    "-probesize", "65536", "-analyzeduration", "500000",
    "-f", "h264", "-i", "pipe:0",
    "-frames:v", "1", "-f", "image2pipe", "-c:v", "png", "pipe:1",
)

/**
 * The ffmpeg command that wraps raw H.264 on stdin into an mp4 at [outputPath] without encoding it
 * again. Raw H.264 carries no timestamps, so each frame is stamped with when it arrived, which is
 * when the device sent it. `+faststart` puts the index first, so the file plays while it loads.
 */
@VisibleForTesting
internal fun ffmpegRemuxCommand(ffmpegPath: String, outputPath: String): List<String> = listOf(
    ffmpegPath, "-hide_banner", "-nostats", "-loglevel", "error",
    "-use_wallclock_as_timestamps", "1", "-f", "h264", "-i", "pipe:0",
    "-c", "copy", "-movflags", "+faststart", "-y", outputPath,
)

/**
 * Writes the raw H.264 that [sourceProcess] prints into [outputFile] as an mp4, through the ffmpeg
 * at [ffmpegPath], until [stop].
 */
internal class H264FileRecorder(private val sourceProcess: Process, ffmpegPath: String, private val outputFile: File) {
    private val ffmpegProcess = SystemProcessLauncher.start(ffmpegRemuxCommand(ffmpegPath, outputFile.absolutePath))
    private val errors = StringBuilder()
    private val log = thread(isDaemon = true, name = "mirror-recording-ffmpeg-log") {
        try {
            ffmpegProcess.errorStream.bufferedReader().forEachLine { synchronized(errors) { errors.appendLine(it) } }
        } catch (_: IOException) {
        }
    }
    private val feeding = thread(isDaemon = true, name = "mirror-recording-ffmpeg-input") { feed(sourceProcess.inputStream, ffmpegProcess) }

    /**
     * Ends the source, which ends ffmpeg's input; ffmpeg then writes the mp4's index and exits.
     * Killing ffmpeg instead would leave a file without an index, which does not play.
     */
    fun stop(): File {
        sourceProcess.destroy()
        if (!sourceProcess.waitFor(FFMPEG_EXIT_WAIT_MILLIS, TimeUnit.MILLISECONDS)) sourceProcess.destroyForcibly()
        feeding.join(FFMPEG_EXIT_WAIT_MILLIS)
        val finished = ffmpegProcess.waitFor(RECORDING_FINISH_WAIT_MILLIS, TimeUnit.MILLISECONDS)
        if (!finished) ffmpegProcess.destroyForcibly()
        log.join(FFMPEG_EXIT_WAIT_MILLIS)
        if (!finished || ffmpegProcess.exitValue() != 0 || outputFile.length() == 0L) {
            val reason = synchronized(errors, errors::toString).trim().take(MAX_ERROR_CHARS)
            throw deviceControlError("the recording could not be written${if (reason.isEmpty()) "" else ": $reason"}")
        }
        return outputFile
    }
}

private const val FFMPEG_EXIT_WAIT_MILLIS = 2_000L

/** Writing the mp4's index after the last frame takes a moment for a long recording. */
private const val RECORDING_FINISH_WAIT_MILLIS = 15_000L

private const val MAX_ERROR_CHARS = 500
