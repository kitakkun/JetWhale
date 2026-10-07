package com.kitakkun.jetwhale.tools.docsscreenshots

import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.SkikoComposeUiTest
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.unit.Density
import org.jetbrains.skia.Image
import java.io.File

/**
 * Renders docs shots headlessly, once in the light and once in the dark theme, and writes each image
 * into [imagesDirectory].
 */
class DocsShotRecorder(private val imagesDirectory: DocsImagesDirectory) {
    /**
     * Records [shot]: [showState] sets the content for the theme it is given, brings it to the state
     * the page shows (a selected row, an open menu), and returns the node to capture.
     *
     * Focus is released before the capture, through the scene since the test API cannot clear it. On
     * desktop a click moves focus, and the ring of the control clicked last, or a text field's
     * blinking caret, would otherwise be in the picture.
     */
    @OptIn(ExperimentalTestApi::class, InternalComposeUiApi::class)
    fun record(shot: DocsShot, showState: suspend SkikoComposeUiTest.(darkTheme: Boolean) -> SemanticsNodeInteraction) {
        for (darkTheme in listOf(false, true)) {
            val density = Density(shot.density)
            val surfaceSize = with(density) { Size(shot.surfaceSize.width.toPx(), shot.surfaceSize.height.toPx()) }
            runSkikoComposeUiTest(size = surfaceSize, density = density) {
                val node = showState(darkTheme)
                runOnUiThread { scene.focusManager.releaseFocus() }
                val image = node.captureToImage()
                check(image.width == shot.imageWidthPx) {
                    "${shot.page}/${shot.name} captured ${image.width} px wide; ${shot.displayWidth} CSS px needs ${shot.imageWidthPx}"
                }
                imagesDirectory.writeWebpIfChanged(
                    page = shot.page,
                    fileName = shot.fileName(darkTheme),
                    image = Image.makeFromBitmap(image.asSkiaBitmap()),
                )
            }
        }
    }

    companion object {
        /** The recorder for the `docs/images` directory the `recordDocsScreenshots` task names. */
        fun forImagesDirectoryProperty(): DocsShotRecorder {
            val path = checkNotNull(System.getProperty(IMAGES_DIRECTORY_PROPERTY)) {
                "$IMAGES_DIRECTORY_PROPERTY is not set; run ./gradlew recordDocsScreenshots"
            }
            return DocsShotRecorder(DocsImagesDirectory(File(path)))
        }

        private const val IMAGES_DIRECTORY_PROPERTY = "jetwhale.docs.imagesDir"
    }
}

/**
 * The whole surface: the content's root, with every popup open over it, since popups draw onto the
 * same surface.
 */
@OptIn(ExperimentalTestApi::class)
fun SkikoComposeUiTest.onSurface(): SemanticsNodeInteraction = onAllNodes(isRoot()).onFirst()

/**
 * Clicks the node with the mouse, as on a desktop, then moves the pointer off the window so that no
 * hover tint is left in the picture.
 */
fun SemanticsNodeInteraction.mouseClickThenMovePointerAway(): SemanticsNodeInteraction = performMouseInput {
    click()
    moveTo(OFF_WINDOW)
}

/** A pointer position past the top left of any shot's surface. */
private val OFF_WINDOW = Offset(-10_000f, -10_000f)
