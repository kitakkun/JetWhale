package com.kitakkun.jetwhale.plugins.permissions.agent

import com.kitakkun.jetwhale.plugins.permissions.protocol.PermissionCategory

/**
 * How an Android permission is classified, from `PermissionInfo.protectionLevel`. Kept free of
 * Android types so the mapping can be tested everywhere.
 */
internal data class ProtectionClass(val category: PermissionCategory, val label: String)

// Values of android.content.pm.PermissionInfo, which are part of the public SDK and do not change.
private const val PROTECTION_MASK_BASE = 0xf
private const val PROTECTION_NORMAL = 0
private const val PROTECTION_DANGEROUS = 1
private const val PROTECTION_SIGNATURE = 2
private const val PROTECTION_SIGNATURE_OR_SYSTEM = 3
private const val PROTECTION_INTERNAL = 4
private const val PROTECTION_FLAG_APPOP = 0x40

/**
 * A dangerous permission is asked for at runtime; one carrying the app-op flag is a special access
 * the user grants on a settings screen; everything else is decided at install.
 */
internal fun classifyProtection(protectionLevel: Int): ProtectionClass {
    val base = when (protectionLevel and PROTECTION_MASK_BASE) {
        PROTECTION_NORMAL -> "normal"
        PROTECTION_DANGEROUS -> "dangerous"
        PROTECTION_SIGNATURE -> "signature"
        PROTECTION_SIGNATURE_OR_SYSTEM -> "signature|system"
        PROTECTION_INTERNAL -> "internal"
        else -> "level ${protectionLevel and PROTECTION_MASK_BASE}"
    }
    val appOp = protectionLevel and PROTECTION_FLAG_APPOP != 0
    val category = when {
        protectionLevel and PROTECTION_MASK_BASE == PROTECTION_DANGEROUS -> PermissionCategory.Runtime
        appOp -> PermissionCategory.SpecialAccess
        else -> PermissionCategory.InstallTime
    }
    return ProtectionClass(category = category, label = if (appOp) "$base|appop" else base)
}
