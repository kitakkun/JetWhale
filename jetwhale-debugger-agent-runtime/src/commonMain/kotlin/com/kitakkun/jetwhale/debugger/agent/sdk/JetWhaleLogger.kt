package com.kitakkun.jetwhale.debugger.agent.sdk

import io.ktor.client.plugins.logging.Logger

private typealias KermitLogger = co.touchlab.kermit.Logger

internal object JetWhaleLogger : Logger {

    override fun log(message: String) {
        KermitLogger.v(message)
    }

    fun d(message: String, throwable: Throwable? = null) {
        KermitLogger.d(message, throwable)
    }

    fun e(message: String, throwable: Throwable? = null) {
        KermitLogger.e(message, throwable)
    }

    fun i(message: String) {
        KermitLogger.i(message)
    }
}
