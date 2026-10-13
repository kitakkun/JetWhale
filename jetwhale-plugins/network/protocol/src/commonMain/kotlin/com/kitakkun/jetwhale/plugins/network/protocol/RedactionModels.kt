package com.kitakkun.jetwhale.plugins.network.protocol

import kotlinx.serialization.Serializable

/** Where a [RedactionRule] is enforced. */
@Serializable
enum class RedactionScope {
    /** Applied at capture time on the agent: the value never leaves the debuggee process. */
    EVERYWHERE,

    /** Applied by the host only when serving MCP tool results: the host UI still shows the value. */
    MCP_ONLY,
}

/** How a redacted value is rendered. */
@Serializable
enum class RedactionStrategy {
    /** Replaces the value with [REDACTED_PLACEHOLDER]. */
    PLACEHOLDER,

    /** Replaces the value with one `*` per Unicode code point, so supplementary-plane characters (e.g. emoji) count as one. */
    MASK,
}

/** What part of a captured request/response a [RedactionRule] matches. */
@Serializable
enum class RedactionTarget {
    HEADER,
    URL_QUERY_PARAM,

    /** A field of a JSON body, or a field of an `application/x-www-form-urlencoded` body. */
    BODY_FIELD,
}

/** A single redaction rule; [name] is matched case-insensitively against the [target]. */
@Serializable
data class RedactionRule(
    val target: RedactionTarget,
    val name: String,
    val scope: RedactionScope,
    val strategy: RedactionStrategy,
)

const val REDACTED_PLACEHOLDER: String = "<redacted>"

/**
 * Applies the matching rules to a captured request. Runs on the agent for
 * [RedactionScope.EVERYWHERE] rules and on the host (before building MCP tool results) for
 * [RedactionScope.MCP_ONLY] rules.
 *
 * URL query rules apply to the URL and to every header value a header rule does not hide whole:
 * headers such as `Referer` carry a URL, so the value after a matching `?name=` or `&name=` in them
 * is hidden as in a failure's message.
 */
fun List<RedactionRule>.redact(request: CapturedHttpRequest): CapturedHttpRequest {
    if (isEmpty()) return request
    return request.copy(
        url = redactUrl(request.url),
        headers = redactHeaders(request.headers),
        body = request.body?.let { redactBody(it, request.headers, request.bodyEncoding) },
    )
}

/**
 * Applies the matching rules to a captured response; see the [CapturedHttpRequest] overload. A
 * redirect's `Location` is one of the header values whose query values this hides.
 */
fun List<RedactionRule>.redact(response: CapturedHttpResponse): CapturedHttpResponse {
    if (isEmpty()) return response
    return response.copy(
        headers = redactHeaders(response.headers),
        body = response.body?.let { redactBody(it, response.headers, response.bodyEncoding) },
    )
}

/**
 * Applies the URL query rules to a failure's message, which may quote the request URL in full, as
 * Ktor's timeout exceptions do. The value after every `?name=` or `&name=` in the message is hidden
 * up to the next `&`, `#` or whitespace, whatever the URL's scheme (WebSocket URLs included) and
 * wherever the URL seems to end.
 */
fun List<RedactionRule>.redact(failure: HttpRequestFailure): HttpRequestFailure = failure.copy(message = redactQueryParamsInText(failure.message))

private fun List<RedactionRule>.redactHeaders(headers: Map<String, List<String>>): Map<String, List<String>> = headers.mapValues { (name, values) ->
    when (val strategy = strategyFor(RedactionTarget.HEADER, name)) {
        null -> values.map { redactQueryParamsInText(it) }
        else -> values.map(strategy::render)
    }
}

internal fun List<RedactionRule>.strategyFor(target: RedactionTarget, name: String): RedactionStrategy? = lastOrNull { it.target == target && it.name.equals(name, ignoreCase = true) }?.strategy

internal fun RedactionStrategy.render(original: String): String = when (this) {
    RedactionStrategy.PLACEHOLDER -> REDACTED_PLACEHOLDER
    RedactionStrategy.MASK -> "*".repeat(original.count { !it.isLowSurrogate() })
}
