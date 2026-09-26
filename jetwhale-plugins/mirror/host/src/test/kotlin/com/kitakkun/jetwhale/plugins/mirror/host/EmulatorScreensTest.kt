package com.kitakkun.jetwhale.plugins.mirror.host

import androidx.compose.ui.unit.IntSize
import okhttp3.Protocol
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import java.io.ByteArrayInputStream
import java.io.EOFException
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame

class EmulatorScreensTest {
    @Test
    fun `a discovery file gives the console port the gRPC port and the token`() {
        val (consolePort, endpoint) = assertNotNull(parseEmulatorDiscovery("avd.id=Pixel\nport.serial=5554\ngrpc.port=8554\ngrpc.token=secret\n"))

        assertEquals(5554, consolePort)
        assertEquals(8554, endpoint.port)
        assertEquals("secret", endpoint.token)
    }

    @Test
    fun `a discovery file without a gRPC port names no endpoint`() {
        assertNull(parseEmulatorDiscovery("port.serial=5554\nport.adb=5555\n"))
    }

    @Test
    fun `the endpoint is the one whose console port matches the serial`() {
        val directory = Files.createTempDirectory("avd-running").toFile().apply { deleteOnExit() }
        File(directory, "pid_1.ini").writeText("port.serial=5556\ngrpc.port=8556\n")
        File(directory, "pid_2.ini").writeText("port.serial=5554\ngrpc.port=8554\n")
        File(directory, "notes.txt").writeText("port.serial=5554\ngrpc.port=9999\n")

        assertEquals(8554, findEmulatorEndpoint("emulator-5554", listOf(directory))?.port)
        assertNull(findEmulatorEndpoint("emulator-5558", listOf(directory)))
        assertNull(findEmulatorEndpoint("R58M123ABC", listOf(directory)))
    }

    @Test
    fun `the request asks for RGBA frames of the wanted size`() {
        val expected = byteArrayOf(0x08, 0x01, 0x18, 0x9c.toByte(), 0x04, 0x20, 0xb0.toByte(), 0x09)

        assertContentEquals(expected, encodeImageFormat(IntSize(540, 1200)))
        assertContentEquals(byteArrayOf(0, 0, 0, 0, 2, 0x08, 0x01), grpcMessage(encodeImageFormat(null)))
    }

    @Test
    fun `frames are read with their size and pixels and the fields the mirror ignores are skipped`() {
        val first = ByteArray(2 * 1 * 4, Int::toByte)
        val second = ByteArray(1 * 2 * 4) { (it + 10).toByte() }
        val reader = EmulatorImageReader(ByteArrayInputStream(imageMessage(2, 1, first, seq = 1) + imageMessage(1, 2, second, seq = 2)))

        val one = assertNotNull(reader.next())
        assertEquals(2 to 1, one.width to one.height)
        assertContentEquals(first, reader.pixels.copyOf(first.size))
        val buffer = reader.pixels

        val two = assertNotNull(reader.next())
        assertEquals(1 to 2, two.width to two.height)
        assertContentEquals(second, reader.pixels.copyOf(second.size))
        assertSame(buffer, reader.pixels)
        assertNull(reader.next())
    }

    @Test
    fun `a stream cut off inside a frame fails instead of showing half of it`() {
        val whole = imageMessage(2, 1, ByteArray(8), seq = 1)

        assertFailsWith<EOFException> { EmulatorImageReader(ByteArrayInputStream(whole.copyOf(whole.size - 3))).next() }
    }

    @Test
    fun `frames reach the surface as they arrive`() {
        val frames = imageMessage(2, 1, ByteArray(8) { 7 }, seq = 1) + imageMessage(2, 1, ByteArray(8) { 9 }, seq = 2)
        MirrorSurface().use { surface ->
            var count = 0

            readEmulatorFramesInto(surface, ByteArrayInputStream(frames)) { count++ }

            assertEquals(2, count)
            surface.drawFrame { bitmap -> assertEquals(9.toByte(), bitmap.readPixels()?.first()) }
        }
    }

    @Test
    fun `an emulator that answers the call streams its frames and one that refuses it names no stream`() {
        MockWebServer().use { server ->
            server.protocols = listOf(Protocol.H2_PRIOR_KNOWLEDGE)
            server.enqueue(MockResponse().setHeader("content-type", "application/grpc").setHeader("grpc-status", "7"))
            server.enqueue(MockResponse().setHeader("content-type", "application/grpc").setBody(Buffer().write(imageMessage(2, 1, ByteArray(8), seq = 1))))
            server.start()
            val directory = Files.createTempDirectory("avd-running").toFile().apply { deleteOnExit() }
            File(directory, "pid_1.ini").writeText("port.serial=5554\ngrpc.port=${server.port}\ngrpc.token=secret\n")
            val screens = EmulatorScreens(listOf(directory))

            assertNull(screens.open("emulator-5554", IntSize(540, 1200)))
            val stream = assertIs<VideoStream.EmulatorRgba>(screens.open("emulator-5554", IntSize(540, 1200)))

            stream.frames.use { assertEquals(2, assertNotNull(EmulatorImageReader(it).next()).width) }
            assertEquals("Bearer secret", server.takeRequest().getHeader("authorization"))
        }
    }

    @Test
    fun `an emulator with no discovery file names no stream`() {
        assertNull(EmulatorScreens(emptyList()).open("emulator-5554", null))
    }
}

/** A gRPC-framed `Image` message of [width] by [height] with [pixels], plus fields the reader skips. */
private fun imageMessage(width: Int, height: Int, pixels: ByteArray, seq: Int): ByteArray {
    val format = Buffer().apply {
        writeByte(0x08).writeByte(0x01)
        writeByte(0x18).writeVarint(width)
        writeByte(0x20).writeVarint(height)
    }.readByteArray()
    val image = Buffer().apply {
        writeByte(0x0a).writeVarint(format.size).write(format)
        writeByte(0x22).writeVarint(pixels.size).write(pixels)
        writeByte(0x28).writeVarint(seq)
        writeByte(0x31).write(ByteArray(8))
    }.readByteArray()
    return grpcMessage(image)
}

private fun Buffer.writeVarint(value: Int): Buffer = apply {
    var remaining = value
    while (remaining >= 0x80) {
        writeByte((remaining and 0x7f) or 0x80)
        remaining = remaining ushr 7
    }
    writeByte(remaining)
}
