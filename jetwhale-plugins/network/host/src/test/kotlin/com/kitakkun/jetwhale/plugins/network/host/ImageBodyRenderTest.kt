package com.kitakkun.jetwhale.plugins.network.host

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.kitakkun.jetwhale.host.ui.JwTheme
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlin.io.encoding.Base64
import kotlin.test.Test

/** Covers what the Traffic tab shows for an image body: the picture itself and its export actions. */
@OptIn(ExperimentalTestApi::class)
class ImageBodyRenderTest {

    @Test
    fun `an image body renders as a picture with its export actions`() = runComposeUiTest {
        setImageBody(Base64.encode(pngBytes(width = 8, height = 6)))

        onNodeWithContentDescription("Response image preview").assertIsDisplayed()
        onNodeWithText("8×6", substring = true).assertIsDisplayed()
        onNodeWithText("Copy image").assertIsDisplayed()
        onNodeWithText("Save image…").assertIsDisplayed()
    }

    @Test
    fun `a body that does not decode falls back to the text rendering`() = runComposeUiTest {
        setImageBody(Base64.encode(byteArrayOf(1, 2, 3)))

        onNodeWithContentDescription("Response image preview").assertDoesNotExist()
        onNodeWithText("Copy image").assertDoesNotExist()
    }

    private fun ComposeUiTest.setImageBody(body: String) = setContent {
        JwTheme(darkTheme = false) {
            Box(Modifier.requiredSize(width = 600.dp, height = 500.dp)) {
                ImageBodyBlock(
                    body = body,
                    mediaType = "image/png",
                    url = "https://example.com/assets/logo.png",
                    truncated = false,
                )
            }
        }
    }

    private fun pngBytes(width: Int, height: Int): ByteArray {
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        return ByteArrayOutputStream().also { ImageIO.write(image, "png", it) }.toByteArray()
    }
}
