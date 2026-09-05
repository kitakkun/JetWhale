package com.kitakkun.jetwhale.host.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/** Material icons are authored on a 24-unit grid; the paths below come from that set. */
private const val MATERIAL_VIEWPORT = 24f
private val MaterialGlyphSize = 24.dp

/**
 * The few glyphs the components themselves draw. Plugins bring their own icon set; nothing here is
 * meant to grow into one.
 */
public object JwIcons {
    /** A downward chevron: the trailing glyph of a dropdown. */
    public val ChevronDown: ImageVector by lazy {
        icon("ChevronDown", "M16.59 8.59 12 13.17 7.41 8.59 6 10l6 6 6-6z")
    }

    /** A rightward chevron: the collapsed state of a section or tree node; rotate it 90° for expanded. */
    public val ChevronRight: ImageVector by lazy {
        icon("ChevronRight", "M10 6 8.59 7.41 13.17 12l-4.58 4.59L10 18l6-6z")
    }

    /** A cross: dismiss a dialog, clear a field. */
    public val Close: ImageVector by lazy {
        icon("Close", "M19 6.41 17.59 5 12 10.59 6.41 5 5 6.41 10.59 12 5 17.59 6.41 19 12 13.41 17.59 19 19 17.59 13.41 12z")
    }

    /** A magnifier: the leading glyph of a search field. */
    public val Search: ImageVector by lazy {
        icon(
            "Search",
            "M15.5 14h-.79l-.28-.27A6.471 6.471 0 0 0 16 9.5 6.5 6.5 0 1 0 9.5 16c1.61 0 3.09-.59 4.23-1.57l.27.28v.79l5 4.99L20.49 19l-4.99-5zm-6 0C7.01 14 5 11.99 5 9.5S7.01 5 9.5 5 14 7.01 14 9.5 11.99 14 9.5 14z",
        )
    }

    /** Three dots: an overflow menu. */
    public val MoreHorizontal: ImageVector by lazy {
        icon(
            "MoreHorizontal",
            "M6 10c-1.1 0-2 .9-2 2s.9 2 2 2 2-.9 2-2-.9-2-2-2zm12 0c-1.1 0-2 .9-2 2s.9 2 2 2 2-.9 2-2-.9-2-2-2zm-6 0c-1.1 0-2 .9-2 2s.9 2 2 2 2-.9 2-2-.9-2-2-2z",
        )
    }

    /** A check mark: the selected item of a menu, a ticked checkbox. */
    public val Check: ImageVector by lazy {
        icon("Check", "M9 16.17 4.83 12l-1.42 1.41L9 19 21 7l-1.41-1.41z")
    }

    private fun icon(name: String, pathData: String): ImageVector = ImageVector.Builder(
        name = "JwIcons.$name",
        defaultWidth = MaterialGlyphSize,
        defaultHeight = MaterialGlyphSize,
        viewportWidth = MATERIAL_VIEWPORT,
        viewportHeight = MATERIAL_VIEWPORT,
    ).addPath(
        pathData = addPathNodes(pathData),
        fill = SolidColor(Color.Black),
    ).build()
}
