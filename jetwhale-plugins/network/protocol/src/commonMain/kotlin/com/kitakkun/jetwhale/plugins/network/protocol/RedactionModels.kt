package com.kitakkun.jetwhale.plugins.network.protocol

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

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

    /** Replaces each character of the value with `*`, preserving its length. */
    MASK,
}

/** What part of a captured request/response a [RedactionRule] matches. */
@Serializable
enum class RedactionTarget {
    HEADER,
    URL_QUERY_PARAM,
    BODY_JSON_FIELD,
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
 */
fun List<RedactionRule>.redact(request: CapturedHttpRequest): CapturedHttpRequest {
    if (isEmpty()) return request
    return request.copy(
        url = redactUrl(request.url),
        headers = redactHeaders(request.headers),
        body = request.body?.let(::redactBody),
    )
}

/** Applies the matching rules to a captured response; see the [CapturedHttpRequest] overload. */
fun List<RedactionRule>.redact(response: CapturedHttpResponse): CapturedHttpResponse {
    if (isEmpty()) return response
    return response.copy(
        headers = redactHeaders(response.headers),
        body = response.body?.let(::redactBody),
    )
}

private fun List<RedactionRule>.strategyFor(target: RedactionTarget, name: String): RedactionStrategy? = lastOrNull { it.target == target && it.name.equals(name, ignoreCase = true) }?.strategy

private fun RedactionStrategy.render(original: String): String = when (this) {
    RedactionStrategy.PLACEHOLDER -> REDACTED_PLACEHOLDER
    RedactionStrategy.MASK -> "*".repeat(original.length)
}

private fun List<RedactionRule>.redactHeaders(headers: Map<String, List<String>>): Map<String, List<String>> = headers.mapValues { (name, values) ->
    when (val strategy = strategyFor(RedactionTarget.HEADER, name)) {
        null -> values
        else -> values.map { strategy.render(it) }
    }
}

private fun List<RedactionRule>.redactUrl(url: String): String {
    if (none { it.target == RedactionTarget.URL_QUERY_PARAM }) return url
    val queryStart = url.indexOf('?')
    if (queryStart < 0) return url
    val fragmentStart = url.indexOf('#', startIndex = queryStart)
    val queryEnd = if (fragmentStart < 0) url.length else fragmentStart
    val redactedQuery = url.substring(queryStart + 1, queryEnd)
        .split('&')
        .joinToString("&") { param ->
            val name = param.substringBefore('=')
            val strategy = strategyFor(RedactionTarget.URL_QUERY_PARAM, name)
            if ('=' in param && strategy != null) {
                "$name=${strategy.render(param.substringAfter('='))}"
            } else {
                param
            }
        }
    return url.substring(0, queryStart + 1) + redactedQuery + url.substring(queryEnd)
}

// A body that is not structured JSON (non-JSON content type, bare literal, or truncated by
// maxBodyChars) is forwarded unchanged; only header/query rules can protect such bodies.
private fun List<RedactionRule>.redactBody(body: String): String {
    if (none { it.target == RedactionTarget.BODY_JSON_FIELD }) return body
    val element = try {
        Json.parseToJsonElement(body).takeIf { it is JsonObject || it is JsonArray } ?: return body
    } catch (_: Exception) {
        return body
    }
    return Json.encodeToString(JsonElement.serializer(), redactFields(element))
}

private fun List<RedactionRule>.redactFields(element: JsonElement): JsonElement = when (element) {
    is JsonObject -> JsonObject(
        element.mapValues { (key, value) ->
            when (val strategy = strategyFor(RedactionTarget.BODY_JSON_FIELD, key)) {
                null -> redactFields(value)
                else -> JsonPrimitive(strategy.render(value.stringContentOrPlaceholder()))
            }
        },
    )

    is JsonArray -> JsonArray(element.map { redactFields(it) })

    else -> element
}

// MASK preserves the length of string values; non-string values (numbers, objects, arrays)
// are collapsed to a fixed-width mask so their shape is not leaked.
private fun JsonElement.stringContentOrPlaceholder(): String = if (this is JsonPrimitive && isString) content else "***"
