package com.kitakkun.jetwhale.plugins.semantics.agent

import com.kitakkun.jetwhale.plugins.semantics.protocol.NodeBounds
import kotlinx.cinterop.CValue
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGRect

@OptIn(ExperimentalForeignApi::class)
internal fun CValue<CGRect>.toNodeBounds(): NodeBounds = useContents {
    NodeBounds(
        left = origin.x.toFloat(),
        top = origin.y.toFloat(),
        right = (origin.x + size.width).toFloat(),
        bottom = (origin.y + size.height).toFloat(),
    )
}

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
