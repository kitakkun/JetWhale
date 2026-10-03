package com.kitakkun.jetwhale.host.model

import kotlin.time.Instant

/** @property id Unique among the entries of one run; the same line captured twice gets two ids. */
data class LogEntry(
    val id: Long,
    val timestamp: Instant,
    val message: String,
    val level: LogLevel = LogLevel.INFO,
)

enum class LogLevel {
    INFO,
    ERROR,
}
