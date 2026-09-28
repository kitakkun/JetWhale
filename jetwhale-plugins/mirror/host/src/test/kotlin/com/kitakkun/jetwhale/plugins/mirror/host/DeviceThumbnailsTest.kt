package com.kitakkun.jetwhale.plugins.mirror.host

import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Surface
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

private const val INTERVAL_MILLIS = 1_000L

@OptIn(ExperimentalCoroutinesApi::class)
class DeviceThumbnailsTest {
    private val scheduler = TestCoroutineScheduler()
    private val thumbnails = DeviceThumbnails(refreshIntervalMillis = INTERVAL_MILLIS, maxConcurrentCaptures = 2, decodeDispatcher = StandardTestDispatcher(scheduler), clock = FixedClock)

    @AfterTest
    fun closeThumbnails() {
        thumbnails.close()
    }

    @Test
    fun `a tile captures once per interval until its poll is cancelled`() = runTest(StandardTestDispatcher(scheduler)) {
        val controller = FakeScreen()
        val poll = launch { thumbnails.keepFresh(heightPx = 40) { device("a", controller) } }

        runCurrent()
        advanceTimeBy(INTERVAL_MILLIS * 2 + 1)
        val whilePolling = controller.captures.get()
        poll.cancel()
        advanceTimeBy(INTERVAL_MILLIS * 5)

        assertEquals(3, whilePolling)
        assertEquals(whilePolling, controller.captures.get())
    }

    @Test
    fun `polling stops once the device is gone`() = runTest(StandardTestDispatcher(scheduler)) {
        val controller = FakeScreen()
        var present = true
        val poll = launch { thumbnails.keepFresh(heightPx = 40) { if (present) device("a", controller) else null } }

        runCurrent()
        present = false
        advanceTimeBy(INTERVAL_MILLIS * 3)

        assertEquals(1, controller.captures.get())
        assertEquals(true, poll.isCompleted)
    }

    @Test
    fun `no more than the permitted captures run at once`() = runTest(StandardTestDispatcher(scheduler)) {
        val gate = CompletableDeferred<Unit>()
        val screens = List(4) { FakeScreen(gate) }
        val polls = screens.mapIndexed { index, screen -> launch { thumbnails.keepFresh(heightPx = 40) { device("d$index", screen) } } }

        runCurrent()
        val inFlight = screens.sumOf { it.inFlight.get() }
        gate.complete(Unit)
        polls.forEach { it.cancel() }

        assertEquals(2, inFlight)
    }

    @Test
    fun `a captured screen becomes a thumbnail of the requested height`() = runTest(StandardTestDispatcher(scheduler)) {
        val poll = launch { thumbnails.keepFresh(heightPx = 40) { device("a", FakeScreen()) } }

        runCurrent()
        poll.cancel()

        val thumbnail = thumbnails.thumbnailOf("a")
        assertEquals(ThumbnailState.Live, thumbnail.state)
        assertEquals(40, assertNotNull(thumbnail.image).height)
        assertEquals(CAPTURED_AT_MILLIS, thumbnail.updatedAtMillis)
    }

    @Test
    fun `an image is closed once two newer ones have replaced it`() = runTest(StandardTestDispatcher(scheduler)) {
        val poll = launch { thumbnails.keepFresh(heightPx = 40) { device("a", FakeScreen()) } }
        runCurrent()
        val first = assertNotNull(thumbnails.thumbnailOf("a").image)
        advanceTimeBy(INTERVAL_MILLIS + 1)
        assertEquals(false, first.asSkiaBitmap().isClosed, "closed while a tile may still draw it")

        advanceTimeBy(INTERVAL_MILLIS)
        poll.cancel()

        assertTrue(first.asSkiaBitmap().isClosed)
    }

    @Test
    fun `a sleeping screen is not captured and keeps its last image`() = runTest(StandardTestDispatcher(scheduler)) {
        val controller = FakeScreen()
        val poll = launch { thumbnails.keepFresh(heightPx = 40) { device("a", controller) } }
        runCurrent()
        controller.awake = false
        advanceTimeBy(INTERVAL_MILLIS + 1)
        poll.cancel()

        assertEquals(1, controller.captures.get())
        assertEquals(ThumbnailState.ScreenOff, thumbnails.thumbnailOf("a").state)
        assertNotNull(thumbnails.thumbnailOf("a").image)
    }

    @Test
    fun `a failed capture reports why and keeps the last image`() = runTest(StandardTestDispatcher(scheduler)) {
        val controller = FakeScreen()
        val poll = launch { thumbnails.keepFresh(heightPx = 40) { device("a", controller) } }
        runCurrent()
        controller.failure = "adb: device offline"
        advanceTimeBy(INTERVAL_MILLIS + 1)
        poll.cancel()

        assertEquals(ThumbnailState.Failed("adb: device offline"), thumbnails.thumbnailOf("a").state)
        assertNotNull(thumbnails.thumbnailOf("a").image)
        // The image still shown is the one from before the failure, and says so.
        assertEquals(CAPTURED_AT_MILLIS, thumbnails.thumbnailOf("a").updatedAtMillis)
    }

    @Test
    fun `a device that is no longer listed loses its thumbnail`() = runTest(StandardTestDispatcher(scheduler)) {
        val poll = launch { thumbnails.keepFresh(heightPx = 40) { device("a", FakeScreen()) } }
        runCurrent()
        poll.cancel()

        thumbnails.retainOnly(emptySet())

        assertNull(thumbnails.thumbnailOf("a").image)
        assertEquals(ThumbnailState.Loading, thumbnails.thumbnailOf("a").state)
    }

    @Test
    fun `a screenshot that is not an image reads as failed, not live`() = runTest(StandardTestDispatcher(scheduler)) {
        val controller = FakeScreen().apply { png = byteArrayOf(1, 2, 3) }
        val poll = launch { thumbnails.keepFresh(heightPx = 40) { device("a", controller) } }
        runCurrent()
        poll.cancel()

        assertEquals(ThumbnailState.Failed("the screenshot could not be read as an image"), thumbnails.thumbnailOf("a").state)
    }

    @Test
    fun `a capture that finishes after its device was removed is dropped`() = runTest(StandardTestDispatcher(scheduler)) {
        val gate = CompletableDeferred<Unit>()
        thumbnails.retainOnly(setOf("a"))
        val poll = launch { thumbnails.keepFresh(heightPx = 40) { device("a", FakeScreen(gate)) } }
        runCurrent()

        thumbnails.retainOnly(emptySet())
        gate.complete(Unit)
        runCurrent()
        poll.cancel()

        assertNull(thumbnails.thumbnailOf("a").image)
        assertEquals(ThumbnailState.Loading, thumbnails.thumbnailOf("a").state)
    }

    @Test
    fun `another screenshot waits for a free capture slot`() = runTest(StandardTestDispatcher(scheduler)) {
        val gate = CompletableDeferred<Unit>()
        val polls = List(2) { index -> launch { thumbnails.keepFresh(heightPx = 40) { device("d$index", FakeScreen(gate)) } } }
        runCurrent()
        var extraRan = false
        val extra = launch { thumbnails.withCapturePermit("another") { extraRan = true } }
        runCurrent()
        assertEquals(false, extraRan, "ran while both slots were busy")

        gate.complete(Unit)
        runCurrent()
        polls.forEach { it.cancel() }
        extra.cancel()

        assertEquals(true, extraRan)
    }

    @Test
    fun `another screenshot of a device waits for the tile capturing it`() = runTest(StandardTestDispatcher(scheduler)) {
        val gate = CompletableDeferred<Unit>()
        val poll = launch { thumbnails.keepFresh(heightPx = 40) { device("a", FakeScreen(gate)) } }
        runCurrent()
        var extraRan = false
        val extra = launch { thumbnails.withCapturePermit("a") { extraRan = true } }
        runCurrent()
        assertEquals(false, extraRan, "ran while the tile was capturing the same device")

        gate.complete(Unit)
        runCurrent()
        poll.cancel()
        extra.cancel()

        assertEquals(true, extraRan)
    }
}

private fun device(id: String, controller: DeviceController) = MirrorDevice(DeviceListing(id, id, DeviceKind.AndroidEmulator, osVersion = null), controller)

/** A 100x200 screen whose screenshots can be held back by [gate], counting what was asked of it. */
private class FakeScreen(private val gate: CompletableDeferred<Unit>? = null) : DeviceController {
    val captures = AtomicInteger()
    val inFlight = AtomicInteger()

    @Volatile
    var awake = true

    @Volatile
    var failure: String? = null

    @Volatile
    var png: ByteArray = SCREEN_PNG

    override val capabilities = DeviceCapabilities(input = false, buttons = emptyList(), recording = false, screenPower = true)

    override suspend fun captureScreenshot(): ByteArray {
        failure?.let { throw deviceControlError(it) }
        inFlight.incrementAndGet()
        try {
            gate?.await()
        } finally {
            inFlight.decrementAndGet()
        }
        captures.incrementAndGet()
        return png
    }

    override suspend fun screenPower(): ScreenPower = ScreenPower(awake = awake, locked = false)

    override suspend fun screenSize(): IntSize = IntSize(100, 200)

    override suspend fun tap(x: Int, y: Int) = Unit

    override suspend fun swipe(fromX: Int, fromY: Int, toX: Int, toY: Int, durationMillis: Int) = Unit

    override suspend fun pressButton(button: DeviceButton) = Unit

    override suspend fun inputText(text: String) = Unit

    override suspend fun wake() = Unit

    override suspend fun sleep() = Unit

    override suspend fun startRecording(outputFile: File): DeviceRecording = throw deviceControlError("no recording in tests")

    override suspend fun openVideoStream(wanted: IntSize?): VideoStream = throw deviceControlError("no stream in tests")

    override suspend fun release() = Unit
}

private val SCREEN_PNG: ByteArray = Surface.makeRasterN32Premul(100, 200).use { surface ->
    surface.makeImageSnapshot().use { checkNotNull(it.encodeToData(EncodedImageFormat.PNG)).bytes }
}

/** Every capture happens at the same moment, so a thumbnail's timestamp is known. */
private object FixedClock : Clock {
    override fun now(): Instant = Instant.fromEpochMilliseconds(CAPTURED_AT_MILLIS)
}

private const val CAPTURED_AT_MILLIS = 1_000_000L
