package com.kitakkun.jetwhale.host.model

import kotlinx.datetime.Instant

data class LogEntry(
    val timestamp: Instant,
    val message: String,
    val level: LogLevel = LogLevel.INFO,
)

enum class LogLevel {
    INFO,
    ERROR,
}
