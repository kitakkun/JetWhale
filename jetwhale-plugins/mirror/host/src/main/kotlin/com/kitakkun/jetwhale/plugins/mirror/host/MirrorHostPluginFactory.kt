package com.kitakkun.jetwhale.plugins.mirror.host

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.kitakkun.jetwhale.host.sdk.ExperimentalJetWhaleApi
import com.kitakkun.jetwhale.host.sdk.JetWhaleHostPlugin
import com.kitakkun.jetwhale.host.sdk.JetWhaleHostPluginFactory
import com.kitakkun.jetwhale.host.sdk.JetWhaleHostPluginUi
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArgumentException
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpCapablePlugin
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpCommand
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.bytedeco.javacv.FFmpegFrameGrabber
import org.bytedeco.javacv.Java2DFrameConverter
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.ImageInfo
import java.awt.image.BufferedImage
import java.awt.image.DataBufferInt
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentHashMap
import org.jetbrains.skia.Image as SkiaImage

// Instantiated by the host via the fully-qualified name declared in plugin-manifest.json.
@Suppress("UNUSED")
class MirrorHostPluginFactory : JetWhaleHostPluginFactory {
    override fun createPlugin(): JetWhaleHostPlugin = MirrorHostPlugin()
}

private const val DEVICE_POLL_INTERVAL_MILLIS = 3_000L

// The selected device's frame loop is paced by the screenshot capture itself (~100-300ms per
// adb/simctl round trip); this small gap only keeps a failing capture from busy-looping.
private const val SELECTED_FRAME_INTERVAL_MILLIS = 16L

// Unselected devices stay visible but refresh slowly, so mirroring many devices at once does
// not multiply the process-spawn/decode cost.
private const val BACKGROUND_FRAME_INTERVAL_MILLIS = 500L

// Back off after a failed capture so a dead device doesn't spam error processes.
private const val FRAME_RETRY_INTERVAL_MILLIS = 1_000L

// Publish caps for decoded stream frames: decoding always keeps up with the stream, but
// conversion + publication is throttled by wall-clock (~30fps selected, ~2fps unselected).
// 25ms sits below the 33.3ms interval of the 30fps source so every source frame can pass; the
// cap only bounds a bursty source, and publication never exceeds the source rate regardless.
private const val SELECTED_PUBLISH_GAP_MILLIS = 25L
private const val BACKGROUND_PUBLISH_GAP_MILLIS = 500L

// A stream that has not produced a frame by this deadline is treated as broken.
private const val STREAM_START_TIMEOUT_MILLIS = 7_000L

@OptIn(ExperimentalJetWhaleApi::class)
private class MirrorHostPlugin :
    JetWhaleHostPlugin(),
    JetWhaleHostPluginUi,
    JetWhaleMcpCapablePlugin {

    private val discovery = DeviceDiscovery()

    private val devices: SnapshotStateList<MirrorDevice> = mutableStateListOf()
    private var selectedDeviceId by mutableStateOf<String?>(null)
    private var mirroringEnabled by mutableStateOf(true)

    // All connected devices are mirrored concurrently, one capture loop and frame slot each.
    private val frames: SnapshotStateMap<String, ImageBitmap> = mutableStateMapOf()
    private val mirrorJobs = mutableMapOf<String, Job>()

    // Live decoder subprocesses; grabImage() blocks the IO thread, so cancellation must kill the
    // process to unblock it.
    private val streamProcesses = ConcurrentHashMap<String, Process>()
    private val refreshMutex = Mutex()

    private var lastError by mutableStateOf<String?>(null)
    private var activeRecording by mutableStateOf<ActiveRecording?>(null)

    private class ActiveRecording(val deviceId: String, val recording: DeviceRecording)

    private val selectedDevice: MirrorDevice? get() = devices.firstOrNull { it.id == selectedDeviceId }

    override fun onCreate() {
        pluginScope.launch {
            while (isActive) {
                refreshDevices()
                delay(DEVICE_POLL_INTERVAL_MILLIS)
            }
        }
    }

    override fun onDispose() {
        streamProcesses.values.forEach { it.destroyForcibly() }
        streamProcesses.clear()
    }

    private suspend fun refreshDevices(): List<MirrorDevice> = refreshMutex.withLock {
        val discovered = discovery.discoverDevices()
        devices.apply {
            clear()
            addAll(discovered)
        }
        if (devices.none { it.id == selectedDeviceId }) {
            selectedDeviceId = devices.firstOrNull()?.id
        }
        val discoveredIds = discovered.map { it.id }.toSet()
        mirrorJobs.keys.filter { it !in discoveredIds }.forEach { id ->
            mirrorJobs.remove(id)?.cancel()
            streamProcesses.remove(id)?.destroyForcibly()
            frames.remove(id)
        }
        discovered.forEach { device ->
            if (mirrorJobs[device.id]?.isActive != true) {
                mirrorJobs[device.id] = pluginScope.launch { mirrorLoop(device) }
            }
        }
        discovered
    }

    // Mirrors one device: preferably by decoding a live H.264 stream, falling back to screenshot
    // polling when the platform/tooling cannot stream or the stream setup fails.
    private suspend fun mirrorLoop(device: MirrorDevice) {
        var streamingBroken = false
        var lastPngHash = 0
        while (currentCoroutineContext().isActive) {
            if (!mirroringEnabled) {
                delay(DEVICE_POLL_INTERVAL_MILLIS)
                continue
            }
            if (!streamingBroken) {
                val process = try {
                    device.controller.openVideoStreamProcess()
                } catch (_: DeviceControlException) {
                    null
                }
                if (process != null) {
                    // Returns when the stream ends (screenrecord's time limit) — reopen right
                    // away; if it produced nothing, the stream path doesn't work on this device.
                    if (decodeVideoStream(device, process)) continue
                }
                streamingBroken = true
            }
            try {
                val png = device.controller.captureScreenshot()
                // A static screen produces byte-identical PNGs; skip the decode and the
                // recomposition it would trigger.
                val pngHash = png.contentHashCode()
                if (pngHash != lastPngHash) {
                    frames[device.id] = SkiaImage.makeFromEncoded(png).toComposeImageBitmap()
                    lastPngHash = pngHash
                }
                delay(if (device.id == selectedDeviceId) SELECTED_FRAME_INTERVAL_MILLIS else BACKGROUND_FRAME_INTERVAL_MILLIS)
            } catch (e: DeviceControlException) {
                lastError = "${device.name}: ${e.message}"
                delay(FRAME_RETRY_INTERVAL_MILLIS)
            }
        }
    }

    /**
     * Decodes H.264 frames from [process]'s stdout into [frames] until the stream ends or the
     * loop is cancelled. Returns whether at least one frame was decoded.
     */
    private suspend fun decodeVideoStream(device: MirrorDevice, process: Process): Boolean = withContext(Dispatchers.IO) {
        streamProcesses[device.id] = process
        var produced = false
        val grabber = FFmpegFrameGrabber(process.inputStream, 0)
        grabber.format = "h264"
        // Live-stream tuning: ffmpeg's defaults buffer input and probe the format for seconds,
        // which shows up as a laggy, ever-delayed mirror. A raw H.264 stream needs almost no
        // probing, and buffering is pure latency here.
        grabber.setOption("fflags", "nobuffer")
        grabber.setOption("flags", "low_delay")
        grabber.setOption("probesize", "65536")
        grabber.setOption("analyzeduration", "500000")
        val converter = Java2DFrameConverter()
        // grabber.start() blocks in format probing for as long as the process keeps its stdout
        // open without writing (e.g. idb without a working companion). The watchdog kills the
        // process to unblock it so the loop can fall back to screenshot polling.
        val firstFrameSeen = java.util.concurrent.atomic.AtomicBoolean(false)
        val watchdog = pluginScope.launch {
            delay(STREAM_START_TIMEOUT_MILLIS)
            if (!firstFrameSeen.get()) process.destroyForcibly()
        }
        try {
            grabber.start()
            var lastPublishNanos = 0L
            // Reused across frames so publishing does not allocate a full-resolution buffer every
            // frame (~12MB at 1440p). The decode loop is per-device, so these are never shared.
            var argbScratch: BufferedImage? = null
            var pixelScratch: ByteArray? = null
            while (currentCoroutineContext().isActive && mirroringEnabled) {
                val frame = grabber.grabImage() ?: break
                produced = true
                firstFrameSeen.set(true)
                // Decoding must keep up with the stream (or frames back up in the pipe and the
                // mirror falls ever further behind), but converting/publishing every decoded
                // frame is what actually costs: cap it by wall-clock instead, and give
                // unselected devices a much lower cap.
                val minPublishGapMillis = if (device.id == selectedDeviceId) SELECTED_PUBLISH_GAP_MILLIS else BACKGROUND_PUBLISH_GAP_MILLIS
                val now = System.nanoTime()
                if (now - lastPublishNanos >= minPublishGapMillis * 1_000_000) {
                    val image = converter.convert(frame) ?: continue
                    // BufferedImage.toComposeImageBitmap() converts pixel-by-pixel (~40ms at 1440p),
                    // which alone caps the mirror near 20fps. Copy the pixels straight into a Skia
                    // bitmap as BGRA instead: an INT_ARGB image's native-order ints already lay out
                    // as B,G,R,A bytes on a little-endian host, so the publish drops to a few ms.
                    val w = image.width
                    val h = image.height
                    val argb = if (image.type == BufferedImage.TYPE_INT_ARGB) {
                        image
                    } else {
                        val scratch = argbScratch?.takeIf { it.width == w && it.height == h }
                            ?: BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB).also { argbScratch = it }
                        scratch.createGraphics().apply {
                            drawImage(image, 0, 0, null)
                            dispose()
                        }
                        scratch
                    }
                    val pixels = (argb.raster.dataBuffer as DataBufferInt).data
                    val bytes = pixelScratch?.takeIf { it.size == pixels.size * 4 }
                        ?: ByteArray(pixels.size * 4).also { pixelScratch = it }
                    ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asIntBuffer().put(pixels)
                    // installPixels copies into the bitmap's own storage, so reusing bytes is safe.
                    val info = ImageInfo(w, h, ColorType.BGRA_8888, ColorAlphaType.OPAQUE)
                    val bitmap = Bitmap().apply {
                        allocPixels(info)
                        installPixels(info, bytes, w * 4)
                        setImmutable()
                    }
                    frames[device.id] = bitmap.asComposeImageBitmap()
                    lastPublishNanos = now
                }
            }
        } catch (e: Exception) {
            if (!produced) lastError = "${device.name}: video stream failed (${e.message}); falling back to screenshots"
        } finally {
            watchdog.cancel()
            runCatching { grabber.close() }
            streamProcesses.remove(device.id)
            process.destroyForcibly()
        }
        produced
    }

    // Runs a device-control action from a UI callback, surfacing failures in the status bar.
    private fun runControl(action: suspend () -> Unit) {
        pluginScope.launch {
            try {
                action()
                lastError = null
            } catch (e: DeviceControlException) {
                lastError = e.message
            }
        }
    }

    @Composable
    override fun Content() {
        MirrorScreen(
            devices = devices,
            selectedDeviceId = selectedDeviceId,
            onSelectDevice = { id -> selectedDeviceId = id },
            frames = frames,
            mirroringEnabled = mirroringEnabled,
            onToggleMirroring = { mirroringEnabled = it },
            errorMessage = lastError,
            onRefreshDevices = { pluginScope.launch { refreshDevices() } },
            onTap = { device, x, y -> runControl { device.controller.tap(x, y) } },
            onSwipe = { device, fromX, fromY, toX, toY ->
                runControl { device.controller.swipe(fromX, fromY, toX, toY, durationMillis = 200) }
            },
            onPressButton = { button -> selectedDevice?.let { runControl { it.controller.pressButton(button) } } },
            onInputText = { text -> selectedDevice?.let { runControl { it.controller.inputText(text) } } },
            onSaveScreenshot = { selectedDevice?.let { runControl { saveScreenshotToDisk(it) } } },
            isRecording = activeRecording != null,
            onToggleRecording = {
                if (activeRecording != null) {
                    runControl { lastError = "Saved recording: ${stopRecording().absolutePath}" }
                } else {
                    selectedDevice?.let { runControl { startRecording(it) } }
                }
            },
        )
    }

    private suspend fun saveScreenshotToDisk(device: MirrorDevice) {
        val png = device.controller.captureScreenshot()
        val file = screenshotFile(device)
        file.writeBytes(png)
        lastError = "Saved screenshot: ${file.absolutePath}"
    }

    private suspend fun startRecording(device: MirrorDevice) {
        if (activeRecording != null) throw DeviceControlException("a recording is already in progress; stop it first")
        val recording = device.controller.startRecording(recordingFile(device))
        activeRecording = ActiveRecording(deviceId = device.id, recording = recording)
    }

    private suspend fun stopRecording(): java.io.File {
        val active = activeRecording ?: throw DeviceControlException("no recording in progress")
        activeRecording = null
        return active.recording.stop()
    }

    // -------------------------------------------------------------------------
    // JetWhaleMcpCapablePlugin
    // -------------------------------------------------------------------------

    // MCP tools address devices by id; with the id omitted they fall back to the device selected
    // in the mirror UI so the AI can simply operate "the visible device".
    private fun resolveDevice(deviceId: String?): MirrorDevice {
        if (deviceId == null) {
            return selectedDevice
                ?: throw JetWhaleMcpArgumentException("no device connected; boot an emulator/simulator and call $TOOL_PREFIX.listDevices")
        }
        return devices.firstOrNull { it.id == deviceId }
            ?: throw JetWhaleMcpArgumentException("unknown deviceId: $deviceId (call $TOOL_PREFIX.listDevices)")
    }

    override val mcpCommands: List<JetWhaleMcpCommand> = listOf(
        ListDevicesCommand(refreshDevices = ::refreshDevices, selectedDeviceId = { selectedDeviceId }),
        CaptureScreenshotCommand(resolveDevice = ::resolveDevice),
        TapCommand(resolveDevice = ::resolveDevice),
        SwipeCommand(resolveDevice = ::resolveDevice),
        PressButtonCommand(resolveDevice = ::resolveDevice),
        InputTextCommand(resolveDevice = ::resolveDevice),
        StartRecordingCommand(resolveDevice = ::resolveDevice, startRecording = ::startRecording),
        StopRecordingCommand(stopRecording = ::stopRecording),
    )
}
