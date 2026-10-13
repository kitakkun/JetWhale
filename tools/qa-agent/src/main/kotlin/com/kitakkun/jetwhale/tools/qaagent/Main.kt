package com.kitakkun.jetwhale.tools.qaagent

import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import kotlinx.coroutines.runBlocking
import kotlin.system.exitProcess

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
