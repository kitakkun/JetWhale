package com.kitakkun.jetwhale.plugins.permissions.protocol

import kotlinx.serialization.Serializable

/**
 * Every permission the agent can report on this platform.
 *
 * @property platform "Android", "iOS", "JVM" or "Web".
 * @property unsupportedReason Why nothing is reported, when the platform has no permission model
 *   the agent can read; null when [permissions] is the full answer.
 */
@Serializable
data class PermissionReport(
    val platform: String,
    val unsupportedReason: String?,
    val permissions: List<PermissionState>,
)

/**
 * One permission as the app currently holds it.
 *
 * @property id Identifies the permission in requests: the Android permission name, or a stable
 *   name such as "ios.camera".
 * @property label A short human-readable name.
 * @property protection The Android protection level ("normal", "dangerous", "signature", ...);
 *   null where the platform has none.
 * @property requestable Whether [RequestPermission] can do anything for it right now.
 * @property note What the state cannot tell on its own, or why it cannot be requested.
 */
@Serializable
data class PermissionState(
    val id: String,
    val label: String,
    val category: PermissionCategory,
    val protection: String?,
    val status: PermissionStatus,
    val requestable: Boolean,
    val note: String?,
)

@Serializable
enum class PermissionCategory {
    /** Asked for with a system dialog while the app runs (Android "dangerous", iOS privacy). */
    Runtime,

    /** Granted, or not, when the app is installed; nothing to ask for. */
    InstallTime,

    /** Toggled by the user on a settings screen of its own (overlay, exact alarms, ...). */
    SpecialAccess,
}

@Serializable
enum class PermissionStatus {
    Granted,
    Denied,

    /** The user has not been asked yet. */
    NotDetermined,

    /** Granted in part: a subset of photos, approximate location. */
    Limited,

    /** Blocked by policy (parental controls, device management); the user cannot change it. */
    Restricted,
}

/** A state the agent saw change, with the state before it. */
@Serializable
data class PermissionChange(
    val id: String,
    val label: String,
    val from: PermissionStatus?,
    val to: PermissionStatus?,
    val observedAtEpochMillis: Long,
)
