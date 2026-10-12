package com.kitakkun.jetwhale.tools.qaagent

import com.kitakkun.jetwhale.agent.sdk.messaging.OfflineSendPolicy
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlin.system.measureTimeMillis
import kotlin.time.Duration.Companion.milliseconds

/** Arbitrary plugin messages carried to their host counterparts over the messenger's raw layer. */
internal fun Route.messagingRoutes(apps: Map<String, QaApp>) {
    post("/send") {
        val spec = call.receiveOrBadRequest<SendRequest>() ?: return@post
        val app = call.resolveApp(apps, spec.app) ?: return@post
        val plugin = app.wirePluginsById[spec.pluginId] ?: run {
            call.respond(HttpStatusCode.BadRequest, unknownPluginError(spec.pluginId, app.wirePluginsById.keys))
            return@post
        }
        if (!app.isConnected) {
            call.respond(SendResponse(sent = false, hint = disconnectedAppHint(app.name)))
            return@post
        }
        // Any failure of the send is the QA client's answer, returned as a response rather than a
        // server error.
        try {
            val sent = plugin.send(spec.messageType, spec.payload.toString(), spec.policy)
            val hint = if (sent) {
                null
            } else {
                sendDropHint(
                    pluginId = plugin.pluginId,
                    appName = app.name,
                    appConnected = app.isConnected,
                    activated = plugin.isActivated,
                    ready = plugin.isReady,
                )
            }
            call.respond(SendResponse(sent = sent, hint = hint))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // FAIL policy while offline lands here; that is an answer about the connection,
            // not a malformed call.
            call.respond(HttpStatusCode.OK, ErrorResponse("${e::class.simpleName}: ${e.message}"))
        }
    }

    post("/request") {
        val spec = call.receiveOrBadRequest<RequestMessage>() ?: return@post
        val app = call.resolveApp(apps, spec.app) ?: return@post
        val plugin = app.wirePluginsById[spec.pluginId] ?: run {
            call.respond(HttpStatusCode.BadRequest, unknownPluginError(spec.pluginId, app.wirePluginsById.keys))
            return@post
        }
        if (!app.isConnected) {
            call.respond(HttpStatusCode.OK, ErrorResponse(disconnectedAppHint(app.name)))
            return@post
        }
        // Any failure of the request is the QA client's answer, returned as a response rather than
        // a server error.
        try {
            lateinit var reply: String
            val elapsed = measureTimeMillis {
                reply = plugin.request(
                    messageType = spec.messageType,
                    payload = spec.payload.toString(),
                    timeout = spec.timeoutMs?.milliseconds,
                )
            }
            call.respond(RequestResponse(elapsed, reply.asJsonOrString()))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            call.respond(HttpStatusCode.OK, ErrorResponse("${e::class.simpleName}: ${e.message}"))
        }
    }
}

private fun unknownPluginError(pluginId: String, known: Set<String>) = ErrorResponse(
    if (known.isEmpty()) {
        "No plugin is registered under '$pluginId'. Start the agent with --plugin $pluginId."
    } else {
        "No plugin is registered under '$pluginId'. Registered: ${known.joinToString()}."
    },
)

/** Replies are opaque here — hand back JSON as JSON, and anything else as a string. */
private fun String.asJsonOrString(): JsonElement = runCatching { Json.parseToJsonElement(this) }.getOrElse { JsonPrimitive(this) }

/**
 * @property app which app's session to send from. Optional while only one app is running.
 * @property policy Offline behaviour: DROP (default), QUEUE or FAIL.
 */
@Serializable
private data class SendRequest(
    val app: String? = null,
    val pluginId: String,
    val messageType: String,
    val payload: JsonElement,
    val policy: OfflineSendPolicy = OfflineSendPolicy.DROP,
)

/** @param hint why a `false` send was dropped, when the agent can tell. */
@Serializable
private data class SendResponse(val sent: Boolean, val hint: String? = null)

/** @param app which app's session to request from. Optional while only one app is running. */
@Serializable
private data class RequestMessage(
    val app: String? = null,
    val pluginId: String,
    val messageType: String,
    val payload: JsonElement,
    val timeoutMs: Long? = null,
)

@Serializable
private data class RequestResponse(
    val durationMs: Long,
    val reply: JsonElement,
)
