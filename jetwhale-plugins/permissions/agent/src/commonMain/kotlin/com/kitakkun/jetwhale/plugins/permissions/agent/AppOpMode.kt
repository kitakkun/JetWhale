package com.kitakkun.jetwhale.plugins.permissions.agent

import com.kitakkun.jetwhale.plugins.permissions.protocol.PermissionStatus

// The values of android.app.AppOpsManager's public MODE_ constants, which never change.
private const val MODE_ALLOWED = 0
private const val MODE_DEFAULT = 3
private const val MODE_FOREGROUND = 4

/**
 * What an Android app op's mode says about the permission behind it, or null for MODE_DEFAULT,
 * which leaves the answer to the permission's own grant. Kept free of Android types so the mapping
 * can be tested everywhere.
 */
internal fun statusOfAppOpMode(mode: Int): PermissionStatus? = when (mode) {
    MODE_ALLOWED, MODE_FOREGROUND -> PermissionStatus.Granted
    MODE_DEFAULT -> null
    else -> PermissionStatus.Denied
}
