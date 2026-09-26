package com.kitakkun.jetwhale.tools.qaagent

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
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
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
            statusRoutes(apps = apps, plugins = options.plugins)
            messagingRoutes(apps)
            trafficRoutes(apps)
            sessionRoutes(apps)
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

/** What a caller polls before it trusts a send: is a session held, and is the plugin up on it. */
private fun Route.statusRoutes(apps: Map<String, QaApp>, plugins: Map<String, String>) {
    get("/health") {
        // `ready` is what a caller must poll before sending: the control API answers long
        // before the debug sessions are up, and a send in that window is silently dropped.
        val connected = apps.values.filter(QaApp::isConnected)
        call.respond(
            HealthResponse(
                status = "ok",
                ready = connected.isNotEmpty() && connected.all(QaApp::isReady),
                apps = apps.mapValues { (_, app) -> AppHealth(connected = app.isConnected, ready = app.isReady) },
            ),
        )
    }

    get("/plugins") {
        call.respond(
            // Every app registers the same plugin ids, so the plugin stays the top-level key
            // and a single-app run reads exactly as it did before there were several.
            plugins.mapValues { (pluginId, version) ->
                val perApp = apps.mapValues { (_, app) -> app.pluginStatus(pluginId) }
                val connected = perApp.filterKeys { apps.getValue(it).isConnected }.values
                PluginStatus(
                    version = version,
                    activated = connected.isNotEmpty() && connected.all(AppPluginStatus::activated),
                    ready = connected.isNotEmpty() && connected.all(AppPluginStatus::ready),
                    apps = perApp,
                )
            },
        )
    }
}

/** Arbitrary plugin messages carried to their host counterparts over the messenger's raw layer. */
private fun Route.messagingRoutes(apps: Map<String, QaApp>) {
    post("/send") {
        val spec = call.receiveOrBadRequest<SendRequest>() ?: return@post
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
        @Suppress("KOTRAIL_CATCH_TOO_BROAD")
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
        @Suppress("KOTRAIL_CATCH_TOO_BROAD")
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
            // A host handler that fails, times out or is not registered is a legitimate QA
            // finding, so report it as data rather than a control-API error.
            call.respond(HttpStatusCode.OK, ErrorResponse("${e::class.simpleName}: ${e.message}"))
        }
    }
}

/** HTTP traffic injected through an app's instrumented client, for the bundled Network Inspector. */
private fun Route.trafficRoutes(apps: Map<String, QaApp>) {
    post("/fire") {
        val spec = call.receiveOrBadRequest<FireRequest>() ?: return@post
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
        @Suppress("KOTRAIL_CATCH_TOO_BROAD")
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
            // A failed request is a legitimate QA scenario (the inspector should show it as
            // a failure), so report it as data rather than a control-API error.
            call.respond(HttpStatusCode.OK, ErrorResponse("${e::class.simpleName}: ${e.message}"))
        }
    }
}

/** Giving a session up on its own, or stopping the whole process. */
private fun Route.sessionRoutes(apps: Map<String, QaApp>) {
    post("/disconnect") {
        val spec = call.receiveOrBadRequest<DisconnectRequest>() ?: return@post
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

/**
 * Receives the call's body, or answers the caller that it was malformed and returns null so the
 * handler stops. A control API driven by hand-written curl gets malformed bodies often enough that
 * every route needs the same answer.
 */
// Ktor reports a malformed body as one of several exception types, depending on the plugin that rejects it.
@Suppress("KOTRAIL_CATCH_TOO_BROAD")
private suspend inline fun <reified T : Any> ApplicationCall.receiveOrBadRequest(): T? = try {
    receive<T>()
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    respond(HttpStatusCode.BadRequest, ErrorResponse("Malformed request: ${e.message}"))
    null
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
