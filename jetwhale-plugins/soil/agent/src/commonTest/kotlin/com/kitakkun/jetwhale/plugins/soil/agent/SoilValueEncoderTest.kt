package com.kitakkun.jetwhale.plugins.soil.agent

import com.kitakkun.jetwhale.plugins.soil.protocol.MAX_SOIL_VALUE_TEXT_LENGTH
import com.kitakkun.jetwhale.plugins.soil.protocol.SoilEntryKind
import com.kitakkun.jetwhale.plugins.soil.protocol.SoilEntryValue
import com.kitakkun.jetwhale.plugins.soil.protocol.SoilValueEncoding
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import soil.query.InfiniteQueryId
import soil.query.QueryChunk
import soil.query.QueryId
import soil.query.core.Reply
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SoilValueEncoderTest {
    private val profile = Profile(id = 42, name = "Ada")

    @Test
    fun `a value with a serializer of its own is encoded with it`() {
        val value = SoilValueEncoder(SoilValueSerializers.None).encode(queryEntryKey("users/profile"), Reply.some(profile))

        assertEquals(json(SoilValueEncoding.CLASS_SERIALIZERS, """{"id":42,"name":"Ada"}"""), value)
    }

    @Test
    fun `a serializer registered for the namespace wins over the serializer of the value`() {
        val encoder = SoilValueEncoder(SoilValueSerializers { namespace("users/profile", ProfileNameSerializer) })

        val value = encoder.encode(queryEntryKey("users/profile"), Reply.some(profile))

        assertEquals(json(SoilValueEncoding.REGISTERED_SERIALIZER, "\"Ada\""), value)
    }

    @Test
    fun `a serializer registered for an id class covers every id of that class`() {
        val encoder = SoilValueEncoder(SoilValueSerializers { idClass<ProfileId>(ProfileNameSerializer) })

        val value = encoder.encode(SoilEntryKey(SoilEntryKind.QUERY, ProfileId(7)), Reply.some(profile))

        assertEquals(json(SoilValueEncoding.REGISTERED_SERIALIZER, "\"Ada\""), value)
    }

    @Test
    fun `a serializer registered for another type falls through to the serializer of the value`() {
        val encoder = SoilValueEncoder(SoilValueSerializers { namespace("users/profile", Int.serializer()) })

        val value = encoder.encode(queryEntryKey("users/profile"), Reply.some(profile))

        assertEquals(json(SoilValueEncoding.CLASS_SERIALIZERS, """{"id":42,"name":"Ada"}"""), value)
    }

    @Test
    fun `a registered serializer encodes the data of each chunk of an infinite query`() {
        val encoder = SoilValueEncoder(SoilValueSerializers { namespace("posts/feed", Page.serializer(Profile.serializer())) })
        val chunks = listOf(QueryChunk(data = Page(items = listOf(profile), next = 2), param = 1))

        val value = encoder.encode(SoilEntryKey(SoilEntryKind.INFINITE_QUERY, InfiniteQueryId<Page<Profile>, Int>("posts/feed")), Reply.some(chunks))

        assertEquals(json(SoilValueEncoding.REGISTERED_SERIALIZER, """[{"data":{"items":[{"id":42,"name":"Ada"}],"next":2},"param":1}]"""), value)
    }

    @Test
    fun `lists maps pairs and chunks are walked down to the serializers of their leaves`() {
        val value = SoilValueEncoder(SoilValueSerializers.None).encode(
            queryEntryKey("mixed"),
            Reply.some(mapOf("first" to Pair(profile, listOf(1, 2)), "chunk" to QueryChunk(data = setOf("a"), param = null))),
        )

        assertEquals(
            json(SoilValueEncoding.CLASS_SERIALIZERS, """{"first":{"first":{"id":42,"name":"Ada"},"second":[1,2]},"chunk":{"data":["a"],"param":null}}"""),
            value,
        )
    }

    @Test
    fun `a generic class is encoded with its own serializer and the values of its type arguments`() {
        val value = SoilValueEncoder(SoilValueSerializers.None).encode(queryEntryKey("posts/page"), Reply.some(Page(items = listOf(profile), next = null)))

        assertEquals(json(SoilValueEncoding.CLASS_SERIALIZERS, """{"items":[{"id":42,"name":"Ada"}],"next":null}"""), value)
    }

    @Test
    fun `a value holding a class without a serializer falls back to toString`() {
        val value = SoilValueEncoder(SoilValueSerializers.None).encode(queryEntryKey("posts/page"), Reply.some(Page(items = listOf(Receipt(7)), next = 2)))

        assertEquals(SoilEntryValue.Text(encoding = SoilValueEncoding.TO_STRING, text = "Page(items=[Receipt #7], next=2)", fullLength = 32), value)
    }

    @Test
    fun `builtin values and enums without a serializer of their own are encoded`() {
        val value = SoilValueEncoder(SoilValueSerializers.None).encode(queryEntryKey("mixed"), Reply.some(listOf(Unit, Tier.PRO)))

        assertEquals(json(SoilValueEncoding.CLASS_SERIALIZERS, """[{},"PRO"]"""), value)
    }

    @Test
    fun `a map with keys that are not plain values falls back to toString`() {
        val value = SoilValueEncoder(SoilValueSerializers.None).encode(queryEntryKey("by-profile"), Reply.some(mapOf(profile to 1)))

        assertEquals(SoilValueEncoding.TO_STRING, (value as SoilEntryValue.Text).encoding)
    }

    @Test
    fun `a value too long to send whole is cut and says how long it was`() {
        val longText = "x".repeat(MAX_SOIL_VALUE_TEXT_LENGTH + 10)

        val value = SoilValueEncoder(SoilValueSerializers.None).encode(queryEntryKey("long"), Reply.some(longText)) as SoilEntryValue.Text

        assertEquals(SoilValueEncoding.CLASS_SERIALIZERS, value.encoding)
        assertEquals(MAX_SOIL_VALUE_TEXT_LENGTH, value.text.length)
        assertEquals(MAX_SOIL_VALUE_TEXT_LENGTH + 12, value.fullLength)
        assertTrue(value.isTruncated)
    }

    @Test
    fun `an entry without a reply says so`() {
        assertEquals(SoilEntryValue.NoReply, SoilValueEncoder(SoilValueSerializers.None).encode(queryEntryKey("users/profile"), Reply.none<Profile>()))
    }

    private fun queryEntryKey(namespace: String) = SoilEntryKey(SoilEntryKind.QUERY, QueryId<Any>(namespace))

    private fun json(encoding: SoilValueEncoding, text: String) = SoilEntryValue.Json(encoding = encoding, json = Json.parseToJsonElement(text))
}

@Serializable
internal data class Profile(val id: Int, val name: String)

@Serializable
internal data class Page<T>(val items: List<T>, val next: Int?)

internal class ProfileId(id: Int) : QueryId<Profile>("users/profile", id)

internal class Receipt(private val number: Int) {
    override fun toString(): String = "Receipt #$number"
}

internal enum class Tier { FREE, PRO }

internal object ProfileNameSerializer : KSerializer<Profile> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("ProfileName", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Profile) = encoder.encodeString(value.name)

    override fun deserialize(decoder: Decoder): Profile = error("only encoded")
}
