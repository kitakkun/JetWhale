package com.kitakkun.jetwhale.plugins.permissions.agent

import com.kitakkun.jetwhale.plugins.permissions.protocol.PermissionCategory
import com.kitakkun.jetwhale.plugins.permissions.protocol.PermissionChange
import com.kitakkun.jetwhale.plugins.permissions.protocol.PermissionState
import com.kitakkun.jetwhale.plugins.permissions.protocol.PermissionStatus
import kotlin.test.Test
import kotlin.test.assertEquals

class PermissionClassificationTest {
    @Test
    fun `a dangerous permission is requested at runtime`() {
        assertEquals(ProtectionClass(PermissionCategory.Runtime, "dangerous"), classifyProtection(1))
    }

    @Test
    fun `a normal permission is decided at install`() {
        assertEquals(ProtectionClass(PermissionCategory.InstallTime, "normal"), classifyProtection(0))
    }

    @Test
    fun `a signature permission with the app-op flag is a special access`() {
        assertEquals(ProtectionClass(PermissionCategory.SpecialAccess, "signature|appop"), classifyProtection(2 or 0x40))
    }

    @Test
    fun `flags above the base level do not change a dangerous permission`() {
        assertEquals(PermissionCategory.Runtime, classifyProtection(1 or 0x1000).category)
    }

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

        assertEquals(listOf(PermissionChange("camera", "camera", PermissionStatus.Denied, PermissionStatus.Granted, 42)), changes)
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
                PermissionChange("new", "new", null, PermissionStatus.NotDetermined, 7),
                PermissionChange("old", "old", PermissionStatus.Granted, null, 7),
            ),
            changes,
        )
    }

    private fun state(id: String, status: PermissionStatus) = PermissionState(
        id = id,
        label = id,
        category = PermissionCategory.Runtime,
        protection = null,
        status = status,
        requestable = false,
        note = null,
    )
}
