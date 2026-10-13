package com.kitakkun.jetwhale.plugins.permissions.agent

import com.kitakkun.jetwhale.plugins.permissions.protocol.PermissionCategory
import com.kitakkun.jetwhale.plugins.permissions.protocol.PermissionChange
import com.kitakkun.jetwhale.plugins.permissions.protocol.PermissionState
import com.kitakkun.jetwhale.plugins.permissions.protocol.PermissionStatus
import kotlin.test.Test
import kotlin.test.assertEquals

class PermissionDiffTest {
    @Test
    fun `an unchanged read reports no change`() {
        val states = listOf(state("camera", PermissionStatus.Denied))

        assertEquals(emptyList(), diffPermissions(states, states, atEpochMillis = 1))
    }

    @Test
    fun `a status change is reported with the status before it`() {
        val changes = diffPermissions(
            before = listOf(state("camera", PermissionStatus.Denied), state("location", PermissionStatus.Denied)),
            after = listOf(state("camera", PermissionStatus.Granted), state("location", PermissionStatus.Denied)),
            atEpochMillis = 42,
        )

        assertEquals(listOf(PermissionChange("camera", "camera", PermissionStatus.Denied, PermissionStatus.Granted, note = null, 42)), changes)
    }

    @Test
    fun `a denial that leaves the status at Denied is reported with its new note`() {
        val changes = diffPermissions(
            before = listOf(state("camera", PermissionStatus.Denied, note = RuntimeDenial.Unknown.note)),
            after = listOf(state("camera", PermissionStatus.Denied, note = RuntimeDenial.Once.note)),
            atEpochMillis = 3,
        )

        assertEquals(listOf(PermissionChange("camera", "camera", PermissionStatus.Denied, PermissionStatus.Denied, RuntimeDenial.Once.note, 3)), changes)
    }

    @Test
    fun `permissions that appear or disappear change from or to nothing`() {
        val changes = diffPermissions(
            before = listOf(state("old", PermissionStatus.Granted)),
            after = listOf(state("new", PermissionStatus.NotDetermined)),
            atEpochMillis = 7,
        )

        assertEquals(
            listOf(
                PermissionChange("new", "new", null, PermissionStatus.NotDetermined, note = null, 7),
                PermissionChange("old", "old", PermissionStatus.Granted, null, note = null, 7),
            ),
            changes,
        )
    }

    private fun state(id: String, status: PermissionStatus, note: String? = null) = PermissionState(
        id = id,
        label = id,
        category = PermissionCategory.Runtime,
        protection = null,
        status = status,
        requestable = false,
        note = note,
    )
}
