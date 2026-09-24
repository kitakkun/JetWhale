package com.kitakkun.jetwhale.plugins.actions.host

import com.kitakkun.jetwhale.plugins.actions.protocol.ActionCatalog
import com.kitakkun.jetwhale.plugins.actions.protocol.ParameterType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
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
    fun `a replaced catalog drops suggestions the new descriptor no longer offers`() {
        runBlocking { browser.load() }

        browser.adopt(ActionCatalog(listOf(signIn.copy(parameters = signIn.parameters.map { it.copy(hasOptions = false) }))))

        assertEquals(null, browser.options[signIn.id])
    }
}
