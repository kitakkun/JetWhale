package com.kitakkun.jetwhale.plugins.semantics.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NodeHitTestingTest {
    @Test
    fun `a button with nothing over it takes its own tap`() {
        val roots = NodeHitTesting.resolve(listOf(root(button(id = 1, at = rect(0f, 0f, 100f, 50f)))))

        val button = roots.node(1)
        assertTrue(button.isHittable)
        assertNull(button.obscuredBy)
    }

    @Test
    fun `a later sibling covering the button takes the tap instead`() {
        val roots = NodeHitTesting.resolve(
            listOf(
                root(
                    button(id = 1, at = rect(0f, 0f, 100f, 50f)),
                    button(id = 2, at = rect(0f, 0f, 100f, 50f)),
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
                    button(id = 1, at = rect(0f, 0f, 100f, 50f)),
                    button(id = 2, at = rect(200f, 0f, 300f, 50f)),
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
                        at = rect(0f, 0f, 100f, 50f),
                        children = listOf(button(id = 2, at = rect(0f, 0f, 100f, 50f))),
                    ),
                ),
            ),
        )

        assertEquals(NodeRef(ROOT_ID, 2), roots.node(1).obscuredBy, "the deepest node at the point wins")
        assertTrue(roots.node(2).isHittable)
    }

    @Test
    fun `a node clipped out of its scroll container has no area to tap`() {
        val roots = NodeHitTesting.resolve(
            listOf(root(button(id = 1, at = rect(0f, 0f, 100f, 50f), inScreen = rect(0f, 0f, 0f, 0f)))),
        )

        assertEquals(false, roots.node(1).isHittable)
        assertNull(roots.node(1).obscuredBy, "nothing takes the tap: there is nowhere to aim it")
    }

    @Test
    fun `nothing in a window below a touch-modal dialog can be tapped`() {
        val roots = NodeHitTesting.resolve(
            listOf(
                root(button(id = 1, at = rect(0f, 0f, 100f, 50f))),
                root(
                    button(id = 2, at = rect(0f, 500f, 100f, 550f)),
                    rootId = "dialog",
                    isTouchModal = true,
                ),
            ),
        )

        val behind = roots.first().node!!.children.single()
        assertEquals(false, behind.isHittable, "a dialog takes the taps that land outside it too")
        assertEquals(NodeRef("dialog", 0), behind.obscuredBy, "the dialog window itself is what takes the tap")
        assertTrue(roots.last().node!!.children.single().isHittable)
    }

    @Test
    fun `a window above covers what sits under it`() {
        val roots = NodeHitTesting.resolve(
            listOf(
                root(button(id = 1, at = rect(0f, 0f, 100f, 50f))),
                root(button(id = 2, at = rect(0f, 0f, 100f, 50f)), rootId = "popup"),
            ),
        )

        assertEquals(NodeRef("popup", 2), roots.first().node!!.children.single().obscuredBy)
    }

    @Test
    fun `a label is never reported as obstructed`() {
        val roots = NodeHitTesting.resolve(
            listOf(
                root(
                    label(id = 1, at = rect(0f, 0f, 100f, 50f)),
                    button(id = 2, at = rect(0f, 0f, 100f, 50f)),
                ),
            ),
        )

        assertTrue(roots.node(1).isHittable, "a node that accepts no touch has no tap to lose")
    }

    @Test
    fun `nodeAt reports the topmost accepting node`() {
        val roots = listOf(
            root(button(id = 1, at = rect(0f, 0f, 100f, 50f))),
            root(button(id = 2, at = rect(0f, 0f, 40f, 20f)), rootId = "popup"),
        )

        assertEquals(NodeRef("popup", 2), NodeHitTesting.nodeAt(roots, 10f, 10f))
        assertEquals(NodeRef(ROOT_ID, 1), NodeHitTesting.nodeAt(roots, 80f, 40f))
        assertNull(NodeHitTesting.nodeAt(roots, 500f, 500f))
    }

    @Test
    fun `nodeAt stops at a touch-modal window`() {
        val roots = listOf(
            root(button(id = 1, at = rect(0f, 0f, 100f, 50f))),
            root(button(id = 2, at = rect(0f, 500f, 100f, 550f)), rootId = "dialog", isTouchModal = true),
        )

        assertNull(NodeHitTesting.nodeAt(roots, 10f, 10f), "the dialog takes it, and has nothing at the point")
    }

    @Test
    fun `a window swallowing the tap is told apart from nothing taking it`() {
        val withDialog = listOf(
            root(button(id = 1, at = rect(0f, 0f, 100f, 50f))),
            root(button(id = 2, at = rect(0f, 500f, 100f, 550f)), rootId = "dialog", isTouchModal = true),
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

private fun List<ComposeRoot>.node(id: Int): UiNode = first { it.rootId == ROOT_ID }.node!!.find(id)!!

private fun UiNode.find(id: Int): UiNode? = takeIf { it.id == id } ?: children.firstNotNullOfOrNull { it.find(id) }

private fun rect(left: Float, top: Float, right: Float, bottom: Float) = NodeBounds(left, top, right, bottom)

private fun root(
    vararg children: UiNode,
    rootId: String = ROOT_ID,
    isTouchModal: Boolean = false,
) = ComposeRoot(
    rootId = rootId,
    label = rootId,
    density = 1f,
    windowOffsetX = 0f,
    windowOffsetY = 0f,
    isTouchModal = isTouchModal,
    node = ComposeNode(
        id = 0,
        bounds = rect(0f, 0f, 1000f, 1000f),
        boundsInScreen = rect(0f, 0f, 1000f, 1000f),
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

private fun label(id: Int, at: NodeBounds) = ComposeNode(id = id, bounds = at, boundsInScreen = at, text = "label")
