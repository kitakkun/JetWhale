package com.kitakkun.jetwhale.plugins.semantics.protocol

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Reads a node whose JSON object names no [UiNode] subtype as a [ComposeNode].
 *
 * A tree can arrive from an agent older than the host reading it, and an agent that knows only one
 * kind of node writes objects naming no subtype at all. Falling back to [ComposeNode] decodes such a
 * tree as what it is instead of failing the whole snapshot; a subtype is chosen only when the object
 * says which one it is.
 *
 * Writing delegates to the sealed hierarchy's own serializer, so a node is encoded with whatever
 * discriminator the format is configured for and nothing here shapes the wire format.
 */
object UiNodeSerializer : KSerializer<UiNode> {
    private val sealed: KSerializer<UiNode> = UiNode.serializer()

    override val descriptor: SerialDescriptor get() = sealed.descriptor

    override fun serialize(encoder: Encoder, value: UiNode) {
        encoder.encodeSerializableValue(sealed, value)
    }

    override fun deserialize(decoder: Decoder): UiNode {
        val input = decoder as? JsonDecoder
            ?: throw SerializationException("a UiNode can only be read from JSON, which is what this protocol travels over")
        val element = input.decodeJsonElement()
        val discriminator = element.namedSubtype(input.json.classDiscriminator())
        val serializer: DeserializationStrategy<UiNode> = if (discriminator in subtypeNames) sealed else ComposeNode.serializer()
        return input.json.decodeFromJsonElement(serializer, element)
    }

    private fun JsonElement.namedSubtype(discriminator: String): String? = ((this as? JsonObject)?.get(discriminator) as? JsonPrimitive)?.takeIf { it.isString }?.content

    /**
     * The subtypes' serial names, read off the sealed descriptor rather than restated, so adding a
     * subtype cannot leave this behind.
     */
    private val subtypeNames: Set<String> by lazy {
        // A sealed serializer's descriptor holds the discriminator first and the subtypes second.
        val subtypes = descriptor.getElementDescriptor(1)
        (0 until subtypes.elementsCount).mapTo(mutableSetOf()) { subtypes.getElementName(it) }
    }
}

/** The key the format names a subtype under, so the fallback looks where this very format writes. */
private fun Json.classDiscriminator(): String = configuration.classDiscriminator
