package com.kitakkun.jetwhale.plugins.semantics.host

import com.kitakkun.jetwhale.plugins.semantics.protocol.AppleNode
import com.kitakkun.jetwhale.plugins.semantics.protocol.ComposeNode
import com.kitakkun.jetwhale.plugins.semantics.protocol.UiNode
import com.kitakkun.jetwhale.plugins.semantics.protocol.ViewNode

/** How a node should read in a list: its own label if it has one, otherwise its role or id. */
internal fun UiNode.displayLabel(): String {
    val label = ownLabel
    return when (this) {
        is AppleNode -> buildString {
            append(className.substringAfterLast('.'))
            accessibilityIdentifier?.let { append(" · $it") }
            label?.let { append(" · $it") }
        }

        is ViewNode -> buildString {
            append(viewClass.substringAfterLast('.'))
            resourceId?.let { append(" · @id/$it") }
            label?.let { append(" · $it") }
        }

        is ComposeNode -> {
            val role = role
            when {
                isLabeledById -> "#$id"
                role != null && label != null -> "$role · $label"
                else -> role ?: label.orEmpty()
            }
        }
    }
}

/** `true` when [displayLabel] falls back to the node's id, the node having no role or label of its own. */
internal val UiNode.isLabeledById: Boolean
    get() = this is ComposeNode && role == null && ownLabel == null

private val UiNode.ownLabel: String?
    get() = listOfNotNull(text, contentDescription, editableText, (this as? ComposeNode)?.testTag).firstOrNull()
