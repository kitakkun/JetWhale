package com.kitakkun.jetwhale.plugins.actions.host

import com.kitakkun.jetwhale.plugins.actions.protocol.ActionCatalog
import com.kitakkun.jetwhale.plugins.actions.protocol.ActionOptions
import com.kitakkun.jetwhale.plugins.actions.protocol.ParameterType
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals

class ActionsBrowserTest {
    private val signIn = action(
        "Account / Sign in",
        destructive = false,
        parameters = listOf(parameter("email", ParameterType.STRING, optional = false, nullable = false).copy(hasOptions = true)),
    )
    private val client = FakeActionsClient(
        actions = listOf(signIn),
        options = mapOf((signIn.id to "email") to listOf("qa@example.com")),
        result = succeeded,
    )

    // The fake answers without suspending, so every launched call has finished when launch returns.
    private val browser = ActionsBrowser(client, CoroutineScope(Dispatchers.Unconfined))

    @Test
    fun `the action selected when the catalog arrives gets its suggestions`() {
        runBlocking { browser.load() }

        assertEquals(signIn.id, browser.selectedId)
        assertEquals(mapOf("email" to listOf("qa@example.com")), browser.options[signIn.id])
    }

    @Test
    fun `suggestions from an older request do not replace newer ones`() {
        val firstRequestGate = CompletableDeferred<Unit>()
        var requests = 0
        val slowFirst = object : ActionsClient by client {
            override suspend fun options(actionId: String, parameter: String): ActionOptions {
                requests++
                if (requests == 1) {
                    firstRequestGate.await()
                    return ActionOptions(values = listOf("stale@example.com"), error = null)
                }
                return ActionOptions(values = listOf("fresh@example.com"), error = null)
            }
        }
        val racingBrowser = ActionsBrowser(slowFirst, CoroutineScope(Dispatchers.Unconfined))
        racingBrowser.adopt(ActionCatalog(listOf(signIn)))

        racingBrowser.select(signIn.id)
        firstRequestGate.complete(Unit)

        assertEquals(listOf("fresh@example.com"), racingBrowser.options[signIn.id]?.get("email"))
    }

    @Test
    fun `a replaced catalog drops suggestions the new descriptor no longer offers`() {
        runBlocking { browser.load() }

        browser.adopt(ActionCatalog(listOf(signIn.copy(parameters = signIn.parameters.map { it.copy(hasOptions = false) }))))

        assertEquals(null, browser.options[signIn.id])
    }

    @Test
    fun `the history keeps only the latest hundred runs with the newest first`() {
        runBlocking {
            browser.load()
            repeat(105) { index -> browser.runNow(signIn.id, emailArguments("user$index@example.com"), RunOrigin.USER, confirmedDestructive = false) }
        }

        assertEquals(100, browser.history.size)
        assertEquals(emailArguments("user104@example.com"), browser.history.first().arguments)
        assertEquals(emailArguments("user5@example.com"), browser.history.last().arguments)
    }

    @Test
    fun `run again opens the action on the actions tab with the run's arguments`() {
        val reset = action("Reset onboarding", destructive = false, parameters = emptyList())
        client.actions = listOf(signIn, reset)
        runBlocking {
            browser.load()
            browser.runNow(signIn.id, emailArguments("qa@example.com"), RunOrigin.AI_AGENT, confirmedDestructive = false)
        }
        browser.select(reset.id)
        browser.showTab(ActionsTab.HISTORY)

        browser.runAgain(browser.history.single().runId)

        assertEquals(ActionsTab.ACTIONS, browser.tab)
        assertEquals(signIn.id, browser.selectedId)
        assertEquals(signIn.id, browser.prefill?.actionId)
        assertEquals(emailArguments("qa@example.com"), browser.prefill?.arguments)
    }

    @Test
    fun `selecting another action drops the run again arguments`() {
        val reset = action("Reset onboarding", destructive = false, parameters = emptyList())
        client.actions = listOf(signIn, reset)
        runBlocking {
            browser.load()
            browser.runNow(signIn.id, emailArguments("qa@example.com"), RunOrigin.USER, confirmedDestructive = false)
        }
        browser.runAgain(browser.history.single().runId)

        browser.select(reset.id)

        assertEquals(null, browser.prefill)
    }

    private fun emailArguments(email: String) = JsonObject(mapOf("email" to JsonPrimitive(email)))
}
