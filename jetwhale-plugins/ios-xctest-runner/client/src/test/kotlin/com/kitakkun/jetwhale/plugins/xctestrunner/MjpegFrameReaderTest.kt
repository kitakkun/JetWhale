package com.kitakkun.jetwhale.plugins.xctestrunner

import okio.Buffer
import java.io.EOFException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class MjpegFrameReaderTest {
    @Test
    fun `frames are read part by part until the body ends between two parts`() {
        val reader = readerOf(part("first") + part("second"))

        assertEquals("first", reader.readFrame()?.decodeToString())
        assertEquals("second", reader.readFrame()?.decodeToString())
        assertNull(reader.readFrame())
    }

    @Test
    fun `a frame's bytes are taken by their length even when they hold line breaks or the boundary`() {
        val reader = readerOf(part("a\r\n--jetwhale-frame\r\nb") + part("next"))

        assertEquals("a\r\n--jetwhale-frame\r\nb", reader.readFrame()?.decodeToString())
        assertEquals("next", reader.readFrame()?.decodeToString())
    }

    @Test
    fun `a header is found whatever its case and other headers are skipped`() {
        val reader = readerOf("--jetwhale-frame\r\nX-Other: 1\r\ncontent-length: 4\r\nContent-Type: image/jpeg\r\n\r\njpeg\r\n")

        assertEquals("jpeg", reader.readFrame()?.decodeToString())
    }

    @Test
    fun `the closing boundary ends the stream`() {
        val reader = readerOf(part("only") + "--jetwhale-frame--\r\n" + part("after the end"))

        assertEquals("only", reader.readFrame()?.decodeToString())
        assertNull(reader.readFrame())
    }

    @Test
    fun `a body that ends inside a frame is cut short rather than ended`() {
        val reader = readerOf("--jetwhale-frame\r\nContent-Length: 10\r\n\r\nshort")

        assertFailsWith<EOFException>(block = reader::readFrame)
    }

    @Test
    fun `a part without a Content-Length is refused`() {
        val failure = assertFailsWith<XcTestRunnerException> { readerOf("--jetwhale-frame\r\nContent-Type: image/jpeg\r\n\r\njpeg\r\n").readFrame() }

        assertEquals("a frame of the XCTest runner's screen stream has no Content-Length of at most 67108864 bytes", failure.message)
    }

    @Test
    fun `a Content-Length larger than any frame is refused before anything is read into memory`() {
        assertFailsWith<XcTestRunnerException> { readerOf("--jetwhale-frame\r\nContent-Length: 99999999999\r\n\r\n").readFrame() }
    }

    @Test
    fun `something other than the boundary where a frame should start is refused`() {
        val failure = assertFailsWith<XcTestRunnerException> { readerOf("""{"ok":false,"error":"unknown command"}""" + "\r\n").readFrame() }

        assertEquals("""the XCTest runner's screen stream has '{"ok":false,"error":"unknown command"}' where a frame should start""", failure.message)
    }

    private fun part(body: String) = "--jetwhale-frame\r\nContent-Type: image/jpeg\r\nContent-Length: ${body.length}\r\n\r\n$body\r\n"

    private fun readerOf(body: String) = MjpegFrameReader(Buffer().writeUtf8(body), boundary = "jetwhale-frame")
}
