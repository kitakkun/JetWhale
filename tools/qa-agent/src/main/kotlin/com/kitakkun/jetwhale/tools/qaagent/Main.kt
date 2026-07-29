package com.kitakkun.jetwhale.tools.qaagent

import com.kitakkun.jetwhale.agent.sdk.messaging.OfflineSendPolicy
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.ApplicationCall
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
 * One process can hold **several apps** — one session each, all under the same device, which is how
 * the host groups them. Every control call takes an optional `app`; with a single app it can be left
 * out. `/disconnect` gives one app's session up on its own, which is how the host's disconnect
 * handling gets exercised without stopping the process.
 *
 * The control API binds loopback IPv4 only, so address it as `127.0.0.1`: `localhost` may resolve to
 * `::1` first and be refused.
 *
 * ```
 * ./gradlew :tools:qa-agent:run --args="--app checkout --app catalog --plugin com.example.myplugin"
 *
 * curl -s 127.0.0.1:7100/send -H 'Content-Type: application/json' -d '{
 *   "app": "checkout",
 *   "pluginId": "com.example.myplugin",
 *   "messageType": "com.example.myplugin.protocol.ItemAdded",
 *   "payload": {"id": 1, "label": "hello"}
 * }'
 * ```
 */
private const val BODY_PREVIEW_LIMIT = 2000

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

/** @param app which app's session to send from. Optional while only one app is running. */
@Serializable
private data class SendRequest(
    val app: String? = null,
    val pluginId: String,
    val messageType: String,
    val payload: JsonElement,
    /** Offline behaviour: DROP (default), QUEUE or FAIL. */
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

/** @param app which app to disconnect. Optional while only one app is running. */
@Serializable
private data class DisconnectRequest(val app: String? = null)

/** @param disconnected false when that app had already given its session up. */
@Serializable
private data class DisconnectResponse(val app: String, val disconnected: Boolean)

@Serializable
private data class ErrorResponse(val error: String)

/**
 * @param ready whether every still-connected app can reach the host right now. False once every app
 *   has been disconnected: nothing is left to drive.
 * @param apps per-app breakdown, so a run with several apps can tell which one is holding things up.
 */
@Serializable
private data class HealthResponse(
    val status: String,
    val ready: Boolean,
    val apps: Map<String, AppHealth>,
)

/** @param connected false once this app gave its session up via `/disconnect`. */
@Serializable
private data class AppHealth(val connected: Boolean, val ready: Boolean)

/**
 * @param activated the host enabled this plugin id in every connected app. False means it is
 *   disabled there, and waiting will not help.
 * @param ready a message sent now would reach the host from every connected app.
 * @param apps the same two flags per app, for a run holding more than one.
 */
@Serializable
private data class PluginStatus(
    val version: String,
    val activated: Boolean,
    val ready: Boolean,
    val apps: Map<String, AppPluginStatus>,
)

@Serializable
private data class AppPluginStatus(val activated: Boolean, val ready: Boolean)

fun main(args: Array<String>) {
    val options = try {
        parseArgs(args)
    } catch (_: HelpRequestedException) {
        println(usage)
        exitProcess(0)
    }

    val apps = options.apps.associateWith { name -> startQaApp(name, options) }

    val server = embeddedServer(Netty, host = "127.0.0.1", port = options.controlPort) {
        install(ContentNegotiation) { json() }
        routing {
            get("/health") {
                // `ready` is what a caller must poll before sending: the control API answers long
                // before the debug sessions are up, and a send in that window is silently dropped.
                val connected = apps.values.filter { it.isConnected }
                call.respond(
                    HealthResponse(
                        status = "ok",
                        ready = connected.isNotEmpty() && connected.all { it.isReady },
                        apps = apps.mapValues { (_, app) -> AppHealth(connected = app.isConnected, ready = app.isReady) },
                    ),
                )
            }

            get("/plugins") {
                call.respond(
                    // Every app registers the same plugin ids, so the plugin stays the top-level key
                    // and a single-app run reads exactly as it did before there were several.
                    options.plugins.mapValues { (pluginId, version) ->
                        val perApp = apps.mapValues { (_, app) -> app.pluginStatus(pluginId) }
                        val connected = perApp.filterKeys { apps.getValue(it).isConnected }.values
                        PluginStatus(
                            version = version,
                            activated = connected.isNotEmpty() && connected.all { it.activated },
                            ready = connected.isNotEmpty() && connected.all { it.ready },
                            apps = perApp,
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
                val app = call.resolveApp(apps, spec.app) ?: return@post
                val plugin = app.wirePluginsById[spec.pluginId] ?: run {
                    call.respond(HttpStatusCode.BadRequest, unknownPluginError(spec.pluginId, app.wirePluginsById.keys))
                    return@post
                }
                // Refuse before touching the messenger: stop() returns once teardown is scheduled, so
                // a send here can still succeed for a moment and report an app as reachable after it
                // was given up. Timing-dependent answers are the last thing a QA run needs.
                if (!app.isConnected) {
                    call.respond(SendResponse(sent = false, hint = disconnectedAppHint(app.name)))
                    return@post
                }
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
                val app = call.resolveApp(apps, spec.app) ?: return@post
                val plugin = app.wirePluginsById[spec.pluginId] ?: run {
                    call.respond(HttpStatusCode.BadRequest, unknownPluginError(spec.pluginId, app.wirePluginsById.keys))
                    return@post
                }
                if (!app.isConnected) {
                    call.respond(HttpStatusCode.OK, ErrorResponse(disconnectedAppHint(app.name)))
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
                val app = call.resolveApp(apps, spec.app) ?: return@post
                if (!app.isConnected) {
                    // The client is instrumented per app, so traffic fired here would be captured for
                    // a session that no longer exists — silently invisible in the inspector.
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
                } catch (e: Exception) {
                    // A failed request is a legitimate QA scenario (the inspector should show it as
                    // a failure), so report it as data rather than a control-API error.
                    call.respond(HttpStatusCode.OK, ErrorResponse("${e::class.simpleName}: ${e.message}"))
                }
            }

            post("/disconnect") {
                val spec = try {
                    call.receive<DisconnectRequest>()
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("Malformed request: ${e.message}"))
                    return@post
                }
                val app = call.resolveApp(apps, spec.app) ?: return@post
                call.respond(DisconnectResponse(app = app.name, disconnected = app.disconnect()))
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
    println("qa-agent: apps ${options.apps.joinToString()}")
    println(
        if (options.plugins.isEmpty()) {
            "qa-agent: no --plugin given; only /fire (Network Inspector) is available."
        } else {
            "qa-agent: impersonating ${options.plugins.keys.joinToString()}"
        },
    )
    runBlocking { server.start(wait = true) }
}

/**
 * A disconnected app keeps the plugin's activation flag — the host never deactivated it, the session
 * simply went away — so both flags are reported against the session actually being held.
 */
private fun QaApp.pluginStatus(pluginId: String): AppPluginStatus {
    val plugin = wirePluginsById.getValue(pluginId)
    return AppPluginStatus(
        activated = isConnected && plugin.isActivated,
        ready = isConnected && plugin.isReady,
    )
}

/**
 * Resolves the app a call is addressed to, answering the caller itself when it cannot be resolved
 * and returning null so the handler stops without touching a session.
 */
private suspend fun ApplicationCall.resolveApp(apps: Map<String, QaApp>, requested: String?): QaApp? = when (val resolution = resolveAppName(requested, apps.keys)) {
    is AppResolution.Resolved -> apps.getValue(resolution.name)

    is AppResolution.Failed -> {
        respond(HttpStatusCode.BadRequest, ErrorResponse(resolution.error))
        null
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
