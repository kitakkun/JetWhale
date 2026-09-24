package com.kitakkun.jetwhale.plugins.actions.host

import com.kitakkun.jetwhale.plugins.actions.protocol.ActionDescriptor
import kotlin.test.Test
import kotlin.test.assertEquals

class ActionSearchTest {
    private val actions = listOf(
        action("Account / Log in as", destructive = false, parameters = emptyList()),
        action("Account / Log out", destructive = false, parameters = emptyList()),
        action("Clock / Shift time", destructive = false, parameters = emptyList()),
        action("Wipe local data", destructive = true, parameters = emptyList()),
    )

    @Test
    fun `every word of the query must appear in the group or title`() {
        assertEquals(listOf("Account / Log in as"), searchActions(actions, "account in", pinnedIds = emptySet()).map(ActionDescriptor::id))
    }

    @Test
    fun `pinned actions come first then titles starting with the query`() {
        val found = searchActions(actions, "lo", pinnedIds = setOf("Clock / Shift time")).map(ActionDescriptor::id)

        assertEquals(listOf("Clock / Shift time", "Account / Log in as", "Account / Log out", "Wipe local data"), found)
    }

    @Test
    fun `an empty query lists everything with pins first`() {
        val found = searchActions(actions, "", pinnedIds = setOf("Wipe local data")).map(ActionDescriptor::id)

        assertEquals("Wipe local data", found.first())
        assertEquals(4, found.size)
    }
}
