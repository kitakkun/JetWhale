package com.kitakkun.jetwhale.host.ui.gallery

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runDesktopComposeUiTest
import com.kitakkun.jetwhale.host.ui.JwTheme
import io.github.takahirom.roborazzi.captureRoboImage
import kotlin.test.Test

/**
 * Renders [JwGallery] in both built-in themes and compares against the recorded images under
 * `screenshots/`. Run `./gradlew :jetwhale-host-ui:recordRoborazziJvm` after a deliberate visual
 * change, and review the diff the verify task writes under `build/outputs/roborazzi` when one fails.
 *
 * Rendering goes through Skia with the fonts bundled in the JDK, so the images are stable across
 * machines as long as the same JDK and Compose versions are used.
 */
@OptIn(ExperimentalTestApi::class)
class JwGalleryScreenshotTest {

    @Test
    fun `gallery in the light theme`() = captureGallery(darkTheme = false)

    @Test
    fun `gallery in the dark theme`() = captureGallery(darkTheme = true)

    private fun captureGallery(darkTheme: Boolean) = runDesktopComposeUiTest(width = GALLERY_WIDTH, height = GALLERY_HEIGHT) {
        setContent {
            JwTheme(darkTheme = darkTheme) {
                JwGallery()
            }
        }
        onRoot().captureRoboImage("screenshots/gallery-${if (darkTheme) "dark" else "light"}.png")
    }
}

/** Tall enough for the whole gallery; anything below it would be cut off silently. */
private const val GALLERY_HEIGHT = 1800
