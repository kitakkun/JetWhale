package com.kitakkun.jetwhale.plugins.soil.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class SoilEntryActionPolicyTest {
    @Test
    fun `an active entry is never removed`() {
        assertNotNull(SoilEntryActionPolicy.refusalOf(entry(SoilEntryKind.QUERY, SoilEntryLocation.ACTIVE), SoilEntryAction.REMOVE_INACTIVE))
        assertNotNull(SoilEntryActionPolicy.refusalOf(entry(SoilEntryKind.SUBSCRIPTION, SoilEntryLocation.ACTIVE_AND_CACHED), SoilEntryAction.REMOVE_INACTIVE))
    }

    @Test
    fun `an inactive query or subscription can be removed`() {
        assertNull(SoilEntryActionPolicy.refusalOf(entry(SoilEntryKind.INFINITE_QUERY, SoilEntryLocation.INACTIVE), SoilEntryAction.REMOVE_INACTIVE))
        assertNull(SoilEntryActionPolicy.refusalOf(entry(SoilEntryKind.SUBSCRIPTION, SoilEntryLocation.INACTIVE), SoilEntryAction.REMOVE_INACTIVE))
    }

    @Test
    fun `queries are invalidated wherever Soil holds them`() {
        SoilEntryLocation.entries.forEach { location ->
            assertNull(SoilEntryActionPolicy.refusalOf(entry(SoilEntryKind.QUERY, location), SoilEntryAction.INVALIDATE))
        }
    }

    @Test
    fun `mutations take no action at all`() {
        SoilEntryAction.entries.forEach { action ->
            assertNotNull(SoilEntryActionPolicy.refusalOf(entry(SoilEntryKind.MUTATION, SoilEntryLocation.ACTIVE), action))
        }
    }

    @Test
    fun `resume needs an active entry that a screen observes`() {
        assertNull(SoilEntryActionPolicy.refusalOf(entry(SoilEntryKind.SUBSCRIPTION, SoilEntryLocation.ACTIVE, isObserved = true), SoilEntryAction.RESUME))
        assertEquals(
            "Nothing observes this entry, so Soil would ignore the resume.",
            SoilEntryActionPolicy.refusalOf(entry(SoilEntryKind.QUERY, SoilEntryLocation.ACTIVE, isObserved = false), SoilEntryAction.RESUME),
        )
        assertNotNull(SoilEntryActionPolicy.refusalOf(entry(SoilEntryKind.QUERY, SoilEntryLocation.INACTIVE, isObserved = false), SoilEntryAction.RESUME))
    }

    private fun entry(kind: SoilEntryKind, location: SoilEntryLocation, isObserved: Boolean = false) = SoilEntry(
        handle = "h",
        kind = kind,
        location = location,
        id = SoilEntryId(className = "QueryId", namespace = "users", tags = emptyList()),
        state = SoilEntryState.Subscription(status = SoilStatus.SUCCESS, hasReply = true, replyUpdatedAt = 1, error = null, errorUpdatedAt = 0, restartedAt = 0),
        isObserved = isObserved,
        options = emptyMap(),
        replyRevision = 0,
    )
}
