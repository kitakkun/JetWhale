package com.kitakkun.jetwhale.tools.mcpworkflow

import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.ImageContent
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull

/**
 * What paths in `save` and `expect` read: the structured content when the server sends it, otherwise
 * the text content parsed as JSON — which is how most servers, JetWhale's included, return data — and
 * the plain text when it is not JSON. Several text blocks become an array.
 */
fun resultDocument(result: CallToolResult): JsonElement {
    result.structuredContent?.let { return it }
    val textDocuments = result.content.filterIsInstance<TextContent>().map { parseOrText(it.text) }
    return when (textDocuments.size) {
        0 -> JsonNull
        1 -> textDocuments.single()
        else -> JsonArray(textDocuments)
    }
}

fun resultImages(result: CallToolResult): List<ImageContent> = result.content.filterIsInstance<ImageContent>()

private fun parseOrText(jsonOrText: String): JsonElement = try {
    Json.parseToJsonElement(jsonOrText)
} catch (_: SerializationException) {
    JsonPrimitive(jsonOrText)
}

/** Why [expectation] does not hold for [document], or null when it does. */
fun failureOf(expectation: Expectation, document: JsonElement, isError: Boolean): String? {
    expectation.error?.let { expected ->
        if (expected != isError) return if (isError) "the tool reported an error" else "expected the tool to report an error"
    }
    val path = JsonPath.parse(expectation.path)
    val value = path.select(document)
    // Servers send "error": null and leave error out to mean the same thing.
    val present = value != null && value !is JsonNull && !(path.selectsMany && value is JsonArray && value.isEmpty())
    expectation.exists?.let { expected ->
        if (expected != present) return if (expected) "$path matched nothing" else "$path matched ${value.toTruncatedText()}"
    }
    val checks = listOfNotNull(
        expectation.equals?.let { expected -> ("equal ${expected.toTruncatedText()}" to (value != null && jsonEquals(value, expected))) },
        expectation.notEquals?.let { expected -> ("differ from ${expected.toTruncatedText()}" to (value == null || !jsonEquals(value, expected))) },
        expectation.contains?.let { expected -> ("contain ${expected.toTruncatedText()}" to contains(value, expected)) },
        expectation.matches?.let { pattern -> ("match /$pattern/" to ((value as? JsonPrimitive)?.content?.let { Regex(pattern).containsMatchIn(it) } == true)) },
        expectation.gt?.let { bound -> ("be > $bound" to (value.number()?.let { it > bound } == true)) },
        expectation.gte?.let { bound -> ("be >= $bound" to (value.number()?.let { it >= bound } == true)) },
        expectation.lt?.let { bound -> ("be < $bound" to (value.number()?.let { it < bound } == true)) },
        expectation.lte?.let { bound -> ("be <= $bound" to (value.number()?.let { it <= bound } == true)) },
        expectation.length?.let { expected -> ("have length $expected" to (value.lengthOrNull() == expected)) },
    )
    val failedCheck = checks.firstOrNull { (_, holds) -> !holds } ?: return null
    return "expected $path to ${failedCheck.first}, but it was ${value.toTruncatedText()}"
}

private fun contains(value: JsonElement?, expected: JsonElement): Boolean = when (value) {
    is JsonArray -> value.any { jsonEquals(it, expected) }
    is JsonObject -> (expected as? JsonPrimitive)?.content?.let(value::containsKey) == true
    is JsonPrimitive -> value.isString && (expected as? JsonPrimitive)?.content?.let(value.content::contains) == true
    else -> false
}

private fun JsonElement?.number(): Double? = (this as? JsonPrimitive)?.takeUnless(JsonPrimitive::isString)?.doubleOrNull

private fun JsonElement?.lengthOrNull(): Int? = when (this) {
    is JsonArray -> size
    is JsonObject -> size
    is JsonPrimitive -> if (isString) content.length else null
    else -> null
}

private const val TRUNCATED_TEXT_LIMIT = 200

internal fun JsonElement?.toTruncatedText(): String {
    val text = this?.toString() ?: "nothing"
    return if (text.length <= TRUNCATED_TEXT_LIMIT) text else text.take(TRUNCATED_TEXT_LIMIT) + "…"
}
