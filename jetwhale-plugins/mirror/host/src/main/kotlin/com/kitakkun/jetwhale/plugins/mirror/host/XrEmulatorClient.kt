package com.kitakkun.jetwhale.plugins.mirror.host

import androidx.annotation.VisibleForTesting
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okio.BufferedSink
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Clients for the running Android XR emulators' gRPC endpoints; one HTTP/2 client serves them all. */
internal class XrEmulatorClients(private val grpcEndpoints: EmulatorGrpcEndpoints) {
    private val httpClient = OkHttpClient.Builder().protocols(listOf(Protocol.H2_PRIOR_KNOWLEDGE)).build()

    /**
     * Whether the XR emulator with [serial] left a gRPC token in its discovery file, which every call
     * its client makes needs; `-grpc-use-token` makes the emulator write one.
     */
    suspend fun hasToken(serial: String): Boolean = withContext(Dispatchers.IO) { grpcEndpoints.findEndpoint(serial)?.token != null }

    fun clientFor(serial: String): XrEmulatorClient = XrEmulatorClient(serial, grpcEndpoints, httpClient)
}

/**
 * The gRPC endpoint of the Android XR emulator with [serial], which does what adb cannot there: a
 * hand that points and pinches at a pixel of the emulator's framebuffer, which is the picture its
 * screen stream shows; the direction the head faces; and passthrough. Coordinates are in that
 * framebuffer's pixels. Each call looks the endpoint up in [grpcEndpoints] again, since an emulator
 * started again under the same serial listens on another port with another token.
 */
internal class XrEmulatorClient(
    private val serial: String,
    private val grpcEndpoints: EmulatorGrpcEndpoints,
    private val httpClient: OkHttpClient,
) {
    /** Held for a whole tap or swipe: the emulator has one hand, and two gestures sent at once would mix their points and pinches. */
    private val handGestureLock = Mutex()

    /** Held while a head turn is sent, so that one is sent at a time and the turns asked for meanwhile wait in [pendingHeadTurn]. */
    private val headTurnLock = Mutex()

    private val pendingHeadTurn = PendingHeadTurn()

    /** Points at ([x], [y]), pinches and lets go, as a click on the emulator's own window does. */
    suspend fun tap(x: Int, y: Int) = handGestureLock.withLock { sendHandEvents(xrHandDragEvents(fromX = x, fromY = y, toX = x, toY = y, durationMillis = TAP_PINCH_MILLIS)) }

    /** Pinches at the start, moves the pinch to the end over [durationMillis], and lets go there. */
    suspend fun swipe(fromX: Int, fromY: Int, toX: Int, toY: Int, durationMillis: Int) = handGestureLock.withLock { sendHandEvents(xrHandDragEvents(fromX = fromX, fromY = fromY, toX = toX, toY = toY, durationMillis = durationMillis)) }

    /**
     * Turns the head from where it faces now: [upRadians] up and [leftRadians] to the left. While one
     * turn is being sent, the turns asked for meanwhile are added up and sent as one after it, so a
     * fast drag never queues calls the emulator is slow to answer; returns once this turn is sent.
     */
    suspend fun turnHead(upRadians: Float, leftRadians: Float) {
        pendingHeadTurn.add(upRadians = upRadians, leftRadians = leftRadians)
        headTurnLock.withLock {
            val turn = pendingHeadTurn.take() ?: return@withLock
            sendInputEvents(listOf(TimedInputEvent(afterMillis = 0, message = encodeXrHeadRotation(upRadians = turn.upRadians, leftRadians = turn.leftRadians))))
        }
    }

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

    private suspend fun callGrpcMethod(method: String, body: RequestBody): ByteArray {
        val endpoint = withContext(Dispatchers.IO) { grpcEndpoints.findEndpoint(serial) }?.takeIf { it.token != null }
            ?: throw deviceControlError("$serial left no gRPC endpoint with a token in its discovery file; start it with -grpc-use-token")
        val request = grpcEndpoints.buildRequest(endpoint, method, body)
        return suspendCancellableCoroutine { continuation ->
            val call = httpClient.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(GrpcReplyCallback(method, grpcEndpoints, continuation))
        }
    }
}

/** A head turn asked for and not sent yet; turns are each relative to the one before, so they add up. */
private class PendingHeadTurn {
    private var upRadians = 0f
    private var leftRadians = 0f

    @Synchronized
    fun add(upRadians: Float, leftRadians: Float) {
        this.upRadians += upRadians
        this.leftRadians += leftRadians
    }

    /** The turn added since the last take, or null when there is none; what it returns is no longer pending. */
    @Synchronized
    fun take(): HeadTurn? {
        val turn = HeadTurn(upRadians = upRadians, leftRadians = leftRadians).takeIf { it.upRadians != 0f || it.leftRadians != 0f }
        upRadians = 0f
        leftRadians = 0f
        return turn
    }
}

private data class HeadTurn(val upRadians: Float, val leftRadians: Float)

/**
 * Resumes [continuation] with the message of a unary gRPC reply to [method], or with why there is
 * none. The call is enqueued rather than executed because a blocking `execute()` ignores coroutine
 * cancellation, while cancelling an enqueued call ends it at once, through its reply's body too.
 */
private class GrpcReplyCallback(
    private val method: String,
    private val grpcEndpoints: EmulatorGrpcEndpoints,
    private val continuation: CancellableContinuation<ByteArray>,
) : Callback {
    override fun onFailure(call: Call, e: IOException) {
        continuation.resumeWithException(deviceControlError("the XR emulator's gRPC endpoint did not answer $method: ${e.message}"))
    }

    override fun onResponse(call: Call, response: Response) {
        val message = try {
            response.use(::readReplyMessage)
        } catch (e: IOException) {
            continuation.resumeWithException(deviceControlError("the XR emulator's gRPC endpoint did not answer $method: ${e.message}"))
            return
        } catch (e: DeviceControlException) {
            continuation.resumeWithException(e)
            return
        }
        continuation.resume(message)
    }

    private fun readReplyMessage(response: Response): ByteArray {
        val reply = response.body.bytes()
        grpcEndpoints.refusalOfUnaryReply(response)?.let { throw deviceControlError("the XR emulator refused $method: $it") }
        return reply.copyOfRange(minOf(GRPC_MESSAGE_PREFIX_BYTES, reply.size), reply.size)
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
