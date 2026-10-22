package com.kitakkun.jetwhale.plugins.soil.agent

import com.kitakkun.jetwhale.plugins.soil.protocol.SoilEntryKind
import com.kitakkun.jetwhale.plugins.soil.protocol.SoilEntryLocation
import kotlinx.coroutines.flow.StateFlow
import soil.query.core.DataModel
import soil.query.core.UniqueId

/** One entry of the cache: queries and mutations live in separate stores, so the id alone is not enough. */
internal data class SoilEntryKey(val kind: SoilEntryKind, val id: UniqueId)

/**
 * One entry as read from Soil, before it is put on the wire.
 *
 * @property model The entry's `QueryState`, `MutationState` or `SubscriptionState`.
 * @property stateFlow Where an active entry's state changes; null for an inactive one, whose
 *   cached state only changes by being replaced.
 */
internal data class SoilCacheRecord(
    val key: SoilEntryKey,
    val location: SoilEntryLocation,
    val model: DataModel<*>,
    val isObserved: Boolean,
    val options: Map<String, String>,
    val stateFlow: StateFlow<*>?,
)
