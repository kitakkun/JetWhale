package com.kitakkun.jetwhale.host.data.crash

import com.kitakkun.jetwhale.host.model.CrashRecoveryService
import com.kitakkun.jetwhale.host.model.SafeMode
import com.kitakkun.jetwhale.host.model.SafeModeReason
import com.kitakkun.jetwhale.host.model.SafeModeRequest
import com.kitakkun.jetwhale.host.model.SafeModeService
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Startup crashes in a row after which the next run starts without plugins. */
private const val STARTUP_CRASHES_BEFORE_SAFE_MODE = 2

/**
 * Decides once per run whether plugins stay unloaded. It reads the crash count lazily, so
 * [CrashRecoveryService.onStartup] must have run before anything first asks — which main() does
 * before the rest of the host starts.
 */
@Inject
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class DefaultSafeModeService(
    private val safeModeRequest: SafeModeRequest,
    private val crashRecoveryService: CrashRecoveryService,
) : SafeModeService {
    private val mutableSafeModeFlow: MutableStateFlow<SafeMode?> by lazy {
        MutableStateFlow(
            when {
                safeModeRequest.requested -> SafeMode(SafeModeReason.RequestedOnCommandLine)
                crashRecoveryService.consecutiveStartupCrashes >= STARTUP_CRASHES_BEFORE_SAFE_MODE -> SafeMode(SafeModeReason.RepeatedStartupCrashes)
                else -> null
            },
        )
    }

    override val safeModeFlow: StateFlow<SafeMode?> get() = mutableSafeModeFlow

    override fun leaveSafeMode() {
        mutableSafeModeFlow.value = null
    }
}
