package com.kitakkun.jetwhale.host.settings.logviewer.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.kitakkun.jetwhale.host.model.LogEntry
import com.kitakkun.jetwhale.host.model.LogLevel

@Composable
fun LogEntryRow(
    logEntry: LogEntry,
    modifier: Modifier = Modifier,
) {
    val backgroundColor = logEntry.level.backgroundColor
    val textColor = logEntry.level.textColor

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

@Composable
private fun LogTimestamp(timestamp: String) {
    Text(
        text = timestamp,
        style = MaterialTheme.typography.bodySmall,
        fontFamily = FontFamily.Monospace,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun LogLevelBadge(
    level: LogLevel,
    color: Color,
) {
    Text(
        text = level.name,
        style = MaterialTheme.typography.bodySmall,
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
    Text(
        text = message,
        style = MaterialTheme.typography.bodySmall,
        fontFamily = FontFamily.Monospace,
        color = color,
        modifier = modifier,
    )
}

private val LogLevel.backgroundColor: Color
    @Composable
    get() = when (this) {
        LogLevel.ERROR -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
        LogLevel.INFO -> MaterialTheme.colorScheme.surface
    }

private val LogLevel.textColor: Color
    @Composable
    get() = when (this) {
        LogLevel.ERROR -> MaterialTheme.colorScheme.error
        LogLevel.INFO -> MaterialTheme.colorScheme.onSurface
    }
