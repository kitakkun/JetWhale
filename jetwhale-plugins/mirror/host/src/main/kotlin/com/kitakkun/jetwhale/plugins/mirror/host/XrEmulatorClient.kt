package com.kitakkun.jetwhale.plugins.mirror.host

import androidx.annotation.VisibleForTesting
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSink
import java.io.File
import java.io.IOException

/** Finds each running Android XR emulator's gRPC endpoint; one HTTP/2 client serves them all. */
internal class XrEmulatorClients(private val runningDirectories: List<File>) {
    private val httpClient = OkHttpClient.Builder().protocols(listOf(Protocol.H2_PRIOR_KNOWLEDGE)).build()

    /**
     * The client for the XR emulator with [serial], or null when its discovery file names no gRPC
     * endpoint with a token, which `-grpc-use-token` makes it write.
     */
    suspend fun clientFor(serial: String): XrEmulatorClient? = withContext(Dispatchers.IO) {
        val endpoint = findEmulatorEndpoint(serial, runningDirectories) ?: return@withContext null
        endpoint.token?.let { XrEmulatorClient(port = endpoint.port, token = it, httpClient = httpClient) }
    }
}

/**
 * One Android XR emulator's gRPC endpoint, which does what adb cannot there: a hand that points and
 * pinches at a pixel of the emulator's framebuffer, which is the picture its screen stream shows; the
 * direction the head faces; and passthrough. Coordinates are in that framebuffer's pixels.
 */
internal class XrEmulatorClient(
    private val port: Int,
    private val token: String,
    private val httpClient: OkHttpClient,
) {
    /** Points at ([x], [y]), pinches and lets go, as a click on the emulator's own window does. */
    suspend fun tap(x: Int, y: Int) = sendHandEvents(xrHandDragEvents(fromX = x, fromY = y, toX = x, toY = y, durationMillis = TAP_PINCH_MILLIS))

    /** Pinches at the start, moves the pinch to the end over [durationMillis], and lets go there. */
    suspend fun swipe(fromX: Int, fromY: Int, toX: Int, toY: Int, durationMillis: Int) = sendHandEvents(xrHandDragEvents(fromX = fromX, fromY = fromY, toX = toX, toY = toY, durationMillis = durationMillis))

    /** Turns the head from where it faces now: [upRadians] up and [leftRadians] to the left. */
    suspend fun turnHead(upRadians: Float, leftRadians: Float) = sendInputEvents(listOf(TimedInputEvent(afterMillis = 0, message = encodeXrHeadRotation(upRadians = upRadians, leftRadians = leftRadians))))

    /** Puts the head back where it started, facing forward. */
    suspend fun recenterHead() = sendInputEvents(listOf(TimedInputEvent(afterMillis = 0, message = encodeXrRecenter())))

    suspend fun readXrOptions(): XrOptions = parseXrOptions(callGrpcMethod("getXrOptions", grpcMessage(ByteArray(0)).toRequestBody(GRPC_MEDIA_TYPE)))

    suspend fun writeXrOptions(options: XrOptions) {
        callGrpcMethod("setXrOptions", grpcMessage(encodeXrOptions(options)).toRequestBody(GRPC_MEDIA_TYPE))
    }

    /** The framebuffer, whole, as a PNG; the emulator encodes it in a fraction of the time `screencap` takes. */
    suspend fun captureScreenshotPng(): ByteArray = parseImageBytes(callGrpcMethod("getScreenshot", grpcMessage(encodePngImageFormat()).toRequestBody(GRPC_MEDIA_TYPE)))

    private suspend fun sendHandEvents(events: List<XrHandEvent>) = sendInputEvents(events.map { TimedInputEvent(it.afterMillis, encodeXrHandEvent(it.x, it.y, it.pinching)) })

    /**
     * Sends [events] on one `streamInputEvent` call, each once its delay after the one before has
     * passed: the emulator handles each as it arrives, so the pauses reach the device as they are.
     */
    private suspend fun sendInputEvents(events: List<TimedInputEvent>) {
        callGrpcMethod("streamInputEvent", TimedInputEventsBody(events))
    }

    private suspend fun callGrpcMethod(method: String, body: RequestBody): ByteArray = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("http://127.0.0.1:$port/android.emulation.control.EmulatorController/$method")
            .header("te", "trailers")
            .header("authorization", "Bearer $token")
            .post(body)
            .build()
        try {
            httpClient.newCall(request).execute().use { response ->
                val reply = response.body.bytes()
                // A call that fails at once answers with headers only, its status among them; any
                // other sends the status in trailers after the reply.
                val status = response.header("grpc-status") ?: response.trailers()["grpc-status"]
                if (response.code != 200 || status != "0") {
                    val reason = response.header("grpc-message") ?: response.trailers()["grpc-message"] ?: "status ${status ?: response.code}"
                    throw deviceControlError("the XR emulator refused $method: $reason")
                }
                reply.copyOfRange(minOf(GRPC_MESSAGE_PREFIX_BYTES, reply.size), reply.size)
            }
        } catch (e: IOException) {
            throw deviceControlError("the XR emulator's gRPC endpoint did not answer $method: ${e.message}")
        }
    }
}

/** An `InputEvent` message, to send [afterMillis] after the one before it. */
private class TimedInputEvent(val afterMillis: Long, val message: ByteArray)

/** Writes each event as its time comes; a gesture sent twice would act twice, so it is never retried. */
private class TimedInputEventsBody(private val events: List<TimedInputEvent>) : RequestBody() {
    override fun contentType(): MediaType = GRPC_MEDIA_TYPE

    override fun isOneShot(): Boolean = true

    override fun writeTo(sink: BufferedSink) {
        events.forEach { event ->
            if (event.afterMillis > 0) Thread.sleep(event.afterMillis)
            sink.write(grpcMessage(event.message))
            sink.flush()
        }
    }
}

/**
 * The hand events of a pinch at ([fromX], [fromY]) moved to ([toX], [toY]) over [durationMillis]: the
 * hand points at the start first, so the pinch lands on what it points at, then pinches, moves one
 * display frame at a time, and lets go at the end. A tap is a drag that does not move.
 */
@VisibleForTesting
internal fun xrHandDragEvents(fromX: Int, fromY: Int, toX: Int, toY: Int, durationMillis: Int): List<XrHandEvent> {
    val steps = maxOf(1, durationMillis / DISPLAY_FRAME_MILLIS)
    val moves = (1..steps).map { step ->
        XrHandEvent(
            x = fromX + (toX - fromX) * step / steps,
            y = fromY + (toY - fromY) * step / steps,
            pinching = true,
            afterMillis = durationMillis.toLong() / steps,
        )
    }
    return listOf(XrHandEvent(fromX, fromY, pinching = false, afterMillis = 0), XrHandEvent(fromX, fromY, pinching = true, afterMillis = POINT_BEFORE_PINCH_MILLIS)) +
        moves +
        XrHandEvent(toX, toY, pinching = false, afterMillis = 0)
}

/** One step of the hand: where it points, whether it pinches, and how long after the step before. */
@VisibleForTesting
internal data class XrHandEvent(val x: Int, val y: Int, val pinching: Boolean, val afterMillis: Long)

private const val GRPC_MESSAGE_PREFIX_BYTES = 5

/** How long a tap holds its pinch: long enough for the XR input service to see it, as a click does. */
private const val TAP_PINCH_MILLIS = 60

/** How long the hand points before it pinches, so the pinch lands on what it points at. */
private const val POINT_BEFORE_PINCH_MILLIS = 30L

private const val DISPLAY_FRAME_MILLIS = 16
