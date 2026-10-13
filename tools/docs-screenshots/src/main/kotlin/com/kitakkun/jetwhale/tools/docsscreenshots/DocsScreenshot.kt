package com.kitakkun.jetwhale.tools.docsscreenshots

import androidx.compose.ui.unit.DpSize

/**
 * One screenshot in the user guides: `docs/images/<page>/<name>-light.webp` and its `-dark` twin.
 *
 * The image is rendered at twice the width the page displays it at, so it stays sharp on a
 * high-density screen. A screenshot renders its content on a surface of [surfaceSize] at [density] and
 * captures either the whole surface or one node of it; either way the captured image must be
 * `2 × displayWidthCssPx` pixels wide.
 *
 * @property page the guide page's file name without `.md`, which is also the image's directory.
 * @property name the screenshot's id on that page.
 * @property surfaceSize the size the content lays out in.
 * @property density pixels per dp; 2 draws the UI at the size the page displays it.
 * @property displayWidthCssPx the width the page gives the image, in CSS pixels.
 */
data class DocsScreenshot(
    val page: String,
    val name: String,
    val surfaceSize: DpSize,
    val density: Float,
    val displayWidthCssPx: Int,
) {
    val imageWidthPx: Int get() = displayWidthCssPx * 2

    fun fileName(darkTheme: Boolean): String = "$name-${if (darkTheme) "dark" else "light"}.webp"
}
