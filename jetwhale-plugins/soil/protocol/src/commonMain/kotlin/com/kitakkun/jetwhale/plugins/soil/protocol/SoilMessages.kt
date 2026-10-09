package com.kitakkun.jetwhale.plugins.soil.protocol

import com.kitakkun.jetwhale.protocol.messaging.JetWhaleEvent
import com.kitakkun.jetwhale.protocol.messaging.JetWhaleRequest
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** The pluginId shared by the Soil Inspector agent and host plugins. */
const val SOIL_PLUGIN_ID: String = "com.kitakkun.jetwhale.soil"

/**
 * Asks the agent for every entry it can see in the app's Soil cache. The host sends it once per
 * connection; [SoilEntriesChanged] then reports what changes after it.
 */
@SerialName("soil/get_cache_snapshot")
@Serializable
data object GetSoilCacheSnapshot : JetWhaleRequest<SoilCacheSnapshot>

/**
 * Reply to [GetSoilCacheSnapshot].
 *
 * @property coverage What part of the cache the agent can read.
 * @property revision Orders this snapshot among the [SoilEntriesChanged] events: an event whose
 *   revision is not greater was already accounted for here.
 * @property agentEpochSeconds The app's clock when the snapshot was taken, in epoch seconds, the
 *   unit Soil records its timestamps in. Staleness is judged against this clock, not the host's.
 */
@SerialName("soil/cache_snapshot")
@Serializable
data class SoilCacheSnapshot(
    val coverage: SoilCacheCoverage,
    val entries: List<SoilEntry>,
    val revision: Long,
    val agentEpochSeconds: Long,
)

/**
 * Pushed by the agent when entries appear, change or go away after a [GetSoilCacheSnapshot].
 * Changes that happen close together arrive as one event.
 *
 * @property upserts Entries that are new or changed, each in full.
 * @property removedHandles Entries Soil no longer holds, active or inactive.
 * @property revision Greater than the revision of every snapshot and event sent before it.
 * @property agentEpochSeconds The app's clock when the changes were read, in epoch seconds.
 */
@SerialName("soil/entries_changed")
@Serializable
data class SoilEntriesChanged(
    val upserts: List<SoilEntry>,
    val removedHandles: List<String>,
    val revision: Long,
    val agentEpochSeconds: Long,
) : JetWhaleEvent

/** Asks for the value the entry named by [handle] holds: its last reply. */
@SerialName("soil/get_entry_value")
@Serializable
data class GetSoilEntryValue(val handle: String) : JetWhaleRequest<SoilEntryValue>

/** Runs [action] on the entry named by [handle]. */
@SerialName("soil/run_entry_action")
@Serializable
data class RunSoilEntryAction(
    val handle: String,
    val action: SoilEntryAction,
) : JetWhaleRequest<SoilEntryActionResult>

/** Reply to [RunSoilEntryAction]: [error] is null when the action was handed to Soil, and says why not otherwise. */
@SerialName("soil/entry_action_result")
@Serializable
data class SoilEntryActionResult(val error: String?)
