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
    /**
     * The captured roots with [UiNode.isHittable] and [UiNode.obscuredBy] filled in.
     *
     * Runs where the capture is assembled, so every consumer reads one answer instead of each
     * recomputing its own.
     */
    fun resolve(roots: List<ComposeRoot>): List<ComposeRoot> = roots.map { root ->
        root.copy(node = root.node?.resolveHits(root.rootId) { x, y -> nodeAt(roots, x, y) })
    }

    /**
     * What a tap at ([screenX], [screenY]) reaches, or `null` when nothing there accepts touch
     * input.
     *
     * The question an agent has when it works out a coordinate for itself — from a screenshot, say —
     * rather than from a node's own bounds.
     */
    fun nodeAt(roots: List<ComposeRoot>, screenX: Float, screenY: Float): NodeRef? {
        for (index in roots.indices.reversed()) {
            val root = roots[index]
            root.node?.topmostAt(screenX, screenY)?.let { return NodeRef(root.rootId, it.id) }
            // A touch-modal window takes what lands outside it too, so the search stops at one
            // whether or not it had anything at the point.
            if (root.isTouchModal) return null
        }
        return null
    }
}

/**
 * The node a tap at this point is dispatched to: the deepest, last-drawn node containing the point
 * that accepts touch input at all.
 *
 * Children are walked in reverse because the last drawn sits on top, and a child wins over its
 * parent — which is what makes a button inside a clickable row take the tap.
 */
private fun UiNode.topmostAt(screenX: Float, screenY: Float): UiNode? {
    if (!isVisible || !boundsInScreen.contains(screenX, screenY)) return null
    for (index in children.indices.reversed()) {
        children[index].topmostAt(screenX, screenY)?.let { return it }
    }
    return takeIf { it.acceptsTouch }
}

private fun UiNode.resolveHits(rootId: String, winnerAt: (x: Float, y: Float) -> NodeRef?): UiNode {
    val resolvedChildren = children.map { it.resolveHits(rootId, winnerAt) }

    // Only a node that accepts touch is asked about: for anything else there is no tap to obstruct,
    // and reporting every label as unhittable would bury the nodes where it matters.
    if (!acceptsTouch) return withHits(isHittable = true, obscuredBy = null, children = resolvedChildren)

    // No area to aim at is its own kind of unreachable, and there is nothing to name as taking the
    // tap instead.
    if (boundsInScreen.isEmpty) return withHits(isHittable = false, obscuredBy = null, children = resolvedChildren)

    val self = NodeRef(rootId, id)
    val winner = winnerAt(boundsInScreen.centerX, boundsInScreen.centerY)
    return withHits(
        isHittable = winner == self,
        obscuredBy = winner?.takeIf { it != self },
        children = resolvedChildren,
    )
}

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
