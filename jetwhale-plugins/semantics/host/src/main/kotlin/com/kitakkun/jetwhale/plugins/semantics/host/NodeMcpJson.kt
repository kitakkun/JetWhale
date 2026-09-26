package com.kitakkun.jetwhale.plugins.semantics.host

import com.kitakkun.jetwhale.plugins.semantics.protocol.AppleNode
import com.kitakkun.jetwhale.plugins.semantics.protocol.ComposeNode
import com.kitakkun.jetwhale.plugins.semantics.protocol.ComposeRoot
import com.kitakkun.jetwhale.plugins.semantics.protocol.NodeAction
import com.kitakkun.jetwhale.plugins.semantics.protocol.NodeBounds
import com.kitakkun.jetwhale.plugins.semantics.protocol.NodeTreeSnapshot
import com.kitakkun.jetwhale.plugins.semantics.protocol.UiNode
import com.kitakkun.jetwhale.plugins.semantics.protocol.ViewNode
import com.kitakkun.jetwhale.plugins.semantics.protocol.advertisedAs
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import kotlin.math.roundToInt

/**
 * Renders a snapshot for an AI agent.
 *
 * Deliberately not the transport model verbatim: absent and default-valued properties are dropped
 * so a large tree stays readable, and each node carries a ready-made `tap` point, because the whole
 * reason to read this instead of `adb shell uiautomator dump` is to act on it immediately.
 * Coordinates are screen pixels, or points on iOS — each node says which in `unit`, since a flat
 * `findNodes` result does not carry its root's density.
 */
internal fun NodeTreeSnapshot.toMcpJson(): JsonObject = buildJsonObject {
    put("capturedAtMs", capturedAtMs)
    put("captureDurationMs", captureDurationMs)
    put("merged", options.merged)
    put("roots", JsonArray(roots.map { it.toMcpJson() }))
    if (warnings.isNotEmpty()) put("warnings", JsonArray(warnings.map { JsonPrimitive(it) }))
}

internal fun ComposeRoot.toMcpJson(): JsonObject = buildJsonObject {
    put("rootId", rootId)
    put("label", label)
    put("density", density)
    // Where this root's window sits on screen. Zero for a full-screen activity; non-zero for a
    // dialog, a popup, or a split-screen window — which is exactly when node coordinates would
    // drift if they were reported window-relative, so it is worth being able to see it.
    putJsonObject("windowOffset") {
        put("x", windowOffsetX.roundToInt())
        put("y", windowOffsetY.roundToInt())
    }
    node?.let { put("node", it.toMcpJson()) }
}

/**
 * @param rootId when set, added to the node so a flat result stays addressable by
 *   `performNodeAction` without the caller having to track which root it came from.
 */
internal fun UiNode.toMcpJson(rootId: String? = null, includeChildren: Boolean = true): JsonObject = buildJsonObject {
    put("id", id)
    rootId?.let { put("rootId", it) }
    putKindFields(this@toMcpJson)
    text?.let { put("text", it) }
    editableText?.let { put("editableText", it) }
    contentDescription?.let { put("contentDescription", it) }
    toggleableState?.let { put("toggleableState", it) }

    // Only the surprising side of each flag is emitted: an enabled, visible, unfocused node is the
    // norm, and spelling that out on every node would triple the payload for no information.
    if (isClickable) put("clickable", true)
    if (!isEnabled) put("enabled", false)
    if (isFocused) put("focused", true)
    if (isSelected) put("selected", true)
    if (isEditable) put("editable", true)
    if (isScrollable) put("scrollable", true)
    if (!isVisible) put("visible", false)
    // Something to operate that cannot be operated is the surprising case; the flags below say why.
    if (isInteractive && !isOperable) put("operable", false)
    // The reason a caller reaches for coordinates at all: the touch would land somewhere else.
    if (!isHittable) {
        put("hittable", false)
        obscuredBy?.let { obstruction ->
            putJsonObject("obscuredBy") {
                put("rootId", obstruction.rootId)
                put("id", obstruction.nodeId)
            }
        }
    }

    val performable = performableActions()
    if (performable.isNotEmpty()) put("actions", JsonArray(performable.map { JsonPrimitive(it.name) }))

    if (this@toMcpJson !is AppleNode) put("unit", "px")
    putJsonObject("bounds") {
        put("left", boundsInScreen.left.roundToInt())
        put("top", boundsInScreen.top.roundToInt())
        put("right", boundsInScreen.right.roundToInt())
        put("bottom", boundsInScreen.bottom.roundToInt())
    }
    if (!boundsInScreen.isEmpty) {
        putJsonObject("tap") {
            put("x", boundsInScreen.centerX.roundToInt())
            put("y", boundsInScreen.centerY.roundToInt())
        }
    }

    if (includeChildren && children.isNotEmpty()) {
        put("children", JsonArray(children.map { it.toMcpJson(includeChildren = true) }))
    }
}

// Only the surprising kinds are emitted: most of a tree is Compose, and a View or an iOS node is the
// one a caller has to read differently — negative id, a class instead of a role.
private fun JsonObjectBuilder.putKindFields(node: UiNode) {
    when (node) {
        is ViewNode -> {
            put("kind", "View")
            put("viewClass", node.viewClass)
            node.resourceId?.let { put("resourceId", it) }
        }

        is AppleNode -> {
            put("kind", "Apple")
            put("unit", "pt")
            put("className", node.className)
            node.accessibilityIdentifier?.let { put("accessibilityIdentifier", it) }
            node.accessibilityValue?.let { put("accessibilityValue", it) }
            if (node.traits.isNotEmpty()) put("traits", JsonArray(node.traits.map { JsonPrimitive(it) }))
        }

        is ComposeNode -> {
            node.role?.let { put("role", it) }
            node.testTag?.let { put("testTag", it) }
            node.stateDescription?.let { put("stateDescription", it) }
        }
    }
}

/**
 * The actions this node exposes, named as performNodeAction takes them. The node itself carries the
 * platform's names (OnClick, SetTextSubstitution, …), which differ for some actions and include ones
 * nothing can perform.
 */
internal fun UiNode.performableActions(): List<NodeAction> = NodeAction.entries.filter { it.advertisedAs in actions }

internal fun NodeBounds.formatted(): String = "(${left.roundToInt()}, ${top.roundToInt()}) ${width.roundToInt()}×${height.roundToInt()}"
