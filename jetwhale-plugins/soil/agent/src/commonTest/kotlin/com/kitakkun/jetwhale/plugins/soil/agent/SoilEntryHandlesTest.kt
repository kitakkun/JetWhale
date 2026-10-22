package com.kitakkun.jetwhale.plugins.soil.agent

import com.kitakkun.jetwhale.plugins.soil.protocol.SoilEntryKind
import soil.query.MutationId
import soil.query.QueryId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

class SoilEntryHandlesTest {
    private val handles = SoilEntryHandles()

    @Test
    fun `equal ids get the same handle even as different instances`() {
        val first = handles.handleOf(SoilEntryKey(SoilEntryKind.QUERY, QueryId<String>("users/profile", 42)))
        val second = handles.handleOf(SoilEntryKey(SoilEntryKind.QUERY, QueryId<String>("users/profile", 42)))

        assertEquals(first, second)
    }

    @Test
    fun `ids that differ in a tag get different handles`() {
        val first = handles.handleOf(SoilEntryKey(SoilEntryKind.QUERY, QueryId<String>("users/profile", 42)))
        val second = handles.handleOf(SoilEntryKey(SoilEntryKind.QUERY, QueryId<String>("users/profile", 7)))

        assertNotEquals(first, second)
    }

    @Test
    fun `a handle names the entry it was given for`() {
        val key = SoilEntryKey(SoilEntryKind.MUTATION, MutationId<Unit, String>("users/rename"))

        assertEquals(key, handles.keyOf(handles.handleOf(key)))
    }

    @Test
    fun `the handle of a retired entry names nothing until the entry comes back with it`() {
        val key = SoilEntryKey(SoilEntryKind.QUERY, QueryId<String>("settings"))
        val handle = handles.handleOf(key)

        handles.retire(key)
        assertNull(handles.keyOf(handle))

        assertEquals(handle, handles.handleOf(SoilEntryKey(SoilEntryKind.QUERY, QueryId<String>("settings"))))
        assertEquals(key, handles.keyOf(handle))
    }
}
