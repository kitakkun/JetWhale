package com.kitakkun.jetwhale.host.data.crash

import com.kitakkun.jetwhale.host.model.CrashRecoveryService
import com.kitakkun.jetwhale.host.model.DismissUncleanExitReportMutationKey
import com.kitakkun.jetwhale.host.model.LeaveSafeModeMutationKey
import com.kitakkun.jetwhale.host.model.SafeModeService
import com.kitakkun.jetwhale.host.model.SafeModeSubscriptionKey
import com.kitakkun.jetwhale.host.model.UncleanExitReportSubscriptionKey
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import soil.query.MutationId
import soil.query.MutationKey
import soil.query.SubscriptionId
import soil.query.buildMutationKey
import soil.query.buildSubscriptionKey

@Inject
@ContributesBinding(AppScope::class)
class DefaultUncleanExitReportSubscriptionKey(
    private val crashRecoveryService: CrashRecoveryService,
) : UncleanExitReportSubscriptionKey by buildSubscriptionKey(
    id = SubscriptionId("unclean_exit_report"),
    subscribe = { crashRecoveryService.uncleanExitReportFlow },
)

@Inject
@ContributesBinding(AppScope::class, binding = binding<DismissUncleanExitReportMutationKey>())
class DefaultDismissUncleanExitReportMutationKey(
    private val crashRecoveryService: CrashRecoveryService,
) : DismissUncleanExitReportMutationKey,
    MutationKey<Unit, Unit> by buildMutationKey(
        id = MutationId("dismiss_unclean_exit_report"),
        mutate = { crashRecoveryService.dismissUncleanExitReport() },
    )

@Inject
@ContributesBinding(AppScope::class)
class DefaultSafeModeSubscriptionKey(
    private val safeModeService: SafeModeService,
) : SafeModeSubscriptionKey by buildSubscriptionKey(
    id = SubscriptionId("safe_mode"),
    subscribe = { safeModeService.safeModeFlow },
)

@Inject
@ContributesBinding(AppScope::class, binding = binding<LeaveSafeModeMutationKey>())
class DefaultLeaveSafeModeMutationKey(
    private val safeModeService: SafeModeService,
) : LeaveSafeModeMutationKey,
    MutationKey<Unit, Unit> by buildMutationKey(
        id = MutationId("leave_safe_mode"),
        mutate = { safeModeService.leaveSafeMode() },
    )
