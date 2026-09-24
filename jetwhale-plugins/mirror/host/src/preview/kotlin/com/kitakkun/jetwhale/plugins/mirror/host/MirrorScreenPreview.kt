package com.kitakkun.jetwhale.plugins.mirror.host

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.tooling.preview.Preview
import com.kitakkun.jetwhale.host.ui.JwTheme
import org.jetbrains.skia.Color
import java.io.File

private val previewDevices = listOf(
    DeviceListing(id = "emulator-5554", name = "Pixel 9", kind = DeviceKind.AndroidEmulator, osVersion = null),
    DeviceListing(id = "0A1B2C3D-SIMULATOR", name = "iPhone 16", kind = DeviceKind.IosSimulator, osVersion = "iOS 18.5"),
    DeviceListing(id = "00008110-DEVICE", name = "iPhone 13", kind = DeviceKind.IosDevice, osVersion = "iOS 26.6.1"),
)

private val androidCapabilities = DeviceCapabilities(
    input = true,
    buttons = listOf(DeviceButton.Home, DeviceButton.Back, DeviceButton.Power),
    recording = true,
)

private val previewCaptures = listOf(CaptureKind.Screenshot, CaptureKind.Recording).mapIndexed { index, kind ->
    Capture(
        file = File("/tmp/captures/Pixel-9-1a2b3c4d/2026-09-25/10300$index-${kind.suffix}.${kind.extension}"),
        info = CaptureInfo(
            deviceId = "emulator-5554",
            deviceName = "Pixel 9",
            platform = "Android",
            deviceKind = "Emulator",
            osVersion = null,
            kind = kind,
            widthPx = 1080,
            heightPx = 2400,
            capturedAtEpochMillis = 1_790_300_000_000 + index * 60_000L,
            durationMillis = if (kind == CaptureKind.Recording) 12_400 else null,
        ),
    )
}

private object NoThumbnails : ThumbnailSource {
    override fun cachedThumbnail(capture: Capture): ImageBitmap? = null

    override suspend fun loadThumbnail(capture: Capture): ImageBitmap? = null
}

private object NoCaptureActions : CapturesActions {
    override fun showAllDevices(all: Boolean) = Unit

    override fun filterKind(kind: CaptureKind?) = Unit

    override fun filterDay(day: String?) = Unit

    override fun select(capture: Capture?) = Unit

    override fun open(capture: Capture) = Unit

    override fun reveal(capture: Capture) = Unit

    override fun copyImage(capture: Capture) = Unit

    override fun copyPath(capture: Capture) = Unit

    override fun delete(capture: Capture) = Unit

    override fun openDeviceFolder() = Unit

    override fun chooseFolder() = Unit
}

private object NoActions : MirrorActions {
    override fun select(deviceId: String) = Unit

    override fun tap(x: Int, y: Int) = Unit

    override fun swipe(fromX: Int, fromY: Int, toX: Int, toY: Int) = Unit

    override fun pressButton(button: DeviceButton) = Unit

    override fun inputText(text: String) = Unit

    override fun saveScreenshot() = Unit

    override fun toggleRecording() = Unit
}

@Preview
@Composable
private fun MirrorScreenStreamingPreview() {
    JwTheme(darkTheme = true) {
        MirrorScreen(
            devices = previewDevices,
            capabilities = androidCapabilities,
            missingTools = emptyList(),
            selectedId = "emulator-5554",
            state = MirrorState.Streaming,
            status = null,
            recording = false,
            surface = rememberPreviewSurface(),
            actions = NoActions,
            showCaptures = false,
            onToggleCaptures = {},
            capturesPanel = {},
        )
    }
}

@Preview
@Composable
private fun MirrorScreenNoFramesPreview() {
    JwTheme(darkTheme = false) {
        MirrorScreen(
            devices = previewDevices,
            capabilities = DeviceCapabilities(input = false, buttons = emptyList(), recording = false),
            missingTools = listOf("adb was not found, so Android devices are not listed. Install the Android SDK platform tools."),
            selectedId = "00008110-DEVICE",
            state = MirrorState.NoFrames(noFramesHints(DeviceKind.IosDevice)),
            status = null,
            recording = false,
            surface = remember(::MirrorSurface),
            actions = NoActions,
            showCaptures = false,
            onToggleCaptures = {},
            capturesPanel = {},
        )
    }
}

@Preview
@Composable
private fun MirrorScreenEmptyPreview() {
    JwTheme(darkTheme = false) {
        MirrorScreen(
            devices = emptyList(),
            capabilities = null,
            missingTools = emptyList(),
            selectedId = null,
            state = MirrorState.Idle,
            status = null,
            recording = false,
            surface = remember(::MirrorSurface),
            actions = NoActions,
            showCaptures = false,
            onToggleCaptures = {},
            capturesPanel = {},
        )
    }
}

@Preview
@Composable
private fun MirrorScreenWithCapturesPreview() {
    JwTheme(darkTheme = true) {
        MirrorScreen(
            devices = previewDevices,
            capabilities = androidCapabilities,
            missingTools = emptyList(),
            selectedId = "emulator-5554",
            state = MirrorState.Streaming,
            status = null,
            recording = true,
            surface = rememberPreviewSurface(),
            actions = NoActions,
            showCaptures = true,
            onToggleCaptures = {},
            capturesPanel = { CapturesPanelPreview() },
        )
    }
}

@Preview
@Composable
private fun CapturesPanelPreview() {
    JwTheme(darkTheme = false) {
        CapturesPanel(
            captures = previewCaptures,
            allDevices = false,
            kind = null,
            day = null,
            selected = previewCaptures.first(),
            status = null,
            thumbnails = NoThumbnails,
            actions = NoCaptureActions,
        )
    }
}

@Preview
@Composable
private fun MirrorVideoPreview() {
    JwTheme(darkTheme = true) {
        MirrorVideo(surface = rememberPreviewSurface(), interactive = true, onTap = { _, _ -> }, onSwipe = { _, _, _, _ -> }, modifier = Modifier.fillMaxSize())
    }
}

@Composable
private fun rememberPreviewSurface(): MirrorSurface = remember {
    MirrorSurface().apply {
        writeFrame(width = 108, height = 240) { bitmap ->
            bitmap.erase(Color.makeRGB(r = 0x3D, g = 0x5A, b = 0xFE))
            true
        }
    }
}
