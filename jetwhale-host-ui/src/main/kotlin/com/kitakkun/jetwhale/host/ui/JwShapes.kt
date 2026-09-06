package com.kitakkun.jetwhale.host.ui

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/** Tight corner radii: a tool window is made of panes and rows, not cards and pills. */
public object JwShapes {
    /** 3dp: tags, menu items, the box of a checkbox. */
    public val extraSmall: CornerBasedShape = RoundedCornerShape(3.dp)

    /** 4dp: controls — buttons, inputs, list rows. */
    public val small: CornerBasedShape = RoundedCornerShape(4.dp)

    /** 6dp: panels and menus. */
    public val medium: CornerBasedShape = RoundedCornerShape(6.dp)

    /** 8dp: dialogs. */
    public val large: CornerBasedShape = RoundedCornerShape(8.dp)
}
