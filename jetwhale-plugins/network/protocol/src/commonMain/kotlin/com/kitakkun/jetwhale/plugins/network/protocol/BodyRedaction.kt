package com.kitakkun.jetwhale.plugins.network.protocol

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

private const val FORM_URLENCODED_MEDIA_TYPE = "application/x-www-form-urlencoded"

internal fun List<RedactionRule>.redactBody(body: String, headers: Map<String, List<String>>, encoding: BodyEncoding): String {
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
        (headers.mediaType()?.contains("json") == true || body.trimStart().let { it.startsWith('{') || it.startsWith('[') }) && namesBodyField(body) -> WITHHELD_BODY
        else -> body
    }
}

private const val WITHHELD_BODY = "<body withheld: it names a redacted field but could not be parsed to redact it>"

private fun List<RedactionRule>.namesBodyField(text: String): Boolean {
    val names = filter { it.target == RedactionTarget.BODY_FIELD }.map(RedactionRule::name)
    return textBetweenQuotes(text).any { stretch -> names.any { it.equals(stretch, ignoreCase = true) } }
}

/**
 * The text between each two consecutive unescaped quotes in [text], unescaped as a JSON string; a
 * stretch cut off at the end is left out. The stretches between string literals count too, so a
 * stray quote in malformed JSON cannot shift a field name out of the scan.
 */
private fun textBetweenQuotes(text: String): Sequence<String> = sequence {
    var index = text.indexOf('"')
    while (index >= 0) {
        val stretch = StringBuilder()
        var at = index + 1
        while (at < text.length && text[at] != '"') {
            if (text[at] == '\\' && at + 1 < text.length) {
                val unicode = text.getOrNull(at + 1) == 'u' && at + 6 <= text.length
                val code = if (unicode) text.substring(at + 2, at + 6).takeIf { it.all(Char::isHexDigit) }?.toInt(radix = 16) else null
                if (code != null) {
                    stretch.append(code.toChar())
                    at += 6
                } else {
                    stretch.append(
                        when (val escaped = text[at + 1]) {
                            'b' -> '\b'
                            'f' -> '\u000C'
                            'n' -> '\n'
                            'r' -> '\r'
                            't' -> '\t'
                            else -> escaped
                        },
                    )
                    at += 2
                }
            } else {
                stretch.append(text[at])
                at++
            }
        }
        if (at >= text.length) return@sequence
        yield(stretch.toString())
        index = at
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
