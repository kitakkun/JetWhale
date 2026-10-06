package com.kitakkun.jetwhale.host.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The column widths a user has dragged in a [JwTable], by [JwTableColumnKey]. A column with no entry
 * keeps its declared [JwColumnWidth]; a column with one becomes that fixed width, and the
 * [JwColumnWidth.Weight] columns share whatever is left.
 *
 * Hoist it to persist the widths: read [widths] into storage and write the stored map back into it.
 *
 * @param initialWidths widths to start from.
 */
@Stable
public class JwTableColumnState(initialWidths: Map<JwTableColumnKey, Dp>) {
    /** The widths the user set; assign to restore stored widths or to reset them. */
    public var widths: Map<JwTableColumnKey, Dp> by mutableStateOf(initialWidths)

    /** The width each column was last laid out at; what a drag starts from. */
    internal val laidOutWidths = mutableStateMapOf<JwTableColumnKey, Dp>()

    /** The column whose content is being measured for a double-click fit, if any. */
    internal var fitting: JwTableColumnKey? by mutableStateOf(null)

    /**
     * The widest content found for [fitting] in the rows laid out since the fit was asked for. Plain
     * rather than snapshot state: layout writes it, and reading it there must not invalidate layout.
     */
    internal var fittedContentWidth: Dp = 0.dp

    public companion object {
        /** Saves the [widths] as plain values, so a [JwTableColumnState] survives recreation. */
        public val Saver: Saver<JwTableColumnState, Any> = listSaver(
            save = { state -> state.widths.flatMap { (column, width) -> listOf(column.index, column.header, width.value) } },
            restore = { saved ->
                JwTableColumnState(saved.chunked(3).associate { (index, header, width) -> JwTableColumnKey(index as Int, header as String) to (width as Float).dp })
            },
        )
    }
}

/**
 * Which column of a [JwTable] a width in [JwTableColumnState.widths] belongs to: the column's
 * position among the table's columns, and its header. A width applies only while both match, so
 * columns that share a header keep widths of their own, and a stored width is ignored rather than
 * given to another column once the columns change.
 *
 * @param index the column's position in the table's columns, from 0.
 * @param header the column's header.
 */
@Immutable
public class JwTableColumnKey(public val index: Int, public val header: String) {
    override fun equals(other: Any?): Boolean = other is JwTableColumnKey && other.index == index && other.header == header

    override fun hashCode(): Int = 31 * index + header.hashCode()

    override fun toString(): String = "JwTableColumnKey(index=$index, header=$header)"
}

/** A [JwTableColumnState] with no dragged widths, kept across recompositions and recreation. */
@Composable
public fun rememberJwTableColumnState(): JwTableColumnState = rememberSaveable(saver = JwTableColumnState.Saver) { JwTableColumnState(emptyMap()) }
