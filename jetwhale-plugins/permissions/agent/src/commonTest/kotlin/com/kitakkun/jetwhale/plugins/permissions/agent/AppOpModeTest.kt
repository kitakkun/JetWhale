package com.kitakkun.jetwhale.plugins.permissions.agent

import com.kitakkun.jetwhale.plugins.permissions.protocol.PermissionStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

// Mode values as android.app.AppOpsManager defines them.
private const val MODE_ALLOWED = 0
private const val MODE_IGNORED = 1
private const val MODE_ERRORED = 2
private const val MODE_DEFAULT = 3
private const val MODE_FOREGROUND = 4

class AppOpModeTest {
    @Test
    fun `an allowed op grants the permission`() {
        assertEquals(PermissionStatus.Granted, statusOfAppOpMode(MODE_ALLOWED))
        assertEquals(PermissionStatus.Granted, statusOfAppOpMode(MODE_FOREGROUND))
    }

    @Test
    fun `an ignored or erroring op denies the permission`() {
        assertEquals(PermissionStatus.Denied, statusOfAppOpMode(MODE_IGNORED))
        assertEquals(PermissionStatus.Denied, statusOfAppOpMode(MODE_ERRORED))
    }

    @Test
    fun `the default mode leaves the answer to the permission's grant`() {
        assertNull(statusOfAppOpMode(MODE_DEFAULT))
    }
}
