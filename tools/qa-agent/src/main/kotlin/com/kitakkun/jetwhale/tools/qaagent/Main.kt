package com.kitakkun.jetwhale.tools.qaagent

import com.kitakkun.jetwhale.agent.runtime.KtorLogLevel
import com.kitakkun.jetwhale.agent.runtime.LogLevel
import com.kitakkun.jetwhale.agent.runtime.startJetWhale
import com.kitakkun.jetwhale.agent.sdk.messaging.OfflineSendPolicy
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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlin.concurrent.thread
import kotlin.system.exitProcess
import kotlin.system.measureTimeMillis
import kotlin.time.Duration.Companion.milliseconds

/**
 * A headless JetWhale debuggee for QA.
 *
 * Driving a host plugin's UI needs a connected session, and the demo apps are a poor stand-in: they
 * only fire the handful of requests their buttons are wired to, and a GUI app cannot be driven from
 * an agent at all. This connects as an ordinary debug session and exposes a control API instead.
 *
 * It is **plugin-agnostic**. Name the plugin ids to impersonate, and `/send` and `/request` carry
 * arbitrary messages to their host counterparts over the messenger's raw layer — no compile-time
 * dependency on the plugin being tested, so it works for plugins living outside this repository.
 * `/fire`, which injects HTTP traffic for the bundled Network Inspector, is the one plugin-specific
 * convenience on top.
 *
 * The control API binds loopback IPv4 only, so address it as `127.0.0.1`: `localhost` may resolve to
 * `::1` first and be refused.
 *
 * ```
 * ./gradlew :tools:qa-agent:run --args="--plugin com.example.myplugin"
 *
 * curl -s 127.0.0.1:7100/send -H 'Content-Type: application/json' -d '{
 *   "pluginId": "com.example.myplugin",
 *   "messageType": "com.example.myplugin.protocol.ItemAdded",
 *   "payload": {"id": 1, "label": "hello"}
 * }'
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
private data class SendRequest(
    val pluginId: String,
    val messageType: String,
    val payload: JsonElement,
    /** Offline behaviour: DROP (default), QUEUE or FAIL. */
    val policy: OfflineSendPolicy = OfflineSendPolicy.DROP,
)

/** @param hint why a `false` send was dropped, when the agent can tell. */
@Serializable
private data class SendResponse(val sent: Boolean, val hint: String? = null)

@Serializable
private data class RequestMessage(
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

@Serializable
private data class ErrorResponse(val error: String)

/** @param ready whether every impersonated plugin can reach the host right now. */
@Serializable
private data class HealthResponse(val status: String, val ready: Boolean)

/**
 * @param activated the host enabled this plugin id. False means it is disabled there, and waiting
 *   will not help.
 * @param ready a message sent now would reach the host.
 */
@Serializable
private data class PluginStatus(val version: String, val activated: Boolean, val ready: Boolean)

/**
 * What the agent impersonates and where it connects.
 *
 * @param plugins plugin ids to register a [WireLevelQaPlugin] for, each with the version to report.
 */
private data class QaAgentOptions(
    val plugins: Map<String, String>,
    val hostName: String,
    val hostPort: Int,
    val controlPort: Int,
)

private const val DEFAULT_PLUGIN_VERSION = "1.0.0"

private val usage = """
    Usage: qa-agent [options]

      --plugin <id>[@<version>]  Register a raw-messaging plugin under this id, so /send and
                                 /request can drive its host counterpart. Repeatable.
                                 Version defaults to $DEFAULT_PLUGIN_VERSION.
      --host <name>              JetWhale host to connect to (default: localhost).
      --port <n>                 Host debug port (default: $DEFAULT_HOST_PORT).
      --control-port <n>         Port for this agent's own control API (default: $DEFAULT_CONTROL_PORT).
""".trimIndent()

private fun parseArgs(args: Array<String>): QaAgentOptions {
    val plugins = mutableMapOf<String, String>()
    var hostName = "localhost"
    var hostPort = DEFAULT_HOST_PORT
    var controlPort = DEFAULT_CONTROL_PORT

    fun valueOf(index: Int, name: String): String = args.getOrNull(index) ?: error("$name requires a value.\n\n$usage")

    fun intValueOf(index: Int, name: String): Int = valueOf(index, name).toIntOrNull() ?: error("$name requires a number.\n\n$usage")

    var i = 0
    while (i < args.size) {
        when (val arg = args[i]) {
            "--plugin" -> {
                val (id, version) = valueOf(++i, arg).split("@", limit = 2).let {
                    it[0] to (it.getOrNull(1) ?: DEFAULT_PLUGIN_VERSION)
                }
                plugins[id] = version
            }

            "--host" -> hostName = valueOf(++i, arg)

            "--port" -> hostPort = intValueOf(++i, arg)

            "--control-port" -> controlPort = intValueOf(++i, arg)

            "--help", "-h" -> {
                println(usage)
                exitProcess(0)
            }

            else -> error("Unknown option: $arg\n\n$usage")
        }
        i++
    }

    return QaAgentOptions(plugins, hostName, hostPort, controlPort)
}

fun main(args: Array<String>) {
    val options = parseArgs(args)

    val networkAgentPlugin = JetWhaleNetworkAgentPlugin()
    val wirePlugins = options.plugins.map { (id, version) -> WireLevelQaPlugin(id, version) }

    startJetWhale {
        app {
            // Make this session obvious in jetwhale.listSessions so it is never mistaken for a
            // real device someone is debugging.
            appName = "qa-agent"
            deviceName = "QA Agent (headless)"
            deviceId = "jetwhale-qa-agent"
        }
        connection {
            host = options.hostName
            port = options.hostPort
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
            wirePlugins.forEach { register(it) }
        }
    }

    val wirePluginsById = wirePlugins.associateBy { it.pluginId }

    val client = HttpClient {
        install(networkAgentPlugin.ktorClientPlugin())
    }

    val server = embeddedServer(Netty, host = "127.0.0.1", port = options.controlPort) {
        install(ContentNegotiation) { json() }
        routing {
            get("/health") {
                // `ready` is what a caller must poll before sending: the control API answers long
                // before the debug session is up, and a send in that window is silently dropped.
                call.respond(
                    HealthResponse(
                        status = "ok",
                        ready = wirePluginsById.values.all { it.isReady },
                    ),
                )
            }

            get("/plugins") {
                call.respond(
                    wirePluginsById.mapValues { (_, plugin) ->
                        PluginStatus(
                            version = plugin.pluginVersion,
                            activated = plugin.isActivated,
                            ready = plugin.isReady,
                        )
                    },
                )
            }

            post("/send") {
                val spec = try {
                    call.receive<SendRequest>()
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("Malformed request: ${e.message}"))
                    return@post
                }
                val plugin = wirePluginsById[spec.pluginId] ?: run {
                    call.respond(HttpStatusCode.BadRequest, unknownPluginError(spec.pluginId, wirePluginsById.keys))
                    return@post
                }
                try {
                    val sent = plugin.send(spec.messageType, spec.payload.toString(), spec.policy)
                    call.respond(SendResponse(sent = sent, hint = if (sent) null else dropHint(plugin)))
                } catch (e: Exception) {
                    // FAIL policy while offline lands here; that is an answer about the connection,
                    // not a malformed call.
                    call.respond(HttpStatusCode.OK, ErrorResponse("${e::class.simpleName}: ${e.message}"))
                }
            }

            post("/request") {
                val spec = try {
                    call.receive<RequestMessage>()
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("Malformed request: ${e.message}"))
                    return@post
                }
                val plugin = wirePluginsById[spec.pluginId] ?: run {
                    call.respond(HttpStatusCode.BadRequest, unknownPluginError(spec.pluginId, wirePluginsById.keys))
                    return@post
                }
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
                } catch (e: Exception) {
                    // A host handler that fails, times out or is not registered is a legitimate QA
                    // finding, so report it as data rather than a control-API error.
                    call.respond(HttpStatusCode.OK, ErrorResponse("${e::class.simpleName}: ${e.message}"))
                }
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

    println("qa-agent: control API on http://127.0.0.1:${options.controlPort} (host ${options.hostName}:${options.hostPort})")
    println(
        if (wirePluginsById.isEmpty()) {
            "qa-agent: no --plugin given; only /fire (Network Inspector) is available."
        } else {
            "qa-agent: impersonating ${wirePluginsById.keys.joinToString()}"
        },
    )
    runBlocking { server.start(wait = true) }
}

private fun unknownPluginError(pluginId: String, known: Set<String>) = ErrorResponse(
    if (known.isEmpty()) {
        "No plugin is registered under '$pluginId'. Start the agent with --plugin $pluginId."
    } else {
        "No plugin is registered under '$pluginId'. Registered: ${known.joinToString()}."
    },
)

/**
 * Why a send was dropped. Both cases look identical from the caller's side — `sent: false` — but only
 * one of them is worth waiting out.
 */
private fun dropHint(plugin: WireLevelQaPlugin): String = when {
    !plugin.isActivated ->
        "The host has not enabled '${plugin.pluginId}' for this session, so nothing is listening. " +
            "Enable the plugin in the host, or check the id."

    !plugin.isReady -> "Connected but not prepared yet — poll /health until \"ready\": true."

    else -> "The connection was unavailable. Retry, or send with \"policy\": \"QUEUE\" to buffer it."
}

/** Replies are opaque here — hand back JSON as JSON, and anything else as a string. */
private fun String.asJsonOrString(): JsonElement = runCatching { Json.parseToJsonElement(this) }.getOrElse { JsonPrimitive(this) }
