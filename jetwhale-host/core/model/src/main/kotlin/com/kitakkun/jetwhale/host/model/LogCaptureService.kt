package com.kitakkun.jetwhale.host.model

import kotlinx.coroutines.flow.StateFlow

interface LogCaptureService {
    val logs: StateFlow<List<LogEntry>>
    fun startCapture()
    fun stopCapture()
    fun clearLogs()
}
