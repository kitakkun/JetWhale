package com.kitakkun.jetwhale.plugins.mirror.host

import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.CompletableDeferred
import java.io.ByteArrayInputStream
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

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

    override val capabilities = DeviceCapabilities(inputRefusal = null, buttons = emptyList(), recording = false, screenPower = power != null)

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
        return VideoStream.EmulatorRgba(frames = ByteArrayInputStream(ByteArray(0)), cancel = {})
    }
}
