package com.kitakkun.jetwhale.host.model

import kotlin.time.Instant

data class LogEntry(
    val timestamp: Instant,
    val message: String,
    val level: LogLevel = LogLevel.INFO,
)

enum class LogLevel {
    INFO,
    ERROR,
}
