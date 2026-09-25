package com.kitakkun.jetwhale.plugins.semantics.host

import com.kitakkun.jetwhale.plugins.semantics.protocol.AppleNode
import com.kitakkun.jetwhale.plugins.semantics.protocol.ComposeNode
import com.kitakkun.jetwhale.plugins.semantics.protocol.NodeTreeSnapshot
import com.kitakkun.jetwhale.plugins.semantics.protocol.UiNode
import com.kitakkun.jetwhale.plugins.semantics.protocol.ViewNode
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive
import kotlin.math.roundToInt

/**
 * How a tool returns nodes: the JSON every caller gets by default, or the compact outline. The
 * serial names are what the tool's schema advertises and what a caller passes.
 */
@Serializable
internal enum class NodeOutputFormat {
    @SerialName("json")
    Json,

    @SerialName("text")
    Text,
}

/**
 * Renders a snapshot as an indented outline, one line per node, for an agent that reads the tree
 * rather than parses it. It carries what [toMcpJson] carries minus the bounds, in a fraction of the
 * tokens: the same fields are dropped when absent or default, and keys are written once per value
 * instead of once per node.
 *
 * ```
 * root compose-root-1f2e "MainActivity" unit=px density=2.0
 * - node #1
 *   - Button #7 "Send" tag=send-button [clickable] actions=OnClick tap=60,40
 * ```
 */
internal fun NodeTreeSnapshot.toMcpText(): String = buildString {
    warnings.forEach { appendLine("! $it") }
    roots.forEach { root ->
        append("root ${root.rootId} ${quoted(root.label)} unit=${if (root.node is AppleNode) "pt" else "px"} density=${root.density}")
        if (root.windowOffsetX != 0f || root.windowOffsetY != 0f) append(" offset=${root.windowOffsetX.roundToInt()},${root.windowOffsetY.roundToInt()}")
        appendLine()
        root.node?.let { appendOutline(it, depth = 1) }
    }
}.trimEnd()

private fun StringBuilder.appendOutline(node: UiNode, depth: Int) {
    appendLine("  ".repeat(depth - 1) + node.toMcpTextLine(rootId = null))
    node.children.forEach { appendOutline(it, depth + 1) }
}

/**
 * One node as a line of the outline: `- <role or class> #<id>`, then only what is present.
 *
 * @param rootId when set, written after the id so a flat result stays addressable by
 *   `performNodeAction`, as in [toMcpJson].
 */
internal fun UiNode.toMcpTextLine(rootId: String?): String = buildList {
    add("-")
    add(
        when (this@toMcpTextLine) {
            is ViewNode -> viewClass.substringAfterLast('.')
            is AppleNode -> className
            is ComposeNode -> role ?: "node"
        },
    )
    add("#$id")
    rootId?.let { add("root=$it") }
    text?.let { add(quoted(it)) }
    contentDescription?.let { add("desc=${quoted(it)}") }
    editableText?.let { add("input=${quoted(it)}") }
    toggleableState?.let { add("toggle=$it") }
    when (this@toMcpTextLine) {
        is ViewNode -> resourceId?.let { add("resId=$it") }

        is AppleNode -> {
            accessibilityIdentifier?.let { add("axId=$it") }
            accessibilityValue?.let { add("value=${quoted(it)}") }
            if (traits.isNotEmpty()) add("traits=${traits.joinToString(",")}")
        }

        is ComposeNode -> {
            testTag?.let { add("tag=$it") }
            stateDescription?.let { add("state=${quoted(it)}") }
        }
    }
    addAll(flags())
    obscuredBy?.let { add("obscuredBy=${it.rootId}#${it.nodeId}") }
    if (actions.isNotEmpty()) add("actions=${actions.joinToString(",")}")
    if (!boundsInScreen.isEmpty) add("tap=${boundsInScreen.centerX.roundToInt()},${boundsInScreen.centerY.roundToInt()}")
}.joinToString(" ")

/** The same surprising-side-only flags [toMcpJson] emits, as bracketed words. */
private fun UiNode.flags(): List<String> = buildList {
    if (isClickable) add("[clickable]")
    if (!isEnabled) add("[disabled]")
    if (isFocused) add("[focused]")
    if (isSelected) add("[selected]")
    if (isEditable) add("[editable]")
    if (isScrollable) add("[scrollable]")
    if (!isVisible) add("[invisible]")
    if (isInteractive && !isOperable) add("[inoperable]")
    if (!isHittable) add("[unhittable]")
}

/** [value] as a JSON string literal, so quotes and line breaks in app text cannot break the line. */
private fun quoted(value: String): String = JsonPrimitive(value).toString()
