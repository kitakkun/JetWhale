package com.kitakkun.jetwhale.host.settings.logviewer.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.kitakkun.jetwhale.host.model.LogEntry
import com.kitakkun.jetwhale.host.model.LogLevel
import com.kitakkun.jetwhale.host.ui.JwText
import com.kitakkun.jetwhale.host.ui.JwTheme

@Composable
fun LogEntryRow(
    logEntry: LogEntry,
    modifier: Modifier = Modifier,
) {
    val backgroundColor = logEntry.level.backgroundColor
    val textColor = logEntry.level.textColor

    // Per-item SelectionContainer: the log list is a LazyColumn, so selection is scoped to a
    // single line. Wrapping the whole LazyColumn in one SelectionContainer is avoided because it
    // forces composition of off-screen items and has known perf/UX issues.
    SelectionContainer {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .background(backgroundColor)
                .padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            LogTimestamp(
                timestamp = logEntry.timestamp.toString()
                    .substringAfter("T")
                    .substringBefore("."),
            )

            LogLevelBadge(
                level = logEntry.level,
                color = textColor,
            )

            LogMessage(
                message = logEntry.message,
                color = textColor,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun LogTimestamp(timestamp: String) {
    JwText(
        text = timestamp,
        style = JwTheme.textStyles.bodySmall,
        fontFamily = FontFamily.Monospace,
        color = JwTheme.colors.textSecondary,
    )
}

@Composable
private fun LogLevelBadge(
    level: LogLevel,
    color: Color,
) {
    JwText(
        text = level.name,
        style = JwTheme.textStyles.bodySmall,
        fontFamily = FontFamily.Monospace,
        color = color,
        modifier = Modifier.padding(end = 8.dp),
    )
}

@Composable
private fun LogMessage(
    message: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    JwText(
        text = message,
        style = JwTheme.textStyles.bodySmall,
        fontFamily = FontFamily.Monospace,
        color = color,
        modifier = modifier,
    )
}

private val LogLevel.backgroundColor: Color
    @Composable
    get() = when (this) {
        LogLevel.ERROR -> JwTheme.colors.errorContainer.copy(alpha = 0.3f)
        LogLevel.INFO -> JwTheme.colors.surface
    }

private val LogLevel.textColor: Color
    @Composable
    get() = when (this) {
        LogLevel.ERROR -> JwTheme.colors.error
        LogLevel.INFO -> JwTheme.colors.onSurface
    }
