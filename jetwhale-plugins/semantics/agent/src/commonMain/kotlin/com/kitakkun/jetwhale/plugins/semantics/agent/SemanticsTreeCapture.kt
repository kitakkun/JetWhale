package com.kitakkun.jetwhale.plugins.semantics.agent

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.AccessibilityAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import com.kitakkun.jetwhale.plugins.semantics.protocol.ComposeNode
import com.kitakkun.jetwhale.plugins.semantics.protocol.NodeBounds
import com.kitakkun.jetwhale.plugins.semantics.protocol.NodeTreeCaptureOptions

/**
 * Converts a Compose semantics subtree into the transport model.
 *
 * Returns `null` when the node is filtered out — either it is not laid out and [NodeTreeCaptureOptions.includeInvisible]
 * is off, or it sits past [NodeTreeCaptureOptions.maxDepth]. A node whose own bounds are empty is
 * still kept when a descendant survived: dropping it would detach that descendant from the tree.
 */
internal fun SemanticsNode.toComposeNode(
    options: NodeTreeCaptureOptions,
    windowOffsetX: Float,
    windowOffsetY: Float,
    depth: Int,
): ComposeNode? {
    val maxDepth = options.maxDepth
    val children = if (maxDepth != null && depth >= maxDepth) {
        emptyList()
    } else {
        children.mapNotNull { it.toComposeNode(options, windowOffsetX, windowOffsetY, depth + 1) }
    }

    // Visibility is decided on the sanitized bounds, not the raw ones: a NaN coordinate makes
    // every comparison false, so a raw-bounds check would call the node visible while the bounds
    // it reports are the zeroed fallback.
    val bounds = boundsInRoot.toNodeBounds()
    val visible = layoutInfo.isPlaced && !bounds.isEmpty
    if (!visible && !options.includeInvisible && children.isEmpty()) return null

    val config = config
    val editableText = config.getOrNull(SemanticsProperties.EditableText)?.text

    return ComposeNode(
        id = id,
        role = config.getOrNull(SemanticsProperties.Role)?.toString(),
        text = config.getOrNull(SemanticsProperties.Text)
            ?.joinToString(separator = " ") { it.text }
            ?.takeIf { it.isNotEmpty() },
        editableText = editableText,
        contentDescription = config.getOrNull(SemanticsProperties.ContentDescription)
            ?.joinToString(separator = " ")
            ?.takeIf { it.isNotEmpty() },
        testTag = config.getOrNull(SemanticsProperties.TestTag),
        stateDescription = config.getOrNull(SemanticsProperties.StateDescription),
        toggleableState = config.getOrNull(SemanticsProperties.ToggleableState)?.toString(),
        bounds = bounds,
        boundsInScreen = boundsInWindow.toNodeBounds(offsetX = windowOffsetX, offsetY = windowOffsetY),
        actions = config.mapNotNull { (key, value) -> key.name.takeIf { value is AccessibilityAction<*> } },
        isEnabled = config.getOrNull(SemanticsProperties.Disabled) == null,
        isClickable = config.getOrNull(SemanticsActions.OnClick) != null,
        isFocused = config.getOrNull(SemanticsProperties.Focused) == true,
        isSelected = config.getOrNull(SemanticsProperties.Selected) == true,
        isEditable = editableText != null || config.getOrNull(SemanticsActions.SetText) != null,
        isScrollable = config.getOrNull(SemanticsProperties.VerticalScrollAxisRange) != null ||
            config.getOrNull(SemanticsProperties.HorizontalScrollAxisRange) != null,
        isVisible = visible,
        children = children,
    )
}

private fun Rect.toNodeBounds(offsetX: Float = 0f, offsetY: Float = 0f): NodeBounds = NodeBounds(
    left = (left + offsetX).finiteOrZero(),
    top = (top + offsetY).finiteOrZero(),
    right = (right + offsetX).finiteOrZero(),
    bottom = (bottom + offsetY).finiteOrZero(),
)

/**
 * Compose reports an unresolved coordinate as `Offset.Unspecified`, i.e. `NaN`. JSON has no NaN, so
 * one such node would fail the encoding of the **whole** snapshot; reporting the node with empty
 * bounds instead costs the caller one unusable node rather than the entire tree.
 */
private fun Float.finiteOrZero(): Float = if (isFinite()) this else 0f
