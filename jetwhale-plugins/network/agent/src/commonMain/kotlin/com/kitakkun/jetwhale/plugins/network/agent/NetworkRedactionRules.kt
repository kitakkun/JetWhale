package com.kitakkun.jetwhale.plugins.network.agent

import com.kitakkun.jetwhale.plugins.network.protocol.CapturedHttpRequest
import com.kitakkun.jetwhale.plugins.network.protocol.CapturedHttpResponse
import com.kitakkun.jetwhale.plugins.network.protocol.RedactionRule
import com.kitakkun.jetwhale.plugins.network.protocol.RedactionScope
import com.kitakkun.jetwhale.plugins.network.protocol.RedactionStrategy
import com.kitakkun.jetwhale.plugins.network.protocol.RedactionTarget
import com.kitakkun.jetwhale.plugins.network.protocol.redact

/**
 * Redaction rules for the Network Inspector agent plugin. All name matching is case-insensitive.
 *
 * Each rule carries a [RedactionScope] deciding where it is enforced —
 * [RedactionScope.EVERYWHERE] rules are applied at capture time so the value never leaves the
 * process, while [RedactionScope.MCP_ONLY] rules keep the value visible in the host UI but hide
 * it from MCP-connected AI agents — and a [RedactionStrategy] deciding how the value is rendered
 * (a `<redacted>` placeholder, or a length-preserving `*` mask).
 *
 * ```kotlin
 * val agent = JetWhaleNetworkAgentPlugin(
 *     redaction = NetworkRedactionRules {
 *         header("Authorization", "Cookie")
 *         header("X-Session-Id", scope = RedactionScope.MCP_ONLY)
 *         urlQueryParam("token", strategy = RedactionStrategy.MASK)
 *         bodyJsonField("password", "access_token")
 *     },
 * )
 * ```
 */
class NetworkRedactionRules private constructor(rules: List<RedactionRule>) {
    private val captureRules: List<RedactionRule> = rules.filter { it.scope == RedactionScope.EVERYWHERE }

    /** Rules the host must enforce when serving MCP tool results; fetched via GetRedactionConfig. */
    val mcpOnlyRules: List<RedactionRule> = rules.filter { it.scope == RedactionScope.MCP_ONLY }

    fun redactAtCapture(request: CapturedHttpRequest): CapturedHttpRequest = captureRules.redact(request)

    fun redactAtCapture(response: CapturedHttpResponse): CapturedHttpResponse = captureRules.redact(response)

    class Builder internal constructor() {
        private val rules = mutableListOf<RedactionRule>()

        /** Redacts all values of the given request/response headers. */
        fun header(
            vararg names: String,
            // Defaulted: the DSL's common case is "hide everywhere, as a placeholder".
            scope: RedactionScope = RedactionScope.EVERYWHERE,
            strategy: RedactionStrategy = RedactionStrategy.PLACEHOLDER,
        ) {
            add(RedactionTarget.HEADER, names, scope, strategy)
        }

        /** Redacts the values of the given URL query parameters. */
        fun urlQueryParam(
            vararg names: String,
            scope: RedactionScope = RedactionScope.EVERYWHERE,
            strategy: RedactionStrategy = RedactionStrategy.PLACEHOLDER,
        ) {
            add(RedactionTarget.URL_QUERY_PARAM, names, scope, strategy)
        }

        /** Redacts the values of the given field names anywhere in a JSON request/response body. */
        fun bodyJsonField(
            vararg names: String,
            scope: RedactionScope = RedactionScope.EVERYWHERE,
            strategy: RedactionStrategy = RedactionStrategy.PLACEHOLDER,
        ) {
            add(RedactionTarget.BODY_JSON_FIELD, names, scope, strategy)
        }

        private fun add(target: RedactionTarget, names: Array<out String>, scope: RedactionScope, strategy: RedactionStrategy) {
            names.mapTo(rules) { RedactionRule(target = target, name = it, scope = scope, strategy = strategy) }
        }

        internal fun build(): NetworkRedactionRules = NetworkRedactionRules(rules.toList())
    }

    companion object {
        /** No-op rules: captured data is forwarded verbatim. */
        val None: NetworkRedactionRules = NetworkRedactionRules(emptyList())
    }
}

/** Builds [NetworkRedactionRules] with the DSL documented on the class. */
fun NetworkRedactionRules(configure: NetworkRedactionRules.Builder.() -> Unit): NetworkRedactionRules = NetworkRedactionRules.Builder().apply(configure).build()
