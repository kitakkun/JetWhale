package com.kitakkun.jetwhale.tools.mcpworkflow

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull

/**
 * The part of JSONPath a workflow needs, and no more:
 *
 * - `$` the whole document; `.key` or `['key']` a member; `[2]` an element, `[-1]` counting from the end
 * - `[*]` every element or member value
 * - `[?(@.a.b == 'x')]` the elements whose field equals (`==`) or differs from (`!=`) a literal
 *   (quoted string, number, `true`, `false` or `null`); `&&` joins several such comparisons
 *
 * - `.first()` (an extension of this tool) the first of the values selected so far, so a filter can
 *   pick one item: `$.nodes[?(@.text == 'Settings')].first().id`
 *
 * A path with `[*]` or a filter (and no later `.first()`) selects a list, and segments after it apply
 * to each item; any other path selects at most one value.
 */
class JsonPath private constructor(val source: String, private val segments: List<Segment>) {
    val selectsMany: Boolean
        get() {
            val lastFirst = segments.indexOfLast { it is Segment.First }
            return segments.drop(lastFirst + 1).any { it is Segment.Wildcard || it is Segment.Filter }
        }

    /** The selected value — a [JsonArray] of matches when [selectsMany] — or null when nothing matches. */
    fun select(document: JsonElement): JsonElement? {
        var current = listOf(document)
        for (segment in segments) {
            current = when (segment) {
                is Segment.First -> current.take(1)
                else -> current.flatMap(segment::apply)
            }
        }
        return when {
            selectsMany -> JsonArray(current)
            else -> current.firstOrNull()
        }
    }

    override fun toString(): String = source

    private sealed interface Segment {
        fun apply(node: JsonElement): List<JsonElement>

        data class Member(val key: String) : Segment {
            override fun apply(node: JsonElement): List<JsonElement> = listOfNotNull((node as? JsonObject)?.get(key))
        }

        data class Index(val index: Int) : Segment {
            override fun apply(node: JsonElement): List<JsonElement> {
                val array = node as? JsonArray ?: return emptyList()
                val resolved = if (index < 0) array.size + index else index
                return listOfNotNull(array.getOrNull(resolved))
            }
        }

        data object First : Segment {
            override fun apply(node: JsonElement): List<JsonElement> = listOf(node)
        }

        data object Wildcard : Segment {
            override fun apply(node: JsonElement): List<JsonElement> = when (node) {
                is JsonArray -> node
                is JsonObject -> node.values.toList()
                else -> emptyList()
            }
        }

        data class Filter(val conditions: List<Condition>) : Segment {
            override fun apply(node: JsonElement): List<JsonElement> = Wildcard.apply(node).filter { item -> conditions.all { it.holds(item) } }
        }

        data class Condition(val field: JsonPath, val negated: Boolean, val literal: JsonElement) {
            fun holds(item: JsonElement): Boolean {
                val value = field.select(item)
                return (value != null && jsonEquals(value, literal)) != negated
            }
        }
    }

    companion object {
        /** @throws IllegalArgumentException when [source] is not in the supported subset. */
        fun parse(source: String): JsonPath = JsonPath(source, Parser(source.trim()).parse())
    }

    private class Parser(private val text: String) {
        private var position = 0

        fun parse(): List<Segment> {
            require(text.startsWith("$") || text.startsWith("@")) { "path '$text' must start with \$" }
            position = 1
            val segments = mutableListOf<Segment>()
            while (position < text.length) {
                segments += when (text[position]) {
                    '.' -> member()
                    '[' -> bracket()
                    else -> throw IllegalArgumentException("unexpected '${text[position]}' at ${position + 1} in path '$text'")
                }
            }
            return segments
        }

        private fun member(): Segment {
            position++
            if (text.startsWith("first()", position)) {
                position += "first()".length
                return Segment.First
            }
            val start = position
            while (position < text.length && (text[position].isLetterOrDigit() || text[position] in "_-$")) position++
            require(position > start) { "empty member name at ${start + 1} in path '$text'" }
            return Segment.Member(text.substring(start, position))
        }

        private fun bracket(): Segment {
            val close = closingBracket()
            val inner = text.substring(position + 1, close).trim()
            position = close + 1
            return when {
                inner == "*" -> Segment.Wildcard
                inner.startsWith("?(") && inner.endsWith(")") -> filter(inner.substring(2, inner.length - 1).trim())
                inner.startsWith("'") || inner.startsWith("\"") -> Segment.Member(unquote(inner))
                else -> Segment.Index(inner.toIntOrNull() ?: throw IllegalArgumentException("'$inner' is not an index in path '$text'"))
            }
        }

        // A filter's literal may itself hold a ']', so brackets inside quotes are skipped.
        private fun closingBracket(): Int {
            var quote: Char? = null
            for (index in position + 1 until text.length) {
                val char = text[index]
                when {
                    quote != null -> if (char == quote) quote = null
                    char == '\'' || char == '"' -> quote = char
                    char == ']' -> return index
                }
            }
            throw IllegalArgumentException("unclosed '[' in path '$text'")
        }

        private fun filter(expression: String): Segment = Segment.Filter(splitOutsideQuotes(expression, "&&").map { condition(it.trim()) })

        private fun condition(expression: String): Segment.Condition {
            val negated = "!=" in expression
            val operator = if (negated) "!=" else "=="
            val parts = expression.split(operator, limit = 2)
            require(parts.size == 2) { "filter '$expression' must compare with == or != in path '$text'" }
            return Segment.Condition(field = JsonPath.parse(parts[0].trim()), negated = negated, literal = literal(parts[1].trim()))
        }

        private fun splitOutsideQuotes(expression: String, separator: String): List<String> {
            val parts = mutableListOf<String>()
            var quote: Char? = null
            var start = 0
            var index = 0
            while (index < expression.length) {
                val char = expression[index]
                when {
                    quote != null -> if (char == quote) quote = null

                    char == '\'' || char == '"' -> quote = char

                    expression.startsWith(separator, index) -> {
                        parts += expression.substring(start, index)
                        start = index + separator.length
                        index = start - 1
                    }
                }
                index++
            }
            parts += expression.substring(start)
            return parts
        }

        private fun literal(token: String): JsonElement = when {
            token.startsWith("'") || token.startsWith("\"") -> JsonPrimitive(unquote(token))
            token == "true" || token == "false" -> JsonPrimitive(token.toBoolean())
            token == "null" -> JsonNull
            else -> JsonPrimitive(token.toDoubleOrNull() ?: throw IllegalArgumentException("'$token' is not a literal in path '$text'"))
        }

        private fun unquote(token: String): String {
            require(token.length >= 2 && token.last() == token.first()) { "unterminated string $token in path '$text'" }
            return token.substring(1, token.length - 1)
        }
    }
}

/** JSON equality that treats `1` and `1.0` as the same number. */
internal fun jsonEquals(a: JsonElement, b: JsonElement): Boolean {
    if (a is JsonPrimitive && b is JsonPrimitive && !a.isString && !b.isString) {
        val left = a.doubleOrNull
        val right = b.doubleOrNull
        if (left != null && right != null) return left == right
        return a.booleanOrNull == b.booleanOrNull && a.content == b.content
    }
    return a == b
}
