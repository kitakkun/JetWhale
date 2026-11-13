package com.kitakkun.jetwhale.demo.shared

import com.kitakkun.jetwhale.plugins.soil.agent.SoilValueSerializers
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable
internal data class DemoProfile(val id: Int, val name: String, val fetchCount: Int)

@Serializable
internal data class DemoPost(val id: Int, val title: String)

@Serializable
internal data class DemoPostPage(val posts: List<DemoPost>, val page: Int)

@Serializable
internal data class DemoTick(val count: Int)

/** Generic: the Soil Inspector takes its type argument from the payload it holds. */
@Serializable
internal data class DemoEnvelope<T>(val payload: T, val servedBy: String)

/** Not `@Serializable`, so the Soil Inspector shows it with `toString()` unless a serializer is registered. */
internal class DemoReceipt(val number: Int, val total: String) {
    override fun toString(): String = "Receipt #$number, $total"
}

@Serializable
private class DemoReceiptSurrogate(val number: Int, val total: String)

private object DemoReceiptSerializer : KSerializer<DemoReceipt> {
    override val descriptor: SerialDescriptor = DemoReceiptSurrogate.serializer().descriptor

    override fun serialize(encoder: Encoder, value: DemoReceipt) {
        encoder.encodeSerializableValue(DemoReceiptSurrogate.serializer(), DemoReceiptSurrogate(number = value.number, total = value.total))
    }

    override fun deserialize(decoder: Decoder): DemoReceipt = decoder.decodeSerializableValue(DemoReceiptSurrogate.serializer()).let { DemoReceipt(number = it.number, total = it.total) }
}

/** The serializers the demo hands the Soil Inspector for values it cannot encode by itself. */
internal val demoSoilValueSerializers: SoilValueSerializers = SoilValueSerializers {
    namespace(DemoReceiptQueryKey.REGISTERED_NAMESPACE, DemoReceiptSerializer)
}
