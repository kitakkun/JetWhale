package com.kitakkun.jetwhale.plugins.semantics.host

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TreeRowTest {
    @Test
    fun `emits a header per root followed by its nodes, indented by depth`() {
        val rows = buildTreeRows(
            roots = listOf(root("window", label = "MainActivity", node = node(id = 1, children = listOf(node(id = 2))))),
            collapsedKeys = emptySet(),
            predicate = { true },
        )

        assertEquals(3, rows.size)
        val header = rows[0] as TreeRow.RootHeader
        assertEquals("MainActivity", header.root.label)
        assertEquals(2, header.nodeCount)
        assertEquals(0, (rows[1] as TreeRow.NodeRow).depth)
        assertEquals(1, (rows[2] as TreeRow.NodeRow).depth)
    }

    @Test
    fun `a collapsed node hides its subtree but stays visible itself`() {
        val rows = buildTreeRows(
            roots = listOf(root("window", node = node(id = 1, children = listOf(node(id = 2, children = listOf(node(id = 3))))))),
            collapsedKeys = setOf(NodeKey("window", 2)),
            predicate = { true },
        )

        assertEquals(listOf(1, 2), rows.filterIsInstance<TreeRow.NodeRow>().map { it.node.id })
        assertTrue(rows.filterIsInstance<TreeRow.NodeRow>().single { it.node.id == 2 }.expandable)
    }

    @Test
    fun `a root whose nodes are all filtered out still gets its header`() {
        // "This window has nothing matching" is information; dropping the root would read as the
        // window having gone away.
        val rows = buildTreeRows(
            roots = listOf(root("window", node = node(id = 1, text = "hello"))),
            collapsedKeys = emptySet(),
            predicate = { it.text == "absent" },
        )

        assertEquals(1, rows.size)
        assertEquals(0, (rows.single() as TreeRow.RootHeader).nodeCount)
    }

    @Test
    fun `row keys are unique across roots that reuse node ids`() {
        val rows = buildTreeRows(
            roots = listOf(
                root("window", node = node(id = 1)),
                root("dialog", node = node(id = 1)),
            ),
            collapsedKeys = emptySet(),
            predicate = { true },
        )

        assertEquals(rows.size, rows.map { it.key }.distinct().size)
    }
}
