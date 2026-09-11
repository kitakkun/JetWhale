package com.kitakkun.jetwhale.plugins.semantics.protocol

/**
 * Works out what a tap at a point actually reaches, from a captured tree alone.
 *
 * Both platforms dispatch a touch the same way: windows from the top down, and within a window the
 * last-drawn child first, deepest first, until something accepts it. A capture carries both orders —
 * [NodeTreeSnapshot.roots] is bottom window first, and children are in draw order — so the walk is
 * reproducible off the device, from one capture, with no tap to send and nothing to wait for.
 *
 * What a capture cannot see is consumption: a parent that swallows the gesture in
 * `onInterceptTouchEvent` or a `pointerInput` block, and an overlay that takes touches without
 * exposing any semantics. Both make the answer optimistic — a node can read as reachable that a
 * finger would not reach — never the other way round.
 */
object NodeHitTesting {
    /** Where a tap at a point ends up. Internal: what a caller needs from it is [nodeAt] and the flags on the nodes. */
    internal sealed interface TouchTarget {
        /** A node takes it. */
        data class Node(val ref: NodeRef) : TouchTarget

        /**
         * A touch-modal window takes it although nothing in that window accepts one at the point —
         * a dialog's scrim, which swallows every tap outside the dialog rather than passing it down.
         */
        data class Window(val rootId: String, val rootNode: NodeRef?) : TouchTarget

        /** Nothing takes it: the tap reaches the bottom of the stack unclaimed. */
        data object Nothing : TouchTarget
    }

    /**
     * The captured roots with [UiNode.isHittable] and [UiNode.obscuredBy] filled in.
     *
     * Runs where the capture is assembled, so every consumer reads one answer instead of each
     * recomputing its own.
     *
     * One walk of the tree per node that accepts touch, so the cost is the accepting nodes times the
     * tree — not the tree squared, since a label asks nothing. A screen has few of the former even
     * when it has many of the latter, which is what keeps this off the capture's critical path.
     */
    fun resolve(roots: List<ComposeRoot>): List<ComposeRoot> = roots.map { root ->
        root.copy(node = root.node?.resolveHits(root.rootId) { x, y -> targetAt(roots, x, y) })
    }

    /**
     * What a tap at ([screenX], [screenY]) reaches, or `null` when no node takes it.
     *
     * The question an agent has when it works out a coordinate for itself — from a screenshot, say —
     * rather than from a node's own bounds. [targetAt] separates the two ways of reaching `null`.
     */
    fun nodeAt(roots: List<ComposeRoot>, screenX: Float, screenY: Float): NodeRef? = (targetAt(roots, screenX, screenY) as? TouchTarget.Node)?.ref

    /**
     * Where a tap at ([screenX], [screenY]) ends up, distinguishing a window that swallows it from
     * nothing taking it at all — the difference between "a dialog is in the way" and "there is
     * nothing here", which is what [UiNode.obscuredBy] is filled from.
     */
    internal fun targetAt(roots: List<ComposeRoot>, screenX: Float, screenY: Float): TouchTarget {
        for (index in roots.indices.reversed()) {
            val root = roots[index]
            val rootNode = root.node ?: continue
            val insideWindow = rootNode.boundsInScreen.contains(screenX, screenY)
            if (!insideWindow && !root.isTouchModal) continue

            rootNode.topmostAt(screenX, screenY)?.let { return TouchTarget.Node(NodeRef(root.rootId, it.id)) }
            // Either way the search stops here: a window takes delivery of everything inside it,
            // and a touch-modal one takes what lands outside it as well. Neither passes the touch
            // to the window below. Landing inside a window that has nothing to accept it is not
            // the window swallowing the tap — it is nothing taking it, and the app reports as much.
            return when {
                insideWindow -> TouchTarget.Nothing
                else -> TouchTarget.Window(root.rootId, NodeRef(root.rootId, rootNode.id))
            }
        }
        return TouchTarget.Nothing
    }
}

/**
 * The node a tap at this point is dispatched to: the deepest, last-drawn node containing the point
 * that accepts touch input at all.
 *
 * Children are walked in reverse because the last drawn sits on top, and a child wins over its
 * parent — which is what makes a button inside a clickable row take the tap.
 *
 * Compose guarantees the order this relies on: its semantics children come from the layout's
 * z-sorted children, so `Modifier.zIndex` is already accounted for. An Android `ViewGroup` is
 * captured in child order instead, which is paint order until a view is raised by `elevation` or
 * `translationZ` — a raised sibling can therefore be missed as an obstruction.
 */
private fun UiNode.topmostAt(screenX: Float, screenY: Float): UiNode? {
    if (!isVisible || !boundsInScreen.contains(screenX, screenY)) return null
    for (index in children.indices.reversed()) {
        children[index].topmostAt(screenX, screenY)?.let { return it }
    }
    return takeIf { it.acceptsTouch }
}

private fun UiNode.resolveHits(rootId: String, winnerAt: (x: Float, y: Float) -> NodeHitTesting.TouchTarget): UiNode {
    val resolvedChildren = children.map { it.resolveHits(rootId, winnerAt) }

    // Only a node that accepts touch is asked about: for anything else there is no tap to obstruct,
    // and reporting every label as unhittable would bury the nodes where it matters.
    if (!acceptsTouch) return withHits(isHittable = true, obscuredBy = null, children = resolvedChildren)

    if (boundsInScreen.isEmpty) return withHits(isHittable = false, obscuredBy = null, children = resolvedChildren)

    val self = NodeRef(rootId, id)
    val centerX = boundsInScreen.centerX
    val centerY = boundsInScreen.centerY
    return when (val winner = winnerAt(centerX, centerY)) {
        is NodeHitTesting.TouchTarget.Node -> {
            val reached = winner.ref == self || (isScrollable && winner.ref == descendantWinnerAt(rootId, centerX, centerY))
            withHits(
                isHittable = reached,
                obscuredBy = winner.ref.takeUnless { reached },
                children = resolvedChildren,
            )
        }

        // Named rather than left blank: the window is what a caller looks at next.
        is NodeHitTesting.TouchTarget.Window -> withHits(isHittable = false, obscuredBy = winner.rootNode, children = resolvedChildren)

        NodeHitTesting.TouchTarget.Nothing -> withHits(isHittable = false, obscuredBy = null, children = resolvedChildren)
    }
}

/**
 * The node this subtree alone would dispatch a touch at the point to, or `null` when none of it
 * accepts one there. Equal to the window's winner exactly when that winner lies in this subtree.
 *
 * A scrollable asks this because a gesture is not consumed by the leaf alone: Compose delivers
 * pointer events to every pointer-input node from the root down to the hit leaf, and an Android
 * `ViewGroup` sees them first in `onInterceptTouchEvent`. A drag that starts on a row still scrolls
 * the list around it, so a descendant taking the touch obstructs nothing. A click is different: a
 * clickable descendant consumes the tap and the ancestor's click never fires.
 */
private fun UiNode.descendantWinnerAt(rootId: String, screenX: Float, screenY: Float): NodeRef? = topmostAt(screenX, screenY)?.let { NodeRef(rootId, it.id) }

private fun UiNode.withHits(isHittable: Boolean, obscuredBy: NodeRef?, children: List<UiNode>): UiNode = when (this) {
    is ComposeNode -> copy(isHittable = isHittable, obscuredBy = obscuredBy, children = children)
    is ViewNode -> copy(isHittable = isHittable, obscuredBy = obscuredBy, children = children)
}

/**
 * Whether a touch landing on this node stops here.
 *
 * Read from what the node advertises, which is all a capture has. A node that consumes touches
 * without saying so — a bare `pointerInput` overlay, a `View` with only an `OnTouchListener` — is
 * not counted, and is the blind spot this file is optimistic about.
 */
internal val UiNode.acceptsTouch: Boolean
    get() = isClickable ||
        isScrollable ||
        isEditable ||
        toggleableState != null ||
        actions.any { it == "OnLongClick" }

private fun NodeBounds.contains(x: Float, y: Float): Boolean = x >= left && x < right && y >= top && y < bottom
