package com.kitakkun.jetwhale.plugins.background.protocol

import com.kitakkun.jetwhale.protocol.messaging.JetWhaleEvent
import com.kitakkun.jetwhale.protocol.messaging.JetWhaleRequest
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** The pluginId shared by the Background Work agent and host plugins. */
const val BACKGROUND_WORK_PLUGIN_ID: String = "com.kitakkun.jetwhale.background"

/** Asks for the current snapshot; the host sends it once per connection. */
@SerialName("background/get_work")
@Serializable
data object GetBackgroundWork : JetWhaleRequest<BackgroundWorkSnapshot>

/** Pushed by the agent whenever what it reports changes. */
@SerialName("background/work_changed")
@Serializable
data class BackgroundWorkChanged(val snapshot: BackgroundWorkSnapshot) : JetWhaleEvent

/** Cancels [target] in the source named [source]. */
@SerialName("background/cancel_work")
@Serializable
data class CancelWork(
    val source: String,
    val target: CancelTarget,
) : JetWhaleRequest<WorkOperationResult>

/** Runs the work [id] of the source named [source] as soon as the source allows. */
@SerialName("background/run_work_now")
@Serializable
data class RunWorkNow(
    val source: String,
    val id: String,
) : JetWhaleRequest<WorkOperationResult>

/**
 * Reply to a request that acts on work: [error] is null when the source accepted it, and
 * [message] says what was actually done, which may be less than asked (see [RunWorkNow]).
 */
@SerialName("background/operation_result")
@Serializable
data class WorkOperationResult(
    val message: String?,
    val error: String?,
)
