package com.kitakkun.jetwhale.host.settings.logviewer

import com.kitakkun.jetwhale.host.model.LogEntry
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

data class LogViewerScreenUiState(
    val logs: ImmutableList<LogEntry> = persistentListOf(),
    val autoScroll: Boolean = true,
    val filterText: String = "",
)
