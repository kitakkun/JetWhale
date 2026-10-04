package com.kitakkun.jetwhale.host.ui

import androidx.compose.foundation.ContextMenuArea
import androidx.compose.foundation.ContextMenuItem
import androidx.compose.foundation.ContextMenuState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.rightClick
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class JwThemeContextMenuTest {
    private val colors = JwColors.dark().copy(popupBackground = Color(0xFF204060))

    @Test
    fun `a context menu inside the theme is drawn on the theme's popup background`() = runDesktopComposeUiTest(width = 400, height = 300) {
        setContent {
            JwTheme(colors = colors) {
                val state = remember { ContextMenuState().apply { status = ContextMenuState.Status.Open(Rect(Offset(20f, 20f), 0f)) } }
                ContextMenuArea(items = { listOf(ContextMenuItem("First") {}, ContextMenuItem("Last") {}) }, state = state) {
                    Box(Modifier.fillMaxSize())
                }
            }
        }

        assertEquals(colors.popupBackground, backgroundBesideItem("Last"))
    }

    @Test
    fun `a text field's cut, copy and paste menu inside the theme is drawn on the theme's popup background`() = runDesktopComposeUiTest(width = 400, height = 300) {
        setContent {
            JwTheme(colors = colors) {
                BasicTextField(value = "some text", onValueChange = {}, modifier = Modifier.testTag("field"))
            }
        }

        onNodeWithTag("field").performMouseInput { rightClick(center) }

        assertEquals(colors.popupBackground, backgroundBesideItem("Select all"))
    }

    /** A pixel at the left edge of [label]'s row, where the menu's own background shows. */
    private fun ComposeUiTest.backgroundBesideItem(label: String): Color {
        val pixels = onNodeWithText(label).captureToImage().toPixelMap()
        return pixels[1, pixels.height / 2]
    }
}
