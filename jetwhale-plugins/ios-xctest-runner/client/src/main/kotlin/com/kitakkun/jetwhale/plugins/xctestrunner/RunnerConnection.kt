package com.kitakkun.jetwhale.plugins.xctestrunner

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.Closeable
import java.io.IOException
import java.net.ConnectException
import java.util.concurrent.TimeUnit

/** The commands this client sends; `/status` reports the runner's, and an older runner is replaced. */
internal const val PROTOCOL_VERSION = 3

/**
 * How long a screen stream may send nothing before it counts as stalled. The runner sends a frame at
 * least once a second whether the screen changes or not.
 */
private const val SCREEN_STREAM_STALL_SECONDS = 10L

internal const val RUNNER_TOKEN_HEADER = "X-JetWhale-Runner-Token"

/** The runner as `/status` describes it. */
@Serializable
internal data class RunnerStatus(
    val protocolVersion: Int,
    val screenWidthPixels: Int,
    val screenHeightPixels: Int,
    val scale: Double,
    val eventSynthesis: Boolean,
)

/**
 * A runner that took no connection, so the command was never sent and can be sent again. A runner
 * that stops answering after taking the command throws [XcTestRunnerException] instead: it may have
 * carried the command out.
 */
internal class RunnerUnreachableException(message: String, cause: Throwable?) : Exception(message, cause)

/** One runner's HTTP endpoint. */
internal interface RunnerConnection {
    suspend fun status(): RunnerStatus

    /** Sends the command at [path] and returns the runner's answer; its error becomes an [XcTestRunnerException]. */
    suspend fun send(path: String, body: JsonObject): JsonObject

    /** Opens the runner's screen stream at no more than [maxFps] frames a second. */
    suspend fun openScreenStream(maxFps: Int): RunnerScreenStream
}

/** A runner's screen stream, one JPEG frame at a time. */
internal interface RunnerScreenStream : Closeable {
    /**
     * The next frame, blocking until it arrives; null once the stream ends. [close] from another
     * thread ends a read that is waiting.
     */
    fun readJpegFrame(): ByteArray?
}

/** Opens the connection to the runner listening on [port] of this machine's loopback, which checks [token]. */
internal fun interface RunnerConnector {
    fun connect(port: Int, token: String): RunnerConnection
}

private val RunnerJson = Json { ignoreUnknownKeys = true }

private val JSON_MEDIA_TYPE = "application/json".toMediaType()

/**
 * A runner reached over HTTP on this machine's loopback at [port]: the runner itself on a simulator,
 * and a usbmux forward on a device. Every request carries [token].
 */
internal class HttpRunnerConnection(
    private val port: Int,
    private val token: String,
    private val httpClient: OkHttpClient,
) : RunnerConnection {
    override suspend fun status(): RunnerStatus = try {
        RunnerJson.decodeFromJsonElement(post("/status", JsonObject(emptyMap())))
    } catch (e: SerializationException) {
        throw XcTestRunnerException("the XCTest runner's status could not be read: ${e.message}", e)
    }

    override suspend fun send(path: String, body: JsonObject): JsonObject = post(path, body)

    private val screenStreamHttpClient by lazy { httpClient.newBuilder().readTimeout(SCREEN_STREAM_STALL_SECONDS, TimeUnit.SECONDS).build() }

    override suspend fun openScreenStream(maxFps: Int): RunnerScreenStream = withContext(Dispatchers.IO) {
        val call = screenStreamHttpClient.newCall(Request.Builder().url("http://127.0.0.1:$port/stream?fps=$maxFps").header(RUNNER_TOKEN_HEADER, token).build())
        val response = try {
            call.execute()
        } catch (e: ConnectException) {
            throw RunnerUnreachableException("the XCTest runner did not answer: ${e.message}", e)
        } catch (e: IOException) {
            throw XcTestRunnerException("the XCTest runner stopped answering as its screen stream opened: ${e.message}", e)
        }
        val boundary = response.body.contentType()?.takeIf { it.type == "multipart" && it.subtype == "x-mixed-replace" }?.parameter("boundary")
        if (!response.isSuccessful || boundary == null) {
            val text = response.use { it.body.string() }
            throw XcTestRunnerException("the XCTest runner did not stream its screen: ${text.take(200)}", null)
        }
        HttpRunnerScreenStream(call, response, boundary)
    }

    private suspend fun post(path: String, body: JsonObject): JsonObject = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("http://127.0.0.1:$port$path")
            .header(RUNNER_TOKEN_HEADER, token)
            .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()
        val text = try {
            httpClient.newCall(request).execute().use { it.body.string() }
        } catch (e: ConnectException) {
            throw RunnerUnreachableException("the XCTest runner did not answer: ${e.message}", e)
        } catch (e: IOException) {
            throw XcTestRunnerException("the XCTest runner stopped answering during ${path.removePrefix("/")}: ${e.message}", e)
        }
        val answer = try {
            RunnerJson.parseToJsonElement(text).jsonObject
        } catch (e: IllegalArgumentException) {
            throw XcTestRunnerException("the XCTest runner answered something other than JSON: ${text.take(200)}", e)
        }
        if ((answer["ok"] as? JsonPrimitive)?.booleanOrNull != true) {
            val error = (answer["error"] as? JsonPrimitive)?.content ?: "no reason given"
            throw XcTestRunnerException("the XCTest runner failed to ${path.removePrefix("/")}: $error", null)
        }
        answer
    }
}

/** The frames of [response] to [call]. Closing cancels the call, which ends a read waiting on its socket. */
private class HttpRunnerScreenStream(private val call: Call, private val response: Response, boundary: String) : RunnerScreenStream {
    private val frameReader = MjpegFrameReader(response.body.source(), boundary)

    override fun readJpegFrame(): ByteArray? = frameReader.readFrame()

    override fun close() {
        call.cancel()
        response.close()
    }
}
