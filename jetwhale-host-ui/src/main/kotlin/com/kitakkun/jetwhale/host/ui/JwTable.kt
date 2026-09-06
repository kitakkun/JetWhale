package com.kitakkun.jetwhale.host.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Sizes of a [JwTable]. */
public object JwTableDefaults {
    /** Height of the header row. */
    public val headerHeight: Dp = 24.dp

    /** Height of a body row, the same as any other compact row. */
    public val rowHeight: Dp = JwMetrics.controlHeight
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
 * @param cell how an item renders in this column.
 */
@Immutable
public class JwTableColumn<T>(
    public val header: String,
    public val width: JwColumnWidth,
    public val alignment: Alignment.Horizontal = Alignment.Start,
    public val overflow: JwColumnOverflow = JwColumnOverflow.Ellipsis,
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
         * @param style the text style; null for the body style.
         * @param text the string to show for an item.
         */
        public fun <T> text(
            header: String,
            width: JwColumnWidth,
            alignment: Alignment.Horizontal = Alignment.Start,
            overflow: JwColumnOverflow = JwColumnOverflow.Ellipsis,
            style: TextStyle? = null,
            text: (item: T) -> String,
        ): JwTableColumn<T> = JwTableColumn(header = header, width = width, alignment = alignment, overflow = overflow) { item ->
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
    when (LocalJwColumnOverflow.current) {
        JwColumnOverflow.Ellipsis -> JwText(
            text = text,
            modifier = modifier,
            style = style,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        JwColumnOverflow.Wrap -> JwText(text = text, modifier = modifier, style = style, color = color)

        JwColumnOverflow.Scroll -> JwText(
            text = text,
            modifier = modifier,
            style = style,
            color = color,
            maxLines = 1,
            softWrap = false,
        )
    }
}

/**
 * A lazy table: a header row of column names over a virtualized list of rows, each row a
 * [JwListItem] so it hovers, selects and reports `selected` like every other row. Columns are
 * declared once as [JwTableColumn]s and applied to every item; a [Weight] column absorbs the width
 * the [Fixed] ones leave.
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
    emptyContent: (@Composable () -> Unit)? = null,
) {
    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(JwTableDefaults.headerHeight)
                .background(JwTheme.colors.sidebarBackground)
                .padding(horizontal = JwSpacing.medium),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(JwSpacing.medium),
        ) {
            columns.forEach { column ->
                Cell(column) {
                    JwText(
                        text = column.header,
                        style = JwTheme.textStyles.labelSmall,
                        color = JwTheme.colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
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
                JwListItem(
                    selected = isSelected(item),
                    onClick = { onClick?.invoke(item) },
                    enabled = onClick != null,
                ) {
                    columns.forEach { column ->
                        Cell(column) { column.cell(item) }
                    }
                }
            }
        }
    }
}

@Composable
private fun <T> RowScope.Cell(column: JwTableColumn<T>, content: @Composable () -> Unit) {
    val sizing = when (val width = column.width) {
        is JwColumnWidth.Fixed -> Modifier.width(width.width)
        is JwColumnWidth.Weight -> Modifier.weight(width.weight)
    }
    // Scrolling gives the content unbounded width; the other two keep it inside the cell, and
    // clipping catches a custom cell that ignores the setting.
    val overflow = when (column.overflow) {
        JwColumnOverflow.Scroll -> Modifier.horizontalScroll(rememberScrollState())
        JwColumnOverflow.Ellipsis, JwColumnOverflow.Wrap -> Modifier.clipToBounds()
    }
    Box(
        modifier = sizing.then(overflow),
        contentAlignment = when (column.alignment) {
            Alignment.End -> Alignment.CenterEnd
            Alignment.CenterHorizontally -> Alignment.Center
            else -> Alignment.CenterStart
        },
    ) {
        CompositionLocalProvider(LocalJwColumnOverflow provides column.overflow, content = content)
    }
}
