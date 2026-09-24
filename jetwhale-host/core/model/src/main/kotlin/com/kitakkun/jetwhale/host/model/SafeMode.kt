package com.kitakkun.jetwhale.host.model

import kotlinx.coroutines.flow.StateFlow
import soil.query.MutationKey
import soil.query.SubscriptionKey

/** Whether `--safe-mode` was passed on the command line. */
@JvmInline
value class SafeModeRequest(val requested: Boolean)

enum class SafeModeReason {
    RequestedOnCommandLine,
    RepeatedStartupCrashes,
}

/**
 * A run in which no plugin instance is created, so a plugin that crashes the host cannot stop it
 * from starting. Nothing about it is persisted: the next launch decides afresh.
 */
@JvmInline
value class SafeMode(val reason: SafeModeReason)

interface SafeModeService {
    /** Null outside safe mode. */
    val safeModeFlow: StateFlow<SafeMode?>

    /** Lets plugins load again for the rest of this run. */
    fun leaveSafeMode()
}

typealias SafeModeSubscriptionKey = SubscriptionKey<SafeMode?>

interface LeaveSafeModeMutationKey : MutationKey<Unit, Unit>
