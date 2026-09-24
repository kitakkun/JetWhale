package com.kitakkun.jetwhale.plugins.storage.agent

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class FileRootTest {
    private val root = FileRoot(name = "Files", path = "/data/user/0/app/files/")

    @Test
    fun `segments are joined below the root`() {
        assertEquals("/data/user/0/app/files/datastore/settings.preferences_pb", root.resolve(listOf("datastore", "settings.preferences_pb")))
    }

    @Test
    fun `no segments is the root itself`() {
        assertEquals("/data/user/0/app/files", root.resolve(emptyList()))
    }

    @Test
    fun `the file system root stays absolute`() {
        val fileSystemRoot = FileRoot(name = "Everything", path = "/")

        assertEquals("/", fileSystemRoot.resolve(emptyList()))
        assertEquals("/tmp", fileSystemRoot.resolve(listOf("tmp")))
    }

    @Test
    fun `a segment that climbs out of the root is refused`() {
        listOf("..", ".", "", "a/b", "..\\secrets").forEach { segment ->
            assertFailsWith<IllegalArgumentException>(segment) { root.resolve(listOf("datastore", segment)) }
        }
    }
}
