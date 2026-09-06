package com.kitakkun.jetwhale.host.ui.gallery

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runDesktopComposeUiTest
import com.github.takahirom.roborazzi.RoborazziOptions
import com.kitakkun.jetwhale.host.ui.JwTheme
import io.github.takahirom.roborazzi.captureRoboImage
import kotlin.test.Test

/**
 * Renders [JwGallery] in both built-in themes into `screenshots/` (git-ignored). CI is what
 * compares: the `Screenshot check` workflow renders `main` and the pull request on the same
 * runner, publishes both to companion branches, and comments on the PR with a side-by-side diff
 * of every image that changed. Locally, `./gradlew :jetwhale-host-ui:recordRoborazziJvm` writes
 * the images to look at.
 *
 * Rendering goes through Skia with the platform's fonts, so images from different machines are
 * not comparable; the small change threshold only absorbs anti-aliasing noise within one platform.
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
 * Fraction of pixels allowed to differ. Anti-aliasing noise stays under 0.01% of them; a changed
 * component moves whole rows.
 */
private const val CHANGE_THRESHOLD = 0.001f

/** Tall enough for the whole gallery; anything below it would be cut off silently. */
private const val GALLERY_HEIGHT = 1800
