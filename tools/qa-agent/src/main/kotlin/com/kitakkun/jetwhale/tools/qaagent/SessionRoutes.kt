package com.kitakkun.jetwhale.tools.qaagent

import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import kotlinx.serialization.Serializable
import kotlin.concurrent.thread
import kotlin.system.exitProcess

/** Giving a session up on its own, or stopping the whole process. */
internal fun Route.sessionRoutes(apps: Map<String, QaApp>) {
    post("/disconnect") {
        val spec = call.receiveOrBadRequest<DisconnectRequest>() ?: return@post
        val app = call.resolveApp(apps, spec.app) ?: return@post
        call.respond(DisconnectResponse(app = app.name, disconnected = app.disconnect()))
    }

    post("/shutdown") {
        call.respond(mapOf("status" to "stopping"))
        thread {
            // Lets the response above reach the caller before the process exits.
            Thread.sleep(200)
            exitProcess(0)
        }
    }
}

/** @param app which app to disconnect. Optional while only one app is running. */
@Serializable
private data class DisconnectRequest(val app: String? = null)

/** @param disconnected false when that app had already given its session up. */
@Serializable
private data class DisconnectResponse(val app: String, val disconnected: Boolean)
