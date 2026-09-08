package com.kitakkun.jetwhale.host.settings.logviewer.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.kitakkun.jetwhale.host.model.LogEntry
import com.kitakkun.jetwhale.host.settings.Res
import com.kitakkun.jetwhale.host.settings.log_viewer_no_logs
import com.kitakkun.jetwhale.host.ui.JwText
import com.kitakkun.jetwhale.host.ui.JwTheme
import kotlinx.collections.immutable.ImmutableList
import org.jetbrains.compose.resources.stringResource

@Composable
fun LogListContent(
    logs: ImmutableList<LogEntry>,
    autoScroll: Boolean,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()

    LaunchedEffect(logs.size) {
        if (autoScroll && logs.isNotEmpty()) {
            listState.animateScrollToItem(logs.size - 1)
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(JwTheme.colors.neutralContainer),
    ) {
        if (logs.isEmpty()) {
            EmptyLogsPlaceholder()
        } else {
            LogList(
                logs = logs,
                listState = listState,
            )
        }
    }
}

@Composable
private fun EmptyLogsPlaceholder() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        JwText(
            text = stringResource(Res.string.log_viewer_no_logs),
            style = JwTheme.textStyles.body,
            color = JwTheme.colors.textSecondary,
        )
    }
}

@Composable
private fun LogList(
    logs: ImmutableList<LogEntry>,
    listState: androidx.compose.foundation.lazy.LazyListState,
) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
    ) {
        items(
            items = logs,
            key = { "${it.timestamp}-${it.message.hashCode()}" },
        ) { logEntry ->
            LogEntryRow(logEntry)
        }
    }
}
