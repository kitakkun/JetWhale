package com.kitakkun.jetwhale.demo

import io.ktor.http.ContentType
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing

/**
 * A tiny self-contained API the demo client calls. Running it locally keeps this OSS demo from
 * depending on (or hammering) a public endpoint, and gives the Network Inspector real traffic.
 */
fun startDemoApiServer(port: Int = 8080) {
    embeddedServer(Netty, port = port, host = "127.0.0.1") {
        routing {
            get("/api/todos/{id}") {
                val id = call.parameters["id"] ?: "0"
                call.respondText(
                    """{"id":$id,"title":"Sample todo $id","completed":false}""",
                    ContentType.Application.Json,
                )
            }
        }
    }.start(wait = false)
}
