package com.kitakkun.jetwhale.plugins.semantics.protocol

import com.kitakkun.jetwhale.protocol.messaging.JetWhaleRequest
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Reading and writing the platform attributes of one `ViewNode` — what an Android `View` exposes
// beyond the semantics every `UiNode` reports.
//
// Attributes travel on their own request rather than inside a `NodeTreeSnapshot`: a tree of two
// hundred nodes must not carry thirty attributes each, and a host only ever shows the attributes of
// the one node a user selected.
//
// A `ComposeNode` has none. A semantics node is a projection of composition state, so a write to it
// lasts until the next recomposition overwrites it — which is why a root read through its
// `SemanticsOwner` answers "not supported" instead.

/** One attribute's current value; the variant also says how a host should edit it. */
@Serializable
sealed interface ViewAttributeValue {
    @Serializable
    @SerialName("bool")
    data class BooleanValue(val value: Boolean) : ViewAttributeValue

    @Serializable
    @SerialName("int")
    data class IntValue(val value: Int) : ViewAttributeValue

    @Serializable
    @SerialName("float")
    data class FloatValue(val value: Float) : ViewAttributeValue

    @Serializable
    @SerialName("text")
    data class TextValue(val value: String) : ViewAttributeValue

    /** Straight ARGB, as `View` itself stores colors. */
    @Serializable
    @SerialName("color")
    data class ColorValue(val argb: Int) : ViewAttributeValue

    /**
     * A length in pixels; [dp] is the same length at the root's density, for reading. A write reads
     * [px] only, so a caller that has no density to hand may repeat the pixel figure in [dp].
     */
    @Serializable
    @SerialName("dimension")
    data class DimensionValue(val px: Float, val dp: Float) : ViewAttributeValue

    /** One of [options], and nothing else. */
    @Serializable
    @SerialName("enum")
    data class EnumValue(val value: String, val options: List<String>) : ViewAttributeValue

    /**
     * A `layout.width` / `layout.height`, which is either one of the constants that name a rule or a
     * length — and can be moved between the two.
     *
     * Both cases are one variant rather than an [EnumValue] and a [DimensionValue] taking turns,
     * because the variant is what a host picks an editor from: were the shape to follow the current
     * value, a size reading `WRAP_CONTENT` would offer no way to type a length, and a size reading
     * as a length no way to get back.
     *
     * @param constant one of [constants] when the size names a rule, `null` when it is a length.
     * @param px the length in pixels, `null` when [constant] is set. A write reads [px] only, so a
     *   caller that has no density to hand may repeat the pixel figure in [dp].
     * @param dp the same length at the root's density, for reading.
     * @param constants the rules this size accepts, whichever case it is in — so an editor can
     *   offer them while the size is a length.
     */
    @Serializable
    @SerialName("layoutSize")
    data class LayoutSizeValue(
        val constant: String?,
        val px: Float?,
        val dp: Float?,
        val constants: List<String>,
    ) : ViewAttributeValue
}

@Serializable
data class ViewAttribute(
    /** Stable identifier a [SetViewAttribute] names, e.g. `visibility`, `padding.left`. */
    val id: String,
    val label: String,
    /** Section a host groups this under: `State`, `Layout`, `Appearance`, `Text`, `Info`. */
    val group: String,
    val value: ViewAttributeValue,
    /** `false` when this view exposes the attribute but nothing can write it back. */
    val editable: Boolean,
)

@Serializable
data class ViewAttributeSnapshot(
    val rootId: String,
    val nodeId: Int,
    val viewClass: String,
    val attributes: List<ViewAttribute>,
)

/**
 * Reads every attribute the node addressed by [rootId]/[nodeId] exposes.
 *
 * Answered per node, on demand: a host asks when a selection changes, so a capture stays as small
 * as it is today.
 */
@SerialName("view/get_attributes")
@Serializable
data class GetViewAttributes(
    val rootId: String,
    val nodeId: Int,
) : JetWhaleRequest<ViewAttributeResponse>

/** Separate from the snapshot so a node that has gone away is an answer, not a transport failure. */
@Serializable
data class ViewAttributeResponse(
    val snapshot: ViewAttributeSnapshot?,
    val message: String? = null,
)

/**
 * Writes one attribute of one node.
 *
 * The write is temporary in the same way the Android Studio Layout Inspector's is: the app owns the
 * property, and a relayout, a rebind or the app writing it itself takes the value back.
 */
@SerialName("view/set_attribute")
@Serializable
data class SetViewAttribute(
    val rootId: String,
    val nodeId: Int,
    val attributeId: String,
    val value: ViewAttributeValue,
) : JetWhaleRequest<ViewAttributeResult>

@Serializable
data class ViewAttributeResult(
    val applied: Boolean,
    val message: String? = null,
    /** The attribute as it reads back after the write, so a host shows what actually took effect. */
    val attribute: ViewAttribute? = null,
)
