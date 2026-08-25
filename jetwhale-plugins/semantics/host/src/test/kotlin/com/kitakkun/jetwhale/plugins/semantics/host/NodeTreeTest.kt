package com.kitakkun.jetwhale.plugins.semantics.host

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NodeTreeTest {
    @Test
    fun `filterTree keeps a match together with its ancestors`() {
        val tree = node(
            id = 1,
            children = listOf(
                node(id = 2, children = listOf(node(id = 3, testTag = "target"))),
                node(id = 4),
            ),
        )

        val filtered = tree.filterTree { it.testTag == "target" }

        // The ancestors 1 and 2 do not match themselves, but dropping them would reparent the
        // match and lose where it sits in the tree.
        assertEquals(listOf(1, 2, 3), filtered?.asSequence()?.map { it.id }?.toList())
    }

    @Test
    fun `filterTree returns null when nothing in the subtree matches`() {
        val tree = node(id = 1, children = listOf(node(id = 2)))

        assertNull(tree.filterTree { it.testTag == "absent" })
    }

    @Test
    fun `filterTree drops the children of a node kept only on its own merit`() {
        val tree = node(id = 1, testTag = "target", children = listOf(node(id = 2)))

        assertEquals(listOf(1), tree.filterTree { it.testTag == "target" }?.asSequence()?.map { it.id }?.toList())
    }

    @Test
    fun `asSequence walks depth-first with the node itself first`() {
        val tree = node(
            id = 1,
            children = listOf(
                node(id = 2, children = listOf(node(id = 3))),
                node(id = 4),
            ),
        )

        assertEquals(listOf(1, 2, 3, 4), tree.asSequence().map { it.id }.toList())
    }

    @Test
    fun `findRootOf prefers the newest root when an id appears in more than one`() {
        // Ids are only unique within a root, so a dialog can reuse the id of a node underneath it.
        val snapshot = snapshot(
            root("window", node = node(id = 7, text = "behind")),
            root("dialog", node = node(id = 7, text = "in front")),
        )

        assertEquals("dialog", snapshot.findRootOf(nodeId = 7)?.rootId)
    }

    @Test
    fun `findRootOf returns null for an unknown id`() {
        assertNull(snapshot(root("window", node = node(id = 1))).findRootOf(nodeId = 99))
    }

    @Test
    fun `nodeCount counts every node across every root`() {
        val snapshot = snapshot(
            root("a", node = node(id = 1, children = listOf(node(id = 2)))),
            root("b", node = node(id = 3)),
            root("c", node = null),
        )

        assertEquals(3, snapshot.nodeCount())
    }

    @Test
    fun `isInteractive covers actions, editing and scrolling`() {
        assertTrue(node(id = 1, actions = listOf("OnClick"), isClickable = true).isInteractive)
        assertTrue(node(id = 2, isEditable = true).isInteractive)
        assertTrue(node(id = 3, isScrollable = true).isInteractive)
        assertFalse(node(id = 4, text = "just a label").isInteractive)
    }

    @Test
    fun `matches combines criteria with AND and compares case-insensitively by substring`() {
        val target = node(id = 1, role = "Button", text = "Send message", testTag = "send-button", isClickable = true, actions = listOf("OnClick"))

        assertTrue(target.matches(NodeQuery(text = "send", role = "button")))
        assertTrue(target.matches(NodeQuery(testTag = "SEND-BUTTON")))
        assertFalse(target.matches(NodeQuery(text = "send", role = "checkbox")))
    }

    @Test
    fun `matches compares whole values when exact is set`() {
        val target = node(id = 1, text = "Send message")

        assertFalse(target.matches(NodeQuery(text = "Send", exact = true)))
        assertTrue(target.matches(NodeQuery(text = "send message", exact = true)))
    }

    @Test
    fun `matches rejects a non-interactive node when interactiveOnly is set`() {
        assertFalse(node(id = 1, text = "label").matches(NodeQuery(interactiveOnly = true)))
        assertTrue(node(id = 2, text = "label", isClickable = true).matches(NodeQuery(interactiveOnly = true)))
    }

    @Test
    fun `matchesFreeText searches every label the node displays`() {
        val target = node(id = 42, role = "Tab", contentDescription = "Profile picture")

        assertTrue(target.matchesFreeText("profile"))
        assertTrue(target.matchesFreeText("tab"))
        assertTrue(target.matchesFreeText("42"))
        assertTrue(target.matchesFreeText("   "))
        assertFalse(target.matchesFreeText("absent"))
    }

    @Test
    fun `a View node keeps its ancestors when filtered`() {
        // An Android capture nests the two kinds in one tree, so a filter that matched a View node
        // has to keep the Compose nodes above it just as it would a Compose match.
        val tree = node(
            id = 1,
            children = listOf(node(id = 2, children = listOf(viewNode(id = -3, viewClass = "android.widget.Button", resourceId = "submit")))),
        )

        val filtered = tree.filterTree { it.resourceId == "submit" }

        assertEquals(listOf(1, 2, -3), filtered?.asSequence()?.map { it.id }?.toList())
    }

    @Test
    fun `matches compares a resourceId whole even when the other criteria are substrings`() {
        val target = viewNode(id = -1, viewClass = "android.widget.Button", resourceId = "submit", text = "Send it")

        assertTrue(target.matches(NodeQuery(resourceId = "SUBMIT")))
        assertTrue(target.matches(NodeQuery(resourceId = "submit", text = "Send")))
        assertFalse(target.matches(NodeQuery(resourceId = "sub")))
        assertFalse(node(id = 1, text = "Send it").matches(NodeQuery(resourceId = "submit")))
    }

    @Test
    fun `matchesFreeText searches a View node's class and resource id`() {
        val target = viewNode(id = -1, viewClass = "android.widget.Button", resourceId = "submit")

        assertTrue(target.matchesFreeText("button"))
        assertTrue(target.matchesFreeText("submit"))
    }

    @Test
    fun `displayLabel names a View node by its class, resource id and label`() {
        assertEquals(
            "Button · @id/submit · Send",
            viewNode(id = -1, viewClass = "android.widget.Button", resourceId = "submit", text = "Send").displayLabel(),
        )
        assertEquals("LinearLayout", viewNode(id = -2, viewClass = "android.widget.LinearLayout").displayLabel())
    }

    @Test
    fun `displayLabel prefers the node's own label and falls back to role then id`() {
        assertEquals("Button · Send", node(id = 1, role = "Button", text = "Send").displayLabel())
        assertEquals("Send", node(id = 1, text = "Send").displayLabel())
        assertEquals("Button", node(id = 1, role = "Button").displayLabel())
        assertEquals("#1", node(id = 1).displayLabel())
    }
}
