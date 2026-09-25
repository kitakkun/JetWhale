package com.kitakkun.jetwhale.host.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.awt.Cursor

/** Sizes of a [JwTable]. */
public object JwTableDefaults {
    /** Height of the header row. */
    public val headerHeight: Dp = 24.dp

    /** Height of a body row, the same as any other compact row. */
    public val rowHeight: Dp = JwMetrics.controlHeight

    /** The narrowest a column is dragged down to, unless the column sets its own. */
    public val minColumnWidth: Dp = 40.dp

    /** Width of the grab area at the trailing edge of each header cell. */
    public val resizeHandleWidth: Dp = 8.dp
}

/** How wide a [JwTableColumn] is. */
@Immutable
public sealed class JwColumnWidth {
    /** A fixed width, for columns whose content has a known size: a status code, a timestamp. */
    public class Fixed(public val width: Dp) : JwColumnWidth()

    /** A share of the width left after the fixed columns, in proportion to [weight]. */
    public class Weight(public val weight: Float) : JwColumnWidth()
}

/** What a [JwTableColumn] does with content wider than the column. */
public enum class JwColumnOverflow {
    /** One line, cut with an ellipsis. The row stays one control tall. */
    Ellipsis,

    /** Content wraps onto more lines and the row grows to fit the tallest cell. */
    Wrap,

    /** One line that scrolls sideways inside the cell — a URL read by dragging it. */
    Scroll,
}

/**
 * One column of a [JwTable]: its [header], its [width], and how a row's item renders in its
 * [cell]. The cell is a box the column's width; [overflow] says what happens to content wider
 * than that. A text-only column is simplest as [JwTableColumn.text]; a [cell] of your own should
 * draw its text with [JwTableCellText], which follows the column's [overflow] for it.
 *
 * @param T the row item type.
 * @param header the column name, shown once above the rows.
 * @param width how wide the column is; see [JwColumnWidth].
 * @param alignment where content sits inside a cell wider than it.
 * @param overflow how content wider than the column is handled; see [JwColumnOverflow].
 * @param minWidth the narrowest the user can drag the column to.
 * @param cell how an item renders in this column.
 */
@Immutable
public class JwTableColumn<T>(
    public val header: String,
    public val width: JwColumnWidth,
    public val alignment: Alignment.Horizontal = Alignment.Start,
    public val overflow: JwColumnOverflow = JwColumnOverflow.Ellipsis,
    public val minWidth: Dp = JwTableDefaults.minColumnWidth,
    public val cell: @Composable (item: T) -> Unit,
) {
    public companion object {
        /**
         * A column that shows one string per item in [style], following [overflow]: ellipsized,
         * wrapped, or scrolled sideways.
         *
         * @param header the column name.
         * @param width how wide the column is.
         * @param alignment where the text sits inside a wider cell.
         * @param overflow what happens to text wider than the column.
         * @param minWidth the narrowest the user can drag the column to.
         * @param style the text style; null for the body style.
         * @param text the string to show for an item.
         */
        public fun <T> text(
            header: String,
            width: JwColumnWidth,
            alignment: Alignment.Horizontal = Alignment.Start,
            overflow: JwColumnOverflow = JwColumnOverflow.Ellipsis,
            minWidth: Dp = JwTableDefaults.minColumnWidth,
            style: TextStyle? = null,
            text: (item: T) -> String,
        ): JwTableColumn<T> = JwTableColumn(header = header, width = width, alignment = alignment, overflow = overflow, minWidth = minWidth) { item ->
            JwTableCellText(text = text(item), style = style)
        }
    }
}

/** The overflow of the column a cell is being drawn in; [JwTableCellText] reads it. */
private val LocalJwColumnOverflow = compositionLocalOf { JwColumnOverflow.Ellipsis }

/**
 * Text inside a [JwTableColumn.cell], laid out the way the column's [JwColumnOverflow] asks: one
 * ellipsized line, wrapped lines, or one line the cell scrolls. Use it instead of [JwText] in a
 * custom cell so the column's setting is honored.
 *
 * @param text what to show.
 * @param style the text style; null for the body style.
 * @param color the text color; unspecified means the row's content color.
 */
@Composable
public fun JwTableCellText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle? = null,
    color: Color = Color.Unspecified,
) {
    val columnOverflow = LocalJwColumnOverflow.current
    JwText(
        text = text,
        modifier = modifier,
        style = style,
        color = color,
        overflow = if (columnOverflow == JwColumnOverflow.Ellipsis) TextOverflow.Ellipsis else TextOverflow.Clip,
        softWrap = columnOverflow != JwColumnOverflow.Scroll,
        maxLines = when (columnOverflow) {
            JwColumnOverflow.Ellipsis, JwColumnOverflow.Scroll -> 1
            JwColumnOverflow.Wrap -> Int.MAX_VALUE
        },
    )
}

/**
 * A lazy table: a header row of column names over a virtualized list of rows, each row a
 * [JwListItem] so it hovers, selects and reports `selected` like every other row. Columns are
 * declared once as [JwTableColumn]s and applied to every item; a [Weight] column absorbs the width
 * the [Fixed] ones leave.
 *
 * The user resizes a column by dragging the trailing edge of its header, and fits it to its content
 * by double-clicking that edge. A resized column keeps the width it was given, even if it was a
 * [Weight] column, and the remaining [Weight] columns share what is left; a drag stops where they
 * would go below their [JwTableColumn.minWidth]. [columnState] holds those widths — hoist it to
 * persist them.
 *
 * Rows are one compact control tall unless a column wraps ([JwColumnOverflow.Wrap]), in which
 * case a row grows to its tallest cell.
 *
 * @param items the rows, in display order — sort and filter before passing them.
 * @param columns the columns, in display order.
 * @param key a stable identity per item, so selection and scroll position survive reordering.
 * @param isSelected whether the row for an item is the current one.
 * @param onClick what selecting a row does; null for a read-only table.
 * @param state the list's scroll state; hoist it to scroll programmatically.
 * @param contentPadding padding around the rows, inside the scrolling area.
 * @param columnState the widths the user dragged; hoist it to persist them.
 * @param emptyContent what to show instead of rows while [items] is empty — a [JwEmptyState].
 */
@Composable
public fun <T> JwTable(
    items: List<T>,
    columns: List<JwTableColumn<T>>,
    modifier: Modifier = Modifier,
    key: ((item: T) -> Any)? = null,
    isSelected: (item: T) -> Boolean = { false },
    onClick: ((item: T) -> Unit)? = null,
    state: LazyListState = rememberLazyListState(),
    contentPadding: PaddingValues = PaddingValues(0.dp),
    columnState: JwTableColumnState = rememberJwTableColumnState(),
    emptyContent: (@Composable () -> Unit)? = null,
) {
    FitColumnEffect(columns, columnState)
    val density = LocalDensity.current
    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(JwTableDefaults.headerHeight)
                .background(JwTheme.colors.sidebarBackground)
                .padding(horizontal = JwSpacing.medium)
                .onSizeChanged { columnState.rowWidth = with(density) { it.width.toDp() } },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(JwSpacing.medium),
        ) {
            columns.forEach { column ->
                Cell(
                    column = column,
                    columnState = columnState,
                    modifier = Modifier.onSizeChanged { columnState.laidOutWidths[column.header] = with(density) { it.width.toDp() } },
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) {
                        JwText(
                            text = column.header,
                            style = JwTheme.textStyles.labelSmall,
                            color = JwTheme.colors.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(end = JwTableDefaults.resizeHandleWidth),
                        )
                        ResizeHandle(column = column, columns = columns, columnState = columnState, modifier = Modifier.align(Alignment.CenterEnd))
                    }
                }
            }
        }
        JwHorizontalDivider()
        if (items.isEmpty() && emptyContent != null) {
            emptyContent()
            return@Column
        }
        LazyColumn(
            state = state,
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding,
        ) {
            items(items = items, key = key) { item ->
                val cells: @Composable RowScope.() -> Unit = {
                    columns.forEach { column ->
                        Cell(column = column, columnState = columnState) { column.cell(item) }
                    }
                }
                if (onClick == null) {
                    ReadOnlyRow(selected = isSelected(item), content = cells)
                } else {
                    JwListItem(selected = isSelected(item), onClick = { onClick(item) }, content = cells)
                }
            }
        }
    }
}

/**
 * A row of a table without [JwTable]'s onClick: the same metrics and selection tint as
 * [JwListItem], in the ordinary text color, with no hover and nothing to click.
 */
@Composable
private fun ReadOnlyRow(selected: Boolean, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = JwMetrics.controlHeight)
            .clip(JwShapes.small)
            .background(if (selected) JwTheme.colors.selection else Color.Transparent)
            .padding(horizontal = JwSpacing.medium),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(JwSpacing.medium),
    ) {
        val contentColor = if (selected) JwTheme.colors.onSelection else JwTheme.colors.onSurface
        CompositionLocalProvider(LocalJwContentColor provides contentColor, content = { content() })
    }
}

/**
 * The grab area at the trailing edge of a header cell: dragging it resizes the column, and a
 * double-click fits the column to its widest laid-out content. A hairline shows while it is hovered
 * or dragged.
 */
@Composable
private fun <T> ResizeHandle(
    column: JwTableColumn<T>,
    columns: List<JwTableColumn<T>>,
    columnState: JwTableColumnState,
    modifier: Modifier,
) {
    val density = LocalDensity.current
    val interactionSource = remember(calculation = ::MutableInteractionSource)
    val hovered by interactionSource.collectIsHoveredAsState()
    val dragged by interactionSource.collectIsDraggedAsState()
    val dragState = rememberDraggableState(
        onDelta = onDelta@{ deltaPx ->
            val current = columnState.widths[column.header] ?: columnState.laidOutWidths[column.header] ?: return@onDelta
            val growth = with(density) { deltaPx.toDp() }
            val widest = columnState.widest(column, columns).coerceAtLeast(column.minWidth)
            columnState.widths += column.header to (current + growth).coerceIn(column.minWidth, widest)
        },
    )
    Box(
        modifier = modifier
            .width(JwTableDefaults.resizeHandleWidth)
            .fillMaxHeight()
            .hoverable(interactionSource)
            .pointerHoverIcon(PointerIcon(Cursor(Cursor.E_RESIZE_CURSOR)))
            .draggable(state = dragState, orientation = Orientation.Horizontal, interactionSource = interactionSource)
            .semantics { contentDescription = "Resize ${column.header}" }
            .pointerInput(column.header) {
                detectTapGestures(
                    onDoubleTap = {
                        columnState.fittedContentWidth = 0.dp
                        columnState.fitting = column.header
                    },
                )
            },
        contentAlignment = Alignment.CenterEnd,
    ) {
        if (hovered || dragged) JwVerticalDivider(modifier = Modifier.fillMaxHeight())
    }
}

/**
 * The widest [column] can be dragged to: the row, less the gaps and what every other column keeps.
 * A column the user sized or declared fixed keeps its width; one still sharing by weight keeps only
 * its minimum. Computed from declarations rather than the last layout, which lags a fast drag.
 */
private fun <T> JwTableColumnState.widest(column: JwTableColumn<T>, columns: List<JwTableColumn<T>>): Dp {
    val gaps = JwSpacing.medium * (columns.size - 1).coerceAtLeast(0)
    val kept = columns.filter { it !== column }.fold(0.dp) { sum, other ->
        sum + (widths[other.header] ?: (other.width as? JwColumnWidth.Fixed)?.width ?: other.minWidth)
    }
    return rowWidth - gaps - kept
}

/**
 * Finishes a double-click fit: gives the rows a frame to report their content widths for the column
 * being fitted, then sets the column to the widest of them.
 */
@Composable
private fun <T> FitColumnEffect(columns: List<JwTableColumn<T>>, columnState: JwTableColumnState) {
    val header = columnState.fitting ?: return
    LaunchedEffect(header) {
        withFrameNanos { }
        withFrameNanos { }
        val column = columns.firstOrNull { it.header == header }
        if (column != null && columnState.fittedContentWidth > 0.dp) {
            columnState.widths += header to columnState.fittedContentWidth.coerceAtLeast(column.minWidth)
        }
        columnState.fitting = null
    }
}

@Composable
private fun <T> RowScope.Cell(
    column: JwTableColumn<T>,
    columnState: JwTableColumnState,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val sizing = when (val width = columnState.widths[column.header]?.let(JwColumnWidth::Fixed) ?: column.width) {
        is JwColumnWidth.Fixed -> Modifier.width(width.width)
        is JwColumnWidth.Weight -> Modifier.weight(width.weight)
    }
    // Reads the content's natural width only while this column is being fitted, so ordinary layout
    // pays nothing for it.
    val fitProbe = Modifier.layout { measurable, constraints ->
        if (columnState.fitting == column.header) {
            val natural = measurable.maxIntrinsicWidth(constraints.maxHeight).toDp()
            if (natural > columnState.fittedContentWidth) columnState.fittedContentWidth = natural
        }
        val placeable = measurable.measure(constraints)
        layout(placeable.width, placeable.height) { placeable.place(0, 0) }
    }
    // Scrolling gives the content unbounded width; the other two keep it inside the cell, and
    // clipping catches a custom cell that ignores the setting.
    val overflow = when (column.overflow) {
        JwColumnOverflow.Scroll -> Modifier.horizontalScroll(rememberScrollState())
        JwColumnOverflow.Ellipsis, JwColumnOverflow.Wrap -> Modifier.clipToBounds()
    }
    Box(
        modifier = modifier.then(sizing).then(overflow).then(fitProbe),
        contentAlignment = when (column.alignment) {
            Alignment.End -> Alignment.CenterEnd
            Alignment.CenterHorizontally -> Alignment.Center
            else -> Alignment.CenterStart
        },
    ) {
        CompositionLocalProvider(LocalJwColumnOverflow provides column.overflow, content = content)
    }
}
