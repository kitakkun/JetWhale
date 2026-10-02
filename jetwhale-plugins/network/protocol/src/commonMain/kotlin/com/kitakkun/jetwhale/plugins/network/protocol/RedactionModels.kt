package com.kitakkun.jetwhale.plugins.network.protocol

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
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
 */
fun List<RedactionRule>.redact(request: CapturedHttpRequest): CapturedHttpRequest {
    if (isEmpty()) return request
    return request.copy(
        url = redactUrl(request.url),
        headers = redactHeaders(request.headers),
        body = request.body?.let { redactBody(it, request.headers, request.bodyEncoding) },
    )
}

/** Applies the matching rules to a captured response; see the [CapturedHttpRequest] overload. */
fun List<RedactionRule>.redact(response: CapturedHttpResponse): CapturedHttpResponse {
    if (isEmpty()) return response
    return response.copy(
        headers = redactHeaders(response.headers),
        body = response.body?.let { redactBody(it, response.headers, response.bodyEncoding) },
    )
}

/**
 * Applies the URL query rules to the URLs inside a failure's message: client exceptions such as
 * Ktor's timeouts quote the request URL in full.
 */
fun List<RedactionRule>.redact(failure: HttpRequestFailure): HttpRequestFailure {
    if (none { it.target == RedactionTarget.URL_QUERY_PARAM }) return failure
    return failure.copy(message = failure.message.replace(URL_IN_TEXT) { redactUrl(it.value) })
}

private val URL_IN_TEXT = Regex("""https?://\S+""")

private fun List<RedactionRule>.redactHeaders(headers: Map<String, List<String>>): Map<String, List<String>> = headers.mapValues { (name, values) ->
    when (val strategy = strategyFor(RedactionTarget.HEADER, name)) {
        null -> values
        else -> values.map(strategy::render)
    }
}

private fun List<RedactionRule>.strategyFor(target: RedactionTarget, name: String): RedactionStrategy? = lastOrNull { it.target == target && it.name.equals(name, ignoreCase = true) }?.strategy

private fun RedactionStrategy.render(original: String): String = when (this) {
    RedactionStrategy.PLACEHOLDER -> REDACTED_PLACEHOLDER
    RedactionStrategy.MASK -> "*".repeat(original.count { !it.isLowSurrogate() })
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
            val strategy = strategyFor(RedactionTarget.URL_QUERY_PARAM, name.formUrlDecode())
            if ('=' in param && strategy != null) {
                "$name=${strategy.render(param.substringAfter('='))}"
            } else {
                param
            }
        }
    return url.substring(0, queryStart + 1) + redactedQuery + url.substring(queryEnd)
}

private const val FORM_URLENCODED_MEDIA_TYPE = "application/x-www-form-urlencoded"

private fun List<RedactionRule>.redactBody(body: String, headers: Map<String, List<String>>, encoding: BodyEncoding): String {
    if (none { it.target == RedactionTarget.BODY_FIELD }) return body
    if (encoding == BodyEncoding.BASE64) return body
    if (headers.mediaType() == FORM_URLENCODED_MEDIA_TYPE) return redactFormBody(body)
    val element = try {
        Json.parseToJsonElement(body).takeIf { it is JsonObject || it is JsonArray }
    } catch (_: SerializationException) {
        null
    }
    return when {
        element != null -> Json.encodeToString(JsonElement.serializer(), redactFields(element))

        // JSON that does not parse, a truncated body above all, cannot have its fields redacted one by
        // one, and the value of a field it names may follow the name.
        looksLikeJson(body, headers) && namesBodyField(body) -> WITHHELD_BODY

        else -> body
    }
}

private const val WITHHELD_BODY = "<body withheld: it names a redacted field but could not be parsed to redact it>"

private fun looksLikeJson(body: String, headers: Map<String, List<String>>): Boolean = headers.mediaType()?.contains("json") == true || body.trimStart().let { it.startsWith('{') || it.startsWith('[') }

private fun List<RedactionRule>.namesBodyField(text: String): Boolean {
    val names = filter { it.target == RedactionTarget.BODY_FIELD }.map(RedactionRule::name)
    return jsonStringLiterals(text).any { literal -> names.any { it.equals(literal, ignoreCase = true) } }
}

/** The complete JSON string literals in [text], unescaped; one cut off at the end is left out. */
private fun jsonStringLiterals(text: String): Sequence<String> = sequence {
    var index = text.indexOf('"')
    while (index >= 0) {
        val literal = StringBuilder()
        var at = index + 1
        while (at < text.length && text[at] != '"') {
            if (text[at] == '\\' && at + 1 < text.length) {
                val unicode = text.getOrNull(at + 1) == 'u' && at + 6 <= text.length
                val code = if (unicode) text.substring(at + 2, at + 6).takeIf { it.all(Char::isHexDigit) }?.toInt(radix = 16) else null
                if (code != null) {
                    literal.append(code.toChar())
                    at += 6
                } else {
                    literal.append(text[at + 1])
                    at += 2
                }
            } else {
                literal.append(text[at])
                at++
            }
        }
        if (at >= text.length) return@sequence
        yield(literal.toString())
        index = text.indexOf('"', startIndex = at + 1)
    }
}

private fun List<RedactionRule>.redactFormBody(body: String): String = body
    .split('&')
    .joinToString("&") { param ->
        val name = param.substringBefore('=')
        val strategy = strategyFor(RedactionTarget.BODY_FIELD, name.formUrlDecode())
        if ('=' in param && strategy != null) {
            "$name=${strategy.render(param.substringAfter('=').formUrlDecode())}"
        } else {
            param
        }
    }

private fun String.formUrlDecode(): String {
    if ('%' !in this && '+' !in this) return this
    val decoded = StringBuilder(length)
    val bytes = mutableListOf<Byte>()
    fun flushBytes() {
        if (bytes.isEmpty()) return
        decoded.append(bytes.toByteArray().decodeToString())
        bytes.clear()
    }
    var index = 0
    while (index < length) {
        val char = this[index]
        val escaped = if (char == '%' && index + 2 < length) substring(index + 1, index + 3).hexByteOrNull() else null
        if (escaped != null) {
            bytes.add(escaped.toByte())
            index += 3
        } else {
            flushBytes()
            decoded.append(if (char == '+') ' ' else char)
            index++
        }
    }
    flushBytes()
    return decoded.toString()
}

// Only two hex digits are an escape: toIntOrNull would also accept a sign, decoding "%-1" as a byte.
private fun String.hexByteOrNull(): Int? = if (all(Char::isHexDigit)) toInt(radix = 16) else null

private fun Char.isHexDigit(): Boolean = this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'

private fun List<RedactionRule>.redactFields(element: JsonElement): JsonElement = when (element) {
    is JsonObject -> JsonObject(
        element.mapValues { (key, value) ->
            when (val strategy = strategyFor(RedactionTarget.BODY_FIELD, key)) {
                null -> redactFields(value)
                else -> JsonPrimitive(strategy.render(value.stringContentOrPlaceholder()))
            }
        },
    )

    is JsonArray -> JsonArray(element.map { redactFields(it) })

    else -> element
}

private fun JsonElement.stringContentOrPlaceholder(): String = if (this is JsonPrimitive && isString) content else "***"
