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

    @Test
    fun `an upload is staged beside the file it replaces`() {
        val paths = root.uploadPaths(listOf("datastore", "settings.preferences_pb"), uploadId = "a1-b2")

        assertEquals("/data/user/0/app/files/datastore/settings.preferences_pb", paths.target)
        assertEquals("/data/user/0/app/files/datastore/.settings.preferences_pb.jetwhale-upload-a1-b2", paths.staging)
    }

    @Test
    fun `an upload cannot replace the root itself`() {
        assertFailsWith<IllegalArgumentException> { root.uploadPaths(emptyList(), uploadId = "a1") }
    }

    @Test
    fun `an upload id that could name another path is refused`() {
        listOf("", "../x", "a/b", "a.b").forEach { uploadId ->
            assertFailsWith<IllegalArgumentException>(uploadId) { root.uploadPaths(listOf("notes.txt"), uploadId = uploadId) }
        }
    }
}
