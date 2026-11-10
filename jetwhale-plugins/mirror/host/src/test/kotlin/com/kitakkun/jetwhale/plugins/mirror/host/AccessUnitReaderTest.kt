package com.kitakkun.jetwhale.plugins.mirror.host

import java.io.ByteArrayInputStream
import java.io.InputStream
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AccessUnitReaderTest {
    @Test
    fun `a stream is split after each access unit delimiter, which stays with the unit it ends`() {
        val reader = AccessUnitReader(ByteArrayInputStream(KEY_FRAME + DELTA_FRAME))

        assertContentEquals(KEY_FRAME, reader.readAccessUnit()?.bytes)
        assertContentEquals(DELTA_FRAME, reader.readAccessUnit()?.bytes)
        assertNull(reader.readAccessUnit())
    }

    @Test
    fun `an access unit with an IDR slice is a key frame and one with only other slices is not`() {
        val reader = AccessUnitReader(ByteArrayInputStream(KEY_FRAME + DELTA_FRAME))

        assertEquals(true, reader.readAccessUnit()?.isKeyFrame)
        assertEquals(false, reader.readAccessUnit()?.isKeyFrame)
    }

    @Test
    fun `an access unit that arrives a byte at a time is read whole`() {
        val reader = AccessUnitReader(OneBytePerReadInputStream(KEY_FRAME + DELTA_FRAME))

        assertContentEquals(KEY_FRAME, reader.readAccessUnit()?.bytes)
        assertContentEquals(DELTA_FRAME, reader.readAccessUnit()?.bytes)
    }

    @Test
    fun `three-byte start codes are read like four-byte ones`() {
        val unitBytes = byteArrayOf(0, 0, 1, 0x65, 0x88.toByte(), 0x84.toByte(), 0, 0, 1, 0x09, 0xF0.toByte())

        val accessUnit = AccessUnitReader(ByteArrayInputStream(unitBytes)).readAccessUnit()

        assertContentEquals(unitBytes, accessUnit?.bytes)
        assertEquals(true, accessUnit?.isKeyFrame)
    }

    @Test
    fun `bytes after the last delimiter are an access unit cut short and are dropped`() {
        val reader = AccessUnitReader(ByteArrayInputStream(KEY_FRAME + nal(0x41, 0x9A)))

        assertContentEquals(KEY_FRAME, reader.readAccessUnit()?.bytes)
        assertNull(reader.readAccessUnit())
    }

    @Test
    fun `a large access unit grows the buffer`() {
        val slice = nal(0x65, *IntArray(300_000) { 0x5A })
        val unitBytes = slice + DELIMITER

        assertContentEquals(unitBytes, AccessUnitReader(ByteArrayInputStream(unitBytes)).readAccessUnit()?.bytes)
    }
}

/** A stream that hands out one byte per read, as a slow pipe can. */
private class OneBytePerReadInputStream(bytes: ByteArray) : InputStream() {
    private val source = ByteArrayInputStream(bytes)

    override fun read(): Int = source.read()

    override fun read(b: ByteArray, off: Int, len: Int): Int = if (len == 0) 0 else source.read(b, off, 1)
}

/** A NAL unit of [bytes] after a four-byte start code. */
private fun nal(vararg bytes: Int): ByteArray = byteArrayOf(0, 0, 0, 1) + bytes.map(Int::toByte).toByteArray()

private val DELIMITER = nal(0x09, 0xF0)

/** A sequence and a picture parameter set, an IDR slice, then the delimiter. */
private val KEY_FRAME = nal(0x67, 0x64, 0x00, 0x1F) + nal(0x68, 0xEE, 0x3C) + nal(0x65, 0x88, 0x84, 0x00) + DELIMITER

/** A slice of a frame that refers to others, then the delimiter. */
private val DELTA_FRAME = nal(0x41, 0x9A, 0x02) + DELIMITER
