package com.kitakkun.jetwhale.host.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Sizes of a [JwKeyValueRow]. */
public object JwKeyValueRowDefaults {
    /** The default width of the key column: fits a header name like "Content-Type". */
    public val keyWidth: Dp = 140.dp
}

/**
 * One "key: value" line of a detail view — a header, a property, a setting's current value. Keys
 * share a column of [keyWidth] so a list of rows lines up; values are selectable and, when
 * [monospace], set in [JwTypography.code].
 *
 * @param key the property's name, in the left column.
 * @param value the property's value, selectable.
 * @param keyWidth the width of the key column; pass the same value to every row of a list.
 * @param monospace sets the value in [JwTypography.code].
 * @param wrap `true` lets a long value take several lines — for text worth reading in full. `false`
 * keeps it on one line that scrolls sideways, so a long id cannot push the rows below it away.
 * @param trailingContent controls that act on the value: a copy button, a link.
 */
@Composable
public fun JwKeyValueRow(
    key: String,
    value: String,
    modifier: Modifier = Modifier,
    keyWidth: Dp = JwKeyValueRowDefaults.keyWidth,
    monospace: Boolean = false,
    wrap: Boolean = true,
    trailingContent: (@Composable RowScope.() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = JwSpacing.tiny),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(JwSpacing.medium),
    ) {
        Text(
            text = key,
            style = MaterialTheme.typography.bodySmall,
            color = JwTheme.colors.textSecondary,
            modifier = Modifier.width(keyWidth),
        )
        val valueStyle = if (monospace) JwTypography.code else MaterialTheme.typography.bodySmall
        SelectionContainer(modifier = Modifier.weight(1f)) {
            if (wrap) {
                Text(text = value, style = valueStyle)
            } else {
                Text(
                    text = value,
                    style = valueStyle,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                )
            }
        }
        trailingContent?.invoke(this)
    }
}
