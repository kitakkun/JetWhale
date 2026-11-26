package com.kitakkun.jetwhale.plugins.mirror.host

import androidx.compose.ui.geometry.Offset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import java.io.File
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AndroidXrControllersTest {
    private val server = MockWebServer().apply {
        protocols = listOf(Protocol.H2_PRIOR_KNOWLEDGE)
        start()
    }

    private val runningDirectory: File = Files.createTempDirectory("avd-running").toFile().apply {
        File(this, "pid_1.ini").writeText("port.serial=5554\ngrpc.port=${server.port}\ngrpc.token=secret\n")
    }

    private val httpClient = OkHttpClient.Builder().protocols(listOf(Protocol.H2_PRIOR_KNOWLEDGE)).build()

    private val xrEmulatorClient = XrEmulatorClient(serial = "emulator-5554", grpcEndpoints = EmulatorGrpcEndpoints(listOf(runningDirectory)), httpClient = httpClient)

    private val emulator = AndroidXrEmulatorDeviceController(androidDeviceControllerWithoutAdb(), xrEmulatorClient)

    @AfterTest
    fun cleanUp() {
        server.close()
        runningDirectory.deleteRecursively()
    }

    @Test
    fun `an emulator started again under the same serial is reached at its new port with its new token`() = runBlocking {
        server.enqueue(grpcReply(ByteArray(0)))
        emulator.recenterView()
        MockWebServer().use { restartedServer ->
            restartedServer.protocols = listOf(Protocol.H2_PRIOR_KNOWLEDGE)
            restartedServer.enqueue(grpcReply(ByteArray(0)))
            restartedServer.start()
            File(runningDirectory, "pid_1.ini").writeText("port.serial=5554\ngrpc.port=${restartedServer.port}\ngrpc.token=renewed\n")

            emulator.recenterView()

            assertEquals("Bearer renewed", restartedServer.takeRequest().getHeader("authorization"))
        }
    }

    @Test
    fun `a tap sent while another is under way waits for it, so the hand makes one gesture at a time`() = runBlocking {
        val secondGestureArrived = CountDownLatch(1)
        val secondGestureArrivedBeforeFirstAnswer = AtomicBoolean()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                if (request.sequenceNumber == 0) {
                    secondGestureArrivedBeforeFirstAnswer.set(secondGestureArrived.await(LATER_CALL_WAIT_MILLIS, TimeUnit.MILLISECONDS))
                } else {
                    secondGestureArrived.countDown()
                }
                return grpcReply(ByteArray(0))
            }
        }

        listOf(launch(Dispatchers.IO) { emulator.tap(10, 10) }, launch(Dispatchers.IO) { emulator.tap(20, 20) }).joinAll()

        assertEquals(2, server.requestCount)
        assertFalse(secondGestureArrivedBeforeFirstAnswer.get())
    }

    @Test
    fun `a tap points the hand, pinches and lets go on one input stream, with the token`() = runBlocking {
        server.enqueue(grpcReply(ByteArray(0)))

        emulator.tap(840, 1472)

        val request = server.takeRequest()
        assertEquals("/android.emulation.control.EmulatorController/streamInputEvent", request.path)
        assertEquals("Bearer secret", request.getHeader("authorization"))
        val events = grpcMessagesOf(request)
        assertContentEquals(encodeXrHandEvent(840, 1472, pinching = false), events.first())
        assertContentEquals(encodeXrHandEvent(840, 1472, pinching = true), events[1])
        assertContentEquals(encodeXrHandEvent(840, 1472, pinching = false), events.last())
    }

    @Test
    fun `a swipe pinches at the start, moves the pinch to the end in steps and lets go there`() {
        val events = xrHandDragEvents(fromX = 800, fromY = 1600, toX = 800, toY = 1200, durationMillis = 160)

        assertEquals(XrHandEvent(800, 1600, pinching = false, afterMillis = 0), events.first())
        assertEquals(XrHandEvent(800, 1600, pinching = true, afterMillis = 30), events[1])
        val moves = events.subList(2, events.lastIndex)
        assertEquals(10, moves.size)
        assertTrue(moves.all { it.pinching && it.x == 800 })
        assertEquals(moves.map(XrHandEvent::y).sortedDescending(), moves.map(XrHandEvent::y))
        assertEquals(160, moves.sumOf(XrHandEvent::afterMillis))
        assertEquals(XrHandEvent(800, 1200, pinching = true, afterMillis = 16), moves.last())
        assertEquals(XrHandEvent(800, 1200, pinching = false, afterMillis = 0), events.last())
    }

    @Test
    fun `a call the emulator refuses fails with its reason`() {
        server.enqueue(MockResponse().setHeader("content-type", "application/grpc").setHeader("grpc-status", "16").setHeader("grpc-message", "missing token"))

        val failure = assertFailsWith<DeviceControlException> { runBlocking { emulator.recenterView() } }

        assertEquals("the XR emulator refused streamInputEvent: missing token", failure.message)
    }

    @Test
    fun `turning passthrough on keeps the environment and dimming the emulator had`() = runBlocking {
        server.enqueue(grpcReply(encodeXrOptions(XrOptions(environment = 1, passthroughCoefficient = 0f, dimmingValue = 0.25f))))
        server.enqueue(grpcReply(ByteArray(0)))

        emulator.setPassthrough(true)

        assertEquals("/android.emulation.control.EmulatorController/getXrOptions", server.takeRequest().path)
        val written = server.takeRequest()
        assertEquals("/android.emulation.control.EmulatorController/setXrOptions", written.path)
        assertEquals(XrOptions(environment = 1, passthroughCoefficient = 1f, dimmingValue = 0.25f), parseXrOptions(grpcMessagesOf(written).single()))
    }

    @Test
    fun `dragging the scene right across half the screen turns the view left by half the field of view`() = runBlocking {
        server.enqueue(grpcReply(ByteArray(0)))

        emulator.turnViewWithDrag(dragFromInScreenWidths = Offset.Zero, dragToInScreenWidths = Offset(0.5f, 0f))

        val expected = encodeXrHeadRotation(upRadians = 0f, leftRadians = Math.toRadians(40.0).toFloat())
        assertContentEquals(expected, grpcMessagesOf(server.takeRequest()).single())
    }

    @Test
    fun `a drag turns the view as far whatever parts it is reported in, and back to nothing when it returns to its start`() = runBlocking {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = grpcReply(ByteArray(0))
        }
        val whole = listOf(0f, 0.5f)
        val inParts = listOf(0f, 0.1f, 0.2f, 0.3f, 0.4f, 0.5f)
        val outAndBack = listOf(0f, 0.5f, 0.4f, 0.3f, 0.2f, 0.1f, 0f)

        val turns = listOf(whole, inParts, outAndBack).map { dragXs ->
            dragXs.zipWithNext().forEach { (from, to) -> emulator.turnViewWithDrag(dragFromInScreenWidths = Offset(from, 0f), dragToInScreenWidths = Offset(to, 0f)) }
            List(dragXs.size - 1) { leftRadiansOf(grpcMessagesOf(server.takeRequest()).single()) }.sum()
        }

        assertEquals(turns[0], turns[1], absoluteTolerance = 1e-6f)
        assertEquals(0f, turns[2], absoluteTolerance = 1e-6f)
    }

    @Test
    fun `a call to an emulator that does not answer ends as soon as its caller is cancelled, and lets the next gesture through`() = runBlocking {
        val firstAnswerHoldingDispatcher = FirstAnswerHoldingDispatcher()
        server.dispatcher = firstAnswerHoldingDispatcher
        val callsEnded = CountDownLatch(1)
        httpClient.dispatcher.idleCallback = Runnable(callsEnded::countDown)
        try {
            val unansweredTap = launch(Dispatchers.IO) { emulator.tap(10, 10) }
            assertTrue(firstAnswerHoldingDispatcher.awaitFirstCall())

            withTimeout(CANCELLED_CALL_END_MILLIS) { unansweredTap.cancelAndJoin() }
            assertTrue(callsEnded.await(CANCELLED_CALL_END_MILLIS, TimeUnit.MILLISECONDS))
            withTimeout(CANCELLED_CALL_END_MILLIS) { emulator.tap(20, 20) }
        } finally {
            firstAnswerHoldingDispatcher.releaseFirstAnswer()
        }
    }

    @Test
    fun `turns asked for while one is being sent are added up and sent as one, so the view stops with the drag`() = runBlocking {
        val firstAnswerHoldingDispatcher = FirstAnswerHoldingDispatcher()
        server.dispatcher = firstAnswerHoldingDispatcher
        val firstTurn = launch(Dispatchers.IO) { xrEmulatorClient.turnHead(upRadians = 0f, leftRadians = 0.1f) }
        assertTrue(firstAnswerHoldingDispatcher.awaitFirstCall())
        val waitingTurns = List(WAITING_TURNS) { launch { xrEmulatorClient.turnHead(upRadians = 0f, leftRadians = 0.1f) } }
        yield()

        firstAnswerHoldingDispatcher.releaseFirstAnswer()
        (waitingTurns + firstTurn).joinAll()

        val sentLeftRadians = List(server.requestCount) { leftRadiansOf(grpcMessagesOf(server.takeRequest()).single()) }
        assertEquals(2, sentLeftRadians.size)
        assertEquals(0.1f * (WAITING_TURNS + 1), sentLeftRadians.sum(), absoluteTolerance = 1e-6f)
    }

    @Test
    fun `a turn cancelled while it waits for the one being sent is never sent, not even with the next turn`() = runBlocking {
        val firstAnswerHoldingDispatcher = FirstAnswerHoldingDispatcher()
        server.dispatcher = firstAnswerHoldingDispatcher
        val firstTurn = launch(Dispatchers.IO) { xrEmulatorClient.turnHead(upRadians = 0f, leftRadians = 0.1f) }
        assertTrue(firstAnswerHoldingDispatcher.awaitFirstCall())
        val cancelledTurn = launch { xrEmulatorClient.turnHead(upRadians = 0f, leftRadians = 0.2f) }
        yield()
        cancelledTurn.cancelAndJoin()
        firstAnswerHoldingDispatcher.releaseFirstAnswer()
        firstTurn.join()

        xrEmulatorClient.turnHead(upRadians = 0f, leftRadians = 0.3f)

        val sentLeftRadians = List(server.requestCount) { leftRadiansOf(grpcMessagesOf(server.takeRequest()).single()) }
        assertEquals(listOf(0.1f, 0.3f), sentLeftRadians)
    }

    @Test
    fun `a recenter asked for while a turn is being sent goes after it and after the turns waiting for it`() = runBlocking {
        val firstAnswerHoldingDispatcher = FirstAnswerHoldingDispatcher()
        server.dispatcher = firstAnswerHoldingDispatcher
        val firstTurn = launch(Dispatchers.IO) { xrEmulatorClient.turnHead(upRadians = 0f, leftRadians = 0.1f) }
        assertTrue(firstAnswerHoldingDispatcher.awaitFirstCall())
        val waitingTurn = launch { xrEmulatorClient.turnHead(upRadians = 0f, leftRadians = 0.2f) }
        yield()
        val recenter = launch(Dispatchers.IO) { xrEmulatorClient.recenterHead() }

        assertFalse(firstAnswerHoldingDispatcher.awaitLaterCall(LATER_CALL_WAIT_MILLIS))
        firstAnswerHoldingDispatcher.releaseFirstAnswer()
        listOf(firstTurn, waitingTurn, recenter).joinAll()

        val sentMessages = List(server.requestCount) { grpcMessagesOf(server.takeRequest()).single() }
        assertEquals(listOf(false, false, true), sentMessages.map { it.contentEquals(encodeXrRecenter()) })
    }

    @Test
    fun `a drag turns the view by the angle it spans at the middle of the view`() {
        assertEquals(0.0, xrEmulatorDegreesAcross(0f))
        assertTrue(abs(xrEmulatorDegreesAcross(0.5f) - 40.0) < 1e-9)
        assertEquals(-xrEmulatorDegreesAcross(0.1f), xrEmulatorDegreesAcross(-0.1f))
    }

    @Test
    fun `a screenshot is the emulator's framebuffer, in the pixels the hand points at`() = runBlocking {
        val png = byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47)
        server.enqueue(grpcReply(Buffer().writeByte(0x22).writeByte(png.size).write(png).readByteArray()))

        assertContentEquals(png, emulator.captureScreenshot())
        assertEquals("/android.emulation.control.EmulatorController/getScreenshot", server.takeRequest().path)
    }

    @Test
    fun `an XR emulator offers All apps beside Home, and a view-only XR device takes no input at all, for its reason`() {
        val viewOnlyController = ViewOnlyAndroidXrDeviceController(androidDeviceControllerWithoutAdb(), inputRefusal = "no way in")

        assertTrue(DeviceButton.AllApps in emulator.capabilities.buttons)
        assertNull(emulator.capabilities.inputRefusal)
        assertEquals("no way in", viewOnlyController.capabilities.inputRefusal)
        assertEquals(emptyList(), viewOnlyController.capabilities.buttons)
        listOf<suspend () -> Unit>({ viewOnlyController.tap(1, 1) }, { viewOnlyController.swipe(fromX = 1, fromY = 1, toX = 2, toY = 2, durationMillis = 100) }, { viewOnlyController.pressButton(DeviceButton.Back) }, { viewOnlyController.inputText("a") }).forEach { input ->
            assertEquals("no way in", assertFailsWith<DeviceControlException> { runBlocking { input() } }.message)
        }
    }
}

/**
 * Answers every call at once except the first, whose answer it holds back until [releaseFirstAnswer]:
 * the calls made meanwhile either wait for that answer or reach the emulator while it is held.
 */
private class FirstAnswerHoldingDispatcher : Dispatcher() {
    private val firstCallArrived = CountDownLatch(1)
    private val laterCallArrived = CountDownLatch(1)
    private val firstAnswerReleased = CountDownLatch(1)

    override fun dispatch(request: RecordedRequest): MockResponse {
        if (request.sequenceNumber == 0) {
            firstCallArrived.countDown()
            firstAnswerReleased.await(UNANSWERED_CALL_MILLIS, TimeUnit.MILLISECONDS)
        } else {
            laterCallArrived.countDown()
        }
        return grpcReply(ByteArray(0))
    }

    fun awaitFirstCall(): Boolean = firstCallArrived.await(UNANSWERED_CALL_MILLIS, TimeUnit.MILLISECONDS)

    /** Whether a call after the first reaches the emulator within [timeoutMillis]. */
    fun awaitLaterCall(timeoutMillis: Long): Boolean = laterCallArrived.await(timeoutMillis, TimeUnit.MILLISECONDS)

    fun releaseFirstAnswer() = firstAnswerReleased.countDown()
}

/** A controller that is never asked to run adb: every adb call would fail on the missing executable. */
private fun androidDeviceControllerWithoutAdb() = AndroidDeviceController(adbPath = "/nonexistent/adb", serial = "emulator-5554", emulatorScreens = null, ffmpegPath = null)

/**
 * The turn left in a head rotation message, about its y axis: the second float, after the message's
 * key and length, and the first float with its key.
 */
private fun leftRadiansOf(message: ByteArray): Float = Buffer().write(message).run {
    skip(2 + 1 + 4 + 1)
    Float.fromBits(readIntLe())
}

/** Longer than OkHttp waits for an answer by default, so only cancelling ends the call sooner. */
private const val UNANSWERED_CALL_MILLIS = 15_000L

private const val CANCELLED_CALL_END_MILLIS = 2_000L

private const val WAITING_TURNS = 5

/** Far longer than a call takes to reach the emulator, so that one sent at once would arrive within it. */
private const val LATER_CALL_WAIT_MILLIS = 1_000L
