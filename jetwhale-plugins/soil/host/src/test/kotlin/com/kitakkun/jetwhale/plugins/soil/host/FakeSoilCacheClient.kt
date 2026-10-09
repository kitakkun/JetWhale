package com.kitakkun.jetwhale.plugins.soil.host

import com.kitakkun.jetwhale.plugins.soil.protocol.SoilCacheCoverage
import com.kitakkun.jetwhale.plugins.soil.protocol.SoilCacheSnapshot
import com.kitakkun.jetwhale.plugins.soil.protocol.SoilEntry
import com.kitakkun.jetwhale.plugins.soil.protocol.SoilEntryAction
import com.kitakkun.jetwhale.plugins.soil.protocol.SoilEntryActionResult
import com.kitakkun.jetwhale.plugins.soil.protocol.SoilEntryId
import com.kitakkun.jetwhale.plugins.soil.protocol.SoilEntryKind
import com.kitakkun.jetwhale.plugins.soil.protocol.SoilEntryLocation
import com.kitakkun.jetwhale.plugins.soil.protocol.SoilEntryState
import com.kitakkun.jetwhale.plugins.soil.protocol.SoilEntryValue
import com.kitakkun.jetwhale.plugins.soil.protocol.SoilFetchStatus
import com.kitakkun.jetwhale.plugins.soil.protocol.SoilStatus
import com.kitakkun.jetwhale.plugins.soil.protocol.SoilValueEncoding
import kotlinx.serialization.json.JsonPrimitive
import kotlin.time.Clock
import kotlin.time.Instant

internal const val HOST_NOW = 1_000L

internal val readableCoverage = SoilCacheCoverage(clientClassName = "SwrCache", isClientReadable = true, includesInactiveEntries = true, includesSubscriptions = false)

internal object FixedClock : Clock {
    override fun now(): Instant = Instant.fromEpochSeconds(HOST_NOW)
}

internal class FakeSoilCacheClient(
    var snapshot: SoilCacheSnapshot,
) : SoilCacheClient {
    var actionResult = SoilEntryActionResult(error = null)
    val valueRequests = mutableListOf<String>()
    val actionRequests = mutableListOf<Pair<String, SoilEntryAction>>()

    override suspend fun takeSnapshot(): SoilCacheSnapshot = snapshot

    override suspend fun readValue(handle: String): SoilEntryValue {
        valueRequests += handle
        return SoilEntryValue.Json(encoding = SoilValueEncoding.CLASS_SERIALIZERS, json = JsonPrimitive("value of $handle"))
    }

    override suspend fun runAction(handle: String, action: SoilEntryAction): SoilEntryActionResult {
        actionRequests += handle to action
        return actionResult
    }
}

internal fun queryEntry(
    handle: String,
    namespace: String,
    location: SoilEntryLocation = SoilEntryLocation.ACTIVE,
    status: SoilStatus = SoilStatus.SUCCESS,
    replyUpdatedAt: Long = 900,
    staleAt: Long = 950,
    isObserved: Boolean = true,
) = SoilEntry(
    handle = handle,
    kind = SoilEntryKind.QUERY,
    location = location,
    id = SoilEntryId(className = "QueryId", namespace = namespace, tags = emptyList()),
    state = SoilEntryState.Query(
        status = status,
        hasReply = status == SoilStatus.SUCCESS,
        replyUpdatedAt = replyUpdatedAt,
        error = null,
        errorUpdatedAt = 0,
        staleAt = staleAt,
        fetchStatus = SoilFetchStatus.Idle,
        isInvalidated = false,
    ),
    isObserved = isObserved,
    options = emptyMap(),
)

internal fun mutationEntry(handle: String, namespace: String) = SoilEntry(
    handle = handle,
    kind = SoilEntryKind.MUTATION,
    location = SoilEntryLocation.ACTIVE,
    id = SoilEntryId(className = "MutationId", namespace = namespace, tags = emptyList()),
    state = SoilEntryState.Mutation(status = SoilStatus.SUCCESS, hasReply = true, replyUpdatedAt = 990, error = null, errorUpdatedAt = 0, mutatedCount = 1, submittedAt = 990),
    isObserved = false,
    options = emptyMap(),
)

internal fun snapshotOf(vararg entries: SoilEntry, revision: Long = 1, agentEpochSeconds: Long = HOST_NOW) = SoilCacheSnapshot(
    coverage = readableCoverage,
    entries = entries.toList(),
    revision = revision,
    agentEpochMillis = agentEpochSeconds * 1000,
)
