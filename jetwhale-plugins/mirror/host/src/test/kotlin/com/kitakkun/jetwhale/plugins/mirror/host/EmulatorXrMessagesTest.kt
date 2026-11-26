package com.kitakkun.jetwhale.plugins.mirror.host

import okio.Buffer
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class EmulatorXrMessagesTest {
    @Test
    fun `a hand event points at a framebuffer pixel and pinches with the primary button`() {
        val pointing = byteArrayOf(0x3a, 0x08, 0x08, 0xc8.toByte(), 0x06, 0x10, 0xc0.toByte(), 0x0b, 0x18, 0x00)
        val pinching = pointing.copyOf().also { it[it.lastIndex] = 0x01 }

        assertContentEquals(pointing, encodeXrHandEvent(x = 840, y = 1472, pinching = false))
        assertContentEquals(pinching, encodeXrHandEvent(x = 840, y = 1472, pinching = true))
    }

    @Test
    fun `a head rotation carries the turn up about the x axis and the turn left about the y axis`() {
        val rotation = Buffer().writeByte(0x0d).writeIntLe(0.25f.toRawBits()).writeByte(0x15).writeIntLe((-0.5f).toRawBits()).readByteArray()
        val expected = Buffer().writeByte(0x52).writeByte(rotation.size).write(rotation).readByteArray()

        assertContentEquals(expected, encodeXrHeadRotation(upRadians = 0.25f, leftRadians = -0.5f))
    }

    @Test
    fun `recentering is the recenter action of an XR command`() {
        assertContentEquals(byteArrayOf(0x4a, 0x02, 0x08, 0x00), encodeXrRecenter())
    }

    @Test
    fun `XR options read back as they were written`() {
        val options = XrOptions(environment = 1, passthroughCoefficient = 1f, dimmingValue = 0.5f)

        assertEquals(options, parseXrOptions(encodeXrOptions(options)))
    }

    @Test
    fun `XR options left out of a reply read as zero, and fields the mirror does not know are skipped`() {
        val reply = Buffer().writeByte(0x1a).writeByte(0x02).writeByte(0x15).writeByte(0x00).writeByte(0x15).writeIntLe(1f.toRawBits()).readByteArray()

        assertEquals(XrOptions(environment = 0, passthroughCoefficient = 1f, dimmingValue = 0f), parseXrOptions(reply))
    }

    @Test
    fun `a screenshot reply gives the picture in its image field`() {
        val png = byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47)
        val reply = Buffer().apply {
            writeByte(0x0a).writeByte(0x00)
            writeByte(0x22).writeByte(png.size).write(png)
            writeByte(0x28).writeByte(0x03)
        }.readByteArray()

        assertContentEquals(png, parseImageBytes(reply))
    }

    @Test
    fun `a screenshot reply without a picture is refused`() {
        assertFailsWith<DeviceControlException> { parseImageBytes(byteArrayOf(0x28, 0x03)) }
    }

    @Test
    fun `a screenshot is asked for whole, as a PNG`() {
        assertContentEquals(byteArrayOf(0x08, 0x00), encodePngImageFormat())
    }
}
