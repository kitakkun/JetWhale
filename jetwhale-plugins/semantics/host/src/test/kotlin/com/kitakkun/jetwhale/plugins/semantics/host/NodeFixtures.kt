package com.kitakkun.jetwhale.plugins.semantics.host

import com.kitakkun.jetwhale.plugins.semantics.protocol.ComposeNode
import com.kitakkun.jetwhale.plugins.semantics.protocol.ComposeRoot
import com.kitakkun.jetwhale.plugins.semantics.protocol.NodeBounds
import com.kitakkun.jetwhale.plugins.semantics.protocol.NodeTreeCaptureOptions
import com.kitakkun.jetwhale.plugins.semantics.protocol.NodeTreeSnapshot

internal fun node(
    id: Int,
    role: String? = null,
    text: String? = null,
    contentDescription: String? = null,
    testTag: String? = null,
    actions: List<String> = emptyList(),
    isClickable: Boolean = false,
    isEditable: Boolean = false,
    isScrollable: Boolean = false,
    isEnabled: Boolean = true,
    isVisible: Boolean = true,
    bounds: NodeBounds = NodeBounds(0f, 0f, 100f, 40f),
    children: List<ComposeNode> = emptyList(),
): ComposeNode = ComposeNode(
    id = id,
    role = role,
    text = text,
    contentDescription = contentDescription,
    testTag = testTag,
    bounds = bounds,
    boundsInScreen = bounds,
    actions = actions,
    isEnabled = isEnabled,
    isClickable = isClickable,
    isEditable = isEditable,
    isScrollable = isScrollable,
    isVisible = isVisible,
    children = children,
)

internal fun root(rootId: String, label: String = rootId, node: ComposeNode?): ComposeRoot = ComposeRoot(
    rootId = rootId,
    label = label,
    density = 2f,
    windowOffsetX = 0f,
    windowOffsetY = 0f,
    node = node,
)

internal fun snapshot(vararg roots: ComposeRoot): NodeTreeSnapshot = NodeTreeSnapshot(
    capturedAtMs = 0,
    captureDurationMs = 1,
    options = NodeTreeCaptureOptions(),
    roots = roots.toList(),
)
