package com.kitakkun.jetwhale.plugins.semantics.agent

import android.content.res.Resources
import android.view.View
import android.view.ViewGroup
import android.widget.Checkable
import android.widget.EditText
import android.widget.TextView
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.node.InteroperableComposeUiNode
import androidx.compose.ui.platform.ViewRootForTest
import androidx.compose.ui.semantics.SemanticsNode
import com.kitakkun.jetwhale.plugins.semantics.protocol.NodeBounds
import com.kitakkun.jetwhale.plugins.semantics.protocol.NodeTreeCaptureOptions
import com.kitakkun.jetwhale.plugins.semantics.protocol.UiNode
import com.kitakkun.jetwhale.plugins.semantics.protocol.ViewNode

/**
 * Converts an Android `View` subtree into the transport model, descending into every composition it
 * hosts.
 *
 * This is what makes an Android capture one tree per window rather than one per composition: a
 * `ComposeView` sits inside a layout, and that layout — a Fragment's, an Activity's — is as much
 * part of what is on screen as the composition is.
 *
 * Returns `null` when the view is filtered out: it is invisible and
 * [NodeTreeCaptureOptions.includeInvisible] is off, or it sits past
 * [NodeTreeCaptureOptions.maxDepth]. A view kept only because a descendant survived stays in the
 * tree, exactly as on the Compose side, so the descendant does not get reparented.
 *
 * Must be called on the main thread.
 */
internal fun View.toViewNode(
    options: NodeTreeCaptureOptions,
    windowOffsetX: Float,
    windowOffsetY: Float,
    depth: Int,
): ViewNode? {
    val maxDepth = options.maxDepth
    val children = if (maxDepth != null && depth >= maxDepth) {
        emptyList()
    } else {
        childNodes(options, windowOffsetX, windowOffsetY, depth + 1)
    }

    val bounds = boundsInWindow()
    val visible = visibility == View.VISIBLE && isShown && !bounds.isEmpty
    if (!visible && !options.includeInvisible && children.isEmpty()) return null

    val editable = this as? EditText
    val label = this as? TextView

    return ViewNode(
        id = ViewNodeIds.idOf(this),
        viewClass = javaClass.name,
        resourceId = resourceEntryName(),
        text = label?.takeIf { editable == null }?.text?.toString()?.takeIf { it.isNotEmpty() },
        editableText = editable?.text?.toString(),
        contentDescription = contentDescription?.toString()?.takeIf { it.isNotEmpty() },
        toggleableState = (this as? Checkable)?.let { if (it.isChecked) "On" else "Off" },
        bounds = bounds,
        boundsInScreen = bounds.translated(windowOffsetX, windowOffsetY),
        actions = viewActionNames(),
        isEnabled = isEnabled,
        isClickable = isClickable,
        isFocused = isFocused,
        isSelected = isSelected,
        isEditable = editable != null,
        isScrollable = isScrollable(),
        isVisible = visible,
        children = children,
    )
}

/**
 * The composition a Compose-backed view hosts, or the plain `View` children of anything else.
 *
 * A view backing a composition is not descended into as a view: the interop views underneath it are
 * already reported under the semantics nodes that placed them, and Compose's own scaffolding around
 * them is internal machinery a caller has no way to act on.
 */
@OptIn(InternalComposeUiApi::class)
private fun View.childNodes(
    options: NodeTreeCaptureOptions,
    windowOffsetX: Float,
    windowOffsetY: Float,
    depth: Int,
): List<UiNode> = when {
    this is ViewRootForTest -> {
        val owner = semanticsOwner
        val semanticsRoot = if (options.merged) owner.rootSemanticsNode else owner.unmergedRootSemanticsNode
        val inWindow = IntArray(2).also(::getLocationInWindow)
        listOfNotNull(
            semanticsRoot.toComposeNode(
                options = options,
                windowOffsetX = windowOffsetX,
                windowOffsetY = windowOffsetY,
                rootOffset = Offset(inWindow[0].toFloat(), inWindow[1].toFloat()),
                depth = depth,
                interopChildren = { node, nodeDepth ->
                    node.interopViewNodes(options, windowOffsetX, windowOffsetY, nodeDepth + 1)
                },
            ),
        )
    }

    this is ViewGroup -> (0 until childCount).mapNotNull {
        getChildAt(it)?.toViewNode(options, windowOffsetX, windowOffsetY, depth)
    }

    else -> emptyList()
}

/**
 * The `View` an `AndroidView { }` placed at this semantics node, as a subtree.
 *
 * The link is the layout node's own `getInteropView()`. The views Compose wraps the content in are
 * internal to it and deliberately not looked up by name; if this ever came back `null` for content
 * that is on screen, the alternative route is matching the semantics node's `boundsInWindow`
 * against the candidate views' `getLocationInWindow`.
 */
@OptIn(InternalComposeUiApi::class)
private fun SemanticsNode.interopViewNodes(
    options: NodeTreeCaptureOptions,
    windowOffsetX: Float,
    windowOffsetY: Float,
    depth: Int,
): List<UiNode> {
    val interopView = (layoutInfo as? InteroperableComposeUiNode)?.getInteropView() ?: return emptyList()
    return listOfNotNull(interopView.toViewNode(options, windowOffsetX, windowOffsetY, depth))
}

/** The names this view's actions are advertised under — the semantics keys of their Compose counterparts. */
private fun View.viewActionNames(): List<String> = buildList {
    if (isClickable) add("OnClick")
    if (isLongClickable) add("OnLongClick")
    if (this@viewActionNames is EditText) {
        add("SetText")
        add("InsertTextAtCursor")
        add("PerformImeAction")
    }
    if (isScrollable()) add("ScrollBy")
    if (isFocusable) add("RequestFocus")
}

/**
 * A view scrolls when it has somewhere to scroll to in any direction. Asking the view itself covers
 * every scrolling container — `ScrollView`, `RecyclerView`, a custom one — without naming any.
 */
internal fun View.isScrollable(): Boolean = canScrollVertically(1) || canScrollVertically(-1) || canScrollHorizontally(1) || canScrollHorizontally(-1)

/**
 * The entry name of the view's `android:id` (`submit` for `@id/submit`), or `null` when it has none.
 *
 * A generated id — the kind `View.generateViewId()` hands out — has no entry to look up, so the
 * lookup is allowed to fail rather than being guarded by a check on the id's packing.
 */
private fun View.resourceEntryName(): String? {
    if (id == View.NO_ID) return null
    return try {
        resources?.getResourceEntryName(id)
    } catch (_: Resources.NotFoundException) {
        null
    }
}

/** Where the view sits in its window, in pixels — the same space Compose reports `boundsInWindow` in. */
private fun View.boundsInWindow(): NodeBounds {
    val location = IntArray(2).also(::getLocationInWindow)
    return NodeBounds(
        left = location[0].toFloat(),
        top = location[1].toFloat(),
        right = (location[0] + width).toFloat(),
        bottom = (location[1] + height).toFloat(),
    )
}

private fun NodeBounds.translated(offsetX: Float, offsetY: Float): NodeBounds = NodeBounds(
    left = left + offsetX,
    top = top + offsetY,
    right = right + offsetX,
    bottom = bottom + offsetY,
)
