package com.kitakkun.jetwhale.plugins.soil.agent

import com.kitakkun.jetwhale.plugins.soil.protocol.MAX_SOIL_VALUE_TEXT_LENGTH
import com.kitakkun.jetwhale.plugins.soil.protocol.SoilEntryKind
import com.kitakkun.jetwhale.plugins.soil.protocol.SoilEntryValue
import com.kitakkun.jetwhale.plugins.soil.protocol.SoilValueEncoding
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.serializerOrNull
import soil.query.QueryChunk
import soil.query.core.Reply

/**
 * Turns an entry's reply into what the host shows: JSON when some serializer covers the value,
 * `toString()` when none does, cut at [MAX_SOIL_VALUE_TEXT_LENGTH] either way.
 *
 * A serializer the app registered comes first, since only it knows a generic class's type
 * arguments; then the serializers of the value's own classes; then `toString()`.
 */
internal class SoilValueEncoder(private val registeredSerializers: SoilValueSerializers) {
    private val json = Json { encodeDefaults = true }

    fun encode(key: SoilEntryKey, reply: Reply<*>): SoilEntryValue = when (reply) {
        is Reply.None -> SoilEntryValue.NoReply
        is Reply.Some -> encodeValue(key, reply.value)
    }

    private fun encodeValue(key: SoilEntryKey, value: Any?): SoilEntryValue {
        val registered = registeredSerializers.serializerFor(key.id)?.let { serializer ->
            if (key.kind == SoilEntryKind.INFINITE_QUERY) encodeChunksWith(serializer, value) else encodeWith(serializer, value)
        }
        if (registered != null) return jsonValue(SoilValueEncoding.REGISTERED_SERIALIZER, registered)
        val walked = encodeWithClassSerializers(value)
        if (walked != null) return jsonValue(SoilValueEncoding.CLASS_SERIALIZERS, walked)
        return textValue(SoilValueEncoding.TO_STRING, value.toString())
    }

    private fun jsonValue(encoding: SoilValueEncoding, element: JsonElement): SoilEntryValue {
        val text = element.toString()
        return if (text.length > MAX_SOIL_VALUE_TEXT_LENGTH) textValue(encoding, text) else SoilEntryValue.Json(encoding = encoding, json = element)
    }

    private fun textValue(encoding: SoilValueEncoding, text: String): SoilEntryValue = SoilEntryValue.Text(encoding = encoding, text = text.take(MAX_SOIL_VALUE_TEXT_LENGTH), fullLength = text.length)

    /** Encodes each chunk's data with the registered [serializer], and its param as any other value. */
    private fun encodeChunksWith(serializer: KSerializer<*>, value: Any?): JsonElement? {
        val chunks = value as? List<*> ?: return null
        return JsonArray(
            chunks.map { chunk ->
                if (chunk !is QueryChunk<*, *>) return null
                JsonObject(
                    mapOf(
                        "data" to (encodeWith(serializer, chunk.data) ?: return null),
                        "param" to (encodeWithClassSerializers(chunk.param) ?: return null),
                    ),
                )
            },
        )
    }

    /** Null when [value] is not what [serializer] describes: the app registered it for another type. */
    private fun encodeWith(serializer: KSerializer<*>, value: Any?): JsonElement? = try {
        @Suppress("UNCHECKED_CAST")
        json.encodeToJsonElement(serializer as KSerializer<Any?>, value)
    } catch (_: IllegalArgumentException) {
        null
    } catch (_: ClassCastException) {
        null
    }

    /**
     * Encodes [value] with the serializers of its own classes, walking into the containers whose
     * element types are erased at runtime. Null when some part of it has no serializer.
     */
    private fun encodeWithClassSerializers(value: Any?): JsonElement? = when (value) {
        null -> JsonNull
        is String -> JsonPrimitive(value)
        is Boolean -> JsonPrimitive(value)
        is Number -> JsonPrimitive(value)
        is Char -> JsonPrimitive(value.toString())
        is QueryChunk<*, *> -> encodeFields("data" to value.data, "param" to value.param)
        is Pair<*, *> -> encodeFields("first" to value.first, "second" to value.second)
        is Triple<*, *, *> -> encodeFields("first" to value.first, "second" to value.second, "third" to value.third)
        is Map<*, *> -> encodeMap(value)
        is Collection<*> -> encodeElements(value)
        is Array<*> -> encodeElements(value.asList())
        else -> encodeWithOwnSerializer(value)
    }

    private fun encodeFields(vararg fields: Pair<String, Any?>): JsonElement? = JsonObject(
        fields.associate { (name, fieldValue) -> name to (encodeWithClassSerializers(fieldValue) ?: return null) },
    )

    private fun encodeElements(elements: Collection<*>): JsonElement? = JsonArray(elements.map { encodeWithClassSerializers(it) ?: return null })

    /** JSON keys are strings, so only a map whose keys read as one unambiguously is walked. */
    private fun encodeMap(map: Map<*, *>): JsonElement? = JsonObject(
        map.entries.associate { (mapKey, mapValue) ->
            val jsonKey = when (mapKey) {
                is String, is Number, is Boolean, is Char -> mapKey.toString()
                is Enum<*> -> mapKey.name
                else -> return null
            }
            jsonKey to (encodeWithClassSerializers(mapValue) ?: return null)
        },
    )

    /**
     * The lookup only finds the serializers of non-generic classes. On Kotlin/Native and Wasm it can
     * throw for a generic one instead of returning null.
     */
    @OptIn(InternalSerializationApi::class)
    private fun encodeWithOwnSerializer(value: Any): JsonElement? {
        val serializer = runCatching { value::class.serializerOrNull() }.getOrNull()
        return when {
            serializer != null -> encodeWith(serializer, value)
            value is Enum<*> -> JsonPrimitive(value.name)
            else -> null
        }
    }
}
