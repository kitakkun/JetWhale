package com.kitakkun.jetwhale.plugins.storage.host

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FilePreviewTest {
    @Test
    fun `text reads as text and has a hex dump too`() {
        val file = LoadedFile(location("Files", "notes.txt"), "hello\nworld".encodeToByteArray(), totalSizeBytes = 11)

        assertEquals(listOf(PreviewFormat.Text, PreviewFormat.Hex), previewFormatsOf(file))
    }

    @Test
    fun `a PNG is drawn before anything else`() {
        val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
        val file = LoadedFile(location("Cache", "image_cache", "a.bin"), png, totalSizeBytes = 8)

        assertEquals(listOf(PreviewFormat.Image, PreviewFormat.Hex), previewFormatsOf(file))
    }

    @Test
    fun `a whole preferences DataStore file is decoded first`() {
        val file = LoadedFile(location("Files", "datastore", "settings.preferences_pb"), ByteArray(0), totalSizeBytes = 0)

        assertEquals(PreviewFormat.Preferences, previewFormatsOf(file).first())
    }

    @Test
    fun `a preferences DataStore file read only in part is not decoded`() {
        val file = LoadedFile(location("Files", "datastore", "settings.preferences_pb"), ByteArray(4), totalSizeBytes = 400_000)

        assertEquals(listOf(PreviewFormat.Hex), previewFormatsOf(file))
    }

    @Test
    fun `a character cut in half by the read size still reads as text`() {
        val bytes = "日本語".encodeToByteArray()

        assertEquals("日本", decodeTextOrNull(bytes.copyOf(bytes.size - 1)))
    }

    @Test
    fun `control bytes other than whitespace make a file binary`() {
        assertNull(decodeTextOrNull(byteArrayOf(0x53, 0x51, 0x4C, 0x00, 0x01)))
    }

    @Test
    fun `the hex dump shows offset, bytes and their printable characters`() {
        assertEquals(
            "00000000  48 69 00" + " ".repeat(39) + "  Hi.",
            hexDump(byteArrayOf(0x48, 0x69, 0x00)),
        )
    }
}
