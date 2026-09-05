package com.kitakkun.jetwhale.host.ui.gallery

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runDesktopComposeUiTest
import com.github.takahirom.roborazzi.RoborazziOptions
import com.kitakkun.jetwhale.host.ui.JwTheme
import io.github.takahirom.roborazzi.captureRoboImage
import kotlin.test.Test

/**
 * Renders [JwGallery] in both built-in themes and compares against the recorded images under
 * `screenshots/`. Run `./gradlew :jetwhale-host-ui:recordRoborazziJvm` after a deliberate visual
 * change, and review the diff the verify task writes under `build/outputs/roborazzi` when one fails.
 *
 * Rendering goes through Skia with the platform's fonts, so the images are only comparable
 * between macOS machines — and even those differ by a few anti-aliased pixels between OS
 * releases, which is what the change threshold absorbs. A real change moves far more than that.
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
        onRoot().captureRoboImage(
            filePath = "screenshots/gallery-${if (darkTheme) "dark" else "light"}.png",
            roborazziOptions = RoborazziOptions(
                compareOptions = RoborazziOptions.CompareOptions(changeThreshold = CHANGE_THRESHOLD),
            ),
        )
    }
}

/**
 * Fraction of pixels allowed to differ. Anti-aliasing between macOS releases moves under 0.01% of
 * them; a changed component moves whole rows.
 */
private const val CHANGE_THRESHOLD = 0.001f

/** Tall enough for the whole gallery; anything below it would be cut off silently. */
private const val GALLERY_HEIGHT = 1800
