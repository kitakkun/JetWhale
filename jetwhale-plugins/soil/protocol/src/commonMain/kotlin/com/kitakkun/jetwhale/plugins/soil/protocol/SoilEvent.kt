package com.kitakkun.jetwhale.plugins.soil.protocol

import kotlinx.serialization.Serializable

/**
 * Something that happened to an entry, as the agent saw it between two readings of the cache.
 *
 * The agent reads the cache whenever an active entry's state changes, but Soil's state flows keep
 * only the latest state, so a transition shorter than a reading can be missed: a fetch that
 * finishes before it is seen shows up as [SoilEventKind.DATA_UPDATED] without a duration.
 *
 * @property sequence Increases by one per event the agent records, across connections.
 * @property atEpochMillis The app's clock when the agent saw the change.
 * @property durationMillis For a fetch or mutation that ended, how long it ran from when the agent
 *   saw it start; null when the start was not seen.
 * @property detail The error of a failure, or what else the kind alone does not say.
 */
@Serializable
data class SoilEvent(
    val sequence: Long,
    val atEpochMillis: Long,
    val handle: String,
    val entryKind: SoilEntryKind,
    val entryId: SoilEntryId,
    val kind: SoilEventKind,
    val durationMillis: Long?,
    val detail: String?,
)

@Serializable
enum class SoilEventKind {
    /** The entry was seen for the first time, in the store or in the cache. */
    APPEARED,

    /** A cached entry was taken back into the store by a screen. */
    BECAME_ACTIVE,

    /** The entry left the store for the cache after its last screen let go of it. */
    BECAME_INACTIVE,

    /** Soil no longer holds the entry. */
    REMOVED,

    OBSERVED,
    UNOBSERVED,

    FETCH_STARTED,
    FETCH_SUCCEEDED,
    FETCH_FAILED,

    /** Soil holds the query back after an error until a set time. */
    FETCH_PAUSED,

    /** The reply changed without the fetch being seen: a short fetch, or data set directly. */
    DATA_UPDATED,
    INVALIDATED,

    MUTATION_STARTED,
    MUTATION_SUCCEEDED,
    MUTATION_FAILED,

    SUBSCRIPTION_DATA_RECEIVED,
    SUBSCRIPTION_FAILED,
    SUBSCRIPTION_RESTARTED,
}
