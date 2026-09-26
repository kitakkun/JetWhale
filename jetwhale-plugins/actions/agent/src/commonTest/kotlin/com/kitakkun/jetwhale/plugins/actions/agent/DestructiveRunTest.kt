package com.kitakkun.jetwhale.plugins.actions.agent

import com.kitakkun.jetwhale.annotations.InternalJetWhaleApi
import com.kitakkun.jetwhale.plugins.actions.protocol.ActionOutcome
import com.kitakkun.jetwhale.plugins.actions.protocol.RunAction
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(InternalJetWhaleApi::class)
class DestructiveRunTest {
    private val plugin = JetWhaleDebugActionsAgentPlugin()
    private var wiped = false

    init {
        plugin.register {
            action("Wipe data") {
                destructive = true
                run { wiped = true }
            }
        }
        plugin.dispatchActivate()
    }

    @AfterTest
    fun deactivate() {
        plugin.dispatchDeactivate()
    }

    @Test
    fun `an unconfirmed run of a destructive action is refused by the agent itself`() = runTest {
        val result = plugin.runAction(RunAction(runId = "1", actionId = "Wipe data", arguments = JsonObject(emptyMap()), confirmedDestructive = false))

        assertEquals(ActionOutcome.FAILURE, result.outcome)
        assertEquals(false, wiped)
    }

    @Test
    fun `a confirmed run of a destructive action runs`() = runTest {
        val result = plugin.runAction(RunAction(runId = "1", actionId = "Wipe data", arguments = JsonObject(emptyMap()), confirmedDestructive = true))

        assertEquals(ActionOutcome.SUCCESS, result.outcome)
        assertEquals(true, wiped)
    }

    @Test
    fun `an id no longer registered fails instead of running something else`() = runTest {
        val result = plugin.runAction(RunAction(runId = "1", actionId = "Fill form", arguments = JsonObject(emptyMap()), confirmedDestructive = true))

        assertEquals(ActionOutcome.FAILURE, result.outcome)
        assertEquals(false, wiped)
    }
}
