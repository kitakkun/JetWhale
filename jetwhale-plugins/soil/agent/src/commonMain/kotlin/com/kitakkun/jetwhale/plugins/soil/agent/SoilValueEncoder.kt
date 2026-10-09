package com.kitakkun.jetwhale.plugins.soil.agent

import com.kitakkun.jetwhale.plugins.soil.protocol.MAX_SOIL_VALUE_TEXT_LENGTH
import com.kitakkun.jetwhale.plugins.soil.protocol.SoilEntryKind
import com.kitakkun.jetwhale.plugins.soil.protocol.SoilEntryValue
import com.kitakkun.jetwhale.plugins.soil.protocol.SoilValueEncoding
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.modules.EmptySerializersModule
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.serializer
import kotlinx.serialization.serializerOrNull
import soil.query.QueryChunk
import soil.query.core.Reply

/**
 * Turns an entry's reply into what the host shows: JSON when some serializer covers the value,
 * `toString()` when none does, cut at [MAX_SOIL_VALUE_TEXT_LENGTH] either way.
 *
 * A serializer the app registered comes first, since it is the app's own say on the value; then the
 * serializers of the value's own classes; then `toString()`.
 */
internal class SoilValueEncoder(private val registeredSerializers: SoilValueSerializers) {
    private val json = Json { encodeDefaults = true }
    private val emptyModule: SerializersModule = EmptySerializersModule()

    /**
     * Stands in for the type arguments of a generic class: the values themselves say what they are,
     * so each is encoded the way a value of its own would be.
     */
    private val typeArgumentSerializer = object : KSerializer<Any?> {
        override val descriptor: SerialDescriptor = JsonElement.serializer().descriptor

        override fun serialize(encoder: Encoder, value: Any?) {
            val jsonEncoder = encoder as? JsonEncoder ?: throw SerializationException("only encodes to JSON")
            jsonEncoder.encodeJsonElement(encodeWithClassSerializers(value) ?: throw SerializationException("no serializer covers ${value?.let { it::class.simpleName }}"))
        }

        override fun deserialize(decoder: Decoder): Any? = throw SerializationException("only encodes")
    }

    fun encode(key: SoilEntryKey, reply: Reply<*>): SoilEntryValue = when (reply) {
        is Reply.None -> SoilEntryValue.NoReply
        is Reply.Some -> encodeValue(key, reply.value)
    }

    private fun encodeValue(key: SoilEntryKey, value: Any?): SoilEntryValue {
        val registeredSerializerJson = registeredSerializers.serializerFor(key.id)?.let { serializer ->
            if (key.kind == SoilEntryKind.INFINITE_QUERY) encodeChunksWith(serializer, value) else encodeWith(serializer, value)
        }
        if (registeredSerializerJson != null) return jsonOrTextValue(SoilValueEncoding.REGISTERED_SERIALIZER, registeredSerializerJson)
        val classSerializersJson = encodeWithClassSerializers(value)
        if (classSerializersJson != null) return jsonOrTextValue(SoilValueEncoding.CLASS_SERIALIZERS, classSerializersJson)
        return textValue(SoilValueEncoding.TO_STRING, value.toString())
    }

    private fun jsonOrTextValue(encoding: SoilValueEncoding, element: JsonElement): SoilEntryValue {
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

    /** Null when [value] is not what [serializer] describes, or holds something nothing can encode. */
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
        is Double -> value.takeIf(Double::isFinite)?.let(::JsonPrimitive)
        is Float -> value.takeIf(Float::isFinite)?.let(::JsonPrimitive)
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

    /**
     * JSON keys are strings, so only a map whose keys read as one unambiguously is walked: keys of
     * plain values, no two of them alike as strings.
     */
    private fun encodeMap(map: Map<*, *>): JsonElement? {
        val fields = map.entries.associate { (mapKey, mapValue) ->
            val jsonKey = when (mapKey) {
                is String, is Number, is Boolean, is Char -> mapKey.toString()
                is Enum<*> -> mapKey.name
                else -> return null
            }
            jsonKey to (encodeWithClassSerializers(mapValue) ?: return null)
        }
        return if (fields.size == map.size) JsonObject(fields) else null
    }

    /**
     * A generic class is looked up with [typeArgumentSerializer] for each of its type arguments, so
     * that `Page<User>` is encoded as well as `User`. Classes without a serializer of their own fall
     * through to the built-in ones, such as `Unit`'s or `Duration`'s, and an enum to its name. That
     * lookup throws on Kotlin/Native for a generic class the first one missed.
     */
    @OptIn(InternalSerializationApi::class)
    private fun encodeWithOwnSerializer(value: Any): JsonElement? {
        val serializer = parametrizedSerializerOf(value) ?: runCatching { value::class.serializerOrNull() }.getOrNull()
        return when {
            serializer != null -> encodeWith(serializer, value)
            value is Enum<*> -> JsonPrimitive(value.name)
            else -> null
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    private fun parametrizedSerializerOf(value: Any): KSerializer<Any?>? {
        val typeArgumentCount = typeArgumentCountOf(value::class)
        if (typeArgumentCount == 0) return null
        return try {
            emptyModule.serializer(value::class, List(typeArgumentCount) { typeArgumentSerializer }, isNullable = false)
        } catch (_: SerializationException) {
            null
        }
    }
}
