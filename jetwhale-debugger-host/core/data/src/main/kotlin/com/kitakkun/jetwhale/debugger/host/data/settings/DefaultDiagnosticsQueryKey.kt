package com.kitakkun.jetwhale.debugger.host.data.settings

import com.kitakkun.jetwhale.debugger.host.model.DebuggingToolsDiagnostics
import com.kitakkun.jetwhale.debugger.host.model.DiagnosticsQueryKey
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import soil.query.QueryId
import soil.query.buildQueryKey

@ContributesBinding(AppScope::class)
@Inject
class DefaultDiagnosticsQueryKey : DiagnosticsQueryKey by buildQueryKey(
    id = QueryId("DefaultDiagnosticsQueryKey"),
    fetch = {
        val adbPath: String
        val adbPathDetectionProcess = ProcessBuilder()
            .command("which", "adb")
            .start()
        adbPathDetectionProcess
            .inputReader().useLines {
                adbPath = it.firstOrNull().orEmpty()
            }
        adbPathDetectionProcess.waitFor()

        DebuggingToolsDiagnostics(adbPath = adbPath)
    }
)
