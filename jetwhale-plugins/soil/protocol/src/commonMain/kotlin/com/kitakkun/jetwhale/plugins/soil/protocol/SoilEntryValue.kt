package com.kitakkun.jetwhale.plugins.soil.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/** The longest [SoilEntryValue.Text] the agent sends; a longer value is cut there. */
const val MAX_SOIL_VALUE_TEXT_LENGTH: Int = 256 * 1024

/** Reply to [GetSoilEntryValue]: the entry's last reply, encoded as far as the app's types allow. */
@Serializable
sealed interface SoilEntryValue {
    /** The entry has no reply yet. */
    @SerialName("soil/value/no_reply")
    @Serializable
    data object NoReply : SoilEntryValue

    /** No entry has the requested handle any more. */
    @SerialName("soil/value/entry_gone")
    @Serializable
    data object EntryGone : SoilEntryValue

    /** The reply as JSON, by [encoding]. */
    @SerialName("soil/value/json")
    @Serializable
    data class Json(
        val encoding: SoilValueEncoding,
        val json: JsonElement,
    ) : SoilEntryValue

    /**
     * The reply as text: its `toString()`, or JSON too long to send whole. [fullLength] is the length
     * of the whole text, so [text] was cut at [MAX_SOIL_VALUE_TEXT_LENGTH] when it is longer.
     */
    @SerialName("soil/value/text")
    @Serializable
    data class Text(
        val encoding: SoilValueEncoding,
        val text: String,
        val fullLength: Int,
    ) : SoilEntryValue {
        val isTruncated: Boolean get() = text.length < fullLength
    }
}

/** How the agent turned a reply into JSON or text, from most to least faithful. */
@Serializable
enum class SoilValueEncoding {
    /** With the serializer the app registered for the entry's namespace or id class. */
    REGISTERED_SERIALIZER,

    /**
     * With the serializers of the value's own classes, walking into lists, sets, arrays, maps,
     * pairs, triples and infinite-query chunks. A generic class has no serializer to find.
     */
    CLASS_SERIALIZERS,

    /** With `toString()`, because neither of the above covered the value. */
    TO_STRING,
}
