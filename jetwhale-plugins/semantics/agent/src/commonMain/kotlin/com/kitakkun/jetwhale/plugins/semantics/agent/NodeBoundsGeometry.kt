package com.kitakkun.jetwhale.plugins.semantics.agent

import com.kitakkun.jetwhale.plugins.semantics.protocol.NodeBounds

internal fun NodeBounds.intersect(other: NodeBounds): NodeBounds {
    val left = maxOf(left, other.left)
    val top = maxOf(top, other.top)
    val right = minOf(right, other.right)
    val bottom = minOf(bottom, other.bottom)
    return if (right <= left || bottom <= top) {
        NodeBounds(left = 0f, top = 0f, right = 0f, bottom = 0f)
    } else {
        NodeBounds(left = left, top = top, right = right, bottom = bottom)
    }
}

internal fun NodeBounds.translated(offsetX: Float, offsetY: Float): NodeBounds = NodeBounds(
    left = left + offsetX,
    top = top + offsetY,
    right = right + offsetX,
    bottom = bottom + offsetY,
)
