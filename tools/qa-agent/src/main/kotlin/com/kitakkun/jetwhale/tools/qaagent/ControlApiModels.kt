package com.kitakkun.jetwhale.tools.qaagent

import com.kitakkun.jetwhale.agent.sdk.messaging.OfflineSendPolicy
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/** @param app which app's session to act on. Optional while only one app is running. */
@Serializable
internal data class FireRequest(
    val app: String? = null,
    val url: String,
    val method: String = "GET",
    val headers: Map<String, String> = emptyMap(),
    val body: String? = null,
    val contentType: String? = null,
)

@Serializable
internal data class FireResponse(
    val status: Int,
    val durationMs: Long,
    val bodyPreview: String,
)

/**
 * @property app which app's session to send from. Optional while only one app is running.
 * @property policy Offline behaviour: DROP (default), QUEUE or FAIL.
 */
@Serializable
internal data class SendRequest(
    val app: String? = null,
    val pluginId: String,
    val messageType: String,
    val payload: JsonElement,
    val policy: OfflineSendPolicy = OfflineSendPolicy.DROP,
)

/** @param hint why a `false` send was dropped, when the agent can tell. */
@Serializable
internal data class SendResponse(val sent: Boolean, val hint: String? = null)

/** @param app which app's session to request from. Optional while only one app is running. */
@Serializable
internal data class RequestMessage(
    val app: String? = null,
    val pluginId: String,
    val messageType: String,
    val payload: JsonElement,
    val timeoutMs: Long? = null,
)

@Serializable
internal data class RequestResponse(
    val durationMs: Long,
    val reply: JsonElement,
)

/** @param app which app to disconnect. Optional while only one app is running. */
@Serializable
internal data class DisconnectRequest(val app: String? = null)

/** @param disconnected false when that app had already given its session up. */
@Serializable
internal data class DisconnectResponse(val app: String, val disconnected: Boolean)

@Serializable
internal data class ErrorResponse(val error: String)

/**
 * @param ready whether every still-connected app can reach the host right now. False once every app
 *   has been disconnected: nothing is left to drive.
 * @param apps per-app breakdown, so a run with several apps can tell which one is holding things up.
 */
@Serializable
internal data class HealthResponse(
    val status: String,
    val ready: Boolean,
    val apps: Map<String, AppHealth>,
)

/** @param connected false once this app gave its session up via `/disconnect`. */
@Serializable
internal data class AppHealth(val connected: Boolean, val ready: Boolean)

/**
 * @param activated the host enabled this plugin id in every connected app. False means it is
 *   disabled there, and waiting will not help.
 * @param ready a message sent now would reach the host from every connected app.
 * @param apps the same two flags per app, for a run holding more than one.
 */
@Serializable
internal data class PluginStatus(
    val version: String,
    val activated: Boolean,
    val ready: Boolean,
    val apps: Map<String, AppPluginStatus>,
)

@Serializable
internal data class AppPluginStatus(val activated: Boolean, val ready: Boolean)
