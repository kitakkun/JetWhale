package com.kitakkun.jetwhale.plugins.mirror.host

import java.io.IOException
import java.io.InputStream

/** One frame of H.264 in Annex B form: its NAL units, then the access unit delimiter that ended it. */
internal class AccessUnit(val bytes: ByteArray, val isKeyFrame: Boolean)

/**
 * Reads H.264 in Annex B form whose access units are each followed by an access unit delimiter, as
 * the iPhone capture helper writes it, one access unit at a time. The delimiter after a frame,
 * rather than before the next, lets a frame be passed on as soon as it is whole.
 */
internal class AccessUnitReader(private val source: InputStream) {
    private var buffer = ByteArray(READ_CHUNK_BYTES * 2)
    private var length = 0

    /** Where the search for the next delimiter resumes: the bytes before it hold none. */
    private var searchedUpTo = 0

    /**
     * The next access unit, its delimiter included, or null at the end of the stream. Bytes after
     * the last delimiter are an access unit cut short, and are dropped.
     *
     * @throws IOException when no delimiter arrives within [MAX_ACCESS_UNIT_BYTES]: the stream is
     * not in the form this reads.
     */
    fun readAccessUnit(): AccessUnit? {
        while (true) {
            val end = findAccessUnitEnd()
            if (end != null) {
                val unit = buffer.copyOf(end)
                buffer.copyInto(buffer, destinationOffset = 0, startIndex = end, endIndex = length)
                length -= end
                searchedUpTo = 0
                return AccessUnit(unit, isKeyFrame = containsIdrSlice(unit))
            }
            if (length >= MAX_ACCESS_UNIT_BYTES) throw IOException("no access unit delimiter in the first $MAX_ACCESS_UNIT_BYTES bytes of H.264")
            if (buffer.size - length < READ_CHUNK_BYTES) buffer = buffer.copyOf(buffer.size * 2)
            val read = source.read(buffer, length, buffer.size - length)
            if (read < 0) return null
            length += read
        }
    }

    /** The end of the first access unit in the buffer: just past its delimiter, a start code, the NAL header 9 and one byte. */
    private fun findAccessUnitEnd(): Int? {
        var index = searchedUpTo
        while (index + DELIMITER_AFTER_START_CODE_BYTES <= length) {
            if (isStartCodeAt(buffer, index) && buffer[index + 3].toInt() and NAL_TYPE_MASK == NAL_TYPE_ACCESS_UNIT_DELIMITER) return index + DELIMITER_AFTER_START_CODE_BYTES
            index++
        }
        searchedUpTo = index
        return null
    }
}

/** Whether [unit] holds a slice of an IDR picture, from which a decoder can start. */
private fun containsIdrSlice(unit: ByteArray): Boolean = (0..unit.size - 4).any { isStartCodeAt(unit, it) && unit[it + 3].toInt() and NAL_TYPE_MASK == NAL_TYPE_IDR_SLICE }

/** Whether a three-byte start code, `00 00 01`, begins at [index]; a four-byte one ends with it. */
private fun isStartCodeAt(bytes: ByteArray, index: Int): Boolean = bytes[index].toInt() == 0 && bytes[index + 1].toInt() == 0 && bytes[index + 2].toInt() == 1

private const val NAL_TYPE_MASK = 0x1F

private const val NAL_TYPE_IDR_SLICE = 5

private const val NAL_TYPE_ACCESS_UNIT_DELIMITER = 9

/** A three-byte start code, the delimiter's NAL header, and its one byte of payload. */
private const val DELIMITER_AFTER_START_CODE_BYTES = 5

private const val READ_CHUNK_BYTES = 64 * 1024

/** Keeps a stream without delimiters from growing the buffer without end. */
private const val MAX_ACCESS_UNIT_BYTES = 32 * 1024 * 1024
