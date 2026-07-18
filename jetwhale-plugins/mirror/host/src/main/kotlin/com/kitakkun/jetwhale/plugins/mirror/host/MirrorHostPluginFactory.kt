package com.kitakkun.jetwhale.plugins.mirror.host

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.kitakkun.jetwhale.host.sdk.ExperimentalJetWhaleApi
import com.kitakkun.jetwhale.host.sdk.JetWhaleHostPlugin
import com.kitakkun.jetwhale.host.sdk.JetWhaleHostPluginFactory
import com.kitakkun.jetwhale.host.sdk.JetWhaleHostPluginUi
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpArgumentException
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpCapablePlugin
import com.kitakkun.jetwhale.host.sdk.JetWhaleMcpCommand
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.jetbrains.skia.Image as SkiaImage

// Instantiated by the host via the fully-qualified name declared in plugin-manifest.json.
@Suppress("UNUSED")
class MirrorHostPluginFactory : JetWhaleHostPluginFactory {
    override fun createPlugin(): JetWhaleHostPlugin = MirrorHostPlugin()
}

private const val DEVICE_POLL_INTERVAL_MILLIS = 3_000L
private const val FRAME_INTERVAL_MILLIS = 350L

@OptIn(ExperimentalJetWhaleApi::class)
private class MirrorHostPlugin :
    JetWhaleHostPlugin(),
    JetWhaleHostPluginUi,
    JetWhaleMcpCapablePlugin {

    private val discovery = DeviceDiscovery()

    private val devices: SnapshotStateList<MirrorDevice> = mutableStateListOf()
    private var selectedDeviceId by mutableStateOf<String?>(null)
    private var mirroringEnabled by mutableStateOf(true)
    private var latestFrame by mutableStateOf<ImageBitmap?>(null)
    private var lastError by mutableStateOf<String?>(null)

    private val selectedDevice: MirrorDevice? get() = devices.firstOrNull { it.id == selectedDeviceId }

    override fun onCreate() {
        pluginScope.launch {
            while (isActive) {
                refreshDevices()
                delay(DEVICE_POLL_INTERVAL_MILLIS)
            }
        }
        // The mirror is a screenshot poll loop: cheap, tool-free, and identical for both platforms.
        pluginScope.launch {
            while (isActive) {
                val device = selectedDevice
                if (mirroringEnabled && device != null) {
                    try {
                        val png = device.controller.captureScreenshot()
                        latestFrame = SkiaImage.makeFromEncoded(png).toComposeImageBitmap()
                        lastError = null
                    } catch (e: DeviceControlException) {
                        lastError = e.message
                    }
                    delay(FRAME_INTERVAL_MILLIS)
                } else {
                    delay(DEVICE_POLL_INTERVAL_MILLIS)
                }
            }
        }
    }

    private suspend fun refreshDevices(): List<MirrorDevice> {
        val discovered = discovery.discoverDevices()
        devices.apply {
            clear()
            addAll(discovered)
        }
        if (devices.none { it.id == selectedDeviceId }) {
            selectedDeviceId = devices.firstOrNull()?.id
            latestFrame = null
        }
        return discovered
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
            onSelectDevice = { id ->
                selectedDeviceId = id
                latestFrame = null
            },
            frame = latestFrame,
            mirroringEnabled = mirroringEnabled,
            onToggleMirroring = { mirroringEnabled = it },
            errorMessage = lastError,
            onRefreshDevices = { pluginScope.launch { refreshDevices() } },
            onTap = { x, y -> selectedDevice?.let { runControl { it.controller.tap(x, y) } } },
            onSwipe = { fromX, fromY, toX, toY ->
                selectedDevice?.let { runControl { it.controller.swipe(fromX, fromY, toX, toY, durationMillis = 200) } }
            },
            onPressButton = { button -> selectedDevice?.let { runControl { it.controller.pressButton(button) } } },
            onInputText = { text -> selectedDevice?.let { runControl { it.controller.inputText(text) } } },
            onSaveScreenshot = { selectedDevice?.let { runControl { saveScreenshotToDisk(it) } } },
        )
    }

    private suspend fun saveScreenshotToDisk(device: MirrorDevice) {
        val png = device.controller.captureScreenshot()
        val file = screenshotFile(device)
        file.writeBytes(png)
        lastError = "Saved screenshot: ${file.absolutePath}"
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
    )
}
