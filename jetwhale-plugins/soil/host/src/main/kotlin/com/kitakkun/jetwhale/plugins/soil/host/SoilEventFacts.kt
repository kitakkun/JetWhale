package com.kitakkun.jetwhale.plugins.soil.host

import com.kitakkun.jetwhale.host.ui.JwTone
import com.kitakkun.jetwhale.plugins.soil.protocol.SoilEvent
import com.kitakkun.jetwhale.plugins.soil.protocol.SoilEventKind
import kotlinx.serialization.Serializable

/** What the timeline can be narrowed to; each [SoilEventKind] falls in one. */
@Serializable
internal enum class SoilEventCategory { FETCHES, INVALIDATIONS, MUTATIONS, SUBSCRIPTIONS, LIFECYCLE }

internal val SoilEventCategory.label: String
    get() = when (this) {
        SoilEventCategory.FETCHES -> "Fetches"
        SoilEventCategory.INVALIDATIONS -> "Invalidations"
        SoilEventCategory.MUTATIONS -> "Mutations"
        SoilEventCategory.SUBSCRIPTIONS -> "Subscriptions"
        SoilEventCategory.LIFECYCLE -> "Lifecycle"
    }

internal val SoilEventKind.category: SoilEventCategory
    get() = when (this) {
        SoilEventKind.FETCH_STARTED, SoilEventKind.FETCH_SUCCEEDED, SoilEventKind.FETCH_FAILED, SoilEventKind.FETCH_PAUSED, SoilEventKind.DATA_UPDATED -> SoilEventCategory.FETCHES
        SoilEventKind.INVALIDATED -> SoilEventCategory.INVALIDATIONS
        SoilEventKind.MUTATION_STARTED, SoilEventKind.MUTATION_SUCCEEDED, SoilEventKind.MUTATION_FAILED -> SoilEventCategory.MUTATIONS
        SoilEventKind.SUBSCRIPTION_DATA_RECEIVED, SoilEventKind.SUBSCRIPTION_FAILED, SoilEventKind.SUBSCRIPTION_RESTARTED -> SoilEventCategory.SUBSCRIPTIONS
        SoilEventKind.APPEARED, SoilEventKind.BECAME_ACTIVE, SoilEventKind.BECAME_INACTIVE, SoilEventKind.REMOVED, SoilEventKind.OBSERVED, SoilEventKind.UNOBSERVED -> SoilEventCategory.LIFECYCLE
    }

internal val SoilEventKind.label: String
    get() = when (this) {
        SoilEventKind.APPEARED -> "Appeared"
        SoilEventKind.BECAME_ACTIVE -> "Became active"
        SoilEventKind.BECAME_INACTIVE -> "Became inactive"
        SoilEventKind.REMOVED -> "Removed"
        SoilEventKind.OBSERVED -> "Observed"
        SoilEventKind.UNOBSERVED -> "Unobserved"
        SoilEventKind.FETCH_STARTED -> "Fetch started"
        SoilEventKind.FETCH_SUCCEEDED -> "Fetch succeeded"
        SoilEventKind.FETCH_FAILED -> "Fetch failed"
        SoilEventKind.FETCH_PAUSED -> "Fetch paused"
        SoilEventKind.DATA_UPDATED -> "Data updated"
        SoilEventKind.INVALIDATED -> "Invalidated"
        SoilEventKind.MUTATION_STARTED -> "Mutation started"
        SoilEventKind.MUTATION_SUCCEEDED -> "Mutation succeeded"
        SoilEventKind.MUTATION_FAILED -> "Mutation failed"
        SoilEventKind.SUBSCRIPTION_DATA_RECEIVED -> "Received"
        SoilEventKind.SUBSCRIPTION_FAILED -> "Subscription failed"
        SoilEventKind.SUBSCRIPTION_RESTARTED -> "Restarted"
    }

internal val SoilEventKind.tone: JwTone
    get() = when (this) {
        SoilEventKind.FETCH_FAILED, SoilEventKind.MUTATION_FAILED, SoilEventKind.SUBSCRIPTION_FAILED -> JwTone.Error
        SoilEventKind.FETCH_PAUSED, SoilEventKind.INVALIDATED -> JwTone.Warning
        SoilEventKind.FETCH_SUCCEEDED, SoilEventKind.MUTATION_SUCCEEDED, SoilEventKind.SUBSCRIPTION_DATA_RECEIVED, SoilEventKind.DATA_UPDATED -> JwTone.Success
        SoilEventKind.FETCH_STARTED, SoilEventKind.MUTATION_STARTED, SoilEventKind.SUBSCRIPTION_RESTARTED -> JwTone.Accent
        SoilEventKind.APPEARED, SoilEventKind.BECAME_ACTIVE, SoilEventKind.BECAME_INACTIVE, SoilEventKind.REMOVED, SoilEventKind.OBSERVED, SoilEventKind.UNOBSERVED -> JwTone.Neutral
    }

/**
 * How the user narrowed the timeline; kept across sessions.
 *
 * @property categories The categories shown; empty shows them all.
 * @property isSelectedEntryOnly Shows only the events of the entry selected in the list.
 */
@Serializable
internal data class SoilTimelineSettings(
    val categories: Set<SoilEventCategory>,
    val isSelectedEntryOnly: Boolean,
) {
    /** Whether the timeline shows [event] while the entry [selectedHandle] names is selected. */
    fun shows(event: SoilEvent, selectedHandle: String?): Boolean = (categories.isEmpty() || event.kind.category in categories) &&
        (!isSelectedEntryOnly || event.handle == selectedHandle)

    companion object {
        val Initial = SoilTimelineSettings(categories = emptySet(), isSelectedEntryOnly = false)
    }
}
