package com.kitakkun.jetwhale.host.ui

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** The spacing steps every JetWhale component is laid out on. */
public object JwSpacing {
    /** 2dp: the gap between a label and its count, or between stacked text lines. */
    public val xxs: Dp = 2.dp

    /** 4dp: the gap between icon buttons in a toolbar, or between tags. */
    public val xs: Dp = 4.dp

    /** 6dp: the gap between an icon and its label inside a control. */
    public val sm: Dp = 6.dp

    /** 8dp: the gap between controls in a row, and the padding of compact rows. */
    public val md: Dp = 8.dp

    /** 12dp: the padding of panels and panes, and the gap between form fields. */
    public val lg: Dp = 12.dp

    /** 16dp: the padding of dialogs and settings pages. */
    public val xl: Dp = 16.dp

    /** 24dp: the padding around an empty state. */
    public val xxl: Dp = 24.dp
}

/** Fixed heights of the compact controls, so custom rows line up with the built-in ones. */
public object JwMetrics {
    /** Height of a compact control: buttons, inputs, list rows. */
    public val controlHeight: Dp = 28.dp

    /** Height of a toolbar and of the sidebar header. */
    public val toolbarHeight: Dp = 36.dp

    /** Height of a section header row inside a list. */
    public val sectionHeaderHeight: Dp = 24.dp

    /** Icon size inside compact controls. */
    public val iconSize: Dp = 16.dp

    /** Width of the sidebar when expanded. */
    public val sidebarWidth: Dp = 280.dp

    /** Width of the sidebar when collapsed to icons. */
    public val railWidth: Dp = 44.dp

    /** Hairline width of borders and dividers. */
    public val borderWidth: Dp = 1.dp
}
