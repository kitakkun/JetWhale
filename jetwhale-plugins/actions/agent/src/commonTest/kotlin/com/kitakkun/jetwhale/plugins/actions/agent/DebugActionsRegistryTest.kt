package com.kitakkun.jetwhale.plugins.actions.agent

import com.kitakkun.jetwhale.annotations.InternalJetWhaleApi
import com.kitakkun.jetwhale.plugins.actions.protocol.ActionDescriptor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@OptIn(InternalJetWhaleApi::class)
class DebugActionsRegistryTest {
    private val plugin = JetWhaleDebugActionsAgentPlugin()

    @Test
    fun `groups nest into the action path`() {
        plugin.register {
            group("Account") {
                group("Test users") { action("Log in") { perform { } } }
            }
        }

        val action = plugin.catalog().actions.single()
        assertEquals("Account / Test users", action.group)
        assertEquals("Account / Test users / Log in", action.id)
    }

    @Test
    fun `the same action registered twice gets a second id`() {
        plugin.registerScoped { action("Fill form") { perform { } } }
        plugin.registerScoped { action("Fill form") { perform { } } }

        assertEquals(listOf("Fill form", "Fill form #2"), plugin.catalog().actions.map(ActionDescriptor::id))
    }

    @Test
    fun `unregistering removes only that registration's actions`() {
        plugin.register { action("Reset onboarding") { perform { } } }
        val screen = plugin.registerScoped { action("Fill form") { perform { } } }

        screen.unregister()

        assertEquals(listOf("Reset onboarding"), plugin.catalog().actions.map(ActionDescriptor::id))
    }

    @Test
    fun `scoped and destructive actions are marked as such`() {
        plugin.register {
            action("Wipe data") {
                destructive = true
                perform { }
            }
        }
        plugin.registerScoped { action("Fill form") { perform { } } }

        val byId = plugin.catalog().actions.associateBy(ActionDescriptor::id)
        assertEquals(true, byId.getValue("Wipe data").destructive)
        assertEquals(false, byId.getValue("Wipe data").scoped)
        assertEquals(true, byId.getValue("Fill form").scoped)
    }

    @Test
    fun `an action without a perform block is refused when declared`() {
        assertFailsWith<IllegalStateException> { plugin.register { action("Nothing") { } } }
    }
}
