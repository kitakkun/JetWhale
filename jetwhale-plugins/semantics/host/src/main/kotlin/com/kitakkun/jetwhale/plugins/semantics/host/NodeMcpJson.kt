package com.kitakkun.jetwhale.plugins.semantics.host

import com.kitakkun.jetwhale.plugins.semantics.protocol.ComposeNode
import com.kitakkun.jetwhale.plugins.semantics.protocol.ComposeRoot
import com.kitakkun.jetwhale.plugins.semantics.protocol.NodeBounds
import com.kitakkun.jetwhale.plugins.semantics.protocol.NodeTreeSnapshot
import com.kitakkun.jetwhale.plugins.semantics.protocol.UiNode
import com.kitakkun.jetwhale.plugins.semantics.protocol.ViewNode
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
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
 * Coordinates are screen pixels.
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
    // Only the surprising kind is emitted: most of a tree is Compose, and an Android View node is
    // the one a caller has to read differently — negative id, a class instead of a role.
    when (this@toMcpJson) {
        is ViewNode -> {
            put("kind", "View")
            put("viewClass", viewClass)
            resourceId?.let { put("resourceId", it) }
        }

        is ComposeNode -> {
            role?.let { put("role", it) }
            testTag?.let { put("testTag", it) }
            stateDescription?.let { put("stateDescription", it) }
        }
    }
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

    if (actions.isNotEmpty()) put("actions", JsonArray(actions.map { JsonPrimitive(it) }))

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

internal fun NodeBounds.formatted(): String = "(${left.roundToInt()}, ${top.roundToInt()}) ${width.roundToInt()}×${height.roundToInt()}"
