package com.kitakkun.jetwhale.tools.qaagent

import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlin.system.measureTimeMillis

private const val BODY_PREVIEW_LIMIT = 2000

/** HTTP traffic injected through an app's instrumented client, for the bundled Network Inspector. */
internal fun Route.trafficRoutes(apps: Map<String, QaApp>) {
    post("/fire") {
        val spec = call.receiveOrBadRequest<FireRequest>() ?: return@post
        val app = call.resolveApp(apps, spec.app) ?: return@post
        if (!app.isConnected) {
            call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponse("App '${app.name}' was disconnected, so its traffic is no longer recorded."),
            )
            return@post
        }
        try {
            lateinit var status: HttpStatusCode
            lateinit var preview: String
            val elapsed = measureTimeMillis {
                val response = app.httpClient.request(spec.url) {
                    method = HttpMethod.parse(spec.method.uppercase())
                    spec.contentType?.let { contentType(ContentType.parse(it)) }
                    spec.headers.forEach { (name, value) -> headers.append(name, value) }
                    spec.body?.let { setBody(it) }
                }
                status = response.status
                preview = response.bodyAsText().take(BODY_PREVIEW_LIMIT)
            }
            call.respond(FireResponse(status.value, elapsed, preview))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // A failed HTTP call is a finding about the request the caller fired, not a malformed
            // control call, so it comes back as data with 200.
            call.respond(HttpStatusCode.OK, ErrorResponse("${e::class.simpleName}: ${e.message}"))
        }
    }
}

/** @param app which app's session to act on. Optional while only one app is running. */
@Serializable
private data class FireRequest(
    val app: String? = null,
    val url: String,
    val method: String = "GET",
    val headers: Map<String, String> = emptyMap(),
    val body: String? = null,
    val contentType: String? = null,
)

@Serializable
private data class FireResponse(
    val status: Int,
    val durationMs: Long,
    val bodyPreview: String,
)
