package com.kitakkun.jetwhale.host.model

data class DebuggingToolsDiagnostics(
    val adbPath: String,
    // Empty when the tool is not installed.
    val idbPath: String,
    val idbCompanionPath: String,
    val appDataPath: String,
)
