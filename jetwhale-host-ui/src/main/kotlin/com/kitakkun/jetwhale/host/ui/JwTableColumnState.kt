package com.kitakkun.jetwhale.host.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.mapSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The column widths a user has dragged in a [JwTable], by column header. A column with no entry
 * keeps its declared [JwColumnWidth]; a column with one becomes that fixed width, and the
 * [JwColumnWidth.Weight] columns share whatever is left. Headers identify columns, so they must be
 * unique within a table.
 *
 * Hoist it to persist the widths: read [widths] into storage and write the stored map back into it.
 *
 * @param initialWidths widths to start from, by header.
 */
@Stable
public class JwTableColumnState(initialWidths: Map<String, Dp>) {
    /** The widths the user set, by header; assign to restore stored widths or to reset them. */
    public var widths: Map<String, Dp> by mutableStateOf(initialWidths)

    /** The width each column was last laid out at, by header; what a drag starts from. */
    internal val laidOutWidths = mutableStateMapOf<String, Dp>()

    /** The header row's width inside its padding, which bounds how far a column can grow. */
    internal var rowWidth: Dp by mutableStateOf(0.dp)

    /** The header whose content is being measured for a double-click fit, if any. */
    internal var fitting: String? by mutableStateOf(null)

    /**
     * The widest content found for [fitting] in the rows laid out since the fit was asked for. Plain
     * rather than snapshot state: layout writes it, and reading it there must not invalidate layout.
     */
    internal var fittedContentWidth: Dp = 0.dp

    public companion object {
        /** Saves the [widths] as plain numbers, so a [JwTableColumnState] survives recreation. */
        public val Saver: Saver<JwTableColumnState, Any> = mapSaver(
            save = { state -> state.widths.mapValues { (_, width) -> width.value } },
            restore = { saved -> JwTableColumnState(saved.mapValues { (_, width) -> (width as Float).dp }) },
        )
    }
}

/** A [JwTableColumnState] with no dragged widths, kept across recompositions and recreation. */
@Composable
public fun rememberJwTableColumnState(): JwTableColumnState = rememberSaveable(saver = JwTableColumnState.Saver) { JwTableColumnState(emptyMap()) }
