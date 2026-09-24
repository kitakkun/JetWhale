package com.kitakkun.jetwhale.plugins.semantics.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NodeHitTestingTest {
    @Test
    fun `a button with nothing over it takes its own tap`() {
        val roots = NodeHitTesting.resolve(listOf(root(button(id = 1, at = rect(left = 0f, top = 0f, right = 100f, bottom = 50f)))))

        val button = roots.node(1)
        assertTrue(button.isHittable)
        assertNull(button.obscuredBy)
    }

    @Test
    fun `a later sibling covering the button takes the tap instead`() {
        val roots = NodeHitTesting.resolve(
            listOf(
                root(
                    button(id = 1, at = rect(left = 0f, top = 0f, right = 100f, bottom = 50f)),
                    button(id = 2, at = rect(left = 0f, top = 0f, right = 100f, bottom = 50f)),
                ),
            ),
        )

        assertEquals(false, roots.node(1).isHittable)
        assertEquals(NodeRef(ROOT_ID, 2), roots.node(1).obscuredBy)
        assertTrue(roots.node(2).isHittable, "the covering node is reachable itself")
    }

    @Test
    fun `an earlier sibling drawn under it does not obstruct`() {
        val roots = NodeHitTesting.resolve(
            listOf(
                root(
                    button(id = 1, at = rect(left = 0f, top = 0f, right = 100f, bottom = 50f)),
                    button(id = 2, at = rect(left = 200f, top = 0f, right = 300f, bottom = 50f)),
                ),
            ),
        )

        assertTrue(roots.node(1).isHittable)
        assertTrue(roots.node(2).isHittable)
    }

    @Test
    fun `a child takes the tap from the clickable row it sits in`() {
        val roots = NodeHitTesting.resolve(
            listOf(
                root(
                    button(
                        id = 1,
                        at = rect(left = 0f, top = 0f, right = 100f, bottom = 50f),
                        children = listOf(button(id = 2, at = rect(left = 0f, top = 0f, right = 100f, bottom = 50f))),
                    ),
                ),
            ),
        )

        assertEquals(NodeRef(ROOT_ID, 2), roots.node(1).obscuredBy, "the deepest node at the point wins")
        assertTrue(roots.node(2).isHittable)
    }

    @Test
    fun `a list is not obstructed by the row its center lands on`() {
        val roots = NodeHitTesting.resolve(
            listOf(
                root(
                    list(
                        id = 1,
                        at = rect(left = 0f, top = 0f, right = 100f, bottom = 200f),
                        children = listOf(button(id = 2, at = rect(left = 0f, top = 80f, right = 100f, bottom = 120f))),
                    ),
                ),
            ),
        )

        assertTrue(roots.node(1).isHittable, "a drag starting on the row still scrolls the list")
        assertNull(roots.node(1).obscuredBy)
        assertTrue(roots.node(2).isHittable)
    }

    @Test
    fun `a list that is also clickable reads reachable while its row takes the tap`() {
        val roots = NodeHitTesting.resolve(
            listOf(
                root(
                    list(
                        id = 1,
                        at = rect(left = 0f, top = 0f, right = 100f, bottom = 200f),
                        children = listOf(button(id = 2, at = rect(left = 0f, top = 80f, right = 100f, bottom = 120f))),
                        isClickable = true,
                    ),
                ),
            ),
        )

        assertTrue(roots.node(1).isHittable, "one flag answers for every gesture the node accepts, and the drag gets through")
        assertNull(roots.node(1).obscuredBy)
    }

    @Test
    fun `a list is still obstructed by a sibling drawn over its center`() {
        val roots = NodeHitTesting.resolve(
            listOf(
                root(
                    list(
                        id = 1,
                        at = rect(left = 0f, top = 0f, right = 100f, bottom = 200f),
                        children = listOf(button(id = 2, at = rect(left = 0f, top = 80f, right = 100f, bottom = 120f))),
                    ),
                    button(id = 3, at = rect(left = 0f, top = 80f, right = 100f, bottom = 120f)),
                ),
            ),
        )

        assertEquals(false, roots.node(1).isHittable)
        assertEquals(NodeRef(ROOT_ID, 3), roots.node(1).obscuredBy)
    }

    @Test
    fun `a node clipped out of its scroll container has no area to tap`() {
        val roots = NodeHitTesting.resolve(
            listOf(root(button(id = 1, at = rect(left = 0f, top = 0f, right = 100f, bottom = 50f), inScreen = rect(left = 0f, top = 0f, right = 0f, bottom = 0f)))),
        )

        assertEquals(false, roots.node(1).isHittable)
        assertNull(roots.node(1).obscuredBy, "nothing takes the tap: there is nowhere to aim it")
    }

    @Test
    fun `nothing in a window below a touch-modal dialog can be tapped`() {
        val roots = NodeHitTesting.resolve(
            listOf(
                root(button(id = 1, at = rect(left = 0f, top = 0f, right = 100f, bottom = 50f))),
                root(
                    button(id = 2, at = rect(left = 0f, top = 500f, right = 100f, bottom = 550f)),
                    rootId = "dialog",
                    isTouchModal = true,
                    bounds = rect(left = 0f, top = 480f, right = 200f, bottom = 600f),
                ),
            ),
        )

        val behind = roots.first().requiredNode.children.single()
        assertEquals(false, behind.isHittable, "a dialog takes the taps that land outside it too")
        assertEquals(NodeRef("dialog", 0), behind.obscuredBy, "the dialog window itself is what takes the tap")
        assertTrue(roots.last().requiredNode.children.single().isHittable)
    }

    @Test
    fun `a window above covers what sits under it`() {
        val roots = NodeHitTesting.resolve(
            listOf(
                root(button(id = 1, at = rect(left = 0f, top = 0f, right = 100f, bottom = 50f))),
                root(button(id = 2, at = rect(left = 0f, top = 0f, right = 100f, bottom = 50f)), rootId = "popup", bounds = rect(left = 0f, top = 0f, right = 100f, bottom = 50f)),
            ),
        )

        assertEquals(NodeRef("popup", 2), roots.first().requiredNode.children.single().obscuredBy)
    }

    @Test
    fun `a label is never reported as obstructed`() {
        val roots = NodeHitTesting.resolve(
            listOf(
                root(
                    label(id = 1, at = rect(left = 0f, top = 0f, right = 100f, bottom = 50f)),
                    button(id = 2, at = rect(left = 0f, top = 0f, right = 100f, bottom = 50f)),
                ),
            ),
        )

        assertTrue(roots.node(1).isHittable, "a node that accepts no touch has no tap to lose")
    }

    @Test
    fun `nodeAt reports the topmost accepting node`() {
        val roots = listOf(
            root(button(id = 1, at = rect(left = 0f, top = 0f, right = 100f, bottom = 50f))),
            root(button(id = 2, at = rect(left = 0f, top = 0f, right = 40f, bottom = 20f)), rootId = "popup", bounds = rect(left = 0f, top = 0f, right = 40f, bottom = 20f)),
        )

        assertEquals(NodeRef("popup", 2), NodeHitTesting.nodeAt(roots, 10f, 10f))
        assertEquals(NodeRef(ROOT_ID, 1), NodeHitTesting.nodeAt(roots, 80f, 40f))
        assertNull(NodeHitTesting.nodeAt(roots, 500f, 500f))
    }

    @Test
    fun `nodeAt stops at a touch-modal window`() {
        val roots = listOf(
            root(button(id = 1, at = rect(left = 0f, top = 0f, right = 100f, bottom = 50f))),
            root(
                button(id = 2, at = rect(left = 0f, top = 500f, right = 100f, bottom = 550f)),
                rootId = "dialog",
                isTouchModal = true,
                bounds = rect(left = 0f, top = 480f, right = 200f, bottom = 600f),
            ),
        )

        assertNull(NodeHitTesting.nodeAt(roots, 10f, 10f), "the dialog takes it, and has nothing at the point")
    }

    @Test
    fun `a point inside a window that nothing accepts is nobody's rather than the window's`() {
        // Every window is touch-modal, the activity's included; a tap landing in it that no node
        // takes is not the window swallowing anything — the app takes no touch at all.
        val roots = listOf(root(button(id = 1, at = rect(left = 0f, top = 0f, right = 100f, bottom = 50f)), isTouchModal = true))

        assertEquals(NodeHitTesting.TouchTarget.Nothing, NodeHitTesting.targetAt(roots, 500f, 500f))
    }

    @Test
    fun `a window swallowing the tap is told apart from nothing taking it`() {
        val withDialog = listOf(
            root(button(id = 1, at = rect(left = 0f, top = 0f, right = 100f, bottom = 50f))),
            root(
                button(id = 2, at = rect(left = 0f, top = 500f, right = 100f, bottom = 550f)),
                rootId = "dialog",
                isTouchModal = true,
                bounds = rect(left = 0f, top = 480f, right = 200f, bottom = 600f),
            ),
        )

        assertEquals(
            NodeHitTesting.TouchTarget.Window("dialog", NodeRef("dialog", 0)),
            NodeHitTesting.targetAt(withDialog, 10f, 10f),
        )
        assertEquals(
            NodeHitTesting.TouchTarget.Nothing,
            NodeHitTesting.targetAt(withDialog.take(1), 500f, 500f),
        )
    }
}

private const val ROOT_ID = "window"

private fun List<ComposeRoot>.node(id: Int): UiNode = requireNotNull(first { it.rootId == ROOT_ID }.requiredNode.find(id)) { "no node with id $id in the captured tree" }

private val ComposeRoot.requiredNode: UiNode get() = requireNotNull(node) { "root $rootId was captured without a node" }

private fun UiNode.find(id: Int): UiNode? = takeIf { it.id == id } ?: children.firstNotNullOfOrNull { it.find(id) }

private fun rect(left: Float, top: Float, right: Float, bottom: Float) = NodeBounds(left = left, top = top, right = right, bottom = bottom)

private fun root(
    vararg children: UiNode,
    rootId: String = ROOT_ID,
    isTouchModal: Boolean = false,
    bounds: NodeBounds = rect(left = 0f, top = 0f, right = 1000f, bottom = 1000f),
) = ComposeRoot(
    rootId = rootId,
    label = rootId,
    density = 1f,
    windowOffsetX = 0f,
    windowOffsetY = 0f,
    isTouchModal = isTouchModal,
    node = ComposeNode(
        id = 0,
        bounds = bounds,
        boundsInScreen = bounds,
        children = children.toList(),
    ),
)

private fun button(
    id: Int,
    at: NodeBounds,
    inScreen: NodeBounds = at,
    children: List<UiNode> = emptyList(),
) = ComposeNode(
    id = id,
    bounds = at,
    boundsInScreen = inScreen,
    isClickable = true,
    actions = listOf("OnClick"),
    children = children,
)

private fun list(id: Int, at: NodeBounds, children: List<UiNode>, isClickable: Boolean = false) = ComposeNode(
    id = id,
    bounds = at,
    boundsInScreen = at,
    isClickable = isClickable,
    isScrollable = true,
    actions = listOfNotNull("ScrollBy", "OnClick".takeIf { isClickable }),
    children = children,
)

private fun label(id: Int, at: NodeBounds) = ComposeNode(id = id, bounds = at, boundsInScreen = at, text = "label")
