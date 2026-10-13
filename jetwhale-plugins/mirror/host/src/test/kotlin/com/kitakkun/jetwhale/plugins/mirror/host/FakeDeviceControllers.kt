package com.kitakkun.jetwhale.plugins.mirror.host

import androidx.compose.ui.unit.IntSize
import com.kitakkun.jetwhale.tools.docsscreenshots.InMemoryPluginStorage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Surface
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.io.SequenceInputStream
import java.nio.file.Files
import java.time.ZoneOffset
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Long enough that every concurrent call reaches the recorder while the first is still inside it. */
private const val RECORDER_LATENCY_MILLIS = 100L

private val UNFOLDED_SCREEN = IntSize(2208, 1840)

internal val FOLDED_SCREEN = IntSize(1080, 2092)

/** Holds each caller of [arrive] until [parties] callers have arrived. */
internal class StartBarrier(private val parties: Int) {
    private val arrived = AtomicInteger()
    private val open = CompletableDeferred<Unit>()

    suspend fun arrive() {
        if (arrived.incrementAndGet() >= parties) open.complete(Unit)
        open.await()
    }
}

internal class SlowRecorder : DeviceController {
    val started = AtomicInteger()
    val stopped = AtomicInteger()

    /** Completes when a recording has begun stopping. */
    val stopping = CompletableDeferred<Unit>()

    /** Thrown by the next stop, as a pull that fails would. */
    var stopFailure: DeviceControlException? = null

    override val capabilities = DeviceCapabilities(input = true, buttons = emptyList(), recording = true, screenPower = false)

    /** Completes when a start has begun. */
    val entered = CompletableDeferred<Unit>()

    /** When set, a start waits at it until every party has arrived. */
    var startGate: StartBarrier? = null

    override suspend fun startRecording(outputFile: File): DeviceRecording {
        entered.complete(Unit)
        startGate?.arrive()
        delay(RECORDER_LATENCY_MILLIS)
        started.incrementAndGet()
        return object : DeviceRecording {
            override suspend fun stop(): File {
                stopping.complete(Unit)
                delay(RECORDER_LATENCY_MILLIS)
                stopFailure?.let { throw it }
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

    override suspend fun openVideoStream(wanted: IntSize?): VideoStream = throw deviceControlError("no stream in tests")

    override suspend fun release() = Unit
}

/**
 * A device whose video stream ends at once, as screenrecord's does at its time limit. With a
 * [power], its screen state can be read, and stays that.
 */
internal class EndingStream(private val power: ScreenPower?) : DeviceController {
    private val opened = AtomicInteger()

    val screenPowerReads = AtomicInteger()

    /** Completes once the mirror, done with the first stream, opens the next. */
    val reopened = CompletableDeferred<Unit>()

    /** The size the first stream was asked for. */
    val firstWanted = CompletableDeferred<IntSize?>()

    /** Completes when the mirror first reads the screen's size. */
    val screenSizeRead = CompletableDeferred<Unit>()

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

    override suspend fun screenSize(): IntSize {
        screenSizeRead.complete(Unit)
        return IntSize(1080, 2400)
    }

    override suspend fun captureScreenshot(): ByteArray = throw deviceControlError("no screenshots in tests")

    override suspend fun tap(x: Int, y: Int) = Unit

    override suspend fun swipe(fromX: Int, fromY: Int, toX: Int, toY: Int, durationMillis: Int) = Unit

    override suspend fun pressButton(button: DeviceButton) = Unit

    override suspend fun inputText(text: String) = Unit

    override suspend fun openVideoStream(wanted: IntSize?): VideoStream {
        firstWanted.complete(wanted)
        if (opened.incrementAndGet() == 2) reopened.complete(Unit)
        return VideoStream.RawBgra(EmptyProcess(), frameSize = IntSize(1, 1), rowBytes = 64, fps = 30, onFellBehind = { false })
    }

    override suspend fun release() = Unit
}

/**
 * A simulator whose raw stream blocks in its read until the mirror closes it, and then throws
 * "Stream closed", as a process pipe closed under a blocked read does.
 */
internal class ClosingStream : DeviceController {
    /** Completes once the mirror is blocked reading the stream. */
    val reading = CompletableDeferred<Unit>()

    override val capabilities = DeviceCapabilities(input = true, buttons = emptyList(), recording = false, screenPower = false)

    override suspend fun openVideoStream(wanted: IntSize?): VideoStream = VideoStream.RawBgra(
        ClosedUnderReadProcess(
            reading,
        ),
        frameSize = IntSize(1, 1),
        rowBytes = 64,
        fps = 30,
        onFellBehind = {
            false
        },
    )

    override suspend fun screenSize(): IntSize = IntSize(1080, 2400)

    override suspend fun screenPower(): ScreenPower = throw deviceControlError(NO_SCREEN_POWER)

    override suspend fun wake() = Unit

    override suspend fun sleep() = Unit

    override suspend fun startRecording(outputFile: File): DeviceRecording = throw deviceControlError("no recording in tests")

    override suspend fun captureScreenshot(): ByteArray = throw deviceControlError("no screenshots in tests")

    override suspend fun tap(x: Int, y: Int) = Unit

    override suspend fun swipe(fromX: Int, fromY: Int, toX: Int, toY: Int, durationMillis: Int) = Unit

    override suspend fun pressButton(button: DeviceButton) = Unit

    override suspend fun inputText(text: String) = Unit

    override suspend fun release() = Unit
}

/** An emulator whose gRPC stream sends one frame and then nothing, as a still screen does. */
internal class StillEmulator : DeviceController {
    /** Completes if the mirror asks for a screenshot. */
    val screenshotTaken = CompletableDeferred<Unit>()

    override val capabilities = DeviceCapabilities(input = true, buttons = emptyList(), recording = false, screenPower = false)

    override suspend fun screenPower(): ScreenPower = throw deviceControlError(NO_SCREEN_POWER)

    override suspend fun wake() = Unit

    override suspend fun sleep() = Unit

    override suspend fun startRecording(outputFile: File): DeviceRecording = throw deviceControlError("no recording in tests")

    override suspend fun screenSize(): IntSize = IntSize(1080, 2400)

    override suspend fun captureScreenshot(): ByteArray {
        screenshotTaken.complete(Unit)
        throw deviceControlError("no screenshots in tests")
    }

    override suspend fun tap(x: Int, y: Int) = Unit

    override suspend fun swipe(fromX: Int, fromY: Int, toX: Int, toY: Int, durationMillis: Int) = Unit

    override suspend fun pressButton(button: DeviceButton) = Unit

    override suspend fun inputText(text: String) = Unit

    override suspend fun openVideoStream(wanted: IntSize?): VideoStream {
        val writer = PipedOutputStream()
        val frames = PipedInputStream(writer, ONE_FRAME_MESSAGE.size)
        writer.write(ONE_FRAME_MESSAGE)
        writer.flush()
        return VideoStream.EmulatorRgba(frames = frames, cancel = writer::close)
    }

    override suspend fun release() = Unit
}

/** An emulator that folds once its first stream is open, whose streams send nothing until closed. */
internal class FoldingEmulator(private val stateNow: () -> MirrorState) : DeviceController {
    private val opened = AtomicInteger()

    /** Completes with the mirror's state once it opens a second stream. */
    val reopened = CompletableDeferred<MirrorState>()

    override val capabilities = DeviceCapabilities(input = true, buttons = emptyList(), recording = false, screenPower = false)

    override suspend fun screenSize(): IntSize = if (opened.get() == 0) UNFOLDED_SCREEN else FOLDED_SCREEN

    override suspend fun openVideoStream(wanted: IntSize?): VideoStream {
        if (opened.incrementAndGet() == 2) reopened.complete(stateNow())
        val writer = PipedOutputStream()
        return VideoStream.EmulatorRgba(frames = PipedInputStream(writer), cancel = writer::close)
    }

    override suspend fun screenPower(): ScreenPower = throw deviceControlError(NO_SCREEN_POWER)

    override suspend fun wake() = Unit

    override suspend fun sleep() = Unit

    override suspend fun startRecording(outputFile: File): DeviceRecording = throw deviceControlError("no recording in tests")

    override suspend fun captureScreenshot(): ByteArray = throw deviceControlError("no screenshots in tests")

    override suspend fun tap(x: Int, y: Int) = Unit

    override suspend fun swipe(fromX: Int, fromY: Int, toX: Int, toY: Int, durationMillis: Int) = Unit

    override suspend fun pressButton(button: DeviceButton) = Unit

    override suspend fun inputText(text: String) = Unit

    override suspend fun release() = Unit
}

/**
 * A device whose screenrecord stream sends [sent] and then stays open until closed, decoded by the
 * ffmpeg at [ffmpegPath]. Its screenshots are [screenshot], or never finish when it is null, so the
 * mirror's state stays as it set it.
 */
internal class ScreenrecordDevice(
    private val ffmpegPath: String,
    private val sent: ByteArray,
    private val screenshot: ByteArray?,
) : DeviceController {
    private val screenshots = AtomicInteger()
    private val opened = AtomicInteger()

    /** Completes once the mirror opens a second stream. */
    val reopened = CompletableDeferred<Unit>()

    /**
     * Completes when the mirror asks for a second screenshot. The first fills in a still screen;
     * another comes only from falling back to screenshots.
     */
    val polled = CompletableDeferred<Unit>()

    /** Completes when the mirror asks for a third screenshot, once the second one's outcome is shown. */
    val polledAgain = CompletableDeferred<Unit>()

    override val capabilities = DeviceCapabilities(input = true, buttons = emptyList(), recording = false, screenPower = false)

    override suspend fun openVideoStream(wanted: IntSize?): VideoStream {
        if (opened.incrementAndGet() == 2) reopened.complete(Unit)
        return VideoStream.H264(HeldOpenProcess(sent), ffmpegPath)
    }

    override suspend fun screenSize(): IntSize = FOLDED_SCREEN

    override suspend fun captureScreenshot(): ByteArray {
        when (screenshots.incrementAndGet()) {
            2 -> polled.complete(Unit)
            3 -> polledAgain.complete(Unit)
        }
        return screenshot ?: awaitCancellation()
    }

    override suspend fun screenPower(): ScreenPower = throw deviceControlError(NO_SCREEN_POWER)

    override suspend fun wake() = Unit

    override suspend fun sleep() = Unit

    override suspend fun startRecording(outputFile: File): DeviceRecording = throw deviceControlError("no recording in tests")

    override suspend fun tap(x: Int, y: Int) = Unit

    override suspend fun swipe(fromX: Int, fromY: Int, toX: Int, toY: Int, durationMillis: Int) = Unit

    override suspend fun pressButton(button: DeviceButton) = Unit

    override suspend fun inputText(text: String) = Unit

    override suspend fun release() = Unit
}

/**
 * An emulator whose first stream fails to open and whose next one sends a frame. Its screenshots
 * never finish, so the mirror's state stays as it set it.
 */
internal class FlakyStreamDevice(private val stateNow: () -> MirrorState) : DeviceController {
    private val opened = AtomicInteger()

    /** Completes with the mirror's state when it opens a second stream. */
    val triedAgain = CompletableDeferred<MirrorState>()

    override val capabilities = DeviceCapabilities(input = true, buttons = emptyList(), recording = false, screenPower = false)

    override suspend fun screenSize(): IntSize = IntSize(1080, 2400)

    override suspend fun openVideoStream(wanted: IntSize?): VideoStream {
        if (opened.incrementAndGet() == 1) throw deviceControlError("adb is restarting")
        triedAgain.complete(stateNow())
        val writer = PipedOutputStream()
        val frames = PipedInputStream(writer, ONE_FRAME_MESSAGE.size)
        writer.write(ONE_FRAME_MESSAGE)
        writer.flush()
        return VideoStream.EmulatorRgba(frames = frames, cancel = writer::close)
    }

    override suspend fun captureScreenshot(): ByteArray = awaitCancellation()

    override suspend fun screenPower(): ScreenPower = throw deviceControlError(NO_SCREEN_POWER)

    override suspend fun wake() = Unit

    override suspend fun sleep() = Unit

    override suspend fun startRecording(outputFile: File): DeviceRecording = throw deviceControlError("no recording in tests")

    override suspend fun tap(x: Int, y: Int) = Unit

    override suspend fun swipe(fromX: Int, fromY: Int, toX: Int, toY: Int, durationMillis: Int) = Unit

    override suspend fun pressButton(button: DeviceButton) = Unit

    override suspend fun inputText(text: String) = Unit

    override suspend fun release() = Unit
}

/** A process that writes [output] and then keeps its stdout open until it is destroyed. */
private class HeldOpenProcess(output: ByteArray) : Process() {
    private val destroyed = CompletableDeferred<Unit>()

    private val stdout = SequenceInputStream(
        ByteArrayInputStream(output),
        object : InputStream() {
            override fun read(): Int {
                runBlocking { destroyed.await() }
                return -1
            }
        },
    )

    override fun getOutputStream(): OutputStream = OutputStream.nullOutputStream()

    override fun getInputStream(): InputStream = stdout

    override fun getErrorStream(): InputStream = ByteArrayInputStream(ByteArray(0))

    override fun waitFor(): Int = 0

    override fun exitValue(): Int = 0

    override fun destroy() {
        destroyed.complete(Unit)
    }
}

/** A gRPC `Image` message of one 1x1 RGBA frame. */
private val ONE_FRAME_MESSAGE = grpcMessage(byteArrayOf(0x0a, 0x04, 0x18, 0x01, 0x20, 0x01, 0x22, 0x04, 1, 2, 3, 4))

/** A process whose output blocks until it is destroyed, and then fails the blocked read. */
private class ClosedUnderReadProcess(private val reading: CompletableDeferred<Unit>) : Process() {
    private val destroyed = CompletableDeferred<Unit>()

    override fun getOutputStream(): OutputStream = OutputStream.nullOutputStream()

    override fun getInputStream(): InputStream = object : InputStream() {
        override fun read(): Int {
            reading.complete(Unit)
            runBlocking { destroyed.await() }
            throw IOException("Stream closed")
        }
    }

    override fun getErrorStream(): InputStream = ByteArrayInputStream(ByteArray(0))

    override fun waitFor(): Int = 0

    override fun exitValue(): Int = 0

    override fun destroy() {
        destroyed.complete(Unit)
    }
}

private class EmptyProcess : Process() {
    override fun getOutputStream(): OutputStream = OutputStream.nullOutputStream()

    override fun getInputStream(): InputStream = ByteArrayInputStream(ByteArray(0))

    override fun getErrorStream(): InputStream = ByteArrayInputStream(ByteArray(0))

    override fun waitFor(): Int = 0

    override fun exitValue(): Int = 0

    override fun destroy() = Unit
}
