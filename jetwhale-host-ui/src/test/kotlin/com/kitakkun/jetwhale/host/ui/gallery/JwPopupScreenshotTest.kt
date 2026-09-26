package com.kitakkun.jetwhale.host.ui.gallery

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.RoborazziOptions
import com.kitakkun.jetwhale.host.ui.JwDropdownButton
import com.kitakkun.jetwhale.host.ui.JwMenuItem
import com.kitakkun.jetwhale.host.ui.JwTheme
import io.github.takahirom.roborazzi.captureRoboImage
import kotlin.test.Test

/**
 * An open menu over the sidebar's chrome, the backdrop it is hardest to tell apart from, in both
 * built-in themes. Compared by CI like [JwGalleryScreenshotTest].
 */
@OptIn(ExperimentalTestApi::class)
class JwPopupScreenshotTest {

    @Test
    fun `an open menu over the sidebar in the light theme`() = captureOpenMenu(darkTheme = false)

    @Test
    fun `an open menu over the sidebar in the dark theme`() = captureOpenMenu(darkTheme = true)

    private fun captureOpenMenu(darkTheme: Boolean) = runDesktopComposeUiTest(width = 420, height = 260) {
        setContent {
            JwTheme(darkTheme = darkTheme) {
                Box(Modifier.fillMaxSize().background(JwTheme.colors.surface)) {
                    Box(Modifier.width(280.dp).fillMaxHeight().background(JwTheme.colors.sidebarBackground).padding(12.dp)) {
                        JwDropdownButton(text = "JetWhale Demo", expanded = true, onExpandedChange = {}) {
                            JwMenuItem(text = "JetWhale Demo", onClick = {}, selected = true)
                            JwMenuItem(text = "Another app", onClick = {})
                            JwMenuItem(text = "A third app", onClick = {})
                        }
                    }
                }
            }
        }
        onAllNodes(isRoot()).onFirst().captureRoboImage(
            filePath = "screenshots/popup-menu-${if (darkTheme) "dark" else "light"}.png",
            roborazziOptions = RoborazziOptions(
                compareOptions = RoborazziOptions.CompareOptions(changeThreshold = 0.001f),
            ),
        )
    }
}
