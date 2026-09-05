package com.kitakkun.jetwhale.host.ui

import androidx.compose.foundation.background
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

/**
 * One column of a [JwTable]: its [header], its [width], and how a row's item renders in its
 * [cell]. The cell is a box the column's width; content that may be wider — a URL — scrolls or
 * ellipsizes inside it as the cell decides.
 *
 * @param T the row item type.
 */
@Immutable
public class JwTableColumn<T>(
    public val header: String,
    public val width: JwColumnWidth,
    public val alignment: Alignment.Horizontal = Alignment.Start,
    public val cell: @Composable (item: T) -> Unit,
)

/**
 * A lazy table: a header row of column names over a virtualized list of rows, each row a
 * [JwListItem] so it hovers, selects and reports `selected` like every other row. Columns are
 * declared once as [JwTableColumn]s and applied to every item; a [Weight] column absorbs the width
 * the [Fixed] ones leave.
 *
 * Rows are the same height as any compact control; a cell that needs more should be a detail
 * pane, not a taller row.
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
                    Text(
                        text = column.header,
                        style = MaterialTheme.typography.labelSmall,
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
    Box(
        modifier = sizing,
        contentAlignment = when (column.alignment) {
            Alignment.End -> Alignment.CenterEnd
            Alignment.CenterHorizontally -> Alignment.Center
            else -> Alignment.CenterStart
        },
    ) {
        content()
    }
}
