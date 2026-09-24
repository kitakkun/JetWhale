package com.kitakkun.jetwhale.plugins.mirror.host

import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import java.time.ZoneOffset
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

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
}

private const val SIMULTANEOUS_CALLS = 8

/** Long enough that every concurrent call reaches the recorder while the first is still inside it. */
private const val RECORDER_LATENCY_MILLIS = 100L

private class SlowRecorder : DeviceController {
    val started = AtomicInteger()
    val stopped = AtomicInteger()

    /** Completes when a recording has begun stopping. */
    val stopping = CompletableDeferred<Unit>()

    override val capabilities = DeviceCapabilities(input = true, buttons = emptyList(), recording = true)

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

    override suspend fun openVideoStream(): Process = throw deviceControlError("no stream in tests")

    override suspend fun release() = Unit
}
