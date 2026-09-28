package com.kitakkun.jetwhale.plugins.mirror.host

import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import kotlin.concurrent.thread

/**
 * A physical iOS device over USB. idb streams its screen through the device's companion, but idb
 * sends touches, buttons and text to simulators only, so a device is watched and not driven.
 *
 * Its screenshots and recordings come from that H.264 stream through ffmpeg: `idb screenshot` does
 * not reach a device running iOS 17 or later. The companion outlives each use by
 * [IdbCompanions]' idle timeout, so repeated screenshots do not restart it.
 */
internal class IosPhysicalDeviceController(
    private val udid: String,
    private val idbPath: String,
    private val companions: IdbCompanions,
    private val ffmpegPath: String?,
) : DeviceController,
    DeviceScreen {
    override val screen: DeviceScreen get() = this
    override val input: DeviceInput? get() = null
    override val power: DevicePower? get() = null
    override val recorder: DeviceRecorder? = ffmpegPath?.let(::PhysicalRecorder)

    private var streaming = false

    @Volatile
    private var knownScreenSize: IntSize? = null

    override suspend fun captureScreenshot(): ByteArray {
        val ffmpegPath = requireFfmpegPath("a screenshot of a physical iOS device")
        companions.acquire(udid)
        try {
            val stream = startVideoStream()
            try {
                return withContext(Dispatchers.IO) { firstH264FrameAsPng(stream.inputStream, ffmpegPath, STILL_FRAME_TIMEOUT_MILLIS) }
            } finally {
                stream.destroyForcibly()
            }
        } finally {
            companions.release(udid)
        }
    }

    // The screen's size never changes, so idb is asked once. The companion is held only for the
    // question: this runs before a stream and after a recording alike, and only a stream keeps it.
    override suspend fun screenSize(): IntSize {
        knownScreenSize?.let { return it }
        companions.acquire(udid)
        val described = try {
            describeIdbScreen(idbPath, udid)
        } finally {
            companions.release(udid)
        }
        return described.size.also { knownScreenSize = it }
    }

    // --fps is ignored for a device, which streams at about 60; the mirror drops what it cannot show.
    override suspend fun openVideoStream(wanted: IntSize?): VideoStream {
        val ffmpegPath = requireFfmpegPath("mirroring a physical iOS device")
        holdCompanion()
        return withContext(Dispatchers.IO) { VideoStream.H264(SystemProcessLauncher.start(videoStreamCommand()), ffmpegPath) }
    }

    private fun videoStreamCommand(): List<String> = listOf(idbPath, "video-stream", "--udid", udid, "--format", "h264", "--fps", "30")

    /** An idb stream of the device's screen as H.264 on stdout, its log drained so it never stalls on a full pipe. */
    private suspend fun startVideoStream(): Process = withContext(Dispatchers.IO) {
        SystemProcessLauncher.start(videoStreamCommand()).also { stream ->
            thread(isDaemon = true, name = "mirror-idb-stream-log") { stream.errorStream.use(InputStream::readAllBytes) }
        }
    }

    private fun requireFfmpegPath(use: String): String = ffmpegPath ?: throw deviceControlError("$use needs ffmpeg to decode its video. $FFMPEG_INSTALL")

    private suspend fun holdCompanion() {
        if (streaming) return
        companions.acquire(udid)
        streaming = true
    }

    override suspend fun release() {
        if (!streaming) return
        streaming = false
        companions.release(udid)
    }

    private inner class PhysicalRecorder(private val ffmpegPath: String) : DeviceRecorder {
        override suspend fun startRecording(outputFile: File): DeviceRecording {
            companions.acquire(udid)
            val recorder = try {
                val stream = startVideoStream()
                try {
                    withContext(Dispatchers.IO) { H264FileRecorder(stream, ffmpegPath, outputFile) }
                } catch (e: DeviceControlException) {
                    stream.destroyForcibly()
                    throw e
                }
            } catch (e: DeviceControlException) {
                companions.release(udid)
                throw e
            }
            return object : DeviceRecording {
                override suspend fun stop(): File = try {
                    withContext(Dispatchers.IO) { recorder.stop() }
                } finally {
                    companions.release(udid)
                }
            }
        }
    }
}

/** How long a screenshot waits for the device's stream to send its first frame. */
private const val STILL_FRAME_TIMEOUT_MILLIS = 10_000L

internal const val VIEW_ONLY = "a physical iOS device is view-only: idb sends touches, buttons and text to simulators only"
