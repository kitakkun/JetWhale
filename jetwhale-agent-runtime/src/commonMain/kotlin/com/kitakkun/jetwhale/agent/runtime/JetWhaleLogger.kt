package com.kitakkun.jetwhale.agent.runtime

import io.ktor.client.plugins.logging.Logger

private typealias KermitLogger = co.touchlab.kermit.Logger

internal object JetWhaleLogger : Logger {
    private const val tagPrefix = "JetWhale"
    private val runtimeLogger: KermitLogger = KermitLogger.withTag("${tagPrefix}-runtime")
    private val ktorLogger: KermitLogger = KermitLogger.withTag("${tagPrefix}-ktor")

    private var enabled: Boolean = true
    private var minLogLevel: LogLevel = LogLevel.WARN
    internal var ktorLogLevel: KtorLogLevel = KtorLogLevel.NONE
        private set

    fun setEnabled(enabled: Boolean) {
        this.enabled = enabled
    }

    fun setLogLevel(logLevel: LogLevel) {
        this.minLogLevel = logLevel
    }

    fun setKtorLogLevel(ktorLogLevel: KtorLogLevel) {
        this.ktorLogLevel = ktorLogLevel
    }

    override fun log(message: String) {
        if (!enabled) return
        if (ktorLogLevel == KtorLogLevel.NONE) return
        ktorLogger.v(message)
    }

    fun v(message: String) {
        if (!enabled) return
        if (minLogLevel > LogLevel.VERBOSE) return
        runtimeLogger.v(message)
    }

    fun d(message: String, throwable: Throwable? = null) {
        if (!enabled) return
        if (minLogLevel > LogLevel.DEBUG) return
        runtimeLogger.d(message, throwable)
    }

    fun e(message: String, throwable: Throwable? = null) {
        if (!enabled) return
        if (minLogLevel > LogLevel.ERROR) return
        runtimeLogger.e(message, throwable)
    }

    fun i(message: String) {
        if (!enabled) return
        if (minLogLevel > LogLevel.INFO) return
        runtimeLogger.i(message)
    }
}
