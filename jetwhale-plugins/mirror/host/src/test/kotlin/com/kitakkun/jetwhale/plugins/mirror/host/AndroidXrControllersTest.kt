package com.kitakkun.jetwhale.plugins.mirror.host

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
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

    private val xrEmulatorClient = XrEmulatorClient(serial = "emulator-5554", runningDirectories = listOf(runningDirectory), httpClient = OkHttpClient.Builder().protocols(listOf(Protocol.H2_PRIOR_KNOWLEDGE)).build())

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
                    secondGestureArrivedBeforeFirstAnswer.set(secondGestureArrived.await(SECOND_GESTURE_WAIT_MILLIS, TimeUnit.MILLISECONDS))
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

        emulator.turnViewWithDrag(dragX = 0.5f, dragY = 0f)

        val expected = encodeXrHeadRotation(upRadians = 0f, leftRadians = Math.toRadians(40.0).toFloat())
        assertContentEquals(expected, grpcMessagesOf(server.takeRequest()).single())
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
    fun `an XR emulator whose endpoint takes no token from here takes no input, and says how to start it`() {
        val emulatorWithoutToken = AndroidXrEmulatorDeviceController(androidDeviceControllerWithoutAdb(), xrEmulatorClient = null)

        val refusal = emulatorWithoutToken.capabilities.inputRefusal.orEmpty()
        assertTrue("-grpc-use-token" in refusal, refusal)
        assertEquals(emptyList(), emulatorWithoutToken.capabilities.buttons)
        assertEquals(refusal, assertFailsWith<DeviceControlException> { runBlocking { emulatorWithoutToken.tap(1, 1) } }.message)
    }

    @Test
    fun `an XR emulator offers All apps beside Home, and a headset takes no input at all`() {
        val headset = AndroidXrHeadsetDeviceController(androidDeviceControllerWithoutAdb())

        assertTrue(DeviceButton.AllApps in emulator.capabilities.buttons)
        assertNull(emulator.capabilities.inputRefusal)
        val refusal = headset.capabilities.inputRefusal.orEmpty()
        assertTrue("headset" in refusal, refusal)
        assertEquals(emptyList(), headset.capabilities.buttons)
        listOf<suspend () -> Unit>({ headset.tap(1, 1) }, { headset.swipe(fromX = 1, fromY = 1, toX = 2, toY = 2, durationMillis = 100) }, { headset.pressButton(DeviceButton.Back) }, { headset.inputText("a") }).forEach { input ->
            assertEquals(refusal, assertFailsWith<DeviceControlException> { runBlocking { input() } }.message)
        }
    }
}

/** A controller that is never asked to run adb: every adb call would fail on the missing executable. */
private fun androidDeviceControllerWithoutAdb() = AndroidDeviceController(adbPath = "/nonexistent/adb", serial = "emulator-5554", emulatorScreens = null, ffmpegPath = null)

private fun grpcReply(message: ByteArray): MockResponse = MockResponse()
    .setHeader("content-type", "application/grpc")
    .setHeader("grpc-status", "0")
    .setBody(Buffer().write(grpcMessage(message)))

/** The messages of a gRPC request body, without their five-byte prefixes. */
private fun grpcMessagesOf(request: RecordedRequest): List<ByteArray> {
    val body = request.body
    return buildList {
        while (!body.exhausted()) {
            body.readByte()
            add(body.readByteArray(body.readInt().toLong()))
        }
    }
}

/** Far longer than a tap takes to send, so that a second tap sent at once would arrive within it. */
private const val SECOND_GESTURE_WAIT_MILLIS = 1_000L
