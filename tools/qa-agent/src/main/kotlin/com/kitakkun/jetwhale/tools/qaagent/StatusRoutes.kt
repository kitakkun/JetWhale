package com.kitakkun.jetwhale.tools.qaagent

import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlinx.serialization.Serializable

/** What a caller polls before it trusts a send: is a session held, and is the plugin up on it. */
internal fun Route.statusRoutes(apps: Map<String, QaApp>, plugins: Map<String, String>) {
    get("/health") {
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
