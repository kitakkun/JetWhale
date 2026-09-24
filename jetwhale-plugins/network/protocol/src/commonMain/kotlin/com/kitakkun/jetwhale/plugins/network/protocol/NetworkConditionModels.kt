package com.kitakkun.jetwhale.plugins.network.protocol

import com.kitakkun.jetwhale.annotations.McpDescription
import kotlinx.serialization.Serializable

/** A connection failure a [NetworkCondition] can inject in place of the real exchange. */
@Serializable
enum class InjectedFailure {
    /** The request times out, as a socket read timeout would. */
    TIMEOUT,

    /** The connection is reset mid-exchange. */
    CONNECTION_RESET,

    /** The server cannot be reached (refused, or no route). */
    UNREACHABLE,
}

/**
 * How the simulated network treats a request.
 *
 * A condition models the network, not the server: it slows down, throttles or breaks the exchange,
 * whether the response then comes from the real server or from a [MockRule].
 */
@Serializable
@McpDescription("How the simulated network treats matching requests.")
data class NetworkCondition(
    @McpDescription("Latency added before the request is sent, in milliseconds. Defaults to 0.")
    val latencyMs: Long = 0,
    @McpDescription("Random extra latency of up to this many milliseconds, added to latencyMs per request. Defaults to 0.")
    val jitterMs: Long = 0,
    @McpDescription("Caps how fast the response body arrives, in bytes per second. Unlimited if omitted.")
    val downloadBytesPerSecond: Long? = null,
    @McpDescription("Caps how fast the request body is sent, in bytes per second. Unlimited if omitted.")
    val uploadBytesPerSecond: Long? = null,
    @McpDescription("Probability between 0 and 1 that a request fails with `failure` instead of completing. Defaults to 0.")
    val failureRate: Double = 0.0,
    @McpDescription("The failure injected when a request fails per failureRate. Defaults to CONNECTION_RESET.")
    val failure: InjectedFailure = InjectedFailure.CONNECTION_RESET,
    @McpDescription("How long an injected failure takes to surface, in milliseconds (a timeout usually takes a while). Defaults to 0.")
    val failureAfterMs: Long = 0,
    @McpDescription("When true, every matching request fails at once as if the device had no network. Overrides everything else. Defaults to false.")
    val offline: Boolean = false,
)

/**
 * A network condition applied to the requests [matcher] selects, or to every request when
 * [matcher] is null. Owned by the host and pushed to the agent via [SetNetworkConditions].
 */
@Serializable
data class NetworkConditionRule(
    @McpDescription("Stable identifier of the rule. Reuse the id of an existing rule to edit it; use a fresh UUID for a new one.")
    val id: String,
    @McpDescription("Human-readable rule name shown in the UI and on affected transactions.")
    val name: String = "",
    @McpDescription("Whether the rule takes effect. Defaults to true.")
    val enabled: Boolean = true,
    @McpDescription("Which requests the rule applies to, as for mock rules. Applies to every request if omitted.")
    val matcher: MockMatcher? = null,
    val condition: NetworkCondition,
)

/**
 * The first enabled rule that selects [method] [url]. Like mock rules, the order of the list
 * decides which rule wins, so an endpoint-specific rule goes before a catch-all one.
 */
fun List<NetworkConditionRule>.findMatchingCondition(method: String, url: String): NetworkConditionRule? = firstOrNull { rule -> rule.enabled && (rule.matcher == null || rule.matcher.matches(method, url)) }

/**
 * What a network condition did to one transaction, reported with its response or failure.
 *
 * @property addedLatencyMs The latency actually added, jitter included.
 * @property injectedFailure The failure injected instead of the exchange, if any.
 */
@Serializable
data class AppliedNetworkCondition(
    val ruleId: String,
    val ruleName: String,
    val addedLatencyMs: Long,
    val downloadBytesPerSecond: Long?,
    val uploadBytesPerSecond: Long?,
    val injectedFailure: InjectedFailure?,
    val offline: Boolean,
)
