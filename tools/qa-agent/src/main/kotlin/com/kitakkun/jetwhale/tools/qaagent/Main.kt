package com.kitakkun.jetwhale.tools.qaagent

import com.kitakkun.jetwhale.agent.runtime.KtorLogLevel
import com.kitakkun.jetwhale.agent.runtime.LogLevel
import com.kitakkun.jetwhale.agent.runtime.startJetWhale
import com.kitakkun.jetwhale.plugins.network.agent.JetWhaleNetworkAgentPlugin
import com.kitakkun.jetwhale.plugins.network.agent.ktor.ktorClientPlugin
import io.ktor.client.HttpClient
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlin.concurrent.thread
import kotlin.system.exitProcess
import kotlin.system.measureTimeMillis

/**
 * A headless JetWhale debuggee for QA.
 *
 * The demo apps can only fire the handful of requests their buttons are wired to, and a GUI app
 * cannot be driven from an agent at all. This connects to the host as an ordinary debug session and
 * exposes a small control API instead, so any request — arbitrary URL, method, headers, body — can
 * be injected into the Network Inspector on demand.
 *
 * The control API binds loopback IPv4 only, so address it as `127.0.0.1`: `localhost` may resolve to
 * `::1` first and be refused.
 *
 * ```
 * ./gradlew :tools:qa-agent:run
 * curl -s 127.0.0.1:7100/fire -H 'Content-Type: application/json' \
 *   -d '{"method":"GET","url":"https://example.com/items?page=2"}'
 * ```
 */
private const val DEFAULT_CONTROL_PORT = 7100
private const val DEFAULT_HOST_PORT = 5443
private const val BODY_PREVIEW_LIMIT = 2000

@Serializable
private data class FireRequest(
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

@Serializable
private data class ErrorResponse(val error: String)

private fun intProperty(name: String, default: Int): Int = System.getProperty(name)?.toIntOrNull() ?: default

fun main() {
    val controlPort = intProperty("qa.controlPort", DEFAULT_CONTROL_PORT)
    val hostPort = intProperty("qa.hostPort", DEFAULT_HOST_PORT)
    val hostName = System.getProperty("qa.host") ?: "localhost"

    val networkAgentPlugin = JetWhaleNetworkAgentPlugin()

    startJetWhale {
        app {
            // Make this session obvious in jetwhale.listSessions so it is never mistaken for a
            // real device someone is debugging.
            appName = "qa-agent"
            deviceName = "QA Agent (headless)"
            deviceId = "jetwhale-qa-agent"
        }
        connection {
            host = hostName
            port = hostPort
            ssl {
                trustServerCertificate()
            }
        }
        logging {
            enabled = true
            logLevel = LogLevel.INFO
            ktorLogLevel = KtorLogLevel.NONE
        }
        plugins {
            register(networkAgentPlugin)
        }
    }

    val client = HttpClient {
        install(networkAgentPlugin.ktorClientPlugin())
    }

    val server = embeddedServer(Netty, host = "127.0.0.1", port = controlPort) {
        install(ContentNegotiation) { json() }
        routing {
            get("/health") {
                call.respond(mapOf("status" to "ok"))
            }

            post("/fire") {
                val spec = try {
                    call.receive<FireRequest>()
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("Malformed request: ${e.message}"))
                    return@post
                }
                try {
                    lateinit var status: HttpStatusCode
                    lateinit var preview: String
                    val elapsed = measureTimeMillis {
                        val response = client.request(spec.url) {
                            method = HttpMethod.parse(spec.method.uppercase())
                            spec.contentType?.let { contentType(ContentType.parse(it)) }
                            spec.headers.forEach { (name, value) -> headers.append(name, value) }
                            spec.body?.let { setBody(it) }
                        }
                        status = response.status
                        preview = response.bodyAsText().take(BODY_PREVIEW_LIMIT)
                    }
                    call.respond(FireResponse(status.value, elapsed, preview))
                } catch (e: Exception) {
                    // A failed request is a legitimate QA scenario (the inspector should show it as
                    // a failure), so report it as data rather than a control-API error.
                    call.respond(HttpStatusCode.OK, ErrorResponse("${e::class.simpleName}: ${e.message}"))
                }
            }

            post("/shutdown") {
                call.respond(mapOf("status" to "stopping"))
                thread {
                    Thread.sleep(200)
                    exitProcess(0)
                }
            }
        }
    }

    println("qa-agent: control API on http://127.0.0.1:$controlPort (host $hostName:$hostPort)")
    runBlocking { server.start(wait = true) }
}
