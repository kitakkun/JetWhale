package com.kitakkun.jetwhale.plugins.permissions.agent

import com.kitakkun.jetwhale.plugins.permissions.protocol.PermissionCategory
import kotlin.test.Test
import kotlin.test.assertEquals

class ProtectionClassTest {
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
}
