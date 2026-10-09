package com.kitakkun.jetwhale.plugins.soil.protocol

/**
 * Which [SoilEntryAction]s apply to which entries. The agent refuses an action by it and the host
 * disables the action by it, so both give the same reason.
 */
object SoilEntryActionPolicy {
    /** Why [action] does not apply to [entry], or null when it does. */
    fun refusalOf(entry: SoilEntry, action: SoilEntryAction): String? = when (action) {
        SoilEntryAction.INVALIDATE -> when (entry.kind) {
            SoilEntryKind.QUERY, SoilEntryKind.INFINITE_QUERY -> null
            SoilEntryKind.MUTATION -> "Soil does not invalidate mutations."
            SoilEntryKind.SUBSCRIPTION -> "Soil does not invalidate subscriptions; resume restarts one."
        }

        SoilEntryAction.RESUME -> when {
            entry.kind == SoilEntryKind.MUTATION -> "A mutation runs only when the app calls mutate."
            entry.location == SoilEntryLocation.INACTIVE -> "Resume reaches active entries only, and this one is inactive."
            !entry.isObserved -> "Nothing observes this entry, so Soil would ignore the resume."
            else -> null
        }

        SoilEntryAction.REMOVE_INACTIVE -> when {
            entry.kind == SoilEntryKind.MUTATION -> "Soil keeps no inactive mutations."
            entry.location != SoilEntryLocation.INACTIVE -> "The entry is active, and active entries are not removed."
            else -> null
        }
    }
}
