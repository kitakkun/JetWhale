package com.kitakkun.jetwhale.plugins.permissions.protocol

import com.kitakkun.jetwhale.protocol.messaging.JetWhaleEvent
import com.kitakkun.jetwhale.protocol.messaging.JetWhaleRequest
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** The pluginId shared by the Permissions agent and host plugins. */
const val PERMISSIONS_PLUGIN_ID: String = "com.kitakkun.jetwhale.permissions"

/** Asks the agent for the current state of every permission it can report. */
@SerialName("permissions/get_permissions")
@Serializable
data object GetPermissions : JetWhaleRequest<PermissionReport>

/**
 * Asks the agent to request the permission [id] from the user: a runtime permission shows the
 * system dialog, a special access opens its settings screen. The reply says what was started, not
 * what the user chose; the choice arrives later as a [PermissionsChanged] event.
 */
@SerialName("permissions/request_permission")
@Serializable
data class RequestPermission(val id: String) : JetWhaleRequest<PermissionActionResult>

/** Asks the agent to open the app's page in the system settings. */
@SerialName("permissions/open_app_settings")
@Serializable
data object OpenAppSettings : JetWhaleRequest<PermissionActionResult>

/** Reply to a request that starts something on the device: [error] is null when it was started. */
@SerialName("permissions/action_result")
@Serializable
data class PermissionActionResult(
    val message: String,
    val error: String?,
)

/** Pushed by the agent whenever it sees a permission's state change, whoever changed it. */
@SerialName("permissions/permissions_changed")
@Serializable
data class PermissionsChanged(val changes: List<PermissionChange>) : JetWhaleEvent
