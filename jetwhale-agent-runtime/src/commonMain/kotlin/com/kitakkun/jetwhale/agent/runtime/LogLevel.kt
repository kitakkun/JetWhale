package com.kitakkun.jetwhale.agent.runtime

/**
 * Log levels for JetWhale agent runtime logging
 */
public enum class LogLevel {
    VERBOSE,
    DEBUG,
    INFO,
    WARN,
    ERROR,
    ASSERT,
}

/**
 * Log levels for Ktor HTTP client logging
 */
public enum class KtorLogLevel {
    ALL,
    HEADERS,
    BODY,
    NONE,
}
