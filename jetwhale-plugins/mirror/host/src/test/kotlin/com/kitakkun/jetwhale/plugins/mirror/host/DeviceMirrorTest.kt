package com.kitakkun.jetwhale.plugins.mirror.host

import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.time.ZoneOffset
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DeviceMirrorTest {
    private val root: File = Files.createTempDirectory("mirror-recordings").toFile()
    private val scope = CoroutineScope(Dispatchers.Default)
    private val recorder = SlowRecorder()
    private val device = MirrorDevice(DeviceListing("emulator-5554", "Pixel 9", DeviceKind.AndroidEmulator, osVersion = null), recorder)
    private val mirror = DeviceMirror(
        discovery = DeviceDiscovery(MirrorTools(adb = null, idb = null, idbCompanion = null, xcrun = null), companions = null),
        captures = MirrorCaptures(root, storage = null, scope = scope, zone = ZoneOffset.UTC),
        scope = scope,
    )

    @AfterTest
    fun cleanUp() {
        scope.cancel()
        root.deleteRecursively()
    }

    @Test
    fun `recordings started at once leave one recorder running and refuse the others`() = runBlocking {
        val outcomes = List(SIMULTANEOUS_CALLS) { async(Dispatchers.Default) { runCatching { mirror.startRecording(device) } } }.awaitAll()

        assertEquals(1, outcomes.count { it.isSuccess })
        assertEquals(1, recorder.started.get())
    }

    @Test
    fun `stops sent at once finish the one recording once and refuse the rest`() = runBlocking {
        mirror.startRecording(device)

        val outcomes = List(SIMULTANEOUS_CALLS) { async(Dispatchers.Default) { runCatching { mirror.stopRecording() } } }.awaitAll()

        assertEquals(1, outcomes.count { it.isSuccess })
        assertEquals(1, recorder.stopped.get())
    }

    @Test
    fun `a recording started while another stops waits for the stop and then runs`() = runBlocking {
        mirror.startRecording(device)

        val stop = async(Dispatchers.Default) { mirror.stopRecording() }
        recorder.stopping.await()
        mirror.startRecording(device)
        stop.await()
        mirror.stopRecording()

        assertEquals(2, recorder.started.get())
        assertEquals(2, recorder.stopped.get())
    }

    @Test
    fun `a recording running when the mirror is disposed is kept in the library`() = runBlocking {
        mirror.startRecording(device)

        mirror.dispose()

        assertEquals(listOf(CaptureKind.Recording), mirror.listCaptures(deviceId = null, kind = null, sinceEpochMillis = null).map { it.info.kind })
    }

    @Test
    fun `a stream that ends without a resize lets the mirror move on`() = runBlocking {
        val controller = EndingStream(power = null)
        val streaming = MirrorDevice(DeviceListing("emulator-5556", "Pixel 9", DeviceKind.AndroidEmulator, osVersion = null), controller)
        val session = scope.launch { mirror.mirror(streaming) }

        withTimeout(STREAM_END_TIMEOUT_MILLIS) { controller.reopened.await() }
        session.cancel()
    }

    @Test
    fun `a mirrored Android device whose screen is off says so until mirroring stops`() = runBlocking {
        val asleep = ScreenPower(awake = false, locked = true)
        val controller = EndingStream(power = asleep)
        val session = scope.launch { mirror.mirror(MirrorDevice(DeviceListing("emulator-5556", "Pixel 9", DeviceKind.AndroidEmulator, osVersion = null), controller)) }

        // By the second read the first one's answer has been shown.
        withTimeout(STREAM_END_TIMEOUT_MILLIS) { controller.readTwice.await() }
        val whileMirroring = mirror.screenPower
        session.cancel()
        session.join()

        assertEquals(asleep, whileMirroring)
        assertNull(mirror.screenPower)
    }

    @Test
    fun `a device without screen power is never asked for it`() = runBlocking {
        val controller = EndingStream(power = null)
        val session = scope.launch { mirror.mirror(MirrorDevice(DeviceListing("emulator-5558", "Pixel 9", DeviceKind.AndroidEmulator, osVersion = null), controller)) }

        withTimeout(STREAM_END_TIMEOUT_MILLIS) { controller.reopened.await() }
        session.cancel()

        assertEquals(0, controller.screenPowerReads.get())
        assertNull(mirror.screenPower)
    }
}

private const val SIMULTANEOUS_CALLS = 8

private const val STREAM_END_TIMEOUT_MILLIS = 5_000L

/** Long enough that every concurrent call reaches the recorder while the first is still inside it. */
private const val RECORDER_LATENCY_MILLIS = 100L

private class SlowRecorder : DeviceController {
    val started = AtomicInteger()
    val stopped = AtomicInteger()

    /** Completes when a recording has begun stopping. */
    val stopping = CompletableDeferred<Unit>()

    override val capabilities = DeviceCapabilities(input = true, buttons = emptyList(), recording = true, screenPower = false)

    override suspend fun startRecording(outputFile: File): DeviceRecording {
        delay(RECORDER_LATENCY_MILLIS)
        started.incrementAndGet()
        return object : DeviceRecording {
            override suspend fun stop(): File {
                stopping.complete(Unit)
                delay(RECORDER_LATENCY_MILLIS)
                stopped.incrementAndGet()
                return outputFile
            }
        }
    }

    override suspend fun screenSize(): IntSize = IntSize(1080, 2400)

    override suspend fun captureScreenshot(): ByteArray = throw deviceControlError("no screenshots in tests")

    override suspend fun tap(x: Int, y: Int) = Unit

    override suspend fun swipe(fromX: Int, fromY: Int, toX: Int, toY: Int, durationMillis: Int) = Unit

    override suspend fun pressButton(button: DeviceButton) = Unit

    override suspend fun inputText(text: String) = Unit

    override suspend fun screenPower(): ScreenPower = throw deviceControlError(NO_SCREEN_POWER)

    override suspend fun wake() = Unit

    override suspend fun sleep() = Unit

    override suspend fun openVideoStream(): Process = throw deviceControlError("no stream in tests")

    override suspend fun release() = Unit
}

/**
 * A device whose video stream ends at once, as screenrecord's does at its time limit. With a
 * [power], its screen state can be read, and stays that.
 */
private class EndingStream(private val power: ScreenPower?) : DeviceController {
    private val opened = AtomicInteger()

    val screenPowerReads = AtomicInteger()

    /** Completes once the mirror, done with the first stream, opens the next. */
    val reopened = CompletableDeferred<Unit>()

    override val capabilities = DeviceCapabilities(input = true, buttons = emptyList(), recording = false, screenPower = power != null)

    /** Completes when the screen state is read a second time. */
    val readTwice = CompletableDeferred<Unit>()

    override suspend fun screenPower(): ScreenPower {
        if (screenPowerReads.incrementAndGet() == 2) readTwice.complete(Unit)
        return power ?: throw deviceControlError(NO_SCREEN_POWER)
    }

    override suspend fun wake() = Unit

    override suspend fun sleep() = Unit

    override suspend fun startRecording(outputFile: File): DeviceRecording = throw deviceControlError("no recording in tests")

    override suspend fun screenSize(): IntSize = IntSize(1080, 2400)

    override suspend fun captureScreenshot(): ByteArray = throw deviceControlError("no screenshots in tests")

    override suspend fun tap(x: Int, y: Int) = Unit

    override suspend fun swipe(fromX: Int, fromY: Int, toX: Int, toY: Int, durationMillis: Int) = Unit

    override suspend fun pressButton(button: DeviceButton) = Unit

    override suspend fun inputText(text: String) = Unit

    override suspend fun openVideoStream(): Process {
        if (opened.incrementAndGet() == 2) reopened.complete(Unit)
        return EmptyProcess()
    }

    override suspend fun release() = Unit
}

private class EmptyProcess : Process() {
    override fun getOutputStream(): OutputStream = OutputStream.nullOutputStream()

    override fun getInputStream(): InputStream = ByteArrayInputStream(ByteArray(0))

    override fun getErrorStream(): InputStream = ByteArrayInputStream(ByteArray(0))

    override fun waitFor(): Int = 0

    override fun exitValue(): Int = 0

    override fun destroy() = Unit
}
