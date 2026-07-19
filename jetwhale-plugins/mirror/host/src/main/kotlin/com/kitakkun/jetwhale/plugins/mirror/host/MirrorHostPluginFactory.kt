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

// Unselected devices publish only every Nth decoded stream frame (~2fps at 30fps input).
private const val BACKGROUND_STREAM_FRAME_STRIDE = 15

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
        val converter = Java2DFrameConverter()
        try {
            grabber.start()
            var frameIndex = 0
            while (currentCoroutineContext().isActive && mirroringEnabled) {
                val frame = grabber.grabImage() ?: break
                produced = true
                frameIndex++
                // Unselected devices decode (H.264 requires it) but convert/publish only a
                // subset of frames to keep the multi-device cost down.
                if (device.id == selectedDeviceId || frameIndex % BACKGROUND_STREAM_FRAME_STRIDE == 0) {
                    val image = converter.convert(frame) ?: continue
                    frames[device.id] = image.toComposeImageBitmap()
                }
            }
        } catch (e: Exception) {
            if (!produced) lastError = "${device.name}: video stream failed (${e.message}); falling back to screenshots"
        } finally {
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
