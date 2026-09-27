package com.kitakkun.jetwhale.plugins.storage.host

import com.kitakkun.jetwhale.plugins.storage.protocol.DirectoryMeasurement
import com.kitakkun.jetwhale.plugins.storage.protocol.FileRootInfo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class EntryFactsTest {
    private val root = FileRootInfo(name = "Data", absolutePath = "/data/user/0/com.example.app/")

    @Test
    fun `a text file is described by path owner kind and lines`() {
        val location = location("Data", "shared_prefs", "settings.xml")
        val facts = EntryFacts(
            row = FileTreeRow(location = location, depth = 2, entry = fileEntry("settings.xml", sizeBytes = 2048), expanded = false),
            root = root,
            loadedFile = LoadedFile(location, "<map>\n</map>\n".encodeToByteArray(), totalSizeBytes = 13),
            directoryMeasurement = null,
            fileSha256 = null,
        ).rows.toMap()

        assertEquals("/data/user/0/com.example.app/shared_prefs/settings.xml", facts["Path"])
        assertEquals("SharedPreferences", facts["Stored by"])
        assertEquals("XML", facts["Kind"])
        assertEquals("2 lines, UTF-8", facts["Text"])
        assertEquals("2.0 KB (2,048 bytes)", facts["Size"])
        assertEquals("Read and write", facts["Access"])
    }

    @Test
    fun `a symbolic link shows where it points and a read-only entry says so`() {
        val entry = fileEntry("lib", sizeBytes = 0).copy(isSymbolicLink = true, linkTarget = "/data/app/lib/arm64", writable = false)
        val facts = EntryFacts(
            row = FileTreeRow(location = location("Data", "lib"), depth = 1, entry = entry, expanded = false),
            root = root,
            loadedFile = null,
            directoryMeasurement = null,
            fileSha256 = null,
        ).rows.toMap()

        assertEquals("/data/app/lib/arm64", facts["Link"])
        assertEquals("Read only", facts["Access"])
    }

    @Test
    fun `a measurement cut short reads as a floor`() {
        val facts = EntryFacts(
            row = FileTreeRow(location = location("Data", "cache"), depth = 1, entry = directoryEntry("cache"), expanded = false),
            root = root,
            loadedFile = null,
            directoryMeasurement = DirectoryMeasurement(
                totalSizeBytes = 3_145_728,
                fileCount = 1,
                directoryCount = 2,
                truncated = true,
                error = null,
            ),
            fileSha256 = null,
        ).rows.toMap()

        assertEquals("at least 3.0 MB (3,145,728 bytes)", facts["Total size"])
        assertEquals("at least 1 file, 2 directories", facts["Contents"])
        assertFalse("Size" in facts)
    }
}
