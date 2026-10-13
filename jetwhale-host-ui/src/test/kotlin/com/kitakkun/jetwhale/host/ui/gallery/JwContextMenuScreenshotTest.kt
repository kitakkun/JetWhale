package com.kitakkun.jetwhale.host.ui.gallery

import androidx.compose.foundation.ContextMenuArea
import androidx.compose.foundation.ContextMenuItem
import androidx.compose.foundation.ContextMenuState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import com.github.takahirom.roborazzi.RoborazziOptions
import com.kitakkun.jetwhale.host.ui.JwTheme
import io.github.takahirom.roborazzi.captureRoboImage
import kotlin.test.Test

/**
 * An open context menu, with a disabled item, over the surface it usually opens on, in both
 * built-in themes. Compared by CI like [JwGalleryScreenshotTest].
 */
@OptIn(ExperimentalTestApi::class)
class JwContextMenuScreenshotTest {

    @Test
    fun `an open context menu in the light theme`() = captureOpenContextMenu(darkTheme = false)

    @Test
    fun `an open context menu in the dark theme`() = captureOpenContextMenu(darkTheme = true)

    private fun captureOpenContextMenu(darkTheme: Boolean) = runDesktopComposeUiTest(width = 320, height = 200) {
        setContent {
            JwTheme(darkTheme = darkTheme) {
                val state = remember { ContextMenuState().apply { status = ContextMenuState.Status.Open(Rect(Offset(24f, 24f), 0f)) } }
                ContextMenuArea(
                    items = {
                        listOf(
                            ContextMenuItem("Copy as cURL") {},
                            ContextMenuItem("Copy URL") {},
                            ContextMenuItem("Copy request body", enabled = false) {},
                            ContextMenuItem("Copy response body") {},
                        )
                    },
                    state = state,
                ) {
                    Box(Modifier.fillMaxSize().background(JwTheme.colors.surface))
                }
            }
        }
        onAllNodes(isRoot()).onFirst().captureRoboImage(
            filePath = "screenshots/context-menu-${if (darkTheme) "dark" else "light"}.png",
            roborazziOptions = RoborazziOptions(
                compareOptions = RoborazziOptions.CompareOptions(changeThreshold = 0.001f),
            ),
        )
    }
}
