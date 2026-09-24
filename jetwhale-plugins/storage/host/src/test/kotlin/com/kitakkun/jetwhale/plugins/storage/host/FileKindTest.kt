package com.kitakkun.jetwhale.plugins.storage.host

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FileKindTest {
    @Test
    fun `binary formats are told by their signature`() {
        assertEquals(FileKind.Sqlite, fileKindOf("app.db", "SQLite format 3\u0000".encodeToByteArray() + ByteArray(8)))
        assertEquals(FileKind.Png, fileKindOf("a", byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D)))
        assertEquals(FileKind.Jpeg, fileKindOf("a", byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte())))
        assertEquals(FileKind.Pdf, fileKindOf("a", "%PDF-1.7".encodeToByteArray()))
        assertEquals(FileKind.Zip, fileKindOf("base.apk", byteArrayOf(0x50, 0x4B, 0x03, 0x04, 0x14)))
        assertEquals(FileKind.Gzip, fileKindOf("a.gz", byteArrayOf(0x1F, 0x8B.toByte(), 0x08)))
    }

    @Test
    fun `a RIFF file is WebP only when its tag says so`() {
        assertEquals(FileKind.WebP, fileKindOf("a", "RIFF\u0000\u0000\u0000\u0000WEBPVP8 ".encodeToByteArray()))
        assertNull(fileKindOf("a", "RIFF\u0000\u0000\u0000\u0000WAVEfmt ".encodeToByteArray()))
    }

    @Test
    fun `text is told apart as JSON XML or plain text`() {
        assertEquals(FileKind.Json, fileKindOf("cache", "  {\"a\": 1}".encodeToByteArray()))
        assertEquals(FileKind.Json, fileKindOf("cache", "[1, 2]".encodeToByteArray()))
        assertEquals(FileKind.Xml, fileKindOf("prefs.xml", "<?xml version='1.0'?><map/>".encodeToByteArray()))
        assertEquals(FileKind.Text, fileKindOf("notes", "hello".encodeToByteArray()))
    }

    @Test
    fun `text that happens to start with BM is still text`() {
        assertEquals(FileKind.Text, fileKindOf("notes", "BMW is a car".encodeToByteArray()))
        assertEquals(FileKind.Bmp, fileKindOf("a.bmp", byteArrayOf(0x42, 0x4D, 0x3A, 0x00, 0x00, 0x00)))
    }

    @Test
    fun `a preferences DataStore file is known by its name`() {
        assertEquals(FileKind.PreferencesDataStore, fileKindOf("settings.preferences_pb", byteArrayOf(0x0A, 0x0F)))
    }

    @Test
    fun `an empty file has no kind`() {
        assertNull(fileKindOf("empty", ByteArray(0)))
    }
}
