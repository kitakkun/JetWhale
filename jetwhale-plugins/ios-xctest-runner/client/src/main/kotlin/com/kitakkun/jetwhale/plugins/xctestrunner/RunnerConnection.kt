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
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.ConnectException

/** The commands this client sends; `/status` reports the runner's, and an older runner is replaced. */
internal const val PROTOCOL_VERSION = 2

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
 * A runner that took no connection, so the command was never sent, or a status request that got no
 * answer; either can be sent again. A runner that stops answering after taking a command throws
 * [XcTestRunnerException] instead: it may have carried the command out.
 */
internal class RunnerUnreachableException(message: String, cause: Throwable?) : Exception(message, cause)

/** One runner's HTTP endpoint. */
internal interface RunnerConnection {
    suspend fun status(): RunnerStatus

    /** Sends the command at [path] and returns the runner's answer; its error becomes an [XcTestRunnerException]. */
    suspend fun send(path: String, body: JsonObject): JsonObject
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
        RunnerJson.decodeFromJsonElement(post("/status", JsonObject(emptyMap()), isSafeToRepeat = true))
    } catch (e: SerializationException) {
        throw XcTestRunnerException("the XCTest runner's status could not be read: ${e.message}", e)
    }

    override suspend fun send(path: String, body: JsonObject): JsonObject = post(path, body, isSafeToRepeat = false)

    /**
     * Posts [body] to [path] and returns the runner's answer. A request that gets no answer is
     * unreachable when the connection was refused, or when it [isSafeToRepeat], as `/status` is: a
     * device's forward takes the connection even while the runner behind it is not listening yet,
     * and then closes it.
     */
    private suspend fun post(path: String, body: JsonObject, isSafeToRepeat: Boolean): JsonObject = withContext(Dispatchers.IO) {
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
            if (isSafeToRepeat) throw RunnerUnreachableException("the XCTest runner did not answer: ${e.message}", e)
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
