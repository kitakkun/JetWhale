package com.kitakkun.jetwhale.plugins.mirror.host

import java.io.EOFException
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile

/**
 * The length of the MP4 video in [file], read from its movie header (`moov` › `mvhd`), or null when
 * the file has no readable header. A recording's own length differs from the time between starting
 * and stopping it: the recorder takes a moment to start before its first frame.
 *
 * The movie header is the length players show. A simulator sends frames only when its screen
 * changes, so `simctl recordVideo` ends a recording with an edit that holds the last frame until the
 * stop, and its frames alone (the track's `mdhd`, or ffprobe's duration) cover less than that.
 */
internal fun mp4DurationMillis(file: File): Long? = try {
    RandomAccessFile(file, "r").use { mp4 ->
        val moov = mp4.findBox("moov", from = 0, until = mp4.length()) ?: return null
        val mvhd = mp4.findBox("mvhd", from = moov.contentStart, until = moov.end) ?: return null
        mp4.seek(mvhd.contentStart)
        val version = mp4.readUnsignedByte()
        // flags, then the creation and modification times, whose width depends on the version.
        mp4.skipBytes(if (version == 1) 3 + 16 else 3 + 8)
        val timescale = mp4.readInt().toLong() and 0xFFFFFFFFL
        val duration = if (version == 1) mp4.readLong() else mp4.readInt().toLong() and 0xFFFFFFFFL
        if (timescale == 0L) null else duration * 1000 / timescale
    }
} catch (_: EOFException) {
    null
} catch (_: IOException) {
    null
}

private class Mp4Box(val contentStart: Long, val end: Long)

private fun RandomAccessFile.findBox(type: String, from: Long, until: Long): Mp4Box? {
    var position = from
    while (position + 8 <= until) {
        seek(position)
        val declaredSize = readInt().toLong() and 0xFFFFFFFFL
        val boxType = ByteArray(4).also(::readFully).decodeToString()
        val (headerSize, size) = when (declaredSize) {
            // A 64-bit size follows the type.
            1L -> 16L to readLong()

            // The box runs to the end of its container.
            0L -> 8L to until - position

            else -> 8L to declaredSize
        }
        if (size < headerSize) return null
        if (boxType == type) return Mp4Box(contentStart = position + headerSize, end = minOf(position + size, until))
        position += size
    }
    return null
}
