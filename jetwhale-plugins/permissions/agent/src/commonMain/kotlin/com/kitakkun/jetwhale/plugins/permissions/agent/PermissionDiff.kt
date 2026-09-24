package com.kitakkun.jetwhale.plugins.permissions.agent

import com.kitakkun.jetwhale.plugins.permissions.protocol.PermissionChange
import com.kitakkun.jetwhale.plugins.permissions.protocol.PermissionState

/**
 * The permissions whose status differs between two reads, in the order of [after] followed by
 * those that disappeared. A permission present on one side only changes from or to null.
 */
internal fun diffPermissions(before: List<PermissionState>, after: List<PermissionState>, atEpochMillis: Long): List<PermissionChange> {
    val beforeById = before.associateBy(PermissionState::id)
    val afterIds = after.map(PermissionState::id).toSet()
    val changed = after.mapNotNull { current ->
        val previous = beforeById[current.id]
        if (previous?.status == current.status) return@mapNotNull null
        PermissionChange(id = current.id, label = current.label, from = previous?.status, to = current.status, observedAtEpochMillis = atEpochMillis)
    }
    val removed = before.filterNot { it.id in afterIds }.map { gone ->
        PermissionChange(id = gone.id, label = gone.label, from = gone.status, to = null, observedAtEpochMillis = atEpochMillis)
    }
    return changed + removed
}
