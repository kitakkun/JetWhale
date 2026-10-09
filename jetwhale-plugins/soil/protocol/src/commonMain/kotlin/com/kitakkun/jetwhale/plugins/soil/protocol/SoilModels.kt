package com.kitakkun.jetwhale.plugins.soil.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * What part of the app's Soil cache the agent can read.
 *
 * @property clientClassName Simple name of the client the app handed the agent.
 * @property isClientReadable False when the client is not one of Soil's own caches (a decorator
 *   around one, say): the agent then sees no entries at all.
 * @property includesInactiveEntries Whether entries kept in the cache after their last user went
 *   away are listed; it needs the app to hand over the cache's policy too.
 * @property includesSubscriptions Whether the client holds subscriptions, which only Soil's
 *   `SwrCachePlus` does.
 */
@Serializable
data class SoilCacheCoverage(
    val clientClassName: String,
    val isClientReadable: Boolean,
    val includesInactiveEntries: Boolean,
    val includesSubscriptions: Boolean,
)

@Serializable
enum class SoilEntryKind { QUERY, INFINITE_QUERY, MUTATION, SUBSCRIPTION }

/** Where Soil holds an entry. */
@Serializable
enum class SoilEntryLocation {
    /** In the client's store: in use, or used within its keepAliveTime. */
    ACTIVE,

    /** Only in the policy's cache, kept after deactivation until its gcTime runs out. */
    INACTIVE,

    /** In the client's store, with an older copy still in the policy's cache. */
    ACTIVE_AND_CACHED,
}

/**
 * One query, infinite query, mutation or subscription as the agent last read it.
 *
 * @property handle Names the entry in requests. It stays the same for as long as Soil holds an
 *   entry under an equal id.
 * @property isObserved Whether a screen is attached to the entry right now. Always false for an
 *   [SoilEntryLocation.INACTIVE] entry.
 * @property replyRevision Changes whenever Soil replaces the entry's reply, even twice within the
 *   second its timestamps resolve to, so a host showing the value knows to read it again.
 * @property options The entry's options by name, durations in Kotlin's `Duration` notation. Soil
 *   keeps no options for an [SoilEntryLocation.INACTIVE] entry, so it carries the ones it had while
 *   active, or none when the agent never saw it active.
 * @property inactiveSinceEpochMillis When the agent saw the entry move from the store into the
 *   cache, by the app's clock; null while it is active, or when it was already cached when the agent
 *   first saw it.
 * @property inFlightSinceEpochMillis Since when, by the app's clock, the agent has seen the query
 *   fetching or the mutation running; null when neither is under way. For an entry that was already
 *   in flight when the agent first saw it, the fetch or run started earlier than this.
 * @property chunkParams For an infinite query, the param each loaded chunk was fetched with, in
 *   order; null for every other kind.
 */
@Serializable
data class SoilEntry(
    val handle: String,
    val kind: SoilEntryKind,
    val location: SoilEntryLocation,
    val id: SoilEntryId,
    val state: SoilEntryState,
    val isObserved: Boolean,
    val options: Map<String, String>,
    val replyRevision: Long,
    val inactiveSinceEpochMillis: Long?,
    val inFlightSinceEpochMillis: Long?,
    val chunkParams: List<String>?,
)

/**
 * An entry's Soil id, rendered for reading: Soil compares ids by [namespace] and tags, whatever
 * their types.
 *
 * @property className Simple name of the id's class, e.g. `QueryId`.
 * @property tags Each tag's `toString()`.
 */
@Serializable
data class SoilEntryId(
    val className: String,
    val namespace: String,
    val tags: List<String>,
)

/** The status Soil gives an entry. Only mutations are ever [IDLE]. */
@Serializable
enum class SoilStatus { IDLE, PENDING, SUCCESS, FAILURE }

/** What a query is doing right now. */
@Serializable
sealed interface SoilFetchStatus {
    @SerialName("soil/fetch/idle")
    @Serializable
    data object Idle : SoilFetchStatus

    /** Fetching; [isValidating] when the query already has data and is refreshing it. */
    @SerialName("soil/fetch/fetching")
    @Serializable
    data class Fetching(val isValidating: Boolean) : SoilFetchStatus

    /** Held back after an error until [unpauseAt], in epoch seconds. */
    @SerialName("soil/fetch/paused")
    @Serializable
    data class Paused(val unpauseAt: Long) : SoilFetchStatus
}

/** The error an entry last failed with. [message] is null when the throwable had none. */
@Serializable
data class SoilEntryError(
    val className: String,
    val message: String?,
)

/**
 * An entry's state, field for field as Soil keeps it. Timestamps are epoch seconds, and 0 means
 * Soil never set them: a reply with `replyUpdatedAt` 0 is initial or preloaded data.
 */
@Serializable
sealed interface SoilEntryState {
    val status: SoilStatus

    /** Whether the entry holds a reply, which a failed entry may still do from an earlier success. */
    val hasReply: Boolean
    val replyUpdatedAt: Long
    val error: SoilEntryError?
    val errorUpdatedAt: Long

    /** Whether a query is fetching or a mutation is running; a subscription never is. */
    val isInFlight: Boolean
        get() = when (this) {
            is Query -> fetchStatus is SoilFetchStatus.Fetching
            is Mutation -> status == SoilStatus.PENDING
            is Subscription -> false
        }

    /** The state of a query or an infinite query. [staleAt] is `Long.MAX_VALUE` when it never goes stale. */
    @SerialName("soil/state/query")
    @Serializable
    data class Query(
        override val status: SoilStatus,
        override val hasReply: Boolean,
        override val replyUpdatedAt: Long,
        override val error: SoilEntryError?,
        override val errorUpdatedAt: Long,
        val staleAt: Long,
        val fetchStatus: SoilFetchStatus,
        val isInvalidated: Boolean,
    ) : SoilEntryState

    /** The state of a mutation. [submittedAt] is the later of the reply and the error. */
    @SerialName("soil/state/mutation")
    @Serializable
    data class Mutation(
        override val status: SoilStatus,
        override val hasReply: Boolean,
        override val replyUpdatedAt: Long,
        override val error: SoilEntryError?,
        override val errorUpdatedAt: Long,
        val mutatedCount: Int,
        val submittedAt: Long,
    ) : SoilEntryState

    @SerialName("soil/state/subscription")
    @Serializable
    data class Subscription(
        override val status: SoilStatus,
        override val hasReply: Boolean,
        override val replyUpdatedAt: Long,
        override val error: SoilEntryError?,
        override val errorUpdatedAt: Long,
        val restartedAt: Long,
    ) : SoilEntryState
}

/** What the host can ask Soil to do with an entry. */
@Serializable
enum class SoilEntryAction {
    /** Marks a query invalidated, active or not; an active one refetches. */
    INVALIDATE,

    /** Asks an observed query to fetch if its data needs it, or an observed subscription to restart. */
    RESUME,

    /** Drops an inactive query or subscription from the cache. Active entries are never removed. */
    REMOVE_INACTIVE,
}
