package com.kitakkun.jetwhale.plugins.mirror.host

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SkikoComposeUiTest
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.kitakkun.jetwhale.tools.docsscreenshots.DocsShot
import com.kitakkun.jetwhale.tools.docsscreenshots.DocsShotRecorder
import com.kitakkun.jetwhale.tools.docsscreenshots.InMemoryPluginStorage
import com.kitakkun.jetwhale.tools.docsscreenshots.PluginSceneSurface
import com.kitakkun.jetwhale.tools.docsscreenshots.onSurface
import kotlinx.coroutines.awaitCancellation
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Canvas
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.Font
import org.jetbrains.skia.FontMgr
import org.jetbrains.skia.FontStyle
import org.jetbrains.skia.Paint
import org.jetbrains.skia.RRect
import org.jetbrains.skia.Rect
import java.io.File
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test

/**
 * [MirrorScreen] and [DeviceGrid] rather than the Root: the Root drives a live [DeviceMirror],
 * which needs adb or idb and a device. Each device shows [SampleAppScreenPainter]'s made-up app as
 * its picture.
 */
@OptIn(ExperimentalTestApi::class)
class DeviceMirrorDocsScreenshots {
    private val recorder = DocsShotRecorder.forImagesDirectoryProperty()

    @Test
    fun `the live view of an Android emulator`() = recorder.record(
        DocsShot(page = PAGE, name = "live", surfaceSize = DpSize(688.dp, 520.dp), density = 2f, displayWidth = 688),
    ) { darkTheme ->
        setMirrorScreen(darkTheme, showCaptures = false)
        onSurface()
    }

    @Test
    fun `the grid of every device`() = recorder.record(
        DocsShot(page = PAGE, name = "grid", surfaceSize = DpSize(860.dp, 480.dp), density = 1.6f, displayWidth = 688),
    ) { darkTheme ->
        val thumbnails = mapOf(
            PIXEL.id to ThumbnailState.Live,
            IPHONE.id to ThumbnailState.Live,
            PIXEL_DEVICE.id to ThumbnailState.ScreenOff,
        ).mapValues { (id, state) ->
            // The grid measures a thumbnail's age against the system clock, so the current time is
            // what keeps its caption at "just now".
            DeviceThumbnail(image = sampleAppScreenImage(SCREEN_SIZES.getValue(id)), updatedAtMillis = System.currentTimeMillis(), state = state)
        }
        setContent {
            PluginSceneSurface(darkTheme = darkTheme, storage = InMemoryPluginStorage(emptyMap())) {
                DeviceGrid(
                    devices = DEVICES,
                    selectedId = PIXEL.id,
                    missingTools = emptyList(),
                    notices = PreviewNotices(notice = null),
                    recording = GridRecordingState(recordingDeviceIds = emptySet(), recordableDevices = listOf(PIXEL, IPHONE, PIXEL_DEVICE), warningSuppressed = false),
                    recordingActions = NoGridRecordingActions,
                    thumbnailOf = thumbnails::getValue,
                    poll = { _, _ -> awaitCancellation() },
                    livenessOf = { id -> if (thumbnails.getValue(id).state == ThumbnailState.ScreenOff) DeviceLiveness.ScreenOff else DeviceLiveness.Live },
                    onOpen = {},
                    onScreenshot = {},
                    onScreenshotAll = {},
                )
            }
        }
        onSurface()
    }

    @Test
    fun `the captures panel beside the live view`() = recorder.record(
        DocsShot(page = PAGE, name = "captures", surfaceSize = DpSize(860.dp, 560.dp), density = 1.6f, displayWidth = 688),
    ) { darkTheme ->
        setMirrorScreen(darkTheme, showCaptures = true)
        onSurface()
    }
}

@OptIn(ExperimentalTestApi::class)
private fun SkikoComposeUiTest.setMirrorScreen(darkTheme: Boolean, showCaptures: Boolean) {
    val surface = MirrorSurface().apply {
        switchTo(PIXEL.id)
        val (width, height) = SCREEN_SIZES.getValue(PIXEL.id)
        startStream().writeFrame(width = width, height = height, colorType = ColorType.N32) { target ->
            SampleAppScreenPainter(Canvas(target), width = width, height = height).drawScreen()
            true
        }
    }
    setContent {
        PluginSceneSurface(darkTheme = darkTheme, storage = InMemoryPluginStorage(emptyMap())) {
            MirrorScreen(
                devices = DEVICES,
                capabilities = DeviceCapabilities(input = true, buttons = DeviceButton.entries, recording = true, screenPower = true),
                missingTools = emptyList(),
                selectedId = PIXEL.id,
                state = MirrorState.Streaming,
                notices = PreviewNotices(notice = null),
                screenPower = ScreenPower(awake = true, locked = false),
                recordingSinceMillis = null,
                surface = surface,
                actions = NoMirrorActions,
                showCaptures = showCaptures,
                livenessOf = { id -> if (id == PIXEL_DEVICE.id) DeviceLiveness.ScreenOff else DeviceLiveness.Live },
                onToggleCaptures = {},
                onShowGrid = {},
                capturesPanel = {
                    CapturesPanel(
                        captures = CAPTURES,
                        allDevices = false,
                        kind = null,
                        day = null,
                        selected = CAPTURES.first(),
                        thumbnails = SampleAppThumbnails,
                        actions = NoCapturesActions,
                    )
                },
            )
        }
    }
}

private object NoMirrorActions : MirrorActions {
    override fun select(deviceId: String) = Unit

    override fun tap(x: Int, y: Int) = Unit

    override fun swipe(fromX: Int, fromY: Int, toX: Int, toY: Int) = Unit

    override fun pressButton(button: DeviceButton) = Unit

    override fun inputText(text: String) = Unit

    override fun saveScreenshot() = Unit

    override fun recordSelectedDevice() = Unit

    override fun stopSelectedRecording() = Unit

    override fun wake() = Unit

    override fun sleep() = Unit
}

private object NoGridRecordingActions : GridRecordingActions {
    override fun recordAll() = Unit

    override fun stopAll() = Unit

    override fun suppressWarning() = Unit
}

private object NoCapturesActions : CapturesActions {
    override fun showAllDevices(all: Boolean) = Unit

    override fun filterKind(kind: CaptureKind?) = Unit

    override fun filterDay(day: String?) = Unit

    override fun select(capture: Capture?) = Unit

    override fun open(capture: Capture) = Unit

    override fun reveal(capture: Capture) = Unit

    override fun copy(capture: Capture) = Unit

    override fun copyPath(capture: Capture) = Unit

    override fun delete(capture: Capture) = Unit

    override fun openDeviceFolder() = Unit

    override fun chooseFolder() = Unit
}

/** Every capture shows the made-up app, at the size of the device that took it. */
private object SampleAppThumbnails : ThumbnailSource {
    override fun cachedThumbnail(capture: Capture): ImageBitmap = sampleAppScreenImage(SCREEN_SIZES.getValue(capture.info.deviceId))

    override suspend fun loadThumbnail(capture: Capture): ImageBitmap = cachedThumbnail(capture)
}

private const val PAGE = "device-mirror"

private val PIXEL = DeviceListing(id = "emulator-5554", name = "Pixel 9", kind = DeviceKind.AndroidEmulator, osVersion = null)

private val IPHONE = DeviceListing(id = "4F1C2B9A-7D3E-4A55-9C10-2B6E8F0A1D34", name = "iPhone 16", kind = DeviceKind.IosSimulator, osVersion = "iOS 18.5")

private val PIXEL_DEVICE = DeviceListing(id = "28241FDH20031K", name = "Pixel 7", kind = DeviceKind.AndroidDevice, osVersion = null)

private val DEVICES = listOf(PIXEL, PIXEL_DEVICE, IPHONE)

/** Each device's frame size, at a third of its real resolution: enough for the pane it is shown in. */
private val SCREEN_SIZES = mapOf(
    PIXEL.id to (360 to 800),
    IPHONE.id to (393 to 852),
    PIXEL_DEVICE.id to (360 to 780),
)

/** Absolute, as the panel shows a capture's absolute path: a relative one would resolve against this checkout. */
private const val CAPTURES_DIRECTORY = "/Users/sample/.jetwhale/plugin-data/com.kitakkun.jetwhale.mirror/captures/Pixel-9-3fa2c1d0"

/** 2026-10-01 09:30 UTC. */
private const val FIRST_CAPTURE_AT = 1_790_847_000_000

private const val MINUTE_MILLIS = 60_000L

/** Newest first, as the panel lists them. */
private val CAPTURES = listOf(
    capture(kind = CaptureKind.Screenshot, capturedAt = FIRST_CAPTURE_AT + 42 * MINUTE_MILLIS, durationMillis = null),
    capture(kind = CaptureKind.Recording, capturedAt = FIRST_CAPTURE_AT + 17 * MINUTE_MILLIS, durationMillis = 18_400),
    capture(kind = CaptureKind.Screenshot, capturedAt = FIRST_CAPTURE_AT + 5 * MINUTE_MILLIS, durationMillis = null),
    capture(kind = CaptureKind.Screenshot, capturedAt = FIRST_CAPTURE_AT, durationMillis = null),
)

private fun capture(kind: CaptureKind, capturedAt: Long, durationMillis: Long?): Capture {
    val time = Instant.ofEpochMilli(capturedAt).atZone(ZoneOffset.UTC)
    return Capture(
        file = File("$CAPTURES_DIRECTORY/${time.toLocalDate()}/%02d%02d%02d-${kind.suffix}.${kind.extension}".format(time.hour, time.minute, time.second)),
        info = CaptureInfo(
            deviceId = PIXEL.id,
            deviceName = PIXEL.name,
            platform = "Android",
            deviceKind = PIXEL.kind.label,
            osVersion = null,
            kind = kind,
            widthPx = 1080,
            heightPx = 2400,
            capturedAtEpochMillis = capturedAt,
            durationMillis = durationMillis,
        ),
    )
}

private fun sampleAppScreenImage(size: Pair<Int, Int>): ImageBitmap {
    val (width, height) = size
    val bitmap = Bitmap()
    bitmap.allocN32Pixels(width, height, opaque = true)
    SampleAppScreenPainter(Canvas(bitmap), width = width, height = height).drawScreen()
    return bitmap.asComposeImageBitmap()
}

/**
 * A made-up shopping app, "Sample App", drawn onto [canvas] at [width] by [height] pixels: a status
 * bar, a title, a search field, four products and a button. It stands in for a real app on the
 * mirrored devices.
 */
private class SampleAppScreenPainter(private val canvas: Canvas, private val width: Int, private val height: Int) {
    private val scale = width / BASE_WIDTH
    private val regular = checkNotNull(FontMgr.default.legacyMakeTypeface("", FontStyle.NORMAL))
    private val bold = checkNotNull(FontMgr.default.legacyMakeTypeface("", FontStyle.BOLD))

    fun drawScreen() {
        canvas.clear(SCREEN_BACKGROUND)
        drawHeader()
        PRODUCTS.forEachIndexed { index, product -> drawProduct(product, top = px(PRODUCTS_TOP + index * (PRODUCT_HEIGHT + PRODUCT_GAP))) }
        drawButton()
    }

    private fun drawHeader() {
        canvas.drawString(s = "9:41", x = px(16), y = px(17), font = Font(bold, px(12)), paint = paint(TEXT_PRIMARY))
        canvas.drawRRect(RRect.makeXYWH(l = width - px(40), t = px(7), w = px(22), h = px(11), radius = px(3)), paint(TEXT_PRIMARY))
        canvas.drawString(s = "Sample App", x = px(16), y = px(62), font = Font(bold, px(22)), paint = paint(TEXT_PRIMARY))
        canvas.drawRRect(RRect.makeXYWH(l = px(16), t = px(80), w = width - px(32), h = px(40), radius = px(20)), paint(SEARCH_FIELD))
        canvas.drawString(s = "Search products", x = px(36), y = px(105), font = Font(regular, px(14)), paint = paint(TEXT_SECONDARY))
    }

    private fun drawProduct(product: Product, top: Float) {
        canvas.drawRRect(RRect.makeXYWH(l = px(16), t = top, w = width - px(32), h = px(PRODUCT_HEIGHT), radius = px(12)), paint(CARD))
        canvas.drawRRect(RRect.makeXYWH(l = px(28), t = top + px(12), w = px(60), h = px(60), radius = px(8)), paint(product.thumbnailColor))
        canvas.drawString(s = product.name, x = px(104), y = top + px(36), font = Font(bold, px(16)), paint = paint(TEXT_PRIMARY))
        canvas.drawString(s = product.price, x = px(104), y = top + px(60), font = Font(regular, px(14)), paint = paint(TEXT_SECONDARY))
    }

    private fun drawButton() {
        val top = height - px(88)
        canvas.drawRRect(RRect.makeXYWH(l = px(16), t = top, w = width - px(32), h = px(48), radius = px(24)), paint(ACCENT))
        val label = "Add to cart"
        val font = Font(bold, px(16))
        canvas.drawString(s = label, x = (width - font.measureTextWidth(label)) / 2, y = top + px(30), font = font, paint = paint(ON_ACCENT))
        canvas.drawRect(Rect.makeXYWH(l = width / 2f - px(54), t = height - px(14), w = px(108), h = px(4)), paint(TEXT_SECONDARY))
    }

    /** [dp] of the 360-wide layout the app is designed at, in this screen's pixels. */
    private fun px(dp: Int): Float = dp * scale

    private fun paint(argb: Int) = Paint().apply {
        color = argb
        isAntiAlias = true
    }
}

private class Product(val name: String, val price: String, val thumbnailColor: Int)

private const val BASE_WIDTH = 360f

private const val PRODUCTS_TOP = 136

private const val PRODUCT_HEIGHT = 84

private const val PRODUCT_GAP = 12

private val PRODUCTS = listOf(
    Product(name = "Blue mug", price = "$12.50", thumbnailColor = 0xFF9DBDF6.toInt()),
    Product(name = "Notebook", price = "$4.00", thumbnailColor = 0xFFF6D49D.toInt()),
    Product(name = "Water bottle", price = "$18.00", thumbnailColor = 0xFF9DE0C8.toInt()),
    Product(name = "Desk lamp", price = "$32.00", thumbnailColor = 0xFFE6B3E0.toInt()),
)

private const val SCREEN_BACKGROUND = 0xFFF4F5F8.toInt()

private const val SEARCH_FIELD = 0xFFE6E8EE.toInt()

private const val CARD = 0xFFFFFFFF.toInt()

private const val TEXT_PRIMARY = 0xFF1D1F23.toInt()

private const val TEXT_SECONDARY = 0xFF6B7080.toInt()

private const val ACCENT = 0xFF2F6FE4.toInt()

private const val ON_ACCENT = 0xFFFFFFFF.toInt()
