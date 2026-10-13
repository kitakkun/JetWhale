package com.kitakkun.jetwhale.tools.qaagent

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable

/**
 * Receives the call's body, or answers the caller that it was malformed and returns null so the
 * handler stops. A control API driven by hand-written curl gets malformed bodies often enough that
 * every route needs the same answer.
 */
internal suspend inline fun <reified T : Any> ApplicationCall.receiveOrBadRequest(): T? = try {
    receive<T>()
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    // Ktor reports a malformed body as one of several exception types, depending on the plugin
    // that rejects it.
    respond(HttpStatusCode.BadRequest, ErrorResponse("Malformed request: ${e.message}"))
    null
}

/**
 * Resolves the app a call is addressed to, answering the caller itself when it cannot be resolved
 * and returning null so the handler stops without touching a session.
 */
internal suspend fun ApplicationCall.resolveApp(
    apps: Map<String, QaApp>,
    requested: String?,
): QaApp? = when (val resolution = resolveAppName(requested, apps.keys)) {
    is AppResolution.Resolved -> apps.getValue(resolution.name)

    is AppResolution.Failed -> {
        respond(HttpStatusCode.BadRequest, ErrorResponse(resolution.error))
        null
    }
}

@Serializable
internal data class ErrorResponse(val error: String)
