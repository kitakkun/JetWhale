package com.kitakkun.jetwhale.plugins.soil.protocol

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SoilMessagesTest {
    private val json = Json

    private val queryEntry = SoilEntry(
        handle = "query-1",
        kind = SoilEntryKind.QUERY,
        location = SoilEntryLocation.ACTIVE_AND_CACHED,
        id = SoilEntryId(className = "QueryId", namespace = "users/profile", tags = listOf("42", "(en, null)")),
        state = SoilEntryState.Query(
            status = SoilStatus.FAILURE,
            hasReply = true,
            replyUpdatedAt = 100,
            error = SoilEntryError(className = "IOException", message = null),
            errorUpdatedAt = 120,
            staleAt = Long.MAX_VALUE,
            fetchStatus = SoilFetchStatus.Paused(unpauseAt = 150),
            isInvalidated = false,
        ),
        isObserved = true,
        options = mapOf("staleTime" to "Infinity", "gcTime" to "5m"),
    )

    @Test
    fun `a snapshot survives the round trip`() {
        val snapshot = SoilCacheSnapshot(
            coverage = SoilCacheCoverage(clientClassName = "SwrCachePlus", isClientReadable = true, includesInactiveEntries = true, includesSubscriptions = true),
            entries = listOf(
                queryEntry,
                queryEntry.copy(
                    handle = "mutation-2",
                    kind = SoilEntryKind.MUTATION,
                    state = SoilEntryState.Mutation(status = SoilStatus.IDLE, hasReply = false, replyUpdatedAt = 0, error = null, errorUpdatedAt = 0, mutatedCount = 0, submittedAt = 0),
                ),
                queryEntry.copy(
                    handle = "subscription-3",
                    kind = SoilEntryKind.SUBSCRIPTION,
                    state = SoilEntryState.Subscription(status = SoilStatus.PENDING, hasReply = false, replyUpdatedAt = 0, error = null, errorUpdatedAt = 0, restartedAt = 90),
                ),
            ),
            revision = 7,
            agentEpochMillis = 200_000,
        )

        assertEquals(snapshot, json.decodeFromString(SoilCacheSnapshot.serializer(), json.encodeToString(SoilCacheSnapshot.serializer(), snapshot)))
    }

    @Test
    fun `every kind of value survives the round trip`() {
        listOf(
            SoilEntryValue.NoReply,
            SoilEntryValue.EntryGone,
            SoilEntryValue.Json(encoding = SoilValueEncoding.REGISTERED_SERIALIZER, json = Json.parseToJsonElement("""{"items":[1,2]}""")),
            SoilEntryValue.Text(encoding = SoilValueEncoding.TO_STRING, text = "Page(items=[1", fullLength = 20),
        ).forEach { value ->
            assertEquals(value, json.decodeFromString(SoilEntryValue.serializer(), json.encodeToString(SoilEntryValue.serializer(), value)))
        }
    }

    @Test
    fun `state variants are told apart by their serial names on the wire`() {
        val encoded = json.encodeToJsonElement(SoilEntry.serializer(), queryEntry).jsonObject

        assertEquals("soil/state/query", encoded.getValue("state").jsonObject.getValue("type").jsonPrimitive.content)
        assertEquals("soil/fetch/paused", encoded.getValue("state").jsonObject.getValue("fetchStatus").jsonObject.getValue("type").jsonPrimitive.content)
    }

    @Test
    fun `a text value knows when it was cut`() {
        assertTrue(SoilEntryValue.Text(encoding = SoilValueEncoding.TO_STRING, text = "abc", fullLength = 10).isTruncated)
        assertFalse(SoilEntryValue.Text(encoding = SoilValueEncoding.TO_STRING, text = "abc", fullLength = 3).isTruncated)
    }
}
