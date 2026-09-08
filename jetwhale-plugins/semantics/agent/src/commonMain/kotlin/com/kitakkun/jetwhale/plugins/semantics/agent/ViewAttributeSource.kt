package com.kitakkun.jetwhale.plugins.semantics.agent

import com.kitakkun.jetwhale.plugins.semantics.protocol.ViewAttributeResult
import com.kitakkun.jetwhale.plugins.semantics.protocol.ViewAttributeSnapshot
import com.kitakkun.jetwhale.plugins.semantics.protocol.ViewAttributeValue
import com.kitakkun.jetwhale.plugins.semantics.protocol.type

/**
 * A node source whose nodes carry platform attributes that can be read, and some of which can be
 * written.
 *
 * This is a capability a source may or may not have, not part of [ComposeNodeSource]: a source that
 * has none — a composition read through its `SemanticsOwner` — simply does not implement it, and the
 * plugin answers "not supported" for its roots rather than every such source having to carry a pair
 * of no-op methods.
 */
interface ViewAttributeSource {
    /**
     * Every attribute the node addressed by [nodeId] exposes, or `null` when this source has no such
     * node — it left the window, or it is a Compose semantics node, which has no platform attributes.
     */
    suspend fun attributes(nodeId: Int): ViewAttributeSnapshot?

    /**
     * Writes one attribute, reporting the outcome rather than throwing: a rejected value, an unknown
     * [attributeId] and an app that refuses the change are all normal answers.
     */
    suspend fun setAttribute(nodeId: Int, attributeId: String, value: ViewAttributeValue): ViewAttributeResult
}

/**
 * Why [ViewAttributeSource] reports nothing for [nodeId].
 *
 * The sign of the id is what separates the two cases, and it is part of the transport contract: a
 * Compose semantics node's id is non-negative, a `View` node's is negative.
 */
internal fun noViewAttributesMessage(nodeId: Int): String = if (nodeId >= 0) {
    "node $nodeId is a Compose semantics node, which has no View attributes: a semantics node is a projection of composition state, " +
        "so a write to it would be undone by the next recomposition. Only View nodes (negative ids) have attributes."
} else {
    "unknown nodeId: $nodeId (the node may have left this window; capture the tree again)"
}

/** The `@SerialName` of [value]'s variant — how a message names the shape a caller sent or owes. */
internal fun variantNameOf(value: ViewAttributeValue): String = value.type.wireName

/** Names what the attribute takes and what arrived, so a caller can fix the call from the message alone. */
internal fun wrongVariantMessage(attributeId: String, expected: String, actual: ViewAttributeValue): String = "$attributeId expects a value of type '$expected', but a '${variantNameOf(actual)}' value was sent"

/**
 * A layout size as either of the two constants that are not lengths at all, or as the length it
 * otherwise is — one value either way, so an editor for it never changes shape.
 *
 * @param constantName `MATCH_PARENT` or `WRAP_CONTENT` when the raw size is one of them, `null`
 *   when it is a pixel figure.
 */
internal fun layoutSizeValue(constantName: String?, px: Float, density: Float): ViewAttributeValue.LayoutSizeValue = ViewAttributeValue.LayoutSizeValue(
    constant = constantName,
    px = px.takeIf { constantName == null },
    dp = (px / density).takeIf { constantName == null },
    constants = LAYOUT_SIZE_CONSTANTS,
)

/** The two `layout.width` / `layout.height` values that name a rule instead of a length. */
internal val LAYOUT_SIZE_CONSTANTS: List<String> = listOf("MATCH_PARENT", "WRAP_CONTENT")
