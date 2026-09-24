package com.kitakkun.jetwhale.plugins.storage.host

import com.kitakkun.jetwhale.plugins.storage.protocol.FileRootInfo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FileTreeTest {
    private val roots = listOf(FileRootInfo(name = "Files", absolutePath = "/data/files"), FileRootInfo(name = "Cache", absolutePath = "/data/cache"))
    private val children = mapOf(
        location("Files") to listOf(directoryEntry("datastore"), fileEntry("notes.txt", sizeBytes = 5)),
        location("Files", "datastore") to listOf(fileEntry("settings.preferences_pb", sizeBytes = 5)),
    )

    @Test
    fun `only the roots show until something is expanded`() {
        val rows = flattenFileTree(roots, children, expanded = emptySet())

        assertEquals(listOf("Files", "Cache"), rows.map { it.location.name })
    }

    @Test
    fun `an expanded directory shows its content below it, one level deeper`() {
        val rows = flattenFileTree(roots, children, expanded = setOf(location("Files"), location("Files", "datastore")))

        assertEquals(
            listOf("Files" to 0, "datastore" to 1, "settings.preferences_pb" to 2, "notes.txt" to 1, "Cache" to 0),
            rows.map { it.location.name to it.depth },
        )
    }

    @Test
    fun `a directory inside a collapsed one stays hidden even if it is expanded`() {
        val rows = flattenFileTree(roots, children, expanded = setOf(location("Files", "datastore")))

        assertEquals(listOf("Files", "Cache"), rows.map { it.location.name })
    }

    @Test
    fun `a location contains itself and what lies below it, and nothing in another root`() {
        assertTrue(location("Files", "datastore", "a.preferences_pb") in location("Files", "datastore"))
        assertTrue(location("Files", "datastore") in location("Files", "datastore"))
        assertFalse(location("Files", "data") in location("Files", "datastore"))
        assertFalse(location("Cache", "datastore") in location("Files", "datastore"))
    }
}
