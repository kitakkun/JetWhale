package com.kitakkun.jetwhale.host.data.settings

import com.kitakkun.jetwhale.host.data.AppDataDirectoryProvider
import com.kitakkun.jetwhale.host.data.util.findAdbPath
import com.kitakkun.jetwhale.host.model.DebuggingToolsDiagnostics
import com.kitakkun.jetwhale.host.model.DiagnosticsQueryKey
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import soil.query.QueryId
import soil.query.buildQueryKey

@ContributesBinding(AppScope::class)
@Inject
class DefaultDiagnosticsQueryKey(
    private val appDataDirectoryProvider: AppDataDirectoryProvider,
) : DiagnosticsQueryKey by buildQueryKey(
    id = QueryId("DefaultDiagnosticsQueryKey"),
    fetch = {
        val adbPath = findAdbPath()
        val appDataPath = appDataDirectoryProvider.getAppDataPath()
        DebuggingToolsDiagnostics(
            adbPath = adbPath,
            appDataPath = appDataPath,
        )
    }
)
