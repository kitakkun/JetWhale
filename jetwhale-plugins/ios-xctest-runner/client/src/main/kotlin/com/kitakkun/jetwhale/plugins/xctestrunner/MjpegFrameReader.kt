package com.kitakkun.jetwhale.plugins.xctestrunner

import okio.BufferedSource

/** Longer than any line of a part's header the runner writes; a longer line is not one of them. */
private const val MAX_LINE_BYTES = 1024L

/** Far more than a JPEG of an iPhone's or iPad's screen; a part that claims more is not a frame. */
private const val MAX_FRAME_BYTES = 64L shl 20

/**
 * Reads the frames of a `multipart/x-mixed-replace` body as the runner's `/stream` writes it: each
 * part opens with the [boundary] line and a `Content-Length` header, and a line break follows its
 * bytes.
 */
internal class MjpegFrameReader(private val source: BufferedSource, boundary: String) {
    private val delimiter = "--$boundary"

    /**
     * The next part's bytes, blocking until they have all arrived; null once the body ends between
     * two parts.
     *
     * @throws java.io.EOFException when the body ends inside a part.
     * @throws XcTestRunnerException when the body is not made of such parts.
     */
    fun readFrame(): ByteArray? {
        var line: String
        do {
            if (source.exhausted()) return null
            line = source.readUtf8LineStrict(MAX_LINE_BYTES)
        } while (line.isEmpty())
        if (line == "$delimiter--") return null
        if (line != delimiter) throw XcTestRunnerException("the XCTest runner's screen stream has '${line.take(80)}' where a frame should start", null)
        var contentLength: Long? = null
        while (true) {
            val header = source.readUtf8LineStrict(MAX_LINE_BYTES)
            if (header.isEmpty()) break
            if (header.substringBefore(':').trim().equals("Content-Length", ignoreCase = true)) contentLength = header.substringAfter(':').trim().toLongOrNull()
        }
        val frameLength = contentLength?.takeIf { it in 0..MAX_FRAME_BYTES } ?: throw XcTestRunnerException("a frame of the XCTest runner's screen stream has no Content-Length of at most $MAX_FRAME_BYTES bytes", null)
        return source.readByteArray(frameLength)
    }
}
