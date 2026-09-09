package com.kitakkun.jetwhale.plugins.semantics.host

import com.kitakkun.jetwhale.plugins.semantics.protocol.ComposeNode
import com.kitakkun.jetwhale.plugins.semantics.protocol.ComposeRoot
import com.kitakkun.jetwhale.plugins.semantics.protocol.NodeBounds
import com.kitakkun.jetwhale.plugins.semantics.protocol.NodeRef
import com.kitakkun.jetwhale.plugins.semantics.protocol.NodeTreeCaptureOptions
import com.kitakkun.jetwhale.plugins.semantics.protocol.NodeTreeSnapshot
import com.kitakkun.jetwhale.plugins.semantics.protocol.UiNode
import com.kitakkun.jetwhale.plugins.semantics.protocol.ViewNode

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
    isHittable: Boolean = true,
    obscuredBy: NodeRef? = null,
    bounds: NodeBounds = NodeBounds(0f, 0f, 100f, 40f),
    children: List<UiNode> = emptyList(),
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
    isHittable = isHittable,
    obscuredBy = obscuredBy,
    children = children,
)

/** An Android View node, as an Android capture reports one: a negative id and a class instead of a role. */
internal fun viewNode(
    id: Int,
    viewClass: String,
    resourceId: String? = null,
    text: String? = null,
    contentDescription: String? = null,
    actions: List<String> = emptyList(),
    isClickable: Boolean = false,
    bounds: NodeBounds = NodeBounds(0f, 0f, 100f, 40f),
    children: List<UiNode> = emptyList(),
): ViewNode = ViewNode(
    id = id,
    viewClass = viewClass,
    resourceId = resourceId,
    text = text,
    contentDescription = contentDescription,
    bounds = bounds,
    boundsInScreen = bounds,
    actions = actions,
    isClickable = isClickable,
    children = children,
)

internal fun root(
    rootId: String,
    label: String = rootId,
    node: UiNode?,
    isTouchModal: Boolean = false,
): ComposeRoot = ComposeRoot(
    rootId = rootId,
    label = label,
    density = 2f,
    windowOffsetX = 0f,
    windowOffsetY = 0f,
    isTouchModal = isTouchModal,
    node = node,
)

internal fun snapshot(vararg roots: ComposeRoot): NodeTreeSnapshot = NodeTreeSnapshot(
    capturedAtMs = 0,
    captureDurationMs = 1,
    options = NodeTreeCaptureOptions(),
    roots = roots.toList(),
)
