package com.kitakkun.jetwhale.plugins.network.host

import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ImageBodyViewTest {

    @Test
    fun `decodes a Base64 image body into a bitmap and its original bytes`() {
        val png = pngBytes(width = 4, height = 3)
        val decoded = decodeImageBody(Base64.encode(png))!!

        assertEquals(4, decoded.bitmap.width)
        assertEquals(3, decoded.bitmap.height)
        // The original bytes are kept as-is: saving must write the file the server sent.
        assertContentEquals(png, decoded.bytes)
    }

    @Test
    fun `a body that is not a decodable image decodes to null`() {
        assertNull(decodeImageBody("not base64 at all"))
        assertNull(decodeImageBody(Base64.encode(byteArrayOf(1, 2, 3))))
    }

    @Test
    fun `file name comes from the URL path and the media type`() {
        assertEquals("avatar.png", suggestedImageFileName("https://example.com/users/avatar.png", "image/png"))
        // jpeg is spelled jpg, and query strings are not part of the name.
        assertEquals("photo.jpg", suggestedImageFileName("https://example.com/photo?size=large", "image/jpeg"))
        assertEquals("image.webp", suggestedImageFileName("https://example.com/", "image/webp"))
        assertEquals("icon.ico", suggestedImageFileName("https://example.com/icon.ico", "image/x-icon"))
    }

    @Test
    fun `size of a Base64 body is reported without decoding it`() {
        assertEquals(8, base64DecodedSize(Base64.encode(ByteArray(8))))
        assertEquals(9, base64DecodedSize(Base64.encode(ByteArray(9))))
        assertEquals(10, base64DecodedSize(Base64.encode(ByteArray(10))))
    }

    private fun pngBytes(width: Int, height: Int): ByteArray {
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        return ByteArrayOutputStream().also { ImageIO.write(image, "png", it) }.toByteArray()
    }
}
